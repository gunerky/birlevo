# Birlevo 0.1.0 imzalı kapalı test paketi — 5 Ekim 2026

**Güncel aday: 0.1.10 (11).** İmzalı APK / AAB, İngilizce / Türkçe notlar, lisans ve R8 doğrulama kaydı [0.1.10 yayın raporunda](RELEASE_0.1.10.md); yerel dosyalar `output/releases/0.1.10/` altında. Bu paket Play’e gönderilmedi. Aşağıdaki bölüm 0.1.0 paketinin tarihsel kaydıdır.

Play Console'a yüklenecek dosya: [birlevo-0.1.0-1.aab](../../output/releases/0.1.0/birlevo-0.1.0-1.aab).
Dosya yerel olarak hazırlandı; Google Play'e henüz yüklenmedi. Mağaza görselleri, Console formları, testçi listesi ve inceleme aşaması ayrıca tamamlanacak.

## Paket kimliği

- `applicationId`: `studio.birlevo.android`
- Sürüm: `0.1.0`; sürüm kodu: `1`
- Minimum Android: API 24 / Android 7.0; hedef: API 36
- Boyut: 12.119.220 bayt (yaklaşık 12,1 MB)
- SHA-256: `35f68f686e49e42d21afb6c4d7d6205b7774e3f64dd7d91aafda0e810812cd33`
- Yükleme sertifikası SHA-256: `3D:14:F0:55:9A:4E:0F:63:32:30:3F:D3:52:01:7B:68:D3:28:DA:AD:41:77:1E:2B:8B:D3:83:BF:56:85:6F:2C`

Makinece okunabilir inceleme kaydı: [build-info.json](../../output/releases/0.1.0/build-info.json).

## Doğrulamalar

- `bundleRelease`, `assembleRelease` ve `lintRelease` başarılı. Lint: 0 hata, 24 uyarı; uyarılar bağımlılık güncellemesi, kullanılmayan kaynaklar ve KTX yazım önerileriyle sınırlı.
- `jarsigner` AAB imzasını doğruladı; paket içindeki imza sertifikası oluşturulan Birlevo yükleme sertifikasıyla eşleştirildi. Kendinden imzalı sertifika ve zaman damgası uyarıları ayrıntılı yerel kayıt içinde tutulur; paket imzası doğrulanmıştır.
- Google `bundletool 1.18.1 validate` başarılı; paket manifesti AAB içinden çıkarılıp incelendi.
- Paket debug/test-only değil. INTERNET, AD_ID ve geniş medya okuma izni yok; reklam veya analiz SDK'sı bulunmuyor. ACCESS_NETWORK_STATE ve uygulamanın imza korumalı dahili alıcı izni mevcut.
- Son `birlevo_logo_safe.png` piksel içeriği paket içindeki kaynakla eşleştirildi. Lacivert zemin tam tuvali doldurur; yalnızca merkez işaret küçültülmüştür.
- ARM64 ve x86_64 yerel ELF yükleme bölümleri 16 KB hizalı. Release APK için `zipalign -c -P 16 4` ve `apksigner verify` başarılı. Bu kontrol her cihazda çalışma garantisi değildir.
- Daha önce Android 16 emülatöründe 28/28 debug cihaz testi geçti. AAB'den üretilen ve yalnızca yerel deneme için debug sertifikasıyla imzalanan evrensel APK emülatörde açıldı; ana ekran, görsel sıkıştırma, Video düzenle ve sunum/PDF önizleme kontrollerine erişim doğrulandı. Bu deneme APK'sı mağazaya yüklenecek dosya değildir.
- Kullanıcı fiziksel telefon testinin şimdilik ertelenmesini seçti. Xiaomi'de önceki çalıştırma 27/28 geçti; bir görsel testi ana ekran metin beklemesinde takıldı. Sonraki güncel derleme kurulumu telefon tarafından engellendiğinden düzeltme bu telefonda henüz doğrulanmadı.

## Anahtar saklama ve yeniden derleme

Yükleme anahtarı `C:\Users\user\AppData\Local\Birlevo\signing\birlevo-upload.p12` konumunda, proje/Git dışında tutulur. RSA 3072 bit, alias `birlevo-upload`, sertifika son tarihi 20 Şubat 2054. Şifre rastgele üretildi; `upload-credential.clixml` içinde Windows DPAPI ile aynı kullanıcı/bilgisayar için şifreli saklanır. Klasör erişimi mevcut Windows kullanıcısı ve SYSTEM ile sınırlıdır. Şifre sohbet veya komut argümanlarında yayımlanmadı.

İkinci yerel kopya: `C:\Users\user\Documents\Birlevo-signing-backup`. Anahtar karması ve şifreli kaydın açılabilirliği kontrol edildi. **İki kopya da aynı bilgisayardadır; bu bağımsız felaket kurtarma yedeği değildir.** Windows'u yeniden kurmadan veya başka bilgisayara geçmeden, klasördeki `RECOVERY.txt` adımlarına göre şifreyi mevcut Windows oturumunda bir parola yöneticisine alın ve `.p12` dosyasını ayrı korumalı depolamaya kopyalayın. DPAPI kaydını başka bilgisayara kopyalamak tek başına yeterli değildir.

Tekrar imzalı derleme:

```powershell
.\tools\release\build-signed-release.ps1
```

Betik şifreleri yalnızca derleme alt sürecinin ortamına verir ve sonunda eski ortamı geri getirir; kalıcı Gradle daemon kullanmaz. `initialize-upload-key.ps1` mevcut anahtar klasörlerinin üzerine yazmayı reddeder; sonraki sürümlerde aynı yükleme anahtarını kullanın. İleride Play'e yüklenmiş sürüm kodunu tekrar kullanmayın.

Yalnızca açık sertifika: [upload-certificate.pem](../../output/releases/0.1.0/upload-certificate.pem). Özel `.p12` anahtarı Play Console'a dosya eki olarak göndermeyin. Yükleme anahtarı ve Google'ın dağıtım için kullandığı uygulama imza anahtarı ayrı rollerdir: [Android uygulama imzalama yönergesi](https://developer.android.com/studio/publish/app-signing).

Google Play kabulü/incelemesi henüz yapılmadı. Sıradaki iş mağaza görselleri ve Console kapalı test hazırlığıdır.
