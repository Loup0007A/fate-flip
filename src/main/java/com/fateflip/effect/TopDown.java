package com.fateflip.effect;

import com.fateflip.FateFlip;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

import java.lang.reflect.Method;

/**
 * Vue de dessus : le jeu passe en 2D, vu d'en haut, avec environ 13 blocs de haut à l'écran.
 * La souris déplace un pointeur libre ; le joueur se tourne vers lui et vise ce qu'il
 * survole (casser, poser, frapper, utiliser : toutes les actions restent possibles).
 * ZQSD / WASD déplacent vers le nord, l'ouest, le sud et l'est de l'écran.
 */
public class TopDown extends CardEffect {

    /** Hauteur visible au niveau des pieds, en blocs. */
    private static final float SPAN = 13f;
    private static final double MAX_UP = 14.0;
    private static Method setPosition;

    private CameraType previousCamera;
    private boolean previousCull;
    private float cursorX, cursorY;       // décalage du pointeur par rapport au centre, en pixels GUI
    private float lookYaw, lookPitch = 60f;
    private boolean haveLook;
    private double camUp = MAX_UP, camUpO = MAX_UP, camUpTarget = MAX_UP;
    private double ceilingY = Double.NaN;  // premier plafond au-dessus des yeux (ou NaN)
    private double lastFov = 30.0;
    private boolean inReach;

    private float ease() {
        return fade * fade * (3 - 2 * fade);
    }

    @Override
    public void start() {
        previousCamera = MC.options.getCameraType();
        MC.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        previousCull = MC.smartCull;
        MC.smartCull = false;
        cursorY = -MC.getWindow().getGuiScaledHeight() * 0.18f;
        if (MC.player != null) lookYaw = MC.player.getYRot();
        updateCeiling();
        camUp = camUpO = camUpTarget;
    }

    @Override
    public void stop() {
        MC.options.setCameraType(previousCamera == null ? CameraType.FIRST_PERSON : previousCamera);
        MC.smartCull = previousCull;
        LocalPlayer p = MC.player;
        if (p != null && haveLook) {
            p.setYRot(lookYaw);
            p.setXRot(Mth.clamp(lookPitch, -60f, 60f));
            p.yRotO = p.getYRot();
            p.xRotO = p.getXRot();
        }
    }

    private boolean active() {
        return MC.player != null && MC.level != null && ease() > 0.5f;
    }

    // ───────────── orientation : le joueur regarde toujours le pointeur ─────────────

    private void applyLook() {
        LocalPlayer p = MC.player;
        if (p == null || !haveLook) return;
        p.setYRot(lookYaw);
        p.setXRot(lookPitch);
        p.yRotO = lookYaw;
        p.xRotO = lookPitch;
    }

    /** Orientation « neutre » : ce que la souris y ajoute est ensuite lu comme un déplacement du pointeur. */
    private void applyNeutral() {
        LocalPlayer p = MC.player;
        if (p == null || !haveLook) return;
        p.setYRot(lookYaw);
        p.setXRot(0f);
        p.yRotO = lookYaw;
        p.xRotO = 0f;
    }

    @Override
    public void preTick() {
        if (active()) applyLook();
    }

    @Override
    public void tick() {
        if (MC.options.getCameraType() != CameraType.THIRD_PERSON_BACK) MC.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        MC.smartCull = false;
        updateCeiling();
        camUpO = camUp;
        camUp += (camUpTarget - camUp) * 0.35;
        if (active()) applyNeutral();
    }

    @Override
    public void frame(float partialTick) {
        LocalPlayer p = MC.player;
        if (p == null || MC.level == null) return;
        int w = MC.getWindow().getGuiScaledWidth(), h = MC.getWindow().getGuiScaledHeight();
        if (active() && haveLook) {
            // ce que la souris vient d'ajouter à l'orientation neutre = mouvement du pointeur
            float dyaw = p.getYRot() - lookYaw, dpitch = p.getXRot();
            if (Math.abs(dyaw) < 120f && Math.abs(dpitch) < 120f && MC.screen == null) {
                double scale = MC.getWindow().getGuiScale();
                int inv = MC.options.invertYMouse().get() ? -1 : 1;
                cursorX += (float) (dyaw / 0.15 / scale);
                cursorY += (float) (dpitch * inv / 0.15 / scale);
            }
        }
        cursorX = Mth.clamp(cursorX, -w / 2f + 2, w / 2f - 2);
        cursorY = Mth.clamp(cursorY, -h / 2f + 2, h / 2f - 2);
        aim(partialTick, w, h);
        if (active()) applyLook();
    }

    /** Calcule le point du monde sous le pointeur et l'orientation pour le viser depuis les yeux. */
    private void aim(float pt, int w, int h) {
        LocalPlayer p = MC.player;
        Vec3 eye = p.getEyePosition(pt);
        double up = Mth.lerp(pt, camUpO, camUp);
        Vec3 cam = eye.add(0, up, 0);
        double t = Math.tan(Math.toRadians(lastFov) / 2.0);
        double aspect = (double) w / h;
        double nx = cursorX / (w / 2.0), ny = -cursorY / (h / 2.0);
        Vec3 dir = new Vec3(nx * t * aspect, -1.0, -ny * t).normalize();
        // le rayon part sous le plafond (dans une grotte, la caméra est dans la roche)
        double startY = Double.isNaN(ceilingY) ? cam.y : Math.min(cam.y, ceilingY - 0.02);
        double k0 = (cam.y - startY) / -dir.y;
        Vec3 start = cam.add(dir.scale(k0));
        Vec3 end = start.add(dir.scale(64));
        BlockHitResult block = MC.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        Vec3 target;
        double best = block.getType() == HitResult.Type.MISS ? 64 * 64 : block.getLocation().distanceToSqr(start);
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(MC.level, p, start, end, new AABB(start, end).inflate(1.0),
            e -> !e.isSpectator() && e.isPickable() && e != p, (float) best);
        if (ent != null) {
            Entity e = ent.getEntity();
            target = e.getBoundingBox().getCenter();
        } else if (block.getType() != HitResult.Type.MISS) {
            // un peu à l'intérieur de la face visée, pour que le rayon des yeux tombe sur le même bloc
            target = block.getLocation().subtract(dir.scale(-0.02));
        } else {
            double k = (cam.y - p.getY()) / -dir.y;
            target = cam.add(dir.scale(k));
        }
        Vec3 d = target.subtract(eye);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        if (horiz > 0.25) lookYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        lookPitch = (float) Mth.clamp(-(Mth.atan2(d.y, horiz) * Mth.RAD_TO_DEG), -89.9, 89.9);
        haveLook = true;
        double reach = ent != null ? p.entityInteractionRange() : p.blockInteractionRange();
        inReach = (ent != null || block.getType() != HitResult.Type.MISS) && eye.distanceTo(target) <= reach + 0.3;
    }

    /** Hauteur de caméra : sous le plafond, ou dans la roche au-dessus d'une grotte étroite. */
    private void updateCeiling() {
        LocalPlayer p = MC.player;
        if (p == null || MC.level == null) return;
        Vec3 eye = p.getEyePosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int y0 = Mth.floor(eye.y);
        double ceiling = Double.NaN;
        int i = 1;
        for (; i <= 16; i++) {
            m.set(eye.x, y0 + i, eye.z);
            BlockState s = MC.level.getBlockState(m);
            if (!s.getVisualShape(MC.level, m, net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty()) {
                ceiling = y0 + i;
                break;
            }
        }
        ceilingY = ceiling;
        if (Double.isNaN(ceiling)) {
            camUpTarget = MAX_UP;
            return;
        }
        double room = ceiling - eye.y - 0.3;
        if (room >= 3.0) {
            camUpTarget = Math.min(MAX_UP, room);
            return;
        }
        // grotte basse : si le plafond est de la roche pleine, la caméra s'y loge (on voit au travers)
        m.set(eye.x, ceiling, eye.z);
        if (!MC.level.getBlockState(m).isSolidRender(MC.level, m)) {
            camUpTarget = Math.max(0.6, room);
            return;
        }
        int top = (int) ceiling;
        while (top - eye.y < MAX_UP) {
            m.set(eye.x, top + 1, eye.z);
            if (!MC.level.getBlockState(m).isSolidRender(MC.level, m)) break;
            top++;
        }
        camUpTarget = Math.min(MAX_UP, top + 0.7 - eye.y);
    }

    // ───────────── caméra ─────────────

    private static void setCameraPosition(Camera cam, Vec3 pos) {
        try {
            if (setPosition == null) {
                setPosition = Camera.class.getDeclaredMethod("setPosition", Vec3.class);
                setPosition.setAccessible(true);
            }
            setPosition.invoke(cam, pos);
        } catch (Throwable t) {
            FateFlip.LOGGER.debug("Fate Flip : caméra non déplaçable", t);
        }
    }

    @Override
    public void camera(ViewportEvent.ComputeCameraAngles e) {
        LocalPlayer p = MC.player;
        if (p == null) return;
        float pt = (float) e.getPartialTick();
        float k = ease();
        Vec3 eye = p.getEyePosition(pt);
        Vec3 top = eye.add(0, Mth.lerp(pt, camUpO, camUp), 0);
        Vec3 from = e.getCamera().getPosition();
        setCameraPosition(e.getCamera(), from.lerp(top, k));
        e.setYaw(Mth.rotLerp(k, e.getYaw(), 180f));
        e.setPitch(Mth.lerp(k, e.getPitch(), 90f));
        e.setRoll(e.getRoll() * (1f - k));
    }

    @Override
    public double fov(double fov, float partialTick) {
        LocalPlayer p = MC.player;
        if (p == null) return fov;
        double d = Mth.lerp(partialTick, camUpO, camUp) + p.getEyeHeight();
        double ours = Math.toDegrees(2.0 * Math.atan(SPAN / 2.0 / Math.max(0.5, d)));
        ours = Mth.clamp(ours, 8.0, 150.0);
        lastFov = ours;
        return Mth.lerp(ease(), fov, ours);
    }

    // ───────────── déplacements : ZQSD = nord / ouest / sud / est de l'écran ─────────────

    @Override
    public void input(Input in, LocalPlayer player) {
        if (!active() || !haveLook) return;
        float mx = (in.right ? 1f : 0f) - (in.left ? 1f : 0f);
        float mz = (in.down ? 1f : 0f) - (in.up ? 1f : 0f);
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
        float fwd = -mx * sin + mz * cos;
        float left = mx * cos + mz * sin;
        if (player.isMovingSlowly()) {
            float f = (float) player.getAttributeValue(Attributes.SNEAKING_SPEED);
            fwd *= f;
            left *= f;
        }
        in.forwardImpulse = fwd;
        in.leftImpulse = left;
    }

    // ───────────── pointeur ─────────────

    @Override
    public void hudEnd(GuiGraphics g, float partialTick) {
        // l'image est dessinée : on repasse à l'orientation neutre pour lire la souris à l'image suivante
        if (active()) applyNeutral();
        if (ease() < 0.5f || MC.options.hideGui) return;
        int cx = g.guiWidth() / 2 + Math.round(cursorX), cy = g.guiHeight() / 2 + Math.round(cursorY);
        int col = inReach ? 0xFFFFFFFF : 0xB0A0A0B0;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(cx - 6, cy, cx - 2, cy + 1, col);
        g.fill(cx + 3, cy, cx + 7, cy + 1, col);
        g.fill(cx, cy - 6, cx + 1, cy - 2, col);
        g.fill(cx, cy + 3, cx + 1, cy + 7, col);
        g.renderOutline(cx - 3, cy - 3, 7, 7, inReach ? 0xFFF3D58A : 0x80707080);
        if (age < 120) {
            float a = Math.min(1f, (120 - age) / 30f);
            g.drawCenteredString(MC.font, Component.translatable("fateflip.top_down.hint"), g.guiWidth() / 2, g.guiHeight() - 62,
                ((int) (a * 255) << 24) | 0xF3D58A);
        }
        g.pose().popPose();
        RenderSystem.enableBlend();
    }
}
