# Düzenlenebilir PPTX alt kümesi — 14 Ekim 2026

Bu ilk Latin alt kümesinin kapsam ve test kaydıdır. Aynı gün tamamlanan [RTL devamı](PPTX_RTL_VE_FONT_KAPSAMI.md) modern Arapça/Farsça ve karışık metni ayrıca açar; aşağıdaki Latin sınırlaması tarihsel ilk aşamaya aittir.

PPTX ekranına **Görünümü koru / Düzenlenebilir** seçimi eklendi. Varsayılan görünümü koruyan çıktı önceki gibi bütün slaydı tek görsel olarak taşır. Düzenlenebilir seçenek mevcut Birlevo sunumunun başlık, gövde, proje etiketi, sayfa numarası ve görselini ayrı nesneler olarak yazar. Bu, genel PPTX içe aktarma veya tablo/grafik/animasyon desteği değildir; mevcut proje modeli korunur.

## Kabul edilen kapsam

- Görünür metinde Latin harfleri, Latin aksanları/birleştirici işaretleri, rakamlar ve seçilmiş yaygın noktalama. Üç tema ve üç mevcut slayt düzeni; görselli ve metin ağırlıklı slaytlar.
- Başlık, gövde ve tekrar eden etiket konumu/yazı boyutu ortak `PresentationLayout` ölçümünden gelir. Gövde satır aralığı ölçülen taban çizgilerine dayanır; ikinci bir fit algoritması yoktur. Kaynak metnin paragraf sırası ve karakterleri korunur.
- Görsel mevcut decoder ile yönlendirilir, oranı korunarak kendi kutusuna yerleşir ve PNG olarak yazılır. Seçilen 1024/2048/4096 sınırı bu modda görsellerin piksel boyutuna uygulanır. Metin vektör nesnesi olarak kalır. Yalnız tek kaynak bitmap tutulur; tam slayt rasteri PPTX'ye eklenmez.
- Notlar varsayılan kapalıdır; açık seçilirse ayrı notesSlide parçalarıdır ve Unicode not metni korunur. Not içeriği görünür metin kapsam filtresine tabi değildir. Açıklaması alıcının bunları okuyabileceğini belirtir.

Arapça/Farsça ve diğer doğrulanmamış yazılar, emoji ve bidi yön kontrolü varsa düzenlenebilir seçim açık bir açıklamayla kapatılır. Motor aynı koşulu ayrıca doğrular; karakterleri silmez, ters çevirmez veya otomatik rastere dönüştürmez. Kullanıcı **Görünümü koru** ile mevcut bütün sunum yazılarını dışa aktarabilir. Bu sınır yalnız yeni seçenek içindir; önceki yetenekler azaltılmaz.

## Font, görünüm ve önizleme

Çıktı Arial adını ister; font dosyası gömülmez. Android'in ölçtüğü sistem fontu ve başka okuyucunun seçtiği font aynı olmak zorunda değildir. Satır kırılması, sığma ve yerleşim başka uygulamada değişebilir. Bu bilgi mod seçiminde gösterilir. Düzenlenebilir sonuç için uygulamadaki resim **yerleşim referansı** olarak etiketlenir; açılan PPTX'nin gerçek renderer önizlemesi olarak sunulmaz.

Kaynakta minimum boyutta da sığmayan metin yine hata verir; başarılı çıktı diye kırpılmaz. Başka uygulamada sonradan metni uzatan kullanıcı gerekirse metin kutusunu, font boyutunu veya diğer nesnelerin konumunu ayarlamalıdır. QA'da uzun bir değiştirilmiş başlığın gövdeyle çakıştığı görüldü; tek başlık düzenlemesinin bütün nesneleri otomatik yeniden akıtacağı iddia edilmez. Bağımsız roundtrip kanıtı kutuya sığan bir başlık değişikliğiyle tamamlandı.

OOXML yazımı mevcut, kendi yazdığımız ZIP/XML paketleyicisine dayanır. [PresentationML metin gövdesi](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.presentation.textbody) ayrı metin nesnelerini tanımlar. Yeni Office SDK, codec veya dağıtılan font yoktur. Gerçek runtime lisans envanterindeki 95 paket ve ayrı native encoder hash kapsamı yeniden geçti. Geliştirme okuyucusu yalnız `tools/pptx/` içindedir; Android/Gradle/CI bağımlılığı değildir. Önceki ticari inceleme [PPTX uyumluluk/lisans kaydında](PPTX_UYUMLULUK_VE_LISANS_DENEMESI.md) korunur.

## Dayanıklılık ve eski kayıtlar

Eski v1 PPTX isteklerinde `mode` yoksa **Görünümü koru** kabul edilir. Bu modun JSON'una yeni alan eklenmedi; eski iş fingerprint'leri değişmez. Düzenlenebilir istek açık mod alanı içerir. Kaydedilen dosyanın mod işareti ve SHA-256 doğrulanır; yanlış modla geri yükleme reddedilir. Mod/ayar/içerik değişimi önceki sonucu geçersiz kılar. Ön plan işi, iptal, atomik `.part` kaydı, kaynak üstüne yazma reddi, gerçek çıktı boyutu ve açma/paylaşma doğrulaması mevcut yolları kullanır. Proje kayıt şeması ve FileProvider kökleri değişmez.

## Doğrulama

Yalnız ayrılmış `ofice_qa`, Android API 36, debug APK:

- **12 özellik/regresyon testi**: ayrı nesneler, üç tema/düzen, görsel yönü/PNG, dahil/haricî notlar, desteklenmeyen yazı/emoji/yön kontrolü, eski v1 istek, mod kimliği/geri yükleme, iptal/taşma temizliği; mevcut raster çözünürlükler, kalıcı iş ve seçenek/süreç geri yükleme. Arayüzde mod değiştirme eski sonucu temizler ve desteklenmeyen yazıda görünümü koruyan seçim erişilebilir kalır.
- Arapça %200 yazıda **2**, Farsça tablet düzeninde **2** kontrol geçti: toplam **16 emülatör test yürütmesi**. Örnek ekranlar görsel olarak incelendi.
- Android'de üç tema × not açık/kapalı **6 PPTX / 24 slayt** üretildi. Bağımsız Artifact Tool okuyucusu gerçek başlık/gövde/notları doğruladı, bütün slaytları çizdi. Bir başlık o okuyucuda düzenlendi; yeniden kaydedilen dosya tekrar açılıp yeni metin ve notların korunduğu doğrulandı. Orijinal dosyalar değiştirilmedi. Bu **gerçek PowerPoint testi değildir**; kullanıcı isteğiyle PowerPoint kurulumu veya fiziksel cihaz istenmedi.
- Debug APK/test APK, **106 JVM testi** ve lint başarılı: 0 hata, 62 uyarı, 2 ipucu. Beş yeni metin 22 dilde eklendi; anahtar/format/kaynak eşleşmesi geçti. Çeviriler bütün dillerde insan semantik değerlendirmesi sayılmaz. Yerel AST grafiği güncellendi; kısmi Kotlin parser sınırları test kanıtının yerine kullanılmaz.

Koşucu `tools/testing/pptx-editable-qa.ps1`; bağımsız okuyucu `tools/pptx/verify_editable.mjs`. Ham günlükler, sentetik dosyalar, görüntüler ve JSON doğrulaması `output/development/pptx-editable/` altında Git dışındadır. Jev veya kullanıcı medyasını dış servise yükleme yoktur.

Debug APK SHA-256: `9b57132af3cd29d88b0462c9bb76d62db0acba31bdb5ccac9c18895a376cd873`. Yeni sürüm/release üretilmedi; 0.1.11 (12) AAB hash'i `a9917105ed2bbe1564cf40da92280ed26f61571d56f43142d6f02aae896876d5` kaldı. Yeni kod için küçültülmüş release/R8 doğrulaması kullanıcı derleme istediğinde yapılır. Emülatör kanıtından fiziksel cihaz performansı veya genel Office uyumluluğu türetilmez.

Sonraki uyumluluk işi karışık RTL/Latin koşuları ve font eşlemeleridir; yeni bir yazı sistemi ancak ayrı yeniden düzenleme/render kanıtıyla bu seçeneğe alınacaktır. Genel Office içe aktarma zengin öğe modeli ve açık kayıp önizlemesi gerektiren ayrı iştir.
