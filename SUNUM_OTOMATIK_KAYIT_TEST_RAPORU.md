# Sunumda otomatik kayıt ve slayt yönetimi — 5 Ekim 2026

Çalışma sürümü: **Birlevo 0.1.1 (2)**, `studio.birlevo.android`. Bu geliştirme yerelde tamamlandı; yeni paket Play Console'a gönderilmedi. Önceden yüklenen 0.1.0 (1) AAB korunuyor.

## Eklenen davranış

- Sunum taslağı, düzenleme 600 ms durunca otomatik kaydedilir. Arka plana geçiş ve PDF önizlemesine geçiş ayrıca kayıt başlatır. Kaydet / Geri, son değişiklikleri sakladıktan sonra taslağı kapatır.
- Ekran kaydedilmemiş değişiklikleri, kayıt işlemini, tamamlanan kaydı veya hatayı gösterir. Hata ayrıca altta mesaj olarak görünür. Başarısız kayıtta taslak kapanmaz; yeniden deneme mümkündür.
- Tek kayıt kuyruğu, disk yazımı sürerken yapılan yeni düzenlemeleri sonraki kayda alır. Ekran döndürülmesi kayıt işini iptal etmez; dosya atomik yazılır. Proje listesi okunamıyorsa mevcut dosyanın üstüne yazılmaz.
- Slaytın İşlemler / Actions menüsü çoğaltma, yukarı / aşağı taşıma ve silme sunar. Silme onay ister; tek kalan slayt silinemez. Ekleme ve çoğaltma 100 slaytla sınırlıdır.
- Çoğaltma yeni bir slayt kimliği oluşturur; metin ve görsel referansını kopyalar. Sıralama kimliği ve içeriği korur. Geç dönen görsel seçimi, slaydı liste sırasıyla değil kimliğiyle günceller; kaldırılmış slaydı geri getirmez.
- Dosya şeması sürümü 1 korunur. Eski slaytlarda kimlik bulunmuyorsa proje ve sıra bilgisinden kararlı, farklı kimlikler üretilir; mevcut metin ve görseller değiştirilmez.

## Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| JVM birim testleri | 12/12 geçti: eski JSON, aynı içerikli eski slaytların kimlikleri, kayıt turu, sınırlar, taşıma ve çoğaltma dahil |
| Android 16 telefon emülatörü, tam takım | 33/33 geçti; görsel / video / PDF regresyonları dahil |
| Son hata mesajı ve ekran görüntüsü eklemesinden sonraki odaklı tekrar | Sunum / uygulama 8 test ve otomatik kayıt 3 test: 11/11 geçti |
| Tablet yerleşimi | 1600×2560 px, 240 dpi, yazı 1×; slayt işlemleri / PDF / yeniden açma ve arka plan kaydı: 2/2 geçti |
| Dar ekran, büyük yazı | 720×1440 px, 360 dpi (320 dp genişlik), yazı 2×; aynı görevler: 2/2 geçti |
| Dil kaynakları | 22 dil, 88 anahtar ve biçim yer tutucuları doğrulandı; mevcut dil / RTL cihaz testleri geçti |
| Debug ve release | Derleme ve lint başarılı; her iki lint raporunda 0 hata, 24 mevcut uyarı |
| İmzalı paket | Aynı Birlevo yükleme sertifikası; jarsigner ve Bundletool 1.18.1 doğrulaması geçti |
| AAB içeriği | 0.1.1 / kod 2, debug kapalı, minSdk 24 / targetSdk 36; test örnekleri yok; mevcut güvenli logo ve dört ABI korunuyor; yerel kütüphanelerde 16 KB hizalama geçti |

Yavaş yazım sırasında yeni metin / sıralama / görsel değişikliğinin son kayıtta bulunması, yazma hatasında eski verinin korunması ve yeniden deneme, bozuk proje dosyası varken kayıt yapmama özellikle sınandı. PDF önizlemesi, çoğaltılmış ve sıralanmış gerçek iki slaydı üretti. Silme onayını iptal etmek içeriği korudu. Ekran yeniden oluşturulduktan ve yeni Activity ile uygulama açıldıktan sonra dosyadan aynı içerik yüklendi.

Telefon, tablet ve dar ekran görüntüleri görsel olarak incelendi; menü seçenekleri erişilebilir. Kontrol sonunda emülatör boyutu, yoğunluğu ve yazı ölçeği varsayılanlarına döndürüldü. Fiziksel telefon testi kullanıcı tercihiyle ertelenmiş durumda; emülatör sonucu tüm cihaz kodekleri için garanti değildir.

Tam takım XML sonuçları ve son odaklı / tablet / dar ekran günlükleri `output/development/0.1.1/qa/` altında. Yeni çevirilerin kaynak bütünlüğü doğrulandı; tüm yeni metinlerin ana dili konuşan kişilerce dilsel kontrolü tamamlanmadı.

## Paketler ve sınırlar

- [İmzalı AAB](output/releases/0.1.1/birlevo-0.1.1-2.aab): 12.138.666 bayt. SHA-256: `724eebaed8b256aaa58657317b91387413cd1955b05f2e9bc5258f333231dec9`.
- [Paket doğrulama raporu](output/releases/0.1.1/validation.json).
- [İngilizce Play sürüm notları](output/releases/0.1.1/play-console-release-notes.txt).
- [Geliştirme APK'sı](output/development/0.1.1/birlevo-0.1.1-2-debug.apk): yalnız geliştirme imzası; Play yüklemesi için AAB kullanılır.
- [Telefon menüsü](output/development/0.1.1/qa/phone-slide-actions.png), [tablet menüsü](output/development/0.1.1/qa/tablet-slide-actions.png), [dar ekran / 2× yazı](output/development/0.1.1/qa/narrow-large-text-slide-actions.png).

Otomatik kayıt, sunum taslaklarını kapsar. Video düzenleme oturumunu otomatik kaydetme, tema / hazır düzenler ve ortak medya kitaplığı bu iş paketinde eklenmedi. Yazma tamamlanmadan gerçekleşen ani süreç sonlandırması son değişiklikleri kaybettirebilir; ekrandaki kayıt durumu esas alınır. Sunum görselleri halen dosya referansıdır: kaynak silinirse veya erişim izni kaybolursa görsele ulaşılamayabilir. Slayt silmek kaynak görsel dosyasını silmez.

Reklam SDK'sı, INTERNET ve AD_ID izni eklenmedi; dosyalar cihazda işlenir. Reklam / veri toplama davranışı değişmedi. 0.1.0 AAB'nin SHA-256 değeri tekrar doğrulandı: `35f68f686e49e42d21afb6c4d7d6205b7774e3f64dd7d91aafda0e810812cd33`.

Graphify yerel AST haritası kod değişikliklerinden sonra güncellendi. Beş Kotlin dosyasında (yeni kayıt ViewModel'i ve mevcut dört dosya) ayrıştırıcının kısmi çıkarım uyarıları var; kod derlendi ve ilgili davranışlar testlerle doğrulandı. Grafik sonuçları kaynak kod doğrulamasının yerine kullanılmadı.
