# Görsel formatları ve fotoğraflardan PDF — 0.1.7 (8)

## Tamamlanan kapsam

- JPEG / PNG / durağan WebP içe aktarma ve gerçek JPEG / PNG / kayıplı WebP dışa aktarma. Uzantı ve MIME gerçek kodlamayla eşleşir. Animasyonlu WebP kabul edilmez; statik çıktı uyarısı gösterilir.
- PNG ve WebP şeffaflığı korur. JPEG beyaz zemin kullanır. PNG kalite kaydırıcısı gösterilmez; 1280 / 1920 / 2560 çözünürlük sınırları ve JPEG / WebP kalite seçenekleri korunur.
- Gerçek çıktı boyutu, önizleme, kaydetme, açma, paylaşma ve sunuma aktarım ortak akışta çalışır. Eşit / daha büyük sonuç, sunuma veya fotoğraf PDF'sine aktarılırken de sonuca özel onay ister. Seçenek değişikliği eski onayı geçersiz kılar.
- İşlenen görsel doğrudan fotoğraf PDF'sine eklenebilir; araçtan çıkmadan önce kopyalama tamamlanır. Sunuma aktarılan görseller kalıcı proje alanında tutulmaya devam eder.
- Fotoğraflar çoklu seçilir, yukarı / aşağı taşınır veya kaldırılır. EXIF yönü uygulanır; A4 dikey / yatay sayfaya oran korunarak ve kırpılmadan yerleştirilir. Saydam alan PDF'de beyazdır.
- Fotoğraf PDF'si gerçek PDF'den, sayfa oranını koruyan önizleme üretir. Sayfa değiştirme, yeni dosyaya kaydetme ve ortak aç / paylaş işlevleri vardır. PDF oluşturma ve içe aktarma iptal edilebilir; başarısız toplu ekleme önceki seçimi değiştirmez.
- 22 dilin kaynakları güncellendi. Ek üretim bağımlılığı, ağ yüklemesi, reklam veya izin eklenmedi.

## Kaynak ve yaşam döngüsü

En fazla 100 sayfa, görsel başına 128 MiB, toplam 512 MiB kaynak kopyası sınırı açıkça gösterilir. Fotoğraf PDF'sinde uzun kenar sınırı kullanıcı tarafından 2048 veya 4096 seçilir. Önizleme 1280 ile ayrıdır; kalite otomatik düşürülmez.

İçe aktarma 64 KiB tamponla, iptal kontrolü ve atomik toplu ekleme ile çalışır. PDF dosyası geçici dosya / fsync / yeniden adlandırma ile tamamlanır. Aynı anda bir sayfanın çözülmüş bitmap'i işlenir. Android PdfDocument tüm belgenin kaynaklarını yazıma kadar tutabileceği için bütün PDF'nin sabit bellek kullandığı iddia edilmez. Büyük partilerin düşük donanımda sınırı fiziksel cihazda ölçülmelidir.

Fotoğraf seçimi ViewModel'de tutulur; ekran döndürme korunur. Süreç kapanması sonrasında bu seçim için taslak yükleme henüz yoktur; ekrandaki uyarı sonucu ayrılmadan kaydetmeyi belirtir. Geçici özel dosyalar yeniden açılışta 24 saat eşiğiyle temizlenir. Kaydedilen PDF kullanıcı seçtiği konumdadır. OCR / aranabilir yazı veya PDF sıkıştırma bu paketin kapsamına dahil değildir.

## Doğrulama

20/20 birim testi ve 22 dil kaynak kontrolü geçti. Telefon debug takımında 64 ayrı testin tamamı düzeltme tekrarlarıyla geçti: ilk çalışmada 58/64, ilgili 21 test tekrarında 20/21, kalan slayt menüsü akışında 1/1. Tek çalışmada nihai 64/64 sonucu iddia edilmez. Slayt diyaloğundan sonra odak geri dönüşü sırasında liste yer değiştirebildiği için menü testi Compose düğmesinin erişilebilirlik eylemiyle açılır. Debug / release lint ayrı ayrı 0 hata, 29 uyarıdır; güncel olmayan bağımlılık / KTX önerileri, eski animasyon analizi ve kullanılmayan iki kaynağı içerir.

Gerçek imzalı ve küçültülmüş APK'da telefonun altı akışı ayrı çalışmalarla geçti. JPEG, PNG, WebP, MP4 ve PDF başka test uygulamasında okunabildi. Test APK'sı üretim paketine dahil değildir. Nihai sayılar ve paket durumu `output/development/0.1.7/qa-summary.json` içinde tutulacaktır. İlk 64 test çalışmasının 6 başarısızlığı gizlenmez: dört eski test ana sayfadaki artık ekran dışında kalan tembel proje öğesini doğrudan arıyordu; fotoğraf PDF testi tembel çıktı öğesini doğrudan arıyordu; bir test sağlayıcının desteklemediği kaynak silme işlemini çağırıyordu. Testler liste üzerinden öğeyi bulacak ve gerçekten silinebilen kaynak kopyasını kullanacak şekilde düzeltildi. Nihai kontroller ayrı loglarda tutulur.

Fiziksel cihaz codec, büyük parti bellek / ısınma / pil ve çeviri dili incelemesi emülatör sonucundan çıkarılmaz. Önceki 0.1.6'nın tamamlanmamış dar büyük yazı / koyu tema yolları bu yeni imzalı aday üzerinde yeniden doğrulanır.

## Teknik kaynaklar

- [Android Bitmap sıkıştırma biçimleri](https://developer.android.com/reference/android/graphics/Bitmap.CompressFormat)
- [WebP kapsayıcısı ve animasyon bayrağı](https://developers.google.com/speed/webp/docs/riff_container)
- [Android FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)

Gizlilik sayfalarının yerel taslakları 0.1.7 için güncellendi; bu çalışma web sitesine veya Play'e otomatik yayın yapmaz.

## İmzalı adayın ekran kontrolleri

- Telefon 1080×2400 / 420 dpi / yazı 1: altı medya akışı ayrı çalışmalarla doğrulandı; JPEG, MP4 / AAC, sunum PDF, kalıcı sunuma görsel aktarımı, PNG / WebP ve fotoğraf PDF. Uygulama zorla kapatılıp yeniden açıldığında kalıcı görselden PDF üretildi.
- Tablet 1600×2560 / 240 dpi / yazı 1: yeni PNG / WebP ve fotoğraf PDF akışları geçti. Fotoğraf sırası / EXIF yönü oluşturulan PDF'nin pikselleriyle doğrulandı. İlk fotoğraf PDF çalışmasında pencere geçişi zaman aşımı / bildirim paneline kayan otomasyon oluştu; log korunur, temiz Android açılışındaki tekrar geçti.
- Dar ekran 720×1440 / 360 dpi / yazı 2: MP4 / AAC işlem, kayıt / paylaşma ve büyük kopya onayı; sunum tema değişimi / PDF; kalıcı sunuma aktarım; PNG / WebP; fotoğraf PDF geçti. Sonrasında force-stop / yeniden açılışta sunum görselinden PDF üretildi. PDF'nin ilk çalışması koşucu uyarlanırken bilerek durduruldu; sonrasında geçen ayrı tekrar kayıtlıdır.
- Büyük yazılı kısmen görünür kartlar için koşucu Android erişilebilirlik tıklamasını kullanır; kaydırma yalnızca uygulamanın penceresinde yapılır. Bu koşucu değişiklikleri üretim APK'sına girmez. Önceki fiziksel koordinatlı telefon / tablet kontrolleri de ayrı loglarda korunur.

AAB: `output/releases/0.1.7/birlevo-0.1.7-8.aab` — 7.135.005 bayt. SHA-256: `1d929520c3331d6f13b6f2d81b9b425f5ddc0585e6d638c08afd75d79a8c4c13`. Upload sertifikası, APK v2 imzası, gerçek bundle manifestinde sürüm / izinler, R8 / kaynak küçültmesi, bundletool doğrulaması ve 16 KiB yerel kitaplık hizalaması kontrol edildi. Önceki aday dosyaları korunur.

Koyu telefon modunda sunum tema / PDF, kalıcı sunuma aktarım, PNG / WebP ve fotoğraf PDF'nin dört akışı geçti. Force-stop / yeniden açılış sonrası sunum görseli ve PDF korunur. Lacivert arka plan / bölüm ayrımı ve yeni araçların ekranları görsel olarak incelendi. Sonuçların nihai kaydı `qa-summary.json` içindedir. `technicalQaPassed=true`; web gizlilik güncellemesi ve mağaza metni / yeni ekran görüntüsü kontrolü dağıtımdan önce kaldığı için `readyForDistribution=false` tutulur. Play'e yükleme yapılmadı.
