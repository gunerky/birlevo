"""Local draft translations for confirmed project/session removal; no network calls."""
import json
from pathlib import Path

KEYS = ["delete_project", "delete_project_confirmation", "clear_work_confirmation", "work_actions", "clear_work_error"]
ROWS = {
"en-US": ["Delete project", "Delete %1$s from saved projects? Original and exported files are kept.", "Clear %1$s and its temporary files? Original files, saved projects and exported files are kept.", "Actions for %1$s", "Could not clear this work. Wait for processing to finish and try again."],
"tr": ["Projeyi sil", "%1$s kayıtlı projelerden silinsin mi? Özgün ve dışa aktarılan dosyalar korunur.", "%1$s ve geçici dosyaları temizlensin mi? Özgün dosyalar, kayıtlı projeler ve dışa aktarılan dosyalar korunur.", "%1$s için işlemler", "Çalışma temizlenemedi. İşlemin bitmesini bekleyip tekrar deneyin."],
"de": ["Projekt löschen", "%1$s aus gespeicherten Projekten löschen? Originale und exportierte Dateien bleiben erhalten.", "%1$s und temporäre Dateien entfernen? Originale, gespeicherte Projekte und exportierte Dateien bleiben erhalten.", "Aktionen für %1$s", "Die Arbeit konnte nicht entfernt werden. Warte auf das Ende der Verarbeitung und versuche es erneut."],
"fr": ["Supprimer le projet", "Supprimer %1$s des projets enregistrés ? Les fichiers originaux et exportés sont conservés.", "Effacer %1$s et ses fichiers temporaires ? Les originaux, projets enregistrés et fichiers exportés sont conservés.", "Actions pour %1$s", "Impossible d’effacer ce travail. Attendez la fin du traitement et réessayez."],
"es": ["Eliminar proyecto", "¿Eliminar %1$s de los proyectos guardados? Se conservan los archivos originales y exportados.", "¿Borrar %1$s y sus archivos temporales? Se conservan los originales, proyectos guardados y archivos exportados.", "Acciones para %1$s", "No se pudo borrar este trabajo. Espera a que termine el procesamiento e inténtalo de nuevo."],
"pt": ["Excluir projeto", "Excluir %1$s dos projetos salvos? Os arquivos originais e exportados são mantidos.", "Limpar %1$s e seus arquivos temporários? Os originais, projetos salvos e arquivos exportados são mantidos.", "Ações para %1$s", "Não foi possível limpar este trabalho. Aguarde o fim do processamento e tente novamente."],
"it": ["Elimina progetto", "Eliminare %1$s dai progetti salvati? I file originali ed esportati vengono conservati.", "Cancellare %1$s e i file temporanei? Originali, progetti salvati e file esportati vengono conservati.", "Azioni per %1$s", "Impossibile cancellare questo lavoro. Attendi la fine dell’elaborazione e riprova."],
"ru": ["Удалить проект", "Удалить %1$s из сохранённых проектов? Исходные и экспортированные файлы сохранятся.", "Очистить %1$s и временные файлы? Исходные файлы, сохранённые проекты и экспортированные файлы сохранятся.", "Действия для %1$s", "Не удалось очистить работу. Дождитесь завершения обработки и повторите попытку."],
"ar": ["حذف المشروع", "هل تريد حذف %1$s من المشاريع المحفوظة؟ تُحفظ الملفات الأصلية والمصدّرة.", "هل تريد مسح %1$s وملفاته المؤقتة؟ تُحفظ الملفات الأصلية والمشاريع المحفوظة والملفات المصدّرة.", "إجراءات %1$s", "تعذر مسح هذا العمل. انتظر انتهاء المعالجة وحاول مرة أخرى."],
"fa": ["حذف پروژه", "آیا %1$s از پروژه‌های ذخیره‌شده حذف شود؟ فایل‌های اصلی و خروجی حفظ می‌شوند.", "آیا %1$s و فایل‌های موقت آن پاک شوند؟ فایل‌های اصلی، پروژه‌های ذخیره‌شده و فایل‌های خروجی حفظ می‌شوند.", "عملیات برای %1$s", "پاک کردن این کار ممکن نشد. منتظر پایان پردازش بمانید و دوباره تلاش کنید."],
"hi": ["प्रोजेक्ट हटाएँ", "सहेजे गए प्रोजेक्ट से %1$s हटाएँ? मूल और निर्यात की गई फ़ाइलें सुरक्षित रहेंगी।", "%1$s और उसकी अस्थायी फ़ाइलें साफ़ करें? मूल फ़ाइलें, सहेजे गए प्रोजेक्ट और निर्यात की गई फ़ाइलें सुरक्षित रहेंगी।", "%1$s के लिए कार्रवाइयाँ", "यह काम साफ़ नहीं हो सका। प्रोसेसिंग पूरी होने का इंतज़ार करें और फिर कोशिश करें।"],
"pl-PL": ["Usuń projekt", "Usunąć %1$s z zapisanych projektów? Oryginalne i wyeksportowane pliki zostaną zachowane.", "Wyczyścić %1$s i pliki tymczasowe? Oryginały, zapisane projekty i wyeksportowane pliki zostaną zachowane.", "Działania dla %1$s", "Nie można wyczyścić tej pracy. Poczekaj na zakończenie przetwarzania i spróbuj ponownie."],
"id": ["Hapus proyek", "Hapus %1$s dari proyek tersimpan? File asli dan hasil ekspor tetap disimpan.", "Bersihkan %1$s dan file sementaranya? File asli, proyek tersimpan dan hasil ekspor tetap disimpan.", "Tindakan untuk %1$s", "Pekerjaan ini tidak dapat dibersihkan. Tunggu pemrosesan selesai dan coba lagi."],
"vi": ["Xóa dự án", "Xóa %1$s khỏi các dự án đã lưu? Giữ lại tệp gốc và tệp đã xuất.", "Xóa %1$s và các tệp tạm? Giữ lại tệp gốc, dự án đã lưu và tệp đã xuất.", "Thao tác cho %1$s", "Không thể xóa công việc này. Chờ xử lý hoàn tất rồi thử lại."],
"th": ["ลบโปรเจกต์", "ลบ %1$s ออกจากโปรเจกต์ที่บันทึกไว้หรือไม่? ไฟล์ต้นฉบับและไฟล์ที่ส่งออกจะยังอยู่", "ล้าง %1$s และไฟล์ชั่วคราวหรือไม่? ไฟล์ต้นฉบับ โปรเจกต์ที่บันทึกไว้ และไฟล์ที่ส่งออกจะยังอยู่", "การดำเนินการสำหรับ %1$s", "ไม่สามารถล้างงานนี้ได้ รอให้ประมวลผลเสร็จแล้วลองอีกครั้ง"],
"ja": ["プロジェクトを削除", "保存済みプロジェクトから%1$sを削除しますか？元のファイルと書き出したファイルは保持されます。", "%1$sと一時ファイルを消去しますか？元のファイル、保存済みプロジェクト、書き出したファイルは保持されます。", "%1$sの操作", "この作業を消去できませんでした。処理が終わってから再試行してください。"],
"ko": ["프로젝트 삭제", "저장된 프로젝트에서 %1$s을(를) 삭제할까요? 원본과 내보낸 파일은 유지됩니다.", "%1$s 및 임시 파일을 지울까요? 원본 파일, 저장된 프로젝트와 내보낸 파일은 유지됩니다.", "%1$s 작업", "작업을 지울 수 없습니다. 처리가 끝난 후 다시 시도하세요."],
"zh-CN": ["删除项目", "从已保存的项目中删除%1$s？原始文件和已导出的文件将保留。", "清除%1$s及其临时文件？原始文件、已保存的项目和已导出的文件将保留。", "%1$s的操作", "无法清除此工作。请等待处理完成后重试。"],
"zh-TW": ["刪除專案", "從已儲存的專案中刪除%1$s？原始檔案及已匯出的檔案將保留。", "清除%1$s及其暫存檔案？原始檔案、已儲存的專案及已匯出的檔案將保留。", "%1$s的操作", "無法清除此工作。請等待處理完成後再試一次。"],
"sw": ["Futa mradi", "Futa %1$s kutoka miradi iliyohifadhiwa? Faili asili na zilizohamishwa zitahifadhiwa.", "Futa %1$s na faili zake za muda? Faili asili, miradi iliyohifadhiwa na faili zilizohamishwa zitahifadhiwa.", "Vitendo vya %1$s", "Kazi hii haikuweza kufutwa. Subiri uchakataji ukamilike kisha ujaribu tena."],
"ha": ["Goge aikin", "A goge %1$s daga ayyukan da aka ajiye? Za a riƙe fayilolin asali da waɗanda aka fitar.", "A share %1$s da fayilolinsa na wucin gadi? Za a riƙe fayilolin asali, ayyukan da aka ajiye da fayilolin da aka fitar.", "Zaɓuɓɓuka na %1$s", "Ba a iya share wannan aikin ba. Jira sarrafawa ta ƙare sannan a sake gwadawa."],
"ro": ["Șterge proiectul", "Ștergi %1$s din proiectele salvate? Fișierele originale și exportate sunt păstrate.", "Ștergi %1$s și fișierele temporare? Originalele, proiectele salvate și fișierele exportate sunt păstrate.", "Acțiuni pentru %1$s", "Lucrarea nu a putut fi ștearsă. Așteaptă finalizarea procesării și încearcă din nou."],
}

if __name__ == "__main__":
    aliases = {"tr": "tr-TR", "pt": "pt-BR", "ja": "ja-JP", "fr": "fr-FR", "de": "de-DE",
        "ko": "ko-KR", "zh-CN": "zh-Hans", "zh-TW": "zh-Hant", "it": "it-IT", "th": "th-TH", "vi": "vi-VN"}
    ROWS = {aliases.get(tag, tag): values for tag, values in ROWS.items()}
    path = Path(__file__).with_name("catalog.json")
    catalog = json.loads(path.read_text(encoding="utf-8"))
    assert set(ROWS) == set(catalog["locales"])
    for tag, values in ROWS.items():
        for key, value in zip(KEYS, values):
            if key not in catalog["keys"]: catalog["keys"].append(key)
            index = catalog["keys"].index(key)
            target = catalog["locales"][tag]
            if index == len(target): target.append(value)
            else: target[index] = value
    path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
