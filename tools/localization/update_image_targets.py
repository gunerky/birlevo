"""Idempotently add BMP losses and generalize PDF image-target labels in all locales."""
import json
from pathlib import Path

TEXT = {
    'en-US': ('BMP stores uncompressed 24-bit color. Transparent areas become white; metadata is removed. Resizing may reduce detail and the file can be larger.', 'BMP has no quality setting. It stores uncompressed pixels; adjust dimensions to change size.'),
    'tr-TR': ('BMP, sıkıştırılmamış 24 bit renk saklar. Saydam alanlar beyaz olur; meta veriler kaldırılır. Boyutlandırma ayrıntıyı azaltabilir ve dosya büyüyebilir.', 'BMP kalite ayarı içermez. Pikselleri sıkıştırmadan saklar; boyutu değiştirmek için boyutları ayarlayın.'),
    'es': ('BMP guarda color de 24 bits sin compresión. Las zonas transparentes se vuelven blancas; se eliminan los metadatos. Redimensionar puede reducir el detalle y aumentar el archivo.', 'BMP no tiene ajuste de calidad. Guarda píxeles sin compresión; cambia las dimensiones para ajustar el tamaño.'),
    'pt-BR': ('BMP armazena cores de 24 bits sem compressão. Áreas transparentes ficam brancas; os metadados são removidos. Redimensionar pode reduzir detalhes e aumentar o arquivo.', 'BMP não tem ajuste de qualidade. Armazena pixels sem compressão; ajuste as dimensões para mudar o tamanho.'),
    'id': ('BMP menyimpan warna 24 bit tanpa kompresi. Area transparan menjadi putih; metadata dihapus. Pengubahan dimensi dapat mengurangi detail dan memperbesar berkas.', 'BMP tidak memiliki pengaturan kualitas. Piksel disimpan tanpa kompresi; ubah dimensi untuk mengatur ukuran.'),
    'hi': ('BMP बिना संपीड़न के 24-बिट रंग रखता है। पारदर्शी हिस्से सफेद हो जाते हैं; मेटाडेटा हटा दिया जाता है। आकार बदलने से विवरण कम और फ़ाइल बड़ी हो सकती है।', 'BMP में गुणवत्ता सेटिंग नहीं है। पिक्सेल बिना संपीड़न रखे जाते हैं; फ़ाइल का आकार बदलने के लिए आयाम बदलें।'),
    'ar': ('يخزّن BMP ألوانًا غير مضغوطة بدقة 24 بت. تصبح المناطق الشفافة بيضاء وتُزال البيانات الوصفية. قد يقلّل تغيير الأبعاد التفاصيل ويزيد حجم الملف.', 'لا يتضمن BMP إعدادًا للجودة. تُحفظ البكسلات دون ضغط؛ غيّر الأبعاد لضبط حجم الملف.'),
    'ru': ('BMP хранит несжатый 24-битный цвет. Прозрачные области становятся белыми; метаданные удаляются. Изменение размеров может уменьшить детализацию и увеличить файл.', 'В BMP нет настройки качества. Пиксели хранятся без сжатия; измените размеры изображения для изменения размера файла.'),
    'ja-JP': ('BMPは非圧縮の24ビットカラーを保存します。透明部分は白になり、メタデータは削除されます。サイズ変更で細部が失われ、ファイルが大きくなることがあります。', 'BMPに品質設定はありません。非圧縮のピクセルを保存します。ファイルサイズを変えるには画像の寸法を調整してください。'),
    'fr-FR': ('BMP stocke des couleurs sur 24 bits sans compression. Les zones transparentes deviennent blanches ; les métadonnées sont supprimées. Le redimensionnement peut réduire les détails et agrandir le fichier.', 'BMP ne propose pas de réglage de qualité. Les pixels sont stockés sans compression ; ajustez les dimensions pour changer la taille.'),
    'de-DE': ('BMP speichert unkomprimierte 24-Bit-Farben. Transparente Bereiche werden weiß; Metadaten werden entfernt. Skalieren kann Details verringern und die Datei vergrößern.', 'BMP hat keine Qualitätseinstellung. Pixel werden unkomprimiert gespeichert; ändern Sie die Abmessungen, um die Dateigröße anzupassen.'),
    'pl-PL': ('BMP zapisuje nieskompresowany kolor 24-bitowy. Przezroczyste obszary stają się białe; metadane są usuwane. Zmiana wymiarów może zmniejszyć szczegóły i powiększyć plik.', 'BMP nie ma ustawienia jakości. Piksele są zapisywane bez kompresji; zmień wymiary, aby dostosować rozmiar pliku.'),
    'ko-KR': ('BMP는 압축하지 않은 24비트 색상을 저장합니다. 투명한 영역은 흰색이 되고 메타데이터는 제거됩니다. 크기 변경으로 세부 정보가 줄고 파일이 커질 수 있습니다.', 'BMP에는 품질 설정이 없습니다. 픽셀을 압축하지 않고 저장하므로 파일 크기를 바꾸려면 이미지 크기를 조절하세요.'),
    'zh-Hans': ('BMP保存未压缩的24位颜色。透明区域变为白色，元数据会被移除。调整尺寸可能减少细节并增大文件。', 'BMP没有质量设置。像素不经压缩保存；请调整图像尺寸以改变文件大小。'),
    'zh-Hant': ('BMP儲存未壓縮的24位元色彩。透明區域變為白色，中繼資料會被移除。調整尺寸可能減少細節並增大檔案。', 'BMP沒有品質設定。像素不經壓縮儲存；請調整影像尺寸以改變檔案大小。'),
    'it-IT': ('BMP salva colori a 24 bit senza compressione. Le aree trasparenti diventano bianche; i metadati vengono rimossi. Ridimensionare può ridurre i dettagli e aumentare il file.', 'BMP non ha impostazioni di qualità. Salva pixel senza compressione; regola le dimensioni per cambiare la grandezza del file.'),
    'th-TH': ('BMP เก็บสี 24 บิตโดยไม่บีบอัด พื้นที่โปร่งใสจะเป็นสีขาวและข้อมูลเมตาจะถูกลบ การปรับขนาดอาจลดรายละเอียดและทำให้ไฟล์ใหญ่ขึ้น', 'BMP ไม่มีการตั้งค่าคุณภาพ เก็บพิกเซลโดยไม่บีบอัด ปรับมิติภาพเพื่อเปลี่ยนขนาดไฟล์'),
    'vi-VN': ('BMP lưu màu 24 bit không nén. Vùng trong suốt chuyển thành trắng; siêu dữ liệu bị xóa. Đổi kích thước có thể giảm chi tiết và làm tệp lớn hơn.', 'BMP không có cài đặt chất lượng. Điểm ảnh được lưu không nén; chỉnh kích thước ảnh để đổi dung lượng tệp.'),
    'fa': ('BMP رنگ ۲۴ بیتی را بدون فشرده‌سازی ذخیره می‌کند. نواحی شفاف سفید می‌شوند و فراداده حذف می‌شود. تغییر ابعاد ممکن است جزئیات را کاهش دهد و فایل را بزرگ‌تر کند.', 'BMP تنظیم کیفیت ندارد. پیکسل‌ها بدون فشرده‌سازی ذخیره می‌شوند؛ برای تغییر اندازهٔ فایل، ابعاد تصویر را تنظیم کنید.'),
    'sw': ('BMP huhifadhi rangi ya biti 24 bila kubana. Sehemu wazi huwa nyeupe; metadata huondolewa. Kubadili vipimo kunaweza kupunguza maelezo na kuongeza ukubwa wa faili.', 'BMP haina mpangilio wa ubora. Pikseli huhifadhiwa bila kubana; badili vipimo ili kurekebisha ukubwa wa faili.'),
    'ha': ('BMP yana adana launi na bit 24 ba tare da matsawa ba. Wuraren da ake gani ta cikinsu suna zama fari; ana cire metadata. Sauya girman hoto na iya rage daki-daki kuma ya ƙara girman fayil.', 'BMP ba shi da saitin inganci. Yana adana piksel ba tare da matsawa ba; sauya girman hoto don sauya girman fayil.'),
    'ro': ('BMP stochează culori pe 24 de biți fără compresie. Zonele transparente devin albe; metadatele sunt eliminate. Redimensionarea poate reduce detaliile și mări fișierul.', 'BMP nu are setare de calitate. Stochează pixeli necomprimați; ajustează dimensiunile pentru a schimba mărimea fișierului.'),
}

PDF_ENCODING = {
    'en-US': 'Pages have a white background. JPEG and WebP use lossy encoding; PNG preserves the rendered pixels.',
    'tr-TR': 'Sayfalar beyaz zeminlidir. JPEG ve WebP kayıplı kodlama kullanır; PNG resmedilen pikselleri korur.',
    'es': 'Las páginas tienen fondo blanco. JPEG y WebP usan codificación con pérdida; PNG conserva los píxeles renderizados.',
    'pt-BR': 'As páginas têm fundo branco. JPEG e WebP usam codificação com perdas; PNG preserva os pixels renderizados.',
    'id': 'Halaman berlatar putih. JPEG dan WebP memakai pengodean lossy; PNG mempertahankan piksel yang dirender.',
    'hi': 'पृष्ठों की पृष्ठभूमि सफेद होती है। JPEG और WebP हानिपूर्ण एन्कोडिंग का उपयोग करते हैं; PNG रेंडर किए गए पिक्सेल रखता है।',
    'ar': 'للصفحات خلفية بيضاء. يستخدم JPEG وWebP ترميزًا بفقدان بعض التفاصيل؛ يحافظ PNG على البكسلات المعروضة.',
    'ru': 'У страниц белый фон. JPEG и WebP используют кодирование с потерями; PNG сохраняет отрисованные пиксели.',
    'ja-JP': 'ページの背景は白です。JPEGとWebPは非可逆圧縮を使用し、PNGは描画されたピクセルを保持します。',
    'fr-FR': 'Les pages ont un fond blanc. JPEG et WebP utilisent un encodage avec perte ; PNG conserve les pixels rendus.',
    'de-DE': 'Seiten haben einen weißen Hintergrund. JPEG und WebP kodieren verlustbehaftet; PNG erhält die gerenderten Pixel.',
    'pl-PL': 'Strony mają białe tło. JPEG i WebP używają kodowania stratnego; PNG zachowuje wyrenderowane piksele.',
    'ko-KR': '페이지 배경은 흰색입니다. JPEG와 WebP는 손실 인코딩을 사용하고 PNG는 렌더링된 픽셀을 보존합니다.',
    'zh-Hans': '页面使用白色背景。JPEG和WebP采用有损编码；PNG保留渲染后的像素。',
    'zh-Hant': '頁面使用白色背景。JPEG和WebP採用有損編碼；PNG保留算繪後的像素。',
    'it-IT': 'Le pagine hanno uno sfondo bianco. JPEG e WebP usano una codifica con perdita; PNG conserva i pixel renderizzati.',
    'th-TH': 'หน้ามีพื้นหลังสีขาว JPEG และ WebP เข้ารหัสแบบสูญเสียรายละเอียด ส่วน PNG เก็บพิกเซลที่เรนเดอร์แล้ว',
    'vi-VN': 'Trang có nền trắng. JPEG và WebP dùng mã hóa mất dữ liệu; PNG giữ nguyên các điểm ảnh đã kết xuất.',
    'fa': 'پس‌زمینهٔ صفحه‌ها سفید است. JPEG و WebP از رمزگذاری با اتلاف استفاده می‌کنند؛ PNG پیکسل‌های رندرشده را حفظ می‌کند.',
    'sw': 'Kurasa zina mandharinyuma meupe. JPEG na WebP husimba kwa kupoteza baadhi ya maelezo; PNG huhifadhi pikseli zilizochorwa.',
    'ha': 'Shafuka suna da bango fari. JPEG da WebP suna amfani da kodin da ke rasa wasu bayanai; PNG yana kiyaye piksel da aka zana.',
    'ro': 'Paginile au fundal alb. JPEG și WebP folosesc codare cu pierderi; PNG păstrează pixelii redați.',
}

path = Path(__file__).with_name('catalog.json')
catalog = json.loads(path.read_text(encoding='utf-8'))
for locale, values in catalog['locales'].items():
    for key in ('compression_hint', 'slide_image_error', 'image_error', 'photo_pdf_import_error', 'error_image_format'):
        index = catalog['keys'].index(key)
        if 'BMP' not in values[index]:
            values[index] = values[index].replace('PNG', 'PNG / BMP')
    index = catalog['keys'].index('reader_image_loss')
    if 'JPEG' not in values[index]:
        values[index] = values[index].replace('PNG', 'PNG / JPEG / WebP')
    for key in ('reader_create_image', 'reader_save_image'):
        index = catalog['keys'].index(key)
        values[index] = values[index].replace('PNG', '%1$s')
for column, key in enumerate(('bmp_losses', 'bmp_quality_hint')):
    if key not in catalog['keys']:
        catalog['keys'].append(key)
        for locale, values in catalog['locales'].items():
            values.append(TEXT[locale][column])
if 'reader_encoding_hint' not in catalog['keys']:
    catalog['keys'].append('reader_encoding_hint')
    for locale, values in catalog['locales'].items():
        values.append(PDF_ENCODING[locale])
catalog['locales']['en-US'][catalog['keys'].index('reader_image_loss')] = (
    'Saving this page as PNG, JPEG or WebP creates a still image. Searchable text, links and interactive fields are not kept. This is conversion, not compression.'
)
path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
