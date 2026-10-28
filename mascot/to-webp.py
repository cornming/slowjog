"""把 render.js 輸出的 PNG 轉成 App 用的 WebP，放進 app/src/main/res/drawable-nodpi/。

用法：python3 to-webp.py [輸入資料夾，預設 mascot/out]
- 像素風（pixel）用無損壓縮，保留一格一格的邊緣。
- 其他風格用有損壓縮（品質 88），透明背景保留，檔案約為 PNG 的三分之一。
"""
import glob
import os
import sys

from PIL import Image

here = os.path.dirname(os.path.abspath(__file__))
src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, 'out')
dst = os.path.join(here, '..', 'app', 'src', 'main', 'res', 'drawable-nodpi')
for path in sorted(glob.glob(os.path.join(src, 'mascot_*.png'))):
    name = os.path.splitext(os.path.basename(path))[0]
    im = Image.open(path).convert('RGBA')
    out = os.path.join(dst, name + '.webp')
    if '_pixel_' in name:
        im.save(out, 'WEBP', lossless=True, method=6)
    else:
        im.save(out, 'WEBP', quality=88, method=6, alpha_quality=100)
    print(name, os.path.getsize(out) // 1024, 'KB')
