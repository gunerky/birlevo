# Birlevo — Git ve GitHub

## 15 Ekim 2026 CI incelemesi

PR #7'nin `4899df5` push kontrolü uygulama derlenmeden önce NDK arşivi indirilirken `Error on ZipFile unknown archive` hatasıyla durdu. CI artık Gradle'daki sabit NDK / CMake sürümlerini önce resmî SDK yöneticisiyle hazırlar; kurulu sürüm ve çalıştırılabilir dosyayı doğrular. Eksik kurulum en fazla üç kez, her çağrı en fazla beş dakika olacak şekilde denenir. Başarısız hazırlık derlemeyi durdurur; testler veya lisans denetimi atlanmaz. Hazırlama aracının çevrimdışı testleri de CI'a dahildir.

`main` birleştirmesinde PR'ın güncel commit'i ve tamamlanmış CI sonuçları doğrulanır. 15 Ekim tarihindeki GitHub API sorguları, gizli depoda dal koruması / ruleset için plan yükseltmesi gerektiğini belirten HTTP 403 döndürdü. Depo gizliliği değiştirilmez; sunucu tarafında zorunlu PR ve kontrol kuralı etkinleştirilmiş sayılmaz. Hesap uygun olduğunda `debug` kontrolü, güncel taban, PR üzerinden birleştirme ve force-push / silme yasağı uygulanmalıdır. Tek geliştiricinin kendi PR'ını onaylaması istenmez.

Bağımlılık PR'ları bu özellik birleştirmesinden ayrıdır: Media3 güncellemeleri birlikte API geçişi gerektirir; Exif güncellemesi gerçek çalışma zamanı lisans / hash envanteri incelenmeden alınmaz.

## İlk kurulum kaydı

6 Ekim 2026. Özel depo: [gunerky/birlevo](https://github.com/gunerky/birlevo). İlk çalışan geliştirme kaydı `a38927b` ile `main` dalına gönderildi. Android sürüm numarası Git kaydından bağımsızdır; commit oluşturmak release APK / AAB üretmek değildir.

Yeni işler `codex/` dallarında kaydedilir. Bu ilk özellik `codex/background-processing` dalındadır; değişiklikler test sonuçlarıyla pull request üzerinden incelenebilir. Birleştirme öncesi değişiklik ve kontroller gözden geçirilir. Bu depo ilk oluşturuldu; önceki sürümlerin geçmiş commit'leri sonradan varmış gibi üretilmedi.

## Depoya alınanlar / dışarıda kalanlar

Kaynak, testler ve yapay test dosyaları, Gradle wrapper, çeviri kataloğu / üretilen kaynakları, geliştirme araçları, tasarım / ürün / test belgeleri ve web kaynakları saklanır. `.gitignore` build / output / Graphify grafiği / yerel IDE / SDK ayarlarını, API `.env` dosyalarını, şifreli `.clixml` kayıtlarını ve imza anahtarlarını dışarıda tutar. İmzalama anahtarının yedeği ve yayın paketleri ayrı güvenli saklama konusudur; GitHub'a eklenmedi.

`tools/repository/check_repository.py` Git indexindeki dosya yollarını ve sık kullanılan anahtar / token imzalarını denetler, değerleri göstermez. Commit öncesi ve CI'da çalışır. Bu dar denetim bütün olası sırların veya kod açıklarının bulunacağı garantisi değildir. Depo özel tutulur; kullanıcı izni olmadan herkese açık yapılmaz. Geliştirme anahtarı Android / Gradle / APK içine eklenmez.

## Otomatik kontroller

GitHub Actions yalnız debug uygulama / test APK'sı, birim testleri, lint, çeviri kaynak kontrolü, depo hijyeni, Jev çevrimdışı testleri ve APK izolasyonunu çalıştırır. İş akışı izinleri `contents: read` ile sınırlıdır; checkout / Java hazırlama adımları doğrulanmış commit SHA'larına sabitlenmiştir. İmza anahtarı, TypeSafe API anahtarı veya canlı Jev çağrısı gerekmez. Release, Play yayını ve web dağıtımı otomatik tetiklenmez. Emülatör / fiziksel cihaz testleri yerel QA'da yürür; CI JVM testleri bunların yerine sayılmaz.

Dependabot Gradle bağımlılıklarını haftalık, Actions adımlarını aylık kontrol eder; otomatik birleştirme açılmadı. Düşük donanım, minSdk, lisans, codec ve mevcut kalite seçenekleri üzerindeki etkisi incelenmeden bağımlılık güncellemesi alınmaz. Branch koruma / zorunlu onay kuralı bu ilk kurulumda ayrıca etkinleştirilmedi; hesap / depo planına uygun kural daha sonra seçilebilir.

Rutin sıra: değiştir → uygun yerel testler → `git add` → depo denetimi → commit → özellik dalını gönder → pull request / CI sonucunu incele. Yeni Android yayın paketi yalnız kullanıcı istediğinde hazırlanır.
