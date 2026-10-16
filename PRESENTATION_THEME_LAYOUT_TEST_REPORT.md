# Sunum temaları ve hazır düzenler — 5 Ekim 2026

Çalışma sürümü: **Birlevo 0.1.2 (3)**, `studio.birlevo.android`. Geliştirme ve doğrulama yerelde tamamlandı. Yeni AAB Play Console'a gönderilmedi. Önceki 0.1.0 (1) ve 0.1.1 (2) paketleri ayrı dosyalarda korundu.

## Eklenen davranış

- Sunumun tamamına Klasik (beyaz), Okyanus (lacivert) veya Sıcak (krem) teması uygulanır. Arka plan, metin, yardımcı metin ve vurgu renkleri birlikte değişir.
- Her slayt için görsel sağda, solda veya üstte düzeni seçilebilir. Görselsiz slaytlar metin için tam genişliği kullanır; görsel eklenince seçilen yerleşim uygulanır. Görseller en-boy oranını korur; kırpılmaz.
- Slaytı önizle / Preview slide, düzenleyicide gerçek PDF çiziminden bir 16:9 önizleme üretir. Tema, metin, yerleşim veya sıra değiştiğinde önizleme güncellenir. Önizleme, dışa aktarılan PDF ile aynı çizim kodunu kullanır; gerçek slayt numarası ve toplam sayı korunur.
- Bellek kullanımını sınırlamak için aynı anda tek slayt önizlemesi açılır. Metin yazarken 250 ms beklenir; geçici önizleme PDF dosyası işlem sonunda temizlenir. Hata ve yeniden deneme gösterilir.
- Tema ve düzen otomatik kayda dahildir. Slayt çoğaltma / sıralama, metin ve görselle birlikte seçilen düzeni korur. Uygulama yeniden açılınca tasarım geri gelir.
- Eski taslaklarda yeni alanlar yoksa Klasik tema ve görsel sağda düzeni kullanılır; eski görünüm korunur. Tanınmayan tasarım değerleri yükleme hatası oluşturur; kaydedilmiş tasarım sessizce değiştirilmez.
- Taşan metin kırpılmadan, slayt numarasıyla uyarılır. Üstte görsel düzeni metne daha az yükseklik bırakır; uzun içerik başka slaytlara bölünmelidir. Boş slayt tasarımı önizlenebilir, tümü boş sunumun PDF dışa aktarımı yine engellenir.

## Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| JVM birim testleri | 14/14 geçti; eski taslak varsayılanları, 3 tema × 3 düzen kayıt turu, bilinmeyen tasarım değerleri ve tasarımlı slayt çoğaltma / taşıma dahil |
| Android 16 telefon emülatörü | Tam takım 35/35 geçti; görsel sıkıştırma, video kesme / sıkıştırma, PDF, dil / RTL ve otomatik kayıt regresyonları dahil |
| Tasarım matrisi | Dokuz birleşimde gerçek PDF ve tek slayt önizlemesi piksel piksel aynı; arka plan rengi ve görselin seçilen bölgedeki konumu doğrulandı |
| Tasarım akışı | Tema / yerleşim seçimi, açık önizlemede tema değiştirme, çoğaltma, taşıma, otomatik kayıt, Activity yeniden oluşturma, dosyadan yeniden açma ve PDF çıktısı geçti |
| Tablet | 1600×2560 px / 240 dpi / 1× yazı; aynı tasarım akışı 1/1 geçti |
| Dar ekran / büyük yazı | 720×1440 px / 360 dpi (320 dp genişlik) / 2× yazı; aynı tasarım akışı 1/1 geçti |
| İçerik güvenilirliği | Boş tasarım önizlemesi çalıştı; boş dışa aktarma reddedildi; üstte görselli uzun metin taşması uyarı verdi ve geçici PDF bırakmadı |
| Dil kaynakları | 22 dilde 100 anahtar, biçim yer tutucuları ve üretilen kaynaklar doğrulandı |
| Debug / release | Derleme ve lint geçti; her iki raporda 0 hata, 24 mevcut uyarı |
| İmzalı AAB | Aynı Birlevo yükleme sertifikası; jarsigner ve Bundletool doğrulaması geçti |
| Paket içeriği | Kod 3 / sürüm 0.1.2, debug kapalı, minSdk 24 / targetSdk 36; güvenli logo, dört ABI, 16 KB yerel kütüphane hizalaması; test örnekleri yok |

Telefon, tablet ve dar ekran düzenleyici görüntüleri ile örnek PDF sayfaları görsel olarak incelendi. Emülatör ekran boyutu / yoğunluğu ve yazı ölçeği test sonunda varsayılanlarına döndürüldü. Fiziksel cihaz testi kullanıcı tercihiyle ertelenmiş durumda; daha eski Android sürümleri ve bütün cihaz kodekleri bu kontrolde sınanmadı. Yeni çeviriler kaynak açısından tamdır; ana dili konuşan kişilerce dilsel kontrol tamamlanmadı.

## Paketler ve kanıtlar

- [İmzalı AAB](output/releases/0.1.2/birlevo-0.1.2-3.aab): 12.163.329 bayt. SHA-256: `1ded7065b40c0176142ab51ddb892fe1229778cdee90f667f04e157f77d8264b`.
- [Paket doğrulama raporu](output/releases/0.1.2/validation.json) ve [Bundletool günlüğü](output/releases/0.1.2/bundletool-validation.log).
- [İngilizce Play sürüm notları](output/releases/0.1.2/play-console-release-notes.txt); metin, önceki 0.1.0 paketi üzerine gelen otomatik kayıt ve slayt yönetimini de içerir.
- [Geliştirme APK'sı](output/development/0.1.2/birlevo-0.1.2-3-debug.apk): geliştirme imzası, Play yüklemesi için AAB kullanılır.
- [Telefon](output/development/0.1.2/qa/phone/presentation-theme-editor.png), [tablet](output/development/0.1.2/qa/tablet/presentation-theme-editor.png), [dar ekran / büyük yazı](output/development/0.1.2/qa/narrow-large-text/presentation-theme-editor.png).
- [Okyanus / görsel solda](output/development/0.1.2/qa/phone/design-OCEAN-IMAGE_LEFT.png) ve [Sıcak / görsel üstte](output/development/0.1.2/qa/phone/design-WARM-IMAGE_TOP.png). Renkli test görseli yön ve yerleşim kontrolü içindir, uygulama şablon içeriği değildir.
- Tam takım XML sonuçları, birim testleri, lint, tablet / dar ekran günlükleri ve [özet](output/development/0.1.2/qa-summary.json) `output/development/0.1.2/` altında saklandı.

Reklam SDK'sı, INTERNET veya AD_ID izni eklenmedi. Dosyalar cihazda işlenir; gizlilik / veri toplama davranışı değişmedi. Sunum görselleri dosya referansıdır; kaynak silinirse veya izin kaybolursa görsele erişilemeyebilir. Kaynak varlıkları projeye kopyalama ve araçlar arası çıktı aktarımı sonraki iş paketindedir. Serbest yerleşim, çoklu görsel katmanı, animasyon, slayt gösterisi ve PPTX bu geliştirmede eklenmedi.

Önceki paket hash değerleri tekrar doğrulandı: 0.1.0 `35f68f686e49e42d21afb6c4d7d6205b7774e3f64dd7d91aafda0e810812cd33`, 0.1.1 `724eebaed8b256aaa58657317b91387413cd1955b05f2e9bc5258f333231dec9`.

Graphify yerel AST grafiği kod değişikliklerinden sonra güncellendi. Altı Kotlin dosyasında kısmi ayrıştırma uyarısı var; gerçek Kotlin derlemesi ve kaynak / davranış testleri geçti. Grafik, testlerin yerine kullanılmadı.
