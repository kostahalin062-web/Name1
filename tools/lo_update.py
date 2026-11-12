"""Обновляет оглавление и поля в .docx через LibreOffice (UNO), сохраняет docx и pdf.
usage: python3 lo_update.py file.docx"""
import subprocess, sys, time, os, uno
from com.sun.star.beans import PropertyValue
path = os.path.abspath(sys.argv[1])
proc = subprocess.Popen(['soffice', '--headless', '--norestore', '--accept=socket,host=localhost,port=2002;urp;'],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
ctx = None
for _ in range(60):
    try:
        local = uno.getComponentContext()
        resolver = local.ServiceManager.createInstanceWithContext('com.sun.star.bridge.UnoUrlResolver', local)
        ctx = resolver.resolve('uno:socket,host=localhost,port=2002;urp;StarOffice.ComponentContext'); break
    except Exception: time.sleep(0.5)
desktop = ctx.ServiceManager.createInstanceWithContext('com.sun.star.frame.Desktop', ctx)
def pv(n, v): p = PropertyValue(); p.Name = n; p.Value = v; return p
doc = desktop.loadComponentFromURL(uno.systemPathToFileUrl(path), '_blank', 0, (pv('Hidden', True),))
idx = doc.getDocumentIndexes()
for i in range(idx.getCount()): idx.getByIndex(i).update()
doc.getTextFields().refresh()
doc.storeToURL(uno.systemPathToFileUrl(path), (pv('FilterName', 'MS Word 2007 XML'),))
pdf = path[:-5] + '.pdf'
doc.storeToURL(uno.systemPathToFileUrl(pdf), (pv('FilterName', 'writer_pdf_Export'),))
pages = doc.getCurrentController().getPropertyValue('PageCount')
doc.close(True)
try: desktop.terminate()
except Exception: pass
proc.wait(timeout=20)
print('pages:', pages, 'pdf:', pdf)
