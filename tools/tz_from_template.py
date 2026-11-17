"""ТЗ на ИС «TurboPC» по шаблону преподавателя (tz-shablon/шаблон_ТЗ.docx).
Берёт из шаблона титульный лист, оглавление, стили, нумерацию и таблицы; содержимое заполняется по макету TurboPC.
usage: python3 tools/tz_from_template.py tz-shablon/шаблон_ТЗ.docx tz-shablon/ТЗ_TurboPC.docx"""
import sys, copy
from docx import Document
from docx.shared import Cm, Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
from tz_content import CONTENT, TITLE, AUTHOR, FUNCS, WORKS, ACCEPT_DATES, SCREENS

src, out = sys.argv[1], sys.argv[2]
d = Document(src)
P = d.paragraphs
body = d.element.body

# ---------- прототипы абзацев из шаблона ----------
proto = {
    'h1': P[25], 'h2': P[26], 'h3': P[27], 'h4': P[116],
    'p': P[28], 'n': P[55], 'b': P[58], 'bb': P[70],
}
proto = {k: copy.deepcopy(v._p) for k, v in proto.items()}
T_func, T_works, T_accept, T_docs = [copy.deepcopy(t._tbl) for t in d.tables]


def set_text(p_el, text, bold=None, italic_prefix=None):
    """Заменяет текст абзаца, сохраняя свойства абзаца и первого прогона; убирает выделение маркером."""
    runs = p_el.findall(qn('w:r'))
    rpr = None
    for r in runs:
        if r.find(qn('w:rPr')) is not None:
            rpr = copy.deepcopy(r.find(qn('w:rPr'))); break
    for ch in list(p_el):
        if ch.tag != qn('w:pPr'):
            p_el.remove(ch)
    if rpr is not None:
        for h in rpr.findall(qn('w:highlight')) + rpr.findall(qn('w:shd')):
            rpr.remove(h)
        for tag in ('w:b', 'w:bCs', 'w:i', 'w:iCs'):
            for e in rpr.findall(qn(tag)):
                rpr.remove(e)
    parts = [(italic_prefix, True), (text, False)] if italic_prefix else [(text, False)]
    for t, ital in parts:
        r = OxmlElement('w:r')
        rp = copy.deepcopy(rpr) if rpr is not None else OxmlElement('w:rPr')
        if bold:
            rp.append(OxmlElement('w:b'))
        if ital:
            rp.append(OxmlElement('w:i'))
        r.append(rp)
        for k, seg in enumerate(t.replace('\t', ' ').split('\t')):
            if k:
                r.append(OxmlElement('w:tab'))
            te = OxmlElement('w:t'); te.set(qn('xml:space'), 'preserve'); te.text = seg; r.append(te)
        p_el.append(r)
    return p_el


num_root = d.part.numbering_part.element
def new_numbering(src_p):
    """Новый экземпляр нумерованного списка (нумерация с 1) на основе списка шаблона."""
    old = src_p.find(qn('w:pPr')).find(qn('w:numPr')).find(qn('w:numId')).get(qn('w:val'))
    n_old = [x for x in num_root.findall(qn('w:num')) if x.get(qn('w:numId')) == old][0]
    abs_id = n_old.find(qn('w:abstractNumId')).get(qn('w:val'))
    new_id = str(max(int(x.get(qn('w:numId'))) for x in num_root.findall(qn('w:num'))) + 1)
    n = OxmlElement('w:num'); n.set(qn('w:numId'), new_id)
    a = OxmlElement('w:abstractNumId'); a.set(qn('w:val'), abs_id); n.append(a)
    lo = OxmlElement('w:lvlOverride'); lo.set(qn('w:ilvl'), '0')
    so = OxmlElement('w:startOverride'); so.set(qn('w:val'), '1'); lo.append(so); n.append(lo)
    num_root.append(n)
    return new_id


def page_break_before(p_el):
    ppr = p_el.find(qn('w:pPr'))
    e = OxmlElement('w:pageBreakBefore'); ppr.insert(0, e) if ppr is not None else None


# ---------- титульный лист ----------
set_text(P[18]._p, TITLE, bold=True)
set_text(P[20]._p, AUTHOR[0])
anchor = P[20]._p
for line in AUTHOR[1:]:
    np_ = set_text(copy.deepcopy(P[20]._p), line); anchor.addnext(np_); anchor = np_

# ---------- удаляем старое содержимое после оглавления ----------
sdt = [c for c in body if c.tag == qn('w:sdt')][0]
started = False
for c in list(body):
    if c is sdt:
        started = True; continue
    if started and c.tag != qn('w:sectPr'):
        body.remove(c)
sect = body.find(qn('w:sectPr'))


def add(el):
    sect.addprevious(el); return el


def para(kind, text, **kw):
    return add(set_text(copy.deepcopy(proto[kind]), text, **kw))


def cell_fill(tc, kind_el, texts, bold=False):
    """Заполняет ячейку таблицы абзацами-клонами kind_el."""
    for p in tc.findall(qn('w:p')):
        tc.remove(p)
    for t in texts:
        tc.append(set_text(copy.deepcopy(kind_el), t, bold=bold))


def table_functions():
    t = copy.deepcopy(T_func)
    rows = t.findall(qn('w:tr')); hdr, proto_row = rows[0], rows[1]
    t.remove(proto_row)
    c0p = proto_row.findall(qn('w:tc'))[0].find(qn('w:p'))
    c1p = proto_row.findall(qn('w:tc'))[1].find(qn('w:p'))
    for func, tasks in FUNCS:
        r = copy.deepcopy(proto_row); tcs = r.findall(qn('w:tc'))
        cell_fill(tcs[0], c0p, [func]); cell_fill(tcs[1], c1p, tasks)
        t.append(r)
    add(t)


def table_works():
    t = copy.deepcopy(T_works)
    rows = t.findall(qn('w:tr')); stage_row, sub_row = rows[1], rows[2]
    for r in rows[1:]:
        t.remove(r)
    for num, title, term, subs in WORKS:
        r = copy.deepcopy(stage_row); tcs = r.findall(qn('w:tc'))
        for tc, txt in zip(tcs, (num, title, term)):
            p = tc.find(qn('w:p')); set_text(p, txt, bold=True)
        t.append(r)
        for sn, stxt in subs:
            r = copy.deepcopy(sub_row); tcs = r.findall(qn('w:tc'))
            for tc, txt in zip(tcs, (sn, stxt, '')):
                p = tc.find(qn('w:p')); set_text(p, txt)
            t.append(r)
    add(t)


def table_accept():
    t = copy.deepcopy(T_accept)
    for r, dates in zip(t.findall(qn('w:tr'))[1:], ACCEPT_DATES):
        tc = r.findall(qn('w:tc'))[2]; set_text(tc.find(qn('w:p')), dates)
        for tc in r.findall(qn('w:tc')):
            for p in tc.findall(qn('w:p')):
                for rr in p.findall(qn('w:r')):
                    rp = rr.find(qn('w:rPr'))
                    if rp is not None:
                        for h in rp.findall(qn('w:highlight')):
                            rp.remove(h)
    add(t)


def table_docs():
    t = copy.deepcopy(T_docs)
    # в шаблоне ячейки оформлены стилем заголовка — переводим в обычный текст, чтобы не попадали в оглавление
    for p in t.iter(qn('w:p')):
        ps = p.find(qn('w:pPr'))
        if ps is not None and ps.find(qn('w:pStyle')) is not None:
            ps.find(qn('w:pStyle')).set(qn('w:val'), d.styles['Normal'].style_id)
        for r in p.findall(qn('w:r')):
            rp = r.find(qn('w:rPr'))
            if rp is None:
                rp = OxmlElement('w:rPr'); r.insert(0, rp)
            f = OxmlElement('w:rFonts')
            for a in ('w:ascii', 'w:hAnsi', 'w:cs'):
                f.set(qn(a), 'Times New Roman')
            rp.append(f)
            sz = OxmlElement('w:sz'); sz.set(qn('w:val'), '24'); rp.append(sz)
    add(t)


def screens():
    for path, caption in SCREENS:
        p = para('p', '')
        pp = d.paragraphs[[x._p for x in d.paragraphs].index(p)]
        pp.paragraph_format.first_line_indent = Cm(0); pp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        pp.paragraph_format.keep_with_next = True
        pp.add_run().add_picture(path, width=Cm(16))
        c = para('p', caption)
        cp = d.paragraphs[[x._p for x in d.paragraphs].index(c)]
        cp.alignment = WD_ALIGN_PARAGRAPH.CENTER; cp.paragraph_format.first_line_indent = Cm(0)
        cp.paragraph_format.space_after = Pt(12)


first_h1 = True
num_id = None
prev = None
for item in CONTENT:
    kind, text = item[0], item[1]
    if kind == 'h1':
        el = para('h1', text)
        page_break_before(el)
        first_h1 = False
    elif kind == 'n':
        if prev != 'n':
            num_id = new_numbering(proto['n'])
        el = para('n', text)
        el.find(qn('w:pPr')).find(qn('w:numPr')).find(qn('w:numId')).set(qn('w:val'), num_id)
    elif kind == 'p_i':  # абзац с курсивным началом, как в шаблоне («Целями создания…»)
        para('p', item[2], italic_prefix=text)
    elif kind == 'TABLE_FUNCS':
        table_functions()
    elif kind == 'TABLE_WORKS':
        table_works()
    elif kind == 'TABLE_ACCEPT':
        table_accept()
    elif kind == 'TABLE_DOCS':
        table_docs()
    elif kind == 'SCREENS':
        screens()
    else:
        para(kind, text)
    prev = kind

d.core_properties.title = 'Техническое задание: ' + TITLE
d.core_properties.author = 'Разработчик ИС TurboPC'
d.save(out)
print('saved', out)
