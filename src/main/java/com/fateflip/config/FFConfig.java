package com.fateflip.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Configuration client : config/fateflip-client.toml */
public final class FFConfig {

    private FFConfig() {}

    public enum HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, HIDDEN }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue DRAW_INTERVAL;
    public static final ForgeConfigSpec.IntValue FIRST_DRAW_DELAY;
    public static final ForgeConfigSpec.IntValue EFFECT_DURATION;
    public static final ForgeConfigSpec.IntValue CHOICE_TIMEOUT;
    public static final ForgeConfigSpec.IntValue CARDS_PER_DRAW;
    public static final ForgeConfigSpec.IntValue MAX_FLIPS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_CARDS;
    public static final ForgeConfigSpec.BooleanValue REDUCE_FLASHING;
    public static final ForgeConfigSpec.BooleanValue NO_MOTION_SICKNESS;
    public static final ForgeConfigSpec.EnumValue<HudCorner> HUD_CORNER;
    public static final ForgeConfigSpec.BooleanValue LOCK_CONTROLS;
    public static final ForgeConfigSpec.BooleanValue POWERS;
    public static final ForgeConfigSpec.BooleanValue CURSES;
    public static final ForgeConfigSpec.DoubleValue CURSE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue SOUND_INTRO;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Fate Flip — configuration client").push("general");
        ENABLED = b.comment("Active les tirages. false = le mod est en sommeil.")
            .define("enabled", true);
        b.pop();

        b.push("timing");
        DRAW_INTERVAL = b.comment("Secondes entre deux tirages (compté à partir du moment où une carte est jouée).")
            .defineInRange("drawIntervalSeconds", 120, 15, 3600);
        FIRST_DRAW_DELAY = b.comment("Secondes avant le premier tirage après l'arrivée dans un monde.")
            .defineInRange("firstDrawDelaySeconds", 30, 5, 3600);
        EFFECT_DURATION = b.comment("Durée maximale d'un effet, en secondes. Un effet s'arrête toujours au tirage suivant.")
            .defineInRange("effectDurationSeconds", 120, 5, 3600);
        CHOICE_TIMEOUT = b.comment("Secondes laissées pour choisir en multijoueur avant que le destin ne choisisse (0 = illimité).")
            .defineInRange("choiceTimeoutSeconds", 60, 0, 600);
        b.pop();

        b.push("draw");
        CARDS_PER_DRAW = b.comment("Nombre de cartes proposées à chaque tirage.")
            .defineInRange("cardsPerDraw", 10, 3, 10);
        MAX_FLIPS = b.comment("Nombre maximal de cartes retournées par tirage (stop ou encore).")
            .defineInRange("maxFlips", 3, 1, 5);
        POWERS = b.comment("V2 : éclats du destin et atouts (Clairvoyance, Sursis, Phénix, Demi-mesure) pendant les tirages.")
            .define("fatePowers", true);
        CURSES = b.comment("2.2 : malus permanents. Au 3e retournement d'un même tirage, la carte peut être maudite.",
                "false = plus aucun nouveau malus, et ceux déjà reçus sont mis en sommeil.")
            .define("curses", true);
        CURSE_CHANCE = b.comment("Probabilité qu'une 3e carte retournée soit un malus permanent (1/6 par défaut).")
            .defineInRange("curseChance", 1.0 / 6.0, 0.0, 1.0);
        DISABLED_CARDS = b.comment("Identifiants des cartes à retirer du jeu, ex. [\"scrambled_keys\", \"spyglass\"]. La liste est dans le Grimoire (touche K).")
            .defineListAllowEmpty("disabledCards", List.of(), o -> o instanceof String);
        b.pop();

        b.push("comfort");
        REDUCE_FLASHING = b.comment("Adoucit les effets qui scintillent (VHS, orage, glitchs).")
            .define("reduceFlashing", false);
        NO_MOTION_SICKNESS = b.comment("Retire les cartes qui font tanguer ou tourner la caméra (mal de mer, vertige, tonneau, respiration...).")
            .define("noMotionSickness", false);
        LOCK_CONTROLS = b.comment("Verrouille l'écran des contrôles tant qu'une carte « Contrôles » est active (anti-triche).")
            .define("lockControlsScreen", true);
        HUD_CORNER = b.comment("Position du rappel de la carte active.")
            .defineEnum("hudCorner", HudCorner.TOP_LEFT);
        SOUND_INTRO = b.comment("Affiche l'intro « montez le son » au premier lancement, et prévient si le son est coupé.")
            .define("soundIntro", true);
        b.pop();

        SPEC = b.build();
    }
}
