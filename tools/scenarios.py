"""Схема пользовательских сценариев (приложение А): три дорожки с шагами по страницам прототипа."""
import sys
lanes = [
 ('Сценарий 1. Забронировать ПК', ['Главная', 'Кнопка «Забронировать»', 'Каталог → Схема зала', 'Дата, время, длительность', 'Выбор свободного места', 'Итог: место, тариф, сумма', 'Вход / регистрация', 'Личный кабинет: бронь в «Мои брони»']),
 ('Сценарий 2. Пополнить баланс', ['Шапка: баланс / профиль', 'Личный кабинет', 'Карточка «Баланс» → «Пополнить»', 'Окно: сумма 300–2000 ₽', 'Способ: СБП / карта / стойка', '«Оплатить»', 'История платежей']),
 ('Сценарий 3. Записаться на турнир', ['Главная: «Ближайшие турниры»', 'Карточка турнира', '«Записаться»', 'Личный кабинет', '«Мои турниры»: статус заявки']),
 ('Сценарий 4. Обратная связь', ['Меню «Обратная связь»', 'Форма: имя, телефон, тема, сообщение', 'Согласие на обработку ПД', '«Отправить»', 'Ошибки под полями / «Заявка отправлена»']),
]
CW, BW, BH, GY = 300, 250, 70, 26
H = 70 + max(len(st) for _, st in lanes) * (BH + GY) + 20
W = 20 + CW * len(lanes)
out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}"><rect width="100%" height="100%" fill="#fff"/>',
       '<defs><marker id="a" markerWidth="10" markerHeight="10" refX="5" refY="9" orient="auto-start-reverse"><path d="M0 0 L10 0 L5 10 z" fill="#0E7C93"/></marker></defs>',
       '<style>text{font-family:"Liberation Sans",Arial}</style>']
for i, (title, steps) in enumerate(lanes):
    x0 = 10 + i * CW
    out.append(f'<rect x="{x0}" y="10" width="{CW-14}" height="{H-20}" rx="12" fill="{"#F3F8FA" if i % 2 == 0 else "#FFF6F0"}" stroke="#C9D6DC"/>')
    tl = title.split('. ')
    out.append(f'<text x="{x0+(CW-14)/2}" y="36" font-size="17" font-weight="700" fill="#B0185A" text-anchor="middle">{tl[0]}</text>')
    out.append(f'<text x="{x0+(CW-14)/2}" y="58" font-size="17" font-weight="700" fill="#B0185A" text-anchor="middle">{tl[1]}</text>')
    for j, st in enumerate(steps):
        x = x0 + (CW - 14 - BW) / 2; by = 76 + j * (BH + GY)
        out.append(f'<rect x="{x}" y="{by}" width="{BW}" height="{BH}" rx="8" fill="#fff" stroke="#0E7C93" stroke-width="2"/>')
        words = st.split(' '); lines = ['']
        for w in words:
            if len(lines[-1]) + len(w) > 26: lines.append('')
            lines[-1] += (' ' if lines[-1] else '') + w
        for k, ln in enumerate(lines):
            out.append(f'<text x="{x + BW/2}" y="{by + BH/2 + (k - (len(lines)-1)/2) * 19 + 6}" font-size="16" text-anchor="middle" fill="#1A1A1A">{ln.replace("&","&amp;").replace("<","&lt;")}</text>')
        if j < len(steps) - 1:
            cx = x + BW/2; out.append(f'<line x1="{cx}" y1="{by+BH}" x2="{cx}" y2="{by+BH+GY-9}" stroke="#0E7C93" stroke-width="2"/><path d="M{cx-6} {by+BH+GY-10} L{cx+6} {by+BH+GY-10} L{cx} {by+BH+GY-1} z" fill="#0E7C93"/>')
out.append('</svg>'); open(sys.argv[1], 'w').write(''.join(out))
