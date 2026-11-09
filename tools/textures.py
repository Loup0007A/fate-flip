"""Génère les textures de Fate Flip (cartes, lueurs, runes, logo).
Tout est dessiné en 2x puis réduit (anticrénelage). Carte : 256x384 = 64x96 unités x4."""
import math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageChops

OUT = '/home/claude/fate-flip/src/main/resources/assets/fateflip/textures/gui/'
SS = 2                      # suréchantillonnage
U = 4 * SS                  # pixels par unité de carte (dessin)
CW, CH = 64 * U, 96 * U

CATS = {
    'space':   ((176, 124, 255), (91, 45, 179)),
    'vision':  ((79, 227, 240), (22, 128, 140)),
    'mind':    ((255, 209, 102), (176, 122, 18)),
    'control': ((255, 107, 122), (163, 25, 43)),
    'sound':   ((91, 227, 154), (27, 138, 80)),
}
GOLD = (233, 196, 106)
GOLD_D = (140, 98, 30)



class BlendDraw:
    """Comme ImageDraw.Draw, mais chaque forme est fusionnée (alpha) au lieu de remplacer les pixels."""
    def __init__(self, img):
        self.img = img

    def __getattr__(self, name):
        def call(*a, **k):
            layer = Image.new('RGBA', self.img.size, (0, 0, 0, 0))
            getattr(ImageDraw.Draw(layer), name)(*a, **k)
            self.img.alpha_composite(layer)
        return call


def u(v):
    return int(round(v * U))


def rr(d, box, r, **kw):
    d.rounded_rectangle([u(box[0]), u(box[1]), u(box[2]) - 1, u(box[3]) - 1], radius=u(r), **kw)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def vgrad(w, h, top, bottom):
    t = np.linspace(0, 1, h)[:, None, None]
    a = np.array(top, np.float32)[None, None, :]
    b = np.array(bottom, np.float32)[None, None, :]
    g = a + (b - a) * t
    return Image.fromarray(np.repeat(g, w, axis=1).astype(np.uint8), 'RGBA' if len(top) == 4 else 'RGB')


def radial(w, h, cx, cy, r, inner, outer, power=1.6):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.clip(np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / r, 0, 1) ** power
    a = np.array(inner, np.float32)
    b = np.array(outer, np.float32)
    g = a[None, None, :] + (b - a)[None, None, :] * d[..., None]
    return Image.fromarray(g.astype(np.uint8), 'RGBA')


def mask_rounded(w, h, box, r):
    m = Image.new('L', (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle([u(box[0]), u(box[1]), u(box[2]) - 1, u(box[3]) - 1], radius=u(r), fill=255)
    return m


def noise_img(w, h, scale, seed, amp=1.0):
    rng = np.random.default_rng(seed)
    small = rng.random((max(2, h // scale), max(2, w // scale))).astype(np.float32)
    im = Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return im


def finish(img, name, size=(256, 384)):
    img.resize(size, Image.LANCZOS).save(OUT + name)


def metal_frame(img, light, dark, seed):
    """Cadre métallique : dégradé diagonal + liseré clair + ornements d'angle."""
    w, h = img.size
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    t = (np.sin((x / w * 1.3 + y / h * 0.9) * math.pi * 2.0) * 0.5 + 0.5)
    l = np.array(light, np.float32); dk = np.array(dark, np.float32)
    col = dk[None, None, :] + (l - dk)[None, None, :] * (0.35 + 0.65 * t[..., None])
    frame = Image.fromarray(np.concatenate([col, np.full((h, w, 1), 255, np.float32)], -1).astype(np.uint8), 'RGBA')
    outer = mask_rounded(w, h, (0, 0, 64, 96), 4.5)
    inner = mask_rounded(w, h, (3.2, 3.2, 60.8, 92.8), 2.6)
    ring = ImageChops.subtract(outer, inner)
    img.paste(frame, (0, 0), ring)
    d = BlendDraw(img)
    rr(d, (0.4, 0.4, 63.6, 95.6), 4.3, outline=lerp(light, (255, 255, 255), 0.35) + (255,), width=u(0.45))
    rr(d, (2.6, 2.6, 61.4, 93.4), 3.0, outline=GOLD + (255,), width=u(0.45))
    rr(d, (3.2, 3.2, 60.8, 92.8), 2.6, outline=(20, 12, 24, 255), width=u(0.35))
    # ornements d'angle (petites volutes dorées)
    for (cx, cy, sx, sy) in [(5.5, 5.5, 1, 1), (58.5, 5.5, -1, 1), (5.5, 90.5, 1, -1), (58.5, 90.5, -1, -1)]:
        pts = []
        for i in range(24):
            a = i / 23 * math.pi * 1.5
            r = 0.4 + i / 23 * 2.2
            pts.append((u(cx + sx * math.cos(a) * r * 0.9), u(cy + sy * math.sin(a) * r * 0.9)))
        d.line(pts, fill=GOLD + (255,), width=u(0.35))
        d.ellipse([u(cx - 0.7), u(cy - 0.7), u(cx + 0.7), u(cy + 0.7)], fill=(255, 236, 170, 255))


def stars(d, box, n, seed, color=(255, 255, 255), alpha=(60, 200), size=(0.15, 0.45)):
    rnd = random.Random(seed)
    for _ in range(n):
        x = rnd.uniform(box[0], box[2]); y = rnd.uniform(box[1], box[3])
        s = rnd.uniform(*size); a = rnd.randint(*alpha)
        d.ellipse([u(x - s), u(y - s), u(x + s), u(y + s)], fill=color + (a,))


def front(cat):
    light, dark = CATS[cat]
    img = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    # fond intérieur : dégradé sombre teinté
    bg = vgrad(CW, CH, lerp(dark, (14, 10, 22), 0.72) + (255,), lerp(dark, (6, 4, 10), 0.85) + (255,))
    img.paste(bg, (0, 0), mask_rounded(CW, CH, (3.2, 3.2, 60.8, 92.8), 2.6))
    tex = noise_img(CW, CH, 6, hash(cat) & 0xffff)
    tint = Image.new('RGBA', (CW, CH), (255, 255, 255, 0))
    tint.putalpha(tex.point(lambda v: int(v * 0.06)))
    img.alpha_composite(tint)
    metal_frame(img, light, dark, 1)
    d = BlendDraw(img)
    # plaque du nom
    rr(d, (6, 5.4, 58, 15.6), 2.2, fill=(10, 7, 14, 235), outline=lerp(light, GOLD, 0.5) + (255,), width=u(0.4))
    d.line([(u(9), u(14.3)), (u(55), u(14.3))], fill=light + (70,), width=u(0.25))
    # fenêtre d'illustration
    aw = (6, 18, 58, 58)
    art = radial(CW, CH, u(32), u(37), u(30), lerp(light, (255, 255, 255), 0.15) + (255,), lerp(dark, (8, 5, 14), 0.8) + (255,), 1.15)
    rays = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rays)
    for i in range(18):
        a0 = i / 18 * math.tau; a1 = a0 + math.tau / 36
        p = [(u(32), u(37)), (u(32) + math.cos(a0) * u(60), u(37) + math.sin(a0) * u(60)), (u(32) + math.cos(a1) * u(60), u(37) + math.sin(a1) * u(60))]
        rd.polygon(p, fill=(255, 255, 255, 22))
    art.alpha_composite(rays)
    sd = BlendDraw(art)
    stars(sd, aw, 40, hash(cat) & 0xff, alpha=(40, 160))
    vign = radial(CW, CH, u(32), u(38), u(34), (0, 0, 0, 0), (0, 0, 0, 170), 2.2)
    art.alpha_composite(vign)
    img.paste(art, (0, 0), mask_rounded(CW, CH, aw, 2.4))
    rr(d, aw, 2.4, outline=lerp(light, (255, 255, 255), 0.3) + (230,), width=u(0.45))
    rr(d, (aw[0] + 0.8, aw[1] + 0.8, aw[2] - 0.8, aw[3] - 0.8), 1.8, outline=(0, 0, 0, 120), width=u(0.3))
    # ruban de catégorie (bouts entaillés)
    y0, y1 = 59.6, 66.4
    pts = [(10, y0), (54, y0), (56.5, (y0 + y1) / 2), (54, y1), (10, y1), (7.5, (y0 + y1) / 2)]
    d.polygon([(u(x), u(y)) for x, y in pts], fill=dark + (255,), outline=lerp(light, GOLD, 0.4) + (255,))
    d.line([(u(12), u(y0 + 0.9)), (u(52), u(y0 + 0.9))], fill=light + (110,), width=u(0.3))
    # cadre de description
    rr(d, (6, 68, 58, 87.6), 2.0, fill=(18, 13, 20, 225), outline=lerp(light, (255, 255, 255), 0.1) + (150,), width=u(0.35))
    # trois chatons pour les gemmes d'intensité
    for k in (-1, 0, 1):
        cx, cy = 32 + k * 7, 91.2
        d.ellipse([u(cx - 2.3), u(cy - 2.3), u(cx + 2.3), u(cy + 2.3)], fill=(12, 8, 14, 255), outline=GOLD + (255,), width=u(0.35))
    finish(img, f'card_front_{cat}.png')


def back():
    img = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    bg = radial(CW, CH, u(32), u(48), u(62), (72, 34, 128, 255), (14, 8, 30, 255), 1.1)
    img.paste(bg, (0, 0), mask_rounded(CW, CH, (0, 0, 64, 96), 4.5))
    # treillis de losanges
    lat = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    ld = ImageDraw.Draw(lat)
    step = 6
    for k in range(-20, 40):
        ld.line([(u(k * step), u(0)), (u(k * step + 96), u(96))], fill=(255, 215, 140, 26), width=u(0.3))
        ld.line([(u(k * step), u(0)), (u(k * step - 96), u(96))], fill=(255, 215, 140, 26), width=u(0.3))
    img.alpha_composite(lat)
    d = BlendDraw(img)
    stars(d, (4, 4, 60, 92), 70, 7, color=(255, 240, 210), alpha=(50, 200))
    # bordures dorées
    rr(d, (0.4, 0.4, 63.6, 95.6), 4.3, outline=(255, 226, 150, 255), width=u(0.6))
    rr(d, (2.4, 2.4, 61.6, 93.6), 3.2, outline=GOLD_D + (255,), width=u(0.9))
    rr(d, (4.2, 4.2, 59.8, 91.8), 2.4, outline=GOLD + (255,), width=u(0.35))
    # emblème : étoile à 8 branches, cercle, œil
    cx, cy = 32, 48
    glow = radial(CW, CH, u(cx), u(cy), u(22), (255, 200, 120, 120), (255, 200, 120, 0), 1.4)
    img.alpha_composite(glow)
    d = BlendDraw(img)
    for i in range(8):
        a = i / 8 * math.tau - math.pi / 2
        r1, r2 = 19 if i % 2 == 0 else 13, 4.5
        p = [(u(cx + math.cos(a) * r1), u(cy + math.sin(a) * r1)),
             (u(cx + math.cos(a + 0.33) * r2), u(cy + math.sin(a + 0.33) * r2)),
             (u(cx), u(cy)),
             (u(cx + math.cos(a - 0.33) * r2), u(cy + math.sin(a - 0.33) * r2))]
        d.polygon(p, fill=(233, 196, 106, 255) if i % 2 == 0 else (190, 140, 60, 255), outline=(255, 236, 170, 255))
    for r, w, c in [(11.5, 0.6, (255, 226, 150, 255)), (9.8, 0.35, GOLD_D + (255,))]:
        d.ellipse([u(cx - r), u(cy - r), u(cx + r), u(cy + r)], outline=c, width=u(w))
    # œil
    d.ellipse([u(cx - 7.5), u(cy - 4.2), u(cx + 7.5), u(cy + 4.2)], fill=(24, 12, 40, 255), outline=(255, 236, 170, 255), width=u(0.5))
    iris = radial(CW, CH, u(cx), u(cy), u(3.4), (140, 230, 255, 255), (40, 70, 160, 255), 1.0)
    m = Image.new('L', (CW, CH), 0)
    ImageDraw.Draw(m).ellipse([u(cx - 3.4), u(cy - 3.4), u(cx + 3.4), u(cy + 3.4)], fill=255)
    img.paste(iris, (0, 0), m)
    d = BlendDraw(img)
    d.ellipse([u(cx - 1.3), u(cy - 1.3), u(cx + 1.3), u(cy + 1.3)], fill=(8, 4, 16, 255))
    d.ellipse([u(cx - 2.2), u(cy - 2.4), u(cx - 1.2), u(cy - 1.4)], fill=(255, 255, 255, 230))
    # petites lunes haut / bas
    for (y, flip) in [(12, False), (84, True)]:
        d.ellipse([u(cx - 3), u(y - 3), u(cx + 3), u(y + 3)], fill=(233, 196, 106, 255))
        off = 1.6 if not flip else -1.6
        d.ellipse([u(cx - 3 + off), u(y - 3.3), u(cx + 3 + off), u(y + 2.7)], fill=(30, 16, 56, 255))
        for k in (-1, 1):
            d.ellipse([u(cx + k * 7 - 0.6), u(y - 0.6), u(cx + k * 7 + 0.6), u(y + 0.6)], fill=(255, 236, 170, 255))
    finish(img, 'card_back.png')


def burn():
    """Calque « carte brûlée » : bords carbonisés, trous et braises."""
    w, h = CW, CH
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    edge = np.minimum(np.minimum(x, w - x), np.minimum(y, h - y)) / U
    n = np.asarray(noise_img(w, h, 10, 11), np.float32) / 255
    n2 = np.asarray(noise_img(w, h, 3, 12), np.float32) / 255
    char = np.clip(1.25 - edge / 13.0 + (n - 0.5) * 0.9 + (n2 - 0.5) * 0.3, 0, 1)
    rgb = np.zeros((h, w, 3), np.float32)
    rgb[...] = np.array([18, 10, 8], np.float32)
    ember = np.clip(1 - np.abs(char - 0.62) * 9, 0, 1) * (n2 > 0.45)
    rgb += ember[..., None] * np.array([255, 120, 30], np.float32)
    alpha = np.clip(char * 1.4 - 0.15, 0, 1) * 235 + ember * 20
    hole = char > 0.93
    alpha[hole] = 0
    img = Image.fromarray(np.concatenate([np.clip(rgb, 0, 255), np.clip(alpha, 0, 255)[..., None]], -1).astype(np.uint8), 'RGBA')
    img.putalpha(ImageChops.multiply(img.getchannel('A'), mask_rounded(w, h, (0, 0, 64, 96), 4.5)))
    finish(img, 'card_burn.png')


def glow():
    g = radial(256, 256, 128, 128, 128, (255, 255, 255, 255), (255, 255, 255, 0), 0.9)
    g.resize((128, 128), Image.LANCZOS).save(OUT + 'glow.png')


def spark():
    s = 64
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    y, x = np.mgrid[0:s, 0:s].astype(np.float32) - s / 2 + 0.5
    r = np.sqrt(x * x + y * y) / (s / 2)
    core = np.clip(1 - r * 2.2, 0, 1) ** 1.5
    cross = (np.clip(1 - np.abs(x) / 1.6, 0, 1) * np.clip(1 - np.abs(y) / (s / 2), 0, 1) +
             np.clip(1 - np.abs(y) / 1.6, 0, 1) * np.clip(1 - np.abs(x) / (s / 2), 0, 1))
    a = np.clip(core + cross * 0.8 + np.clip(1 - r, 0, 1) ** 3 * 0.35, 0, 1)
    arr = np.zeros((s, s, 4), np.uint8)
    arr[..., :3] = 255
    arr[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(arr, 'RGBA').resize((32, 32), Image.LANCZOS).save(OUT + 'spark.png')


def gem():
    s = 64
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = BlendDraw(img)
    c = s / 2
    d.polygon([(c, 4), (s - 6, c), (c, s - 4), (6, c)], fill=(235, 235, 235, 255))
    d.polygon([(c, 4), (s - 6, c), (c, c)], fill=(255, 255, 255, 255))
    d.polygon([(6, c), (c, s - 4), (c, c)], fill=(170, 170, 170, 255))
    d.polygon([(c, 14), (c + 10, c), (c, c + 10), (c - 10, c)], fill=(250, 250, 250, 255))
    d.ellipse([c - 14, c - 16, c - 6, c - 8], fill=(255, 255, 255, 255))
    img.resize((16, 16), Image.LANCZOS).save(OUT + 'gem.png')


def runes():
    s = 1024
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = BlendDraw(img)
    c = s / 2
    W = (255, 255, 255)
    for r, w, a in [(500, 3, 200), (470, 2, 140), (360, 3, 190), (340, 1, 120), (190, 2, 160)]:
        d.ellipse([c - r, c - r, c + r, c + r], outline=W + (a,), width=w)
    rnd = random.Random(5)
    # anneau de « runes » : petits glyphes géométriques
    for i in range(48):
        a = i / 48 * math.tau
        x, y = c + math.cos(a) * 415, c + math.sin(a) * 415
        g = rnd.randint(0, 4)
        sz = 16
        ca, sa = math.cos(a + math.pi / 2), math.sin(a + math.pi / 2)
        def P(px, py):
            return (x + px * ca - py * sa, y + px * sa + py * ca)
        if g == 0:
            d.line([P(-sz, -sz), P(0, sz), P(sz, -sz)], fill=W + (190,), width=3)
        elif g == 1:
            d.line([P(0, -sz), P(0, sz)], fill=W + (190,), width=3); d.line([P(-sz, -4), P(sz, 4)], fill=W + (190,), width=3)
        elif g == 2:
            d.ellipse([x - 9, y - 9, x + 9, y + 9], outline=W + (190,), width=3); d.line([P(0, -sz), P(0, sz)], fill=W + (190,), width=3)
        elif g == 3:
            d.polygon([P(0, -sz), P(sz, sz), P(-sz, sz)], outline=W + (190,), width=3)
        else:
            d.line([P(-sz, 0), P(sz, 0)], fill=W + (190,), width=3); d.line([P(-sz * 0.6, -sz), P(sz * 0.6, sz)], fill=W + (190,), width=3)
    # étoile inscrite
    pts = [(c + math.cos(i / 7 * math.tau * 3 - math.pi / 2) * 340, c + math.sin(i / 7 * math.tau * 3 - math.pi / 2) * 340) for i in range(8)]
    d.line(pts, fill=W + (150,), width=2)
    for i in range(72):
        a = i / 72 * math.tau
        r0, r1 = (470, 500) if i % 6 == 0 else (482, 500)
        d.line([(c + math.cos(a) * r0, c + math.sin(a) * r0), (c + math.cos(a) * r1, c + math.sin(a) * r1)], fill=W + (170,), width=2)
    img.resize((512, 512), Image.LANCZOS).save(OUT + 'rune_circle.png')





def shard():
    """Éclat du destin : cristal à facettes (V2)."""
    s = 256
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = BlendDraw(img)
    c = s / 2
    top, left, right, bot = (c, 18), (c - 70, c - 10), (c + 70, c - 10), (c, s - 18)
    midl, midr = (c - 28, c + 8), (c + 28, c + 8)
    d.polygon([top, left, midl], fill=(176, 140, 255, 255))
    d.polygon([top, midl, (c, c - 4)], fill=(222, 200, 255, 255))
    d.polygon([top, (c, c - 4), midr], fill=(196, 166, 255, 255))
    d.polygon([top, midr, right], fill=(130, 96, 230, 255))
    d.polygon([left, midl, bot], fill=(110, 76, 210, 255))
    d.polygon([midl, (c, c - 4), midr, bot], fill=(160, 124, 245, 255))
    d.polygon([midr, right, bot], fill=(84, 54, 180, 255))
    d.line([top, left, bot, right, top], fill=(255, 245, 255, 255), width=5)
    d.line([left, midl, (c, c - 4), midr, right], fill=(255, 255, 255, 140), width=3)
    d.line([midl, bot], fill=(255, 255, 255, 90), width=3)
    d.line([midr, bot], fill=(255, 255, 255, 90), width=3)
    d.ellipse([c - 30, 58, c - 14, 74], fill=(255, 255, 255, 230))
    img.resize((32, 32), Image.LANCZOS).save(OUT + 'shard.png')


def medallion():
    """Médaillon d'atout : anneau doré gravé, disque sombre (V2)."""
    s = 512
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    c = s / 2
    disc = radial(s, s, c, c * 0.9, c * 0.95, (64, 34, 110, 255), (12, 6, 26, 255), 1.2)
    m = Image.new('L', (s, s), 0)
    ImageDraw.Draw(m).ellipse([24, 24, s - 24, s - 24], fill=255)
    img.paste(disc, (0, 0), m)
    d = BlendDraw(img)
    d.ellipse([10, 10, s - 10, s - 10], outline=(140, 98, 30, 255), width=26)
    d.ellipse([16, 16, s - 16, s - 16], outline=(233, 196, 106, 255), width=12)
    d.ellipse([40, 40, s - 40, s - 40], outline=(255, 226, 150, 200), width=5)
    for i in range(24):
        a = i / 24 * math.tau
        r0, r1 = c - 34, c - 20
        d.line([(c + math.cos(a) * r0, c + math.sin(a) * r0), (c + math.cos(a) * r1, c + math.sin(a) * r1)],
               fill=(90, 60, 20, 255), width=5)
    d.ellipse([60, 60, s - 60, s - 60], outline=(0, 0, 0, 120), width=8)
    gl = radial(s, s, c * 0.75, c * 0.6, c * 0.5, (255, 255, 255, 40), (255, 255, 255, 0), 1.5)
    img.alpha_composite(gl)
    img.resize((128, 128), Image.LANCZOS).save(OUT + 'medallion.png')


if __name__ == '__main__':
    for cat in CATS:
        front(cat)
    back(); burn(); glow(); spark(); gem(); runes(); shard(); medallion()
    print('textures ok')
