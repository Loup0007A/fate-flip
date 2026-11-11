package com.fateflip.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
//#if !mc26
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
//#endif

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

    //#if mc26
    //$$ /**
    //$$  * 26.x : le monde est assombri par un cube autour de la caméra, puis les minerais et leur halo coloré
    //$$  * sont soumis par-dessus, sans test de profondeur (ordres 1000, 1001, 1002 : dans cet ordre).
    //$$  */
    //$$ @Override
    //$$ public void submitLevel(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent e) {
    //$$     if (MC.level == null || fade < 0.05f) return;
    //$$     Vec3 cp = e.getLevelRenderState().cameraRenderState.pos;
    //$$     var collector = e.getSubmitNodeCollector();
    //$$     PoseStack pose = e.getPoseStack();
    //$$     final List<Ore> ores = found;
    //$$     final int dark = ((int) (0.78f * fade * 255) << 24) | 0x05080F;
    //$$     collector.order(1000).submitCustomGeometry(pose, com.fateflip.render.Pipelines.XRAY_TINT,
    //$$         (p, vc) -> box(p, vc, -3f, -3f, -3f, 3f, 3f, 3f, dark));
    //$$     collector.order(1001).submitCustomGeometry(pose, com.fateflip.render.Pipelines.XRAY_BLOCKS, (p, vc) -> {
    //$$         var models = MC.getModelManager().getBlockStateModelSet();
    //$$         var parts = new ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
    //$$         var quad = new com.mojang.blaze3d.vertex.QuadInstance();
    //$$         quad.setColor(0xFFFFFFFF);
    //$$         quad.setLightCoords(net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
    //$$         quad.setOverlayCoords(OverlayTexture.NO_OVERLAY);
    //$$         net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
    //$$         for (Ore o : ores) {
    //$$             PoseStack.Pose at = p.copy();
    //$$             at.translate((float) (o.pos.getX() - cp.x), (float) (o.pos.getY() - cp.y), (float) (o.pos.getZ() - cp.z));
    //$$             parts.clear();
    //$$             random.setSeed(42L);
    //$$             models.get(o.state).collectParts(random, parts);
    //$$             for (var part : parts) {
    //$$                 for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
    //$$                     for (var q : part.getQuads(d)) vc.putBakedQuad(at, q, quad);
    //$$                 }
    //$$                 for (var q : part.getQuads(null)) vc.putBakedQuad(at, q, quad);
    //$$             }
    //$$         }
    //$$     });
    //$$     final int alpha = (int) (0.28f * fade * 255);
    //$$     collector.order(1002).submitCustomGeometry(pose, com.fateflip.render.Pipelines.XRAY_TINT, (p, vc) -> {
    //$$         for (Ore o : ores) {
    //$$             float x = (float) (o.pos.getX() - cp.x), y = (float) (o.pos.getY() - cp.y), z = (float) (o.pos.getZ() - cp.z);
    //$$             box(p, vc, x - 0.02f, y - 0.02f, z - 0.02f, x + 1.02f, y + 1.02f, z + 1.02f, (alpha << 24) | o.color);
    //$$         }
    //$$     });
    //$$ }
    //$$
    //$$ /** Les six faces d'une boîte, d'une seule couleur. */
    //$$ private static void box(PoseStack.Pose p, com.mojang.blaze3d.vertex.VertexConsumer vc,
    //$$                         float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
    //$$     float[][] f = {
    //$$         {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
    //$$         {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
    //$$         {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
    //$$     for (float[] q : f) {
    //$$         for (int i = 0; i < 12; i += 3) vc.addVertex(p, q[i], q[i + 1], q[i + 2]).setColor(argb);
    //$$     }
    //$$ }
    //#else
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
    //#endif

    /** Pour l'auto-test. */
    public int count() {
        return found.size();
    }
}
