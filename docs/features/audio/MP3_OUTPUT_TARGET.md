# Birlevo — MP3 çıktı hedefi

7 Ekim 2026 geliştirme devamı. Ses aracı artık WAV PCM16 / M4A AAC / OGG Opus /
FLAC PCM16 yanında **MP3** üretir. Yedi ses girdisi ve videonun tek ses parçası
bu hedefe işlenir. Hedef seçimi, dinleme, yeni dosyaya kayıt / açma / paylaşma,
kalıcı sonuç ve büyük kopya onayı mevcut akışa bağlıdır. Sürüm numarası 0.1.9 (10)
olarak kaldı; dondurulmuş imzalı 0.1.9 dosyaları bu devamı içermez.

## Kalite ve sınırlar

LAME 4.0 CBR, kalite / karmaşıklık 2, 64 / 128 / 256 kbps. MP3 kayıplıdır;
başlangıçtaki kaybı geri getirmez. Çözülmüş PCM16 küçük tamponlarla encoder'a
akar. Kaynak örnekleme hızı ve mono / stereo kanal sayısı korunur; metadata /
kapak / diğer parçalar aktarılmaz. Ara WAV veya bütün dosyayı RAM'de tutma yoktur.

| Örnekleme hızı | Mevcut seçilebilir bit hızlarından gerçek destek |
| --- | --- |
| 32 / 44.1 / 48 kHz | 64 / 128 / 256 kbps |
| 16 / 22.05 / 24 kHz | 64 / 128 kbps |
| 8 / 11.025 / 12 kHz | 64 kbps |
| Diğerleri | MP3 hedefi istenen ayarla kullanılamaz; WAV / FLAC / diğer hedefler korunur |

Düşük MPEG-2.5 hızlarında 128 kbps'nin native kodlayıcı tarafından değiştirilmesi
ilk gerçek testte yakalandı. Politika sınırı ve native `lame_init_params` sonrası
gerçek rate / kanal / bitrate kontrolü bunu birlikte engeller. Destek dışı istekte
kalite / bit hızı / örnekleme otomatik düşürülmez. Aynı MP3 codec'indeki kaynak
da seçilen bit hızını uygulamak için yeniden kodlanır; aktarım tekrar kodlamaz.

512 MiB / on dakika / mono veya stereo giriş sınırları değişmez. İş kuyruğu,
kullanıcı başlatmalı servis, atomik tek yazıcılı sonuç ve kesinti sonrası açık
yeniden başlatma kullanılır. İptal her PCM bloğunda kontrol edilir, JNI handle
`use` / senkronize idempotent close ile kapanır, hata / iptal çıktısı silinir.
JNI giriş / çıkış boyutları native tarafta da denetlenir; 4096 frame giriş /
12800 byte çıkış tamponu kullanılır.

LAME Info / gecikme / padding başlığı tamamlanırken yazılır. Çıktı önce gerçek
Layer III frame başlıklarıyla baştan sona, sonra Android extractor bilgisiyle
doğrulanır. CBR / örnekleme / kanal tutarlılığı, gerçek dosya boyutu ve süre
kontrol edilir; yalnız `.mp3` adını almak yeterli değildir. Gapless editör veya
örnek hassasiyetinde kesme özelliği olarak sunulmaz.

## Native ve lisans kararı

Android'in genel MP3 encoder garantisine bağlı kalınmaz. Büyük FFmpeg paketi veya
uzak dönüştürme servisi eklenmedi. Resmî LAME arşivi hash ile sabitlendi ve yalnız
encoder C dosyaları NDK 27.1 / CMake 3.22.1 ile dört ABI için derlenir. GPL decoder
ve frontend yoktur. JNI köprüsü ayrı dinamik kütüphanedir. Java JNI isimleri dar
R8 ABI kuralıyla korunur; geniş keep veya yeni INTERNET izni yoktur.

Reklam gelirli ticari kullanım için [lisans incelemesi](../../licensing/COMMERCIAL_LICENSE_INVENTORY.md)
yapıldı. Tam lisans / kaynak / Android build / arayüz paketi uygulamadan çevrimdışı
kaydedilebilir. Bu, uygulamanın diğer kodunu veya özel GitHub reposunu yayımlamaz.

Dört ABI içeren debug APK: önce 28,929,290 byte, sonra yaklaşık 31.67 MB;
yaklaşık **2.61 MiB** artış. Native encoder / köprü bir ABI için yaklaşık
195–286 KiB; kaynak ZIP'i yaklaşık 1.45 MiB. Bu universal debug paket farkıdır;
Play ABI bölünmüş indirme veya sonraki R8 release boyutu olarak sunulmaz.
Sekiz native ELF'de 16 KiB LOAD ve RELRO bitiş hizalaması, debug ZIP hizalaması
geçti. Fiziksel 16 KiB cihazda çalışma / düşük donanım / termal ölçüm yapılmadı.

## Doğrulama

Güncel kanıtlar `output/development/mp3-expansion/` altında Git dışında tutulur.

- 62 JVM testi geçti; MP3 politika / sahte uzantı / bozuk frame / karışık bitrate
  ve eski ses oturum kimlikleri dahil.
- 21 ses Android testi ve 8 ortak çıktı / lisans UI / WebM aktarımı testi geçti:
  toplam **29 özgün hedefli debug Android testi**. Yedi kapsayıcı × beş hedef
  35 dönüşüm yoludur; 35 ayrı JUnit testi değildir.
- Gerçek CBR, 9 MPEG örnekleme hızı / destek dışı istek, mono / stereo, MP3'ten
  MP3'e kodlama, PCM çözümü, oynatmanın sona ulaşması, iptal / tekrar üretim,
  onay / MIME / byte eşitliği, UI ayar değişikliği, Activity / kalıcı sonuç
  yeniden açma ve lisans kaynak ZIP'ini belge seçicisinden kaydetme sınandı.
- Dokuz yapay MP3 dosyası host'ta bağımsız decoder ile açıldı; gerçek CBR / rate /
  kanal / süre eşleşti. Host FFmpeg uygulama bağımlılığı değildir.
- Tablet, büyük yazılı dar ekran ve koyu modda üç yeni ses hedefinin **9 yerleşim
  tekrarı** geçti; ekran görüntüleri incelenir. Tekrarlar özgün test sayısına eklenmez.
- 22 dilde iki yeni metin ve tam anahtar kontrolü; debug lint / native paket /
  lisans envanteri / Jev uygulama izolasyonu kontrol edilir. Çeviri dil uzmanı
  onayı veya fiziksel cihaz performans garantisi sayılmaz.

Kalan: yeni hedeflerin sonraki kullanıcı sürüm isteğinde R8 / küçültülmüş APK ve
native değiştirme denemesi; fiziksel ARM cihazı / eski Android / düşük bellek /
dolu disk / pil ölçümleri. MP3, OGG ve FLAC bu devamda debug üzerinden sınandı;
yeni imzalı release APK veya AAB üretilmedi. Ses kesme / birleştirme sonraki ayrı
ürün işidir.

## 0.1.10 yayın doğrulaması — 7 Ekim 2026

Yukarıdaki geliştirme aşamasından sonra ses kesme / sıralama / birleştirme eklendi; kullanıcının isteğiyle 0.1.10 (11) imzalı, küçültülmüş APK / AAB hazırlandı. MP3 / OGG / FLAC, iki kaynak / kesme / sıra / 48 kHz stereo / süre / kaydetme / açma / paylaşma / orijinal koruma yolları gerçek R8 uygulamasında geçti. Tablet, büyük yazı ve koyu mod tekrarları tamamlandı. FLAC için Android’in decoded PCM track davranışı yanında gerçek fLaC başlığı, STREAMINFO ve bağımsız PCM MD5 doğrulandı.

Son APK’nın kaynak ZIP’i çevrimdışı kaydedilip x86_64 encoder yalnız bu kaynaklardan yeniden derlendi. Sürüm işareti değiştirilmiş encoder başka APK girdisi değiştirilmeden QA kopyasına kondu; Birlevo yükleme anahtarı olmadan ayrı yerel anahtarla imzalandı. R8 JNI ABI / gerçek ses araçları / dört bağımsız çözümleme geçti; resmi APK ve native sürüm geri kuruldu. QA değiştirilmiş APK dağıtım dosyası değildir. Dört ABI’nin kaynak / LOAD / RELRO / ZIP 16 KiB denetimi ayrıca geçti. Fiziksel ARM / eski Android / düşük bellek / dolu disk / pil sınamaları bekler. [Paket ve kanıtlar](../../releases/RELEASE_0.1.10.md).
