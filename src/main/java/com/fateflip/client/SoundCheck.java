package com.fateflip.client;

import com.fateflip.card.Collection;
import com.fateflip.config.FFConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

/**
 * 2.2 : Fate Flip se vit avec le son. Intro au premier lancement (voir IntroScreen)
 * et petit rappel en arrivant dans un monde si le son est coupé.
 */
public final class SoundCheck {

    private SoundCheck() {}

    private static final boolean TEST = Boolean.getBoolean("fateflip.clienttest");
    private static int inWorld;
    private static boolean warned;

    /** L'intro doit-elle remplacer l'écran titre ? */
    public static boolean wantsIntro() {
        try {
            return !TEST && FFConfig.SOUND_INTRO.get() && !Collection.get().introSeen();
        } catch (Throwable t) {
            return false; // configuration pas encore chargée : on réessaiera au prochain écran titre
        }
    }

    /** Volume effectif (général × catégorie) entre 0 et 1. */
    public static float volume(SoundSource source) {
        Minecraft mc = Minecraft.getInstance();
        float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
        return source == SoundSource.MASTER ? master : master * mc.options.getSoundSourceVolume(source);
    }

    public static boolean muted() {
        return volume(SoundSource.MASTER) < 0.01f;
    }

    static void tick() {
        if (warned || TEST || !FFConfig.SOUND_INTRO.get() || !FFConfig.ENABLED.get()) return;
        if (++inWorld < 100) return;
        warned = true;
        if (muted()) {
            Minecraft mc = Minecraft.getInstance();
            SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("fateflip.sound.muted.title"), Component.translatable("fateflip.sound.muted"));
        }
    }

    static void reset() {
        inWorld = 0;
        warned = false;
    }
}
