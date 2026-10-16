# Düzenlenebilir PPTX: RTL ve font kapsamı — 14 Ekim 2026

Düzenlenebilir sunum çıktısına modern Arapça/Farsça harfler, harekeler, bu dillerin rakamları, Latin ile karışık metin ve Farsça yarım boşluk (ZWNJ) eklendi. Varsayılan **Görünümü koru** çıktısı değişmedi. Bu, genel Office içe aktarma veya bütün yazı sistemleri için uyumluluk iddiası değildir.

## Çıktı ve düzenleme

Latin metin alanları önceki gibi tek başlık/gövde kutusudur. Arapça içeren alanlarda mevcut Android ölçümü kelimelerin fiziksel yerlerini verir; aynı kelimedeki Latin/rakam/yön parçaları gerektiğinde ayrılır. Bu kutular başlık/gövde/proje etiketi grubuna alınır. OOXML içinde kaynak mantıksal karakter sırası, boşluklar, boş satırlar ve ZWNJ korunur; metin ters çevrilmez veya rastere dönüştürülmez. Kelime/yön kutuları ayrı düzenlenir. Uzun bir değişiklik diğer nesneleri otomatik akıtmaz; kullanıcı konumları gerekirse ayarlar. Bu açıklama düzenlenebilir RTL seçiminde 22 dilde gösterilir.

[OOXML paragraf yönü](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.drawing.textparagraphpropertiestype.righttoleft) ve [koşu yönü](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.drawing.righttoleft) işaretleri yazılır. Denemede yalnız bu işaretlerin bağımsız okuyucuda kelime sırasını düzeltmediği görüldü; son yol ölçülmüş nesne konumlarını da taşır. Android ölçümü kaynak/önizleme/PDF/slayt MP4 ile ortak `PresentationLayout` üzerinden gelir; kelime metaverisi yalnız bu düzenlenebilir dışa aktarımda oluşturulur.

Font olarak Arial istenir, font dosyası gömülmez. Başka okuyucu farklı font seçebilir. Ölçülen yazı boyutları ve görsel çözünürlükleri korunur; kutulara font değişimi için pay ve mevcut satır alanına sınırlı boşluk eklenir. Kaynakta sığmayan metin önceki taşma hatasını verir. Düzenlenebilir sonuç önizlemesi açılan PPTX'nin gerçek çizimi olarak sunulmaz; **yerleşim referansı** etiketi ve font/satır kırılması uyarısı korunur. Görünüm doğruluğu gerektiğinde görünümü koruyan çıktı seçilebilir.

Dar sütunlu görsel örneği ayrıca bir ölçüm hatasını ortaya çıkardı: sarılan satırın son kelimesine ait seçim yolu, sondaki boşluk nedeniyle bütün sütunu kapsıyordu. Görünür harfler ölçülüp boşluk genişliği ayrıca eklenerek düzeltildi. Kaynak metin değiştirilmedi. Regresyon örneği bu kutuların bütün sütun genişliği almasını reddeder; bağımsız çizim ayrıca gözle kontrol edilir.

## Açık sınırlar ve güvenilirlik

Görünür karakter filtresi mevcut Latin kapsamına `U+0620–06D3`, `U+06F0–06F9`, Arapça virgül/noktalı virgül/soru işareti ve ZWNJ ekler. Bu, Arapça Unicode bloklarının tamamı değildir: sunum biçimli eski ligatür kodları, diğer doğrulanmamış yazılar (örneğin Kiril/CJK/İbranice), emoji ve bidi yön kontrol kodları bu seçenek için kapalı kalır. Kullanıcı açıklamayla **Görünümü koru** seçeneğine yönlendirilir. Motor da aynı koşulu denetler; hiçbir karakter sessizce çıkarılmaz. Notların Unicode kapsamı değişmez.

PPTX istek/süreç kaydı, atomik `.part` yazımı, mod işareti/SHA doğrulaması, iptal, kaynak üstüne yazma reddi ve eski v1 varsayılanları aynı yolları kullanır. Araç/ayar değişince eski sonuç temizlenir. Proje kayıt şeması ve FileProvider kökleri değişmedi. Yeni SDK, codec, font dosyası veya runtime paketi yoktur. Gerçek release sınıf yolu envanterinin 95 paketi ve ayrı native encoder hash kapsamı yeniden geçti; geliştirme okuyucusu Android'e bağlı değildir. Ticari dağıtım için önceki [lisans kaydı](PPTX_COMPATIBILITY_AND_LICENSE_REVIEW.md) geçerlidir; bu kod codec patent/ülke izinleri hakkında yeni bir karar vermez.

## Doğrulama

Ayrılmış `ofice_qa`, Android API 36 ve debug hedefi:

- 14 özellik/regresyon testi; üç tema, native nesneler, görsel yönü, not tercihi, eski istek, mod kimliği, iptal/taşma temizliği, kalıcı iş/sonuç geri yükleme, kaynak koruması ve desteklenmeyen içerikte görünümü koruyan yol.
- Arapça yüzde 200 yazıda 3, Farsça tablet düzeninde 3 arayüz yürütmesi: toplam **20 emülatör test yürütmesi**. RTL açıklaması kaydırılabilir panelde erişilebilir; oluşturulan sonuç etkin kaydet düğmesiyle geri yüklenir. Bu testler genel TalkBack/performans kapsamının tamamı sayılmaz; önceki [erişilebilirlik kaydı](ACCESSIBILITY_AND_RTL.md) ayrıca korunur.
- Gerçek üretim motorundan üç tema × altı örnek = **3 RTL PPTX / 18 slayt**. Saf Arapça/Farsça, Latin/dosya adı/rakam, paragraf/boş satır, ZWNJ/NBSP, birleşik yön parçaları, harekeli başlık ve dar görselli metin sütunu örnekleri. Bağımsız Artifact Tool okuyucusunda başlık/gövde/proje etiketinin bütün kaynak karakterleri ve ilk Arapça kelimenin sağdaki konumu doğrulanır; bütün slaytlar çizilir. Bir Arapça kelime değiştirilip dosya yeniden kaydedilir ve açılır; yeni başlık ile diğer bütün metinlerin korunması doğrulanır.
- Latin regresyonu: üç tema × not açık/kapalı **6 PPTX / 24 slayt**, bağımsız okuma/çizim ve başlık düzenleme/kayıt/yeni okuma kontrolü.
- 106 JVM ve 12 host PPTX testi geçti. Debug APK/test APK ve lint geçti: 0 hata, 62 uyarı, 2 ipucu. 22 dilde kaynak/anahtar/format eşleşmesi geçti; bu bütün çevirilerin insan incelemesinden geçtiği anlamına gelmez. Yerel kod grafiği güncellendi; kısmi Kotlin AST çıkarımı gerçek kaynak/test kanıtının yerine kullanılmadı.

Koşucu `tools/testing/pptx-editable-qa.ps1`; bağımsız kontroller `tools/pptx/verify_bidi.mjs` ve `verify_editable.mjs`. Son kaynaklar `wrap-sources.json`, emülatör günlüğü `wrap-qa.log`, bağımsız kontroller `wrap-rtl/` ve `wrap-latin/` olarak `output/development/pptx-bidi/` altında Git dışındadır. Önceki başarısız adaylar son doğrulama olarak kullanılmaz. Sentetik örnekler çevrimdışı işlenir; kullanıcı medyası veya Jev çağrısı yoktur.

Bu **gerçek PowerPoint testi değildir**. Başka Office uygulamalarında/fontlarda kusursuz görünüm, bütün Arapça varyantları veya fiziksel cihaz FPS/pil/bellek garantisi verilmez. PowerPoint kurulumu ve fiziksel cihaz kullanıcıdan beklenen adımlar değildir.

Yeni sürüm/release oluşturulmadı; 0.1.11 (12) korunur. Yeni kodun küçültülmüş release/R8 kontrolü kullanıcı yayın derlemesi istediğinde yapılır. Sonraki ürün işi DOCX/XLSX için yerel motor, açık biçim kayıpları ve ticari lisans denemesidir; bu işi tamamlamak o biçimlere destek açmaz.

Son debug APK SHA-256: `87971ea158d12ed90986c89e397aa3730fb8536c8a6289390d689f3798466887`. Mevcut 0.1.11 (12) AAB SHA-256: `a9917105ed2bbe1564cf40da92280ed26f61571d56f43142d6f02aae896876d5`; yayın dosyası değiştirilmedi.
