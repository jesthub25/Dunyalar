#!/usr/bin/env bash
# Gerçek Paper sunucusunda uçtan uca test.
# Kullanım: ci/smoke.sh <paper-sürümü | latest-1.21> <eklenti.jar> [bot]
set -uo pipefail

VER="$1"
PLUGIN="$(readlink -f "$2")"
WITH_BOT="${3:-}"
CI_DIR="$(cd "$(dirname "$0")" && pwd)"
UA="Dunyalar-CI/1.0 (+https://github.com/jesthub25/Dunyalar)"
PORT=25599
WORK=""
SPID=""

# Günlük kaynağı: logs/latest.log (log4j, her zaman UTF-8). Henüz yoksa ham konsol çıktısı.
clean() {
  local src="$WORK/logs/latest.log"
  [ -f "$src" ] || src="$WORK/console.out"
  [ -f "$src" ] && sed -e 's/\x1b\[[0-9;?]*[A-Za-z]//g' "$src" > "$WORK/console.clean"
  return 0
}
fail() {
  echo "::error::[Paper $VER] $*"
  clean
  # Günlük sonunu tek bir annotation olarak da göster (API üzerinden okunabilir)
  if [ -f "$WORK/console.clean" ]; then
    echo "::error title=Gunluk $VER::$(tail -n 45 "$WORK/console.clean" | cut -c1-220 | sed 's/%/%25/g' | awk '{printf "%s%%0A", $0}')"
  fi
  echo "----- günlük (son 150 satır) -----"
  tail -n 150 "$WORK/console.clean" 2>/dev/null
  echo "----- ham konsol (son 40 satır) -----"
  tail -n 40 "$WORK/console.out" 2>/dev/null
  [ -n "$SPID" ] && kill -9 "$SPID" 2>/dev/null
  exit 1
}

if [ "$VER" = "latest-1.21" ]; then
  VER="$(curl -fsSL -A "$UA" https://fill.papermc.io/v3/projects/paper 2>/dev/null \
    | jq -r '.versions["1.21"][]? // empty' | grep -E '^1\.21(\.[0-9]+)?$' | sort -V | tail -n1)"
  if [ -z "$VER" ]; then
    VER="$(curl -fsSL -A "$UA" https://api.papermc.io/v2/projects/paper \
      | jq -r '.versions[]' | grep -E '^1\.21(\.[0-9]+)?$' | sort -V | tail -n1)"
  fi
  [ -n "$VER" ] || { echo "::error::En güncel 1.21 sürümü bulunamadı"; exit 1; }
  echo "En güncel 1.21 sürümü: $VER"
fi

WORK="$(pwd)/smoke-$VER"
rm -rf "$WORK"
mkdir -p "$WORK/plugins"
cd "$WORK" || exit 1
cp "$PLUGIN" plugins/

# ---------------------------------------------------------------- Paper indir
URL="$(curl -fsSL -A "$UA" "https://fill.papermc.io/v3/projects/paper/versions/$VER/builds/latest" 2>/dev/null \
  | jq -r '.downloads["server:default"].url // empty')"
if [ -z "$URL" ]; then
  B="$(curl -fsSL -A "$UA" "https://api.papermc.io/v2/projects/paper/versions/$VER/builds" | jq -r '.builds[-1].build')"
  URL="https://api.papermc.io/v2/projects/paper/versions/$VER/builds/$B/downloads/paper-$VER-$B.jar"
fi
echo "Paper: $URL"
curl -fsSL -A "$UA" -o paper.jar "$URL" || fail "Paper indirilemedi"

# ---------------------------------------------------------------- sunucu ayarları
echo "eula=true" > eula.txt
cat > server.properties <<EOF
online-mode=false
enforce-secure-profile=false
allow-nether=false
allow-flight=true
level-type=flat
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
server-port=$PORT
max-players=4
EOF
cat > bukkit.yml <<EOF
settings:
  allow-end: false
EOF
python3 - > ops.json <<'PY'
import hashlib, json, uuid
name = "DunyaBot"
h = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
h[6] = (h[6] & 0x0f) | 0x30
h[8] = (h[8] & 0x3f) | 0x80
print(json.dumps([{"uuid": str(uuid.UUID(bytes=bytes(h))), "name": name, "level": 4, "bypassesPlayerLimit": False}]))
PY

# ---------------------------------------------------------------- yardımcılar
wait_for() {
  local pat="$1" t="${2:-120}" i
  for ((i = 0; i < t; i++)); do
    clean
    grep -qF -- "$pat" console.clean && return 0
    kill -0 "$SPID" 2>/dev/null || { echo "sunucu beklenmedik şekilde kapandı"; return 1; }
    sleep 1
  done
  echo "zaman aşımı: $pat"
  return 1
}
count() { clean; grep -cF -- "$1" console.clean || true; }
send() { echo ">> $*"; echo "$*" >&3; }
expect() {   # expect "<komut>" "<beklenen çıktı>" [saniye]
  local before i t="${3:-60}"
  before="$(count "$2")"
  send "$1"
  for ((i = 0; i < t; i++)); do
    [ "$(count "$2")" -gt "$before" ] && return 0
    kill -0 "$SPID" 2>/dev/null || fail "Sunucu kapandı (komut: $1)"
    sleep 1
  done
  fail "Beklenen çıktı gelmedi: '$2' (komut: $1)"
}
load_chunk() {   # load_chunk <dünya> <x> <z> : chunk'ı geçici olarak yükle (/execute yüklü chunk ister)
  expect "execute in minecraft:$1 run forceload add $2 $3" "force load" 30
  sleep 2
}
unload_chunk() {
  expect "execute in minecraft:$1 run forceload remove $2 $3" "force load" 30
}
expect_block() {   # vanilla /execute ile blok/varlık doğrulama
  local p f n i
  p="$(count "Test passed")"; f="$(count "Test failed")"; n="$(count "not loaded")"
  send "$1"
  for ((i = 0; i < 30; i++)); do
    [ "$(count "Test passed")" -gt "$p" ] && return 0
    [ "$(count "Test failed")" -gt "$f" ] && fail "Blok testi başarısız: $1"
    [ "$(count "not loaded")" -gt "$n" ] && fail "Chunk yüklü değil: $1"
    sleep 1
  done
  fail "Blok testi yanıt vermedi: $1"
}
start_server() {
  rm -f cmd.fifo logs/latest.log console.clean
  mkfifo cmd.fifo
  : > console.out
  LANG=C.UTF-8 LC_ALL=C.UTF-8 java -Xms512M -Xmx2G -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
    -jar paper.jar nogui < cmd.fifo > console.out 2>&1 &
  SPID=$!
  exec 3> cmd.fifo
  wait_for "Done (" 600 || fail "Sunucu açılmadı"
}
stop_server() {
  local i
  send "stop"
  for ((i = 0; i < 180; i++)); do
    kill -0 "$SPID" 2>/dev/null || break
    sleep 1
  done
  kill -0 "$SPID" 2>/dev/null && fail "Sunucu kapanmadı"
  exec 3>&-
  clean
  cat console.clean >> all.log
  SPID=""
}

# ================================================================ 1. açılış
start_server
grep -qF "aktif. Yönetilen dünya" console.clean || fail "Eklenti etkinleşmedi"

expect "dunya olustur TestVoid world void 50x50" "hazır: TestVoid" 120
expect "dunya olustur TestFlat nether flat 40x30" "hazır: TestFlat" 120
expect "dunya olustur TestEnd end flat 30x30" "hazır: TestEnd" 120
expect "dunya olustur TestRect world flat 40x12" "hazır: TestRect" 120
expect "dunya olustur TestNormal world normal 24x24" "hazır: TestNormal" 300

# Doğrulama ve hata mesajları
expect "dunya olustur Kotu.Isim world void 10x10" "Geçersiz dünya adı"
expect "dunya olustur testvoid world void 10x10" "zaten var"
expect "dunya olustur X1 mars void 10x10" "Geçersiz tema"
expect "dunya olustur X1 world kare 10x10" "Geçersiz tip"
expect "dunya olustur X1 world void 10y10" "Geçersiz boyut"
expect "dunya olustur X1 world void 2x2" "en az"
expect "dunya olustur X1 world void" "Kullanım"
expect "dunya isinlan TestVoid" "sadece oyuncular"
expect "dunya liste" "TestNormal"

# Arazi doğrulama (vanilla /execute)
load_chunk testvoid 0 0
expect_block "execute in minecraft:testvoid if block 0 64 0 minecraft:bedrock"
expect_block "execute in minecraft:testvoid if block 1 64 -1 minecraft:bedrock"
expect_block "execute in minecraft:testvoid if block 10 30 10 minecraft:air"
expect_block "execute in minecraft:testvoid if block 10 0 10 minecraft:air"
unload_chunk testvoid 0 0
load_chunk testflat 0 0
expect_block "execute in minecraft:testflat if block 5 0 5 minecraft:bedrock"
expect_block "execute in minecraft:testflat if block 5 1 5 minecraft:netherrack"
expect_block "execute in minecraft:testflat if block 5 4 5 minecraft:netherrack"
expect_block "execute in minecraft:testflat if block 5 5 5 minecraft:air"
unload_chunk testflat 0 0
load_chunk testend 0 0
expect_block "execute in minecraft:testend if block 5 4 5 minecraft:end_stone"
expect_block "execute in minecraft:testend if block 5 5 5 minecraft:air"
unload_chunk testend 0 0
load_chunk testend 120 120
expect_block "execute in minecraft:testend if block 120 0 120 minecraft:end_portal"
expect_block "execute in minecraft:testend if block 120 1 120 minecraft:bedrock"
unload_chunk testend 120 120

expect "dunya reload" "Ayarlar yeniden yüklendi"
expect "dunya sil TestFlat" "Dünya silindi: TestFlat" 90
expect "dunya sil YokBoyle" "Dünya bulunamadı"

# ---------------------------------------------------------------- oyuncu testi
if [ -n "$WITH_BOT" ]; then
  if node -e "process.exit(require('$CI_DIR/node_modules/minecraft-data')('$VER') ? 0 : 1)" 2>/dev/null; then
    node "$CI_DIR/bot.js" "$VER" "$PORT" > bot.out 2>&1
    BOT_EXIT=$?
    cat bot.out
    [ "$BOT_EXIT" -eq 0 ] && grep -q "BOT_OK" bot.out || fail "Oyuncu (bot) testi başarısız"
    # Oyuncu End'e girdi: ejderha savaşı kapalı olduğu için ejderha ve çıkış portalı olmamalı
    sleep 3
    load_chunk testend 0 0
    expect_block "execute in minecraft:testend unless entity @e[type=minecraft:ender_dragon]"
    expect_block "execute in minecraft:testend if block 0 4 0 minecraft:end_stone"
    expect_block "execute in minecraft:testend if block 0 5 0 minecraft:air"
    unload_chunk testend 0 0
  else
    echo "::notice::mineflayer $VER sürümünü desteklemiyor, oyuncu testi atlandı."
    WITH_BOT=""
  fi
fi

stop_server

for w in TestVoid TestEnd TestRect TestNormal; do
  [ -d "$w" ] || fail "Dünya klasörü yok: $w"
done
[ ! -e TestFlat ] || fail "Silinen dünyanın klasörü duruyor: TestFlat"
N="$(grep -c 'tema:' plugins/Dunyalar/worlds.yml)"
[ "$N" = "4" ] || fail "worlds.yml kayıt sayısı 4 değil: $N"
if [ -n "$WITH_BOT" ]; then
  grep -q 'spawn:' plugins/Dunyalar/worlds.yml || fail "setspawn worlds.yml'ye kaydedilmedi"
fi

# ================================================================ 2. açılış (kalıcılık)
start_server
for w in TestVoid TestEnd TestRect TestNormal; do
  grep -qF "Dünya yüklendi: $w" console.clean || fail "Yeniden başlatmada yüklenmedi: $w"
done
expect "dunya liste" "TestRect"
load_chunk testvoid 0 0
expect_block "execute in minecraft:testvoid if block 0 64 0 minecraft:bedrock"
unload_chunk testvoid 0 0
expect "dunya sil TestRect" "Dünya silindi: TestRect" 90
stop_server
[ ! -e TestRect ] || fail "Silinen dünyanın klasörü duruyor: TestRect"

# ================================================================ hata taraması
if grep -nE "at com\.dunyalar\.|Could not pass event .*Dunyalar|Error occurred while (enabling|disabling) Dunyalar|(WARN|ERROR|SEVERE)\]?:? \[Dunyalar\]" all.log; then
  echo "----- tam günlük -----"
  cat all.log
  echo "::error::[Paper $VER] Günlükte eklentiye ait uyarı/hata bulundu (yukarıda)."
  exit 1
fi

echo "OK: Paper $VER uçtan uca test başarılı."
