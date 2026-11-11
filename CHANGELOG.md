# Changelog

## 2.5.1

- **Forge 1.20.1**: the 28 screen effects that only change the world (Noir, VHS, Thermal Vision, 3D Glasses, Tunnel Vision, Comic Book…) no longer turn the screen black with Fancy graphics. The HUD vignette was drawn with the wrong blending right after the effect.
- Forge 1.20.1 : les 28 effets d'écran « monde » (Noir, VHS, Vision thermique, Lunettes 3D…) ne donnent plus un écran noir en graphismes « Détaillés » (le vignettage du HUD était mal mélangé juste après l'effet).

## 2.5.0

- **New: Minecraft 26.3 (NeoForge 26.3)** — `fateflip-2.5.0-neoforge-26.3.jar`, Java 25. Every card, curse, power and the Grimoire work, with both the **OpenGL** and the new **Vulkan** renderer (both tested in-game by the CI).
- Grimoire: long card descriptions no longer run into the footer.
- Nouveau : un jar pour Minecraft 26.3 (NeoForge, OpenGL et Vulkan) ; grimoire : le texte ne déborde plus.

## 2.4.0

- **More versions**: Fate Flip now comes in several jars, built from the same code and each tested in-game by the CI:
  - **Forge 1.21 – 1.21.1** (`fateflip-2.4.0-forge-1.21.1.jar`)
  - **NeoForge 1.21 – 1.21.1** (`fateflip-2.4.0-neoforge-1.21.1.jar`)
  - **Forge 1.20.1** (`fateflip-2.4.0-forge-1.20.1.jar`)
- All 100 cards, curses, fate powers and the Grimoire are available in every jar.
- X-Ray and Hallucinations now draw correctly whatever the loader.
- Plus de versions : un jar Forge 1.21–1.21.1, un jar NeoForge 1.21–1.21.1 et un jar Forge 1.20.1, testés en jeu.

## 2.2.0

- **New: permanent curses.** On the **3rd flip of a single draw** (after two AGAINs), the card has a **1 in 6** chance to be **cursed**: it can't be burned and its small curse stays active **forever** (until `/fateflip reset`). 8 curses: Lightning Rod, Short Arms, Side Stitch, Butterfingers, Fear of the Dark, Hay Fever, Lingering Mist, Trick Knee. Active curses show under the HUD reminder and in a new **Curses** tab of the Grimoire. Config: `curses`, `curseChance`; commands `/fateflip curse list|add|remove|clear`.
- **12 new cards — 100 in total**: X-Ray (like the cheat: ores glow through the rock), Transparency Off, Sun Intolerance, Fly Vision, Blood Sky, Render Distance: 3, Screamers, Hallucinations (fake villages that fade as you approach), No Animations, Piece of Cake (every mob is a cake), Top-Down (2D view from above with a free mouse pointer, every action still works), Muffled. The old "X-Ray" card is now called **Radiograph**.
- **Sound intro**: on first launch, a short screen asks you to turn the sound up (test sound, sound settings); a reminder appears if your volume is at 0.
- **Inventory fixes**: Amnesia now fully hides the hotbar (item counts no longer show through) and also in the inventory; Upside-Down HUD and Screensaver now apply to inventory screens too (the mouse follows); Hotbar Chaos also scrambles the 1-9 swap keys in inventories; the card reminder moves out of the way when the hotbar goes to the top.
- Nouveau : malus permanents (3e retournement, 1 chance sur 6), 12 nouvelles cartes (100 au total), intro « montez le son », corrections du HUD de l'inventaire.

## 2.1.0

- **21 new cards** — the deck grows from 67 to **88 cards**.
- Space: Video Wall, Droste Effect, Ripple, Glitch.
- Vision: Shadow Play, Stained Glass, Pointillism, X-Ray, Hologram, Miniature (tilt-shift).
- Mind: Look Behind, Upside-Down HUD, Absurd Achievements, Zero Gravity, Disco Night.
- Controls: Ice Rink, Input Lag, Leapfrog.
- Hearing: Crossed Ears, Warped Vinyl, Elevator Music.
- 21 nouvelles cartes (88 au total), traduites en français et en anglais.

## 2.0.0

- **New: Fate Powers / Atouts du destin.** Daring pays off: every card you keep earns **fate shards** ✦ (Gentle +1, Strange +2, Nightmare +3, and +1 for every AGAIN you dared during that draw).
- Spend shards during a draw on **4 powers**, each once per draw:
  - **Clairvoyance** (3 ✦): card backs reveal their family and intensity.
  - **Reprieve** (2 ✦): one extra flip.
  - **Phoenix** (4 ✦): revive a burned card and take it back.
  - **Half Measure** (5 ✦): the card you keep only lasts half as long.
- Shard counter on the draw screen and in the Grimoire, "+N ✦" reward animation, "keep this card: +N" hint, ½ badge on the HUD reminder.
- New config option `fatePowers` (default true) and client command `/fateflip shards <n>`.
- Nouveau : Atouts du destin, éclats gagnés en prenant des risques, 4 atouts utilisables pendant les tirages.

## 1.0.0

- First release / Première version.
- A draw every 2 minutes: 10 face-down cards, up to 3 flips, the last one always applies (stop or again?).
- Cards are always never-seen (or never-played) until the whole deck has been played.
- 67 cards in 5 families: Space, Vision, Mind, Controls, Hearing — with 3 intensity levels.
- Live preview of screen effects on the revealed card.
- Grimoire (K key) with your whole collection.
- Controls screen sealed while a Controls card is active (anti-cheat).
- 100% client-side: works in singleplayer and on any server.
- English and French.
