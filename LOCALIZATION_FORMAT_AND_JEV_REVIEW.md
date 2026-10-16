# 6 Ekim 2026 — dil, dosya desteği ve Jev incelemesi

Bu belge ilk incelemenin tarihsel kaydıdır. Uygulanan 0.1.8 (9) uyumluluk paketi, yeni metinler ve gerçek cihaz testleri [güncel raporda](LANGUAGE_AND_FILE_COMPATIBILITY_0.1.8.md) tutulur. Aşağıdaki ilk inceleme sayıları 0.1.8 katalog sayılarıyla karıştırılmamalıdır.

Kullanıcı 0.1.7 (8) Alpha'yı Google incelemesine gönderdiğini bildirdi. Onay / testçilere açılma doğrulanmadı; Console yeniden kontrol edilmedi. Gönderilmiş AAB değişmedi; yeni dağıtım paketi üretilmedi.

## Geliştirme aşaması

Erken Android test sürümü çalışıyor: JPEG / PNG / durağan WebP dönüştürme ve sıkıştırma; SDR H.264 / isteğe bağlı AAC MP4 kesme ve sıkıştırma; yerel sunum taslağı; sunum / fotoğraflardan PDF; işlenen görseli sunum / fotoğraf PDF aracına aktarım; çıktı açma / paylaşma. Gerçek boyut, sonuca özel büyük kopya onayı ve orijinal koruma mevcut. Yuvarlak UI, koyu mod ve açılış geçişi hazır.

Önceki sıradaki ürün işi PDF birleştirme / bölme / sıralama için belge altyapısıydı. Kullanıcının dil ve dosya açma bildirimiyle ilk sıraya uyumluluk ve kullanılabilirlik kontrolü alındı. Erken sürüm genel içerik stüdyosu hedefinin tamamı değildir.

## Dil kontrolü

22 yerelleştirmede 136 arayüz anahtarı mevcut (`app_name` ile 137). Eksik / boş / yinelenen anahtar veya kodun kullanıp katalogda bulunmayan anahtar yok. Üreticinin `--check` kontrolü geçti. Bu, anlam kontrolü veya uzman onayı değildir.

Aynı İngilizce metin tek başına eksik çeviri değildir. `compression_hint` yalnız format, sayı ve birimlerden oluşur. Almanca `Text`, `Original`, `Warm` ve Endonezce `Edit video` doğal karşılık olabilir; otomatik değiştirilmedi. UI'da bazı sayısal birimler doğrudan `px` / `s` yazılmış; sayı / birim yerelleştirmesi ayrı iştir.

Jev 21 hedef yerelleştirmeden dört güvenlik / oturum uyarısı ve dört kısa etiketle 88 örnek aldı. 87 geçerli anlam karşılaştırmasının tamamını `preserved` seçti; 38 örneği açıklık veya düşük güven nedeniyle insan kontrolüne bıraktı. Bir Hintçe uyarı yanıtı doğrulama kontrolünden geçmedi. Bütün 2.856 katalog hücresinin anlam kontrolü yapılmış sayılmaz.

Svahili / Hausa WebP açıklamaları ve Rumence Okyanus etiketi düşük açıklık puanıyla seçildi; düşük confidence nedeniyle doğrulanmış hata olarak sayılmadı. Uzun teknik uyarıları sadeleştirme adayları mevcut. Proje / dosya adı ve kullanıcının slayt metni dil değişince çevrilmez. Sistem dosya seçicisi / harici görüntüleyici Birlevo kataloğuyla yönetilmez.

Kullanıcıya eksik görünen dil / ekran ve açılamayan dosya uzantısı soruldu. Somut örnek geldiğinde desteklenen akış hatası ile yeni özellik ihtiyacı ayrılacak.

## Gerçek dosya desteği

| Tür | Mevcut kullanım / çıktı | Sınır |
|---|---|---|
| JPEG / JPG, PNG, durağan WebP | Görsel aracı, sunum, fotoğraf PDF; JPEG / PNG / kayıplı WebP çıktısı | Animasyon yok; metadata / renk profili korunmaz; JPEG şeffaflığı beyaza dönüşür |
| MP4 | Kesme / sıkıştırma / oynatma; H.264 MP4, varsa AAC çıktı | SDR tek H.264 görüntü ve en çok bir AAC ses; HEVC, HDR ve ek izler reddedilir |
| PDF | Uygulamanın ürettiği PDF önizlenir / kaydedilir / harici uygulamada açılır | Harici PDF içe alma, genel okuyucu ve sayfa işlemleri yok |
| HEIC / HEIF, GIF, BMP, TIFF, SVG | Henüz yok | Decoder / animasyon / vektör / çok sayfalı içerik ayrı doğrulanmalı |
| MOV, MKV, WebM, 3GP | Henüz yok | Kapsayıcı ile kodek ayrı değerlendirilir |
| MP3, WAV, FLAC, OGG vb. | Bağımsız ses aracı yok | Okuma / oynatma ve dönüştürme ayrı işler |
| TXT, CSV, DOCX, XLSX, PPTX, ODF | Genel belge okuyucu / içe aktarım yok | Salt okuma, düzenleme ve PDF dönüşümü ayrı |
| ZIP, 7Z, RAR | Arşiv aracı yok | Lisans, yol taşması ve açılma boyutu güvenliği gerekli |

Seçici görselde üç MIME türü, videoda `video/mp4` ile sınırlı. İçerik motorlarda ayrıca kontrol edilir; uzantı destek kanıtı değildir. Manifestte genel `ACTION_VIEW` / `ACTION_SEND` girişi yok. Dosya yöneticisinden Birlevo ile her dosyayı açabilme henüz gerçek yetenek değil. Kanıt: `ImageFormats.kt`, `VideoCompressor.inspect`, `SlideImages.validate`, `AndroidManifest.xml`.

## Native sembol uyarısı

Release'e `ndk.debugSymbolLevel = "FULL"` eklendi. AGP metadata çıkarma görevi yeniden çalıştırıldı ve başarılı tamamlandı; dört ABI'de `libandroidx.graphics.path.so` için metadata'nın zaten çıkarılmış olduğunu bildirdi.

Bağımlılık `androidx.graphics:graphics-path:1.0.1`. AAB kütüphanelerinde `.symtab` / `.debug_*` yok; yerel AAR sembol paketi içermiyor. Doğru build kimliğiyle eşleşen sembol ZIP'i üretilemedi. **Play uyarısı tamamen giderilmiş sayılmıyor.** Dinamik sembolleri debug sembolü diye paketlemek veya başka derlemenin sembollerini yüklemek doğru olmaz.

Mevcut sembolleri sonraki release'lere ekleyecek ayar hazır. Tam çözüm için sağlayıcının bu binary'lerle eşleşen sembolleri veya ileride doğrulanmış sembollü bağımlılık derlemesi gerekir. Uyarıyı gizlemek için uygulama yeteneği / Compose bağımlılığı kaldırılmadı. AAB SHA256 değişmedi: `1d929520c3331d6f13b6f2d81b9b425f5ddc0585e6d638c08afd75d79a8c4c13`.

[Android'ın açıklaması](https://developer.android.com/build/include-native-symbols). Kanıt: `output/development/20261006-native-symbols.log`.

## Jev sonuçları

Yalnız seçilmiş UI / çeviri metni, anonim iki bildirim ve 18 risk senaryosu gönderildi. Kullanıcı medyası, bütün kaynak kod, anahtar ve ham cihaz logları gönderilmedi. Jev Android'e eklenmedi.

| Profil | Seçilen | Geçerli yanıt | İnsan incelemesi |
|---|---:|---:|---:|
| Çeviri | 88 | 87 | 38 |
| İngilizce açıklamalar | 8 | 8 | 2 |
| Test riskleri | 18 | 17 | 13 |
| Geri bildirim | 2 | 2 | 2 |
| Toplam | 116 | 114 | 55 |

106 HTTP denemesi / 10 önbellek yanıtı; 232.037 gönderim baytı; başarılı yanıtlarda 79.851 girdi / 6.152 çıktı tokenı. Dolar maliyeti sorgulanmadı; başarısız çağrılar ücretlenmiş olabilir. İki geçersiz dağılım (Hintçe kopya uyarısı / eski taslak yükseltme senaryosu) reddedildi. Her tur hatada durdu; sonraki turlar elle yalnız denenmemiş maddelerle sürdürüldü. Başarısız maddeler tekrar çağrılmadı.

Sekiz İngilizce açıklamanın tamamı desteklenen vaat seçildi. Fotoğraf PDF / PNG açıklamaları açıklık / güven için incelemeye girdi. Orijinal koruma, büyük kopya onayı, bozuk medya / sağlayıcı, arka plan işi ve paylaşım senaryolarında kritik risk seçildi. **Bunlar Android testi yürütme veya kodda yeni hata bulma değildir.**

İnsan kararında modelin düşük öncelikleri izlenmedi: proje görselini kaybetme kritik kalır; dil değiştirme / eylemlere erişim kozmetik sayılmaz; eski taslak güncelleme testi geçersiz yanıt nedeniyle atlanamaz. Reklam ve native risklerine `uncertain` dedi; gerçek teknik / politika kontrolleri ayrı kalır.

Ham raporlar `output/development/jev/20261006-release-review*/`; birleşik sonuç `20261006-release-review/combined-summary.json`. `tools/jev/release_audit.py` tekrar kullanılabilir; varsayılan çevrimdışı, API için açık `--live`. Kaynak hash'i her canlı tur boyunca aynı kaldı.

## Güncel geliştirme sırası

1. **Uyumluluk ve dil:** Somut örnekleri yeniden üret; işlem bazlı destek listesi, dosya / gerçek içerik / cihaz koşulunun ortak kaydı ve anlaşılır ret nedeni sun. İçe alma / önizleme / dönüştürme / çıktı ayrı gösterilir. Uyarıları sadeleştirirken kalite / kayıp bilgisi korunur.
2. **Harici PDF okuyucu / sayfa araçları:** Açma, parola / bozuk / büyük belge; sonra birleştirme / bölme / sıralama / döndürme. PDF sayfasını görsel / slayta aktarma. İmza / şifreleme kaybı açıklanır.
3. **Telefon medyası:** HEIC / HEIF, durağan GIF / BMP ve uygun MOV / HEVC için teknik / cihaz denemeleri. Animasyon veya çok sayfalı dosyayı tek kareye indirme sessiz olmaz. Doğrulamadan destek vaadi verilmez.
4. **Okuyucu genişletme:** TXT / CSV; sonra DOCX / PPTX / XLSX salt okuma için lisans, sadakat, APK ve bellek denemesi. Office dönüşümü ayrı ileri hedef. Ses / ZIP ayrı motorlar.
5. **Oturum / donanım:** Kalıcı fotoğraf PDF / video işleri; büyük taslak Bundle riski; 100 sayfa / 4096 bellek ölçümü, düşük depolama, eski Android / düşük donanım. Kalite / çıktı seçenekleri azaltılmaz.
6. **Video / sunum:** Çok klip, 9:16 kadraj, metin / müzik / temel renk; şablon ve sunum modu. Araçlar arası aktarım birlikte geliştirilir.
7. **Reklam:** Test reklamları, izin, doğal görev geçişi, sıklık sınırı. SDK eklenen sürümde reklam / reklam kimliği / veri güvenliği / gizlilik beyanları birlikte güncellenir. Şu an reklam SDK'sı yok.

İlk somut ürün işi **dil ve dosya uyumluluğunu doğru ve görünür hâle getirmek**; sonraki paket **harici PDF okuyucu / sayfa araçları**. Desteklenen işlevde testçi hatası bu sıranın önüne geçer. Geniş format hedefi korunur.

## Yerel doğrulama

20 Android birim testi: 0 hata / başarısızlık. `lintRelease`: 0 hata / 29 uyarı. Jev aracının 20 çevrimdışı testi ve 22 dil kaynak kontrolü geçti. Bu tur yeni fiziksel cihaz / emülatör UI testi yapılmadı; önceki QA tarihsel korunur. Native ayarı metadata göreviyle doğrulandı; yeni paket üretilmedi.
