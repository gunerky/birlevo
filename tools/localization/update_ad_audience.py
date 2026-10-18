"""Add local age-choice copy; provider forms remain separate account configuration."""
import json
from pathlib import Path

KEYS = ['ad_age_title', 'ad_age_notice', 'ad_age_under_13', 'ad_age_13_15', 'ad_age_16_17',
        'ad_age_adult', 'ad_age_undisclosed', 'ad_age_saving', 'ad_age_save_failed']
ROWS = {
 'en-US': ['Age group', 'Your age group is saved on this device to choose ad privacy protections. No birth date is needed. All file tools work without choosing.', 'Under 13', '18 or older', 'Prefer not to say', 'Saving preference…', 'Could not save your choice. Ads remain off; file tools are available.'],
 'tr-TR': ['Yaş grubu', 'Reklam gizliliği korumalarını belirlemek için yaş grubunuz bu cihazda saklanır. Doğum tarihiniz istenmez. Seçim yapmadan tüm dosya araçlarını kullanabilirsiniz.', '13 yaşından küçük', '18 yaş ve üzeri', 'Belirtmek istemiyorum', 'Tercih kaydediliyor…', 'Seçiminiz kaydedilemedi. Reklamlar kapalı; dosya araçlarını kullanabilirsiniz.'],
 'de-DE': ['Altersgruppe', 'Ihre Altersgruppe wird auf diesem Gerät gespeichert, um den Datenschutz bei Werbung festzulegen. Ein Geburtsdatum ist nicht nötig. Alle Dateiwerkzeuge funktionieren ohne Auswahl.', 'Unter 13', '18 oder älter', 'Keine Angabe', 'Auswahl wird gespeichert…', 'Ihre Auswahl konnte nicht gespeichert werden. Werbung bleibt aus; die Dateiwerkzeuge sind verfügbar.'],
 'fr-FR': ["Tranche d’âge", "Votre tranche d’âge est enregistrée sur cet appareil pour adapter la protection publicitaire. Aucune date de naissance n’est demandée. Tous les outils restent utilisables sans choix.", 'Moins de 13 ans', '18 ans ou plus', 'Préfère ne pas répondre', 'Enregistrement du choix…', "Impossible d’enregistrer votre choix. Les publicités restent désactivées ; les outils sont disponibles."],
 'es': ['Grupo de edad', 'Tu grupo de edad se guarda en este dispositivo para aplicar protecciones de privacidad publicitaria. No se necesita tu fecha de nacimiento. Todas las herramientas funcionan sin elegir.', 'Menos de 13 años', '18 años o más', 'Prefiero no decirlo', 'Guardando preferencia…', 'No se pudo guardar tu elección. Los anuncios siguen desactivados; las herramientas están disponibles.'],
 'it-IT': ['Fascia d’età', 'La fascia d’età viene salvata su questo dispositivo per applicare le protezioni della privacy pubblicitaria. Non serve la data di nascita. Tutti gli strumenti funzionano anche senza scegliere.', 'Meno di 13 anni', '18 anni o più', 'Preferisco non dirlo', 'Salvataggio della preferenza…', 'Impossibile salvare la scelta. Gli annunci restano disattivati; gli strumenti sono disponibili.'],
 'pt-BR': ['Faixa etária', 'Sua faixa etária é salva neste dispositivo para definir as proteções de privacidade dos anúncios. Não é necessária a data de nascimento. Todas as ferramentas funcionam sem escolher.', 'Menos de 13 anos', '18 anos ou mais', 'Prefiro não informar', 'Salvando preferência…', 'Não foi possível salvar sua escolha. Os anúncios continuam desativados; as ferramentas estão disponíveis.'],
 'id': ['Kelompok usia', 'Kelompok usia disimpan di perangkat ini untuk menentukan perlindungan privasi iklan. Tanggal lahir tidak diperlukan. Semua alat berkas tetap dapat digunakan tanpa memilih.', 'Di bawah 13 tahun', '18 tahun atau lebih', 'Tidak ingin menyebutkan', 'Menyimpan preferensi…', 'Pilihan tidak dapat disimpan. Iklan tetap dinonaktifkan; alat berkas tersedia.'],
 'hi': ['आयु वर्ग', 'विज्ञापन गोपनीयता सुरक्षा तय करने के लिए आपका आयु वर्ग इस डिवाइस पर सहेजा जाता है। जन्मतिथि की ज़रूरत नहीं है। बिना चुने भी सभी फ़ाइल टूल इस्तेमाल कर सकते हैं।', '13 वर्ष से कम', '18 वर्ष या अधिक', 'बताना नहीं चाहते', 'पसंद सहेजी जा रही है…', 'आपकी पसंद सहेजी नहीं जा सकी। विज्ञापन बंद रहेंगे; फ़ाइल टूल उपलब्ध हैं।'],
 'ar': ['الفئة العمرية', 'تُحفظ فئتك العمرية على هذا الجهاز لتحديد حماية خصوصية الإعلانات. لا نحتاج إلى تاريخ ميلادك. يمكنك استخدام جميع أدوات الملفات دون اختيار.', 'أقل من 13 عامًا', '18 عامًا أو أكثر', 'أفضل عدم الإفصاح', 'جارٍ حفظ التفضيل…', 'تعذر حفظ اختيارك. تبقى الإعلانات متوقفة وأدوات الملفات متاحة.'],
 'ru': ['Возрастная группа', 'Возрастная группа сохраняется на устройстве для выбора защиты конфиденциальности рекламы. Дата рождения не нужна. Все инструменты доступны и без выбора.', 'Младше 13 лет', '18 лет и старше', 'Не хочу указывать', 'Сохранение выбора…', 'Не удалось сохранить выбор. Реклама остаётся отключённой; инструменты доступны.'],
 'ja-JP': ['年齢層', '広告のプライバシー保護を設定するため、年齢層をこの端末に保存します。生年月日は不要です。選択しなくてもすべてのファイルツールを使えます。', '13歳未満', '18歳以上', '回答しない', '設定を保存中…', '選択を保存できませんでした。広告は無効のままです。ファイルツールは利用できます。'],
 'pl-PL': ['Grupa wiekowa', 'Grupa wiekowa jest zapisywana na tym urządzeniu, aby dobrać ochronę prywatności reklam. Data urodzenia nie jest potrzebna. Wszystkie narzędzia działają bez wyboru.', 'Poniżej 13 lat', '18 lat lub więcej', 'Wolę nie podawać', 'Zapisywanie wyboru…', 'Nie udało się zapisać wyboru. Reklamy pozostają wyłączone; narzędzia są dostępne.'],
 'ko-KR': ['연령대', '광고 개인정보 보호를 설정하기 위해 연령대를 이 기기에 저장합니다. 생년월일은 필요하지 않습니다. 선택하지 않아도 모든 파일 도구를 사용할 수 있습니다.', '13세 미만', '18세 이상', '응답하지 않음', '설정 저장 중…', '선택을 저장하지 못했습니다. 광고는 계속 꺼져 있으며 파일 도구는 사용할 수 있습니다.'],
 'zh-Hans': ['年龄段', '年龄段保存在此设备上，用于选择广告隐私保护。不需要出生日期。不作选择也可以使用所有文件工具。', '未满13岁', '18岁或以上', '不愿透露', '正在保存偏好…', '无法保存您的选择。广告保持关闭，文件工具仍可使用。'],
 'zh-Hant': ['年齡層', '年齡層會儲存在此裝置上，用於選擇廣告隱私保護。不需要出生日期。不作選擇也能使用所有檔案工具。', '未滿13歲', '18歲或以上', '不願透露', '正在儲存偏好…', '無法儲存您的選擇。廣告保持關閉，檔案工具仍可使用。'],
 'th-TH': ['ช่วงอายุ', 'ระบบบันทึกช่วงอายุไว้ในอุปกรณ์นี้เพื่อเลือกการคุ้มครองความเป็นส่วนตัวของโฆษณา ไม่ต้องระบุวันเกิด คุณใช้เครื่องมือไฟล์ทั้งหมดได้โดยไม่ต้องเลือก', 'อายุต่ำกว่า 13 ปี', 'อายุ 18 ปีขึ้นไป', 'ไม่ประสงค์ระบุ', 'กำลังบันทึกการตั้งค่า…', 'บันทึกตัวเลือกไม่ได้ โฆษณายังคงปิดอยู่และเครื่องมือไฟล์ยังใช้งานได้'],
 'vi-VN': ['Nhóm tuổi', 'Nhóm tuổi được lưu trên thiết bị này để chọn biện pháp bảo vệ quyền riêng tư quảng cáo. Không cần ngày sinh. Bạn vẫn dùng được mọi công cụ tệp mà không chọn.', 'Dưới 13 tuổi', 'Từ 18 tuổi trở lên', 'Không muốn tiết lộ', 'Đang lưu lựa chọn…', 'Không thể lưu lựa chọn. Quảng cáo vẫn tắt; các công cụ tệp vẫn dùng được.'],
 'fa': ['گروه سنی', 'گروه سنی شما برای تعیین حفاظت حریم خصوصی تبلیغات در این دستگاه ذخیره می‌شود. تاریخ تولد لازم نیست. بدون انتخاب هم همه ابزارهای فایل قابل استفاده‌اند.', 'کمتر از ۱۳ سال', '۱۸ سال یا بیشتر', 'ترجیح می‌دهم نگویم', 'در حال ذخیره ترجیح…', 'انتخاب شما ذخیره نشد. تبلیغات خاموش می‌ماند و ابزارهای فایل در دسترس‌اند.'],
 'sw': ['Kundi la umri', 'Kundi lako la umri huhifadhiwa kwenye kifaa hiki ili kuchagua ulinzi wa faragha ya matangazo. Tarehe ya kuzaliwa haihitajiki. Zana zote za faili zinatumika bila kuchagua.', 'Chini ya miaka 13', 'Miaka 18 au zaidi', 'Sitaki kusema', 'Inahifadhi chaguo…', 'Chaguo halikuhifadhiwa. Matangazo yanabaki yamezimwa; zana za faili zinapatikana.'],
 'ha': ['Rukunin shekaru', 'Ana adana rukunin shekarunka a wannan na’ura don zaɓar kariyar sirrin tallace-tallace. Ba a buƙatar ranar haihuwa. Duk kayan aikin fayil suna aiki ba tare da zaɓi ba.', 'Ƙasa da shekaru 13', 'Shekaru 18 ko fiye', 'Ba na son faɗa', 'Ana adana zaɓi…', 'Ba a iya adana zaɓinka ba. Tallace-tallace suna nan a kashe; kayan aikin fayil suna aiki.'],
 'ro': ['Grupă de vârstă', 'Grupa de vârstă este salvată pe acest dispozitiv pentru a stabili protecția datelor în reclame. Nu este necesară data nașterii. Toate instrumentele funcționează fără a alege.', 'Sub 13 ani', '18 ani sau peste', 'Prefer să nu spun', 'Se salvează preferința…', 'Alegerea nu a putut fi salvată. Reclamele rămân oprite; instrumentele sunt disponibile.'],
}

if __name__ == '__main__':
    path = Path(__file__).with_name('catalog.json')
    catalog = json.loads(path.read_text(encoding='utf-8'))
    # Use the application's stable public locale tags.
    for old, new in [('th-TH', 'th'), ('vi-VN', 'vi')]:
        if new in catalog['locales']:
            ROWS[new] = ROWS.pop(old)
    assert set(ROWS) == set(catalog['locales'])
    for tag, row in ROWS.items():
        values = row[:3] + ['13–15', '16–17'] + row[3:]
        assert len(values) == len(KEYS)
        for key, value in zip(KEYS, values):
            if key not in catalog['keys']:
                catalog['keys'].append(key)
            index = catalog['keys'].index(key)
            target = catalog['locales'][tag]
            if index == len(target):
                target.append(value)
            else:
                target[index] = value
    path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
