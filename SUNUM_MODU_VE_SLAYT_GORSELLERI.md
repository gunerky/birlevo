# Tam ekran sunum, konuşmacı notları ve slayt görselleri

8 Ekim 2026 geliştirme kodu. Sürüm numarası ve imzalı `0.1.10 (11)` yayın dosyaları değişmedi. Yalnız debug uygulama/test APK'sı kullanıldı.

## Kullanım

Sunum düzenleyicisinde seçili slaydın **Konuşmacı notları** paneli açılır. Notlar slayt başına en fazla 8.000 karakterdir; otomatik proje kaydı, çoğaltma, sıralama ve eski taslakların açılması korunur. Not alanı olmayan eski kayıtlar boş notlarla açılır. Notlar slaydın görünür metni değildir; PDF, MP4 ve görsel çıktıya girmez.

**Diğer işlemler → Tam ekran sun** seçili projeyi sunar. Önceki/sonraki slayt düğmeleri, notları açma, kontrolleri gizleme ve başlat/duraklat/sıfırla sayacı vardır. Slayda dokunmak kontrolleri gösterir veya gizler. Geri önce not alanını kapatır, ardından gizli kontrolleri gösterir, sonra düzenleyiciye döner. Klavye okları/Page Up/Page Down, Home/End ve işlenmemiş boşluk tuşu slayt gezinmesini destekler; Escape aynı geri sırasını izler. Sistem tam ekran yardımını Android kendisi gösterebilir; uygulama bu sistem ayarını değiştirmez.

Sayaç monotonik saat kullanır; tarih/saat değişikliğinden etkilenmez. Arka plana geçişte ve geri dönüşte duraklatılır. Seçili slaydın kimliği ve süre Android SavedState üzerinden geri yüklenir; otomatik yeniden başlatılmaz. Sıfırlama açık onay ister. Sunum etkin olduğunda ekran açık tutulur; çıkışta önceki sistem çubuğu ve ekran açık tutma durumu geri verilir. Bu cihazın sunum ekranıdır; ikinci ekrana ayrı konuşmacı görünümü veya otomatik slayt ilerletme eklenmedi.

**Diğer işlemler → Slayt görseli** seçili slayttan yeni 16:9 görsel üretir:

| Seçenek | Kapsam |
|---|---|
| Biçim | PNG, JPEG, kayıplı WebP, BMP |
| Boyut | 1024×576, 2048×1152, 4096×2304 |
| Kalite | JPEG/WebP için 40–95; PNG/BMP için kalite kaydırıcısı yok |
| Sonuç | Gerçek boyut/çözünürlük, önizleme, kaydetme, açma/paylaşma, görsel aracına aktarım |

Görsel doğrudan nihai çözünürlükte ortak slayt ressamıyla çizilir; küçük önizleme büyütülmez. Tema, düzen, çok alfabeli metin ve mevcut görsel yerleşimi kullanılır. Sığmayan metin kesilmeden hata verir. Seçim/ayar değişince önceki sonuç geçersiz olur; yalnız otomatik kayıt zamanının değişmesi sonucu veya sayacı kesmez. Seçim, biçim, kenar ve kalite Android SavedState'e yazılır; süreç yeniden kurulduğunda geçici çıktı tekrar oluşturulur. Kaydetme, beklenen sonuç dosyasına bağlıdır; eski sonuç veya değişen dosya kabul edilmez. Kaynak URI'leri korunur, aktarım yeniden kodlama yapmaz ve önceki araca dönüş yolu kalır. Yeni slayt görüntüsü oluşturmak sıkıştırma başarısı olarak sunulmaz; görsel sıkıştırma aracındaki mevcut büyük kopya onayı aynen sürer.

PDF ve MP4 menü yolları korunur; bu adım tek seçili slaydın çıktısıdır. Toplu slayt ZIP'si, PPTX okuma/yazma ve harici ekran ayrımı ayrıca kapsamlandırılacaktır.

## Teknik yapı ve ticari dağıtım

- `PresentationClock` saf zaman hesabı; `PresentationPlayerViewModel` seçim, önizleme ve saat durumudur. `PresentationPlayerScreen` yalnız sunum arayüzü ve pencere yaşam döngüsünü yönetir.
- `PresentationImageEngine` nihai boyutta çizim/kodlama/kayıt; `PresentationImageViewModel` iptal, geçerli sonuç ve seçenekler; ekran ortak `ToolWorkspace` ve `OutputActions` kullanır.
- Sunum önizlemesi 1280×720, görsel aracının kaynak önizlemesi 512×288, çıktı önizlemesi en fazla 512 kenardır. Aynı anda yalnız seçili slayt hazırlanır; seçim listesi sabit kimlikli tembel listedir. Nihai çıktı/kalite seçenekleri önizleme optimizasyonuyla azaltılmaz.
- İptal edilen ve teslim edilmeyen bitmap/dosya temizlenir. UI'a teslim edilmiş bitmap zorla recycle edilmez. Tam boyut çalışma bitmap'i kodlamadan sonra, küçük çıktı önizlemesinden önce bırakılır. Slayt zemini opak olduğu için JPEG yolunun gereksiz ikinci tam boyut beyaz zemin bitmap'i de oluşturulmaz. Yedi günden eski araç önbelleği temizlenir.
- Proje kaydı mevcut tek yazıcı/AtomicFile/SavedState yolu üzerinden devam eder. `Slide.notes` isteğe bağlı ek alandır; proje şeması 1, slayt kimlikleri, görsel sahipliği ve FileProvider kökleri korunur. MP4 snapshot'ının slayt metadata'sına notlar kopyalanmaz.
- Yeni SDK, codec, font, ağ izni veya Jev bağlantısı yoktur. Mevcut Android/Compose ve `ImageEncoder` kullanılır. `exportRuntimeLicenseInventory` ve `audit_runtime.py --check`: 95 gerçek runtime artifact ve mevcut native encoder için incelenmiş hash/lisans kapsamı geçti. Codec patent/ülke değerlendirmesi bundan ayrı kalır.

## Doğrulama

```powershell
./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug exportRuntimeLicenseInventory
python tools/localization/generate.py --check
python tools/licensing/audit_runtime.py --check
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/presentation-mode-qa.ps1 -Suite feature
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/presentation-mode-qa.ps1 -Suite regression -SkipInstall
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/presentation-mode-qa.ps1 -Suite layouts -SkipInstall
graphify update . --no-cluster
```

Kanıtlar Git dışında `output/development/presentation-mode/` altında saklanır. QA koşucusu yalnız `ofice_qa`/`ofice_qa_api35` emülatörlerini kabul eder; düzen kontrolleri yazı, ekran ve gece ayarlarını sonunda geri verir.

99 JVM testi (7 yeni), debug/test APK derlemeleri ve lint geçti: sıfır hata, 62 mevcut uyarı. 8 yeni cihaz testi; not kaydı, sunum gezinmesi/sayacı, arka plan dönüşü, ekran yeniden oluşturma, pencere dönüşü, dört gerçek görsel biçimi, 4096 çıktı, notların piksele etkisizliği, kaynak koruması, iptal/taşma temizliği, bayt eşit kayıt, eski sonuç ve aktarım dönüşünü kapsar. 23 regresyon testi; otomatik kayıt/eski taslak, tema/düzen/PDF ve sunumdan MP4 snapshot'ını kapsar. Yeni metinler 22 dilde anahtar/placeholder/üretilmiş kaynak bakımından tamdır; ana dili konuşan insan doğrulaması sayılmaz.

Telefon, tablet, %200 yazı, koyu tema ve Arapça RTL için beş düzen tekrarı geçti; gerçek ekran görüntüleri incelendi. Büyütülmüş yazıda gezinme düğmeleri ayrı satırlara alınarak kelime bölünmesi giderildi. Fiziksel düşük RAM/ARM, eski Android/OEM, gerçek dolu disk, pil/termal, bu yeni ekranların manuel TalkBack kullanımı ve yeni kodun küçültülmüş release doğrulaması henüz ölçülmedi. Yerel AST grafiği güncellendi; bazı Kotlin dosyalarındaki kısmi çıkarma uyarıları derleme/testlerin yerine kullanılmadı. SavedState geri yükleme ve Activity yeniden oluşturma sınandı; gerçek işletim sistemi süreç öldürme/son uygulamalardan dönüş matrisi ayrıca bekler. Emülatör sonucu fiziksel cihaz FPS, bellek veya pil garantisi değildir.

Sıradaki ürün işi: PPTX içe/dışa aktarımı için küçük, çok alfabeli ve görsel içeren uyumluluk denemesi; biçim kayıpları ve ticari lisans koşulları doğrulanmadan genel Office desteği ilan edilmez.
