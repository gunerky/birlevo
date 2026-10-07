from pathlib import Path
import json
keys = ['audio_ogg_description','audio_flac_description','audio_pcm_description','audio_output_android','audio_output_encoder','audio_output_decoder','audio_output_check']
texts = {
'en': [
'OGG uses lossy Opus at 48 kHz and the selected bitrate. Channels are retained; other sample rates are converted to 48 kHz.',
'FLAC preserves decoded 16-bit PCM, sample rate and channels. Higher bit depths are reduced to 16-bit; lost detail cannot be recovered.',
'WAV stores decoded 16-bit PCM. M4A uses lossy AAC. Sample rate and channels are retained.',
'OGG output requires Android 10 or later. Choose another format.',
'The device cannot encode this format with the requested settings. Choose another format or bitrate.',
'The device cannot play this output format. Choose another format.',
'Output support could not be checked. Reselect the format to try again.'],
'tr-rTR': [
'OGG, 48 kHz ve seçilen bit hızında kayıplı Opus kullanır. Kanal sayısı korunur; diğer örnekleme hızları 48 kHz’e dönüştürülür.',
'FLAC, çözülmüş 16 bit PCM verisini, örnekleme hızını ve kanal sayısını korur. Daha yüksek bit derinliği 16 bite indirilir; kayıp ayrıntı geri getirilemez.',
'WAV, çözülmüş 16 bit PCM saklar. M4A kayıplı AAC kullanır. Örnekleme hızı ve kanal sayısı korunur.',
'OGG çıktısı Android 10 veya üzerini gerektirir. Başka bir biçim seçin.',
'Cihaz bu biçimi istenen ayarlarla kodlayamıyor. Başka bir biçim veya bit hızı seçin.',
'Cihaz bu çıktı biçimini oynatamıyor. Başka bir biçim seçin.',
'Çıktı desteği kontrol edilemedi. Yeniden denemek için biçimi tekrar seçin.'],
'de-rDE': [
'OGG nutzt verlustbehaftetes Opus mit 48 kHz und der gewählten Bitrate. Die Kanäle bleiben erhalten; andere Abtastraten werden auf 48 kHz umgerechnet.',
'FLAC erhält decodiertes 16-Bit-PCM, Abtastrate und Kanäle. Höhere Bittiefen werden auf 16 Bit reduziert; verlorene Details lassen sich nicht wiederherstellen.',
'WAV speichert decodiertes 16-Bit-PCM. M4A nutzt verlustbehaftetes AAC. Abtastrate und Kanäle bleiben erhalten.',
'OGG-Ausgabe benötigt Android 10 oder neuer. Wähle ein anderes Format.',
'Das Gerät kann dieses Format mit den gewünschten Einstellungen nicht codieren. Wähle ein anderes Format oder eine andere Bitrate.',
'Das Gerät kann dieses Ausgabeformat nicht abspielen. Wähle ein anderes Format.',
'Die Ausgabeunterstützung konnte nicht geprüft werden. Wähle das Format erneut.'],
'es': [
'OGG usa Opus con pérdida a 48 kHz y la tasa elegida. Se conservan los canales; otras frecuencias se convierten a 48 kHz.',
'FLAC conserva PCM de 16 bits decodificado, la frecuencia y los canales. Las profundidades mayores se reducen a 16 bits; no se recuperan detalles perdidos.',
'WAV guarda PCM de 16 bits decodificado. M4A usa AAC con pérdida. Se conservan la frecuencia y los canales.',
'La salida OGG requiere Android 10 o posterior. Elige otro formato.',
'El dispositivo no puede codificar este formato con los ajustes elegidos. Elige otro formato o tasa de bits.',
'El dispositivo no puede reproducir este formato de salida. Elige otro formato.',
'No se pudo comprobar la compatibilidad de salida. Vuelve a seleccionar el formato.'],
'fr-rFR': [
'OGG utilise Opus avec perte à 48 kHz et au débit choisi. Les canaux sont conservés ; les autres fréquences sont converties à 48 kHz.',
'FLAC conserve le PCM 16 bits décodé, la fréquence et les canaux. Les profondeurs supérieures sont réduites à 16 bits ; les détails perdus ne sont pas restaurés.',
'WAV stocke le PCM 16 bits décodé. M4A utilise AAC avec perte. La fréquence et les canaux sont conservés.',
'La sortie OGG nécessite Android 10 ou ultérieur. Choisissez un autre format.',
'Cet appareil ne peut pas encoder ce format avec ces paramètres. Choisissez un autre format ou débit.',
'Cet appareil ne peut pas lire ce format de sortie. Choisissez un autre format.',
'La compatibilité de sortie n’a pas pu être vérifiée. Sélectionnez à nouveau le format.'],
'it-rIT': [
'OGG usa Opus con perdita a 48 kHz e al bitrate scelto. I canali restano invariati; altre frequenze vengono convertite a 48 kHz.',
'FLAC conserva PCM decodificato a 16 bit, frequenza e canali. Profondità maggiori vengono ridotte a 16 bit; i dettagli persi non si recuperano.',
'WAV salva PCM decodificato a 16 bit. M4A usa AAC con perdita. Frequenza e canali restano invariati.',
'La uscita OGG richiede Android 10 o successivo. Scegli un altro formato.',
'Il dispositivo non può codificare questo formato con le impostazioni richieste. Scegli un altro formato o bitrate.',
'Il dispositivo non può riprodurre questo formato di uscita. Scegli un altro formato.',
'Impossibile verificare il supporto di uscita. Seleziona di nuovo il formato.'],
'pt-rBR': [
'OGG usa Opus com perdas a 48 kHz e na taxa escolhida. Os canais são mantidos; outras frequências são convertidas para 48 kHz.',
'FLAC preserva PCM de 16 bits decodificado, frequência e canais. Profundidades maiores são reduzidas a 16 bits; detalhes perdidos não são recuperados.',
'WAV armazena PCM de 16 bits decodificado. M4A usa AAC com perdas. A frequência e os canais são mantidos.',
'A saída OGG exige Android 10 ou posterior. Escolha outro formato.',
'O dispositivo não pode codificar este formato com os ajustes solicitados. Escolha outro formato ou taxa de bits.',
'O dispositivo não pode reproduzir este formato de saída. Escolha outro formato.',
'Não foi possível verificar o suporte de saída. Selecione o formato novamente.'],
'ru': [
'OGG использует Opus с потерями, 48 кГц и выбранный битрейт. Каналы сохраняются; другая частота преобразуется в 48 кГц.',
'FLAC сохраняет декодированный 16-битный PCM, частоту и каналы. Большая разрядность снижается до 16 бит; утраченные детали не восстанавливаются.',
'WAV хранит декодированный 16-битный PCM. M4A использует AAC с потерями. Частота и каналы сохраняются.',
'Для вывода OGG нужен Android 10 или новее. Выберите другой формат.',
'Устройство не может кодировать этот формат с заданными настройками. Выберите другой формат или битрейт.',
'Устройство не может воспроизвести этот формат вывода. Выберите другой формат.',
'Не удалось проверить поддержку вывода. Выберите формат повторно.'],
'pl-rPL': [
'OGG używa stratnego Opus, 48 kHz i wybranego bitrate. Kanały są zachowane; inne częstotliwości są konwertowane na 48 kHz.',
'FLAC zachowuje zdekodowany 16-bitowy PCM, częstotliwość i kanały. Większa głębia jest redukowana do 16 bitów; utracone szczegóły nie wracają.',
'WAV zapisuje zdekodowany 16-bitowy PCM. M4A używa stratnego AAC. Częstotliwość i kanały są zachowane.',
'Wyjście OGG wymaga Androida 10 lub nowszego. Wybierz inny format.',
'Urządzenie nie może kodować tego formatu z tymi ustawieniami. Wybierz inny format lub bitrate.',
'Urządzenie nie może odtwarzać tego formatu wyjściowego. Wybierz inny format.',
'Nie można sprawdzić obsługi wyjścia. Wybierz format ponownie.'],
'ro': [
'OGG folosește Opus cu pierderi la 48 kHz și rata aleasă. Canalele se păstrează; alte frecvențe se convertesc la 48 kHz.',
'FLAC păstrează PCM decodat de 16 biți, frecvența și canalele. Adâncimile mai mari sunt reduse la 16 biți; detaliile pierdute nu se recuperează.',
'WAV stochează PCM decodat de 16 biți. M4A folosește AAC cu pierderi. Frecvența și canalele se păstrează.',
'Ieșirea OGG necesită Android 10 sau mai nou. Alege alt format.',
'Dispozitivul nu poate codifica acest format cu setările cerute. Alege alt format sau rată de biți.',
'Dispozitivul nu poate reda acest format de ieșire. Alege alt format.',
'Suportul de ieșire nu a putut fi verificat. Selectează din nou formatul.'],
'in': [
'OGG memakai Opus lossy pada 48 kHz dan bitrate pilihan. Kanal dipertahankan; laju sampel lain dikonversi ke 48 kHz.',
'FLAC mempertahankan PCM 16-bit yang didekode, laju sampel dan kanal. Kedalaman bit lebih tinggi dikurangi ke 16-bit; detail yang hilang tidak pulih.',
'WAV menyimpan PCM 16-bit yang didekode. M4A memakai AAC lossy. Laju sampel dan kanal dipertahankan.',
'Keluaran OGG memerlukan Android 10 atau lebih baru. Pilih format lain.',
'Perangkat tidak dapat mengodekan format ini dengan pengaturan tersebut. Pilih format atau bitrate lain.',
'Perangkat tidak dapat memutar format keluaran ini. Pilih format lain.',
'Dukungan keluaran tidak dapat diperiksa. Pilih ulang format untuk mencoba lagi.'],
'vi-rVN': [
'OGG dùng Opus mất dữ liệu ở 48 kHz và bitrate đã chọn. Giữ số kênh; tần số khác được chuyển thành 48 kHz.',
'FLAC giữ PCM 16-bit đã giải mã, tần số và số kênh. Độ sâu bit cao hơn giảm xuống 16-bit; chi tiết đã mất không thể khôi phục.',
'WAV lưu PCM 16-bit đã giải mã. M4A dùng AAC mất dữ liệu. Giữ tần số và số kênh.',
'Xuất OGG yêu cầu Android 10 trở lên. Chọn định dạng khác.',
'Thiết bị không thể mã hóa định dạng này với cài đặt yêu cầu. Chọn định dạng hoặc bitrate khác.',
'Thiết bị không thể phát định dạng đầu ra này. Chọn định dạng khác.',
'Không thể kiểm tra hỗ trợ đầu ra. Chọn lại định dạng để thử lại.'],
'ja-rJP': [
'OGGは48 kHzと選択したビットレートで非可逆Opusを使用します。チャンネル数は維持され、他のサンプルレートは48 kHzに変換されます。',
'FLACは復号した16ビットPCM、サンプルレート、チャンネル数を維持します。より高いビット深度は16ビットに減り、失われた詳細は復元できません。',
'WAVは復号した16ビットPCMを保存します。M4Aは非可逆AACを使用します。サンプルレートとチャンネル数は維持されます。',
'OGG出力にはAndroid 10以降が必要です。他の形式を選択してください。',
'この端末は指定した設定でこの形式を符号化できません。他の形式かビットレートを選択してください。',
'この端末はこの出力形式を再生できません。他の形式を選択してください。',
'出力の対応状況を確認できませんでした。形式を選び直してください。'],
'ko-rKR': [
'OGG는 48 kHz와 선택한 비트레이트의 손실 Opus를 사용합니다. 채널은 유지되며 다른 샘플레이트는 48 kHz로 변환됩니다.',
'FLAC는 디코딩한 16비트 PCM, 샘플레이트와 채널을 보존합니다. 더 높은 비트 깊이는 16비트로 줄어들며 잃은 세부 정보는 복원되지 않습니다.',
'WAV는 디코딩한 16비트 PCM을 저장합니다. M4A는 손실 AAC를 사용합니다. 샘플레이트와 채널은 유지됩니다.',
'OGG 출력은 Android 10 이상이 필요합니다. 다른 형식을 선택하세요.',
'기기가 요청한 설정으로 이 형식을 인코딩할 수 없습니다. 다른 형식이나 비트레이트를 선택하세요.',
'기기가 이 출력 형식을 재생할 수 없습니다. 다른 형식을 선택하세요.',
'출력 지원을 확인하지 못했습니다. 형식을 다시 선택하세요.'],
'b+zh+Hans': [
'OGG使用48 kHz和所选码率的有损Opus。声道数保持不变；其他采样率转换为48 kHz。',
'FLAC保留解码后的16位PCM、采样率和声道。更高位深会降为16位；无法恢复已丢失的细节。',
'WAV保存解码后的16位PCM。M4A使用有损AAC。采样率和声道保持不变。',
'OGG输出需要Android 10或更高版本。请选择其他格式。',
'设备无法按所选设置编码此格式。请选择其他格式或码率。',
'设备无法播放此输出格式。请选择其他格式。',
'无法检查输出支持情况。请重新选择格式以重试。'],
'b+zh+Hant': [
'OGG使用48 kHz和所選位元率的有損Opus。聲道數保持不變；其他取樣率轉換為48 kHz。',
'FLAC保留解碼後的16位元PCM、取樣率和聲道。更高位元深度會降為16位元；無法復原已遺失的細節。',
'WAV儲存解碼後的16位元PCM。M4A使用有損AAC。取樣率和聲道保持不變。',
'OGG輸出需要Android 10或更新版本。請選擇其他格式。',
'裝置無法依所選設定編碼此格式。請選擇其他格式或位元率。',
'裝置無法播放此輸出格式。請選擇其他格式。',
'無法檢查輸出支援情況。請重新選擇格式以重試。'],
'ar': [
'يستخدم OGG ترميز Opus فاقدًا عند 48 كيلوهرتز ومعدل البت المحدد. تُحفظ القنوات؛ وتُحوّل معدلات العينات الأخرى إلى 48 كيلوهرتز.',
'يحفظ FLAC بيانات PCM المفكوكة من 16 بت ومعدل العينات والقنوات. تُخفض الأعماق الأعلى إلى 16 بت؛ ولا تُستعاد التفاصيل المفقودة.',
'يخزن WAV بيانات PCM المفكوكة من 16 بت. يستخدم M4A ترميز AAC فاقدًا. يُحفظ معدل العينات والقنوات.',
'يتطلب إخراج OGG نظام Android 10 أو أحدث. اختر تنسيقًا آخر.',
'لا يستطيع الجهاز ترميز هذا التنسيق بالإعدادات المطلوبة. اختر تنسيقًا أو معدل بت آخر.',
'لا يستطيع الجهاز تشغيل تنسيق الإخراج هذا. اختر تنسيقًا آخر.',
'تعذر التحقق من دعم الإخراج. أعد اختيار التنسيق للمحاولة مجددًا.'],
'fa': [
'OGG از Opus با اتلاف در ۴۸ کیلوهرتز و نرخ بیت انتخابی استفاده می‌کند. کانال‌ها حفظ می‌شوند؛ نرخ‌های دیگر به ۴۸ کیلوهرتز تبدیل می‌شوند.',
'FLAC داده PCM رمزگشایی‌شده ۱۶ بیتی، نرخ نمونه و کانال‌ها را حفظ می‌کند. عمق‌های بیشتر به ۱۶ بیت کاهش می‌یابند؛ جزئیات ازدست‌رفته بازنمی‌گردند.',
'WAV داده PCM رمزگشایی‌شده ۱۶ بیتی را ذخیره می‌کند. M4A از AAC با اتلاف استفاده می‌کند. نرخ نمونه و کانال‌ها حفظ می‌شوند.',
'خروجی OGG به Android 10 یا جدیدتر نیاز دارد. قالب دیگری انتخاب کنید.',
'دستگاه نمی‌تواند این قالب را با تنظیمات درخواستی رمزگذاری کند. قالب یا نرخ بیت دیگری انتخاب کنید.',
'دستگاه نمی‌تواند این قالب خروجی را پخش کند. قالب دیگری انتخاب کنید.',
'پشتیبانی خروجی بررسی نشد. قالب را دوباره انتخاب کنید.'],
'hi': [
'OGG चुनी बिट दर पर 48 kHz का हानिपूर्ण Opus उपयोग करता है। चैनल बने रहते हैं; दूसरी नमूना दरें 48 kHz में बदलती हैं।',
'FLAC डिकोड किए 16-बिट PCM, नमूना दर और चैनल सुरक्षित रखता है। अधिक बिट गहराई 16-बिट तक घटती है; खोया विवरण वापस नहीं आता।',
'WAV डिकोड किया 16-बिट PCM रखता है। M4A हानिपूर्ण AAC उपयोग करता है। नमूना दर और चैनल बने रहते हैं।',
'OGG आउटपुट के लिए Android 10 या नया चाहिए। दूसरा प्रारूप चुनें।',
'डिवाइस इन सेटिंग से यह प्रारूप एन्कोड नहीं कर सकता। दूसरा प्रारूप या बिट दर चुनें।',
'डिवाइस यह आउटपुट प्रारूप नहीं चला सकता। दूसरा प्रारूप चुनें।',
'आउटपुट समर्थन जाँचा नहीं जा सका। प्रारूप फिर चुनकर कोशिश करें।'],
'th-rTH': [
'OGG ใช้ Opus แบบสูญเสียข้อมูลที่ 48 kHz และอัตราบิตที่เลือก คงจำนวนช่องเสียงไว้ และแปลงอัตราสุ่มอื่นเป็น 48 kHz',
'FLAC รักษา PCM 16 บิตที่ถอดรหัสแล้ว อัตราสุ่มและช่องเสียง ความลึกบิตที่สูงกว่าจะลดเป็น 16 บิต และไม่กู้รายละเอียดที่สูญหาย',
'WAV เก็บ PCM 16 บิตที่ถอดรหัสแล้ว M4A ใช้ AAC แบบสูญเสียข้อมูล คงอัตราสุ่มและช่องเสียงไว้',
'การส่งออก OGG ต้องใช้ Android 10 ขึ้นไป โปรดเลือกรูปแบบอื่น',
'อุปกรณ์ไม่สามารถเข้ารหัสรูปแบบนี้ด้วยการตั้งค่าที่ต้องการ โปรดเลือกรูปแบบหรืออัตราบิตอื่น',
'อุปกรณ์ไม่สามารถเล่นรูปแบบผลลัพธ์นี้ โปรดเลือกรูปแบบอื่น',
'ตรวจสอบการรองรับผลลัพธ์ไม่ได้ โปรดเลือกรูปแบบอีกครั้ง'],
'ha': [
'OGG yana amfani da Opus mai rage bayanai a 48 kHz da saurin bit da aka zaɓa. Ana kiyaye tashoshi; ana sauya sauran saurin samfur zuwa 48 kHz.',
'FLAC yana kiyaye PCM na bit 16 da aka warware, saurin samfur da tashoshi. Ana rage zurfin bit mafi girma zuwa bit 16; bayanan da suka ɓace ba sa dawowa.',
'WAV yana adana PCM na bit 16 da aka warware. M4A yana amfani da AAC mai rage bayanai. Ana kiyaye saurin samfur da tashoshi.',
'Fitar OGG tana buƙatar Android 10 ko sabo. Zaɓi wani tsari.',
'Na’urar ba za ta iya ƙirƙirar wannan tsari da saitunan da aka zaɓa ba. Zaɓi wani tsari ko saurin bit.',
'Na’urar ba za ta iya kunna wannan tsarin fitarwa ba. Zaɓi wani tsari.',
'Ba a iya duba tallafin fitarwa ba. Sake zaɓar tsarin don gwadawa.'],
'sw': [
'OGG hutumia Opus yenye upotevu kwa 48 kHz na kasi ya biti iliyochaguliwa. Chaneli huhifadhiwa; viwango vingine vya sampuli hubadilishwa kuwa 48 kHz.',
'FLAC huhifadhi PCM ya biti 16 iliyofumbuliwa, kiwango cha sampuli na chaneli. Kina kikubwa cha biti hupunguzwa kuwa biti 16; maelezo yaliyopotea hayarejeshwi.',
'WAV huhifadhi PCM ya biti 16 iliyofumbuliwa. M4A hutumia AAC yenye upotevu. Kiwango cha sampuli na chaneli huhifadhiwa.',
'Toleo la OGG linahitaji Android 10 au mpya zaidi. Chagua umbizo jingine.',
'Kifaa hakiwezi kusimba umbizo hili kwa mipangilio hii. Chagua umbizo au kasi ya biti nyingine.',
'Kifaa hakiwezi kucheza umbizo hili la matokeo. Chagua umbizo jingine.',
'Usaidizi wa matokeo haukuweza kukaguliwa. Chagua umbizo tena ujaribu.'],
}
metadata = {
'en': 'Metadata and other tracks are omitted. A smaller file is not guaranteed.',
'tr-rTR': 'Meta veriler ve diğer parçalar aktarılmaz. Daha küçük dosya garantisi yoktur.',
'de-rDE': 'Metadaten und andere Spuren entfallen. Eine kleinere Datei ist nicht garantiert.',
'es': 'Se omiten los metadatos y otras pistas. No se garantiza un archivo menor.',
'fr-rFR': 'Les métadonnées et autres pistes sont omises. Un fichier plus petit n’est pas garanti.',
'it-rIT': 'Metadati e altre tracce vengono omessi. Un file più piccolo non è garantito.',
'pt-rBR': 'Metadados e outras faixas são omitidos. Um arquivo menor não é garantido.',
'ru': 'Метаданные и другие дорожки исключаются. Уменьшение файла не гарантируется.',
'pl-rPL': 'Metadane i inne ścieżki są pomijane. Mniejszy plik nie jest gwarantowany.',
'ro': 'Metadatele și alte piste sunt omise. Un fișier mai mic nu este garantat.',
'in': 'Metadata dan trek lain dihilangkan. Ukuran file lebih kecil tidak dijamin.',
'vi-rVN': 'Siêu dữ liệu và rãnh khác bị bỏ qua. Không bảo đảm tệp nhỏ hơn.',
'ja-rJP': 'メタデータと他のトラックは除かれます。ファイルサイズが小さくなるとは限りません。',
'ko-rKR': '메타데이터와 다른 트랙은 제외됩니다. 더 작은 파일은 보장되지 않습니다.',
'b+zh+Hans': '元数据和其他轨道会被省略。不保证文件更小。',
'b+zh+Hant': '中繼資料和其他軌道會被略過。不保證檔案更小。',
'ar': 'تُحذف البيانات الوصفية والمسارات الأخرى. لا نضمن ملفًا أصغر.',
'fa': 'فراداده و مسیرهای دیگر حذف می‌شوند. کوچک‌تر شدن فایل تضمین نمی‌شود.',
'hi': 'मेटाडेटा और अन्य ट्रैक नहीं रखे जाते। छोटा फ़ाइल आकार सुनिश्चित नहीं है।',
'th-rTH': 'ข้อมูลกำกับและแทร็กอื่นจะถูกละไว้ ไม่รับประกันว่าไฟล์จะเล็กลง',
'ha': 'Ana barin metadata da sauran waƙoƙi. Ba a tabbatar da ƙaramin fayil ba.',
'sw': 'Metadata na njia nyingine huachwa. Faili ndogo haijahakikishwa.',
}
keys.append('audio_metadata')
for qualifier, values in texts.items(): values.append(metadata[qualifier])
catalog_path = Path(__file__).with_name('catalog.json')
catalog = json.loads(catalog_path.read_text(encoding='utf-8'))
for key in keys:
    if key not in catalog['keys']:
        catalog['keys'].append(key)
        for values in catalog['locales'].values(): values.append('')
for tag, values in catalog['locales'].items():
    qualifier = {'en-US': 'en', 'id': 'in', 'zh-Hans': 'b+zh+Hans', 'zh-Hant': 'b+zh+Hant'}.get(tag)
    if qualifier is None:
        language, _, region = tag.partition('-')
        qualifier = language + ('-r' + region if region else '')
    for key, text in zip(keys, texts[qualifier]): values[catalog['keys'].index(key)] = text
catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('Audio output text catalog updated; run generate.py and generate.py --check.')
