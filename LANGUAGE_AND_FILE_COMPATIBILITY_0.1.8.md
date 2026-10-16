# Birlevo 0.1.8 (9) — dil ve dosya uyumluluğu

6 Ekim 2026. İlk uyumluluk paketi: desteklenen işlemleri görünür kılma, ortak gerçek içerik doğrulaması, anlaşılır içe alma hataları ve yerelleştirilmiş ölçü birimleri. Yeni format motoru veya harici PDF okuyucu bu pakete dahil değildir. Gönderilmiş 0.1.7 AAB değiştirilmez.

## Kullanıcıya görünen değişiklikler

- Ana sayfa ve görsel / video / fotoğraf PDF / sunum araçlarında **Desteklenen dosyalar** yardımı. Her işlem için giriş ve çıktı ayrı gösterilir. PDF, şu anda çıktı türüdür; mevcut PDF içe alma desteği varmış gibi sunulmaz.
- Görsel biçimi, animasyon, dosya / piksel sınırı; video kapsayıcısı, codec, HDR, ek izler ve işlem sınırı; dosya erişimi ve bellek sorunları ayrı açıklanır. Diğer hata yollarında mevcut yerelleştirilmiş işlem uyarıları korunur.
- Çözünürlük seçenekleri, önizleme ölçüleri ve video süresi seçili dilin sayı / birim kaynaklarını kullanır. Format adları ve teknik kısaltmalar çevrilmez.
- Yeni 19 metin anahtarı 22 yerelleştirmeye eklendi. Katalog 155 anahtar, `app_name` ile 156 metin içerir. Boş anahtar / eksik kaynak / placeholder uyumsuzluğu bulunmadı. Bu kontrol bütün çevirilerin uzman onayı değildir.

## Teknik yapı ve korunan kurallar

`media/FormatSupport.kt` işlem / MIME ilişkisini ve ortak doğrulamayı tutar. `ui/SupportedFiles.kt` yalnız yerelleştirilmiş gösterimdir. Görsel dönüştürücü, sunum görseli, fotoğraf PDF ve video motorları ortak hata nedenleri kullanır. Dosya uzantısı veya sağlayıcının MIME etiketi gerçek içerik kontrolünün yerine geçmez.

Android emülatöründe H.264 SPS içinde PQ renk bilgisi olan gerçek bir dosyada `MediaExtractor` renk transferini, `MediaMetadataRetriever` de yedek alanı vermedi. `media/VideoColor.kt`, Android'in zaten sağladığı AVC başlangıç verisindeki SPS'yi mevcut Media3 ayrıştırıcısıyla kontrol eder. Ek kare çözme / ağ / yeni kitaplık yoktur; buffer konumu değiştirilmez ve okunan başlangıç verisi 128 KiB ile sınırlanır. HDR metadata/SPS bilgisi mevcut olduğunda saptanır; metadata'sı eksik veya yanlış her dosyayı saptama garantisi verilmez. Bu, önceden belirlenmiş SDR desteği kuralını uygular; HDR işleme desteği eklemez.

Görsel 128 MiB / 100 MP; video 512 MiB / 10 dakika / 4096 kenar / 8.847.360 piksel / 60 fps ve tek H.264 + en çok bir AAC iz sınırları korunur. Fotoğraf PDF'de 100 sayfa / toplam 512 MiB kaynak ve 2048 / 4096 çıktı seçenekleri korunur. Kalite otomatik azaltılmaz. Gerçek boyuta göre küçülmeyen sonuç için sonuca özel açık kopya onayı, kaynak koruma, FileProvider yetkisi ve proje dosyası sahipliği değişmez.

Yardım içeriği tembel ve kimliği korunan satırlardan oluşur. Hata sonrası yeni başarısız çalışma kopyaları temizlenir; önceden yüklenmiş geçerli kaynak korunur. Grafik yerel AST üzerinden güncellenir.

## Jev değerlendirmesi

Yalnız 8 İngilizce yardım / hata metni ve Türkçe, Arapça, Hausa, Svahili dillerinden 12 seçilmiş çeviri değerlendirildi. 20/20 geçerli yanıt; 20 API çağrısı; 46.320 gönderim baytı; 16.001 girdi / 1.332 çıktı tokenı. Sekiz İngilizce vaat `supported`, 12 çeviri anlamı `preserved` seçildi. Açıklık veya düşük confidence nedeniyle 14 insan inceleme işareti var; bunlar 14 doğrulanmış hata değildir. Ek izler uyarısındaki teknik terimler sadeleştirme / uzman dil kontrolü adayıdır.

Jev kod / medya çalıştırmadı. Kullanıcı dosyaları, anahtar ve bütün kaynak kod gönderilmedi. Canlı inceleme boyunca kaynak hash'i sabitti. Android APK / AAB'sine Jev eklenmez. Rapor: `output/development/jev/20261006-0.1.8-new-copy/report.md`.

## Doğrulama

25/25 birim testi ve 68/68 Android debug testi tek nihai çalışmada geçti. Yeni dört cihaz testi 22 dilde açma / yeniden oluşturma / yardım kapatma, JPEG etiketi taşıyan gerçek GIF'in reddi ve eski kaynağın korunması, sunum / fotoğraf PDF ortak doğrulaması ve başarısız parti temizliği, HEVC / PQ HDR / iki görüntü izi / MKV ret nedenlerini kapsar.

İmzalı, R8 / kaynak küçültmeli gerçek APK'da yedi telefon iş akışı tek çalışmada geçti: görsel, video, sunum PDF, görseli sunuma aktarım, PNG / WebP çıktıları, fotoğraf PDF ve yeni destek / ret ekranları. Ayrı süreç durdurma / yeniden açma kontrolü proje görseli ve PDF çıktısını doğruladı. Tablet (1600×2560 / 240 dpi), dar büyük yazı (720×1440 / 360 dpi / 2× yazı) ve koyu telefon modlarının her birinde yeni destek / hata, PNG / WebP ve fotoğraf PDF akışları geçti: toplam 9 ek yayın akışı. Debug ve release lint: ayrı ayrı 0 hata / 29 uyarı.

İlk yayın testinde test koşucusu yeni proje adı işlenip kaydedilmeden `Activity.finish()` çağırdı; bir önceki kaydın durumunu yeterli sandı. Koşucu artık yazılan gerçek değeri kontrol ediyor ve aktarım testinde kullanıcıdaki **Kaydet** akışıyla çıkıyor. İlk başarısız log korundu; aynı uygulama APK'sındaki nihai telefon çalışması geçti. Dar / iki kat yazı ekranında ana eylem ilk viewport dışında kaldığından koşucunun açılış kontrolü sabit Birlevo başlığına alındı; eylemler kaydırılarak bulunur. Bu otomasyon düzeltmeleri için uygulama paketine değişiklik yapılmadı.

AAB: `output/releases/0.1.8/birlevo-0.1.8-9.aab`, 7.165.358 bayt, SHA-256 `fe11985fd8e4d361f7f2f6fe0ff3ac01705e8ddda450d6a76a26815011f19345`. Upload sertifikası, APK imzası, bundletool, gerçek AAB manifesti, 16 KiB native hizalaması ve Jev ayrımı doğrulandı. R8 mapping pakette ve ayrıca sürüm dizinindedir. Native debug sembol girdisi yoktur; sağlayıcı sınırı sürer. R8, Kotlin metadata ayrıştırma uyarıları verdi; testlerin başarısı bunu uyarısız bir derleme yapmaz.

QA emülatörü **ofice_qa**, Android API 36'dır. Sadece bu emülatörün test uygulama verileri sıfırlandı. Fiziksel cihaz testi yapılmadı. Grafik yerel AST ile 938 düğüm / 2567 ilişkiye güncellendi; 11 dosyada kısmi ayrıştırma uyarısı var, sonuçlar kaynak ve çalışan testlerle doğrulandı. Ekran görüntüleri `output/development/0.1.8/release-smoke/` altında; ölçümler sürüm `qa-summary.json` dosyasında.

0.1.8 Play'e yüklenmedi. 0.1.7'nin inceleme durumu kullanıcı bildirimi olarak korunur; eski AAB hash'i değişmedi. Önce yeni AAB'yi dahili testte yükleyip kısa elle kontrol, sonra aynı paket kodunu Alpha'ya taşıma önerilir. Birleştirilmiş İngilizce / Türkçe etiketli notlar sürüm dizinindeki `release-notes.txt` içindedir.

## Sonraki paket

Harici PDF okuyucu için güvenli içe alma, sayfa önizleme, parola / bozuk belge / boyut sınırları ve kaynak koruma. Ardından sayfa birleştirme / bölme / sıralama / döndürme ve PDF sayfasını görsel / sunuma aktarma. Sonraki format grubu HEIC / HEIF, durağan GIF / BMP ve uygun MOV / HEVC için cihaz / decoder denemeleridir; bu sürüm henüz bunları desteklemez.

Fiziksel düşük donanım, eski Android / üretici codec, büyük fotoğraf partisi ve süreç dayanıklılığı işleri açık kalır. Reklam SDK'sı eklenmedi; reklam eklenen sürümde Play reklam / reklam kimliği / veri güvenliği / gizlilik beyanları birlikte güncellenir. Native sembol `FULL` ayarı korunur; önceden sembolleri çıkarılmış AndroidX graphics-path kütüphanelerinin Play uyarısı bütünüyle giderilmiş değildir.
