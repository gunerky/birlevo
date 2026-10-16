# 16 October — legacy decode and photo PDF retention

[Legacy emulator / PDF work](../development/LEGACY_ANDROID_AND_PDF_MEMORY.md) fixes sampling below the selected slide/photo edge and closes native photo-PDF pages individually, spooling encoded resources to disk. Existing formats, output choices and protected originals remain. Next: presentation PDF/slideshow resource retention, then image-heavy external PDFs near their byte ceiling. Physical-device performance and arbitrary complex PDF memory remain unmeasured; no automatic quality reduction or release build. Earlier entries are historical.

# 16 Ekim — ZIP boyut uyumu ve cihaz çalışma alanı tamamlandı

[Ortak ZIP / kapasite politikası](../development/DEVICE_CAPACITY_AND_ZIP_LIMITS.md) 512 MiB içeriği başlık payıyla yeniden açar; çalışma sınırını cihazdaki gerçek boş alana göre daraltır, alan azalmasını yazma sırasında kontrol eder. 143 JVM / 10 emülatör testi ve büyük WAV zinciri / host çözümü geçti. **Sıradaki ürün işi görsel, video ve PDF işlemlerinde çözülmüş bellek / geçici disk ihtiyacını işlem bazında değerlendirmek**; son çözünürlük ve kaliteyi sessizce azaltmamak. Yeni yayın yalnız kullanıcı isteğinde. Aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — zincir doğrulaması tamamlandı

[Toplu dönüştürme → ZIP → klasör](../features/batch/BATCH_ZIP_FOLDER_FLOW_AND_REGRESSION.md) 19 hedefte gerçek dosya/hash/bağımsız okuyucu kontrolleriyle doğrulandı; RTL dosya adları düzeltildi. [Yeni kapalı test listesi](../testing/CURRENT_CLOSED_TEST_CHECKLIST.md) geliştirme/yayın ayrımını korur. **Sıradaki ürün işi 512 MiB toplu çıktı ve 128 MiB/dosya ZIP okuyucu sınırlarını güvenli biçimde uyumlu hale getirmektir.** Kalite otomatik düşürülmez. Yeni release yalnız kullanıcı isteğinde; aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — ZIP çoklu çıkarma

[Klasöre güvenli çıkarma](../features/archives/ZIP_MULTI_SELECTION_AND_FOLDER_EXTRACTION.md) seçili dosyalar için tamamlandı: yeni SAF alt klasörü, korunan yollar/baytlar, ad/kimlik çakışması kontrolü, iptal ve kısmi hedef bildirimi. Tek dosyalı kayıt ve mevcut ZIP/medya sınırları değişmez. **Sıradaki iş toplu dönüştürme → ZIP → klasör zincirinin ortak emülatör regresyonu ve kapalı test kontrol listesidir.** Yeni release yalnız kullanıcı isteğinde. Aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — belge toplu tarifleri

[Toplu belge paketi](../features/batch/BATCH_DOCUMENTS_AND_APPROVED_RECIPES.md) uygun metin/tablo/Office alt kümesini TXT/CSV/TSV/PDF/DOCX/XLSX olarak ayrı dosyalara dönüştürür. Sayfa seçimi ve kayıp onayı açık; eşit/büyük sonuç ayrıca gerçek boyutla onaylanır. Hedef uyumsuzluğu dosyaya özgü hata verir, içerik kesilmez veya başka biçime otomatik dönülmez. Eski görsel/video/ses kayıtları korunur. **Sıradaki ürün işi ZIP çoklu seçim/klasöre çıkarma akışıdır.** Yeni release yalnız kullanıcı isteğinde. Aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — ZIP açma ve dosya çıkarma

[Güvenli ZIP okuyucu](../features/archives/ZIP_READING_AND_SAFE_EXTRACTION.md) tamamlanan arşivleri dosya başına, yeniden kodlamadan kaydeder. ZIP32 stored/deflate, 1000 öğe, 512 MiB kaynak/toplam içerik, 128 MiB dosya ve 8 MiB merkez dizin sınırı vardır. Şifreli, ZIP64, bölünmüş ve özel kayıtlar desteklenmez. Mevcut medya/Office akışları ve çıktı onayları korunur. **Sonraki iş belge toplu tariflerinin kayıp/uyumluluk değerlendirmesidir.** Klasöre çoklu çıkarma ayrı sağlayıcı işlem sözleşmesi ister. Yeni release yalnız kullanıcı isterse hazırlanır. Aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — video/ses toplu tarifleri

[Ortak toplu kuyruk](../features/batch/BATCH_VIDEO_AUDIO_AND_SHARED_QUEUE.md) görsel, video ve sesi ayrı kalıcı listelerde işler. Video dört mevcut çıktı biçimi ve üç kalite/boyut seçeneğini; ses beş biçim ve kayıplı hedeflerde üç bit hızını korur. Tam dosyalar ayrı dönüştürülür; düzenleme/birleştirme araçlarının yetenekleri değişmez. Kaynak, dosya başına kopya onayı ve ZIP inceleme kuralları sürer. **Sonraki iş güvenli ZIP açma/çıkarma yoludur; belge toplu tarifleri ayrı kayıp/uyumluluk değerlendirmesi ister.** Yeni sürüm yalnız kullanıcı isteğinde. Aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — toplu görsel ve ZIP

[Toplu görsel/ZIP ilk paketi](../features/batch/BATCH_IMAGES_AND_ZIP.md): JPEG/PNG/WebP/BMP, 20 dosya, ayrı 512 MiB kaynak/çıktı sınırları, mevcut kalite/çözünürlükler, sıralı arka plan işi, dosya başına onay ve atomik devam kaydı eklendi. ZIP paketleme, sıkıştırma başarısı iddiası taşımaz. **Sıradaki iş video/ses toplu tariflerini genişletmek; ardından güvenli ZIP açma ve belge tariflerini değerlendirmektir.** Yeni sürüm yalnız kullanıcı isterse derlenir. Ayrıntılı test ve lisans kapsamı bağlantılı rapordadır; aşağıdaki kayıtlar tarihseldir.

# 15 Ekim — codec matrisi ve VP9

[Codec matrisi ve VP9 paketi](../features/CODEC_MATRIX_AND_VP9_SUPPORT.md) tamamlandı: VP9 Profile 0 / 8-bit SDR girişi, VP9/Opus WebM çıktısı, ortak kare-codec kaydı, oturum/kopya onayı ve 22 dil. Yeni SDK veya gömülü codec yoktur; ticari telif/envanter ile patent kapsamı ayrı kaydedildi. 109 JVM ve 16 emülatör testi, 7 VP9 / 8 katmanlı dosya ve 14 karenin bağımsız çözümü geçti. AV1/APV aday envanteri desteğe eşit sayılmaz. **Sıradaki ürün işi toplu dönüştürme / ZIP akışıdır.** Yeni release yalnız kullanıcı isteğinde; aşağıdaki kayıtlar tarihseldir.

# 14 Ekim — DOCX/XLSX belge ekranı

[Sınırlı DOCX/XLSX desteği](../features/documents/DOCX_XLSX_UI_INTEGRATION.md) uygulamaya bağlandı: metin/tablo okuma, çalışma sayfası ve formül/kayıtlı sonuç seçimi, sade DOCX ve metin hücreli XLSX çıktı, açık kayıp onayı, kaynak/oturum koruması ve 22 dil. Formül hesaplaması veya tam Office görünüm uyumluluğu iddia edilmez. Yeni SDK/font yok; mevcut dosya türü, kalite ve çözünürlük yolları korunur. Yeni release yalnız kullanıcı isteğinde. Sıradaki kapsam codec adaylarını ticari koşullar ve cihaz desteğiyle değerlendirmek, ardından toplu/ZIP akışını ele almaktır. Kritik testçi hataları önceliklidir. Aşağıdaki kayıtlar tarihseldir.

# 14 Ekim — DOCX XLSX motor denemesi sonrası sıra

[DOCX/XLSX teknik ve ticari lisans denemesi](../features/documents/DOCX_XLSX_ENGINE_AND_LICENSE_REVIEW.md) tamamlandı: yerel paragraf/tablo ve hücre/sayfa okuma, açık kayıp raporu, formül–kayıtlı sonuç ayrımı, basit DOCX ve metin XLSX yazımı test kodunda doğrulandı. Yeni SDK/font yok; Office MIME desteği üretime açılmadı. Sonraki somut iş **seçilen alt kümeyi belge ekranına, sayfa seçimine ve açık kayıp/çıktı tercihlerine bağlamak**; oturum, iptal/kayıt, kaynak koruması ve 22 dil birlikte tamamlanacak. Gerçek Word sayfa görünümü ve Excel/Office renderer uyumluluğu kanıtlanmadı. Yeni release yalnız kullanıcı istediğinde. Aşağıdaki kayıtlar tarihseldir.

# Birlevo — dönüşüm kapsamı ve güncel iş sırası

9 Ekim yeni devam: Birlevo sunumu → görünümü koruyan PPTX üretim entegrasyonu tamamlandı; her slayt tek PNG, metin/nesneler ayrı düzenlenemez ve metin aranamaz. 1024/2048/4096, opt-in konuşmacı notları, gerçek önizleme/boyut, kalıcı işlem, iptal/kayıt/açma/paylaşma vardır. [Kapsam ve kanıt](../features/presentations/PPTX_APPEARANCE_PRESERVING_EXPORT.md). PPTX giriş veya genel Office dönüşümü ilan edilmez; diğer araçların kapsam/kalite/çözünürlükleri korunur. **Sırada Office/RTL/font kontrolü ve düzenlenebilir PPTX için ölçülmüş alt küme** vardır. Yeni release yok. Aşağıdaki kayıtlar önceki aşamalardır.

9 Ekim güncel sıra: [PPTX uyumluluk/ticari lisans denemesi](../features/presentations/PPTX_COMPATIBILITY_AND_LICENSE_REVIEW.md) tamamlandı. Üretimde PPTX desteği henüz açılmadı. Sıradaki iş mevcut sunumların **görünümünü koruyan PPTX dışa aktarımı**, çözünürlük/not seçimi ve açık düzenlenebilirlik kaybıyla üretim ekranına bağlamaktır. Düzenlenebilir yolda ortak yerleşim/RTL/font/Office kontrolü; genel import için öğe modeli/kayıp önizlemesi bekler. Yeni SDK/yayın paketi yok; aşağıdaki sıra kayıtları tarihseldir.

7 Ekim 2026. Kullanıcı hedefi: geniş dosya çeşitliliği, seçilebilir çıktı biçimleri ve bir araçta üretilen içeriğin diğerinde kullanılabilmesi. Bu belge geliştirme planıdır; aşağıdaki adaylar henüz uygulama desteği veya yayın taahhüdü değildir. Bu geliştirme tamamlanırken kullanıcı yeni sürüm istedi; 0.1.9 (10) imzalı yayın adayı hazırlandı ve doğrulandı; Play’e yüklenmedi. Sonraki her geliştirme için otomatik sürüm üretme kuralı değişmez.

Son durum: TXT / MD / CSV / TSV, video / fotoğraf PDF kaynak / ayar kaydı ve bu iki aracın kullanıcı başlatmalı arka plan üretimi geliştirme kodundadır; [metin / tablo yolları](../features/documents/TEXT_TABLE_READER_AND_CONVERSION.md), [medya oturumları](../development/MEDIA_SESSION_RESILIENCE.md), [servis / kalıcı sonuç / kesinti](../development/BACKGROUND_PROCESSING.md). 3. sıranın ilk ses paketi eklendi: ayrı ses dosyası / videodan ses çıkarma, WAV PCM16 ve M4A AAC, doğrudan video sonucu aktarımı. [Kapsam](../features/audio/AUDIO_TOOLS_AND_OUTPUT_FORMATS.md). BMP görsel hedefi ve PDF sayfası JPEG / WebP hedefleri de geliştirme koduna eklendi; [kapsam](../features/images/IMAGE_AND_PDF_OUTPUT_TARGETS.md). HEVC MP4 ve VP8 / Opus WebM çıktı hedefleri cihaz kontrolü, kalıcı oturum, gerçek çıktı doğrulaması ve ortak aktarımıyla uygulama motoruna eklendi; [güncel kapsam](../features/video/VIDEO_OUTPUT_TARGETS.md). 0.1.9 R8 kontrolü tamamlandı; fiziksel cihaz / gerçek düşük depolama matrisi bekler. DOCX / XLSX ayrı teknik deneme bekler; destek listesine eklenmez.

7 Ekim ses devamı: OGG / Opus ve FLAC çıktı hedefleri eklendi. OGG açıkça 48 kHz ve seçilen bit hızında çalışır; FLAC çözülmüş PCM16'yı ek kayıpsız kodlar. Cihaz / ayar denetimi, gerçek kapsayıcı, oynatma, kayıt / paylaşım ve büyük kopya onayı birlikte doğrulanır. [Kapsam](../features/audio/ADDITIONAL_AUDIO_OUTPUT_TARGETS.md). Bu devam için yeni yayın paketi oluşturulmadı; dondurulmuş 0.1.9 değişmez.

## Tamamlanan temel

7 Ekim teknik devamı: HEVC / WebM denemesi ve uygulama entegrasyonu tamamlandı. Hedef seçimi, kaynak ve ayara göre cihaz desteği, 22 dilde kayıp açıklaması, eski oturum varsayılanı, doğru uzantı / MIME ve videodan ses aktarımı birlikte çalışır. [Kanıt ve sınırlar](../features/video/VIDEO_OUTPUT_TARGETS.md). Önceki 0.1.8 paketi değişmedi; bu yetenekler yeni 0.1.9 paketindedir. [Paket / notlar / Console kılavuzu](../releases/RELEASE_0.1.9.md).

Geliştirme kodunda JPEG / PNG / BMP / durağan WebP / HEIC / HEIF / AVIF görsel girdileri; MP4 / MOV / 3GP / MKV / WebM video girdileri; PDF okuyucu ve sayfa araçları vardır. HEIF / AVIF ve video desteği cihaz çözücüsüne ve akış profiline bağlıdır. Video H.264 / HEVC 8 bit SDR / VP8 ve isteğe bağlı AAC / Opus / Vorbis / MP3 sesi kabul eder. Video motorunun ses desteğinden ayrı olarak `audio/` aracı MP3 / M4A / AAC / WAV PCM16 / OGG Vorbis veya Opus / FLAC ve videonun tek ses parçasını WAV / M4A / OGG / FLAC / MP3 hedefine işleyebilir; cihaz desteği gereklidir.

Görsel çıktıları JPEG / PNG / kayıplı WebP / BMP; video MP4 / H.264 veya HEVC / AAC ve WebM / VP8 / Opus; sunum ve fotoğraf belgesi PDF; PDF sayfası PNG / JPEG / kayıplı WebP'dir. Bir dosyayı açabilmek o biçimde çıktı yazabilmek değildir. HEIF / AVIF girişi eklenirken HEIF / AVIF çıktı eklenmemiştir. [Çalışan kapsam ve testler](../design/PHONE_MEDIA_FORMATS_AND_SIMPLE_HEADER.md).

## Dönüşüm hedefi

Akış: **Dosya ekle → hedef biçimi seç → gerekli ayarları yap → önizle → yeni dosyayı kaydet / sonraki araçta kullan.** Kullanıcı motor veya codec seçmek zorunda kalmaz; gelişmiş seçeneklerde desteklenen ayrıntılar açılabilir. Hedef seçimi mevcut sıkıştırma ve düzenleme seçeneklerini kaldırmaz.

Destek, sadece uzantı listesiyle tutulmaz. Her giriş → çıktı yolu için okuma, çözme, gerekli ara içerik, kodlama / yazma, cihaz koşulu, beklenen özellik kaybı ve gerçek test sonucu kaydedilir. MIME / uzantı gerçek içeriğin yerine geçmez. Aynı video uzantısı farklı codec / bit derinliği / ses içerdiğinde sonuç değişebilir. Android de kodlama ve çözmeyi ayrı listeler. [Android biçim desteği](https://developer.android.com/media/platform/supported-formats).

Her kaynak → her hedef çifti için içerik eşdeğerliği yoktur. Aileler arası yollar ayrı işlemlerdir: videodan ses çıkarmak, görsel dizisini video yapmak, PDF sayfasını resmetmek, metni konuşmaya çevirmek. PDF'den DOCX'e yapı kurma veya taramadan metin çıkarma geri kazanım işlemidir; kayıpsız orijinal belge dönüşümü olarak sunulmaz. PNG'den JPEG'e saydamlık, animasyondan durağan görsele hareket, XLSX'ten CSV'ye formül / biçim / çoklu sayfa kayıpları önceden açıklanır.

## İş sırası

8 Ekim arayüz önceliği tamamlandı: dosya odaklı ana sayfa, türü belirli kaydedilebilir gezinme, ortak alt/yan panel ve sonuç düzeni bütün araçlara bağlandı. Biçimler, kalite, çözünürlükler, katmanlar, aktarım ve büyük kopya korumaları korunur. [Uygulama ve doğrulama](../design/FILE_FOCUSED_INTERFACE.md). Ardından görsel/slaytlardan zamanlamalı MP4 üretimi de tamamlandı; [kapsam ve doğrulama](../features/presentations/IMAGE_AND_SLIDE_VIDEO.md). Sonraki yeni ürün işi tam ekran sunum modu, not/zamanlayıcı ve slayt görseli çıktısıdır; kritik testçi hataları önce çözülür. Yeni yayın derlemesi kullanıcı isteğini bekler.

Kritik testçi hataları her aşamada önce giderilir. Aşağıdaki sıra yeni ürün geliştirmesidir; reklam ve yayın kontrolleri hazır pakete ayrıca uygulanır.

| Sıra | İş | Somut tamamlanma ölçütü |
| --- | --- | --- |
| 1 | Belge / tablo okuyucusu ve ortak hedef seçimi | TXT / Markdown kaynak metni ve CSV / TSV, geçerli TXT / CSV / TSV / PDF hedefleriyle tamamlandı. Kodlama, ayırıcı, tırnak, satır sonu, Unicode, çok sayfalı PDF ve sunum aktarımı sınandı. Biçimli Markdown ve DOCX / XLSX ayrı teknik deneme bekliyor. |
| 2 | Oturum ve uzun işlem dayanıklılığı | Video / fotoğraf PDF oturumları, kullanıcı başlatmalı ön plan servisi, tek ağır iş sırası, bildirim iptali / süre sınırı ve atomik sonuç / iş durumu eklendi. Tamamlanmış çıktı geri yüklenir; zorla durdurulmuş iş açık yeniden başlatma bekler. 0.1.9 R8 kontrolü tamamlandı; fiziksel cihaz / gerçek dolu disk matrisi bekliyor. Sunumun eski taslak uyumluluğu sürer. |
| 3 | Çıktı biçimleri, ses aracı ve ek codec'ler | Ses aracı, oynatma, videodan ses çıkarma, WAV PCM16 / M4A AAC / OGG Opus / FLAC PCM16 / MP3 CBR ve özel video sonucu aktarımı eklendi. BMP görsel çıktısı ve PDF sayfası PNG / JPEG / WebP hedefleri eklendi. MP3 yazma encoder / boyut / ticari lisans denemesi ve entegrasyonuyla tamamlandı. HEVC MP4 ve WebM VP8 / Opus eklendi; cihaz ve ayar kontrolü gerekir. VP9 / AV1 girdi / çıktı ayrı profil ve encoder denemesi bekler. |
| 4 | Çok klipli video ve ortak varlıklar | İki kaynakta ayrı kesme / sıralama / ardışık birleştirme temeli tamamlandı. Seçilebilir 9:16 / 16:9 / kare kadraj ve hız eklendi. Tek yazı, görsel / logo ve kullanıcı sesi miksajı tamamlandı. Video karesinden tam çözünürlüklü PNG → görsel araçları eklendi. Görsel / slaytlardan süreleri seçilen sessiz H.264 MP4 ve video düzenleyicisine aktarımı da tamamlandı. |
| 5 | Sunum modu ve sunum biçimleri | Zamanlamalı slayt videosu tamamlandı. Sırada tam ekran akış, notlar / zamanlayıcı ve slayt görseli çıktısı var. PPTX içe / dışa ayrı denemede yazı, yerleşim ve düzenlenebilirlikle doğrulanır; animasyon desteği ayrı tutulur. |
| 6 | ZIP ve toplu tarifler | Güvenli ZIP oluşturma / açma, dosya sıralama / adlandırma, iptal / kurtarma; aynı dönüşüm yollarıyla toplu işlem. Ardından 7Z / RAR açma adayı; RAR yazma sözü verilmez. |
| 7 | OCR ve ileri içerik araçları | Önce aranabilir PDF ve seçilmiş OCR dilleri; PDF'den düzenlenebilir metin / tablo geri kazanımı. Sonra otomatik altyazı, renk / efekt, maske, anahtar kareler, arka plan kaldırma ve hareket takibi. Her yetenek kendi doğruluk / maliyet testine bağlıdır. |
| 8 | Reklamlı sürüm ve yayın doğrulaması | Uygun banner ve sınırlı doğal görev geçişi, izin yönetimi, hata sırasında temel kullanımın sürmesi; Play reklam / reklam kimliği / veri güvenliği / gizlilik birlikte güncellenir. Diller, erişilebilirlik, tablet, fiziksel düşük donanım ve release R8 kontrolleri tamamlanır. |

Dil / erişilebilirlik / performans bakımı sona bırakılmaz, her aşamada devam eder. Reklam işi temel akışlar ve geri bildirim hazır olduğunda bu sıranın daha erken bir noktasında bağımsız test paketi olarak yapılabilir; tüm ileri özelliklerin bitmesini beklemez. Büyük kopya onayı, kalite seçenekleri ve nihai çözünürlükler korunur. Sıkıştırma gerçek bayt boyutlarıyla değerlendirilir; kalite kendiliğinden düşürülmez.

## Biçim ve codec genişleme havuzu

Yaklaşık 30–40 yaygın uzantı orta vadeli aday havuzu olarak makuldür; teknik tavan veya yakın yayın sözü değildir. `.jpg` / `.jpeg` ve `.tif` / `.tiff` gibi adlar ayrı motor başarısı sayılmaz. Codec profilleri, animasyon, düzenlenebilirlik ve giriş / çıktı durumları ayrı raporlanır.

| Aile | Sonraki adaylar | Başlıca sınır |
| --- | --- | --- |
| Görsel | SVG, GIF, TIFF; HEIF / AVIF çıktı | Vektör / raster farkı; animasyon kareleri, çok sayfa, alfa, renk ve cihaz encoder desteği |
| Video | M4V, TS; AVI ayrıca motor denemesi; VP9 / AV1 ve ek çıktı profilleri; HEVC / WebM mevcut koşullu hedefler | Codec, kapsayıcı, profil, encoder ve çözünürlük uyumu; HDR / 10 bit ayrı yol ve açık kalite seçimi |
| Ses | MP3, M4A / AAC, WAV, OGG / Opus, FLAC, AMR | Her decoder için encoder bulunmaz; kanal / örnekleme / kapsayıcı / kayıplı kodlama ayrı doğrulanır |
| Belge / tablo / sunum | TXT, MD, CSV, TSV, DOCX, XLSX, PPTX, RTF; ODT / ODS / ODP daha sonra | Metin çıkarmak tam sayfa görüntülemek değildir; formül, font, tablo, çoklu sayfa ve animasyon korunumu ayrı ölçülür |
| Arşiv | ZIP; 7Z / RAR okuma daha sonra | Paketleme dönüşümden ayrı işlemdir; yol, açılma boyutu, bozuk / şifreli içerik denetimi |

Mevcut Android bağımlılığı Media3 1.5.1'dir. Güncel Media3 belgeleri MP4 dışında özel muxer yollarıyla WebM / AAC / Ogg / WAV hedeflerini de anlatır; bu, kullanılan sürümde bu hedeflerin hazır olduğu anlamına gelmez. Sürüm / motor değişikliği ve dosya doğrulaması ayrı yapılır. [Media3 çıktı koşulları](https://developer.android.com/media/media3/transformer/supported-formats).

Cihaz encoder'ı olmayan yollar için sınırlı yazılım codec / native motor teknik denemesi yapılabilir; yükleme boyutu, ABI, çalışma süresi, bellek ve pil ölçülmeden düşük donanım sözü verilmez. FFmpeg eklemek tek başına bütün formatları veya serbest dağıtımı garanti etmez; seçilen yapı ve harici kütüphaneler lisans kapsamını değiştirir. [FFmpeg lisans koşulları](https://ffmpeg.org/legal.html). Yeni native codec motoru veya ağ eklenmedi; mevcut Media3 1.5.1 muxer API bağımlılığı uygulama katmanına taşındı ve platform WebM adaptörü eklendi. Sunucuya kullanıcı dosyası gönderme varsayılan değildir; gerekirse ayrı ürün kararı ve kullanıcıya açıklanan veri akışı gerekir.

## Sonraki somut paket

8 Ekim güncel devam: video karesini özgün klipten veya işlenmiş sonuçtan seçip görsel araçlarına aktarım tamamlandı. [Kapsam ve doğrulama](../features/video/VIDEO_FRAME_HANDOFF.md). **Görsel / slaytlardan zamanlamalı MP4** de tamamlandı; [kapsam ve doğrulama](../features/presentations/IMAGE_AND_SLIDE_VIDEO.md). Sıradaki somut ürün işi **tam ekran sunum modu, notlar / zamanlayıcı ve slayt görseli çıktısı**; aşağıdaki paragraf önceki sıra kaydıdır. Yeni release oluşturulmadı.

Ses aracı WAV / M4A / OGG / FLAC / MP3 çıktılarıyla ve iki kaynaklı ses kesme / ardışık birleştirmeyle tamamlandı; BMP görsel, PDF sayfası → JPEG / WebP ve HEVC / WebM entegrasyonu da tamamlandı. [Ses kesme / birleştirme](../features/audio/AUDIO_TRIMMING_AND_JOINING.md) sıra / zaman aralığı, görünür örnekleme / kanal ayarı, gerçek içerik / süre, iptal, kalıcı kayıt ve büyük kopya onayıyla doğrulanır. İki video klibini ayrı kesme / sıralama / ardışık birleştirme temeli de tamamlandı; ses / sığdırma / kalıcı oturum / iptal ve boyut koruması birlikte doğrulanır. [Kapsam](../features/video/VIDEO_CLIP_JOINING.md). Seçilebilir kadraj ve hız kontrolü geliştirme kodunda tamamlandı; [kapsam ve doğrulama](../features/video/VIDEO_FRAMING_AND_SPEED.md). Tek yazı katmanı geliştirme kodunda tamamlandı; [kapsam ve doğrulama](../features/video/VIDEO_TEXT_OVERLAY.md). Tek görsel / logo katmanı da geliştirme kodunda tamamlandı; [kapsam ve doğrulama](../features/video/VIDEO_IMAGE_OVERLAY.md). Kullanıcı sesi miksajı da tamamlandı; [kapsam ve doğrulama](../features/video/VIDEO_AUDIO_MIXING.md). Sıradaki somut ürün işi video karesinden görsel aktarımı, ardından görsel / slaytlardan MP4 üretimidir. Daha fazla ses kaynağı ve ortak zaman çizelgesi sonrasında genişletilebilir. DOCX / XLSX ayrıca küçük uyumluluk örnekleri bekler; tam Office sadakati sözü verilmez. Fiziksel cihaz / düşük depolama bakım işleri ve her yeni bağımlılıkta ticari lisans incelemesi sürer.


## MP3 ve ticari lisans devamı — 7 Ekim

MP3 yazma denemesi ve entegrasyonu tamamlandı: LAME 4.0 CBR, gerçek ayar / frame doğrulaması, iptal, kalıcı sonuç, oynatma, doğru MIME ve büyük kopya onayı. [Kapsam ve sınırlar](../features/audio/MP3_OUTPUT_TARGET.md). Reklam gelirli ticari dağıtım için 95 ayrı runtime artifact ve yeni native encoder incelendi; eksik Bouncy Castle bildirimi giderildi, çevrimdışı LGPL kaynak kaydı ve CI kapsam kontrolü eklendi. [Lisans kaydı](../licensing/COMMERCIAL_LICENSE_INVENTORY.md).

3. sıranın ses çıktı hedefleri WAV / M4A / OGG / FLAC / MP3 olarak tamamlandı. Yeni sürüm üretilmedi; 0.1.9 dosyaları değişmedi. Sonraki geliştirmede iki kaynaklı ses kesme / birleştirme de tamamlandı: sıra / zaman aralığı seçimi, gerçek süre / PCM içerik doğrulaması, seçilen aralığı oynatma, iptal ve oturum / büyük kopya onayı. [Kapsam ve sınırlar](../features/audio/AUDIO_TRIMMING_AND_JOINING.md). İki video klibini ayrı kesme / sıralama / birleştirme temeli de tamamlandı; [kanıt ve sınırlar](../features/video/VIDEO_CLIP_JOINING.md). Seçilebilir kadraj (9:16 dahil) ve hız kontrolü eklendi; sonraki ürün adımı yazı / kullanıcı sesi / görsel katmanlarıdır. Yeni biçim / codec, font ve reklam SDK’larında ticari lisans incelemesi sürer.


## Video ve Play / site bakım devamı — 7 Ekim

İki klip, iki yön / üç mevcut çıktı, ses / sessizlik, gerçek boyut koruması ve atomik restorasyon geliştirme kodunda tamamlandı. [Teknik kayıt](../features/video/VIDEO_CLIP_JOINING.md). 0.1.9 kaynaklı sistem çubuğu uyarılarının çalışma yolu düzeltildi; sonraki R8 / Play teyidi bekliyor. Aynı paket kimliğine göre native sembol doğrulaması yayın yardımcısına eklendi; eski graphics-path paketinin sembolleri upstream olmadan geri üretilemez. Site ve iki gizlilik metni 0.1.9 kapsamıyla canlıya yayımlandı. Kullanıcının tercih ettiği mavi üst şerit ve arka plansız favicon eklendi. [Bakım durumu](../releases/PLAY_WARNINGS_AND_WEBSITE_MAINTENANCE.md). Yeni sürüm numarası veya imzalı APK / AAB üretilmedi.

## 0.1.10 yayın paketi — 7 Ekim

Kullanıcının sonraki isteğiyle 0.1.10 (11) üretildi. İki kaynaklı ses / video, MP3 / OGG / FLAC, kadraj / hız, R8, çevrimdışı LGPL kaynak kaydı / kütüphane değiştirme ve son pakete uygun native semboller doğrulandı. [Yayın kaydı](../releases/RELEASE_0.1.10.md). Fiziksel cihaz ve Console teyidi ayrı bekler. Sıradaki ürün işi yazı / kullanıcı sesi / görsel katmanları olarak kalır; bu yayın işleminde katman veya ek biçim geliştirilmedi.

## Yazı katmanı — 8 Ekim

Tek yazı, dokuz konum, marka renklerini içeren beş renk, boyut ve global çıktı zamanında görünme aralığı eklendi. Üç çıktı biçimi, hız / klip sınırı, özgün dosyalar, gerçek kayıt baytları, V1–V3 uyumu / V4 restorasyon, iptal ve bitmap önbelleği doğrulandı. [Kapsam ve kanıt](../features/video/VIDEO_TEXT_OVERLAY.md). Yeni yayın paketi üretilmedi; dondurulmuş 0.1.10 dosyaları değişmedi. Sırada görsel katmanı, ardından kullanıcı sesiyle miks vardır.

## Görsel / logo katmanı — 8 Ekim

Tek özel görsel kaynağı, dokuz konum, boyut, saydamlık ve global çıktı zamanında görünme aralığı eklendi. Yazıyla birlikte çalışır; EXIF / alfa, gerçek üç biçim / iki hız çıktıları, iptal, özgün kaynaklar, V5 kayıt ve başarısız atomik değiştirme / kaldırma sonrası yeniden deneme doğrulandı. [Kapsam ve kanıt](../features/video/VIDEO_IMAGE_OVERLAY.md). Yeni sürüm üretilmedi; dondurulmuş 0.1.10 değişmedi. Sıradaki ürün işi kullanıcı sesiyle mikstir.

## Kullanıcı sesi miksajı — 8 Ekim

Tek özel ses kaynağı, kaynak kesme, nihai videoda başlangıç, ek ses / video sesi düzeyleri ve sessiz videoya ses eklendi. 48 kHz mono/stereo miksaj, video sonunda kesilme ve sabit ek ses hızı / perdesi açıkça gösterilir. V6 / V1–V5 uyumu, atomik kaynak emekliliği, gerçek üç çıktı / iki hız ses düzeyleri ve özgün dosyalar doğrulandı. [Kapsam ve kanıt](../features/video/VIDEO_AUDIO_MIXING.md). Yeni yayın paketi yok; dondurulmuş 0.1.10 aynı kalır. Sıradaki ürün işi video karesinden görsel aktarımı, ardından görsel / slaytlardan MP4'tür.
