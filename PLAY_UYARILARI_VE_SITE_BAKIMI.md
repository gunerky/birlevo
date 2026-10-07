# Play uyarıları, native semboller ve site bakımı

7 Ekim 2026. Kullanıcının ekran görüntüleri 0.1.7 (8) ve 0.1.9 (10) paketlerinde iki uçtan uca ekran uyarısı bulunduğunu doğruluyor. Bu geliştirme eski AAB'yi değiştirmez; yeni imzalı release üretilmedi.

## Sistem çubukları ve ekran çentiği

0.1.9 R8 mapping eşlemesi: `c.m` = `androidx.activity.EdgeToEdgeApi23`, `c.n` = `EdgeToEdgeApi26`, `c.p` = `EdgeToEdgeApi29`. `K0.g.z` D8 / R8 tarafından birleştirilmiş API köprüsündeki cutout alanı atamasıdır. Ekranda bildirilen renk setter'ları ve `SHORT_EDGES`, eski AndroidX uyumluluk yollarıyla ilişkilidir. `enableEdgeToEdge()` zaten çağrılıyordu; yalnız tekrar eklemek çözüm değildir.

Yeni `SystemBars.kt`, decor'u sistem çubuklarının altına yerleştirir; API 30+ için cutout modu `ALWAYS` olur. API 28–29 varsayılan güvenli cutout / yatay letterbox davranışını korur. Dokuz Scaffold ve üst araç çubuğu sistem çubuğu + display cutout insets'lerini uygular; IME ayrıca mevcut ekran akışında ele alınır. Açık / koyu temada ikon kontrastı insets controller ile ayarlanır. Sistem çubuğu renkleri tema kaynaklarında tutulur; API 24–25 koyu gezinme ikonunu desteklemediğinden açık temada da lacivert gezinme zemini korunur, API 26+ saydam zemini kullanır; uygulama kodu renk setter'ı veya `SHORT_EDGES` çağırmaz.

Eski açılış listener'ı ayrıca deprecated renk setter'larını çağırıyor ve ikon görünümünü sıfırlıyordu. Resmî core-splashscreen 1.2.0 kaynak kodunda da bu yol bulunduğundan kör bağımlılık yükseltmesi yapılmadı. Android 12+ doğrudan platform SplashScreen exit API'siyle aynı 300 ms pencere / logo animasyonunu kullanır. Android 7–11 sistemin statik başlangıç penceresinden ilk içerik karesine kısa geçiş yapar; ikinci splash veya yükleme bekleme süresi eklenmez. Animasyon kapalıysa ve Activity yeniden oluşturulmuşsa geçiş bekletilmez. Compat kütüphanesinin başlangıç tema kaynakları korunur; çalışma zamanındaki eski exit listener kullanılmaz.

Debug QA Android 15 / 16'da içerik alanının pencere kenarlarına ulaşmasını, kontrollerin sistem çubuklarını aşmamasını, cutout modunu, tema ikon kontrastını ve dil dialogunun kullanılabilirliğini kontrol eder. Tablet, dar ekran / iki kat yazı, koyu tema tekrarları ve Android 15'te yatay / çentikli ekran / üç düğmeli gezinme kontrolü ayrı raporlanır.

Play'in statik uyarısının kalktığı henüz iddia edilmez. Kullanıcı sonraki release'i istediğinde R8 sonrası çağrı listesi / mapping ve gerçek APK testleri yeniden kontrol edilir; yeni AAB Play'e yüklendikten sonra Console sonucu doğrulanır. [Android 15 davranışı](https://developer.android.com/about/versions/15/behavior-changes-15), [kenarlara yerleşim ve insets](https://developer.android.com/develop/ui/views/layout/edge-to-edge), [platform splash API'si](https://developer.android.com/reference/android/window/SplashScreen).

## Native hata ayıklama sembolleri

Dondurulmuş `output/releases/0.1.9/birlevo-0.1.9-10.aab` yalnız AndroidX graphics-path'in dört önceden stripped kütüphanesini içeriyor. Pakette veya mevcut yerel önbellekte bunların gerçek `.symtab` / DWARF sembolleri yok: **0/4 yüklenebilir kütüphane**. Dinamik export isimleri gerçek debug sembolü sayılmadı. Başka derlemeden alınan veya aynı kaynaktan yeniden derlenen kütüphane sembolleri bu eski pakete yüklenmez; kaynak proje ve üretim binary'si aynı şey değildir. Eski uyarı bu nedenle yerel olarak tamamen kapatılamadı.

Güncel debug APK'da LAME encoder ve JNI köprüsünün dört ABI'deki **8/8** kütüphanesi için gerçek DWARF / symbol table eşleşmesi doğrulandı. graphics-path'in dört kütüphanesi için eksik upstream sembol durumu sürer: tüm native dosyalarda **8/12** kapsam. Bu debug sembolleri 0.1.9'a yüklenmedi ve yüklenmemelidir.

`tools/release/prepare_native_symbols.py` mevcut APK / AAB'nin SHA-256'sını, her kütüphanenin GNU build ID'sini ve kod bulunduğunda `.text` hash'ini kontrol eder. AAB içindeki `BUNDLE-METADATA/...debugsymbols` veya açıkça seçilmiş yerel unstripped dosyalardan yalnız eşleşen gerçek sembolleri ABI dizinli ZIP'e koyar. FULL debug dosyasının kod bölümü NOBITS ise aynı GNU build ID yine zorunludur. Gerçek NDK `--only-keep-debug` dosyası kabul ve değiştirilmiş build ID ret denemeleri geçti. ZIP adı paket hash'ini içerir; JSON rapor hangi dosyaların kapsandığını gösterir.

`ndk.debugSymbolLevel = FULL` mevcut release ayarıdır. İmzalı build yardımcısı artık bu **aynı AAB'de** sahip olunan native sembollerin eşleşmesini de zorunlu doğrular. Kullanıcının sonraki yayın isteğinde `app/build/outputs/native-debug-symbols/release/verified/` altında doğrulanmış ZIP / rapor hazırlanır. AAB metadata'sındaki semboller Play'e paketle birlikte gider; gerekirse doğru sürümün **Test ve yayın → Son sürümler ve paketler / App bundle explorer → İndirilenler / Öğeler → Native debug symbols** alanına yalnız o paketin ZIP'i elle yüklenir. Bu oturumda Play'e sembol yüklenmedi. [Android paketleme](https://developer.android.com/build/include-native-symbols), [Play sembol yükleme](https://support.google.com/googleplay/android-developer/answer/9848633?hl=en).

## birlevo.com

Ana sayfa, `/privacy/` ve `/en/privacy/` HTTPS üzerinden 200 döndürdü. Canlı metin hâlâ 0.1.7'ye ve fotoğraf PDF seçiminin süreç kapanınca kaybolduğu eski davranışa dayanıyordu. Yerel üç HTML sayfası dondurulmuş 0.1.9'ın gerçek özelliklerine göre güncellendi: mevcut ses / PDF / belge araçları, özel proje görselleri, kalıcı kaynak / ayarlar / sonuçlar, referanslı dosyaların temizleme dışında tutulması ve açma / paylaşım önbelleği açıklandı. Geliştirme kodundaki yayımlanmamış MP3 / OGG / FLAC, iki ses kaynağı veya iki video klibi siteye eklenmedi.

7 Ekim'de mevcut `birlevo` Cloudflare Pages projesine yayımlandı. Mavi şerit ve favicon düzeninin ilk üretim dağıtımı: `44bc220e-3a94-4450-a0db-d8e64a019679`. Ana sayfa kullanıcının tarif ettiği sade mavi üst şerit düzenine getirildi; gizlilik sayfalarının mevcut tasarım dili korundu. Daha eski ana sayfanın birebir kaynak kopyası bulunamadığından bu kayıt tam tarihsel tasarım restorasyonu iddiası taşımaz.

Üç sayfa ve beş favicon HTTPS üzerinden doğrulandı. HTML karşılaştırması yalnız bilinen Cloudflare e-posta korumasını ve otomatik script eklerini normalleştirir; diğer metin ve CSS aynı olmalıdır. İkonların indirilen baytları yerel dosyalarla eşleşir. Rapor, canlı ekran görüntüsü ve sekiz dosyalık güncel ZIP: `output/development/video-canvas-speed/`. Önceki üç sayfalık ZIP tarihsel hazırlık paketidir.

Logo uygulamanın mevcut görselinden imagegen düzenleme modu ile türetildi. Dış lacivert kare şeffaflaştırıldı; içerideki lacivert oynat simgesi, beyaz şekil, camgöbeği köşe ve mercan şerit korundu. PNG 16 / 32 / 48 / 192 ve ICO 16 / 32 / 48 dosyaları `web/` altındadır. Büyük şeffaf kaynak `output/development/video-canvas-speed/favicon-master.png` içinde tutulur; uygulamanın simgesi değiştirilmedi.

Kullanılan imagegen istemi:

> Edit the provided existing Birlevo logo for a website favicon. Remove ONLY the navy blue outer square background, leaving a genuinely transparent alpha background outside the logo. Preserve the exact existing logo silhouette and geometry: a rounded white folded document shaped like B, cyan blue folded top-right corner, coral-red curved lower diagonal ribbon, and the navy right-pointing play triangle inside. Keep the internal navy play triangle opaque for contrast. Do not redesign the mark, do not change colors, do not add text, shadow, badge, frame, or extra elements. Tight square framing around the complete mark with only about 5 percent transparent padding, centered and large for readability at 16 and 32 pixels. Sharp clean edges, production brand asset.

Reklam eklenirse site gizliliği, Play reklam / reklam kimliği / veri güvenliği beyanları ve SDK'nın ticari lisans incelemesi aynı sürümde yeniden güncellenir. Yayımlanmamış geliştirme özellikleri hâlâ kamu sayfasında ilan edilmez.

## İngilizce ana sayfa — 7 Ekim

Ana sayfanın başlığı, açıklamaları, meta açıklaması ve `lang` alanı İngilizce yapıldı. Kullanıcının isteğiyle alt bölümde Türkçe gizlilik politikası bağlantısı kaldırıldı; İngilizce Privacy Policy ve destek bağlantısı kaldı. Mavi üst şerit, CSS, şeffaf favicon ve 0.1.9 özellik kapsamı korundu. Mevcut iki gizlilik sayfasının URL ve içerikleri korunur.

Güncel üretim dağıtımı: `b6a5867d-b89d-4e96-9ae6-4fffaf257470`. Cloudflare üretim durumu success; üç canlı sayfa kaynaklarla eşleşti. Tarayıcıda İngilizce metinler ve iki alt bağlantı doğrulandı; ekran görüntüsü / rapor / sekiz dosyalık ZIP `output/development/site-english/` içinde. Uygulama sürümü veya APK / AAB oluşturulmadı.
