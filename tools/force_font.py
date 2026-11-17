"""Весь текст документа: Times New Roman 14 пт, чёрный (стили, умолчания, все прогоны, включая таблицы и оглавление).
usage: python3 force_font.py file.docx"""
import sys
from docx import Document
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

def fix_rpr(rpr):
    for tag in ('w:rFonts', 'w:sz', 'w:szCs', 'w:color', 'w:highlight', 'w:shd'):
        for e in rpr.findall(qn(tag)):
            rpr.remove(e)
    f = OxmlElement('w:rFonts')
    for a in ('w:ascii', 'w:hAnsi', 'w:cs', 'w:eastAsia'):
        f.set(qn(a), 'Times New Roman')
    rpr.insert(0, f)
    c = OxmlElement('w:color'); c.set(qn('w:val'), '000000'); rpr.append(c)
    for tag in ('w:sz', 'w:szCs'):
        e = OxmlElement(tag); e.set(qn('w:val'), '28'); rpr.append(e)

def get_rpr(parent):
    r = parent.find(qn('w:rPr'))
    if r is None:
        r = OxmlElement('w:rPr'); parent.insert(0, r)
    return r

d = Document(sys.argv[1])
st = d.styles.element
dd = st.find(qn('w:docDefaults'))
if dd is not None:
    rd = dd.find(qn('w:rPrDefault'))
    if rd is not None:
        fix_rpr(get_rpr(rd) if rd.find(qn('w:rPr')) is not None else rd.append(OxmlElement('w:rPr')) or rd.find(qn('w:rPr')))
for s in st.findall(qn('w:style')):
    if s.get(qn('w:type')) in ('paragraph', 'character', 'table'):
        rp = s.find(qn('w:rPr'))
        if rp is None:
            rp = OxmlElement('w:rPr'); s.append(rp)
        fix_rpr(rp)
parts = [d.element] + [s.header._element for s in d.sections] + [s.footer._element for s in d.sections] \
        + [s.first_page_header._element for s in d.sections] + [s.first_page_footer._element for s in d.sections]
n = 0
for root in parts:
    for r in root.iter(qn('w:r')):
        fix_rpr(get_rpr(r)); n += 1
    for ppr in root.iter(qn('w:pPr')):
        rp = ppr.find(qn('w:rPr'))
        if rp is not None:
            fix_rpr(rp)
d.save(sys.argv[1])
print('runs fixed:', n)
