# Birlevo — ses araçları ve çıktı biçimleri

7 Ekim devamı: bu belge ilk WAV / M4A paketinin tarihsel kapsamıdır. Güncel ses çıktıları OGG / Opus ve FLAC'ı da içerir; [ek hedefler ve doğrulama](SES_EK_CIKTI_HEDEFLERI.md). İlk ses paketi 0.1.9'a alındı; OGG / FLAC devamı dondurulmuş 0.1.9 APK / AAB'de değildir ve yeni sürüm oluşturulmamıştır.

6 Ekim 2026. Bu kayıt geliştirme kodunu anlatır; dondurulmuş 0.1.8 (9) APK / AAB değişmedi. İş sırasının 3. maddesinin ilk ses paketi tamamlanır; görsel ek hedefleri, ek video encoder / muxer ve Office motorları ayrı iş olarak kalır.

## Kullanıcı akışı

Ana ekran → Ses araçları → ses veya video seç → WAV / M4A seç → gerçek sonucu üret ve dinle → yeni dosyaya kaydet / aç / paylaş. M4A için 64 / 128 / 256 kbps seçilebilir. Düzenlenmiş videonun sonucu da doğrudan ses aracına aktarılır; kaynak videonun tamamı yerine gerçekten üretilen kesilmiş dosya kullanılır.

| Giriş | Koşul | Çıktı |
| --- | --- | --- |
| MP3, M4A / AAC, ADTS AAC, WAV PCM16, OGG Vorbis / Opus, FLAC | Gerçek içerikte bir mono / stereo ses parçası; gerekli cihaz çözücüsü | WAV PCM16 / M4A AAC |
| MP4 / MOV / 3GP / MKV / WebM içindeki desteklenen ses | Tek ses parçası; video çözülmez. Sessiz veya çok ses parçalı dosya açık hata verir | WAV PCM16 / M4A AAC |
| İşlenmiş video sonucu | Geçerli MP4 çıktısı, ses parçası; büyük kopya ise sonuca özel onay | Ses aracına baytları değiştirmeden özel kaynak kopyası |

Uzantı / dosya seçici MIME bilgisi başarı garantisi değildir. MediaExtractor gerçek parçayı, örnekleme hızı / kanal / süreyi doğrular; MediaCodecList cihaz desteğini denetler. Giriş: 512 MiB, on dakika, mono / stereo, 8–192 kHz. Bu üst sınır her cihazın her örnekleme hızında AAC kodlayabildiği iddiası değildir. M4A kodlamasında fallback kapalıdır; örnekleme / kanal sayısı kendiliğinden düşürülmez. WAV dışa aktarımı 512 MiB sınırındadır. Sonuç süre / örnekleme / kanal / parça yapısı yeniden okunur; codec padding için küçük süre toleransı bulunur.

WAV çözülen 16 bit PCM'dir; sıkıştırılmış kaynağa göre büyüyebilir, kaybolmuş ayrıntıyı geri getirmez. M4A kayıplı AAC'dir; seçilen bitrate kaynak AAC olsa da gerçekten yeniden kodlanır. Meta veriler, kapak, altyazı ve diğer parçalar aktarılmaz. Bu kayıplar hedef seçiminde açıklanır. WAV dışındaki yüksek bit derinlikli çözülmüş sesin 16 bit çıktısı kayıpsız kaynak eşdeğerliği olarak sunulmaz. MP3 / OGG / FLAC yazma, ses kesme / karıştırma ve zaman çizelgesine kullanıcı müziği ekleme bu pakette yoktur.

## Mimari / kaynak koruma

`audio/AudioPolicy` saf hedef / sınır / RIFF başlık kodudur. `AudioEngine` IO'da gerçek içeriği inceler, PCM'yi MediaCodec ile sınırlı tamponlardan WAV'a yazar; M4A mevcut Media3 1.5.1 Transformer ve cihaz AAC encoder'ıyla üretilir. Yeni native motor, Gradle bağımlılığı veya INTERNET izni yoktur. FFmpeg yalnız bilgisayarda yapay test dosyası üretir; uygulamaya eklenmez.

`AudioSession` yalnız özel kaynak referansı / hedef / bitrate kaydeder; ortak tek yazıcı ve atomik metadata kullanılır. `AudioToolViewModel` işlem durumunu koordine eder; `AudioToolScreen` tembel ve kimliği korunan Compose öğelerini gösterir. Ses ViewModel'i ana ekranda gereksiz oluşturulmaz. Oynatıcı durdurulan ekranda duraklar, çıkışta serbest bırakılır ve oynatma hatası gösterilir.

Ses dönüşümü mevcut `ProcessingJobs` tek ağır iş sırasını ve kullanıcı başlatmalı ön plan servisini kullanır. Activity kapanması / ekranın kapanması işlemi iptal etmez; bildirimden veya ekrandan iptal mümkündür. Kaynak ve ayarlar atomik kaydedilir; sonuç ancak iş kaydı kalıcı olduğunda görünür. Yeni owner tamamlanmış çıktıyı geri yükler; yarım kalan iş otomatik başlatılmaz. Büyük kopya onayı kalıcı kayda yazılmaz. İptal / hata kısmi WAV / M4A dosyasını temizler. Motorun yirmi dakikalık işlem ve decoder'ın otuz saniyelik ilerlemesizlik sınırı vardır.

Yeni araç kaynak URI'sini yazmak için açmaz. Kaydetme / açma / paylaşma gerçek kaynak / çıktı baytlarını karşılaştırır; eşit veya büyük sonuç normal kayıt düğmesinden çıkmaz, o sonuca özel açık kopya onayı gerekir. Ortak FileProvider yetkisi ve kök adları korunmuştur. Video → ses aktarımı yeniden kodlama yapmaz; sesin kendi özel kaynağı üretici videonun temizlenmesinden bağımsızdır.

## Doğrulama ve açık ölçümler

Ayrıntılı yerel kanıtlar `output/development/audio-tools/qa-summary.json` ve aynı dizindeki loglardadır. Test APK'sı yedi özgün ses kapsayıcısında her iki hedefi sınar: toplam 14 ses giriş → hedef yolu; ayrıca gerçek videodan WAV / M4A çıkarma, AAC bitrate farkı, iptal dosya temizliği, kaynak koruma / büyük kopya onayı, gerçek oynatıcı hazırlığı, Activity yeniden oluşturma / yeni owner sonuç geri yükleme, uzun ses işinin Activity ve ekran kapatıldıktan sonra bitmesi ve kesilmiş video sonucunun özel ses kaynağına kayıpsız aktarımı kontrol edilir. Eski video / arka plan / ortak çıktı akışları da tekrar kontrol edilir.

22 dilde 11 yeni anahtar ve placeholder / kaynak tutarlılığı doğrulanır; çeviriler dil uzmanı tarafından onaylanmış sayılmaz. API 36 yazılım GPU emülatörü fiziksel düşük donanım / eski Android / üretici codec / gerçek dolu disk / pil garantisi değildir. Release R8 / imzalı APK ve Play bildirimleri sonraki kullanıcı yayın isteğinde doğrulanır. Canlı Jev çağrısı yapılmaz; yalnız çevrimdışı araç testleri ve APK izolasyonu kullanılır.

Son yerel çalıştırma: 50 birim testi ve 32 hedefli Android testi geçti. Aynı ses aracı UI testi ayrıca tablet (1600×2560, 240 dpi), dar ekran / büyük yazı (720×1280, 320 dpi, font 1.4) ve koyu modda tekrar geçti; bunlar üç yeni benzersiz test olarak sayılmaz. Ekran görüntüleri incelendi. Lint: sıfır hata, 39 uyarı. Jev aracının 20 çevrimdışı testi ve debug APK izolasyon kontrolü geçti. Graphify AST güncellemesi tamamlandı; 19 dosyada kısmi ayrıştırma uyarısı var, Kotlin derlemesi başarılıdır. Yayın numarası / imzalı paket değiştirilmedi.
