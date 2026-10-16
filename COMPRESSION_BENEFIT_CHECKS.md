# Sıkıştırma faydası kontrolü — 5 Ekim 2026

`0.1.5 (6)` görsel ve video sıkıştırmada, çıktının kaynaktan büyük olmasını normal bir sıkıştırma başarısı olarak sunmaz. Kaynak dosya, kalite seçenekleri, çözünürlükler ve düzenleme yetenekleri korunur.

## Davranış

- `CompressionBenefit.kt` gerçek kaynak / çıktı baytlarını küçük, eşit ve büyük olarak sınıflandırır. Boş dosya başarı değildir.
- Çıktı küçükse normal kaydetme yolu açıktır. Eşit veya büyükse normal kayıt kapalıdır; büyük sonuç pozitif “Boyut artışı”, eşit sonuç “Dosya boyutu değişmedi” olarak gösterilir.
- Düzenlenmiş veya dönüştürülmüş kopya yine kullanılabilir: “Yine de kaydet” seçimi, orijinali koruma seçeneği bulunan bir onay penceresi açar. Onay o sonuç dosyasına bağlıdır; yeni işlem sonucunu yetkilendirmez. Pencere Android yeniden oluşturmasında korunur, büyük yazıda açıklama kaydırılabilir.
- Hem görsel hem video dışa aktarma motorları varsayılan olarak eşit / büyük kopyayı reddeder. Denetim ekranla sınırlı değildir; reddedilen kayıt daha önce kaydedilmiş bir dosyayı değiştirmez.
- Video için, seçili video bit hızı kaynak dosyanın toplam ortalama bit hızına eşit veya daha büyükse işlem öncesinde risk uyarısı verilir. Bu bir tahmindir; gerçek çıktı kontrolü her zaman yapılır. Kesilen video için bu tam süre tahmini gösterilmez; gerçek boyut ve açık kopya onayı yine geçerlidir.
- Kalite / çözünürlük otomatik düşürülmez, art arda gizli yeniden kodlama yapılmaz. Kullanıcının seçtiği seçeneklerle tek çıktı üretilir.
- Yedi yeni arayüz metni 22 dil kataloğuna eklendi; kaynaklar ve format yer tutucuları doğrulandı. İnsan çeviri incelemesi hâlâ gereklidir.

## Belgelerde sınır

Mevcut belge aracı sunumdan yeni PDF üretir; PDF sıkıştırma motoru henüz yoktur. Belge oluşturma, dönüştürme ve paketleme işlemlerinde boyut tek başına geçerlilik ölçütü değildir. Bu nedenle yeni PDF oluşturma akışı engellenmez. İleride PDF / belge sıkıştırma eklendiğinde aynı gerçek boyut sözleşmesi uygulanır; bu karar `AGENTS.md`, ürün test planı ve mimari kurallarına kaydedildi.

## Doğrulama

- JVM: 17 test, sıfır hata. Küçük / eşit / büyük, boş çıktı ve video bit hızı sınırları dahil.
- Android 16 telefon: tam takımda 45 senaryonun 44'ü geçti. İlgisiz slayt menüsü testinde açılır pencere oluşmadan tıklama görüldü; menünün oluşmasını bekleyen test düzeltildikten sonra ilgili senaryo ayrı koşumda 1/1 geçti. Bu rapor tam takımın son koşumunu 45/45 olarak göstermiyor.
- Yeni Android senaryoları: büyük PNG için kayıt engeli, orijinali koruma, ekran yeniden oluşturmasından sonra açık kayıt; büyük video için kayıt engeli ve önceki sonucun onayının yeni dosyaya taşınmaması; boyutu artan kesilmiş videonun açık onayla kaydedilmesi. Kaynak / kaydedilen dosya özetleri kontrol edildi.
- İmzalı, R8 ile küçültülmüş APK: telefon (1080×2400), tablet (1600×2560, 240 dpi) ve büyük yazılı dar ekran (720×1440, 360 dpi, yazı ölçeği 2,0) için görsel, sesli video ve PDF dışa aktarma akışları; ayrıca büyük görsel / video kayıt engeli ve açık kopya kaydı geçti. Üç boyutun her birinde 3 temel akış ve 2 ek boyut artışı senaryosu doğrulandı.
- Gerçek büyüme örnekleri: PNG 111 → 781 bayt; düşük bit hızlı MP4 20.449 → 93.780 bayt. İkisinde de normal kayıt kapalı, açık kopya kaydı çalışır ve kaynak değişmez.
- Debug / release lint: sıfır hata, 27 uyarı. Release derlemesinde R8 Kotlin metadata ayrıştırma uyarıları da görüldü; bunlar geniş keep kurallarıyla gizlenmedi. Gerçek küçültülmüş APK'nın yukarıdaki çalışır akışları bağımsız platform koşucusuyla kontrol edildi.
- İlk denemelerde otomasyon hataları düzeltildi: test yeni çıktı üretilince temizlenen geçici dosyayı okumak yerine kaydedilmiş kopyanın özetini tutar; platform koşucusu belirli Button sınıfını varsaymaz ve kaydırmada yenilenen erişilebilirlik düğümlerini yeniden alır. Başarısız ilk kayıtlar QA klasöründe korunur. Ekran geçişlerinde QA emülatörü bir kez yanıt vermedi; yalnızca bu emülatör yeniden başlatıldı.
- Graphify yerel AST grafiği güncellendi. Kısmi Kotlin çıkarım uyarıları gerçek derleme / testlerin yerine kullanılmaz. Fiziksel düşük donanım ve farklı üretici codec testleri henüz yapılmadı.

## Paket

- AAB: `output/releases/0.1.5/birlevo-0.1.5-6.aab`
- Boyut: 7.020.440 bayt; bu Play indirme boyutu değildir.
- SHA-256: `0c5e8660f782f1fc2f584927020a6c0fec686fccd0923ca02c2fad46d31efeca`
- İmza, paket kimliği, sürüm, izinler, logo, dört ABI için 16 KB ELF hizası ve bundletool doğrulaması tamamlandı. Reklam SDK'sı veya yeni ağ izni eklenmedi. Önceki paketler korunur; bu paket Play'e gönderilmedi.
- Kanıtlar: `output/development/0.1.5/`, `output/releases/0.1.5/release-validation.json`.
