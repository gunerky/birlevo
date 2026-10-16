# PPTX sürüm öncesi kontrolü

10 Ekim 2026. Test edilen uygulama kodu:
`62b5b88d2713f302cfd74a51996995c28dc927d4`.
Bu adımda yeni APK/AAB yayın paketi veya sürüm numarası hazırlanmadı.

## Tamamlanan doğrulamalar

| Kontrol | Sonuç ve kapsam |
| --- | --- |
| GitHub push kontrolü | [37913323607](https://github.com/gunerky/birlevo/actions/runs/37913323607) başarılı. Yukarıdaki kaynak commit'i. |
| GitHub PR kontrolü | [37913327641](https://github.com/gunerky/birlevo/actions/runs/37913327641) başarılı. Aynı kaynak commit'i. |
| Yeni yerel PPTX cihaz çalıştırması | Ayrılmış `ofice_qa` emülatöründe **6 test** başarılı. Oluşturma, not tercihi, iptal, eski sonuç, hash/kayıt ve açma/paylaşma staging, Activity kapanırken devam etme. |
| Yeni bağımsız PPTX okuma | Bu cihaz çalıştırmasının altı dosyası Artifact Tool 2.8.89 ile yeniden açıldı. **24 slayt** çizildi; boyut/geometri, not dışlama/dahil etme ve referansla raster farkı denetlendi. Gerçek Office testi değildir. |
| Host envanter regresyonu | **12 test** başarılı. |
| Ticari runtime kapsamı | **95 artifact + native encoder** için incelenmiş koordinat/hash kapsamı başarılı. Yeni runtime paket/font/Office SDK'sı eklenmedi. |

GitHub işi debug uygulama/test APK derlemesi, JVM testleri, lint, dil/repository denetimi,
runtime envanteri ve lisans/hash denetimi, debug MP3 artifact denetimi ve Jev izolasyonunu
çalıştırdı. Aynı kaynak için 9 Ekim'deki 106 JVM testi, 26 cihaz regresyonu ve beş UI
düzeni raporu geçerlidir; bu adımda bunların her biri yeniden yerelde çalıştırılmış sayılmaz.

Yeni yerel kanıtlar Git dışında `output/development/pptx-export/feature.log`,
`native/`, `rendered/` ve `verification.json` altındadır.

## Gerçek Office kontrolü bekliyor

PowerPoint ve LibreOffice standart kurulum yollarında bulunamadı. Bağlı Codex belge
oturumu yoktu. Dolayısıyla PowerPoint/Google Slides/LibreOffice'te açma, dosya onarım
uyarısı ve slayt gösterisi görünümü için **başarı kaydı yoktur**.

Hazır yerel paket: `output/development/pptx-office-review-2026-10-10/`.

- `index.html`: Türkçe kontrol adımları, altı PPTX bağlantısı, 24 kaynak slayt PNG'si
  ve tam beklenen konuşmacı notları. Yalnız sentetik QA metin/görselleri vardır.
- `decks/`: Doğrulanmış PPTX'lerin byte-eşit kopyaları.
- `manifest.json`: Test edilen uygulama kaynak commit'i, dosya/görsel SHA-256
  kimlikleri, seçenekler ve **pending** Office durumu.
- `review-results-template.json`: Uygulama/sürüm/platform, onarım uyarısı olmadan
  açma, slayt sırası/görünümü, notlar ve slayt gösterisi için boş insan kontrolü.
  Denetlenmeyen alanlar `null`; her dosya kaydı özgün hash'e bağlıdır.

Paket `tools/pptx/prepare_office_review.py` ile çevrimdışı oluşturulur. Araç bütün
kaynak hash'lerini mevcut başarılı doğrulama raporuyla karşılaştırır. Yeni, ayrı
bir klasör ister ve önceki insan değerlendirmesini üzerine yazarak silmez.
HTML açılması veya paket hazırlanması Office uyumluluğu testi sayılmaz.

PowerPoint'te her dosya açılıp dört slayt hem düzenleyicide hem slayt gösterisinde
referanslarla karşılaştırılmalı. Notlar hariç dosyada not alanları boş, dahil
dosyada beklenen metinlerle aynı olmalı. Korumalı Görünüm bildirimi ayrı kaydedilir;
dosya onarım uyarısı ile karıştırılmaz. Sonuç şablonu her uygulama/sürüm/platform
için ayrı bir adla doldurulur; özgün örnek dosyalar üzerine kaydedilmez.

## Yayın derlemesi sırasında yapılacaklar

Kullanıcı yeni sürüm istediğinde imzalı, R8/kaynak küçültülmüş APK/AAB üretilir.
Yeni PPTX yolunun release smoke kapsamı, native kaynak/lisans kopyası ve kütüphane
değiştirme yolu, dört ABI ve 16 KiB hizalama, APK/AAB native sembolleri ve İngilizce/
Türkçe sürüm notları tamamlanır. Bunlar debug başarısıyla yapılmış sayılmaz.

Fiziksel ARM/düşük RAM, pil/termal, gerçek dolu disk/provider, eski OEM/API, yeni
PPTX yolunun OS süreç öldürme matrisi ve manuel TalkBack ölçümleri halen açıktır.
Bu kontrol uygulamaya düzenlenebilir PPTX veya genel Office içe aktarma yeteneği eklemez.
