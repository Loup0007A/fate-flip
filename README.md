# 🃏 Fate Flip — Stop ou encore ? (Minecraft Forge 1.21.1)

> *Toutes les deux minutes, le destin vous tend dix cartes face cachée.*

Retournez-en une. Elle vous plaît ? **STOP**. Elle vous fait peur ? **ENCORE** : elle brûle et vous en retournez une autre… mais vous n'avez droit qu'à **trois retournements**, et **la dernière carte retournée s'applique toujours**, jusqu'au tirage suivant.

Les cartes ne touchent jamais à la partie (pas de monstres plus forts, pas d'objets en plus) : elles changent **ce que vous voyez, ce que vous entendez et la façon dont vos commandes répondent**. Écran découpé en trois et remélangé à chaque changement d'emplacement, taquin, vision thermique, écholocation, vue de dessus, X-ray, screamers, touches mélangées, sons remplacés par un orchestre, narrateur sarcastique…

Seule exception : les **malus permanents** (2.2), qui peuvent frapper quand on pousse sa chance jusqu'au troisième retournement.

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

## ☠ Nouveau en 2.2 : les malus permanents

Pousser sa chance a un prix. Au **troisième retournement d'un même tirage** (après deux ENCORE), la carte a **1 chance sur 6** de se révéler **maudite** : impossible de la brûler, et son malus reste actif **pour toujours** (jusqu'à `/fateflip reset`). Les malus sont petits, mais ce sont les seuls effets qui changent un peu la partie :

| Malus | Effet |
|---|---|
| ⚡ **Paratonnerre** | Par temps d'orage, la foudre tombe sans cesse autour de vous |
| 🦖 **Bras courts** | Portée réduite : −1,5 bloc pour les blocs, −1 pour les créatures |
| 🏃 **Point de côté** | Après 15 s de sprint, impossible de courir pendant 4 s |
| 🧼 **Mains moites** | Blessé, l'objet en main glisse parfois vers l'emplacement voisin |
| 🌑 **Peur du noir** | Dans l'obscurité, la vue se resserre et le cœur s'emballe |
| 🌼 **Rhume des foins** | Près des fleurs, vous éternuez : la visée sursaute, les yeux se ferment |
| 🌫 **Brume tenace** | Une brume pâle raccourcit votre vue partout |
| 🦴 **Genou capricieux** | De temps en temps, votre genou lâche et vous force à vous accroupir |

Les malus actifs s'affichent sous le rappel de carte, et dans l'onglet **Malus** du Grimoire. Réglages : `curses` (activer / mettre en sommeil) et `curseChance`.

## 📖 Le Grimoire (touche **K**)

Toute votre collection, famille par famille : cartes découvertes, nombre de fois retournées et jouées, et l'identifiant de chaque carte (pour la désactiver dans la configuration).

## 🌀 Les 100 cartes

| Famille | Cartes |
|---|---|
| **Espace** (16) | Triptyque, Miroir, Antipodes, Taquin, Kaléidoscope, Vertige, Format vertical, Fenêtre baladeuse, Fonte, Tourbillon, Œil de poisson, Écran brisé, *Mur d'écrans, Mise en abyme, Ricochet, Bug d'affichage* |
| **Vision** (32) | Film noir, Écarlate, Négatif, Vision thermique, 8 bits, Console de poche, Cassette VHS, Vieux téléviseur, Lunettes 3D, Ivresse, Vision tunnel, Carnet de croquis, Code source, Psychédélique, Myopie, Écholocation, Objectif mouillé, Bande dessinée, Cinémascope, Vision nocturne, Daltonisme, *Ombres chinoises, Vitrail, Pointillisme, Radiographie, Hologramme, Maquette*, **X-ray, Transparence désactivée, Intolérance au soleil, Vision de mouche, Ciel de sang** |
| **Esprit** (30) | Selfie, Tonneau, Longue-vue, Grand angle, Respiration, Hoquet, Mal de mer, Dinnerbone, Monde de papier, Proportions, Économiseur d'écran, Amnésie, Viseurs fantômes, Le Narrateur, Glyphes d'enchanteur, Paupières lourdes, Éclipse, Déluge, Purée de pois, Fête permanente, *Regard en arrière, Interface renversée, Succès absurdes, Apesanteur, Soirée disco*, **Distance de rendu : 3, Screamers, Hallucinations, Sans animation, C'est du gâteau** |
| **Contrôles** (10) | Touches mélangées, Souris inversée, Steadicam, Triple expresso, Barre capricieuse, Marche du crabe, *Patinoire, Latence, Saut accroupi*, **Vue de dessus** |
| **Ouïe** (12) | Hélium, Voix d'outre-tombe, Orchestre, Zoo, Paranoïa, Écho, Silence de mort, Mauvais doublage, *Oreilles croisées, Vinyle voilé, Musique d'ascenseur*, **Sourdine** |

*En italique : les 21 cartes ajoutées en 2.1. En gras : les 12 cartes ajoutées en 2.2 (l'ancienne « Rayons X » s'appelle désormais Radiographie).*

🔊 **Fate Flip se joue avec le son** : au premier lancement, une petite intro vous propose de tester et régler le volume, et un rappel s'affiche si le son est coupé.

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
| `curses` | true | Malus permanents (2.2) ; false = plus de nouveaux malus et ceux reçus en sommeil |
| `curseChance` | 0.1667 | Chance qu'une 3e carte retournée soit maudite |
| `soundIntro` | true | Intro « montez le son » et rappel si le son est coupé |

## ⌨️ Commandes (client)

`/fateflip draw` (tirage immédiat) · `/fateflip play <carte>` · `/fateflip stop` · `/fateflip grimoire` · `/fateflip list` · `/fateflip shards <n>` · `/fateflip curse list|add <malus>|remove <malus>|clear` · `/fateflip reset`

## 🔧 Développement

- Java 21, Forge 1.21.1-52.0.7, mappings Parchment, `reobf = false`.
- Tous les effets d'écran sont dans un seul shader : `assets/fateflip/shaders/core/screen_fx.fsh`. `tools/fxpreview.py` les rend hors jeu sur une scène de test.
- Les textures des cartes et le logo sont générés par `tools/textures.py` et `tools/logo.py`, les traductions par `tools/lang.py`.
- **CI GitHub Actions** (à chaque push sur `main`) : compilation, lancement d'un vrai serveur Forge puis d'un vrai client qui déroule un tirage et photographie chaque carte (logs et captures sur la branche `ci-logs`). Si tout passe : release GitHub, jar copié dans `releases/` et, pour chaque nouvelle version, envoi sur **CurseForge** (voir [`curseforge/SETUP.md`](curseforge/SETUP.md)).

Licence MIT.
