# Birlevo arayüz dili — 6 Ekim geliştirme kodu

Son işlev denetimi ve test sınırları [PDF sayfa araçları ve arayüz incelemesinde](PDF_SAYFA_ARACLARI_VE_ARAYUZ_INCELEMESI.md). Bu tasarım için yeni release üretilmedi.

Üst çubukta görsel simge yerine **mercan kırmızısı Birlevo** yazısı kullanılır. Uygulama / mağaza simgesi ve açılış ekranındaki mevcut logo korunur. Araç simgeleri görevler arasında farklı renklere ayrılmaz.

Ana ekran başlığı arka planla bütünleşir. Son kullanıcı düzeltmesine göre büyük lacivert tanıtım kartı korunur; içindeki simgelerin arkasına eklenen ayrı mavi kutular kaldırılır. Dil düğmesi yalnız metindir, buz mavisi zemin / çerçeve yoktur. En az 48 dp dokunma alanı, üç şeffaf kısayol ve dil penceresi korunur. Bu son değişiklik Android kodundadır; Figma eski ana ekranın görsel referansı olarak kalır. [Güncel çalışma ve native ekran doğrulaması](TELEFON_MEDYA_BICIMLERI_VE_SADE_BASLIK.md).

| Rol | Açık görünüm | Koyu görünüm |
| --- | --- | --- |
| Marka yazısı | Koyu mercan `#B73B35` | Açık mercan `#FF9A90` |
| Ana görev düğmeleri | Lacivert `#08164D`, beyaz yazı | Buz mavisi `#9BDCEF`, lacivert yazı |
| Alt araç simgeleri | Açık mercan zemin `#FFEAE7`, koyu mercan `#B73B35` | Zemin `#4D292D`, açık mercan `#FF9A90` |
| Arka plan | `#F5F7FC` | Koyu lacivert `#080E24` |
| Kart yüzeyi | Beyaz | Açık lacivert `#192947` |
| Kart kenarlığı | `#DEE5F1` | `#405371` |
| Ana metin | `#152449` | `#EDF1FF` |
| Yardımcı metin | `#566581` | `#B7C2DB` |

Ana ekran, proje ve içe alma araç ikonları ortak mercan ailesindedir; yön okları nötr kalır. Tanıtım kartındaki simgeler açık mercan `#FF9A90` ve arkasındaki ayrı kutular şeffaftır. Küçük mercan detay `#FC5B54`, camgöbeği `#38D7FE` logonun renk ailesinde korunur. Marka görseli değiştirilmez; metin ve ikonlar için daha okunaklı mercan tonları kullanılır. Nötr yüzeyler ve ana metin çoğunluğu oluşturur, vurgu renkleri görev hiyerarşisi için kullanılır.

Kart köşeleri 24 dp, tanıtım ve slayt kartları 28 dp, alanlar 18–20 dp, düğmeler ovaldir. Ekran boşluğu 20 dp; yazılar cihazın yazı ölçeğine uyar. En az 340 dp kullanılabilir genişlik ve en fazla 1.3× yazı ölçeğinde dört araç iki sütuna yerleşir; dar ekran / büyük yazıda tek sütun olur. Geniş pencerede görevler ve projeler yan yana durur. Uzun içerik kaydırılır, metin üst üste bindirilmez.

Koyu mod cihaz ayarını takip eder. Arka plan, kart ve kenarlık ayrı lacivert tonlarıdır. Durum çubuğu simgeleri koyu modda açık renktir. Arayüz teması, dışa aktarılan sunumun Klasik / Okyanus / Sıcak seçimini değiştirmez.

Açılış Android SplashScreen uyumluluk katmanını kullanır. Hazır uygulamaya geçişte logo 0.94 → 1.02 ölçeğiyle ve pencere 300 ms saydamlaşmayla kaybolur. İkinci bir açılış sayfası veya bekleme süresi eklenmez. Sistem animasyonları kapalıysa ya da Activity kaydedilmiş durumdan kuruluyorsa geçiş hemen kaldırılır. Sistem simgesiz açılış sağlarsa yalnız pencere saydamlaşır.

[Figma dosyası](https://www.figma.com/design/yNHHPygq0vPFBIEnpo6DP8): telefon ana ekranı, sunum düzenleyici, görsel aracı, açılış ve koyu ana ekran; düzenlenebilir metin / otomatik yerleşim / ortak araç kartları / düğmeler / renk değişkenleri. Figma görsel referanstır; Android davranışı ve son görünüm emülatör ekranlarıyla doğrulanır. Figma durum kaydı `output/design/figma-state.json` içindedir. Önceki yapım betikleri işlem geçmişidir; mevcut dosyaya yeniden uygulanmaz.
