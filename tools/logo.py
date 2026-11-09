"""Logo du mod (liste des mods + avatar CurseForge) : un éventail de trois cartes."""
import math
from PIL import Image, ImageDraw, ImageFilter
import numpy as np

G = '/home/claude/fate-flip/src/main/resources/assets/fateflip/textures/gui/'
S = 1024
img = Image.new('RGBA', (S, S), (0, 0, 0, 0))

# fond : disque nuit étoilée
y, x = np.mgrid[0:S, 0:S].astype(np.float32)
r = np.sqrt((x - S / 2) ** 2 + (y - S / 2) ** 2) / (S / 2)
bg = np.zeros((S, S, 4), np.float32)
c0 = np.array([86, 40, 150]); c1 = np.array([12, 6, 28])
t = np.clip(r, 0, 1)[..., None]
bg[..., :3] = c0 * (1 - t) + c1 * t
bg[..., 3] = np.clip((1 - r) * 40, 0, 1) * 255
img = Image.fromarray(bg.astype(np.uint8), 'RGBA')
d = ImageDraw.Draw(img)
rng = np.random.default_rng(4)
for _ in range(140):
    px, py = rng.uniform(80, S - 80, 2)
    if math.hypot(px - S / 2, py - S / 2) > S * 0.46:
        continue
    s = rng.uniform(1.5, 4.5)
    a = int(rng.uniform(80, 230))
    d.ellipse([px - s, py - s, px + s, py + s], fill=(255, 240, 210, a))
ring = Image.new('RGBA', (S, S), (0, 0, 0, 0))
ImageDraw.Draw(ring).ellipse([22, 22, S - 22, S - 22], outline=(233, 196, 106, 255), width=14)
ImageDraw.Draw(ring).ellipse([46, 46, S - 46, S - 46], outline=(140, 98, 30, 255), width=5)
img.alpha_composite(ring)

cards = [('card_front_vision.png', 20, -150), ('card_front_space.png', -20, 150), ('card_back.png', 0, 0)]
for name, angle, dx in cards:
    c = Image.open(G + name).convert('RGBA').resize((340, 510), Image.LANCZOS)
    pad = Image.new('RGBA', (700, 700), (0, 0, 0, 0))
    sh = Image.new('RGBA', (340, 510), (0, 0, 0, 160))
    sh.putalpha(c.getchannel('A').point(lambda v: int(v * 0.6)))
    pad.alpha_composite(sh, (190, 115))
    pad = pad.filter(ImageFilter.GaussianBlur(16))
    pad.alpha_composite(c, (180, 95))
    rot = pad.rotate(angle, resample=Image.BICUBIC)
    img.alpha_composite(rot, (int(S / 2 - 350 + dx), int(S / 2 - 350 + abs(dx) * 0.12)))

# halo doré autour de l'œil central
glow = Image.new('RGBA', (S, S), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse([S / 2 - 120, S / 2 - 120, S / 2 + 120, S / 2 + 120], fill=(255, 210, 120, 70))
img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(40)))
img.resize((512, 512), Image.LANCZOS).save('/home/claude/fate-flip/src/main/resources/fateflip_logo.png')
img.resize((400, 400), Image.LANCZOS).save('/tmp/fateflip_avatar_400.png')
print('logo ok')
