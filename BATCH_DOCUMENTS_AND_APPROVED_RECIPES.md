# 15 Ekim 2026 — toplu belge dönüştürme

## Kullanım ve gerçek kapsam

**Ana sayfa → Toplu işlemler → Toplu belgeler / ZIP** yeni bağımsız belge kuyruğunu açar. En fazla 20 kaynak, kaynak başına mevcut 4 MiB sınırı korunur. İşlem her dosyayı ayrı dönüştürür; ZIP yalnız seçilmiş başarılı sonuçları paketler, ek sıkıştırma başarısı vaat etmez.

| Girdi | Uygun hedefler |
|---|---|
| TXT / düz Markdown | TXT, PDF, DOCX |
| CSV / TSV | TXT, CSV, TSV, PDF, XLSX |
| DOCX desteklenen düz içerik | TXT, PDF, DOCX |
| XLSX seçilen çalışma sayfası | TXT, CSV, TSV, PDF, XLSX |

Bir tarif bütün kuyruk için ortak hedef, **girdi** kodlaması ve CSV ayırıcısı taşır. Otomatik kodlama mevcut UTF-8/BOM kurallarını kullanır; Windows-1254/1252 açık seçim ister, sessiz tahmin/fallback yoktur. Çıktı metni UTF-8'dir. Birbirinden farklı özel kodlamalar için ayrı grup veya tek belge aracı kullanılabilir. PDF **girdisi** mevcut PDF araçlarında kalır. Eski DOC/XLS, RTF/PPT ve tam Office görünüm uyumluluğu iddia edilmez.

Uyumsuz hedef bir dosyada `TARGET` hatası gösterir; uygun diğer dosyalar işlenebilir. Kullanıcı başarısız/seçilmeyen satırları sayılarıyla inceleyip yalnız başarılı sonuçları ZIP'e alır. Yeni dosya üretmek belge sıkıştırma başarısı sayılmaz.

## İki ayrı onay ve kaynak koruma

Office dosyalarında açılır **Dönüştürmeyi incele** alanı kayıp bildirimini ve dosyaya ait onayı gösterir. Birden çok Excel sayfası varsa başlangıçta hiçbiri seçilmez; kullanıcı sayfayı açıkça seçer. Saklanmış ham sonuçlar veya formül metni ayrı tercihlerdir. Eksik formül sonucu sayısı gösterilir; değer modunda bu hücreler boş kalır, sıfır uydurulmaz. Tarihler ham seri/değer olarak kalır; 1900/1904 sistemi gösterilir. Formüller hesaplanmaz; başka uygulamalar CSV formül metnini yorumlayabilir.

Bu onay kaynak kimliği/özel dosya/boyut, belge türü, sayfa/metaveri, mod ve ortak hedef/kodlama/ayırıcıdan türetilen anahtara bağlıdır. Sayfa veya tarif değişince eski onay geçmez; başka satırın değişmesi aynı dosyanın değişmemiş onayını bozmaz. Anahtar kaynak dosyanın kriptografik içerik hash'i olarak sunulmaz; kaynak uygulamaya ait değişmez özel kopyadır. Dönüştürmede yeniden okunan Office metaverisi karşılaştırılır. Onay kayda/iş isteğine yazılmaz. Yeniden süreç açılışında kayıtlı sonuçları paketlemek için de gerekli Office onayları tekrar istenir.

**Büyük/eşit çıktı onayı ayrıdır:** gerçek sonuç baytı kaynakla karşılaştırılır. Eşit/büyük kopya kendi sonuç adı/boyutuna bağlı açık onay olmadan paketlenmez veya kaydedilmez. Yeniden üretim, yeni dosya anahtarıyla bu onayı geçersiz kılar. Kalite otomatik azaltılmaz, dosya kısaltılmaz. Toplam çıktı 512 MiB'ı aşarsa yeni sonuç bırakılmaz; belgeye özgü `OUTPUT_LIMIT` hatası verilir, video sınırı mesajı kullanılmaz.

Kaynaklar yerel özel alana kopyalanır; seçim grubu atomiktir. Bozuk/uyumsuz bir içe aktarma önceki kuyruğu bozmaz. Ayar değiştirme sonuçları geçersiz kılar; sayfa/mod değiştirme yalnız ilgili sonucu kaldırır. Kaynak ve diğer tamamlanan sonuçlar korunur. İşlem iptalinde tamamlanan satırlar kalır ve tekrar kodlanmaz. Sonuç ve ZIP geçicileri temizlenir. Boş Word gövdesinden **0 bayt TXT** geçerli olabilir; yalnız belge kuyruğunda bu sonuç açıkça desteklenir, boş medya dosyaları geçerli hale getirilmez.

Ortak toplu kayıt akışında eski/bilinmeyen seçici yanıtı artık hedefi açmaz **ve silmez**. Eski kuyruğa ait kaynak URI'si yeni/boş kuyruğun metadata'sında bulunmayabilir; yalnız yeni kayda bakarak dosya silme yetkisi çıkarılmaz. Bu nedenle eski bir seçici yanıtının yeni oluşturduğu boş belge kalabilir. Geçerli kayıt sırasında hata/iptalde sağlayıcıdan yeni hedefi temizlemesi istenir; bütün SAF sağlayıcılarında atomiklik/silme garantisi yoktur. Orijinaller hiçbir araç temizlemesinin hedefi değildir.

## Yapı, yaşam döngüsü ve sınırlar

Yeni kopya motoru yerine ortak `BatchEngine` / `BatchViewModel` kullanılır. `BatchDocuments` yalnız sınırlandırılmış metadata ve onay anahtarını, `BatchConverters` mevcut `TextDocumentEngine` uyarlamasını, `BatchDocumentControls` arayüzü taşır. Belge motorunun varsayılan klasörü değişmez; toplu akış kendi `assets` klasörünü açıkça verir. `batch-documents` ayrı depodur. Metadata sürümü **3**; görsel sürüm **1**, video/ses sürüm **2** korunur. Onay, hücreler, metin ve bit eşlem kayda eklenmez. FileProvider yetkisi/kökleri değişmedi.

20 dosya ve 4 MiB kaynak sınırı birlikte en çok 80 MiB kaynak demektir; ortak 512 MiB toplam kaynak/çıktı koruması da sürer. Mevcut metin/tablo ve Office motoru sınırları aynıdır: 2 milyon karakter; metin tablolarında 5000 satır/100 sütun/50 bin hücre; hücrede 32768 karakter; PDF'de 300 sayfa. Office'in daha dar ZIP/XML/paragraf/hücre/sayfa sınırları ayrıca uygulanır; hedefe sığmayan içerik kırpılmaz. Çıktının kaynak 4 MiB sınırıyla yanlışlıkla sınırlandırılması önlendi; gerçek çıktı bütçesi ayrıca denetlenir.

Dosyalar sırayla işlenir. Kaynak içeriği/hücreler bir dosya için geçici olarak yüklenir; bütün kuyruğun içerikleri veya önizlemeleri UI'da tutulmaz. Liste sabit dosya kimlikli tembel öğeler kullanır. Kalıcı yazıcı tek kilit/atomik dosyayla çalışır; işlem sistemi yalnız küçük tamamlanma makbuzunu sahiplenir. Devam listesi yalnız metadata okur. Kaynak/sonuç ve ZIP diskte birlikte bulunabilir; 512 MiB çıktı bütçesi toplam disk alanı garantisi değildir.

`ProcessingJobs` ortak ağır işlem kilidi ve uygulama ömründeki kapsamını korur. Ekrandan çıkmak işlemi iptal etmez; açık İptal veya bildirim eylemi kullanılır. Her işin motoruna girmeden önce, Main üzerinde hizmet türü uygun hale getirilir: Android 35+ belge işi **dataSync**, mevcut medya işleri **mediaProcessing**; eski platform davranışı korunur. Manifestteki mevcut izinler/türler değişmedi. Yerel dosya işleme, Android'in [dataSync kapsamındadır](https://developer.android.com/develop/background-work/services/fgs/service-types#data-sync). Timeout kaynakları/tamamlanan satırları tutar; süreç ölümünden sonra kullanıcı devam eder, otomatik yeniden başlatma yoktur.

## Doğrulama ve ticari dağıtım

- **136 JVM testi**, sıfır hata/atlama; yeni belge politikasında 6 test.
- İlk tam emülatör paketi **25 başarılı yürütme**: 4 belge motoru, 4 belge UI, 1 gerçek hizmet türü/geçiş, 2 gerçek süreç hazırlama/geri yükleme, 12 mevcut arka plan/toplu/Office regresyonu ve 2 büyük yazı/tablet senaryosu.
- Kaynak koruması ve onay metni değişikliklerinden sonra son APK'da **7 belge UI/görünüm yürütmesi**: yeni eski-seçici testiyle 5 normal UI, yüzde 200 Arapça/RTL ve koyu tablet. Bunlar tekrar içerir; 32 benzersiz test iddiası değildir. Ortak toplu arayüzler de geçerli kayıt yollarında ayrıca kontrol edildi.
- Altı hedefte **16 dosya**, formül metni/ham tarih/sıfır başlı metin ve boş TXT ZIP'i bağımsız Python ile denetlendi. ZIP CRC/hash, CSV/TSV hücreleri, DOCX metin/tab/kesme öğeleri, XLSX metin hücreleri/formül düğümü yokluğu ve PDF yapı/sayfa/Latin içerik örnekleri kontrol edildi. Android PDFRenderer ilk/son sayfayı ayrıca açıp çizdi. PDF metin çıkarımının tam RTL okuma sırası veya Office görünüm eşitliği iddia edilmez.
- **22 dil** ve runtime **95 incelenmiş artifact + mevcut native encoder** denetimi geçti. Yeni SDK/font/codec veya Office çalıştırma bağımlılığı yoktur. Lint: **0 hata, 67 uyarı, 2 ipucu**; debug derleme başarılıdır. Yerel AST grafiği güncellendi (3852 düğüm/10424 ilişki). 24 kısmi Kotlin ayrıştırma uyarısı kaynak/derleyiciyle kontrol edildi; derleme hatası veya dış servis çağrısı değildir.

Kanıtlar Git dışındaki `output/development/batch-documents/` altındadır: `build.log`, `build-guard.log`, `device.log`, süreç/regresyon ve profil logları, `qa-final.log`, `qa-ui-final.log`, `manifest.json`, `independent-check.json`, sekiz ZIP ve ekran görüntüleri. İlk koşucudaki JUnit `void` dönüş tipi, Android dump alan adı ve casus sağlayıcı dosya adı hataları ürün başarısı sayılmadı. PDF çıkarıcısının eklediği boşluk ve OOXML tab/kesme öğeleri bağımsız okuyucuda doğru ele alındı; ürün içeriği kesilerek assertion geçilmedi. Son testler kaynak silme API'sinin pozitif kontrolünü ve sonrasında **hiç silme çağrısı yapılmamasını** içerir.

Tekrar çalıştırma:

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory
python tools/localization/generate.py --check
python tools/licensing/audit_runtime.py --check
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/batch-documents-qa.ps1 -SkipBuild
```

Testler yalnız `ofice_qa` API 36 emülatöründe yapıldı. API 24/ARM, fiziksel düşük donanım RAM/pil/FPS, bütün sağlayıcı davranışları ve R8/release ölçülmüş sayılmaz. Sürüm **0.1.11 / 12** değişmedi; imzalı APK/AAB hazırlanmadı. Sonraki yayın isteğinde R8/release doğrulamasına ek olarak Play ön plan hizmeti beyanı/açıklaması ve demo, yerel belge işlemlerini de kapsayacak şekilde gözden geçirilmelidir.

## Sonraki ürün işi

ZIP içinde çoklu seçim ve kullanıcı tarafından seçilen klasöre güvenli çıkarma. Yeni hedef oluşturma, ad çakışması ve sağlayıcı hatasında yarım hedefleri ele alma sözleşmesi açıkça tanımlanmalı; mevcut dosya başına ZIP okuyucusu ve kaynak koruması korunmalıdır.
