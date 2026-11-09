package com.fateflip.card;

import com.fateflip.config.FFConfig;
import com.fateflip.effect.CameraEffects;
import com.fateflip.effect.ControlEffects;
import com.fateflip.effect.EntityEffects;
import com.fateflip.effect.HudEffects;
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

/** Le jeu complet : 67 cartes. */
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

        // ── CONTRÔLES : la façon dont vos commandes sont lues ──
        add(Card.of("scrambled_keys", CONTROL, NIGHTMARE, "tripwire_hook", ControlEffects.ScrambledKeys::new));
        add(Card.of("inverted_mouse", CONTROL, NIGHTMARE, "recovery_compass", ControlEffects.InvertedMouse::new));
        add(Card.of("steadicam", CONTROL, GENTLE, "minecart", ControlEffects.Steadicam::new));
        add(Card.of("coffee", CONTROL, STRANGE, "cocoa_beans", ControlEffects.Coffee::new));
        add(Card.of("hotbar_chaos", CONTROL, STRANGE, "bundle", ControlEffects.HotbarChaos::new));
        add(Card.of("crab_walk", CONTROL, STRANGE, "turtle_scute", ControlEffects.CrabWalk::new));

        // ── OUÏE : ce que vous entendez ──
        add(Card.of("helium", SOUND, GENTLE, "feather", () -> new SoundFx.Pitch(1.85f)));
        add(Card.of("deep_voice", SOUND, GENTLE, "anvil", () -> new SoundFx.Pitch(0.55f)));
        add(Card.of("orchestra", SOUND, GENTLE, "note_block", SoundFx.Orchestra::new));
        add(Card.of("zoo", SOUND, STRANGE, "lead", SoundFx.Zoo::new));
        add(Card.of("paranoia", SOUND, NIGHTMARE, "creeper_head", SoundFx.Paranoia::new));
        add(Card.of("echo", SOUND, GENTLE, "echo_shard", SoundFx.Echo::new));
        add(Card.of("dead_silence", SOUND, NIGHTMARE, "white_wool", SoundFx.DeadSilence::new));
        add(Card.of("bad_dubbing", SOUND, STRANGE, "jukebox", SoundFx.BadDubbing::new));
    }

    public static Card get(String id) {
        return BY_ID.get(id);
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
        List<Card> out = new ArrayList<>();
        for (Card c : BY_ID.values()) if (c.category == cat) out.add(c);
        return out;
    }
}
