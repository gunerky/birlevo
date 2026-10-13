# İki video klibini kesme ve birleştirme

7 Ekim 2026 — geliştirme kodu. Yeni sürüm numarası veya imzalı APK / AAB oluşturulmadı; yayımlanmış 0.1.9 (10) dosyaları değişmedi.

## Kullanım ve sonuç

Video aracında bir kaynak seçilir; ikinci kaynak eklenebilir. İki klibin başlangıç / bitiş aralıkları ayrı seçilir ve oynatılabilir. Sıra değiştirilebilir, bir klip kaldırılabilir. Seçilen aralıklar ardışık olarak tek videoya yazılır. Mevcut MP4 H.264 / AAC, MP4 HEVC / AAC ve WebM VP8 / Opus hedefleri ile kalite / çözünürlük seçenekleri korunur; hedef cihazın encoder desteği gereklidir.

Çıktı tuvali ilk klibin yönünü izler. Diğer klip görüntüyü kesmeden bu tuvale sığdırılır; gerekiyorsa siyah kenarlar oluşur. Sıralamayı değiştirmek bu nedenle tuvali de değiştirebilir. Birleştirmede ses 48 kHz'e ve sesli kaynaklardaki en yüksek kanal sayısına eşlenir. Mono → stereo iki kanalda aynı sesi kullanır; sessiz kliplerin süresi sessiz kalır. İki sessiz kaynakta ses parçası oluşturulmaz. Bu davranış arayüzde açıklanır.

Her kaynak mevcut 512 MiB / 10 dakika sınırındadır. İki kaynak seçildikten sonra toplam seçilmiş süre en fazla 10 dakikadır; daha uzun seçim taslakta korunur ve kullanıcı süreyi kesmeden üretim başlayamaz. Sonraki geliştirmede [seçilebilir kadraj ve hız](VIDEO_KADRAJ_VE_HIZ.md) eklendi. Katmanlar, ikiden fazla kaynak ve ortak zaman çizelgesi henüz eklenmedi. Farklı çok kanallı surround profilleri otomatik stereo'ya düşürülmez; uyumsuz eşleme açıkça kullanılamaz olarak gösterilir. Önizleme için kullanılan küçük poster nihai çözünürlüğü değiştirmez.

## Kayıt, boyut ve iptal

Kaynaklar özel çalışma dosyalarına kopyalanır. Sıra / aralıklar / ayarlar atomik oturumda tutulur; önceki tek kaynaklı V1 isteğinin JSON baytları ve iş parmak izi korunur. İki kaynak V2 kaydı kullanır. Eksik aralık, tekrar edilmiş kaynak veya geçersiz klip sayısı kabul edilmez. Tamamlanmış sonuç yeni ViewModel / süreçte geri yüklenirken gerçek süre, tuval, codec, ses hızı ve kanallar tekrar doğrulanır.

Kaynak kaldırma / değiştirme ancak yeni kayıt kalıcı olduğunda eski özel dosyayı emekliye ayırır. Kayıt hatası eski kaynakları saklar. İptal çalışma çıktısını temizler ve her iki kaynağı korur. UI'a teslim edilmiş poster zorla recycle edilmez.

Boyut kazancı gerçek çıktı baytları ile iki gerçek kaynak dosyasının toplam baytı karşılaştırılarak gösterilir; bu, kesilen sürenin kazancını da içerir ve encoder verimliliği ölçüsü değildir. Eşit / daha büyük dosya normal sıkıştırma kaydıyla kaydedilmez, açılmaz, paylaşılmaz veya ses aracına aktarılmaz; o sonuç dosyasına özel düzenleme kopyası onayı gerekir. Seçim / ayar / sıra değişince onay sıfırlanır. Her iki orijinal URI, gecikmiş dosya seçici yanıtı ve süreç yeniden oluşturma durumunda da üzerine yazmaya karşı korunur.

## Yapı ve doğrulama

`VideoEdit` / `VideoClip` seçim modelidir. Media3 1.5.1 composition / Presentation / Sonic / kanal eşleme mevcut bağımlılıklardır; yeni SDK veya codec paketi eklenmedi. Her iki kaynak bir video listesi olarak akışta işlenir; tüm video RAM'e veya ara ham kare dosyasına alınmaz. Kaynak / sıra / profil değişmeden yalnız kesme sürgüsü değiştiğinde cihaz encoder listesi yeniden taranmaz. Birleştirme kodlama gerektirir. Eşzamanlı emülatör yükünde gözlenen Media3 21 saniyelik çıktı bekleme hatası nedeniyle muxer boşta bekleme sınırı 120 saniyeye çıkarıldı; iptal ve toplam 20 dakikalık iş sınırı korunur. Bu, düşük donanımda başarı / süre / pil / ısınma garantisi değildir.

QA komutu: `./tools/testing/video-edits-qa.ps1`; yalnız özel `ofice_qa` / `ofice_qa_api35` emülatörleri kabul edilir. Raporlar Git dışındaki `output/development/video-edits/` altındadır. Debug testleri gerçek kesilmiş renkleri / tonları, üç hedefi, iki yönü, sessizlik zamanlamasını, iptali, atomik kayıt hatasını, kaynak URI korumasını, tamamlanmış sonuç restorasyonunu ve gerçek UI kontrollerini doğrular. Altı çıktı ayrıca host FFmpeg ile bağımsız çözülür; gerçek kareler, siyah sığdırma kenarları, 440 / 660 Hz ton sırası ve iki saniyelik süre kontrol edilir. FFmpeg yalnız host test aracıdır; uygulamaya gömülmez.

Fiziksel ARM, düşük depolama, 10 dakikalık yüksek çözünürlük yükü ve küçültülmüş release doğrulaması kullanıcı sonraki sürümü istediğinde / ayrı cihaz testinde tamamlanır. 22 dilin anahtar / placeholder kapsamı kontrol edilir; bütün dillerde insan çeviri değerlendirmesi yapıldığı iddia edilmez.

[Play uyarıları, semboller ve web sitesi bakımı](PLAY_UYARILARI_VE_SITE_BAKIMI.md). [Güncel iş sırası](DONUSTURME_KAPSAMI_VE_GUNCEL_IS_SIRASI.md).

## Son doğrulama kaydı

67 JVM testi, birlikte 30 video / oturum / sistem çubuğu debug Android testi ve Android 15'te 7 ortak ekran akışı geçti. Tablet, 320 dp / iki kat yazı ve koyu temada 6 video / sistem çubuğu tekrar testi geçti; ekran görüntüleri incelendi. Altı dosya bağımsız host çözümünde gerçek kesilmiş kare / ton / süre / tuval kontrolünden geçti. 22 dil, 95 runtime artifact, geliştirme aracı izolasyonu ve dört ABI'nin sekiz native dosyası için kaynak / bildirim / 16 KiB LOAD-RELRO kontrolleri geçti. Lint: 0 hata / 39 uyarı. Graphify AST güncellendi: 2128 düğüm / 5600 kenar; 20 Kotlin parser uyarısı nedeniyle ilişkiler kaynak / gerçek testlerle doğrulandı. Ağla Jev çağrısı yapılmadı.
