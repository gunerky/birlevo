# Birlevo 0.1.10 — test sürümü

7 Ekim 2026. Kullanıcının isteğiyle **0.1.10 (11)** hazırlandı. Paket kimliği `studio.birlevo.android`, minimum API 24, hedef API 36. Önceki 0.1.9 dosyaları korunur. Google Play'e yükleme veya yayın yapılmadı.

## Dosyalar

- Play AAB: `output/releases/0.1.10/birlevo-0.1.10-11.aab`.
- Aynı kodun imzalı, R8 ve kaynak küçültmesi uygulanmış APK'sı: `output/development/0.1.10/birlevo-0.1.10-11-release.apk`.
- İngilizce / Türkçe Play notları: `output/releases/0.1.10/release-notes.txt`; `<en-US>` / `<tr-TR>` etiketlidir. Ayrı dil dosyaları da bulunur; her biri 500 karakterin altındadır.
- R8 eşleme dosyası: `output/development/0.1.10/mapping.txt`; AAB içindeki metadata ile birebir eşleşir.
- Bu AAB'ye özel native sembol ZIP'i ve kimlik raporu: `output/releases/0.1.10/native-debug-symbols/`.

| Paket | Bayt | SHA-256 |
| --- | ---: | --- |
| AAB | 17.010.364 | `ca7979f75c1d83225b68baa2baefcb621e5eaee89cd985512add413aef355b72` |
| APK | 13.144.188 | `cef25a8d447c3cf346e78c30fe1c1ed7ba3a9488216c9cd6d695b2936d892b87` |

Paketler, raporlar ve anahtarlar Git dışında tutulur. Mevcut Birlevo yükleme sertifikası kullanılır; özel anahtar veya şifre yayın dosyalarına eklenmez.

## Değişiklikler

WAV / M4A yanında MP3 CBR, OGG / Opus ve FLAC PCM16 hedefleri. İki ses kaynağını ayrı kesme, sıralama ve birleştirme; seçilebilir örnekleme / kanal profili. İki video klibini ayrı kesme, sıralama ve birleştirme; ilk klibin oranı, 9:16, 16:9 veya kare tuval; sığdırma / ortadan kırpma; 0,5×–2× hız. Mevcut MP4 AVC / HEVC ve WebM VP8 hedefleri, kalite ve çözünürlük seçenekleri korunur; codec desteği cihaza bağlıdır.

Kaynak önizlemesi ile gerçek üretilen sonuç ayrılır. Varsayılan V1 / V2 taslak baytları korunur; yeni video ayarları V3 atomik kayıtta saklanır. Eşit / daha büyük sonuçta kaydetme, açma, paylaşma veya araç aktarımı için o sonuca özel kopya onayı gerekir; orijinaller değişmez. On dakikalık kaynak yavaşlatıldığında en fazla yirmi dakikalık sonuç ayrı çıktı süresi sınırıyla incelenir / geri yüklenir.

Reddedilen video dosyasının nedeni, önceki kaynak / sonuç korunurken dosya seçiminin hemen altında gösterilir. Eski sonuç geri yüklenirken yeni dosyanın hatasının silinmesi düzeltildi; sonraki kaydetme mesajı önceki içe aktarma nedeninden ayrı gösterilir. Dokuz ekranın sistem çubukları / kesik alan davranışı ve platform açılış animasyonu güncellendi. Play Console'daki eski API uyarısının kalkması, bu yeni AAB yüklenince Console'da ayrıca görülmelidir; eski 0.1.9 raporu yeni paketin sonucu değildir.

## Yükleme

Önce dahili teste sürüm kodu **11** olan bu AAB'yi yükle ve etiketli notları yapıştır. Telefon üzerinde yeni ses hedeflerini, iki klip / hız / kadrajı, çıktıları açma / paylaşma ve uygulamayı yeniden açınca sonuçların geri gelmesini dene. Ardından **aynı AAB'yi** kapalı test kanalına taşı; testçi, ülke ve katılım bağlantısını kontrol et. İncelemeye gönderme işlemini kullanıcı yapar.

Ön plan medya işleme / dataSync uyumluluk yolu sürer. Önceki beyan akışı değişmedi; Console isterse kullanıcı başlatmalı yerel işleme, görünür bildirim, iptal ve uygulamaya dönüşü anlatan erişilebilir demo bağlantısını kullan. 0.1.9 demo kaydı tarihsel olarak o paketi gösterir; yeni sürümü gösterdiği söylenmez.

Bu sürümde reklam SDK'sı, reklam kimliği, Jev bağlantısı veya kullanıcı dosyalarını sunucuya gönderen uygulama yolu yoktur. Reklam eklenince ticari SDK incelemesi ve Play reklam / reklam kimliği / veri güvenliği / gizlilik metinleri birlikte güncellenir. Kamu web sitesi şu anda 0.1.9 kapsamını anlatır; bu paket oluşturma işlemi siteyi veya mağaza metnini otomatik yayımlamaz.

## Lisans ve semboller

95 gerçek runtime artifact koordinatı / dosya hash'i ve ayrı native encoder inceleme kapsamı geçti. MP3 encoder ve JNI köprüsü LGPL altında ayrı dinamik kütüphanelerdir; GPL mpglib decoder derlenmez. Eksiksiz karşılık gelen kaynak ZIP'i uygulamanın lisans ekranından çevrimdışı kaydedilebilir. Lisans incelemesi ülkeye göre codec patent izni yerine geçmez.

Bu AAB'deki sekiz LAME / köprü kütüphanesinin gerçek sembolleri GNU build ID / mevcut kod hash'i ile doğrulandı. Dört AndroidX graphics-path kütüphanesi upstream'de sembolleri çıkarılmış olduğundan toplam kapsam **8/12**; Play'in tüm native dosyalar için uyarısı kalabilir. AAB metadata'sı sembolleri içerir; ayrıca yüklemek gerekirse yalnız sürüm **11** için aynı klasördeki ZIP'i seç. Eski sürüme bu ZIP yüklenmez. Bu işlemde Console'a sembol yüklenmedi.

## Doğrulama

68 JVM testi, release derlemesi / R8 / kaynak küçültmesi, bundletool, APK ve AAB imzaları, gerçek manifest / geliştirme aracı yalıtımı, APK ZIP / AAB config / dört ABI ELF 16 KiB hizalaması ve 22 dil anahtar / placeholder kontrolü geçti. Release lint: **0 hata / 39 uyarı**. Dil kaynak denetimi tüm diller için insan değerlendirmesi sayılmaz. Canlı Jev çağrısı yapılmadı.

Aynı son APK’da 12 temel akış ve üç yeni hedefli akış: toplam **15 R8 iş akışı** geçti; tek başarılı toplu 15-akış koşusu olarak raporlanmaz. Test koşucusunun numaralı kaynak / OGG etiketi, Android’in FLAC için decoded PCM döndürmesi ve büyük yazıda uzun yardım listesinin kaydırma süresi düzeltildi. Gerçek FLAC başlığı ve bağımsız PCM MD5 kontrolü de geçti. Süreç yeniden açma / özel sunum görseli, tablet / dar ekran + 2 kat yazı / koyu modda dokuz tekrar ve beş bağımsız resmi çıktı çözümlemesi geçti. LGPL kaynakları son APK’dan çıkarılıp x86_64 için çevrimdışı yeniden derlendi; yalnız encoder kütüphanesi değiştirilmiş QA kopyası Birlevo yükleme anahtarına erişmeden ayrı yerel anahtarla imzalandı. R8 uygulamasında değiştirilen gerçek native sürüm, ses üretimi / kaydetme / paylaşma ve dört bağımsız ses çözümlemesi geçti; resmi APK geri kuruldu ve orijinal native sürüm tekrar doğrulandı. QA kopyası yayın paketi değildir. Kanıtlar yerel `qa-summary.json` dosyasındadır. R8 Kotlin metadata uyarıları araç zinciri bakımına kaydedilir; gerçek küçültülmüş iş akışları doğrulanmıştır. Yalnız QA emülatörü kullanılır. Fiziksel ARM / eski Android / düşük bellek / dolu disk / pil ve ısınma ölçümleri ayrıca bekler; emülatör sonucu bunlar için garanti değildir. 32×32 / 1 fps aşırı sentetik video yolu önceki geliştirme testinde ilerlemedi; doğrulanmış destek sayılmaz. Otomatik çözünürlük, kare veya kalite düşürme eklenmez.
