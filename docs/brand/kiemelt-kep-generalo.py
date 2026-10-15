# A Google Play kiemelt kép (1024x500) generálója. Futtatás: python kiemelt-kep-generalo.py 588 792 regelo-kiemelt-1024x500.png
from PIL import Image, ImageDraw, ImageFont
import sys
S = 3  # supersample
W, H = 1024, 500
import os
F = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'app', 'src', 'main', 'res', 'font') + os.sep
INK = (0x2B, 0x3A, 0x55); RUST = (0xC9, 0x7C, 0x3D); CREAM = (0xF3, 0xEB, 0xDD)
def mix(a, b, t): return tuple(round(a[i]*(1-t) + b[i]*t) for i in range(3))

img = Image.new('RGB', (W*S, H*S), INK)
d = ImageDraw.Draw(img)
STEP, Y0 = 46, 48
rules = [Y0 + k*STEP for k in range(10)]
for y in rules:
    d.rectangle([0, y*S, W*S, y*S + S - 1], fill=mix(INK, CREAM, 0.07))
MX = 72  # notebook margin line
d.rectangle([MX*S, 0, MX*S + 2*S - 1, H*S], fill=mix(INK, RUST, 0.55))

def text(x, baseline, s, font, fill):
    d.text((x*S, baseline*S), s, font=font, fill=fill, anchor='ls')

word = ImageFont.truetype(F + 'lora_semibold.ttf', 112*S)
tag = ImageFont.truetype(F + 'manrope_medium.ttf', 30*S)
big = ImageFont.truetype(F + 'lora_italic.ttf', int(sys.argv[1] if len(sys.argv) > 1 else 600)*S)

X = 104
text(X, rules[4], 'Regélő', word, CREAM)
text(X + 4, rules[6], 'Írj, publikálj és olvass', tag, mix(INK, CREAM, 0.84))
text(X + 4, rules[7], 'magyar történeteket.', tag, mix(INK, CREAM, 0.84))

# Giant italic R standing on the bottom rule, centred in the right half.
bx = d.textbbox((0, 0), 'R', font=big, anchor='ls')
rw = bx[2] - bx[0]
cx = int(sys.argv[2]) if len(sys.argv) > 2 else 790
d.text((cx*S - rw//2 - bx[0], rules[9]*S), 'R', font=big, fill=RUST, anchor='ls')
print('R bbox (1x):', [(cx*S - rw//2 + (v - bx[0]) if i % 2 == 0 else rules[9]*S + v)/S for i, v in enumerate(bx)])

out = img.resize((W, H), Image.LANCZOS)
out.save(sys.argv[3] if len(sys.argv) > 3 else 'feature.png', optimize=True)
