"""Генерация SVG концептуальной модели данных ИС «TurboPC»."""
import sys
W = 300
ents = {
 'tariff': ('Тариф', 40, 30, ['PK id', 'FK зона', 'название', 'цена за час', 'действует с — по']),
 'zone': ('Зона', 440, 30, ['PK id', 'название', 'описание', 'кол-во мест']),
 'pack': ('Пакет часов', 840, 30, ['PK id', 'FK зона', 'название', 'кол-во часов', 'цена', 'срок действия']),
 'upack': ('Купленный пакет', 1240, 30, ['PK id', 'FK пользователь', 'FK пакет', 'остаток минут', 'дата окончания']),
 'place': ('Игровое место', 40, 300, ['PK id', 'FK зона', 'номер', 'конфигурация', 'статус']),
 'booking': ('Бронирование', 440, 300, ['PK id', 'FK пользователь', 'FK место', 'начало', 'окончание', 'стоимость', 'статус']),
 'user': ('Пользователь', 840, 300, ['PK id', 'имя', 'никнейм', 'телефон', 'e-mail', 'дата рождения', 'хеш пароля', 'роль', 'баланс', 'дата регистрации', 'статус']),
 'fin': ('Финансовая операция', 1240, 300, ['PK id', 'FK пользователь', 'тип', 'сумма', 'способ оплаты', 'дата', 'основание']),
 'feedback': ('Обращение', 40, 680, ['PK id', 'имя', 'телефон', 'тема', 'текст', 'согласие', 'дата', 'статус']),
 'session': ('Сессия', 440, 680, ['PK id', 'FK пользователь', 'FK место', 'FK бронирование', 'начало', 'окончание', 'стоимость']),
 'app': ('Заявка на турнир', 840, 680, ['PK id', 'FK турнир', 'FK пользователь', 'команда', 'статус', 'итоговое место']),
 'order': ('Заказ услуги', 1240, 680, ['PK id', 'FK пользователь', 'FK услуга', 'количество', 'сумма', 'дата']),
 'tour': ('Турнир', 840, 970, ['PK id', 'название', 'дисциплина', 'формат', 'дата', 'взнос', 'призовой фонд', 'лимит', 'статус']),
 'service': ('Доп. услуга', 1240, 970, ['PK id', 'название', 'категория', 'цена']),
}
rels = [('zone','tariff'),('zone','pack'),('zone','place'),('pack','upack'),('user','upack'),
        ('place','booking'),('user','booking'),('user','fin'),('booking','session'),('place','session'),
        ('user','session'),('user','app'),('tour','app'),('user','order'),('service','order')]
HDR, ROW = 40, 24
def box(k):
    t, x, y, a = ents[k]; return x, y, W, HDR + ROW*len(a) + 10
def clip(cx, cy, tx, ty, b):
    x, y, w, h = b; dx, dy = tx-cx, ty-cy
    ts = []
    if dx: ts += [((x if dx<0 else x+w)-cx)/dx]
    if dy: ts += [((y if dy<0 else y+h)-cy)/dy]
    t = min(t for t in ts if t > 0); return cx+dx*t, cy+dy*t
out = []
for a, b in rels:
    A, B = box(a), box(b)
    ax, ay = A[0]+A[2]/2, A[1]+A[3]/2; bx, by = B[0]+B[2]/2, B[1]+B[3]/2
    p1 = clip(ax, ay, bx, by, A); p2 = clip(bx, by, ax, ay, B)
    lab = '0..1' if (a, b) == ('booking','session') else 'N'
    def off(p, q, d=28):
        import math
        L = math.hypot(q[0]-p[0], q[1]-p[1]); ux, uy = (q[0]-p[0])/L, (q[1]-p[1])/L
        return p[0]+ux*d-uy*13, p[1]+uy*d+ux*13
    o1, o2 = off(p1, p2), off(p2, p1)
    out.append(f'<line x1="{p1[0]:.0f}" y1="{p1[1]:.0f}" x2="{p2[0]:.0f}" y2="{p2[1]:.0f}" stroke="#334" stroke-width="2"/>')
    out.append(f'<circle cx="{p2[0]:.0f}" cy="{p2[1]:.0f}" r="5" fill="#334"/>')
    out.append(f'<text x="{o1[0]:.0f}" y="{o1[1]+5:.0f}" class="c">1</text><text x="{o2[0]:.0f}" y="{o2[1]+5:.0f}" class="c">{lab}</text>')
for k, (t, x, y, a) in ents.items():
    _, _, w, h = box(k)
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="#fff" stroke="#223" stroke-width="2"/>')
    out.append(f'<path d="M{x} {y+8} a8 8 0 0 1 8 -8 h{w-16} a8 8 0 0 1 8 8 v{HDR-8} h-{w} z" fill="#0E7C93"/>')
    out.append(f'<text x="{x+w/2}" y="{y+27}" class="h">{t}</text>')
    for i, at in enumerate(a):
        cls = 'k' if at.startswith(('PK','FK')) else 'a'
        out.append(f'<text x="{x+14}" y="{y+HDR+22+i*ROW}" class="{cls}">{at}</text>')
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1580" height="1240" viewBox="0 0 1580 1240">
<style>text{{font-family:'Liberation Sans',Arial,sans-serif}} .h{{fill:#fff;font-size:19px;font-weight:700;text-anchor:middle}}
.a{{font-size:16px;fill:#222}} .k{{font-size:16px;fill:#0E7C93;font-weight:700}} .c{{font-size:16px;font-weight:700;fill:#B0185A;text-anchor:middle}}</style>
<rect width="1580" height="1240" fill="#fff"/>{''.join(out)}</svg>'''
open(sys.argv[1], 'w').write(svg)
