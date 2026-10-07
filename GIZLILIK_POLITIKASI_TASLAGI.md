# Birlevo gizlilik politikası — 0.1.0 yayımlanan metin

**Yürürlük tarihi:** 5 Ekim 2026  
**İletişim:** `support@birlevo.com` · Gizlilik talepleri: `privacy@birlevo.com`

Birlevo'nun 0.1.0 kapalı test sürümü görselleri, videoları ve sunum taslaklarını Android cihazınızda işler. Uygulama hesabı, bulut eşitlemesi, reklam SDK'sı veya analiz SDK'sı içermez. Seçtiğiniz dosyalar ve üretilen medya geliştiricinin sunucusuna gönderilmez.

Android sistem dosya seçicisiyle açtığınız görsel veya videoya yalnızca seçiminiz doğrultusunda erişilir. Görsel ve video işleme için cihazın uygulamaya özel alanında geçici kopya veya çıktı tutulabilir; araç tekrar açıldığında 24 saatten eski işlem dosyaları temizlenir. Sunum taslakları uygulamanın özel alanında saklanır. Slayta eklenen görselin kalıcı Android dosya referansı taslakta tutulur; kaynak taşınır veya silinirse görsel açılamayabilir.

Çıktıyı dışa aktardığınızda kaydetme yerini Android'in sistem ekranında siz belirlersiniz. Seçtiğiniz bulut depolama sağlayıcısının veya paylaşım uygulamasının veri uygulamaları kendi politikalarına tabidir. Birlevo sunucusuna dosya aktarmaz.

Uygulama kullanımı sırasında geliştiriciye kişisel veri aktarımı, üçüncü taraf reklam paylaşımı veya davranışsal izleme yapılmaz. Uygulama verilerini cihaz ayarlarından silerseniz yerel taslaklar ve geçici çalışma dosyaları kaldırılır; sistem dosya seçicisiyle dışa aktardığınız dosyalar seçtiğiniz konumda kalır.

Bu web sayfası Cloudflare Pages üzerinde barındırılır. Cloudflare'ın web ziyaret ölçümü sayfanın kullanımına ilişkin teknik verileri işler; bu ölçüm Android uygulamasındaki medya dosyalarına erişmez.

Sorularınız için `support@birlevo.com`, gizlilik talepleriniz için `privacy@birlevo.com` adresine yazabilirsiniz. E-posta gönderirseniz, iletiniz ve belirttiğiniz iletişim bilgileri talebinize yanıt vermek için kullanılır. Bu adreslere gelen iletiler Cloudflare Email Routing üzerinden geliştiricinin gelen kutusuna yönlendirilir. Reklam, analiz, bulut veya yeni dosya işleme hizmetleri eklenirse politika ve Play veri güvenliği beyanı ilgili sürümden önce güncellenecektir.

**Yayın durumu:** Metin 5 Ekim 2026'da `https://birlevo.com/privacy/` adresinde yayımlandı ve HTTPS üzerinden görüntülendi. Son AAB izinleri / SDK'ları yeniden incelenmeli; e-posta teslimi henüz gerçek bir iletiyle doğrulanmadı.

## 0.1.7 yerel güncelleme — 5 Ekim 2026

`web/privacy/index.html` ve `web/en/privacy/index.html` 0.1.7 için güncellendi. Yeni sunum görselleri 0.1.6'dan itibaren özel proje kopyalarıdır; eski taslakların kaynak bağlantıları korunur. Fotoğraf PDF'si için geçici özel görsel kopyaları, ekran döndürme / süreç yeniden başlatma davranışı ve 24 saat temizliği açıklanır. JPEG / PNG / durağan WebP işleme cihazda kalır; reklam / analiz veya ağ yükleme SDK'sı eklenmedi. Bu yerel güncelleme henüz Cloudflare'a dağıtılmadı. Son yayın adayını kullanıma açmadan önce güncel metin ve mağaza açıklaması kontrol edilir; reklam eklendiğinde reklam içerir, reklam kimliği ve veri güvenliği beyanları ayrıca güncellenir.

## 0.1.9 yerel güncelleme — 7 Ekim 2026

7 Ekim HTTP kontrolünde üç canlı sayfa 200 döndürdü ve 0.1.7 metni görünüyordu; yukarıdaki dağıtılmadı kaydı 5 Ekim'deki hazırlık anını anlatır. `web/index.html`, Türkçe ve İngilizce gizlilik sayfaları şimdi 0.1.9'ın mevcut ses / belge / PDF araçları ve kalıcı video / ses / fotoğraf PDF çalışma davranışına göre güncellendi. Kaydedilmiş kaynak / sonuçlar 24 saat temizliğinin dışında tutulur; Çalışmayı temizle özel dosya emekliliğini başlatır. Açma / paylaşım önbelleği ve eski sunum referansları ayrı açıklanır. Uygulamada reklam / analiz SDK'sı veya kullanıcı medyasını sunucuya gönderme eklenmedi.

Bu **0.1.9 güncellemesi 7 Ekim 2026 tarihinde canlıya dağıtıldı**. Son Cloudflare üretim dağıtımı `44bc220e-3a94-4450-a0db-d8e64a019679`; üç sayfa ve arka plansız sekme simgeleri doğrulandı. Güncel sekiz dosyalık ZIP ve hash manifesti `output/development/video-canvas-speed/` içindedir. Geliştirme kodundaki yayımlanmamış codec / iki kaynaklı düzenleme özellikleri kamu sayfasına eklenmedi. [Yayın adımları ve bakım kaydı](PLAY_UYARILARI_VE_SITE_BAKIMI.md).
