# 16 Ekim 2026 — cihaz kapasitesi ve ZIP sınırları

Toplu çıktı ve ZIP okuyucusu aynı `StreamingLimits` içerik tavanını kullanır: tek dosya / toplam açılmış içerik 512 MiB. Arşivin kendisi için 528 MiB ayrılır; sıkıştırılmamış DEFLATE blokları, UTF-8 adlar ve ZIP başlıkları 512 MiB içeriği eski 512 MiB arşiv sınırının üzerine çıkarabilir. ZIP32 / stored / deflate kapsamı, 1000 öğe / 8 MiB indeks, yol / CRC / gerçek açılmış bayt kontrolleri sürer. ZIP64, şifreli ve split arşivler eklenmedi.

## Cihaza göre uyarlama

`StreamingStoragePolicy` teknik tavanı büyütmez; uygulamanın gerçek özel depolama birimindeki **o anda kullanılabilir alanla** uygulanabilir çalışma bütçesini belirler. `StatFs.availableBytes` kullanılır; toplam kapasite veya sistem önbelleğinin silinebileceği varsayımı boş alan sayılmaz.

- Güvenlik payı: birimin toplam alanının yüzde 2'si; en az 64 MiB, en fazla 512 MiB. Bu ürün politikasıdır, Android'in garanti ettiği bir eşik değildir.
- Yeni ZIP kopyası için gösterilen sınır `min(528 MiB, kullanılabilir alan − güvenlik payı)` olur. Eski oturum yeni kopya başarıyla kaydedilene kadar silinmez.
- Toplu ZIP oluşturma, çıktı toplamına ilave muhafazakâr ZIP ek yüküyle ön kontrol yapar. İçerik yeniden kodlanmaz; okuyucunun indeks / boyut sözleşmesi de yayınlamadan doğrulanır.
- Seçili dosyalar hedef klasöre dokunmadan önce toplam açılmış boyut kadar özel alan gerektirir. Önce bütün seçili içerik CRC ile doğrulanır, ardından kullanıcı hedefi oluşturulur.
- Özel kopya, ZIP yazma, çıkarma aşaması ve dışa yazma sırasında her en fazla 1 MiB yazmada yerel alan yeniden ölçülür. Alan azalırsa işlem durabilir. Kalite / çözünürlük düşürülmez; kısmi özel dosyalar temizlenir, kaynaklar / tamamlanmış toplu sonuçlar korunur.
- Uzak / USB / SD hedefinin alanı özel depolama ölçümünden çıkarılamaz. SAF sağlayıcısının yazma hatası otoritatiftir. Yeni klasörü temizleme ve temizlenemeyen kısmi hedefi bildirme korumaları sürer.

Bu bir disk rezervasyonu veya başarı garantisi değildir. İki ölçüm arasında alan değişebilir; gerçek IO hataları ve iptal yine ele alınır. Hatalı alan bilgisi sınırsız kabul edilmez. ZIP ekranı son ölçülen yeni kopya sınırını ve alan hatasını gösterir; yeni metinler 22 dil kataloğundadır. Kayıt şemaları, FileProvider yetkisi / kökleri ve sonuca özel büyük kopya onayları değişmez.

## RAM ile dosya boyutu farklıdır

ZIP içeriği 64 KiB tamponlarla akar; 512 MiB dosya RAM'e alınmaz. Dosyayı toplam RAM'in keyfî oranına göre reddetmek uygun değildir. İndeks / öğe sayısı ayrıca sınırlıdır. Görselin çözülmüş piksel belleği, video decoder / encoder / GPU ve PDF sayfa maliyeti başka bütçelerdir. Bu adım onların mevcut piksel / süre / sayfa / codec sınırlarını değiştirmez ve bütün medya motorlarına yeni bellek garantisi eklemez. Sonraki kapasite çalışması bu motorlara işlem bazlı bellek / geçici çıktı bütçesi eklemek olmalıdır; kalite ve son çözünürlük korunacaktır.

Resmî kaynaklar: [StatFs](https://developer.android.com/reference/android/os/StatFs), [StorageManager](https://developer.android.com/reference/android/os/storage/StorageManager), [MemoryInfo](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo). `getAllocatableBytes` önbellek temizliği varsayabildiği için hızlı periyodik ölçüm olarak kullanılmaz.

## Doğrulama

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
powershell -NoProfile -ExecutionPolicy Bypass -File tools/testing/archive-capacity-qa.ps1 -SkipBuild
python tools/localization/generate.py --check
python tools/licensing/audit_runtime.py --check
graphify update . --no-cluster
```

JVM testleri değişen alan / hatalı ölçüm / sayısal sınırlar, 128 MiB üzeri akış / CRC / iptal ve **tam 512 MiB içeriğin başlıklarla daha büyük ZIP olarak açılması** yollarını kapsar. Koşucu yalnız `ofice_qa` emülatörünü kabul eder. Gerçek WAV dönüştürme / ZIP / yeniden açma / gerçek SAF klasörüne çıkarma zinciri 128 MiB üzerindedir; bağımsız host testi hash / CRC / WAV örnekleme ve tam FFmpeg çözümü yapar. Düşük alan gerçek diski doldurarak değil sentetik ölçümlerle sınanır. Tamamlanan test sonuçları aşağıya eklenir.

Kanıtlar Git dışındaki `output/development/archive-capacity/` içindedir. Fiziksel cihaz, gerçek dolu disk, düşük RAM'de 512 MiB medya kodlama ve bütün SAF sağlayıcıları ölçülmüş sayılmaz. Yeni SDK / codec / font veya sürüm numarası / imzalı APK / AAB yoktur.

### Kabul sonucu

- Debug uygulama / test APK'sı, **143 JVM testi** ve lint geçti: 0 hata, 67 mevcut uyarı, 2 ipucu. `build-final.log`, son 512 MiB testi için `unit-boundary.log`.
- **10 emülatör testi** geçti: kapasite / büyük dosya 3, mevcut klasör korumaları 4, kaynak / eski istek koruması 1, iptal 1, gerçek UI açma / kayıt / yeniden oluşturma / temizleme 1. İlk yapay WAV sağlayıcısı kendi süreç alanından uygulama sınıfına bağlanmaya çalıştığı için boş girdi üretmişti; sağlayıcı bağımsız WAV başlığıyla düzeltildi. Başarısız koşu kabul kanıtı değildir; son kabul `qa-final.log` içindedir.
- Gerçek **134.283.308 bayt WAV**, **134.303.936 bayt ZIP** olarak paketlenip SAF klasörüne çıkarıldı. ZIP / çıkarılan dosya / paketleme öncesi sonuç hash eşitliği, CRC, **96 kHz / stereo / PCM16 / 349,696 saniye** ve host FFmpeg tam çözümü geçti (`independent-check.json`). Kaynak hash'i de korundu.
- Sentetik düşük alan: yeni kaynak eski oturumu değiştirmedi; staging reddi kullanıcı klasörüne dokunmadı; birinci dosyadan sonraki alan düşüşü yalnız yeni klasörü geri aldı; tekrar deneme geçti. Paketleme reddi tamamlanmış dönüştürme dosyasını / kuyruğunu korudu.
- 22 dil ve placeholder kontrolü, 95 incelenmiş runtime artifact + native encoder kapsamı, dört ABI / sekiz kütüphane 16 KiB hizalama / kaynak bildirimi ve APK Jev izolasyonu geçti. Yeni runtime paketi eklenmedi.
