# Birlevo — dönüşüm kapsamı ve güncel iş sırası

7 Ekim 2026. Kullanıcı hedefi: geniş dosya çeşitliliği, seçilebilir çıktı biçimleri ve bir araçta üretilen içeriğin diğerinde kullanılabilmesi. Bu belge geliştirme planıdır; aşağıdaki adaylar henüz uygulama desteği veya yayın taahhüdü değildir. Bu geliştirme tamamlanırken kullanıcı yeni sürüm istedi; 0.1.9 (10) imzalı yayın adayı hazırlandı ve doğrulandı; Play’e yüklenmedi. Sonraki her geliştirme için otomatik sürüm üretme kuralı değişmez.

Son durum: TXT / MD / CSV / TSV, video / fotoğraf PDF kaynak / ayar kaydı ve bu iki aracın kullanıcı başlatmalı arka plan üretimi geliştirme kodundadır; [metin / tablo yolları](METIN_TABLO_OKUYUCU_VE_DONUSTURME.md), [medya oturumları](MEDYA_OTURUM_DAYANIKLILIGI.md), [servis / kalıcı sonuç / kesinti](ARKA_PLAN_ISLEM_YONETIMI.md). 3. sıranın ilk ses paketi eklendi: ayrı ses dosyası / videodan ses çıkarma, WAV PCM16 ve M4A AAC, doğrudan video sonucu aktarımı. [Kapsam](SES_ARACLARI_VE_CIKTI_BICIMLERI.md). BMP görsel hedefi ve PDF sayfası JPEG / WebP hedefleri de geliştirme koduna eklendi; [kapsam](GORSEL_VE_PDF_CIKTI_HEDEFLERI.md). HEVC MP4 ve VP8 / Opus WebM çıktı hedefleri cihaz kontrolü, kalıcı oturum, gerçek çıktı doğrulaması ve ortak aktarımıyla uygulama motoruna eklendi; [güncel kapsam](VIDEO_CIKTI_HEDEFLERI.md). 0.1.9 R8 kontrolü tamamlandı; fiziksel cihaz / gerçek düşük depolama matrisi bekler. DOCX / XLSX ayrı teknik deneme bekler; destek listesine eklenmez.

7 Ekim ses devamı: OGG / Opus ve FLAC çıktı hedefleri eklendi. OGG açıkça 48 kHz ve seçilen bit hızında çalışır; FLAC çözülmüş PCM16'yı ek kayıpsız kodlar. Cihaz / ayar denetimi, gerçek kapsayıcı, oynatma, kayıt / paylaşım ve büyük kopya onayı birlikte doğrulanır. [Kapsam](SES_EK_CIKTI_HEDEFLERI.md). Bu devam için yeni yayın paketi oluşturulmadı; dondurulmuş 0.1.9 değişmez.

## Tamamlanan temel

7 Ekim teknik devamı: HEVC / WebM denemesi ve uygulama entegrasyonu tamamlandı. Hedef seçimi, kaynak ve ayara göre cihaz desteği, 22 dilde kayıp açıklaması, eski oturum varsayılanı, doğru uzantı / MIME ve videodan ses aktarımı birlikte çalışır. [Kanıt ve sınırlar](VIDEO_CIKTI_HEDEFLERI.md). Önceki 0.1.8 paketi değişmedi; bu yetenekler yeni 0.1.9 paketindedir. [Paket / notlar / Console kılavuzu](YAYIN_0.1.9.md).

Geliştirme kodunda JPEG / PNG / BMP / durağan WebP / HEIC / HEIF / AVIF görsel girdileri; MP4 / MOV / 3GP / MKV / WebM video girdileri; PDF okuyucu ve sayfa araçları vardır. HEIF / AVIF ve video desteği cihaz çözücüsüne ve akış profiline bağlıdır. Video H.264 / HEVC 8 bit SDR / VP8 ve isteğe bağlı AAC / Opus / Vorbis / MP3 sesi kabul eder. Video motorunun ses desteğinden ayrı olarak `audio/` aracı MP3 / M4A / AAC / WAV PCM16 / OGG Vorbis veya Opus / FLAC ve videonun tek ses parçasını WAV / M4A hedefine işleyebilir; cihaz desteği gereklidir.

Görsel çıktıları JPEG / PNG / kayıplı WebP / BMP; video MP4 / H.264 veya HEVC / AAC ve WebM / VP8 / Opus; sunum ve fotoğraf belgesi PDF; PDF sayfası PNG / JPEG / kayıplı WebP'dir. Bir dosyayı açabilmek o biçimde çıktı yazabilmek değildir. HEIF / AVIF girişi eklenirken HEIF / AVIF çıktı eklenmemiştir. [Çalışan kapsam ve testler](TELEFON_MEDYA_BICIMLERI_VE_SADE_BASLIK.md).

## Dönüşüm hedefi

Akış: **Dosya ekle → hedef biçimi seç → gerekli ayarları yap → önizle → yeni dosyayı kaydet / sonraki araçta kullan.** Kullanıcı motor veya codec seçmek zorunda kalmaz; gelişmiş seçeneklerde desteklenen ayrıntılar açılabilir. Hedef seçimi mevcut sıkıştırma ve düzenleme seçeneklerini kaldırmaz.

Destek, sadece uzantı listesiyle tutulmaz. Her giriş → çıktı yolu için okuma, çözme, gerekli ara içerik, kodlama / yazma, cihaz koşulu, beklenen özellik kaybı ve gerçek test sonucu kaydedilir. MIME / uzantı gerçek içeriğin yerine geçmez. Aynı video uzantısı farklı codec / bit derinliği / ses içerdiğinde sonuç değişebilir. Android de kodlama ve çözmeyi ayrı listeler. [Android biçim desteği](https://developer.android.com/media/platform/supported-formats).

Her kaynak → her hedef çifti için içerik eşdeğerliği yoktur. Aileler arası yollar ayrı işlemlerdir: videodan ses çıkarmak, görsel dizisini video yapmak, PDF sayfasını resmetmek, metni konuşmaya çevirmek. PDF'den DOCX'e yapı kurma veya taramadan metin çıkarma geri kazanım işlemidir; kayıpsız orijinal belge dönüşümü olarak sunulmaz. PNG'den JPEG'e saydamlık, animasyondan durağan görsele hareket, XLSX'ten CSV'ye formül / biçim / çoklu sayfa kayıpları önceden açıklanır.

## İş sırası

Kritik testçi hataları her aşamada önce giderilir. Aşağıdaki sıra yeni ürün geliştirmesidir; reklam ve yayın kontrolleri hazır pakete ayrıca uygulanır.

| Sıra | İş | Somut tamamlanma ölçütü |
| --- | --- | --- |
| 1 | Belge / tablo okuyucusu ve ortak hedef seçimi | TXT / Markdown kaynak metni ve CSV / TSV, geçerli TXT / CSV / TSV / PDF hedefleriyle tamamlandı. Kodlama, ayırıcı, tırnak, satır sonu, Unicode, çok sayfalı PDF ve sunum aktarımı sınandı. Biçimli Markdown ve DOCX / XLSX ayrı teknik deneme bekliyor. |
| 2 | Oturum ve uzun işlem dayanıklılığı | Video / fotoğraf PDF oturumları, kullanıcı başlatmalı ön plan servisi, tek ağır iş sırası, bildirim iptali / süre sınırı ve atomik sonuç / iş durumu eklendi. Tamamlanmış çıktı geri yüklenir; zorla durdurulmuş iş açık yeniden başlatma bekler. 0.1.9 R8 kontrolü tamamlandı; fiziksel cihaz / gerçek dolu disk matrisi bekliyor. Sunumun eski taslak uyumluluğu sürer. |
| 3 | Çıktı biçimleri, ses aracı ve ek codec'ler | Ses aracı, oynatma, videodan ses çıkarma, WAV PCM16 / M4A AAC / OGG Opus / FLAC PCM16 ve özel video sonucu aktarımı eklendi. BMP görsel çıktısı ve PDF sayfası PNG / JPEG / WebP hedefleri eklendi. MP3 yazma ayrı encoder / boyut / lisans denemesi bekler. HEVC MP4 ve WebM VP8 / Opus eklendi; cihaz ve ayar kontrolü gerekir. VP9 / AV1 girdi / çıktı ayrı profil ve encoder denemesi bekler. |
| 4 | Çok klipli video ve ortak varlıklar | İki klipten birleştirme, bölme / sıralama, 9:16 kadraj, hız; sonra yazı / kullanıcı sesi / görsel katmanları. Video karesinden görsel ve görsel / slaytlardan MP4 yolları. Önizleme / çıktı süre, kadraj, ses ve katman eşleşmesi. |
| 5 | Sunum modu ve sunum biçimleri | Tam ekran akış, notlar / zamanlayıcı; slayt görseli ve zamanlamalı video. PPTX içe / dışa ayrı denemede yazı, yerleşim ve düzenlenebilirlikle doğrulanır; animasyon desteği ayrı tutulur. |
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

WAV / M4A ve videodan ses çıkarma ilk ses paketinde doğrulandı; kullanıcı ses çıktı genişlemesinden devam etmeyi seçti ve OGG / Opus ile FLAC eklendi. BMP görsel hedefi, PDF sayfası → JPEG / WebP ve HEVC / WebM entegrasyonu tamamlandı. Kalan biçim işi MP3 yazma için ayrı encoder / boyut / lisans denemesidir; Android'in MP3 okuyabilmesi yazma desteği değildir. Sonraki ürün paketi iki klip birleştirme, sıralama ve kırpma ile başlayacak çok klipli video temelidir; ses / kadraj / kalıcı proje ve iptal doğrulaması birlikte hazırlanır. DOCX / XLSX ayrıca küçük uyumluluk örnekleri bekler; tam Office sadakati sözü verilmez. Fiziksel cihaz / düşük depolama bakım işleri geliştirme sırasında sürer.
