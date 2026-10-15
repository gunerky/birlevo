# Toplu video/ses ve ortak kuyruk — 15 Ekim 2026

Görseller için hazırlanan toplu dönüştürme/ZIP yolu video ve sese genişletildi. Ana sayfada tek **Toplu işlemler** düğmesi üç ayrı liste açar: görsel, video ve ses. Kaydı olan listeler **Devam et** altında kendi türüyle gösterilir; ayrı ayrı temizlenebilir. Uygulama sürümü 0.1.11 (12) olarak korunur; bu adımda yalnız debug derleme ve emülatör doğrulaması yapılır.

## Tarifler ve korunan seçenekler

| Kuyruk | Çıktılar | Ayarlar |
|---|---|---|
| Görsel | JPEG, PNG, WebP, BMP | Önceki 40–95 kalite ve 1280/1920/2560 uzun kenar seçenekleri |
| Video | H.264/AAC MP4, HEVC/AAC MP4, VP8/Opus WebM, VP9/Opus WebM | Küçük/dengeli/yüksek kalite; 640/1280/1920 uzun kenar; mevcut cihaz/SDR/codec kontrolleri |
| Ses | WAV PCM16, M4A AAC, OGG Opus, FLAC, MP3 | Kayıplı biçimlerde 64/128/256 kbps; kaynak örnekleme hızı/kanal sayısı korunur, OGG mevcut açık 48 kHz dönüşümünü kullanır |

Her dosya **tam süresiyle ayrı** dönüştürülür; otomatik birleştirme, kesme veya kullanıcı adına kalite düşürme yapılmaz. Düzenleme/katman/kesme/birleştirme araçları mevcut tek çalışma ekranlarında kalır; bu paket onların yeteneklerini kaldırmaz. Ses listesine desteklenen videolar da seçilerek ses çıkarılabilir. Çıktı desteği gerçek dosya ve cihaz kodlayıcı/çözücüsüyle doğrulanır; listede bir hedef bulunması her cihazda çalışacağı sözü değildir.

Liste başına en fazla 20 dosya ve toplam 512 MiB kaynak vardır. Video/ses için mevcut dosya başına 512 MiB / 10 dakika kontrolleri; görseller için mevcut 128 MiB / 100 MP kontrolleri korunur. Her listenin toplam çıktı payload’ı da 512 MiB ile sınırlıdır. Kaynak, sonuçlar ve ZIP diskte birlikte bulunur; ZIP başlıkları ek yer kullanır. Sınır aşımı kaliteyi veya çözünürlüğü sessizce değiştirerek giderilmez.

Her satırda gerçek kaynak/çıktı baytları, uygun kaynak boyutları veya süre ve dosyaya özgü hata gösterilir. Cihazın MP3 için kaynak hızını desteklememesi veya WebM’in 180° dönüş işaretini koruyamaması diğer satırların dönüştürülmesini engellemez; başarısız satıra alternatif biçim sessizce uygulanmaz. Aynı ayarlarla yeniden deneme yalnız sonucu eksik satırları işler. Ayar değişikliği ilgili listenin eski sonuç/ZIP/onaylarını geçersizleştirir.

Eşit veya büyük her dosya için sonuç kimliğiyle eşleşen ayrı kopya onayı gerekir. ZIP’e dahil etme kutuları, son incelemedeki eklenecek/toplam adet ve hata/dışlama açıklaması korunur. ZIP’in kendisi sıkıştırma başarısı sayılmaz; paketlemeden ek boyut kazancı beklenmez. Kaynak URI üzerine yazma, eski seçicinin farklı sonuç kaydetmesi ve onaysız kopyanın ZIP üzerinden dışarı çıkması reddedilir. İptal edilen `.part` paket temizlenir; kaynaklar ve bitmiş sonuçlar korunur.

## Modüler yapı ve kayıt uyumluluğu

- `BatchQueue.kt`: `BatchKind`, türü belirli çıktı seçenekleri, ortak öğe modeli, sürümlü metadata ve dosya/onay/ZIP adı politikası.
- `BatchConverters.kt`: mevcut `ImageCompressor`, `VideoCompressor` ve `AudioEngine` için küçük adaptörler. Video posterleri ve görsel önizlemeleri hiçbir UI katmanına verilmeden bırakılır. Seçilmeyen video biçimleri için toplu kodlayıcı denemesi yapılmaz; seçili hedefin gerçek motor denetimi kullanılır.
- `BatchEngine.kt`: tür başına ayrı, tek yazıcılı/atomik kayıt; sıralı çalışma; dosya sahipliği; 64 KiB akışlı ZIP ve kayıt. Çalışma klasörleri `batch-images`, `batch-video` ve `batch-audio` olarak ayrıdır. Tek dosya araçlarının önbellek temizliği bu klasörlere girmez.
- `BatchViewModel.kt`: ortak iş/durum akışı ve üç küçük Android ViewModel. `ProcessingJobs` uygulama ömrüne sahip tek ağır işlem kilidini kullanır; video ve ses aynı anda encoder açmaz. Başka bir türün metadata okunması için ortak disk kilidi tutulmaz. Aktif listeyi temizleme isteği bekleyen bir ekran yüklemesinde takılmadan reddedilir.
- `BatchScreen.kt` ve `BatchMediaOptions.kt`: ortak sonuç/ZIP/inceleme ekranı ve türe göre seçenekler. Kimliği korunan tembel satırlar, 48 dp kontroller, telefon alt paneli ve geniş ekranda mevcut yan panel düzeni kullanılır. RTL’de genişlik × yükseklik teknik değeri LTR olarak gösterilir.

Eski görsel kayıtları **version 1** olarak aynı alanlarla okunur/yazılır. Video/ses **version 2** kaydı tür, zorunlu profil alanları ve kaynak sürelerini içerir; yanlış klasör/tür veya bozuk profil varsayılan biçime çevrilmez. Yeni çocuk ViewModel’ler mevcut varsayılan Android fabrikasıyla oluşturulur. Kayda medya baytları, bitmap veya kopya onayı girmez. Her tamamlanan sonuç sonraki dosyadan önce kalıcılaştırılır. Süreç kapanınca otomatik yeniden kodlama yapılmaz; eski sonuçlar korunur, yeni dışa aktarım onayları yeniden istenir.

`VideoCompressor` ve `AudioEngine` yalnız dahili sahip olunan çalışma klasörü seçeneği kazandı; varsayılan klasörleri değişmedi. Var olan FileProvider yetkisi, kök adları, proje dosyaları, eski video/ses taslakları ve tek dosya araçlarının kayıt biçimleri değiştirilmedi.

## Lisans ve doğrulama

Yeni SDK, native codec, font veya Maven paketi eklenmedi. ZIP mevcut Android/JDK sınıflarıyla oluşturulur; codec motorları ve LAME ticari kaynak/lisans paketi önceki kapsamda kalır. Gerçek `releaseRuntimeClasspath` envanteri ve incelenmiş hash’ler ayrıca denetlenir; bu kontrol codec patent/ülke izinlerinin yerine geçmez. Jev veya başka bir ağ servisi uygulamaya/derlemeye bağlanmadı. FFmpeg/Python yalnız sentetik test örneklerini üretmek ve host tarafında bağımsız doğrulamak için kullanılır, uygulamaya dağıtılmaz.

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
python tools/localization/generate.py --check
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/batch-media-qa.ps1 -SkipBuild
```

Koşucu yalnız `ofice_qa` emülatörünü kabul eder. Video biçim/kalite/boyut, ses biçim/hız/kanal, onay/ZIP/gerçek kayıt, iptal/yeniden deneme, dosyaya özgü destek hatası, tek ağır iş kilidi, eski görsel listesi, Activity ve gerçek süreç yeniden oluşturma, RTL/yüzde 200 yazı ve koyu tablet yolları sınanır. Tek dosya video/ses araçları ayrıca regresyondan geçirilir. Host doğrulayıcısı 9 ZIP içindeki 27 gerçek dosyanın CRC/hash, tam çözüm, codec, görüntü boyutu, süre, ses hızı/kanalı ve perde değerlerini bağımsız FFmpeg ile karşılaştırır. Sentetik kaynak fixture hash/provenansı `app/src/androidTest/assets/batch-media-manifest.json` içindedir.

Kaynak/çıktı kanıtı `output/development/batch-media/` altında Git dışında tutulur. Yeni metinler 22 dilde tamamlanır; şema/placeholder denetimi dil uzmanı değerlendirmesi değildir. Android 24/ARM, gerçek düşük donanım/pil/FPS, bütün belge sağlayıcıları ve yeni ekranların gerçek TalkBack sesli dolaşımı bu emülatör ölçümünden türetilmez. Yeni kodun R8 ve imzalı yayın doğrulaması sonraki kullanıcı sürüm isteğinde tamamlanır.

### Kabul kaydı

- **121 JVM testi**, 0 hata. Yeni altı test tüm video kalite/biçim/boyutlarını, ses hedef/bit hızlarını, eski görsel şemasını, büyük medya sınırlarını ve yanlış metadata/destek hatalarını kapsar.
- **39 emülatör yürütmesi**: yeni/ortak toplu motor ve arayüz ile eski toplu görseller 16; gerçek süreç hazırlama/geri yükleme 2; eski tek video/ses araçları 19; Arapça/yüzde 200 yazı ve koyu tablet 2. Uygulama APK hash’i, yalnız test gezinmesi düzeltilen ara derlemelerde değişmedi.
- Son görsel incelemede ZIP için yanlış “görsel kaydedildi” metni ortak “dosya kaydedildi” metnine düzeltildi; süreler mevcut yerel saniye kaynağını kullanır ve biçim özeti enum adı yerine okunur etiket gösterir. Yalnız bu sunum değişikliklerinden sonra debug/lint ve etkilenen **8 arayüz yürütmesi** ayrıca geçti. Android Dialog pencere animasyonunu da bekleyerek ekran kanıtını yenileyen 2 telefon/tablet yürütmesi aynı uygulama APK’sıyla geçti. Böylece kabul edilmiş toplam **49 yürütme** vardır; farklı test sayısı iddiası değildir. Ekran görüntüleri panel animasyonu bittikten sonra alınarak gözle incelendi.
- **9 ZIP / 27 dosya**: bütün CRC/hash ve bağımsız tam çözüm, codec, görüntü boyutu, süre, ses hızı/kanalı ve perde denetimleri geçti. Döndürülmüş MP4’te kodlanmış boyut ile görüntülenen boyut farkı beklenir; gerçek görüntülenen boyut karşılaştırılır.
- 22 dil kaynak/placeholder kontrolü ve 95 paket + native encoder için mevcut lisans/hash envanteri geçti. Mevcut debug APK’da sekiz native kütüphane/dört ABI, 16 KiB LOAD/RELRO, dinamik encoder bağlantısı ve karşılık gelen çevrimdışı kaynak/bildirim paketi ayrıca denetlendi. Lint: **0 hata, 68 uyarı, 2 ipucu**.
- Yerel Graphify grafiği kod değişikliklerinden sonra API/LLM kullanmadan güncellendi. Kısmi Kotlin AST uyarıları derleyici ve test kanıtının yerine geçmez.

Başarısız ara turlar kabul sayısına katılmadı. İlk sentetik 180° video üretiminde dönüş matrisi oluşmadığı için fixture düzeltilip bağımsız kontrol edildi; önbellekteki eski fixture ile karışmaması için ayrı ad kullanıldı. Ses testinin yanlış çalışma klasörü, eski lisans testinin kapalı yardım bölümü ve tablet testinin telefona özgü kapatma düğmesi varsayımı test tarafında düzeltildi; üretim dosya sahipliği ve onay denetimleri gevşetilmedi. FFmpeg metin ayrıştırıcısının VP8 etiketinde bırakabildiği virgül host karşılaştırmasında temizlendi; codec veya uygulama çıktısı değiştirilmedi.

Sonraki ürün adımı güvenli ZIP açma/çıkarma yoludur: dosya yolu, toplam açılma baytı, dosya adedi, bozuk/şifreli veri ve iptal sınırları ayrıca kurulmalıdır. Bu paket ZIP çıkarma, 7Z/RAR veya belge toplu tarifleri sunduğunu iddia etmez.
