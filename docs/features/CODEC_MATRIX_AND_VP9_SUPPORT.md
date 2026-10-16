# Codec matrisi ve VP9 genişlemesi — 15 Ekim 2026

İlk codec genişletme paketi, platformdaki kodlayıcı/çözücüleri kullanarak VP9 girişini ve VP9/Opus WebM çıktısını ekler. Yeni native codec kütüphanesi, SDK veya font dosyası uygulamaya eklenmedi. Aşağıdaki destek, dosya uzantısından değil gerçek akış ve cihaz kontrollerinden geçer.

| İşlem | Uygulama kapsamı | Koşul |
|---|---|---|
| Görsel okuma | JPEG, PNG, BMP, durağan WebP, HEIC/HEIF, AVIF | Son üç tür için cihaz çözücüsü; hareketli WebP desteklenmez |
| Görsel yazma | JPEG, PNG, WebP, BMP | Mevcut kalite, saydamlık ve çözünürlük tercihleri |
| Video kapsayıcısı okuma | MP4, MOV, 3GP, MKV, WebM | Tek video, en fazla tek desteklenen ses akışı |
| Video akışı okuma | H.264, HEVC, VP8, **VP9 Profile 0** | Mevcut SDR/8-bit kapsamı, cihaz çözücüsü ve boyut/süre kontrolleri |
| Video yazma | H.264/AAC MP4, HEVC/AAC MP4, VP8/Opus WebM, **VP9/Opus WebM** | Tam istenen boyut/hız/bit hızı için kodlayıcı ve önizleme çözücüsü |
| Ses okuma | MP3, M4A/AAC, WAV PCM16, OGG Vorbis/Opus, FLAC; desteklenen videonun ses akışı | Cihaz, örnekleme hızı ve kanal kontrolleri |
| Ses yazma | WAV PCM16, M4A AAC, OGG Opus, FLAC, MP3 | Opus 48 kHz; mevcut bit hızı/kanal ve codec koşulları |
| Belgeler | TXT/MD/CSV/TSV, sınırlı DOCX/XLSX, PDF araçları | [Office kapsamı](documents/DOCX_XLSX_UI_INTEGRATION.md); genel zengin Office görünümü değildir |

## VP9 davranışı

VP9 Profile 0, 8-bit 4:2:0’dır. Profile 1 farklı chroma alt örneklemesi, Profile 2/3 ise 10/12-bit kullanır; bu pakette etkinleştirilmez. Kaynakta gerçek ilk VP9 erişim biriminin frame marker ve profil bitleri okunur. Dosya adına veya kapsayıcının profil etiketine dayanılmaz. Bilinen PQ/HLG HDR metaverisi reddedilir; 10-bit/HDR otomatik olarak 8-bit’e indirilmez. Bu sınırlı kontrol tam bitstream doğrulaması, dinamik profil değişimi veya saydam video desteği garantisi değildir. [WebM profilleri](https://www.webmproject.org/vp9/profiles/), [kapsayıcı ilkeleri](https://www.webmproject.org/docs/container/).

Video içe aktarma ve kare çıkarma aynı codec kaydını ve VP9 profil kontrolünü kullanır. Profil okuması için kodlanmış erişim birimi en çok 32 MiB olabilir; aşım hata verir. Android 28 ve üzerinde gerçek sample boyutu kadar geçici bellek ayrılır; daha eski API’de en çok 32 MiB geçici alan gerekir. Eski Android ve fiziksel düşük bellek yolu ölçülmedi.

VP9 çıktısı Profile 0 ile istenir. Kodlayıcı seçiminde profile, surface, VBR ve gerçek istenen ayarlar kontrol edilir; donanım kodlayıcı varsa önceliklidir. Eksik/yanlış bildirilmiş aralık için mevcut sınırlı configure denemesi kullanılır. Kalite, hız veya nihai boyut için otomatik geri dönüş yoktur. Çıktının gerçek MIME, boyut, süre ve sesi tekrar okunur; yalnız encoder listesinde adı bulunması başarı sayılmaz.

Her iki WebM hedefi ortak kapsayıcı yolundadır. Ses Opus’a, açıkça 48 kHz’e kodlanır; mevcut kanal denetimi korunur. Sesli WebM API 29+, sessiz VP9 WebM uygulamanın API 24+ tabanında ve uygun codec varsa kullanılabilir. MP4’ün 180 derece yön ipucunu temsil edemeyen WebM yolu hâlâ açık hata verir. Portre çıktı, yazı/görsel katmanı, kesme ve çoklu klip akışı mevcut motor üzerinden çalışır. [Android format tablosu](https://developer.android.com/media/platform/supported-formats), [MediaMuxer codec/kapsayıcı tablosu](https://developer.android.com/reference/android/media/MediaMuxer).

İşlem kimliği VP9 seçimini kapsar. Eski AVC oturumunun kaydı/parmak izi değişmez; VP8 ve VP9 oturumları birbirine karışmaz. Kayıt/açma/paylaşma ve ses aracına aktarım gerçek `video/webm`/`.webm` bilgisini kullanır. Eşit veya büyük çıktı, o sonuca özel kopya onayı olmadan kaydedilemez veya aktarılamaz. Ayar değişimi eski sonucu geçersizleştirir; iptal, özgün dosyaları değiştirmeden çalışma dosyalarını temizler. FileProvider yetkisi/kök adı ve kayıt şeması korunur.

## Ticari dağıtım değerlendirmesi

APK’ya libvpx, libaom, FFmpeg veya başka bir codec uygulaması taşınmadı. Media3 ve mevcut runtime artifact’leri aynı sürüm/hash kapsamındadır. Kullanılan platform codec’i Android/vendor dağıtımındadır; bu, kendi başına bütün patent taleplerinin karşılandığı anlamına gelmez.

WebM uygulama kaynakları için BSD tarzı yeniden dağıtım şartları ve Google’ın sınırlı ek patent hakları vardır. Ek haklar Google’ın lisanslayabildiği, tanımlanan uygulamalarda zorunlu olarak kullanılan taleplerle sınırlıdır. Bunlardan bütün VP9 uygulamalarının ve bütün ülkelerdeki ticari kullanımın bedelsiz olduğu sonucu çıkarılmaz. [WebM yazılım lisansı](https://www.webmproject.org/license/software/), [ek haklar](https://www.webmproject.org/license/additional/).

Sisvel ayrıca VP9 ve AV1 için patent havuzları sunduğunu bildiriyor. Bu rapor, bu taleplerin Birlevo’ya hukuken uygulanacağını veya uygulanmayacağını belirlemiyor. Uygulama dağıtımı, ülke ve platform/vendor kapsamı reklam geliriyle halka açık yayın değerlendirmesinde ayrı ele alınmalıdır. Yeni SDK telif/hash kontrolü ile patent kapsamı farklı kararlardır. [VP9 programı](https://www.sisvel.com/licensing-programmes/audio-and-video-coding-decoding/video-coding-platform/), [AV1 programı](https://www.sisvel.com/licensing-programmes/audio-and-video-coding-decoding/video-coding-platform-av1/).

| Aday | Teknik bulgu | Bu adımın kararı |
|---|---|---|
| VP9 / Opus WebM | API 36 QA envanterinde encoder/decoder; gerçek dosya ve katman yolları test edilir | Sınırlı Profile 0 desteği geliştirme koduna eklendi |
| AV1 / AAC MP4 | QA envanterinde encoder/decoder; Android platform belgesi API 34+ mux yolunu açıklar | Henüz gerçek dönüşüm/HDR/profil zinciri doğrulanmadı; uygulama listesine eklenmedi |
| APV | Android platform belgesinde API 36 codec/kapsayıcı desteği | Profesyonel profil/bit derinliği ve boyut/performans denemesi yapılmadı; eklenmedi |
| AAC ADTS çıktı | AAC mevcut codec, kapsayıcı yolu ayrı geliştirme gerektirir | Ayrı gelecekteki kapsayıcı işi; bu pakette yok |
| Native FFmpeg/libvpx/libaom | Yeni dört ABI, 16 KiB hizalama, lisans/kaynak/binary envanteri ve bakım gerektirir | Platform yolu varken bu pakete eklenmedi |

Host FFmpeg yalnız özgün sentetik test dosyalarını üretir ve sonuçları bağımsız çözer; Android build/runtime bağımlılığı değildir. Jev/API çağrısı yoktur. Yeni release/versiyon numarası yoktur. Kamuya açık reklamlı yayın için bu geliştirme kontrolü genel hukuki onay sayılmaz.

## Tekrarlanabilir doğrulama

```powershell
# Yalnız ayrılmış ofice_qa emülatörü; fiziksel cihaz gerekmez.
./tools/testing/vp9-codecs-qa.ps1
python tools/testing/verify_vp9_outputs.py output/development/vp9-codecs/vp9-codecs
python tools/testing/verify_video_text_files.py output/development/vp9-codecs/video-text
python tools/testing/verify_video_frame_files.py output/development/vp9-codecs/video-frame
python tools/licensing/audit_runtime.py --check
python tools/localization/generate.py --check
```

`output/development/vp9-codecs/` ölçüm/kayıt dizinidir. Codec listesi, doğru ayarla configure ve gerçek kodlanmış dosya farklı kanıtlardır. Sentetik emülatör sonucu fiziksel cihaz FPS, pil, bellek veya bütün Android/vendor codec’leri için garanti değildir. Yeni kodun küçültülmüş R8/yayın doğrulaması sonraki kullanıcı sürüm isteğinde yapılır.

Kabul edilen son koşu: 109 JVM testi, 16 emülatör testi; lint 0 hata / 64 uyarı / 2 öneri. Bağımsız çözücü 7 VP9 dosyasında gerçek profil, boyut, süre, ses ve perdeyi; 8 katmanlı çıktıda her karede yazı/zaman/konum/renk ve klip sırasını; 14 PNG karede gerçek PTS, yön ve RGB farkını doğruladı. Kaynak boyutundan küçük olmayan kopya onayı, eski oturum varsayılanı, Activity ve diskten yeni ViewModel geri yükleme, iptal ve ses aktarımı test edildi. Bu pakette ayrı işletim sistemi process-kill koşusu yapılmadı. İlk System UI ANR ve eksik kare-codec kaydı koşuları bu başarı sayısına dahil değildir.

Son debug APK SHA-256: `542e93791b0b384da2ea7d90eb38915443b8cdda56675c3047a5a9a05a6d611f`. 95 runtime artifact/hash ve ayrı native encoder lisans kapsamı yeniden geçti; imzalı 0.1.11 (12) dosyası değiştirilmedi. Sonraki ürün işi toplu dönüştürme / ZIP akışıdır; diğer codec adayları yalnız bu rapordaki koşullar karşılanınca etkinleştirilecektir.
