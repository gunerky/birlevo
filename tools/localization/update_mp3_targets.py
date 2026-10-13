"""MP3 and offline source text in all current UI languages; no remote calls."""
import json
from pathlib import Path

TEXT = {
 'en-US': ['MP3 uses lossy LAME encoding at the selected bitrate. Sample rate and channels are retained. Unsupported combinations are unavailable; no automatic resampling.', 'Save MP3 encoder source'],
 'tr-TR': ['MP3, seçilen bit hızında kayıplı LAME kodlaması kullanır. Örnekleme hızı ve kanal sayısı korunur. Desteklenmeyen birleşimler kullanılamaz; otomatik yeniden örnekleme yapılmaz.', 'MP3 kodlayıcı kaynağını kaydet'],
 'de-DE': ['MP3 nutzt verlustbehaftete LAME-Codierung mit der gewählten Bitrate. Abtastrate und Kanäle bleiben erhalten. Nicht unterstützte Kombinationen sind nicht verfügbar; keine automatische Neuabtastung.', 'MP3-Encoder-Quellcode speichern'],
 'es': ['MP3 usa codificación LAME con pérdida a la tasa elegida. Se conservan la frecuencia y los canales. Las combinaciones no compatibles no están disponibles; no hay remuestreo automático.', 'Guardar el código del codificador MP3'],
 'fr-FR': ['MP3 utilise LAME avec perte au débit choisi. La fréquence et les canaux sont conservés. Les combinaisons non prises en charge sont indisponibles ; aucun rééchantillonnage automatique.', 'Enregistrer le code source de l’encodeur MP3'],
 'it-IT': ['MP3 usa LAME con perdita al bitrate scelto. Frequenza e canali restano invariati. Le combinazioni non supportate non sono disponibili; nessun ricampionamento automatico.', 'Salva il codice sorgente del codificatore MP3'],
 'pt-BR': ['MP3 usa codificação LAME com perdas na taxa escolhida. A frequência e os canais são mantidos. Combinações incompatíveis ficam indisponíveis; não há reamostragem automática.', 'Salvar o código-fonte do codificador MP3'],
 'ru': ['MP3 использует LAME с потерями и выбранным битрейтом. Частота и каналы сохраняются. Неподдерживаемые сочетания недоступны; автоматического изменения частоты нет.', 'Сохранить исходный код MP3-кодировщика'],
 'ar': ['يستخدم MP3 ترميز LAME مع فقدان بالجودة ومعدل البت المحدد. تُحفظ ترددات العينات والقنوات. التركيبات غير المدعومة غير متاحة؛ لا توجد إعادة أخذ عينات تلقائية.', 'حفظ مصدر مُرمّز MP3'],
 'fa': ['MP3 از کدگذاری با اتلاف LAME با نرخ بیت انتخابی استفاده می‌کند. نرخ نمونه‌برداری و کانال‌ها حفظ می‌شوند. ترکیب‌های پشتیبانی‌نشده در دسترس نیستند؛ نمونه‌برداری مجدد خودکار انجام نمی‌شود.', 'ذخیره کد منبع رمزگذار MP3'],
 'hi': ['MP3 चुनी गई बिटरेट पर हानिपूर्ण LAME एन्कोडिंग का उपयोग करता है। सैंपल दर और चैनल बनाए रखे जाते हैं। असमर्थित संयोजन उपलब्ध नहीं हैं; अपने आप रीसैंपलिंग नहीं होती।', 'MP3 एन्कोडर का स्रोत सहेजें'],
 'id': ['MP3 menggunakan enkode LAME lossy pada bitrate pilihan. Laju sampel dan kanal dipertahankan. Kombinasi yang tidak didukung tidak tersedia; tanpa resampling otomatis.', 'Simpan kode sumber encoder MP3'],
 'vi-VN': ['MP3 dùng mã hóa LAME có mất dữ liệu ở tốc độ bit đã chọn. Giữ nguyên tần số lấy mẫu và kênh. Tổ hợp không hỗ trợ sẽ không khả dụng; không tự động lấy mẫu lại.', 'Lưu mã nguồn bộ mã hóa MP3'],
 'zh-Hans': ['MP3 使用所选比特率的 LAME 有损编码。保留采样率和声道。不支持的组合不可用；不会自动重采样。', '保存 MP3 编码器源代码'],
 'zh-Hant': ['MP3 使用所選位元率的 LAME 有損編碼。保留取樣率和聲道。不支援的組合無法使用；不會自動重新取樣。', '儲存 MP3 編碼器原始碼'],
 'ja-JP': ['MP3 は選択したビットレートで LAME の非可逆圧縮を使用します。サンプルレートとチャンネル数は保持されます。非対応の組み合わせは使用できず、自動リサンプリングは行いません。', 'MP3 エンコーダーのソースを保存'],
 'ko-KR': ['MP3는 선택한 비트레이트로 LAME 손실 인코딩을 사용합니다. 샘플 레이트와 채널을 유지합니다. 지원하지 않는 조합은 사용할 수 없으며 자동 리샘플링하지 않습니다.', 'MP3 인코더 소스 저장'],
 'th-TH': ['MP3 ใช้การเข้ารหัส LAME แบบสูญเสียข้อมูลตามบิตเรตที่เลือก โดยคงอัตราสุ่มและช่องเสียงไว้ ไม่สามารถใช้ชุดค่าที่ไม่รองรับ และไม่มีการสุ่มตัวอย่างใหม่อัตโนมัติ', 'บันทึกซอร์สโค้ดตัวเข้ารหัส MP3'],
 'ha': ['MP3 yana amfani da LAME mai rasa bayanai da bitrate da aka zaɓa. Ana kiyaye yawan samfurori da tashoshi. Haɗuwar da ba a goyi baya ba ba ta samuwa; babu sauya yawan samfurori ta atomatik.', 'Ajiye lambar tushen encoder na MP3'],
 'sw': ['MP3 hutumia LAME yenye upotevu katika kasi ya biti iliyochaguliwa. Kiwango cha sampuli na chaneli huhifadhiwa. Mchanganyiko usiotumika haupatikani; hakuna kubadili sampuli kiotomatiki.', 'Hifadhi msimbo chanzo wa kisimbaji MP3'],
 'pl-PL': ['MP3 używa stratnego kodowania LAME z wybraną przepływnością. Częstotliwość próbkowania i kanały są zachowane. Nieobsługiwane kombinacje są niedostępne; bez automatycznego przepróbkowania.', 'Zapisz kod źródłowy kodera MP3'],
 'ro': ['MP3 folosește codare LAME cu pierderi la rata de biți aleasă. Rata de eșantionare și canalele sunt păstrate. Combinațiile neacceptate nu sunt disponibile; fără reeșantionare automată.', 'Salvează codul sursă al codorului MP3'],
}
path = Path(__file__).with_name('catalog.json')
catalog = json.loads(path.read_text(encoding='utf-8'))
keys = ['audio_mp3_description', 'mp3_source_save']
for key in keys:
    if key not in catalog['keys']:
        catalog['keys'].append(key)
        for values in catalog['locales'].values(): values.append('')
for tag, values in catalog['locales'].items():
    for key, value in zip(keys, TEXT[tag]): values[catalog['keys'].index(key)] = value
path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('MP3 catalog updated; regenerate resources.')
