# Fate Flip — consignes pour Claude

- **Toujours pousser directement sur `main`** (pas de branche `dev`, pas de PR), à la demande de Loup0007A.
- Avant de pousser : le code doit compiler. Ensuite, vérifier le résultat de la CI : logs et captures d'écran du test client sur la branche `ci-logs` (`main.clienttest.log`, `screenshots/main/`).
- Après chaque push sur `main`, la CI ajoute un commit « Jar compilé … » : faire `git fetch` + rebase avant le push suivant.
- Nouvelle version = augmenter `mod_version` dans `gradle.properties` + ajouter une section `## x.y.z` en haut de `CHANGELOG.md` (elle sert de notes pour la release GitHub et pour CurseForge, envoi automatique).
- Toute nouvelle carte : l'enregistrer dans `card/Cards.java`, l'ajouter dans `tools/lang.py` (FR + EN) puis régénérer les fichiers de langue, et mettre à jour les listes du README et de `curseforge/description.md`.
- Les effets d'écran sont dans `assets/fateflip/shaders/core/screen_fx.fsh` (numéros de mode recopiés dans `render/FxPass.java`) ; `tools/fxpreview.py` les rend hors jeu.
- Les cartes ne changent jamais la partie (pas de monstres, pas d'objets) : seulement ce qu'on voit, entend, et la façon dont les commandes répondent. Le mod reste 100 % client.
