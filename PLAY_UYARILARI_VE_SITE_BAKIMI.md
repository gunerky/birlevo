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

Hazır paket: `output/development/video-edits/site-update/birlevo-site-0.1.9.zip`; kökünde `index.html`, `privacy/index.html`, `en/privacy/index.html` bulunur. İç gizlilik bağlantıları ve sürüm metinleri kontrol edildi; dosya hash'leri aynı klasörde `manifest.json` içindedir. **Canlıya dağıtılmadı:** bu oturumda Cloudflare yayın connector'ı / CLI oturumu bulunmuyor. Kullanıcı tarayıcı işlemlerini kendisinin yapacağını belirtti.

Elle yayın için Cloudflare'da mevcut Birlevo Pages projesini açıp yeni dağıtıma bu ZIP'i yükleyin. Dağıtım tamamlanınca üç URL'de 0.1.9 metnini ve gizlilik bağlantılarını doğrulayın. Yeni bir proje / alan adı açmak gerekmez. Reklam eklenirse site gizliliği, Play reklam / reklam kimliği / veri güvenliği beyanları ve SDK'nın ticari lisans incelemesi aynı sürümde yeniden güncellenir.
