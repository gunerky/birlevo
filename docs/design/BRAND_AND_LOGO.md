# Birlevo — ilk marka kararı

Current Google Play artwork: [larger store icon and upload instructions](PLAY_STORE_ICON.md), prepared on 19 October 2026. This store-only asset gives the mark more space than the launcher-safe version documented below; it does not change Android resources or existing signed builds.

**Ad:** Birlevo. “Birleştir” fikrini çağrıştıran kısa bir çalışma adı; dosya, video ve sunum işlerinin tek alanda buluşmasına uygun. Türkçe dahil tüm 22 arayüz dilinde aynı marka adı gösterilir. Adın hukuki uygunluğu ve Play'deki paket kimliği ilk yüklemeden önce ayrıca doğrulanmalıdır.

**Uygulama simgesi:** Koyu lacivert zeminde beyaz dosya sayfası, merkezde oynatma üçgeni, turkuaz sayfa kıvrımı ve mercan renkli alt şerit. Dosya işleme, sunum ve videoyu bir simgede anlatır. Logo içinde metin veya başka bir uygulamanın adı bulunmaz. Android 13 ve üzeri temalı simge için tek renkli sürüm eklidir.

**Renkler:** Lacivert `#0A1B60`; turkuaz ve mercan vurgular logo içindedir. Bu palet mevcut uygulama ekranlarına henüz genel tema olarak uygulanmadı; kapalı testte önce simgenin okunurluğu değerlendirilecek.

**Kaynak:** [birlevo_logo.png](assets/birlevo_logo.png). Yerel görsel üretim aracına verilen istem: “Kare Android simgesi; dosya sayfası ile oynatma üçgenini birleştiren yalın geometrik işaret; koyu lacivert zemin, beyaz ana şekil, turkuaz ve mercan vurgu; yazısız, gölgesiz, küçük boyutta okunaklı.” Üretim için yerleşik imagegen kullanıldı. Uygulama içindeki eski vektör simge yerine bu görsel ve eşlenmiş tek renkli Android vektörü bağlandı.

The original artwork is retained here as a design source. The installed launcher uses `birlevo_logo_safe.png`; the unused original is no longer packaged as an Android drawable. The launcher resource and its stable name remain unchanged.

**Güvenli simge boşluğu:** [birlevo_logo_safe.png](../../app/src/main/res/drawable-nodpi/birlevo_logo_safe.png), kaynak görselin yerleşik imagegen aracıyla düzenlenmiş sürümüdür. İstem: “Tam kare tuvali ve lacivert arka planı koru; yalnızca merkezdeki işareti mevcut genişlik ve yüksekliğinin %72’sine indir, merkezde tut; logo şekillerini ve renklerini koru; çerçeve veya yeni arka plan ekleme.” Android kaynaklarında görselin tamamına uygulanan önceki kenar boşlukları kaldırıldı. Arka plan tuvali doldurur; yalnızca işaret küçültülerek maske içinde kalır. Orijinal kaynak korundu. Tek renkli sürüm de %72 ölçeğe indirildi.

Play mağaza simgesi [app-icon-512.png](../../output/play-store/0.1.0/app-icon-512.png) olarak dışa aktarıldı. Mevcut güvenli logo tasarımı ve arka plan korunarak 512×512 RGBA PNG oluşturuldu; dosya 1 MB altında ve görsel kontrolü tamamlandı. İngilizce/Türkçe özellik grafikleri ve gerçek emülatör ekran görüntüleri [mağaza yükleme yönergesinde](../../output/play-store/0.1.0/UPLOAD.md) listelenmiştir.
