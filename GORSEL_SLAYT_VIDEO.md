# Görsel ve slaytlardan zamanlamalı MP4 — 8 Ekim 2026

Geliştirme koduna ayrı, dosya odaklı bir video oluşturma aracı eklendi. Yeni sürüm numarası veya imzalı APK/AAB üretilmedi. Kamuya açık site ve Play açıklaması bu geliştirme için değiştirilmedi.

## Kullanım ve kapsam

- Ana sayfa **Yeni oluştur → Görsel / slaytlardan video**; dosya açmada görsele uygun işlemler; işlenmiş görselde **Diğer işlemler** üzerinden erişilir.
- Sunum editöründeki video düğmesi başlık, tema, metin, düzen ve görsellerin bağımsız bir kopyasını alır. Sonraki sunum düzenlemeleri videoyu değiştirmez. Açık video çalışmasını değiştirmek onay ister; iptalden sonra tekrar aktarım da onay gösterir.
- Her kare 0,5–60 saniye; varsayılan 3 saniye. Seçme, sıralama, kaldırma, ek görsel seçme ve süreyi açıkça uygulama vardır. Arapça/Farsça sayı karakterleri ve yerel ondalık ayırıcılar kabul edilir.
- Sessiz H.264 MP4, 30 fps; 16:9, 9:16 veya kare; sığdırma / ortadan kırpma; üç kalite ve 640 / 1280 / 1920 piksel uzun kenar. Cihazın tam ayarı desteklemesi gerekir; otomatik kalite veya çözünürlük düşürülmez.
- En çok 100 kare, toplam 10 dakika, görsel başına 128 MiB, özgün özel görsel kopyalarında toplam 512 MiB. Bunlar mevcut medya kaynak sınırlarıdır. Render PNG'leri ve video için ayrıca boş depolama gerekir.
- Gerçek sonuç oynatılır, yeni belgeye bayt bayt kaydedilir, paylaşılır/açılır. Video düzenleyicisine aktarım sonrası mevcut ses, yazı, logo ve diğer video araçları kullanılabilir; geri dönünce video çalışması korunur.

Bu yeni video oluşturmadır. MP4 boyutu görsellerin toplamından büyük olabilir; bu durum sıkıştırma başarısı olarak gösterilmez. Kaynak bir görsel sıkıştırma aracının eşit/büyük çıktısıysa, video aracına aktarılmadan önce o sonuca özel büyük kopya onayı gerekir. Yeni aracın eklenmesi mevcut sıkıştırma korumalarını değiştirmez.

Video durağandan üretilir; animasyonlu görsel kabul edilmez. Şeffaflık siyaha döner; sığdırma siyah kenar ekleyebilir, doldurma merkezi kırpar. Video kayıplıdır; renk/detay değişebilir, görsel metaverisi korunmaz. Bu bilgiler çıktı panelinde 22 yerelleştirmede gösterilir. Geçiş efektleri, slayt animasyonları ve doğrudan ses ekleme bu ilk aracın kapsamına dahil değildir; ses mevcut video düzenleyicisinde eklenir.

## Yapı ve dayanıklılık

`SlideshowTimeline` ve sınırları belirli v1 JSON, `SlideshowEngine`, `SlideshowViewModel` ve `SlideshowScreen` ayrıdır. Fotoğraf PDF ve video için byte-exact kaynak alımı `ImageSnapshots` üzerinden paylaşılır. Sunum PDF ve video aynı slayt ressamını kullanır; video özgün çıktı çözünürlüğünde boyanır, PDF/önizleme ekran görüntüsü büyütülmez.

Görseller `noBackupFilesDir/slideshow-video/page-*` içine kopyalanır; özgün dosyalar değişmez. Aynı sunum görseli bir kez kopyalanır. Taslak, ayarlar ve referanslar mevcut atomik tek yazıcıyla tutulur; kaldırılan dosya ancak başarılı kayıttan sonra emekli edilir. Kayıt başarısızsa video kodlaması başlamaz. Kaynak URI üzerine kayıt engellenir; ayar değişince eski sonuç geçersizleşir, açık kayıt seçicisi eski sonucu dışa aktaramaz.

Uygulama tarafından sahiplenilen ön plan iş kuyruğu kullanılır. Activity/ViewModel kapanması çalışan işi iptal etmez. Tamamlanan gerçek dosya boyutu ve iş kaydı kalıcıdır; yeniden açılınca gerçek sonuç doğrulanır. Süreç sonlandırması kodlamayı kaldığı kareden sürdürmez: kesinti görünür ve yeniden başlatma kullanıcı eylemidir. Normal iptal/hata ara PNG/MP4 dosyalarını temizler. Kurtarma tamamlandıktan sonra aktif iş yoksa, yedi günden eski sahipsiz özel iş dosyaları temizlenir; kayıtlı kaynaklar/sonuçlar ve yeni dosyalar korunur.

Önizleme 512, tembel küçük kareler 160 kenarda yüklenir; teslim edilmiş bitmap zorla recycle edilmez. Çıktı rasterizasyonu bir kareyi sırayla işler ve PNG'leri özel diske yazar; tüm kareler bitmap olarak bellekte tutulmaz. Büyük çıktı ve 100 karede disk/encoder maliyeti fiziksel cihazlarda ayrıca ölçülmelidir.

Mevcut Media3 1.5.1 ve Android codec'leri kullanılır. Yeni runtime SDK, codec, font veya ağ bağlantısı yoktur. Runtime lisans envanteri ticari dağıtım denetiminden geçirilir; Jev uygulamaya bağlanmaz. Host ffmpeg yalnız bağımsız test çözücüsüdür.

## Doğrulama

Debug APK ve debug cihaz testi kullanılır. Komutlar:

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory
python tools/localization/generate.py --check
python tools/licensing/audit_runtime.py --check
./tools/testing/slideshow-qa.ps1 -Suite feature
./tools/testing/slideshow-qa.ps1 -Suite regression -SkipInstall
./tools/testing/slideshow-qa.ps1 -Suite layouts -SkipInstall
python tools/testing/verify_slideshow_files.py <QA-çıktı-klasörü> --report output/development/slideshow/independent-decode.json
```

Özel `ofice_qa` emülatörü dışında test koşucusu çalışmaz; başka uygulamanın emülatörü/verisi değiştirilmez. Kanıtlar Git dışında `output/development/slideshow/` altında tutulur.

- 92 JVM testi; sıfır hata/atlama. Debug ve debug test APK derlemeleri geçti. Lint sıfır hata, 62 mevcut uyarı.
- 12 yeni cihaz testi: 8 motor/dayanıklılık ve 4 UI/aktarım testi. Dikey slayt hem sığdırma hem kırpma için doğrudan son çözünürlükte boyanır; 1920×1080 yüksek kalite gerçek MP4 de üretildi.
- 24 regresyon testi: fotoğraf PDF, sunum tasarımı/PDF, medya oturumu, video sıkıştırma/kesme ve dosya yönlendirme. Video kayıt/işlem hatası mesajlarının görünmemesi bu turda düzeltildi.
- Telefon, tablet, %200 yazı, koyu tema ve Arapça RTL için beş ayrı düzen tekrarı; çıktı ayarları ve ana düğme erişilebilir. Gerçek ekran görüntüleri ayrıca incelendi. Bir ADB kopması sonrası düzen koşusu yeniden yapıldı ve tamamlandı.
- Üç gerçek MP4, host ffmpeg ile tüm kareleri çözülerek bağımsız kontrol edildi: 640×360, 360×640, 640×640; 30 fps, 75 kare/2,5 saniye, 30 kırmızı ardından 45 mavi kare; H.264 ve sessiz. Önizleme resmi tek başına kanıt sayılmadı.
- 22 dilde anahtar/placeholder/kaynak üretimi; 95 runtime artifact ve mevcut native encoder için tam incelenmiş hash/lisans kapsamı geçti. Çeviri kontrolü bütün dillerde ana dili konuşan insan değerlendirmesi değildir.
- Yerel Graphify güncellendi: AST parser'ın bazı mevcut Kotlin dosyaları için kısmi çıkarma uyarıları kaynak derlemesinin yerine kullanılmadı. Kod/testler esas alındı; dış servise kaynak veya anahtar gönderilmedi.

Testler gerçek MP4 kareleri/süre/sıra/codec/çözünürlük, 1920×1080 yüksek kalite, çok alfabeli sunum tasarımı, bayt eşit kayıt ve özgün dosya korumasını kapsar. İptal, kaynak kopyası, atomik kayıt hatası/yeniden deneme, eski sonuç geçersizliği, ViewModel kapanması sırasında sıradaki işin tamamlanması, sonuç kurtarma, büyük görsel onayı ve iki araç arasındaki dönüş yolu sınanır. Ortak ressam/kaynak alımı için sunum PDF, fotoğraf PDF, video ve medya oturumu regresyonları da çalıştırılır.

Bu aşama fiziksel düşük donanım, gerçek dolu disk, pil/termal, tüm üreticiler, TalkBack ile manuel kullanıcı testi veya yeni kodun release R8 doğrulaması değildir. Yayın derlemesi kullanıcı istediğinde bunların yayın kontrolleri ayrıca yürütülür.
