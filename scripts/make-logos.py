"""Builds every logo and app icon from one source image (logo.jpg in the project root).

The source has a checkerboard "transparency" baked into its pixels, so the outside is cut away first.
Run: python scripts/make-logos.py            (writes into the project)
     python scripts/make-logos.py --dry DIR  (writes the same tree under DIR, for a look first)
"""
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

root = Path(__file__).resolve().parent.parent
out = Path(sys.argv[sys.argv.index('--dry') + 1]) if '--dry' in sys.argv else root

# 1. Cut the rounded square out of the checkerboard.
src = Image.open(root / 'logo.jpg').convert('RGB')
rgb = np.asarray(src)
light = Image.fromarray(((rgb.min(axis=2) > 120) * 255).astype('uint8')).copy()  # copy: a NumPy-backed image is read-only
w, h = src.size
for seed in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
    ImageDraw.floodfill(light, seed, 128, thresh=0)  # light pixels connected to the border = background
inside = Image.fromarray(((np.asarray(light) != 128) * 255).astype('uint8'))
inside = inside.filter(ImageFilter.MinFilter(5))  # shave the 2 px edge that still blends with the checkerboard
alpha = inside.filter(ImageFilter.GaussianBlur(1.1))
logo = src.convert('RGBA')
logo.putalpha(alpha)
logo = logo.crop(logo.getchannel('A').point(lambda v: 255 if v > 8 else 0).getbbox())

dark_pixels = np.asarray(logo)[..., :3][(np.asarray(logo)[..., 3] > 250) & (np.asarray(logo)[..., :3].max(axis=2) < 60)]
DARK = tuple(int(v) for v in np.median(dark_pixels, axis=0))  # the logo's own black, used as icon background

def square(img, size, margin=0.0):
    """The logo centred on a transparent square, `margin` of the side kept free around it."""
    side = round(size * (1 - 2 * margin))
    scaled = img.resize((side, round(side * img.height / img.width)), Image.LANCZOS)
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    canvas.paste(scaled, ((size - scaled.width) // 2, (size - scaled.height) // 2), scaled)
    return canvas

def opaque(img, size):
    """Full-bleed icon without alpha (App Store, Play Store, apple-touch): the logo's black fills the corners."""
    canvas = Image.new('RGB', (size, size), DARK)
    scaled = square(img, size)
    canvas.paste(scaled, (0, 0), scaled)
    return canvas

def save(img, rel):
    path = out / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)

master = square(logo, 1024, 0.01)
save(master.resize((512, 512), Image.LANCZOS), 'public/brand-logo.png')
save(square(logo, 256, 0.0), 'public/brand-mark.png')
save(square(logo, 64, 0.0), 'public/favicon-64.png')
save(square(logo, 192, 0.0), 'public/icon-192.png')
save(opaque(logo, 180), 'public/apple-touch-icon.png')
path = out / 'public/favicon.ico'
path.parent.mkdir(parents=True, exist_ok=True)
square(logo, 256, 0.0).save(path, sizes=[(16, 16), (32, 32), (48, 48), (64, 64)])
save(opaque(logo, 1024), 'ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-512@2x.png')
save(opaque(logo, 512), 'store/10-appikon-512.png')

# Android: legacy icon, round icon and the adaptive foreground. The adaptive mask can be a circle, so the
# foreground keeps the logo to about 58 % of the canvas (the farthest foot then stays inside the circle).
for dpi, legacy, foreground in [('mdpi', 48, 108), ('hdpi', 72, 162), ('xhdpi', 96, 216), ('xxhdpi', 144, 324), ('xxxhdpi', 192, 432)]:
    res = f'android/app/src/main/res/mipmap-{dpi}'
    save(square(logo, legacy, 0.02), f'{res}/ic_launcher.png')
    circle = Image.new('RGBA', (legacy * 4, legacy * 4), (0, 0, 0, 0))
    ImageDraw.Draw(circle).ellipse((0, 0, legacy * 4 - 1, legacy * 4 - 1), fill=DARK + (255,))
    big = square(logo, legacy * 4, 0.10)
    circle.alpha_composite(big)
    save(circle.resize((legacy, legacy), Image.LANCZOS), f'{res}/ic_launcher_round.png')
    save(square(logo, foreground, 0.21), f'{res}/ic_launcher_foreground.png')

print('dark', '#%02x%02x%02x' % DARK, 'logo', logo.size)
