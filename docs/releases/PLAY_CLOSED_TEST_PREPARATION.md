# Birlevo kapalı test gönderim hazırlığı — 5 Ekim 2026

**15 Ekim 2026:** Güncel geliştirme/yayın ayrımı ve testçi adımları [yeni kapalı test kontrol listesinde](../testing/CURRENT_CLOSED_TEST_CHECKLIST.md). Son imzalı 0.1.11/code 12 paketi dondurulmuştur; sonraki geliştirme özellikleri o pakete eklenmiş sayılmaz. Bu doğrulamada yeni release/Console gönderimi yapılmaz. Aşağıdaki durumlar tarihsel kayıttır.

Güncel sürüm **0.1.7 (8)**: kullanıcı 5 Ekim 2026'da dahili teste yüklediğini bildirdi. Kapalı teste gönderme / onay / dağıtım henüz doğrulanmadı. [0.1.7'ye özel geçiş kontrol listesi](../testing/CLOSED_TEST_0.1.7_CHECKLIST.md), [paket ve teknik QA](../../output/releases/0.1.7/README.md). Yeni gizlilik kaynaklarının yayımlanması ve mağaza metni / güncel telefon görselleri kontrolü bekliyor. Aşağıdaki 0.1.0 bölümü ilk gönderimin tarihsel kaydıdır.

## Doğrulanan derleme

- Çalışma adı: **Birlevo**; Android `applicationId`: `studio.birlevo.android`; sürüm `1 (0.1.0)`; Android 7.0 ve üzeri (`minSdk 24`), hedef SDK 36.
- `bundleRelease`, debug APK, birim testleri, lint ve Android 16 emülatöründe 28/28 cihaz testi başarılı. Son logolu **imzalı** yükleme paketi [birlevo-0.1.0-1.aab](../../output/releases/0.1.0/birlevo-0.1.0-1.aab) olarak hazırlandı. İmza/sertifika eşleşmesi, bundletool, manifest ve 16 KB hizalama kontrolleri geçti; ayrıntılar [RELEASE_PACKAGE_REPORT.md](RELEASE_PACKAGE_REPORT.md) içindedir. Kullanıcının paylaştığı Play Console ekranı sürüm kodu 1 için AAB yüklemesini ve iki tanılama uyarısını gösteriyor; dahili testte yayımlanmasının tamamlandığı henüz doğrulanmadı.
- Release birleştirilmiş manifestinde `ACCESS_NETWORK_STATE` var; `INTERNET`, reklam kimliği (`AD_ID`) ve medya deposunu bütünüyle okuma izni yok. Reklam veya analiz SDK'sı ekli değil. Play beyanları, yüklenen **son** AAB için yeniden denetlenmeli.
- Web araması bir marka veya paket kimliğinin müsait olduğunu hukuken ya da Play Console düzeyinde kanıtlamaz. İlk yüklemeden önce Console'da paket kimliğini ve adın kullanım hakkını doğrula.

## Mağaza metni taslağı

**Başlık:** Birlevo

**Kısa açıklama:** Görselleri ve videoları küçültün; görselli sunumlarınızı PDF olarak kaydedin.

**Açıklama:** Birlevo, günlük içerik işlerini Android telefonunuzda tek yerden yapmanıza yardımcı olur. JPEG veya PNG görsel seçip kalite ve boyut ayarlarıyla yeni bir JPEG kaydedin. Desteklenen MP4 videoda başlangıç ve bitişi seçin, kaliteyi ayarlayın ve sesli MP4 çıktı oluşturun. Metin ve slayt başına bir görselle sunum hazırlayıp PDF olarak dışa aktarın. Çıktıyı kaydetmeden önce önizleyin ve boyutunu görün. Kaynak dosya korunur; yeni dosyanın nereye kaydedileceğini siz seçersiniz. Kayıtlı sunum taslaklarını daha sonra açabilirsiniz. Dönüştürme cihazda yapılır.

**Kapsam uyarısı:** Bu sürüm genel Office dosya okuyucusu, PDF düzenleyici, çok klipli video zaman çizelgesi veya otomatik altyazı içermez. Sıkıştırmanın her dosyada boyutu azaltacağı vaat edilmez. Mağaza görselleri gerçek uygulama ekranlarından alınmalı.

## İmza ve yükleme sırası

1. Play Console'da yeni uygulama kaydını oluştur; `studio.birlevo.android` kimliğini **ilk artefakt yüklemesinden önce** denetle. Gerekirse yerel kimliği değiştirip bütün testleri tekrar çalıştır.
2. Yükleme anahtarını güvenli, Git dışı bir konumda üret ve şifresiyle birlikte yedekle. Örnek: `keytool -genkeypair -v -keystore <gizli-yol> -alias birlevo-upload -keyalg RSA -keysize 3072 -validity 10000`. Şifreleri terminal geçmişine veya repoya yazma.
3. `BIRLEVO_UPLOAD_KEYSTORE`, `BIRLEVO_UPLOAD_STORE_PASSWORD`, `BIRLEVO_UPLOAD_KEY_ALIAS`, `BIRLEVO_UPLOAD_KEY_PASSWORD` ortam değişkenlerini yalnızca derleme oturumunda ayarla. Dördü de verilirse Gradle release AAB'yi yükleme anahtarıyla imzalar; eksik küme hata verir.
4. `./gradlew bundleRelease` çalıştır. `jarsigner -verify app/build/outputs/bundle/release/app-release.aab` ile imzayı doğrula; dosya boyutu / SHA-256 ve sürüm kodunu kaydet. Play App Signing için Console yönergelerini izle ve anahtarın kurtarma bilgilerini güvenle sakla.
5. [Gizlilik politikası](../licensing/PRIVACY_POLICY_DRAFT.md) Türkçe `https://birlevo.com/privacy/` ve İngilizce `https://birlevo.com/en/privacy/` adreslerinde yayımlandı; ikisi de HTTPS üzerinden doğrulandı. Kaynak HTML [web/privacy/index.html](../../web/privacy/index.html) ve [web/en/privacy/index.html](../../web/en/privacy/index.html) dosyalarındadır. Son AAB ile metni tekrar karşılaştır. Play Console veri güvenliği, uygulama içeriği, reklam, yaş grubu, erişim ve ülke beyanlarını son AAB'ye göre doldur. Reklam içermeyen bu ilk derlemeye reklam var demek veya reklam gelirini mağaza açıklamasında vaat etmek doğru olmaz.
6. Kapalı test kanalına imzalı AAB'yi yükle; testçi e-posta listesini / grubunu ve geri bildirim kanalını tanımla. Katılım bağlantısını Console oluşturduktan sonra testçilere ilet. Google'ın inceleme süresi dış bağımlılıktır.

Resmî yönergeler: [Play uygulama oluşturma](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en), [kapalı test kurulumu](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en), [veri güvenliği](https://support.google.com/googleplay/android-developer/answer/10787469).

## Reklam eklendiğinde zorunlu yayın kontrolü

Kullanıcı 5 Ekim 2026'da, reklam entegrasyonu sırasında Play beyanlarının güncellenmesinin unutulmamasını istedi. Bu kontrol, reklam SDK'sı ekleyen veya reklam/veri davranışını değiştiren her sürümde uygulanır; dahili ve kapalı testte test reklamları kullanılması da kontrolü atlatmaz. İşaretlenmemiş maddeler reklamlı sürümün incelemeye gönderilmesinden önce tamamlanır.

- [ ] Son AAB'nin birleştirilmiş manifesti, reklam/analiz SDK sürümleri, izinleri ve gerçek veri davranışı incelendi.
- [ ] **Uygulama içeriği → Reklamlar:** Reklam sunan sürüm için beyan **Evet** olarak güncellendi.
- [ ] **Uygulama içeriği → Reklam Kimliği:** Uygulama veya SDK reklam kimliği kullanıyorsa beyan **Evet** ve gerekli `AD_ID` izni doğrulandı. Kimlik kullanılmıyorsa reklam var diye otomatik Evet seçilmedi; SDK yapılandırmasıyla tutarlı yanıt verildi.
- [ ] **Veri güvenliği:** Kullanılan SDK ve ayarlara göre veri toplama/paylaşma, veri türleri ve amaçları güncellendi. Test reklamı olması tek başına veri toplanmadığı varsayımına yol açmadı.
- [ ] **Gizlilik politikası:** Türkçe/İngilizce web metinleri ve uygulama içi açıklama reklam sağlayıcısını ve gerçek veri işleme davranışını yansıtıyor. Mevcut sürüm için “reklam veya analiz SDK'sı bulunmaz” gibi geçersiz kalan ifadeler düzeltildi; politika yayımlandı ve Play bağlantıları doğrulandı.
- [ ] Gerekli izin/rıza ve gizlilik tercihleri akışları, hedef yaş grupları, reklam yerleşimleri ve içerik uygunluğu kontrol edildi. Geliştirme/testte test reklam kimlikleri kullanıldı; canlı reklam yapılandırması ayrı doğrulandı.

Bu kontrol listesi yalnızca planlanan üretim özelliklerine göre değil, dağıtılacak gerçek derleme ve Play'de dağıtılan sürümlerin ilgili veri davranışlarına göre doldurulur. Yeni reklam SDK'sı veya yapılandırma değişikliğinde yeniden uygulanır.

## Eksik girdiler

- Fiziksel telefon testi kullanıcı tercihiyle şimdilik ertelendi; güncel derlemenin gerçek cihaz kontrolü ilk testçi geri bildirimlerinde tamamlanacak. Telefon/tablet emülatör kontrolleri mevcut.
- `support@birlevo.com` ve `privacy@birlevo.com` Cloudflare Email Routing kuralları etkin; gerçek e-posta teslimi henüz denenmedi. Gizlilik politikası `https://birlevo.com/privacy/` adresinde yayımlandı ve açıldığı doğrulandı.
- Yükleme anahtarı ve imzalı AAB oluşturuldu; ikinci yerel anahtar kopyası doğrulandı. Bağımsız kurtarma yedeği için anahtar ayrı korumalı depolamaya, şifre parola yöneticisine alınmalı; Windows DPAPI kaydı aynı kullanıcı/bilgisayara bağlıdır.
- Son logoyla 512×512 mağaza simgesi, İngilizce/Türkçe 1024×500 özellik grafikleri ve her dil için beş adet 1080×1920 gerçek telefon ekran görüntüsü hazırlandı. Açıklamalar ve yükleme sırası [UPLOAD.md](../../output/play-store/0.1.0/UPLOAD.md) içindedir; boyut, biçim ve metin sınırı doğrulaması `validation.json` dosyasındadır. Mağaza görsellerinin Console'a yüklenmesi henüz doğrulanmadı. Tablet mağaza görselleri bu pakette hazırlanmadı.
- Play Console'da kimlik müsaitliği, testçi listesi, uygulama içeriği / veri güvenliği formları ve son yükleme.

## Cloudflare destek e-postası planı

5 Ekim 2026'da `birlevo.com` Cloudflare'de etkin olduğu doğrulandı. Email Routing açıldı; `support@birlevo.com` ve `privacy@birlevo.com` mevcut doğrulanmış gelen kutusuna yönlendirildi. Cloudflare MX/SPF/DKIM kayıtlarını oluşturdu. Panelde yönlendirme `ready` ve kurallar etkin görünüyor; gerçek bir gelen e-postanın ulaştığı henüz doğrulanmadı. Email Routing gelen e-postayı yönlendirir; tek başına tam bir posta kutusu veya güvenilir alan adı üzerinden cevap gönderme hizmeti değildir. Cevapları destek adresinden göndermek için hedef posta sağlayıcısının “send as”/SMTP özelliği veya Cloudflare Email Service'in outbound özelliği ayrıca kurulmalıdır.

Aynı gün Cloudflare Pages `birlevo` projesi oluşturuldu; `web/` içeriği yüklendi. `birlevo.com` özel alan adı Pages'e bağlandı ve panelde `Active / SSL enabled` görüldü. Ana sayfa, `/privacy/` ve `/en/privacy/` adresleri tarayıcıda açıldı; iki politika sayfasında son metin ve iletişim bağlantıları doğrulandı. Cloudflare'ın web ziyaret ölçümü sayfaya eklendiği için politika metinleri web ölçümünü ayrıca açıklar.

Gelen destek iletilerini bir Worker'a verip otomatik sınıflandırma daha sonra eklenebilir; ilk sürümde doğrudan doğrulanmış gelen kutusuna yönlendirme yeterlidir.

