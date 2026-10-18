# 18 October — account privacy messages

[Account UMP QA](../development/ADVERTISING_ACCOUNT_QA.md) adds an explicit debug-only Birlevo account profile with aligned manifest/SDK application IDs and separate EEA, regulated-US and other-region tests. The banner unit remains Google's sample; default debug remains a sample account and release activation stays closed. The owner chose English-only messages. Next is the final user-facing Home placement and persistent privacy entry, followed by integrated resident-SDK/media qualification; audience, readiness and exact candidate disclosures remain gates. No new signed release is requested.

# 18 October — advertising foundation

[AdMob/UMP foundation](../development/ADVERTISING_FOUNDATION.md) adds explicit debug consent testing, current-provider eligibility, cancellable waits and safe lifecycle invalidation without media-engine coupling. The 128-artifact exact runtime and proprietary/composite notices are reviewed together with [privacy/Console drafts](../releases/ADVERTISING_DISCLOSURE_DRAFT.md). Release activation and placements remain disabled. Next is step 6: register Birlevo/messages in the existing AdMob account, then qualify test placements and conservative caps. No signed release or site/Play publication. Earlier entries are historical.

# 17 October — storage cleanup safety

[Cleanup ownership](../development/STORAGE_CLEANUP_SAFETY.md) now gates startup sweeps on recovered job/session references, retains completed outputs across recipe changes, and prevents aged share restaging from deleting its source. Known file patterns and flat canonical ownership checks preserve unknown files, project images and symlink targets. Next is first-production step 5, advertising foundation, consent/data behavior and exact commercial SDK review. Placements follow in step 6. No release is built. Earlier entries are historical.

# 17 October — long video and legacy frame reliability

[Video resource reliability](../development/VIDEO_RESOURCE_RELIABILITY.md) separates small legacy posters from full-resolution still export and repairs a repeated-seek defect that produced green legacy frames. Real 599-second encodes, source/result protection, cancellation/storage pressure, background completion and explicit process-restart retry are checked on the dedicated emulator matrix. Existing formats, quality settings and final dimensions remain. Next is first-production step 4, storage/cleanup safety; advertising follows in steps 5–6. No release is built. Earlier entries are historical.

# 17 October — image-heavy external PDF resources

[External PDF resource handling](../development/EXTERNAL_PDF_RESOURCES.md) completes first-production step 2: descriptor-backed isolated parsing/copying, bounded disk scratch, storage/cancellation/death cleanup and exact image/text preservation near the input ceiling. Existing page/format/quality limits remain. Next implementation is first-production step 3, long/high-resolution video and legacy preview resources. Advertising remains steps 5–6; new formats, broader Office and OCR remain post-launch. No release is built by this step. Earlier entries are historical.

# 16 October — first production scope agreed

The [first production plan](FIRST_PRODUCTION_PLAN.md) is the current delivery order: existing capabilities, PDF/video/storage reliability, advertising foundation and placements, integrated qualification, then an explicitly requested release candidate, closed-test readiness and controlled production rollout. New formats, broader Office and OCR are deferred. Immediate implementation: image-heavy external PDFs. Existing quality/resolution choices remain; this planning step does not build or publish a release. Earlier entries are historical.

# 16 October — presentation export resources

[Presentation PDF/slideshow resource work](../development/PRESENTATION_EXPORT_RESOURCES.md) retains common native fonts and text while spooling each image separately. The same API 28 24-slide workload falls from 77.08 to 5.33 MiB highest sampled native allocation, with exact baseline page/image/video appearance. All quality/resolution options remain. Slideshow adds operation admission and live disk/cancellation guards. Next: image-heavy external PDFs near their byte ceiling. No release was requested; earlier entries are historical.

# 16 October — legacy Android and multipage PDF memory

[Legacy Android / PDF profiling](../development/LEGACY_ANDROID_AND_PDF_MEMORY.md) fixes undersized slide/photo-PDF decoding and native page retention using the existing PDFBox dependency and private disk scratch. The 100-photo 4096 setting and 2000 vector-page worker are exercised on emulators; requested quality, page order and source/result protection remain. [Operation admission](../development/OPERATION_RESOURCE_BUDGETS.md) and English developer naming remain in place. Next: presentation PDF/slideshow resource retention, followed by image-heavy external PDFs near their byte limit. Release preparation still requires an explicit request. Earlier entries are historical.

# 16 Ekim — ZIP uyumu ve cihaz çalışma alanı

[ZIP ve kapasite adımı](../development/DEVICE_CAPACITY_AND_ZIP_LIMITS.md) tamamlandı: 512 MiB ortak içerik, ZIP başlık payı, gerçek boş alana göre çalışma bütçesi, alan azalmasında kaynak / sonuç koruması. 143 JVM / 10 emülatör testi ve bağımsız büyük dosya çözümü geçti. Sonraki iş görsel / video / PDF için işlem bazlı bellek ve geçici disk ön kontrolüdür; kalite ve son çözünürlük korunur. Yeni release üretilmedi; aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — toplu / ZIP / klasör ortak regresyonu

[Zincir doğrulaması](../features/batch/BATCH_ZIP_FOLDER_FLOW_AND_REGRESSION.md) ve [güncel kapalı test kontrol listesi](../testing/CURRENT_CLOSED_TEST_CHECKLIST.md) tamamlandı. 19 hedef / 22 ZIP / 50 çıkarılan dosya, onay/iptal/devam ve RTL dahil UI kontrolü vardır. ZIP dosya adı yönü düzeltildi. Sonraki iş toplu çıktı/ZIP okuyucu dosya boyutu sınırlarını uyumlu hale getirmektir. Yeni yayın yalnız kullanıcı isteğinde; aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — ZIP klasöre çoklu çıkarma

[Çoklu ZIP seçimi](../features/archives/ZIP_MULTI_SELECTION_AND_FOLDER_EXTRACTION.md) eklendi: alt klasörleri koruyan yeni hedef, mevcut dosyalara yazmama, iptal/hata temizliği ve süreç kesintisi bildirimi. Dosyalar yeniden kodlanmaz; önceki tekil kayıt, sınırlar ve kopya onayları korunur. Sonraki iş toplu dönüştürme → ZIP → klasör zincirini ortak emülatör regresyonuna ve kapalı test kontrol listesine eklemektir. Yeni release yalnız kullanıcı isteğinde. Aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — toplu belgeler

[Belge toplu tarifleri](../features/batch/BATCH_DOCUMENTS_AND_APPROVED_RECIPES.md) eklendi: altı hedef, girdi kodlaması/CSV ayırıcısı, dosya başına Excel sayfası ve ham değer/formül seçimi, ayrı kayıp/kopya onayları, atomik devam ve ZIP kaydı. Tam Office uyumluluğu veya PDF girdisi bu kuyruğa eklenmedi. Yeni bağımlılık veya release yoktur. Sonraki ürün işi güvenli çoklu ZIP çıkarma ve sağlayıcı ad çakışması/yarım hedef kurallarıdır. Aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — ZIP açma/çıkarma

[ZIP okuyucu](../features/archives/ZIP_READING_AND_SAFE_EXTRACTION.md) ZIP32 stored/deflate arşivleri denetler, içerikleri listeler ve dosyaları ayrı Android kayıt penceresiyle özgün baytlarıyla çıkarır. Kaynak/atomik kayıt, iptal, süreç yeniden açma ve ad/CRC/boyut denetimi vardır. Şifreli/ZIP64/7Z/RAR ve klasöre toplu çıkarma ayrı kapsamdır. Yeni SDK veya release yoktur. Sıradaki iş belge toplu tariflerini açık kayıp/uyumluluk ve boyut kurallarıyla tasarlamaktır. Aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — toplu video/ses

[Video/ses toplu tarifleri](../features/batch/BATCH_VIDEO_AUDIO_AND_SHARED_QUEUE.md) eklendi: AVC/HEVC MP4, VP8/VP9 WebM; WAV/M4A/OGG/FLAC/MP3; mevcut kalite/boyut/bit hızları; dosya bazında destek hatası; kaynak/onay koruması ve üç ayrı atomik kuyruk. Eski görsel version 1 kaydı korunur. Yeni SDK veya release yoktur. Sonraki ürün işi güvenli ZIP açma/çıkarma, ardından belge toplu tariflerinin kayıp/onay kapsamını değerlendirmektir. Aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — toplu görsel ve ZIP

[İlk toplu paket](../features/batch/BATCH_IMAGES_AND_ZIP.md) görseller için tamamlandı: 20 dosya/512 MiB kaynak sınırı, JPEG/PNG/WebP/BMP, mevcut kalite ve boyutlar, tek ağır iş kuyruğu, sonuç başına onay, atomik devam, dosya seçerek ZIP oluşturma/kaydetme ve 22 dil. Yeni SDK veya release yoktur. Sonraki ürün işi video ve ses için aynı korumaları taşıyan toplu tariflerdir. ZIP açma, şifreli/7Z/RAR arşivler ve karışık dosyalar bu pakette desteklenmez. Aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — codec matrisi ve VP9

[Codec matrisi ve VP9 paketi](../features/CODEC_MATRIX_AND_VP9_SUPPORT.md) tamamlandı: VP9 Profile 0 / 8-bit SDR girişi, VP9/Opus WebM çıktısı, ortak kare-codec kaydı, oturum/kopya onayı ve 22 dil. Yeni SDK veya gömülü codec yoktur; ticari telif/envanter ile patent kapsamı ayrı kaydedildi. 109 JVM ve 16 emülatör testi, 7 VP9 / 8 katmanlı dosya ve 14 karenin bağımsız çözümü geçti. AV1/APV aday envanteri desteğe eşit sayılmaz. **Sıradaki ürün işi toplu dönüştürme / ZIP akışıdır.** Yeni release yalnız kullanıcı isteğinde; aşağıdaki kayıtlar tarihseldir.

# 14 Ekim — DOCX/XLSX belge ekranı

[Sınırlı DOCX/XLSX desteği](../features/documents/DOCX_XLSX_UI_INTEGRATION.md) uygulamaya bağlandı: metin/tablo okuma, çalışma sayfası ve formül/kayıtlı sonuç seçimi, sade DOCX ve metin hücreli XLSX çıktı, açık kayıp onayı, kaynak/oturum koruması ve 22 dil. Formül hesaplaması veya tam Office görünüm uyumluluğu iddia edilmez. Yeni SDK/font yok; mevcut dosya türü, kalite ve çözünürlük yolları korunur. Yeni release yalnız kullanıcı isteğinde. Sıradaki kapsam codec adaylarını ticari koşullar ve cihaz desteğiyle değerlendirmek, ardından toplu/ZIP akışını ele almaktır. Kritik testçi hataları önceliklidir. Aşağıdaki kayıtlar tarihseldir.

# 14 Ekim — DOCX XLSX motor denemesi sonrası sıra

[DOCX/XLSX teknik ve ticari lisans denemesi](../features/documents/DOCX_XLSX_ENGINE_AND_LICENSE_REVIEW.md) tamamlandı: yerel paragraf/tablo ve hücre/sayfa okuma, açık kayıp raporu, formül–kayıtlı sonuç ayrımı, basit DOCX ve metin XLSX yazımı test kodunda doğrulandı. Yeni SDK/font yok; Office MIME desteği üretime açılmadı. Sonraki somut iş **seçilen alt kümeyi belge ekranına, sayfa seçimine ve açık kayıp/çıktı tercihlerine bağlamak**; oturum, iptal/kayıt, kaynak koruması ve 22 dil birlikte tamamlanacak. Gerçek Word sayfa görünümü ve Excel/Office renderer uyumluluğu kanıtlanmadı. Yeni release yalnız kullanıcı istediğinde. Aşağıdaki kayıtlar tarihseldir.

# 14 Ekim — düzenlenebilir PPTX için RTL devamı

[RTL ve font kapsamı](../features/presentations/PPTX_RTL_AND_FONT_SCOPE.md) Latin metne ek olarak modern Arapça/Farsça, karışık rakamlar ve metin için tamamlandı. Sağdan sola metin ayrı düzenlenebilir kelime/yön kutularında gruplanır; font dosyası gömülmez. Bağımsız okuyucu kanıtı gerçek PowerPoint uyumluluğu iddiası değildir. Görünümü koruyan seçenek ve mevcut kalite/çözünürlük yolları kalır. Sonraki somut iş **DOCX/XLSX için yerel motor, biçim kayıpları ve ticari lisans denemesi**; ardından codec adayları ve toplu/ZIP akışıdır. Kritik testçi hataları önceliklidir. Yeni release yalnız kullanıcı isteğinde derlenir; fiziksel cihaz veya PowerPoint kurulumu beklenen kullanıcı adımı değildir. Aşağıdaki kayıtlar tarihseldir.

# 14 Ekim — düzenlenebilir PPTX sonrası sıra

[Latin metin için düzenlenebilir PPTX](../features/presentations/PPTX_EDITABLE_SUBSET.md) ayrı, açık sınırları olan çıktı seçeneği olarak tamamlandı. RTL/diğer yazılar/emoji sessizce kaybedilmez; mevcut görünümü koruyan seçenek erişilebilir kalır. Sıradaki uyumluluk işi **karışık yönlü metin koşuları ve font eşlemesi için bağımsız okuyucu kanıtı** hazırlamaktır. Genel Office içe aktarma ayrı zengin öğe modeli gerektirir. Kritik testçi hataları önceliklidir; yeni sürüm yalnız kullanıcı isteğinde derlenir. Fiziksel cihaz veya PowerPoint kurulumu beklenen kullanıcı adımı değildir. Aşağıdaki kayıtlar tarihseldir.

# 13 Ekim — ortak yerleşim sonrası güncel sıra

[Sunumların ortak ölçüm modeli](../features/presentations/SHARED_PRESENTATION_LAYOUT_AND_RTL.md) tamamlandı: gerçek satır/font/paragraf yönü metaverisi, arayüzden bağımsız çıktı ve eski görünüm/taşma kuralları doğrulandı. Sıradaki somut iş bu modelle **düzenlenebilir PPTX alt kümesi** hazırlamak ve bağımsız okuyucuda yeniden düzenleme/font/RTL farklarını ölçmektir. Uyumsuz içerik sessizce kaybedilmeyecek; mevcut görünümü koruyan PPTX seçeneği kalacak. Gerçek Office sonucu yoktur; PowerPoint kurulumu kullanıcı adımı olarak beklenmez. Yeni sürüm yalnız kullanıcı isteğinde derlenir. Aşağıdaki kayıtlar tarihseldir.

# 13 Ekim — erişilebilirlik sonrası güncel sıra

[Arayüzün TalkBack, yüzde 200 yazı ve RTL kontrolleri](../design/ACCESSIBILITY_AND_RTL.md) tamamlanan yollar ve ölçüm sınırlarıyla kaydedildi. Seçim/başlık/panel/kalite bilgisi geliştirildi; PDF önizleme hata döngüsü düzeltildi. Sıradaki somut ürün işi sunum için ortak yerleşim, RTL ve font ölçümleridir; düzenlenebilir PPTX alt kümesi bu kanıta göre açılacaktır. Kritik testçi hataları önceliklidir. Kullanıcı istemeden yeni release hazırlanmaz; gerçek Office testi yapılmış sayılmaz ve PowerPoint kurulumu kullanıcıya bekleyen görev olarak verilmez. Aşağıdaki kayıtlar tarihseldir.

# 13 Ekim — proje ve çalışma yönetimi

[Kayıtlı proje silme ve ana sayfadan çalışma temizleme](../development/PROJECT_AND_WORKSPACE_MANAGEMENT.md) eklendi. Özgün/dışa aktarılan dosyalar ve başka kayıtların kullanabileceği proje görselleri korunur. Kullanıcı bu aşamadaki Android testlerini yalnız emülatörde istedi; fiziksel cihaz testi veya PowerPoint kurulumu geliştirmeyi bekleten bir kullanıcı görevi değildir. Gerçek Office doğrulaması mevcut bağımsız PPTX kanıtından ayrı, yapılmamış bir kontrol olarak kalır. Sonraki işler arayüzün büyük yazı/TalkBack/RTL doğrulaması ve ortak yerleşim ölçümlerinden sonra düzenlenebilir PPTX alt kümesidir. Kritik testçi hataları önceliklidir. Yeni release paketi kullanıcı isteğini bekler; 0.1.11 (12) değişmez. Aşağıdaki kayıtlar tarihseldir.

# 10 Ekim — test yayını ve güncel sıra

Kullanıcı isteğiyle [0.1.11 (12) test paketi](../releases/RELEASE_0.1.11.md) hazırlandı; önce dahili testte fiziksel cihaz geri bildirimi, ardından aynı AAB ile kapalı test. Kritik testçi hataları önceliklidir. Gerçek PowerPoint kontrolü için [çevrimdışı örnekler](../testing/PPTX_PRE_RELEASE_CHECKS.md) hazırdır; henüz gerçek Office sonucu yoktur. Sonraki ürün geliştirmesi ortak yerleşim/RTL/font ölçümleri ve düzenlenebilir PPTX kabul alt kümesidir. Genel Office desteği veya reklam SDK'sı açılmadı. Aşağıdaki kayıtlar tarihseldir.

# 9 Ekim — PPTX dışa aktarımı sonrası güncel sıra

Mevcut sunumlardan görünümü koruyan PPTX üretim motoru ve ekranı tamamlandı. Çözünürlük/not tercihi, açık düzenlenebilirlik kaybı, gerçek önizleme/boyut, iptal, kayıt/açma/paylaşma, atomik ayarlar ve kalıcı arka plan işi vardır. [Kapsam ve doğrulama](../features/presentations/PPTX_APPEARANCE_PRESERVING_EXPORT.md). Genel PPTX içe aktarma ve düzenlenebilir nesne çıktı desteği açılmadı. **Sıradaki somut iş gerçek Office kontrolü ve ortak yerleşim/RTL/font ölçümleriyle düzenlenebilir PPTX alt kümesi**. Fiziksel cihaz ve yeni kodun release doğrulaması bekler; 0.1.10 değişmedi. Aşağıdaki kayıtlar tarihsel sıradır.

# 9 Ekim — PPTX denemesi sonrası güncel sıra

PPTX uyumluluk/ticari lisans denemesi tamamlandı. [Kapsam ve kanıt](../features/presentations/PPTX_COMPATIBILITY_AND_LICENSE_REVIEW.md). Genel Office desteği açılmadı; RTL/font ve zengin öğe modeli kabul bekler. **Sıradaki somut iş mevcut sunumlar için görünümü koruyan PPTX dışa aktarma motoru/ekranı**: çözünürlük/not tercihi, açık düzenlenebilirlik kaybı, iptal/kayıt/geçerli sonuç. Düzenlenebilir aktarım ortak yerleşim ve Office/RTL doğrulamasından sonra ayrı seçenek olacak. Yeni yayın paketi kullanıcı isteğini bekler. Aşağıdaki sıra kayıtları tarihseldir.

# 8 Ekim — güncel geliştirme durumu

Dosya odaklı arayüzden sonraki zamanlamalı görsel / slayt MP4 üretimi geliştirme kodunda tamamlandı. Süre / sıra, tema / düzen kopyası, gerçek çıktı, kalıcı kayıt, arka plan sırası ve video araçlarına aktarım vardır. [Kapsam ve doğrulama](../features/presentations/IMAGE_AND_SLIDE_VIDEO.md). Yeni yayın derlemesi kullanıcı isteğini bekler. Sonraki somut ürün paketi **tam ekran sunum modu, notlar / zamanlayıcı ve slayt görseli çıktısı**; PPTX uyumluluğu ayrıca teknik denemeyle değerlendirilir. Kritik testçi hataları önceliklidir; aşağıdaki eski sıra kayıtları tarihseldir.

# Mobil içerik stüdyosu — ürün planı

Güncel devam — 8 Ekim 2026: [dosya odaklı sade arayüz](../design/FILE_FOCUSED_INTERFACE.md) bütün mevcut araçlara uygulandı; yeni yayın derlemesi yapılmadı. Sonraki yeni ürün adımı görsel/slaytlardan zamanlamalı MP4 üretimi. Eski yol haritasının tamamlanan/bekleyen ayrımı için [güncel iş sırası](CONVERSION_SCOPE_AND_NEXT_STEPS.md) esas alınır.

Tarih: 4 Ekim 2026
Durum: Yalnızca Android. Tek geliştiriciyle 11 Ekim 2026'ya kadar Google Play kapalı testine ilk AAB'yi gönderme hedefi belirlendi. Bir haftalık kapsam [CLOSED_TEST_PLAN.md](../testing/CLOSED_TEST_PLAN.md) içindedir; aşağıdaki kapsam ilk genel yayına uzanan ürün yönünü anlatır.

## 1. Ürün kararı

6 Ekim arka plan devamı: video / fotoğraf PDF üretimi için kullanıcı başlatmalı ön plan servisi, tek ağır iş sırası, iptal / süre sınırı, kalıcı iş durumu ve tamamlanmış çıktı geri yüklemesi eklendi. [Kapsam ve doğrulama](../development/BACKGROUND_PROCESSING.md). Fiziksel cihaz / gerçek düşük depolama ve sonraki release R8 doğrulaması bakım listesinde kalır; sıradaki ürün işi çıktı biçimleri, ses aracı ve codec genişleme denemeleridir. [Özel GitHub](../development/GITHUB_AND_VERSION_CONTROL.md) ile sürüm takibi başladı. Yeni Android yayın paketi oluşturulmadı.

6 Ekim medya oturumu devamı: video kaynak / kesim / kalite / çözünürlük ve fotoğraf PDF kaynak / sıra / çözünürlük kaydı tamamlandı. Atomik kayıt, hata / yeniden deneme ve gerçek süreç kapatma sonrası yeniden üretim doğrulandı. [Kapsam ve açık sınırlar](../development/MEDIA_SESSION_RESILIENCE.md). Sıradaki somut iş uzun işlemlerin arka plan / bildirim / kalıcı iş durumu yönetimidir. Yarım kalan işlemin otomatik sürdürülmesi bu pakette yoktur. Yeni release hazırlanmadı; aşağıdaki önceki sıra kararları tarihseldir.

6 Ekim metin / tablo devamı: TXT / Markdown kaynak metni ve CSV / TSV okuyucusu, türüne uygun TXT / CSV / TSV / PDF hedef seçimi, seçilen bölüm / satırın sunuma kopyalanması ve PDF okuyucu geçişi geliştirme kodunda tamamlandı. [Uygulanan yollar ve açık sınırlar](../features/documents/TEXT_TABLE_READER_AND_CONVERSION.md). DOCX / XLSX / PPTX ayrı motor denemesi bekler. Sıradaki somut uygulama işi oturum ve uzun işlem dayanıklılığıdır. Yeni release hazırlanmadı; aşağıdaki önceki adım kayıtları tarihseldir.

6 Ekim güncel dönüşüm kararı: kullanıcı hedefi seçilebilir çıktı biçimleri ve araçlar arası yeniden kullanımdır. Telefon medya girişleri tamamlandı; sıradaki iş belge / tablo okuyucusu ile ortak hedef seçimi, ardından oturum dayanıklılığı ve çıktı / ses / codec genişlemesidir. Bütün girişleri tek çıktı biçimine çevirmek uzun vadeli hedefi karşılamaz; doğrulanan giriş → çıktı yolları birlikte büyütülür. [Güncel sıra, aday biçimler ve bitiş koşulları](CONVERSION_SCOPE_AND_NEXT_STEPS.md). Bu kayıt bir uygulama desteği veya yeni sürüm değildir.

6 Ekim son devamı: geliştirme kodunda telefon medya girişleri genişletildi. Durağan HEIC / HEIF / AVIF görsel, slayt ve fotoğraf PDF araçlarında ortak kullanılır; MOV / 3GP / MKV / WebM ile HEVC 8 bit SDR / VP8 girişleri gerçek cihaz çözücüsü koşuluyla eklenmiştir. Çıktı ve kalite seçenekleri korunur. Başlık arka planla bütünleşir ve dil düğmesi zeminsiz metindir. Son kullanıcı düzeltmesine göre lacivert tanıtım kartı korunur, yalnız içindeki simgelerin ayrı mavi kutuları kaldırılır. [Kapsam ve test sınırları](../design/PHONE_MEDIA_FORMATS_AND_SIMPLE_HEADER.md). Bu işten sonra belge / tablo okuyucuları için yerel, ölçülü kapsam seçimi; ardından oturum dayanıklılığı, toplu iş akışları ve ileri video / sunum işleri gelir. Bu kod henüz release paketine alınmadı.

0.1.8 (9) ilk uyumluluk paketini uygular: işlem bazlı giriş / çıktı yardımı, ortak içerik kontrolleri, ayrı hata nedenleri ve 22 dilde yeni metinler. Bu dondurulmuş pakette yeni format motoru eklenmedi. [Paket kapsamı ve doğrulama](../testing/LANGUAGE_AND_FILE_COMPATIBILITY_0.1.8.md).

6 Ekim devamı: geliştirme kodunda **harici PDF okuyucu, sayfa gezinme / yakınlaştırma, PDF kopyası ve PNG sayfa aktarımı** tamamlandı. Ardından metin / vektörleri koruyan **PDF birleştirme, seçili sayfaları yeni belgeye çıkarma, sıralama, döndürme, sayfa önizleme ve geri alma** eklendi. İşlevsiz tanıtım simgeleri çalışan kısayollara dönüştürüldü; eski video taslakları editörü açıyor. Metin / eylem / vurgu renk rolleri açık ve koyu mod için düzenlendi. [Yeni kapsam, sınırlar ve doğrulama](../features/pdf/PDF_PAGE_TOOLS_AND_UI_REVIEW.md). Release üretilmedi; yeni paket yalnız kullanıcı istediğinde hazırlanır. Sıradaki somut iş telefon medya formatlarını gerçek motor desteğiyle genişletmek. PNG'ye düzleştirme belge düzenlemenin yerine geçmez.

6 Ekim ilk incelemesinde belirlenen sıra: 0.1.7 Alpha incelemeye gönderildi (kullanıcı bildirimi). Önce somut dil / dosya uyumluluk düzeltmeleri ve işlem bazlı destek kaydı; ardından harici PDF okuyucu / sayfa araçları, telefon medya biçimleri, belge okuyucuları, oturum dayanıklılığı ve video / sunum gelişimi. İlk paket 0.1.8'de tamamlandı. Reklam SDK'sıyla birlikte Play beyanları güncellenir. Bu sıra aşağıdaki uzun vadeli format / yetenek hedefini azaltmaz. [İlk inceleme ve bitiş ölçütleri](../development/LOCALIZATION_FORMAT_AND_JEV_REVIEW.md).

Android telefon ve tabletlerde sunum hazırlamayı, kısa video düzenlemeyi ve video / görsel dosyalarının boyutunu küçültmeyi kolaylaştıran bir içerik stüdyosu geliştirilecek.

Ana vaat: **İçeriğini ekle, kolayca düzenle, sunuma veya kısa videoya hazırla.**

Kullanıcı tarafından belirlenenler:

- Yalnızca Android uygulaması.
- Farklı telefonlarda ve tabletlerde kullanılabilen, ekran alanına uyarlanan arayüz.
- folkloreArchive projesindeki dil seçeneklerinin tamamı: başlangıçta en az 22 dil / yerelleştirme seçeneği.
- İlk sürümde video ve görsel dosyalarını sıkıştırma; çözünürlüğü ayrıca değiştirebilme.
- Geniş dosya türü desteği rekabet hedefidir; destek, her dosya türü için doğrulanmış kullanım yollarına göre büyütülecek.
- Kullanımı kolay bir deneyim.
- Sunum hazırlayanlar ve kısa video düzenleyenler ana kullanıcılar; video editörü klip, yazı, ses ve görsel katmanlarının birlikte çalıştığı kolay bir zaman çizelgesi sunmalı.
- Orijinal içerikleri koruma ve projeye daha sonra devam etme.
- Kullanıcı arayüzünde, şablonlarda ve ürün tanıtımında sosyal platform adlarını kullanmama.
- Ana gelir kaynağı olarak uygulama içi reklamları kullanma.

Başlangıç için kabul edilen yaklaşım:

- İlk sürümde iki kullanım alanını temel özelliklerle sunmak.
- Türkçe ve İngilizce kaynak metinlerden ilerlemek; ilk yayın öncesinde 22 yerelleştirmenin tamamını çeviri ve arayüz kontrolünden geçirmek.
- İlk projeyi hesap açmadan hazırlayabilmek.
- Dosyaları ve düzenlemeleri öncelikle cihazda saklamak.
- Otomatik altyazı için ayrı, internet gerektiren bir işlem kullanmak.
- Her iki modda aynı görsel, yazı stili ve proje varlıklarını yeniden kullanabilmek.
- Temel düzenleme, standart PDF / MP4 çıktısı ve proje kaydını ücretsiz sunmak.
- Ana sayfa, projeler ve şablonlarda alt banner kullanmak; tamamlanmış işler arasındaki uygun geçişlerde sınırlı tam ekran reklamı denemek; ek şablonları isteğe bağlı ödüllü reklamla açmak.

Kullanıcının her işlemden önce ve editten sonra tam ekran reklam önerisi değerlendirildi. Ürün önerisi, kesme / yazı ekleme / renk değiştirme gibi her düzenleme adımında reklam göstermemektir. Tam ekran yerleşimler tamamlanmış görevler arasındaki doğal geçişlerle sınırlandırılır; sıklık ve gelir sonuçları beta sırasında ölçülür. Alt banner önerisi uygun gezinme ekranlarında plana dahil edildi.

Gelir modeli reklam destekli ücretsiz kullanım olarak belirlendi. Geliştirme tek kişiyle yürütülecek; 11 Ekim 2026 kapalı test gönderimi hedefleniyor ve Play Console hesabı hazır. Bütçe, uygulama adı, ilk genel yayın pazarı, hedef yaş grubu ve genel yayın takvimi henüz belirlenmedi. Aşağıdaki reklam sıklıkları ve gelir hesapları başlangıç önerileridir; gerçek kullanım verileriyle güncellenecek.

## 2. Kullanıcılar ve tamamlanacak işler

| Kullanıcı | Tamamlamak istediği iş | Ürünün sağlayacağı sonuç |
| --- | --- | --- |
| Öğrenci veya eğitmen | Telefon veya tablette bir konu anlatımı sunumu hazırlamak | Tutarlı şablon, okunaklı slaytlar, PDF ve uygulama içi sunum |
| Kısa video içerik üreticisi | Birkaç videodan kısa, dikey bir edit hazırlamak | Kolay kesme, yazı ve altyazı, ses ayarı, paylaşılabilir MP4 |
| İki formatı kullanan kişi | Aynı anlatımı farklı yerlerde kullanmak | Projeyi kopyalama, görselleri ve stilleri yeniden kullanma; sonraki sürümde slaytlardan kısa video |
| Dosya boyutunu küçültmek isteyen kişi | Video veya görselini daha kolay saklamak ve paylaşmak | Kalite önizlemesi, boyut tahmini, sıkıştırılmış yeni dosya |

İlk kullanıcı araştırması önerisi: 5 sunum hazırlayan kişi ve 5 kısa video hazırlayan kişiyle mevcut işlerini gözlemlemek. Hangi işlemi tekrar yaptıkları, nerede yardım aradıkları ve neyi dışa aktardıkları kaydedilecek. Bu sayı bir araştırma başlangıcıdır; pazar büyüklüğü kanıtı değildir.

## 3. Kolay kullanım ilkeleri

1. Başlangıç ekranında iki ana eylem: **Sunum oluştur** ve **Video düzenle**. Altlarında küçük bir **Dosya küçült** kısayolu; dosya türü seçildiğinde uygun sıkıştırma araçları açılacak.
2. Alt gezinmede üç alan: Ana sayfa, Projeler, Şablonlar. Ayarlar ikincil konumda olacak.
3. İlk kullanımda zorunlu tanıtım turu veya hesap açma olmayacak. Kullanıcı örnek şablonla hemen başlayabilecek.
4. Seçili içerikle ilgili araçlar açılacak. Yazı seçildiğinde yazı araçları, video seçildiğinde kesme ve ses araçları gösterilecek.
5. Ana işlemler görünür ve dokunmaya uygun olacak. Daha ayrıntılı ayarlar bir alt panelde açılacak.
6. Hizalama çizgileri, hazır yazı hiyerarşisi, tema renkleri ve kadraj şablonları teknik kararları azaltacak.
7. Geri al / yinele ve otomatik kaydetme her iki editörde aynı yerde olacak.
8. Kaydetme durumu açık olacak. Uygulama kapatılıp açıldığında son kayıt geri getirilecek.
9. Dışa aktarmada önce amaca uygun hazır seçenekler sunulacak. Çözünürlük ve kodek gibi ayrıntılar gelişmiş ayarlarda kalacak.
10. Dosya erişimi ve mikrofon gibi izinler ilgili işlem başladığında istenecek.
11. Banner için ekranın alt bölümünde ayrı alan ayrılacak; içerik veya gezinme düğmeleri üzerine bindirilmeyecek. Reklam alanı belirgin biçimde ayrılacak ve **Reklam** etiketiyle tanınacak.
12. Düzenleme, önizleme, sunum gösterimi ve dosya üretme sırasında reklam araya girmeyecek. Reklam ağına erişilememesi temel araçları durdurmayacak.

Görünüş sade, içerik önizlemesi büyük olacak. Sunum editöründe slayt şeridi; video editöründe kliplerin ana hattı ile gerektiğinde açılan yazı, altyazı, görsel ve ses hatları kullanılacak. Telefonda seçili katmanın temel kontrolleri öne çıkacak; tablette zaman çizelgesi, varlıklar ve önizleme daha geniş yerleşebilecek. Video dışa aktarma zaman ayarları normal sunum hazırlama akışına zorunlu olarak eklenmeyecek.

## 4. Ana kullanım akışları

### Sunum hazırlama

Sunum oluştur → şablon seç veya boş başla → slaytlara yazı ve görsel ekle → slaytları sırala → önizle → PDF kaydet veya uygulamada sun.

Başlangıç şablonları: Ders / konu anlatımı, proje tanıtımı, ürün tanıtımı, portfolyo ve görsel hikâye. Her ailede küçük, tutarlı bir tasarım seti olacak.

Slayt işlemleri: Ekle, çoğalt, sil, sırala, başlık ve gövde yazısını değiştir, görseli kırp ve hizala, temayı uygula.

Sunum sırasında: Tam ekran gösterim, ileri / geri, isteğe bağlı zamanlayıcı. Konuşmacı notları düzenlenebilir; ayrı izleyici ekranı ve telefondan uzaktan kontrol sonraki aşamada değerlendirilir.

### Kısa video hazırlama

Video düzenle → galeriden klipleri seç → 9:16 kadrajı ayarla → kes ve sırala → yazı / altyazı / ses ekle → önizle → kalite / dosya boyutunu ayarla → MP4 kaydet → cihazın paylaşım menüsünü aç.

Altyazı akışı: Oluştur → metni gözden geçir → yanlış sözcükleri düzelt → zamanlamayı düzelt → bir stil seç.

Müzik akışı: Kullanıcının seçtiği ses dosyasını ekle → başlangıcı ve süresi ayarla → video sesiyle dengele. Uygulamaya ait bir müzik kataloğu ayrı bir içerik ve kullanım hakkı çalışması gerektirir; ilk genel yayında kullanıcı dosyalarıyla başlanır.

İlk sürümde hedef, dosya üretmek ve cihazın genel paylaşım menüsünü açmaktır. Kadraj seçenekleri en-boy oranıyla adlandırılır. Şablon adları anlatım, ders, ürün tanıtımı ve görsel hikâye gibi içerik amaçlarını tarif eder.

### Video veya görsel sıkıştırma

Dosya küçült → video veya görsel seç → **Dengeli**, **Daha küçük dosya** veya **Özel ayar** seç → önce / sonra önizlemesini ve tahmini boyutu incele → yeni dosyayı kaydet.

Dosya boyutunu küçültme ile piksel ölçülerini değiştirme ayrı ayarlardır. Kullanıcı uygun dosyalarda çözünürlüğü koruyup kaliteyi ayarlayabilir veya çözünürlüğü de düşürebilir. İlk sürüm tek dosyayla çalışır; toplu işlem daha sonra eklenir. Aynı video sıkıştırma seçeneklerine editörün dışa aktarma adımından da erişilir.

Video: Kalite / bit hızı, çözünürlük ve desteklenen çıktı profili. Görsel: JPEG / WebP kalite ayarı, en-boy oranını koruyarak boyutlandırma ve desteklenen çıktı formatı. PNG gibi kayıpsız formatlarda yanıltıcı bir kalite kaydırıcısı kullanılmaz; boyutlandırma veya uygun format değişimi seçenekleri sunulur.

Hedef MB seçeneğinde dosyanın süresi ve içeriğine göre ayar önerilir. İşlem öncesindeki boyut tahmindir; tamamlanınca gerçek boyut ve değişim oranı gösterilir. Seçilen hedefe kalite veya cihaz sınırları içinde ulaşılamıyorsa açıklanır. Zaten küçük / iyi sıkıştırılmış dosyalarda küçülme garanti edilmez.

Şeffaflığın, hareketin, HDR bilgisinin veya dosya özelliklerinin kaybolacağı dönüşümlerde işlemden önce açık uyarı gösterilir. Çıktı önizlemesi, karşılaştırma ve yeniden ayarlama bulunur. Orijinal dosya korunur; çıktı yeni dosya olarak üretilir.

### İsteğe bağlı ek şablon açma

Bu akış ilk genel yayından sonraki sürüm için planlanır.

Şablonlar → ek şablonu seç → ödülü açıkça gör → **Reklam izle, bu projede kullan** seçeneğine dokun → ödül doğrulandığında şablonu aç → düzenlemeye devam et.

Alternatif eylem: **Ücretsiz şablonlarla devam et**. Açılan şablon o projede kayıtlı kalır; projeyi yeniden açınca tekrar reklam istenmez. Reklam bulunamazsa kullanıcıya kısa bir durum mesajı ve ücretsiz seçenekler gösterilir. Reklamın süresi veya ödülün verilme anı hakkında sabit bir süre sözü verilmez.

### Araçlar arası birlikte çalışma

Sunum, video, okuyucu, dönüştürme ve sıkıştırma ayrı görevler olarak anlaşılır; ancak aynı medya alanını ve kullanıcı projelerini paylaşır. Kullanıcı bir çıktı aldıktan sonra onu bulup yeniden seçmek zorunda kalmadan uygun sonraki araca gönderebilir.

- İlk genel yayında galeriden alınan veya sıkıştırma / dönüştürme ile üretilen görsel ve videolar, sunum ya da video projesine eklenebilir.
- Geliştirme kodunda PDF sayfası düzleştirilmiş PNG olarak sunuma veya fotoğraf PDF aracına aktarılabilir; metin / bağlantı kaybı gösterilir. Video karesini kapak / slayt görseli yapma ve PDF sayfasını video sahnesine aktarma sonraki aşamadadır. PDF sayfasındaki metin bu görsel aktarımında düzenlenebilir olmaz.
- Sunumdan üretilen PDF ilk genel yayında okuyucuda açılıp paylaşılabilir. Slaytlardan zamanlamalı MP4 üretme daha sonraki ortak çıktı akışıdır.
- Video, görsel ve sunum editörlerinde renk paleti, yazı stilleri, logo ve kullanıcı varlıkları yeniden kullanılabilir. Her proje kendi düzenini ve geri alma geçmişini korur.
- Her çıktı eyleminde uygun devam seçenekleri gösterilir: **Video projesinde kullan**, **Sunuma ekle**, **PDF olarak aç** veya **Paylaş**. Kullanıcıya geçiş öncesi kaynak ve olası kalite kaybı anlatılır.
- Bir proje içindeki kırpma, renk, yazı ve katman ayarları mümkün olduğunca kaynak dosyayı değiştirmeden saklanır. Sıkıştırma, proje üzerinde çalışırken son dışa aktarma adımında uygulanır; böylece düzenleme boyunca art arda kayıplı kodlama yapılmaz. Bağımsız sıkıştırma çıktısı da yeni, yeniden kullanılabilir bir dosyadır.

## 5. Kapalı testten ilk genel yayına kapsam

Bir haftalık **kapalı test**, görsel / video sıkıştırma, tek video kesme ve görsel içerebilen sunumdan PDF akışlarını kullanır. Genel yayın için aşağıdaki kapsam kapalı test sonuçlarına göre sırayla tamamlanır. Böylece format genişliği ve ileri efektler, çalışan temel görevleri geciktirmez.

| Alan | İlk genel yayında hedef | Çalışır sayılması için |
| --- | --- | --- |
| Ortak çalışma alanı | Galeriden / dosyalardan ekleme, proje oluşturma, yeniden açma, otomatik kayıt, geri alma | Uygulama yeniden açıldığında içerik ve düzenlemeler geri geliyor |
| Ortak medya alanı | Uygulamada üretilen sıkıştırılmış / dönüştürülmüş dosyaları ve dışa aktarılan PDF / MP4'leri projelerden seçme; uygun çıktıda bir sonraki araca geçiş | Bir araçta üretilen dosya başka bir araçta kullanılabiliyor; orijinal ve çıktı ayırt ediliyor; aktarma reklamla kesilmiyor |
| Dosya okuyucu | PDF sayfalarını açma, gezinme ve yakınlaştırma; desteklenen görsel, video ve ses dosyalarını uygulama içinde önizleme / oynatma; dosya bilgilerini gösterme | Desteklenen dosya seçildiğinde doğru araç öneriliyor; okunamayan dosyada anlaşılır neden ve alternatif gösteriliyor; kaynak dosya değişmiyor |
| Sınırlı dosya dönüştürme | JPEG / PNG / WebP arasında teknik olarak doğrulanan görsel çıktıları; fotoğraflardan PDF; sunum taslağından PDF; desteklenen video girdilerinden MP4 | Her giriş / çıkış çifti gerçek cihazlarda doğrulanıyor; kalite, şeffaflık, hareket, ses veya düzen kaybı önceden belirtiliyor; evrensel dönüşüm vaadi verilmiyor |
| Dil ve cihaz uyumu | 22 arayüz yerelleştirmesi, Arapça / Farsça sağdan sola düzen, telefon / tablet yerleşimleri | Temel görevler seçilen dilde tamamlanıyor; ekran ve yazı boyutu değişiminde içerik veya kontroller kaybolmuyor |
| Sunum oluşturma | Hazır şablonlar, metin ve görsel, slayt ekleme / çoğaltma / sıralama, ortak tema | Kullanıcı 5 slaytlık bir sunumu yardım almadan hazırlayabiliyor |
| Sunum çıktısı | Statik PDF, uygulama içinde tam ekran sunum, temel konuşmacı notları | Desteklenen alfabelerde yazılar, görseller ve slayt sırası çıktıda korunuyor |
| Temel video | Çok klipli ana zaman çizelgesi; kesme, bölme, sıralama, birleştirme, 9:16 kırpma ve basit sabit hız seçenekleri | Desteklenen girdilerden tutarlı bir dikey video çıkıyor; telefonda temel düzenlemeler az adımla bulunuyor |
| Katmanlı düzenleme | Zamanı ayarlanabilen yazı / altyazı, görsel / logo ve ses katmanları; temel hizalama ve klibe göre süre ayarı | Katman sırası, konum ve süre önizleme ile dışa aktarılan videoda aynı |
| Altyazı | Elle yazma / düzeltme ve basit zaman ayarı | Kullanıcı metni okunaklı biçimde zamanlayıp çıktıda görebiliyor |
| Ses | Kullanıcı müziği, orijinal ses seviyesi, basit ses başlangıç / bitiş ayarı | Ses önizleme ve çıktı boyunca eşzamanlı |
| Renk | Parlaklık, kontrast, doygunluk ve az sayıda hazır stil | Aynı değişiklik önizlemede ve çıktıda görülüyor |
| Video sıkıştırma | Bağımsız dosya küçültme ve editör çıktısında kalite / boyut ayarı, 720p / 1080p profilleri | Gerçek çıktı boyutu gösteriliyor; önizleme, ses eşzamanı ve orijinal dosya korunuyor |
| Görsel sıkıştırma | JPEG / WebP kalite ayarı, desteklenen formatlarda boyutlandırma, önce / sonra karşılaştırması | Kalite / özellik kaybı işlemden önce anlaşılır; yeni dosya açılabiliyor; orijinal korunuyor |
| Dışa aktarma | MP4, galeriye kaydetme, paylaşım menüsü, kalite seçenekleri ve boyut tahmini | İşlem ilerlemesi ve sonucu anlaşılır; hata sonrası proje korunuyor |
| Reklam yerleşimleri | Ana sayfa / proje listesinde alt banner, tamamlanmış görevlerden sonra sıklık sınırıyla tam ekran denemesi ve izin yönetimi | Reklam yükleme hatası temel işleri durdurmuyor; düzenleme, okuma ve sunum ekranlarını kapatmıyor |

Otomatik altyazı genel yayın sonrasına alınır. Türkçe doğruluk, süre, maliyet ve veri işleme koşulları denenir; diğer diller ayrı tanıma / doğruluk matrisine göre eklenir. 22 arayüz dili, 22 dilde otomatik konuşma tanıma desteği anlamına gelmez. Manuel altyazıda desteklenen alfabelerde metin girişi ve çıktı doğrulanır. Sunucu kullanılıyorsa ücretsiz kota ve veri aktarımı işlem öncesi gösterilir; ek otomatik haklar için ödüllü reklam ancak beklenen gelir işlem maliyetini karşılayabildiğinde değerlendirilir.

İlk video çıktı önerisi: 9:16, 1080 × 1920, 30 fps, MP4 / H.264 video ve AAC ses. Daha küçük dosya için 720p seçeneği. Bunlar uygulamanın başlangıç ayarlarıdır; farklı paylaşım hizmetlerinin zorunlu teknik sınırları olarak sunulmaz. Hedef cihaz testlerine göre ayarlar değişebilir. Standart çıktıya reklam veya zorunlu filigran eklenmez.

İlk girdiler için adaylar: JPEG / PNG / WebP görseller, yaygın telefon MP4 / MOV videoları ve kullanıcı ses dosyaları. HEIC, HEVC, HDR ve değişken kare hızlı girdilerin Android cihaz gruplarındaki davranışı teknik denemede ölçülür. Dosya uzantısı tek başına kodek desteği sayılmaz. Destek listesi bu denemeden sonra kesinleştirilir.

Sunuma video ekleme daha ileri aşama hedefidir: Uygulama içi sunumda oynatma sınanır. Statik PDF çıktısında videonun kapak karesi kullanılır ve ses / hareketin bu çıktıya aktarılmadığı gösterilir.

Dosya okuyucu ilk genel yayında PDF okuma ve uygulamanın gerçekten desteklediği medya türlerini önizlemeyle başlar. DOCX, XLSX ve PPTX dosyalarında düzen, yazı tipi, formül ve animasyon uyumluluğu doğrulanana kadar uygulama içi görüntüleme / düzenleme sözü verilmez; Android'in uyumlu uygulamayla açma veya paylaşma seçeneği sunulabilir. Android sistem dosya seçicisiyle erişilemeyen dosyalar için geniş depolama izni istenmez.

## 6. Sonraki sürümler

### İlk kullanım verileri sonrası

- Otomatik altyazı: Önce Türkçe için doğruluk, süre, kişisel veri akışı ve işlem maliyeti doğrulanır; sonra dil kapsamı genişletilir.
- İsteğe bağlı ödüllü reklamla ek şablon açma; ücretsiz şablonlar temel görevleri tamamlamaya devam eder.
- Slaytlardan kısa video üretme: Kullanıcı slaytları seçer, süreleri belirler, dikey kadrajı kontrol eder, ses ve altyazı ekler.
- Düzenlenebilir PPTX çıktısı ve PPTX içe aktarma için ayrı uyumluluk denemesi.
- Basit geçişler, hazır hareketler, müzik ritmine göre elle işaretleme veya yardımcı kesme.
- Kullanım verisi ve cihaz testleri desteklediğinde ek bir video düzenleme paketi: hazır geçişler, tekrar kullanılabilir efekt / yazı animasyonu stilleri, ses geçişleri ve daha ayrıntılı hız kontrolleri. Araçlar editörde ilgili klip veya katman seçildiğinde açılır.
- Daha fazla şablon, kişisel renk / yazı stili seti, tekrar kullanılabilir tarifler.
- PDF birleştirme ve toplu görsel / video sıkıştırma gibi tamamlayıcı araçlar. Tek dosya sıkıştırma ve görsel boyutlandırma kapalı testte de bulunur.
- PDF sayfalarını sıralama, döndürme, bölme ve seçilen sayfaları dışa aktarma; önce küçük dosya kümelerinde doğrulanır.
- Görselleri PDF sayfalarına dönüştürme ve PDF sayfalarını görsel olarak dışa aktarma; çıktı kalitesi ve metin seçilebilirliği farkı işlem öncesi gösterilir.
- Tekrarlanan işlere yönelik kuyruk / toplu işlem: kullanıcı dosyaları seçer, bir çıktı profili belirler, sonuçları kontrol edip birlikte kaydeder. İlk aşamada reklam veya işlem maliyeti yaratabilecek sunucu özelliği eklenmez.
- PDF sayfalarını ve slaytları video sahnesi olarak kullanma; sunumdan zamanlamalı MP4 üretme; kullanıcı talebine göre süre, dikey / yatay kadraj, ses ve altyazıyı ortak çıktı akışında ayarlama.
- Videodan sabit kare çıkarıp görsel / sunum varlığı olarak kullanma; PDF sayfasını düzenlenebilir metin gibi göstermeden sunuma ya da video sahnesine taşıma.
- Birden fazla adımı kaydedilebilir işlem tarifi olarak birleştirme. İlk tarifler cihazda çalışan ve geri alınabilir adımlarla sınırlanır; tarifin tüm dosya türleriyle uyumlu olduğu varsayılmaz.

### Geniş dosya türü hedefi ve destek matrisi

Hedef, uygulamanın önemli dosya ailelerinde rakiplerinden daha geniş ve güvenilir kullanım sunmasıdır. Bu hedef, yalnızca uzantı sayısını artırarak ölçülmez. Her dosya türü için **içe alma, önizleme, projede kullanma / düzenleme, dönüştürme ve dışa aktarma** ayrı ayrı doğrulanır; bir biçimi açabilmek o biçime çıktı üretebildiğimiz anlamına gelmez. Rakip karşılaştırması video editörü, PDF / belge aracı ve genel dönüştürücü kategorilerindeki Android uygulamalarının resmî destek sayfaları üzerinden, ilk beta kapsamı kesinleşmeden yapılır.

| Dosya ailesi | İlk hedef / adaylar | Genişletme adayları | Doğrulanacak başlıca ayrım |
| --- | --- | --- | --- |
| Görsel | JPEG / JPG, PNG, WebP | HEIC / HEIF, GIF, BMP, TIFF, SVG | Animasyon karelerinin korunması, saydamlık, renk profili, SVG karmaşık öğeleri ve çıktı kalitesi |
| Video | MP4 ve yaygın telefon videoları | MOV, MKV, WebM, 3GP; desteklenen H.264 / HEVC ve diğer cihaz kodekleri | Kapsayıcı uzantısı kodek desteği değildir; HDR, değişken kare hızı, ses izi, çözünürlük ve donanım kodlayıcısı cihaz bazında sınanır |
| Ses | MP3, M4A / AAC, WAV | OGG / Opus, FLAC, AMR | Önizleme ile dışa aktarma desteği ayrılır; yeniden kodlamada kalite ve kanal kaybı açıklanır |
| Belge ve sunum | PDF; uygulama içinde oluşturulan PDF / MP4 | DOCX, XLSX, PPTX; ODT, ODS, ODP; TXT, RTF, CSV | Salt okunur görüntüleme, düzenlenebilir içe aktarma ve PDF'ye dönüştürme ayrı kapsamlar; sayfa düzeni, yazı, formül ve animasyon korunumu sınanır |
| Arşiv | ZIP değerlendirme adayı | 7Z, RAR ve diğer arşiv türleri | Kütüphane lisansı, şifreli arşiv, bozuk dosya, dosya yolu güvenliği ve açılma boyutu sınırları |

Bu tablo bir yayın desteği vaadi değil, teknik deneme havuzudur. Her hücredeki aday, ancak temsili telefon ve tabletlerde, gerçek örnek dosyalarla ve kullanıcıya gösterilecek kayıp uyarılarıyla doğrulandıktan sonra desteklenenler listesine alınır. İlk strateji, olabildiğince çok girdiyi güvenli biçimde tanıyıp önizlemek; dönüştürme ve dışa aktarmayı daha dar, yüksek güvenli bir kümeyle başlatıp ölçümlere göre genişletmektir.

Dosya seçildiğinde uzantıya ek olarak MIME / gerçek içerik ve medya kodek bilgisi incelenir. Uygun olmayan işlem gösterilmez; mümkünse kullanıcıya **önizle**, **uyumlu uygulamada aç** veya **başka çıktıya dönüştür** alternatifleri sunulur. Destek matrisi en az biçim, işlem seviyesi, Android / cihaz koşulu, bilinen kayıp, test durumu ve son doğrulama tarihini tutar.

### Daha ileri aşama

- DOCX / PPTX / XLSX için salt okunur önizleme ve PDF'ye aktarma: önce farklı yazı tipleri, tablolar, görseller, sayfa boyutları, formüller ve slayt düzenlerini içeren test kümesi; kullanılan dönüştürme motorunun lisansı ve cihaz maliyeti incelenir. Yalnızca doğrulanan alt kümeler desteklenir.
- PDF'den DOCX / XLSX / PPTX'e dönüşüm, kaynak dosyanın yapısını geri kurmanın güvenilir olmadığı belirtilerek ertelenir; ürün bunu düzenlenebilir ve kayıpsız dönüşüm gibi sunmaz.
- Fotoğraf / taramadan aranabilir PDF için kenar düzeltme, OCR dil matrisi, çevrimdışı çalışma ve kişisel veri akışı doğrulanır.
- ZIP oluşturma / açma, güvenli yol denetimi ve aşırı açılma boyutu sınırları eklenecekse bağımsız araç olarak değerlendirilir. Parola korumalı ve bozuk arşivler ayrıca sınanır.
- Arka plan kaldırma, anahtar kareler, maske ve karıştırma modları.
- Hareket takibi, perspektife yerleştirme, otomatik renk eşleştirme.
- Bulut eşitleme, birlikte çalışma ve telefondan sunum kontrolü.

Geçişler ve katmanlar desteklenen cihaz gruplarında doğrulanana kadar genel destek vaadi verilmez. PPTX içe / dışa aktarımında yazı tipleri, yerleşim, animasyon ve düzenlenebilirlik ayrı ayrı doğrulanır.

### Dönüştürme için ürün sınırları

- Dönüştürücü, “her dosyayı her formata” aracı gibi konumlandırılmaz. Uygulama, dosyanın gerçek içeriğini ve cihaz yeteneğini doğrulayıp yalnızca denenmiş giriş → çıkış çiftlerini gösterir.
- İlk hedefler: görsel biçimi / boyutu, fotoğraflardan PDF, sunumdan PDF ve düzenlenmiş videodan MP4. MOV, HEIC, HEVC, HDR ve ses kapsayıcıları için destek cihaz ve kodek testlerinden sonra açıklanır.
- Her dönüşümden önce beklenen dosya boyutu ile görünür kalite / özellik kayıpları açıklanır. Örnekler: saydamlığın düzleşmesi, animasyonun ilk kareye inmesi, PDF metninin resme dönüşmesi, Office yerleşiminin değişmesi ve video sesinin / HDR bilgisinin kaybı.
- Orijinal dosya saklanır; çıktı yeni dosya olur. Hata veya iptalde kısmi çıktı temizlenir ve mümkünse yeniden denenebilir.
- Okuma ekranında içerik boyunca reklam gösterilmez. Dönüştürme reklamı varsa kayıt tamamlandıktan sonraki doğal geçişte, mevcut sıklık sınırı kurallarıyla değerlendirilir; okuma / oynatma sırasında banner gizlenir.

## 7. Teknik yaklaşım önerisi

**Başlangıç uygulaması:** Yalnızca Android hedefi için Kotlin ve Jetpack Compose ile kuruluyor. Video işlemleri için Media3 ve cihazın medya yetenekleri teknik denemede değerlendirilecek.

- Jetpack Compose ile pencerenin kullanılabilir alanına göre uyarlanan telefon / tablet arayüzü hazırlanır. Dar ve geniş ekranlar için ayrı yerleşim düzenleri tasarlanır. [Android uyarlanan ekran düzenleri](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes)
- Android video denemesinde Media3 Transformer kullanılabilir; düzenleme ve dışa aktarma yetenekleri resmî belgelerde yer alır. [Android Media3 Transformer](https://developer.android.com/media/media3/transformer)
- Video / ses için kapsayıcı, kodek ve cihaz desteği ayrı izlenir; Android'in yerleşik medya biçimleri ve Media3'ün dönüştürme sınırları destek matrisi hazırlanırken kaynak alınır. [Android medya formatları](https://developer.android.com/media/platform/supported-formats), [Media3 desteklenen formatlar](https://developer.android.com/media/media3/transformer/supported-formats)
- Rakip uygulamaların kabul ettiği içe aktarma biçimleri ile bizim önizleme / düzenleme / dönüştürme / dışa aktarma kapsamımız ayrı karşılaştırılır. Örneğin Adobe Express'in resmî mobil görsel kabul listesi AI, GIF, HEIC, JPEG, PNG, PSD, SVG ve WebP gibi türleri sayar; bu kabul listesi her türde aynı düzenleme veya dışa aktarma desteğini tek başına kanıtlamaz. [Adobe Express görsel gereksinimleri](https://helpx.adobe.com/express/web/image-creation-and-editing/change-file-formats/image-requirements.html)
- Android Media3'ün çoklu medya düzenlemesinde mevcut kısıtlar vardır; özellikle video / ses crossfade desteği hazır varsayılmayacak. Geçişler eklenmeden önce ayrı deneme gerekir. [Android kompozisyon desteği ve kısıtları](https://developer.android.com/media/media3/transformer/composition)
- Çıktı desteği donanım ve kodeklere bağlı olabileceği için cihaz bazlı destek matrisi hazırlanacak. [Android desteklenen formatlar](https://developer.android.com/media/media3/transformer/supported-formats)
- Görsel işlemleri için cihazda çalışan kodlayıcılar; büyük görsellerde belleği aşmadan okuma, boyutlandırma ve çıktı üretme denemesi yapılır. Dosya türüne göre kalite / şeffaflık ayarları değişir.
- Arayüz metinleri kaynak anahtarlarıyla yönetilir; sayılar, süreler, çoğullar ve biçimlendirme yerelleştirilir. Android varsayılan kaynakları tam tutulur. [Android yerelleştirme](https://developer.android.com/guide/topics/resources/localization)

Proje modeli: Ortak medya kitaplığında her dosyanın kaynağı, türü, izin verilen işlemleri ve üretilmiş türevleri; projelerde slaytlar veya klipler, katmanlar, metin / renk stilleri, ses, çıktı ve geri alma bilgileri tutulur. Model sürümlü olacak; kullanıcı aynı medya varlığını farklı projelerde kullanabilir ve proje kopyalayarak başka çıktı hazırlayabilir. Sunum ve video editörü ortak varlıkları kendi işine uygun biçimde gösterir. Büyük kaynak dosyaları gereksiz yere çoğaltılmayacak; uygulama erişimi güvenli biçimde sürdüremiyorsa kullanıcıdan yeniden seçmesi istenecek.

Kaynak dosyalar düzenleme sırasında değiştirilmez. Düzenlemeler proje verisine yazılır; çıktı yeni dosya olarak üretilir. Galerideki bir kaynak silindiğinde projenin bozulmaması için kullanılan içeriklerin proje içine kopyalanması veya kalıcı erişim yaklaşımı test edilir.

Temel kesme, slayt düzenleme, video / görsel sıkıştırma, önizleme ve dışa aktarma cihazda çalışacak şekilde tasarlanır. Bu tercih, kullanıcı başına sunucu maliyetini azaltmayı da hedefler. Otomatik altyazı için ilk aday, gerekli ses içeriğini gönderen bir servis bağlantısıdır. Kullanıcı işlemden önce internet, veri gönderimi ve kullanım kotası hakkında bilgilendirilir; servis, saklama süresi ve işlem maliyeti seçilmeden sınırsız otomatik işlem sözü verilmez.

Dışa aktarma cihaz sınırlarına göre ele alınır. Uygulama arka plana geçtiğinde veya kapandığında işlemin koşulsuz devam edeceği varsayılmaz. İptal, kesinti ve yeniden deneme durumları proje kaybına yol açmayacak şekilde tasarlanır.

Reklam sistemi editörlerden ayrı bir bileşen olacak. Gösterim kararları ekran, kullanıcı tercihi, sıklık sınırı ve reklamın hazır olma durumuna göre verilecek. Reklam sağlayıcısını veya bir yerleşimi uzaktan kapatabilmek mümkün olacak. Kullanıcının belge, fotoğraf, video içeriği ve proje metni reklam hedefleme verisine eklenmeyecek.

Başlangıç reklam ağı adayı AdMob'dur. İlk denemede tek ağ kullanılacak; birden fazla ağla gelir optimizasyonu gerçek trafik sonrası değerlendirilir. Geliştirme ve QA sırasında test reklamları kullanılacak; gerçek reklam tıklamaları test amacıyla üretilmeyecek.

İzin yönetimi reklam entegrasyonuna dahil olacak. Android UMP ile güncel izin durumu kontrol edilir, gerektiğinde kullanıcı tercih formu gösterilir ve SDK reklam isteğine izin veriyorsa istek yapılır. Gerekli durumlarda Ayarlar'dan reklam / gizlilik tercihlerine yeniden erişilir. [Google Android UMP entegrasyonu](https://developers.google.com/admob/android/privacy)

Kullanıcının reklam / gizlilik tercihi temel düzenleme erişimini değiştirmez. Uygunsuz reklamı bildirme erişimi de tasarıma dahil edilir. Yaş grubu ve yayın pazarı netleştiğinde reklam yapılandırması bunlara göre seçilir.

## 8. Geliştirme sırası ve bitiş koşulları

| Aşama | Somut çıktı | Sonraki aşamaya geçiş koşulu |
| --- | --- | --- |
| 0. Çalışan temel | JPEG / PNG sıkıştırma, desteklenen MP4 kesme / sıkıştırma, görsel içerebilen sunumdan PDF, proje taslağı ve 22 dilin kaynak yapısı | Mevcut emülatör testleri ve derleme sonucu kayıtlı; gerçek cihaz sınırlamaları açık |
| 1. Bir haftalık Play kapalı testi | Tek video kesme + mevcut sıkıştırma ile tek çıktı; kapsamı doğru anlatan imzalı AAB, gizlilik ve veri beyanları, testçi akışı | [Kapalı test planındaki](../testing/CLOSED_TEST_PLAN.md) üç görev çalışıyor; kaynaklar korunuyor; Play Console'a gönderim hazır |
| 2. Test geri bildirimi ve proje güvenliği | Kullanıcının takıldığı ekranları düzeltme; seçilen dosyaların kalıcı erişimini ve proje tekrar açılmasını güçlendirme; cihaz / format matrisi | Aynı görevler farklı telefonlarda tamamlanıyor; desteklenmeyen dosyanın nedeni anlaşılır; veri kaybı yaşanmıyor |
| 3. Üretici akışları | Görselli 5 slaytlık sunum ve tam ekran gösterim; 2–3 klipli dikey video, temel yazı / ses / manuel altyazı; en az bir doğrudan araçlar arası aktarım | Hedef kullanıcılar 5 slaytlık PDF ve 30 saniyelik MP4'ü yardım almadan çıkarıyor; önizleme / çıktı uyuşuyor |
| 4. Genel yayın hazırlığı | Temel PDF / medya okuyucu ve doğrulanmış sınırlı dönüştürme; 22 dilin içerik / alfabe denetimi; telefon / tablet testleri; banner / sınırlı geçiş reklamı ve izin yönetimi | Mağaza açıklaması gerçek formatları söylüyor; diller, cihazlar ve reklamlı akışlar kalite eşiğini geçiyor |
| 5. Genel yayın ve izleme | Tamamlanan görev, tekrar kullanım, hata, gerçek reklam gösterimi ve maliyet sonuçları | Özellik / format genişlemesi kullanım kanıtına ve cihaz uyumluluğuna göre seçiliyor |

Yalnızca 1. aşama için bir haftalık hedef var. Diğer aşamaların tarihi kapalı test sonuçları ve tek geliştiricinin kapasitesiyle belirlenecek. 22 dil, tablet uyumu ve reklam geliri genel yayın hedefidir; ilk kapalı testin kapsamı ve gerçek çeviri durumu testçilere açıkça anlatılır.

## 9. Başarı ölçütleri

Aşağıdaki sayılar ilk genel yayın için önerilen ürün hedefleridir; henüz ölçülmüş sonuçlar değildir. Bir haftalık kapalı testin daha dar başarı eşiği [kapalı test planındadır](../testing/CLOSED_TEST_PLAN.md).

- Hazır örnek içerikle, 10 hedef kullanıcının en az 8'i yardım almadan 5 dakika içinde 5 slaytlık bir sunum hazırlayıp PDF alabilsin.
- Hazır 3 kliple, 10 kısa video kullanıcısının en az 8'i yardım almadan 5 dakika içinde 30 saniyelik bir dikey edit hazırlayıp dışa aktarabilsin.
- Bu iki süre hedefi dosya seçme ve düzenlemeyi kapsar; dışa aktarma süresi ayrıca cihaz ve içerik türüne göre raporlanır.
- Destek matrisindeki beta denemelerinde dışa aktarma başarı oranı için başlangıç hedefi en az %95 olsun. İçerik türü ve cihaz grubu ayrıca izlenir.
- Uygulama yeniden açma, kesinti, depolama doluluğu ve işlem iptali senaryolarında kaydedilmiş proje korunmuş olsun.
- Önizleme ile çıktı arasında yazı konumu, görsel kırpması ve ses zamanlamasını etkileyen bilinen hata kalmasın.
- 22 yerelleştirmede temel akışların metinleri tam olsun; dil değişimi, Arapça / Farsça yönü ve farklı alfabelerde PDF / video yazıları doğrulansın.
- Telefon / tablet, yatay / dikey, bölünmüş ekran ve büyütülmüş sistem yazısında temel kontroller erişilebilir olsun; ekran değişiminde proje kaybolmasın.
- Sıkıştırmada kaynak ve gerçek çıktı boyutu gösterilsin; tahmin farkı ölçülsün. Çıktı büyürse veya hedef MB'ye ulaşılamazsa açıkça bildirilsin; orijinal değişmesin.
- İlk çıktıyı alanların ikinci proje oluşturması izlenir; sayısal devamlılık hedefi başlangıç kullanım verisi görüldükten sonra belirlenir.
- Reklamlı akışta aynı kullanım hedefleri korunur. Görev tamamlama veya tekrar kullanım düşerse yerleşim / sıklık değiştirilir.
- Reklam yüklenmemesi, izin formu hatası, reklamın erken kapatılması ve ödülün tekrar bildirilmesi senaryoları doğrulanır.
- Reklam istekleri, dolan istekler ve gerçek gösterimler ayrı ölçülür; gelir hesabına gerçek gösterimler alınır.
- Günlük aktif kullanıcı, 1. / 7. / 30. gün geri dönüş, kullanıcı başına reklam geliri, format bazlı eCPM ve otomatik işlem maliyeti izlenir.

## 10. Reklam geliri ve ücretsiz kullanım

### Temel model

Uygulama ücretsiz başlayacak. Bir haftalık Play kapalı test derlemesine reklam SDK'sı eklenmez; Play beyanları bu gerçek durumu yansıtır. Temel sunum / video düzenleme, video / görsel sıkıştırma, proje kaydı ve standart çıktılar ilk genel yayında reklam destekli olacak. Gelir planı ücretli aboneliğe dayanmayacak.

Ücretsiz şablonlar ana görevleri tamamlamaya yeterli olacak. Ek şablonlar veya isteğe bağlı ek stiller ödüllü reklamla açılabilecek. İlk ödül, sunucu maliyeti düşük olan **bir ek şablonu mevcut projede kullanma hakkı** olacak.

Ödüllü reklam kullanıcı tarafından açıkça seçilmeli, karşılığı önceden anlaşılmalı ve hak edilen ödül verilmeli. Ödüllü reklamı reddetmek veya kapatmak normal kullanımı engellememeli. [Google ödüllü reklam kuralları](https://support.google.com/admob/answer/7313578?hl=en)

### Yerleşimler

| Reklam türü | Konum | Başlangıç kararı |
| --- | --- | --- |
| Alt banner | Ana sayfa, Projeler ve Şablonlar ekranlarının altındaki ayrılmış alan | Kullanılabilir genişliğe uygun tek banner; Reklam etiketi; gezinme ve düzenleme düğmelerinden ayrı |
| Ödüllü reklam | Ek şablonun ayrıntı ekranı | Genel yayın sonrası; kullanıcı isteğiyle; kazanılacak hak önceden yazılı; ücretsiz alternatif görünür |
| Tam ekran geçiş reklamı | Çıktı kaydedilip görev tamamlandıktan sonra uygun bir sonraki göreve / proje listesine geçiş | Kapalı betada sıklık sınırıyla denenecek; yayın ayarı kullanım ve gelir sonuçlarına göre belirlenecek |

Banner, Android ekranındaki kullanılabilir genişliğe uygun uyarlanan bir yerleşim olacak. Kendi alanı ayrılır; içerik, alt gezinme ve sistem gezinme alanına bindirilmez. Düzenleme, sıkıştırma ayarı, önizleme, tam ekran sunum ve dosya üretme ekranlarında gizlenir. Aynı ekrana ikinci reklam alanı eklenmez. Otomatik yenileme sağlayıcının desteklediği davranışla ve görünür alanla sınırlı ele alınır. [Google Android banner yerleşimi](https://developers.google.com/admob/android/banner)

Her kesme, yazı ekleme, renk değiştirme veya düğmeye dokunma öncesinde tam ekran reklam kullanılmaz. Reklam ağı rehberi her kullanıcı eylemine bağlı tam ekran gösterimleri uygun bulmaz; doğal içerik / görev geçişlerini gerektirir. İşe başlamadan ve bittiğinde arka arkaya iki reklam da başlangıç modeline eklenmez. [Google tam ekran reklam kuralları](https://support.google.com/admob/answer/6201362?hl=en)

Tam ekran deneme koşulları: Kullanıcının ilk tamamlanan görevinde gösterilmez. Düzenleme, önizleme, sunum, kayıt ve dosya üretiminde tetiklenmez. Sonuç dosyası kaydedildikten sonra, görevler arasındaki önceden tanımlanmış doğal geçişte ve önceden yüklenmişse gösterilebilir. Reklam temel çıktıyı açmanın koşulu değildir. Geç yüklenen reklam yeni görev başladıktan sonra açılmaz; reklam hazır değilse geçiş doğrudan tamamlanır.

Deneme için önerilen üst sınır: En az 6 dakika arayla, kullanıcı başına günde en fazla 2 tam ekran geçiş reklamı. Yakın zamanda ödüllü reklam gösterilmişse geçiş reklamı atlanır. Bu, ürünün kendi başlangıç sınırıdır; reklam ağının garanti ettiği veya zorunlu tuttuğu bir sayı değildir. İlk ödüllü reklam üst sınırı için günde 3 isteğe bağlı kullanım önerilir; kota ve gelir gerçek verilerle değerlendirilir.

Banner ve sınırlı tam ekran denemesi, banner ve isteğe bağlı ödüllü reklam akışıyla karşılaştırılır. Sonuç yalnızca gösterim sayısıyla seçilmez: Tamamlanan görev, 7. gün geri dönüş ve kullanıcı başına gelir birlikte değerlendirilir. Bir günde daha fazla reklam gösteren akışın uzun vadede daha fazla gelir getireceği varsayılmaz.

### Gelir hesabı

Gelir ölçümünde temel birim başarılı reklam gösterimidir. İndirme sayısı ve reklam isteği ayrı izlenir. Formatların getirisi farklı olabileceğinden banner / içerik / ödüllü / geçiş reklamları ayrı hesaplanır.

**Aylık reklam geliri = her format için (gerçek gösterim / 1.000 × yayıncı eCPM) toplamı.**

eCPM, bin gerçek gösterim başına panelde raporlanan ortalama yayıncı geliridir. Tahmini ve kesinleşmiş gelir birlikte izlenir. Gerçek değerler yayın pazarı, format, kullanıcı tercihleri ve reklam talebi görüldükten sonra modelin yerine konur.

Aşağıdaki tablo yalnızca varsayımsal hassasiyet hesabıdır. Her gün aynı sayıda aktif kullanıcı, kullanıcı başına günde ortalama 3 gerçek reklam gösterimi ve 30 gün varsayılır. 0,50 / 1,50 / 3,00 USD eCPM değerleri hesap örnekleridir; güncel piyasa oranı veya gelir vaadi değildir. Gösterim ortalaması bir kullanıcıyı 3 reklam izlemeye zorlama hedefi değildir.

| Günlük aktif kullanıcı | Aylık gerçek gösterim | 0,50 USD eCPM | 1,50 USD eCPM | 3,00 USD eCPM |
| --- | --- | --- | --- | --- |
| 1.000 | 90.000 | 45 USD | 135 USD | 270 USD |
| 5.000 | 450.000 | 225 USD | 675 USD | 1.350 USD |
| 10.000 | 900.000 | 450 USD | 1.350 USD | 2.700 USD |

Bu tutarlar uygulamanın giderleri düşülmeden hesaplanan reklam geliridir. Dolar yalnızca ortak hesap birimidir; kur dönüşümü veya yerel gelir tahmini yapılmamıştır.

**Faaliyet sonucu = kesinleşmiş reklam geliri − altyazı / diğer otomatik işlem maliyeti − sunucu ve depolama − destek / bakım − kullanıcı edinme ve diğer giderler.**

İlk sürümde kesme, renk, slayt hazırlama, sıkıştırma ve çıktı üretiminin cihazda çalışması maliyet kontrolüne hizmet eder. Reklam karşılığında sunucu işlemi verilecekse, bir ödülün beklenen reklam geliri ile işlem maliyeti karşılaştırılır. Şablon ödülüyle başlanır; maliyetli işlem hakları pozitif katkı sağladığında eklenir.

### Tekrar kullanım ve büyüme

- Kullanıcının ikinci proje hazırlamasını kolaylaştırmak için favori şablonlar, son kullanılan stiller ve proje çoğaltma ilk kullanım verileriyle önceliklendirilir.
- Mağaza tanıtımı iş odaklı hazırlanır: Sunum hazırlama, kısa video düzenleme, altyazı ve sıkıştırma. Sosyal platform adları ürün dili içine eklenmez.
- İlk kullanıcı kazanımı kısa kullanım örnekleri, özgün şablonlar ve kullanıcı geri bildirimleriyle sınanır.
- Ücretli kullanıcı edinmeye bütçe ayrılacaksa, bir kullanıcının zaman içindeki reklam geliri ve değişken maliyeti ölçüldükten sonra limit belirlenir.
- Reklam tıklaması talep eden veya reklam izlemeyi kullanıcıya ahlaki yükümlülük gibi sunan mesajlar kullanılmaz; ödül ve karşılık doğrudan anlatılır.

## 11. Kaynaklar ve maliyet başlıkları

Kaynak ihtiyacı: Android arayüz ve medya geliştirme, telefon / tablet etkileşim tasarımı, şablon tasarımı, 22 yerelleştirme için çeviri / dil kontrolü ve gerçek cihaz testi. Küçük ekipte roller birleşebilir; cihazların medya yetenekleri ve farklı alfabeler için test zamanı ayrılır.

Maliyet başlıkları: Geliştirme zamanı, Android telefon / tablet test cihazları, çeviri ve dil kontrolü, reklam SDK / izin entegrasyonu, otomatik altyazı işlemleri, isteğe bağlı bulut depolama, şablon / font / içerik kaynakları, bakım ve mağaza yayın hazırlığı. Bütçe aralıkları ekip ve servis seçimleriyle birlikte çıkarılır.

Birinci gelir kaynağı uygulama içi reklamdır. İleride reklamsız kullanım seçeneği ayrıca değerlendirilebilir; ilk genel yayının gelir hesabı buna bağlanmaz. Başlangıçta ülke bazlı eCPM veya sabit aylık gelir sözü verilmez.

## 12. Dil ve yerelleştirme kapsamı

Alt sınır, folkloreArchive projesinde tanımlanan 22 seçeneğin tamamıdır. Kaynak, 3 Ekim 2026 tarihinde okunan eski projenin `src/data/locale-preferences.ts` dil tanımları ve `supportedLocales` listesidir. Bu tarihsel referansın dil kümesi kullanılır; yeni uygulamanın metinleri kendi iş akışlarına göre hazırlanır.

| Dil / yerelleştirme | Seçicide görünen ad | Referans kod |
| --- | --- | --- |
| Türkçe | Türkçe | tr-TR |
| İngilizce | English | en-US |
| İspanyolca | Español | es |
| Brezilya Portekizcesi | Português (Brasil) | pt-BR |
| Endonezce | Bahasa Indonesia | id |
| Hintçe | हिन्दी | hi |
| Arapça | العربية | ar |
| Rusça | Русский | ru |
| Japonca | 日本語 | ja-JP |
| Fransızca | Français | fr-FR |
| Almanca | Deutsch | de-DE |
| Lehçe | Polski | pl-PL |
| Korece | 한국어 | ko-KR |
| Basitleştirilmiş Çince | 简体中文 | zh-Hans |
| Geleneksel Çince | 繁體中文 | zh-Hant |
| İtalyanca | Italiano | it-IT |
| Tayca | ไทย | th-TH |
| Vietnamca | Tiếng Việt | vi-VN |
| Farsça | فارسی | fa |
| Svahili | Kiswahili | sw |
| Hausa | Hausa | ha |
| Rumence | Română | ro |

Çincenin iki yazı sistemi ayrı arayüz seçenekleridir; 22 sayısı dil / yerelleştirme seçeneklerinin sayısıdır. Bu kodlar referans kümesidir; Android kaynak adları ve dil eşlemesi teknik uygulamada platform kurallarına göre yapılır.

İlk açılışta cihazın diline uygun seçenek belirlenir. Ayarlar'daki dil seçiciden kullanıcı değiştirebilir; seçimi sonraki açılışlarda korunur. Desteklenmeyen cihaz dili için İngilizce varsayılan kullanılır. Kullanıcının kendi proje metinleri arayüz dili değiştiğinde korunur.

Yerelleştirilecek alanlar: Ana ekran, editör araçları, dosya / çıktı ayarları, sıkıştırma açıklamaları, hata ve ilerleme mesajları, şablon adları / örnek metinleri, ödül açıklamaları, yardım ve ayarlar. Reklam sağlayıcısının kendi reklam içeriğinin dili uygulama metinleriyle aynı kontrol kapsamında değildir.

Arapça ve Farsçada sağdan sola yerleşim; farklı alfabelerde yazı tipi, birleşik harfler, satır kırılması ve yazı yönü kontrol edilir. PDF ve video çıktısında yazı biçimi korunur. Uzun çeviriler, çoğullar, sayılar ve büyütülmüş yazı boyutu için ayrı düzen kontrolü yapılır. Aynı işleme ait terimler bir sözlükle tutarlı tutulur.

Yayın koşulu: 22 seçenekte temel görevlerin metinleri ve hata durumları eksiksiz; alfabe / yön / çıktı kontrolleri tamamlanmış olmalı. İngilizce geri dönüş mekanizması beklenmeyen eksik kaynakları yönetir; yayın öncesi çeviri eksiklerini kabul etme gerekçesi değildir. Otomatik altyazının dil listesi, konuşma tanıma doğruluğu ve maliyeti doğrulanan ayrı bir özellik matrisinde gösterilir.

## 13. Android telefon ve tablet uyumu

Yerleşim, cihazın adına veya yalnızca fiziksel ekranına göre değil, uygulama penceresindeki kullanılabilir alanın genişliğine göre seçilir. Ekran döndürme, bölünmüş ekran ve katlanabilir cihazda pencere değişimi aynı tasarım yaklaşımıyla ele alınır. [Android pencere boyutu sınıfları](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes)

| Cihaz / kullanım durumu | Arayüz davranışı | Doğrulanacak sonuç |
| --- | --- | --- |
| Küçük / standart telefon | Tek sütun, büyük önizleme, araçlar için alt panel | Ana eylemler ve dosya seçenekleri dar ekranda erişilebilir |
| Büyük telefon / yatay ekran | Alan uygunsa önizleme ve araçlar yan yana; alan daralınca tek sütun | Düşük ekran yüksekliğinde kontroller kaybolmuyor |
| Tablet | Slayt / klip listesi, önizleme ve ilgili araçlar için birden fazla alan | Ek alan düzenlemeyi kolaylaştırıyor; dosya / proje seçimi rahat |
| Tablet bölünmüş ekran | Pencere daralınca telefon düzenine geçiş | Seçili sahne, geri alma geçmişi ve taslak korunuyor |
| Katlanabilir ekran | Kullanılabilir pencereye göre düzen değişimi | Açma / kapama ve ekran değişiminde proje kaybı yok |
| Düşük bellek / sınırlı donanım | Daha hafif önizleme; desteklenen çıktı profilleri ve açık seçenekler | Desteklenmeyen çıktı başlamadan açıklanıyor; sessiz kalite değişimi yapılmıyor |

Minimum desteklenen Android sürümü, medya / reklam bağımlılıkları ve teknik denemeyle kesinleştirilir. Her cihazda aynı kodek, 4K çıktı veya aynı işlem süresi vaat edilmez. Telefon / tablet uyumu ile medya formatı desteği ayrı matrislerde yayınlanır.

Başlangıç QA örneklemi önerisi: En az üç üreticiden alt / orta / üst donanım düzeylerini kapsayan altı telefon ve iki tablet; ek olarak katlanabilir / bölünmüş ekran emülatörleri. Bu kaynak planı önerisidir; cihaz seçimi bütçeyle kesinleştirilir. Desteklenen en eski Android sürümü ve güncel hedef sürüm gerçek cihaz / emülatör karışımıyla kontrol edilir.

Kontroller: Dikey / yatay ekran, büyütülmüş yazı, sağdan sola düzen, ekran okuyucu etiketleri, sistem gezinme alanı, düşük depolama, uzun video, büyük görsel, arka plana geçiş ve kesintiden kurtarma. Reklam alanının boyutu da pencereyle birlikte hesaplanır; reklam yüklendiğinde kontrollerin üstüne gelmez.

## 14. İlk uygulama işleri

- [ ] Sunum ve kısa video kullanıcılarından örnek içerik ve görev toplamak.
- [ ] Prototipte ilk proje oluşturmayı, düzenlemeyi ve dışa aktarmayı sınamak.
- [ ] Android telefon ve tablet teknik denemesinde aynı test içeriklerini kullanmak.
- [ ] Başlangıç cihaz ve dosya destek matrisini çıkarmak.
- [ ] Minimum desteklenen Android sürümünü ve donanıma göre çıktı profillerini kesinleştirmek.
- [ ] Video ve görsel sıkıştırmayı; kalite, hedef boyut tahmini, gerçek çıktı ve orijinalin korunmasıyla sınamak.
- [ ] folkloreArchive referansındaki 22 dil için terim sözlüğü, kaynak anahtarları, çeviri ve kontrol listesi hazırlamak.
- [ ] Sağdan sola düzeni ve farklı alfabelerde PDF / video yazılarını doğrulamak.
- [ ] Tablet, yatay ekran, bölünmüş ekran ve büyütülmüş yazıda ana akışları kontrol etmek.
- [ ] Önizleme / çıktı uyumu, proje kaydı ve hata sonrası kurtarmayı doğrulamak.
- [ ] Otomatik Türkçe altyazı servis adaylarını doğruluk, süre ve maliyetle değerlendirmek.
- [ ] İlk yayın pazarı ve hedef yaş grubunu belirlemek.
- [ ] Reklam alanları, ücretsiz seçenekler ve ödül açıklamasını prototipte sınamak.
- [ ] Android banner / geçiş / ödüllü reklam test birimlerini ve izin yönetimini hazırlamak.
- [ ] Doğal görev geçişlerini, reklamın hazır olmadığı durumu ve sıklık sınırlarını sınamak.
- [ ] Reklam yokluğu, ağ hatası, erken kapatma ve yinelenen ödül bildirimi durumlarını doğrulamak.
- [ ] Gelir / gösterim ve otomatik işlem maliyeti ölçümünü kurmak.
- [ ] Ekip, bütçe ve iki modun önceliğini belirleyip tarihli görev planını hazırlamak.

Bu belgenin ilk somut geliştirme hedefi: Kullanıcı desteklenen Android telefon veya tablette kendi arayüz dilini seçip bir sunum / kısa video hazırlayabilsin veya video / görsel dosyasını küçültebilsin; projeyi yeniden açabilsin ve kullanılabilir çıktı alabilsin. Reklam geliri bu akışların çevresinde kurulacak; ek şablon ödülleri isteğe bağlı olacak.

## 15. Geliştirme başlangıcı — 4 Ekim 2026

Kotlin / Jetpack Compose temeli oluşturuldu. Ana ekran, 22 başlangıç yerelleştirmesi / dil seçimi, geniş pencerede iki sütun, sunum taslağına slayt / metin ekleme, video dosyası bağlama ve sürümlü yerel proje kaydı koda eklendi. İlk geliştirme APK'sı derlendi; üç proje verisi testi ve lint kontrolü geçti. Gerçek cihaz testi henüz yapılmadı. Bu başlangıç, ilk genel yayın kapsamının tamamlandığı anlamına gelmez.

Tek görsel akışı tamamlandı: JPEG / PNG seçme, kalite ve çözünürlük sınırı, gerçek JPEG önizlemesi, önce / sonra boyutları ve yeni belgeye kaydetme. Orijinal korunuyor; yön, şeffaflık, iptal ve hata sonrası yeniden deneme testleri geçti. Telefon, tablet boyutu ve büyük yazı emülatör kontrolleri yapıldı. İlk akış hedef dosya boyutunu otomatik tutturma veya diğer görsel formatlarının tamamı için destek sunmaz. Ayrıntılar [görsel sıkıştırma raporunda](C:/Users/user/Documents/ChatGPT/ofice/IMAGE_COMPRESSION_TEST_REPORT.md).

Tek video sıkıştırma akışı da eklendi: MP4 / H.264 seçme, üç kalite profili, çözünürlük sınırı, gerçek çıktıdan oynatma, önce / sonra boyutları, işlem ilerlemesi / iptal ve yeni dosyaya kaydetme. Uyumlu AAC ses ve dikey yön korunur; ayar değişince eski çıktı geçersiz olur. İlk destek kapsamı SDR, tek görüntü ve isteğe bağlı tek ses parçasıdır; bütün video formatları veya kesin hedef boyut henüz desteklenmez. Ayrıntılar [video sıkıştırma raporunda](C:/Users/user/Documents/ChatGPT/ofice/VIDEO_COMPRESSION_TEST_REPORT.md).

Sunumdan PDF akışı eklendi: Düzenlenen metin slaytlarından 16:9 sayfalar, gerçek PDF'den önizleme, önceki / sonraki sayfa, uzun metinde slayt numarasıyla uyarı ve yeni belgeye kaydetme. Önizlemeden dönünce düzenleme taslağı korunur. İlk PDF adımı metin slaytlarıyla başladı; sonraki adımda slayt başına bir JPEG / PNG görsel eklendi. Video yerleştirme, PPTX ve animasyon henüz yoktur. Çok alfabeli görsel çıktı kontrol edildi; PDF'de metin kopyalama / arama tüm alfabelerde eksiksiz değildir. Ayrıntılar [sunum PDF raporunda](C:/Users/user/Documents/ChatGPT/ofice/PRESENTATION_PDF_TEST_REPORT.md).

Tek MP4 için başlangıç / bitiş seçme, seçilen aralığı önizleme ve mevcut kalite profilleriyle tek dışa aktarmada yeni MP4 kaydetme eklendi. Telefon emülatöründe video sınıfındaki 8 test, o aşamadaki 27 cihaz testi ve yeni akışın tablet / dar ekran denemeleri geçti; ayrıntılar [VIDEO_TRIMMING_TEST_REPORT.md](../features/video/VIDEO_TRIMMING_TEST_REPORT.md) içindedir. Folklore Archive'deki Graphify yaklaşımı geliştirme aracına eklendi; grafiği yerel tutan kullanım kuralı [AGENTS.md](../../AGENTS.md) içindedir. Sıradaki iş gerçek cihazda temel akışları kontrol etmek, kalıcı uygulama kimliğini ve yayın imzasını hazırlamak, ardından 11 Ekim 2026 hedefli Google Play kapalı test AAB'sini göndermektir. Ayrıntılar [CLOSED_TEST_PLAN.md](../testing/CLOSED_TEST_PLAN.md) içindedir. Çok klipli zaman çizelgesi, gelişmiş sunum düzeni ve reklam SDK'sı kapalı test geri bildiriminden sonra geliştirilir. Derleme ve kullanım bilgileri [README.md](C:/Users/user/Documents/ChatGPT/ofice/README.md) içindedir.

Slayt başına JPEG / PNG görsel seçme, küçük önizleme, kaldırma ve oranını koruyarak PDF’ye yerleştirme eklendi. Yalnızca görsel içeren slayt desteklenir; yeni akışın telefon, tablet ölçüsü ve dar ekran / büyük yazı testleri geçti. Tüm telefon emülatörü cihaz testleri 28/28 başarılı. Gerçek sistem dosya seçicisinin kalıcı erişim izni gerçek cihazda doğrulanacak. Ayrıntılar [PRESENTATION_IMAGE_TEST_REPORT.md](../features/presentations/PRESENTATION_IMAGE_TEST_REPORT.md) içindedir.

## 5 Ekim — 0.1.7 görsel formatları ve fotoğraf PDF'si

JPEG / PNG / durağan WebP giriş ve gerçek JPEG / PNG / kayıplı WebP çıktısı eklendi. PNG / WebP şeffaflığı, biçime özel kayıp açıklaması ve sonuca özel büyük dosya onayı korunur. Fotoğraflar çoklu seçilip sıralanır; yönleri düzeltilerek A4 PDF yapılır, gerçek sonuç önizlenir, kaydedilir, açılır ve paylaşılır. İşlenen görsel doğrudan bu araca eklenebilir. Fotoğraf seçimi ekran döndürmede korunur; süreç yeniden başlatma için taslak kaydı henüz yoktur. Görsel işlevleri / kalite seçenekleri azaltılmadı.

[Kapsam, sınırlar ve doğrulama](../features/images/IMAGE_FORMATS_AND_PHOTO_PDF.md), nihai ölçümler `output/development/0.1.7/qa-summary.json` içinde tutulur. Gizlilik metni yerel olarak güncellendi; web / Play yayını ayrı adımdır. Sonraki ürün paketi PDF birleştirme / bölme / sıralama için güvenli belge altyapısıdır. Testçi hataları öncelikli kalır.


## 6 Ekim — ayrı ses aracı

Ses dosyası / videodan tek ses parçası → WAV PCM16 / M4A AAC ve düzenlenmiş video sonucunu ses aracına doğrudan aktarım geliştirme kodundadır. Mevcut arka plan sırası, atomik kaynak / ayar / sonuç kaydı ve gerçek boyut / büyük kopya onayı kullanılır. MP3 / OGG / FLAC yazma veya çok ses parçalı düzenleme tamamlanmış sayılmaz. [Kapsam ve doğrulama](../features/audio/AUDIO_TOOLS_AND_OUTPUT_FORMATS.md). Yeni yayın paketi yoktur; iş sırasının 3. maddesinde görsel / PDF ek hedefleri ve ek video encoder / muxer denemeleri sürer.

## 7 Ekim 2026 — görsel ve PDF çıktı hedefleri

BMP görsel hedefi / yeniden içe alma, PDF sayfası JPEG / WebP hedefi, kalite ve tam çözünürlük seçenekleri eklendi. Ortak sunum / fotoğraf PDF aktarımı ve gerçek boyuta göre büyük kopya onayı korunur. [Kapsam](../features/images/IMAGE_AND_PDF_OUTPUT_TARGETS.md). Yeni yayın derlemesi yapılmaz. Sırada HEVC / WebM encoder / muxer denemesi var.


## 8 Ekim — tam ekran sunum ve slayt görselleri

Sunum düzenleyicisinde ayrı konuşmacı notu paneli, Diğer işlemler altında tam ekran sunum/sayaç ve seçili slayt görseli tamamlandı. PNG/JPEG/WebP/BMP ve doğrudan 1024/2048/4096 çıktı, mevcut PDF/MP4 yolları ve özel taslak korunur. [Kapsam ve doğrulama](../features/presentations/PRESENTATION_MODE_AND_SLIDE_IMAGES.md). Yeni sürüm paketi üretilmedi. Sonraki ürün adımı PPTX için küçük uyumluluk/ticari lisans denemesidir; bütün Office dosyalarını eksiksiz dönüştürme vaadi verilmez.
