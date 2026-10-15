# Birlevo — reklam gelirli ticari dağıtım lisans incelemesi

7 Ekim 2026. Kullanıcı Birlevo'dan reklam geliri elde edecek. İnceleme, yeni MP3
motorunu ve mevcut gerçek çalışma zamanı bağımlılıklarını kapsar. Bir paketin
ücretsiz veya açık kaynak olması tek başına dağıtım izni sayılmaz.

## Bulgular ve uygulanan koşullar

`releaseRuntimeClasspath` çözümünden **95 ayrı artifact / 95 Maven modülü** çıkarıldı.
92 artifact Apache-2.0, üçü Bouncy Castle'ın izin veren lisansını bildiriyor.
Bu, R8 öncesi muhafazakâr kapsamdır; hangi sınıfın son yayında kaldığına ilişkin
bir sonuç değildir. JUnit / Espresso / test sağlayıcıları ve debug araçları bu
envanterin dışında, test APK'sında kalır. Tam koordinatlar, SHA-256, resmî POM /
parent POM kaynakları ve paketlenmiş bildirimler
[`tools/licensing/runtime-licenses.json`](tools/licensing/runtime-licenses.json) içindedir.

| Bileşen | İncelenen koşul | Birlevo'da karşılığı |
| --- | --- | --- |
| AndroidX / Compose / Media3 / Kotlin / Coroutines / Guava / JSpecify / annotations | Apache-2.0; ticari kullanım ve dağıtım izinli, lisans ve geçerli bildirimler korunmalı | Tam lisans, artifact listesi ve 23 artifact'ın içerdiği lisans metinleri okunabilir uygulama bildiriminde tutulur |
| PDFBox-Android 2.0.27.0 | Apache-2.0 yanında Adobe AFM / CMap / Unicode ve eski PDFBox izin bildirimleri | Önceden eklenmiş tam PDFBox LICENSE / NOTICE ve ek bileşen metinleri korunur; yalnız üst POM'a bakılarak silinmez |
| Bouncy Castle bcprov / bcpkix / bcutil 1.72 | Ticari satış dahil izin veren telif / izin / sorumluluk metni korunmalı | **Eksik bildirim giderildi.** Gerçek bcprov `org/bouncycastle/LICENSE.class` içindeki 2000–2022 telif ve tam izin metni çıkarılıp uygulamaya eklendi |
| LAME 4.0 encoder | LGPL-2.0-or-later; ticari kullanım mümkün, ayrı kaynak / bağlantı / bildirim koşulları var | Upstream'in “or later” hakkıyla dağıtım için LGPL 2.1 seçildi; encoder ayrı `libmp3lame.so`, köprü ayrı `libbirlevo_mp3.so`. Tam kaynak ve tam LGPL uygulamadan çevrimdışı kaydedilir |
| LAME JNI / Java köprüsü | Köprü kaynak lisansı açık olmalı ve yeniden derlenebilir arayüz korunmalı | Köprü LGPL-2.0-or-later olarak kaynak paketinde; Java sınıf adı / native yöntemleri R8 kuralıyla ABI olarak korunur |
| LAME mpglib / frontend / host FFmpeg | Yeni uygulama runtime bağımlılığı olarak kullanılmaz | GPL decoder ve executable frontend derleme kaynak listesinde yoktur. Host FFmpeg yalnız yapay test üretimi / bağımsız kontrol içindir |

LAME kaynak arşivindeki kullanılmayan ek bileşenler kendi orijinal lisanslarını
korur; komple upstream kaynak sunulması onların uygulamada derlendiği anlamına
gelmez. CMake encoder kaynaklarını açıkça listeler; bir klasörün tamamı derlenmez.
Native ELF kontrolü decoder sembollerini ve ayrı dinamik bağlantıyı da denetler.

Mevcut incelenen kod lisanslarında reklam geliri / ticari dağıtım yasağı bulunmadı.
Bu sonuç koşulların korunmasına bağlıdır; yeni paketlere veya her ülkedeki codec
patent izinlerine aktarılmaz. H.264 / HEVC / AAC yolları Android platform
codec'lerini kullanır. Bu kod lisans envanteri bu codec'lerin patent havuzu /
bölgesel kullanım hakları için verilmiş bir onay değildir.

## Kaynak erişimi ve değiştirme

Desteklenen dosyalar → Açık kaynak lisansları → **MP3 kodlayıcı kaynağını kaydet**.
Belge seçicisi `application/zip` ve `birlevo-mp3-sources.zip` kullanır. İnternet,
hesap, GitHub'ın özel reposuna erişim veya kişisel anahtar gerekmez.

Paket: dokunulmamış LAME 4.0 arşivi, orijinal COPYING, seçilen tam LGPL 2.1,
Android CMake ayarı, config, C JNI köprüsü, Java arayüzü ve yeniden derleme /
kütüphane değiştirme kılavuzu. Kütüphane değişikliğine runtime hash / imza engeli
eklenmez. Kullanıcının kendi kullanımı için birleşik işi değiştirmesine ve bu
değişiklikleri hata ayıklamak amacıyla tersine mühendisliğe izin veren bildirim
eklenmiştir. Android kurulum imzası gereği değiştirilmiş APK kullanıcı anahtarıyla
yeniden imzalanır; resmî güncelleme / özel veriler farklı imzaya otomatik taşınmaz.

Debug APK içindeki kaynak baytları mevcut kaynakla eşleşti. Paketten çıkarılan
encoder ve köprü **x86_64 için uygulama / özel repo gerektirmeden yeniden derlendi**;
köprünün `DT_NEEDED` girdisinde `libmp3lame.so` doğrulandı. Release isteğinde
gerçek küçültülmüş APK ile değiştirip yeniden imzalanan kütüphaneyi yükleme denemesi
de yayın doğrulamasına dahildir; bu adım bu debug geliştirmede tamamlanmış sayılmaz.

## Tekrarlanabilir denetim

```
./gradlew.bat exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
python tools/testing/verify_mp3_artifact.py app/build/outputs/apk/debug/app-debug.apk
```

İlk lisans incelemesinde eksik POM'lar yalnız açık `--fetch` ile Google Maven /
Maven Central'dan alındı. Gradle parent POM mirası izlendi. Bildirilmemiş veya
tanınmayan lisans otomatik onaylanmaz. `--check` tam artifact koordinatı / adı /
hash kümesini incelenmiş manifestle karşılaştırır; yeni SDK veya sürüm önce
yeniden incelenir. CI hem bu kapsamı hem native kaynak / bildirim kontrolünü yapar.
Uygulamaya yeni network / reklam SDK'sı eklenmedi.

Reklam SDK'sı seçiminde ticari hizmet şartları ayrıca incelenecek. Entegrasyonda
Play reklam, reklam kimliği, veri güvenliği ve web / uygulama gizlilik metinleri
aynı değişikliğin parçası olarak güncellenecek. Reklam eklenmeden bunlar varmış
gibi beyan edilmez.

Birincil dayanaklar: [LAME ticari kullanım açıklaması](https://lame.sourceforge.io/license.txt),
[GNU LGPL 2.1 tam metni](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt),
[Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0),
[Bouncy Castle 1.72 upstream](https://github.com/bcgit/bc-java/tree/r1rv72).
Paketle eşleşen Bouncy Castle bildirim kaydı
[`tools/licensing/notices/BouncyCastle-1.72.txt`](tools/licensing/notices/BouncyCastle-1.72.txt) içindedir.

## 0.1.10 gerçek yayın paketi

7 Ekim 2026: 95 gerçek release runtime artifact koordinatı / dosya hash’i ve native encoder kapsamı yeniden geçti. Son imzalı R8 APK / AAB’de sekiz LAME / köprü ELF, dört ABI, 16 KiB LOAD / RELRO / ZIP, dinamik bağlantı, GPL decoder sembollerinin yokluğu, tam kaynak ve bildirimler kontrol edildi. Kaynak ZIP’i R8 UI’dan çevrimdışı kaydedildi. Yalnız bu paketten x86_64 yeniden derleme / ayrı müşteri anahtarıyla encoder değiştirme / gerçek R8 ses araçları / bağımsız çözümleme ve resmi APK’yı geri kurma denemesi geçti. Deney kopyası dağıtıma konmaz. [Yayın kaydı](RELEASE_0.1.10.md). Fiziksel 16 KiB / ARM çalışma ve ülke bazlı codec izinleri bu deneyden türetilmez.
