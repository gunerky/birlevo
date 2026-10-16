# 15 Ekim 2026 — toplu dönüştürme / ZIP / klasör zinciri

Bu adım daha önce ayrı doğrulanan araçların birlikte çalışmasını sınar. Gerçek `BatchEngine`, `ArchiveEngine`, dosya yönlendirmesi, ViewModel ve ekranlar kullanılır. Test sağlayıcısı yalnız yapay kaynak ve SAF hedefidir; dönüştürme/ZIP/çıkarma motorlarının yerine geçmez. Medya motorları, kalite/çözünürlük seçenekleri, oturum biçimleri ve FileProvider kökleri değişmedi. Görsel kontrolde bulunan RTL dosya adı sıralama hatası ZIP ekranında düzeltildi.

## Kapsam

Kaynak → gerçek dönüştürme → sonuç başına onay → ZIP oluşturma → SAF kaydı → kayıtlı URI'yi ZIP okuyucuda açma → seçili dosyaları SAF klasörüne çıkarma. Beklenen hash ZIP'ten değil, **dönüştürülmüş dosyadan paketleme öncesi** alınır. Kayıtlı ZIP ve gerçekten SAF'a yazılmış çıkarma sonucu karşılaştırılır. Host doğrudan bu çıkarılan dosyaları açar; yalnız host'ta ZIP açıp onu doğru saymaz.

| Araç | Hedefler | Örnekler |
|---|---|---|
| Görsel | JPEG, PNG, WebP, BMP | Gürültülü JPEG, saydam PNG ve aynı adlı ikinci kaynak |
| Video | AVC/HEVC MP4, VP8/VP9 WebM | Sesli yatay/dikey video; mevcut kalite/640–1920 boyutlar |
| Ses | WAV, M4A, OGG/Opus, FLAC, MP3 | Stereo/44,1 kHz mono; OGG için açık 48 kHz hedefi |
| Belge | Uygun girdiler için TXT, CSV, TSV, PDF, DOCX, XLSX | UTF-16/Unicode metin, tırnaklı/çok satırlı CSV, Word metni/tablosu, seçili Excel sayfası |

İki aşamalı seçim, sıfır bayt TXT, iptal/yeniden çıkarma, atomik kuyruk devamı ve Office/kopya onayları da sınanır. Kaynak URI'lerinin/özel kaynak kopyalarının hash'leri tekrar denetlenir. Çalışma temizliği kaydedilmiş ZIP/çıktıyı korumalıdır.

`BatchArchiveFlowDeviceTest` matris/korumaları; `BatchArchiveFlowProcessTest` ilk kalıcı sonuçtan sonra kesinti/kalan işi tamamlama zincirini; `BatchArchiveFlowUiDeviceTest` ekranlar üzerinden bütün yolu sınar. UI'da kopya onayı, ZIP inceleme/kayıt, Ana Sayfa/Dosya aç yönlendirmesi, seçim, ekran yeniden oluşturma ve çıkarma vardır. Seçici sonucu kontrollü SAF URI'sine bağlanır; gerçek sistem klasör seçicisi ayrıca mevcut regresyonla çalışır.

## Yeniden çalıştırma

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/archive-flow-qa.ps1 -SkipBuild
python tools/localization/generate.py --check
python tools/licensing/audit_runtime.py --check
graphify update . --no-cluster
```

Koşucu yalnız `ofice_qa` emülatörünü kabul eder; telefon/font/gece ayarlarını `finally` ile geri yükler. `-EngineOnly` motor/süreç/host; `-UiOnly` yalnız üç UI profilidir. Sürüm artırılmaz, imzalı APK/AAB üretilmez. Host'ta mevcut FFmpeg, Pillow, pypdf ve Python standart kütüphanesi kullanılır; Android bağımlılığına eklenmez. Kanıt: Git dışındaki `output/development/archive-flow/` dizini.

## Kabul kanıtı

- 19 çıktı tarifi: 4 görsel, 4 video, 5 ses, 6 belge hedefi. Temel matris 46 dosya; seçim, süreçten devam ve boş TXT örnekleriyle **22 dışa aktarılmış ZIP / 50 gerçek SAF çıkarma sonucu** bağımsız host kontrolünden geçti. Seçim örneğinde ZIP'teki iki dosyadan yalnız biri çıkarılır; bunun dışında paketlenen/çıkarılan sayılar eşleşir.
- Host'ta ZIP CRC, önceden alınmış SHA-256, gerçek dosya baytları, Pillow tam görsel çözümü/ölçü/saydamlık, FFmpeg tam ses/video çözümü/codec/süre/kanallar/örnekleme ve döndürülerek çözülen video kare ölçüleri kontrol edildi. DOCX/XLSX içeriği XML/ZIP, CSV/TSV/TXT standart okuyucular ve PDF pypdf ile denetlendi. PDF denetimi tüm alfabelerde kusursuz görsel yerleşim garantisi değildir.
- **17 emülatör yürütmesi**: bütünleşik motor/koruma 7, süreç hazırlama/geri dönme 2, mevcut klasör korumaları 4, gerçek Android sistem klasör seçicisi 1, uçtan uca UI 3 profil. UI profilleri aynı yöntemi tekrarlar; 17 farklı yöntem iddiası yoktur. Tam motor/süreç/sağlayıcı sonuçları `qa-final.log`, son UI kabulü `qa-ui-rtl-fixed.log`, bağımsız sonuç `host.log` / `independent-check.json` dosyalarındadır. Başarısız ilk denemeler kabul kanıtı sayılmaz.
- Test örneğinin “Office PDF her zaman büyüktür” varsayımı gerçek boyutlara göre düzeltildi; küçük PDF için fazladan kopya onayı icat edilmedi. Ayrı küçük metnin gerçekten büyük PDF çıktısı bu onayı zorunlu kılar. Yeni çıktıya eski kopya anahtarının geçmemesi ayrıca doğrulanır.
- Android çoklu belge seçicisi aynı URI'yi tekilleştirir. UI örneği aynı adlı **ayrı URI'ler** ile çalışır. Gerçek klasör seçici, kontrollü sağlayıcıdan bağımsız mevcut testle ayrıca doğrulanır.
- Arapça yüzde 200 ekranında `01_tiny.bmp` yanlışlıkla `tiny.bmp_01` gibi görünüyordu. ZIP adı/yoluna açık LTR metin yönü verildi; genel arayüz RTL kaldı. Test, yalnız metin içeriğini değil gerçek `TextLayoutResult` paragraf yönünü de denetler. Açık telefon ve koyu tablet aynı zincirden geçer; güncel görüntüler `ui-phone.png`, `ui-font200-ar.png`, `ui-tablet-dark.png` içindedir.
- Debug/AndroidTest derlemesi ve **137 JVM testi** geçti; atlama/hata yok. Lint 0 hata, 67 mevcut uyarı ve 2 ipucu. 22 dil anahtar/biçim/üretilmiş kaynak denetimi ve **95 incelenmiş runtime artifact + mevcut native encoder** lisans/hash kapsamı çevrimdışı geçti. Yeni SDK/codec/font yoktur.
- Yerel Graphify AST **4012 düğüm / 10837 ilişki** olarak güncellendi. 24 dosyanın kısmi Kotlin ayrıştırma uyarısı vardır; bunlar Gradle derleme hatası değildir. Ağ/LLM/API anahtarı kullanılmadı.

Bu koşularda motor/süreç/sağlayıcı kısmı tamamlandıktan sonra yalnız düzeltilen UI profilleri tekrarlandı; 19 tarif gereksiz yere yeniden kodlanmadı. Dikey videonun kodlanmış kapsayıcı ölçüsü görüntü döndürme matrisiyle farklı olabilir: host denetimi ilk header ölçüsü yerine gerçekten döndürülerek çözülen kareyi kullanır. Çıktıyı veya kaliteyi bu farkı gizlemek için değiştirmez.

## Sınırlar

Bu küçük yapay matris bütün dosyaların, cihaz codec'lerinin veya sağlayıcıların desteğini kanıtlamaz. Büyük dosya/düşük disk, Android 24/ARM ve fiziksel cihaz RAM/pil/FPS ölçülmedi. Kullanıcı tercihiyle yalnız emülatör kullanılır; Office kurulumu gerekmez. Yayın/R8/native sembol/LAME paket kontrolleri sonraki açık release isteğindedir.

Toplu çıktı sınırı 512 MiB/dosya olabilirken mevcut ZIP okuyucu 128 MiB/dosya ile sınırlıdır; zincir bu kesişimde doğrulanır. Her paketi uygulamanın tekrar açabilmesi vaat edilmez. ZIP64/şifreli/split veya başka arşiv biçimi eklenmez. Dosyayı sessizce küçültmek/kaliteyi düşürmek çözüm değildir.

Manuel/yayın ayrımı ve testçi adımları [güncel kapalı test kontrol listesinde](../../testing/CURRENT_CLOSED_TEST_CHECKLIST.md). Önceki 0.1.7 listesi ve ilk gönderim planı tarihsel kayıttır; yeni paketin hazır veya dağıtılmış olduğu anlamına gelmez.

Sonraki ürün işi: toplu çıktı ile ZIP okuyucusunun dosya boyutu sınırlarını kaynak/bellek/iptal korumalarını sürdürerek uyumlu hale getirmek; büyük dosyada dışarı kaydedilen paketin tekrar açılma kapsamını açıklaştırmak. Yeni release yalnız kullanıcı isteğinde.
