"""Génère une scène « façon Minecraft » (couleur + profondeur) pour tester les shaders hors jeu.
Ray-casting DDA vectorisé sur une petite grille de voxels texturés."""
import numpy as np
from PIL import Image

T = '/home/claude/mc/assets/assets/minecraft/textures/'
W, H = 960, 540
rng = np.random.default_rng(3)

def tex(name, tint=None):
    im = np.asarray(Image.open(T + 'block/' + name + '.png').convert('RGBA'), dtype=np.float32)[:16, :16] / 255
    if tint is not None:
        im[..., :3] *= np.array(tint, dtype=np.float32)
    return im

GRASS_TOP = tex('grass_block_top', (0.55, 0.78, 0.35))
GRASS_SIDE = tex('grass_block_side')
DIRT = tex('dirt'); STONE = tex('stone'); LOG = tex('oak_log'); LOG_TOP = tex('oak_log_top')
LEAVES = tex('oak_leaves', (0.45, 0.72, 0.3)); SAND = tex('sand'); WATER = tex('water_still', (0.25, 0.45, 0.9))
PLANKS = tex('oak_planks'); GLASS = tex('glass'); FLOWER = tex('poppy')

SX, SY, SZ = 72, 28, 72
world = np.zeros((SX, SY, SZ), np.int8)  # 0 air 1 grass 2 dirt 3 stone 4 log 5 leaves 6 sand 7 water 8 planks
xs, zs = np.meshgrid(np.arange(SX), np.arange(SZ), indexing='ij')
h = (9 + 3 * np.sin(xs / 9.0) + 2.5 * np.cos(zs / 7.0) + 2 * np.sin((xs + zs) / 13.0)).astype(int)
for x in range(SX):
    for z in range(SZ):
        hh = h[x, z]
        world[x, :hh - 3, z] = 3
        world[x, hh - 3:hh, z] = 2
        world[x, hh, z] = 1 if hh > 8 else 6
        if hh < 8:
            world[x, hh + 1:9, z] = 7
for (tx, tz) in [(30, 22), (38, 30), (25, 34), (44, 18), (50, 38), (20, 26)]:
    base = h[tx, tz] + 1
    for y in range(base, base + 5):
        world[tx, y, tz] = 4
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(3, 6):
                if abs(dx) + abs(dz) + (dy - 3) < 5 and world[tx + dx, base + dy, tz + dz] == 0:
                    world[tx + dx, base + dy, tz + dz] = 5
# petite cabane
hx, hz = 34, 40
hb = h[hx, hz] + 1
for dx in range(5):
    for dz in range(5):
        for dy in range(4):
            if dx in (0, 4) or dz in (0, 4) or dy == 3:
                world[hx + dx, hb + dy, hz + dz] = 8

cam = np.array([22.5, 17.5, 6.5])
yaw, pitch = np.radians(28), np.radians(-14)
fov = np.radians(70)
fy = np.tan(fov / 2); fx = fy * W / H
u = (np.arange(W) + 0.5) / W * 2 - 1
v = 1 - (np.arange(H) + 0.5) / H * 2
U, V = np.meshgrid(u * fx, v * fy)
d = np.stack([U, V, np.ones_like(U)], -1)
cp, sp = np.cos(pitch), np.sin(pitch)
d = np.stack([d[..., 0], d[..., 1] * cp + d[..., 2] * sp, -d[..., 1] * sp + d[..., 2] * cp], -1)
cy, sy = np.cos(yaw), np.sin(yaw)
d = np.stack([d[..., 0] * cy + d[..., 2] * sy, d[..., 1], -d[..., 0] * sy + d[..., 2] * cy], -1)
d /= np.linalg.norm(d, axis=-1, keepdims=True)
d = d.reshape(-1, 3)
N = d.shape[0]
pos = np.floor(cam).astype(int) * np.ones((N, 3), int)
step = np.sign(d).astype(int)
inv = 1 / np.where(d == 0, 1e-9, d)
tmax = ((pos + (step > 0)) - cam) * inv
tdelta = np.abs(inv)
hit = np.zeros(N, np.int8); face = np.zeros(N, int); dist = np.full(N, 200.0)
alive = np.ones(N, bool)
last_axis = np.zeros(N, int)
for _ in range(220):
    idx = np.nonzero(alive)[0]
    if idx.size == 0:
        break
    tm = tmax[idx]
    ax = np.argmin(tm, axis=1)
    t = tm[np.arange(idx.size), ax]
    pos[idx, ax] += step[idx, ax]
    tmax[idx, ax] += tdelta[idx, ax]
    p = pos[idx]
    inside = (p[:, 0] >= 0) & (p[:, 0] < SX) & (p[:, 1] >= 0) & (p[:, 1] < SY) & (p[:, 2] >= 0) & (p[:, 2] < SZ)
    out = ~inside & ((p[:, 1] >= SY) | (p[:, 0] < -1) | (p[:, 0] > SX) | (p[:, 2] < -1) | (p[:, 2] > SZ) | (p[:, 1] < 0))
    alive[idx[out]] = False
    b = np.zeros(idx.size, np.int8)
    b[inside] = world[p[inside, 0], p[inside, 1], p[inside, 2]]
    got = b > 0
    hit[idx[got]] = b[got]; face[idx[got]] = ax[got]; dist[idx[got]] = t[got]
    alive[idx[got]] = False

hp = cam + d * dist[:, None]
img = np.zeros((N, 3), np.float32)
sky_t = np.clip(d[:, 1] * 2 + 0.3, 0, 1)[:, None]
sky = (1 - sky_t) * np.array([0.72, 0.84, 1.0]) + sky_t * np.array([0.42, 0.62, 0.98])
img[:] = sky
for bid in range(1, 9):
    m = hit == bid
    if not m.any():
        continue
    f = face[m]; q = hp[m]
    uu = np.where(f == 0, q[:, 2], q[:, 0]) % 1
    vv = np.where(f == 1, q[:, 2], 1 - (q[:, 1] % 1)) % 1
    ti = np.clip((uu * 16).astype(int), 0, 15); tj = np.clip((vv * 16).astype(int), 0, 15)
    top = f == 1
    if bid == 1:
        col = np.where(top[:, None], GRASS_TOP[tj, ti, :3], GRASS_SIDE[tj, ti, :3])
    elif bid == 2: col = DIRT[tj, ti, :3]
    elif bid == 3: col = STONE[tj, ti, :3]
    elif bid == 4: col = np.where(top[:, None], LOG_TOP[tj, ti, :3], LOG[tj, ti, :3])
    elif bid == 5: col = LEAVES[tj, ti, :3]
    elif bid == 6: col = SAND[tj, ti, :3]
    elif bid == 7: col = WATER[tj, ti, :3]
    else: col = PLANKS[tj, ti, :3]
    shade = np.where(f == 1, 1.0, np.where(f == 0, 0.8, 0.65))[:, None]
    img[m] = col * shade
fogk = np.clip((dist - 40) / 60, 0, 1)[:, None]
img = img * (1 - fogk) + sky * fogk
img = img.reshape(H, W, 3)
depth = np.clip(dist, 0.05, 200).reshape(H, W)
near, far = 0.05, 512.0
ndc = (far + near) / (far - near) - 2 * far * near / ((far - near) * depth)
depth01 = np.where(hit.reshape(H, W) > 0, ndc * 0.5 + 0.5, 1.0)

out = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).resize((1280, 720), Image.NEAREST)
# HUD : barre d'action, cœurs, viseur
G = T + 'gui/sprites/hud/'
hot = Image.open(G + 'hotbar.png').convert('RGBA'); sel = Image.open(G + 'hotbar_selection.png').convert('RGBA')
S = 3
hot = hot.resize((hot.width * S, hot.height * S), Image.NEAREST)
sel = sel.resize((sel.width * S, sel.height * S), Image.NEAREST)
hx0 = 640 - hot.width // 2; hy0 = 720 - hot.height
out.paste(hot, (hx0, hy0), hot)
items = ['diamond_sword', 'diamond_pickaxe', 'bread', 'torch', 'apple', 'bow', 'arrow', 'iron_ingot', 'oak_planks']
for i, it in enumerate(items):
    p = T + 'item/' + it + '.png'
    try:
        im = Image.open(p).convert('RGBA')
    except FileNotFoundError:
        im = Image.open(T + 'block/' + it + '.png').convert('RGBA')
    im = im.resize((16 * S, 16 * S), Image.NEAREST)
    out.paste(im, (hx0 + (3 + i * 20) * S, hy0 + 3 * S), im)
out.paste(sel, (hx0 - S + 2 * 20 * S, hy0 - S), sel)
heart = Image.open(G + 'heart/full.png').convert('RGBA').resize((27, 27), Image.NEAREST)
food = Image.open(G + 'food_full.png').convert('RGBA').resize((27, 27), Image.NEAREST)
for i in range(10):
    out.paste(heart, (hx0 + i * 24, hy0 - 10 * S - 18), heart)
    out.paste(food, (hx0 + hot.width - 27 - i * 24, hy0 - 10 * S - 18), food)
ch = Image.open(G + 'crosshair.png').convert('RGBA').resize((45, 45), Image.NEAREST)
out.paste(ch, (640 - 22, 360 - 22), ch)
out.save('/home/claude/tools/scene.png')
Image.fromarray((np.clip(depth01, 0, 1) * 65535).astype(np.uint16)).resize((1280, 720), Image.NEAREST).save('/home/claude/tools/scene_depth.png')
print('ok', hit.astype(bool).mean())
