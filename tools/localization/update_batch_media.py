"""Copy for independent audio/video batch queues; no network calls."""
import json
from pathlib import Path
KEYS=['batch_video','batch_audio','batch_tools','batch_media_hint','batch_add_media']
ROWS={
'en-US':'Batch video / ZIP|Batch audio / ZIP|Batch tools|Up to 20 files and 512 MiB total; 10 minutes per file. Each whole file is converted separately. Originals are kept.|Add files',
'tr-TR':'Toplu video / ZIP|Toplu ses / ZIP|Toplu işlemler|En fazla 20 dosya, toplam 512 MiB ve dosya başına 10 dakika. Her dosyanın tamamı ayrı dönüştürülür. Özgün dosyalar korunur.|Dosya ekle',
'es':'Vídeo por lotes / ZIP|Audio por lotes / ZIP|Herramientas por lotes|Hasta 20 archivos y 512 MiB en total; 10 minutos por archivo. Cada archivo completo se convierte por separado. Se conservan los originales.|Añadir archivos',
'pt-BR':'Vídeo em lote / ZIP|Áudio em lote / ZIP|Ferramentas em lote|Até 20 arquivos e 512 MiB no total; 10 minutos por arquivo. Cada arquivo inteiro é convertido separadamente. Os originais são mantidos.|Adicionar arquivos',
'id':'Video sekaligus / ZIP|Audio sekaligus / ZIP|Alat pemrosesan sekaligus|Maksimal 20 file dan total 512 MiB; 10 menit per file. Setiap file utuh dikonversi terpisah. File asli tetap disimpan.|Tambah file',
'hi':'एक साथ वीडियो / ZIP|एक साथ ऑडियो / ZIP|सामूहिक रूपांतरण उपकरण|अधिकतम 20 फ़ाइलें और कुल 512 MiB; प्रति फ़ाइल 10 मिनट। हर पूरी फ़ाइल अलग बदली जाती है। मूल फ़ाइलें सुरक्षित रहती हैं।|फ़ाइलें जोड़ें',
'ar':'فيديو متعدد / ZIP|صوت متعدد / ZIP|أدوات التحويل المتعدد|حتى 20 ملفًا و512 MiB إجمالًا؛ 10 دقائق لكل ملف. يُحوّل كل ملف كاملًا على حدة وتُحفظ الأصول.|إضافة ملفات',
'ru':'Пакет видео / ZIP|Пакет аудио / ZIP|Пакетные инструменты|До 20 файлов и 512 МиБ суммарно; 10 минут на файл. Каждый файл преобразуется целиком и отдельно. Оригиналы сохраняются.|Добавить файлы',
'ja-JP':'動画の一括変換 / ZIP|音声の一括変換 / ZIP|一括処理ツール|最大20ファイル、合計512 MiB、各ファイル10分まで。各ファイル全体を個別に変換します。元のファイルは保持されます。|ファイルを追加',
'fr-FR':'Vidéos par lot / ZIP|Audio par lot / ZIP|Outils par lot|20 fichiers et 512 Mio au total maximum ; 10 minutes par fichier. Chaque fichier entier est converti séparément. Les originaux sont conservés.|Ajouter des fichiers',
'de-DE':'Videos im Stapel / ZIP|Audio im Stapel / ZIP|Stapelwerkzeuge|Bis zu 20 Dateien und insgesamt 512 MiB; 10 Minuten je Datei. Jede ganze Datei wird einzeln konvertiert. Originale bleiben erhalten.|Dateien hinzufügen',
'pl-PL':'Wideo seryjnie / ZIP|Audio seryjnie / ZIP|Narzędzia seryjne|Do 20 plików i łącznie 512 MiB; 10 minut na plik. Każdy cały plik jest konwertowany oddzielnie. Oryginały są zachowywane.|Dodaj pliki',
'ko-KR':'동영상 일괄 변환 / ZIP|오디오 일괄 변환 / ZIP|일괄 처리 도구|최대 20개 파일, 총 512 MiB, 파일당 10분. 각 파일 전체를 개별 변환합니다. 원본은 유지됩니다.|파일 추가',
'zh-Hans':'批量视频 / ZIP|批量音频 / ZIP|批量工具|最多20个文件，总计512 MiB，每个文件10分钟。逐个转换完整文件，保留原文件。|添加文件',
'zh-Hant':'批次影片 / ZIP|批次音訊 / ZIP|批次工具|最多20個檔案，總計512 MiB，每個檔案10分鐘。逐一轉換完整檔案，保留原始檔案。|新增檔案',
'it-IT':'Video in serie / ZIP|Audio in serie / ZIP|Strumenti in serie|Fino a 20 file e 512 MiB totali; 10 minuti per file. Ogni file intero viene convertito separatamente. Gli originali vengono conservati.|Aggiungi file',
'th-TH':'วิดีโอแบบกลุ่ม / ZIP|เสียงแบบกลุ่ม / ZIP|เครื่องมือแบบกลุ่ม|สูงสุด 20 ไฟล์ รวม 512 MiB และไฟล์ละ 10 นาที แปลงแต่ละไฟล์ทั้งไฟล์แยกกันและเก็บต้นฉบับไว้|เพิ่มไฟล์',
'vi-VN':'Video hàng loạt / ZIP|Âm thanh hàng loạt / ZIP|Công cụ hàng loạt|Tối đa 20 tệp và tổng 512 MiB; mỗi tệp 10 phút. Chuyển đổi riêng toàn bộ từng tệp. Giữ nguyên tệp gốc.|Thêm tệp',
'fa':'تبدیل گروهی ویدیو / ZIP|تبدیل گروهی صدا / ZIP|ابزارهای گروهی|حداکثر ۲۰ فایل و در مجموع 512 MiB؛ هر فایل ۱۰ دقیقه. هر فایل کامل و جداگانه تبدیل می‌شود. اصل فایل‌ها حفظ می‌شود.|افزودن فایل',
'sw':'Video kwa pamoja / ZIP|Sauti kwa pamoja / ZIP|Zana za kazi za pamoja|Hadi faili 20 na jumla ya 512 MiB; dakika 10 kwa faili. Kila faili nzima hubadilishwa peke yake. Faili asili huhifadhiwa.|Ongeza faili',
'ha':'Bidiyo a jere / ZIP|Sauti a jere / ZIP|Kayan aikin jere|Fayiloli har 20 da jimillar 512 MiB; minti 10 ga kowane fayil. Ana sauya kowane fayil gaba ɗaya dabam. Ana adana na asali.|Ƙara fayiloli',
'ro':'Video în lot / ZIP|Audio în lot / ZIP|Instrumente în lot|Cel mult 20 de fișiere și 512 MiB în total; 10 minute per fișier. Fiecare fișier întreg este convertit separat. Originalele sunt păstrate.|Adaugă fișiere',
}
p=Path(__file__).with_name('catalog.json'); c=json.loads(p.read_text(encoding='utf-8'))
assert set(ROWS)==set(c['locales'])
for key in KEYS:
 if key not in c['keys']:
  c['keys'].append(key)
  for values in c['locales'].values(): values.append('')
for locale,row in ROWS.items():
 values=row.split('|'); assert len(values)==len(KEYS)
 for key,value in zip(KEYS,values):c['locales'][locale][c['keys'].index(key)]=value
p.write_text(json.dumps(c,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
