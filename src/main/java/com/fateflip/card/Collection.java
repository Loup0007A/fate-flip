package com.fateflip.card;

import com.fateflip.FateFlip;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * La collection du joueur : quelles cartes ont été vues (retournées) et jouées.
 * Enregistrée dans {@code .minecraft/fateflip/collection.json}, commune à tous les mondes.
 *
 * <p>Un « cycle » regroupe des tirages : dans un cycle, une carte proposée est
 * toujours soit jamais vue, soit jamais jouée. Quand ce n'est plus possible,
 * un nouveau cycle commence.</p>
 */
public final class Collection {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Collection instance;

    // Totaux sur toute la vie de la collection (pour le Grimoire)
    private final Map<String, Integer> seenTotal = new HashMap<>();
    private final Map<String, Integer> usedTotal = new HashMap<>();
    // Cycle en cours
    private final Map<String, Boolean> cycleSeen = new HashMap<>();
    private final Map<String, Boolean> cycleUsed = new HashMap<>();
    private int cycle = 1;
    private int draws;
    private int flips;
    // V2 : éclats du destin et atouts
    private int shards;
    private int shardsEarned;
    private final Map<String, Integer> powersUsed = new HashMap<>();

    public static Collection get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("fateflip").resolve("collection.json");
    }

    private static Collection load() {
        Collection c = new Collection();
        Path f = file();
        if (!Files.exists(f)) return c;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject o = GSON.fromJson(r, JsonObject.class);
            c.cycle = o.has("cycle") ? o.get("cycle").getAsInt() : 1;
            c.draws = o.has("draws") ? o.get("draws").getAsInt() : 0;
            c.flips = o.has("flips") ? o.get("flips").getAsInt() : 0;
            c.shards = o.has("shards") ? o.get("shards").getAsInt() : 0;
            c.shardsEarned = o.has("shardsEarned") ? o.get("shardsEarned").getAsInt() : 0;
            readCounts(o, "powersUsed", c.powersUsed);
            readCounts(o, "seenTotal", c.seenTotal);
            readCounts(o, "usedTotal", c.usedTotal);
            readFlags(o, "cycleSeen", c.cycleSeen);
            readFlags(o, "cycleUsed", c.cycleUsed);
        } catch (Exception e) {
            FateFlip.LOGGER.warn("Collection Fate Flip illisible, on repart de zéro", e);
        }
        return c;
    }

    private static void readCounts(JsonObject o, String key, Map<String, Integer> into) {
        if (!o.has(key)) return;
        o.getAsJsonObject(key).entrySet().forEach(e -> into.put(e.getKey(), e.getValue().getAsInt()));
    }

    private static void readFlags(JsonObject o, String key, Map<String, Boolean> into) {
        if (!o.has(key)) return;
        o.getAsJsonArray(key).forEach(e -> into.put(e.getAsString(), true));
    }

    public void save() {
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        o.addProperty("cycle", cycle);
        o.addProperty("draws", draws);
        o.addProperty("flips", flips);
        o.addProperty("shards", shards);
        o.addProperty("shardsEarned", shardsEarned);
        JsonObject pu = new JsonObject();
        powersUsed.forEach(pu::addProperty);
        o.add("powersUsed", pu);
        JsonObject st = new JsonObject();
        seenTotal.forEach(st::addProperty);
        o.add("seenTotal", st);
        JsonObject ut = new JsonObject();
        usedTotal.forEach(ut::addProperty);
        o.add("usedTotal", ut);
        com.google.gson.JsonArray cs = new com.google.gson.JsonArray();
        cycleSeen.keySet().forEach(cs::add);
        o.add("cycleSeen", cs);
        com.google.gson.JsonArray cu = new com.google.gson.JsonArray();
        cycleUsed.keySet().forEach(cu::add);
        o.add("cycleUsed", cu);
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("collection.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(o, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            FateFlip.LOGGER.warn("Impossible d'enregistrer la collection Fate Flip", e);
        }
    }

    /**
     * Tire une main de cartes toutes différentes : d'abord des cartes jamais vues
     * pendant le cycle, puis des cartes vues mais jamais jouées ; si cela ne suffit
     * pas, un nouveau cycle commence.
     */
    public List<Card> dealHand(int size, Random random) {
        List<Card> pool = Cards.enabled();
        if (pool.isEmpty()) pool = Cards.all();
        size = Math.min(size, pool.size());

        List<Card> unseen = new ArrayList<>(), seenUnused = new ArrayList<>();
        for (Card c : pool) {
            if (!cycleSeen.containsKey(c.id)) unseen.add(c);
            else if (!cycleUsed.containsKey(c.id)) seenUnused.add(c);
        }
        java.util.Collections.shuffle(unseen, random);
        java.util.Collections.shuffle(seenUnused, random);
        List<Card> hand = new ArrayList<>();
        for (Card c : unseen) if (hand.size() < size) hand.add(c);
        for (Card c : seenUnused) if (hand.size() < size) hand.add(c);

        if (hand.size() < size) {
            // Toutes les cartes ont été jouées : nouveau cycle, la main est complétée par des cartes « neuves ».
            cycle++;
            cycleSeen.clear();
            cycleUsed.clear();
            List<Card> rest = new ArrayList<>(pool);
            rest.removeAll(hand);
            java.util.Collections.shuffle(rest, random);
            for (Card c : rest) if (hand.size() < size) hand.add(c);
            FateFlip.LOGGER.info("Fate Flip : toutes les cartes ont été jouées, cycle {} commence", cycle);
        }
        java.util.Collections.shuffle(hand, random);
        draws++;
        return hand;
    }

    /** Une carte vient d'être retournée. @return true si c'est la toute première fois qu'on la voit. */
    public boolean markSeen(Card card) {
        flips++;
        cycleSeen.put(card.id, true);
        int before = seenTotal.getOrDefault(card.id, 0);
        seenTotal.put(card.id, before + 1);
        return before == 0;
    }

    public void markUsed(Card card) {
        cycleSeen.put(card.id, true);
        cycleUsed.put(card.id, true);
        usedTotal.merge(card.id, 1, Integer::sum);
    }

    public boolean discovered(Card card) {
        return seenTotal.getOrDefault(card.id, 0) > 0;
    }

    public int timesSeen(Card card) {
        return seenTotal.getOrDefault(card.id, 0);
    }

    public int timesUsed(Card card) {
        return usedTotal.getOrDefault(card.id, 0);
    }

    public int discoveredCount() {
        int n = 0;
        for (Card c : Cards.all()) if (discovered(c)) n++;
        return n;
    }

    public int shards() { return shards; }
    public int shardsEarned() { return shardsEarned; }

    public void addShards(int n) {
        shards = Math.max(0, shards + n);
        if (n > 0) shardsEarned += n;
    }

    /** Dépense des éclats pour un atout. @return false si le solde est insuffisant. */
    public boolean spend(Power power) {
        if (shards < power.cost) return false;
        shards -= power.cost;
        powersUsed.merge(power.key(), 1, Integer::sum);
        return true;
    }

    public int timesUsed(Power power) {
        return powersUsed.getOrDefault(power.key(), 0);
    }

    public int cycle() { return cycle; }
    public int draws() { return draws; }
    public int flips() { return flips; }

    public void reset() {
        seenTotal.clear();
        usedTotal.clear();
        cycleSeen.clear();
        cycleUsed.clear();
        cycle = 1;
        draws = 0;
        flips = 0;
        shards = 0;
        shardsEarned = 0;
        powersUsed.clear();
        save();
    }
}
