# Birlevo — dosya odaklı çalışma alanı

8 Ekim 2026 geliştirme kodu. Sürüm numarası, yayın APK/AAB'si, kayıt biçimleri ve medya motorları değiştirilmedi. Bu çalışma debug hedefinde doğrulanır; görsel/slaytlardan zamanlamalı MP4 sonraki ürün adımıdır.

## Başlangıç ve gezinme

Ana sayfada kısa başlangıç, **Dosya aç**, **Yeni oluştur**, beş kategori ve **Devam et** bulunur. Yeni oluştur sunum / fotoğraf PDF'sini; Belgeler PDF okuyucu, PDF sayfa araçları ve metin/tablo okuyucusunu açar. Video düzenleme ve sıkıştırma aynı çalışma alanına gider. Dokuz büyük araç kartı ve tanıtımdaki tekrarlanan kısayollar kaldırıldı.

Genel seçici tek dosya alır. `FileActions` mevcut giriş kayıtlarıyla MIME ve dosya adını birlikte değerlendirir; bilinen türler çelişirse yönlendirme yapmaz. Genel MIME sağlayan dosyalarda ad ipucu kullanılabilir. Seçici veya uzantı gerçek içerik/codec doğrulamasının yerine geçmez; mevcut motorlar geçersiz veya cihazda desteklenmeyen içeriği reddeder.

| Dosya ailesi | Erişilen işlemler |
| --- | --- |
| Görsel | Dönüştür/sıkıştır, fotoğraf PDF'sine ekle, yeni/mevcut sunuma ekle |
| Video | Düzenle/dönüştür/sıkıştır, ses çıkar, kare çıkar |
| Ses | Kes/birleştir/dönüştür |
| PDF | Oku, sayfaları düzenle/birleştir, sayfayı görsele dönüştür |
| Metin/tablo | Oku, hedef biçime dönüştür, sunuma aktar |

`StudioRoute` enum'u ve kaydedilebilir route yığını eski bağımsız ekran bayraklarının yerine geçti. Ekran/panel seçimi Activity yeniden kurulunca korunur. Araç aktarımı hedef ekranı yığına ekler; geri dönüş önceki çalışmaya döner. Kaynak ve sonuçlar aktarım ardından sıfırlanmaz. Dosya değiştirme, kaldırma ve oturum temizleme açık eylemlerdir. Sunum kaydı başarısızsa taslak korunur ve yeniden deneme erişilebilir kalır.

Ana sayfa yalnız proje metaverisi ve üç medya oturumunun sınırlı atomik JSON metaverisini okur. Medya ViewModel'leri, özel kaynak restorasyonu, bitmap ve oynatıcılar ilgili araç ziyaret edildiğinde başlar. İlk üç devam kaydı gösterilir; tüm kayıtlar sabit kimlikli tembel listeden erişilir.

## Ortak çalışma alanı

`ToolWorkspace` panel, geri hareketi, yerleşim ve işlem alanını yönetir; domain ayarlarına veya dosya sahipliğine müdahale etmez. Telefonda kaydırılabilir alt panel, 840 dp ve üzerindeki normal yazı ölçeğinde 360 dp ayar sütunu kullanılır. Yazı ölçeği 1,3'ü aşınca tek sütuna dönülür. Panel kapanınca ayarlar korunur. Geri önce paneli, ardından ekranı kapatır.

Ana işlem ekranın altında kalır; telefonda panel açıkken aynı işlem panelin sabit altına taşınır. Dar/büyük yazılı ekranlarda işlem alanı sınırlı ve kaydırılabilirdir. Yeni sonuç hazır olduğunda panel kapanır; aynı sonucun yeniden bağlanması açık paneli gereksiz kapatmaz. `SourceHeader` dosya seçildikten sonra büyük içe aktarma kartının yerini alır.

| Araç | Ayarlar / korunmuş yetenekler |
| --- | --- |
| Video | Klipler; kadraj/sığdırma/hız; yazı; görsel/logo; kullanıcı sesi; çıktı. İki kaynakta ayrı kesme/sıra, üç video hedefi, kalite ve çözünürlük seçenekleri korunur. Kaynak oynatma gerçek çıktı oluşana kadar kaynak olarak etiketlenir. |
| Görsel | Tek kaynak/sonuç önizlemesi; JPEG/PNG/WebP/BMP, kalite ve tüm mevcut kenar sınırları ayrı çıktı panelindedir. |
| Ses | Kaynak/sonuç oynatma; iki kaynak, kesme/sıra, WAV/M4A/OGG/FLAC/MP3 ve profil seçenekleri ayrı panellerdedir. |
| PDF okuyucu | Sayfa/zoom önizlemesi, sayfa seçimi, görsel hedef/kalite/kenar ve byte-exact PDF kopyası. |
| PDF sayfaları | Seçili büyük sayfa; sabit kimlikli küçük liste; seçili sayfada taşı/döndür/kaldır, aralık seçimi/geri al ve sonuç önizlemesi. |
| Fotoğraf PDF'si | Seçili fotoğraf ve küçük tembel liste; seçili öğede taşı/kaldır; 2048/4096 çıktı sınırı ve üretilmiş PDF önizlemesi. |
| Metin/tablo | İçerik önde; kodlama/tür/ayırıcı ve hedef seçenekleri ayrı; tablo satırları tembel. |
| Sunum | Yalnız seçili slayt düzenleyicisi; küçük slayt listesi, büyük önizleme, tüm tema/düzen, çoğalt/sırala/sil, kalıcı görsel ekleme, PDF ve eski video taslağı erişimi. |

Uzun genel açıklamalar **Ayrıntılar ve yardım** altında. Kalite kaybı, şeffaflık/kırpma, cihaz desteği, iş ve kayıt hataları ilgili alanda görünür kalır. Gerçek kaynak/çıktı boyutları karşılaştırılır. Eşit/büyük medya sonucu normal sıkıştırma kaydı sunmaz: **Dönüştürülmüş kopyayı kaydet** sonuca özel onay ister. Aynı koruma Diğer işlemler içindeki açma/paylaşma/aktarımda sürer. Ayar değişince eski sonuç ViewModel tarafından geçersizleştirilir; otomatik kalite/çözünürlük azaltımı eklenmedi.

## Renk ve erişilebilirlik

Marka, araç simgeleri ve ana eylemler mercandır. Açık temada küçük düğme yazısı koyu mercan `#B73B35` / beyaz; koyu temada logo mercanı `#FC5B54` / lacivert `#08164D` kullanır. Metin/yüzey rolleri ayrı, ikincil eylemler daha sakindir. Küçük kontroller 8–12 dp, büyük yüzeyler 20–28 dp yuvarlatılır. Yeni metinler 22 dilde tamamlandı. Yön simgeleri yerel RTL düzenini izler.

JVM kontrast testi iki temada ana eylem, küçük gövde ve yardımcı metin rollerini 4,5:1 eşiğinde denetler. Material dokunma hedefleri ve özel kategori/panel düğmelerinin en az 48 dp alanı korunur; kalite sürgülerine yerelleştirilmiş erişilebilir ad eklendi. Ölçütler [Android erişilebilirlik rehberine](https://developer.android.com/guide/topics/ui/accessibility/apps) dayanır. Otomatik TalkBack odak/tıklama denemesi konuşmanın bütün dillerde manuel değerlendirilmesi değildir.

## Tekrar çalıştırma

```
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
./tools/testing/workspace-qa.ps1 -Suite navigation
./tools/testing/workspace-qa.ps1 -Suite media -SkipInstall
./tools/testing/workspace-qa.ps1 -Suite documents -SkipInstall
./tools/testing/workspace-qa.ps1 -Suite design -SkipInstall
./tools/testing/workspace-qa.ps1 -Suite recovery -SkipInstall
./tools/testing/workspace-qa.ps1 -Suite layouts -SkipInstall
./tools/testing/workspace-qa.ps1 -Suite accessibility -SkipInstall
./tools/testing/workspace-comparison.ps1 -BaselineApk <önceki-debug-apk>
python tools/localization/generate.py --check
python tools/licensing/audit_runtime.py --check
```

QA betiği yalnız `ofice_qa` / `ofice_qa_api35` emülatörünü kabul eder; yerleşim ve TalkBack ayarlarını sonunda eski hâline getirir. Loglar ve ekranlar `output/development/workspace/` altında Git dışında kalır. Karşılaştırma testi aynı fixture/görevi önceki Git HEAD ve yeni debug APK'da gerçek ekran üzerinden yürütür; dokunma ve ekran dışındaki hedef için gereken kaydırma isteğini ayrı sayar. Kullanıcı hızı, fiziksel parmak hareketi veya genel kullanılabilirlik skoru ölçülmez.

## Doğrulama kaydı

API 36 `ofice_qa` emülatöründe debug APK ve test APK kullanıldı. JVM: **86 test, 0 hata**. Debug lint: **0 hata / 62 uyarı**; bu geçiş bağımlılık güncellik/üçüncü taraf/eskimiş kaynak uyarılarının tamamını çözme iddiası taşımaz. 22 dilde anahtar/placeholder/üretilmiş kaynak denetimi ve 95 runtime artifact + native encoder lisans kapsamı kontrolü geçti.

| Cihaz kontrolü | Geçen testler | Kanıt |
| --- | --- | --- |
| Dosya seçimi, ilk medya eylemi, gezinme, panel/sonuç koruması | 11 | `navigation.log` |
| İki video klibi/kadraj, yazı/görsel/ses, kare aktarımı, ses birleştirme ve görsel kayıt korumaları | 12 | `media.log` |
| PDF okuyucu/sayfalar, fotoğraf PDF, metin/tablo kayıt/aktarım | 26 | `documents.log` |
| Sunum tema/düzen/çoğaltma, PDF, çıktı aktarımı ve sistem çubukları | 24 | `design.log`: 23 geçti; tembel görselin seçili editör üzerinden kontrolü düzeltildikten sonra kalan test `presentation-image.log` içinde geçti |
| Arka plan/sıra/iptal/süre sınırı ve atomik eski medya oturumları | 10 | `recovery.log`; profil değişiminden sonraki asenkron encoder denetimini bekleyen test düzeltildi |
| Yerel TalkBack odak/tıklama, 48 dp hedef ve gerçek görsel dönüşümü | 1 | `talkback.log`; Compose sanal erişilebilir ağaç doğrudan dolaşıldı, TalkBack servisi etkinliği denetlendi |
| Telefon, tablet, dar telefon %200 yazı, koyu, Arapça RTL, tablet %200 yazı | 6 yerleşim tekrarı | `phone.log`, `tablet.log`, `large-text.log`, `dark.log`, `rtl.log`, `tablet-large-text.log` |
| 22 uygulama diline gerçek geçiş/RTL yönü | 1 tekrar | `languages.log` |

86 farklı cihaz testi (karşılaştırmanın iki görevi dahil) başarıyla yürütüldü; yerleşim/dil ve eski APK karşılaştırma tekrarları bu özgün sayıya eklenmez. Bu, bütün mevcut codec motor testlerinin bu arayüz değişikliği için yeniden koşulduğu anlamına gelmez. Motor kaynakları değişmedi; arayüzün temsilî uçtan uca görevleri ve kritik korumalar yukarıdaki kapsamla doğrulandı.

### Aynı görevde dokunma/kaydırma karşılaştırması

Önceki APK, Git `215d46624f7ee45df890770e5caa5caef487d4ae` kodundan ayrı klasörde debug olarak derlendi. Aynı 1080×2400 telefon / normal yazı / İngilizce / fixture ile yeni APK karşılaştırıldı. Fotoğraf görevi varsayılan JPEG sonucunu üretmeye; sunum görevi bir slayt başlığı girip gerçek PDF önizlemesine kadar sayılır. Sistem dosya seçicisi fixture ile yanıtlanır, metin girişi dokunma sayısına eklenmez; kaydırma sayısı hedef ekranda olmadığı için gereken `performScrollTo` istekleridir. Kayıt/paylaşım onayları bu iki görev sayısına dahil değildir; ayrı koruma testleri vardır.

| Görev | Önceki dokunma / kaydırma | Yeni dokunma / kaydırma |
| --- | --- | --- |
| Varsayılan görsel dönüştürme | 3 / 1 | 3 / 0 |
| Tek slayt başlığı → PDF önizleme | 2 / 0 | 5 / 0 |

Sunumda Yeni oluştur menüsü ve düzenleme panelini açıp kapatma ek dokunuş getirir. Sadeleştirme bütün görevlerde daha az dokunma iddiası değildir; aynı anda gösterilen seçenek ve düzenleyici sayısını azaltır. Çok slaytlı kullanımın insanlarla kullanılabilirlik ölçümü ayrıca gerekir. Ham ölçümler `comparison-*/comparison-{baseline,current}-{image,presentation}.json` içinde tutulur; karşılaştırma betiği sonunda güncel APK'yı geri yükler.

Graphify yalnız yerel AST ile güncellendi. Kotlin parser'ının bazı mevcut dosyalarda kısmi çıkarımı vardır; grafik ilişkileri başarılı Kotlin derlemesi ve kaynak/testlerle doğrulandı. Grafik uygulamaya/Git'e eklenmedi. Arayüz kaynaklarının tutarlı biçimlenmesi için Maven SHA256 doğrulamalı ktfmt 0.59 yalnız geliştirme çıktısı altında kullanıldı; Gradle/runtime bağımlılığı eklenmedi.

## Performans ve yayın sınırı

Önizleme ile nihai dışa aktarma çözünürlüğü ayrıdır; UI'a teslim edilmiş bitmap zorla geri dönüştürülmez. Seçili sayfa/slayt önizlemesi ve kimliği korunan tembel küçük listeler kullanılır. Aynı anda yalnız etkin oynatma açılır. Medya motorları, oturum şemaları, atomik tek yazıcı, eski FileProvider kökü ve kaynak erişim korumaları değişmedi. Yeni runtime SDK/codec/font yoktur; 95 mevcut runtime artifact ve native encoder lisans kapsamı denetimi değişmedi.

Fiziksel düşük RAM/ARM, uzun ve yüksek çözünürlüklü medya, gerçek dolu disk, pil/termal/FPS ölçümü ayrıca gerekir. Debug emülatör sonucu bu cihazlar veya küçültülmüş release için garanti değildir. Kullanıcı yeni sürüm istediğinde R8/native sembol/LGPL/ABI/16 KiB yayın kontrolleri tekrar yapılır.
