<p align="center">
  <img src="web/favicon-192.png" alt="Birlevo logosu" width="80" height="80">
</p>

<h1 align="center">Birlevo</h1>

<p align="center"><strong>Dosyalarını dönüştür, düzenle ve yeni içerikler oluştur.</strong></p>
<p align="center">Android 7.0+ · Kotlin &amp; Jetpack Compose · 22 arayüz dili</p>
<p align="center">
  <a href="https://birlevo.com">Web sitesi</a> ·
  <a href="https://birlevo.com/en/privacy/">Gizlilik politikası</a> ·
  <a href="mailto:support@birlevo.com">Destek</a>
</p>

Birlevo; görsel, video, ses, PDF ve belgelerle çalışmak için geliştirilen bir Android uygulamasıdır. Dosya işleme cihazda yapılır; Birlevo hesabı gerekmez. Dosya odaklı arayüz, önizleme, açık/koyu tema ve kayıtlı çalışmalara geri dönme akışlarını bir araya getirir.

[Özellikler](#özellikler) · [Kurulum](#kurulum-ve-debug-derleme) · [Doğrulama](#doğrulama) · [Belgeler](#belgeler)

## Özellikler

| Alan | Yapılabilenler |
| --- | --- |
| **Görsel** | Dönüştürme ve sıkıştırma; JPEG, PNG, WebP ve BMP çıktıları. Desteklenen cihazlarda HEIC, HEIF ve AVIF içe aktarma. |
| **Video** | Kesme, iki klibi birleştirme, kadraj ve hız ayarı; yazı, görsel ve ses katmanları. MP4 ve WebM çıktıları, ses ve kare çıkarma. |
| **Ses** | Kesme, iki kaynağı birleştirme ve dönüştürme. WAV, M4A, OGG/Opus, FLAC ve MP3 çıktıları. |
| **PDF** | Okuma, önizleme, birleştirme, sayfa seçme/sıralama/döndürme; sayfadan görsel ve fotoğraflardan PDF oluşturma. |
| **Metin ve tablo** | TXT, Markdown kaynak metni, CSV ve TSV okuma/dönüştürme; sınırlı DOCX/XLSX içe aktarma ve sade belge çıktıları. |
| **Sunum** | Slayt, tema, düzen, görsel ve konuşmacı notları; tam ekran sunum. PDF, slayt görseli, MP4 ve PPTX dışa aktarma. |
| **Toplu işlem ve ZIP** | Görsel, video, ses ve belge kuyrukları; sonuçlardan ZIP oluşturma, ZIP inceleme ve seçili dosyaları klasöre çıkarma. |

Biçim desteği; gerçek içeriğe, Android sürümüne ve cihazın çözücü/kodlayıcı desteğine bağlıdır. Her dosya her biçime dönüştürülemez. Desteklenen video eşleşmeleri [codec matrisinde](CODEC_MATRISI_VE_VP9_GENISLEMESI.md) açıklanır.

DOCX/XLSX desteği tam Office görünümü veya Excel formül hesaplama motoru değildir. PPTX çıktısında görünümü koruyan ve desteklenen metinlerde düzenlenebilir seçenekler vardır; genel PPTX içe aktarma henüz yoktur. Ayrıntılar: [belge kapsamı](DOCX_XLSX_ARAYUZ_ENTEGRASYONU.md), [PPTX ve RTL](PPTX_RTL_VE_FONT_KAPSAMI.md).

## Dosyalar ve çalışma güvenliği

- Kaynak dosyalar doğrudan değiştirilmez; çıktılar Android dosya seçicisiyle ayrı kaydedilir.
- Sıkıştırma sonucu gerçek dosya boyutuyla değerlendirilir. Eşit veya büyük çıktı, o sonuca özel açık kopya onayı gerektirir; kalite otomatik düşürülmez.
- Proje ve medya çalışma kayıtları atomik olarak tutulur. İptal, kesinti ve yeniden deneme davranışı araca göre doğrulanır; yarım işler kendiliğinden yeniden başlatılmaz.
- Önizlemenin kaynak kullanımı ile nihai çıktı kalitesi ve çözünürlüğü ayrı ele alınır.

## Kaynak kod ve yayın sürümü

Bu README **bulunduğu daldaki geliştirme kapsamını** anlatır. Kaynak koddaki her özellik, daha önce üretilmiş APK/AAB'de veya Play'deki test sürümünde bulunmayabilir.

Son hazırlanmış imzalı paket **0.1.11 / versionCode 12**'dir. Paket kapsamı ve İngilizce/Türkçe notlar [0.1.11 yayın kaydındadır](YAYIN_0.1.11.md). Yeni sürüm numarası, imzalı derleme ve yayın hazırlığı ayrı kullanıcı isteğiyle yapılır; normal geliştirme kontrolleri debug hedefini kullanır.

## Kurulum ve debug derleme

| Gereksinim | Sürüm / kullanım |
| --- | --- |
| JDK | 17 |
| Android SDK | Platform 36; minimum uygulama API seviyesi 24 |
| Android NDK | 27.1.12297006 |
| CMake | 3.22.1 |
| Gradle | Depodaki wrapper kullanılır; ayrıca kurmak gerekmez |
| Python | 3.10+; yerel doğrulama ve geliştirme araçları için |

1. Depoyu klonlayın ve Android Studio'da kök dizini açın.
2. SDK Manager üzerinden gerekli SDK, NDK ve CMake sürümlerini kurun. Java 17 ve SDK konumunu ayarlayın; SDK yolu Android Studio'nun yerel ayarıyla veya terminalde `ANDROID_HOME` üzerinden belirtilebilir.
3. Depo kökünde debug APK oluşturun:

```powershell
.\gradlew.bat assembleDebug
```

Çıktı: `app/build/outputs/apk/debug/app-debug.apk`. macOS/Linux için: `bash gradlew assembleDebug`.

Debug derleme için imza veya Jev/TypeSafe anahtarı gerekmez. Makineye özel SDK ayarları, kimlik bilgileri ve üretilen paketler Git'e alınmaz.

## Doğrulama

Derleme, JVM testleri ve Android lint:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
```

Dil kaynakları ve gerçek çalışma zamanı lisans envanteri:

```powershell
python tools/localization/generate.py --check
.\gradlew.bat exportRuntimeLicenseInventory
python tools/licensing/audit_runtime.py --check
```

GitHub Actions ayrıca depo hijyeni, geliştirme araçlarının çevrimdışı testleri, native kütüphane kontrolleri ve APK izolasyonunu denetler. İmzalı sürüm veya dağıtım üretmez.

Uçtan uca Android testleri ayrı bir **QA emülatöründe** çalıştırılır. Testler veri temizleyebildiği için kişisel çalışmalar bulunan bir ortam kullanılmaz. Arşiv akışı için `tools/testing/archive-flow-qa.ps1` koşucusu ve [bütünleşik regresyon raporu](TOPLU_ZIP_KLASOR_ZINCIRI_VE_REGRESYON.md) başlangıç noktasıdır. PowerShell koşucularındaki yerel SDK/Python yolları ortama göre ayarlanmalıdır. Emülatör ve bağımsız okuyucu kontrolleri, fiziksel cihaz performansı veya genel Microsoft Office uyumluluğu garantisi değildir.

### Çeviri kaynakları

22 dilin kaynak kataloğu `tools/localization/catalog.json` dosyasıdır. Katalog değişince Android kaynaklarını üretin ve denetleyin:

```powershell
python tools/localization/generate.py
python tools/localization/generate.py --check
```

Kontrol anahtarları ve biçim yer tutucularını doğrular; dilsel incelemenin yerini almaz.

## Depo düzeni

```text
app/src/main/          Android uygulaması, medya motorları ve arayüz
app/src/test/          JVM testleri
app/src/androidTest/   Emülatör testleri ve yapay dosya sağlayıcıları
tools/testing/        QA koşucuları ve bağımsız çıktı kontrolleri
tools/localization/   Dil kataloğu ve kaynak üretimi
tools/licensing/      Ticari dağıtım için bağımlılık envanteri
tools/jev/            Elle çalıştırılan geliştirme değerlendirmesi
web/                  Web sitesi ve gizlilik sayfaları
```

`output/`, `graphify-out/`, derleme önbellekleri ve yerel kimlik bilgileri Git dışında tutulur. Jev/TypeSafe ve Graphify geliştirme araçlarıdır; Android uygulamasının çalışma bağımlılığı değildir.

## Lisans ve ticari dağıtım

Birlevo ticari dağıtım ve reklam geliri hedefiyle geliştirilir. Üçüncü taraf SDK, codec ve fontlar bu kullanım kapsamında incelenir; ücretsiz olmaları tek başına yeterli sayılmaz.

[Ticari lisans envanteri](TICARI_LISANS_ENVANTERI.md) bağımlılık kapsamını, [MP3 çıktı kaydı](MP3_CIKTI_HEDEFI.md) LAME/LGPL kaynak, bildirim ve kütüphane değiştirme akışını açıklar. Üçüncü taraf lisansları, uygulamanın tüm kaynak kodu için tek bir açık kaynak lisansı ilanı değildir.

## Belgeler

| Konu | Başlangıç noktası |
| --- | --- |
| Ürün kapsamı ve iş sırası | [Ürün planı](URUN_PLANI.md) · [Dönüştürme kapsamı](DONUSTURME_KAPSAMI_VE_GUNCEL_IS_SIRASI.md) |
| Arayüz ve erişilebilirlik | [Dosya odaklı arayüz](DOSYA_ODAKLI_ARAYUZ.md) · [Erişilebilirlik ve RTL](ERISILEBILIRLIK_VE_RTL.md) |
| Mimari ve performans | [Modülerlik ve performans](MODULERLIK_VE_PERFORMANS.md) |
| Test ve yayın | [Güncel kapalı test listesi](KAPALI_TEST_GUNCEL_KONTROL_LISTESI.md) · [0.1.11 paketi](YAYIN_0.1.11.md) |
| Geliştirme araçları | [Çalışma kuralları](AGENTS.md) · [Git/GitHub düzeni](GITHUB_VE_VERSIYON_KONTROLU.md) · [Jev](JEV_GELISTIRME_ARACI.md) |
| Önceki notlar | [Eski README arşivi](GELISTIRME_GECMISI.md) |

Hata bildirirken uygulama sürümünü, Android sürümünü, dosya türünü ve tekrar adımlarını belirtin. Kişisel dosya veya anahtar yerine mümkünse sorunu gösteren yapay bir örnek kullanın. Destek: [support@birlevo.com](mailto:support@birlevo.com).
