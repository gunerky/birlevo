# Birlevo — video ve fotoğraf PDF oturum dayanıklılığı

6 Ekim 2026. Geliştirme kodunda uygulanmıştır. Sürüm numarası artırılmadı; imzalı release APK / AAB hazırlanmadı. Önceki 0.1.8 (9) paketi değişmedi.

## Kullanıcı davranışı

- Video aracında özel kaynak kopyası, kalite, 640 / 1280 / 1920 uzun kenar seçimi ve kesim aralığı geri yüklenir. Kaynağın gerçek codec / profil / süre bilgisi yeniden denetlenir; eski poster diske yazılmaz.
- Fotoğraf PDF aracında özel kaynak kopyaları, sayfa kimlikleri, sıralama ve 2048 / 4096 kenar seçimi geri yüklenir. Görsellerin gerçek içeriği yeniden doğrulanır. Mevcut 100 sayfa, görsel başına 128 MiB ve toplam 512 MiB sınırları korunur.
- Geri düğmesi çalışmayı korur, devam eden araç işlemini iptal eder ve oturum kaydını tetikler. “Çalışmayı temizle” ayrı eylemdir. Kayıt durumu, hata ve yeniden deneme ekranda gösterilir; ekran pasifleşirken de kayıt tetiklenir.
- Yeni bir kaynak eklenemediğinde önceki geçerli çalışma korunur. Kayıt hatasında önceki kalıcı kaydın kullandığı kaynaklar hemen silinmez. Açılışta bozuk / eksik kaynak hatası gizlenmez; yeniden deneme veya açık temizleme mümkündür.
- Tamamlanmış çalışma çıktısı, PDF önizleme sayfası, oynatma konumu ve büyük kopya onayı bu oturumlara yazılmaz. Süreç yeniden açıldıktan sonra kullanıcı çıktıyı yeniden üretir. Yarım kalan kodlama / PDF üretimi otomatik sürdürülmez.

Kaynaklar ve ayarlar kalıcı kaydın tamamlanmasından sonra dayanıklıdır. 150 ms bekleme ile sık değişiklikler birleştirilir. Kullanıcı son değişikliğin kaydı bitmeden süreci zorla sonlandırırsa son başarılı kayda dönülebilir; Android'in süreç öldürmeden önce lifecycle bildirimi göndereceği varsayılmaz. Kayıt durumu bu ayrımı görünür kılar.

## Saklama ve sahiplik

`media/MediaSession.kt` sürümlü JSON codec, sınırlandırılmış okuma ve ortak tek yazıcıyı içerir. Video ve fotoğraf araçları kendi `noBackupFilesDir/video-compression/` ve `noBackupFilesDir/photo-pdf/` alanlarında `session.json` tutar. Kaynaklar mevcut özel medya dosyalarıdır; yeniden kodlama veya uzak servise aktarım yoktur. JSON yalnız metadata / dosya referansları içerir ve en çok 512 KiB'dır. Mevcut FileProvider yetkisi ve kökleri korunur.

Yazıcı Mutex ve birleştirilen istek kanalı kullanır. IO'daki `AtomicFile` yazımı bitmeden önceki kaydın kaynakları emekliye ayrılmaz. Yavaş kayıt sırasında yeni değişiklik gelirse daha yeni kayıt beklemeye devam eder; güncel kaydın referansları silme listesinden çıkarılır. Temizleme de atomik `null` kaydıyla yürür; başarısız temizleme kaydı eski kaynakları korur. Kullanıcı yeniden denediğinde son durum yazılır. Eski / referanssız çalışma dosyaları mevcut yaşa göre temizlik politikasına tabidir; geri yüklenen aktif kaynaklar, yaşları 24 saati aşsa bile bu temizlikten korunur.

Dosya referanslarında ad / kimlik / URI uzunluğu, basename, izin verilen özel klasör, gerçek dosya boyutu ve mevcut medya sınırları denetlenir. Geç gelen kaydetme seçicisinin hedefi hem güncel hem de seçici açılırken yakalanmış orijinal URI'larla karşılaştırılır. Eski çıktıya ait dönüş, yeni çıktıya uygulanmaz. Mevcut gerçek boyut karşılaştırması ve sonuca özel büyük kopya onayı sürer; onay yeni süreçte taşınmaz. URI takma adlarının aynı fiziksel dosyayı gösterdiğini bütün belge sağlayıcıları için belirleyen yeni bir mekanizma eklenmedi.

## Doğrulama

API 36 `ofice_qa` emülatöründe debug uygulaması kullanıldı. Fiziksel telefon ölçümü yapılmadı.

| Kontrol | Sonuç |
| --- | --- |
| JVM birim testleri | 43 geçti; 4 yeni oturum codec testi dahil |
| Yeni Android oturum testleri | 6 geçti: özel / yaşlanmış kaynak, sıra / ayarlar, başarısız kayıt ve yeniden deneme, yavaş yazıcı, kesilmiş atomik yazım / geçersiz içerik, geç seçicide orijinal koruması |
| Gerçek süreç kapatma | 2 ayrı test aşaması geçti; kaynak / ayarlar hazırlandı, dışarıdan `am force-stop` uygulandı, farklı PID'de geri yükleme ve yeniden video / PDF üretimi doğrulandı |
| Mevcut akış regresyonu | 38 farklı test son durumda geçti: video 10, fotoğraf PDF 5, arayüz 6, çıktı aktarımı 5, medya çıktıları 6, PDF okuyucu 6 |
| Çeviri kaynakları | 22 dilde 227 anahtar / placeholder / üretilen XML kontrolü geçti; 5 yeni anahtar ve fotoğraf PDF açıklaması güncellendi |
| Jev geliştirme aracı | 20 çevrimdışı test ve kaynak / debug APK izolasyon denetimi geçti; canlı API kullanılmadı |
| Debug / lint | Debug uygulama ve test APK'sı derlendi; lint 0 hata, 38 uyarı |

Toplam 46 farklı Android test / test aşaması doğrulandı. Bazı sınıflar tekrar çalıştırıldı; tekrarlar bu sayıya eklenmedi. İlk 38 testlik çalışmada ekran dışındaki tembel düğmeyi doğrudan arayan eski fotoğraf PDF testi başarısızdı. Test, liste üzerinden düğmeye kaydıracak şekilde düzeltildi; fotoğraf sınıfının 5 testi yeniden geçti. Süreç testinin ilk hazırlık denemesinde de aynı test bulma sorunu düzeltildi. Başarısız denemeler loglarda saklanır; bunlar başarılı koşulara dahil değildir. İlk cihaz kurulum denemesi QA emülatörü kapalı olduğu için tamamlanmadı; emülatör başlatılarak doğrulama yapıldı.

Süreç testinde orijinaller silindi, özel kaynakların yaşı temizlik eşiğini aşacak şekilde değiştirildi. PID 4137'den 4271'e değişti; 1.000–4.500 ms kesim, HIGH / 1920 video ayarı, 4096 fotoğraf seçimi ve ters sayfa sırası geri geldi. Sonrasında yeni ayarlarla video ve iki sayfalı PDF üretildi. Test verileri yapaydır. [Makine raporu](output/development/media-sessions/qa-summary.json), [video ekranı](output/development/media-sessions/session-video-restored.png), [fotoğraf ekranı](output/development/media-sessions/session-photos-restored.png).

## Açık işler ve ölçüm sınırı

Uzun işlemlerin uygun arka plan işi / ön plan bildirimiyle yönetimi, kalıcı iş durumu, ekran kapatma ve Android süreç / servis sınırları sıradaki iştir. Bu paket arka planda kodlamayı tamamlayacağı veya otomatik yeniden başlatacağı sözü vermez. Depolama hatası enjekte edilen yazıcıyla sınandı; gerçek dolu fiziksel disk, bütün belge sağlayıcıları, eski Android / üretici cihazları, fiziksel düşük donanım bellek / pil / süre matrisi ve yeni kod için R8 release doğrulaması bekler. Emülatör sonucu donanım performansı garantisi değildir.

Graphify yerel AST grafiği güncellendi. 17 Kotlin dosyasında bilinen kısmi ayrıştırma uyarısı bulunur; ilişkiler kaynak kod ve testlerle doğrulanır. Android bağımlılığı, izin, codec, çıktı kalite seçeneği veya çözünürlük azaltılmadı. 22 dil için kaynak bütünlüğü kontrolü dil uzmanı onayı anlamına gelmez.
