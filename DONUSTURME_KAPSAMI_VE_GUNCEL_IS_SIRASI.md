# Birlevo — dönüşüm kapsamı ve güncel iş sırası

9 Ekim güncel sıra: [PPTX uyumluluk/ticari lisans denemesi](PPTX_UYUMLULUK_VE_LISANS_DENEMESI.md) tamamlandı. Üretimde PPTX desteği henüz açılmadı. Sıradaki iş mevcut sunumların **görünümünü koruyan PPTX dışa aktarımı**, çözünürlük/not seçimi ve açık düzenlenebilirlik kaybıyla üretim ekranına bağlamaktır. Düzenlenebilir yolda ortak yerleşim/RTL/font/Office kontrolü; genel import için öğe modeli/kayıp önizlemesi bekler. Yeni SDK/yayın paketi yok; aşağıdaki sıra kayıtları tarihseldir.

7 Ekim 2026. Kullanıcı hedefi: geniş dosya çeşitliliği, seçilebilir çıktı biçimleri ve bir araçta üretilen içeriğin diğerinde kullanılabilmesi. Bu belge geliştirme planıdır; aşağıdaki adaylar henüz uygulama desteği veya yayın taahhüdü değildir. Bu geliştirme tamamlanırken kullanıcı yeni sürüm istedi; 0.1.9 (10) imzalı yayın adayı hazırlandı ve doğrulandı; Play’e yüklenmedi. Sonraki her geliştirme için otomatik sürüm üretme kuralı değişmez.

Son durum: TXT / MD / CSV / TSV, video / fotoğraf PDF kaynak / ayar kaydı ve bu iki aracın kullanıcı başlatmalı arka plan üretimi geliştirme kodundadır; [metin / tablo yolları](METIN_TABLO_OKUYUCU_VE_DONUSTURME.md), [medya oturumları](MEDYA_OTURUM_DAYANIKLILIGI.md), [servis / kalıcı sonuç / kesinti](ARKA_PLAN_ISLEM_YONETIMI.md). 3. sıranın ilk ses paketi eklendi: ayrı ses dosyası / videodan ses çıkarma, WAV PCM16 ve M4A AAC, doğrudan video sonucu aktarımı. [Kapsam](SES_ARACLARI_VE_CIKTI_BICIMLERI.md). BMP görsel hedefi ve PDF sayfası JPEG / WebP hedefleri de geliştirme koduna eklendi; [kapsam](GORSEL_VE_PDF_CIKTI_HEDEFLERI.md). HEVC MP4 ve VP8 / Opus WebM çıktı hedefleri cihaz kontrolü, kalıcı oturum, gerçek çıktı doğrulaması ve ortak aktarımıyla uygulama motoruna eklendi; [güncel kapsam](VIDEO_CIKTI_HEDEFLERI.md). 0.1.9 R8 kontrolü tamamlandı; fiziksel cihaz / gerçek düşük depolama matrisi bekler. DOCX / XLSX ayrı teknik deneme bekler; destek listesine eklenmez.

7 Ekim ses devamı: OGG / Opus ve FLAC çıktı hedefleri eklendi. OGG açıkça 48 kHz ve seçilen bit hızında çalışır; FLAC çözülmüş PCM16'yı ek kayıpsız kodlar. Cihaz / ayar denetimi, gerçek kapsayıcı, oynatma, kayıt / paylaşım ve büyük kopya onayı birlikte doğrulanır. [Kapsam](SES_EK_CIKTI_HEDEFLERI.md). Bu devam için yeni yayın paketi oluşturulmadı; dondurulmuş 0.1.9 değişmez.

## Tamamlanan temel

7 Ekim teknik devamı: HEVC / WebM denemesi ve uygulama entegrasyonu tamamlandı. Hedef seçimi, kaynak ve ayara göre cihaz desteği, 22 dilde kayıp açıklaması, eski oturum varsayılanı, doğru uzantı / MIME ve videodan ses aktarımı birlikte çalışır. [Kanıt ve sınırlar](VIDEO_CIKTI_HEDEFLERI.md). Önceki 0.1.8 paketi değişmedi; bu yetenekler yeni 0.1.9 paketindedir. [Paket / notlar / Console kılavuzu](YAYIN_0.1.9.md).

Geliştirme kodunda JPEG / PNG / BMP / durağan WebP / HEIC / HEIF / AVIF görsel girdileri; MP4 / MOV / 3GP / MKV / WebM video girdileri; PDF okuyucu ve sayfa araçları vardır. HEIF / AVIF ve video desteği cihaz çözücüsüne ve akış profiline bağlıdır. Video H.264 / HEVC 8 bit SDR / VP8 ve isteğe bağlı AAC / Opus / Vorbis / MP3 sesi kabul eder. Video motorunun ses desteğinden ayrı olarak `audio/` aracı MP3 / M4A / AAC / WAV PCM16 / OGG Vorbis veya Opus / FLAC ve videonun tek ses parçasını WAV / M4A / OGG / FLAC / MP3 hedefine işleyebilir; cihaz desteği gereklidir.

Görsel çıktıları JPEG / PNG / kayıplı WebP / BMP; video MP4 / H.264 veya HEVC / AAC ve WebM / VP8 / Opus; sunum ve fotoğraf belgesi PDF; PDF sayfası PNG / JPEG / kayıplı WebP'dir. Bir dosyayı açabilmek o biçimde çıktı yazabilmek değildir. HEIF / AVIF girişi eklenirken HEIF / AVIF çıktı eklenmemiştir. [Çalışan kapsam ve testler](TELEFON_MEDYA_BICIMLERI_VE_SADE_BASLIK.md).

## Dönüşüm hedefi

Akış: **Dosya ekle → hedef biçimi seç → gerekli ayarları yap → önizle → yeni dosyayı kaydet / sonraki araçta kullan.** Kullanıcı motor veya codec seçmek zorunda kalmaz; gelişmiş seçeneklerde desteklenen ayrıntılar açılabilir. Hedef seçimi mevcut sıkıştırma ve düzenleme seçeneklerini kaldırmaz.

Destek, sadece uzantı listesiyle tutulmaz. Her giriş → çıktı yolu için okuma, çözme, gerekli ara içerik, kodlama / yazma, cihaz koşulu, beklenen özellik kaybı ve gerçek test sonucu kaydedilir. MIME / uzantı gerçek içeriğin yerine geçmez. Aynı video uzantısı farklı codec / bit derinliği / ses içerdiğinde sonuç değişebilir. Android de kodlama ve çözmeyi ayrı listeler. [Android biçim desteği](https://developer.android.com/media/platform/supported-formats).

Her kaynak → her hedef çifti için içerik eşdeğerliği yoktur. Aileler arası yollar ayrı işlemlerdir: videodan ses çıkarmak, görsel dizisini video yapmak, PDF sayfasını resmetmek, metni konuşmaya çevirmek. PDF'den DOCX'e yapı kurma veya taramadan metin çıkarma geri kazanım işlemidir; kayıpsız orijinal belge dönüşümü olarak sunulmaz. PNG'den JPEG'e saydamlık, animasyondan durağan görsele hareket, XLSX'ten CSV'ye formül / biçim / çoklu sayfa kayıpları önceden açıklanır.

## İş sırası

8 Ekim arayüz önceliği tamamlandı: dosya odaklı ana sayfa, türü belirli kaydedilebilir gezinme, ortak alt/yan panel ve sonuç düzeni bütün araçlara bağlandı. Biçimler, kalite, çözünürlükler, katmanlar, aktarım ve büyük kopya korumaları korunur. [Uygulama ve doğrulama](DOSYA_ODAKLI_ARAYUZ.md). Ardından görsel/slaytlardan zamanlamalı MP4 üretimi de tamamlandı; [kapsam ve doğrulama](GORSEL_SLAYT_VIDEO.md). Sonraki yeni ürün işi tam ekran sunum modu, not/zamanlayıcı ve slayt görseli çıktısıdır; kritik testçi hataları önce çözülür. Yeni yayın derlemesi kullanıcı isteğini bekler.

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

8 Ekim güncel devam: video karesini özgün klipten veya işlenmiş sonuçtan seçip görsel araçlarına aktarım tamamlandı. [Kapsam ve doğrulama](VIDEO_KARE_AKTARIMI.md). **Görsel / slaytlardan zamanlamalı MP4** de tamamlandı; [kapsam ve doğrulama](GORSEL_SLAYT_VIDEO.md). Sıradaki somut ürün işi **tam ekran sunum modu, notlar / zamanlayıcı ve slayt görseli çıktısı**; aşağıdaki paragraf önceki sıra kaydıdır. Yeni release oluşturulmadı.

Ses aracı WAV / M4A / OGG / FLAC / MP3 çıktılarıyla ve iki kaynaklı ses kesme / ardışık birleştirmeyle tamamlandı; BMP görsel, PDF sayfası → JPEG / WebP ve HEVC / WebM entegrasyonu da tamamlandı. [Ses kesme / birleştirme](SES_KESME_VE_BIRLESTIRME.md) sıra / zaman aralığı, görünür örnekleme / kanal ayarı, gerçek içerik / süre, iptal, kalıcı kayıt ve büyük kopya onayıyla doğrulanır. İki video klibini ayrı kesme / sıralama / ardışık birleştirme temeli de tamamlandı; ses / sığdırma / kalıcı oturum / iptal ve boyut koruması birlikte doğrulanır. [Kapsam](VIDEO_KLIP_BIRLESTIRME.md). Seçilebilir kadraj ve hız kontrolü geliştirme kodunda tamamlandı; [kapsam ve doğrulama](VIDEO_KADRAJ_VE_HIZ.md). Tek yazı katmanı geliştirme kodunda tamamlandı; [kapsam ve doğrulama](VIDEO_YAZI_KATMANI.md). Tek görsel / logo katmanı da geliştirme kodunda tamamlandı; [kapsam ve doğrulama](VIDEO_GORSEL_KATMANI.md). Kullanıcı sesi miksajı da tamamlandı; [kapsam ve doğrulama](VIDEO_SES_MIKSAJI.md). Sıradaki somut ürün işi video karesinden görsel aktarımı, ardından görsel / slaytlardan MP4 üretimidir. Daha fazla ses kaynağı ve ortak zaman çizelgesi sonrasında genişletilebilir. DOCX / XLSX ayrıca küçük uyumluluk örnekleri bekler; tam Office sadakati sözü verilmez. Fiziksel cihaz / düşük depolama bakım işleri ve her yeni bağımlılıkta ticari lisans incelemesi sürer.


## MP3 ve ticari lisans devamı — 7 Ekim

MP3 yazma denemesi ve entegrasyonu tamamlandı: LAME 4.0 CBR, gerçek ayar / frame doğrulaması, iptal, kalıcı sonuç, oynatma, doğru MIME ve büyük kopya onayı. [Kapsam ve sınırlar](MP3_CIKTI_HEDEFI.md). Reklam gelirli ticari dağıtım için 95 ayrı runtime artifact ve yeni native encoder incelendi; eksik Bouncy Castle bildirimi giderildi, çevrimdışı LGPL kaynak kaydı ve CI kapsam kontrolü eklendi. [Lisans kaydı](TICARI_LISANS_ENVANTERI.md).

3. sıranın ses çıktı hedefleri WAV / M4A / OGG / FLAC / MP3 olarak tamamlandı. Yeni sürüm üretilmedi; 0.1.9 dosyaları değişmedi. Sonraki geliştirmede iki kaynaklı ses kesme / birleştirme de tamamlandı: sıra / zaman aralığı seçimi, gerçek süre / PCM içerik doğrulaması, seçilen aralığı oynatma, iptal ve oturum / büyük kopya onayı. [Kapsam ve sınırlar](SES_KESME_VE_BIRLESTIRME.md). İki video klibini ayrı kesme / sıralama / birleştirme temeli de tamamlandı; [kanıt ve sınırlar](VIDEO_KLIP_BIRLESTIRME.md). Seçilebilir kadraj (9:16 dahil) ve hız kontrolü eklendi; sonraki ürün adımı yazı / kullanıcı sesi / görsel katmanlarıdır. Yeni biçim / codec, font ve reklam SDK’larında ticari lisans incelemesi sürer.


## Video ve Play / site bakım devamı — 7 Ekim

İki klip, iki yön / üç mevcut çıktı, ses / sessizlik, gerçek boyut koruması ve atomik restorasyon geliştirme kodunda tamamlandı. [Teknik kayıt](VIDEO_KLIP_BIRLESTIRME.md). 0.1.9 kaynaklı sistem çubuğu uyarılarının çalışma yolu düzeltildi; sonraki R8 / Play teyidi bekliyor. Aynı paket kimliğine göre native sembol doğrulaması yayın yardımcısına eklendi; eski graphics-path paketinin sembolleri upstream olmadan geri üretilemez. Site ve iki gizlilik metni 0.1.9 kapsamıyla canlıya yayımlandı. Kullanıcının tercih ettiği mavi üst şerit ve arka plansız favicon eklendi. [Bakım durumu](PLAY_UYARILARI_VE_SITE_BAKIMI.md). Yeni sürüm numarası veya imzalı APK / AAB üretilmedi.

## 0.1.10 yayın paketi — 7 Ekim

Kullanıcının sonraki isteğiyle 0.1.10 (11) üretildi. İki kaynaklı ses / video, MP3 / OGG / FLAC, kadraj / hız, R8, çevrimdışı LGPL kaynak kaydı / kütüphane değiştirme ve son pakete uygun native semboller doğrulandı. [Yayın kaydı](YAYIN_0.1.10.md). Fiziksel cihaz ve Console teyidi ayrı bekler. Sıradaki ürün işi yazı / kullanıcı sesi / görsel katmanları olarak kalır; bu yayın işleminde katman veya ek biçim geliştirilmedi.

## Yazı katmanı — 8 Ekim

Tek yazı, dokuz konum, marka renklerini içeren beş renk, boyut ve global çıktı zamanında görünme aralığı eklendi. Üç çıktı biçimi, hız / klip sınırı, özgün dosyalar, gerçek kayıt baytları, V1–V3 uyumu / V4 restorasyon, iptal ve bitmap önbelleği doğrulandı. [Kapsam ve kanıt](VIDEO_YAZI_KATMANI.md). Yeni yayın paketi üretilmedi; dondurulmuş 0.1.10 dosyaları değişmedi. Sırada görsel katmanı, ardından kullanıcı sesiyle miks vardır.

## Görsel / logo katmanı — 8 Ekim

Tek özel görsel kaynağı, dokuz konum, boyut, saydamlık ve global çıktı zamanında görünme aralığı eklendi. Yazıyla birlikte çalışır; EXIF / alfa, gerçek üç biçim / iki hız çıktıları, iptal, özgün kaynaklar, V5 kayıt ve başarısız atomik değiştirme / kaldırma sonrası yeniden deneme doğrulandı. [Kapsam ve kanıt](VIDEO_GORSEL_KATMANI.md). Yeni sürüm üretilmedi; dondurulmuş 0.1.10 değişmedi. Sıradaki ürün işi kullanıcı sesiyle mikstir.

## Kullanıcı sesi miksajı — 8 Ekim

Tek özel ses kaynağı, kaynak kesme, nihai videoda başlangıç, ek ses / video sesi düzeyleri ve sessiz videoya ses eklendi. 48 kHz mono/stereo miksaj, video sonunda kesilme ve sabit ek ses hızı / perdesi açıkça gösterilir. V6 / V1–V5 uyumu, atomik kaynak emekliliği, gerçek üç çıktı / iki hız ses düzeyleri ve özgün dosyalar doğrulandı. [Kapsam ve kanıt](VIDEO_SES_MIKSAJI.md). Yeni yayın paketi yok; dondurulmuş 0.1.10 aynı kalır. Sıradaki ürün işi video karesinden görsel aktarımı, ardından görsel / slaytlardan MP4'tür.
