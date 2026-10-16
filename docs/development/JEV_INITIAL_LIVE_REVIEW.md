# Jev — ilk canlı geliştirme değerlendirmesi

5 Ekim 2026. Proje dışındaki Windows şifreli anahtar çalıştı. Sabit `jev-1.13.0` ile yalnız seçilmiş arayüz / çeviri metinleri ve yapay geri bildirim / test senaryoları gönderildi. Uygulama veya kullanıcı medyası gönderilmedi. Android kodu, kaynak metinler ve çeviriler değiştirilmedi; yeni APK/AAB derlenmedi veya yayımlanmadı.

## Ölçülen ilk sonuç

| Tur | İncelenen öğe | API denemesi | Önbellek | Girdi token |
|---|---:|---:|---:|---:|
| Cevapları bilinen yapay örnekler | 14 | 14 | 0 | 10.829 |
| İngilizce arayüz metni | 25 | 25 | 0 | 21.342 |
| Beş hedef dilde seçilmiş çeviri | 25 | 25 | 0 | 19.150 |
| Yapay geri bildirim | 3 | 2 | 1 | 1.636 |
| Yapay test senaryosu | 3 | 3 | 0 | 2.009 |
| Aynı örnekleri yeniden okuma | 14 | 0 | 14 | 0 |
| Toplam; tekrar okuma dahil | 84 | 69 | 15 | 54.966 |

Başlangıç değerlendirmesi 70 öğedir; 14 öğe önbelleği doğrulamak için yeniden okunmuştur. 5.005 çıktı tokenı ve 158.479 gönderim baytı kaydedildi. Çağrı hatası / zaman aşımı / tekrar yoktur. Raporlanan API süreleri örneklerin tek çağrısına aittir; bağlantı kurma ve tur hazırlığıyla birlikte uygulama performansı ölçümü değildir. Sağlayıcı faturası ayrıca sorgulanmadı; kesin dolar maliyeti iddia edilmez.

14 örneğin 13'ü tüm beklenen Choice kararlarıyla eşleşti: Türkçe anlam koruma / bozulma 4/4, İngilizce arayüz vaatleri 2/2, test riskleri 2/2, geri bildirim konu+öncelik 5/6. Geri bildirim **konusu** 6/6 doğru seçildi. Ayrışan örnek Arapça Kaydet düğmesi çevirisinin yanlışlığıydı: konu `localization`, öncelik `low`; elle tanımlanan beklenen öncelik `normal` idi. Bu 14 örnek genel doğruluk yüzdesi, tüm dillerde başarı veya insandan daha iyi performans kanıtı değildir.

## İnsan incelemesi

- 25 çeviri, İngilizce kaynakla karşılaştırılarak Jev tarafından `preserved` seçildi. Türkçe, Almanca, Fransızca, Japonca ve Arapçada yalnız beş uyarı metni incelendi. Modelin bu kararı bağımsız dil uzmanı doğrulaması değildir. 17 öğe açıklık puanının belirsizliği nedeniyle inceleme kuyruğuna girdi; bunlar 17 hatalı çeviri olarak sayılmaz.
- 25 İngilizce metnin hiçbirinde `unsupported` vaat kararı verilmedi. 15 öğe çoğunlukla düşük confidence nedeniyle incelemeye işaretlendi; bu sayı doğrulanmış hata sayısı değildir.
- `output_format` ve `home_title` kısa, tek başına verilmiş etiketlerdi. Kaynakta `output_format` JPEG/PNG/WebP seçim çiplerinin üstündedir; `home_title` ana sayfa tasarımının parçasıdır. Bu bağlam olmadan çıkan düşük güvenli belirsizlik, bu etiketlerin yanlış olduğu anlamına gelmez.
- `max_edge`: “Maximum long edge” hem görsel hem video / fotoğraf-PDF araçlarında teknik bir etikettir. Kullanıcı denemesi için “Longest side (pixels)” gibi bir sadeleştirme adayı hazırlamak makuldür. Mevcut kaynakta çipler piksel birimini zaten gösterir. Otomatik metin değişikliği yapılmadı.
- JPEG / video kayıp açıklamaları ve fotoğraf-PDF sınır metni teknik ve yoğun. Gelecek arayüz turunda kısa görev açıklamasıyla ayrıntılı kayıp / sınır açıklamasını ayrı sunmak değerlendirilebilir; kullanıcıya gerekli özellik / kalite kaybı bilgisi korunmalıdır.
- Yapay “klavye Kaydet düğmesini gizliyor” bildirimi `interface`, öncelik `low` seçildi (confidence 0,70). Kaydetmeye erişimi engelleyebileceği için insan incelemesinde işlevsel sorun olarak ele alınmalıdır. Jev önceliği otomatik görev önceliğine dönüşmez.
- Yapay taslak yeniden açılış testi `functional` seçildi. Düzenlemelerin kaybı söz konusu olduğunda projenin veri koruma kuralları önceliklidir; proje kaydı ve süreç geri yükleme testleri kritik kontrol olarak tutulur. Daha düşük AI önerisiyle test veya yetenek azaltılmaz.

## Kullanım kararı

İlk sonuçlar kapalı seçeneklerle **konu sınıflandırmasının** yararlı bir aday olduğunu gösteriyor. Çeviri anlam karşılaştırması da yardımcı bir ikinci göz olarak kullanılabilir. Açıklık puanı ve görev / test önceliği henüz otomatik karar için yeterince doğrulanmış değildir. Jev raporları inceleme sırasını destekler; kod / test / çeviri değiştirme veya yayın kararı vermez.

Sonraki geliştirme işlerinde yalnız seçilmiş görevler için kullanılacak:

1. Testçi bildirimlerini elle anonimleştirip konu sınıfı ve geliştirici önceliğiyle karşılaştırmak.
2. Yeni veya değişmiş arayüz metinlerine ekran bağlamını ekleyerek değerlendirmek; her seferinde bütün kataloğu göndermemek.
3. Yeni PDF belge işlemlerinin seçilmiş test senaryolarında kritik veri / çıktı risklerini kontrol listesiyle birlikte değerlendirmek. Mevcut deterministik koruma kuralları ve gerçek testler korunur.
4. Çeviri denemelerini küçük dil grupları ve o dile ait bilinen iyi/kötü örneklerle genişletmek.

Yeniden aynı veri değerlendirmesinde önbellek çalıştı: 14 sonuç, sıfır API çağrısı. Uygulama kaynağı hash'i bütün turlarda aynı kaldı. Mevcut 0.1.7 APK / AAB geliştirme ayrımı kontrolleri geçmiştir. Ham yerel raporlar `output/development/jev/20261005-first-live/` altında ve Git dışında tutulur; anahtar hiçbir rapora yazılmadı.
