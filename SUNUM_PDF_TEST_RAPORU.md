# Sunumdan PDF — 4 Ekim 2026

Bu rapor ilk metin slaytı akışını belgeler. Sonradan eklenen slayt görselleri [SUNUM_GORSEL_TEST_RAPORU.md](SUNUM_GORSEL_TEST_RAPORU.md) içinde doğrulanmıştır.

Sunum taslağından 16:9 PDF üretme, gerçek dosyadan sayfa önizleme, önceki / sonraki sayfa ve yeni belgeye kaydetme eklendi. Önizlemeden dönüldüğünde düzenleme taslağı korunur. Uzun slayt metni okunaklı alt sınıra rağmen sığmıyorsa metin kesilmez; ilgili slayt numarasıyla düzenleme uyarısı gösterilir. Yeni ekranın 11 metin anahtarı mevcut 22 yerelleştirmeye eklendi; kaynak kontrolü toplam 71 anahtar için geçti. Çeviriler yayın öncesi dil kontrolü gerektirir.

## Doğrulama

Kurulu `medium_phone` AVD'sinde Android 16 / API 36, x86_64, dört sanal çekirdek ve 2 GB yapılandırılmış RAM kullanıldı.

| Senaryo | Ayarlar | Sonuç |
| --- | --- | --- |
| Telefon | 1080×2400, 420 dpi, yazı 1.0 | 26/26; 7 PDF, 7 video, 6 görsel, 6 temel uygulama testi |
| Tablet boyutu | 1920×1200, 240 dpi, yazı 1.0 | PDF testleri 7/7 |
| Dar ekran / büyük yazı | 1080×2400, 540 dpi, 320 dp genişlik, yazı 2.0 | PDF testleri 7/7 |
| Yerel JUnit | Mevcut proje verisi / video profili | 6/6 |
| Debug APK / test APK / lint | Gradle | Başarılı; 0 lint hatası, 20 uyarı |
| Dil kaynakları / APK hizalama | 22 dil, 71 anahtar; zipalign | Başarılı |

Lint uyarıları 14 bağımlılık sürümü ve 6 KTX kullanım önerisidir. Debug APK yaklaşık 16,4 MiB; mağaza yayın paketi değildir. Tablet boyutu aynı telefon AVD'sinin ekran ayarlarıyla oluşturuldu; gerçek tablet donanımı değildir. Test sonunda ekran, yoğunluk ve yazı büyüklüğü ilk değerlerine döndürüldü.

Yedi PDF testi şunları doğruladı:

- Kayıtlı sunumu açma, kaydedilmemiş slayt başlığını düzenleme, dört sayfalı PDF oluşturma, gerçek PDF'den önizleme ve sayfa geçişleri. İlk / son sayfa düğmelerinin sınırları, Activity yeniden oluşturulduğunda sonuç / seçili sayfanın korunması, yeni içerik URI'sine yazma ve PDF başlığının / sayfa boyutlarının okunması kontrol edildi. Yeni belge ile üretilen dosyanın baytları eşitti. Önizleme / dışa aktarma proje dosyasını değiştirmedi. Geri dönünce düzenlemeler kaldı; yeni düzenleme sonrası yeni PDF üretildi.
- Tamamen boş sunumda içerik ekleme mesajı; metin eklendikten sonra başarıyla yeniden üretim.
- Taşan gövde metninde ilgili slayt numarası, PDF kaydetme seçeneğinin olmaması; metin kısaltıldıktan sonra başarılı üretim.
- Hedef sağlayıcı hatası sonrasında başka hedefe başarıyla yeniden kaydetme.
- Sonuç yeniden üretilirken dönen yeni boş belgenin, silmeyi destekleyen sağlayıcıyla kaldırılması. Bu, yeniden üretim / belge dönüş durumunun simülasyonudur; gerçek süreç öldürme testi değildir.
- Gerçek PDF sayfası üretildikten sonra ilerleme çağrısından iptal; kısmi dosyanın silinmesi ve motorun sonraki üretimde çalışması. UI iptal düğmesi aynı ViewModel işi iptal eder; bu test deterministik motor iptalidir.
- 101 slayt için sınır hatası ve çıktı üretilmemesi.

Son tam telefon koşusunda mevcut görsel, video, proje kaydı ve 22 dil / Arapça-Farsça yön testleri de geçti. Bir ara tekrar koşusunda dil geçişinin 15 saniyelik beklemesi zaman aşımına takıldı; aynı dil testi sonraki iki koşuda geçti. Dil kodunda bu görev kapsamında değişiklik yapılmadı; geçici başarısızlığın kesin nedeni doğrulanmadı.

Dosya kaydetme seçicisinin sonucu Espresso Intents ile kontrollü bir test sağlayıcısından verildi. Gerçek ContentResolver, Android PDF motoru, PdfRenderer ve çıktı yazma çalıştı. Sistem dosya seçicisinin kendi ekranları ve farklı bulut sağlayıcıları henüz sınanmadı. Test sağlayıcısı Android'in belge silme çağrısını da destekler; yalnızca test APK'sındadır.

## PDF ve görsel inceleme

Uygulamanın ürettiği dört sayfalık örnek **156.267 bayt**, **PDF 1.4**, sayfalar **960×540 pt**. PDF, Android'deki önizlemenin dışında Poppler ile de açıldı ve dört sayfası PNG olarak işlendi. Sayfaların tamamı gözle incelendi; başlık / gövde / alt bilgi hizası, kenar boşlukları ve çok alfabeli görüntü doğrulandı. Arapça / Farsça paragraflar sağdan sola yerleşti; karma Latin satırı kendi yönünü korudu. Türkçe, Arapça, Farsça, Japonca, Çince, Korece, Hintçe, Tayca, Kiril ve Latin aksanlı örneklerde görünür eksik kutu veya kesilmiş metin görülmedi. Bu örnekler bütün dillerde dilsel doğruluk denetimi değildir.

Telefon, tablet ve büyük yazı ekran görüntüleri incelendi. Geniş pencerede PDF solda, kaydetme / durum alanı sağda; dar pencerede tek sütun. Kaydırma, sayfa geçişi ve kaydetme kontrolleri erişilebilir kaldı. Büyük yazı ve normal telefon örnek PDF'lerinin SHA-256 değerleri aynıydı; uygulamadaki yazı büyüklüğü PDF düzenini değiştirmedi. Tam TalkBack denetimi henüz yapılmadı.

Metin çıkarma ayrıca PDFPlumber ile kontrol edildi. Türkçe / Kiril / Latin metinleri alındı. CJK için Unicode uyumluluk normalizasyonu gerekebildi. Arapça / Farsça şekillenmiş harfler ve ters okuma sırası, Hintçe kümelerinde boş / eksik eşlemeler ve Taycada farklı boşluklar görüldü. Görüntü doğru olsa da **PDF'den kopyalama ve aramanın tüm alfabelerde eksiksiz olması bu sürümde sağlanmıyor**. PDF etiketli değil; erişilebilir PDF yapısı ayrıca geliştirilmelidir.

## İlk çıktı kapsamı

Her metin slaydı bir sayfa; beyaz zemin, sabit koyu yazı / vurgu rengi, proje etiketi ve sayfa numarası. Başlık 44–28 pt, gövde 26–18 pt aralığında küçültülür. Bu sınırda sığmayan slayt reddedilir; slayt metni sessizce kesilmez. Tekrarlanan proje etiketi çok uzunsa kısalır. Sistem fontları ve Android StaticLayout ile şekillendirme kullanılır; cihaz font kapsamı gerçek cihazlarda ayrıca doğrulanmalıdır.

Girdi en fazla 100 slayt; proje adı 500, slayt başlığı 1000, gövde 8000 ve toplam slayt metni 200.000 karakter. İlk sürüm metin slaytlarıyla sınırlıdır. Görsel / video yerleştirme, şablon ve renk seçimi, PPTX, animasyon ve uygulama içi sunum gösterimi henüz yoktur. Hız / bellek zirvesi veya üst sınırdaki bütün sunumlar için performans yük testi yapılmadı.

PDF üretimi proje kaydı yerine geçmez. Düzenleme taslağı mevcut Kaydet / Geri akışıyla saklanır; PDF yalnızca yeni belgeye yazılır. Dosya üretme / render / dışa aktarma IO iş parçacığında yürür. Ekran yeniden oluşturulurken ViewModel sonucu korur; süreç yeniden başladığında Android'deki taslak durumu geri gelirse PDF yeniden üretilir. Kalıcı PDF işlem oturumu ve arka plan hizmeti henüz yoktur. İptalde devam eden yerel çağrı bitene kadar kısa bekleme olabilir; kısmi çıktı kullanılmaz. Yeni kısmi belge, sağlayıcı destekliyorsa kaldırılır. Araç açılınca 24 saatten eski PDF önbelleği temizlenir. Reklam SDK'sı henüz eklenmedi.

## Kanıtlar ve devam

Git dışında tutulan `test-artifacts/presentation-pdf/` içinde son telefon XML sonucu, tablet / büyük yazı test çıktıları, üç ekran görüntüsü, örnek PDF, yerel ve Poppler sayfa görüntüleri ve çıkarılan metin JSON'u bulunur. İnceleme için aynı PDF `output/pdf/ornek-sunum.pdf` konumuna kopyalandı. Test kodu `PresentationPdfDeviceTest.kt`; motor `media/PresentationPdf.kt`, durum yönetimi `media/PresentationPdfViewModel.kt`, arayüz `ui/PresentationPdfScreen.kt` içindedir.

Gerçek cihazlar, eski Android sürümleri, farklı sistem fontları, düşük depolama, sistem dosya seçicisi, gerçek süreç ölümü ve uzun kullanım kontrolleri yayın öncesinde gerekir. Sonraki geliştirme: tek videoda başlangıç / bitiş seçme, kesilmiş aralığı önizleme ve yeni MP4 olarak kaydetme.

Teknik kaynaklar: [Android PdfDocument](https://developer.android.com/reference/android/graphics/pdf/PdfDocument), [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer), [StaticLayout.Builder](https://developer.android.com/reference/android/text/StaticLayout.Builder), [belge silme](https://developer.android.com/reference/android/provider/DocumentsContract#deleteDocument(android.content.ContentResolver,%20android.net.Uri)).
