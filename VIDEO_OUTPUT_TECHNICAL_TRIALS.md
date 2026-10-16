# HEVC / WebM çıktı teknik denemeleri — önceki aşamanın kaydı

Bu belge ilk, test APK’sına özel denemenin tarihsel kaydıdır. Aynı gün hedefler gerçek uygulama motoruna entegre edildi; güncel kapsam için [video çıktı hedefleri](VIDEO_OUTPUT_TARGETS.md). Eski raporların `productionSupport: false` alanı o aşamayı anlatır. Tekrar çalıştırılan matris artık ortak uygulama motorunu kullanır.

7 Ekim 2026 ilk denemesi. Deneme tamamlandı; **kullanıcıya sunulan video çıktısı hâlâ MP4 / H.264 / AAC**. HEVC ve WebM prototipleri yalnız `androidTest` kaynaklarında / test APK'sındadır. Mağaza açıklaması ve destek listesine henüz eklenmez. Sürüm 0.1.8 (9) değişmedi, release APK / AAB üretilmedi.

## Ölçülen kapsam

Android 16 / API 36 `ofice_qa`, yazılım codec'leri ve Media3 1.5.1 ile 5 cihaz testi geçti. Alt işlemler: 13 video üretimi, her biri uygulama okuyucusunda açma ve gerçek oynatıcıda ilk kareden sona kadar oynatma; 6 sesli sonuçtan toplam 12 WAV / M4A üretimi; iki hedefte başlamış işi iptal etme ve geçici dosya temizliği. Ayrıca 53 JVM testi, debug derleme, lint 0 hata / 40 uyarı, 22 dil kaynak kontrolü ve 20 çevrimdışı Jev testi geçti. Canlı Jev API'si çağrılmadı.

| Hedef | Gerçek parçalar | Temel kaynaklar | Sonuç |
| --- | --- | --- | --- |
| MP4 / HEVC | HEVC, varsa AAC | Sesli AVC, sessiz AVC, 90° etiketli AVC | 3 Balanced / 640 + Small / 640 + High / 1280: 5 çıktı |
| WebM / VP8 | VP8, varsa Opus | Aynı üç kaynak; HEVC, VP8 / Opus WebM, VP8 / Vorbis WebM | 6 Balanced / 640 + Small / 640 + High / 1280: 8 çıktı |

Kırpma 250 ms'den başlar; kaynağa göre 750 veya 1500 ms hedeflenir. Gerçek sürede en fazla 300 ms sapma, istenen boyutların tam eşleşmesi, codec MIME türleri ve parça sayısı kontrol edilir. Kaynak URI / özel kaynak baytları korunur. Dışa aktarılan dosya sonuçla bayt düzeyinde eşleşir. Her sonuçta eşit boyutlu kopyayı onaysız kaydetme üretim motorunun mevcut kontrolüyle reddedilir. Bu test arayüz onayı veya ortak aktarım entegrasyonu değildir.

Örnek: 7.463 baytlık HEVC kaynak WebM'de 25.189 bayt oldu; bu sıkıştırma başarısı değildir. Başka bir 4.007.503 baytlık kaynağın Balanced / 640 sonucu HEVC'de 139.997, WebM'de 120.010 bayttır. Bunlar tek yapay örneğin ölçümleri; genel oran veya kalite kıyaslaması değildir.

Yerel kanıt: `output/development/video-targets/device-tests-final.log`, `final-report/inventory.json`, altı `*-balanced-640.json` / `*-small-640.json` / `*-high-1280.json`, derleme ve lint kayıtları. Git dışında tutulur. Eski başarısız raporlar tanı kaydıdır, nihai sonuç sayılmaz.

## Denemede çözülen sorunlar

Bu Media3 sürümünün varsayılan yazıcısı MP4 üretir. WebM için küçük platform muxer adaptörü yalnız test APK'sına eklendi. [Android muxer tablosu](https://developer.android.com/reference/android/media/MediaMuxer) WebM / VP8 ve API 29'dan itibaren WebM / Opus yolunu tanımlar. Sesli WebM prototipi API 29+ ile sınırlıdır; eski API ölçülmedi.

İlk WebM denemesi dikey videoyu reddetti. [Media3 1.5.1](https://github.com/androidx/media/blob/1.5.1/libraries/transformer/src/main/java/androidx/media3/transformer/Transformer.java) dikey kareleri yatay kodlayıp yön etiketi kullanabiliyor. Prototipte gerçek dikey kodlama açıldı; yazıcı sıfır olmayan yön etiketini kaybetmek yerine reddeder. 180° / 270° ayrıca doğrulanmalı.

Kırpılmış Opus'u doğrudan kopyalamak negatif başlangıç zaman damgasıyla ikinci denemeyi bozdu. [Android WebM yazıcısı](https://android.googlesource.com/platform/frameworks/av/+/master/media/libstagefright/webm/WebmWriter.cpp) negatif / geriye giden damgayı kabul etmez. Prototip WebM sesini yeniden kodlar; parçaları ayrı ayrı kaydırmaz veya başlangıç sesini atmaz. AAC kaynak örneği 44,1 kHz, Opus sonucu 48 kHz raporlandı; entegrasyonda bu değişim / kayıp açıklanmalı. Örnek düzeyinde A/V senkron doğruluğu, renk doğruluğu ve algısal kalite garantisi verilmez.

Video encoder fallback kapalıdır; kalite / çözünürlük otomatik düşürülmez. Envanter okuma / yazmayı ayrı tutar. VP9 / AV1'in emülatörde listelenmesi uygulama desteği değildir.

## Sıradaki uygulama işi

1. Hedef seçimini video motoru, kalıcı işlem isteği ve eski oturum varsayılanıyla birleştir. Eski MP4 sonuçlarının istek kimliği / geri yüklemesi korunmalı.
2. Kaynak ve seçilmiş boyut / kalite / kare hızı / ses özellikleriyle cihaz codec / muxer kontrolü yap. Destek yoksa açık gerekçe göster; codec veya çözünürlük değiştirme.
3. Uzantı, CreateDocument MIME, aç / paylaş ve ses aracına özel dosya aktarımını hedefe bağla. Büyük kopya onayı aynı sonuçla sınırlı kalsın.
4. Kayıp / hata metinlerini 22 dile ekle; oturum yeniden oluşturma, arka plan işi, tablet, eski Android ve fiziksel codec matrisiyle doğrula.

Fiziksel cihaz, 1920 / uzun video, diğer frame rate / ses profilleri, 180° / 270° ve yeni release R8 testi bekliyor. Emülatör süreleri düşük donanım, RAM, pil veya ısınma garantisi değildir.

Tekrar: `tools/testing/video-target-probe.ps1`. Yalnız `ofice_qa` emülatörüne debug uygulama / test APK'sı yükler; verileri sıfırlamaz, release / canlı API çağrısı yapmaz. Altı hedef raporu başarılı olmadıkça destek sonucu vermez.
