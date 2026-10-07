# Birlevo — OGG/Opus ve FLAC çıktıları

7 Ekim 2026 geliştirme devamı. WAV / M4A yollarına OGG ve FLAC eklendi. Sürüm numarası değiştirilmez; dondurulmuş 0.1.9 APK / AAB bu devamın kodunu içermez. Yayın paketi ve R8 doğrulaması sonraki kullanıcı sürüm isteğine bağlıdır.

## Kullanıcı akışı ve sınırlar

Ses araçları → ses veya video seç → WAV, M4A, OGG · Opus veya FLAC seç → sonucu üret ve dinle → yeni dosyaya kaydet, aç veya paylaş. Eski hedeflerin ve bit hızlarının adları / oturum kimlikleri korunur. Desteklenmeyen hedef seçilebilir; üretim kapatılır ve nedeni gösterilir. Hedef değiştirmek sonucu ve ona ait büyük kopya onayını geçersiz kılar.

| Hedef | Gerçek içerik | Ayar / koşul |
| --- | --- | --- |
| WAV | Çözülmüş PCM16 | Kaynak örnekleme hızı / kanal sayısı korunur; mevcut hedef |
| M4A | AAC | 64 / 128 / 256 kbps, örnekleme hızı / kanal sayısı korunur; mevcut hedef |
| OGG | Opus | Android 10+, cihaz encoder / decoder, 64 / 128 / 256 kbps. Çıkış açıkça 48 kHz; kaynak başka hızdaysa dönüştürülür, kanal sayısı korunur |
| FLAC | Native FLAC, PCM16 | Cihaz FLAC encoder / decoder, kaynak örnekleme hızı ve kanal sayısı. Sabit orta kodlama karmaşıklığı; bu bir kalite düşürme ayarı değildir |

OGG / M4A kayıplıdır. FLAC, bu aracın çözdüğü **16 bit PCM'yi** ek kayıp olmadan kodlar; daha yüksek bit derinlikli kaynağın bütünüyle kayıpsız dönüşümü olarak sunulmaz. Hedef açıklamasında 16 bit sınırı ve OGG için 48 kHz dönüşümü gösterilir. Meta veriler, kapak ve diğer parçalar aktarılmaz. Mevcut 512 MiB / on dakika / mono veya stereo giriş sınırları ve WAV / M4A seçenekleri azaltılmadı. MP3 yazma, Vorbis yazma, ses kesme / birleştirme bu adımın desteğine eklenmez.

## Motor ve gerçek çıktı denetimi

`AudioOutputSupport` tam hedef örnekleme / kanal / bitrate ayarını IO'da kontrol eder. Üretimden hemen önce kontrol tekrarlanır. Başka codec veya bitrate'e fallback yapılmaz; başarısız kodlama açıklamalı hata yolunda kalır. Codec envanteri bulunması gerçek üretim başarısı sayılmaz.

OGG mevcut Media3 1.5.1 kodlama yolunu kullanır; `OggAudioMuxer` platform Ogg yazıcısına tek Opus parçası verir. Metadata kopyalamaz. Sonic işlemcisi yalnız bu hedef için 48 kHz dönüşümü uygular. Aynı codec'teki kaynak da bit hızı ayarının uygulanması için tekrar kodlanır. [Platform Opus / Ogg koşulları](https://developer.android.com/reference/android/media/MediaMuxer).

`PcmAudioDecoder`, WAV ve FLAC için ortak sınırlı tamponlu PCM16 okuyucusudur. FLAC, PCM ara dosyası veya tüm sesin bellekte kopyası olmadan MediaCodec encoder'a akar. Encoder girdisi, çıktısı ve decoder buffer sahipliği ayrıdır; iptal bütün döngülerde kontrol edilir. Otuz saniyelik ilerlemesizlik ve mevcut yirmi dakikalık toplam işlem sınırı sürer. Encoder ve extractor finally ile bırakılır; başarısız / iptal edilmiş çıktı silinir.

`FlacHeader` encoder'ın sınırlandırılmış başlığını doğrular. Yalnız STREAMINFO tutulur; gerçek örnek sayısı ve verilen PCM'nin MD5 özeti tamamlanırken yazılır. Bilinmeyen frame boyutları standarttaki sıfır değerini kullanır. [STREAMINFO tanımı](https://datatracker.ietf.org/doc/html/rfc9639#section-8.2).

Çıktı tekrar MediaExtractor ile açılır; süre, kanal, örnekleme, parça yapısı, boyut ve codec kontrol edilir. Bazı Android FLAC extractor'ları zaten çözülmüş `audio/raw` verir; bunun için native `fLaC` başlığı / 16 bit / örnek sayısı ayrıca denetlenir. Yeniden adlandırılmış WAV, FLAC kabul edilmez. OGG'nin gerçek `OggS` kapsayıcı imzası da doğrulanır. Kaynaklara yazma erişimi açılmaz.

Tek ağır iş sırası, kullanıcı başlatmalı ön plan servisi, atomik tek yazıcılı kaynak / ayar / bitmiş sonuç kaydı kullanılır. Eski WAV / M4A checkpoint sürümü / isimleri değişmez; yeni hedefler ayrı request kimliğidir, bilinmeyen hedef sessizce M4A'ya çevrilmez. FileProvider yetkisi / kökleri değişmez. Kaydetme / açma / paylaşma gerçek dosya boyutuyla karar verir; eşit veya büyük dosyaya o sonuca özel kopya onayı gerekir. OGG ve FLAC doğru uzantı / MIME ile aktarılır; yeniden kodlama yapılmaz.

## Doğrulama kaydı

Güncel koşu kanıtları Git dışındaki `output/development/audio-expansion/` dizinindedir. `tools/testing/audio-targets-qa.ps1` yalnız adı doğrulanan **ofice_qa** emülatörünü kullanır. Yeni 44.1 kHz mono yapay ton yalnız test APK'sındadır; gerçek kullanıcı medyası işlenmez. Host FFmpeg test örneği ve bağımsız doğrulama içindir, uygulamaya eklenmez.

Kontrol kapsamı: yedi giriş kapsayıcısında dört gerçek çıktı hedefi; videodan ses; mono 44.1 kHz / stereo 48 kHz; PCM byte eşitliği ve FLAC MD5; Opus girdisinden farklı bit hızlarında gerçek üretim; oynatıcının sona ulaşması; UI hedef seçimi, doğru kayıt MIME / baytları, Activity / yeni ViewModelStore ile sonuç geri yükleme; izin / onay; yok encoder ve destek dışı AAC örnekleme hızı; iki yeni hedefin iptal temizliği; eski arka plan / video aktarımı regresyonları. Yeni metinler 22 dilde kaynak katalogla üretilir; dil uzmanı onayı sayılmaz.

Son koşu: **60 birim testi**, **21 özgün hedefli Android testi** (14 ses + 6 ortak çıktı + 1 WebM aktarımı) ve tablet / büyük yazı / koyu modda **6 yerleşim tekrarı** geçti. Ekran görüntüleri incelendi. Yedi özgün ses girdisi × dört hedef = 28 dönüşüm yoludur; her yol ayrı test sayılmaz. Altı çıktı ayrıca host decoder ile açıldı, dört FLAC dosyasında gerçek PCM MD5 / örnek sayısı eşleşti. Opus codec padding için küçük süre toleransı vardır; gapless / örnek hassasiyetinde kesme garantisi verilmez. Lint: 0 hata / 40 uyarı. 22 dilde 8 yeni anahtar, 20 çevrimdışı Jev testi ve debug APK izolasyonu geçti. Yerel Graphify güncellendi; 20 bilinen kısmi Kotlin parser uyarısı vardır, gerçek Kotlin / Java derlemesi başarılıdır.

Fiziksel üretici codec'leri, Android 7–9 üzerinde FLAC, düşük donanım / düşük depolama / pil / termal davranış ve sonraki release R8 çalışması ölçülmüş değildir. Emülatör doğrulaması bu cihazlar için performans veya tam destek garantisi vermez. Yeni Gradle / native codec / ağ bağımlılığı ve canlı Jev çağrısı yoktur.
