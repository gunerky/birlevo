# PDF sayfa araçları ve arayüz incelemesi — 6 Ekim 2026

Bu çalışma geliştirme kodundadır. Sürüm numarası artırılmadı, release APK / AAB ve Play paketi üretilmedi. Önceki 0.1.8 (9) AAB bu değişiklikleri içermez.

## Çalışan PDF akışı

Ana ekran → PDF sayfa araçları → bir veya birden çok PDF ekle → sayfaları önizle → sırayı değiştir / 90° döndür / kaldır → gerekiyorsa liste konumlarından sayfa aralığı seç → yeni PDF oluştur → sonucu okuyucuda incele → kaydet / aç / paylaş.

- Birleştirme: eklenen belgelerin sayfaları tek listede görünür, kaynak adı ve kaynak sayfa numarası gösterilir.
- Bölme / çıkarma: `1,3-5` veya `1,3–5` gibi aralıklar mevcut listenin konumlarına uygulanır; yalnız seçilen sayfalar yeni PDF'ye girer. Arapça / Farsça / Hintçe rakamlar ve Arapça virgül de kabul edilir. Her sayfa için ayrı dosya / ZIP üretimi bu adımda yoktur.
- Sıralama, döndürme ve kaldırma için çalışan düğmeler; son 20 liste değişikliği geri alınabilir. Geri alma geçmişi süreç yeniden başlatılınca korunmaz.
- Tek bir sayfa istendiğinde 1280 px önizleme hazırlanır. Sayfa listesi kimliği korunan tembel öğeler kullanır; tüm sayfalara bitmap yüklenmez.
- Yeni PDF'nin metni, gömülü font kaynakları, içerik akışları, vektörleri, sayfa kutuları ve yönü kopyalanır. Görseller tekrar kodlanmaz; sayfalar PNG'ye düzleştirilmez.
- Liste ve dosya referansları `SavedStateHandle` ile yeniden oluşturulur. Kaynaklar uygulamaya özel kopyalardır; orijinal belgeye yazma / silme erişimi kullanılmaz. Çıkış, yeni bir dosyadır. Dışa aktarma penceresine ait kaynak koruması oturum sıfırlandıktan sonra da geçerlidir. Araçtan Geri ile çıkmak geçici çalışma oturumunu temizler; bu henüz adlandırılmış kalıcı PDF projesi değildir. Yeni çıktı ve geri alma geçmişi süreç yeniden başlatılınca tekrar oluşturulur.
- Sonuç PDF okuyucuda açılabilir. Okuyucunun PNG → sunum / fotoğraf PDF aktarımı bu sonuçta da kullanılabilir.

## Dürüst destek sınırı

En fazla 8 kaynak PDF, toplam gerçek dosya boyutu 128 MiB, çalışma listesinde 2.000 sayfa. Bu sınırlar yeni sayfa motoruna aittir; mevcut görsel / video / okuyucu seçenekleri azaltılmadı. Yeni PDF en fazla 128 MiB olabilir. Sayfa işçisinin her oluşturma isteği 60 saniye içinde tamamlanmalı; iptal / süre aşımında kısıtlı süreç sonlandırılır. Kaynak okuma sağlayıcısının bloklayan I/O davranışı ayrı bir Android sınırıdır.

Yeni belge oluşturulmadan önce **yorumlar, bağlantılar, yer imleri, erişilebilirlik etiketleri ve belge meta verilerinin aktarılmadığı** gösterilir ve kullanıcı onayı alınır. Şifreli, dijital imzalı, form içeren PDF'ler; belge ekleri / portföyleri bu motorda reddedilir. Mevcut okuyucunun desteklediği salt okunur açma yolları korunur. Bu işlem sıkıştırma veya güvenli sansürleme değildir; paylaşılan kaynak nesnelerde kullanılmayan içerik bulunabileceği için gizli bilgiyi güvenli yok etme vaadi verilmez.

## Motor ve bakım

`PdfPagePlan` seçim ve sınırları, `PdfPageEngine` belge nesnelerini, `PdfPageService` kısıtlı işçiyi, `PdfPageClient` süre / dosya tanıtıcılarını, `PdfPagesViewModel` oturum ve iptali, `PdfPagesScreen` arayüzü yönetir. Medya katmanı Compose durumuna bağımlı değildir.

Kütüphane: [PDFBox-Android 2.0.27.0](https://github.com/TomRoush/PdfBox-Android/tree/v2.0.27.0), Apache 2.0. Belge ağacı yeniden kurulur; `/Parent`, belge eylemleri, form ve ek dosya ağaçları klonlanmaz. Kütüphanenin örnek uygulama / ek dosya çıkarma kodları çağrılmaz. [Apache PDFBox güvenlik duyuruları](https://pdfbox.apache.org/security.html) bakımda izlenecek kaynaklardır; Android portunun sürümü ile güncel masaüstü PDFBox sürümü aynı değildir. Kısıtlı süreç, boyut / süre sınırları bakımın yerine geçmez.

Yeni işçi dışa kapalı, `isolatedProcess=true`; yalnız salt okunur kaynak dosya tanıtıcıları ve geçici çıktı tanıtıcısı alır. Uygulama dosyalarına, URI izinlerine veya ağa erişimi yoktur. PDF içindeki eylemler çalıştırılmaz. PDFBox ve geçişli Bouncy Castle lisans bildirimleri APK içindedir; Desteklenen dosyalar → Açık kaynak lisansları üzerinden okunur.

Jev Android'e bağlanmadı. Bu çalışma sırasında yeni canlı Jev çağrısı yapılmadı; gerçek PDF, UI, kaynak ve çıktı testleri kullanıldı.

## Arayüzde bulunanlar ve düzeltmeler

| Bulgular | Değişiklik |
| --- | --- |
| Tanıtım kartında düğme gibi görünen üç dekoratif simge | Görseller / Video / Slaytlar için etiketli, erişilebilir ve çalışan kısayollar |
| Parlak mavi ana işlem, bağlantı ve alan odağında aynı ölçüde baskın | Açık mod ana işlemleri lacivert; koyu mod açık buz mavisi; parlak mavi tanıtım kartında |
| Marka yazısı ham logo rengiyle tüm zeminlerde kullanılıyor | Logonun kendisi korunur; metin için açık modda daha koyu mercan, koyu modda açık mercan |
| Kullanıcı buz mavisi araç ikonlarını markayla uyumsuz buldu | Ana ekran, proje ve içe alma araç ikonları ortak mercan ailesinde; lacivert tanıtım kartında açık mercan |
| Alan metni, etiketi, odağı ve kenarlığı ortak tanımda eksik | `studioFieldColors` bu rolleri açıkça tanımlar; PDF okuyucu alanı da kullanır |
| Büyük yazıda içe alma açıklaması dosya seçme düğmesini aşağı itiyor | Büyük yazıda dosya seçimi açıklamadan önce gelir; metinler kaydırılarak okunur |
| PDF başlıkları ve uzun onay metni büyük yazıda taşabilir | Başlık tek satır / üç nokta, onay metni kaydırılabilir; aralık işleminden sonra odak temizlenir |
| Eski video taslağı yalnız dosya adı gösteriyor | Taslaktaki kaynak URI ile çalışan video düzenleyiciye geçiş; eski kayıt biçimi korunur |
| Gelecek araç için varsayılan boş callback riski | Ana ekran araç callback'leri zorunlu; görünür yeni sayfa düğmeleri gerçek işlemlere bağlı |
| PDF işlemlerinde liste, ayar ve sonuç iç içe | Nötr, oval bölüm kartları; üretme, sonuç ve sayfa düzenleme bölümleri ayrı |

İşlevsiz bir OCR, otomatik altyazı, Office düzenleyici veya arşiv düğmesi eklenmedi. Bunlar plan kapsamındadır; uygulamada çalışıyormuş gibi gösterilmez. Görünür araçlar ve arayüz callback'leri kaynakta kontrol edildi; boş `onClick` / `TODO` eşleşmesi bulunmadı. Bu statik arama, bütün davranışların tüm Android cihazlarında çalıştığı iddiası değildir.

## Renk rolleri

| Rol | Açık mod | Koyu mod |
| --- | --- | --- |
| Ana metin | `#152449` | `#EDF1FF` |
| Yardımcı metin | `#566581` | `#B7C2DB` |
| Marka / mercan metin | `#B73B35` | `#FF9A90` |
| Ana işlem zemini / metni | `#08164D` / beyaz | `#9BDCEF` / `#08164D` |
| Araç simgesi / zemini | `#B73B35` / `#FFEAE7` | `#FF9A90` / `#4D292D` |
| Arka plan / kart | `#F5F7FC` / beyaz | `#080E24` / `#192947` |

Oranlar her ekranda zorunlu bir yüzde olarak uygulanmaz. Nötr yüzeyler ve okunaklı yazılar çoğunluğu oluşturur; mercan marka ve araç simgelerinin ortak vurgusudur. Buz mavisi koyu mod ana işlem düğmelerinde kalır. Çıktı sunumunun renk teması ayrı kalır.

`StudioContrastTest`, seçilen normal metin / yüzey ve düğme çiftlerinde en az 4.5:1; alan kenarlığı için en az 3:1 kontrol eder. Bunlar [W3C metin kontrastı](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) ve [kontrol kontrastı](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) eşiklerinden alınan tasarım ölçütleridir. Bütün uygulama için erişilebilirlik sertifikası değildir.

[Mevcut Figma referansı](https://www.figma.com/design/yNHHPygq0vPFBIEnpo6DP8): ana renk değişkenleri, marka metni, tanıtım kartı metni ve simge tonları eşleştirildi. Figma eski araç sayısını / örnek ekranları içerir; yeni PDF işlevlerinin kanıtı Android uygulamasıdır.

## Doğrulama kaydı

Son uygulama koduyla debug derleme, Android test APK'sı, birim testleri ve lint tamamlandı. **30/30 birim testi** ve aşağıdaki **31/31 ilgili emülatör kontrolü** geçti. Aynı API 36 `ofice_qa` emülatörünün pencere / yoğunluk / yazı ayarları kullanıldı; bunlar ayrı fiziksel telefonlar veya tabletler değildir.

| Yapılandırma | Sonuç | Kapsam |
| --- | --- | --- |
| Telefon — 1080×2400 / 420 dpi / 1× yazı | 15/15 | PDF sayfa motoru, kaynak koruması, iptal / hatalı toplu ekleme, okuyucu / aktarım, kısayollar ve eski video taslağı |
| Tablet ölçüsü — 1600×2560 / 240 dpi | 5/5 | Araç akışları, PDF oluşturma → okuyucu → dönüş, çıktı aktarımı ve görünüm |
| Dar ekran — 720×1440 / 360 dpi / 2× yazı | 5/5 | Aynı akışlar; dosya seçimi ve uzun metnin kaydırılarak erişilebilirliği |
| Lacivert koyu mod — normal telefon ölçüsü | 5/5 | Aynı akışlar ve mercan ikon / metin / bölüm kontrastı |
| Arapça / sağdan sola — koyu telefon görünümü | 1/1 | Ana ekran, görsel içe alma ve PDF araçlarına erişim / görsel kayıt |

İlk geniş regresyon koşusunda 82 testten 81'i geçti. Tek hata, yeni kısayol testindeki yanlış video düğmesi etiketiydi; düzeltildi ve son kısayol kontrolleri tüm dört yapılandırmada geçti. Sonraki dar ekran testinde tembel okuyucu önizlemesine kaydırma eklendi; Espresso paylaşım kaydı eşzamanlı güncellenirken oluşan test koşucusu yarışı giderildi. Arapça ekran kaydı pencerenin ilk çizimini bekleyecek şekilde düzeltildi. İlk mercan koşusunu Android sistem çökmesi kesti (`INSTRUMENTATION_ABORTED: System has crashed`); bu koşu başarı sayılmadı, emülatör yeniden başlatılarak son kontroller tamamlandı. Çökmenin kök nedeni belirlenmiş değildir. Son değişikliklerden sonra 82 testin tamamının tekrar aynı kodla çalıştığı iddia edilmez.

Lint **0 hata / 37 uyarı**. Üç `TrustAllX509TrustManager` uyarısı, geçişli Bouncy Castle bağımlılığının uygulamanın çağırmadığı TLS sınıflarındadır; bastırılmadı. Uygulamada INTERNET izni veya bu sınıflara bağlantı yoktur. Diğer uyarılar çoğunlukla bağımlılık güncelliği, kullanılmayan kaynaklar ve KTX önerileridir. Bu durum bağımlılık bakımını gereksiz kılmaz.

22 dilde 195 ortak metin anahtarı, biçim yer tutucuları ve üretilen Android kaynakları doğrulandı; bu dil uzmanı incelemesi değildir. Jev'in çevrimdışı izolasyon denetimi kaynaklar ve debug APK için geçti; canlı API çağrısı yapılmadı. Graphify yerel AST grafiği güncellendi; Kotlin parser'ının kısmi çıkarım uyarıları gerçek Kotlin derlemesi / test sonucunun yerine kullanılmadı.

Kanıtlar ve makine okunur özet `output/development/pdf-tools-design/` içinde tutulur. Son debug APK SHA-256: `22422d44b67463f9eefdd50c206e30c02faa0c679c98d5e21f6bf204c429a292`. Dondurulmuş 0.1.8 (9) AAB özeti `fe11985fd8e4d361f7f2f6fe0ff3ac01705e8ddda450d6a76a26815011f19345` olarak korundu. Emülatör normal ölçü / 1× yazı / açık mod / en-US ayarlarına geri alındı.

Release küçültme / imzalı paket doğrulaması kullanıcı yeni sürüm istediğinde yapılır; fiziksel düşük donanım, eski Android API'leri, 2.000 karmaşık sayfa, pil / FPS / en kötü bellek bu emülatör testlerinden garanti edilmez.

## Sonraki ürün işi

Telefon medya biçimlerini işlem bazında genişletmek: önce HEIC / HEIF ve ek Android video kapsayıcı / codec yollarını gerçek decoder ile ayrı ayrı doğrulamak. Kullanıcı test geri bildirimi veya yayın isteği bu sırayı değiştirebilir. Belge okuyucuları, toplu iş akışları ve gelişmiş video düzenleme sonraki ürün kalemleridir.
