# Tek video kesme — 4 Ekim 2026

5 Ekim güncellemesi: Ana ekrandaki **Edit video / Video düzenle** kartı artık yalnızca kaynak dosya tutan bir taslak açmak yerine kesme, önizleme ve MP4 dışa aktarma ekranına gidiyor. Android emülatöründe bu karttan başlayan sesli MP4 seçme → bölüm kesme → önizleme → dışa aktarma testi geçti. Önceden kaydedilmiş video taslağını açma testi de geçti.

Desteklenen MP4 için başlangıç ve bitiş seçimi, yalnızca seçili aralığın oynatılması ve mevcut sıkıştırma profiliyle tek Media3 dışa aktarması eklendi. Değişiklik kaynak dosyaya yazmaz. Aralık değişirse önceki çıktı geçersiz olur. Yeni beş arayüz metni 22 dil kataloğuna eklendi; toplam 76 anahtarın kaynak tamlığı geçti. Çeviriler dilsel yayın kontrolü bekler.

## Doğrulama

| Koşul | Sonuç |
| --- | --- |
| Telefon emülatörü, 1080×2400 / 420 dpi | Video sınıfındaki 8/8 cihaz testi başarılı |
| Tablet boyutu, 1920×1200 / 240 dpi | Yeni kesme senaryosu başarılı |
| Dar ekran / büyük yazı, 320 dp / yazı 2.0 | Yeni kesme senaryosu başarılı |
| Android 16 telefon emülatörü, tüm cihaz testleri birlikte | 27/27 başarılı |
| Debug derleme, yerel birim testleri, lint | Başarılı |
| Dil kaynak kataloğu | 22 dil, 76 anahtar doğrulandı |

Yeni uçtan uca test, altı saniyelik H.264/AAC örneğinde 1,5–3,5 saniye aralığını UI kontrolleriyle seçti. Oynatıcı yaklaşık iki saniyelik seçimi gösterdi. Yeni MP4 yaklaşık iki saniye sürdü, ses parçasını korudu, hareketli kareler içerdi ve kaynaktan küçüktü. Kaynağın SHA-256 değeri işlem sonunda aynı kaldı. Aralık yeniden değiştirildiğinde eski çıktı kaydetme durumundan çıkarıldı. Var olan yedi sıkıştırma testi, tam video seçiminin önceki davranışını ve iptal / hata akışlarını tekrar doğruladı.

Toplu test sırasında emülatörün düşük boş alan koşulunda Android özel önbellekteki ara dosyaları temizledi; ilk koşulda üç geçici test hatası görüldü. Görsel, video ve PDF işlem dosyaları yedeklenmeyen özel dosya alanına taşındı ve 24 saatlik temizlik korundu. Aynı 27 test yeniden birlikte çalıştırıldığında tamamı geçti. Graphify yerel kod grafiği güncellendi; üç ViewModel için kısmi ayrıştırma uyarısı sürdüğünden ilişkiler kaynak kod ve cihaz testleriyle doğrulandı.

Tablet ve dar ekran denemeleri aynı telefon AVD'sinin ekran ölçüsü, yoğunluk ve yazı ayarlarıyla yapıldı; gerçek tablet veya farklı üretici kodlayıcısını temsil etmez. Test sonunda ayarlar sıfırlandı. Kaynak seçimi ve hedef kaydı kontrollü test sağlayıcısıyla yapıldı; sistem dosya seçicisinin gerçek ekranları ve gerçek cihazlar kapalı test öncesinde ayrıca kontrol edilmelidir. Kesme Media3 ile yeniden kodlandığı için seçilmemiş karelerin MP4 düzenleme listesiyle dosyada bırakılmasına dayanan hızlı kesme kullanılmaz.
