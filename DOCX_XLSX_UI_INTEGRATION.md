# DOCX/XLSX belge ekranı — 14 Ekim 2026

Yerel motor belge ekranına ve genel dosya yönlendirmesine bağlandı. Bu, düz metin ve hücre odaklı bir Office alt kümesidir. Microsoft Word/Excel yerleşim veya hesaplama motoru eklenmedi.

| Kaynak | Okuma ve çıktı |
|---|---|
| TXT / MD / DOCX | Düz metin, DOCX paragraf/tablo metni; TXT, metin PDF’si veya sade DOCX |
| CSV / TSV / XLSX | Satırlar, XLSX çalışma sayfası seçimi; TXT, CSV, TSV, satır PDF’si veya metin hücreli XLSX |

DOCX içindeki desteklenen paragraf ve tablo metni okunur. Sunuma aktarım mevcut seçili metin/bölüm yolunu kullanır. DOCX yeniden yazımında temel paragraf ve tablo yapısı kalır; PDF/TXT görünümü kaynak Word sayfalarının kopyası değildir. Zengin biçimlendirme, resimler, başlık/altbilgi, notlar, alanlar, değişiklik takibi, içerik denetimleri ve desteklenmeyen gömülü içeriklerin korunması iddia edilmez.

XLSX kaynak modeli hücre adreslerini, kaynak türünü, ham değeri, formül metnini, varsa kayıtlı sonucu, biçim bilgisini, birleşimleri ve tarih sistemini ayrı tutar. Kullanıcı çalışma sayfasını ve **kayıtlı sonuç** veya **formül metni** görünümünü seçer. Formüller hesaplanmaz. Sonucu bulunmayan formül hücresi değer çıktısında boş kalır; sıfır üretilmez. Eksik sonuç sayısı ve örnek adresler gösterilir. Sayı/tarih biçimleri uygulanmaz; sayısal tarih için 1900/1904 bilgisi ayrıca verilir.

Yeni XLSX kopyasında **bütün hücreler metindir**. `00123`, `00000` ve `=SUM(...)` metni sayıya veya çalıştırılabilir XLSX formülüne dönüştürülmez. Diğer çalışma sayfaları, birleşimler, stiller ve grafikler çıktıya taşınmaz. CSV formül metnini başka uygulamalar yorumlayabileceği ayrıca açıklanır. Bu seçenek, biçimleri/formülleri koruyan genel Excel düzenlemesi değildir.

## Onay, oturum ve kaynak güvenliği

- Office kaynağının dönüştürülmesi veya sunuma aktarılması açık kayıp onayı ister. Motor da onaysız çıktı oluşturmayı reddeder.
- Çalışma sayfası, formül/değer modu veya çıktı biçimi değişince eski sonuç ve onay geçersizleşir. Onayın kaldırılması da eski sonucu kaldırır.
- Kontrol noktası yalnız kaynak kopyası ve hafif seçenekleri tutar; Office modeli veya belge metni Bundle içine yazılmaz. Normal TXT/CSV/TSV kaydının sekiz alanlı yapısı korunur. Office için biçim, sayfa ve mod eklenir; süreç yeniden kurulduğunda onay yeniden istenir.
- Başarısız veya iptal edilen yeni kaynak seçimi önceki çalışmayı değiştirmez. Yeni özel kopya ve tamamlanmamış çıktı temizlenir. Eski dosya seçici sonucu kaynak üzerine yazamaz; sonuç kimliği hâlâ doğrulanır.
- Office ZIP çıktısı benzersiz `.part` dosyası, flush/sync ve rename üzerinden yayımlanır. Açma/paylaşma mevcut değişmez çıktı kopyasını kullanır. FileProvider yetkisi ve kök adları değişmedi.
- Bu işlem belge oluşturma/dönüştürmedir; sıkıştırma başarısı sayılmaz. Mevcut medya sıkıştırması ve büyük kopya onayı yolları değişmedi.

## Sınırlar ve performans

Office kaynakları için sıkıştırılmış giriş 4 MiB, gerçek açılmış toplam 8 MiB, parça başına 1 MiB, 256 ZIP girdisi, 30.000 XML düğümü ve 64 derinlik sınırı vardır. En çok 16 çalışma sayfası, 500 satır, 100 sütun ve tüm çalışma kitabında 5.000 dolu hücre okunur. Seçili sayfanın önizleme ızgarası en çok 50.000 yuvadır. XLSX çıktısı 5.000 metin hücresiyle sınırlıdır. DOCX için 500 blok, tablo/hücre sınırları ve 2 milyon karakter sınırı uygulanır. Aşım sessiz kırpmayla değil hata ile sonuçlanır.

Eski CSV/TSV’nin 5.000 satır / 50.000 hücre kapsamı küçültülmedi. Office çıktı sınırını aşan düz tablo mevcut CSV/TSV/PDF yollarından kullanılabilir.

İçe aktarma, ZIP/XML denetimi ve çıktı IO üzerinde çalışır; seçili sayfanın görünümü Default üzerinde hazırlanır. Kaynak çalışma kitabı seyrek hücrelerle tutulur; ayrıştırılan çalışma sayfasının XML ağacı bırakılır. Adres düzenli ifadesi bir kez oluşturulur. Hücre ayrıntıları, her sütun için tüm kitabı taramak yerine adres haritasından alınır. Önizleme satırları mevcut kimlikli LazyColumn yolundadır. Çıktı kalitesi veya mevcut çözünürlük seçeneği azaltılmadı.

Dosya uzantısı/MIME yalnız yönlendirme ipucudur. Ana OPC parçası, içerik türleri, ilişkiler, gerçek açılmış boyut ve CRC denetlenir. Haricî içerik indirilmez, diske ZIP açılmaz, DTD ve belirsiz yollar reddedilir. Makro/imzalı/şifreli paketler, eski DOC/XLS, Strict OOXML ve ortak/dizi/veri tablosu formülleri bu kapsamda desteklenmez. Tam şema doğrulaması veya bütün Office özelliklerini algılama iddiası yoktur.

## Doğrulama ve lisans

Yeni ekran metinleri 22 dilde eklendi; anahtar, biçim belirteci ve üretilen kaynak eşitliği kontrol edildi. Ekran akışları İngilizce, Arapça ve Farsça ile; normal telefon, yüzde 200 yazı ve koyu tablet profillerinde doğrulanır. Diğer dillerin ana dili konuşuru incelemesi ve elle TalkBack dinleme kontrolü yapılmış sayılmaz. Onay, etiketli tek bir checkbox eylemidir ve dokunma alanı en az 48 dp’dir. Kaynak metin/hücre yönü arayüz dilinden ayrıdır; RTL’de formülün `=` işareti kaydırılmaz.

Kabul edilen kayıtlar `output/development/office-integration/` altındadır. Tekrarlanabilir emülatör akışları:

- 106 JVM testi: hata yok; debug derleme başarılı. Lint: 0 hata, 64 uyarı, 2 öneri.
- 32 başarılı emülatör yürütmesi: 17 mevcut belge/Office güvenlik kontrolü, 10 yeni telefon akışı, 3 yüzde 200 yazı/RTL akışı, 1 koyu tablet akışı ve 1 bağımsız okuyucuda düzenlenmiş dosyayı Android’de yeniden açma kontrolü.
- DOCX paragraf/tablo metni, XLSX tür/kayıtlı sonuç/eksik sonuç, baştaki sıfırlar ve literal formüller bağımsız okuyucularla doğrulandı. Hatalı deneme kayıtları bu sayıya dahil değildir.
- Son debug APK SHA-256: `1f90f24be32a1ff1a4876d35d127f8c9646f77be17d3e6b650b2c45187e4fc3e`. Son küçük biçim etiketi düzeltmesi ayrıca debug/JVM/lint ile doğrulandı.

```powershell
./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
python tools/localization/generate.py --check
./tools/testing/office-trial-qa.ps1
./tools/testing/office-document-qa.ps1 -SkipInstall -Profile Phone
./tools/testing/office-document-qa.ps1 -SkipInstall -Profile LargeText
./tools/testing/office-document-qa.ps1 -SkipInstall -Profile DarkTablet
```

Koşucular yalnız `ofice_qa` emülatörünü kabul eder ve ekran/yazı/tema ayarlarını geri yükler. Enstrümantasyon koşuları sırayla yapılır. Bağımsız python-docx/openpyxl ve Artifact Tool açma/düzenleme/yeniden kaydetme kanıtı `tools/office/` araçlarıyla sürdürülür. Artifact Tool PNG önizlemesinin sayıya benzeyen metinde sıfır düşürme sınırlaması dosya değerlerinden ayrıdır; Android önizlemesinde literal `00123` korunur. Gerçek Microsoft Office veya fiziksel cihaz performansı test edilmiş sayılmaz.

Yeni SDK, codec veya gömülü font yoktur. Motor uygulamanın yerel Java ZIP / Android XML kodudur; bağımsız geliştirme okuyucuları APK’ya eklenmez. 95 gerçek runtime artifact/hash ve ayrı native encoder kapsamı lisans kontrolünden geçti. Jev/API çağrısı yapılmadı. Yeni release sürümü hazırlanmadı; 0.1.11 (12) yayın paketi değişmedi. Yeni kodun R8/yayın testi kullanıcının sonraki sürüm isteğinde yapılacaktır.
