# 🃏 Fate Flip — Stop ou encore ? (Minecraft Forge 1.21.1)

> *Toutes les deux minutes, le destin vous tend dix cartes face cachée.*

Retournez-en une. Elle vous plaît ? **STOP**. Elle vous fait peur ? **ENCORE** : elle brûle et vous en retournez une autre… mais vous n'avez droit qu'à **trois retournements**, et **la dernière carte retournée s'applique toujours**, jusqu'au tirage suivant.

Les cartes ne touchent jamais à la partie (pas de monstres plus forts, pas d'objets en plus) : elles changent **ce que vous voyez, ce que vous entendez et la façon dont vos commandes répondent**. Écran découpé en trois et remélangé à chaque changement d'emplacement, taquin, vision thermique, écholocation, touches mélangées, sons remplacés par un orchestre, narrateur sarcastique…

**Mod 100 % client** : il fonctionne en solo et sur n'importe quel serveur, même vanilla.

## 📦 Installation

1. Installez **Minecraft 1.21.1** avec **Forge 52.0.x** ou plus récent.
2. Téléchargez le jar :
   - dans les [Releases](https://github.com/Loup0007A/fate-flip/releases), ou
   - directement dans le dossier [`releases/`](releases/) (`fateflip-latest.jar`).
3. Copiez-le dans le dossier `mods/` de votre installation (côté client uniquement).

## 🎴 Le tirage

- Toutes les **2 minutes** (réglable), l'écran de tirage s'ouvre : 10 cartes **toutes différentes**.
- Une carte proposée est **toujours jamais vue** (ou, quand vous les avez toutes vues, **jamais jouée**). Quand tout a été joué, un nouveau cycle commence.
- Chaque carte affiche sa **famille** et son **intensité** (1 à 3 gemmes : Doux, Étrange, Cauchemar) : c'est ce qui vous aide à décider entre stop et encore.
- Les cartes d'effet d'écran montrent un **aperçu en direct** de votre propre vue à travers l'effet.
- En solo, le jeu est en pause pendant le tirage. En multijoueur, vous avez 60 secondes, ensuite le destin choisit pour vous.
- Clavier : `1`…`0` pour retourner une carte, `Entrée` = stop, `Espace` = encore.

## ✦ Nouveau en V2 : les Atouts du destin

L'audace paie. Chaque carte gardée rapporte des **éclats du destin** ✦ : +1 (Doux), +2 (Étrange), +3 (Cauchemar), **+1 par ENCORE** tenté pendant le tirage. Ils sont conservés d'une partie à l'autre.

Pendant un tirage, dépensez-les sur la colonne d'**atouts** à gauche (chacun une fois par tirage) :

| Atout | Coût | Effet |
|---|---|---|
| 👁 **Clairvoyance** | 3 ✦ | Les dos des cartes révèlent leur famille et leur intensité |
| 🍀 **Sursis** | 2 ✦ | Un retournement de plus |
| 🔥 **Phénix** | 4 ✦ | Une carte brûlée renaît : vous pouvez la reprendre |
| ⏳ **Demi-mesure** | 5 ✦ | La carte gardée ne dure que la moitié du temps |

## 📖 Le Grimoire (touche **K**)

Toute votre collection, famille par famille : cartes découvertes, nombre de fois retournées et jouées, et l'identifiant de chaque carte (pour la désactiver dans la configuration).

## 🌀 Les 88 cartes

| Famille | Cartes |
|---|---|
| **Espace** (16) | Triptyque, Miroir, Antipodes, Taquin, Kaléidoscope, Vertige, Format vertical, Fenêtre baladeuse, Fonte, Tourbillon, Œil de poisson, Écran brisé, *Mur d'écrans, Mise en abyme, Ricochet, Bug d'affichage* |
| **Vision** (27) | Film noir, Écarlate, Négatif, Vision thermique, 8 bits, Console de poche, Cassette VHS, Vieux téléviseur, Lunettes 3D, Ivresse, Vision tunnel, Carnet de croquis, Code source, Psychédélique, Myopie, Écholocation, Objectif mouillé, Bande dessinée, Cinémascope, Vision nocturne, Daltonisme, *Ombres chinoises, Vitrail, Pointillisme, Rayons X, Hologramme, Maquette* |
| **Esprit** (25) | Selfie, Tonneau, Longue-vue, Grand angle, Respiration, Hoquet, Mal de mer, Dinnerbone, Monde de papier, Proportions, Économiseur d'écran, Amnésie, Viseurs fantômes, Le Narrateur, Glyphes d'enchanteur, Paupières lourdes, Éclipse, Déluge, Purée de pois, Fête permanente, *Regard en arrière, Interface renversée, Succès absurdes, Apesanteur, Soirée disco* |
| **Contrôles** (9) | Touches mélangées, Souris inversée, Steadicam, Triple expresso, Barre capricieuse, Marche du crabe, *Patinoire, Latence, Saut accroupi* |
| **Ouïe** (11) | Hélium, Voix d'outre-tombe, Orchestre, Zoo, Paranoïa, Écho, Silence de mort, Mauvais doublage, *Oreilles croisées, Vinyle voilé, Musique d'ascenseur* |

*En italique : les 21 cartes ajoutées en 2.1.*

**Anti-triche** : tant qu'une carte « Contrôles » est active, l'écran des contrôles (touches et souris) est **scellé**. Les cartes ne modifient jamais vos touches enregistrées : elles réinterprètent seulement vos entrées pendant l'effet, rien n'est sauvegardé.

## ⚙️ Configuration

`config/fateflip-client.toml` :

| Option | Défaut | Rôle |
|---|---|---|
| `drawIntervalSeconds` | 120 | Temps entre deux tirages |
| `firstDrawDelaySeconds` | 30 | Premier tirage après l'arrivée en jeu |
| `effectDurationSeconds` | 120 | Durée max d'un effet (il s'arrête toujours au tirage suivant) |
| `choiceTimeoutSeconds` | 60 | Temps pour choisir en multijoueur (0 = illimité) |
| `cardsPerDraw` / `maxFlips` | 10 / 3 | Taille de la main, nombre de retournements |
| `disabledCards` | `[]` | Cartes à retirer, ex. `["scrambled_keys"]` |
| `reduceFlashing` | false | Adoucit les scintillements (VHS, orage) |
| `noMotionSickness` | false | Retire les cartes qui font bouger la caméra |
| `lockControlsScreen` | true | Scelle l'écran des contrôles pendant les cartes « Contrôles » |
| `hudCorner` | TOP_LEFT | Position du rappel de carte (ou HIDDEN) |
| `fatePowers` | true | Éclats du destin et atouts (V2) |

## ⌨️ Commandes (client)

`/fateflip draw` (tirage immédiat) · `/fateflip play <carte>` · `/fateflip stop` · `/fateflip grimoire` · `/fateflip list` · `/fateflip shards <n>` · `/fateflip reset`

## 🔧 Développement

- Java 21, Forge 1.21.1-52.0.7, mappings Parchment, `reobf = false`.
- Tous les effets d'écran sont dans un seul shader : `assets/fateflip/shaders/core/screen_fx.fsh`. `tools/fxpreview.py` les rend hors jeu sur une scène de test.
- Les textures des cartes et le logo sont générés par `tools/textures.py` et `tools/logo.py`, les traductions par `tools/lang.py`.
- **CI GitHub Actions** : compilation, lancement d'un vrai serveur Forge, puis (hors `main`) d'un vrai client qui déroule un tirage et photographie chaque carte. Logs et captures sur la branche `ci-logs`. Sur `main` : release GitHub, jar copié dans `releases/` et, pour chaque nouvelle version, envoi sur **CurseForge** (voir [`curseforge/SETUP.md`](curseforge/SETUP.md)).

Licence MIT.
