"""Add advertising privacy controls in all application locales; not provider consent copy."""
import json
from pathlib import Path

KEYS = ['ad_privacy', 'ad_test_notice', 'ad_checking', 'ad_ready', 'ad_unavailable',
        'ad_not_started', 'ad_check_privacy', 'ad_privacy_choices']
ROWS = {
'en-US': ['Ad privacy', 'Test mode. Checking privacy contacts Google. Your files stay on this device. No live ads.', 'Checking privacy…', 'Privacy check complete.', 'Privacy check unavailable. File tools remain available.', 'Privacy check has not started.', 'Check privacy', 'Privacy choices'],
'tr-TR': ['Reklam gizliliği', 'Test modu. Gizlilik kontrolü Google ile bağlantı kurar. Dosyalarınız cihazda kalır. Canlı reklam yoktur.', 'Gizlilik kontrol ediliyor…', 'Gizlilik kontrolü tamamlandı.', 'Gizlilik kontrolü kullanılamıyor. Dosya araçlarını kullanabilirsiniz.', 'Gizlilik kontrolü başlatılmadı.', 'Gizliliği kontrol et', 'Gizlilik tercihleri'],
'de-DE': ['Werbedatenschutz', 'Testmodus. Die Datenschutzprüfung kontaktiert Google. Ihre Dateien bleiben auf diesem Gerät. Keine Live-Anzeigen.', 'Datenschutz wird geprüft…', 'Datenschutzprüfung abgeschlossen.', 'Datenschutzprüfung nicht verfügbar. Dateiwerkzeuge bleiben verfügbar.', 'Datenschutzprüfung noch nicht gestartet.', 'Datenschutz prüfen', 'Datenschutzauswahl'],
'fr-FR': ['Confidentialité publicitaire', 'Mode test. La vérification contacte Google. Vos fichiers restent sur cet appareil. Aucune publicité réelle.', 'Vérification de la confidentialité…', 'Vérification terminée.', 'Vérification indisponible. Les outils de fichiers restent disponibles.', 'La vérification n’a pas commencé.', 'Vérifier la confidentialité', 'Choix de confidentialité'],
'es': ['Privacidad de anuncios', 'Modo de prueba. La comprobación contacta con Google. Tus archivos permanecen en este dispositivo. Sin anuncios reales.', 'Comprobando la privacidad…', 'Comprobación de privacidad completada.', 'Comprobación no disponible. Las herramientas de archivos siguen disponibles.', 'La comprobación no ha comenzado.', 'Comprobar privacidad', 'Opciones de privacidad'],
'it-IT': ['Privacy degli annunci', 'Modalità di test. Il controllo contatta Google. I file restano su questo dispositivo. Nessun annuncio reale.', 'Controllo della privacy…', 'Controllo della privacy completato.', 'Controllo non disponibile. Gli strumenti per i file restano disponibili.', 'Il controllo non è iniziato.', 'Controlla la privacy', 'Scelte sulla privacy'],
'pt-BR': ['Privacidade dos anúncios', 'Modo de teste. A verificação contata o Google. Seus arquivos ficam neste dispositivo. Sem anúncios reais.', 'Verificando privacidade…', 'Verificação de privacidade concluída.', 'Verificação indisponível. As ferramentas de arquivos continuam disponíveis.', 'A verificação não foi iniciada.', 'Verificar privacidade', 'Opções de privacidade'],
'ru': ['Конфиденциальность рекламы', 'Тестовый режим. Проверка связывается с Google. Ваши файлы остаются на устройстве. Реальной рекламы нет.', 'Проверка конфиденциальности…', 'Проверка завершена.', 'Проверка недоступна. Инструменты для файлов доступны.', 'Проверка ещё не началась.', 'Проверить конфиденциальность', 'Настройки конфиденциальности'],
'ar': ['خصوصية الإعلانات', 'وضع الاختبار. يتصل فحص الخصوصية بـ Google. تبقى ملفاتك على هذا الجهاز. لا توجد إعلانات فعلية.', 'جارٍ فحص الخصوصية…', 'اكتمل فحص الخصوصية.', 'فحص الخصوصية غير متاح. أدوات الملفات لا تزال متاحة.', 'لم يبدأ فحص الخصوصية.', 'فحص الخصوصية', 'خيارات الخصوصية'],
'hi': ['विज्ञापन गोपनीयता', 'परीक्षण मोड। गोपनीयता जाँच Google से संपर्क करती है। आपकी फ़ाइलें इस डिवाइस पर रहती हैं। कोई वास्तविक विज्ञापन नहीं।', 'गोपनीयता की जाँच हो रही है…', 'गोपनीयता जाँच पूरी हुई।', 'गोपनीयता जाँच उपलब्ध नहीं है। फ़ाइल उपकरण उपलब्ध हैं।', 'गोपनीयता जाँच शुरू नहीं हुई है।', 'गोपनीयता जाँचें', 'गोपनीयता विकल्प'],
'bn': ['বিজ্ঞাপনের গোপনীয়তা', 'পরীক্ষা মোড। গোপনীয়তা যাচাই Google-এর সাথে যোগাযোগ করে। আপনার ফাইল এই ডিভাইসেই থাকে। কোনো আসল বিজ্ঞাপন নেই।', 'গোপনীয়তা যাচাই হচ্ছে…', 'গোপনীয়তা যাচাই সম্পন্ন।', 'গোপনীয়তা যাচাই উপলভ্য নয়। ফাইল টুল ব্যবহার করতে পারবেন।', 'গোপনীয়তা যাচাই শুরু হয়নি।', 'গোপনীয়তা যাচাই করুন', 'গোপনীয়তার বিকল্প'],
'ur': ['اشتہارات کی رازداری', 'آزمائشی وضع۔ رازداری کی جانچ Google سے رابطہ کرتی ہے۔ آپ کی فائلیں اسی آلے پر رہتی ہیں۔ کوئی حقیقی اشتہار نہیں۔', 'رازداری کی جانچ جاری ہے…', 'رازداری کی جانچ مکمل ہو گئی۔', 'رازداری کی جانچ دستیاب نہیں۔ فائل ٹولز دستیاب ہیں۔', 'رازداری کی جانچ شروع نہیں ہوئی۔', 'رازداری کی جانچ کریں', 'رازداری کے اختیارات'],
'pl': ['Prywatność reklam', 'Tryb testowy. Sprawdzenie kontaktuje się z Google. Pliki pozostają na tym urządzeniu. Brak prawdziwych reklam.', 'Sprawdzanie prywatności…', 'Sprawdzanie prywatności zakończone.', 'Sprawdzenie niedostępne. Narzędzia plików pozostają dostępne.', 'Sprawdzanie jeszcze się nie rozpoczęło.', 'Sprawdź prywatność', 'Opcje prywatności'],
'id': ['Privasi iklan', 'Mode uji. Pemeriksaan privasi menghubungi Google. File Anda tetap di perangkat ini. Tidak ada iklan sungguhan.', 'Memeriksa privasi…', 'Pemeriksaan privasi selesai.', 'Pemeriksaan tidak tersedia. Alat file tetap tersedia.', 'Pemeriksaan privasi belum dimulai.', 'Periksa privasi', 'Pilihan privasi'],
'vi-VN': ['Quyền riêng tư quảng cáo', 'Chế độ thử nghiệm. Kiểm tra quyền riêng tư kết nối với Google. Tệp vẫn ở trên thiết bị. Không có quảng cáo thật.', 'Đang kiểm tra quyền riêng tư…', 'Đã kiểm tra quyền riêng tư.', 'Không thể kiểm tra. Công cụ tệp vẫn dùng được.', 'Chưa bắt đầu kiểm tra quyền riêng tư.', 'Kiểm tra quyền riêng tư', 'Lựa chọn quyền riêng tư'],
'th-TH': ['ความเป็นส่วนตัวของโฆษณา', 'โหมดทดสอบ การตรวจสอบความเป็นส่วนตัวจะติดต่อ Google ไฟล์ยังอยู่ในอุปกรณ์นี้ ไม่มีโฆษณาจริง', 'กำลังตรวจสอบความเป็นส่วนตัว…', 'ตรวจสอบความเป็นส่วนตัวเสร็จแล้ว', 'ตรวจสอบไม่ได้ เครื่องมือไฟล์ยังใช้งานได้', 'ยังไม่ได้เริ่มตรวจสอบความเป็นส่วนตัว', 'ตรวจสอบความเป็นส่วนตัว', 'ตัวเลือกความเป็นส่วนตัว'],
'ja-JP': ['広告のプライバシー', 'テストモード。プライバシーの確認でGoogleに接続します。ファイルはこの端末内に保持されます。実際の広告はありません。', 'プライバシーを確認中…', 'プライバシーの確認が完了しました。', '確認できません。ファイルツールは引き続き使用できます。', 'プライバシーの確認は開始されていません。', 'プライバシーを確認', 'プライバシーの選択'],
'ko-KR': ['광고 개인정보 보호', '테스트 모드입니다. 개인정보 확인 시 Google에 연결합니다. 파일은 이 기기에 남습니다. 실제 광고는 없습니다.', '개인정보 확인 중…', '개인정보 확인을 완료했습니다.', '확인할 수 없습니다. 파일 도구는 계속 사용할 수 있습니다.', '개인정보 확인을 시작하지 않았습니다.', '개인정보 확인', '개인정보 선택사항'],
'zh-Hans': ['广告隐私', '测试模式。隐私检查会连接Google。文件保留在此设备上。没有真实广告。', '正在检查隐私…', '隐私检查完成。', '隐私检查不可用。文件工具仍可使用。', '尚未开始隐私检查。', '检查隐私', '隐私选项'],
'zh-Hant': ['廣告隱私', '測試模式。隱私檢查會連線至Google。檔案保留在此裝置上。沒有真實廣告。', '正在檢查隱私…', '隱私檢查完成。', '無法檢查隱私。檔案工具仍可使用。', '尚未開始隱私檢查。', '檢查隱私', '隱私選項'],
'sw': ['Faragha ya matangazo', 'Hali ya majaribio. Ukaguzi wa faragha huwasiliana na Google. Faili zako zinabaki kwenye kifaa hiki. Hakuna matangazo halisi.', 'Inakagua faragha…', 'Ukaguzi wa faragha umekamilika.', 'Ukaguzi haupatikani. Zana za faili bado zinapatikana.', 'Ukaguzi wa faragha haujaanza.', 'Kagua faragha', 'Chaguo za faragha'],
'ha': ['Sirrin tallace-tallace', 'Yanayin gwaji. Duba sirri yana tuntuɓar Google. Fayilolinka suna nan a wannan na’ura. Babu tallace-tallace na gaske.', 'Ana duba sirri…', 'An gama duba sirri.', 'Ba a iya duba sirri ba. Kayan aikin fayil suna nan.', 'Ba a fara duba sirri ba.', 'Duba sirri', 'Zaɓuɓɓukan sirri'],
'ro': ['Confidențialitatea reclamelor', 'Mod de test. Verificarea contactează Google. Fișierele rămân pe acest dispozitiv. Fără reclame reale.', 'Se verifică confidențialitatea…', 'Verificare încheiată.', 'Verificare indisponibilă. Instrumentele pentru fișiere rămân disponibile.', 'Verificarea nu a început.', 'Verifică confidențialitatea', 'Opțiuni de confidențialitate'],
}

if __name__ == '__main__':
    ROWS['pl-PL'] = ROWS.pop('pl')
    ROWS.pop('bn'); ROWS.pop('ur')
    ROWS['fa'] = ['حریم خصوصی تبلیغات', 'حالت آزمایشی. بررسی حریم خصوصی با Google ارتباط برقرار می‌کند. فایل‌ها در این دستگاه می‌مانند. تبلیغ واقعی نمایش داده نمی‌شود.', 'در حال بررسی حریم خصوصی…', 'بررسی حریم خصوصی کامل شد.', 'بررسی در دسترس نیست. ابزارهای فایل همچنان قابل استفاده هستند.', 'بررسی حریم خصوصی شروع نشده است.', 'بررسی حریم خصوصی', 'گزینه‌های حریم خصوصی']
    path = Path(__file__).with_name('catalog.json')
    catalog = json.loads(path.read_text(encoding='utf-8'))
    assert set(ROWS) == set(catalog['locales'])
    for tag, values in ROWS.items():
        assert len(values) == len(KEYS)
        for key, value in zip(KEYS, values):
            if key not in catalog['keys']: catalog['keys'].append(key)
            index = catalog['keys'].index(key)
            target = catalog['locales'][tag]
            if index == len(target): target.append(value)
            else: target[index] = value
    path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
