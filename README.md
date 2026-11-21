# Dunyalar

Gelişmiş çoklu dünya oluşturma ve yönetim eklentisi. Multiverse-Core benzeri; her dünya sunucu ana klasörüne kalıcı olarak kaydedilir.

**Uyumluluk:** Spigot / Paper **1.16.5 – 1.21.x** (Java 8 bytecode; Java 8–21 sunucularda çalışır).
Her sürüm CI'da gerçek sunucularda ve gerçek bir oyuncu botuyla uçtan uca test edilir:
Paper 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.4, 1.20.6, 1.21.4, 1.21.11 (asenkron + senkron ön yükleme) ve Spigot 1.16.5, 1.21.4, 1.21.11.

## Kurulum

1. [Releases](../../releases) sayfasından `Dunyalar-x.y.z.jar` dosyasını indir.
2. `plugins/` klasörüne at, sunucuyu başlat.
3. Ayarlar: `plugins/Dunyalar/config.yml` — değişiklikleri `/dünya reload` ile uygula.

## Komutlar

| Komut | Açıklama | Yetki |
|---|---|---|
| `/dünya oluştur <isim> <world\|nether\|end> <normal\|void\|flat> <XxZ>` | Dünya oluşturur | `dunyalar.olustur` |
| `/dünya sil <dünya>` | Dünyayı kalıcı siler. İçinde oyuncu varsa **iptal edilir** | `dunyalar.sil` |
| `/dünya setspawn <dünya>` | Bulunduğun yeri spawn yapar (sadece o dünyanın içindeyken) | `dunyalar.setspawn` |
| `/dünya ışınlan <dünya>` | Spawn'a, yoksa dünyanın güvenli merkezine ışınlar | `dunyalar.isinlan` |
| `/dünya liste` | Dünyaları listeler | `dunyalar.liste` |
| `/dünya reload` | config.yml ve worlds.yml'yi oyuna uygular | `dunyalar.reload` |

- Takma adlar: `/dunya`, `/dunyalar`; alt komutlarda `olustur`, `isinlan`, `tp` de çalışır.
- Tüm komutlarda tab tamamlama vardır. Boyut için örnek olarak her zaman `100x100` önerilir.
- Komutu görmek için `dunyalar.use`, alt komutlar için ilgili yetki gerekir (varsayılan: OP). Tümü: `dunyalar.*`.

### Örnekler

```
/dünya oluştur Elytra world void 1000x1000
/dünya oluştur Pvp nether flat 500x500
/dünya oluştur Deneme end normal 10x10
```

## Özellikler

- **Temalar:** `world`, `nether`, `end` — gökyüzü, sis, ortam sesleri ve müzik temaya göre gelir.
- **Tipler:**
  - `normal`: vanilla arazi.
  - `void`: tamamen boş; merkezde küçük güvenli platform (config). Sınır tüm yüksekliği kaplar, eklenti yükseklik kısıtı koymaz.
  - `flat`: 1 bedrock + 4 tema katmanı (world: toprak/çimen, nether: netherrack, end: end taşı) — config'den değiştirilebilir.
- **Boyut:** `XxZ`. Varsayılan `yaricap` modunda `1000x1000` merkezden her yöne 1000 blok demektir; `toplam` modunda toplam alandır (`olusturma.boyut-modu`).
- **Sınır:** Vanilla mavi dünya sınırı + dikdörtgen dünyalarda kısa kenarı koruyan hareket kontrolü. Ender incisi, koro meyvesi, elytra, araç ve `/tp` ile sınır dışına çıkılamaz.
- **Performans:** Vanilla'nın dünya açılışında yaptığı senkron spawn bölgesi üretimi eklenti dünyalarında atlanır; arazi TPS dostu ön yüklemeyle üretilir (`on-yukleme`).
- **Oluşturma ilerlemesi:** Chat'te adımlar ve `[||||||||||] %45 | 120/260 chunk | Kalan ~12 sn` biçiminde ilerleme. Arazi merkezden dışa doğru önceden üretilir (Spigot'ta TPS bütçeli, Paper'da asenkron).
- **Kalıcılık:** Dünyalar `plugins/Dunyalar/worlds.yml`'ye atomik yazılır, sunucu açılışında otomatik yüklenir. `worlds.yml` elle düzenlenip `/dünya reload` ile uygulanabilir.
- **Yeniden doğma:** Eklentinin bir dünyasında ölen oyuncu o dünyanın spawn noktasında doğar (yatak önceliği ayarlanabilir).
- **End:** Flat/void End dünyalarında ejderha savaşı ve çıkış portalı oluşmaz (config: `end.ejderha`).
- **Güvenli ışınlanma:** Lav, ateş, su, kaktüs vb. üstüne ışınlamaz; Nether tavanına çıkarmaz; gerekirse platform kurar.

## Derleme

```
mvn clean package
```
Çıktı: `target/Dunyalar-1.0.0.jar`. API uyumluluk kontrolü: `mvn -P spigot-1.21 compile`, `mvn -P paper-1.21 compile`.

## Not

Bir dünyanın inşa yüksekliği (ör. 1.16'da 0–255, 1.18+'da -64–319) Minecraft'ın boyut tipine bağlıdır ve hiçbir eklenti tarafından dünya başına değiştirilemez; void dünyalarda eklenti bu aralığın tamamını serbest bırakır.
