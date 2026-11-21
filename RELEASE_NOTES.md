## Dunyalar 1.0.0

Spigot / Paper **1.16.5 – 1.21.x** uyumlu çoklu dünya eklentisi.

**Komutlar:** `/dünya oluştur | sil | setspawn | ışınlan | liste | reload` (takma ad: `/dunya`)

- world / nether / end temaları; normal / void / flat tipleri; `XxZ` boyut ve mavi dünya sınırı
- Dikdörtgen sınır koruması, ender incisi / `/tp` / araç ile sınır dışına çıkma engeli
- Chat'te ilerleme çubuklu oluşturma, TPS dostu ön yükleme (Paper'da asenkron)
- İçinde oyuncu olan dünya silinemez; silme ve kayıt kalıcıdır
- setspawn sadece ilgili dünyada; ışınlanma spawn'a, yoksa güvenli merkeze
- Ölünce dünyanın spawn noktasında doğma
- Flat/void End'de ejderha savaşı yok (ayarlanabilir)
- Her şey `config.yml` üzerinden, `/dünya reload` ile canlı uygulanır

Her sürüm gerçek Paper sunucularında (1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.4, 1.20.6, 1.21.4, en güncel 1.21) bot oyuncuyla uçtan uca test edilmiştir.

**Kurulum:** `Dunyalar-1.0.0.jar` dosyasını `plugins/` klasörüne at.
