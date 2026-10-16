# 0.1.7 (8) — dahili testten kapalı teste geçiş

6 Ekim güncellemesi: Kullanıcı Alpha 0.1.7 (8) sürümünü incelemeye gönderdiğini bildirdi. Gönderim tamamlandı; onay / testçilere açılma henüz doğrulanmadı. Aşağıdaki 5 Ekim hazırlık adımları tarihsel kayıttır, yeniden AAB yüklemek / yeni taslak oluşturmak gerekmez. İnceleme sonrası Play güncellemesi ve geri bildirim e-postası teslimi ayrıca doğrulanmalı. Güncel geliştirme sırası [dil / dosya / Jev raporunda](LOCALIZATION_FORMAT_AND_JEV_REVIEW.md).

5 Ekim 2026. Console'da 0.1.7 (8) dahili test kullanıcıları tarafından kullanılabilir olarak doğrulandı. Alpha'da dağıtılan sürüm hâlâ 0.1.0 (1). 0.1.7 paketi kitaplıktan Alpha taslağına eklendi, İngilizce sürüm notlarıyla kaydedildi; incelemeye gönderilmedi. Mevcut teknik QA ve AAB değişmedi. Kullanıcının isteğiyle tarayıcı işlemleri durduruldu; kalan Console işlemlerini kullanıcı tamamlayacak.

## Hazırlananlar ve şimdi yapacağın işlemler

- Güncel İngilizce ve Türkçe gizlilik sayfaları Cloudflare Pages `birlevo` projesinde yayımlandı. Dağıtım: `ad3ddbcf-cd3d-4c3d-ab15-5307ff4747fe`. `birlevo.com/en/privacy/` ve `birlevo.com/privacy/` bağımsız tarayıcıda giriş gerektirmeden 0.1.7 metniyle açıldı.
- İngilizce kısa ve tam mağaza açıklaması Console'da taslak olarak kaydedildi.
- Eski beş telefon görseli açık düzenleme formundan kaldırıldı; bu son görsel değişikliği kaydedilmedi. Form hâlâ açıksa yenilerini eklemeden kaydetme. Form yeniden açıldığında eski görseller görünüyorsa yalnız telefon görsellerini yeni setle değiştir. Mevcut uygulama simgesi ve özellik grafiği korunur.
- Alpha'da Google Grubu `testers-community@googlegroups.com`, geri bildirim `support@birlevo.com`, 178 ülke/bölge kayıtlı olarak görüldü. Gerçek destek e-postası teslimi ve Play'den cihaz güncellemesi ayrıca denenmeli.

1. **Mağaza girişleri → Varsayılan girişi düzenle → Telefon ekran görüntüleri** bölümüne `output/play-store/0.1.7/` içindeki numaralı İngilizce PNG dosyalarını sırayla yükle. `UPLOAD.md` dosyasında sıra yazılıdır. Logo / özellik grafiğini yeniden yüklemek gerekmiyor.
2. **İleri → Yorum** ekranında metinleri ve görselleri kontrol et; mevcut yapay zekâ öğe beyanını değiştirme. **Kaydet** ile mağaza değişikliğini yayın özetine aktar. Yalnız **Taslak olarak kaydet** kullanılırsa incelemeye hazır değişiklik oluşmamış olabilir.
3. **Kapalı test → Alpha → Sürümler** altında mevcut **8 (0.1.7) taslağını düzenle**. Yeni bir sürüm oluşturma veya AAB'yi yeniden yükleme. Paket ve İngilizce sürüm notları hazır.
4. **İleri / Önizle ve onayla** ekranında hataları kontrol et; ardından Console'un gösterdiği **Kaydet / Yayın özetine git** adımını tamamla.
5. **Yayın özeti** bölümünde yalnız hazırladığın mağaza değişikliği ve Alpha **0.1.7 (8)** sürümünün bulunduğunu kontrol et. Hata yoksa **İncelemeye gönder**. İncelemeye gönderilmesi, onaylanması ve testçilere açılması ayrı aşamalardır.
6. Testçilere aynı mevcut katılım bağlantısını ver: `https://play.google.com/apps/testing/studio.birlevo.android`. Gruba üyelik ve doğru Google hesabı gerekir. Yeni paket onaylanıp dağıtıldığında uygulama bilgisi **0.1.7** olmalı. Uygulamayı kaldırıp taslaklarını silmelerini isteme.

## Önce tamamlanacaklar

- [ ] Dahili test sürümünü Play üzerinden güncelleyip JPEG/PNG/WebP işlemi, MP4 kesme, sunum PDF'si ve fotoğraf PDF'sini kaydet / aç / paylaş ile dene. Varsa eski sunum taslağını aç. Test için uygulama verilerini silme. Kritik çökme / dosya kaybı / yanlış çıktı varsa önce düzelt; böyle bir kod değişikliği yeni, daha yüksek sürüm koduyla paketlenir.
- [x] 0.1.7 gizlilik metinleri yayımlandı ve İngilizce / Türkçe adresler bağımsız tarayıcıda doğrulandı. Önceki otomatik HTTP kontrolündeki 403 sonucu tarayıcı doğrulamasının yerine kullanılmaz.
- [ ] Varsayılan İngilizce mağaza girişindeki kısa ve tam açıklamayı aşağıdaki 0.1.7 metinleriyle güncelle. Yeni biçim seçimi ve fotoğraf-PDF ekranının gerçek telefon görüntülerini hazırla / ekle. Önceki logo ve özellik grafiği kullanılabilir; güncel olmayan özellik ekranları kontrol edilir. Türkçe mağaza girişi ve tablet görselleri önceki kullanıcı kararıyla sonraya bırakıldı.
- [ ] Uygulama içeriği beyanları yüklenen 0.1.7 ile tutarlı: hesap / kısıtlı erişim yok; reklam yok; reklam kimliği yok; uygulama veya SDK kaynaklı açıklanması zorunlu toplama / paylaşma yok. Sistem dosya seçicisiyle cihazda işlem ve kullanıcının seçtiği paylaşım akışı var. Jev bilgisayardaki geliştirme aracıdır; bu AAB'nin veri davranışını değiştirmez. Reklam eklenen ileriki sürümde bu beyanlar yeniden incelenir.
- [ ] Console'daki kurulum görevleri, hedef kitle, içerik derecelendirmesi, kategori ve iletişim bilgileri tamam. Geri bildirim için `support@birlevo.com` adresinden gelen iletinin gerçekten ulaştığını dene.

İngilizce mağaza metinleri:

- [Kısa açıklama](output/play-store/0.1.7/short-description-en-US.txt)
- [Tam açıklama](output/play-store/0.1.7/full-description-en-US.txt)
- [İngilizce sürüm notu — etiketli](output/releases/0.1.7/release-notes-en-US.txt)

## Console'da kapalı test sürümünü oluştur

1. **Birlevo → Test etme ve yayınlama → Test → Kapalı test → Alpha → Kanalı yönet** yolunu aç. Ülkeler/bölgeler sekmesinde testçilerinin ülkelerini doğrula.
2. **Test kullanıcıları** sekmesindeki e-posta listesi veya Google Grubu doğru olsun. Google Grubu kullanılıyorsa kişiler önce gruba üye olmalı. Geri bildirim adresi `support@birlevo.com` olsun; kaydet.
3. **Sürümler → Yeni sürüm oluştur → Kitaplıktan ekle** ile zaten yüklediğin **0.1.7, sürüm kodu 8** paketini seç. Dahili sürüm sayfasında yükseltme menüsü görünüyorsa kapalı test / Alpha hedefini seçmek de bir kısa yoldur; menü yoksa kitaplık yöntemi kullanılır. Aynı AAB'yi yeniden yüklemek veya yalnız kanal değiştiği için yeni sürüm kodu üretmek gerekmez. Yeni kodla derleme yapılırsa o ayrı bir pakettir.
4. Sürüm adını `0.1.7` olarak kullan; etiketli İngilizce sürüm notunu yapıştır. Bu kanalda daha önceki notları yanlışlıkla kullanma.
5. **Sonraki / Önizle ve onayla** ekranını kontrol et. Yayını engelleyen hataları gider. 0.1.7 AAB, R8 eşleme dosyasını `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` içinde taşır. Yerel hata ayıklama sembolü uyarısı çıkarsa ayrıntısını incele; uyarı ile engelleyici hata aynı değildir.
6. Taslağı kaydet / kapalı teste sunma işlemini Console'un gösterdiği biçimde tamamla. **Yayın özeti** sayfasında bekleyen değişiklikler varsa **İncelemeye gönder** işlemini de tamamla. Gönderildi, incelemede ve kullanıma sunuldu durumlarını ayrı kaydet. Yönetilen yayınlama açıksa onay sonrası yayımlama adımını da tamamla.

[Google: sürümü hazırlama, kitaplıktan ekleme ve incelemeye gönderme](https://support.google.com/googleplay/android-developer/answer/9859348?hl=tr).

## Testçilerin katılımı

- [ ] Kapalı test sürümü kullanıma açıldıktan sonra **Alpha → Test kullanıcıları → Test kullanıcıları testinize nasıl katılabilir?** bölümünden o kanalın katılım bağlantısını kopyala. Daha önceki dahili test bağlantısını gönderme; linkin değerini Console'dan al.
- [ ] Testçi doğru Google hesabında, seçilen listede / grupta ve izin verilen ülkede olsun. Dahili teste katılmış kişiler önce dahili testten ayrılıp kapalı test bağlantısından katılsın. Bu, uygulamayı kaldırma veya yerel taslakları silme talimatı değildir.
- [ ] Yalnızca listeye / gruba eklenmek yeterli değildir: kişi bağlantıdan teste katılmayı kabul edip Play üzerinden 0.1.7'yi kurmalı / güncellemeli. Bir testçiyle bütün yolu doğrula.
- [ ] Uygulamanın temel görevleri, kullanılan cihaz / Android sürümü, sonuç boyutu, takılma noktası ve tekrar adımlarını topla. Geri bildirimleri anonimleştirerek Jev'e konu sınıflandırması için verebiliriz; kritik önceliği biz doğrularız.

[Google: kapalı test, gruplar, katılım ve dahili test uygunluğu](https://support.google.com/googleplay/android-developer/answer/9845334?hl=tr).

13 Kasım 2023'ten sonra açılmış kişisel hesap test koşulu hesabına uygulanıyorsa üretim başvurusu için en az 12 kişinin önceki 14 gün boyunca aralıksız **kapalı teste** kayıtlı olması gerekir. Dahili test bu koşulun yerine geçmez. Sayı / sürenin yanında gerçek test ve geri bildirim de önemlidir; Console'daki hesap koşulunu izle. [Google: kişisel hesap test şartları](https://support.google.com/googleplay/android-developer/answer/14151465?hl=tr).

## Kayıt

Console doğrulaması, Alpha taslağı ve gizlilik yayını `output/releases/0.1.7/distribution-status.json` içinde ayrı tutulur. İlk derleme QA dosyasındaki `uploadedToPlay: false` tarihsel derleme anını anlatır; ilk QA / imza kanıtı değiştirilmedi. Kapalı teste gönderme, onay ve testçilere açılma ilerledikçe dağıtım durumu ayrıca güncellenir.
