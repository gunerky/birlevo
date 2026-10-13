"""Add the two-clip explanation to all supported locales; native language QA remains separate."""
from pathlib import Path
import json
import subprocess
import sys

TEXT = {
 'en-US': 'Up to two clips, with a selected total of 10 minutes. The output canvas follows the first clip; other clips fit with black borders, without cropping. Joining resamples audio to 48 kHz and uses the larger channel count. Silent clips keep silence.',
 'tr-TR': 'En fazla iki klip; seçilen toplam süre 10 dakika. Çıktı kadrajı ilk klibi izler; diğer klipler kırpılmadan siyah kenarlarla sığdırılır. Birleştirmede ses 48 kHz ve daha yüksek kanal sayısıyla işlenir. Sessiz klipler sessiz kalır.',
 'de-DE': 'Bis zu zwei Clips, insgesamt höchstens 10 ausgewählte Minuten. Das Ausgabeformat folgt dem ersten Clip; andere Clips passen mit schwarzen Rändern ohne Zuschnitt hinein. Beim Verbinden wird Audio auf 48 kHz und die größere Kanalzahl gebracht. Stumme Clips bleiben stumm.',
 'es-ES': 'Hasta dos clips, con un total seleccionado de 10 minutos. El lienzo sigue al primer clip; los demás se ajustan con bordes negros, sin recorte. Al unirlos, el audio se remuestrea a 48 kHz y usa el mayor número de canales. Los clips sin sonido conservan el silencio.',
 'fr-FR': 'Deux clips au maximum, pour 10 minutes sélectionnées au total. Le cadre suit le premier clip ; les autres tiennent avec des bandes noires, sans rognage. La fusion rééchantillonne le son à 48 kHz et utilise le plus grand nombre de canaux. Les clips muets restent muets.',
 'it-IT': 'Fino a due clip, con 10 minuti selezionati in totale. Il quadro segue il primo clip; gli altri si adattano con bordi neri, senza ritaglio. Unendo i clip, l’audio viene ricampionato a 48 kHz e usa il maggior numero di canali. I clip senza audio restano silenziosi.',
 'pt-BR': 'Até dois clipes, com 10 minutos selecionados no total. O quadro segue o primeiro clipe; os demais se ajustam com bordas pretas, sem corte. A união reamostra o áudio para 48 kHz e usa o maior número de canais. Clipes sem áudio mantêm o silêncio.',
 'ru-RU': 'До двух клипов, суммарно до 10 выбранных минут. Размер кадра задаёт первый клип; остальные вписываются с чёрными полями без обрезки. При объединении звук преобразуется в 48 кГц с большим числом каналов. Клипы без звука остаются беззвучными.',
 'uk-UA': 'До двох кліпів, разом до 10 вибраних хвилин. Кадр визначає перший кліп; інші вписуються з чорними полями без обрізання. При об’єднанні звук перетворюється на 48 кГц із більшою кількістю каналів. Кліпи без звуку залишаються беззвучними.',
 'ar': 'حتى مقطعين بإجمالي محدد لا يتجاوز 10 دقائق. يتبع إطار الإخراج المقطع الأول؛ تلائم المقاطع الأخرى الإطار بحدود سوداء دون قص. عند الدمج يُعاد أخذ عينات الصوت إلى 48 كيلوهرتز مع العدد الأكبر من القنوات. تبقى المقاطع الصامتة صامتة.',
 'fa': 'حداکثر دو کلیپ با مجموع انتخاب‌شدهٔ ۱۰ دقیقه. قاب خروجی از کلیپ اول پیروی می‌کند؛ کلیپ‌های دیگر بدون برش با حاشیهٔ سیاه جا می‌گیرند. هنگام ادغام، صدا به ۴۸ کیلوهرتز و تعداد کانال بیشتر تبدیل می‌شود. کلیپ‌های بی‌صدا بی‌صدا می‌مانند.',
 'hi': 'अधिकतम दो क्लिप, चुनी गई कुल अवधि 10 मिनट तक। आउटपुट का आकार पहली क्लिप के अनुसार है; दूसरी क्लिप बिना काटे काले किनारों के साथ फिट होती है। जोड़ते समय ऑडियो 48 kHz और अधिक चैनलों में बदला जाता है। बिना आवाज़ वाली क्लिप मौन रहती हैं।',
 'bn': 'সর্বোচ্চ দুটি ক্লিপ, নির্বাচিত মোট সময় ১০ মিনিট। প্রথম ক্লিপ আউটপুটের ফ্রেম নির্ধারণ করে; অন্য ক্লিপ কালো কিনারা দিয়ে না কেটে মাপে বসে। জোড়ার সময় অডিও ৪৮ kHz এবং বেশি চ্যানেলে রূপান্তরিত হয়। শব্দহীন ক্লিপ নীরব থাকে।',
 'id': 'Maksimal dua klip, dengan total pilihan 10 menit. Bingkai keluaran mengikuti klip pertama; klip lain disesuaikan dengan tepi hitam tanpa dipotong. Penggabungan mengubah audio ke 48 kHz dan jumlah kanal yang lebih besar. Klip tanpa suara tetap hening.',
 'vi': 'Tối đa hai đoạn, tổng thời lượng chọn 10 phút. Khung đầu ra theo đoạn đầu; đoạn khác vừa khung với viền đen, không cắt hình. Khi ghép, âm thanh được lấy mẫu lại ở 48 kHz với số kênh lớn hơn. Đoạn không có âm thanh vẫn im lặng.',
 'ja-JP': '最大2クリップ、選択した合計は10分以内です。出力の枠は最初のクリップに合わせ、他のクリップは切り取らず黒い余白で収めます。結合時は音声を48 kHzと多い方のチャンネル数に変換します。無音クリップは無音のままです。',
 'ko-KR': '최대 두 클립, 선택한 총 길이는 10분입니다. 출력 화면은 첫 클립을 따르며 다른 클립은 자르지 않고 검은 여백으로 맞춥니다. 결합 시 오디오는 48 kHz와 더 많은 채널 수로 변환됩니다. 무음 클립은 무음으로 유지됩니다.',
 'zh-Hans': '最多两个片段，所选总时长不超过10分钟。输出画面以第一个片段为准；其他片段以黑边适应画面，不裁剪。合并时音频重采样为48 kHz，并采用较多的声道数。无声片段保持静音。',
 'zh-Hant': '最多兩個片段，所選總時長不超過10分鐘。輸出畫面以第一個片段為準；其他片段以黑邊適應畫面，不裁切。合併時音訊重新取樣為48 kHz，並採用較多的聲道數。無聲片段保持靜音。',
 'pl-PL': 'Do dwóch klipów, łącznie do 10 wybranych minut. Kadr wyniku odpowiada pierwszemu klipowi; pozostałe mieszczą się z czarnymi pasami, bez przycinania. Łączenie przelicza dźwięk do 48 kHz i większej liczby kanałów. Klipy bez dźwięku pozostają nieme.',
 'nl-NL': 'Maximaal twee clips, met samen 10 geselecteerde minuten. Het uitvoerkader volgt de eerste clip; andere clips passen met zwarte randen, zonder bijsnijden. Samenvoegen zet audio om naar 48 kHz en het grotere aantal kanalen. Stille clips blijven stil.',
 'ro-RO': 'Cel mult două clipuri, cu 10 minute selectate în total. Cadrul rezultatului urmează primul clip; celelalte se încadrează cu margini negre, fără decupare. Îmbinarea reeșantionează sunetul la 48 kHz și folosește numărul mai mare de canale. Clipurile fără sunet rămân silențioase.',
}

def main():
    for old, new in [('es-ES', 'es'), ('ru-RU', 'ru'), ('vi', 'vi-VN'), ('ro-RO', 'ro')]: TEXT[new] = TEXT.pop(old)
    for unused in ['uk-UA', 'bn', 'nl-NL']: TEXT.pop(unused)
    TEXT.update({
        'th-TH': 'สูงสุดสองคลิป โดยเวลารวมที่เลือกไม่เกิน 10 นาที กรอบเอาต์พุตตามคลิปแรก คลิปอื่นจะพอดีด้วยขอบสีดำโดยไม่ครอบตัด เมื่อรวมคลิป เสียงจะปรับเป็น 48 kHz และใช้จำนวนช่องที่มากกว่า คลิปที่ไม่มีเสียงยังคงเงียบ',
        'sw': 'Hadi klipu mbili, jumla iliyochaguliwa dakika 10. Fremu ya toleo hufuata klipu ya kwanza; nyingine hutoshea kwa mipaka myeusi bila kukatwa. Kuunganisha hubadilisha sauti kuwa 48 kHz na idadi kubwa ya chaneli. Klipu zisizo na sauti hubaki kimya.',
        'ha': 'Klip biyu ne iyaka, jimillar lokacin da aka zaɓa minti 10. Firam ɗin fitarwa yana bin klip na farko; sauran suna dacewa da gefuna baƙi ba tare da yanke hoto ba. Haɗawa yana sauya sauti zuwa 48 kHz da yawan tashoshi mafi girma. Klip marasa sauti suna nan a shiru.',
    })
    path = Path(__file__).with_name('catalog.json')
    data = json.loads(path.read_text(encoding='utf-8'))
    assert set(TEXT) == set(data['locales'])
    key = 'video_join_hint'
    if key not in data['keys']:
        data['keys'].append(key)
        for values in data['locales'].values(): values.append('')
    for locale, values in data['locales'].items(): values[data['keys'].index(key)] = TEXT[locale]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    subprocess.run([sys.executable, str(Path(__file__).with_name('generate.py'))], check=True)

if __name__ == '__main__': main()
