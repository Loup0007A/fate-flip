package com.fateflip.debug;

import com.fateflip.FateFlip;
import com.fateflip.card.Card;
import com.fateflip.card.Cards;
import com.fateflip.card.Category;
import com.fateflip.card.Collection;
import com.fateflip.card.Power;
import com.fateflip.client.Fate;
import com.fateflip.client.gui.DrawScreen;
import com.fateflip.client.gui.GrimoireScreen;
import com.fateflip.render.PostFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
@Mod.EventBusSubscriber(modid = FateFlip.MOD_ID, value = Dist.CLIENT)
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
        step("annonce", 6, () -> {
            check(Fate.current() != null, "une carte est active après le tirage");
            check(Fate.isHalved(), "Demi-mesure appliquée");
            check(Collection.get().shards() > 6, "éclats gagnés en gardant la carte (" + Collection.get().shards() + ")");
            shot("07_announce");
        });
        step("hud", 70, () -> shot("08_hud"));
        for (Card card : Cards.all()) {
            boolean visual = card.category != Category.SOUND && card.category != Category.CONTROL;
            step("play " + card.id, 2, () -> Fate.play(card, false));
            if (visual) {
                step("shot " + card.id, 46, () -> {
                    check(Fate.current() != null && Fate.current().card == card, "carte " + card.id + " active");
                    shot("fx_" + card.id);
                });
            } else {
                step("check " + card.id, 20, () -> check(Fate.current() != null && Fate.current().card == card, "carte " + card.id + " active"));
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
        step("fin effets", 2, Fate::stopEffect);
        step("grimoire", 2, () -> mc().setScreen(new GrimoireScreen(null)));
        step("grimoire-capture", 20, () -> shot("09_grimoire"));
        step("grimoire-vision", 2, () -> {
            if (mc().screen instanceof GrimoireScreen gs) gs.setTab(Category.VISION);
        });
        step("grimoire-vision-capture", 12, () -> shot("10_grimoire_vision"));
        step("fin", 10, () -> {
            mc().setScreen(null);
            check(!PostFx.isBroken(), "shader d'écran opérationnel");
            check(Fate.failures() == 0, "aucune carte n'a planté (" + Fate.failures() + ")");
            FateFlip.LOGGER.info("[CLIENTTEST] RESULT: {}", failures == 0 ? "OK" : "FAIL " + failures);
            mc().stop();
        });
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;
        inGame++;
        if (inGame < 160) return;
        if (!built) {
            build();
            FateFlip.LOGGER.info("[CLIENTTEST] début ({} étapes)", STEPS.size());
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
