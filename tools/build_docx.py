"""Сборка .docx из Markdown по требованиям оформления:
Times New Roman 14, интервал 1,5, поля 30/15/20/20 мм (лев/прав/верх/низ), нумерация страниц.
usage: build_docx.py input.md output.docx title.json [--resource-path DIR]"""
import json, subprocess, sys, os, copy
from docx import Document
from docx.shared import Pt, Mm, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

src, out, title_json = sys.argv[1:4]
res = os.path.dirname(os.path.abspath(src))
here = os.path.dirname(os.path.abspath(__file__))
ref = os.path.join(here, '_reference.docx')

def set_font(style, size=14, bold=None, italic=None, color=RGBColor(0, 0, 0)):
    f = style.font; f.name = 'Times New Roman'; f.size = Pt(size); f.color.rgb = color
    if bold is not None: f.bold = bold
    if italic is not None: f.italic = italic
    rpr = style.element.get_or_add_rPr(); rf = rpr.find(qn('w:rFonts'))
    if rf is None: rf = OxmlElement('w:rFonts'); rpr.append(rf)
    for a in ('w:ascii', 'w:hAnsi', 'w:cs', 'w:eastAsia'): rf.set(qn(a), 'Times New Roman')
    for t in ('w:asciiTheme', 'w:hAnsiTheme', 'w:cstheme', 'w:eastAsiaTheme'):
        if rf.get(qn(t)) is not None: del rf.attrib[qn(t)]

# 1. reference.docx со стилями
with open(ref, 'wb') as f:
    f.write(subprocess.check_output(['pandoc', '--print-default-data-file', 'reference.docx']))
d = Document(ref)
ST = {s.name: s for s in d.styles}
for s in d.styles:
    if s.type != 1: continue
    try: set_font(s)
    except Exception: pass
for name in ('Normal', 'Body Text', 'First Paragraph', 'Compact'):
    if name in [s.name for s in d.styles]:
        s = ST[name]; pf = s.paragraph_format
        pf.line_spacing = 1.5; pf.space_before = Pt(0); pf.space_after = Pt(0)
        pf.first_line_indent = Cm(1.25); pf.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
for i, sz in ((1, 16), (2, 14), (3, 14), (4, 14)):
    s = ST[f'Heading {i}']; set_font(s, sz, bold=True, italic=False)
    pf = s.paragraph_format; pf.line_spacing = 1.5; pf.space_before = Pt(12 if i > 1 else 0); pf.space_after = Pt(6)
    pf.first_line_indent = Cm(1.25) if i > 1 else Cm(0); pf.keep_with_next = True
    pf.alignment = WD_ALIGN_PARAGRAPH.CENTER if i == 1 else WD_ALIGN_PARAGRAPH.LEFT
    if i == 1: pf.page_break_before = True
for name in ('Image Caption', 'Table Caption', 'Caption'):
    if name in [s.name for s in d.styles]:
        s = ST[name]; set_font(s, 12 if name == 'Table Caption' else 12, italic=False)
        pf = s.paragraph_format; pf.first_line_indent = Cm(0); pf.space_after = Pt(10); pf.line_spacing = 1.0
        pf.alignment = WD_ALIGN_PARAGRAPH.CENTER if name != 'Table Caption' else WD_ALIGN_PARAGRAPH.LEFT
for name in ('Captioned Figure', 'Figure'):
    if name in [s.name for s in d.styles]:
        pf = ST[name].paragraph_format; pf.alignment = WD_ALIGN_PARAGRAPH.CENTER; pf.first_line_indent = Cm(0); pf.keep_with_next = True
if 'TOC Heading' in [s.name for s in d.styles]:
    s = ST['TOC Heading']; set_font(s, 16, bold=True); s.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
d.save(ref)

# 2. pandoc
subprocess.check_call(['pandoc', src, '-o', out, '--reference-doc', ref, '--toc', '--toc-depth=2'] + (['--number-sections'] if '--number' in sys.argv else []) + [
                       '-M', 'toc-title=Содержание', '--resource-path', res, '-f', 'markdown+implicit_figures'])

# 3. постобработка
d = Document(out)
sec = d.sections[0]
sec.page_width, sec.page_height = Mm(210), Mm(297)
sec.left_margin, sec.right_margin, sec.top_margin, sec.bottom_margin = Mm(30), Mm(15), Mm(20), Mm(20)
sec.different_first_page_header_footer = True
fp = sec.footer.paragraphs[0]; fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
fp.paragraph_format.first_line_indent = Cm(0)
def field(par, instr):
    r = par.add_run(); r.font.name = 'Times New Roman'; r.font.size = Pt(12)
    for kind, txt in (('begin', None), (None, instr), ('separate', None), (None, '1'), ('end', None)):
        if kind:
            e = OxmlElement('w:fldChar'); e.set(qn('w:fldCharType'), kind); r._r.append(e)
        elif txt == instr:
            e = OxmlElement('w:instrText'); e.set(qn('xml:space'), 'preserve'); e.text = instr; r._r.append(e)
        else:
            e = OxmlElement('w:t'); e.text = txt; r._r.append(e)
field(fp, 'PAGE')

# таблицы: сетка, шрифт 12, одинарный интервал, без отступа
for t in d.tables:
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    tblPr = t._tbl.tblPr
    for old in tblPr.findall(qn('w:tblBorders')): tblPr.remove(old)
    b = OxmlElement('w:tblBorders')
    for side in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
        e = OxmlElement(f'w:{side}'); e.set(qn('w:val'), 'single'); e.set(qn('w:sz'), '4'); e.set(qn('w:color'), '000000'); b.append(e)
    tblPr.append(b)
    w = OxmlElement('w:tblW'); w.set(qn('w:w'), '5000'); w.set(qn('w:type'), 'pct')
    for old in tblPr.findall(qn('w:tblW')): tblPr.remove(old)
    tblPr.append(w)
    for ri, row in enumerate(t.rows):
        for c in row.cells:
            for p in c.paragraphs:
                p.paragraph_format.first_line_indent = Cm(0); p.paragraph_format.line_spacing = 1.0
                p.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
                for r in p.runs:
                    r.font.size = Pt(12); r.font.name = 'Times New Roman'
                    if ri == 0: r.font.bold = True

# списки: убрать красную строку
for p in d.paragraphs:
    if p._p.pPr is not None and p._p.pPr.numPr is not None:
        p.paragraph_format.first_line_indent = Cm(0)

# титульный лист
T = json.load(open(title_json, encoding='utf-8'))
body = d.element.body
first = body[0]
def tp(text, size=14, bold=False, align=WD_ALIGN_PARAGRAPH.CENTER, before=0, after=0):
    p = d.add_paragraph(); p.alignment = align
    pf = p.paragraph_format; pf.first_line_indent = Cm(0); pf.space_before = Pt(before); pf.space_after = Pt(after); pf.line_spacing = 1.5
    r = p.add_run(text); r.font.size = Pt(size); r.bold = bold; r.font.name = 'Times New Roman'
    first.addprevious(p._p); return p
for line in T['top']: tp(line)
tp(T['doc_type'], 16, True, before=150)
for line in T['title']: tp(line, 16, True)
for line in T.get('subtitle', []): tp(line)
for i, line in enumerate(T['author']): tp(line, align=WD_ALIGN_PARAGRAPH.RIGHT, before=110 if i == 0 else 0)
p = tp(T['bottom'], before=130)
p.add_run().add_break(WD_BREAK.PAGE)
d.save(out)
print('built', out)
