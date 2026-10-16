# Araçlar arası çıktı aktarımı — 0.1.6 (7)

5 Ekim 2026. İlk paket: sıkıştırılmış görseli dosya seçmeden yeni / kayıtlı sunuma ekleme; üretilen görsel, video ve PDF'yi açma / paylaşma. PNG / WebP giriş-çıkışı ve fotoğraflardan PDF sonraki pakettir.

## Davranış

**Add to presentation** yeni bir sunum oluşturur veya seçilen sunumun sonuna yeni slayt ekler. Mevcut yazı, görsel, sıra ve tema korunur. 100 slayt içeren hedef devre dışıdır; yeni sunum oluşturulabilir.

**Open file / Share file** üretilen dosyada kullanılabilir. Alıcı uygulama açılamazsa hata gösterilir; Kaydet işlemiyle kalıcı çıktı alınabilir. Görsel / video sıkıştırmasında eşit veya büyük sonuç için bu yeni işlemler de sonuca özel açık onay ister. Yeni PDF oluşturma sıkıştırma sayılmaz. Kalite ve çözünürlük seçenekleri azaltılmadı.

## Dosya ömrü ve modülerlik

- `MediaOutput` MIME, ad, URI ve gerçek boyutu taşır. Kullanıcı açma / paylaşma istediğinde `cacheDir/shared-outputs` altında çalışma dosyasından bağımsız kopya hazırlanır. Bu, kalıcı dosya kitaplığı değildir. Android önbelleği temizleyebilir; yedi günden eski kopyalar sonraki çıktı hazırlanırken temizlenir. Silinmiş paylaşım kopyası çalışma sonucundan yeniden oluşturulur.
- Sunuma aktarılan veya dosya seçiciden yeni eklenen JPEG / PNG, `filesDir/project-images` içinde saklanır. Kaynak silinmesi, araç sıfırlanması ve önbellek temizliği bu kopyayı kaldırmaz. Çoğaltılmış slayt aynı URI'yi kullanır.
- Kodlanmış baytlar yeniden kodlanmadan korunur; EXIF yönü ve mevcut 128 MiB / 100 MP doğrulaması sürer. Kopyalama 64 KiB tamponla yapılır. `.part`, `fsync` ve yeniden adlandırma tamamlanmadan proje referansı kaydedilmez.
- İptal / geçersiz hedefte bağlanmamış yeni görsel temizlenir. Bağlı görsel, proje atomik tek yazıcısında kayıt başarısız olsa da taslakta korunur; yeniden kayıt denenebilir. UI'a teslim edilmiş bitmap zorla geri dönüştürülmez.
- Eski taslak şeması değişmedi; dış URI'ler okunur. Eski görseller otomatik taşınmaz, yeniden seçilirse özel kopya oluşturulur.
- Projeden kaldırılan / değiştirilen eski özel görseller henüz otomatik silinmez. Gelecek depolama yönetimi kayıtlı taslakların ve çoğaltılmış slaytların referanslarını birlikte izlemelidir. Yerel uygulama verilerini silmek özel kopyaları kaldırır; dışa aktarılan dosyalar ayrıca kalır.

Dosya sağlayıcısı dışa açık değildir; yalnızca iki çıktı / görsel dizini tanımlıdır. Proje JSON'u paylaşılabilir değildir. Alıcı uygulamaya yalnızca okuma izni ve ClipData gönderilir. Ayrı sağlayıcı alt sınıfı [Android'in cihaz uyumluluğu önerisini](https://developer.android.com/reference/androidx/core/content/FileProvider) izler. İnternet, reklam kimliği ve geniş galeri izni eklenmedi.

## Doğrulama

İlk genel debug çalıştırması **53/54** geçti. Önceki slayt menüsü testinde klavye / onay penceresi kapanışı sırasında zamanlama hatası oluştu. Klavye ve pencere kapanışı beklenen tekrar çalıştırmada aktarım ve Studio sınıfları birlikte **18/18** geçti. Son sahiplik, açma / paylaşma, büyük sonuç onayı, kayıt hatası / yeniden deneme ve otomatik kayıt grubu **15/15** geçti. Son kodun bütün 56 senaryoyu birlikte geçtiği iddia edilmez.

Birim testleri **17/17**, 22 dil kaynak kontrolü ve debug / release lint başarılı; her lint raporu **0 hata / 28 uyarı**. Günlükler / sonuç XML'leri `output/development/0.1.6/` altında korunur.

İmzalı, R8 / kaynak küçültmesi uygulanmış **0.1.6 (7)** APK'sında telefon (1080×2400 / 420 dpi) ve tablet (1600×2560 / 240 dpi) profillerinde dört akış geçti: görsel, sesli video, PDF ve görselden sunuma aktarım. JPEG / MP4 / PDF için ayrı test uygulaması okuma izniyle gerçek çıktı baytlarını okudu; büyük görsel / video sonuçlarında onay denetlendi. Tablet profilinde süreç zorla durdurulduktan sonra kayıtlı görsel ve PDF tekrar doğrulandı. Bunlar farklı fiziksel cihazlar değil, aynı Android 16 QA emülatörünün ekran profilleridir.

Dar ekran / %200 yazıda (720×1440 / 360 dpi) ayrı PDF oluşturma / açma / paylaşma denemesi geçti. Tam otomasyon tekrarı video kaydet düğümüne ulaşamadı; kaydırma / otomasyon ayrımı çözümlenmeden bu profil tam geçti sayılmaz. Başarısız koşucu günlükleri ve ekran görüntüleri saklandı. Bu profilin tam video ve aktarım akışı yayın öncesi kalan kontroldür.

Koyu modda görsel ve sesli video oluşturma / kaydetme / açma / paylaşma ve büyük sonuç onayları geçti. Varsayılan tema ile ayrı PDF oluşturma / kaydetme / açma / paylaşma kontrolü de geçti. Tam koşucu PDF için `Project name` alanını bulamadı; hata sonundaki erişilebilirlik ağacında alan vardı. Ayrı tema değiştirme denemesinde `Ocean` menü öğesine ulaşılamadı. Önbellek yenilemesi dar ekran sorununu çözmedi; sorunun yalnızca koşucudan kaynaklandığı iddia edilmez. Koyu modun tam sunum / tema değiştirme / aktarım akışı da yayın öncesi kalan kontroldür.

Çevirilerin insan incelemesi, fiziksel cihaz codec / dosya sağlayıcısı, düşük depolama, bellek ve pil ölçümü sürer; emülatörden fiziksel performans garantisi çıkarılmaz. Graphify yerel AST grafiği güncellendi; yedi Kotlin dosyasındaki kısmi ayrıştırma uyarıları grafik aracının sınırıdır. İlişkiler derlenen kaynak ve testlerle doğrulandı.

## Yayın

Yeni paket Play'e gönderilmez; eski paketler korunur. İngilizce / Türkçe gizlilik kaynakları yeni proje görseli ve geçici paylaşım ömrüne göre hazırlandı. Yeni sürüm dağıtılmadan önce web sitesine yayımlanmalıdır; bu adımda web dağıtımı yapılmadı. Reklam SDK'sı eklenmedi; reklam eklendiğinde Play beyanları ve gizlilik metni birlikte güncellenecek.

## 0.1.7 ile devam eden doğrulama

Görsel formatları ve fotoğraf PDF paketi üzerinde, 0.1.6'dan kalan büyük yazılı dar ekran video / PDF / kalıcı aktarım yolları gerçek imzalı APK ile doğrulandı. 0.1.6 ikili dosyası ve o sürümün kısmi QA sonucu değiştirilmez. Yeni sürümün ayrıntıları [IMAGE_FORMATS_AND_PHOTO_PDF.md](../features/images/IMAGE_FORMATS_AND_PHOTO_PDF.md) ve `output/development/0.1.7/qa-summary.json` içindedir.

0.1.7 imzalı adayında koyu modun tam sunum tema / PDF ve kalıcı aktarım kontrolleri de geçti. Önceki sürümde açık kalan bu QA yolları yeni aday üzerinde kapanmıştır.
