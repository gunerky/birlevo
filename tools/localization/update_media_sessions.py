"""Draft local-session UI copy for 22 languages; no network or model calls."""
import json
from pathlib import Path

path = Path(__file__).with_name('catalog.json')
catalog = json.loads(path.read_text(encoding='utf-8'))
keys = ['media_session_hint', 'clear_session', 'session_saved', 'session_saving']
rows = {
'en-US': 'Sources and settings are saved on this device. Back keeps your work. After an app restart, create the output again; unfinished processing does not resume.|Clear work|Work saved|Saving work…',
'tr-TR': 'Kaynaklar ve ayarlar bu cihazda kaydedilir. Geri düğmesi çalışmayı korur. Uygulama yeniden açılınca çıktıyı tekrar oluşturun; yarım kalan işlem devam etmez.|Çalışmayı temizle|Çalışma kaydedildi|Çalışma kaydediliyor…',
'es': 'Las fuentes y los ajustes se guardan en este dispositivo. Atrás conserva el trabajo. Tras reiniciar la app, crea la salida otra vez; los procesos incompletos no se reanudan.|Borrar trabajo|Trabajo guardado|Guardando trabajo…',
'pt-BR': 'Fontes e ajustes são salvos neste dispositivo. Voltar mantém o trabalho. Após reiniciar o app, crie a saída novamente; o processamento incompleto não continua.|Limpar trabalho|Trabalho salvo|Salvando trabalho…',
'id': 'Sumber dan pengaturan disimpan di perangkat ini. Kembali mempertahankan pekerjaan. Setelah aplikasi dimulai ulang, buat keluaran lagi; proses yang belum selesai tidak dilanjutkan.|Hapus pekerjaan|Pekerjaan tersimpan|Menyimpan pekerjaan…',
'hi': 'स्रोत और सेटिंग इस डिवाइस पर सहेजे जाते हैं। वापस जाने से काम सुरक्षित रहता है। ऐप दोबारा खोलने पर आउटपुट फिर बनाएँ; अधूरी प्रक्रिया जारी नहीं होती।|काम मिटाएँ|काम सहेजा गया|काम सहेज रहे हैं…',
'ar': 'تُحفظ المصادر والإعدادات على هذا الجهاز. يحتفظ الرجوع بعملك. بعد إعادة تشغيل التطبيق، أنشئ الناتج مجددًا؛ لا تُستأنف المعالجة غير المكتملة.|مسح العمل|تم حفظ العمل|جارٍ حفظ العمل…',
'ru': 'Источники и настройки сохраняются на устройстве. Назад сохраняет работу. После перезапуска создайте результат заново; незавершённая обработка не возобновляется.|Очистить работу|Работа сохранена|Сохранение работы…',
'ja-JP': '元ファイルと設定はこの端末に保存されます。戻っても作業は保持されます。アプリ再起動後は出力を再作成してください。未完了の処理は再開されません。|作業を消去|作業を保存しました|作業を保存中…',
'fr-FR': 'Les sources et réglages sont enregistrés sur cet appareil. Retour conserve le travail. Après un redémarrage, recréez la sortie ; le traitement inachevé ne reprend pas.|Effacer le travail|Travail enregistré|Enregistrement du travail…',
'de-DE': 'Quellen und Einstellungen werden auf diesem Gerät gespeichert. Zurück behält die Arbeit. Nach einem App-Neustart die Ausgabe erneut erstellen; unvollständige Verarbeitung wird nicht fortgesetzt.|Arbeit löschen|Arbeit gespeichert|Arbeit wird gespeichert…',
'pl-PL': 'Źródła i ustawienia są zapisywane na urządzeniu. Wstecz zachowuje pracę. Po ponownym uruchomieniu utwórz wynik jeszcze raz; nieukończone przetwarzanie nie jest wznawiane.|Wyczyść pracę|Praca zapisana|Zapisywanie pracy…',
'ko-KR': '원본과 설정은 이 기기에 저장됩니다. 뒤로 가도 작업이 유지됩니다. 앱을 다시 시작하면 출력을 다시 만드세요. 완료되지 않은 처리는 재개되지 않습니다.|작업 지우기|작업 저장됨|작업 저장 중…',
'zh-Hans': '源文件和设置保存在此设备上。返回会保留工作。重启应用后请重新生成输出；未完成的处理不会继续。|清除工作|工作已保存|正在保存工作…',
'zh-Hant': '來源檔案和設定儲存在此裝置上。返回會保留工作。重新啟動應用程式後請重新產生輸出；未完成的處理不會繼續。|清除工作|工作已儲存|正在儲存工作…',
'it-IT': 'Sorgenti e impostazioni sono salvate sul dispositivo. Indietro conserva il lavoro. Dopo il riavvio ricrea il risultato; l’elaborazione incompleta non riprende.|Cancella lavoro|Lavoro salvato|Salvataggio del lavoro…',
'th-TH': 'ไฟล์ต้นฉบับและการตั้งค่าจะบันทึกในอุปกรณ์นี้ การย้อนกลับจะเก็บงานไว้ หลังเริ่มแอปใหม่ ให้สร้างผลลัพธ์อีกครั้ง การประมวลผลที่ยังไม่เสร็จจะไม่ทำต่อ|ล้างงาน|บันทึกงานแล้ว|กำลังบันทึกงาน…',
'vi-VN': 'Nguồn và cài đặt được lưu trên thiết bị này. Quay lại vẫn giữ công việc. Sau khi khởi động lại ứng dụng, hãy tạo đầu ra lần nữa; xử lý chưa xong không tiếp tục.|Xóa công việc|Đã lưu công việc|Đang lưu công việc…',
'fa': 'منابع و تنظیمات در این دستگاه ذخیره می‌شوند. بازگشت کار را نگه می‌دارد. پس از راه‌اندازی دوباره برنامه، خروجی را دوباره بسازید؛ پردازش ناتمام ادامه نمی‌یابد.|پاک کردن کار|کار ذخیره شد|در حال ذخیره کار…',
'sw': 'Vyanzo na mipangilio huhifadhiwa kwenye kifaa hiki. Kurudi huhifadhi kazi. Baada ya kuwasha programu tena, tengeneza matokeo tena; uchakataji ambao haujakamilika hauendelei.|Futa kazi|Kazi imehifadhiwa|Inahifadhi kazi…',
'ha': 'Ana adana tushe da saituna a wannan na’urar. Komawa yana kiyaye aikin. Bayan sake buɗe manhajar, sake samar da sakamako; aikin da bai ƙare ba ba zai ci gaba ba.|Share aiki|An adana aiki|Ana adana aiki…',
'ro': 'Sursele și setările sunt salvate pe acest dispozitiv. Înapoi păstrează lucrul. După repornire, creează din nou rezultatul; procesarea neterminată nu se reia.|Șterge lucrul|Lucru salvat|Se salvează lucrul…',
}
for locale, values in catalog['locales'].items():
    translations = rows[locale].split('|')
    assert len(translations) == len(keys)
    for key, value in zip(keys, translations):
        if key not in catalog['keys']:
            catalog['keys'].append(key)
        index = catalog['keys'].index(key)
        if index < len(values): values[index] = value
        else:
            assert index == len(values)
            values.append(value)
    values[catalog['keys'].index('photo_pdf_session')] = translations[0]
errors = {
    'en-US':'Could not save or restore this work. Retry or select the source files again.',
    'tr-TR':'Bu çalışma kaydedilemedi veya geri yüklenemedi. Tekrar deneyin ya da kaynak dosyaları yeniden seçin.',
    'es':'No se pudo guardar o restaurar el trabajo. Reintenta o vuelve a elegir los archivos de origen.',
    'pt-BR':'Não foi possível salvar ou restaurar o trabalho. Tente novamente ou selecione os arquivos de origem.',
    'id':'Pekerjaan tidak dapat disimpan atau dipulihkan. Coba lagi atau pilih kembali file sumber.',
    'hi':'काम सहेजा या वापस नहीं लाया जा सका। फिर प्रयास करें या स्रोत फ़ाइलें दोबारा चुनें।',
    'ar':'تعذر حفظ العمل أو استعادته. أعد المحاولة أو اختر ملفات المصدر مجددًا.',
    'ru':'Не удалось сохранить или восстановить работу. Повторите попытку или выберите исходные файлы заново.',
    'ja-JP':'作業を保存または復元できませんでした。再試行するか、元ファイルを再選択してください。',
    'fr-FR':'Impossible d’enregistrer ou de restaurer le travail. Réessayez ou sélectionnez à nouveau les fichiers sources.',
    'de-DE':'Arbeit konnte nicht gespeichert oder wiederhergestellt werden. Erneut versuchen oder Quelldateien erneut wählen.',
    'pl-PL':'Nie można zapisać lub przywrócić pracy. Spróbuj ponownie albo wybierz ponownie pliki źródłowe.',
    'ko-KR':'작업을 저장하거나 복원할 수 없습니다. 다시 시도하거나 원본 파일을 다시 선택하세요.',
    'zh-Hans':'无法保存或恢复工作。请重试或重新选择源文件。',
    'zh-Hant':'無法儲存或還原工作。請重試或重新選擇來源檔案。',
    'it-IT':'Impossibile salvare o ripristinare il lavoro. Riprova o scegli di nuovo i file sorgente.',
    'th-TH':'ไม่สามารถบันทึกหรือกู้คืนงานได้ ลองอีกครั้งหรือเลือกไฟล์ต้นฉบับใหม่',
    'vi-VN':'Không thể lưu hoặc khôi phục công việc. Thử lại hoặc chọn lại tệp nguồn.',
    'fa':'کار ذخیره یا بازیابی نشد. دوباره تلاش کنید یا فایل‌های منبع را دوباره انتخاب کنید.',
    'sw':'Kazi haikuweza kuhifadhiwa au kurejeshwa. Jaribu tena au chagua faili za chanzo tena.',
    'ha':'Ba a iya adana ko dawo da aikin ba. Sake gwadawa ko sake zaɓar fayilolin tushe.',
    'ro':'Lucrul nu a putut fi salvat sau restaurat. Reîncearcă sau selectează din nou fișierele sursă.',
}
if 'session_error' not in catalog['keys']: catalog['keys'].append('session_error')
index = catalog['keys'].index('session_error')
for locale, values in catalog['locales'].items():
    if index < len(values): values[index] = errors[locale]
    else:
        assert index == len(values)
        values.append(errors[locale])
path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
