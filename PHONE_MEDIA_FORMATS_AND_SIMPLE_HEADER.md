# Birlevo — telefon medya biçimleri ve sade başlık

6 Ekim 2026, geliştirme kodu. Sürüm numarası değişmedi; release APK / AAB hazırlanmadı. Play'e gönderilen eski paket bu değişiklikleri içermez.

## Görünüm

Ana ekran başlığı arka planla aynı renktedir. Son kullanıcı düzeltmesine göre büyük lacivert tanıtım kartı korunur; içindeki Görseller / Video / Slaytlar simgelerine eklenen ayrı mavi kutular kaldırıldı. Şeffaf kısayollar en az 48 dp dokunma alanıyla çalışır. Mercan Birlevo yazısı ve simgeler korunur. Dil seçimi zemin / çerçeve olmadan metin düğmesidir; 22 dilli seçim penceresi korunur. Yedi ana araç kartının da çalışması yerleşim testinde denetlenir. Mevcut araç kartları, tanıtım kartı, kapak görseli ve uygulama logosunun zemini değiştirilmedi.

## Gerçek giriş / çıktı desteği

| İşlem | Yeni girişler | Çıktı / koşul |
| --- | --- | --- |
| Görsel dönüştürme ve sıkıştırma | Durağan HEIC / HEIF / AVIF | Mevcut JPEG / PNG / kayıplı WebP; kalite seçenekleri korunur |
| Sunum görseli | Durağan HEIC / HEIF / AVIF | Özel proje varlığına orijinal baytlar kopyalanır, slaytta ve sunum PDF'sinde kullanılır |
| Fotoğraflardan PDF | Durağan HEIC / HEIF / AVIF | Mevcut 2048 / 4096 seçeneği, A4 yönü ve sayfa başına çizim |
| Video kesme / sıkıştırma | MOV / 3GP / MKV / WebM; MP4'te HEVC 8 bit SDR | Desteklenen video: H.264 / HEVC 8 bit SDR / VP8; isteğe bağlı AAC / Opus / Vorbis / MP3 sesi. Çıktı MP4, H.264 ve varsa AAC |

Dosya uzantısı ve sağlayıcının bildirdiği MIME tek başına kabul sebebi değildir. Görselde en çok 4096 baytlık `ftyp` kutusu incelenir, boyutlar platform çözücüsünden okunur. Video akışları Android `MediaExtractor` ile, kullanılabilir çözücü `MediaCodecList` ile denetlenir. Dönüştürme sırasında codec başarısızlığı yine olabilir; içe alma denetimi işlem başarısı garantisi vermez.

HEIC / HEIF için bu uygulamanın yeni yolu Android 9+, AVIF için Android 12+ ve uyumlu platform çözücüsü ister. Eski Android'de mevcut JPEG / PNG / WebP işlemleri sürer. Android'in AVIF platform zorunluluğu Android 14'ten itibaren geçerlidir; daha eski uygun sürümlerde her cihazda AVIF sözü verilmez. [Android biçim desteği](https://developer.android.com/media/platform/supported-formats), [ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder) ve [Media3 giriş / çıktı koşulları](https://developer.android.com/media/media3/transformer/supported-formats).

HEIF / AVIF ana durağan görüntüsü standart platform yönüyle çözümlenir. HEIF'in `irot` dönüşümü tekrar EXIF ile uygulanmaz. Yalnızca açıklayıcı EXIF yönü içeren, HEIF dönüşüm bilgisi içermeyen bir dosyada HEIF standardının platform davranışı izlenir. [HEIF yön ayrımı](https://pillow-heif.readthedocs.io/en/latest/workaround-orientation.html). HEIF / AVIF dizileri reddedilir; ana görsel dışındaki yardımcı görüntüler, derinlik, HDR bilgileri ve metadata çıktı dosyasında korunmaz. Bu koşullar 22 dilde uygulamanın destek yardımında gösterilir.

HEVC başlangıç verisi mevcut Media3 SPS ayrıştırıcısıyla en çok 128 KiB okunur; 10 bit / farklı chroma veya doğrulanamayan SPS reddedilir. PQ / HLG, AVC'de olduğu gibi metadata eksik olsa da SPS'den denetlenir. HDR, VP9 / AV1, MPEG-4 Part 2, destek dışı ses, altyazı ve ek akışlar henüz desteklenmez; sessizce atılmaz. HEVC ve VP8 girişini eklemek bütün MP4 / MOV / WebM dosyalarının açılacağı anlamına gelmez.

## Kaynak, kalite ve bellek

Yeni girdi biçimleri çıktı listesinden ayrıdır; HEIF / AVIF çıktı eklenmedi. Kaynak dosyalar salt okunur alınır, proje / fotoğraf PDF kopyaları tekrar kodlanmaz. 100 MP / 128 MiB görsel, 512 MiB / 10 dakika / 4096 kenar / 8,8 MP / 60 fps video sınırları korunur. Önizleme 512 px; modern görsel decode doğrudan gereken boyuta yapılır, yazılım bitmap'i ve sRGB kullanılır. Görsel aracı 1280 / 1920 / 2560, fotoğraf PDF 2048 / 4096, video 640 / 1280 / 1920 seçeneklerini korur. Büyük nihai çıktı önizlemeden bağımsızdır; kalite otomatik düşürülmez.

Gerçek kaynak / çıktı boyutu kuralı değişmedi. Eşit veya büyük görsel / video normal sıkıştırma kaydıyla dışarı verilemez; aynı sonuca özel açık kopya onayı gerekir. Yeni PDF oluşturmak sıkıştırma olarak sayılmaz.

`ModernImages` ortak doğrulama / platform decoder katmanıdır; mevcut JPEG / PNG / WebP çözme yolu korunur. Yeni native kütüphane / ağ / Android bağımlılığı eklenmedi. `pillow-heif` ve güncel Pillow yalnız bu bilgisayarda yapay test dosyası üretimi için `output/development/phone-media/host-tools` altındadır; Android bağımlılığı değildir. Üretim betiği `tools/testing/generate_phone_media_fixtures.py`, örnekler yalnız test APK'sının assets bölümündedir. Jev API çağrılmadı; kaynak ve debug APK izolasyon denetimi geçti.

## Doğrulama

- 33 / 33 birim testi; debug uygulama / test APK derlemesi başarılı. Lint: 0 hata, 38 uyarı. Yeni bir `Uri.parse` için KTX stil önerisi eklendi; mevcut kullanılmayan Bouncy Castle TLS sınıfları dahil uyarılar gizlenmedi. Şeffaf tanıtım kısayollarının kontrast testi gerçek gradyan / dekoratif katmanı kullanır.
- Yeni 4 Android testi gerçek HEIC, AVIF ve `irot` yönlü HEIC'i üç çıktı biçiminde; baytları değişmeden iki PDF aracında; yedi video örneğini MP4 / H.264 / AAC'ye dönüştürerek doğruladı. Kaynak baytları, ses varlığı, gerçek çıktı codec'i, boyut onayı ve başarısız içe alma temizliği kontrol edildi.
- İlk yeni testte yanlış 720 değeri, uygulamanın mevcut 640 / 1280 / 1920 sözleşmesine uymuyordu. HEIF yön örneği de başlangıçta yalnız EXIF içeriyordu. Test 640'a ve gerçek `irot` örneğine düzeltildi; sonraki dört test geçti. Bu başarısız ilk koşu ölçüm dosyasında korunur.
- Medya girişleri tamamlandığında mevcut medya, PDF üretimi ve 22 dil yardımını içeren 44 / 44 Android gerileme testi geçti. Ardından modern görselin kayıp uyarısı eklendi; dört gerçek medya testi ve arayüzün telefon / tablet / dar büyük yazı / koyu / Arapça yerleşim kontrolünü içeren 25 / 25 test geçti.
- Son kullanıcı düzeltmesinde tanıtım kartı korunup ayrı mavi simge kutuları kaldırıldı. Son uygulama APK'sında telefon, tablet, dar ekran / 2× yazı ve koyu modda altışar; Arapça yerleşimde bir test olmak üzere 25 / 25 test geçti. Yedi ana araç kartı, üç şeffaf tanıtım kısayolu, dil seçimi, eski video taslağı ve modern görsel uyarısı denetlendi. Arapça ana ekran görüntüsü ilk iki yakalamada yükleme karesiydi; test yakalayıcısına API 29+ render kare tamamlama beklemesi eklendi. Yalnız test APK'sı yeniden derlenerek Arapça tek test tekrar geçti, ana ekran ve iki araç görüntüsü gözle doğrulandı. Uygulama APK'sı değişmedi.
- Koyu moddaki altı test 238 saniyede geçti; bu gecikme sırasında emülatörün `com.google.android.apps.nexuslauncher` sürecinde `HardwareRenderer` finalizer zaman aşımı kaydedildi. Gecikmenin kök nedeni kesinleştirilmedi; bu koşu düşük donanım hız ölçümü olarak yorumlanmaz. İlk yakalama hataları ve teşhis kayıtları saklanır. Ara tasarım denemesindeki eksik import / unit renk sabiti derleme hataları son başarılı derleme öncesinde düzeltildi.
- Jev'in 20 / 20 çevrimdışı aracı testi ve Android kaynak / APK izolasyon kontrolü geçti; canlı API çağrısı yapılmadı. Sayılar, aşamalar, loglar ve değişmeyen release AAB özeti `output/development/phone-media/qa-summary.json` içine kaydedildi. Yayın paketi üretilmedi.

Graphify yerel AST güncellemesi tamamlandı; Kotlin parser'ın kısmi çıkarım uyarıları kaynak ve Android derleyicisiyle ayrıştırılır. Fiziksel düşük donanım / eski API / üretici HEIC çeşitleri / HDR görsel renk doğruluğu ölçülmüş sayılmaz. Aynı API 36 QA emülatörünün ekran ve tema ayarlarıyla yapılan yerleşim testi ayrı fiziksel cihaz testi değildir.

Sonraki iş: belge / tablo okuyucusu kapsamını gerçek örneklerle seçmek; ardından video ve fotoğraf PDF oturum dayanıklılığı. Tam Office uyumluluğu veya bütün dosya türleri vaat edilmez. Reklam eklenirken Play reklam kimliği, reklam, veri güvenliği ve gizlilik beyanları birlikte güncellenir.
