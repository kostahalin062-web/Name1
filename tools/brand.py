"""Генерация фирменных SVG TurboPC: логотип (цвет/моно), знак, баннер.
Текст переводится в кривые (fontTools), поэтому SVG не зависят от установленных шрифтов."""
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
import os
F = 'prototype/assets/fonts/'
fonts = {}
def font(name):
    if name not in fonts: fonts[name] = TTFont(F + name)
    return fonts[name]

def text_path(text, fname, size, x, y, ls=0.0, skew=0.0):
    """Возвращает (d, ширина). skew — наклон (x += skew * (-y_glyph))."""
    f = font(fname); gs = f.getGlyphSet(); cmap = f.getBestCmap(); upm = f['head'].unitsPerEm
    s = size / upm; pen = SVGPathPen(gs); cx = 0.0
    for ch in text:
        g = cmap[ord(ch)]
        tp = TransformPen(pen, (s, 0, skew * s, -s, x + cx, y))
        gs[g].draw(tp)
        cx += f['hmtx'][g][0] * s + ls
    return pen.getCommands(), cx - ls

SL = 0.2
def sk(x, y): return (x + SL * (100 - y), y)
def poly(pts, r=0):
    if not r: return 'M' + ' L'.join(f'{x:.2f} {y:.2f}' for x, y in pts) + ' Z'
    import math; d = ''; n = len(pts)
    for i in range(n):
        p0, p1, p2 = pts[i-1], pts[i], pts[(i+1) % n]
        def toward(a, b, t):
            L = math.hypot(b[0]-a[0], b[1]-a[1]); return (a[0]+(b[0]-a[0])*t/L, a[1]+(b[1]-a[1])*t/L)
        a = toward(p1, p0, r); b = toward(p1, p2, r)
        d += (f'M{a[0]:.2f} {a[1]:.2f}' if i == 0 else f' L{a[0]:.2f} {a[1]:.2f}') + f' Q{p1[0]:.2f} {p1[1]:.2f} {b[0]:.2f} {b[1]:.2f}'
    return d + ' Z'

BADGE = poly([sk(0, 0), sk(80, 0), sk(80, 100), sk(0, 100)], 9)
T = poly([sk(*p) for p in [(15, 17), (65, 17), (65, 34), (49, 34), (49, 83), (31, 83), (31, 34), (15, 34)]], 2.5)
SPEED = [poly([sk(*p) for p in [(x0, y0), (-8, y0), (-8, y0 + 8), (x0, y0 + 8)]], 2) for x0, y0 in ((-40, 22), (-30, 46), (-20, 70))]
MARK_BOX = (-24, 0, 124, 100)  # x0 y0 w h (с учётом наклона)

ORANGE, ORANGE2, CYAN, DARK, WHITE, MUTED = '#FF6A1A', '#FF2E4D', '#1ED8F5', '#0A0D14', '#EEF1F8', '#A3ACC2'
GRAD = f'<linearGradient id="tpcG" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FF8A2B"/><stop offset=".55" stop-color="{ORANGE}"/><stop offset="1" stop-color="{ORANGE2}"/></linearGradient>'

def mark(mode='color'):
    if mode == 'color':
        return (f'<path d="{BADGE}" fill="url(#tpcG)"/><path d="{T}" fill="{DARK}"/>' +
                ''.join(f'<path d="{s}" fill="{CYAN}"/>' for s in SPEED))
    return f'<path d="{BADGE} {T}" fill-rule="evenodd" fill="{mode}"/>' + ''.join(f'<path d="{s}" fill="{mode}"/>' for s in SPEED)

def logo(mode='color', tagline=True):
    tx = 116
    d1, w1 = text_path('TURBO', 'Unbounded-800.ttf', 60, tx, 66, ls=1, skew=SL)
    d2, w2 = text_path('PC', 'Unbounded-800.ttf', 60, tx + w1 + 3, 66, ls=1, skew=SL)
    c1, c2, c3 = (WHITE, ORANGE, MUTED) if mode == 'color' else (mode, mode, mode)
    body = mark(mode) + f'<path d="{d1}" fill="{c1}"/><path d="{d2}" fill="{c2}"/>'
    W = tx + w1 + 3 + w2 + 12
    if tagline:
        d3, w3 = text_path('КОМПЬЮТЕРНЫЙ КЛУБ', 'Unbounded-500.ttf', 13.5, tx + 2, 94, ls=4.15)
        body += f'<path d="{d3}" fill="{c3}"/>'
    defs = f'<defs>{GRAD}</defs>' if mode == 'color' else ''
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-30 -6 {W + 36:.0f} 112" width="{W + 36:.0f}" height="112">'
            f'<title>TurboPC</title>{defs}{body}</svg>')

def mark_svg(mode='color', bg=None):
    defs = f'<defs>{GRAD}</defs>' if mode == 'color' else ''
    b = f'<rect x="-34" y="-12" width="152" height="124" rx="28" fill="{bg}"/>' if bg else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-34 -12 152 124" width="152" height="124"><title>TurboPC</title>{defs}{b}{mark(mode)}</svg>'

def banner_art(w=1440, h=500, vx=1060, vy=250, mark_tf='translate(1030 100) scale(2.85)', mark_op=1, glow=(1130, 250)):
    """Фоновая графика баннера без текста."""
    import random; random.seed(7)
    g = []
    # перспективная сетка «пола»
    for i in range(-14, 15):
        x2 = vx + i * 140
        g.append(f'<line x1="{vx}" y1="{vy}" x2="{x2*1.0:.0f}" y2="{h}" />')
    for k in range(1, 9):
        y = vy + (h - vy) * (k / 8) ** 1.8
        g.append(f'<line x1="0" y1="{y:.0f}" x2="{w}" y2="{y:.0f}" />')
    grid = f'<g stroke="{CYAN}" stroke-width="1" opacity=".16" clip-path="url(#floor)">{"".join(g)}</g>'
    # полосы скорости
    st = []
    for i in range(26):
        y = random.uniform(40, 460); L = random.uniform(120, 520); x = random.uniform(w * .39, w - L * .3)
        col = random.choice([ORANGE, CYAN, ORANGE, '#FFFFFF']); op = random.uniform(.15, .55); th = random.choice([1, 1.5, 2, 3])
        st.append(f'<rect x="{x:.0f}" y="{y:.0f}" width="{L:.0f}" height="{th}" rx="{th/2}" fill="url(#streak{col[1:]})" opacity="{op:.2f}"/>')
    grads = ''.join(f'<linearGradient id="streak{c[1:]}" x1="0" x2="1"><stop offset="0" stop-color="{c}" stop-opacity="0"/><stop offset="1" stop-color="{c}"/></linearGradient>' for c in (ORANGE, CYAN, '#FFFFFF'))
    big = f'<g transform="{mark_tf}" opacity="{mark_op}" filter="url(#glow)">{mark("color")}</g>' if mark_tf else ''
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}" preserveAspectRatio="xMidYMid slice">
<defs>{GRAD}{grads}
<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0A0D14"/><stop offset=".6" stop-color="#0E1222"/><stop offset="1" stop-color="#1A0D18"/></linearGradient>
<radialGradient id="glowO" cx="{glow[0]}" cy="{glow[1]}" r="420" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{ORANGE}" stop-opacity=".42"/><stop offset="1" stop-color="{ORANGE}" stop-opacity="0"/></radialGradient>
<radialGradient id="glowC" cx="120" cy="-40" r="520" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{CYAN}" stop-opacity=".22"/><stop offset="1" stop-color="{CYAN}" stop-opacity="0"/></radialGradient>
<clipPath id="floor"><rect x="0" y="{vy}" width="{w}" height="{h-vy}"/></clipPath>
<filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="9" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
</defs>
<rect width="{w}" height="{h}" fill="url(#bg)"/><rect width="{w}" height="{h}" fill="url(#glowO)"/><rect width="{w}" height="{h}" fill="url(#glowC)"/>
{grid}{"".join(st)}{big}
<rect x="0" y="{h-2}" width="{w}" height="2" fill="url(#streakFF6A1A)" opacity=".8"/>
</svg>'''

def banner_full():
    art = banner_art()
    inner = art.split('>', 1)[1].rsplit('</svg>', 1)[0]
    x = 120; parts = []
    # плашка
    dp, wp = text_path('КОМПЬЮТЕРНЫЙ КЛУБ · 24/7', 'Inter-SemiBold.otf', 14, x + 18, 132, ls=1.6)
    parts.append(f'<rect x="{x}" y="110" width="{wp + 36:.0f}" height="32" rx="16" fill="{CYAN}" fill-opacity=".1" stroke="{CYAN}" stroke-opacity=".5"/><path d="{dp}" fill="{CYAN}"/>')
    d1, _ = text_path('ИГРАЙ НА ПОЛНОЙ', 'Unbounded-800.ttf', 60, x, 228, skew=0)
    d2, _ = text_path('СКОРОСТИ', 'Unbounded-800.ttf', 60, x, 298)
    parts.append(f'<path d="{d1}" fill="{WHITE}"/><path d="{d2}" fill="url(#tpcG)"/>')
    s1, _ = text_path('240 Гц, мощное железо и VIP-зал. Бронируй место', 'Inter-Regular.otf', 20, x, 352)
    s2, _ = text_path('онлайн за минуту — без звонков и очередей.', 'Inter-Regular.otf', 20, x, 382)
    parts.append(f'<path d="{s1}" fill="{MUTED}"/><path d="{s2}" fill="{MUTED}"/>')
    b1, bw = text_path('Забронировать ПК', 'Inter-SemiBold.otf', 18, x + 28, 444)
    parts.append(f'<rect x="{x}" y="414" width="{bw + 56 + 28:.0f}" height="48" rx="12" fill="{ORANGE}"/><path d="{b1}" fill="{DARK}"/>'
                 f'<path d="M{x + 28 + bw + 12:.0f} 438 h14 m-6 -6 l6 6 l-6 6" stroke="{DARK}" stroke-width="2.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/>')
    b2, bw2 = text_path('Смотреть тарифы', 'Inter-SemiBold.otf', 18, x + bw + 84 + 16 + 24, 444)
    parts.append(f'<rect x="{x + bw + 84 + 16:.0f}" y="414" width="{bw2 + 48:.0f}" height="48" rx="12" fill="none" stroke="#3A4560" stroke-width="1.5"/><path d="{b2}" fill="{WHITE}"/>')
    return art.split('>', 1)[0] + '>' + inner + ''.join(parts) + '</svg>'

os.makedirs('brand', exist_ok=True)
W = lambda p, s: open(p, 'w', encoding='utf-8').write(s)
W('brand/logo.svg', logo('color'))
W('brand/logo-mono.svg', logo('#0A0D14'))
W('brand/logo-mono-white.svg', logo('#FFFFFF'))
W('brand/logo-mark.svg', mark_svg('color'))
W('brand/logo-mark-mono.svg', mark_svg('#0A0D14'))
W('brand/favicon.svg', mark_svg('color', bg=DARK))
W('brand/banner-art.svg', banner_art())
W('brand/banner.svg', banner_full())
W('prototype/assets/logo.svg', logo('color'))
W('prototype/assets/logo-compact.svg', logo('color', tagline=False))
W('prototype/assets/logo-mono-white.svg', logo('#FFFFFF'))
W('prototype/assets/favicon.svg', mark_svg('color', bg=DARK))
W('prototype/assets/banner-art.svg', banner_art())
W('prototype/assets/mark.svg', mark_svg('color'))
W('prototype/assets/auth-art.svg', banner_art(720, 900, vx=360, vy=560, mark_tf='translate(520 740) scale(1.2)', mark_op=.9, glow=(620, 820)))
print('ok')
