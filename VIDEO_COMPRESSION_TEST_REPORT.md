# Video sıkıştırma — 4 Ekim 2026

MP4 seçme, üç kalite profili, çözünürlük sınırı, gerçek çıktı oynatma, boyut karşılaştırması ve yeni belgeye kaydetme eklendi. İşlem ilerlemesi ve iptal var. Ayar değişince önceki çıktı geçersiz olur. Orijinal dosya korunur. Ekranın 15 yeni metin anahtarı mevcut 22 yerelleştirmeye eklendi; kaynak kontrolü toplam 60 anahtar için geçti. Çeviriler taslaktır, yayın öncesi dil kontrolü gerekir.

## Doğrulama

Kurulu `medium_phone` AVD'sinde Android 16 / API 36, x86_64, dört sanal çekirdek ve 2 GB yapılandırılmış RAM kullanıldı.

| Senaryo | Ayarlar | Sonuç |
| --- | --- | --- |
| Telefon | 1080×2400, 420 dpi, yazı 1.0 | 19/19; 7 video, 6 görsel, 6 mevcut uygulama testi |
| Tablet boyutu | 1920×1200, 240 dpi, yazı 1.0 | Video testleri 7/7 |
| Dar ekran / büyük yazı | 1080×2400, 540 dpi, 320 dp genişlik, yazı 2.0 | Video testleri 7/7 |
| Yerel JUnit | Proje verisi ve video çıktı profili | 6/6 |
| Debug APK / test APK / lint | Gradle | Başarılı; 0 lint hatası, 17 uyarı |
| Dil kaynakları / APK hizalama | 22 dil, 60 anahtar; zipalign | Başarılı |

Lint uyarıları 14 bağımlılık sürümü önerisi ve 3 KTX kullanım önerisidir. APK yaklaşık 15,4 MiB; geliştirme anahtarıyla imzalanmıştır. Tablet boyutu aynı telefon AVD'sinin ekran ayarlarıyla oluşturuldu; gerçek tablet donanımı değildir. Test sonunda ekran, yoğunluk ve yazı ayarları ilk değerlerine döndürüldü. Telefon ekran görüntüsünü saklamak için uçtan uca video testi ayrıca tek başına çalıştırıldı ve geçti.

Yedi video testi şunları doğruladı:

- Video seçimi, küçük dosya / 640 px seçimi, gerçek H.264 çıktı, Activity yeniden oluşturma, uygulama içinde ExoPlayer ile ilerleyen oynatma ve yeni içerik URI'sine kaydetme. Dışa aktarılan dosyadan farklı zamanlardaki kareler çözümlendi; hareket vardı. AAC parçası MediaCodec ile PCM'e çözümlendi; ses örnekleri sıfır değildi. Süre / çözünürlük doğruydu ve kaynak SHA-256 değeri değişmedi. Ayar değişince eski çıktıyı kaydetme seçeneği kalktı.
- 320×240 sessiz videoda büyütme yapılmaması ve ses parçası eklenmemesi.
- Dönüş matrisi 90 derece olan dosyada 360×640 dikey görüntünün korunması; çözümlenen karenin de dikey olması, sesin bulunması ve kaynağın değişmemesi.
- Dengeli ve yüksek kalite profillerinin 1280×720 oynatılabilir çıktıları. Bu sabit örnekte yüksek bit hızlı çıktı daha büyüktü.
- Geçersiz MP4 için hata mesajı ve dışa aktarma seçeneğinin bulunmaması.
- Kodlama sırasında iptal; kısmi çıktının silinmesi, kaynağın korunması ve ardından başka dosyanın başarıyla işlenmesi.
- Kaynak URI'sine yazmayı reddetme; hedef sağlayıcı hatasından sonra yeni hedefe başarıyla yeniden kaydetme.

Üç ekran görüntüsü gözle incelendi. Tablet boyutunda kaynak ve çıktı yan yana; dar pencerede tek sütun. Büyük yazıda seçenekler satırlara yayılıyor; kaydırma ile işlem ve kaydetme düğmeleri erişilebilir. Dar ekranda uzun üst başlık üç noktayla kısalıyor. Bu kontroller tam TalkBack denetimi değildir. 22 uygulama dili ve Arapça / Farsça yön kontrolü mevcut temel testlerde geçti; yeni video ekranının tüm dillerde dilsel / görsel incelemesi henüz yapılmadı.

Dosya seçici sonuçları Espresso Intents ile ayrı test sağlayıcısından verildi. Gerçek ContentResolver, Media3 Transformer, cihazın MediaCodec kodlayıcı / çözücüleri ve belge yazma çalıştı. Sistem dosya seçicisinin kendi ekranlarında gezinme ve farklı bulut sağlayıcıları henüz sınanmadı. Test sağlayıcıları ve özgün hareketli renk / ses örnekleri yalnızca test APK'sındadır. Üretim uygulamasına video test dosyaları dahil edilmedi. Örnek üreticisi `tools/testing/generate_video_fixtures.py`; FFmpeg yalnızca geliştirme bilgisayarında test örneklerini üretmek için kullanıldı.

## Ölçülen örnek

Altı saniyelik, 30 kare/s, 1280×720 H.264 ve AAC test videosunun kaynak boyutu **4.007.503 bayt**.

| Profil | Çıktı | Dosya boyutu | Kaynağa göre azalma | Telefon kodlama süresi |
| --- | --- | --- | --- | --- |
| Küçük dosya / 640 px | 640×360 | 587.614 bayt | %85,3 | 5.410 ms |
| Dengeli / 1280 px | 1280×720 | 1.527.130 bayt | %61,9 | 7.984 ms |
| Yüksek kalite / 1280 px | 1280×720 | 2.882.102 bayt | %28,1 | 6.212 ms |

Süreler son tam telefon testinden alınmıştır; kaynak kopyalama, kullanıcı seçimi, oynatma ve dışa aktarma dahil değildir. Küçük dosya profili tablet boyutunda 4.533 ms, büyük yazı senaryosunda 4.672 ms sürdü. Aynı kodlayıcı kullanıldığı için bu farklar ekran büyüklüğünün hız etkisini kanıtlamaz; emülatör ve bilgisayar yükü etkiler. Daha yüksek kalite her zaman daha yavaş olmak zorunda değildir.

Telefon testinde dışa aktarma sonrasında tek anlık toplam PSS **188.357 KB**, yaklaşık **183,9 MiB** idi. Tam telefon koşusu içindeki seçili 50 saniyelik aralıkta, yaklaşık iki saniyede bir alınan 21 PSS örneğinin en büyüğü **190.295 KB**, yaklaşık **185,8 MiB** idi. Bu, örneklenen aralığın en büyük değeridir; işlem boyunca gerçek bellek zirvesi veya sızıntı ölçümü değildir. Test altyapısı ve diğer testlerin etkileri aynı süreçte bulunur.

Bu debug emülatör ölçümleri tüm videolarda boyut veya hız garantisi değildir. Zaten iyi sıkıştırılmış küçük dosyalar büyüyebilir; arayüz bunu belirtir.

## İlk destek kapsamı ve sınırlar

- Girdi: MP4 içinde tek H.264 görüntü, isteğe bağlı tek AAC ses; SDR. HDR, altyazı, farklı kodek ve ek parçalar reddedilir.
- En fazla 10 dakika, 512 MiB, en uzun kenar 4096 px ve 8.847.360 piksel. Kare hızı dosya bilgisinde varsa en fazla 60 kare/s. Bu üst sınırların tamamı emülatörde yük testiyle doğrulanmadı.
- Çıktı: MP4 / H.264; uyumlu AAC ses parçası korunur. Sessiz girdi sessiz kalır. Kaynakta bulunmayan ses eklenmez.
- En uzun kenar üst sınırı 640 / 1280 / 1920 px; küçük girdiler büyütülmez. Kodlayıcı için çift piksel ölçüleri kullanılır; en fazla bir piksellik yuvarlama olabilir.
- Bit hızları 720p için yaklaşık 1 / 2 / 4 Mbit/s temelinden piksel alanına göre ölçeklenir; 0,2–8 Mbit/s aralığıyla sınırlıdır. Bunlar kodlayıcıya verilen hedeflerdir, kesin dosya boyutu değildir. Hedef megabayta otomatik optimizasyon henüz yoktur.
- Cihazın kodlayıcı desteği gerekir. Motorun otomatik çıktı çözünürlüğü geri dönüşü kapalıdır; desteklenmeyen durumda hata ve daha düşük çözünürlük / farklı dosyayla yeniden deneme sunulur.

Kaynak uygulamanın özel önbelleğine kopyalanır; orijinal yazma için açılmaz. Dosya kopyalama, inceleme ve dışa aktarma IO iş parçacığında; Transformer başlatma / ilerleme / durdurma kendi ana iş parçacığında yürür. İptalde motor durdurulduktan sonra kısmi çıktı silinir. Başarısız belge yazmada sağlayıcı destekliyorsa yeni kısmi belge kaldırılır. İşlem için 20 dakika zaman aşımı vardır; bu süre dolunca yeniden deneme sunulur.

Ekran yeniden oluşturulmasında ViewModel geçici oturumu korur. Uygulama süreci öldürüldüğünde oturum geri yüklenmez; yeniden seçim gerekir. Uzun işlemler için arka plan hizmeti ve tamamlanma bildirimi henüz yoktur. Süreç ölümü, uzun arka plan kullanımı ve en büyük girdiler ayrıca test edilmelidir. Araç açılınca 24 saatten eski işlem önbelleği temizlenir. Bu geliştirme sürümünde reklam SDK'sı eklenmedi.

## Kanıtlar ve devam

Git dışında tutulan `test-artifacts/video-compression/` klasöründe telefon XML sonucu; tablet / büyük yazı test çıktıları; profil ölçümleri; bellek CSV'si ve `phone.png`, `tablet.png`, `large-font.png` bulunur. Test kodu `VideoCompressionDeviceTest.kt`, motor `media/VideoCompressor.kt`, profil hesabı `media/VideoProfile.kt`, arayüz `ui/VideoCompressionScreen.kt` içindedir.

Gerçek cihazlar, eski Android sürümleri, farklı üretici kodlayıcıları, düşük depolama, sistem dosya seçicisi, uzun kullanım ve bellek zirvesi kontrolleri yayın öncesinde gerekir. Sonraki geliştirme: sunum taslaklarından PDF çıktısı ve yeni dosyaya kaydetme.

Teknik kaynaklar: [Media3 Transformer başlangıcı](https://developer.android.com/media/media3/transformer/getting-started), [dönüşümler](https://developer.android.com/media/media3/transformer/transformations), [desteklenen formatlar](https://developer.android.com/media/media3/transformer/supported-formats).
