# Emülatör doğrulaması — 4 Ekim 2026

İlk Android uygulama temelinin kayıt, dil ve ekran davranışı doğrulandı. Bu rapor medya işleme performansını veya tüm Android cihazlarla uyumluluğu kanıtlamaz.

## Ortam ve sonuçlar

Kurulu `medium_phone` profili kullanıldı: Android 16 / API 36, x86_64, dört sanal CPU çekirdeği, 2 GB yapılandırılmış RAM. `Folklore_API_35` profiline dokunulmadı. AVD silinmedi veya sıfırlanmadı. Boyut, yoğunluk ve yazı büyüklüğü yalnızca emülatörde geçici olarak değiştirildi; sonunda 1080×2400, 420 dpi ve 1.0 yazı ölçeğine döndürüldü.

| Senaryo | Ayarlar | Sonuç |
| --- | --- | --- |
| Telefon | 1080×2400, 420 dpi, yaklaşık 411 dp genişlik | 6/6 başarılı |
| Tablet boyutu | 1920×1200, 240 dpi, 1280×800 dp | 6/6 başarılı |
| Dar ekran / büyük yazı | 1080×2400, 540 dpi, 320 dp genişlik, yazı ölçeği 2.0 | 3/3 başarılı |
| Proje veri testleri | Yerel JUnit | 3/3 başarılı |
| Android lint | Debug uygulama ve testler | 0 hata, 7 bağımlılık sürümü uyarısı |

Tablet senaryosu aynı telefon AVD'sinin ekran ölçülerini değiştirir; gerçek tablet donanımı veya farklı Android sürümü testi değildir. Büyük yazı testi okunabilirlik ve erişilebilirliğin tamamını kapsamaz.

## Doğrulanan davranışlar

- Sunum başlığı, slayt başlığı ve çok satırlı metin Türkçe, Japonca, Arapça, Hintçe ve Çince karakterlerle düzenlendi. Ekran yeniden oluşturulduktan sonra açık taslak korundu. İkinci slayt eklendi, kayıt yapıldı, Activity kapatılıp yeniden açıldı ve kayıtlı içerik doğrulandı.
- Video taslağı oluşturuldu, isimlendirilip kaydedildi ve yeniden açıldı. Bu test video seçme veya video işleme testi değildir.
- 22 uygulama dili hem telefon hem tablet boyutunda ayrı ayrı uygulandı. Ana ekran başlığı, Android'in etkin dil etiketi ve yazı yönü doğrulandı. Endonezce `id` / `in` eşlemesi çalıştı. Arapça ve Farsça sağdan sola açıldı. Bu kontrol tüm ekranların bütün metinlerini veya çeviri kalitesini doğrulamaz.
- Dil menüsünden Türkçe seçimi telefon, tablet boyutu ve büyük yazı senaryosunda çalıştı.
- Dar ekranda tek sütun, geniş ekranda görevler ve projeler yan yana açıldı. Ana görevler ve proje bölümü kaydırılarak erişilebilir kaldı. Türkçe telefon, Arapça tablet ve büyük yazı ekran görüntüleri ayrıca gözle incelendi.
- Geçersiz proje dosyasında yükleme hatası ve yeniden deneme görüldü; test sırasında mevcut dosyanın üstüne yazılmadı.

İlk denemede dil değişimleri aynı Activity üzerinde çok hızlı uygulandığı için test yaşam döngüsüyle yarıştı. Dil kaynak kontrolü her dil için ekranı kapatıp yeniden açacak şekilde düzeltildi; uygulama içindeki dil seçimi ayrı bir kullanıcı akışı testiyle doğrulandı. Son test sonuçlarında hata yok. Uygulama ekranlarına görünmeyen test etiketleri eklendi; bu turda medya optimizasyonu yapılmadı.

## Başlangıç performans ölçümü

Telefon ayarları geri yüklendikten sonra boş proje listesiyle debug APK kullanıldı. `adb shell am start -W -S` ile süreç her ölçüm öncesinde durduruldu. Beş `TotalTime` sonucu: **2093, 1951, 1868, 1890, 1925 ms**. Ortanca **1925 ms**, aralık **1868–2093 ms**. Bunlar Android'in Activity açılış ölçümleridir; tüm içerik hazır olduğunda geçen süreyi ölçen ayrı bir gösterge eklenmedi. Disk ve sistem önbellekleri temizlenmedi.

Son açılıştan iki saniye sonra `dumpsys meminfo`: toplam PSS **76.189 KB**, yaklaşık **74,4 MiB**; toplam RSS **164.708 KB**. Bu tek anlık boş ana ekran örneğidir; bellek sızıntısı, yoğun proje kullanımı veya video işleme sınırı testi değildir.

Debug derleme ve emülatör nedeniyle süreler gerçek telefon ya da yayın sürümü performansı olarak yorumlanmamalıdır. Medya motoru eklendiğinde yayın derlemesiyle çıktı doğruluğu, işlem süresi, en yüksek bellek tüketimi, iptal, düşük depolama ve ısınma koşulları ölçülecek.

## Kanıt dosyaları

Yerel ve Git dışında tutulan `test-artifacts/` klasöründe:

- `phone-results.xml`: telefon test sonuçları; 6 test, 0 hata.
- `tablet-tests.txt`: tablet boyutu sonuçları; 6 test, 0 hata.
- `small-largefont-tests.txt`: dar ekran / büyük yazı sonuçları; 3 test, 0 hata.
- `startup-phone.txt`, `memory-phone.txt`: ham performans çıktıları.
- `phone/home-tr-TR.png`: Türkçe telefon ekranı.
- `tablet-final/home-wide.png`, `tablet-final/home-ar.png`, `tablet-final/home-zh-Hans.png`: geniş ekran örnekleri.
- `large-font/home-compact.png`: iki kat yazı ölçeğindeki dar ekran.

Testler Compose test altyapısı ve Android instrumentation ile gerçek APK üzerinde çalıştırıldı. [Android'in Compose test dokümanı](https://developer.android.com/develop/ui/compose/testing) altyapı referansıdır.

## Sıradaki doğrulamalar

Gerçek dosya seçimi ve kalıcı video erişimi; süreç Android tarafından öldürüldükten sonra açık taslak geri yükleme; Android 24–35 davranışı; gerçek düşük / orta sınıf telefonlar ve tabletler; karanlık tema, TalkBack ve klavye; büyük proje listeleri; sıkıştırma, medya önizleme ve dışa aktarma. Bu turdaki Activity yeniden oluşturma testi gerçek süreç ölümü testi değildir.

Ürünün sonraki geliştirme adımı değişmedi: görsel seç → kalite / çözünürlük ayarla → yeni dosyaya sıkıştır → önce / sonra boyutlarını göster. Video sıkıştırma ardından aynı ölçüm yaklaşımıyla eklenecek.
