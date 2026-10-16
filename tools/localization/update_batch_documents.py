"""Offline copy for document queues; no external service."""
import json
from pathlib import Path
KEYS = ['batch_documents','batch_documents_hint','batch_document_target','batch_document_sheet','batch_document_review']
ROWS = {
'en-US':'Batch documents / ZIP|Up to 20 files, 4 MiB per source: text, CSV/TSV and supported DOCX/XLSX content. PDF input uses the PDF tools. Each file is converted separately; originals are kept.|This target does not support this document type. Choose another target.|Choose a workbook sheet before converting.|Review conversion',
'tr-TR':'Toplu belgeler / ZIP|En fazla 20 dosya, kaynak başına 4 MiB: metin, CSV/TSV ve desteklenen DOCX/XLSX içeriği. PDF girdileri için PDF araçlarını kullanın. Dosyalar ayrı dönüştürülür; özgünler korunur.|Bu hedef bu belge türünü desteklemiyor. Başka bir hedef seçin.|Dönüştürmeden önce çalışma kitabından bir sayfa seçin.|Dönüştürmeyi incele',
'es':'Documentos por lotes / ZIP|Hasta 20 archivos, 4 MiB por origen: texto, CSV/TSV y contenido DOCX/XLSX compatible. Para PDF, use sus herramientas. Cada archivo se convierte por separado; se conservan los originales.|Este destino no admite este tipo de documento. Elija otro.|Elija una hoja del libro antes de convertir.|Revisar conversión',
'pt-BR':'Documentos em lote / ZIP|Até 20 arquivos, 4 MiB por origem: texto, CSV/TSV e conteúdo DOCX/XLSX compatível. Para PDF, use as ferramentas de PDF. Cada arquivo é convertido separadamente; originais são mantidos.|Este destino não aceita este tipo de documento. Escolha outro.|Escolha uma planilha antes de converter.|Revisar conversão',
'id':'Dokumen sekaligus / ZIP|Hingga 20 file, tiap sumber 4 MiB: teks, CSV/TSV dan isi DOCX/XLSX yang didukung. Untuk masukan PDF, gunakan alat PDF. File dikonversi terpisah; aslinya disimpan.|Target ini tidak mendukung jenis dokumen ini. Pilih target lain.|Pilih lembar buku kerja sebelum mengonversi.|Tinjau konversi',
'hi':'एक साथ दस्तावेज़ / ZIP|अधिकतम 20 फ़ाइलें, प्रति स्रोत 4 MiB: पाठ, CSV/TSV और समर्थित DOCX/XLSX सामग्री। PDF इनपुट के लिए PDF उपकरण उपयोग करें। हर फ़ाइल अलग बदलेगी; मूल सुरक्षित रहेंगे।|यह लक्ष्य इस दस्तावेज़ प्रकार को समर्थित नहीं करता। दूसरा लक्ष्य चुनें।|रूपांतरण से पहले कार्यपुस्तिका की शीट चुनें।|रूपांतरण की समीक्षा करें',
'ar':'مستندات متعددة / ZIP|حتى 20 ملفًا، 4 MiB لكل مصدر: نص وCSV/TSV ومحتوى DOCX/XLSX المدعوم. لمدخلات PDF استخدم أدوات PDF. تُحوّل الملفات منفردة وتُحفظ الأصول.|الهدف لا يدعم نوع المستند هذا. اختر هدفًا آخر.|اختر ورقة من المصنف قبل التحويل.|مراجعة التحويل',
'ru':'Пакет документов / ZIP|До 20 файлов, 4 МиБ на источник: текст, CSV/TSV и поддерживаемое содержимое DOCX/XLSX. Для входных PDF используйте инструменты PDF. Файлы преобразуются отдельно; оригиналы сохраняются.|Этот формат не поддерживает данный тип документа. Выберите другой.|Выберите лист книги перед преобразованием.|Проверить преобразование',
'ja-JP':'文書の一括変換 / ZIP|最大20ファイル、各入力4 MiB。テキスト、CSV/TSV、対応するDOCX/XLSX内容を変換します。PDF入力にはPDFツールを使用してください。個別に変換し元のファイルを保持します。|この出力形式は文書の種類に対応していません。別の形式を選択してください。|変換前にブックのシートを選択してください。|変換内容を確認',
'fr-FR':'Documents par lot / ZIP|20 fichiers maximum, 4 Mio par source : texte, CSV/TSV et contenu DOCX/XLSX pris en charge. Utilisez les outils PDF pour les PDF entrants. Conversion séparée ; originaux conservés.|Cette sortie ne prend pas en charge ce type de document. Choisissez-en une autre.|Choisissez une feuille du classeur avant de convertir.|Vérifier la conversion',
'de-DE':'Dokumente im Stapel / ZIP|Bis zu 20 Dateien, je Quelle 4 MiB: Text, CSV/TSV und unterstützte DOCX/XLSX-Inhalte. Für PDF-Eingaben die PDF-Werkzeuge nutzen. Dateien werden einzeln konvertiert; Originale bleiben erhalten.|Dieses Zielformat unterstützt diesen Dokumenttyp nicht. Ein anderes wählen.|Vor der Konvertierung ein Arbeitsblatt wählen.|Konvertierung prüfen',
'pl-PL':'Dokumenty seryjnie / ZIP|Do 20 plików, 4 MiB na źródło: tekst, CSV/TSV i obsługiwana zawartość DOCX/XLSX. Dla wejściowych PDF użyj narzędzi PDF. Pliki są konwertowane oddzielnie; oryginały pozostają.|Ten format nie obsługuje tego typu dokumentu. Wybierz inny.|Przed konwersją wybierz arkusz skoroszytu.|Sprawdź konwersję',
'ko-KR':'문서 일괄 변환 / ZIP|최대 20개 파일, 원본당 4 MiB: 텍스트, CSV/TSV 및 지원되는 DOCX/XLSX 내용. PDF 입력은 PDF 도구를 이용하세요. 개별 변환하며 원본은 유지합니다.|이 출력 형식은 이 문서 유형을 지원하지 않습니다. 다른 형식을 선택하세요.|변환 전에 통합 문서의 시트를 선택하세요.|변환 검토',
'zh-Hans':'批量文档 / ZIP|最多20个文件，每个源文件4 MiB：文本、CSV/TSV及支持的DOCX/XLSX内容。PDF输入请使用PDF工具。逐个转换，保留原文件。|此目标不支持这种文档类型，请选择其他目标。|转换前请选择工作簿中的工作表。|查看转换',
'zh-Hant':'批次文件 / ZIP|最多20個檔案，每個來源4 MiB：文字、CSV/TSV及支援的DOCX/XLSX內容。PDF輸入請使用PDF工具。逐一轉換並保留原始檔。|此目標不支援這種文件類型，請選擇其他目標。|轉換前請選擇活頁簿中的工作表。|檢查轉換',
'it-IT':'Documenti in serie / ZIP|Fino a 20 file, 4 MiB per origine: testo, CSV/TSV e contenuti DOCX/XLSX supportati. Per i PDF in ingresso usa gli strumenti PDF. Conversione separata; originali conservati.|Questo formato non supporta questo tipo di documento. Scegline un altro.|Scegli un foglio della cartella prima della conversione.|Verifica conversione',
'th-TH':'เอกสารแบบกลุ่ม / ZIP|สูงสุด 20 ไฟล์ ไฟล์ต้นฉบับละ 4 MiB: ข้อความ CSV/TSV และเนื้อหา DOCX/XLSX ที่รองรับ ใช้เครื่องมือ PDF สำหรับไฟล์ PDF แปลงทีละไฟล์และเก็บต้นฉบับไว้|รูปแบบปลายทางนี้ไม่รองรับเอกสารชนิดนี้ โปรดเลือกรูปแบบอื่น|เลือกแผ่นงานก่อนแปลง|ตรวจสอบการแปลง',
'vi-VN':'Tài liệu hàng loạt / ZIP|Tối đa 20 tệp, mỗi nguồn 4 MiB: văn bản, CSV/TSV và nội dung DOCX/XLSX được hỗ trợ. Với PDF đầu vào, dùng công cụ PDF. Chuyển đổi riêng từng tệp; giữ nguyên bản gốc.|Đích này không hỗ trợ loại tài liệu này. Hãy chọn đích khác.|Chọn trang tính trước khi chuyển đổi.|Xem lại chuyển đổi',
'fa':'تبدیل گروهی اسناد / ZIP|حداکثر ۲۰ فایل، هر منبع 4 MiB: متن، CSV/TSV و محتوای پشتیبانی‌شده DOCX/XLSX. برای ورودی PDF از ابزارهای PDF استفاده کنید. فایل‌ها جدا تبدیل و اصل‌ها حفظ می‌شوند.|این مقصد از این نوع سند پشتیبانی نمی‌کند. مقصد دیگری انتخاب کنید.|پیش از تبدیل یک برگه از کارپوشه انتخاب کنید.|بررسی تبدیل',
'sw':'Nyaraka kwa pamoja / ZIP|Hadi faili 20, kila chanzo 4 MiB: maandishi, CSV/TSV na maudhui ya DOCX/XLSX yanayotumika. Tumia zana za PDF kwa ingizo la PDF. Faili hubadilishwa moja moja; asili huhifadhiwa.|Lengo hili halitumii aina hii ya hati. Chagua lingine.|Chagua laha ya kitabu kabla ya kubadilisha.|Kagua ubadilishaji',
'ha':'Takardu a jere / ZIP|Fayiloli har 20, kowane tushe 4 MiB: rubutu, CSV/TSV da abun DOCX/XLSX da ake tallafawa. Yi amfani da kayan PDF don shigarwar PDF. Ana sauya dabam; asali yana nan.|Wannan manufa ba ta tallafa wa irin takardar nan ba. Zaɓi wata.|Zaɓi takardar littafin aiki kafin sauyawa.|Duba sauyawa',
'ro':'Documente în lot / ZIP|Maximum 20 de fișiere, 4 MiB per sursă: text, CSV/TSV și conținut DOCX/XLSX acceptat. Pentru PDF la intrare, folosește uneltele PDF. Conversie separată; originalele rămân.|Destinația nu acceptă acest tip de document. Alege alta.|Alege o foaie a registrului înainte de conversie.|Verifică conversia',
}
OUTPUT_LIMIT={
'en-US':'The batch output would exceed 512 MiB. No file was shortened.',
'tr-TR':'Toplu çıktı 512 MiB sınırını aşacak. Hiçbir dosya kısaltılmadı.',
'es':'La salida del lote superaría 512 MiB. No se recortó ningún archivo.',
'pt-BR':'A saída do lote excederia 512 MiB. Nenhum arquivo foi encurtado.',
'id':'Total keluaran akan melebihi 512 MiB. Tidak ada file yang dipotong.',
'hi':'समूह आउटपुट 512 MiB से अधिक होगा। कोई फ़ाइल छोटी नहीं की गई।',
'ar':'سيتجاوز إجمالي الإخراج 512 MiB. لم يُختصر أي ملف.',
'ru':'Общий размер результата превысит 512 МиБ. Файлы не обрезаны.',
'ja-JP':'出力の合計が512 MiBを超えます。ファイルは短縮されていません。',
'fr-FR':'La sortie du lot dépasserait 512 Mio. Aucun fichier n’a été tronqué.',
'de-DE':'Die Stapelausgabe würde 512 MiB überschreiten. Keine Datei wurde gekürzt.',
'pl-PL':'Łączny wynik przekroczyłby 512 MiB. Żaden plik nie został skrócony.',
'ko-KR':'전체 출력이 512 MiB를 초과합니다. 파일을 잘라내지 않았습니다.',
'zh-Hans':'批量输出将超过512 MiB。未截短任何文件。',
'zh-Hant':'批次輸出將超過512 MiB。未截短任何檔案。',
'it-IT':'L’output del lotto supererebbe 512 MiB. Nessun file è stato accorciato.',
'th-TH':'ผลลัพธ์รวมจะเกิน 512 MiB ไม่มีไฟล์ใดถูกตัดให้สั้นลง',
'vi-VN':'Tổng đầu ra sẽ vượt 512 MiB. Không tệp nào bị cắt ngắn.',
'fa':'مجموع خروجی از 512 MiB بیشتر می‌شود. هیچ فایلی کوتاه نشده است.',
'sw':'Jumla ya matokeo ingezidi 512 MiB. Hakuna faili iliyofupishwa.',
'ha':'Jimillar fitarwa za ta wuce 512 MiB. Ba a rage tsawon wani fayil ba.',
'ro':'Ieșirea lotului ar depăși 512 MiB. Niciun fișier nu a fost scurtat.',
}
assert set(OUTPUT_LIMIT)==set(ROWS)
KEYS.append('batch_documents_output_limit')
ROWS={locale:row+'|'+OUTPUT_LIMIT[locale] for locale,row in ROWS.items()}
p=Path(__file__).with_name('catalog.json'); c=json.loads(p.read_text(encoding='utf-8'))
assert set(ROWS)==set(c['locales'])
for key in KEYS:
    if key not in c['keys']:
        c['keys'].append(key)
        for values in c['locales'].values(): values.append('')
for locale,row in ROWS.items():
    values=row.split('|'); assert len(values)==len(KEYS)
    for key,value in zip(KEYS,values): c['locales'][locale][c['keys'].index(key)]=value
p.write_text(json.dumps(c,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
