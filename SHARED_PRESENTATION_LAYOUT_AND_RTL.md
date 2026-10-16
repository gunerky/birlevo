# Sunum ortak yerleşimi ve RTL — 13 Ekim 2026

Sunum ölçümü `PresentationLayout` bileşenine ayrıldı. Editör önizlemesi, PDF, slayt görseli, sunum oynatıcı, slaytlardan MP4 ve görünümü koruyan PPTX aynı ölçülmüş düzeni mevcut `PresentationPdf` çizicisi üzerinden kullanır. Yeni bir ikinci metin sığdırma algoritması üretime eklenmedi. Kayıt şeması, tema/düzenler, kalite, çözünürlük, not tercihi ve FileProvider kökleri değişmez. Düzenlenebilir PPTX veya genel Office içe aktarma bu adımla açılmadı.

## Ortak ölçüm

960 × 540 slayt koordinatlarında başlık, gövde ve tekrar eden proje etiketi ölçülür. Her metin bloğu gerçek yazı boyutu, kalınlık, istenen Android fontunun metrikleri, konum, genişlik ve yüksekliği verir. Satırların özgün metindeki UTF-16 başlangıç/bitişleri, görünür bitişi, yatay sınırları, taban çizgisi ve paragraf yönü ihtiyaç halinde alınır. Video çizimi bu satır listesini her karede oluşturmaz; global veya sınırsız önizleme önbelleği eklenmedi.

Şekillendirme ve satır kırılması Android'in [StaticLayout](https://developer.android.com/reference/android/text/StaticLayout) motorundadır. [Paragraf yönü](https://developer.android.com/reference/android/text/Layout#getParagraphDirection(int)) her satır için ayrı kaydedilir. Karışık Latin/Arapça/Farsça metin mantıksal sırada korunur; karakterler ters çevrilmez. İlk güçlü karakter yönü belirler, yönsüz paragraf LTR'a döner. Görselin fiziksel solda/üstte olması açık slayt düzeni seçimine bağlı kalır, arayüzün RTL yönü bu seçimi değiştirmez.

Font dil tercihi `Locale.ROOT` ile arayüz dilinden ayrıldı. Yazı ölçeği ve ekran yoğunluğu yalnız arayüzü etkiler; çıktı ölçüleri aynı slayt koordinatlarında kalır. Bu, cihazlar arasında aynı fontu garanti etmez: Android'in gerçek glif fallback dosyaları bu metaveride tanımlanmaz ve dosyaya font gömülmez. Yeni font/SDK dağıtılmadı. Paragraf yönü metaverisi, bütün bidi koşularının başka bir Office renderer'ında aynı çizileceği kabulü değildir.

Önceki başlangıç/minimum yazı boyutları ve sığdırma aralıkları korunur. Başlık/gövde minimumda da sığmıyorsa `PdfTextOverflow` hatası sürer; içerik kırpılmaz ve yarım çıktı temizlenir. Yalnız tekrar eden proje etiketi önceki gibi kısaltılabilir. UI'a teslim edilmiş bitmap zorla geri dönüştürülmez.

## Tamamlanan debug/emülatör doğrulaması

Yalnız ayrılmış `ofice_qa`, Android API 36 kullanıldı:

- Yeni dört senaryo telefon/İngilizce, %200 yazı/Arapça ve tablet/Farsçada geçti: **12 test yürütmesi**. Mantıksal metin ve ayrı paragraf yönleri, taşma/temizlik, 22 dil örneğinin ölçümleri, karma yazılı PDF/önizleme/PNG eşleşmesi kapsanır.
- **66 ölçüm kaydı** (22 örnek × 3 düzen) üç koşulda eşittir. Bu dil örnekleri tüm çevirilerin veya tüm glif kombinasyonlarının insan onayı değildir.
- Üç tema × üç düzende karma Arapça/Farsça, Latin, Japonca/Çince/Korece, Hintçe ve Tayca PDF/PNG üretildi; örnekler görsel olarak incelendi. PNG gerçek ARGB pikselleriyle karşılaştırılır; `Bitmap.sameAs` depolama/alpha bayrak farkı içerik farkı sayılmaz.
- Mevcut tasarım, PDF, slayt görseli/oynatma ayarları, PPTX ve MP4 yollarında **21 regresyon testi** geçti. 1024/2048/4096 PPTX ve 1920 MP4 dahil mevcut çözünürlükler korunur.
- Değişiklik öncesinde üretilen dokuz tema/düzen PNG'si güncel motorla **piksel piksel aynı** kaldı. Karşılaştırma her RGBA kanalını kontrol eder.
- Debug APK/test APK derlemesi, **106 JVM testi** ve lint geçti: 0 hata, 61 uyarı, 2 ipucu. 22 dil anahtar/format/kaynak kontrolü ile 95 runtime artifact + ayrı native encoder lisans/hash kapsamı geçti. Grafik yerel AST ile güncellendi; bilinen 17 kısmi Kotlin ayrıştırma uyarısı kaynak/derleyici/test kanıtının yerine kullanılmadı.

Koşucu `tools/testing/presentation-layout-qa.ps1`; host karşılaştırması `tools/testing/verify_presentation_layout.py --baseline <önceki-qa-klasörü> --after <yeni-qa-klasörü> --report <json>`. QA ekran/font ayarları sonunda geri yüklenir. Ham günlükler, görseller ve karşılaştırma JSON'u `output/development/presentation-layout/` altında Git dışında tutulur. Testlerin ilk PNG denemesinde görülen bitmap depolama eşitliği hatası teşhis günlüğünde korunur; başarı sayılarına dahil edilmez.

Son debug APK SHA-256: `23f39df656a209006a002fb774cb3c924503d616750d5adcb3b1bc44e4fb1751`. Yeni yayın paketi/sürüm numarası yoktur. 0.1.11 (12) AAB SHA-256 hâlâ `a9917105ed2bbe1564cf40da92280ed26f61571d56f43142d6f02aae896876d5`. Release/R8 kontrolü sonraki kullanıcı derleme isteğindedir. Fiziksel cihaz performansı ve gerçek PowerPoint doğrulaması yapılmış sayılmaz; PowerPoint kurulumu kullanıcıya bekleyen görev değildir.

Sıradaki iş ölçüm modelini kullanarak **düzenlenebilir PPTX kabul alt kümesi** hazırlamaktır. Font eşleme, karışık yönlü koşular ve bağımsız okuyucuda gerçek yeniden düzenleme/satır kırılması ayrı kanıt ister. Önceki test prototipinin fit kopyası üretim yolunda kullanılmaz; bu sonraki denemede ortak modelle değiştirilmelidir. Uyum sağlanmayan içerik sessizce sadeleştirilmeyecek; görünümü koruyan mevcut seçenek erişilebilir kalacak.
