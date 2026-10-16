# Bir haftalık Google Play kapalı test planı

**15 Ekim 2026:** Güncel adımlar [kapalı test kontrol listesine](CURRENT_CLOSED_TEST_CHECKLIST.md) taşındı; toplu dönüştürme → ZIP → klasör doğrulaması [bütünleşik raporda](../features/batch/BATCH_ZIP_FOLDER_FLOW_AND_REGRESSION.md). Aşağıdaki ilk gönderim tarihleri ve durumlar tarihsel kayıttır. Yeni yayın yalnız kullanıcı isteğinde hazırlanır.

5 Ekim 0.1.6 (7): Görsel sonucundan yeni / kayıtlı sunuma aktarım, yeni slayt görsellerinin kalıcı özel kopyası ve görsel / video / PDF açma-paylaşma eklendi. [Akış ve doğrulama](../development/SHARED_OUTPUT_HANDOFF.md). Sonraki ürün adımı PNG / WebP giriş-çıkışı, ardından fotoğraflardan PDF'dir. Yeni dosya ömrünü anlatan gizlilik metni, Play'e yüklemeden önce web sitesine yayımlanmalıdır.

Tarih: 4 Ekim 2026. Hedef: tek geliştiriciyle 11 Ekim 2026'ya kadar ilk AAB'yi kapalı test kanalına göndermek. Google Play'in inceleme ve dağıtım süresi bizim kontrolümüzde değildir; başarı ölçüsü önce gönderime hazır, doğrulanmış bir derlemedir.

Play Console hesabı hazır. 5 Ekim'de çalışma adı **Birlevo**, `applicationId` ise `studio.birlevo.android` olarak belirlendi. Kimliğin Play Console'da kullanılabilirliği ilk yüklemeden önce doğrulanacak; adın marka uygunluğu ayrıca kontrol edilecek.

4 Ekim ilerlemesi: Tek MP4 kesme, seçili bölüm önizlemesi ve sıkıştırmayla tek dışa aktarma koda eklendi. Video sınıfındaki 8 emülatör testi, yeni akışın tablet ve dar ekran denemeleri, ardından slayta JPEG / PNG görsel ekleme ve PDF’ye yerleştirme tamamlandı. Tüm 28 cihaz testi birlikte geçti; yeni görsel akışı ayrıca tablet ve dar ekran / büyük yazı koşullarında geçti. Debug derleme, birim testleri, lint ve 22 dil / 79 metin anahtarı kontrolü başarılı. Görsel, video ve PDF ara dosyaları düşük depolamada sistem tarafından temizlenmeyen yedeklenmeyen özel alana taşındı. Kalan kritik işler gerçek cihaz kontrolü, kalıcı uygulama kimliği, imzalı AAB ve Play beyanlarıdır.

5 Ekim ilerlemesi: Android 16 emülatöründe gerçek sistem dosya seçicisi ve kaydetme ekranıyla görsel sıkıştırma, sesli video sıkıştırma ve görselli sunumdan PDF dışa aktarma tamamlandı. Görsel referansı uygulama zorla kapatılıp yeniden açıldıktan sonra da çalıştı; kaynak dosya karmaları değişmedi. Birlevo adı ve yeni paket kimliği derlemeye işlendi. Release AAB üretildi ancak yükleme anahtarı olmadığı için imzasızdır. Mağaza metni, gizlilik politikası ve imza kontrol listesi [PLAY_CLOSED_TEST_PREPARATION.md](../releases/PLAY_CLOSED_TEST_PREPARATION.md) içindedir. Fiziksel cihaz, yayın anahtarı ve Play Console adımları bekliyor.

## Test sürümünün işi

Testçi üç basit işi baştan sona yapabilsin: JPEG / PNG görselini küçültüp yeni dosya olarak kaydetmek; desteklenen MP4 videosunu kesip ve/veya sıkıştırıp yeni MP4 kaydetmek; kısa metin ve görsel sunumunu PDF'ye dönüştürüp açmak. Kaynak dosya korunur, çıktı önizlenir ve gerçek boyutu gösterilir. Video kesme ile sıkıştırma aynı dışa aktarmada birleşir; video art arda iki kez kodlanmaz.

Şu anda gerçek görsel sıkıştırma, MP4 kesme / sıkıştırma ve metin veya tek görsel içeren slaytlardan PDF üretme çalışıyor. Kapalı test öncesinde iki küçük üretim yeteneği eklendi: tek video kesme ve slayta bir JPEG / PNG görsel yerleştirme. Çok klipli zaman çizelgesi ve gelişmiş sunum düzeni sonraki iş paketlerinde gelecek.

## Yedi günlük uygulama sırası

| Zaman | Yapılacak iş | Tamamlanma kanıtı |
| --- | --- | --- |
| 4–5 Ekim | Test kapsamını dondur; uygulama adı ve `applicationId` seç; kapalı test grubunu, testçi listesini ve gizlilik politikası adresini hazırla | Birlevo / `studio.birlevo.android` derlendi; testçi daveti ve yayımlanmış politika adresi bekliyor |
| 5–6 Ekim | MP4 için başlangıç / bitiş seçimi, kesilmiş aralığın önizlemesi ve mevcut kalite profilleriyle tek dışa aktarma | Yeni MP4'ün süresi, ses eşzamanı ve seçilen aralık doğrulanmış; iptal / hata kaynak dosyayı etkilemiyor |
| 7–8 Ekim | Dar telefon, tablet ve büyük yazı kontrolleri; çeviri kaynaklarının tamlığı; temel akışlardaki sorunların giderilmesi | Derleme, birim testleri, lint ve telefon / tablet emülatöründe üç görev tamamlanmış; ilk testçi telefonunda tekrar kontrol planlanmış |
| 9 Ekim | Gizlilik politikası, Play veri güvenliği, içerik ve reklam beyanları, kapalı test mağaza metni / görselleri | Beyanlar derlemeyle tutarlı; politika bağlantısı erişilebilir; metin yalnızca çalışan özellikleri vaat ediyor |
| 10–11 Ekim | Yayın anahtarıyla imzalı AAB, son kontrol, kapalı test kanalına yükleme ve testçilere katılım bağlantısı | AAB Play Console'a gönderilmiş; Play incelemesi tamamlandığında testçilere katılım bağlantısı açılıyor |

Play için yeni uygulama AAB olarak hazırlanır. İlk yüklemeden önce uygulama kimliği kalıcı seçilir; dağıtımdan sonra değiştirilemez. Kapalı testte de veri güvenliği formu ve gizlilik politikası gerekir. Geliştirici hesabı yeni bir kişisel hesapsa, genel yayına erişmek için en az 12 testçinin 14 gün boyunca kapalı teste katılması koşulu olabilir. Bu nedenle testçi listesi ilk gün hazırlanır; koşul kapalı testi bu hafta başlatmayı engellemez. [Android uygulama kimliği](https://developer.android.com/studio/publish/preparing), [Play AAB](https://support.google.com/googleplay/android-developer/answer/9844279), [Play veri güvenliği](https://support.google.com/googleplay/android-developer/answer/10787469), [yeni kişisel hesap test koşulları](https://support.google.com/googleplay/android-developer/answer/14151465)

## Bu sürümün sınırı

- Desteklenen girdiler bugün doğrulanan JPEG / PNG ve MP4 / H.264 + isteğe bağlı AAC profiliyle sınırlı. Çıktılar JPEG, MP4 ve metin / görsel sunumundan PDF. Geniş format listesi ürün hedefi olarak kalır; mağaza açıklamasında henüz destekleniyor gibi yazılmaz.
- 22 arayüz dili seçeneği uygulamada mevcut; mevcut çeviriler taslaktır. Kapalı testte kaynak tamlığı ve seçilen alfabelerde görünüm sınanır. Genel yayın için 22 dilde metin ve çıktı kalitesi ayrıca doğrulanır.
- Kapalı testte reklam SDK'sı bulunmaz. Reklam gelir modeli, temel akışlar kullanılabilir hâle geldikten sonra izin yönetimiyle birlikte denenir. Play'deki reklam beyanı, yüklenen derlemenin gerçek davranışını yansıtır.
- Tek video kesme sırasında Media3'ün MP4 düzenleme listesi hızlandırması kullanılmaz; kesilen bölümün dosyada gizli kalmaması doğrulanır. [Media3 kırpma ve düzenleme listeleri](https://developer.android.com/media/media3/transformer/transformations)
- Ortak medya kitaplığı, sunumda gelişmiş görsel düzeni, çok klipli zaman çizelgesi, otomatik altyazı, Office biçimleri, OCR, arşivler ve geniş dönüştürme matrisi sonraki sürümlerin işidir.

## Testçilerden öğrenilecekler

Her testçi üç görevi yardım almadan dener. Kaynak türü / cihaz modeli, işlem süresi, çıktı boyutu, başarısızlık nedeni ve anlaşılmayan ekranlar kaydedilir. İlk geri bildirim özellikle şu soruyu yanıtlar: Kullanıcı, birkaç dokunuşla istediği dosyayı elde ediyor mu? Sonraki geliştirme, testte takılan akışları düzelttikten sonra sunumda görsel düzenini geliştirme ve birden fazla videoyu zaman çizelgesinde birleştirme olacak.

Başarı eşiği: testçi kaynak dosyayı bozmadan her görevden açılabilir bir çıktı alır; uygulama kapanıp açıldığında kayıtlı sunum taslağı geri gelir; desteklenmeyen dosyada neden açıkça görünür. Bir haftanın sonunda bu eşik geçilmezse mevcut çalışan akışlarla test başlatılır ve video kesme bir sonraki kapalı test güncellemesine taşınır.

## Kapalı test beklenirken geliştirme — 5 Ekim 2026

Bu bölüm yukarıdaki ilk hazırlık takviminin devamıdır. İmzalı `0.1.0 (1)` paketi, İngilizce mağaza görselleri ve yayımlanmış gizlilik politikası hazırlandı; kullanıcı Play Console'da sürüm ve testçi ayarlarını yaptı. Son ekran Alpha test katılım bağlantılarını gösteriyor. İncelemenin tamamlandığı ve testçilere dağıtımın başladığı henüz doğrulanmadı. Fiziksel cihaz testi kullanıcı tercihiyle ertelendi; emülatör doğrulaması kullanılıyor.

Yeni geliştirme, mevcut test paketini değiştirmeden sonraki sürümler için yapılır. Sürüm adları hedef paketlerdir; kesin teslim tarihi veya tamamlanmış özellik beyanı değildir. İlk genel yayın kapsamındaki geniş hedefler tek sürümün zorunlu listesi olarak kullanılmaz.

| Öncelik | İş paketi | Kullanıcıya faydası ve kabul ölçütü |
| --- | --- | --- |
| Her aşamada | Test geri bildirimi ve güvenilirlik | Çökme, dosya kaybı, yanlış çıktı ve kaydetme hatası yeni özelliklerden önce çözülür. Cihaz, Android sürümü, dosya türü ve tekrar adımlarıyla kayıt tutulur; ilgili akış yeniden doğrulanır. |
| Her sıkıştırma motorunda | Boyut faydası denetimi | Gerçek çıktının boyutu kaynakla karşılaştırılır. Eşit / büyük dosya normal sıkıştırma kaydından geçmez. Düzenleme veya dönüşüm kopyası gerekiyorsa yalnızca ilgili sonuç için açık onayla kaydedilir. Video ön tahmini uyarı niteliğindedir; kalite / çözünürlük kendiliğinden düşürülmez. Bu sözleşme ileride PDF / belge sıkıştırmaya da uygulanır; mevcut PDF sunum oluşturma akışı engellenmez. |
| 1 — 0.1.1–0.1.2 | Sunum düzenleme | Slayt silme, çoğaltma, yukarı / aşağı taşıma; birkaç hazır düzen ve tema; sunum taslağında otomatik kayıt. Beş slaytlık sunumun sırası ve görselleri yeniden açılışta ve PDF'de korunur; kayıt hatası görünür olur. Önce taslak kayıt güvenilirliği, ardından slayt işlemleri, sonra düzen / tema geliştirilir. |
| 2 — sonraki küçük güncelleme | Araçlar arasında geçiş ve ilk format genişlemesi | Üretilen görseli dosya seçiciden tekrar bulmadan sunuma ekleme; kaydedilmiş çıktıyı açma / paylaşma; doğrulanmış JPEG, PNG ve WebP giriş / çıkışları; fotoğraflardan PDF. Şeffaflık ve kalite kayıpları açıklanır. Dosya erişimi ve çıktı ömrü doğrulanmadan ortak kitaplık vaat edilmez. |
| 3 — 0.2 hedefi | Kısa video oluşturma | Önce iki uyumlu klibi sıralayıp tek MP4'e birleştirme; ardından 9:16 kadraj, basit yazı ve kullanıcı ses dosyası. Her adım ayrı doğrulanır; önizleme ve çıktı süreleri, kadraj, yazı ve ses uyumu karşılaştırılır. Video ayarlarını taslak olarak sürdürme bu paketle birlikte ele alınır. |
| 4 — ayrı test güncellemesi | Reklam ve izin akışı | Temel kullanım geri bildirimi alındıktan sonra geliştirme / test reklamları, uygun ana sayfa banner alanı ve sınırlı doğal görev geçişleri denenir. Reklam hatası düzenleme veya dışa aktarmayı durdurmaz. Reklam beyanı, reklam kimliği, veri güvenliği ve iki dilde gizlilik metni [yayın kontrol listesi](../releases/PLAY_CLOSED_TEST_PREPARATION.md) ile birlikte güncellenir. |

Her paket sonunda değişen akış için birim / emülatör kontrolleri, dar ekran / tablet / büyük yazı ve ilgili 22 dil kaynak kontrolü yapılır. PDF ve medya değişikliklerinde gerçek çıktılar açılıp kontrol edilir. Kaydedilmiş eski sunum taslakları yeni sürümde açılır; biçim değişirse veri geçişi doğrulanır. Play'e gönderilecek yeni paket daha yüksek sürüm koduyla hazırlanır ve mağaza açıklaması yalnızca dağıtılan yetenekleri anlatır.

İlk iş paketi sunum düzenlemedir. Kritik test geri bildirimi gelirse bu sıra durdurulup hata düzeltmesine dönülür. Office belgelerini düzenleme / tam dönüşüm, OCR, otomatik altyazı, gelişmiş katman efektleri ve büyük format listesi daha sonraki aşamalarda ayrı uyumluluk ve maliyet çalışmalarıyla genişletilir.

5 Ekim tasarım devamı: `0.1.3 (4)` için yuvarlak arayüz, mercan marka yazısı, standart buz mavisi araç simgeleri, arka plandan ayrılan lacivert koyu kartlar ve kısa açılış geçişi tamamlandı. Kişisel ekipte düzenlenebilir Figma referansı oluşturuldu. [Test raporu ve ayrı paket](../design/UI_DESIGN_TEST_REPORT.md); AAB Play’e gönderilmedi. Sonraki ürün işi ortak çıktı aktarımı ve doğrulanmış JPEG / PNG / WebP desteği; testçi hataları öncelikli kalır.

5 Ekim mimari / performans devamı: `0.1.4 (5)` için ekran ayrımı, ihtiyaç oldukça slayt / proje hazırlama, önizleme iptal temizliği, JPEG bellek kopyalarının azaltılması ve yayın küçültmesi eklendi. Özellik / kalite seçenekleri azaltılmadı. [İnceleme ve doğrulama](../development/MODULARITY_AND_PERFORMANCE.md) düşük donanımda ölçülmesi gereken kalan yolları da belirtiyor. Ortak çıktı aktarımı / format genişlemesi ürün sırasındaki sonraki iştir; fiziksel düşük bellekli cihaz ve üretici codec testi yayın güvenilirliği için ayrıca sürer.

5 Ekim sıkıştırma geri bildirimi: `0.1.5 (6)` görsel / video çıktısında gerçek boyut faydası kontrolünü tamamlar. Eşit / büyük dosya normal kayıttan geçmez; düzenlenmiş veya dönüştürülmüş kopya için sonuca özel açık onay gerekir. Video ön uyarısı, 22 dil kaynakları ve imzalı küçültülmüş APK'nın telefon / tablet / büyük yazılı dar ekran akışları doğrulandı. [Rapor ve ayrı paket](../development/COMPRESSION_BENEFIT_CHECKS.md); Play'e gönderilmedi. Ortak çıktı aktarımı / format genişlemesi sıradaki ürün işi olmaya devam ediyor.

5 Ekim geliştirme devamı: `0.1.1 (2)` için sunumda otomatik kayıt, kayıt durumu / yeniden deneme, slayt silme / çoğaltma / sıralama tamamlandı. Eski taslak uyumluluğu, yavaş / başarısız yazım ve telefon / tablet / dar ekran görevleri doğrulandı. [Rapor ve ayrı imzalı paket](../features/presentations/PRESENTATION_AUTOSAVE_TEST_REPORT.md). Yeni AAB Play'e gönderilmedi; önceki test paketi korunuyor. Sunum temasını ve hazır düzenleri geliştirme, ilk iş paketinde kalan sonraki adımdır.

5 Ekim geliştirme devamı: `0.1.2 (3)` için üç sunum teması, slayt başına üç görsel yerleşimi ve PDF ile aynı çizimden üretilen slayt önizlemesi tamamlandı. Otomatik kayıt / çoğaltma / taşıma / yeniden açma ve gerçek PDF tasarımı doğrulandı; telefon tam takım 35/35, tablet ve dar ekran tasarım akışı ayrı ayrı 1/1 geçti. [Rapor ve ayrı imzalı paket](../features/presentations/PRESENTATION_THEME_LAYOUT_TEST_REPORT.md). Yeni AAB henüz Play’e gönderilmedi. İlk sunum iş paketi tamamlandı; sonraki iş ortak çıktı aktarımı ve doğrulanmış JPEG / PNG / WebP format genişlemesidir. Testçi hataları öncelikli kalır.

## 5 Ekim — 0.1.7 görsel formatları ve fotoğraf PDF'si

JPEG / PNG / durağan WebP giriş ve gerçek JPEG / PNG / kayıplı WebP çıktısı eklendi. PNG / WebP şeffaflığı, biçime özel kayıp açıklaması ve sonuca özel büyük dosya onayı korunur. Fotoğraflar çoklu seçilip sıralanır; yönleri düzeltilerek A4 PDF yapılır, gerçek sonuç önizlenir, kaydedilir, açılır ve paylaşılır. İşlenen görsel doğrudan bu araca eklenebilir. Fotoğraf seçimi ekran döndürmede korunur; süreç yeniden başlatma için taslak kaydı henüz yoktur. Görsel işlevleri / kalite seçenekleri azaltılmadı.

[Kapsam, sınırlar ve doğrulama](../features/images/IMAGE_FORMATS_AND_PHOTO_PDF.md), nihai ölçümler `output/development/0.1.7/qa-summary.json` içinde tutulur. Gizlilik metni yerel olarak güncellendi; web / Play yayını ayrı adımdır. Sonraki ürün paketi PDF birleştirme / bölme / sıralama için güvenli belge altyapısıdır. Testçi hataları öncelikli kalır.
