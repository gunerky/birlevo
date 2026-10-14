> 14 Ekim entegrasyon güncellemesi: Bu rapor ilk motor denemesinin tarihsel kaydıdır. Motor `app/src/main/java/com/example/contentstudio/office/OfficeCodec.kt` içine taşındı; sınırlı Office girişi/çıktısı ve belge ekranı [entegrasyon raporunda](DOCX_XLSX_ARAYUZ_ENTEGRASYONU.md) anlatılır. Aşağıdaki test koduna özel/MIME kapalı ifadeler önceki aşamayı anlatır.

# DOCX XLSX motor ve ticari lisans denemesi — 14 Ekim 2026

Karar: ilk uygulama entegrasyonu için **yerel, sınırları açık ZIP/XML okuma ve basit çıktı** yolu uygundur. Bu aşamada DOCX/XLSX uygulama desteği açılmadı. Deneme yalnız `androidTest` ve `tools/office/` altında; üretim biçim kayıtları, ViewModel'ler, eski belge oturumları ve yayın sürümü değişmedi. Genel Office düzenleme, formül hesaplama veya tam sayfa sadakati kanıtlanmış değildir.

## Motor karşılaştırması ve ticari koşullar

| Yol | İncelenen koşul | Bu aşamanın kararı |
| --- | --- | --- |
| Kendi ZIP/XML alt kümesi | Android/Java platform sınıfları ve kendi yazdığımız kod. Yeni SDK, font dosyası veya runtime bağımlılığı yok. | Denemede seçildi; sınırlı okuma/yazma ve açık kayıp raporuyla ilerleme. |
| Apache POI XWPF/XSSF | [POI lisans kaydı](https://poi.apache.org/legal.html) Apache 2.0 ve dağıtım NOTICE dosyasını belirtir. [Apache 2.0 dağıtım koşulları](https://www.apache.org/licenses/LICENSE-2.0) lisans kopyası, ilgili atıflar/NOTICE ve değiştirilmiş dosyaların bildirimini gerektirir. Ticari kullanım, bu koşulların karşılanmasına bağlıdır. | Reddedilmiş bir ticari model değildir; bu pakette eklenmedi. Android API/Dex/R8 ve gerçek geçişli artifact/hash denemesi tamamlanmadan dağıtım onayı verilemez. |
| docx4j | [Resmî kaynak lisansı](https://raw.githubusercontent.com/plutext/docx4j/master/legals/LICENSE) Apache 2.0; [kaynak README](https://github.com/plutext/docx4j) Java/JAXB dağıtım seçeneklerini anlatır. Ayrı modüllerin koşulları ana paketten genellenmez. | Bu pakette eklenmedi. Seçilmiş sürüm, geçişli paketler ve Android çalışma yolu ayrıca denenmelidir. |
| Microsoft Open XML SDK | [Kaynak lisansı](https://github.com/dotnet/Open-XML-SDK/blob/main/LICENSE) MIT; [resmî hedefler](https://learn.microsoft.com/en-us/office/open-xml/getting-started) .NET/NuGet ailesidir. | Mevcut Kotlin/Android uygulamasına doğrudan bağımlılık olarak seçilmedi. XML şema belgeleri referans olarak kullanıldı; SDK kodu kopyalanmadı. |

POI'nin [bileşen listesi](https://poi.apache.org/components/index.html) OOXML/schema/XMLBeans ve ek bağımlılıkları gösterir; [FAQ](https://poi.apache.org/help/faq.html) tam/kısıtlı şemaların kapsam farkını anlatır. Düşük donanım hedefi için bunları ölçmeden eklememek bir mühendislik tercihidir; POI'nin Android'de hiçbir yolda çalışamayacağı sonucu değildir. Bu karşılaştırma adaylara genel ticari dağıtım onayı vermez. Seçilecek her yeni gerçek koordinat/hash mevcut lisans envanterine alınmalıdır.

Mevcut `releaseRuntimeClasspath` envanteri tekrar çıkarıldı: **95 artifact ve native encoder** hash kapsamı geçti. Yeni runtime artifact yok. Font dosyası gömülmez; XLSX örneklerinde yalnız Arial adı istenir. Host araçları ve test varlıkları Android yayın paketine girmez. Bu inceleme Microsoft marka/patent haklarının veya başka bir SDK'nın bütün modüllerinin genel hukuk onayı değildir.

## Somut deneme

- DOCX ana parçası OPC ilişkisi ve etkin içerik türü üzerinden bulunur. Paragrafların ve tabloların sırası, Unicode metin, boşluk, sekme ve satır sonları okunur. Düz paragraf/tablo içeriğinden yeni DOCX yazılır. Başlık stilleri, kalın/italik, sayfa düzeni, üstbilgi/altbilgi, görsel, bağlantı, not, liste ve benzeri kayıplar kaynakta saptandığında raporlanır. Zengin kaynağı bu alt kümeyle yeniden yazmak varsayılan olarak reddedilir; belirlenen kayıpları açıkça onaylayan çağrı gerekir.
- XLSX'te bütün seçilmiş sayfa adları, seyrek A1 konumları, shared/inline string, sayı/boolean/hata/ISO tarih türü, stil ve sayı biçimi bilgisi tutulur. ISO tarih metni ile sayısal Excel tarihi birbirine çevrilmeden ayrılır; `date1904` metaverisi korunur. Birleştirilmiş aralıklar rapora alınır. Formül metni ve varsa kaydedilmiş sonucu iki farklı alandır; sonuç yoksa `null` kalır. Hesaplama motoru veya önbelleğin güncel olduğuna dair garanti yoktur.
- CSV/TSV benzeri düz metin tablosundan yeni XLSX yalnız metin hücreleriyle yazılır. `00123`, `0` ve `=HYPERLINK(...)` gibi içerikler metin olarak kalır; formül veya dış bağlantı oluşturulmaz. Tabloyu düzleştirmek açık kayıp onayı gerektirir. Bir sayfanın seçilmesi diğer sayfaları otomatik korumuş sayılmaz; uygulama entegrasyonunda bu seçim ayrıca görünür olmalıdır.
- OPC'de içerik türü yalnız `Override` ile tanımlanmak zorunda değildir; `Default` uzantı eşlemesi de denemede desteklenir. Bağımsız üreticinin geçerli XLSX'i bu farkı ortaya çıkardı. Kaynak biçim belgeleri: [WordprocessingML](https://learn.microsoft.com/en-us/office/open-xml/word/structure-of-a-wordprocessingml-document), [SpreadsheetML](https://learn.microsoft.com/en-us/office/open-xml/spreadsheet/structure-of-a-spreadsheetml-document), [formül ve kayıtlı değer ayrımı](https://learn.microsoft.com/en-us/office/open-xml/spreadsheet/working-with-formulas).

## Kaynak ve kaynak kullanımı sınırları

Arşiv diske açılmaz. Paket yolu/ilişki kökten kaçamaz; dış hedefler okunmaz. Makrolu/imzalı paketler bu adayda reddedilir. Gerçek şişmiş baytlar ve CRC denetlenir; okunan XML parçalarında DTD, çözümlenemeyen entity, aşırı XML derinliği/düğümü reddedilir. İptal okumada ve yazmada kontrol edilir; başarısız `.part` dosyası temizlenir ve yeni çıktı atomik ad değiştirmeyle tamamlanır. Kaynak hash'i işlemden sonra aynı kalır.

Deneme sınırları: 4 MiB paket, parça başına 1 MiB, toplam 8 MiB açılmış içerik, 256 ZIP kaydı, 30.000 XML düğümü, derinlik 64; XLSX için 16 çalışma sayfası, 500 satır/100 sütun/5.000 hücre ve alan başına 32.768 karakter. Bunlar test motorunun ölçülü sınırlarıdır; mevcut CSV/TSV aracının 5.000 satır/50.000 hücre sınırını düşürmez. Büyük XLSX için üretime almadan önce akışlı hücre okuma, seçili sayfa ve tembel önizleme gerekir. Bellekteki sınırlı XML ağacı genel belge renderer'ı değildir.

## Kanıt ve açık sınırlar

Sentetik Word örneği Python-docx, zengin XLSX örneği bağımsız Artifact Tool ile oluşturuldu. Ayrı shared-string/formül önbelleği olmayan XLSX ve yinelenen ZIP adı örnekleri vardır. Android'de kendi motorunun yazdığı basit DOCX/XLSX dosyaları bağımsız Python-docx/openpyxl ve Artifact Tool okuyucularında açıldı. Bir paragraf ve hücre düzenlenip dosyalar tekrar kaydedildi; Android denemesi bunları yeniden okuyabildi. Formula cache kontrolü yeniden hesaplama değildir. XLSX kaynak sayfaları ve yeni tablo bağımsız olarak çizildi; font/renderer farkları not edilir.

Word sayfa çizimi için paketli `render_docx.py` denendi; yerel `soffice.exe` bulunmadı. Bu yüzden **Word sayfa yerleşimi doğrulanmadı**. XLSX'in bağımsız PNG renderer'ı hem kendi oluşturduğu kaynakta hem Android çıktısında `00123` / `00000` metnini görüntüde `123` / `0` gösterdi. XML, Python/openpyxl, Artifact Tool değer okuması ve yeniden kaydedilen dosyaların Android kontrolünde gerçek metin korunur; bu görüntü davranışı dosya içeriğindeki kayıp olarak genellenmez. Öte yandan PNG, baştaki sıfırların görünümünü doğrulayan kanıt da sayılmaz. Uygulama entegrasyonunda kendi metin önizlemesi ve açık tarih/sayı biçimi gerekir; bu host renderer kullanılmayacak.

Microsoft Word/Excel veya PowerPoint'te gerçek uygulama testi yapılmadı. Kullanıcıdan Office kurulumu veya fiziksel cihaz istenmez. Emülatör süreleri cihaz garantisi değildir; bütün dosya çeşitleri ve uygulama sürümleri için doğruluk iddiası yapılmaz.

Son QA yalnız `ofice_qa`, Android API 36 debug hedefindedir: 8 Office denemesi + 9 mevcut belge regresyonu = **17**, bağımsız düzenlemeden Android’e dönüş **1**, toplam **18 emülatör kontrolü** geçti. Kaynak hash, metin/formül ayrımı, eksik cache, ISO/sayısal tarih, kayıp onayı, ZIP yol/DTD/boyut/yinelenen kayıt, iptal temizliği ve eski araç yolları kapsandı. Eski PDF aktarım testinde model asenkron ekran geçişinden önce alınıyordu; test gerçek `reader-preview` ekranını bekleyecek şekilde düzeltildi. Uygulama davranışı değiştirilmedi. Başarısız ve çakışan ilk teşhis koşuları son kabul sayısına eklenmedi.

106 JVM testi, debug APK/test APK, 22 dil anahtar/format kontrolü ve lint geçti (0 hata, 62 uyarı, 2 ipucu). Yerel AST grafiği güncellendi; kısmi Kotlin parser sınırları kaynak/test kanıtının yerine kullanılmadı. Son örnekte Word ZIP/XML okuması 830.200 açılmış bayt için 14 ms, XLSX 11.982 bayt için 14 ms ölçüldü; tek sentetik emülatör koşusudur, genel performans veya bellek garantisi değildir.

Ham günlükler, önizlemeler, JSON raporları ve üretilen dosyalar `output/development/office-trial/` altında Git dışındadır. Kaynak örneklerin hash manifesti `app/src/androidTest/assets/office/manifest.json`; tekrar komutları `tools/office/README.md`. Jev, ağdan formül/dosya çözümü veya kullanıcı medyasının dış servise gönderilmesi yoktur.

## Sonraki somut ürün adımı

Bu denemenin seçilmiş alt kümesini üretim motoruna taşıma: DOCX metin/tablo okuma, XLSX sayfa/hücre ve formül-önbellek açıklaması, kayıp önizlemesi ve açık çıktı seçimi; basit metin→DOCX ve tablo→XLSX yolları. Dosya yönlendirmesi, eski oturum uyumu, iptal/kayıt, kaynak koruması ve 22 dilde metinler birlikte tamamlanmadan MIME listesi açılmayacak. Tam Office biçimini koruyan düzenleyici ve formül hesaplama ayrı ürün işi olarak kalır. Yeni release yalnız kullanıcı istediğinde hazırlanır.

Üretim kaynakları değişmedi. Debug uygulama APK SHA-256: `87971ea158d12ed90986c89e397aa3730fb8536c8a6289390d689f3798466887`; önceki RTL adımıyla aynı. Yeni sürüm, imzalı release veya AAB hazırlanmadı; 0.1.11 (12) korunur.
