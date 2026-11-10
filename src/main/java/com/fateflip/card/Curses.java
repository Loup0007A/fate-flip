package com.fateflip.card;

import com.fateflip.effect.CurseEffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.fateflip.card.Category.CURSE;
import static com.fateflip.card.Severity.CURSED;

/**
 * Malus permanents (2.2). Ils ne sont jamais distribués dans la main : au troisième
 * retournement d'un même tirage, la carte retournée a une chance (1/6 par défaut)
 * d'être maudite. Un malus reste actif pour toujours (jusqu'à /fateflip reset).
 * Ce sont les seules cartes autorisées à changer un peu la partie.
 */
public final class Curses {

    private Curses() {}

    private static final Map<String, Card> BY_ID = new LinkedHashMap<>();

    private static void add(Card.Builder b) {
        Card c = b.build();
        BY_ID.put(c.id, c);
    }

    static {
        add(Card.of("lightning_rod", CURSE, CURSED, "lightning_rod", CurseEffects.LightningRod::new));
        add(Card.of("short_arms", CURSE, CURSED, "stick", CurseEffects.ShortArms::new));
        add(Card.of("side_stitch", CURSE, CURSED, "sugar", CurseEffects.SideStitch::new));
        add(Card.of("butterfingers", CURSE, CURSED, "slime_ball", CurseEffects.Butterfingers::new));
        add(Card.of("nyctophobia", CURSE, CURSED, "soul_lantern", CurseEffects.Nyctophobia::new));
        add(Card.of("hay_fever", CURSE, CURSED, "dandelion", CurseEffects.HayFever::new));
        add(Card.of("lingering_mist", CURSE, CURSED, "phantom_membrane", CurseEffects.LingeringMist::new));
        add(Card.of("trick_knee", CURSE, CURSED, "bone", CurseEffects.TrickKnee::new));
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

    /** Un malus que le joueur n'a pas encore, ou null s'il les a tous. */
    public static Card randomMissing(Random random) {
        List<Card> missing = new ArrayList<>();
        Collection col = Collection.get();
        for (Card c : BY_ID.values()) if (!col.hasCurse(c)) missing.add(c);
        return missing.isEmpty() ? null : missing.get(random.nextInt(missing.size()));
    }
}
