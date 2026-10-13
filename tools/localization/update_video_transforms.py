"""Localized canvas/speed controls; generated XML stays derived from the catalog."""
import json
from pathlib import Path
import subprocess, sys
# Canvas, original shape, fit, crop, speed, output duration, behavior disclosure.
TEXT = {
'en-US': ['Canvas','First clip','Fit · borders','Fill · center crop','Speed','Output duration: %1$s','Ranges use original time. Speed applies to both clips and preserves audio pitch; processing may change sound quality. Fit adds black borders; fill crops the center. Preview the generated result before saving.'],
' tr-TR'.strip(): ['Tuval','İlk klip','Sığdır · kenarlık','Doldur · ortadan kırp','Hız','Çıktı süresi: %1$s','Aralıklar özgün zamana göredir. Hız iki klibe de uygulanır ve ses perdesi korunur; işlem ses kalitesini değiştirebilir. Sığdırma siyah kenarlar ekler; doldurma ortadan kırpar. Kaydetmeden önce üretilen sonucu izleyin.'],
'es': ['Lienzo','Primer clip','Ajustar · bordes','Llenar · recorte central','Velocidad','Duración de salida: %1$s','Los intervalos usan el tiempo original. La velocidad se aplica a ambos clips y conserva el tono; el procesamiento puede cambiar la calidad del sonido. Ajustar añade bordes negros; llenar recorta el centro. Revisa el resultado antes de guardarlo.'],
'pt-BR': ['Quadro','Primeiro clipe','Ajustar · bordas','Preencher · recorte central','Velocidade','Duração da saída: %1$s','Os intervalos usam o tempo original. A velocidade vale para ambos os clipes e mantém o tom; o processamento pode alterar a qualidade do som. Ajustar adiciona bordas pretas; preencher recorta o centro. Veja o resultado antes de salvar.'],
'id': ['Kanvas','Klip pertama','Sesuaikan · tepi','Isi · potong tengah','Kecepatan','Durasi keluaran: %1$s','Rentang memakai waktu asli. Kecepatan berlaku untuk kedua klip dan menjaga nada audio; pemrosesan dapat mengubah kualitas suara. Sesuaikan menambah tepi hitam; isi memotong bagian tengah. Tinjau hasil sebelum menyimpan.'],
'hi': ['कैनवास','पहली क्लिप','फिट · किनारे','भरें · बीच से काटें','गति','आउटपुट अवधि: %1$s','सीमाएँ मूल समय के अनुसार हैं। गति दोनों क्लिप पर लागू होती है और आवाज़ का स्वर बना रहता है; ध्वनि की गुणवत्ता बदल सकती है। फिट में काले किनारे जुड़ते हैं; भरने पर बीच से काटा जाता है। सहेजने से पहले परिणाम देखें।'],
'ar': ['الإطار','المقطع الأول','ملاءمة · حدود','ملء · قص مركزي','السرعة','مدة الإخراج: %1$s','تستخدم النطاقات الوقت الأصلي. تطبق السرعة على المقطعين مع الحفاظ على طبقة الصوت؛ قد تتغير جودة الصوت. تضيف الملاءمة حدوداً سوداء ويقص الملء من الوسط. عاين الناتج قبل حفظه.'],
'ru': ['Кадр','Первый клип','Вписать · поля','Заполнить · обрезка по центру','Скорость','Длительность результата: %1$s','Диапазоны заданы в исходном времени. Скорость применяется к обоим клипам с сохранением высоты звука; качество звука может измениться. Вписывание добавляет чёрные поля, заполнение обрезает по центру. Просмотрите результат перед сохранением.'],
'ja-JP': ['キャンバス','最初のクリップ','収める · 黒い余白','埋める · 中央を切り抜く','速度','出力の長さ: %1$s','範囲は元の時間を使います。速度は両クリップに適用され、音の高さを保ちますが音質が変わる場合があります。収めると黒い余白が入り、埋めると中央で切り抜きます。保存前に出力を確認してください。'],
'fr-FR': ['Cadre','Premier clip','Ajuster · bordures','Remplir · recadrage centré','Vitesse','Durée de sortie : %1$s','Les plages utilisent le temps original. La vitesse concerne les deux clips et préserve la hauteur du son ; sa qualité peut changer. Ajuster ajoute des bandes noires ; remplir recadre au centre. Vérifiez le résultat avant de l’enregistrer.'],
'de-DE': ['Bildformat','Erster Clip','Einpassen · Ränder','Füllen · mittig zuschneiden','Geschwindigkeit','Ausgabedauer: %1$s','Bereiche verwenden die Originalzeit. Die Geschwindigkeit gilt für beide Clips und erhält die Tonhöhe; die Klangqualität kann sich ändern. Einpassen ergänzt schwarze Ränder, Füllen schneidet mittig zu. Prüfe das Ergebnis vor dem Speichern.'],
'pl-PL': ['Kadr','Pierwszy klip','Dopasuj · brzegi','Wypełnij · przytnij środek','Prędkość','Czas wyniku: %1$s','Zakresy używają czasu źródłowego. Prędkość dotyczy obu klipów i zachowuje wysokość dźwięku; jego jakość może się zmienić. Dopasowanie dodaje czarne brzegi, wypełnienie przycina środek. Obejrzyj wynik przed zapisem.'],
'ko-KR': ['캔버스','첫 클립','맞춤 · 여백','채우기 · 중앙 자르기','속도','출력 길이: %1$s','범위는 원본 시간을 사용합니다. 속도는 두 클립에 적용되며 음높이를 유지하지만 음질이 달라질 수 있습니다. 맞춤은 검은 여백을 추가하고 채우기는 중앙을 잘라냅니다. 저장 전에 결과를 확인하세요.'],
'zh-Hans': ['画布','第一个片段','适应 · 黑边','填充 · 居中裁剪','速度','输出时长：%1$s','区间使用原始时间。速度应用于两个片段并保留音高，但音质可能变化。适应添加黑边，填充居中裁剪。保存前请预览生成结果。'],
'zh-Hant': ['畫布','第一個片段','適應 · 黑邊','填滿 · 置中裁切','速度','輸出長度：%1$s','區間使用原始時間。速度套用至兩個片段並保留音高，但音質可能改變。適應會加入黑邊，填滿會置中裁切。儲存前請預覽產生的結果。'],
'it-IT': ['Quadro','Prima clip','Adatta · bordi','Riempi · ritaglio centrale','Velocità','Durata dell’uscita: %1$s','Gli intervalli usano il tempo originale. La velocità vale per entrambe le clip e mantiene il tono; la qualità del suono può cambiare. Adatta aggiunge bordi neri; riempi ritaglia al centro. Controlla il risultato prima di salvarlo.'],
' th-TH'.strip(): ['กรอบภาพ','คลิปแรก','พอดี · ขอบ','เต็ม · ครอบตรงกลาง','ความเร็ว','ระยะเวลาผลลัพธ์: %1$s','ช่วงใช้เวลาของต้นฉบับ ความเร็วใช้กับทั้งสองคลิปและคงระดับเสียง แต่อาจเปลี่ยนคุณภาพเสียง แบบพอดีเพิ่มขอบดำ แบบเต็มครอบตรงกลาง ดูผลลัพธ์ก่อนบันทึก'],
'vi-VN': ['Khung hình','Đoạn đầu','Vừa · viền','Đầy · cắt giữa','Tốc độ','Thời lượng đầu ra: %1$s','Khoảng chọn dùng thời gian gốc. Tốc độ áp dụng cho cả hai đoạn và giữ cao độ; chất lượng âm thanh có thể thay đổi. Vừa thêm viền đen; đầy cắt ở giữa. Xem kết quả trước khi lưu.'],
'fa': ['قاب','کلیپ اول','جا دادن · حاشیه','پر کردن · برش مرکزی','سرعت','زمان خروجی: %1$s','بازه‌ها از زمان اصلی استفاده می‌کنند. سرعت برای هر دو کلیپ اعمال می‌شود و زیر و بمی صدا حفظ می‌شود؛ کیفیت صدا ممکن است تغییر کند. جا دادن حاشیهٔ سیاه می‌افزاید و پر کردن مرکز را برش می‌دهد. پیش از ذخیره نتیجه را ببینید.'],
'sw': ['Fremu','Klipu ya kwanza','Toshea · kingo','Jaza · kata katikati','Kasi','Muda wa matokeo: %1$s','Vipindi hutumia muda asili. Kasi hutumika kwa klipu zote mbili na huhifadhi kiwango cha toni; ubora wa sauti unaweza kubadilika. Toshea huongeza kingo nyeusi; jaza hukata katikati. Hakiki matokeo kabla ya kuhifadhi.'],
'ha': ['Firam','Klip na farko','Daidaita · gefuna','Cika · yanke tsakiya','Sauri','Tsawon fitarwa: %1$s','Lokutan zaɓi suna bin asali. Sauri yana aiki a klip biyu kuma yana kiyaye kaifin sauti; ingancin sauti na iya sauyawa. Daidaitawa tana ƙara gefuna baƙi; cikawa tana yanke tsakiya. Duba sakamakon kafin adanawa.'],
'ro': ['Cadru','Primul clip','Încadrare · margini','Umplere · decupare centrală','Viteză','Durata rezultatului: %1$s','Intervalele folosesc timpul original. Viteza se aplică ambelor clipuri și păstrează înălțimea sunetului; calitatea poate varia. Încadrarea adaugă margini negre, umplerea decupează centrul. Verifică rezultatul înainte de salvare.'],
}
keys=['video_canvas','video_canvas_first','video_fit','video_crop','video_speed','video_output_duration','video_transform_hint']
p=Path(__file__).with_name('catalog.json');d=json.loads(p.read_text(encoding='utf-8'));assert set(TEXT)==set(d['locales'])
for i,key in enumerate(keys):
    if key not in d['keys']:
        d['keys'].append(key)
        for v in d['locales'].values(): v.append('')
    for locale,values in d['locales'].items(): values[d['keys'].index(key)]=TEXT[locale][i]
p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
subprocess.run([sys.executable,str(p.with_name('generate.py'))],check=True)
