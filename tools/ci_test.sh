#!/bin/bash
# Test en jeu d'un jar : vrai serveur (mise en scène) + vrai client (portablemc, Xvfb, rendu logiciel)
# qui déroule un tirage et photographie chaque carte.
# Usage : tools/ci_test.sh <jar> <loader: forge|neoforge> <version du loader> <nom du test>
#   ex.   tools/ci_test.sh out/fateflip-2.3.0-forge-1.21.1.jar forge 1.21.1-52.0.7 forge-1.21.1
set -u
JAR="$(realpath "$1")"; LOADER="$2"; LV="$3"; NAME="$4"
OUT="$PWD/ci-out/$NAME"; mkdir -p "$OUT/screenshots"
W="$PWD/ci-work/$NAME"; rm -rf "$W"; mkdir -p "$W"; cd "$W"

case "$LOADER" in
  forge)    INSTALLER="https://maven.minecraftforge.net/net/minecraftforge/forge/${LV}/forge-${LV}-installer.jar" ;;
  neoforge) INSTALLER="https://maven.neoforged.net/releases/net/neoforged/neoforge/${LV}/neoforge-${LV}-installer.jar" ;;
  *) echo "loader inconnu : $LOADER"; exit 2 ;;
esac

# ---- Serveur ----
mkdir -p srv && cd srv
curl -sSfL -o installer.jar "$INSTALLER" || { echo "installeur introuvable : $INSTALLER"; exit 1; }
java -jar installer.jar --installServer . > installer.log 2>&1 || (tail -30 installer.log; exit 1)
echo "eula=true" > eula.txt
printf 'online-mode=false\nspawn-protection=0\nlevel-seed=fateflip\ngamemode=creative\nspawn-monsters=false\n' > server.properties
mkdir -p mods && cp "$JAR" mods/
printf -- '\n-Dfateflip.selftest=true\n' >> user_jvm_args.txt
chmod +x run.sh
(timeout 1700 ./run.sh nogui < /dev/null > "$OUT/selftest.log" 2>&1; echo "server exit $?" >> "$OUT/selftest.log") &
cd ..
for i in $(seq 1 150); do grep -q "SELFTEST\] READY" "$OUT/selftest.log" && break; sleep 4; done
grep -E "\[SELFTEST\]|Exception|ERROR" "$OUT/selftest.log" | head -50

# ---- Client ----
mkdir -p client/mods && cp "$JAR" client/mods/
printf 'onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\nsoundCategory_master:0.0\nnarrator:0\nguiScale:3\nskipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:8\n' > client/options.txt
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" timeout 1700 \
  portablemc --main-dir "$PWD/mcmain" start --mc-dir "$PWD/client" "${LOADER}::${LV}" \
  -u Tester --join-server 127.0.0.1 --resolution 1280x720 \
  --jvm-arg=-Xmx3G,-Dfateflip.clienttest=true > "$OUT/clienttest.log" 2>&1
echo "client exit $?"
grep -E "CLIENTTEST|FateFlip|Exception|ERROR|GL error|shader" "$OUT/clienttest.log" | head -150
grep -q "CLIENTTEST\] RESULT" "$OUT/clienttest.log" || tail -80 "$OUT/clienttest.log"
cp client/screenshots/*.png "$OUT/screenshots/" 2>/dev/null || true
ls "$OUT/screenshots" | wc -l
pkill -f "nogui" || true
wait
grep -q "\[SELFTEST\] READY" "$OUT/selftest.log" && grep -q "\[CLIENTTEST\] RESULT: OK" "$OUT/clienttest.log"
