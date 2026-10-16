"""Local RTL export disclosures. No network or runtime translation."""
import json
from pathlib import Path
rows = {
'en-US': ['Editable export supports Latin, Arabic and Persian text. For unverified scripts, emoji or direction controls, choose Keep appearance.', 'RTL text uses grouped word and direction-run boxes, each edited separately. Longer edits may need repositioning.'],
 'tr-TR': ['Düzenlenebilir aktarım Latin, Arapça ve Farsça metni destekler. Doğrulanmamış yazılar, emoji veya yön kontrolleri için Görünümü koru seçin.', 'Sağdan sola metin, ayrı düzenlenen kelime ve yön parçası kutularında gruplanır. Uzun değişikliklerde konumları ayarlamak gerekebilir.'],
 'es': ['La exportación editable admite texto latino, árabe y persa. Para escrituras no verificadas, emojis o controles de dirección, elige Conservar apariencia.', 'El texto de derecha a izquierda usa cuadros agrupados de palabras y segmentos de dirección, editados por separado. Los cambios largos pueden requerir ajustar posiciones.'],
 'pt-BR': ['A exportação editável aceita texto latino, árabe e persa. Para escritas não verificadas, emojis ou controles de direção, escolha Preservar aparência.', 'O texto da direita para a esquerda usa caixas agrupadas de palavras e trechos de direção, editadas separadamente. Alterações longas podem exigir reposicionamento.'],
 'id': ['Ekspor yang dapat diedit mendukung teks Latin, Arab dan Persia. Untuk aksara yang belum diverifikasi, emoji atau kontrol arah, pilih Pertahankan tampilan.', 'Teks kanan ke kiri memakai kotak kata dan bagian arah yang dikelompokkan, masing-masing diedit terpisah. Edit panjang mungkin perlu penyesuaian posisi.'],
 'hi': ['संपादन योग्य निर्यात लैटिन, अरबी और फ़ारसी पाठ का समर्थन करता है। अप्रमाणित लिपियों, इमोजी या दिशा नियंत्रण के लिए दिखावट बनाए रखें चुनें।', 'दाएँ से बाएँ पाठ में शब्दों और दिशा खंडों के समूहित बॉक्स अलग-अलग संपादित होते हैं। लंबे बदलावों के बाद स्थान बदलना पड़ सकता है।'],
 'ar': ['يدعم التصدير القابل للتحرير النص اللاتيني والعربي والفارسي. للكتابات غير المتحقق منها أو الرموز التعبيرية أو عناصر التحكم بالاتجاه، اختر الحفاظ على المظهر.', 'يستخدم النص من اليمين إلى اليسار مربعات مجمعة للكلمات ومقاطع الاتجاه، تُحرر كل منها منفصلة. قد تتطلب التعديلات الطويلة تغيير المواضع.'],
 'ru': ['Редактируемый экспорт поддерживает латиницу, арабский и персидский текст. Для непроверенных письменностей, эмодзи и знаков направления выберите сохранение внешнего вида.', 'Текст справа налево состоит из сгруппированных блоков слов и направленных фрагментов, редактируемых отдельно. Длинные изменения могут потребовать перемещения блоков.'],
 'ja-JP': ['編集可能な出力はラテン文字、アラビア語、ペルシャ語に対応しています。未検証の文字、絵文字、方向制御には外観を保持を選んでください。', '右から左のテキストは、単語と方向別の断片をグループ化したボックスで個別に編集します。長い変更では位置の調整が必要な場合があります。'],
 'fr-FR': ['L’export modifiable prend en charge les textes latins, arabes et persans. Pour les écritures non vérifiées, émojis ou contrôles de direction, choisissez Conserver l’apparence.', 'Le texte de droite à gauche utilise des cadres groupés de mots et de segments directionnels, modifiés séparément. Les changements longs peuvent nécessiter un repositionnement.'],
 'de-DE': ['Bearbeitbarer Export unterstützt lateinischen, arabischen und persischen Text. Für ungeprüfte Schriften, Emoji oder Richtungssteuerzeichen Darstellung erhalten wählen.', 'Text von rechts nach links verwendet gruppierte Wort- und Richtungsabschnittsfelder, die einzeln bearbeitet werden. Längere Änderungen können ein Verschieben erfordern.'],
 'pl-PL': ['Edytowalny eksport obsługuje tekst łaciński, arabski i perski. Dla niezweryfikowanych pism, emoji lub znaków kierunku wybierz Zachowaj wygląd.', 'Tekst od prawej do lewej używa zgrupowanych pól słów i fragmentów kierunku, edytowanych osobno. Dłuższe zmiany mogą wymagać przesunięcia pól.'],
 'ko-KR': ['편집 가능한 내보내기는 라틴 문자, 아랍어, 페르시아어를 지원합니다. 검증되지 않은 문자, 이모지, 방향 제어에는 모양 유지를 선택하세요.', '오른쪽에서 왼쪽으로 쓰는 텍스트는 단어와 방향별 조각을 묶은 상자에서 각각 편집합니다. 긴 변경 후에는 위치를 조정해야 할 수 있습니다.'],
 'zh-Hans': ['可编辑导出支持拉丁文字、阿拉伯语和波斯语。未验证的文字、表情或方向控制请使用保留外观。', '从右到左的文字使用分组的单词和方向片段文本框，分别编辑。较长的修改可能需要调整位置。'],
 'zh-Hant': ['可編輯匯出支援拉丁文字、阿拉伯語和波斯語。未驗證的文字、表情或方向控制請使用保留外觀。', '從右到左的文字使用群組化的單字及方向片段文字方塊，分別編輯。較長的修改可能需要調整位置。'],
 'it-IT': ['L’esportazione modificabile supporta testo latino, arabo e persiano. Per scritture non verificate, emoji o controlli di direzione, scegli Mantieni aspetto.', 'Il testo da destra a sinistra usa riquadri raggruppati di parole e segmenti di direzione, modificabili separatamente. Le modifiche lunghe possono richiedere un riposizionamento.'],
 'th-TH': ['การส่งออกแบบแก้ไขได้รองรับข้อความละติน อาหรับและเปอร์เซีย สำหรับอักษรที่ยังไม่ตรวจสอบ อีโมจิหรือการควบคุมทิศทาง ให้เลือกคงรูปลักษณ์', 'ข้อความจากขวาไปซ้ายใช้กล่องคำและส่วนตามทิศทางที่จัดกลุ่ม โดยแก้ไขแต่ละกล่องแยกกัน การแก้ไขที่ยาวขึ้นอาจต้องปรับตำแหน่ง'],
 'vi-VN': ['Xuất có thể sửa hỗ trợ chữ Latin, tiếng Ả Rập và Ba Tư. Với chữ chưa được xác minh, emoji hoặc ký tự điều khiển hướng, chọn Giữ hình thức.', 'Văn bản từ phải sang trái dùng các hộp từ và đoạn theo hướng được nhóm, mỗi hộp sửa riêng. Thay đổi dài có thể cần chỉnh vị trí.'],
 'fa': ['صدور قابل ویرایش از متن لاتین، عربی و فارسی پشتیبانی می‌کند. برای خط‌های بررسی‌نشده، ایموجی یا کنترل جهت، حفظ ظاهر را انتخاب کنید.', 'متن راست‌به‌چپ از کادرهای گروه‌بندی‌شدهٔ واژه و بخش‌های جهت استفاده می‌کند که جدا ویرایش می‌شوند. تغییرات طولانی ممکن است به جابه‌جایی نیاز داشته باشند.'],
 'sw': ['Uhamishaji unaoharirika unaauni maandishi ya Kilatini, Kiarabu na Kiajemi. Kwa hati ambazo hazijathibitishwa, emoji au vidhibiti vya mwelekeo, chagua Hifadhi mwonekano.', 'Maandishi kutoka kulia kwenda kushoto hutumia visanduku vya maneno na sehemu za mwelekeo vilivyopangwa kwa vikundi, vikihaririwa kando. Mabadiliko marefu yanaweza kuhitaji kuhamisha visanduku.'],
 'ha': ['Fitarwa mai gyaruwa tana goyon bayan rubutun Latin, Larabci da Farisanci. Don rubutun da ba a tantance ba, emoji ko alamun alkibla, zaɓi Kiyaye kamanni.', 'Rubutu daga dama zuwa hagu yana amfani da rukunin akwatunan kalmomi da sassan alkibla, ana gyara kowanne dabam. Dogayen canje-canje na iya buƙatar matsar da su.'],
 'ro': ['Exportul editabil acceptă text latin, arab și persan. Pentru scrieri neverificate, emoji sau controale de direcție, alege Păstrează aspectul.', 'Textul de la dreapta la stânga folosește casete grupate pentru cuvinte și segmente direcționale, editate separat. Modificările lungi pot necesita repoziționare.'],
}
p=Path(__file__).with_name('catalog.json')
c=json.loads(p.read_text(encoding='utf-8'))
assert set(c['locales'])==set(rows)
for column,key in enumerate(['pptx_editable_unsupported','pptx_rtl_words_hint']):
    if key not in c['keys']:
        c['keys'].append(key)
        for values in c['locales'].values(): values.append('')
    index=c['keys'].index(key)
    for locale,values in c['locales'].items(): values[index]=rows[locale][column]
p.write_text(json.dumps(c,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
