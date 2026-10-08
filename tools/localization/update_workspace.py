"""Local, repeatable UI label additions. No remote translation or runtime dependency."""
import json
from pathlib import Path
keys = "home_start open_file new_create documents_category resume_work view_all change_file workspace_help other_actions save_converted_copy".split()
labels = {
"en-US":"Start with a file|Open file|Create new|Documents|Continue working|View all|Change|Details and help|Other actions|Save converted copy",
"tr-TR":"Bir dosyayla başlayın|Dosya aç|Yeni oluştur|Belgeler|Devam et|Tümünü gör|Değiştir|Ayrıntılar ve yardım|Diğer işlemler|Dönüştürülmüş kopyayı kaydet",
"es":"Empieza con un archivo|Abrir archivo|Crear nuevo|Documentos|Continuar|Ver todo|Cambiar|Detalles y ayuda|Otras acciones|Guardar copia convertida",
"pt-BR":"Comece com um arquivo|Abrir arquivo|Criar novo|Documentos|Continuar|Ver tudo|Alterar|Detalhes e ajuda|Outras ações|Salvar cópia convertida",
"id":"Mulai dengan berkas|Buka berkas|Buat baru|Dokumen|Lanjutkan|Lihat semua|Ubah|Detail dan bantuan|Tindakan lain|Simpan salinan hasil konversi",
"hi":"फ़ाइल से शुरू करें|फ़ाइल खोलें|नया बनाएँ|दस्तावेज़|काम जारी रखें|सभी देखें|बदलें|विवरण और सहायता|अन्य कार्य|परिवर्तित प्रति सहेजें",
"ar":"ابدأ بملف|فتح ملف|إنشاء جديد|المستندات|متابعة العمل|عرض الكل|تغيير|التفاصيل والمساعدة|إجراءات أخرى|حفظ نسخة محوّلة",
"ru":"Начните с файла|Открыть файл|Создать|Документы|Продолжить работу|Показать всё|Изменить|Подробности и помощь|Другие действия|Сохранить преобразованную копию",
"ja-JP":"ファイルから始める|ファイルを開く|新規作成|ドキュメント|作業を続ける|すべて表示|変更|詳細とヘルプ|その他の操作|変換したコピーを保存",
"fr-FR":"Commencez avec un fichier|Ouvrir un fichier|Créer|Documents|Continuer|Tout voir|Changer|Détails et aide|Autres actions|Enregistrer la copie convertie",
"de-DE":"Mit einer Datei beginnen|Datei öffnen|Neu erstellen|Dokumente|Weiterarbeiten|Alle anzeigen|Ändern|Details und Hilfe|Weitere Aktionen|Konvertierte Kopie speichern",
"pl-PL":"Zacznij od pliku|Otwórz plik|Utwórz nowy|Dokumenty|Kontynuuj pracę|Pokaż wszystko|Zmień|Szczegóły i pomoc|Inne działania|Zapisz przekonwertowaną kopię",
"ko-KR":"파일로 시작하기|파일 열기|새로 만들기|문서|작업 계속하기|모두 보기|변경|세부 정보 및 도움말|다른 작업|변환된 사본 저장",
"zh-Hans":"从文件开始|打开文件|新建|文档|继续工作|查看全部|更换|详情与帮助|其他操作|保存转换后的副本",
"zh-Hant":"從檔案開始|開啟檔案|新增|文件|繼續工作|檢視全部|更換|詳細資料與說明|其他操作|儲存轉換後的副本",
"it-IT":"Inizia da un file|Apri file|Crea nuovo|Documenti|Continua il lavoro|Mostra tutto|Cambia|Dettagli e aiuto|Altre azioni|Salva copia convertita",
"th-TH":"เริ่มต้นด้วยไฟล์|เปิดไฟล์|สร้างใหม่|เอกสาร|ทำงานต่อ|ดูทั้งหมด|เปลี่ยน|รายละเอียดและความช่วยเหลือ|การดำเนินการอื่น|บันทึกสำเนาที่แปลงแล้ว",
"vi-VN":"Bắt đầu với một tệp|Mở tệp|Tạo mới|Tài liệu|Tiếp tục|Xem tất cả|Đổi|Chi tiết và trợ giúp|Thao tác khác|Lưu bản sao đã chuyển đổi",
"fa":"با یک فایل شروع کنید|باز کردن فایل|ایجاد جدید|اسناد|ادامه کار|نمایش همه|تغییر|جزئیات و راهنما|عملیات دیگر|ذخیره نسخه تبدیل‌شده",
"sw":"Anza na faili|Fungua faili|Unda mpya|Nyaraka|Endelea kufanya kazi|Ona zote|Badilisha|Maelezo na msaada|Vitendo vingine|Hifadhi nakala iliyobadilishwa",
"ha":"Fara da fayil|Buɗe fayil|Ƙirƙiri sabo|Takardu|Ci gaba da aiki|Duba duka|Canja|Bayani da taimako|Sauran ayyuka|Ajiye kwafin da aka sauya",
"ro":"Începe cu un fișier|Deschide fișierul|Creează nou|Documente|Continuă lucrul|Vezi tot|Schimbă|Detalii și ajutor|Alte acțiuni|Salvează copia convertită",
}
extra_keys = "create_result workspace_clips workspace_text audio_category workspace_output".split()
extras = {
"en-US":"Create result|Clips|Text|Audio|Output", "tr-TR":"Sonucu oluştur|Klipler|Yazı|Ses|Çıktı",
"es":"Crear resultado|Clips|Texto|Audio|Salida", "pt-BR":"Criar resultado|Clipes|Texto|Áudio|Saída",
"id":"Buat hasil|Klip|Teks|Audio|Hasil", "hi":"परिणाम बनाएँ|क्लिप|टेक्स्ट|ऑडियो|आउटपुट",
"ar":"إنشاء النتيجة|المقاطع|النص|الصوت|الإخراج", "ru":"Создать результат|Клипы|Текст|Аудио|Вывод",
"ja-JP":"結果を作成|クリップ|テキスト|音声|出力", "fr-FR":"Créer le résultat|Clips|Texte|Audio|Sortie",
"de-DE":"Ergebnis erstellen|Clips|Text|Audio|Ausgabe", "pl-PL":"Utwórz wynik|Klipy|Tekst|Dźwięk|Wynik",
"ko-KR":"결과 만들기|클립|텍스트|오디오|출력", "zh-Hans":"生成结果|片段|文字|音频|输出",
"zh-Hant":"產生結果|片段|文字|音訊|輸出", "it-IT":"Crea risultato|Clip|Testo|Audio|Output",
"th-TH":"สร้างผลลัพธ์|คลิป|ข้อความ|เสียง|ผลลัพธ์", "vi-VN":"Tạo kết quả|Đoạn|Văn bản|Âm thanh|Đầu ra",
"fa":"ایجاد نتیجه|کلیپ‌ها|متن|صدا|خروجی", "sw":"Unda matokeo|Klipu|Maandishi|Sauti|Matokeo",
"ha":"Ƙirƙiri sakamako|Klip|Rubutu|Sauti|Sakamako", "ro":"Creează rezultatul|Clipuri|Text|Audio|Ieșire",
}
keys += extra_keys
labels = {tag: text + "|" + extras[tag] for tag, text in labels.items()}
summary_keys = "photo_tool_subtitle edit_tool_subtitle audio_subtitle documents_subtitle".split()
summaries = {
"en-US":"Convert and compress images|Edit, convert and compress videos|Trim, merge and convert audio|Read PDFs, text and tables",
"tr-TR":"Görselleri dönüştürün ve sıkıştırın|Videoları düzenleyin, dönüştürün ve sıkıştırın|Sesi kesin, birleştirin ve dönüştürün|PDF, metin ve tabloları okuyun",
"es":"Convierte y comprime imágenes|Edita, convierte y comprime vídeos|Recorta, une y convierte audio|Lee PDF, texto y tablas",
"pt-BR":"Converta e comprima imagens|Edite, converta e comprima vídeos|Corte, una e converta áudio|Leia PDFs, textos e tabelas",
"id":"Konversi dan kompres gambar|Edit, konversi dan kompres video|Potong, gabungkan dan konversi audio|Baca PDF, teks dan tabel",
"hi":"चित्र बदलें और संपीड़ित करें|वीडियो संपादित, परिवर्तित और संपीड़ित करें|ऑडियो काटें, जोड़ें और बदलें|PDF, टेक्स्ट और तालिकाएँ पढ़ें",
"ar":"تحويل الصور وضغطها|تحرير الفيديو وتحويله وضغطه|قص الصوت ودمجه وتحويله|قراءة PDF والنصوص والجداول",
"ru":"Преобразуйте и сжимайте изображения|Редактируйте, преобразуйте и сжимайте видео|Обрезайте, объединяйте и преобразуйте аудио|Читайте PDF, текст и таблицы",
"ja-JP":"画像を変換・圧縮|動画を編集・変換・圧縮|音声をトリミング・結合・変換|PDF、テキスト、表を読む",
"fr-FR":"Convertissez et compressez des images|Modifiez, convertissez et compressez des vidéos|Coupez, fusionnez et convertissez l’audio|Lisez des PDF, du texte et des tableaux",
"de-DE":"Bilder konvertieren und komprimieren|Videos bearbeiten, konvertieren und komprimieren|Audio kürzen, zusammenfügen und konvertieren|PDFs, Texte und Tabellen lesen",
"pl-PL":"Konwertuj i kompresuj obrazy|Edytuj, konwertuj i kompresuj filmy|Przycinaj, łącz i konwertuj dźwięk|Czytaj PDF, tekst i tabele",
"ko-KR":"이미지 변환 및 압축|동영상 편집, 변환 및 압축|오디오 자르기, 병합 및 변환|PDF, 텍스트 및 표 읽기",
"zh-Hans":"转换和压缩图片|编辑、转换和压缩视频|裁剪、合并和转换音频|阅读 PDF、文本和表格",
"zh-Hant":"轉換和壓縮圖片|編輯、轉換和壓縮影片|裁剪、合併和轉換音訊|閱讀 PDF、文字和表格",
"it-IT":"Converti e comprimi immagini|Modifica, converti e comprimi video|Taglia, unisci e converti audio|Leggi PDF, testi e tabelle",
"th-TH":"แปลงและบีบอัดรูปภาพ|แก้ไข แปลงและบีบอัดวิดีโอ|ตัด รวมและแปลงเสียง|อ่าน PDF ข้อความและตาราง",
"vi-VN":"Chuyển đổi và nén ảnh|Chỉnh sửa, chuyển đổi và nén video|Cắt, ghép và chuyển đổi âm thanh|Đọc PDF, văn bản và bảng",
"fa":"تبدیل و فشرده‌سازی تصاویر|ویرایش، تبدیل و فشرده‌سازی ویدیو|برش، ادغام و تبدیل صدا|خواندن PDF، متن و جدول",
"sw":"Badilisha na bana picha|Hariri, badilisha na bana video|Kata, unganisha na badilisha sauti|Soma PDF, maandishi na majedwali",
"ha":"Sauya kuma matsa hotuna|Gyara, sauya kuma matsa bidiyo|Yanke, haɗa kuma sauya sauti|Karanta PDF, rubutu da tebura",
"ro":"Convertește și comprimă imagini|Editează, convertește și comprimă videoclipuri|Taie, unește și convertește audio|Citește PDF, text și tabele",
}
keys += summary_keys
labels = {tag: text + "|" + summaries[tag] for tag, text in labels.items()}
path = Path(__file__).with_name("catalog.json")
catalog = json.loads(path.read_text(encoding="utf-8"))
for tag, text in labels.items():
    values = text.split("|")
    assert len(values) == len(keys)
    for key, value in zip(keys, values):
        if key not in catalog["keys"]:
            catalog["keys"].append(key)
        index = catalog["keys"].index(key)
        rows = catalog["locales"][tag]
        if index == len(rows): rows.append(value)
        else: rows[index] = value
path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
