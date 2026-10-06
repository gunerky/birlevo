# Harici PDF okuyucu ve sayfa aktarımı

6 Ekim 2026 — geliştirme kodu. Kullanıcı isteğiyle sürüm artırılmadı; imzalı release APK / AAB üretilmedi. Dondurulmuş 0.1.8 (9) paketi bu özelliği içermez. Debug paketleri yalnız QA emülatöründe doğrulama için kullanılır.

## Tamamlanan kapsam

- Ana sayfadan PDF okuyucuya giriş; Android dosya seçicisiyle PDF alma. Dosyanın uzantısına veya sağlayıcı MIME bilgisine güvenilmez; gerçek içerik Android PDF motorunda açılır.
- Geçerli sayfanın önizlemesi, önceki / sonraki sayfa, numarayla atlama; iki parmakla ve düğmelerle 1–4 kat yakınlaştırma / sürükleme. Yakınlaştırma mevcut önizlemeyi kullanır; metin seçme / arama yoktur.
- Orijinal PDF'nin yeni dosyaya baytları değiştirilmeden kopyalanması, uyumlu uygulamada açılması ve salt okunur URI ile paylaşılması. Kaynak URI'ye yazma / hata temizlemesinde silme engellenir. Bu işlem sıkıştırma olarak sunulmaz.
- Geçerli sayfadan PNG: en uzun kenar 1280 / 2048 / 4096 px, en-boy oranı korunur, beyaz zemin. PNG'nin aranabilir metin, bağlantı ve etkileşimli alanları korumadığı işlemden önce açıklanır.
- PNG kaydetme / açma / paylaşma; mevcut ortak çıktı akışıyla yeni veya kayıtlı sunuma, fotoğraf PDF aracına aktarma. Proje / fotoğraf PDF varlığına yeniden kodlama olmadan kopyalanır; paylaşım önbelleği temizlense de bağlı varlık korunur.
- Kaynak özel dosyaya kopyalanır; kaynak sağlayıcı izni daha sonra kaybolsa da oturum kopyası kullanılabilir. Sayfa numarası ve özel dosya referansı `SavedStateHandle` içinde tutulur; bitmap / parola kaydedilmez. Ekran yeniden oluşturma ve kaydedilmiş checkpoint'ten yeni ViewModel geri yüklemesi sınanır. Tam force-stop sonrası otomatik oturum açma vaadi yoktur.
- Hata ve yardım metinleri 22 yerelleştirmede mevcut. Kaynak / placeholder denetimi ile anlam uzmanı onayı ayrı tutulur.

## Motor ve kaynak sınırları

`PdfReaderPolicy`, `PdfReader`, `PdfRenderClient`, `PdfRenderService`, `PdfReaderViewModel` ve `PdfReaderScreen` sorumlulukları ayrıdır. Sayfalar sırayla ve ihtiyaç oldukça hazırlanır; bütün belge bitmap listesine çevrilmez.

Harici PDF'nin yerel ayrıştırma / çizimi, `exported=false`, `isolatedProcess=true` hizmetinde çalışır. İşçi uygulamanın depolama / ağ izinlerini almaz; yalnız kaynak için salt okunur FD ve tek PNG için yazılabilir geçici FD verilir. 30 saniyelik istek sınırı vardır. Son bağ kaldırılınca işçi kendi sürecini kapatır; iptal / zaman aşımı sonrası engellenmiş yerel parser uygulama sürecinde kalmaz. Bu izolasyon her bozuk PDF'nin güvenli veya hızlı açılacağı garantisi değildir.

İçe alma gerçek okunan baytta 128 MiB, belge sayısı 2.000 ile sınırlıdır. Mevcut medya araçlarının sınırları ve kalite seçenekleri değiştirilmez. Önizleme en uzun kenarı 1280 px; tam 4096 PNG dışa aktarımı ayrı işçidedir ve büyük bitmap UI sürecinde açılmaz. 4096×4096 ARGB iş tamponu yaklaşık 64 MiB olabilir; PDF motorunun ek belleği bu değere dahil değildir. Bellek / süreç hatasında açıklama gösterilir; çözünürlük sessizce düşürülmez. Dosya limitleri başarılı işlem / düşük donanım performans garantisi değildir.

İptal veya başarısız içe almada tamamlanmamış kaynak / PNG temizlenir. Kullanıcıya teslim edilmiş bitmap zorla geri dönüştürülmez. Başarısız belge seçimi önceden açılan geçerli belgeyi korur. Oturumda kullanılan kaynak hariç 24 saatten eski geçici reader dosyaları başlangıçta temizlenir; açık oturum kaynak dosyası korunur.

## Doğrulama

İlk odaklı koşuda 6/6 Android emülatörü testi geçti: gerçek üç sayfanın yönü / rengi, üç PNG çözünürlüğü, PDF kopyasının bayt eşitliği, kaynak üstüne yazma engeli, bozuk / şifreli / 128 MiB üstü dosya reddi, iptal temizliği, checkpoint geri yükleme, hata sonrası geçerli belgenin korunması, arayüz gezinme / zoom / döndürme, PDF paylaşım MIME ve salt okunur izinleri, PNG kaydetme ve iki araca kalıcı aktarım. Test dosyaları özgün sentetik dosyalardır; şifreli PDF yalnız test APK varlığıdır.

27/27 birim testi ve debug lint geçti (0 hata / 31 uyarı; çoğu bağımlılık güncelliği / KTX tarzı, mevcut animasyon analiz uyarısı da sürer). 22 dilin eksik anahtar / placeholder / üretilen kaynak kontrolü geçti; destek yardımındaki PDF giriş / PDF-PNG çıktı satırları da 22 dilde Android testinde görüldü. Bu dil uzmanı onayı değildir.

İlk tam regresyon koşusu 74/74 Android testiyle geçti. Sonraki tablet kontrolü, önizlemedeki çelişen genişlik / en-boy oranı kısıtlarının sayfayı düğmeler üzerine çizdiğini gösterdi. Önizleme ölçüsü açık hesaplandı, 700 dp ile sınırlandı; merkezlenmiş 900 dp içerik alanı ve gerçek çizilen görüntüye göre zoom / pan sınırları eklendi. Görüntünün sayfa kontrolleri arkasına taşmamasına ilişkin regresyon kontrolü kondu. Çıktı çözünürlükleri değişmedi. Dar ekrandaki bir sonraki test hatası tembel öğeyi bulamayan test seçicisindeydi; fotoğraf PDF listesindeki mevcut kaydırma yardımcısıyla düzeltildi.

Bu son ekran düzeltmelerinden sonra ilgili PDF paketi **6/6** yeniden geçti. Nihai kodda tablet (1600×2560, 160 dpi), dar ekran (720×1440, 360 dpi, 2× yazı) ve koyu telefon görünümünde **2'şer aktarım akışı** geçti; üç ekran görüntüsü görsel olarak incelendi. Tam 74 testlik koşu ekran düzeltmesinden önce, son 6 PDF + 6 yerleşim kontrolü düzeltmeden sonradır. Eski medya / sunum kodu bu ekran düzeltmesinde değişmedi.

Jev geliştirme bağlamı ve seçilecek uyarı listesi yeni okuyucu gerçeğiyle güncellendi. Araç için 20 çevrimdışı test ve Android kaynak / debug APK ayrım kontrolü geçti; bu adımda canlı Jev API çağrısı yapılmadı. Yerel Graphify AST grafiği 1052 düğüm / 2820 kenarla güncellendi; Kotlin parser'ının 12 kısmi dosya uyarısı kaynak kod / test doğrulamasının yerine geçmez.

Log, `qa-summary.json` ve ekran görüntüleri `output/development/pdf-reader/` altında tutulur. Eski Android sürümleri, karmaşık büyük PDF yükü ve fiziksel düşük donanım bu koşuyla ölçülmüş sayılmaz. Release küçültme ve imza doğrulaması, kullanıcı sonraki sürümü istediğinde yapılacaktır; SavedStateHandle kullanan ViewModel constructor'ı için dar keep kuralı hazırlanmıştır. Mevcut 0.1.8 AAB SHA-256 özeti değişmedi: `fe11985fd8e4d361f7f2f6fe0ff3ac01705e8ddda450d6a76a26815011f19345`.

## Sıradaki iş

PDF birleştirme / bölme / sayfa sıralama / döndürme motoru. Metin ve vektörleri koruyan belge düzenleme ile sayfayı PNG'ye düzleştirme ayrı işlemler olacaktır. Bu okuyucu düzenlenebilir DOCX / XLSX / PPTX veya OCR desteği eklemez. Parola girişini destekleme ve büyük / karmaşık belge test kümesini genişletme ayrıca planlanır. Telefon medya formatları, oturum dayanıklılığı ve reklam entegrasyonu sıradaki ürün hattında kalır.
