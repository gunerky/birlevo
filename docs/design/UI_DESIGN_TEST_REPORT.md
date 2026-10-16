# Arayüz ve açılış — 5 Ekim 2026

6 Ekim devamı: renk / metin rolleri, çalışan tanıtım kısayolları, eski video taslağı geçişi ve PDF sayfa araçları için [güncel inceleme](../features/pdf/PDF_PAGE_TOOLS_AND_UI_REVIEW.md) hazırlandı. Bu devam çalışmasında release üretilmedi. Aşağıdaki 0.1.3 ölçümleri tarihsel kayıttır.

Çalışma sürümü **Birlevo 0.1.3 (4)**. Yuvarlak arayüz, lacivert koyu mod ve kısa açılış geçişi yerelde tamamlandı. Yeni paket Play Console'a gönderilmedi; önceki üç yayın paketi korundu.

- Üstte görsel yerine mercan Birlevo yazısı; alt araçlarda tek buz mavisi simge ailesi.
- Lacivert / mavi tanıtım kartı, dört göreve doğrudan erişim, yuvarlak görev / proje / içeri alma kartları, daha yumuşak alanlar ve oval düğmeler.
- Sunum düzenleyici, görsel / video sıkıştırma ve PDF ekranları ortak tema kullanır. Dosya işleme ve sunum tema seçenekleri korunur.
- Koyu modda arka plan `#080E24`, kartlar `#192947`, kenarlıklar `#405371`; açık metin ve buz mavisi simgeler. Durum çubuğu simgeleri okunaklıdır.
- Android'in açılış penceresinde 300 ms ölçek / saydamlaşma geçişi; simgesiz başlatma güvenli, animasyon kapalıyken anında kaldırma, yeniden oluşturmada animasyonu tekrar etmemek.

## Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| JVM testleri | 14/14 geçti |
| Android 16 telefon | Arayüz / açılış eklendikten sonra tam regresyon 35/35 geçti; görüntü, video, PDF, otomatik kayıt, dil / RTL ve yeniden oluşturma dahil |
| Telefon yerleşimi | Ana ekran erişimi ve sunum tasarım akışı 2/2 geçti |
| Tablet | 1600×2560 / 240 dpi / 1× yazı; aynı iki akış 2/2 geçti |
| Dar ekran / büyük yazı | 720×1440 / 360 dpi / 2× yazı, 320 dp genişlik; aynı iki akış 2/2 geçti |
| Son koyu görünüm | Son palet ve durum çubuğu düzeltmesiyle ana ekran görevi 1/1 geçti; ekran görsel olarak incelendi |
| Son açık görünüm | Mercan yazı ve buz mavisi simgeler, gerçek uygulama ekranında incelendi |
| Açılış | Başlatıcı üzerinden soğuk açılış kaydı alındı; logo ve ana ekrana geçiş karelerde görüldü. Animasyon ölçeği 0 iken ana ekrana erişim UI ağacında doğrulandı; ayar geri yüklendi |
| Yerelleştirme | 22 dil / 106 anahtar, yer tutucular ve üretilen kaynaklar doğrulandı |
| Debug / release | Derleme, birim testleri ve lint geçti; 0 hata / 26 uyarı |
| İmza / paket | Birlevo yükleme sertifikasıyla imzalı; jarsigner, Bundletool ve paket incelemesi geçti |

Tam regresyon, son renk tercihleri öncesindeki aynı yapısal arayüzü sınadı. Sonraki renk / marka yazısı değişiklikleri için ilgili ekranlar ve erişim akışları yeniden kontrol edildi; medya işleme kodu değişmedi. Tablet ve dar ekran kanıtları yerleşim / erişim testleridir; son renk tercihinin ekran kanıtı açık ve koyu telefon görüntüleridir. Bu ayrım testleri gereksiz tekrarlamadan değişen alanı doğrular.

İlk denemede SplashScreen uyumluluk katmanının simgesiz başlatmada simge getter'ı hata verdi; pencere geçişine güvenli geri dönüş eklenince regresyon geçti. PDF testinde klavye açılırken yapılan kaydırma / tıklama zaman aşımı, testin önizlemeye geçmeden klavyeyi kapatmasıyla düzeltildi; ilgili test ve tam takım yeniden geçti. Son durumun kanıtı geçen günlüklerdir, ilk başarısız günlükler tanı amacıyla saklandı.

Lint uyarıları daha yeni kütüphane sürümleri, mevcut API / kaynak kullanımı gibi bakım işleridir. Bu çalışma tüm bağımlılıkları yükseltmez. Fiziksel cihaz testi kullanıcı tercihiyle ertelenmiştir. Android 7–15 ve farklı üretici kodekleri bu turda sınanmadı; 22 çeviri ana dili konuşan kişilerce onaylanmadı.

## Paket ve kanıt

- [İmzalı AAB](../../output/releases/0.1.3/birlevo-0.1.3-4.aab), 12.198.272 bayt; SHA-256 `b5075121cd0ced599b0c389d113015d6f03bde5c2e47e6909fa02645eed9ebf5`.
- [Geliştirme APK](../../output/development/0.1.3/birlevo-0.1.3-4-debug.apk).
- [Açık telefon](../../output/development/0.1.3/qa/phone-final/home-ready.png), [koyu telefon](../../output/development/0.1.3/qa/dark-final/home-ready.png), [açılış kaydı](../../output/development/0.1.3/qa/startup.mp4).
- Tablet / dar ekran, PDF / medya / dil ekranları ve günlükler `output/development/0.1.3/qa/` içindedir. Tam takım XML sonucu `qa/full-results/phone-tests.xml`, özet `qa-summary.json` içindedir.
- [Paket incelemesi](../../output/releases/0.1.3/validation.json) ve [Figma referansı](https://www.figma.com/design/yNHHPygq0vPFBIEnpo6DP8).
- [Tasarım dili](DESIGN_LANGUAGE.md); yerel Graphify grafiği güncellendi. Kısmi Kotlin ayrıştırma uyarıları kaynak ve gerçek derlemeyle ayrıca doğrulandı.

Reklam SDK'sı, INTERNET veya AD_ID eklenmedi. Veri güvenliği davranışı değişmedi. Sıradaki ürün işi ortak çıktı aktarımı ve doğrulanmış JPEG / PNG / WebP format genişlemesidir; testçi hataları önceliklidir.
