# 15 Ekim 2026 — ZIP çoklu seçim ve klasöre çıkarma

## Kullanım ve kapsam

ZIP aç ekranında dosyalar tek tek veya **Tümünü seç** ile işaretlenir. **Klasöre çıkar**, Android klasör seçicisini açar. Seçilen klasörün içinde yeni `Birlevo-<UUID>` klasörü oluşturulur; yalnız seçili dosyalar, gerekli alt klasörleri ve özgün baytlarıyla çıkarılır. Mevcut tek dosyalı **Kaydet** yolu korunur. Tekrar çıkarma ayrı bir yeni klasör üretir.

ZIP32 stored/deflate ve önceki sınırlar değişmez: 1000 arşiv öğesi, 512 MiB kaynak, ayrıca 512 MiB toplam açılmış içerik, 128 MiB/dosya, 8 MiB merkez dizin. Boş dosya ve Unicode ad desteklenir. Dosya içermeyen boş dizinler seçim listesine eklenmez. Şifreli/split/ZIP64/7Z/RAR eklenmedi. Çıkarma yeniden kodlama veya sıkıştırma başarısı sayılmaz; medya kalite/çözünürlükleri ve dönüşüm kopyası onayları değişmedi.

Android 11+ depolama kökü ve Download kökü gibi yerlerin seçimini kısıtlar; bunların içinde izin verilen bir alt klasör seçilir. İşlem ekranı açık tutulmalıdır. Bu yol bir arka plan servisi veya kesintisiz devam taahhüdü eklemez. [Android SAF ve klasör erişimi](https://developer.android.com/training/data-storage/shared/documents-files#grant-access-directory).

## Yazma, iptal ve kurtarma sözleşmesi

- Kaynak, mevcut tek yazıcılı/atomik özel ZIP oturumudur; sürüm 1 metadata uyumludur. Kaynak URI veya sahipliği belirsiz belge yazma/silme hedefi olmaz.
- Seçim, arşiv revizyonuna bağlı en fazla 4 MiB özel atomik kayıtta tutulur. Android örnek durumuna yalnız küçük istek token'ı girer. Klasör seçici açıkken yeniden oluşturma/bekleyen sonuç desteklenir; eski token/revizyon yazmaya başlamadan reddedilir.
- Bütün seçili içerikler önce özel geçici dosyalara çıkarılıp gerçek boyut/CRC ile doğrulanır. Hedef klasör bundan sonra oluşturulur. Arşivdeki yol disk yolu olarak kullanılmaz; doğrulanmış tekil ad bölümleri SAF çağrılarına verilir.
- Çoklu çıkarma, başlıkları her dosyada yeniden taramaz: sabit sayıdaki indeks denetiminin ardından bir açık ZIP üzerinden seçim sıralı okunur. Kopyalama 64 KiB tamponludur. Seçili geçici içerik en fazla 512 MiB olabilir; kaynak ve geçici içerik birlikte disk alanı gerektirir. Kalite/boyut otomatik düşürülmez.
- Yeni hedefin kimliği, ebeveynin ve işlem boyunca görülmüş belgelerin kimlikleriyle karşılaştırılır. Ebeveyn/eski belge kimliği döndüren veya adı değiştiren sağlayıcıya yazılmaz. Sağlayıcının döndürdüğü kimliğin ebeveynin çocuklarında bulunduğu ve beklenen ad/tür denetlenir. Sağlayıcı MIME'ı uzantıya göre belirleyebilir; normal dosyanın MIME'ının octet-stream'e birebir eşit olması gerekmez.
- Belleği sınırlamak için tek ebeveyn sorgusunda en fazla 1001 çocuk kimliği tutulur; daha kalabalık seçili klasör limit hatası verir. Bu, önceki tek dosyalı kaydetmeyi kısıtlamaz; daha boş bir alt klasör seçilebilir.
- İptal/yazma hatasında yalnız bu işin yeni klasörünün silinmesi istenir. Seçilen ana klasör ve önceden var olan belgeler silinmez. Temizleme başarısızsa kalan klasör bilgisi görünür; yerel depolama yazılabildiğinde atomik kayda da alınır. Yerel kayıt ayrıca başarısız olsa da UI uyarısı önce teslim edilir. Yerel geçici dosyalar temizlenir.
- Çıktı klasörü, ilk yazmadan önce kesinti kaydına eklenir. Süreç öldürülürse yeniden açmada kısmi klasör uyarısı gösterilir; eski hedefe kendiliğinden yazılmaz veya eski hedef silinmez. Tamamlanan sonuç için dosya sayısı kaydedilir. Açık **Temizle** yalnız özel çalışma/istek/bildirim kaydını kaldırır, çıkarılmış dosyaları korur.
- Sağlayıcı çağrısı sırasında süreç ölmesi, sağlayıcının yeni kimliği döndürmeden dosya oluşturması, erişim iptali, bulut gecikmesi veya silme reddi bütün hedeflerde atomik kayıt/temizlik garantisine izin vermez. Başlık/ad/kimlik kontrolleri sağlayıcının belge sözleşmesine dayanır; bütünüyle kötü niyetli bir sağlayıcının doğruluğu kanıtlanmış sayılmaz. Klasörün kullanıcı tarafından değiştirilmesi de otomatik kurtarma nedeni değildir.

## Modülerlik ve doğrulama

`ZipArchive.extractMany` saf ZIP doğrulaması/akışı; `ArchiveFolders` SAF ad/kimlik/sahiplik ve geri alma; `ArchiveEngine` özel kaynak, seçim ve kesinti kayıtları; ViewModel seçim/ilerleme/iptal; ekran ise tembel ve yol kimlikli liste ile sistem seçicileridir. Ana sayfa yalnız mevcut hafif kaynak metadatasını okumaya devam eder. Uzun sınır/kurtarma açıklaması Yardım altında, işlem sırasında kısa açık ekran uyarısı ve İptal erişilebilirdir.

Doğrulama komutları:

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/archive-folders-qa.ps1 -SkipBuild
python tools/localization/generate.py --check
python tools/licensing/audit_runtime.py --check
graphify update . --no-cluster
```

Son kabul koşusu:

- **137 JVM testi**, hata/atlama yok. ZIP sınıfındaki 10 test, 1000 dosyalı çoklu çıkarma, sahte/yinelenen seçim, CRC ve iptal kapsamını içerir.
- **20 emülatör yürütmesi**: yeni motor 4; bekleyen seçim ve işlem ortası süreç öldürme/yeniden açma 4; arşiv UI 7; mevcut okuyucu/tek dosya/batch ZIP regresyonu 3; aynı çoklu seçim UI testinin Arapça yüzde 200 telefon ve koyu tablet profilleri 2. Bunlar 20 farklı test yöntemi iddiası değildir.
- Gerçek `DocumentsProvider` protokolündeki test sağlayıcısıyla ebeveyn/eski/başka alt klasöre bağlanmış belge kimliği, ad değiştirme, yazma ve silme reddi doğrulandı. Android'in gerçek sistem klasör seçicisinde `Download/Birlevo-folder-QA` altına kayıt ve alt yol/özgün içerik doğrulandı. Eski iki aynı adlı tek dosya kaydı da ayrı URI üretmeye devam etti.
- Kaydedilmiş üç dosya bağımsız host denetiminde tam bayt ve SHA-256 ile eşleşti: iç içe metin, Japonca adlı metin ve sıfır bayt dosya. Android testleri ayrıca seçim dışı dosyanın çıkarılmamasını, tekrar kaydın ayrı klasör oluşturmasını ve sentinel/kaynakların korunmasını doğruladı.
- Debug derleme, AndroidTest derleme ve lint geçti; lint **0 hata, 67 uyarı ve 2 ipucu**. Bütün uyarıların giderildiği iddia edilmez. Yeni altı metin 22 dilde anahtar/biçim/üretim denetiminden geçti; insan dil incelemesi tüm dillerde yapılmış sayılmaz.
- Gerçek release runtime envanterinde **95 incelenmiş artifact + mevcut native encoder** kapsamı çevrimdışı doğrulandı. Yeni paket/SDK/font/codec yoktur.
- Yerel kayıt hatasının kalan klasör uyarısını engellememesi için son callback sırası düzeltildikten sonra debug/lint/JVM yeniden geçti; ilgili **8 motor/süreç yürütmesi** ayrıca başarılı oldu. Bunlar yukarıdaki testlerin tekrarıdır, 8 yeni bağımsız senaryo değildir.
- Yerel Graphify AST güncellendi: **3946 düğüm / 10690 ilişki**. 24 Kotlin dosyası için kısmi AST ayrıştırma uyarısı vardır; bunlar Gradle derleme hatası değildir. Ağ/LLM/anahtar kullanılmadı.

Kanıt klasörü Git dışındaki `output/development/archive-folders/` dizinidir; tam kabul günlüğü `qa-final-verified.log`, son ilgili koşu `qa-engine-final.log`, güncel debug/lint/JVM günlüğü `build.log`, host sonucu `independent-check.json` ve üç ekran görüntüsü burada bulunur. İlk test koşularındaki JUnit dönüş türü, zorla durdurmada geçici iznin kalkması ve asenkron/son konumu hatırlayan sistem seçici için test düzeltmeleri kabul öncesi yapıldı; başarısız deneme günlükleri kabul kanıtı sayılmaz.

Fiziksel cihaz, Android 24/ARM, bütün bulut/USB/SD sağlayıcıları ve R8/yayın paketi bu adımda doğrulanmış sayılmaz. Emülatörden fiziksel cihaz RAM/pil/FPS garantisi çıkarılmaz. Yeni release veya sürüm artışı yapılmadı; sürüm 0.1.11/code 12 aynı kaldı.

## Sonraki adım

Toplu dönüştürme → ZIP oluşturma → yeniden açma → klasöre çıkarma zincirini ortak emülatör regresyonuna ve kapalı test kontrol listesine bağlamak. Yeni sürüm yalnız kullanıcı istediğinde hazırlanır.
