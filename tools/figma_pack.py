"""Упаковка кадров прототипа для передачи в Figma: JSON {n,s,f,l,x,y} → deflate → base64 с префиксом размера."""
import json, zlib, base64, sys, os
FR = [  # имя файла, имя кадра, x, y
 ('00-Дизайн-система', '00 Дизайн-система', 0, 0),
 ('01-Главная', '01 Главная', 0, 0), ('02-Каталог', '02 Каталог', 1640, 0), ('03-О_клубе', '03 О клубе', 3280, 0),
 ('04-Личный_кабинет', '04 Личный кабинет', 4920, 0), ('04b-Пополнение_баланса', '04b Пополнение баланса', 4920, 2600),
 ('05-Обратная_связь', '05 Обратная связь', 6560, 0), ('05b-Обратная_связь_ошибки', '05b Обратная связь — ошибки', 6560, 1640), ('05c-Заявка_отправлена', '05c Заявка отправлена', 6560, 3340),
 ('06-Вход', '06 Вход', 8200, 0), ('06b-Вход_ошибка', '06b Вход — ошибка', 8200, 1500),
 ('07-Регистрация', '07 Регистрация', 9840, 0), ('07b-Регистрация_ошибки', '07b Регистрация — ошибки', 9840, 1640)]
os.makedirs('figma/pack', exist_ok=True)
for f, n, x, y in FR:
    svg = open(f'figma/fig/{f}.svg', encoding='utf-8').read()
    fonts = ','.join(json.load(open(f'figma/fig/{f}.fonts.json')))
    links = json.load(open(f'figma/fig/{f}.links.json'))
    raw = json.dumps({'n': n, 's': svg, 'f': fonts, 'l': [[l['x'], l['y'], l['w'], l['h'], l['href']] for l in links], 'x': x, 'y': y}, ensure_ascii=False, separators=(',', ':')).encode()
    co = zlib.compressobj(9, zlib.DEFLATED, -15); c = co.compress(raw) + co.flush()
    packed = f'{len(raw)}:' + base64.b64encode(c).decode()
    open(f'figma/pack/{f}.txt', 'w').write(packed)
    print(f, len(raw), len(packed))
