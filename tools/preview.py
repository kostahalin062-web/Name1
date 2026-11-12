"""usage: preview.py file.pdf out.png pages(comma list) — монтаж страниц для проверки"""
import sys, subprocess, tempfile, glob
from PIL import Image
pdf, out, pages = sys.argv[1], sys.argv[2], [int(x) for x in sys.argv[3].split(',')]
d = tempfile.mkdtemp(); ims = []
for n in pages:
    subprocess.check_call(['pdftoppm', '-r', '40', '-png', '-f', str(n), '-l', str(n), pdf, f'{d}/p{n:03d}'])
    ims.append(Image.open(glob.glob(f'{d}/p{n:03d}*.png')[0]))
w, h = ims[0].size; cols = 4; rows = (len(ims) + cols - 1) // cols
m = Image.new('RGB', (w * cols, h * rows), 'white')
for i, im in enumerate(ims): m.paste(im, ((i % cols) * w, (i // cols) * h))
m.save(out)
