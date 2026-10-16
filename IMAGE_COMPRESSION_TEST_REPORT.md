# Görsel sıkıştırma — 4 Ekim 2026

JPEG / PNG içeri alma, kalite / çözünürlük seçimi, gerçek JPEG önizlemesi ve yeni dosyaya kaydetme eklendi. Orijinal ve çıktı boyutları gerçek dosya uzunluklarından alınır. Ayarlar değiştiğinde önceki çıktının kaydedilmesi engellenir. Çıktı küçük değilse kullanıcı bilgilendirilir. Yeni ekranın 17 metin anahtarı mevcut 22 dile eklendi; kaynak kontrolü toplam 45 anahtar için geçti. Çeviriler yayın öncesi dil kontrolü gerektirir.

## Doğrulama

Android 16 / API 36, x86_64, dört sanal çekirdek ve 2 GB yapılandırılmış RAM kullanan kurulu `medium_phone` AVD'sinde:

| Senaryo | Ayarlar | Son test sonucu |
| --- | --- | --- |
| Telefon | 1080×2400, 420 dpi, yazı 1.0 | 12/12; 6 görsel ve 6 mevcut uygulama testi |
| Tablet boyutu | 1920×1200, 240 dpi, yazı 1.0 | Görsel testleri 6/6 |
| Dar ekran / büyük yazı | 1080×2400, 540 dpi, 320 dp genişlik, yazı 2.0 | Görsel testleri 6/6 |
| Yerel veri testleri | JUnit | 3/3 |
| Derleme / lint | Debug APK ve test APK | Başarılı; 0 hata, 12 uyarı |

Lint uyarıları 10 bağımlılık sürümü önerisi ve 2 KTX kullanım önerisidir. Dil kaynak kontrolü ve APK paket hizalama kontrolü geçti. Tablet boyutu aynı telefon AVD'sinin geçici ekran ayarlarıyla oluşturuldu; gerçek tablet donanımı değildir. Test sonunda ekran / yoğunluk / yazı ayarları eski değerlerine döndürüldü.

Altı görsel testi şunları doğruladı:

- Seçim sonucundan gerçek içerik URI'sini açma, kaliteyi 60 ve kenar sınırını 1280 px seçme, önizleme üretme, Activity yeniden oluşturma ve yeni hedef URI'sine JPEG yazma. Dışa aktarılan dosya yeniden çözümlendi; ölçüleri doğru. Orijinalin SHA-256 değeri değişmedi. Ayar değişince eski çıktı geçersiz oldu.
- Şeffaf PNG'nin beyaz zemine dönüşmesi; çok küçük PNG'den daha büyük JPEG çıktığında uyarının gösterilmesi.
- EXIF'in sekiz yön / ayna durumunun renk köşeleri ve piksel ölçüleriyle doğrulanması. Kaynak değişmedi; çıktıda sanatçı ve GPS alanları bulunmadı.
- Geçersiz görselde hata mesajı ve dışa aktarma seçeneğinin bulunmaması.
- Yavaş içerik akışı sırasında içe almayı iptal etme; kısmi geçici dosyanın kaldırılması ve önceki görsel / sonucun korunması.
- Kaynak URI'sine yazmayı reddetme; hedef sağlayıcı hatasından sonra başka hedefe başarıyla yeniden kaydetme.

Telefon, tablet boyutu ve büyük yazı ekran görüntüleri gözle incelendi. Kaydırma, kalite ayarı, çözünürlük seçenekleri ve çıktı düğmesi erişilebilir kaldı. Bu testler tam TalkBack denetimi değildir.

Dosya seçici sonuçları Espresso Intents ile kontrollü bir test sağlayıcısından verildi. Gerçek ContentResolver, bitmap çözümleme, JPEG kodlama ve çıktı yazma çalıştı; sistem dosya seçicisinin kendi ekranlarında gezinme ve farklı bulut / dosya sağlayıcıları henüz test edilmedi. Test sağlayıcısı ayrı test APK'sına aittir; dağıtılan uygulama APK'sında bulunmaz.

## Ölçülen örnek

Sabit rastgele renklerden oluşan **2048×1536 JPEG** test görseli, kalite 60 / 1280 px sınırıyla **1280×960 JPEG** oldu:

- Kaynak: **4.405.627 bayt**.
- Çıktı: **401.189 bayt**, yaklaşık **%90,9 azalma**.
- Son telefon denemesinde çözümleme + ölçekleme + JPEG kodlama + küçük önizleme: **117 ms**. Tablet boyutunda **105 ms**, büyük yazı senaryosunda **129 ms**. Kaynak kopyalama, kullanıcı seçimi ve dışa aktarma bu süreye dahil değildir.
- Telefon testinde dışa aktarma sonrası tek anlık toplam PSS: **126.486 KB**, yaklaşık **123,5 MiB**. En yüksek bellek tüketimi veya sızıntı ölçümü değildir; instrumentation ve diğer testler de aynı süreçteydi.

Rastgele renkli test görseli ve debug emülatör ölçümü bütün fotoğraflar için boyut veya hız vaadi değildir. Küçük ve zaten iyi sıkıştırılmış dosyalar büyüyebilir.

## İşlem sınırları

Girdi JPEG / PNG ile, dosya 128 MiB ve görsel 100 MP ile sınırlı. Ara çözümleme yaklaşık 4 MP ile sınırlanır; küçük veya örneklenmiş görseller büyütülmez. Seçilen değer kesin çıktı ölçüsü değil, en uzun kenar üst sınırıdır. Önizlemeler 512 px sınırında gösterilir; çıktı dosyası ayrı üretilir. Orijinal özel önbelleğe kopyalanır, yazma için açılmaz. Dosya çözümleme / kopyalama / dışa aktarma IO iş parçacıklarında yürür.

JPEG kayıplıdır; renkler değişebilir. Şeffaflık beyaz olur, animasyon ve EXIF bilgileri korunmaz. Kullanıcı bu bilgiyi işlem ekranında görür. İptal mevcut yerel kodlayıcı çağrısını anında kesmeyebilir; çağrı tamamlandığında sonuç atılır. Başarısız dışa aktarmada yeni kısmi belge sağlayıcı destekliyorsa kaldırılır.

Ekran yeniden oluşturulmasında ViewModel geçici oturumu korur. Süreç ölümü sonrası oturum geri yükleme henüz yoktur; kullanıcı yeniden seçim yapar. Dosya seçicisinden süreç yeniden başlatıldıktan sonra dönen boş yeni belgenin silinmesi için koruma eklendi; bu süreç ölümü senaryosu henüz emülatörde ayrıca çalıştırılmadı. 24 saatten eski işlem önbelleği araç tekrar açıldığında temizlenir.

## Kanıtlar ve devam

Git dışında tutulan `test-artifacts/image-compression/` içinde son telefon, tablet ve büyük yazı test çıktıları; `metrics.txt`; `phone-final.png`, `tablet.png` ve `large-font.png` bulunur. Test kodu `ImageCompressionDeviceTest.kt`, işlem motoru `media/ImageCompressor.kt`, arayüz `ui/ImageCompressionScreen.kt` içindedir.

Gerçek cihazlar, eski Android sürümleri, sistem dosya seçicisi, farklı sağlayıcılar, düşük depolama, en büyük girdiler, süreç ölümü ve uzun kullanım / bellek zirvesi kontrolleri yayın öncesinde gerekir. Sonraki geliştirme adımı tek video için sıkıştırma motoru ve aynı çıktı akışıdır.

Teknik kaynaklar: [Android bitmap örnekleme](https://developer.android.com/topic/performance/graphics/load-bitmap), [ExifInterface](https://developer.android.com/jetpack/androidx/releases/exifinterface), [Android belge seçme ve oluşturma](https://developer.android.com/training/data-storage/shared/documents-files).
