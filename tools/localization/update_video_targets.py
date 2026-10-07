"""Curated output/loss/device messages; no network or runtime translation dependency."""
import json
from pathlib import Path

KEYS = ('video_output_hint', 'video_hevc_losses', 'video_webm_losses', 'video_output_device',
        'video_output_audio', 'video_output_platform', 'video_output_orientation')
TEXT = {
 'en-US': (
  'Available formats depend on the device and selected settings.',
  'HEVC MP4 may not play in every app. Re-encoding can lose detail and metadata; a smaller file is not guaranteed.',
  'VP8 video and Opus sound at 48 kHz. Sound is re-encoded; detail and metadata may be lost. A smaller file is not guaranteed.',
  'This device cannot encode or preview the selected format at these settings. Choose another format or adjust the settings.',
  "This format cannot preserve this video's audio on this device. Choose another format.",
  'WebM with sound requires Android 10 or newer.',
  "WebM cannot preserve this video's orientation. Choose MP4."),
 'tr-TR': (
  'Kullanılabilir biçimler cihaza ve seçilen ayarlara bağlıdır.',
  'HEVC MP4 her uygulamada oynatılamayabilir. Yeniden kodlama ayrıntı ve üst veri kaybına yol açabilir; dosyanın küçülmesi garanti değildir.',
  'VP8 video ve 48 kHz Opus ses kullanılır. Ses yeniden kodlanır; ayrıntı ve üst veri kaybı olabilir. Dosyanın küçülmesi garanti değildir.',
  'Bu cihaz seçilen biçimi bu ayarlarla kodlayamıyor veya önizleyemiyor. Başka biçim seç veya ayarları değiştir.',
  'Bu biçim, videonun sesini bu cihazda koruyamıyor. Başka biçim seç.',
  'Sesli WebM için Android 10 veya üzeri gerekir.',
  'WebM bu videonun yönünü koruyamıyor. MP4 seç.'),
 'es': (
  'Los formatos disponibles dependen del dispositivo y los ajustes.',
  'HEVC MP4 puede no reproducirse en todas las apps. Recodificar puede perder detalle y metadatos; no garantiza un archivo menor.',
  'Vídeo VP8 y audio Opus a 48 kHz. El audio se recodifica; pueden perderse detalle y metadatos. No garantiza un archivo menor.',
  'El dispositivo no puede codificar o previsualizar este formato con estos ajustes. Cambia el formato o los ajustes.',
  'Este formato no puede conservar el audio del vídeo en este dispositivo. Elige otro formato.',
  'WebM con audio requiere Android 10 o posterior.',
  'WebM no puede conservar la orientación de este vídeo. Elige MP4.'),
 'pt-BR': (
  'Os formatos disponíveis dependem do dispositivo e dos ajustes.',
  'HEVC MP4 pode não reproduzir em todos os apps. Recodificar pode perder detalhes e metadados; um arquivo menor não é garantido.',
  'Vídeo VP8 e áudio Opus a 48 kHz. O áudio é recodificado; detalhes e metadados podem ser perdidos. Um arquivo menor não é garantido.',
  'O dispositivo não pode codificar ou visualizar este formato com estes ajustes. Altere o formato ou os ajustes.',
  'Este formato não preserva o áudio deste vídeo neste dispositivo. Escolha outro formato.',
  'WebM com áudio requer Android 10 ou superior.',
  'WebM não preserva a orientação deste vídeo. Escolha MP4.'),
 'id': (
  'Format yang tersedia bergantung pada perangkat dan pengaturan.',
  'HEVC MP4 mungkin tidak diputar di semua aplikasi. Pengodean ulang dapat menghilangkan detail dan metadata; file lebih kecil tidak dijamin.',
  'Video VP8 dan audio Opus 48 kHz. Audio dikodekan ulang; detail dan metadata dapat hilang. File lebih kecil tidak dijamin.',
  'Perangkat tidak dapat mengodekan atau menampilkan format ini dengan pengaturan tersebut. Ubah format atau pengaturan.',
  'Format ini tidak dapat mempertahankan audio video pada perangkat ini. Pilih format lain.',
  'WebM dengan audio memerlukan Android 10 atau lebih baru.',
  'WebM tidak dapat mempertahankan orientasi video ini. Pilih MP4.'),
 'hi': (
  'उपलब्ध फ़ॉर्मैट डिवाइस और चुनी गई सेटिंग पर निर्भर हैं।',
  'HEVC MP4 हर ऐप में नहीं चल सकता। दोबारा एन्कोड करने से विवरण और मेटाडेटा खो सकते हैं; छोटा फ़ाइल आकार निश्चित नहीं है।',
  'VP8 वीडियो और 48 kHz Opus ऑडियो। ऑडियो दोबारा एन्कोड होता है; विवरण और मेटाडेटा खो सकते हैं। छोटा आकार निश्चित नहीं है।',
  'यह डिवाइस इन सेटिंग के साथ चुना फ़ॉर्मैट एन्कोड या प्रीव्यू नहीं कर सकता। फ़ॉर्मैट या सेटिंग बदलें।',
  'यह फ़ॉर्मैट इस डिवाइस पर वीडियो का ऑडियो सुरक्षित नहीं रख सकता। दूसरा फ़ॉर्मैट चुनें।',
  'ऑडियो वाले WebM के लिए Android 10 या नया चाहिए।',
  'WebM इस वीडियो की दिशा सुरक्षित नहीं रख सकता। MP4 चुनें।'),
 'ar': (
  'تعتمد التنسيقات المتاحة على الجهاز والإعدادات المحددة.',
  'قد لا يعمل HEVC MP4 في كل التطبيقات. قد تفقد إعادة الترميز التفاصيل والبيانات الوصفية؛ لا يُضمن حجم أصغر.',
  'فيديو VP8 وصوت Opus بتردد 48 kHz. يُعاد ترميز الصوت وقد تُفقد التفاصيل والبيانات الوصفية. لا يُضمن حجم أصغر.',
  'لا يستطيع الجهاز ترميز هذا التنسيق أو معاينته بهذه الإعدادات. غيّر التنسيق أو الإعدادات.',
  'لا يمكن لهذا التنسيق الحفاظ على صوت الفيديو على هذا الجهاز. اختر تنسيقًا آخر.',
  'يتطلب WebM مع الصوت Android 10 أو أحدث.',
  'لا يمكن لـ WebM الحفاظ على اتجاه هذا الفيديو. اختر MP4.'),
 'ru': (
  'Доступные форматы зависят от устройства и выбранных настроек.',
  'HEVC MP4 может не воспроизводиться в некоторых приложениях. Перекодирование может удалить детали и метаданные; меньший размер не гарантируется.',
  'Видео VP8 и звук Opus 48 кГц. Звук перекодируется; детали и метаданные могут теряться. Меньший размер не гарантируется.',
  'Устройство не может кодировать или просматривать этот формат с такими настройками. Измените формат или настройки.',
  'Этот формат не сохраняет звук этого видео на устройстве. Выберите другой формат.',
  'Для WebM со звуком нужен Android 10 или новее.',
  'WebM не сохраняет ориентацию этого видео. Выберите MP4.'),
 'ja-JP': (
  '利用できる形式は端末と設定によって異なります。',
  'HEVC MP4は一部のアプリで再生できません。再エンコードで細部やメタデータが失われる場合があります。容量が小さくなる保証はありません。',
  'VP8動画と48 kHzのOpus音声を使用します。音声は再エンコードされ、細部やメタデータが失われる場合があります。容量の縮小は保証されません。',
  'この端末では選んだ形式を現在の設定でエンコードまたはプレビューできません。形式か設定を変更してください。',
  'この端末ではこの形式で動画の音声を保持できません。別の形式を選んでください。',
  '音声付きWebMにはAndroid 10以降が必要です。',
  'WebMではこの動画の向きを保持できません。MP4を選んでください。'),
 'fr-FR': (
  'Les formats disponibles dépendent de l’appareil et des réglages.',
  'HEVC MP4 peut ne pas être lu dans certaines apps. Le réencodage peut perdre des détails et métadonnées ; un fichier plus petit n’est pas garanti.',
  'Vidéo VP8 et audio Opus à 48 kHz. L’audio est réencodé ; des détails et métadonnées peuvent être perdus. Un fichier plus petit n’est pas garanti.',
  'L’appareil ne peut pas encoder ou prévisualiser ce format avec ces réglages. Changez de format ou de réglages.',
  'Ce format ne conserve pas l’audio de cette vidéo sur cet appareil. Choisissez un autre format.',
  'WebM avec audio nécessite Android 10 ou ultérieur.',
  'WebM ne conserve pas l’orientation de cette vidéo. Choisissez MP4.'),
 'de-DE': (
  'Verfügbare Formate hängen vom Gerät und den Einstellungen ab.',
  'HEVC MP4 lässt sich nicht in jeder App abspielen. Neukodierung kann Details und Metadaten verlieren; eine kleinere Datei ist nicht garantiert.',
  'VP8-Video und Opus-Audio mit 48 kHz. Audio wird neu kodiert; Details und Metadaten können verloren gehen. Eine kleinere Datei ist nicht garantiert.',
  'Das Gerät kann dieses Format mit diesen Einstellungen nicht kodieren oder anzeigen. Ändere Format oder Einstellungen.',
  'Dieses Format kann den Ton dieses Videos auf dem Gerät nicht erhalten. Wähle ein anderes Format.',
  'WebM mit Ton erfordert Android 10 oder neuer.',
  'WebM kann die Ausrichtung dieses Videos nicht erhalten. Wähle MP4.'),
 'pl-PL': (
  'Dostępne formaty zależą od urządzenia i ustawień.',
  'HEVC MP4 może nie działać w każdej aplikacji. Ponowne kodowanie może usunąć szczegóły i metadane; mniejszy plik nie jest gwarantowany.',
  'Wideo VP8 i dźwięk Opus 48 kHz. Dźwięk jest ponownie kodowany; szczegóły i metadane mogą zostać utracone. Mniejszy plik nie jest gwarantowany.',
  'Urządzenie nie może kodować ani podglądać tego formatu przy tych ustawieniach. Zmień format lub ustawienia.',
  'Ten format nie zachowuje dźwięku tego wideo na urządzeniu. Wybierz inny format.',
  'WebM z dźwiękiem wymaga Androida 10 lub nowszego.',
  'WebM nie zachowuje orientacji tego wideo. Wybierz MP4.'),
 'ko-KR': (
  '사용 가능한 형식은 기기와 선택한 설정에 따라 달라집니다.',
  'HEVC MP4는 일부 앱에서 재생되지 않을 수 있습니다. 재인코딩으로 세부 정보와 메타데이터가 손실될 수 있으며 파일 크기 감소는 보장되지 않습니다.',
  'VP8 동영상과 48 kHz Opus 오디오를 사용합니다. 오디오를 재인코딩하며 세부 정보와 메타데이터가 손실될 수 있습니다. 크기 감소는 보장되지 않습니다.',
  '이 기기는 현재 설정으로 선택한 형식을 인코딩하거나 미리 볼 수 없습니다. 형식이나 설정을 변경하세요.',
  '이 형식은 이 기기에서 동영상의 오디오를 유지할 수 없습니다. 다른 형식을 선택하세요.',
  '오디오가 있는 WebM은 Android 10 이상이 필요합니다.',
  'WebM은 이 동영상의 방향을 유지할 수 없습니다. MP4를 선택하세요.'),
 'zh-Hans': (
  '可用格式取决于设备和所选设置。',
  'HEVC MP4可能无法在所有应用中播放。重新编码可能丢失细节和元数据；不能保证文件更小。',
  '使用VP8视频和48 kHz Opus音频。音频会重新编码，可能丢失细节和元数据。不能保证文件更小。',
  '此设备无法按当前设置编码或预览所选格式。请更改格式或设置。',
  '此格式无法在此设备上保留视频的音频。请选择其他格式。',
  '带音频的WebM需要Android 10或更高版本。',
  'WebM无法保留此视频的方向。请选择MP4。'),
 'zh-Hant': (
  '可用格式取決於裝置和所選設定。',
  'HEVC MP4可能無法在所有應用程式中播放。重新編碼可能遺失細節和中繼資料；無法保證檔案更小。',
  '使用VP8影片和48 kHz Opus音訊。音訊會重新編碼，可能遺失細節和中繼資料。無法保證檔案更小。',
  '此裝置無法按目前設定編碼或預覽所選格式。請變更格式或設定。',
  '此格式無法在此裝置上保留影片的音訊。請選擇其他格式。',
  '含音訊的WebM需要Android 10或更新版本。',
  'WebM無法保留此影片的方向。請選擇MP4。'),
 'it-IT': (
  'I formati disponibili dipendono dal dispositivo e dalle impostazioni.',
  'HEVC MP4 potrebbe non funzionare in tutte le app. La ricodifica può perdere dettagli e metadati; un file più piccolo non è garantito.',
  'Video VP8 e audio Opus a 48 kHz. L’audio viene ricodificato; dettagli e metadati possono andare persi. Un file più piccolo non è garantito.',
  'Il dispositivo non può codificare o mostrare questo formato con queste impostazioni. Cambia formato o impostazioni.',
  'Questo formato non conserva l’audio di questo video sul dispositivo. Scegli un altro formato.',
  'WebM con audio richiede Android 10 o successivo.',
  'WebM non conserva l’orientamento di questo video. Scegli MP4.'),
 'th-TH': (
  'รูปแบบที่ใช้ได้ขึ้นอยู่กับอุปกรณ์และการตั้งค่าที่เลือก',
  'HEVC MP4 อาจเล่นไม่ได้ในบางแอป การเข้ารหัสใหม่อาจสูญเสียรายละเอียดและข้อมูลเมตา ไม่รับประกันว่าไฟล์จะเล็กลง',
  'ใช้วิดีโอ VP8 และเสียง Opus ที่ 48 kHz เสียงจะถูกเข้ารหัสใหม่ อาจสูญเสียรายละเอียดและข้อมูลเมตา ไม่รับประกันว่าไฟล์จะเล็กลง',
  'อุปกรณ์นี้ไม่สามารถเข้ารหัสหรือแสดงตัวอย่างรูปแบบนี้ด้วยการตั้งค่าปัจจุบัน เปลี่ยนรูปแบบหรือการตั้งค่า',
  'รูปแบบนี้ไม่สามารถรักษาเสียงวิดีโอบนอุปกรณ์นี้ เลือกรูปแบบอื่น',
  'WebM ที่มีเสียงต้องใช้ Android 10 ขึ้นไป',
  'WebM ไม่สามารถรักษาทิศทางของวิดีโอนี้ เลือก MP4'),
 'vi-VN': (
  'Định dạng có sẵn phụ thuộc vào thiết bị và cài đặt.',
  'HEVC MP4 có thể không phát trong mọi ứng dụng. Mã hóa lại có thể mất chi tiết và siêu dữ liệu; không đảm bảo tệp nhỏ hơn.',
  'Video VP8 và âm thanh Opus 48 kHz. Âm thanh được mã hóa lại; có thể mất chi tiết và siêu dữ liệu. Không đảm bảo tệp nhỏ hơn.',
  'Thiết bị không thể mã hóa hoặc xem trước định dạng này với cài đặt hiện tại. Đổi định dạng hoặc cài đặt.',
  'Định dạng này không giữ được âm thanh video trên thiết bị. Chọn định dạng khác.',
  'WebM có âm thanh cần Android 10 trở lên.',
  'WebM không giữ được hướng của video này. Chọn MP4.'),
 'fa': (
  'قالب‌های موجود به دستگاه و تنظیمات انتخاب‌شده بستگی دارند.',
  'HEVC MP4 ممکن است در همهٔ برنامه‌ها پخش نشود. رمزگذاری دوباره ممکن است جزئیات و فراداده را از بین ببرد؛ کوچک‌تر شدن تضمین نمی‌شود.',
  'ویدیوی VP8 و صدای Opus با 48 kHz. صدا دوباره رمزگذاری می‌شود؛ ممکن است جزئیات و فراداده از بین برود. کوچک‌تر شدن تضمین نمی‌شود.',
  'این دستگاه نمی‌تواند قالب انتخابی را با این تنظیمات رمزگذاری یا پیش‌نمایش کند. قالب یا تنظیمات را تغییر دهید.',
  'این قالب نمی‌تواند صدای ویدیو را روی این دستگاه حفظ کند. قالب دیگری انتخاب کنید.',
  'WebM همراه صدا به Android 10 یا جدیدتر نیاز دارد.',
  'WebM نمی‌تواند جهت این ویدیو را حفظ کند. MP4 را انتخاب کنید.'),
 'sw': (
  'Miundo inayopatikana hutegemea kifaa na mipangilio.',
  'HEVC MP4 inaweza kutocheza katika kila programu. Usimbaji upya unaweza kupoteza maelezo na metadata; faili ndogo haijahakikishwa.',
  'Video ya VP8 na sauti ya Opus ya 48 kHz. Sauti husimbwa upya; maelezo na metadata zinaweza kupotea. Faili ndogo haijahakikishwa.',
  'Kifaa hiki hakiwezi kusimba au kuonyesha muundo huu kwa mipangilio hii. Badili muundo au mipangilio.',
  'Muundo huu hauwezi kuhifadhi sauti ya video kwenye kifaa hiki. Chagua muundo mwingine.',
  'WebM yenye sauti inahitaji Android 10 au mpya zaidi.',
  'WebM haiwezi kuhifadhi mwelekeo wa video hii. Chagua MP4.'),
 'ha': (
  'Tsare-tsaren da ake samu sun dogara da na’ura da saitunan da aka zaɓa.',
  'HEVC MP4 na iya kasa kunnawa a wasu manhajoji. Sake kodin na iya rasa daki-daki da metadata; ba a tabbatar da ƙaramin fayil ba.',
  'Bidiyon VP8 da sautin Opus na 48 kHz. Ana sake kodin sauti; ana iya rasa daki-daki da metadata. Ba a tabbatar da ƙaramin fayil ba.',
  'Na’urar ba za ta iya kodin ko duba wannan tsarin da waɗannan saitunan ba. Canja tsarin ko saituna.',
  'Wannan tsarin ba zai iya kiyaye sautin bidiyon a wannan na’urar ba. Zaɓi wani tsarin.',
  'WebM mai sauti yana buƙatar Android 10 ko sama.',
  'WebM ba zai iya kiyaye alkiblar wannan bidiyon ba. Zaɓi MP4.'),
 'ro': (
  'Formatele disponibile depind de dispozitiv și de setări.',
  'HEVC MP4 poate să nu ruleze în toate aplicațiile. Recodarea poate pierde detalii și metadate; un fișier mai mic nu este garantat.',
  'Video VP8 și sunet Opus la 48 kHz. Sunetul este recodat; detaliile și metadatele se pot pierde. Un fișier mai mic nu este garantat.',
  'Dispozitivul nu poate coda sau previzualiza acest format cu aceste setări. Schimbă formatul sau setările.',
  'Acest format nu poate păstra sunetul videoclipului pe dispozitiv. Alege alt format.',
  'WebM cu sunet necesită Android 10 sau mai nou.',
  'WebM nu poate păstra orientarea acestui videoclip. Alege MP4.'),
}

path = Path(__file__).with_name('catalog.json')
catalog = json.loads(path.read_text(encoding='utf-8'))
assert set(TEXT) == set(catalog['locales'])
for column, key in enumerate(KEYS):
    if key not in catalog['keys']:
        catalog['keys'].append(key)
        for values in catalog['locales'].values(): values.append('')
    index = catalog['keys'].index(key)
    for tag, values in catalog['locales'].items():
        assert len(TEXT[tag]) == len(KEYS)
        values[index] = TEXT[tag][column]
for values in catalog['locales'].values():
    index = catalog['keys'].index('files_video_decoders')
    values[index] = values[index].replace('MP4 (H.264 + AAC)', 'MP4 (H.264 / HEVC + AAC) / WebM (VP8 + Opus)')
    index = catalog['keys'].index('video_losses')
    values[index] = values[index].replace('MP4 ', '')
path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
