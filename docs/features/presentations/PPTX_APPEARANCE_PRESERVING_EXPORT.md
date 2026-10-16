# Görünümü koruyan PPTX dışa aktarımı — 9 Ekim 2026

Birlevo'da oluşturulan sunumlar, düzenleyicinin **Diğer işlemler → PPTX dışa aktar** yolundan yeni `.pptx` dosyası olarak kaydedilebilir. Bu ekleme geliştirme kodundadır; sürüm numarası ve dondurulmuş **0.1.10 (11)** yayın paketleri değişmedi.

## Kullanıcıya sunulan kapsam

- Her slayt, mevcut Android sunum çizim motoruyla tam slayt PNG olarak oluşturulur. Tema, mevcut üç düzen, görselin oranı ve yerel çok alfabeli yazı şekillendirmesi kullanılır. PPTX içinde her slayt tek görseldir: metin ve nesneler ayrı düzenlenemez, metin olarak aranamaz. Kayıp açıklaması ana çalışma ekranında görünürdür.
- Nihai kenar seçimi **1024 / 2048 / 4096**, 16:9 oranıdır. Varsayılan 2048; otomatik çözünürlük veya kalite düşürme yoktur. Önizleme 512×288'dir, nihai çözünürlükten ayrıdır.
- Konuşmacı notları varsayılan olarak **kapalıdır**. Kullanıcı eklemeyi seçerse ayrı OOXML not parçalarında bulunur ve PPTX alıcısı okuyabilir. PNG slaytlara çizilmez. PDF/video/görsel çıktıların mevcut not dışlama davranışı değişmez. Açıklamalar 22 dilde güncellendi.
- İşlem, mevcut uygulama ömründeki tek ağır iş kuyruğu ve ön plan hizmetini kullanır. İlerleme, iptal, arka planda devam etme, tamamlanan sonucun yeniden bağlanması ve kesilmiş işin açık yeniden denemesi mevcut kayıt modeline bağlıdır.
- Önizleme, oluşturulan PPTX'in içindeki seçili PNG'den okunur. Sonuç oluşmadan görüntü **kaynak önizlemesi**, oluştuktan sonra **oluşturulan PPTX önizlemesi** olarak etiketlenir. Gerçek dosya boyutu, çözünürlük ve slayt sayısı gösterilir. Kaydetme ve Diğer işlemler altında açma/paylaşma aynı dosyayı kullanır.
- Ayar veya görünür sunum içeriği değişince eski sonuç geçersizdir. Notlar dışlanmışsa yalnız not değişikliği çıktıyı geçersizleştirmez; notlar dahilse değişiklik geçersizleştirir. Otomatik kayıt zaman damgası çıktı kimliğini değiştirmez. Bekleyen eski kayıt isteği güncel sonuç gibi kabul edilmez.

Bu, **yeni sunum oluşturma** işlemidir; sıkıştırma sonucu değildir. Kaynak dosya boyutuyla kazanç iddiası yapılmaz. Mevcut görsel/video/ses sıkıştırma ve büyük kopya onayları değişmedi. Genel PPTX içe aktarma, düzenlenebilir nesne aktarımı, animasyon, tablo veya grafik aktarımı bu seçenek değildir.

## Teknik yapı ve sınırlar

`PptxPackage` uygulamaya ait OOXML ZIP/XML yazıcısıdır. Testteki boş PPTX şablonu üretim varlığına kopyalanmadı. Master, boş düzen, tema, ilişkiler ve seçilirse not parçaları doğrudan yazılır. Harici ilişkiler, gömülü fontlar veya Office SDK'sı yoktur. Tema font isimleri font dosyası dağıtımı değildir. [Önceki ticari lisans araştırması](PPTX_COMPATIBILITY_AND_LICENSE_REVIEW.md) geçerlidir; gerçek runtime envanteri tekrar denetlendi.

Motor bir slayt bitmap'ini üretip PNG'yi doğrudan ZIP girdisine yazar; bütün slaytlar veya ikinci tam PNG byte dizisi bellekte biriktirilmez. PNG tekrar ZIP ile sıkıştırılmaz. Kodlama bitince motorun bitmap'i bırakılır; UI'a teslim edilmiş bitmap recycle edilmez. Yalnız etkin sonuç önizlemesi açılır ve slayt seçimi kimliği korunan tembel listededir. Büyük çözünürlük yine bellek maliyetlidir; yetersiz bellek görünür hata üretir, çözünürlüğü gizlice azaltmaz.

En fazla **100 slayt / 512 MiB PPTX**; mevcut başlık, gövde ve not kapasitesi korunur. Aynı yerleşim motorunun taşma hatası bu çıktıda da korunur. İptal her slaytta, PNG/ZIP yazımında, hash/kopya okumalarında kontrol edilir. Ara `.part` dosyası atomik yeniden adlandırılır, tamamlanan dosya ve SHA-256 yan kaydı diskle eşitlenir. Normal hata/iptalde ara çıktı temizlenir. Yedi günden eski sahipsiz çıktılar temizlenir; kalıcı iş kaydındaki sonuç korunur.

İstek/not tercihi yerel atomik tek yazıcıyla kaydedilir. PPTX kontrol noktası sınırı 8 MiB'dir; böylece 100×8.000 karakter not kapasitesi eski genel 512 KiB sınırıyla yanlışlıkla düşürülmez. Diğer araçlar mevcut 512 KiB varsayılanını kullanır. İstek JSON'u ve hash'i yalnız kaynak/ayar değişiminde hesaplanır; ilerleme bildirimlerinde yeniden seri hale getirilmez. Slayt görselleri mevcut projeye ait özel URI referanslarını kullanır; paylaşım önbelleği proje varlığına dönüştürülmez.

Kurtarmada boyut, hash, slayt/parça sayısı ve gerçek PNG boyutları denetlenir. Kaydetmeden ve açma/paylaşmadan önce sonuç kimliği ve hash doğrulanır. Kaynak URI, kanonik dosya yolu ve okunabilir dosya kimliği (device/inode) üzerinden özgün dosyanın üzerine kayıt reddedilir. FileProvider yetkisi ve kök adları değişmedi. PPTX MIME yalnız sunum **çıktı** kaydına eklendi; genel dosya seçimi PPTX girdisini destekliyor gibi davranmaz.

## Doğrulama

- **106 JVM testi**: yeni 7 test not parçalarının yokluğu/opt-in, XML özel karakterleri ve Unicode, tüm ilişkilerin gerçek iç parçalara bağlanması, çıktı kimliği, geçersiz XML/çözünürlük, çıktı-only kayıt ve tam not kapasitesini kapsar.
- Debug uygulama/test APK derlemeleri ve lint: **0 hata, 62 mevcut uyarı, 2 ipucu**. İmzalı/release derleme yapılmadı.
- **6 yeni cihaz testi**: üç çözünürlük ve notlar açık/kapalı altı gerçek PPTX / 24 slayt; her gömülü PNG beklenen Android bitmap'iyle birebir eşit. Türkçe/İngilizce, Arapça/Farsça, Japonca/Çince/Korece, Hintçe ve asimetrik görsel; üç tema/düzen vardır. Gerçek önizleme boyutu, byte-eşit kayıt, MIME/açma/paylaşma staging, iptal/taşma temizliği, aynı boyutta bozulmuş dosyanın reddi, kaynak koruması, yeniden açma, eski sonuç ve Activity kapanırken arka plan işi test edilir.
- Bağımsız **Artifact Tool 2.8.89** altı üretim PPTX'i açıp 24 slaytı çizdi. Notlar kapalıysa ZIP'te not parçaları bulunmaz; okuyucunun boş not modeli içerik değildir. Açıkken çok alfabeli notlar birebir korunur. Slayt boyutu 9144000×5143500 EMU; ayrı düzenlenebilir metin yoktur. Bağımsız paket bütünlüğü ve geometri denetimleri altı dosyada geçti. Raster karşılaştırması yeniden örnekleme nedeniyle piksel eşitliği olarak sunulmaz; ölçümler `output/development/pptx-export/verification.json` içindedir.
- 22 dil anahtar/yer tutucu/üretilmiş XML kontrolü ve mevcut **95 runtime artifact + native encoder** hash/lisans kapsamı geçti. Yeni runtime paket/font/SDK eklenmedi; Jev veya kaynak yükleme yapılmadı.

Telefon, tablet, %200 yazı, koyu tema ve Arapça RTL düzenlerinde **iki test × beş tekrar** geçti; ekran görüntüleri incelendi. %200 yazıda önizlemeye kaydırmak gerekir; test tembel listede bu gerçek kullanıcı hareketini uygular. Görünür ana işlem düğmesi ortak workspace alt alanındadır. Sonuç önizlemesi 16:9 oranında mevcut genişliği kullanır (yükseklik en fazla 360 dp); not seçeneğinin kutu/metin hizası ve tek erişilebilir dokunma alanı korunur.

**26 regresyon** geçti: proje otomatik kaydı/eski taslaklar, sunum tasarımı/PDF, sunum modu/slayt görselleri ve mevcut arka plan kuyruğu. Bu mevcut yolların tamamını her gerçek cihazda deneme iddiası değildir.

Tekrarlama:

```powershell
./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
python tools/localization/generate.py --check
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/pptx-export-qa.ps1 -Suite feature
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/pptx-export-qa.ps1 -Suite layouts -SkipInstall
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/pptx-export-qa.ps1 -Suite regression -SkipInstall
# Host tarafında mevcut bundled Artifact Tool modülü ve altı açık native dosya yolu:
node tools/pptx/fixtures.mjs render <artifact_tool.mjs> output/development/pptx-export/rendered <native-pptx-paths...>
python tools/pptx/verify_export.py output/development/pptx-export
```

## Kabul edilmemiş yollar ve devam

Gerçek PowerPoint, Google Slides ve LibreOffice kontrolü; tam OOXML şema doğrulaması; yeni yolun gerçek OS süreç öldürme matrisi, dolu disk/provider hata matrisi, fiziksel ARM/düşük RAM, pil/termal, eski OEM/API ve manuel TalkBack denemeleri henüz yapılmadı. Emulator başarısından fiziksel cihaz performans garantisi çıkarılmaz. Yeni kodun küçültülmüş release/R8 testi sonraki kullanıcı yayın isteğinde tamamlanacak.

**Sıradaki somut iş**, gerçek Office uygulamalarında çıktıyı kontrol etmek ve ortak yerleşim/RTL/font ölçümleriyle **düzenlenebilir PPTX seçeneğinin** uyumluluk alt kümesini hazırlamaktır. Genel Office içe aktarma ayrı ve kayıp önizlemesi gerektiren daha geniş iştir. Önceki raster/nesne denemesi genel uyumluluk kabulü sayılmaz.

## 10 Ekim sürüm öncesi kontrolü

`62b5b88d2713f302cfd74a51996995c28dc927d4` için iki GitHub debug kontrolü başarıyla tamamlandı.
PPTX özellik paketi QA emülatöründe yeniden çalıştırıldı: **6 test geçti**. Bu çalıştırmanın
ürettiği altı PPTX bağımsız okuyucuda yeniden açıldı ve 24 slayt/not kontrolü geçti.
95 runtime artifact + native encoder lisans/hash kapsamı ve 12 host envanter testi de geçti.

PowerPoint/LibreOffice standart kurulum yollarında bulunamadı ve bağlı belge oturumu yoktu.
Bu nedenle **gerçek PowerPoint kontrolü halen bekliyor**. Yeni
`tools/pptx/prepare_office_review.py` aracı yalnız doğrulanmış sentetik çıktıları
değiştirmeden kopyalar; beklenen 24 slayt görselini, notları ve sonuç şablonunu yerel
pakete ekler. Office çalıştırmaz, ağ isteği yapmaz, herhangi bir uyumluluk sonucunu
başarılı işaretlemez. Ayrıntılar [sürüm öncesi kontrol kaydında](../../testing/PPTX_PRE_RELEASE_CHECKS.md).

Uygulama kodu, sürüm numarası ve yayın paketleri bu kontrolde değişmedi. Yeni imzalı
sürüm istendiğinde release/R8, native kaynak/hizalama ve yayın smoke kontrolleri yapılacak.
