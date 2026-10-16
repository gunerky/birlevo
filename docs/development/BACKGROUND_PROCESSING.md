# Birlevo — arka plan işlem yönetimi

6 Ekim 2026, geliştirme kodu. Yeni sürüm numarası veya imzalı release APK / AAB üretilmedi. Dondurulmuş 0.1.8 (9) paketi korunur.

## Uygulanan kapsam

Video çıktı üretimi ve fotoğraftan PDF oluşturma, Activity / ViewModel ömründen bağımsız `ProcessingApplication` tarafından sahiplenilen işlerdir. Kullanıcı işlemi görünür ekranda başlatır. `ProcessingService` ön plan bildirimiyle çalışır; Geri, ana ekrana gitmek ve Activity'nin kapanması çalışan işi iptal etmez. Ekranda iptal ve bildirimde iptal ayrı eylemlerdir. Önizleme oynatımı arka plan ses oynatıcısına dönüştürülmedi.

Bir ağır kodlama / PDF işi çalışırken diğer araçtaki iş sırada bekler. Bu sıra kalite, nihai çözünürlük veya sayfa içeriğini düşürmez. Bekleyen iş motoru başlamadan iptal edilebilir. Her araç aynı anda bir iş kabul eder. Kaynak aktarımı, kaydetme seçicisi, PDF sayfa görüntüleme ve diğer araçlar bu servise taşınmadı; bu paket video kodlama ve fotoğraf PDF üretimini kapsar.

Android 15+ cihazlarda `mediaProcessing`, Android 14 ve öncesinde uygun `dataSync` türü kullanılır. Servis dışa açık değildir; yalnız kullanıcı işlemiyle başlatılır, açılış / boot / otomatik yeniden deneme tetikleyicisi yoktur. İşler bitince bildirim, servis ve süreli wake lock bırakılır. Android süre sınırında `onTimeout` işleri durdurur ve `stopSelf` çağırır. Mevcut video motorunun 20 dakikalık işlem sınırı korunur. Servisin 6 saatle sınırlı wake lock'u sürekli uyanık tutma sözü veya üretici pil yönetimi garantisi değildir.

Android 13+ bildirim izni için ekranda isteğe bağlı bir düğme vardır. İzin reddedilince temel işlem engellenmez; Android'in izin verilmemiş ön plan servislerini gösterme biçimi geçerlidir. Bildirim iptali, kendi kaydedilmiş iş kimliklerini hedefler; eski bir iptal eylemi yeni işi iptal edemez. Bildirime dokunmak uygulamayı açar; ilgili araca kullanıcı geçer.

## Kalıcı durum ve çıktı

`noBackupFilesDir/processing/session.json` atomik kayıtla her araç için son işi tutar. Sıra / çalışıyor / tamamlandı / hata / iptal / yarıda kaldı durumları kaydedilir; sık ilerleme yüzdeleri diske yazılmaz. Kayıtta kaynak dosya içeriği veya ham URI tekrarı yerine kaynak / ayar metadata'sının SHA-256 izi, iş kimliği ve çıktının özel dosya adı / gerçek boyutu bulunur.

İşin girdisi önce araç oturumuna başarıyla kaydedilir. Üretilen çıktı için kalıcı kayıt başarıyla bitmeden kullanıcıya başarı yayımlanmaz. Dosyanın özel klasörü, basename ve gerçek boyutu denetlenir. Yeni ViewModel veya yeni süreç, eşleşen kaynak / ayar kaydına ait tamamlanmış çıktıyı yeniden açar; video gerçek içeriği denetlenir, PDF sayfa sayısı / önizlemesi doğrulanır. Aktif kaynaklar ve eşleşen tamamlanmış çıktı, yaşa göre temizlikte korunur.

Sistem süreci öldürürse veya kullanıcı zorla durdurursa, kayıtta kalan sıradaki / çalışan işler `INTERRUPTED` olur. Kaynak ve ayarlar korunur, durum ekranda görünür; kullanıcı işlemi yeniden başlatır. Kodlama ortasından devam etme veya kendiliğinden yeniden üretme yoktur. Tamamlanmış sonuç ayrı olarak geri yüklenebilir. Gerçek boyut / sıkıştırma faydası kontrolü ve o sonuca özel büyük kopya onayı korunur; **onay yeni süreçte geri yüklenmez**.

İptalde motor ve dispatcher geçişlerinin ürettiği teslim edilmemiş dosyalar temizlenir. Temizleme, işi iptal edip bitmesini bekler; ardından araç oturumunun kaynakları emekliye ayrılır. Aynı araçta yeni iş başlatılırken eski işin son temizliği yeni işin kaydını silemez. UI'a teslim edilmiş bitmap zorla geri dönüştürülmez.

## Doğrulama ve sınırlar

47 JVM testi geçti; yeni işlem codec'i için 4 test dahil. API 36 `ofice_qa` emülatöründe 48 test ve 2 ayrı süreç test aşaması, toplam 50 farklı Android kontrolü geçti. Gerçek video, Activity kapanması, ekranın uyutulması, tek ağır iş sırası, bekleyen iş iptali, bildirim iptali / eski eylemi tekrar kullanma ve kısa Android `mediaProcessing` süre sınırı denendi. Mevcut video / fotoğraf PDF / kaynak oturumu / arayüz / aktarım / çıktı / PDF okuyucu kontrolleri de çalıştırıldı. Ekran kapatma senaryosu ayrıca tekrar çalıştırıldı; tekrar toplam sayıya eklenmedi. Son koşu sayıları ve loglar [QA raporunda](../../output/development/background-processing/qa-summary.json) yer alır.

Yeni süreç testi, gerçekten üretilmiş PDF sonucunun kaydını ve yaşlanmış özel çıktısını geri yükler. Kesinti yolu deterministik olarak RUNNING kaydından sonra bekletilen bir iş ile sınanır; bu bekletme gerçek encoder'ın ortasında öldürülmüş olduğunu iddia etmez. Farklı PID ve dışarıdan force-stop, yarıda kalan durumu, korunan ayarları ve kullanıcı eylemiyle gerçek video üretimini doğrular.

İlk servis doğrulaması ilerleyemedi; Android 36 servis yükseltmesi için uyumluluk yardımcısı yerine platformun sürüme göre korunan `startForeground` çağrısına geçildi ve bekleyen koşu sonlandırıldı. Düzeltilmiş yolda dört servis testi geçti. İlk derlemede fotoğraf aracı başlığının kaynak adı düzeltildi. Başarısız / sonlandırılmış denemeler başarılı koşulara sayılmaz. Lint 0 hata / 38 mevcut uyarıdır; Application sahibi ile yeni statik Context uyarısı giderildi.

22 dilde 231 anahtar / placeholder / XML kontrolü vardır; 4 yeni metin ve oturum açıklamaları güncellendi. Bu taslak çeviriler uzman onayı değildir. Jev canlı API kullanılmadı; bağımsız geliştirme aracının çevrimdışı testleri ve APK izolasyonu denetlenir.

Fiziksel düşük donanım / pil / ekran kapalı uzun çalışma, eski Android / üretici servis kısıtları, gerçek dolu disk ve bütün belge sağlayıcıları matrisi ayrıca ölçülmelidir. Bu sonuçlardan FPS, süre veya pil garantisi çıkarılmaz. Yeni kod için R8 / imzalı release doğrulaması kullanıcı yeni paket istediğinde yapılır. Sonraki ürün işi seçilebilir ek çıktı biçimleri, ses aracı ve codec denemeleridir; fiziksel cihaz / depolama doğrulaması bakım listesinde kalır.

## Sonraki yayın kontrolü

Yeni manifestte ön plan servis türü izinleri, isteğe bağlı bildirim ve wake lock vardır; INTERNET / reklam SDK'sı / kullanıcı dosyasını sunucuya gönderme eklenmedi. Sonraki AAB hazırlanırken Play Console ön plan servis beyanları (`mediaProcessing` ve eski cihazlar için `dataSync`), kullanıcı başlatma / görünür bildirim / iptal akışını gösteren açıklama ve gerekirse demo birlikte tamamlanır. Mevcut kapalı test AAB'si bu geliştirme kodunu içermez. Reklam / reklam kimliği / veri güvenliği değişiklikleri reklam SDK'sı eklendiğinde ayrıca güncellenir.

Android kaynakları: [servis türleri ve kullanım sınırları](https://developer.android.com/develop/background-work/services/fgs/service-types), [arka plandan başlatma kısıtları](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [bildirim izni](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
