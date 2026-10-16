# Birlevo arayüz dili — 8 Ekim geliştirme kodu

Güncel düzen ve doğrulama [dosya odaklı arayüz kaydında](FILE_FOCUSED_INTERFACE.md). Bu tasarım için yeni release üretilmedi.

Üst çubukta görsel simge yerine **mercan kırmızısı Birlevo** yazısı kullanılır. Uygulama / mağaza simgesi ve açılış ekranındaki mevcut logo korunur. Araç simgeleri görevler arasında farklı renklere ayrılmaz.

Ana ekran başlığı arka planla bütünleşir. Büyük tanıtım kartı ve tekrarlanan kısayollar yerine kısa başlangıç, Dosya aç, Yeni oluştur ve beş küçük kategori satırı kullanılır. Dil düğmesi yalnız metindir; en az 48 dp dokunma alanı korunur. Devam et ilk üç kayıt ve Tümünü gör ile hafif metaveri gösterir. Figma dosyası önceki görünümün referansıdır; güncel görünüm Android kodu ve QA ekranlarıdır.

| Rol | Açık görünüm | Koyu görünüm |
| --- | --- | --- |
| Marka yazısı | Koyu mercan `#B73B35` | Açık mercan `#FF9A90` |
| Ana görev düğmeleri | Koyu mercan `#B73B35`, beyaz yazı | Logo mercanı `#FC5B54`, lacivert `#08164D` yazı |
| Alt araç simgeleri | Açık mercan zemin `#FFEAE7`, koyu mercan `#B73B35` | Zemin `#4D292D`, açık mercan `#FF9A90` |
| Arka plan | `#F5F7FC` | Koyu lacivert `#080E24` |
| Kart yüzeyi | Beyaz | Açık lacivert `#192947` |
| Kart kenarlığı | `#DEE5F1` | `#405371` |
| Ana metin | `#152449` | `#EDF1FF` |
| Yardımcı metin | `#566581` | `#B7C2DB` |

Ana ekran, proje ve içe alma araç ikonları ortak mercan ailesindedir; yön okları nötr kalır. Marka görseli değiştirilmez. Logo mercanı küçük beyaz yazının arka planında kullanılmaz. Nötr yüzeyler ve lacivert/açık metin çoğunluğu oluşturur; vurgu marka ve ana eylemi belirtir. Buz mavisi yalnız sakin ikincil seçim yüzeylerinde kalır. Kontrast testleri küçük metin rollerini iki temada en az 4,5:1 eşiğinde kontrol eder.

Küçük kontrol/kategori köşeleri 8–12 dp, büyük yüzeyler 20–28 dp, düğmeler ovaldir. Ekran boşluğu 16–20 dp; yazılar cihazın yazı ölçeğine uyar. Çalışma alanı en az 840 dp genişlikte ve en fazla 1,3× yazı ölçeğinde önizleme ve ayarları yan yana açar. Dar ekran / büyük yazıda ayarlar kaydırılabilir alt paneldedir; ana eylem panelin altında görünür kalır. Tek seçili slayt/sayfa düzenlenir, diğerleri küçük tembel listeden seçilir.

Koyu mod cihaz ayarını takip eder. Arka plan, kart ve kenarlık ayrı lacivert tonlarıdır. Durum çubuğu simgeleri koyu modda açık renktir. Arayüz teması, dışa aktarılan sunumun Klasik / Okyanus / Sıcak seçimini değiştirmez.

Açılış Android SplashScreen uyumluluk katmanını kullanır. Hazır uygulamaya geçişte logo 0.94 → 1.02 ölçeğiyle ve pencere 300 ms saydamlaşmayla kaybolur. İkinci bir açılış sayfası veya bekleme süresi eklenmez. Sistem animasyonları kapalıysa ya da Activity kaydedilmiş durumdan kuruluyorsa geçiş hemen kaldırılır. Sistem simgesiz açılış sağlarsa yalnız pencere saydamlaşır.

[Figma dosyası](https://www.figma.com/design/yNHHPygq0vPFBIEnpo6DP8): telefon ana ekranı, sunum düzenleyici, görsel aracı, açılış ve koyu ana ekran; düzenlenebilir metin / otomatik yerleşim / ortak araç kartları / düğmeler / renk değişkenleri. Figma görsel referanstır; Android davranışı ve son görünüm emülatör ekranlarıyla doğrulanır. Figma durum kaydı `output/design/figma-state.json` içindedir. Önceki yapım betikleri işlem geçmişidir; mevcut dosyaya yeniden uygulanmaz.
