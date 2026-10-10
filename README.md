# 10 Ekim — 0.1.11 test sürümü

Kullanıcının isteğiyle **0.1.11 (12)** imzalı APK/AAB hazırlandı. Dosya odaklı arayüz, video katmanları/kare çıkarma, görsel/slaytlardan MP4, tam ekran sunum ve slayt görseli/PPTX çıktıları bu pakettedir. [Dosyalar, EN/TR notları, doğrulamalar ve sınırlar](YAYIN_0.1.11.md). Semboller AAB içinde; gerçek kapsam 8/12. Play'e yükleme yapılmadı. Gerçek Office ve fiziksel cihaz kontrolleri bekler. Aşağıdaki kayıtlar tarihseldir.

# 9 Ekim — görünümü koruyan PPTX çıktısı

Sunum düzenleyicisinde **Diğer işlemler → PPTX dışa aktar** eklendi: 1024/2048/4096 kenar, kapalı varsayılan konuşmacı notu tercihi, gerçek çıktı önizlemesi/boyutu, kayıt/açma/paylaşma ve kalıcı arka plan işlemi. Her slayt tek görseldir; metin/nesneler ayrı düzenlenemez veya metin olarak aranamaz. Genel PPTX girdisi açılmadı. [Kapsam, testler ve sınırlar](PPTX_GORUNUM_KORUYAN_DISA_AKTARIM.md). Yeni runtime SDK veya release yok; 0.1.10 (11) sabit. Sırada gerçek Office/RTL/font kontrolü ve düzenlenebilir çıktının kabul alt kümesi var. Aşağıdaki kayıtlar önceki adımlardır.

# 9 Ekim — PPTX uyumluluk ve ticari lisans denemesi

Test paketinde düzenlenebilir metin/PNG ve görünümü koruyan raster PPTX yolları karşılaştırıldı. Altı gerçek Android çıktısı / 24 slayt bağımsız okuyucuda açıldı; metin düzenleme round-trip'i, ayrı notlar ve kaynak görseli koruma doğrulandı. RTL/font/yerleşim ve gerçek Office kontrolü bekler; genel PPTX desteği henüz uygulamada açılmadı. Yeni SDK veya yayın paketi yok. [Bulgular ve sıradaki üretim entegrasyonu](PPTX_UYUMLULUK_VE_LISANS_DENEMESI.md). Aşağıdaki kayıtlar önceki adımlardır.

# 8 Ekim — tam ekran sunum ve slayt görseli

Konuşmacı notları, duraklatılabilir sayaç ve tam ekran sunum; ayrıca seçili slayttan doğrudan 1024 / 2048 / 4096 kenarlı PNG / JPEG / WebP / BMP çıktı eklendi. Yeni yollar düzenleyicide **Diğer işlemler** menüsündedir. Notlar yerel taslakta tutulur; PDF/video/görsel çıktılara girmez. Eski taslaklar, PDF/MP4, özgün dosyalar ve araç aktarımı korunur. [Kapsam ve doğrulama](SUNUM_MODU_VE_SLAYT_GORSELLERI.md). Yeni release üretilmedi. Sırada PPTX için lisans ve uyumluluk denemesi var; genel Office desteği henüz ilan edilmez.

# 8 Ekim — görsel / slaytlardan zamanlamalı MP4

Fotoğraf dizisi veya mevcut sunumun bağımsız kopyasından sessiz H.264 MP4 üretimi eklendi: kare sırası / süreleri, üç kadraj, üç kalite ve 640 / 1280 / 1920 kenar; gerçek sonuç oynatma, kayıt ve mevcut video düzenleyicisine aktarım. Kaynaklar, atomik oturum, arka plan işi ve büyük görsel aktarımı onayı korunur. [Kapsam ve doğrulama](GORSEL_SLAYT_VIDEO.md). Yeni release üretilmedi. Bu paket ardından tam ekran sunum, not / zamanlayıcı ve slayt görseli tamamlandı; güncel kapsam üstteki kayıttadır. Aşağıdaki kayıtlar tarihsel geliştirme notlarıdır.

# Güncel geliştirme: video karesini görsel araçlarına aktarma

8 Ekim geliştirme kodunda özgün klipten veya düzenlenmiş video sonucundan seçilen kare önizlenip tam çözünürlüklü PNG olarak görsel araçlarına aktarılabilir. Zaman / yön / renk, bağımsız kaynak kopyası ve özgün dosyaların korunması denetlenir. [Kapsam ve doğrulama](VIDEO_KARE_AKTARIMI.md). Yayın paketi değişmedi; sıradaki ürün işi görsel / slaytlardan MP4'tür.

## Video ses miksajı

8 Ekim geliştirme kodunda videoya tek kullanıcı sesi eklenebilir; kaynak aralığı, videodaki başlangıcı ve iki sesin düzeyi ayrı ayarlanır. Yazı ve görsel / logo katmanları birlikte kullanılabilir. [Kapsam ve doğrulama](VIDEO_SES_MIKSAJI.md). Yayın paketi değişmedi; sıradaki iş video karesini görsel araçlarına aktarmaktır. Aşağıdaki kayıtlar önceki geliştirme adımlarını anlatır.

## Görsel ve PDF hedefleri

BMP görsel içe / dışa aktarımı ve PDF sayfası PNG / JPEG / WebP hedefleri eklendi. Hedef, kalite, mevcut çözünürlükler ve özel araç aktarımı korunur. [Kapsam ve doğrulama](GORSEL_VE_PDF_CIKTI_HEDEFLERI.md). Yayın paketi değişmedi; aşağıdaki önceki sürüm kayıtları tarihsel bilgi içerir.

# Android içerik stüdyosu

## 6 Ekim — ses aracı ve WAV / M4A

MP3 / M4A / AAC / WAV PCM16 / OGG Vorbis veya Opus / FLAC girdilerinden WAV PCM16 veya M4A AAC çıktı üretimi ve videodan tek ses parçası çıkarma eklendi. Düzenlenmiş videonun sonucu doğrudan ses aracına kopyalanır. Bitrate seçimi, gerçek oynatma, kaynak / ayar / tamamlanan sonuç kaydı, ortak arka plan sırası ve büyük kopya onayı korunur. [Kapsam ve sınırlar](SES_ARACLARI_VE_CIKTI_BICIMLERI.md). Sürüm artırılmadı; görsel ek hedefleri ve ek video codec / muxer denemeleri sıradadır. Aşağıdaki kayıtlar önceki adımlardır.

## 6 Ekim — arka plan işleri ve GitHub

Video kodlama ve fotoğraf PDF üretimi kullanıcı başlattığında ön plan servisiyle sürer; Activity kapanması işi iptal etmez. Tek ağır iş sırası, bildirimden iptal, sistem süre sınırı ve atomik sonuç / iş kaydı eklendi. Tamamlanmış çıktı geri yüklenir; yarıda kalan iş kullanıcı eylemiyle yeniden başlatılır ve büyük kopya onayı taşınmaz. [Kapsam ve doğrulama](ARKA_PLAN_ISLEM_YONETIMI.md). Yeni release yoktur. Sıradaki ürün işi çıktı biçimleri / ses aracı / codec denemeleridir; fiziksel cihaz ve gerçek düşük depolama matrisi bekler. [Özel GitHub deposu ve geliştirme akışı](GITHUB_VE_VERSIYON_KONTROLU.md). Aşağıdaki kayıtlar önceki adımları anlatır.

## 6 Ekim — video / fotoğraf PDF çalışmasını koruma

Video kaynağı, kesim / kalite / çözünürlük ayarları ve fotoğraf PDF kaynakları / sırası / çözünürlüğü atomik yerel kayıtla geri yüklenir. Geri dönmek çalışmayı korur; ayrı temizleme ve kayıt hatasında yeniden deneme vardır. Gerçek süreç kapatma ve yeniden çıktı üretimi emülatörde doğrulandı. Çıktı ve büyük kopya onayı yeniden üretilir; yarım kalan işlem otomatik sürdürülmez. [Kapsam ve doğrulama](MEDYA_OTURUM_DAYANIKLILIGI.md). Yeni release yoktur. Sıradaki somut iş uzun işlemlerin arka plan / bildirim / kalıcı iş durumu yönetimidir; aşağıdaki önceki adım notları tarihseldir.

## 6 Ekim — metin / tablo okuyucusu ve seçilebilir çıktı

TXT / Markdown kaynak metni ve CSV / TSV tabloları yerel okunur. Metinden TXT / PDF; tablodan TXT / CSV / TSV / PDF çıktısı seçilir. Kodlama / ayırıcı kontrolleri, tam satır ayrıntısı, seçilen içeriği sunuma ekleme ve oluşturulan PDF'yi okuyucuya aktarma çalışır. Kaynaklar korunur; Office / biçimli Markdown desteği eklenmedi. [Kapsam, sınırlar ve doğrulama](METIN_TABLO_OKUYUCU_VE_DONUSTURME.md). Sürüm artırılmadı, release üretilmedi. Sonraki somut iş video / fotoğraf PDF oturum dayanıklılığıdır; DOCX / XLSX ayrıca teknik deneme bekler.

## 6 Ekim — telefon medyası ve sade başlık

Geliştirme kodunda HEIC / HEIF / AVIF girişi görsel, sunum ve fotoğraf PDF araçlarına ortak eklendi. Video girişleri MOV / 3GP / MKV / WebM ve HEVC 8 bit SDR / VP8 codec'leriyle genişledi; cihaz çözücüsü denetlenir, çıktı mevcut MP4 / H.264 / AAC olarak kalır. Başlık arka planla bütünleşir; dil seçimi metin düğmesidir. Lacivert tanıtım kartı korunur, içindeki üç kısayolun ayrı mavi kutuları kaldırılmıştır. [Destek koşulları ve doğrulama](TELEFON_MEDYA_BICIMLERI_VE_SADE_BASLIK.md). Yeni release yoktur; eski AAB korunur. Sonraki ürün adımı belge / tablo okuyucusu kapsamı ve oturum dayanıklılığıdır.

## 6 Ekim — PDF sayfa araçları ve arayüz işlev denetimi

PDF birleştirme, seçili sayfalardan yeni belge oluşturma, sıralama, 90° döndürme, tek sayfa önizleme ve geri alma eklendi. Metin / vektörler korunur; kaynaklar değişmez. Ana ekranın üç tanıtım kısayolu ve eski video taslağı editör geçişi çalışır. Açık / koyu mod yazı, düğme, alan ve vurgu renkleri düzenlendi; son kullanıcı tercihiyle araç ikonları ortak mercan ailesine geçirildi. Mevcut Figma renk referansı eşleştirildi. [Kapsam, bakım sınırı ve doğrulama](PDF_SAYFA_ARACLARI_VE_ARAYUZ_INCELEMESI.md). Release üretilmedi; sıradaki ürün işi telefon medya formatlarıdır.

## 6 Ekim — geliştirmede harici PDF okuyucu

PDF içe alma, sayfa gezinme / zoom, orijinalden baytları değiştirmeden kopya kaydetme / açma / paylaşma ve sayfadan 1280 / 2048 / 4096 PNG üretme eklendi. PNG doğrudan sunum / fotoğraf PDF aracına aktarılır. Yerel PDF motoru ayrı kısıtlı süreçte çalışır; önizleme ve dışa aktarım ayrıdır. [Kapsam, testler ve sınırlar](PDF_OKUYUCU_VE_SAYFA_AKTARIMI.md).

Kullanıcı isteğiyle bu adımda sürüm artırılmadı, release APK / AAB üretilmedi. Aşağıdaki dondurulmuş 0.1.8 paketi yeni okuyucuyu veya sayfa motorunu içermez.

## 6 Ekim — 0.1.8 (9) uyumluluk paketi

İşlem bazlı desteklenen giriş / çıktı yardımı, ortak gerçek içerik doğrulaması, ayrı içe alma hata nedenleri ve 22 dilde yeni metin / ölçü etiketleri eklendi. HDR'nin Android metadata alanında eksik bildirildiği AVC başlangıç verisi de kontrol edilir. Mevcut yetenek / kalite / çözünürlük ve büyük kopya onayı korunur. Yeni format motoru eklenmez.

25 birim / 68 Android debug testi geçti. Gerçek imzalı küçültülmüş APK'da 7 telefon akışı ve tablet / büyük yazılı dar ekran / koyu modda 3'er ilgili akış geçti; süreç yeniden açılışı da doğrulandı. AAB `output/releases/0.1.8/birlevo-0.1.8-9.aab`, etiketli notlar aynı dizindeki `release-notes.txt` içindedir. Play'e yüklenmedi. [Kapsam, Jev, testler ve sınırlar](DIL_VE_DOSYA_UYUMLULUGU_0.1.8.md). Sonraki iş harici PDF okuyucu / sayfa araçlarıdır. Native sembol sağlayıcı sınırı sürer; fiziksel düşük donanım ölçümü açık kalır.

## 6 Ekim — 0.1.8 öncesindeki inceleme

Kullanıcı 0.1.7 (8) Alpha sürümünü Google incelemesine gönderdiğini bildirdi; onay / testçilere açılma henüz doğrulanmadı. Dil kaynakları ve gerçek dosya desteği kontrol edildi; Jev 116 seçilmiş maddeden 114'üne geçerli yanıt verdi. Native sembol ayarı hazır, ancak önceden sembolleri çıkarılmış AndroidX bağımlılığı için uyarı tamamen giderilemedi. Sonraki iş uyumluluk / dil, ardından harici PDF okuyucu ve sayfa araçları. Kanıtlar, sınırlar ve iş sırası [güncel incelemede](DIL_DOSYA_VE_JEV_INCELEMESI.md). Aşağıdaki 5 Ekim dağıtım açıklamaları tarihsel kayıttır; gönderilmiş AAB korunur.

## Jev ile geliştirme değerlendirmesi

`tools/jev/` yalnız bilgisayarda çalışan, elle başlatılan çeviri / arayüz metni / seçilmiş geri bildirim / test önceliği değerlendirme aracıdır. Android paketine, Gradle'a veya uygulamanın çalışmasına bağımlılık eklemez. Varsayılan çevrimdışı plan üretir; API kullanımı için `--live` ve proje dışında saklanan anahtar gerekir. [Kurulum, kapsam ve doğrulama](JEV_GELISTIRME_ARACI.md).


## 5 Ekim — 0.1.7 görsel formatları ve fotoğraf PDF'si

0.1.7 (8) Console'da dahili test kullanıcıları tarafından kullanılabilir olarak doğrulandı. Alpha için paket ve İngilizce notlar taslak olarak kaydedildi; mevcut kapalı test sürümü 0.1.0'dır. Güncel gizlilik sayfaları yayımlandı ve bağımsız tarayıcıda doğrulandı. Kullanıcı kalan Console işlemlerini kendisi tamamlayacak; [sürüme özel kontrol listesi](KAPALI_TEST_0.1.7_KONTROL_LISTESI.md) güncellendi. İlk derleme QA dosyasındaki yükleme durumu tarihsel kayıt olarak korunur.

JPEG / PNG / durağan WebP giriş ve gerçek JPEG / PNG / kayıplı WebP çıktısı eklendi. PNG / WebP şeffaflığı, biçime özel kayıp açıklaması ve sonuca özel büyük dosya onayı korunur. Fotoğraflar çoklu seçilip sıralanır; yönleri düzeltilerek A4 PDF yapılır, gerçek sonuç önizlenir, kaydedilir, açılır ve paylaşılır. İşlenen görsel doğrudan bu araca eklenebilir. Fotoğraf seçimi ekran döndürmede korunur; süreç yeniden başlatma için taslak kaydı henüz yoktur. Görsel işlevleri / kalite seçenekleri azaltılmadı.

[Kapsam, sınırlar ve doğrulama](GORSEL_FORMATLARI_VE_FOTOGRAF_PDF.md), nihai ölçümler `output/development/0.1.7/qa-summary.json` içinde tutulur. 0.1.7 gizlilik metni Cloudflare Pages'te yayımlandı; Alpha 0.1.7 incelemeye gönderilmedi. Sonraki ürün paketi PDF birleştirme / bölme / sıralama için güvenli belge altyapısıdır. Testçi hataları öncelikli kalır.

5 Ekim aktarım devamı: `0.1.6 (7)` üretilen görseli yeni / kayıtlı sunuma ekler; yeni slayt görsellerini proje içinde korur. Görsel, video ve PDF sonuçları açılıp paylaşılabilir. Büyük sıkıştırma kopyasının onay kuralı korunur. [Kapsam ve doğrulama](ORTAK_CIKTI_AKTARIMI.md). Sonraki adım PNG / WebP ve fotoğraflardan PDF; yeni gizlilik metni yayıma hazırlanmıştır. AAB henüz Play'e gönderilmedi.

Kotlin / Jetpack Compose uygulama temeli; görsel / video sıkıştırma ve sunumdan PDF üretme akışları. Ürün kapsamı [URUN_PLANI.md](URUN_PLANI.md) içindedir.

5 Ekim sıkıştırma devamı: `0.1.5 (6)` gerçek dosya boyutuna göre görsel / video kayıt kontrolü ekler. Küçülmeyen çıktı normal kayıttan geçmez; düzenleme / dönüşüm kopyası açık onayla korunur. Video işlem öncesi boyut artışı riski gösterilir, kalite otomatik düşürülmez. İmzalı APK telefon, tablet ve büyük yazılı dar ekranda doğrulandı. [Kapsam ve test kanıtı](SIKISTIRMA_FAYDA_KONTROLU.md); AAB Play'e yüklenmedi.

5 Ekim performans devamı: `0.1.4 (5)` ana sayfa, dil seçimi ve sunum ekranını ayrı dosyalara taşıyor; slayt / proje listelerini ihtiyaç oldukça hazırlıyor. JPEG iş tamponları azaltıldı, iptal edilen önizlemelerin temizlenmesi güçlendirildi, her tuşta JSON dönüştürme kaldırıldı ve release için R8 / kaynak küçültmesi açıldı. Mevcut format, çözünürlük ve kalite seçenekleri korunuyor. İnceleme, doğrulamalar ve açık teknik sınırlar [MODULERLIK_VE_PERFORMANS.md](MODULERLIK_VE_PERFORMANS.md) içinde. Önceki dağıtım paketleri korunur; yeni paket Play'e gönderilmez.

## Bu başlangıç sürümüne eklenenler

- Dar ekranlarda tek sütun, geniş pencerelerde görevler ve proje listesi yan yana.
- Yuvarlak kartlar / alanlar / düğmeler, mercan Birlevo yazısı ve tek buz mavisi araç simgesi ailesi. Açık / lacivert koyu görünüm cihaz ayarını izler; kart yüzeyleri ve kenarlıkları arka plandan ayrılır. Android açılış penceresinde 300 ms kısa geçiş; sistem animasyonları kapalıyken ve yeniden oluşturmada geçiş hemen kaldırılır.
- 22 dil / yerelleştirme seçeneği, cihaz diline dönüş, Arapça / Farsça yönü için Android kaynak yapısı.
- Sunum taslağında başlık / metin düzenleme, slayt ekleme ve slayt başına JPEG / PNG görsel seçme, önizleme ve kaldırma.
- Önceden kaydedilmiş video taslaklarını açma ve taslaktaki kaynak videoya kalıcı okuma izni.
- Sunum taslağını düzenleme durduktan 600 ms sonra, arka plana geçişte ve PDF önizlemesine geçerken otomatik kaydetme; kayıt durumunu ve hata sonrası yeniden denemeyi gösterme. Kaydet / Geri ile son değişiklikleri saklayıp çıkma; kayıtlı taslağı yeniden açma.
- Slayt işlemleri menüsünden çoğaltma, yukarı / aşağı taşıma ve onayla silme. En az bir slayt korunur; ekleme / çoğaltma 100 slaytla sınırlıdır. Metin, görsel referansı ve slayt kimliği sıralamada korunur.
- Sunumun tamamına Klasik / Okyanus / Sıcak teması; her slayda görsel sağda / solda / üstte düzeni uygulama. Seçimler taslakta, çoğaltılan slaytta ve PDF'de korunur. Görselsiz slaytlar tam genişlikte metin kullanır.
- Düzenleyicide isteğe bağlı gerçek slayt önizlemesi; PDF ile aynı çizim kodu ve 16:9 geometri. Aynı anda tek slayt önizlemesi açılır, düzenleme sonrası güncellenir. Taşan içerik kesilmez, slayt numarasıyla uyarılır.
- Sürümlü proje dosyası, atomik yazma ve yükleme hatasında mevcut dosyanın üstüne yazmayı önleme.
- Açık taslak için Android kaydedilmiş durum mekanizması; ekran döndürmede durumun geri getirilmesi.
- JPEG / PNG seçme, kalite ve en uzun kenar ayarı, gerçek JPEG çıktısından önizleme, boyut karşılaştırması ve yeni dosyaya kaydetme.
- Görsel yönünü EXIF bilgisine göre düzeltme, şeffaf alanları beyaza çevirme, konum / EXIF bilgilerini çıktıya taşımama; iptal ve hata sonrası yeniden deneme.
- Ana ekrandaki **Video düzenle** doğrudan MP4 kesme aracını, **Video sıkıştır** ise aynı aracın sıkıştırma odaklı görünümünü açar. Başlangıç / bitiş seçimi, yalnızca seçilen aralığı oynatma, üç kalite profili, çözünürlük sınırı, işlem ilerlemesi / iptal, boyut karşılaştırması ve yeni dosyaya kaydetme desteklenir. Kesme ile sıkıştırma tek dışa aktarmada yapılır; H.264 görüntü ve varsa AAC ses desteklenir. Düzenleyici ayarlarını proje olarak kaydetme bu erken sürümde yoktur.
- Sunum taslağından metin ve seçili görselle 16:9 PDF üretme, gerçek PDF sayfalarından önizleme, sayfa geçişi, taşan metin için slayt numarasıyla uyarı ve yeni belgeye kaydetme. Önizlemeden dönünce düzenlemeler korunur.

Bu bir geliştirme derlemesidir. Güncel çalışma sürümü `0.1.3 (4)` yuvarlak arayüz, lacivert koyu mod ve kısa açılış geçişini ekler; önceki sunum tema / düzen / önizleme, otomatik kayıt ve slayt yönetimini sürdürür. [Arayüz test raporu](ARAYUZ_TASARIM_TEST_RAPORU.md) ve [tasarım dili / Figma referansı](TASARIM_DILI.md). Zaman çizelgesi, daha gelişmiş sunum şablonları, hedef dosya boyutuna otomatik optimizasyon, diğer dosya formatları, video düzenleme oturumunu otomatik kaydetme ve reklam SDK entegrasyonu sonraki aşamalardır. Mevcut dil metinleri taslak çevirilerdir; yayın için dil ve gerçek cihaz kontrolleri gerekir.

Kapalı test için çalışma adı **Birlevo** ve uygulama kimliği `studio.birlevo.android` seçildi. Ad ve simge kararı [MARKA_VE_LOGO.md](MARKA_VE_LOGO.md) içindedir. İlk Play yüklemesinden önce kimliğin Console'da kullanılabilirliği ve adın hak durumu doğrulanmalıdır; yüklemeden sonra paket kimliği değiştirilemez. Başlangıç derlemesi `minSdk 24`, `targetSdk 36` kullanır; nihai minimum cihaz desteği teknik testlerle kesinleşir. Ham videolar ve fotoğraflar sunucuya gönderilmez; sunucuya aktarım veya reklam SDK'sı eklenmemiştir. Uygulamada INTERNET izni yoktur; Media3 bağımlılığı normal ACCESS_NETWORK_STATE iznini getirir. Video taslağı ve sunum görselleri dosya referansı kullanır; kaynak silinirse erişim kaybolabilir. Proje varlıklarını içeri kopyalama sonraki aşamada ele alınacak.

## Derleme

JDK 17 ve Android SDK 36 gerekir. Yerel SDK konumunu git dışında tutulan `local.properties` dosyasındaki `sdk.dir` ile ayarlayın.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Bu dosya geliştirme anahtarıyla imzalanır; mağaza yayınına uygun yayın paketi değildir.

Kapalı test hazırlığında `./gradlew bundleRelease` ile `app/build/outputs/bundle/release/app-release.aab` üretilir. Dört `BIRLEVO_UPLOAD_*` ortam değişkeni ayarlanmadıysa AAB imzasızdır ve Play'e yüklenemez. Anahtar ve Play hazırlık sırası [PLAY_KAPALI_TEST_HAZIRLIK.md](PLAY_KAPALI_TEST_HAZIRLIK.md) içindedir.

5 Ekim'de son logolu imzalı `0.1.0 (1)` paketi [output/releases/0.1.0/birlevo-0.1.0-1.aab](output/releases/0.1.0/birlevo-0.1.0-1.aab) olarak hazırlandı ve kullanıcı Play Console'a yükledi. Bu paket sonraki geliştirmelerden etkilenmez. Yayın anahtarı proje dışında saklanır; aynı Windows kullanıcısında `.\tools\release\build-signed-release.ps1` ile yeniden derlenebilir. Doğrulamalar ve yerel anahtar yedeğinin sınırları [YAYIN_PAKETI_RAPORU.md](YAYIN_PAKETI_RAPORU.md) içindedir.

## Yerel kod grafiği

Folklore Archive'deki gibi Graphify, kod ve bileşen ilişkilerini geliştirme sırasında sorgulamak için kullanılır. Android APK'sına bağımlılık eklemez. `graphify-out/` yerel olarak üretilir ve Git dışında tutulur; proje kapsamındaki kullanım kuralı [AGENTS.md](AGENTS.md) içindedir.

```powershell
graphify extract . --code-only --no-cluster
graphify query "Video kesme çıktısı nasıl üretiliyor?"
graphify update . --no-cluster
graphify export html
```

İlk yerel AST grafiği oluşturuldu ve video işleme sorgusuyla denendi. İnteraktif çıktı `graphify-out/graph.html` içindedir. Graphify üç ViewModel dosyasında kısmi Kotlin ayrıştırma uyarısı verdi; bu dosyalarla ilgili kararlar kaynak kod ve testlerle ayrıca doğrulanır. Kod ve dokümanlar için daha kapsamlı semantik çıkarım bu haftaki kapalı test kapsamına alınmadı.

5 Ekim 2026 güncel kontrol sonucu: APK ve release AAB derlemesi, yerel JUnit testleri ve `lintDebug` başarılı. Dil kaynak kontrolü 22 dilde 79 metin anahtarı için geçti. Android 16 telefon emülatöründe tüm 28 cihaz testi yeni studio.birlevo.android kimliğiyle geçti. Video kesme ayrıca tablet boyutunda ve 320 dp / iki kat yazı büyüklüğünde sınandı. Ara dosyalar, emülatörün düşük boş alan koşulunda önbellek temizliğine uğramaması için uygulamanın yedeklenmeyen özel dosya alanında tutulur ve 24 saat sonra temizlenir. İlk temel rapor [EMULATOR_TEST_RAPORU.md](EMULATOR_TEST_RAPORU.md), görsel / video doğrulamaları [GORSEL_SIKISTIRMA_TEST_RAPORU.md](GORSEL_SIKISTIRMA_TEST_RAPORU.md), [VIDEO_SIKISTIRMA_TEST_RAPORU.md](VIDEO_SIKISTIRMA_TEST_RAPORU.md) ve [VIDEO_KESME_TEST_RAPORU.md](VIDEO_KESME_TEST_RAPORU.md), PDF doğrulaması [SUNUM_PDF_TEST_RAPORU.md](SUNUM_PDF_TEST_RAPORU.md) içindedir. Gerçek Android sistem dosya seçicisiyle uçtan uca emülatör kontrolü [GERCEK_DOSYA_SECICI_TEST_RAPORU.md](GERCEK_DOSYA_SECICI_TEST_RAPORU.md) içindedir; fiziksel cihaz henüz sınanmadı.

## Görsel sıkıştırmayı kullanma

1. Ana ekranda **Görsel sıkıştır** seçeneğini açın ve JPEG veya PNG seçin.
2. Kaliteyi 40–95 arasında, en uzun kenar sınırını 1280 / 1920 / 2560 px olarak ayarlayın.
3. **Önizleme oluştur** ile gerçek JPEG çıktısını üretin. Önce / sonra boyutlarını ve çözünürlüklerini karşılaştırın.
4. **Yeni görseli kaydet** ile Android dosya seçicisinden çıktı konumunu seçin. Orijinal dosyaya yazılmaz.

Girdi en fazla 100 MP ve 128 MiB olabilir. Küçük görseller büyütülmez. Belleği korumak için ara görseller yaklaşık 4 MP ile sınırlanır; çıktı seçilen kenar sınırından daha küçük olabilir. Şeffaflık beyaz zemine dönüşür; JPEG kayıplıdır, renkler değişebilir, animasyon ve EXIF bilgileri korunmaz. Her görselde boyut azalması garantisi yoktur; daha büyük çıktı ekranda belirtilir. Ayar değişince önceki çıktı geçersiz olur ve yeni önizleme gerekir.

Kaynak uygulamanın yedeklenmeyen özel dosya alanına kopyalanır; kalıcı kaynak okuma iznine ihtiyaç duyulmaz. Ekran yeniden oluşturulunca işlem / sonuç ViewModel içinde korunur. Uygulama süreci öldürülürse bu geçici görsel oturumu geri yüklenmez; yeniden seçim gerekir. 24 saatten eski işlem dosyaları araç yeniden açıldığında temizlenir. İptal sırasında devam eden yerel kodlayıcı çağrısının bitmesi beklenebilir; sonuç kullanılmaz. Başarısız dışa aktarmada yeni kısmi belge sağlayıcı destekliyorsa silinir.

## Video kesme ve sıkıştırmayı kullanma

1. Ana ekranda **Video sıkıştır** seçeneğini açın ve desteklenen MP4 dosyasını seçin.
2. Başlangıç ve bitiş noktalarını seçin; **Seçimi önizle** yalnızca bu aralığı oynatır. Tam videoyu kullanmak için başlangıcı 0, bitişi kaynak süresinde bırakın.
3. **Küçük dosya**, **Dengeli** veya **Yüksek kalite** seçin. En uzun kenarı 640 / 1280 / 1920 px ile sınırlayabilirsiniz.
4. **Çıktıyı oluştur** ile kesme ve sıkıştırmayı tek işlemde uygulayın. Kaynak ve çıktı kartlarında boyut, çözünürlük, süre ve oynatma seçeneklerini kullanın.
5. **Yeni videoyu kaydet** ile yeni bir belgeye yazın. Aralık veya kalite değişince önceki çıktı geçersiz olur; orijinal korunur.

İlk akış, MP4 içindeki tek H.264 görüntü parçasını ve isteğe bağlı tek AAC ses parçasını destekler. En fazla 10 dakika / 512 MiB, en uzun kenar 4096 px ve yaklaşık 8,8 MP girdi; biliniyorsa en fazla 60 kare/s kabul edilir. HDR, altyazı ve ek parçalar reddedilir. Küçük videolar büyütülmez. Çıktı MP4 / H.264'tür; uyumlu AAC ses korunur. Kalite profilleri hedef bit hızını değiştirir, kesin dosya boyutu veya her dosyada küçülme garantisi vermez. Daha büyük çıktı ekranda belirtilir. Kodlayıcı desteği cihaza bağlıdır; başarısız işlemde daha düşük çözünürlük veya farklı dosyayla yeniden deneme sunulur.

Medya3 Transformer ve cihazın kodlayıcıları kullanılır. Kesilen video yeniden kodlanır; MP4 düzenleme listesi hızlandırması kullanılmaz. En kısa seçim, kaynak süre uygunsa bir saniyedir. Kaynak yedeklenmeyen özel dosya alanına kopyalanır; orijinal URI yazma için açılmaz. İptalde motor durdurulur ve kısmi çıktı silinir. Ekran yeniden oluşturulunca ViewModel oturumu korur; süreç öldürüldüğünde geçici oturum geri yüklenmez. Uzun işlemler için arka plan hizmeti henüz yoktur. Araç açılınca 24 saatten eski işlem dosyaları temizlenir. Kesme doğrulaması [VIDEO_KESME_TEST_RAPORU.md](VIDEO_KESME_TEST_RAPORU.md), önceki sıkıştırma testleri [VIDEO_SIKISTIRMA_TEST_RAPORU.md](VIDEO_SIKISTIRMA_TEST_RAPORU.md) içindedir.

## Sunumdan PDF oluşturma

1. Yeni bir sunum oluşturun veya kayıtlı taslağı açın; slayt başlıklarını ve metinlerini yazın. İsterseniz her slayta bir JPEG / PNG görseli seçin.
2. **PDF önizle** ile her slaydı bir PDF sayfasına dönüştürün. **Önceki / Sonraki** ile gerçek PDF sayfalarını kontrol edin.
3. **Yeni PDF kaydet** ile Android dosya seçicisinden yeni çıktı konumunu seçin.
4. **Geri** ile aynı düzenleme taslağına dönün. Metni değiştirdikten sonra yeniden önizleme oluşturulur.

İlk çıktı metin ve slayt başına en fazla bir görsel içerir: 960×540 pt, 16:9, açık zemin, sabit renk / yazı düzeni. Cihazdaki yazı büyüklüğü PDF ölçülerini değiştirmez. Görsel oranı korunarak metnin yanına yerleştirilir; yalnızca görsel içeren slayt da kabul edilir. JPEG / PNG için en fazla 100 MP ve sağlayıcı dosya boyutunu bildiriyorsa 128 MiB sınırı uygulanır; PDF için görsel küçültülerek çözülür. Başlık 44–28 pt, gövde 26–18 pt aralığında sığdırılır; görsel varsa metin alanı daralır. Okunaklı alt sınıra rağmen metin sığmıyorsa çıktı reddedilir ve ilgili slayt belirtilir. Slayt metni kesilmez. Tekrarlanan proje etiketi çok uzunsa kısalır. En fazla 100 slayt, 500 karakter proje adı, slayt başlığı için 1000, gövde için 8000 ve toplam 200.000 karakter kabul edilir. Tamamen boş sunum için içerik ekleme mesajı gösterilir.

PDF üretimi proje kaydının yerine geçmez; sunum düzenlemeleri ayrı otomatik kaydedilir ve önizlemeye geçerken bekleyen değişiklikler için kayıt başlatılır. Önizleme yedeklenmeyen özel dosya alanında tutulur; ekran yeniden oluşturulunca ViewModel korur. Süreç yeniden başladığında kayıtlı Android taslak durumu varsa PDF yeniden üretilir; kalıcı PDF işlem oturumu henüz yoktur. Sonuç hazır olmadan dönen yeni belge, sağlayıcı destekliyorsa kaldırılır. Arka plan hizmeti henüz yoktur. Eski PDF işlem dosyaları 24 saat sonra araç açılışında temizlenir.

Sistem fontları ve Android PDF motoru kullanılır. Çok alfabeli örnek görsel olarak doğrulandı; metin kopyalama / arama için tüm alfabelerde eksiksiz Unicode eşleme garantisi yoktur. PDF metni çıkarılırken Arapça / Farsça şekillenmiş harfler, CJK uyumluluk karakterleri ve bazı Hintçe kümelerinde eksikler görüldü. PPTX, animasyon, video yerleştirme, gelişmiş görsel düzeni, şablon seçimi ve erişilebilir etiketli PDF bu ilk akışta yoktur. Ayrıntılar [SUNUM_PDF_TEST_RAPORU.md](SUNUM_PDF_TEST_RAPORU.md) ve [SUNUM_GORSEL_TEST_RAPORU.md](SUNUM_GORSEL_TEST_RAPORU.md) içindedir.

## Emülatör testleri

Yalnızca test emülatörünü bağlayıp çalıştırın:

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
```

Testler `studio.birlevo.android` uygulamasındaki proje verilerini sıfırlar. Kişisel proje bulunan cihazlarda çalıştırmayın. Gradle bağlı test tamamlanınca uygulama ve test paketini kaldırır; kaydedilen test ekranları da silinir. Ekran görüntülerini saklamak için iki APK'yı `adb install -r` ile kurup `adb shell am instrument -w -r studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner` kullanın ve `/sdcard/Android/data/studio.birlevo.android/files/qa` klasörünü işlem sonunda alın. Testler `StudioDeviceTest.kt`, `ImageCompressionDeviceTest.kt`, `VideoCompressionDeviceTest.kt` ve `PresentationPdfDeviceTest.kt` içindedir. Kontrollü sağlayıcılar ve video örnekleri yalnızca test APK'sına dahildir. Video örneklerini yeniden üretmek için `imageio-ffmpeg` bulunan bir Python ortamında `python tools/testing/generate_video_fixtures.py` çalıştırılabilir; normal derleme bunu gerektirmez.

## Dil kaynakları

`tools/localization/catalog.json` başlangıç metinlerinin kaynağıdır. Android kaynak dosyaları repoya dahil edilir. Katalog değişince üretin ve kontrol edin:

```powershell
python tools/localization/generate.py
python tools/localization/generate.py --check
```

Kontrol çevirinin dilsel doğruluğunu değil, 22 dil kümesini, anahtar tamlığını ve biçim yer tutucularını doğrular.

Endonezce kullanıcıya `id` etiketiyle sunulur; Android kaynaklarında ve sistem dil listesinde uyumlu eski `in` takma adı kullanılır.

## Sonraki somut adım

Üç temel görev Android 16 emülatöründe gerçek sistem dosya seçicisiyle tamamlandı; rapor [GERCEK_DOSYA_SECICI_TEST_RAPORU.md](GERCEK_DOSYA_SECICI_TEST_RAPORU.md) içindedir. 0.1.0 test paketi kullanıcı tarafından Play Console'a yüklendi; gizlilik politikası yayımlandı ve İngilizce mağaza görselleri hazırlandı. Dağıtımın açıldığı henüz doğrulanmadı. 0.1.1 (2) için sunumda otomatik kayıt ve slayt yönetimi tamamlandı; yeni paket ayrı tutulur ve henüz Play'e gönderilmedi. Testler, paket ve sınırlar [SUNUM_OTOMATIK_KAYIT_TEST_RAPORU.md](SUNUM_OTOMATIK_KAYIT_TEST_RAPORU.md) içindedir. 0.1.2 (3) ile üç tema, üç görsel yerleşimi ve gerçek slayt önizlemesi tamamlandı. 14/14 birim ve 35/35 telefon emülatörü testi, tablet ve dar ekran tasarım kontrolleri geçti. Güncel paket ve sınırlar [SUNUM_TEMA_DUZEN_TEST_RAPORU.md](SUNUM_TEMA_DUZEN_TEST_RAPORU.md) içindedir. Sonraki geliştirme araçlar arası çıktı aktarımı ve ilk format genişlemesidir; testçi hataları önceliklidir. Destek e-postası gerçek teslim kontrolü ve fiziksel telefon testi bekliyor. Bir haftalık kapsam ve devam sırası [KAPALI_TEST_PLANI.md](KAPALI_TEST_PLANI.md) içindedir. Çok klipli zaman çizelgesi, geniş format okuyucu / dönüştürücü ve reklam SDK'sı sonraki aşamalardır.

Teknik referanslar: [AGP uyumluluğu](https://developer.android.com/build/releases/agp-8-12-0-release-notes), [Kotlin / Gradle uyumluluğu](https://kotlinlang.org/docs/gradle-configure-project.html), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [uygulama dili](https://developer.android.com/guide/topics/resources/app-languages).
