# Proje ve çalışma yönetimi — 13 Ekim 2026

Ana sayfada kayıtlı projeler ve devam edilebilir medya çalışmaları için 48 dp işlem menüsü vardır. Menüde projeler için **Projeyi sil**, medya kayıtları için **Çalışmayı temizle** gösterilir. Kayıt adı ve silmenin kapsamı ikinci bir onayda açıklanır. İptal ve geri hareketi kayıtları değiştirmez. Kaydetme hatası halinde kayıt korunur ve tekrar denenebilir.

Devam listesindeki çalışma türü artık video, ses, fotoğraf PDF'si veya slayt-video olarak yazılır; aynı adı taşıyan sunum projesi ile araç çalışması ayırt edilebilir. Proje listesinin sunum kategorisindeki kopyasında da aynı silme menüsü vardır.

Proje silme mevcut otomatik kayıt mutex'i ve atomik dosyası üzerinden yürür. Silinen proje açık taslakla aynıysa taslak ve Android durum kaydı da başarılı commit sonrası temizlenir. Bekleyen otomatik kayıt silinen taslağı geri getiremez. Başka açık taslaklar korunur; yazma sırasında değişiklik ve içe aktarma kilitlenir. Ekranın yeniden oluşturulması veya çağıran Activity coroutine'inin iptali yarım bir silme bırakmaz.

Proje ve medya checkpoint'lerinde ayrı repository/metadata okuyucu örnekleri de dosya başına aynı süreç içi kilidi kullanır. Böylece okumanın kurtarma adımı, sürmekte olan yazının geçici dosyasına müdahale edemez. Android `AtomicFile` okuma/yazma erişimini kendi başına kilitlemez; bu davranış [resmî AOSP kaynağıyla](https://android.googlesource.com/platform/frameworks/base.git/%2B/master/core/java/android/util/AtomicFile.java) doğrulandı. Bu kilit başka süreçler arasında koordinasyon sağladığı şeklinde genellenmez.

Ana sayfadaki medya temizleme, kullanıcı isteği geldiğinde ilgili ViewModel'i açar; ilk geri yüklemenin son durumunu bekler ve mevcut reset/atomik oturum yazıcısını kullanır. Video, ses, fotoğraf PDF'si ve slayt-video desteklenir. Aktif arka plan işi sessizce iptal edilmez; işlem bitmeden temizleme yapılmaz. Temizleme sonrasında metadata yenileme sinyali Activity dışında yaşadığı için yeniden oluşturulan ana sayfa da güncellenir. Listeyi okumak hâlâ yalnız hafif metadata okur; bütün oynatıcılar başlangıçta oluşturulmaz.

Özgün URI'ler ve kullanıcı tarafından dışarı kaydedilmiş dosyalar silinmez. Araçların özel geçici kaynakları atomik temizleme başarılı olduktan sonra emekliye ayrılır. **Proje görselleri otomatik silinmez:** diğer projeler, çoğaltılmış slaytlar ve araçlarda saklanan sunum snapshot'ları bunları kullanabilir. Bütün kalıcı referansların taranacağı ayrı bir çöp toplama mekanizması olmadan kalan proje görsellerini topluca kaldırmak güvenli değildir. Bu adım bütün proje görsellerinin disk alanını geri kazanma iddiasında bulunmaz.

Yeni metinler yerel katalogda 22 dilde taslak olarak eklenmiştir; anahtar/placeholder doğrulaması insan çeviri incelemesinin yerine geçmez. SDK, codec veya çalışma zamanı bağımlılığı eklenmemiştir. Jev/TypeSafe çağrısı yoktur.

## Doğrulama

Kullanıcının isteğiyle bu geliştirme adımındaki Android testleri yalnız `ofice_qa` emülatöründe yapılır. Fiziksel cihaz veya PowerPoint kurulumu istenmez. PowerPoint bulunmadığı için gerçek Office doğrulaması yapılmış sayılmaz; önceki bağımsız PPTX/medya kanıtının kapsamı değişmez.

Debug APK/test APK ve JVM testleri: `./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`.

Emülatör koşuları: `tools/testing/project-management-qa.ps1` ve `tools/testing/project-management-qa.ps1 -SkipInstall -Layouts`. Koşucu yalnız ayrılmış QA emülatöründe çalışır; release/debug imza farkı varsa yalnız bu emülatördeki test uygulamalarını yeniden kurar. Büyük yazı, koyu tema ve tablet koşularından sonra sistem görünüm ayarlarını normale döndürür.

Günlükler `output/development/project-management/` altında tutulur. İlk denemelerdeki otomasyon/ilk yükleme hatalarının tanı günlükleri başarılı sonuçlardan ayrı kalır. Emülatör sonucu fiziksel cihaz için hız, bellek veya pil garantisi vermez.

Son kodda **106 JVM testi ve 19 emülatör testi** geçti. Emülatör kontrolleri atomik yazı sırasında ayrı metadata okuyucusu, otomatik kayıt/silme çakışması, kayıt hatası ve tekrar deneme, iptal edilmiş çağıran, onay açıkken Activity yeniden oluşturma, silme sonrası yeniden açma, paylaşılan proje görseli/özgün/dışa aktarılan dosya koruması ve fotoğraf/ses/slayt-video çalışma temizlemeyi kapsar. Video temizleme mevcut oturum geri yükleme/kaynak koruma testinde doğrulanır.

Beş yönetim akışı yüzde 200 yazı, koyu tema ve tablette ayrı ayrı geçti: **15 ek emülatör tekrarı**. Büyük yazıdaki sanallaştırılmış liste öğelerini test koşucusu önce kaydırarak yükler. Bu kapsam uygulamadaki bütün formların/TalkBack/dillerin tamamlandığı şeklinde genellenmez. Debug lint **0 hata, 63 uyarı, 2 öneri**; 22 dil üretilmiş kaynak/placeholder kontrolü ve 95 runtime artifact + native encoder lisans kapsamı geçti. Son debug APK hash'i ve yayın AAB'sinin değişmediği `output/development/project-management/qa-summary.json` içinde tutulur.

Yeni sürüm numarası, imzalı release APK/AAB veya yayın paketi üretilmez. 0.1.11 (12) yayın dosyaları değişmez; bu özelliğin yayın paketine girmesi yeni kullanıcı derleme isteğini bekler.
