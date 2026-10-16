# Video yazı katmanı — 8 Ekim 2026

Geliştirme koduna tek yazı katmanı eklendi. Bu adım sürüm numarasını değiştirmez; dondurulmuş 0.1.10 APK / AAB ve yayın notları aynı kalır. Kullanıcının sonraki yayın isteğinde release R8 doğrulaması yapılır.

## Kullanım

Video aracındaki **Yazı katmanı** anahtarını açın. Metin, dokuz konum, boyut ve beyaz / marka mercanı / buz mavisi / marka laciverti / siyah seçilebilir. Boyut, çıktı tuvalinin kısa kenarının %2,5–10'u kadardır; videonun çözünürlüğü veya kalite ayarı değiştirilmez. Seçenekler renk örneği ve RGB koduyla gösterilir. Yazıya karşıt bir gölge eklenir.

Başlangıç / bitiş, kesilmiş ve birleştirilmiş videonun **hız değişiminden sonraki çıktı zamanına** göredir. “Sona kadar” dinamik bitiştir; belirli bitiş değeri videonun bitişiyle sınırlandırılır. Başlangıç yeni çıktı süresinin dışında kalırsa işlem açıklamalı olarak engellenir; yazı sessizce atılmaz. Boş metin herhangi bir yazı çizmez. Anahtarı kapatma katmanı kaldırır.

En fazla 200 UTF-16 metin birimi ve altı açık satır kabul edilir; ekran bu sınırı gösterir. Uzun satırlar sistem yazı motoruyla sarılır. Tuvale sığmayan metin otomatik küçültülmez veya üç noktayla kesilmez; kısaltma / yazı boyutu ayarı istenir. Sistem sans-serif bold ve cihazın Unicode / iki yönlü metin desteği kullanılır; ek font paketi yoktur.

**Videoyu işle → çıktı kartından oynat** gerçek önizlemedir. Kaydedilen dosya aynı sonuç dosyasından kopyalanır; ikinci bir video kodlama yoktur. Kaynak oynatma özgün klip / seçilmiş aralık olarak kalır, yazı önizlemesi sayılmaz. Süre açıklaması bunu ekranda belirtir. Bu aşamada sürükleyerek yerleştirme, çoklu yazı / animasyon / özel font veya canlı zaman çizelgesi yoktur.

## Mimari ve korumalar

- `VideoTextLayer`: sınırlı metin / konum / boyut / renk / çıktı aralığı; `VideoEdit` içinde isteğe bağlıdır.
- `VideoTextRenderer` / `VideoTextOverlay`: sistem StaticLayout, yazı sınırlarında tek bitmap ve cached GL texture. Her kare için yeni bitmap, font veya layout oluşturulmaz; görünürlük yalnız alfa ayarını seçer. Export-private bitmap GL configure / release sırasında temizlenir; UI'a verilmiş poster geri dönüştürülmez.
- Overlay, kliplerin Presentation / SpeedChangeEffect işlemlerinden sonra composition effect olarak uygulanır. Klip sınırında zaman sıfırlanmaz. Üç mevcut video hedefi ve mevcut ses / sessizlik akışı korunur.
- V4 atomik oturum, bütün yazı ayarlarını kaydeder. Katmansız V1 / V2 / V3 istek baytları aynı kalır; önceki tamamlanmış iş parmak izleri değişmez. Metin / süre / renk / konum / boyut değişince eski sonuç ve sonuca özel kopya onayı geçersiz olur.
- Yazı varlığında kesilmiş çıktı süresi değişirse uygunluk tekrar hesaplanır; eski zamanlama uygunluğu kullanılmaz. Gerçek bayt büyümesi ve özgün kaynakları koruma kuralları aynıdır.
- Yeni SDK / font / ağ / Jev bağlantısı yoktur. Ticari lisans envanteri aynı 95 runtime artifact ve ayrı LAME encoder kapsamındadır.

## Doğrulama

`tools/testing/video-text-qa.ps1`, yalnız özel `ofice_qa` / `ofice_qa_api35` emülatöründe debug hedefiyle çalışır. QA kurulumunu sıfırlar; kullanıcı telefonu veya release dosyalarını değiştirmez. `tools/testing/verify_video_text_files.py` geliştirme bilgisayarında bağımsız çözümdür; FFmpeg uygulamaya eklenmez.

72 JVM testi geçti; dört yeni test eski istek uyumu, V4 Unicode round-trip / parmak izi, zaman sınırları ve bozuk ayarları kapsar. Üç yeni motor testi ve bir UI testi geçti: .5× / 2×, iki kesilmiş klip, 9:16 crop, H.264 / HEVC / VP8, zaman aralığının klip sınırını geçmesi, gerçek kayıt baytları / özgün dosyalar, bitmap önbelleği / dokuz konum, Unicode yerleşimi, sığmama, iptal temizliği, bütün kontroller, gerçek çıktı oynatma ve yeni ViewModel ile sonuç restorasyonu.

Altı dosyanın her karesi bağımsız çözülerek gerçek PTS zamanında yazı görünürlüğü / renk / yerleşim, merkezde klip sırası, 180×320 tuval / süre, 48 kHz stereo ve 440→660 Hz ses perdesi doğrulandı. Bu QA emülatöründe MediaMetadataRetriever WebM için istenen an yerine önceki keyframe'i döndürdü; WebM zaman doğrulaması bağımsız tüm-kare çözümüyle yapılır. MP4 cihaz kare denetimleri de geçer. İlk teşhis kayıtları `output/development/video-text/diagnostic*` altında tutulur.

22 dilin anahtar / placeholder / üretilmiş kaynak kontrolü ve gerçek lisans envanteri kapsam denetimi geçti. Bu, 22 dilde insan çeviri incelemesi veya tüm cihazlarda glyph garantisi değildir. Fiziksel ARM, düşük RAM, uzun / yüksek çözünürlüklü katmanlı video, dolu disk ve pil / sıcaklık ölçümleri ayrıca bekler.

Sıradaki ürün işi **görsel katmanı**, ardından **kullanıcı sesiyle miks**. Birden çok katman ve ortak zaman çizelgesi sonraki genişletmedir.

Tablet, 320 dp / 2× yazı ve koyu modda üç UI tekrarı geçti. İlk dar ekran testi, tembel ana ekranı kaydırmadan görünmeyen kartı bekliyordu; test kaydırma ile düzeltildi. Katmansız üç hedefli kesme / birleştirme ve tamamlanmış iş / atomik yazma hatası restorasyonu iki ek regresyon testiyle geçti. Lint 0 hata / 42 uyarı; yeni renderer içindeki bir uyarı KTX stil önerisidir. Kanıt: `output/development/video-text/qa-summary.json`, `device.log`, `tablet.log`, `narrow-large-text.log`, `dark.log`, `regression.log`, `independent-text-decode.json`.
