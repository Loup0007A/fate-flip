package com.fateflip.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * X-ray, comme le « cheat » : le monde s'assombrit et les minerais (et coffres, générateurs)
 * des environs apparaissent à travers le terrain, entourés de leur couleur.
 * Le terrain est lu côté client, rien n'est envoyé au serveur.
 */
public class XRay extends CardEffect {

    private record Ore(BlockPos pos, BlockState state, int color) {}

    private static final int R = 22;
    private static final int MAX = 900;

    private List<Ore> found = new ArrayList<>();
    private List<Ore> scanning = new ArrayList<>();
    private BlockPos center;
    private int layer = -R;

    @Override
    public void start() {
        MC.getSoundManager().play(SoundFx.mark(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
            SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.5f)));
        // premier balayage complet tout de suite, pour que l'effet soit visible dès le départ
        if (MC.player != null) {
            center = MC.player.blockPosition();
            for (int y = -R; y <= R; y++) scanLayer(y);
            finishScan();
        }
    }

    private static final java.util.Map<net.minecraft.world.level.block.Block, Integer> COLORS = new java.util.IdentityHashMap<>();

    /**
     * Couleur du contour selon la nature du bloc, ou -1 s'il n'est pas intéressant.
     * On se fie au nom du bloc (…_ore, minerais modés compris) plutôt qu'aux tags,
     * qui dépendent de ce que le serveur envoie.
     */
    static int colorOf(BlockState s) {
        return COLORS.computeIfAbsent(s.getBlock(), XRay::classify);
    }

    private static int classify(net.minecraft.world.level.block.Block b) {
        if (b == Blocks.SPAWNER) return 0xC060FF;
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL) return 0xE0A040;
        if (b == Blocks.ANCIENT_DEBRIS) return 0xB07A5A;
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (path.equals("trial_spawner")) return 0xC060FF;
        if (!path.endsWith("_ore")) return -1;
        if (path.contains("diamond")) return 0x5CF2FF;
        if (path.contains("emerald")) return 0x3CFF6A;
        if (path.contains("gold")) return 0xFFD23C;
        if (path.contains("iron")) return 0xE8B89A;
        if (path.contains("redstone")) return 0xFF3030;
        if (path.contains("lapis")) return 0x3C6AFF;
        if (path.contains("copper")) return 0xFF8A4C;
        if (path.contains("coal")) return 0x808080;
        if (path.contains("quartz")) return 0xF0E8E0;
        return 0xFFFFFF;
    }

    private void scanLayer(int dy) {
        if (MC.level == null || center == null) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                BlockState s = MC.level.getBlockState(m);
                if (s.isAir()) continue;
                int c = colorOf(s);
                if (c >= 0) scanning.add(new Ore(m.immutable(), s, c));
            }
        }
    }

    private void finishScan() {
        final BlockPos c = center;
        scanning.sort(Comparator.comparingDouble(o -> o.pos.distSqr(c)));
        if (scanning.size() > MAX) scanning = new ArrayList<>(scanning.subList(0, MAX));
        found = scanning;
        scanning = new ArrayList<>();
    }

    @Override
    public void tick() {
        if (MC.player == null) return;
        if (center == null) center = MC.player.blockPosition();
        for (int i = 0; i < 4 && layer <= R; i++) scanLayer(layer++);
        if (layer > R) {
            finishScan();
            layer = -R;
            center = MC.player.blockPosition();
        }
    }

    @Override
    public void renderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || MC.level == null || fade < 0.05f) return;
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack pose = com.fateflip.render.WorldView.begin(cam);
        try {
            draw(pose, cp);
        } finally {
            com.fateflip.render.WorldView.end();
        }
    }

    private void draw(PoseStack pose, Vec3 cp) {

        // 1) le monde s'assombrit
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float a = 0.78f * fade;
        // un grand carré juste devant la caméra (repère caméra : sans la rotation)
        com.fateflip.platform.Compat.drawColorQuad(new org.joml.Matrix4f(), -50f, -50f, 50f, 50f, -0.2f, 0.02f, 0.03f, 0.08f, a);
        RenderSystem.enableDepthTest();

        // 2) on efface la profondeur : les minerais passent devant tout
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, net.minecraft.client.Minecraft.ON_OSX);

        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        int light = LightTexture.FULL_BRIGHT;
        for (Ore o : found) {
            pose.pushPose();
            pose.translate(o.pos.getX() - cp.x, o.pos.getY() - cp.y, o.pos.getZ() - cp.z);
            MC.getBlockRenderer().renderSingleBlock(o.state, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
        buffers.endBatch();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Ore o : found) {
            double x = o.pos.getX() - cp.x, y = o.pos.getY() - cp.y, z = o.pos.getZ() - cp.z;
            float r = ((o.color >> 16) & 255) / 255f, g = ((o.color >> 8) & 255) / 255f, b = (o.color & 255) / 255f;
            LevelRenderer.renderLineBox(pose, lines, x - 0.02, y - 0.02, z - 0.02, x + 1.02, y + 1.02, z + 1.02, r, g, b, fade);
        }
        buffers.endBatch(RenderType.lines());
    }

    /** Pour l'auto-test. */
    public int count() {
        return found.size();
    }
}
