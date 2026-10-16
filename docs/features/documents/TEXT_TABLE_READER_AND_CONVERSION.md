# Birlevo — metin / tablo okuyucusu ve dönüşüm

6 Ekim 2026. Bu özellikler geliştirme kodundadır. Sürüm numarası artırılmadı; imzalı release APK / AAB hazırlanmadı. Dondurulmuş 0.1.8 (9) bunları içermez. Son doğrulama kanıtları `output/development/text-documents/` içindedir.

## Çalışan yollar

| Giriş | Okuma | Çıktı |
| --- | --- | --- |
| TXT | Kaydırılabilir metin bölümleri | UTF-8 TXT, çok sayfalı PDF |
| MD | Markdown kaynak metni; biçimli Markdown render edilmez | UTF-8 TXT, çok sayfalı PDF |
| CSV | Virgül / noktalı virgül / sekme algılama veya elle seçim; tırnaklı hücreler | UTF-8 CSV, TSV, tabloyu metin olarak TXT, PDF satır raporu |
| TSV | Sekme ayırıcı; tırnaklı hücreler | UTF-8 CSV, TSV, tabloyu metin olarak TXT, PDF satır raporu |

Ana ekrandaki sekizinci çalışan araç **Metin ve tablolar**dır. Dosya türü, karakter kodlaması, ayırıcı ve geçerli hedef biçimler seçilebilir. Kaydetme sistem dosya seçicisini kullanır; sonuçlar mevcut açma / paylaşma araçlarıyla kullanılır. Yeni biçim listesi giriş / çıktı çiftlerini ayırır. DOCX, XLSX, PPTX, RTF ve eski Office ikili dosyaları bu araçta desteklenmez. Bunlar ayrı motor ve sadakat denemesi gerektirir; uzantı / MIME eklemekle açılmış sayılmaz.

Metinde seçilen bölüm veya tabloda seçilen satır yeni / kayıtlı sunuma kopyalanabilir. Satırın bütün sütunları aktarılır. İçerik 420 karakterlik slayt bölümlerine ayrılır; toplam 100 slayt sınırı aşılırsa aktarım reddedilir, içerik sessizce kesilmez. Mevcut slaytlar, görseller, sıralama ve tema korunur; yeni metin slaytlarının kimlikleri ayrıdır. Kaynak okuyucu URI'si sunum varlığı olarak saklanmaz. Oluşturulan PDF, bağımsız çalışma kopyası üzerinden PDF okuyucuya aktarılır.

## İçerik ve kayıp açıklaması

UTF-8, UTF-16LE / BE (BOM ile otomatik) ve elle Windows-1254 / Windows-1252 seçimi vardır. BOM'suz otomatik mod UTF-8 kullanır; bozuk baytların yerine sessizce karakter koymaz. Kodlama hatasında seçim değiştirilip dosya tekrar açılır. Bütün eski kod sayfaları veya her cihaz fontu için destek vaadi yoktur.

CSV / TSV parser hücre içi ayırıcıları, çift tırnakları, çok satırlı hücreleri, boş son hücreleri, baştaki sıfırları ve farklı uzunluktaki satırları korur. CSV çıktısı virgüle, TSV / tablo TXT çıktısı sekmeye normalize edilir; kayıt sonları CRLF'dir. Tırnak biçimi kaynakla birebir aynı olmayabilir; hücre içerikleri yeniden parse edilerek karşılaştırılır. Formül metni korunur fakat uygulama formülleri çalıştırmaz. Başka bir tablo uygulamasının bu metni nasıl yorumlayacağı o uygulamaya bağlıdır.

PDF bitmap'e düzleştirilmiş belge değildir. Android metin yerleşimiyle A4 sayfalara yazılır. Tablo çıktısı özgün elektronik tablo düzeni yerine **satır ve sütun etiketli metin raporu**dur. Son sayfa ve seçilebilir Latin metin doğrulandı. Arapça / Farsça / CJK / emoji örneği de render edilir; bu, bütün karmaşık yazılar için arama / metin çıkarma sadakati garantisi değildir.

Önizlemede satırın ilk üç sütunu ve hücrenin kısa bölümü gösterilir; bu sınır ekranda açıklanır. Satıra dokununca bütün hücreler ayrı kaydırılabilir ayrıntıda açılır. Tam dışa aktarma önizlemenin kısaltılmış metnini kullanmaz. Metin önizlemesi 2048 karakterlik, Unicode surrogate çiftlerini bölmeyen parçalarla hazırlanır.

Yeni belge oluşturma / dönüşüm sıkıştırma değildir. Bu nedenle daha büyük bir PDF üretmek sıkıştırma başarısı olarak sunulmaz. Mevcut görsel / video sıkıştırmasının gerçek boyut karşılaştırması ve sonuca özel büyük kopya onayı korunur.

## Kaynak, oturum ve kaynak kullanımı

- Dosya özel `noBackupFilesDir/text-documents/` alanına sınırlandırılmış akışla alınır. Kaynak dosya değiştirilmez. Başarısız değiştirme önceki geçerli belgeyi ve sonucu korur.
- Başlangıç sınırı: 4 MiB ham dosya; 2.000.000 UTF-16 metin birimi; 5.000 satır, 100 sütun, toplam 50.000 hücre; hücre başına 32.768 metin birimi. PDF en çok 300 sayfadır. Limit aşımı açık hata verir; metin kısaltılmaz.
- Dosya okuma / yazma / PDF işi IO'da, metin önizleme bölme işi Default'ta; listeler kimliği korunan tembel öğelerdir. Aynı ViewModel'de tek işlem çalışır. Bu sınırlar toplam heap / süre / pil garantisi değildir; 300 sayfalı PDF belge nesnesinin maliyeti fiziksel düşük donanımda ölçülmedi.
- İptal / hata teslim edilmemiş özel kaynak ve sonuç kopyalarını siler. Kullanıcıya teslim edilmiş bitmap zorla geri dönüştürülmez. Başarısız dışa aktarmanın kısmi hedefi sağlayıcı destekliyorsa silinir.
- Gecikmiş kayıt seçicisi sonucu, dosya değiştirme / sıfırlama sonrasında eski orijinali yazamaz veya silemez. Seçicinin kaynak URI'si ve beklenen çıktı kimliği birlikte denetlenir.
- Küçük SavedState kaydı yalnız özel dosya adı, orijinal URI, ad, boyut, okuma / kodlama / ayırıcı / hedef bilgilerini saklar. Büyük metin Bundle'a konmaz. Android kayıtlı durum sağladığında özel kaynak yeniden parse edilir; çıktı yeniden üretilebilir. İsimli kalıcı metin projeleri veya her kapatma biçiminde oturum geri kazanımı henüz yoktur.
- Sunum aktarımı mevcut atomik tek yazıcıyı kullanır. Kayıt hatası taslağı yeniden deneme için açık bırakır. FileProvider yetkisi ve kök adları korunur.

## Doğrulama

Saf Kotlin codec testleri altı senaryoda Unicode, kodlama hatası, ayırıcı, tırnak, limit / iptal ve 100 yapay tablo round-trip örneğini kontrol eder. Toplam 39 birim testi geçti. Debug uygulama / test APK derlemesi ve lint geçti: 0 hata, önceki 38 uyarı.

API 36 QA emülatöründe dokuz yeni test geçti: gerçek dönüşüm / kayıt baytları, kaynak değişmezliği, UTF-16 / eski Türkçe kodlama, son içeriği koruyan çok sayfalı PDF, hatalı / büyük dosya / iptal temizliği, küçük checkpoint ile geri yükleme, gerçek seçici hedefi / PDF okuyucu geçişi, yedi sütunlu satır ayrıntısı / sunum aktarımı ve eski kayıt seçicisinin kaynak koruması. Sistem seçicisi test yanıtları yapay sağlayıcıyla verilir; bütün OEM sağlayıcıları sınanmış değildir.

İlk turdaki iki UI test hatası metin eşleştirme ve üst çubuk aramasındaydı. İkinci turdaki aralıklı aktarım testi AtomicFile yazımı devam ederken depoyu tekrar tekrar okuyordu. Test artık tek yazıcının kayıt tamamlanma durumunu bekleyip bitmiş dosyayı bir kez doğrular. İlk başarısız loglar korunur; başarılı son tur `device-tests-verified.log` içindedir.

Mevcut ana ekran, proje otomatik kaydı, ortak çıktı / sunum görsel aktarımı, PDF okuyucu / sayfa araçları ve fotoğraf PDF akışlarına ait 38 regresyon testi de geçti. Telefon, tablet, iki kat büyük yazılı dar ekran ve koyu modda native ekran kontrolleri geçti; bütün üç PDF görseli de incelendi. İlk PDF QA render'ının şeffaf zemini siyah göründüğü için test bitmap'i beyaza doldurularak doğru sayfa görüntüsü tekrar alındı; bu bir çıktı metni kaybı değildi.

22 dilin 222 anahtarı ve placeholder yapısı doğrulandı. Yeni 24 metnin çevirileri taslaktır; bütün diller için uzman onayı iddiası yoktur. RTL görsel incelemesinde karşılaştırma / sayı / MiB sırası karıştığı görüldü; Arapça ve Farsça ölçü grupları Unicode yön izolasyonu ile düzeltildi. İlgili iki native test bu kaynak düzeltmesinden sonra tekrar geçti ve görseller incelendi; fazların APK hash'leri QA kaydında ayrıdır. Jev'in yeni seçilmiş metin / senaryo kapsamı güncellendi; 20 çevrimdışı araç testi geçti. Bu işte canlı Jev çağrısı yapılmadı; Android kodu ve APK içinde Jev / TypeSafe bağlantısı veya anahtarı yoktur.

Mevcut akışların regresyon sonuçları, telefon / tablet / büyük yazılı dar ekran / koyu / RTL görselleri ve nihai hash'ler aynı QA dizinindeki `qa-summary.json` içinde tutulur. Fiziksel telefon, eski Android sürümleri, düşük depolama / tüm sağlayıcı hataları ve release R8 doğrulaması açık kalır. Emülatör sonucu fiziksel FPS, bellek veya pil garantisi değildir.

## Sonraki iş

Güncel sıranın ilk somut TXT / MD / CSV / TSV paketi tamamlandı. DOCX / XLSX tam sayfa / formül sadakati ayrı teknik deneme olarak bekler. Sonraki uygulama işi **oturum ve uzun işlem dayanıklılığı**: video ayarları ve fotoğraf PDF seçimlerinin süreç kapanması sonrası geri yüklenmesi, özel kaynak sahipliği, iptal / hata / düşük depolama ve uygun arka plan işi. Ardından çıktı biçimleri / ayrı ses aracı / ek codec'ler gelir. Yeni release yalnız kullanıcı istediğinde hazırlanır.
