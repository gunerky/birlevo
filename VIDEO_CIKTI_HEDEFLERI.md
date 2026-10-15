> 15 Ekim güncellemesi: Aşağıdaki HEVC/VP8 raporu tarihsel kayıt olarak korunur. VP9 Profile 0 giriş/çıkışı ve ortak kare akışı [codec matrisi raporunda](CODEC_MATRISI_VE_VP9_GENISLEMESI.md) eklendi. Yeni yayın sürümü üretilmedi.

# Birlevo — HEVC MP4 ve WebM çıktı hedefleri

7 Ekim 2026. Hedefler geliştirme uygulamasının gerçek video ekranına ve ortak VideoCompressor motoruna eklendi. Önce geliştirme doğrulaması yapıldı; kullanıcının aynı iş sonunda yeni sürüm isteği üzerine 0.1.9 (10) için imzalı APK / AAB hazırlandı ve R8 doğrulaması tamamlandı. Eski 0.1.8 (9) paketi korunur. Play’e yükleme / yayın bu işlem kapsamında yapılmaz.

## Kullanıcı akışı ve destek

Video seç → MP4 · H.264, MP4 · HEVC veya WebM · VP8 seç → kalite / boyut / kırpma ayarla → sonucu önizle → kaydet, aç, paylaş veya ses aracında kullan. Varsayılan H.264 MP4 korunur. Hedef değiştirmek önceki sonucu ve o sonucun büyük kopya onayını geçersiz kılar.

| Hedef | Gerçek video / ses | Koşul ve açıklama |
| --- | --- | --- |
| MP4 · H.264 | AVC; varsa AAC | Önceki yol ve varsayılan; tamamlanan dosya yeniden doğrulanır. |
| MP4 · HEVC | HEVC Main 8 bit SDR; varsa AAC | Seçilmiş boyut / bitrate / kare hızında encoder ve önizleme decoder gerekir. HEVC oynatma diğer cihaz / uygulamalarda sınırlı olabilir; daha küçük dosya garantisi yoktur. |
| WebM · VP8 | VP8; varsa 48 kHz Opus | VP8 encoder / decoder gerekir. Ses varsa Android 10 / API 29+, Opus encoder / decoder ve aynı kanal sayısı gerekir. Ses yeniden kodlanır; 48 kHz değişimi açıklanır. Media3 1.5.1 yolunda 180° yön etiketli kaynak için MP4 seçilmesi istenir; 90° dikey kaynak doğrulandı, 270° ayrı cihaz matrisi bekler. |

Giriş biçimleri / kalite / 640, 1280, 1920 kenar seçenekleri azaltılmadı. HDR / 10 bit ve destek dışı kaynak profilleri mevcut açıklamalı red yolunda kalır. VP9 / AV1 desteği bu adımda eklenmedi. Hedef desteği dosya uzantısından çıkarılmaz; gerçek parça bilgileri ve cihaz koşulları kullanılır. Destek yoksa işlem düğmesi devre dışı kalır ve nedeni gösterilir; başka codec / çözünürlüğe sessiz geçiş yapılmaz. Destek kontrolü işlemden hemen önce tekrar uygulanır.

## Motor ve dayanıklılık

Saf VideoOutputFormat / platform kuralları, IO cihaz kontrolü, WebmMuxer, motor, oturum / kalıcı iş kaydı ve UI ayrıdır. Media3 1.5.1 muxer bağımlılığı aynı sürümle uygulama katmanına taşındı; yeni native codec motoru, ağ izni veya dış servis yoktur. Eski prototip motor kaldırıldı; eski fixture matrisi artık gerçek uygulama motorunu kullanır.

Cihazın codec aralıkları eksik bildirilebilir: API 36 emülatör HEVC encoder’ı en fazla 512 piksel bildirmesine rağmen gerçek 1280 piksel video üretir. Bildirilen ayar uyumu yoksa tam istenen ayarla MediaCodec.configure / input Surface kontrol edilir; ayarlar küçültülmez. Codec ve Surface finally ile serbest bırakılır, yalnız başarılı yapılandırmalar en çok 64 kayıtla önbelleğe alınır. Kaynak baskısındaki başarısızlıklar kalıcı önbelleğe alınmaz. Yapılandırma başarısı gerçek kodlama garantisi değildir; çıktı yeniden okunarak codec, boyut, süre, ses varlığı, kanal ve örnekleme doğrulanır. Asıl işlem hatası başarı olarak gösterilmez. Encoder adaptörü Java varsayılan `videoNeedsEncoding` yöntemini açıkça asıl factory’ye yönlendirir; kaynakla aynı boyut / codec seçildiğinde de bitrate / kalite ayarı uygulanır. Bu yönlendirme olmadan aynı boyuttaki AVC kopyası yeniden kodlanmıyordu; mevcut bitrate ve başarısız kaydetmeyi yeniden deneme testleri bunu yakaladı ve düzeltildi.

WebM platform yazıcısı sıfır yön etiketli gerçek dikey kareler kullanır. Kırpılmış Opus’un negatif preroll damgalarını ayrı parça kaydırmasıyla gizlemek yerine ses yeniden kodlanır. Kanal sayısı korunur. Ayrıntı / metadata kaybı ve daha küçük dosya garantisi olmadığı 22 dilde açıklanır. Kare / örnek düzeyinde senkron veya algısal kalite garantisi bu testlerden çıkarılmaz.

Eski video JSON’unda format alanı yoksa AVC varsayılanı kullanılır. AVC serileştirmesine yeni alan eklenmediği için eski kalıcı iş isteğinin izi korunur; yeni hedefler farklı istek kimliği alır. Bilinmeyen hedef kabul edilmez. Sonuç geri yüklemede özel klasör, dosya adı / uzantı, gerçek byte boyutu ve gerçek codec kontrol edilir.

Kaydetme için MP4 / WebM CreateDocument sözleşmeleri ayrı ve sabittir; hedefe uygun MIME / uzantı kullanılır. Açma / paylaşma ve ses aracına özel dosya aktarımı da gerçek sonucun biçimini kullanır. Aktarım ek kodlama yapmaz. Kaynaklar, FileProvider yetkisi / kökleri ve eski proje referansları korunur. Eşit / büyük sonuç kaydetme ve ortak aktarım için o sonuca özel onay gerekir; biçim değişmesi sıkıştırma başarısı sayılmaz.

## Doğrulama ve kalan ölçümler

Yerel kanıtlar Git dışındaki `output/development/video-integration/` altında tutulur. Ana entegrasyon paketi 7 Android testi: 13 matris çıktısı ve ayrıca iki gerçek UI üretimi (HEVC 5, WebM 8), sessiz / sesli / 90° dikey kaynaklar, AVC / HEVC / VP8 ve AAC / Opus / Vorbis kaynakları, Small / High ayarları, iptal temizliği, destek dışı encoder / ses / yön gerekçeleri, gerçek boyut onayı, doğru kaydetme MIME / baytları, aç / paylaş intent’leri, Activity ve yeni ViewModelStore ile kalıcı sonuç geri yükleme, gerçek oynatıcıda sona kadar oynatma ve WebM’den ses aracına sahipli aktarım.

Ayrıca ortak motora bağlanan önceki 5 testlik matris video oynatımı ve WAV / M4A üretimini tekrar denetler. Mevcut video, arka plan, ses, oturum, ortak aktarım ve telefon medyası regresyonları; tablet, dar ekran / büyük yazı ve koyu mod yerleşimleri ayrıca çalıştırılır. 49 Android testi tek koşuda geçti; 3 yerleşim tekrarı ve ekran görüntüsü incelemesi tamamlandı. Nihai sayılar / koşu sonucu yerel `qa-summary.json` dosyasında tutulur; aynı UI testinin farklı yerleşimde tekrarı yeni benzersiz test sayılmaz.

55 JVM testi ve debug derleme / lint (0 hata, 40 uyarı), 22 dil kaynak kontrolü ve 20 çevrimdışı Jev aracı testi geçti. Canlı Jev çağrısı yapılmadı; debug APK geliştirme aracından yalıtım kontrolünü geçti. Yerel Graphify güncellendi; Kotlin parser uyarıları kaynak / gerçek derleme sonuçlarıyla değerlendirilir.

0.1.9 release doğrulamasında PDFBox’ın isteğe bağlı JP2Decoder / JP2Encoder bağlantıları için iki dar R8 kuralı eklendi. Uygulama JPEG2000 çözmek için bu eklentiyi çağırmaz: PDF raster Android PdfRenderer ile, sayfa kopyası ham akışla çalışır. JPEG2000 içeren yapay PDF’nin sayfa kopyası / piksel korunumu imzalı APK üzerinde geçti. Tam 12 iş akışı, özel varlığın süreç yeniden açılışında korunması ve üç yerleşimde video hedefleri / PDF okuyucusu (6 tekrar) doğrulandı. Bundletool, gerçek paket manifesti / servisler, imza, 16 KiB native hizalama, R8 eşlemesi ve AAB geliştirme aracı yalıtımı geçti. [Sürüm / Console kılavuzu](YAYIN_0.1.9.md).

Fiziksel düşük donanım, üretici / eski API codec’leri, 1920 piksel / uzun video, 180° / 270° yönler için ileri yol, yüksek kare hızı / değişken kare hızı, dolu disk, pil / ısınma / bellek ölçümleri bekler. 0.1.9 R8 testi tamamlandı; sonraki sürümlerde değişen yollar ayrıca doğrulanır. Emülatör sonucu fiziksel cihaz performans sözü değildir.

İlk prototipin tarihsel kaydı: [teknik denemeler](VIDEO_CIKTI_TEKNIK_DENEMELERI.md). Platform koşulları: [Android MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer), [kullanılan Media3 1.5.1](https://github.com/androidx/media/tree/1.5.1).
