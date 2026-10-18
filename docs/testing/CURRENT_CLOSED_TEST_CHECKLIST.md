# Birlevo — güncel kapalı test kontrol listesi

18 October account privacy QA: [real Birlevo UMP messages](../development/ADVERTISING_ACCOUNT_QA.md) are verified using an explicit debug account profile, with a sample banner unit throughout. The owner chose English-only messages. **26 emulator checks pass** (13 each on API 24/36): EEA accept/reject/withdraw, US opt-out persistence/change, other-region behavior, actual offline failure, account/default startup and manifest boundaries. **176 JVM tests**, lint **0 errors / 7 existing warnings / 0 hints**, exact 132-artifact/native coverage and 22 locale resources pass. Default debug still uses the sample account; even account properties cannot activate release advertising. No signed release or version increment. Next: final user-facing Home placement/privacy entry, then integrated resident-SDK/media qualification; audience, store linking/app-ads.txt/readiness and final Play disclosures remain open.

18 October advertising website setup: [publisher text and EN/TR policy publication](../releases/ADVERTISING_SITE_SETUP.md) is complete on the existing Pages project. All 9 public assets return 200 and match the reviewed sources; the publisher response is plain text. The English homepage, blue header and icons are preserved. This changes website content only; Android artifacts, version, advertising activation and the previous emulator results are unchanged. Account message languages/publication, real-account form tests, developer website/store linking, Google crawling/readiness and final Play disclosures remain separate gates.

18 October Home banner: [sample placement and activation gates](../development/ADVERTISING_PLACEMENTS.md) adds a manually started debug-only adaptive banner with consent rechecks, explicit main-thread callback dispatch, a 15-second load deadline and persisted 60-second app-attempt cooldown. **176 JVM / 35 passing emulator checks** (API 24: 7; API 36: 28 including 8 large-text/dark/RTL checks); one separately opted-in TalkBack-speech case is skipped. All 6 network cases show the actual sample banner after declining the public sample form, then verify lifecycle removal. Lint remains 0 errors / 7 warnings / 0 hints; exact 132-artifact/native license and package checks pass. Owner app/unit IDs and European/US message drafts are recorded; no live identifiers are used by the app. Privacy publication, supported message languages, actual-account forms, audience/readiness/app-ads.txt and integrated resident-SDK/media qualification remain gates. No new signed release; frozen 0.1.11 (12) remains unchanged.

18 October warning follow-up: [lint and compatible AndroidX maintenance](../development/LINT_WARNING_REVIEW.md) records the current **167 JVM / 91 selected emulator checks** (API 24: 12; API 36: 79, including 200%-text/dark/Arabic RTL), **lint 0 errors / 7 warnings / 0 hints**, and exact **132-artifact** license coverage. Real sample-provider consent/SDK initialization is checked on both APIs; no ad load or production activation is introduced. Initial diagnostics and device-codec limits remain explicit in the report. The historical foundation snapshot below retains its original artifact-specific counts. Frozen signed 0.1.11 (12) remains unchanged.

18 October advertising foundation: [AdMob/UMP](../development/ADVERTISING_FOUNDATION.md) adds a manual debug consent entry, current-provider eligibility, bounded loading and immediate cancellation on close. **167 JVM / 40 dedicated API 24/36 emulator checks**, lint 0 errors / 60 warnings / 2 hints, exact 128-artifact commercial coverage and retained SDK/native notices/source checks pass. The real Publisher Test Ads form's rejection path initializes the SDK without an ad load; eligibility closes immediately on leaving the panel. Combined resident-SDK/heavy-media qualification, actual Birlevo messages/units, placements/caps, app readiness/app-ads.txt and production disclosures remain open. Release activation remains false; frozen signed 0.1.11 (12) is unchanged. Next: step 6 test placements after app/message registration.

17 October storage update: [cleanup and service ownership](../development/STORAGE_CLEANUP_SAFETY.md) preserves aged share sources, recovered job outputs, session assets and shared project images. Known canonical ownership gates replace broad age sweeps; uncertain recovery defers deletion. Idle-service shutdown cannot cancel the next attachment's work. Final debug evidence: 157 JVM / 49 targeted API 24/36 emulator checks, lint 0 errors / 60 warnings / 2 hints, exact 97-artifact commercial coverage and native source/alignment checks. The signed 0.1.11 (12) package is unchanged. Next: production-plan step 5, advertising foundation and applicable consent/data/license review; signed release gates remain open.

17 October video update: [long video/legacy frame reliability](../development/VIDEO_RESOURCE_RELIABILITY.md) adds small sampled legacy posters and repairs green-frame output caused by repeated legacy seeks. Final debug verification has 153 JVM / 39 targeted workflow checks, three independently decoded 599-second videos and full-size 4K still output. Existing copy consent, quality choices and source/result ownership remain. Next is production-plan step 4, storage/cleanup safety. The frozen signed package and its release gates remain unchanged.

17 October resource update: [image-heavy external PDFs](../development/EXTERNAL_PDF_RESOURCES.md) now use a descriptor-backed isolated stream arena, preserving the near-limit document's encoded images and vector text. Source/result protection, storage/cancel/death cleanup, 4096 outputs and the 2000-vector-page capability have 153 JVM / 38 targeted emulator checks; the next step is video/legacy preview reliability. The frozen signed package is unchanged, and its release gates below remain open until a new build is requested.

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
