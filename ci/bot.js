// Gerçek oyuncu testi (mineflayer). Kullanım: node bot.js <mc-sürümü> <port>
// Doğrular: ışınlanma (merkez/spawn), void platformu, setspawn kuralları, dolu dünyayı silme
// engeli, kare ve dikdörtgen sınır, /tp ile sınır dışına çıkma engeli, ölünce dünya spawn'ında doğma.
'use strict';
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');

const [, , version, port] = process.argv;
const messages = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: Number(port),
  username: 'DunyaBot',
  version,
  auth: 'offline',
  checkTimeoutInterval: 120000,
});

bot.on('messagestr', (m) => {
  messages.push(m);
  console.log('CHAT:', m);
});
bot.on('kicked', (r) => {
  console.log('BOT_FAIL kicked:', typeof r === 'string' ? r : JSON.stringify(r));
  process.exit(2);
});
bot.on('error', (e) => {
  console.log('BOT_FAIL error:', (e && e.stack) || e);
  process.exit(3);
});

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

function pos() {
  const p = bot.entity.position;
  return `(${p.x.toFixed(2)}, ${p.y.toFixed(2)}, ${p.z.toFixed(2)})`;
}

async function until(fn, ms, what) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    if (fn()) return;
    await sleep(100);
  }
  throw new Error('zaman aşımı: ' + what);
}

function onceEvent(name, ms) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => {
      bot.removeListener(name, h);
      reject(new Error('olay gelmedi: ' + name));
    }, ms);
    const h = (...a) => {
      clearTimeout(t);
      resolve(a);
    };
    bot.once(name, h);
  });
}

async function cmd(command, expect) {
  const idx = messages.length;
  console.log('>>', command);
  bot.chat(command);
  await until(() => messages.slice(idx).some((m) => m.includes(expect)), 15000, `"${expect}" (${command})`);
}

async function tp(command, expect) {
  const moved = onceEvent('forcedMove', 15000);
  await cmd(command, expect);
  await moved;
  await sleep(1200);
}

async function walk(target, ms) {
  await bot.lookAt(target, true);
  bot.setControlState('forward', true);
  bot.setControlState('sprint', true);
  await sleep(ms);
  bot.clearControlStates();
  await sleep(800);
}

async function run() {
  await sleep(2500);

  // 1) Void dünyası: merkeze ve platformun üstüne ışınlanma
  await tp('/dunya isinlan TestVoid', 'Işınlandın');
  let p = bot.entity.position;
  assert(Math.abs(p.x - 0.5) < 1.1 && Math.abs(p.z - 0.5) < 1.1 && Math.abs(p.y - 65) < 1.6, 'void merkez yanlış ' + pos());
  await sleep(2000);
  assert(bot.entity.position.y > 63, 'void platformundan düştü ' + pos());

  // 2) setspawn (doğru dünya)
  await cmd('/dunya setspawn TestVoid', 'Spawn noktası ayarlandı');
  const spawn = bot.entity.position.clone();

  // 3) setspawn (yanlış dünya) reddedilmeli
  await cmd('/dunya setspawn TestEnd', 'dünyanın içindeyken');

  // 4) End flat: yüzeye ışınlanma, dolu dünyayı silme engeli, kare sınır
  await tp('/dunya isinlan TestEnd', 'Işınlandın');
  p = bot.entity.position;
  assert(Math.abs(p.y - 5) < 1.6, 'end flat yüzeyi yanlış ' + pos());
  await cmd('/dunya sil TestEnd', 'Silme iptal edildi');
  await walk(new Vec3(1000, p.y + 1.6, p.z), 12000);
  p = bot.entity.position;
  assert(p.x <= 30.7, 'kare sınır aşıldı ' + pos());
  assert(p.x >= 18, 'bot yürüyemedi ' + pos());

  // 5) Dikdörtgen dünya (40x12): kısa kenar eklenti tarafından korunur
  await tp('/dunya isinlan TestRect', 'Işınlandın');
  p = bot.entity.position;
  await walk(new Vec3(p.x, p.y + 1.6, 1000), 8000);
  p = bot.entity.position;
  assert(p.z <= 12.7, 'dikdörtgen sınır aşıldı ' + pos());
  assert(p.z >= 6, 'bot yürüyemedi (z) ' + pos());

  // 6) /tp ile sınır dışına ışınlanma içeri çekilmeli
  const moved = onceEvent('forcedMove', 15000);
  bot.chat('/tp DunyaBot 0 20 300');
  await moved;
  await sleep(1500);
  p = bot.entity.position;
  assert(Math.abs(p.z) <= 12.7 && Math.abs(p.x) <= 40.7, '/tp sınır dışına çıkardı ' + pos());

  // 7) Spawn'a ışınlanma (setspawn varsa tam oraya)
  await tp('/dunya isinlan TestVoid', 'Işınlandın');
  assert(bot.entity.position.distanceTo(spawn) < 1.2, 'setspawn noktasına ışınlanmadı ' + pos());

  // 8) Ölüm: aynı dünyanın spawn noktasında doğmalı
  const respawned = onceEvent('spawn', 20000);
  bot.chat('/kill DunyaBot');
  await respawned;
  await sleep(2500);
  assert(bot.entity.position.distanceTo(spawn) < 1.6, 'ölünce dünya spawn noktasında doğmadı ' + pos());
}

bot.once('spawn', async () => {
  try {
    await run();
    console.log('BOT_OK');
    bot.quit();
    setTimeout(() => process.exit(0), 800);
  } catch (e) {
    console.log('BOT_FAIL', e.message, 'konum:', bot.entity ? pos() : '?');
    process.exit(1);
  }
});

setTimeout(() => {
  console.log('BOT_FAIL genel zaman aşımı');
  process.exit(4);
}, 240000);
