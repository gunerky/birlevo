# Birlevo — görsel ve PDF çıktı hedefleri

7 Ekim 2026. Geliştirme kodunda yeni hedefler; sürüm numarası ve dondurulmuş 0.1.8 (9) yayın paketi değişmez.

| Araç | Giriş | Çıktı ve seçenekler |
| --- | --- | --- |
| Görsel dönüştürme | Mevcut JPEG / PNG / durağan WebP / HEIC / HEIF / AVIF ve cihazda çözülebilen BMP | JPEG / PNG / kayıplı WebP / BMP. Mevcut 1280 / 1920 / 2560 kenar seçenekleri korunur. JPEG ve WebP kalitesi 40–95; PNG ve BMP kalite kaydırıcısı içermez. |
| PDF okuyucu | Geçerli PDF'nin seçilmiş sayfası | PNG / JPEG / kayıplı WebP. Her hedefte 1280 / 2048 / 4096 kenar seçenekleri; JPEG ve WebP kalitesi 40–95. |
| Ortak aktarım | Üretilmiş BMP / PDF sayfa görseli | Açma / paylaşma, sunuma veya fotoğraftan PDF aracına özel dosya kopyası. Aktarım yeniden kodlama yapmaz. |

BMP, 54 bayt başlık ve dört bayt hizalı, alttan üste BGR satırlarıyla sıkıştırılmamış 24 bit renk yazar. Şeffaf alanlar beyazla birleştirilir. Dosya büyüyebilir; kaybolmuş kaynak ayrıntısını geri getirmez. Metadata / renk profili aktarımı ve tüm BMP varyantlarını okuma garantisi verilmez. PNG / WebP saydamlık davranışı ve JPEG'in beyaz zemini korunur. JPEG / WebP kayıplı kodlamadır; boyutlandırma ayrıntıyı azaltabilir. Kaynak dosya değiştirilmez. Görsel kaydetme / açma / paylaşma / sonraki araca aktarma gerçek bayt karşılaştırmasını ve sonuca özel büyük kopya onayını kullanır.

PDF sayfa görseli yeni bir içerik dönüşümüdür; PDF sıkıştırması olarak sunulmaz. Metin seçimi, bağlantı ve etkileşimli alanlar düz görselde korunmaz. PDF kopyalama baytları değiştirmez. Sayfa beyaz zemin üzerinde resmedilir. Hedef / kalite / çözünürlük değişince önceki çıktı geçersizleşir; ayarlar oturum metadata'sına kaydedilir, eski metadata PNG / 80 / 2048 varsayılanıyla okunur. Dosya seçici dönüşü beklenen çıktı ve özgün PDF URI'sini doğrular; eski sonuç adına yeni biçim kaydedilmez.

## Mimari ve doğrulama

`BmpWriter` Android'den bağımsız satır yazıcısıdır; yalnız bir piksel ve bir bayt satırı tutar, her satırda iptal kontrol eder. `ImageEncoder`, görsel motoru ve izole PDF worker'ının ortak yazıcısıdır; giriş bitmap'ini geri dönüştürmez. Görsel motoru çıktı MIME / boyutlarını ve bayt sınırını tekrar doğrular. PDF worker'ı seçilmiş hedefi doğrudan yazar; uygulama sürecinde önce tam PNG üretip tekrar çözme ve kodlama yapılmaz. Önizleme PNG yolu ayrıdır. İzole UID, yalnız okunan PDF descriptor'ı, sınırlı çıktı descriptor'ı ve mevcut zaman sınırı korunur. Yeni SDK / native motor / ağ izni eklenmez; FileProvider kimliği ve kök adları değişmez.

Yerel kanıtlar `output/development/image-targets/` altında tutulur. BMP başlık / hizalama / BGR / saydamlık / iptal / boyut taşması, gerçek Android çözme / yeniden içe alma, büyük kopya onayı ve özel aktarım sınanır. PDF üç hedef × üç çözünürlükte renk / yön / gerçek MIME kontrolü, dosya seçici MIME, kalite / hedef değişikliği, yeniden oluşturma ve geç dönen kaydetme sınanır. Mevcut görsel, PDF sayfa araçları ve ortak aktarım testleri kapsamla birlikte çalıştırılır.

Son doğrulama: debug uygulama / test APK'ları derlendi; 53 birim testi ve 29 ilgili Android testi geçti. Aynı PDF hedef UI testi ayrıca tablet, dar ekran / font 1.4 ve koyu modda geçti; üç tekrar yeni benzersiz test sayılmaz. Ekran görüntüleri incelendi, QA emülatörünün ayarları geri getirildi. Lint: sıfır hata, 39 uyarı. 22 dilde üç yeni anahtar ve güncellenen biçim placeholder'ları, Jev aracının 20 çevrimdışı testi ve APK izolasyonu doğrulandı. Depo kontrolü: 271 index dosyası. Graphify yerel AST güncellendi; 20 kısmi ayrıştırma uyarısı var, Kotlin derlemesi başarılıdır. Ayrıntılı özet: `output/development/image-targets/qa-summary.json`.

22 dilde metin ve placeholder kaynakları güncellenir; dil uzmanı onayı olarak yorumlanmaz. API 36 QA emülatörü fiziksel düşük donanım veya eski Android garantisi değildir. Release R8 ve üretici cihaz codec'leri sonraki yayın / fiziksel test adımında doğrulanır. Sonraki biçim işi HEVC / WebM encoder ve muxer denemesidir; başarılı çıktı örneği olmadan destek listesine eklenmez.

Biçim referansları: [BMP depolama](https://learn.microsoft.com/en-us/windows/win32/gdi/bitmap-storage), [Bitmap kodlama hedefleri](https://developer.android.com/reference/android/graphics/Bitmap.CompressFormat).
