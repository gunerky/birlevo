# Yerel kod grafiği

Bu projede Graphify, Android uygulamasının parçası değil, geliştirme sırasında kullanılan yerel bir kod ilişkileri aracıdır. Üretilen `graphify-out/` Git dışında tutulur.

- Birden fazla dosyayı ilgilendiren mimari veya etki sorularında, `graphify-out/graph.json` varsa önce `graphify query "<soru>"` ile ilgili yolları daralt. Bir ilişkiyi izlemek için `graphify path`, tek bir bileşen için `graphify explain` kullan.
- Grafik sonuçlarını kaynak kod ve testlerle doğrula; dinamik Android bağları grafikte eksik olabilir.
- Kod değişikliğinden sonra `graphify update . --no-cluster` çalıştır. Grafik yoksa `graphify extract . --code-only --no-cluster` ile yerel AST grafiğini oluştur. Bu akış API anahtarı veya kaynak kodu dış servise göndermeyi gerektirmez.
- Basit, tek dosyalı değişiklikler için grafik sorgusu zorunlu değildir.

# Performans ve yetenek koruma

- Her geliştirme adımından sonra sürüm üretilmez. Sürüm numarası artırma, imzalı release APK / AAB derleme ve yayın paketi hazırlama yalnız kullanıcının isteğiyle yapılır. Geliştirme doğrulaması için debug derleme / emülatör testleri yapılır; yayın doğrulaması sonraki kullanıcı isteğinde tamamlanır.

- Optimizasyon sırasında desteklenen işlemler, kalite seçenekleri veya nihai çıktı çözünürlükleri sessizce azaltılmaz. Önizleme kaynak kullanımı ile dışa aktarma ayrı ele alınır.
- Sıkıştırma başarısı gerçek kaynak / çıktı dosya boyutuyla değerlendirilir. Eşit veya daha büyük dosya normal sıkıştırma kaydıyla dışa aktarılmaz; düzenleme / dönüşüm kopyası için o sonuca özel açık kullanıcı onayı gerekir. Bu kural yeni belge sıkıştırma motorlarına da uygulanır; yeni PDF / belge oluşturmak tek başına sıkıştırma sayılmaz. Ön tahmin kesin boyut garantisi değildir, kalite otomatik düşürülmez.
- Büyük listelerde kimliği korunan tembel öğeler kullanılır. UI'a teslim edilen bitmap, çizim katmanı hâlâ kullanabileceği için zorla geri dönüştürülmez; iptal edilen ve teslim edilmeyen bitmap temizlenir.
- Proje kaydı tek yazıcı ve atomik dosya üzerinden yürür. Durum kaydı / süreç yeniden oluşturma ve eski taslak uyumluluğu korunur.
- Yeni slayt görselleri projeye ait özel dosyalara kopyalanır; çalışma dosyası veya paylaşım önbelleği proje varlığı olarak kaydedilmez. Aktarım ek kodlama yapmaz. Referans alan taslak / çoğaltılmış slaytlar doğrulanmadan proje görselleri otomatik silinmez. Açma / paylaşmada da sonuca özel büyük kopya onayı korunur.
- Projede kaydedilen FileProvider URI yetkisi ve kök adı sürümler arasında korunur; değişiklik gerekiyorsa eski referanslar için veri geçişi hazırlanır.
- Release, R8 / kaynak küçültmesiyle doğrulanır. Normal Compose testleri debug hedefini kullanır. İmzalı küçültülmüş APK için `tools/release/verify-release-smoke.ps1 -Version <sürüm>` platform tabanlı bağımsız test koşucusunu hazırlar; yalnızca QA emülatöründeki uygulama verilerini sıfırlar.
- Güncel değerlendirme ve ölçülmemiş düşük donanım yolları `MODULERLIK_VE_PERFORMANS.md` içinde tutulur. Emülatör sonucundan fiziksel cihaz için FPS / pil / bellek garantisi türetilmez.

# Jev ile geliştirme değerlendirmesi

- Jev / TypeSafe yalnız `tools/jev/` altında bağımsız, elle çalıştırılan geliştirme aracıdır. Android SDK / Gradle / kaynak / APK / AAB içine bağlantı veya anahtar eklenmez; uygulama Jev'e bağlı değildir.
- Anahtar süreçteki `TYPESAFE_API_KEY` veya proje dışındaki Windows şifreli kaydından okunur. Anahtar sohbette, argümanda, Git'te, logda veya raporda gösterilmez. API sadece resmî TypeSafe adresine, açık `--live` ile ve tur sınırları içinde çağrılır; CI / derleme / hook üzerinden otomatik çağrı yoktur.
- Çeviri ve arayüz metni, seçilmiş anonim geri bildirim, seçilmiş test senaryosu ve yapay ölçüm örnekleriyle kapalı karar / puan raporu üretir. Bütün kaynak kod veya kullanıcı medyası otomatik gönderilmez. Model çıktısı kod / çeviri düzenlemez, komut çalıştırmaz, matematik / sıkıştırma kontrolünün veya gerçek testin yerini almaz.
- Jev'in daha iyi olduğu alanlar örnek bazında ölçülür; 14 yapay örnek veya yüksek confidence ile tüm diller / genel doğruluk iddiası yapılmaz. Yeni kullanım kapsamı bu ayrımı korumalıdır. [Kapsam ve komutlar](JEV_GELISTIRME_ARACI.md).
