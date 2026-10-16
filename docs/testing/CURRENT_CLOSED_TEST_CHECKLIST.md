# Birlevo — güncel kapalı test kontrol listesi

Current first-production scope: follow the [first production plan](../product/FIRST_PRODUCTION_PLAN.md). Reliability and advertising precede the requested release candidate; new formats, broader Office and OCR are deferred. The [presentation resource report](../development/PRESENTATION_EXPORT_RESOURCES.md) adds 147 JVM / 60 targeted emulator checks on the merged development code. No new signed package or production eligibility is implied by those results. This checklist remains the artifact-specific test/release evidence ledger.

16 October development update: [legacy Android and PDF memory](../development/LEGACY_ANDROID_AND_PDF_MEMORY.md) adds API 24/28 verification, exact slide/photo dimensions and disk-backed photo PDF generation, including 100 photos at 4096 and 2000 vector pages. [Resource budgets](../development/OPERATION_RESOURCE_BUDGETS.md) remain. English document names and README do not imply a new app package. Frozen 0.1.11 artifacts still need replacement only on an explicit release request.

15 Ekim 2026. Geliştirme kodu ve gönderilecek imzalı paket ayrı doğrulanır. Son hazırlanmış imzalı paket **0.1.11 / versionCode 12** olarak korunur. Sonraki toplu dönüştürme/ZIP/Office değişikliklerinin bu eski pakette bulunduğu veya Play'de dağıtıldığı varsayılmaz. **Bu adım yeni release üretmez veya Console'a gönderim yapmaz.**

## Geliştirme zinciri

16 Ekim ek doğrulaması: [cihaz kapasitesi / ZIP boyut uyumu](../development/DEVICE_CAPACITY_AND_ZIP_LIMITS.md) geliştirme kodunda tamamlandı. 143 JVM / 10 emülatör testi, tam 512 MiB ZIP sınırı, 128 MiB üzeri gerçek WAV aktarımı ve düşük alan / geri alma denetlendi. Bu değişiklikler dondurulmuş 0.1.11 paketine sonradan eklenmiş sayılmaz; yeni yayın doğrulaması kullanıcı istediğinde yapılır.

Kanıt ve komutlar: [toplu dönüştürme → ZIP → klasör raporu](../features/batch/BATCH_ZIP_FOLDER_FLOW_AND_REGRESSION.md). Yerel cihaz kontrolleri özel `ofice_qa`, `ofice_qa_api24` ve `ofice_qa_api28` emülatörlerinde yapılır; fiziksel cihaz veya Microsoft Office gerektirmez.

- [x] Görsel/video/ses/belge motorları aynı kayıtlı dosyayı dönüştürme → SAF ZIP kaydı → ZIP açma → SAF klasöre çıkarma zincirinden geçirdi.
- [x] Dönüştürülen sonuç, ZIP girdisi ve gerçekten çıkarılan dosyanın bayt/hash eşitliği denetlendi; host'ta bağımsız okuyucu/çözücüyle açıldı.
- [x] Aynı adlı kaynaklar ayrı güvenli adlar aldı. Kuyrukta hariç bırakılan öğe ZIP'e girmedi; ZIP içinde seçilmeyen dosya çıkarılmadı.
- [x] Office kayıp onayı ile gerçek büyük/eşit dosya kopyası onayı ayrı uygulandı. Yeni sonuca eski kopya onayı geçmedi; eski tarif/kayıt isteği hedefe yazmadı.
- [x] İptalde kaynak/kayıtlı ZIP korundu; aynı ZIP ile yeniden çıkarma çalıştı. Çalışmayı temizlemek kaydedilmiş ZIP ve çıkarılmış dosyayı silmedi.
- [x] Dönüşüm ortasında süreç kapatılıp açıldı; tamamlanan dosya yeniden kodlanmadan korundu, kalan iş tamamlandı ve yeniden açık onayla ZIP/kayıt zinciri sürdü.
- [x] Gerçek Android klasör seçici, açık telefon, Arapça/RTL yüzde 200 yazı ve koyu tablet akışları denetlendi.
- [x] Debug/JVM/lint, 22 dil kaynak denetimi, ticari runtime lisans kapsamı ve yerel kod grafiği güncellendi.

## Kullanıcı istediğinde hazırlanacak yeni yayın paketi

Bu bölüm debug testinden otomatik olarak tamamlanmış sayılmaz. Yeni sürüm numarası ve imzalı APK/AAB yalnız açık yayın/derleme isteğinde hazırlanır.

- [ ] Dondurulacak commit ve yeni versionName/versionCode kaydedildi; İngilizce/Türkçe sürüm notları gerçek değişikliklerle yazıldı.
- [ ] R8/kaynak küçültmeli imzalı APK/AAB doğrulandı. Yayın smoke testi özel QA emülatöründe geçti; kaynak/oturum/izin/iptal/kopya onayı korundu.
- [ ] Native semboller AAB içinde doğrulandı veya **aynı build/versionCode** ile Console'a yüklendi. Eski sürüm sembolleri kullanılmadı.
- [ ] `exportRuntimeLicenseInventory` ve `audit_runtime.py --check` güncel release classpath için geçti. LAME kaynak/lisans kaydı, kütüphane değiştirme, dört ABI ve 16 KiB paket hizalaması yayın APK'sında tekrar doğrulandı.
- [ ] Mağaza metni, destek matrisi ve telefon görselleri gerçekten paketteki yeteneklerle eşleşti; tam Office uyumluluğu veya her cihazda codec desteği vaat edilmedi.
- [ ] Reklam/AD_ID/veri güvenliği/gizlilik beyanları gerçek SDK ve veri davranışıyla eşleşti. Reklam SDK'sı eklenirse [reklam kontrol listesi](../releases/PLAY_CLOSED_TEST_PREPARATION.md) birlikte uygulandı.
- [ ] Console'da **kapalı test** kanalı, ülke ve testçi grubu doğrulandı; doğru AAB/versionCode seçildi. İki dilde notlar doğru etiketlerle girildi.
- [ ] Ön plan servisi beyanı/demonstrasyon bağlantısı gerçek izin ve görevlerle eşleşti; uyarılar yeni paketin sürüm koduyla kontrol edildi.
- [ ] İnceleme onayı/kanal kullanılabilirliği Console'da ayrıca doğrulandı. Gerekli grup bağlantısı ile test katılım bağlantısı paylaşıldı; aynı Google hesabıyla katılım/indirme denendi.

## Testçinin uygulayabileceği kısa senaryo

1. **Toplu işlemler → Görsel** altında iki aynı adlı küçük görsel ekleyin. BMP seçip sonucu oluşturun. Büyük/eşit sonuç için dosya başına onay olmadan ZIP oluşturulamadığını ve kalitenin kendiliğinden düşmediğini kontrol edin.
2. ZIP incelemesinde başarı/hata/hariç bırakılan dosya sayılarını kontrol edip yeni ZIP kaydedin. Ana sayfada **Dosya aç** ile bu ZIP'i seçip **ZIP aç** işlemini açın.
3. Yalnız bir dosya seçip **Klasöre çıkar** deyin. Android'in izin verdiği bir alt klasör seçin. Yeni Birlevo klasöründe yalnız seçilen dosyanın bulunduğunu; önceki dosyaların ve ZIP'in korunduğunu kontrol edin.
4. Aynı zinciri ses, video ve uygun metin/tablo/Office girdileriyle tekrarlayın. Excel'de çalışma sayfasını ve hücre okuma tercihini açıkça seçin; desteklenmeyen hedefin dosyaya özgü hata vermesini kontrol edin.
5. Bir işlemi iptal edip yeniden deneyin. Çalışmayı temizledikten sonra özgün kaynak, kaydedilen ZIP ve çıkarılmış dosyanın açılabildiğini doğrulayın.

ZIP oluşturmak sıkıştırma başarısı garantisi değildir. Toplu kuyruğun ve ZIP okuyucusunun sınırları farklıdır: okuyucuda **128 MiB/dosya, 512 MiB toplam açılmış içerik ve 1000 öğe** vardır. Büyük girdi reddedilirse asıl ZIP silinmez; kalite/çözünürlük sessizce düşürülmez. Şifreli/split/ZIP64/7Z/RAR desteklenmez. İptal/ani kesinti veya sağlayıcının silmeyi reddetmesi kısmi hedef bırakabilir; uygulamadaki uyarı izlenmelidir.

Hata bildirimi: sürüm kodu, Android sürümü/emülatör profili, dosya türü/boyutu, hedef ve adımlar. Özel dosya veya API anahtarı istenmez; mümkünse yapay örnek kullanılır. Destek: `support@birlevo.com`.
