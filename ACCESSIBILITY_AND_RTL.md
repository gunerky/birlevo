# Erişilebilirlik ve RTL — 13 Ekim 2026

Ortak çalışma alanında başlıklar erişilebilir başlık olarak tanımlandı. Açık ayar bölümü ile seçili sunum slaytı, PDF/fotoğraf sayfası ve slayt-video öğesi yalnız renk üzerinden belirtilmez; erişilebilirlik seçimi de aynı kimlikle güncellenir. Alt panel başlığı **araç adı · bölüm adı** biçimindedir. Farklı araçlarda aynı “Çıktı” adının kullanılması panelin bağlamını kaybettirmez.

Video kesme başlangıç/bitiş ve kare zamanı kaydırıcıları, görsel/PDF sayfası/slayt görseli kalite kaydırıcıları açıklayıcı adlarını ve gerçek değerlerini bildirir. Örneğin 40–95 aralığındaki kalite değeri 72, bu aralığın yüzdesi şeklinde sunulmaz. Android `ACTION_SET_PROGRESS` eylemi gerçek ViewModel ayarını değiştirir; görsel bir taklit değildir. Slayt görseli kalitesi diğer görsel araçları gibi en yakın tam sayıya yuvarlanır; kayan nokta değeri yüzünden 72 isteği 71'e kesilmez. Görsel kontrolde bulunan bozuk kalite ayracı da düzeltildi. Motorlar, kalite/çözünürlük seçenekleri ve çıktılar korunur.

Bu metadata yaklaşımı [Android'in Compose erişilebilirlik anlambilimi rehberine](https://developer.android.com/develop/ui/compose/accessibility/semantics) dayanır. Android buton ve sekme seçimlerini farklı yerel alanlarla bildirebilir; testler yalnız `AccessibilityNodeInfo.isSelected` varsayımına dayanmaz. Aynı kontrolün yerel seçili/işaretli/durum açıklaması öncesi-sonrası ve Compose seçimi birlikte doğrulanır. Panel adı metadata olarak, başlık ise gerçek Android erişilebilirlik düğümü ve odak eylemiyle kontrol edilir. Tek bir pencere olayının her Android sürümünde aynı biçimde yayımlandığı iddia edilmez.

## PDF önizleme hata düzeltmesi

PDF sayfa önizleme hatası sonrasında `busy=false` geçişinin otomatik önizlemeyi yeniden başlatması engellendi. Hata görünür ve kontroller kullanılabilir kalır. Yalnız başarısız önizlemeye ait **Tekrar dene** eylemi gösterilir; kaydetme veya içe aktarma hatası yanlışlıkla önizleme hatası sayılmaz. Başka sayfaya açık kullanıcı seçimi önizleme isteğini başlatabilir.

Yeni sayfa önizlemesi başladığında eski bitmap referansı durumdan kaldırılır, fakat UI'a teslim edilmiş bitmap zorla geri dönüştürülmez. Gösterilen sayfanın kimliği önizleme kimliğiyle eşleşmelidir. Önceki sayfanın görseli yanlış sayfa adı altında gösterilmez. PDF oluşturma, vektör/metin koruma ve dışa aktarma özellikleri değiştirilmez.

## Doğrulama kapsamı

Android kontrolleri kullanıcı isteğiyle yalnız ayrılmış `ofice_qa` emülatöründe, debug APK'da yapılır. Telefon, yüzde 200 yazı, tablet ve koyu tema düzenlerinde Arapça/Farsça testleri; TalkBack servisi etkin İngilizce/Arapça/Farsça yerel eylem testleri vardır. Kaynaklar sentetik QA dosyalarıdır. Gerçek kullanıcı dosyası veya kaynak kod dış servise gönderilmez; Jev çağrısı yoktur.

`AccessibilityRtlDeviceTest` karma Latin/Arapça dosya adı ve proje menüsü, slayt başlık/gövde/not/proje adı düzenleme ve yeniden oluşturma, seçili öğe/panel başlığı/kalite durumu, Arapça rakamlarla PDF sayfa sıralama, başarısız önizleme ve açık tekrar deneme, fotoğraf/slayt-video seçimi, video kesme/kare zamanı, ses/PDF okuyucu/RTL metin ayarlarını kapsar. Ekran görüntüleri yerel QA klasörüne kaydedilir.

TalkBack koşuları aktif servisin varlığını kontrol eder. Gerçek Android erişilebilirlik düğümleriyle odak, tıklama ve değer değiştirme; dosya açıp görsel dönüştürme, proje silme onayını iptal etme, slayt/panel/kalite ve video zaman/kare akışları yürütülür. Bu, bütün ekranlarda insanla jest/konuşma dinleme veya her TTS dilinin telaffuz kontrolü yapıldığı anlamına gelmez.

Koşucular: `tools/testing/accessibility-rtl-qa.ps1` ve `tools/testing/accessibility-rtl-qa.ps1 -SkipInstall -TalkBack`. İş bitince erişilebilirlik servisleri ve yazı ölçeği önceki değerlerine döner; QA ekran boyutu/yoğunluğu ve gece modu sıfırlanır. Günlükler, ekran görüntüleri ve tanı koşuları `output/development/accessibility-rtl/` altındadır. LocaleManager'ın ilk Activity'yi geç yeniden oluşturması test başlangıcında beklenir; başarısız koşular başarılı sonuçlarla karıştırılmaz.

Yeni bağımlılık, sürüm numarası veya imzalı yayın paketi yoktur. R8/release doğrulaması sonraki kullanıcı derleme isteğinde yapılır; 0.1.11 (12) yayın dosyaları değişmez. Fiziksel cihaz performansı, gerçek Office testi ve bütün çevirilerin insan değerlendirmesi bu emülatör kanıtından türetilmez.

## Tamamlanan kontroller

13 Ekim debug doğrulaması, `ofice_qa` Android API 36 emülatöründe:

| Kontrol | Başarılı sonuç |
|---|---:|
| Telefon / %200 yazı / tablet / koyu tema × Arapça / Farsça, sekiz senaryo | 64 |
| TalkBack etkin İngilizce / Arapça / Farsça, dört senaryo | 12 |
| Mevcut PDF sayfa regresyonları | 6 |
| Son kalite ayracı düzeltmesinden sonra %200 yazıda Arapça / Farsça yerel kalite eylemi | 2 |
| JVM birim testleri | 106 |

Emülatörde toplam 84 başarılı test yürütmesi vardır; aynı senaryoların farklı düzen/dillerde koşması ayrı yürütme sayılır. Son kozmetik ayraç düzeltmesi sonrasında bütün matrisi tekrar koşmak yerine ilgili iki kalite akışı ve altı PDF regresyonu güncel APK ile doğrulandı. Debug APK/test APK derlemesi ve `lintDebug` başarılı: 0 hata, 63 uyarı, 2 ipucu. 22 dilde anahtar/format parametresi/kaynak eşleşmesi ve 95 incelenmiş runtime artifact ile ayrı native encoder lisans kapsamı geçti. Yeni bağımlılık yoktur.

Yerel grafik `graphify update . --no-cluster` ile güncellendi. Kotlin AST ayrıştırıcısının 17 dosyadaki kısmi ayrıştırma uyarıları bilinen grafik sınırıdır; kaynak kod, derleyici ve testler doğrulama için esas alındı. Büyük yazı, RTL, tablet ve koyu tema örnek ekranları görsel olarak da incelendi. TalkBack jest/telaffuz ve bütün kontrollerin insan değerlendirmesi kapsam dışında kalır.

Son debug APK SHA-256: `2a497a8fe84b0d48c89445a507c3aa530b3625b7f76a3ff396f3c0393aa0ed95`. Mevcut 0.1.11 (12) AAB SHA-256 hâlâ `a9917105ed2bbe1564cf40da92280ed26f61571d56f43142d6f02aae896876d5`.
