package com.fateflip.debug;

import com.fateflip.FateFlip;
import com.fateflip.card.Card;
import com.fateflip.card.Cards;
import com.fateflip.card.Category;
import com.fateflip.card.Collection;
import com.fateflip.card.Curses;
import com.fateflip.card.Power;
import com.fateflip.client.Fate;
import com.fateflip.client.gui.DrawScreen;
import com.fateflip.client.gui.GrimoireScreen;
import com.fateflip.client.gui.IntroScreen;
import com.fateflip.effect.CardEffect;
import com.fateflip.render.PostFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/**
 * Test visuel client (CI uniquement, -Dfateflip.clienttest=true) : déroule un tirage
 * complet, joue chaque carte et prend des captures d'écran.
 */
//#if mc26
//$$ // 26.x : enregistré par ClientBoot.onClientSetup (Minecraft doit exister avant l'initialisation de la classe)
//#else
@Mod.EventBusSubscriber(modid = FateFlip.MOD_ID, value = Dist.CLIENT)
//#endif
public final class ClientSelfTest {

    private ClientSelfTest() {}

    private static final boolean ENABLED = Boolean.getBoolean("fateflip.clienttest");

    private record Step(String label, BooleanSupplier ready, Runnable action, int minWait) {}

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean built;
    private static int inGame, waited;
    private static int failures;

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void step(String label, int wait, Runnable action) {
        STEPS.add(new Step(label, () -> true, action, wait));
    }

    private static void stepWhen(String label, BooleanSupplier ready, int wait, Runnable action) {
        STEPS.add(new Step(label, ready, action, wait));
    }

    private static DrawScreen draw() {
        return mc().screen instanceof DrawScreen d ? d : null;
    }

    private static void shot(String name) {
        Minecraft mc = mc();
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
            c -> FateFlip.LOGGER.info("[CLIENTTEST] capture {}", name));
    }

    private static void look(float yaw, float pitch) {
        var p = mc().player;
        if (p == null) return;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.yHeadRot = p.yHeadRotO = yaw;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            FateFlip.LOGGER.error("[CLIENTTEST] ÉCHEC : {}", what);
        } else {
            FateFlip.LOGGER.info("[CLIENTTEST] ok : {}", what);
        }
    }

    private static void build() {
        built = true;
        step("monde", 1, () -> {
            shot("00_world");
            Collection.get().addShards(20 - Collection.get().shards());
            Collection.get().clearCurses();
            mc().setScreen(new IntroScreen(null));
        });
        step("intro", 30, () -> {
            check(mc().screen instanceof IntroScreen, "intro sonore affichée");
            shot("00b_intro");
            mc().setScreen(null);
        });
        step("tirage", 1, Fate::forceDraw);
        stepWhen("table", () -> draw() != null && draw().isChoosing(), 10, () -> shot("01_draw_table"));
        step("survol", 1, () -> draw().testHover(2));
        step("survol-capture", 8, () -> shot("02_draw_hover"));
        // V2 : atouts
        step("clairvoyance", 1, () -> {
            draw().testHover(-1);
            draw().testPower(Power.CLAIRVOYANCE);
        });
        step("clairvoyance-capture", 16, () -> {
            check(Collection.get().shards() == 17, "Clairvoyance coûte 3 éclats (" + Collection.get().shards() + ")");
            shot("02b_clairvoyance");
        });
        step("info-atout", 1, () -> draw().testHoverPower(2));
        step("info-atout-capture", 6, () -> {
            shot("02c_power_tooltip");
            draw().testHoverPower(-1);
        });
        step("retourne", 1, () -> draw().testFlip(draw().testFind(true)));
        stepWhen("révélée", () -> draw() != null && draw().isRevealing(), 6, () -> shot("03_reveal_preview"));
        step("encore", 1, () -> draw().testEncore());
        stepWhen("brûlée", () -> draw() != null && draw().isChoosing(), 4, () -> shot("04_burned"));
        step("phénix", 1, () -> draw().testPower(Power.PHOENIX));
        stepWhen("phénix-révélée", () -> draw() != null && draw().isRevealing(), 4, () -> {
            check(Collection.get().shards() == 13, "Phénix coûte 4 éclats (" + Collection.get().shards() + ")");
            draw().testPower(Power.HALF_MEASURE);
        });
        step("demi-mesure-capture", 4, () -> shot("04b_phoenix_half"));
        step("encore-2", 1, () -> draw().testEncore());
        stepWhen("sursis", () -> draw() != null && draw().isChoosing(), 4, () -> draw().testPower(Power.REPRIEVE));
        step("sursis-capture", 6, () -> {
            check(Collection.get().shards() == 6, "Demi-mesure + Sursis coûtent 7 éclats (" + Collection.get().shards() + ")");
            shot("04c_reprieve");
        });
        step("retourne-2", 1, () -> draw().testFlip(draw().testFind(false)));
        stepWhen("révélée-2", () -> draw() != null && draw().isRevealing(), 6, () -> shot("05_reveal_card"));
        step("stop", 1, () -> draw().testStop());
        step("finale", 9, () -> shot("06_finale"));
        stepWhen("fermeture", () -> mc().screen == null, 14, () -> {});
        step("annonce", 26, () -> {
            check(Fate.current() != null, "une carte est active après le tirage");
            check(Fate.isHalved(), "Demi-mesure appliquée");
            check(Collection.get().shards() > 6, "éclats gagnés en gardant la carte (" + Collection.get().shards() + ")");
            shot("07_announce");
        });
        step("hud", 60, () -> shot("08_hud"));
        for (Card card : Cards.all()) {
            boolean visual = (card.category != Category.SOUND && card.category != Category.CONTROL) || card.id.equals("top_down");
            step("play " + card.id, 2, () -> Fate.play(card, false));
            if (card.id.equals("sun_intolerance")) {
                // on regarde le soleil (matin, plein est)
                step("vers le soleil", 2, () -> look(-90f, -62f));
            }
            if (visual) {
                step("déclenche " + card.id, 40, () -> {
                    CardEffect fx = Fate.current();
                    if (fx != null) fx.debugTrigger();
                });
                step("shot " + card.id, 4, () -> {
                    check(Fate.current() != null && Fate.current().card == card, "carte " + card.id + " active");
                    if (Fate.current() instanceof com.fateflip.effect.XRay x) FateFlip.LOGGER.info("[CLIENTTEST] x-ray : {} blocs repérés", x.count());
                    shot("fx_" + card.id);
                });
            } else {
                step("check " + card.id, 20, () -> check(Fate.current() != null && Fate.current().card == card, "carte " + card.id + " active"));
            }
            if (card.id.equals("sun_intolerance") || card.id.equals("top_down")) {
                step("regard normal", 1, () -> {
                    Fate.stopEffect();
                    look(0f, 12f);
                });
            }
            if (card.id.equals("scrambled_keys")) {
                step("verrou", 2, () -> mc().setScreen(new ControlsScreen(null, mc().options)));
                step("verrou-check", 3, () -> {
                    check(!(mc().screen instanceof ControlsScreen), "écran des contrôles verrouillé");
                    shot("fx_controls_locked");
                    mc().setScreen(null);
                });
            }
        }
        // 2.2 : l'inventaire suit les effets du HUD
        for (String id : new String[]{"amnesia", "upside_down_hud", "screensaver"}) {
            step("inv " + id, 2, () -> Fate.play(Cards.get(id), false));
            step("inv-open " + id, 30, () -> mc().setScreen(new InventoryScreen(mc().player)));
            step("inv-shot " + id, 10, () -> {
                check(mc().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, "inventaire ouvert avec " + id);
                shot("inv_" + id);
                mc().setScreen(null);
            });
        }
        step("fin effets", 2, Fate::stopEffect);
        step("grimoire", 2, () -> mc().setScreen(new GrimoireScreen(null)));
        step("grimoire-capture", 20, () -> shot("09_grimoire"));
        step("grimoire-vision", 2, () -> {
            if (mc().screen instanceof GrimoireScreen gs) gs.setTab(Category.VISION);
        });
        step("grimoire-vision-capture", 12, () -> shot("10_grimoire_vision"));
        // 2.2 : un tirage où la 3e carte est maudite
        step("malus-tirage", 2, () -> {
            mc().setScreen(null);
            DrawScreen.testForceCurse = true;
            Fate.forceDraw();
        });
        stepWhen("malus-table", () -> draw() != null && draw().isChoosing(), 6, () -> draw().testFlip(draw().testFind(false)));
        stepWhen("malus-1", () -> draw() != null && draw().isRevealing(), 2, () -> draw().testEncore());
        stepWhen("malus-2", () -> draw() != null && draw().isChoosing(), 2, () -> draw().testFlip(draw().testFind(false)));
        stepWhen("malus-3", () -> draw() != null && draw().isRevealing(), 2, () -> draw().testEncore());
        stepWhen("malus-4", () -> draw() != null && draw().isChoosing(), 2, () -> draw().testFlip(draw().testFind(false)));
        stepWhen("malus-révélé", () -> draw() != null && draw().isCursedReveal(), 8, () -> shot("11_curse_reveal"));
        step("malus-accepte", 1, () -> draw().testStop());
        stepWhen("malus-fermé", () -> mc().screen == null, 30, () -> {
            DrawScreen.testForceCurse = false;
            check(Collection.get().curses().size() == 1, "un malus permanent reçu (" + Collection.get().curses() + ")");
            check(Fate.activeCurses().size() == 1, "le malus est actif");
            shot("12_curse_announce");
        });
        step("tous-les-malus", 2, () -> {
            for (Card c : Curses.all()) Collection.get().addCurse(c);
            Fate.syncCurses();
        });
        step("foudre", 30, () -> {
            check(Fate.activeCurses().size() == Curses.count(), "tous les malus actifs (" + Fate.activeCurses().size() + ")");
            for (CardEffect fx : Fate.activeCurses()) if (fx.card.id.equals("lightning_rod")) fx.debugTrigger();
        });
        step("foudre-capture", 3, () -> shot("13_curse_lightning"));
        step("peur", 2, () -> {
            for (CardEffect fx : Fate.activeCurses()) if (fx.card.id.equals("nyctophobia")) fx.debugTrigger();
        });
        step("peur-capture", 30, () -> shot("14_curses_hud"));
        step("autres-malus", 2, () -> {
            for (CardEffect fx : Fate.activeCurses()) if (!fx.card.id.equals("nyctophobia")) fx.debugTrigger();
        });
        step("grimoire-malus", 40, () -> {
            mc().setScreen(new GrimoireScreen(null));
        });
        step("grimoire-malus-onglet", 2, () -> {
            if (mc().screen instanceof GrimoireScreen gs) gs.setTab(Category.CURSE);
        });
        step("grimoire-malus-capture", 12, () -> {
            shot("15_grimoire_curses");
            mc().setScreen(null);
        });
        step("malus-levés", 2, () -> {
            Collection.get().clearCurses();
            Collection.get().save();
            Fate.syncCurses();
            check(Fate.activeCurses().isEmpty(), "malus levés");
        });
        step("fin", 10, () -> {
            mc().setScreen(null);
            check(!PostFx.isBroken(), "shader d'écran opérationnel");
            check(Fate.failures() == 0, "aucune carte n'a planté (" + Fate.failures() + ")");
            FateFlip.LOGGER.info("[CLIENTTEST] RESULT: {}", failures == 0 ? "OK" : "FAIL " + failures);
            mc().stop();
        });
    }

    //#if mc1201
    //$$ @SubscribeEvent
    //$$ public static void onTick(TickEvent.ClientTickEvent event) {
    //$$     if (event.phase == TickEvent.Phase.END) tick();
    //$$ }
    //#else
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent.Post event) {
        tick();
    }
    //#endif

    private static void tick() {
        if (!ENABLED) return;
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;
        inGame++;
        if (inGame < 160) return;
        if (!built) {
            build();
            FateFlip.LOGGER.info("[CLIENTTEST] début ({} étapes, cible {})", STEPS.size(), com.fateflip.platform.Platform.TARGET);
        }
        Step s = STEPS.peek();
        if (s == null) return;
        waited++;
        if (waited < s.minWait || !s.ready.getAsBoolean()) {
            if (waited > 1200) {
                check(false, "délai dépassé à l'étape « " + s.label + " »");
                STEPS.poll();
                waited = 0;
            }
            return;
        }
        STEPS.poll();
        waited = 0;
        try {
            s.action.run();
        } catch (Throwable t) {
            check(false, "exception à l'étape « " + s.label + " » : " + t);
            FateFlip.LOGGER.error("[CLIENTTEST]", t);
        }
    }
}
