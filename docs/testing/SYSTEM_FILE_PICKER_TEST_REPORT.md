# Sistem dosya seçicisiyle uçtan uca kontrol — 5 Ekim 2026

Android 16 `ofice_qa` telefon emülatöründe debug APK ile Android'in gerçek **Dosyalar** seçicisi ve **Kaydet** ekranları kullanıldı. Bu oturumda seçim sonucunu taklit eden test sağlayıcısı kullanılmadı. Fiziksel telefon bağlı değildi; aşağıdaki sonuçlar gerçek cihaz doğrulamasının yerine geçmez.

| Akış | Doğrulanan sonuç |
| --- | --- |
| Görselli sunum | 1200×800 JPEG seçildi, slaytta gösterildi, proje kaydedildi. Uygulama zorla kapatılıp açılınca görsel erişimi ve küçük önizleme korundu. PDF önizlendi; `Presentation.pdf` sistem ekranından kaydedildi. 1 sayfalı 960×540 pt PDF açıldı ve renkli görselin sayfaya işlendiği görsel olarak doğrulandı. |
| Görsel sıkıştırma | Aynı JPEG gerçek seçiciden açıldı. 16.961 bayt kaynak, 8.496 bayt JPEG çıktıya dönüştü; çözünürlük 1200×800 kaldı. Çıktı `picker-photo-compressed.jpg` olarak kaydedildi ve JPEG olarak tanındı. |
| Video sıkıştırma | 4.007.503 bayt, 1280×720, yaklaşık 6 saniyelik sesli MP4 seçildi. Küçük dosya / 640 px ayarıyla 587.609 bayt, 640×360, 6,01 saniyelik çıktı kaydedildi. Dosyada H.264 görüntü ve AAC ses parçası doğrulandı. |

Kaynak JPEG ve MP4'ün SHA-256 değerleri işlem öncesi ve sonrası aynı kaldı. PDF 11.654 bayt. Sistem seçicisinin verdiği sunum görseli URI'si projede saklandı ve süreç yeniden başladıktan sonra okunabildi. Bu sonuç, seçilen dosya sağlayıcısı ve bu emülatör ortamı için geçerlidir; farklı bulut sağlayıcıları ayrıca denenmelidir.

**Kalan fiziksel cihaz kontrolü:** En az bir Android telefonda ve bir tablette üç görevi gerçek kişisel dosyalarla tekrarla. Uygulamayı kapatıp açtıktan sonra sunum görselinin erişimini, kaynak dosya silinince gösterilen hatayı, video sesini ve uzun işlem sırasında davranışı incele. Test için kişisel projelerin bulunduğu cihaza otomatik cihaz testlerini kurma; test paketi uygulama verilerini sıfırlar.

İncelenen yerel örnekler Git dışındaki `app/build/qa/` klasöründedir; derleme temizlenirse yeniden üretilmeleri gerekir.
