# Ses kesme ve birleştirme — 7 Ekim 2026

Geliştirme kodunda bir veya iki ses / videonun ses kaynağı için zaman aralığı seçimi, ardışık birleştirme, sıra değiştirme ve kaynak kaldırma vardır. WAV, M4A AAC, OGG Opus, FLAC ve MP3 hedefleri ile mevcut bit hızı seçenekleri korunur. Her kaynak en fazla 512 MiB / 10 dakika; seçilen toplam en fazla 10 dakikadır. Bu adım iki kaynağın ardışık birleşimidir.

Başlangıç / bitiş saniye olarak, en fazla üç ondalık basamakla girilir. Nokta / virgül, Arapça ondalık işareti ve Unicode rakamları kabul edilir. Başlangıç bitişten küçük olmalı ve kaynak süresi içinde kalmalıdır. Tam kaynak bitişi özel bir null uçla kaydedilir. Kaynak oynatma seçilen aralığı kullanır; nihai birleştirilmiş sonuç ayrıca oynatılabilir.

Çıktı örnekleme hızı ve kanal sayısı görünür ve değiştirilebilir. İlk varsayılan, kaynakların en yüksek örnekleme hızı ve kanal sayısıdır. Girdi hızları ile 48 kHz seçilebilir; mono / stereo seçilebilir. Farklı hızlar mevcut Media3 Sonic işlemcisiyle yeniden örneklenir; mono stereo için çoğaltılır, stereo mono için iki kanalın ortalamasına çevrilir. OGG hedefi önceki kapsamındaki gibi 48 kHz üretir. Cihaz / MP3 ayar desteği bu nihai profile göre kontrol edilir; bit hızı, hedef veya çözünürlük kendiliğinden azaltılmaz. Çıktı PCM16'dır; yeniden örnekleme / kanal dönüşümü kaynakla bit düzeyinde eşdeğerlik değildir.

## Motor ve kayıt

`AudioEdit` kaynak listesini ve aralık / profil kurallarını taşır. `AudioEditPcm` kaynakları tek tek mevcut decoder'dan alır; örnek indeksleriyle aralığı keser, sınırlı PCM bloklarını işler ve diske yazar. Bütün dosyayı RAM'e almaz. Aralığın sonuna ulaşınca decoder'ın finally temizliğiyle okuma durur. Başlangıca kadar çözme gerekebilir; sıkıştırılmış uzun kaynağın sonundan kısa seçim anında işlem sözü değildir.

WAV seçilen PCM'yi doğrudan nihai özel dosyaya yazar; ikinci çözme / dosya kopyası yapmaz. Diğer hedefler sınırlı özel geçici WAV üzerinden mevcut beş biçim motorunu kullanır. Böylece kayıplı kaynak iki kez kayıplı kodlanmaz: ilk geçiş çözme / PCM, ikinci geçiş seçilen nihai kodlayıcıdır. Geçici WAV en fazla 512 MiB'dir; 10 dakika / 192 kHz / stereo için yaklaşık 460,8 MB olabilir. İçe alınmış kaynaklar ve nihai çıktı için de disk alanı gerekir. Bu RAM azaltımı, düşük depolamada çalışma garantisi değildir.

İptal / başarısızlık çalışma ve yarım çıktı dosyalarını siler; özgün kaynaklar korunur. Toplam motor süresi 20 dakika ve decoder ilerlemesizlik sınırı 30 saniyedir. Nihai dosya yeniden açılarak gerçek kapsayıcı, hız, kanal ve seçilen toplam süre doğrulanır; kodlayıcı padding'i için mevcut küçük süre toleransı korunur.

V1 tek kaynak / tam aralık / değiştirilmemiş profil kayıtları eski JSON baytlarını ve iş parmak izini korur. V2 iki kaynağın sahip olunan dosyalarını, sırasını, aralıklarını ve seçilen profili kaydeder; eksik / bozuk aralık alanı tam kaynak olarak kabul edilmez. Ortak atomik tek yazıcı her iki kaynağı da tutar. Kaldırılan kaynak ancak yeni kayıt başarıyla yazıldıktan sonra emekliye ayrılır. Sıra, aralık, kaynak, hedef veya profil değişikliği eski sonucu ve o sonuca verilmiş büyük kopya onayını geçersiz kılar.

Kaydetme / açma / paylaşma boyut denetimi iki kaynağın gerçek dosya boyutları toplamını nihai dosyayla karşılaştırır. Birleştirme tek başına sıkıştırma başarısı değildir. Eşit / büyük sonuç açık, sonuca özel kopya onayı gerektirir. Her iki özgün URI ve dosya seçici açıldığındaki eski kaynak URI listesi korunur; gecikmiş seçici cevabı kaldırılmış özgün dosyayı yazamaz veya silemez. Tamamlanmış sonuç yeni ViewModel tarafından geri yüklenir; kesilmiş süreç işi kullanıcı tarafından yeniden başlatılır.

## Doğrulama

Yeniden çalıştırılabilir QA: `tools/testing/audio-edits-qa.ps1`; yalnız `ofice_qa` emülatörü ve debug paketleri kullanılır. `-OnlyEdits` yeni motor / UI ve video aktarımı kontrollerini, varsayılan bütün ses testlerini çalıştırır. Tablet, dar ekran / 2× yazı ve koyu mod tekrarları ayrı sayılır. Yapay Android çıktıları `verify_audio_edit_files.py` ile bağımsız host çözücüde kontrol edilir; host FFmpeg uygulamanın parçası değildir. Ayrıntılı yerel loglar ve ölçümler Git dışında `output/development/audio-edits/` içinde tutulur.

WAV kesme / sıra ve FLAC çözülmüş içerik örnekleri birebir karşılaştırılır. Yedi giriş kapsayıcısında seçilen aralık PCM eşitliği, iki farklı örnekleme hızı / mono-stereo için beş hedef, iptalin iki aşaması, büyük kopya onayı, her iki özgün URI'nin koruması, geçersiz toplam / profil, kaydetme, oynatma, eski kayıt kimliği ve yeni owner geri yükleme sınanır. On Android dosyasının bağımsız çözümü iki tonun doğru sırada kaldığını ve seçilen süreyi doğrular. Fiziksel düşük donanım / eski Android / dolu disk ve yeni akışların R8 yayın doğrulaması sonraki kapsamdır; emülatörden pil / FPS garantisi çıkarılmaz.

Son doğrulama: 65 birim testi, 29 benzersiz hedefli debug Android testi, 10 bağımsız ses dosyası kontrolü geçti. Klavye / durum kaydı düzeltmesinden sonra normal ekran, tablet, dar ekran / 2× yazı ve koyu moddaki dört UI tekrarı ayrıca geçti; ekran görüntüleri incelendi. Yeni aralığın 1 saniyelik oynatma süresi gerçek ExoPlayer ile doğrulandı. İlk regresyonda eski video aktarım testinin henüz oluşturulmamış tembel öğeye kaydırma varsayımı düzeltildi; gerçek liste kaydırmasıyla son 29 test tek çalıştırmada geçti. Lint sıfır hata / 40 mevcut uyarıdır. 22 dilde 13 anahtar / placeholder ve kaynak üretimi kontrol edildi; çeviriler dil uzmanı onayı sayılmaz. Graphify 2052 düğüm / 5377 kenara güncellendi; 20 kısmi Kotlin ayrıştırma uyarısına karşı gerçek Kotlin derlemesi başarılıdır. Güncel debug APK izolasyon / kaynak-lisans ve 95 runtime artifact lisans kontrolleri geçti. Canlı Jev çağrısı yapılmadı.

## Ticari dağıtım ve yayın

Yeni SDK, codec, font, API veya runtime bağımlılığı eklenmedi. Yeniden örnekleme mevcut Apache-2.0 Media3 1.5.1 kapsamındadır; mevcut LAME ve çevrimdışı kaynak paketi korunur. Exact runtime lisans denetimi 95 artifact ile native encoder kapsamını ve hash'lerini doğrular. [Ticari lisans kaydı](TICARI_LISANS_ENVANTERI.md), [MP3 kapsamı](MP3_CIKTI_HEDEFI.md). Patent / bölgesel dağıtım konuları bu kod lisansı kontrolünden ayrıdır.

Sürüm numarası 0.1.9 (10) kaldı. Yeni imzalı APK / AAB üretilmedi; dondurulmuş 0.1.9 paketleri bu geliştirme yeteneklerini içermez. Sonraki ürün adımı iki video klibini sıra / zaman aralığı ve ses / kadraj denetimiyle birleştirme temelidir; daha fazla ses kaynağı ve ortak zaman çizelgesi bunun ardından genişletilebilir.
