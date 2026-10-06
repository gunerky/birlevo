# Slayta görsel ekleme — 4 Ekim 2026

Sunum taslağında her slayta bir JPEG veya PNG görsel seçme, küçük önizleme gösterme ve görseli kaldırma eklendi. Proje kaydı görselin dosya seçicisi referansını tutar. PDF üretimi EXIF yön bilgisini uygulayıp görselin oranını koruyarak metnin yanına yerleştirir; yalnızca görsel içeren slayt da kabul edilir. Görsel için 100 MP ve sağlayıcı boyut bildiriyorsa 128 MiB sınırı uygulanır. PDF üretiminde görsel küçültülerek çözülür.

| Doğrulama | Sonuç |
| --- | --- |
| Android 16 telefon emülatörü, tüm cihaz testleri | 28/28 geçti |
| Görselli slayt, tablet ölçüsü 1920×1200 / 240 dpi | Geçti |
| Görselli slayt, 320 dp ve 2.0 yazı ölçeği | Geçti |
| Yerel birim testleri, APK derlemesi ve lint | Geçti |
| Dil kaynakları | 22 dil, 79 anahtar tam |

Yeni cihaz testi, EXIF yönü döndürülmüş bir JPEG referansının düzenleyicide küçük önizlemesini ve PDF sayfasındaki yönü düzeltilmiş renkli görüntü piksellerini doğruluyor. Proje kaydedilip yeniden açıldığında küçük önizleme geri geliyor. Görsel kaldırılınca görüntü kayboluyor ve yalnızca görsel içeren slayt için boş sunum uyarısı geri geliyor. Proje kodlama testi görsel referansını saklamayı ve eski sürümün görselsiz slaytlarını açmayı doğruluyor.

5 Ekim'de Android 16 emülatöründe gerçek sistem dosya seçicisiyle JPEG eklendi, proje kaydedildi, uygulama zorla kapatılıp yeniden açıldı ve görsel korundu. PDF dışa aktarılıp sayfadaki görsel ayrıca incelendi. Ayrıntılar [GERCEK_DOSYA_SECICI_TEST_RAPORU.md](GERCEK_DOSYA_SECICI_TEST_RAPORU.md) içindedir. Fiziksel cihazda aynı akış ve kaynak dosya kaldırıldığında anlaşılır hata gösterme senaryosu kapalı test öncesi yapılmalı. Proje görseli cihazda ayrı bir kopya olarak saklanmaz; kaynak dosya taşınır veya silinirse erişim kaybolabilir. Gelişmiş kırpma, serbest konumlandırma ve çoklu görsel bu sürümde yoktur.
