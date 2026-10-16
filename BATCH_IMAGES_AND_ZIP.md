# Toplu görsel dönüştürme ve ZIP — 15 Ekim 2026

Toplu işlem yolunun ilk paketi görseller içindir. Ana sayfadaki **Toplu görsel / ZIP** düğmesi ve kayıt varsa **Devam et** satırı aynı kalıcı kuyruğu açar. Yeni bir uygulama sürümü veya imzalı yayın paketi üretilmedi; sürüm hâlâ 0.1.11 (12).

## Kullanım ve kapsam

1. Bir veya daha fazla görsel seçilir; sonraki seçimler kuyruğa eklenir. En fazla 20 dosya, toplam 512 MiB kaynak kabul edilir. Her dosyanın mevcut 128 MiB / 100 MP ve gerçek dosya içeriği kontrolleri korunur. Geçersiz bir yeni seçim grubu bütünüyle geri alınır; önceki kuyruk değişmez.
2. Çıktı panelinde JPEG, PNG, WebP veya BMP; 1280 / 1920 / 2560 piksel uzun kenar ve kalite kullanan biçimlerde 40–95 kalite seçilir. Görseller büyütülmez. PNG/BMP için kalite sürgüsü gösterilmez. Saydamlık ve metadata kaybı hedef biçimin yanında açıklanır. Tek dosya aracının seçenekleri azaltılmaz.
3. **Sonucu oluştur** dosyaları sırayla işler. Her satır kaynak boyutunu, boyutlarını, gerçek çıktı boyutunu veya hatasını gösterir. Yeniden deneme tamamlanan sonuçları tekrar kodlamaz.
4. Eşit veya daha büyük her sonuç için ayrı **Dönüştürülmüş kopya** onayı gerekir. Onay dosyanın kimliği, benzersiz sonuç adı ve gerçek bayt sayısıyla eşleşir. Sonuç değişince eski onay kullanılamaz. Onaylar diske veya Android durum paketine yazılmaz; süreç kapanınca yeniden istenir.
5. Kullanıcı sonuçları ZIP’e dahil etme seçimini değiştirebilir, satırı kaldırabilir veya bütün çalışmayı açık onayla temizleyebilir. Bekleyen dosya varken ZIP üretilmez. Hatalı ve işaretsiz satırların dışarıda kaldığı, eklenecek/toplam dosya sayısıyla son inceleme diyaloğunda belirtilir. ZIP oluşturma ayrıca boyut kazancı vaat etmez.
6. ZIP hazır olunca gerçek paket boyutu gösterilir ve sistemin belge oluşturma seçicisiyle kaydedilir. Eski bir seçicinin dönüşü güncel ZIP kimliğiyle eşleşmezse kullanılamaz. Kaynak URI üzerine yazma reddedilir; başarısız/iptal edilmiş yeni kayıt, sağlayıcı destekliyorsa silinir.

ZIP **oluşturma** bu pakettedir. ZIP açma/çıkarma, şifreli arşivler, 7Z/RAR, video/ses/belge toplu tarifleri ve karışık dosya türleri henüz bu ekranın kapsamına girmez. Mevcut tek dosya araçları bütün yetenekleriyle erişilebilir kalır. Sonraki ürün işi aynı kuyruk ilkelerini video ve sese taşımaktır; arşiv açma ayrı yol/şişme/bozuk veri sınırları gerektirir.

## Durum, güvenlik ve kaynak kullanımı

- `BatchImagePolicy` kayıt şemasını, sınırları, sonuç onayını ve düz ZIP adlarını; `BatchImageEngine` sahip olunan dosyaları, atomik kaydı ve akışlı paketlemeyi; `BatchImageViewModel` ekran/arka plan işlerini yönetir. `BatchImageScreen` yalnız metadata gösterir; toplu listeye bitmap dağıtılmaz.
- Dönüştürme ve paketleme, mevcut `ProcessingJobs` / ön plan hizmetinin tek ağır işlem kilidini kullanır. Aynı anda iki encoder çalıştırılmaz. Ekrandan geri dönmek işi durdurmaz; kullanıcı arayüzden veya bildirimden iptal edebilir.
- Yalnız metadata `AtomicMediaSession` üzerinden, tek yazıcı kilidiyle kaydedilir. Her sonuç bir sonraki dosyadan önce kalıcılaşır. Süreç ölürse otomatik arka plan yeniden başlatması yapılmaz; korunmuş kaynak ve ayarlarla kalan dosyalar kullanıcı eylemiyle denenir.
- Kaynak kopyaları ve sonuçlar yalnız `noBackupFilesDir/batch-images/assets` içinde tutulur. Tek görsel aracının eski önbellek temizliği bu dosyalara dokunmaz. Proje varlıkları/diğer araçlar bu klasöre referans oluşturmaz; bu nedenle kesintide kayıt dışı kalan yalnız bu klasörün dosyaları temizlenebilir. Var olan FileProvider yetkisi ve kök adları değişmez.
- Kaynak ve çıktı toplamlarına ayrı ayrı 512 MiB sınırı uygulanır. ZIP payload’ı da bu çıktı sınırına tabidir; arşiv başlıkları ek alan kullanır. Diskte kaynak, sonuç ve paket geçici olarak birlikte bulunur; yeterli boş alan gereklidir. Disk dolması kalite düşürerek aşılmaz.
- Arşiv UTF-8, düz ve sıra numarasıyla başlayan adlar kullanır. Kullanıcı dosya adları dizin yolu olarak alınmaz; büyük/küçük harf duyarsız okuyucularda da çakışma önlenir. Dosyalar 64 KiB tamponla kopyalanır; mevcut kodlanmış medyaya tekrar sıkıştırma uygulanmaz. `.part` paket tamamlanıp senkronize edilmeden nihai ada geçirilmez.
- Yeni SDK, codec veya font yoktur. Android/JDK `java.util.zip` kullanılır. Mevcut gerçek runtime envanteri 95 incelenmiş paket ve ayrı native encoder kapsamıyla yeniden denetlenir. Jev çağrısı veya ağ servisi eklenmedi.

## Doğrulama

Tekrarlanabilir geliştirme kontrolü:

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
python tools/localization/generate.py --check
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/batch-images-qa.ps1 -SkipBuild
```

QA koşucusu yalnız `ofice_qa` emülatörünü kabul eder. Toplu dönüştürme, dosya başına onay, hata/iptal, eski sonuç geçersizleştirme, gerçek ZIP kaydı, Activity yeniden oluşturma, ayrı süreçte geri yükleme, büyük yazı/RTL ve koyu tablet yollarını kapsar. Eski tek görsel dönüştürme testleri de çalıştırılır. Host tarafında Python `zipfile` ve Pillow, dört arşivdeki 12 dosyanın CRC/hash, tam çözüm, boyut ve saydamlığını bağımsız doğrular. Çıktılar `output/development/batch-images/` altında Git dışında tutulur.

Son debug kaydı: **115 JVM testi ve 20 emülatör test yürütmesi geçti**. Ana grup 16; ayrı süreç hazırlama/geri yükleme 2; gerçek Arapça/RTL ve yüzde 200 yazı 1; koyu tablet 1 yürütmedir. ZIP kaydı `application/zip` seçicisiyle ve tekrar açılan arşivin içeriğiyle doğrulandı. Paket yazımı sırasında gerçek bayt ilerlemesinde iptal edilen `.part` dosyasının temizlenmesi ve aynı sonuçların yeniden kodlanmadan paketlenmesi de sınandı. Lint: 0 hata, 68 uyarı ve 2 ipucu; yeni KTX stil önerileri işlev hatası sayılmaz. Yeni kaynaklarla lisans/hash ve 22 dil kontrolleri geçti.

Son görsel incelemede RTL paragrafında boyut çiftinin ters okunabildiği görüldü. Genişlik × yükseklik ayrı LTR teknik satırına taşındı. Yalnız bu sunum değişikliği sonrasında debug/lint ve etkilenen 4 arayüz yürütmesi (normal telefonda 2, Arapça/yüzde 200 yazı 1, koyu tablet 1) tekrar geçti. Böylece kabul edilen toplam 24 emülatör yürütmesi vardır; farklı test sayısı iddiası değildir. `-UiOnly` bu dar tekrar yolunu sağlar. Son ekran görüntüleri de gözle incelendi.

İlk kontrollerde eski görsel biçim testinin kapalı alt panelde kontrol aradığı görüldü; test gerçek panel gezinmesine uyarlandı. Hızlı ZIP testinde sonuç bilgisi ile “hazır” durumunun ayrı yayımlanabildiği yarış düzeltildi; kabul edilen son turda ikisi tek durum güncellemesinde yayımlanır. Başarısız ara turlar kabul sonucu olarak sayılmaz. Yerel Graphify grafiği yenilendi; Kotlin AST ayrıştırıcısının kısmi çıkarım uyarıları derleyici veya test kanıtı yerine geçmez.

22 dilde yeni anahtarlar tamamdır; şema/placeholder kontrolü dil uzmanı değerlendirmesinin yerine geçmez. Emülatör kanıtı fiziksel düşük donanımda pil/FPS/bellek garantisi değildir. Android 24/ARM, bütün sağlayıcıların kayıt/iptal davranışı, bu yeni ekranda gerçek TalkBack sesli dolaşımı ve yeni kodun R8/yayın doğrulaması henüz ölçülmedi. Yayın doğrulaması yalnız sonraki kullanıcı sürüm isteğinde yapılır.
