# Birlevo 0.1.9 — kapalı test paketi

7 Ekim 2026. Kullanıcının isteğiyle hazırlanan sürüm: **0.1.9 (10)**, paket kimliği `studio.birlevo.android`. Android 7 / API 24 ve üzeri; hedef API 36. Google Play'e yükleme veya yayın yapılmadı. Önceki 0.1.8 (9) paketi korunur.

## Yerel dosyalar

- Play için imzalı AAB: `output/releases/0.1.9/birlevo-0.1.9-10.aab`.
- Doğrudan QA kurulumu için imzalı, R8 ile küçültülmüş APK: `output/development/0.1.9/birlevo-0.1.9-10-release.apk`.
- İngilizce / Türkçe etiketli notlar: `output/releases/0.1.9/release-notes.txt`; ayrı dil dosyaları da aynı klasörde.
- Paket incelemesi, doğrulama özeti ve kullanım kılavuzu: `output/releases/0.1.9/`.
- Hata analizi eşleme dosyası: `output/development/0.1.9/mapping.txt`. Özel imza anahtarı proje dışında kalır.

AAB: 13.814.929 bayt. SHA-256: `cd7b1494670042a63d891b580f478429deb0c7df9c8b19af426d8c0f06977f74`. Üretilen dosyalar ve test kanıtları Git dışında tutulur.

## Play Console'da yüklerken

1. Önce dahili teste sürüm kodu 10 olan AAB'yi ekle; önceki sürümü yeniden kullanma. İngilizce / Türkçe notları ilgili dil etiketleriyle yapıştır. Mağazanın Türkçe çevirisi bu paket için zorunlu değildir.
2. Bu sürümde kullanıcı başlatmalı video / ses işleme ve fotoğraftan PDF üretimi ekran kapansa da sürebilir. Manifestte `FOREGROUND_SERVICE_MEDIA_PROCESSING` ve `FOREGROUND_SERVICE_DATA_SYNC` vardır. Play Console bu beyanı isterse **Uygulama içeriği → Ön plan hizmeti izinleri** bölümünü tamamla. Android 15+ `mediaProcessing`, Android 10–14 uyumluluk yolu `dataSync` kullanır. Medya kod dönüştürme / yerel dosya işleme kullanımını açıkla; bunlar sunucuya yükleme veya arka planda medya oynatma değildir. [Google'ın beyan yönergesi](https://support.google.com/googleplay/android-developer/answer/13392821).
3. Beyan için örnek İngilizce açıklama: "Users start video conversion, audio extraction or photo-to-PDF creation from a visible tool screen. Processing continues locally if they leave the screen. An ongoing notification provides cancellation and a return to the app. Interrupting a task prevents its requested output from being completed; jobs do not start automatically."
4. İstenen demo bağlantısında kendi test dosyanla işlemi başlatmayı, ekranı terk edince bildirimi ve iptal / sonuç akışını göster. Video dosyasını veya bağlantısını Play'e sunma bu geliştirme işlemi kapsamında yapılmadı.
5. Veri güvenliği ve reklam beyanları mevcut paketin gerçek davranışını anlatmalı: bu sürümde reklam SDK'sı, Jev bağlantısı, kullanıcı dosyalarını sunucuya gönderme veya uygulama hesabı yoktur. Reklam eklenince reklam / reklam kimliği / veri güvenliği / gizlilik metni birlikte yeniden değerlendirilir.
6. Dahili test doğrulamasından sonra aynı sürüm kodunu kapalı test kanalına taşı; testçi, ülke ve katılım bağlantısını kontrol et. İnceleme / yayına sunmayı kullanıcı yapar.

Mağaza açıklaması yeni PDF, belge / tablo, ses, BMP ve koşullu HEVC / WebM çıktılarını doğru anlatmalı. Her cihazda her codec / boyut desteği veya her dönüşümde daha küçük dosya sözü verilmez. DOCX / XLSX, VP9 / AV1 yazma, çok klipli video ve reklam henüz bu sürümün yeteneği değildir.

## Doğrulama

55 JVM ve 49 benzersiz debug Android testi geçti. İmzalı R8 APK üzerinde 12 iş akışı tek koşuda, süreç yeniden açma ve tablet / dar ekran + 2 kat yazı / koyu modda video hedefleri ile PDF okuyucusu geçti (6 yerleşim tekrarı). 22 dil kaynak denetimi ve 20 çevrimdışı Jev testi geçti; canlı Jev çağrısı yapılmadı. Debug / release lint: 0 hata, 40 uyarı. Ayrıntılar yerel `qa-summary.json` dosyasındadır. AndroidX graphics-path kitaplığı önceden sembolleri çıkarılmış olduğu için Play native sembol uyarısı kalabilir; `FULL` ayarı mevcut, yeni sembol üretmez. R8 Kotlin metadata uyarıları araç zinciri bakımına kaydedildi. R8, bundletool, imza, gerçek paket izinleri / 16 KiB native hizalama ve geliştirme aracı yalıtımı birlikte kontrol edilir. Emülatör kanıtı fiziksel düşük donanım, pil / ısınma veya eski Android codec matrisi için garanti oluşturmaz.

## Sürüm notları

```text
<en-US>
New in this test build:
- Read PDFs, export pages as images and organize PDF pages.
- Read text and CSV/TSV tables; export supported formats.
- Extract audio and create WAV or M4A files.
- Export BMP images, HEVC MP4 and VP8 WebM on compatible devices.
- Continue media tasks in the background and restore results.
- Improved size checks, file handoff and messages in 22 languages.
Device support varies. Original files are preserved.
</en-US>
<tr-TR>
Bu test sürümünde:
- PDF okuma, sayfaları görsel olarak kaydetme ve PDF sayfalarını düzenleme.
- Metin ve CSV/TSV tablolarını okuma; desteklenen biçimlere aktarma.
- Ses çıkarma, WAV ve M4A oluşturma.
- Uyumlu cihazlarda BMP görsel, HEVC MP4 ve VP8 WebM çıktısı.
- Medya işlemlerini arka planda sürdürme ve sonuçları geri yükleme.
- Boyut kontrolleri, araçlar arası aktarım ve 22 dilde mesaj iyileştirmeleri.
Destek cihaza bağlıdır. Orijinal dosyalar korunur.
</tr-TR>
```
