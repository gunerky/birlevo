# Jev / TypeSafe — yalnızca geliştirme aracı

5 Ekim 2026. Kullanıcının kararı: Jev Android uygulamasına gömülmez. `tools/jev/` bilgisayarda çalışan bağımsız, elle başlatılan değerlendirme aracıdır. Android kaynakları, Gradle bağımlılıkları, uygulama izinleri, sürüm numarası ve mevcut dağıtım paketleri değiştirilmedi. Çalıştırmak için Python 3.10+ ve anahtar saklamak için Windows PowerShell yeterlidir; ek Python SDK'sı kurulmaz.

## Kullanım alanları

| Profil | Gönderilen seçilmiş veri | Dönen öneri |
|---|---|---|
| `translations` | Katalogdaki İngilizce metin, hedef çeviri, dil ve kaynak anahtarı | Anlam korunmuş / değişmiş / belirsiz; açıklık puanı |
| `copy` | İngilizce arayüz metni ve sınırlı desteklenen özellik bilgisi | Açıklık ve desteklenmeyen vaat işareti |
| `feedback` | Elle seçilmiş, anonimleştirilmiş JSONL bildirim metni | Konu ve ilk öncelik önerisi |
| `tests` | Elle seçilmiş test senaryosu ve sınırlı ürün kuralları | Kritik / işlevsel / kozmetik / belirsiz risk |
| `benchmark` | Cevapları önceden belirlenmiş 14 yapay örnek | Örnek bazında eşleşme ve profil bazında ölçüm |

Ölçülmeden Jev'in insan veya mevcut koddan daha iyi olduğu iddia edilmez. [Resmî model belgelerine](https://docs.typesafe.ai/models) göre İngilizce en güçlü eğitim dilidir; diğer diller eşit başarı garantisi taşımaz. 14 örnek yalnız bağlantı ve ilk karar kontrolüdür. Diller için uzman kontrolü gerekir. Confidence dağılımdan türetilir, bir kararın doğru olduğuna dair kanıt değildir. Başlangıç inceleme eşiği 0,75 olarak kodda tanımlıdır; ölçülmüş üretim eşiği değildir.

Jev metin / kod / çeviri üretmez, görüntü / ses / video almaz. Sıkıştırma boyutlarını hesaplamaz, format desteğini kesinleştirmez, güvenlik veya test kapsamını ispatlamaz. Sayısal kontrol, placeholder eşitliği, gerçek çıktının doğruluğu ve çalıştırılan testler kodda kalır. [API sözleşmesi](https://docs.typesafe.ai/api), [model sınırları](https://docs.typesafe.ai/model-jaggedness/jev-1.13), [confidence](https://docs.typesafe.ai/confidence).

## Anahtar

Anahtarı sohbete veya komut satırı argümanına yapıştırmayın. Terminalde, proje kökünden:

```powershell
.\tools\jev\set-key.ps1
```

Giriş maskelidir. Betik anahtarı `%LOCALAPPDATA%\BirlevoDevelopment\typesafe-credential.clixml` dosyasına Windows DPAPI ile şifreli kaydeder; klasör erişimi mevcut kullanıcı ve SYSTEM ile sınırlandırılır. Dosya proje dışında kalır. Aynı Windows kullanıcısı / makinesine bağlıdır; başka makineye taşımak için yeniden tanımlanmalıdır. Yerel hesabın ele geçirilmesine karşı ayrı bir kasa değildir. Betik bağlantı veya ücretli çağrı yapmaz. Var olan kaydı değiştirmek için açıkça `-Replace` gerekir.

Önceden süreçte tanımlanmış `TYPESAFE_API_KEY` de desteklenir; diske kopyalamak için `set-key.ps1 -FromEnvironment` kullanılabilir. Anahtarın değerini gösteren durum / hata / rapor komutu yoktur.

```powershell
python tools/jev/review.py status
```

Bu komut yalnız yerel anahtarın mevcut olup olmadığını ve geliştirme ayrımını kontrol eder; anahtarın API'de geçerli olduğunu söylemez.

## Elle çalıştırma

6 Ekim seçilmiş sürüm incelemesi için `tools/jev/release_audit.py` eklendi. 21 hedef yerelleştirmede dört uyarı / dört ek etiket, sekiz İngilizce açıklama, 18 risk senaryosu ve iki anonim bildirim seçer; 116 madde bütün katalog / kaynak kod taraması değildir. Yerel yapısal kontrol ve gönderilecek maddeler önce çevrimdışı görülebilir:

```powershell
python tools/jev/release_audit.py --output output/development/jev/new-review-plan
python tools/jev/release_audit.py --live --output output/development/jev/new-live-review
```

Çıktı klasörü yeni olmalıdır. Bu geniş inceleme altı küçük turdan oluşur; her tur en çok 30 HTTP denemesi / 256 KiB gönderim, bütün çalışma için en çok 180 deneme / 1,5 MiB üst sınır vardır. Bu bir dolar bütçesi değildir. Bir tur geçersiz yanıt / hata alınca bütün çalışma durur. Yalnız henüz denenmemiş maddeleri elle sürdürmek için yeni çıktı klasörü ve önceki raporlar `--continue-from` ile verilebilir; birden fazla önceki klasör için seçenek tekrarlanır. Başarısız madde otomatik tekrar çağrılmaz. [6 Ekim sonuçları](LOCALIZATION_FORMAT_AND_JEV_REVIEW.md).

Önce ücretsiz / çevrimdışı plan; seçilmiş metinler `plan.json` içinde görülebilir:

```powershell
python tools/jev/review.py translations --limit 25
python tools/jev/review.py copy --limit 25
python tools/jev/review.py benchmark
python tools/jev/review.py feedback --input tools/jev/examples/feedback.jsonl
python tools/jev/review.py tests --input tools/jev/examples/tests.jsonl
```

API çağrısı yalnız `--live` ile yapılır. Anahtar hazır olunca ilk sıra:

```powershell
python tools/jev/review.py benchmark --live --max-requests 16
python tools/jev/review.py copy --live --limit 25
python tools/jev/review.py translations --live --limit 25
```

İlk çeviri turu Türkçe, Almanca, Fransızca, Japonca ve Arapçadan seçilmiş 25 metindir. Riskli boyut / veri / kalite uyarıları önce gelir. `--locales tr-TR,de-DE` ve `--limit` ile elle değiştirilebilir. 22 dilin tamamının değerlendirildiği iddia edilmez; İngilizce `copy` profilinde ele alınır. Sonraki diller küçük turlar ve dil bazında elle doğrulanmış örneklerle genişletilir. API'ye gönderilen veri bütün depo, kullanıcı dosyaları, fotoğraflar, videolar veya ham cihaz kayıtları değildir.

`feedback` ve `tests` girdisi satır başına `{"id":"non-personal-id","text":"selected text"}` biçimindedir. Başka alanlar gönderilmez. Yaygın e-posta, URL, dosya yolu, uzun telefon benzeri sayı ve belirgin secret alanları ayıklanır; isimler ve bütün kişisel veriler için otomatik anonimlik garantisi yoktur. Gerçek testçi kayıtları elle anonimleştirilip seçilmelidir. Araç e-posta veya destek hesabına bağlanmaz.

## Maliyet ve hata sınırları

- Varsayılan her elle başlatılan tur için en fazla 30 HTTP denemesi ve 256 KiB toplam gönderim. Tek payload en fazla 48 KiB. Bunlar dolar veya günlük toplam bütçe değildir; açıkça yükseltilebilir.
- 429 / 529 için en fazla bir kısa tekrar; tekrarlar da deneme / bayt sınırına dahil. Zaman aşımı ücretlendirilmiş olabileceği için otomatik tekrar yoktur. 401 / 422 veya geçersiz yanıt turu durdurur; kalan maddeler değerlendirilmemiş olarak raporlanır.
- Aynı model, soru ve metin için doğrulanmış yanıt yerel hash önbelleğinden kullanılır. Önbellek bozuksa başarılı sonuç sayılmaz. `jev-1.13.0` sürümü sabitlenmiştir; model değişince yeniden ölçüm gerekir.
- Gerçek dönen token sayıları ve önbellek / deneme sayıları kaydedilir. Başarısız çağrıların token ve maliyeti bilinmeyebilir; sağlayıcı paneli nihai maliyet kaynağıdır. Fiyatlar kodda sabitlenmez.
- Varsayılan çevrimdışıdır; Gradle, Git hook'u, zamanlanmış görev veya CI'da otomatik çağrı eklenmez. API cevapları kapalı şemaya göre doğrulanır. Model talimatları çıktı dosyasını veya komut çalıştırmayı yönetemez.

Raporlar ve gönderim planları `output/development/jev/` altında, Git dışında tutulur. Kaynak metinleri ve çevirileri otomatik değiştiren bir yol yoktur. Yarıda kalan turun sonuçları her madde sonrası korunur. Her turda uygulama kaynaklarının aynı kaldığı kontrol edilir.

## Geliştirme ayrımı ve doğrulama

```powershell
python -m unittest discover -s tools/jev -p test_review.py -v
python tools/jev/review.py guard --artifact output/releases/0.1.7/birlevo-0.1.7-8.aab
python tools/localization/generate.py --check
```

`guard`, Android kaynakları ve Gradle'da TypeSafe bağlantısı / anahtar referansı bulunmadığını ve seçilen APK/AAB içinde geliştirme dosyası / bilinen TypeSafe izleri bulunmadığını kontrol eder. Bütün olası gizli anahtarları keşfeden genel bir tarayıcı değildir. Mevcut paket üzerinde okumadır; paket yeniden derlenmez veya değiştirilmez.

Kurulumda 20 çevrimdışı kontrol geçti: API yanıt sözleşmesi, bilinmeyen seçenek reddi, belirsiz karar yönlendirmesi, önbellek, bozuk önbellek, deneme / bayt bütçesi, kısa tekrar / zaman aşımı, sır içermeyen hata, yönlendirme reddi, placeholder, seçilmiş veri, paket ayrımı ve sahte anahtarla Windows şifreleme / çözme.

5 Ekim canlı devamı: Kullanıcı anahtarı proje dışındaki şifreli kayda tanımladı; resmî API bağlantısı doğrulandı. 14 yapay örneğin 13'ü beklenen kararlarla eşleşti; ilk 25 İngilizce metin ve beş hedef dilde 25 çeviri değerlendirildi. Aynı 14 örneğin tekrarı sıfır çağrıyla önbellekten okundu. Bu sonuçlar genel dil doğruluğu veya insandan daha iyi performans iddiası değildir. Öncelik kararlarında insan incelemesi gereken ayrışmalar bulundu. [Sonuçlar ve kullanım kararı](JEV_INITIAL_LIVE_REVIEW.md).
