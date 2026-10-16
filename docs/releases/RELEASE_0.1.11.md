# Birlevo 0.1.11 — test sürümü

10 Ekim 2026. Kullanıcının isteğiyle **0.1.11 (12)** hazırlandı. Paket kimliği `studio.birlevo.android`, minimum API 24, hedef API 36. Önceki 0.1.10 dosyaları korunur. Google Play'e yükleme veya yayın yapılmadı.

## Dosyalar ve değişiklikler

- Play AAB: `output/releases/0.1.11/birlevo-0.1.11-12.aab`.
- İmzalı, R8 / kaynak küçültmesi uygulanmış APK: `output/development/0.1.11/birlevo-0.1.11-12-release.apk`.
- İngilizce / Türkçe Play notları: `output/releases/0.1.11/release-notes.txt`; `<en-US>` / `<tr-TR>` etiketlidir ve her dil 500 karakterin altındadır.
- R8 eşlemesi: `output/development/0.1.11/mapping.txt`; AAB metadata'sıyla aynıdır.
- Gerçek native sembol raporu ve gerekirse kullanılacak ZIP: `output/releases/0.1.11/native-debug-symbols/`.

| Paket | Bayt | SHA-256 |
| --- | ---: | --- |
| AAB | 17.549.799 | `a9917105ed2bbe1564cf40da92280ed26f61571d56f43142d6f02aae896876d5` |
| APK | 13.483.080 | `d1924881005d06aa7f4df643433938d3c183572dcfcb406ea765945720c5f881` |

Dosya odaklı ana sayfa ve bölümlere ayrılmış çalışma alanları; zamanlı video yazısı, görsel ve ses katmanları; video karesi çıkarma; görsel/slaytlardan MP4; tam ekran sunum, konuşmacı notları ve zamanlayıcı; slayt görseli ve görünümü koruyan PPTX çıktısı bu test sürümündedir. Mevcut biçim, kalite ve çözünürlük seçenekleri korunur. PPTX slaytları tek görseldir; metin/nesneler ayrı düzenlenemez. Notlar varsayılan kapalıdır. Genel Office içe aktarma desteği açılmaz. PPTX kayıt mesajı dosya çıktısını doğru adlandırır.

Gerçek boyut karşılaştırması ve eşit/büyük çıktının kaydı, açılması, paylaşılması veya aktarılması için sonuca özel onay korunur. Eski kayıtlar ve FileProvider yetki/kökleri korunur. Orijinal dosyalar değiştirilmez.

## Lisans, semboller ve yükleme

95 gerçek runtime artifact / dosya hash'i ve ayrı native encoder lisans denetimi geçti. Yeni Office SDK'sı eklenmedi. LGPL LAME encoder/köprü ayrı dinamik kütüphanelerdir; GPL mpglib decoder derlenmez. Eksiksiz karşılık gelen kaynak, lisans ve build/JNI bilgileri uygulamada çevrimdışı kaydedilebilir. Açık kaynak incelemesi ülkeye göre codec patent izni yerine geçmez.

AAB gerçek sekiz LAME/köprü kütüphanesinin doğrulanmış sembollerini zaten içerir. Dört AndroidX graphics-path dosyası upstream'de sembolleri çıkarılmıştır; kapsam **8/12** olduğundan Play'in genel uyarısı kalabilir. Ayrı ZIP'i rutin olarak yüklemek gerekmez; Console isterse yalnız bu sürüm koduna ait ZIP kullanılır. Önceki sürümlerin sembolleri kullanılmaz. Console'a bu işlemde yükleme yapılmadı.

Önce dahili teste sürüm kodu **12** olan AAB'yi yükle, etiketli notları yapıştır ve cihazında temel akışları dene. Ardından **aynı AAB'yi** kapalı teste taşı; testçi/ülke/katılım bağlantısı ayarlarını kontrol et. İmzalı APK yerel deneme içindir. Paketler ve anahtarlar Git dışında tutulur; mevcut yükleme sertifikası kullanılır.

Reklam SDK'sı, reklam kimliği veya Jev bağlantısı eklenmedi. Reklam eklenince ticari SDK incelemesi ve Play reklam / reklam kimliği / veri güvenliği / gizlilik metinleri birlikte güncellenir. Site veya mağaza metni bu derleme işlemiyle otomatik yayımlanmaz.

## Doğrulama durumu

106 JVM testi, release derlemesi/R8, imza ve bundletool, APK ZIP/AAB config/dört ABI ELF 16 KiB hizalaması, exact-source MP3 artifact ve geliştirme aracının paketten yalıtımı geçti. Release lint: **0 hata, 62 uyarı, 2 öneri**. 22 dil anahtar/placeholder kontrolü geçti; bu insan çeviri değerlendirmesi değildir. Canlı Jev çağrısı yapılmadı.

İmzalı pakette **18 R8 iş akışı** geçti: görsel, video, destek/hata bildirimleri ve sunum PDF'si tamamlanan bir toplu denemede; diğer 14 akış başarılı hedefli koşularda doğrulandı. Tek başarılı toplu 18-akış koşusu olarak raporlanmaz. Yeni arayüzün kategori/panel, sonuca özel kopya onayı, aktarım dönüşü ve kayıtlı tercihleri platform koşucusuna uyarlandı. Başarısız uyarlama denemeleri ayrı loglarda korunur.

Süreç yeniden açma/özel görsel geri yüklemesi, tablet ve koyu modda yeni sunum oluşturma/düzenleme/PPTX/MP4/slayt görseli akışları geçti. Dar ekran + yüzde 200 yazıda **kayıtlı iki slaytlık sunumun çıktı kontrolleri** geçti; bu modda otomatik taslak formu doldurma kontrolü geçmedi ve ayrıca bekler. Toplam **dokuz ekran modu/akış tekrarı**, yüzde 200 yazıda yeni taslak oluşturmayı kapsadığı şeklinde genellenmez. Manuel büyük yazı denemesinde slayt paneli açılıp kaydırıldı; bütün formun uçtan uca doğrulaması sayılmaz.

Beş sentetik medya çıktısının bağımsız çözümlemesi, ses süre/profil/ton sırası ve iki klipli videonun kare sırası geçti. Yeni iki PPTX'in görselleriyle altı saniyelik slayt MP4'ünün **180 çözümlenmiş karesi** ayrıca karşılaştırıldı. Bu, gerçek Office kontrolünün yerine geçmez. Son pakete ait hash ve kanıtlar `output/releases/0.1.11/qa-summary.json` içinde tutulur.

Son APK'nın çevrimdışı LGPL kaynakları x86_64 için yeniden derlendi; yalnız encoder değiştirilen QA kopyası ayrı yerel Android anahtarıyla imzalandı. Gerçek native sürüm değişikliği, R8 uygulamasında üç çıktı biçimi/kesme/birleştirme/kayıt/paylaşma ve dört bağımsız ses çözümlemesi geçti. Resmi APK geri kuruldu, özgün encoder sürümü tekrar doğrulandı. Bu QA kopyası yayın dosyası değildir.

Gerçek PowerPoint/LibreOffice kontrolü bekler; altı Android PPTX/24 slayt için bağımsız okuyucu kanıtı ve çevrimdışı Office kontrol paketi [ön kontrolde](../testing/PPTX_PRE_RELEASE_CHECKS.md) bulunur. Fiziksel ARM/eski Android/düşük bellek/dolu disk/pil/ısınma ve tüm dillerin insan değerlendirmesi bekler. Emülatör sonuçları fiziksel cihaz performans garantisi değildir. R8 Kotlin metadata uyarıları araç zinciri bakımında kalır.
