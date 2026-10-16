# PPTX uyumluluk ve ticari lisans denemesi

9 Ekim 2026. Deneme tamamlandı; uygulamada genel PPTX desteği açılmadı. Prototip yalnız `androidTest` içindedir. Üretim kodu/bağımlılıkları, kayıt şeması, destek listesi ve imzalı **0.1.10 (11)** değişmedi; yeni release hazırlanmadı.

## Sonuç ve sonraki ürün işi

Dört yapay slayt: Türkçe/İngilizce, Arapça/Farsça, Japonca/Çince/Korece ve Hintçe; bir asimetrik PNG, üç mevcut düzen ve üç tema. Android'de **6 PPTX / 24 slayt** üretildi; bağımsız Artifact Tool 2.8.89 okuyucusunda açılıp çizildi. Ayrı dört slaytlık referansta gerçek OOXML tablo/grafik vardır.

| Yol | Doğrulanan | Açık sınır |
| --- | --- | --- |
| Düzenlenebilir metin + PNG | Unicode, satır sonu/sıra, ayrı konuşmacı notları, kaynak PNG bayt/hash eşitliği; bağımsız okuyucuda başlığı değiştirip PPTX kaydetme | Font/satır kırılması değişebilir. RTL ve sağ hizalama yazıldığı halde bu okuyucuda Arapça/Farsça sözcük sırası Android görünümünden farklı; kabul edilmedi. |
| Bütün slaydı PNG olarak taşıma | Gerçek 2048×1152 raster, üç tema/düzen; bağımsız 960×540 render ile görünüm karşılaştırması | Görünür metin/öğeler tek raster; ayrı düzenlenemez. Daha yüksek çözünürlük kaynak maliyetini artırır. |
| Harici PPTX içerik envanteri | İlişkilerden gerçek sıra, metin/not ayrımı, görsel hashleri, tablo/grafik ve diğer kayıp bayrakları | Yerleşim renderer'ı veya Birlevo projesine import değildir. `general_import_ready=false` kalır. |

Raster PNG, bağımsız render boyutuna Lanczos ile küçültüldü. RGB RMS farkı 0–255 ölçeğinde **1,529–2,504**; küçük yeniden örnekleme farkıdır, piksel eşitliği iddiası değildir. Üç tema karşılaştırması ve referans önizlemeleri incelendi. RTL farkı üç temada da var; diğer örneklerde eksik glif görülmedi. Bu sekiz dil örneği bütün 22 dilin onayı sayılmaz.

**Sıradaki iş: mevcut sunumlar için “Görünümü koru” PPTX dışa aktarma motoru/ekranı.** 1024/2048/4096 seçimi, açık düzenlenebilirlik kaybı, ayrı not ekleme tercihi, gerçek boyut, iptal, kayıt ve sonuç geçerliliği mevcut yapıya bağlanacak. Bu denemede yalnız 2048 raster ölçüldü; diğer prototip boyutları henüz ürün desteği sayılmaz. Düzenlenebilir yol ortak yerleşim metaverisi, font/RTL ve gerçek Office doğrulamasından sonra ayrı seçenek olacak. Yeni sunum dosyası oluşturma sıkıştırma başarısı diye sunulmayacak.

Harici zengin PPTX, başlık/gövde/tek görsele sessizce indirgenmeyecek. Tablo, grafik, grup, çoklu görsel, kırpma/dönüş, animasyon/geçiş, gömülü nesne ve medya için kayıp önizlemesi gerekir. Master/tema etkileri ayrıca incelenmeli. Tam sadakat daha zengin öğe modeli veya ayrı renderer gerektirir; PDF'den kayıpsız düzenlenebilir PPTX bu adımın kapsamında değildir.

## Teknik yapı ve input sınırları

- `PptxTrialExporter`: Android/Java ZIP, mevcut `PresentationPdf.renderSlideBitmap` ve üretilmiş boş test şablonunun master/layout/theme parçaları. Slaytlar sırayla yazılır; aynı anda tek raster hazırlanır. PNG kaynağı yeniden kodlanmaz; notlar notesSlide parçasına gider. Üründe notları dosyaya ekleme açık tercih olacak.
- Metin fitting, yalnız test içinde mevcut Android ölçümünü tekrarlar. Üretime ikinci algoritma kopyalanmayacak; PDF/video/görsel/PPTX ortak ölçülmüş yerleşim kullanmalı. Font ismi yazmak başka okuyucunun aynı font/bidi davranışını garanti etmez.
- İptal/geçersiz XML sonrası yarım dosya silinir, kaynak/proje değişmez. Prototip yalnız bilinen yerel PNG'yi kapsar; genel URI, HEIC, EXIF ve alfa ölçülmedi. Üretimde özel proje görselleri, tek yazıcı/atomik kayıt ve mevcut FileProvider kökleri korunacak.
- `inspect_pptx.py` yalnız host envanteridir. ZIP'i diske açmaz, dış ilişkiyi indirmez, komut/makro çalıştırmaz. UTF-8 Transitional OOXML: sıkıştırılmış <=64 MiB, <=2.000 parça, parça <=32 MiB, toplam şişirilmiş <=128 MiB, XML <=2 MiB / 50.000 düğüm / derinlik 100, 1–100 slayt. DTD/entity, çakışan/kaçışlı yollar, bozuk ilişkiler, binary/makro ve kapsam dışı namespace reddedilir. Strict OOXML, UTF-16 ve şifreli Office kapsam dışı. Bunlar uygulamanın mevcut yeteneklerini azaltan ürün sınırları değildir.
- Host envanteri sınırlı parçaları bellekte tutar; Android'e doğrudan taşınamaz. Üretim import için akışlı okuma, eşdeğer XML/ZIP kontrolleri ve özel görsel kopyalama gerekir. Jev/TypeSafe, kullanıcı medyası yükleme ve yeni ağ bağlantısı yoktur.
- Test sınıfı ve şablonun uygulama debug APK'sına girmediği ZIP/DEX kontrolüyle doğrulandı. Yalnız test APK'sındadır. Gradle üretim bağımlılıkları değişmedi.

## Ticari lisans incelemesi

PPTX ayrı slayt/master/layout/theme/resim/not parçaları ve ilişkiler içerir. Formatı yazmak genel bir Office renderer geliştirmekle aynı kapsam değildir. [Microsoft PresentationML yapısı](https://learn.microsoft.com/en-us/office/open-xml/presentation/structure-of-a-presentationml-document).

| Aday | Ticari değerlendirme ve karar |
| --- | --- |
| Kendi dar OOXML yazıcımız + mevcut Android çizimi | Yeni runtime SDK/font dosyası dağıtılmadı. Microsoft OSP, kapsanan spesifikasyonun uygulanan kısımlarındaki gerekli iddialara yöneliktir; sınırsız/genel patent izni değildir. İlk dışa aktarma için bu yol ilerletilecek. [OSP](https://learn.microsoft.com/en-us/openspecs/dev_center/ms-devcentlp/1c24c7c8-28b0-4ce1-a47d-95fe1ff504bc), [Microsoft açıklaması](https://learn.microsoft.com/en-us/openspecs/dev_center/ms-devcentlp/03347966-f8ff-4d53-a05e-63419d4132e2). |
| Apache POI/XSLF | POI Apache-2.0 ile sunulur; lisans/NOTICE ve dağıtım koşulları korunmalı. Resmî render `java.awt.Graphics2D` / `BufferedImage` kullanır; Android'e doğrudan renderer olduğu sonucunu çıkarmadım. Android uyarlamasının kod/lisansı, kesin transitif koordinat/hash kapsamı ve cihaz maliyeti ayrıca incelenmeden eklenmeyecek. Bu adımda **eklenmedi**. [POI lisansı](https://poi.apache.org/legal.html), [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0), [XSLF çizim örneği](https://poi.apache.org/components/slideshow/xslf-cookbook.html). |

Şablon/işaret görseli burada üretildi; dış logo/fotoğraf ve gömülü font dosyası yok. Fontlar yalnız isim olarak belirtilir; ileride font gömmek ayrı dağıtım hakkı incelemesi ister. Artifact Tool/Pillow host QA araçlarıdır, Android'e paketlenmez.

`exportRuntimeLicenseInventory` + `audit_runtime.py --check`: **95 gerçek runtime artifact + mevcut native encoder** için incelenmiş hash kapsamı geçti. Eklenmemiş POI transitiflerinin incelendiği veya bütün ülkelerde codec/patent izni olduğu sonucu çıkmaz. Reklam SDK'sı eklendiğinde lisans, reklam/reklam kimliği, veri güvenliği ve gizlilik beyanları birlikte güncellenecek.

## Kanıtlar ve açık kalanlar

**2 yeni Android test**, **12 yeni host içerik/kötü girdi testi** geçti; mevcut **99 JVM testi** raporunda sıfır hata. Debug/test APK derlemeleri geçti. Native testler altı çıktıyı, not/görsel ayrımını, kaynak baytlarını, iptal/geçersiz XML temizliğini kapsar. Bağımsız okuyucuda başlık düzenleme/kaydetme ve Unicode/not kontrolleri geçti. Altı paket için bağımsız ilişki/paket ve slayt boyutu/nesne geometri kontrolü geçti. Tablo/grafik referansı açık kayıp bayraklarıyla sınıflandı. Yeni UI/metin yok; 22 dil kaynak/placeholder kontrolü geçti.

Son sıcak emülatör ölçümü, dört küçük yapay slayt: düzenlenebilir **30–67 ms / 28.909–28.941 bayt**; 2048 raster **435–632 ms / 156.918–158.729 bayt**. Fiziksel düşük RAM/ARM, bellek zirvesi, pil/termal veya büyük sunum performansı garantisi değildir.

PowerPoint/Google Slides/LibreOffice'te gerçek açma/görsel/düzenleme; uzun metin/font çeşitleri, alfa/EXIF/diğer görüntüler, 100 slayt, düşük depolama, süreç ölümü ve küçültülmüş release kontrolü bekler. Bağımsız paket kontrolü tam OOXML şema doğrulaması değildir. Düzenlenebilir RTL **kabul edilmedi**; Office font/yerleşim sadakati ayrıca bekler. Bu nedenle PPTX destek listesine eklenmedi.

Tekrarlama komutları ve şablon hash'i: [geliştirme araçları](../../../tools/pptx/README.md). Kanıtlar Git dışındaki `output/development/pptx-trial/` içindedir: `device.log`, `native/`, `rendered/`, `verification.json`, paket/geometri raporları ve tema karşılaştırmaları. Graphify yerel AST grafiği güncellendi; kaynak ve testler esas alındı.
