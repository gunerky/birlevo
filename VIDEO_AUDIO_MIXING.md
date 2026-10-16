# Videoya kullanıcı sesi / miksaj — 8 Ekim 2026

Geliştirme koduna tek ek ses katmanı eklendi. Yeni sürüm numarası veya imzalı APK / AAB oluşturulmadı; dondurulmuş 0.1.10 (11) dosyaları aynı kalır. Yeni kodun release R8 / yayın doğrulaması sonraki kullanıcı yayın isteğine bağlıdır.

## Kullanım

Video aracında **Eklenen ses → Ses veya video seç** ile tek mono/stereo ses parçası olan mevcut destekli girdiler seçilir: MP3 / M4A / AAC / WAV PCM16 / OGG Vorbis veya Opus / FLAC ve destekli videoların sesi. Gerçek parça / decoder denetlenir; uzantı desteği bütün codec profilleri için garanti değildir. Kaynak sınırı 512 MiB, 10 dakika, 1–2 kanal ve 8–192 kHz'dir.

Ek sesin kendi kaynak başlangıç / bitiş aralığı ve **nihai videoda başlayacağı zaman** ayrı ayarlanır. Video kesme / sıralama / hızından sonra oluşan çıktı zamanı kullanılır. Ek sesin hızı ve perdesi korunur; video yavaşlayınca ek ses kendiliğinden yavaşlatılmaz. Seçilen ses video sonunda kesilir; süreyi uzatmaz. Başlangıç video dışında kalırsa işlem açıklamayla engellenir. Tek oynatım vardır; döngü, fade, çoklu ek ses ve ortak zaman çizelgesi bu adımda eklenmedi.

**Eklenen ses düzeyi** ve **video sesi düzeyi** bağımsız %0–100 doğrusal kazançtır. İlk ikisi %50'dir. Video sesi %0 ise özgün ses işlemden çıkarılır; ek ses yerini alabilir. İkisi yüksekken toplam ses kırpılabilir / bozulabilir; arayüz bunu açıklar. Otomatik normalizasyon, gizli ses kısma veya perde değiştirme uygulanmaz.

Miksaj profili arayüzde gösterilir: **48 kHz mono/stereo**. Mono gerekirse iki kanala aynı seviyede çoğaltılır; mevcut stereo tutulur. Video sesini koruyan surround miksajı, gizli downmix yapılmadan açıklamayla engellenir. Video sesini tamamen kapatınca surround kaynak ek mono/stereo sesle kullanılabilir. Ek ses olmayan önceki kanal / örnekleme yolları korunur. Video kalite, çözünürlük, tuval, hız, yazı / logo katmanları ve üç çıktı biçimi aynı kalır.

**Videoyu işle → çıktıyı oynat** gerçek miksaj önizlemesidir. Kaydetme aynı dosyayı bayt bayt kopyalar; ikinci kodlama yoktur. Kaynak oynatıcı ek sesin karıştığı sonuç değildir. Mikrofondan kayıt veya ağ servisi kullanılmaz.

## Mimari ve korumalar

- `VideoAudioLayer`: sınırlandırılmış değişmez varlık, kaynak aralığı, çıktı başlangıcı ve iki kazanç. V6 JSON / iş kimliği bu ayarların tamamını içerir. Ek ses olmayan V1–V5 istek baytları aynı kalır; eski işler ve katmanlar yeniden açılır.
- `VideoAudioAssets`: video oturumunun `sound-*.media` özel dosyasına tam kaynak kopyası. Ses aracının çalışma / paylaşım cache'i kullanılmaz. Atomik tek yazıcı ses değiştirme / kaldırma / sıfırlamadan sonra eski varlığı ancak başarılı metadata kaydıyla siler. Görsel ve ses emeklilik kümeleri ayrıdır; biri değiştirilince diğeri korunur.
- Media3 1.5.1'de video dizisi ve bağımsız kesilmiş ses dizisi birlikte karışır. Başlangıç boşluğu mikro saniye cinsinden kurulur; son sessizlik video süresiyle tamamlanır. Ek ses için hız efekti yoktur. Video sesi mevcut kesme / hız / perde yolundan geçer. [Kullanılan sürümün dizi / boşluk API'si](https://github.com/androidx/media/blob/1.5.1/libraries/transformer/src/main/java/androidx/media3/transformer/EditedMediaItemSequence.java), [kompozisyon ve zorlanmış ses](https://github.com/androidx/media/blob/1.5.1/libraries/transformer/src/main/java/androidx/media3/transformer/Composition.java).
- `AudioGainProcessor` PCM16 bloklarına düzey uygular; boş buffer güvenlidir. Dosyanın tamamı belleğe alınmaz; ayrı tam uzunluklu WAV ara dosyası üretilmez. Media3 decode / mixer tamponları kullanılır. Ek parça eşzamanlı bir decoder daha gerektirebilir; fiziksel cihaz maliyeti henüz ölçülmedi.
- Hatalı ses seçimi önceki ses / çıktı / ayarları korur. Aktif özel kaynak yaşa göre cache temizliğinden korunur. Yeni ViewModel özel kopyayı ve bitmiş sonucu geri açar; dış URI tekrar okunmaz.
- Video, logo ve ek sesin özgün URI'larına kayıt engellenir. Sıkıştırma başarısı yalnız özgün video baytlarıyla karşılaştırılır; ek ses baytları toplamı yapay tasarruf iddiası yaratmaz. Eşit / daha büyük çıktıda sonuca özel açık kopya onayı sürer.
- Yeni SDK, codec, font, ağ izni veya Jev bağlantısı yoktur. Mevcut 95 runtime artifact / native encoder için ticari lisans hash kapsamı kontrol edildi. Reklam SDK'sı bu adımda eklenmedi.

## Doğrulama ve açık işler

80 JVM testi: V6 yalnız ses ve bütün katmanlarla round-trip, V1–V5 uyumu, her düzey / aralık / kaynak değişiminde iş kimliği, hatalı ayar ve PCM kazanç / boş buffer.

API 36 özel QA emülatöründe üç motor ve bir UI testi: iki kesilmiş video klibi, 0,5× / 2×, AVC / HEVC MP4 ve VP8 WebM ile altı miksaj; video sesi yerine yeni ses ve sessiz videoya gecikmeli ses için iki ek çıktı. Sekiz dosya bağımsız olarak çözüldü. 440 / 660 Hz video sesi ile 880 Hz ek ses, giriş/çıkış aralığı, ayrı gerçek kazanç, perde, kanal / örnekleme, süre ve video / yazı kareleri ölçüldü. Sessiz aralıkta PCM enerji kontrolü ve kapatılmış video sesinin yokluğu sınandı.

Kaynak / kaydedilen çıktı bayt eşitliği, seçili özgün URI'ların korunması, geçersiz zaman ve surround'u koruma / kapatma uygunluk politikası, iptal, hatalı ses sonrası eski sonuç, yaşlı kaynakla yeni ViewModel, atomik disk hatası sonrası değiştirme / kaldırma / yeniden deneme ve bağımsız görsel varlığının korunması test edildi. UI kaynak kesme, çıktı başlangıcı, iki düzey, gerçek dosya oynatımı, restorasyon ve kaldırmayı içerir; tablet / dar ekran iki kat yazı / koyu modda tekrarlandı. Önceki görsel katmanı / UI, yazı çıktıları ve katmansız atomik video restorasyonu regresyonla denetlendi.

Debug lint 0 hata / 42 uyarı; 22 dil kaynak ve placeholder denetimi geçti. Dil kontrolü tüm dillerde insan incelemesi değildir. `tools/testing/video-audio-qa.ps1` yalnız `ofice_qa` emülatörlerini kabul eder. Yerel raporlar `output/development/video-audio/` altında ve Git dışındadır. Graphify yerel AST grafiği güncellendi; kısmi Kotlin çıkarımı kaynak / gerçek testin yerini almaz.

Fiziksel ARM / düşük RAM, eski API / üretici decoder matrisi, uzun yüksek çözünürlüklü miksaj, gerçek dolu disk, pil / termal ölçümler ve yeni kodun release R8 doğrulaması bekler. Emülatörden fiziksel performans garantisi çıkarılmaz. Sonraki ürün adımı video karesinden görsel aktarımı, ardından görsel / slaytlardan MP4'tür.
