# 15 Ekim 2026 — ZIP açma ve dosya çıkarma

Güncel ek: [çoklu seçim / klasöre çıkarma](ZIP_MULTI_SELECTION_AND_FOLDER_EXTRACTION.md) artık geliştirme kodunda vardır. Aşağıdaki tek dosyalı paket kaydı tarihseldir; o yol da korunur.

## Kullanım

**Dosya aç → ZIP aç** veya **Belgeler → ZIP aç** üzerinden arşiv seçilir. Tam içerik doğrulanınca dosyalar yolları ve gerçek boyutlarıyla listelenir. Her Kaydet düğmesi Android'in yeni belge oluşturma penceresini açar. Baytlar değiştirilmez; yeniden medya kodlaması yapılmaz. Sınırlar açılır Yardım alanındadır; işlem sırasında ekranı açık tutma mesajı ve İptal görünür kalır.

Bu paket dosyaları **tek tek** çıkarır. Klasöre toplu çıkarma, arşive ekleme, şifre çözme, ZIP64/7Z/RAR desteği iddia etmez. Boş dosyalar ve Unicode adlar korunur; boş dizinlerden oluşan arşivde boş durum gösterilir. DOCX/XLSX kendi belge yolundadır; çelişen MIME/uzantı ZIP'e yönlendirilmez. Mevcut toplu görsel/video/ses paketleme ve büyük çıktı onayları değişmez. Arşiv açmak sıkıştırma başarısı sayılmaz.

## Güvenlik ve kaynak koruma

- ZIP32 stored/deflate. Şifreli, bölünmüş, desteklenmeyen sürüm/yöntem ve Unix bağlantı/özel dosya kayıtları reddedilir.
- En fazla 1000 öğe; 512 MiB arşiv ve ayrıca 512 MiB toplam açılmış içerik; dosya başına 128 MiB. Platform ZIP okuyucusu açılmadan önce merkez dizin 8 MiB ile sınırlandırılır. Yol en fazla 1024 karakter/32 bölüm, bölüm 255 karakterdir.
- EOCD/merkez dizin sınırları, yerel ad/bayrak/yöntem eşleşmesi ve sıkıştırılmış veri aralıkları denetlenir. Açılan gerçek bayt sayısı ve CRC her öğede doğrulanmadan liste yayınlanmaz. İptal denetimi başlıklarda ve her akış parçasında vardır.
- Mutlak/üst dizin yolları, ters eğik çizgi, sürücü/akış adları, kontrol/yönlendirme karakterleri ve belirsiz bölüm adları reddedilir. NFC + harf duyarsız ad çakışması ve dosya/dizin üst yol çatışması kontrol edilir.
- **Arşiv yolu hiçbir zaman disk hedefi değildir.** Kaynak ve çıkarma dosyası UUID tabanlı özel dosyalardır. Hedefi kullanıcı Android penceresinde seçer; dosya adı yalnız başlangıç önerisidir.
- Yeni kaynak önce özel alana kopyalanıp tamamen doğrulanır. Atomik metadata kaydı tamamlanmadan önceki çalışma bırakılmaz. Hata/iptal eski arşivi korur. Kaydetmeden önce seçilen dosya özel geçici dosyaya çıkarılıp tekrar boyut/CRC doğrulanır; sonra hedefe akışla yazılır.
- Kaynak URI'sine veya aynı sağlayıcının aynı belge kimliğine yazılmaz. Eski revizyonlu kayıt sonucu hedefi açmaz veya silmez. Boş alan/yazma hatası bozuk arşivden ayrılır.
- Başarısız yeni SAF belgesinin silinmesi istenir. Sağlayıcı silmeyi desteklemiyorsa veya süreç aniden ölürse boş/kısmi hedef kalabilir; bütün sağlayıcılarda atomik hedef kaydı garantisi yoktur. Özgün kaynak korunur. Belirsiz/eski kayıt çağrısında hedefi silmemek kaynak güvenliğine öncelik verir.

## Modülerlik ve yaşam döngüsü

`ZipArchive` saf başlık/yol/akış politikası; `ArchiveEngine` özel dosya ve tek yazıcı; `ArchiveViewModel` ekran işi/iptal; `ArchiveScreen` sunum katmanıdır. Mevcut `AtomicMediaSession` kullanılır. Kalıcı metadata yalnız kaynak adı/kimliği, özel dosya, boyut ve revizyon taşır. Yeniden girişte içerik doğrulanır. **Devam et** yalnız hafif metadata okur; ana sayfa ZIP veya codec açmaz. Temizle onayı yalnız özel kopyayı kaldırır.

Liste yol anahtarlı tembel öğeler kullanır. Tam dosyalar RAM'e alınmaz; 64 KiB akış tamponuna ek olarak indeks ve adlar sınırlıdır. Kaydetmede tüm içerik tekrar şişirilmez; doğrulanmış özel kaynağın indeksi ve seçilen dosyanın gerçek içeriği denetlenir. Bitmap/oynatıcı yoktur. Değiştirmede eski/yeni kaynaklar doğrulama bitene kadar birlikte bulunabilir; kayıt için seçilen dosyanın geçici kopyası ve SAF hedefi de disk alanı ister.

İşlem **ekran açıkken** sürer; Geri iptal eder. Döndürmede ViewModel korunur. Soğuk geri yüklemeyi bekleyen kayıt çağrısı sıraya alınır; iptal ikisini de durdurur. Süreç ölürse tamamlanmış özel arşiv yeniden açılır; yarım çıkarma otomatik sürmez. Yeni ön plan hizmeti/arka plan vaadi yoktur. Mevcut `ProcessingJobs` görevleri korunur; ZIP I/O bu encoder kuyruğuna girmez. Eşzamanlı gerçek düşük donanım davranışı ayrıca ölçülmelidir.

## Ticari dağıtım ve doğrulama

Yeni SDK/font/native codec/arşiv paketi eklenmedi. Android/JDK ZIP API'leri kullanılır. Runtime kapsamı 95 incelenmiş paket ve mevcut native encoder'dır. FileProvider yetki/kökleri, kalite/çözünürlükler, sürüm numarası ve eski toplu kayıtlar değişmedi.

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory
python tools/localization/generate.py --check
python tools/licensing/audit_runtime.py --check
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/archive-qa.ps1 -SkipBuild
```

Testler yalnız `ofice_qa` API 36 emülatöründedir. JVM testleri bozuk başlık/CRC, şifreleme/ZIP64/bağlantı, tehlikeli veya çakışan ad, gerçek açılma/indeks sınırı, iptal ve çıktı yazım hatasını kapsar. Android testleri özel kaynak/atomik değişim/etkin kopyalama iptali, eski toplu ZIP uyumu, dosya yönlendirme/döndürme, gerçek sistem penceresinde aynı adlı iki ayrı kopya, soğuk ViewModel'e dönen kayıt ve gerçek süreç kapatma/geri yüklemeyi kapsar. Python ZIP CRC'sini ve üç çıkarılan dosyanın bayt/hash eşitliğini bağımsız denetler. 22 dil, yüzde 200 Arapça/RTL ve koyu tablet kontrolleri vardır.

Kabul edilen sonuçlar:

- **130 JVM testi**, sıfır hata/atlama; yeni ZIP politikasında 9 test.
- Ana debug/emülatör paketi **17 yürütme**: 7 ZIP motor/arayüz, 2 gerçek süreç hazırlama/geri yükleme, 6 mevcut toplu görsel/video/ses arayüz regresyonu, 1 Arapça/yüzde 200 yazı ve 1 koyu tablet.
- İndeks/yazım hatası ve yardım düzeni değişikliklerinden sonra yalnız ZIP odaklı **9 yürütme** geçti. Son tembel yükleme/taramasız temizleme değişikliğinden sonra güncel APK'da **10 yürütme** geçti: 3 motor + 5 arayüz + 2 görünüm senaryosu. Bunlar tekrar içeren yürütme sayılarıdır; 36 ayrı test iddiası değildir.
- Gerçek Android kayıt penceresi aynı adla iki **farklı URI** oluşturdu; ikisinin içeriği de aynı kaynakla karşılaştırıldı. Sahte kayıt sağlayıcısı testlerinden ayrıdır.
- Bağımsız Python: ZIP CRC ve normal/Unicode/boş **3 çıkarılan dosyanın birebir bayt/hash eşitliği** geçti.
- **22 dil**, çevrimdışı **95 runtime artifact + native encoder** denetimi geçti. Lint: **0 hata, 68 uyarı, 2 ipucu**; mevcut sayılar korunur.
- Yerel AST grafiği güncellendi. Kotlin ayrıştırıcısının kısmi grafik uyarıları derleyici/test başarısızlığı sayılmaz; kaynak ve debug derleyicisiyle doğrulanır. API/anahtar veya semantik dış servis kullanılmadı.

Kanıtlar Git dışındaki `output/development/archive/` altındadır: `build.log`, `qa-final.log`, `qa-focused.log`, `qa-lifecycle.log`, `device.log`, `device-before-help.log`, `device-before-lazy-load.log`, `process-prepare.log`, `process-restored.log`, `batch-regression.log`, `phone-font200-ar.log`, `tablet-dark.log`, `independent-check.json`, üç ekran görüntüsü ve kaynak/çıkarılan örnekler. Sonraki çalıştırmalar aynı log adlarını yeniler. İlk tablet çalıştırmasındaki `performMeasureAndLayout called during measure layout` test altyapısı hatası `tablet-initial-failure.log` içinde tutuldu ve başarılı sayılmadı; platform pencere geçişinin tamamlanması beklenerek ve tablet çalıştırmasına soğuk süreçle girilerek tekrarlandı. Ürün hatasını görmezden gelen exception yakalama veya assertion kaldırma yoktur.

API 24/ARM, bütün sağlayıcılarda hedef atomikliği, fiziksel düşük donanım RAM/pil/FPS ve R8/release bu debug paketinde ölçülmüş sayılmaz. İmzalı sürüm üretilmez.

## Sonraki iş

Belge toplu tariflerinin her hedef için kayıp/uyumluluk bildirimi ve gerçek çıktı boyutuna dayanan kayıt kurallarıyla değerlendirilmesi. Klasöre çoklu ZIP çıkarma ise sağlayıcı ad çakışması/yarım hedefleri geri alma için ayrı işlem sözleşmesi ister; tek dosya yolunun tamamlanması o özelliği otomatik desteklemez.
