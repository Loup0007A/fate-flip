package com.fateflip.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hallucinations : des villages, des portails en ruine et des troupeaux apparaissent au loin…
 * et se dissipent dès qu'on s'en approche. Rien n'existe : blocs et créatures sont seulement dessinés.
 */
public class Hallucinations extends CardEffect {

    private record Placed(BlockPos pos, BlockState state) {}

    private static final class Figure {
        final Entity entity;
        final double x, y, z;
        float yaw;

        Figure(Entity entity, double x, double y, double z, float yaw) {
            this.entity = entity;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }
    }

    private static final class Mirage {
        final List<Placed> blocks = new ArrayList<>();
        final List<Figure> figures = new ArrayList<>();
        Vec3 center;
        float alpha, alphaO;
        boolean vanishing;
    }

    private final List<Mirage> mirages = new ArrayList<>();
    private int cooldown = 40;

    @Override
    public void start() {
        cooldown = 20;
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.level == null) return;
        Vec3 me = MC.player.position();
        Iterator<Mirage> it = mirages.iterator();
        while (it.hasNext()) {
            Mirage m = it.next();
            m.alphaO = m.alpha;
            double dist = Math.sqrt(Math.pow(m.center.x - me.x, 2) + Math.pow(m.center.z - me.z, 2));
            if (!m.vanishing && (dist < 15.0 || dist > 110.0)) vanish(m);
            if (m.vanishing) {
                m.alpha -= 0.06f;
                if (m.alpha <= 0f) {
                    it.remove();
                    continue;
                }
            } else {
                m.alpha = Math.min(1f, m.alpha + 0.025f);
            }
            for (Figure f : m.figures) {
                f.entity.tickCount++;
                if (f.entity instanceof LivingEntity le) {
                    float want = (float) (Mth.atan2(me.z - f.z, me.x - f.x) * Mth.RAD_TO_DEG) - 90f;
                    le.yHeadRotO = le.yHeadRot;
                    le.yHeadRot = Mth.approachDegrees(le.yHeadRot, want, 4f);
                }
            }
        }
        if (mirages.size() < 2 && --cooldown <= 0) {
            cooldown = 160 + rng.nextInt(240);
            double angle = rng.nextDouble() * Math.PI * 2;
            spawn(angle, 42 + rng.nextDouble() * 26, rng.nextInt(100));
        }
    }

    private void vanish(Mirage m) {
        m.vanishing = true;
        for (Figure f : m.figures) {
            for (int i = 0; i < 12; i++) {
                MC.level.addParticle(ParticleTypes.POOF, f.x + rng.nextGaussian() * 0.3, f.y + rng.nextDouble() * 1.8,
                    f.z + rng.nextGaussian() * 0.3, 0, 0.03, 0);
            }
        }
        MC.getSoundManager().play(SoundFx.mark(SimpleSoundInstance.forUI(SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8f, 0.35f)));
    }

    @Override
    public void debugTrigger() {
        if (MC.player == null) return;
        double yaw = Math.toRadians(MC.player.getYRot() + 90.0);
        mirages.clear();
        Mirage m = spawn(yaw, 26, 0);
        if (m != null) m.alpha = m.alphaO = 1f;
    }

    // ───────────────────────── construction ─────────────────────────

    private Mirage spawn(double angle, double dist, int roll) {
        double x = MC.player.getX() + Math.cos(angle) * dist, z = MC.player.getZ() + Math.sin(angle) * dist;
        if (!MC.level.getChunkSource().hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4)) return null;
        Mirage m = new Mirage();
        m.center = new Vec3(x, MC.player.getY(), z);
        Rotation rot = Rotation.values()[rng.nextInt(4)];
        BlockPos o = BlockPos.containing(x, 0, z);
        if (roll < 60) village(m, o, rot);
        else if (roll < 85) ruinedPortal(m, o, rot);
        else herd(m, o);
        if (m.blocks.isEmpty() && m.figures.isEmpty()) return null;
        mirages.add(m);
        return m;
    }

    private int ground(int x, int z) {
        return MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Pose un morceau (coordonnées locales tournées) au niveau du sol de son coin. */
    private void piece(Mirage m, BlockPos origin, Rotation rot, int ox, int oz, List<Placed> local) {
        BlockPos corner = origin.offset(new BlockPos(ox, 0, oz).rotate(rot));
        int gy = ground(corner.getX(), corner.getZ());
        for (Placed p : local) {
            BlockPos r = p.pos.rotate(rot);
            m.blocks.add(new Placed(new BlockPos(corner.getX() + r.getX(), gy + r.getY(), corner.getZ() + r.getZ()), p.state.rotate(rot)));
        }
    }

    private static void box(List<Placed> l, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) l.add(new Placed(new BlockPos(x, y, z), s));
    }

    private List<Placed> house() {
        List<Placed> l = new ArrayList<>();
        box(l, 0, 0, 0, 4, 0, 4, Blocks.COBBLESTONE.defaultBlockState());
        BlockState log = Blocks.OAK_LOG.defaultBlockState(), planks = Blocks.OAK_PLANKS.defaultBlockState();
        for (int y = 1; y <= 3; y++) {
            for (int i = 0; i <= 4; i++) {
                boolean corner = i == 0 || i == 4;
                for (int[] c : new int[][]{{i, 0}, {i, 4}, {0, i}, {4, i}}) {
                    boolean cornerXZ = (c[0] == 0 || c[0] == 4) && (c[1] == 0 || c[1] == 4);
                    BlockState s = cornerXZ ? log : planks;
                    if (y == 2 && !corner && i == 2) s = Blocks.GLASS_PANE.defaultBlockState();
                    l.add(new Placed(new BlockPos(c[0], y, c[1]), s));
                }
            }
        }
        // porte au sud
        l.removeIf(p -> p.pos.getX() == 2 && p.pos.getZ() == 4 && (p.pos.getY() == 1 || p.pos.getY() == 2));
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        l.add(new Placed(new BlockPos(2, 1, 4), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)));
        l.add(new Placed(new BlockPos(2, 2, 4), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
        BlockState roof = Blocks.SPRUCE_PLANKS.defaultBlockState();
        box(l, -1, 4, -1, 5, 4, 5, roof);
        box(l, 0, 5, 0, 4, 5, 4, roof);
        box(l, 1, 6, 1, 3, 6, 3, roof);
        l.add(new Placed(new BlockPos(2, 7, 2), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)));
        l.add(new Placed(new BlockPos(1, 1, 1), Blocks.CRAFTING_TABLE.defaultBlockState()));
        l.add(new Placed(new BlockPos(3, 1, 1), Blocks.BARREL.defaultBlockState()));
        return l;
    }

    private List<Placed> well() {
        List<Placed> l = new ArrayList<>();
        BlockState cob = Blocks.COBBLESTONE.defaultBlockState();
        box(l, 0, 0, 0, 2, 0, 2, cob);
        l.removeIf(p -> p.pos.getX() == 1 && p.pos.getZ() == 1);
        l.add(new Placed(new BlockPos(1, 0, 1), Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)));
        for (int[] c : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
            l.add(new Placed(new BlockPos(c[0], 1, c[1]), Blocks.OAK_FENCE.defaultBlockState()));
            l.add(new Placed(new BlockPos(c[0], 2, c[1]), Blocks.OAK_FENCE.defaultBlockState()));
        }
        box(l, 0, 3, 0, 2, 3, 2, Blocks.COBBLESTONE_SLAB.defaultBlockState());
        return l;
    }

    private List<Placed> farm() {
        List<Placed> l = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 4; z++) {
                l.add(new Placed(new BlockPos(x, 0, z), Blocks.FARMLAND.defaultBlockState()));
                l.add(new Placed(new BlockPos(x, 1, z), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3 + rng.nextInt(5))));
            }
        }
        l.add(new Placed(new BlockPos(5, 0, 0), Blocks.HAY_BLOCK.defaultBlockState()));
        l.add(new Placed(new BlockPos(5, 0, 1), Blocks.HAY_BLOCK.defaultBlockState()));
        l.add(new Placed(new BlockPos(5, 1, 0), Blocks.HAY_BLOCK.defaultBlockState()));
        l.add(new Placed(new BlockPos(5, 0, 3), Blocks.COMPOSTER.defaultBlockState()));
        return l;
    }

    private List<Placed> lamp() {
        List<Placed> l = new ArrayList<>();
        l.add(new Placed(new BlockPos(0, 0, 0), Blocks.OAK_FENCE.defaultBlockState()));
        l.add(new Placed(new BlockPos(0, 1, 0), Blocks.OAK_FENCE.defaultBlockState()));
        l.add(new Placed(new BlockPos(0, 2, 0), Blocks.LANTERN.defaultBlockState()));
        return l;
    }

    private void village(Mirage m, BlockPos o, Rotation rot) {
        piece(m, o, rot, -1, -1, well());
        piece(m, o, rot, -9, -8, house());
        piece(m, o, rot, 5, -9, house());
        piece(m, o, rot, -8, 5, house());
        piece(m, o, rot, 6, 4, farm());
        piece(m, o, rot, 3, 2, lamp());
        VillagerProfession[] jobs = {VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.CLERIC, VillagerProfession.FLETCHER};
        for (int i = 0; i < 3; i++) {
            Villager v = EntityType.VILLAGER.create(MC.level);
            if (v == null) continue;
            v.setVillagerData(v.getVillagerData().setProfession(jobs[rng.nextInt(jobs.length)]));
            figure(m, v, o.getX() + rng.nextInt(9) - 4 + 0.5, o.getZ() + rng.nextInt(9) - 4 + 0.5);
        }
        Entity golem = EntityType.IRON_GOLEM.create(MC.level);
        if (golem != null) figure(m, golem, o.getX() + 3.5, o.getZ() - 3.5);
    }

    private void ruinedPortal(Mirage m, BlockPos o, Rotation rot) {
        List<Placed> l = new ArrayList<>();
        box(l, -2, 0, -1, 5, 0, 2, Blocks.NETHERRACK.defaultBlockState());
        l.add(new Placed(new BlockPos(-1, 0, 2), Blocks.MAGMA_BLOCK.defaultBlockState()));
        l.add(new Placed(new BlockPos(4, 0, -1), Blocks.MAGMA_BLOCK.defaultBlockState()));
        l.add(new Placed(new BlockPos(5, 1, 1), Blocks.GOLD_BLOCK.defaultBlockState()));
        for (int y = 1; y <= 5; y++) {
            for (int x = 0; x <= 3; x++) {
                boolean frame = x == 0 || x == 3 || y == 1 || y == 5;
                if (!frame) continue;
                if (rng.nextInt(7) == 0) continue;
                l.add(new Placed(new BlockPos(x, y, 0), (rng.nextInt(4) == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState()));
            }
        }
        piece(m, o, rot, 0, 0, l);
    }

    private void herd(Mirage m, BlockPos o) {
        EntityType<?>[] kinds = {EntityType.COW, EntityType.SHEEP, EntityType.HORSE, EntityType.PIG};
        EntityType<?> kind = kinds[rng.nextInt(kinds.length)];
        for (int i = 0; i < 5; i++) {
            Entity e = kind.create(MC.level);
            if (e != null) figure(m, e, o.getX() + rng.nextGaussian() * 4, o.getZ() + rng.nextGaussian() * 4);
        }
    }

    private void figure(Mirage m, Entity e, double x, double z) {
        int y = ground(Mth.floor(x), Mth.floor(z));
        float yaw = rng.nextFloat() * 360f;
        e.moveTo(x, y, z, yaw, 0f);
        e.xo = e.xOld = x;
        e.yo = e.yOld = y;
        e.zo = e.zOld = z;
        e.tickCount = rng.nextInt(100);
        if (e instanceof LivingEntity le) {
            le.yBodyRot = le.yBodyRotO = yaw;
            le.yHeadRot = le.yHeadRotO = yaw;
        }
        m.figures.add(new Figure(e, x, y, z, yaw));
    }

    // ───────────────────────── rendu ─────────────────────────

    /** Tampon qui multiplie l'opacité de chaque sommet (fondu des blocs). */
    private static final class Faded implements VertexConsumer {
        private final VertexConsumer inner;
        private final float alpha;

        Faded(VertexConsumer inner, float alpha) {
            this.inner = inner;
            this.alpha = alpha;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { inner.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { inner.setColor(r, g, b, (int) (a * alpha)); return this; }
        @Override public VertexConsumer setUv(float u, float v) { inner.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { inner.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { inner.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { inner.setNormal(x, y, z); return this; }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            int a = (int) (((color >>> 24) & 255) * alpha);
            inner.addVertex(x, y, z, (a << 24) | (color & 0xFFFFFF), u, v, overlay, light, nx, ny, nz);
        }
    }

    @Override
    public void renderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || mirages.isEmpty() || MC.level == null) return;
        float pt = e.getPartialTick();
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack pose = new PoseStack();
        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        RenderType type = Sheets.translucentCullBlockSheet();
        for (Mirage m : mirages) {
            float a = Mth.lerp(pt, m.alphaO, m.alpha) * fade;
            if (a <= 0.02f) continue;
            float shimmer = 0.85f + 0.15f * Mth.sin((age + pt) * 0.3f + (float) m.center.x);
            VertexConsumer vc = new Faded(buffers.getBuffer(type), Math.min(1f, a * shimmer));
            for (Placed p : m.blocks) {
                BakedModel model = MC.getBlockRenderer().getBlockModel(p.state);
                int light = LevelRenderer.getLightColor(MC.level, p.pos.above());
                int tint = MC.getBlockColors().getColor(p.state, MC.level, p.pos, 0);
                float r = 1f, g = 1f, b = 1f;
                if (tint != -1) {
                    r = ((tint >> 16) & 255) / 255f;
                    g = ((tint >> 8) & 255) / 255f;
                    b = (tint & 255) / 255f;
                }
                pose.pushPose();
                pose.translate(p.pos.getX() - cp.x, p.pos.getY() - cp.y, p.pos.getZ() - cp.z);
                MC.getBlockRenderer().getModelRenderer().renderModel(pose.last(), vc, p.state, model, r, g, b, light,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                pose.popPose();
            }
            buffers.endBatch(type);
            if (a > 0.6f) {
                for (Figure f : m.figures) {
                    int light = LevelRenderer.getLightColor(MC.level, BlockPos.containing(f.x, f.y + 1, f.z));
                    MC.getEntityRenderDispatcher().render(f.entity, f.x - cp.x, f.y - cp.y, f.z - cp.z, f.yaw, pt, pose, buffers, light);
                }
                buffers.endBatch();
            }
        }
    }
}
