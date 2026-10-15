# Video kadrajı ve hız kontrolü

7 Ekim 2026 — geliştirme kodu. Yeni yayın sürümü oluşturulmadı; dondurulmuş 0.1.9 APK / AAB değişmedi.

## Kullanım ve yetenekler

Bir veya iki video klibi için ilk klibin oranı, 9:16, 16:9 ve kare tuval seçilebilir. Sığdırma tüm görüntüyü korur ve gerektiğinde siyah kenar ekler. Ortadan kırpma tuvali doldurur; kenar içeriği kesilebilir. Hız seçenekleri 0,5× / 0,75× / 1× / 1,25× / 1,5× / 2×; iki klibe aynı hız uygulanır. Sonuç süresi ayrıca gösterilir. Kaynak aralığı önizlemesi orijinal zaman çizelgesinde kalır; kaydetmeden önce gerçek üretilen sonuç oynatılabilir.

Ses hızı mevcut Sonic ile değiştirilir, perde 1 olarak tutulur. Ses kalitesinin tamamen aynı kalacağı sözü verilmez. Sessiz klipler efekt sonrası süreye göre sessizlik üretir; composition seviyesinde hız ikinci kez uygulanmaz. Mevcut MP4 AVC / AAC, HEVC / AAC ve WebM VP8 / Opus seçenekleri, kalite ve çözünürlük kontrolleri korunur. Tuval, seçilen kenar ve ilk kaynağın uzun kenarıyla sınırlanır; 9:16 ve 16:9 tam oran için çift piksel katlarına yuvarlanır. Kullanıcının açık doldurma seçimi içeriği büyütebilir. Kodlama desteği cihazın codec'lerine bağlıdır; kaliteyi otomatik azaltan fallback kullanılmaz.

Toplam seçilen kaynak süresi en fazla 10 dakika kalır; yavaşlatılmış çıktı en fazla 20 dakika olabilir. Çıktı inceleme ve restorasyon yolu kaynak ithalinden ayrı süre sınırı kullanır. Uzun sonuç, gerçek süre / tuval / ses / codec kontrolünden geçer; tüm video belleğe alınmaz.

## Oturum ve boyut koruması

Varsayılan ilk klip / sığdır / 1× ayarları eski V1 ve V2 istek baytlarını korur. Dönüşüm ayarları V3 atomik kayıtta saklanır; eksik veya geçersiz ayar kabul edilmez. Kadraj, sığdırma ya da hız değişikliği encoder uygunluğu önbelleğini ve önceki sonuç / kopya onayını yeniler. Önceki tek kaynaklı ve iki kaynaklı taslaklar açılabilir. FileProvider kimliği / kökleri değiştirilmedi.

Gerçek çıktı baytları gerçek kaynak baytlarının toplamıyla karşılaştırılır. Eşit veya büyük sonuç için o sonuca özel düzenleme kopyası onayı; kaydetme, açma, paylaşma ve araç aktarımında korunur. Kaynak dosyaları değiştirilmez. Yeni SDK / runtime codec eklenmedi; host FFmpeg yalnız bağımsız test çözümüdür.

## Doğrulama ve sınırlar

68 JVM testi geçti; V1 / V2 korunması ve 48 kadraj / sığdırma / hız kombinasyonunun kayıt restorasyonu kontrol edildi. Lint 0 hata / 39 uyarı. 22 dilin anahtar / placeholder kapsamı, 95 runtime artifact + native encoder ticari lisans kapsamı ve debug APK'da Jev izolasyonu geçti. İnsan tarafından 22 dilin tamamı incelenmiş sayılmaz; Jev'e ağ çağrısı yapılmadı.

İki klibin 0,5× ve 2× hızında üç çıktı biçimiyle altı gerçek dışa aktarımı, ses perdesi, renk sırası, kadraj ve sessizlik zamanlaması doğrulandı. Sekiz dosya ayrıca bağımsız host çözümünde kare sayısı / gerçek zaman damgaları / 440–660 Hz tonları / siyah kenarlar / kırpma kontrolünden geçti. 320×180, 2 fps, 10 dakikalık sentetik kaynak 0,5× ile 20 dakikaya dışa aktarıldı ve tamamlanmış çıktı yeniden yüklendi (QA testi 104,891 saniye). Bu süre fiziksel cihaz hız garantisi değildir. 32×32 / 1 fps ilk sentetik örnek emülatör codec yolunda ilerlemedi; bu aşırı küçük / düşük fps yolu doğrulanmış destek sayılmaz. Denenen kapasite ipuçları bu sonucu çözmediğinden otomatik çözünürlük veya kare artırımı uygulanmadı. Kesirli fps'nin probe sırasında sıfıra yuvarlanması ayrıca kaldırıldı.

Güncel cihaz / tablet / 320 dp iki kat yazı / koyu tema tekrarlarının raporları `output/development/video-canvas-speed/` altında tutulur. Tekrarlanabilir QA: `tools/testing/video-transforms-qa.ps1`; yalnız özel QA emülatörleri kabul edilir. Fiziksel ARM, düşük bellek, yüksek çözünürlüklü uzun kaynaklar, pil / ısınma ve sonraki R8 release doğrulaması ayrı bekleyen işlerdir.

Sıradaki ürün işi yazı / kullanıcı sesi / görsel katmanlarıdır; bu pakette katman veya ortak zaman çizelgesi eklenmedi.

Son tekrar: uzun süre testi ayrı geçti; dört hedefli cihaz testi birlikte geçti (159,117 saniye). Tablet, 320 dp / iki kat yazı ve koyu temada üç UI tekrarı geçti; ekran görüntüleri incelendi. Sekiz nihai çıktı yeniden bağımsız çözüldü. Graphify güncel AST: 2169 düğüm / 5696 kenar; 20 Kotlin parser uyarısı nedeniyle ilişki sonuçları kaynak ve derleme / gerçek testlerle doğrulandı.

## 0.1.10 yayın doğrulaması — 7 Ekim

Kullanıcının isteğiyle imzalı / küçültülmüş 0.1.10 (11) hazırlandı. Aynı son APK’da 15 R8 iş akışı geçti. İki klip / sıra / 9:16 merkez kırpma / 2× hız / gerçek kare ve süre / tamamlanmış sonuç restorasyonu; tablet, büyük yazı ve koyu modda tekrarlandı. Resmi video bağımsız çözüldü: mavi–sarı–kırmızı–yeşil sıra, 660–440 Hz perde, üç saniyelik çıktı ve 48 kHz stereo doğrulandı. Bu R8 örneği tüm 48 seçenek veya 20 dakikalık yüksek çözünürlüklü fiziksel cihaz denemesi olarak genellenmez; önceki debug kanıtları ayrı kalır.

Eski sonuç geri yüklenirken yeni dosya hatasının silinmesi bulundu ve düzeltildi; hata dosya seçimi yanında korunur. Sonraki kaydetme mesajı önceki import nedeninden ayrılır. [Paket ve kalan sınırlar](RELEASE_0.1.10.md).
