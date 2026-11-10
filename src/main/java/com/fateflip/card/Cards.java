package com.fateflip.card;

import com.fateflip.config.FFConfig;
import com.fateflip.effect.CameraEffects;
import com.fateflip.effect.ControlEffects;
import com.fateflip.effect.EntityEffects;
import com.fateflip.effect.Hallucinations;
import com.fateflip.effect.HudEffects;
import com.fateflip.effect.NewEffects;
import com.fateflip.effect.TopDown;
import com.fateflip.effect.WorldEffects;
import com.fateflip.effect.XRay;
import com.fateflip.effect.ShaderEffects;
import com.fateflip.effect.SoundFx;
import com.fateflip.render.FxPass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.fateflip.card.Category.*;
import static com.fateflip.card.Severity.*;

/** Le jeu complet : 100 cartes (67 en 1.0, 21 ajoutées en 2.1, 12 en 2.2). Les malus sont dans {@link Curses}. */
public final class Cards {

    private Cards() {}

    private static final Map<String, Card> BY_ID = new LinkedHashMap<>();

    private static void add(Card.Builder b) {
        Card c = b.build();
        BY_ID.put(c.id, c);
    }

    static {
        // ── ESPACE : la géométrie de l'écran ──
        add(Card.of("triptych", SPACE, NIGHTMARE, "shears", ShaderEffects.Triptych::new).preview());
        add(Card.of("mirror", SPACE, STRANGE, "amethyst_shard", () -> ShaderEffects.full(FxPass.MIRROR)).preview());
        add(Card.of("antipodes", SPACE, NIGHTMARE, "pointed_dripstone", () -> ShaderEffects.full(FxPass.ANTIPODES)).preview());
        add(Card.of("taquin", SPACE, NIGHTMARE, "crafting_table", ShaderEffects.Taquin::new).preview());
        add(Card.of("kaleidoscope", SPACE, STRANGE, "amethyst_cluster", ShaderEffects::kaleidoscope).motion().preview());
        add(Card.of("vertigo", SPACE, STRANGE, "compass", ShaderEffects.Vertigo::new).motion().preview());
        add(Card.of("portrait", SPACE, GENTLE, "item_frame", () -> ShaderEffects.full(FxPass.PORTRAIT)).preview());
        add(Card.of("wandering", SPACE, STRANGE, "map", ShaderEffects.Wandering::new).motion().preview());
        add(Card.of("melt", SPACE, STRANGE, "magma_cream", () -> ShaderEffects.full(FxPass.MELT)).preview());
        add(Card.of("whirlpool", SPACE, STRANGE, "nautilus_shell", () -> ShaderEffects.full(FxPass.WHIRLPOOL)).motion().preview());
        add(Card.of("fisheye", SPACE, GENTLE, "tropical_fish", () -> ShaderEffects.full(FxPass.FISHEYE)).preview());
        add(Card.of("shattered", SPACE, STRANGE, "glass", ShaderEffects::shattered).preview());
        add(Card.of("video_wall", SPACE, STRANGE, "redstone_lamp", () -> ShaderEffects.full(FxPass.VIDEO_WALL)).preview());
        add(Card.of("droste", SPACE, STRANGE, "glow_item_frame", () -> ShaderEffects.full(FxPass.DROSTE)).preview());
        add(Card.of("ripple", SPACE, GENTLE, "lily_pad", () -> ShaderEffects.full(FxPass.RIPPLE)).preview());
        add(Card.of("glitch", SPACE, STRANGE, "redstone", () -> ShaderEffects.full(FxPass.GLITCH)).flashy().preview());

        // ── VISION : couleurs et matières ──
        add(Card.of("noir", VISION, GENTLE, "ink_sac", () -> ShaderEffects.world(FxPass.NOIR)).preview());
        add(Card.of("scarlet", VISION, GENTLE, "poppy", () -> ShaderEffects.world(FxPass.SCARLET)).preview());
        add(Card.of("negative", VISION, STRANGE, "ender_pearl", () -> ShaderEffects.world(FxPass.NEGATIVE)).preview());
        add(Card.of("thermal", VISION, STRANGE, "blaze_powder", () -> ShaderEffects.world(FxPass.THERMAL)).preview());
        add(Card.of("eight_bit", VISION, GENTLE, "emerald", () -> ShaderEffects.world(FxPass.EIGHT_BIT)).preview());
        add(Card.of("pocket", VISION, GENTLE, "lime_dye", () -> ShaderEffects.world(FxPass.POCKET)).preview());
        add(Card.of("vhs", VISION, STRANGE, "music_disc_11", ShaderEffects.Vhs::new).flashy().preview());
        add(Card.of("crt", VISION, GENTLE, "observer", () -> ShaderEffects.world(FxPass.CRT)).preview());
        add(Card.of("anaglyph", VISION, STRANGE, "red_stained_glass", () -> ShaderEffects.world(FxPass.ANAGLYPH)).preview());
        add(Card.of("tipsy", VISION, STRANGE, "honey_bottle", () -> ShaderEffects.world(FxPass.TIPSY)).motion().preview());
        add(Card.of("tunnel", VISION, NIGHTMARE, "ender_eye", ShaderEffects.Tunnel::new).preview());
        add(Card.of("sketch", VISION, GENTLE, "writable_book", () -> ShaderEffects.world(FxPass.SKETCH)).preview());
        add(Card.of("source_code", VISION, NIGHTMARE, "command_block", () -> ShaderEffects.world(FxPass.SOURCE_CODE)).preview());
        add(Card.of("psychedelic", VISION, STRANGE, "chorus_fruit", () -> ShaderEffects.world(FxPass.PSYCHEDELIC)).flashy().preview());
        add(Card.of("myopia", VISION, STRANGE, "glass_bottle", () -> ShaderEffects.world(FxPass.MYOPIA)).preview());
        add(Card.of("echolocation", VISION, NIGHTMARE, "sculk_sensor", ShaderEffects.Echolocation::new).preview());
        add(Card.of("wet_lens", VISION, GENTLE, "water_bucket", () -> ShaderEffects.world(FxPass.WET_LENS)).preview());
        add(Card.of("comic", VISION, GENTLE, "book", () -> ShaderEffects.world(FxPass.COMIC)).preview());
        add(Card.of("cinemascope", VISION, GENTLE, "painting", () -> ShaderEffects.world(FxPass.CINEMASCOPE)).preview());
        add(Card.of("night_goggles", VISION, GENTLE, "glow_ink_sac", () -> ShaderEffects.world(FxPass.NIGHT_GOGGLES)).preview());
        add(Card.of("colour_swap", VISION, GENTLE, "prismarine_crystals", () -> ShaderEffects.world(FxPass.COLOUR_SWAP)).preview());
        add(Card.of("silhouette", VISION, GENTLE, "lantern", () -> ShaderEffects.world(FxPass.SILHOUETTE)).preview());
        add(Card.of("stained_glass", VISION, GENTLE, "magenta_stained_glass", () -> ShaderEffects.world(FxPass.STAINED_GLASS)).preview());
        add(Card.of("pointillism", VISION, GENTLE, "brush", () -> ShaderEffects.world(FxPass.POINTILLISM)).preview());
        add(Card.of("radiograph", VISION, STRANGE, "skeleton_skull", () -> ShaderEffects.world(FxPass.RADIOGRAPH)).preview());
        add(Card.of("hologram", VISION, STRANGE, "end_crystal", () -> ShaderEffects.world(FxPass.HOLOGRAM)).flashy().preview());
        add(Card.of("tilt_shift", VISION, GENTLE, "oak_sapling", () -> ShaderEffects.world(FxPass.TILT_SHIFT)).preview());
        // 2.2
        add(Card.of("xray", VISION, GENTLE, "diamond_ore", XRay::new));
        add(Card.of("no_transparency", VISION, STRANGE, "tinted_glass", WorldEffects.NoTransparency::new));
        add(Card.of("sun_intolerance", VISION, STRANGE, "sunflower", ShaderEffects.SunIntolerance::new).flashy().preview());
        add(Card.of("fly_vision", VISION, STRANGE, "honeycomb", () -> ShaderEffects.world(FxPass.FLY_VISION)).preview());
        add(Card.of("blood_sky", VISION, STRANGE, "crimson_fungus", ShaderEffects.BloodSky::new).preview());

        // ── ESPRIT : caméra, interface et illusions ──
        add(Card.of("selfie", MIND, STRANGE, "player_head", CameraEffects.Selfie::new));
        add(Card.of("barrel_roll", MIND, STRANGE, "elytra", CameraEffects.BarrelRoll::new).motion());
        add(Card.of("spyglass", MIND, NIGHTMARE, "spyglass", CameraEffects.Spyglass::new));
        add(Card.of("ultrawide", MIND, GENTLE, "conduit", CameraEffects.UltraWide::new));
        add(Card.of("breathing", MIND, STRANGE, "pufferfish", CameraEffects.Breathing::new).motion());
        add(Card.of("hiccups", MIND, GENTLE, "cookie", CameraEffects.Hiccups::new).motion());
        add(Card.of("seasick", MIND, STRANGE, "oak_boat", CameraEffects.Seasick::new).motion());
        add(Card.of("dinnerbone", MIND, GENTLE, "name_tag", EntityEffects.Dinnerbone::new));
        add(Card.of("paper_world", MIND, GENTLE, "paper", EntityEffects.PaperWorld::new));
        add(Card.of("wonky_scale", MIND, STRANGE, "rabbit_foot", EntityEffects.WonkyScale::new));
        add(Card.of("screensaver", MIND, STRANGE, "slime_ball", HudEffects.Screensaver::new));
        add(Card.of("amnesia", MIND, STRANGE, "knowledge_book", HudEffects.Amnesia::new));
        add(Card.of("ghost_crosshairs", MIND, STRANGE, "target", HudEffects.GhostCrosshairs::new));
        add(Card.of("narrator", MIND, GENTLE, "written_book", HudEffects.Narrator::new));
        add(Card.of("glyphs", MIND, STRANGE, "enchanted_book", HudEffects.Glyphs::new));
        add(Card.of("eyelids", MIND, STRANGE, "red_bed", HudEffects.Eyelids::new));
        add(Card.of("eclipse", MIND, STRANGE, "clock", CameraEffects.Eclipse::new));
        add(Card.of("deluge", MIND, GENTLE, "trident", CameraEffects.Deluge::new).flashy());
        add(Card.of("pea_soup", MIND, STRANGE, "cobweb", CameraEffects.PeaSoup::new));
        add(Card.of("party", MIND, GENTLE, "firework_rocket", EntityEffects.Party::new));
        add(Card.of("look_back", MIND, NIGHTMARE, "carved_pumpkin", NewEffects.LookBack::new).motion());
        add(Card.of("upside_down_hud", MIND, STRANGE, "glass_pane", NewEffects.UpsideDownHud::new));
        add(Card.of("fake_achievements", MIND, GENTLE, "experience_bottle", NewEffects.FakeAchievements::new));
        add(Card.of("zero_gravity", MIND, GENTLE, "shulker_shell", NewEffects.ZeroGravity::new));
        add(Card.of("disco", MIND, STRANGE, "music_disc_pigstep", NewEffects.Disco::new));
        // 2.2
        add(Card.of("render_distance", MIND, NIGHTMARE, "structure_void", WorldEffects.RenderDistance::new));
        add(Card.of("screamers", MIND, NIGHTMARE, "wither_skeleton_skull", WorldEffects.Screamers::new).flashy());
        add(Card.of("hallucinations", MIND, STRANGE, "red_mushroom", Hallucinations::new));
        add(Card.of("no_anim", MIND, STRANGE, "armor_stand", WorldEffects.NoAnim::new));
        add(Card.of("cake_mobs", MIND, GENTLE, "cake", WorldEffects.CakeMobs::new));

        // ── CONTRÔLES : la façon dont vos commandes sont lues ──
        add(Card.of("scrambled_keys", CONTROL, NIGHTMARE, "tripwire_hook", ControlEffects.ScrambledKeys::new));
        add(Card.of("inverted_mouse", CONTROL, NIGHTMARE, "recovery_compass", ControlEffects.InvertedMouse::new));
        add(Card.of("steadicam", CONTROL, GENTLE, "minecart", ControlEffects.Steadicam::new));
        add(Card.of("coffee", CONTROL, STRANGE, "cocoa_beans", ControlEffects.Coffee::new));
        add(Card.of("hotbar_chaos", CONTROL, STRANGE, "bundle", ControlEffects.HotbarChaos::new));
        add(Card.of("crab_walk", CONTROL, STRANGE, "turtle_scute", ControlEffects.CrabWalk::new));
        add(Card.of("ice_rink", CONTROL, STRANGE, "blue_ice", NewEffects.IceRink::new));
        add(Card.of("input_lag", CONTROL, STRANGE, "honey_block", NewEffects.InputLag::new));
        add(Card.of("leapfrog", CONTROL, GENTLE, "leather_boots", NewEffects.Leapfrog::new));
        // 2.2
        add(Card.of("top_down", CONTROL, STRANGE, "cartography_table", TopDown::new));

        // ── OUÏE : ce que vous entendez ──
        add(Card.of("helium", SOUND, GENTLE, "feather", () -> new SoundFx.Pitch(1.85f)));
        add(Card.of("deep_voice", SOUND, GENTLE, "anvil", () -> new SoundFx.Pitch(0.55f)));
        add(Card.of("orchestra", SOUND, GENTLE, "note_block", SoundFx.Orchestra::new));
        add(Card.of("zoo", SOUND, STRANGE, "lead", SoundFx.Zoo::new));
        add(Card.of("paranoia", SOUND, NIGHTMARE, "creeper_head", SoundFx.Paranoia::new));
        add(Card.of("echo", SOUND, GENTLE, "echo_shard", SoundFx.Echo::new));
        add(Card.of("dead_silence", SOUND, NIGHTMARE, "white_wool", SoundFx.DeadSilence::new));
        add(Card.of("bad_dubbing", SOUND, STRANGE, "jukebox", SoundFx.BadDubbing::new));
        add(Card.of("crossed_ears", SOUND, GENTLE, "goat_horn", NewEffects.CrossedEars::new));
        add(Card.of("warped_vinyl", SOUND, GENTLE, "music_disc_wait", NewEffects.WarpedVinyl::new));
        add(Card.of("elevator_music", SOUND, GENTLE, "bell", NewEffects.ElevatorMusic::new));
        // 2.2
        add(Card.of("muffled", SOUND, GENTLE, "gray_wool", WorldEffects.Muffled::new));
    }

    public static Card get(String id) {
        return BY_ID.get(id);
    }

    /** Carte ou malus. */
    public static Card any(String id) {
        Card c = BY_ID.get(id);
        return c != null ? c : Curses.get(id);
    }

    public static List<Card> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static int count() {
        return BY_ID.size();
    }

    /** Cartes jouables avec la configuration actuelle. */
    public static List<Card> enabled() {
        List<? extends String> disabled = FFConfig.DISABLED_CARDS.get();
        boolean noMotion = FFConfig.NO_MOTION_SICKNESS.get();
        List<Card> out = new ArrayList<>();
        for (Card c : BY_ID.values()) {
            if (disabled.contains(c.id)) continue;
            if (noMotion && c.motion) continue;
            out.add(c);
        }
        return out;
    }

    public static List<Card> ofCategory(Category cat) {
        if (cat == Category.CURSE) return Curses.all();
        List<Card> out = new ArrayList<>();
        for (Card c : BY_ID.values()) if (c.category == cat) out.add(c);
        return out;
    }
}
