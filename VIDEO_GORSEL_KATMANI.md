# Video görsel / logo katmanı — 8 Ekim 2026

Geliştirme koduna tek görsel katmanı eklendi. Dondurulmuş 0.1.10 (11) APK / AAB değişmez; yeni sürüm numarası veya imzalı yayın paketi üretilmedi. Yeni kodun R8 / yayın doğrulaması kullanıcının sonraki yayın isteğinde yapılır.

## Kullanım ve kapsam

Video aracındaki **Görsel / logo katmanı → Görsel seç** ile uygulamanın mevcut görsel girdileri kullanılabilir: JPEG, PNG, BMP, durağan WebP ve cihaz desteğine göre HEIC / HEIF / AVIF. İçerik baytları doğrulanır; uzantıya güvenilmez. Animasyonlu WebP kabul edilmez. Tam görsel, EXIF yönü düzeltilerek ve en-boy oranı korunarak yerleştirilir. PNG / WebP alfa korunur; codec çıktısı mevcut SDR renk sınırlamalarını sürdürür.

Dokuz konum, video genişliğinin %5–80'i kadar boyut, %5–100 saydamlık ve başlangıç / bitiş zamanı vardır. Uzun görselin yüksekliği video yüksekliğinin %90'ına sığdırılır; görsel kırpılmaz. Boyut ayarı nihai video çözünürlüğünü veya kalite seçeneklerini değiştirmez. Düşük çözünürlüklü görsel büyük yerleştirilirse büyütme bulanıklığı olabilir.

Zaman, kesilmiş / birleştirilmiş ve hızı değiştirilmiş **nihai çıktının zamanıdır**. Başlangıç dahil, bitiş hariçtir. “Sona kadar” dinamik bitiştir; belirli bitiş video sonuyla sınırlandırılır. Başlangıç yeni sürenin dışında kalırsa işlem açıklamayla engellenir, katman sessizce atılmaz. Tek yazı katmanı aynı videoda kullanılabilir ve görselin üstüne çizilir.

**Videoyu işle → çıktıyı oynat** gerçek önizlemedir. Kaydetme aynı dosyayı kopyalar, yeniden kodlamaz. Kaynak oynatma katmanlı önizleme sayılmaz. Bu adımda çoklu görsel, sürükleyerek yerleştirme, döndürme, animasyon ve kullanıcı ses miksajı yoktur.

## Kaynak sahipliği ve performans

- `VideoImageAssets` seçilen görseli video oturumunun özel `overlay-*.image` dosyasına bayt bayt kopyalar. Önceki ortak görsel çalışma / paylaşım önbelleğine bağlanmaz. Dış kaynağı tekrar açmadan özel kopyadan geri yüklenir.
- V5 oturum / işlem kimliği görsel kaynağını ve bütün katman ayarlarını içerir. V1–V4 istek baytları değişmez; eski tamamlanmış işler eşleşmeye devam eder. Görsel veya ayarı değişince önceki çıktı ve sonuca özel büyük kopya onayı geçersiz olur.
- Görsel değiştirme / kaldırma / sıfırlamada eski özel dosya ancak atomik metadata yazımı tamamlandıktan sonra silinir. Hatalı içe alma önceki görsel ve çıktıyı korur. Oturumdaki yaşlı görsel cache temizliğinden korunur.
- Önizleme en fazla 512 kenarda çözülür. Dışa aktarma görüntüsü gereken katman boyutuna göre çözülür; mevcut örneklemeli / EXIF destekli görsel decoder paylaşılır. Tam kaynak boyutu ve video kalitesi korunur. `VideoImageOverlay` GL configure sırasında bir özel bitmap üretir; kare başına çözme / bitmap oluşturma yoktur. Görünme aralığı yalnız alfa ayarını seçer. GL bitmap release ile temizlenir; UI'a teslim edilen bitmap zorla geri dönüştürülmez.
- Video ve görselin özgün URI'larına dışa aktarma engellenir. Sıkıştırma karşılaştırması video kaynak baytlarıyla yapılır; eklenen büyük görsel başarılı sıkıştırma iddiası yaratmaz. Eşit / daha büyük video çıktısında o sonuca özel kopya onayı gerekir.
- Yeni SDK, font, codec, ağ izni veya Jev bağlantısı eklenmedi. 95 gerçek runtime artifact ve native encoder için mevcut ticari lisans kapsamı kontrolü geçti.

## Doğrulama

- 76 JVM testi: V5 yalnız görsel / yazıyla birlikte round-trip, V1–V4 uyumu, ayar başına işlem kimliği, bozuk referans / boyut / zaman ve tam en-boy oranı.
- API 36 özel QA emülatörü: üç motor testi ve bir gerçek UI testi. İki klip / iki yön, 0,5× ve 2× hız, AVC / HEVC MP4 ve VP8 WebM ile altı çıktı. Tam alfa, yarı alfa, kullanıcı saydamlığı, global klip sınırı ve birlikte yazı kontrolü için bütün kareler gerçek PTS ile bağımsız olarak çözüldü; çözünürlük, süre, klip sırası ve 440 / 660 Hz ses de doğrulandı.
- Dosya kopyası bayt eşitliği, özgün URI koruması, PNG / WebP alfa, EXIF 6, bitmap önbelleği, video ve görsel içe alma iptali, hatalı görsel sonrası eski sonuç, yaşlı kaynakla yeni ViewModel restorasyonu ve başarısız atomik değiştirme / kaldırma sonrası yeniden deneme sınandı.
- UI dosya seçimi, dokuz konumdan seçim, boyut / alfa / süre, gerçek sonuç oynatıcı URI'sı, yeni ViewModel restorasyonu ve kaldırma üzerinden çalıştırıldı. Tablet, dar ekran / iki kat yazı ve koyu modda aynı akış tekrarlandı.
- Önceki yazı motoru / UI, katmansız iki video / atomik restorasyon ve görsel EXIF / alfa akışlarında altı regresyon testi geçti. Altı önceki yazı çıktısının tüm kareleri ve sesi ayrıca çözüldü. Değişen Kotlin kurucusuna ait eski incremental test bytecode temizlenip bütün Android test sınıfları yeniden derlendi; nihai regresyon bu eşleşen APK'larla geçti.
- Debug lint: 0 hata / 42 uyarı. 22 dil kaynak anahtarları ve placeholder kontrolü geçti; bütün dillerde insan dil incelemesi yapılmış sayılmaz.

Komut: `tools/testing/video-image-qa.ps1` yalnız `ofice_qa` cihazını kabul eder. Yerel raporlar `output/development/video-image/` altında ve Git dışındadır. Graphify yerel AST grafiği güncellendi; kısmi Kotlin çıkarımı kaynak ve gerçek test yerine geçmez.

Fiziksel ARM / düşük RAM, uzun yüksek çözünürlüklü katmanlı video, gerçek dolu disk, eski API / üretici HEIF / AVIF matrisi ve pil / termal ölçümler açıktır. Emülatör sonuçları bu cihazlarda performans garantisi değildir. Sıradaki ürün işi kullanıcı sesiyle miksajdır.
