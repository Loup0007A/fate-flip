"""Rend chaque mode du shader screen_fx sur la scène de test (aperçu hors jeu)."""
import sys, numpy as np, moderngl
from PIL import Image, ImageDraw

ROOT = '/home/claude/fate-flip/src/main/resources/assets/fateflip/'
VS = open(ROOT + 'shaders/core/screen_fx.vsh').read()
FS = open(ROOT + 'shaders/core/screen_fx.fsh').read()
W, H = 1280, 720

ctx = moderngl.create_standalone_context(backend='egl')
prog = ctx.program(vertex_shader=VS, fragment_shader=FS)
scene = Image.open('/home/claude/tools/scene.png').convert('RGBA').transpose(Image.FLIP_TOP_BOTTOM)
depth = np.asarray(Image.open('/home/claude/tools/scene_depth.png'), dtype=np.float32) / 65535.0
depth = np.flipud(depth).copy()
t_col = ctx.texture((W, H), 4, scene.tobytes()); t_col.filter = (moderngl.LINEAR, moderngl.LINEAR)
t_col.repeat_x = t_col.repeat_y = False
t_dep = ctx.texture((W, H), 1, depth.astype('f4').tobytes(), dtype='f4'); t_dep.filter = (moderngl.NEAREST, moderngl.NEAREST)
t_hand = ctx.texture((1, 1), 1, np.array([1.0], 'f4').tobytes(), dtype='f4')
gl = Image.open(ROOT + 'textures/misc/ascii_glyphs.png').convert('RGBA').transpose(Image.FLIP_TOP_BOTTOM)
t_aux = ctx.texture(gl.size, 4, gl.tobytes()); t_aux.filter = (moderngl.NEAREST, moderngl.NEAREST)

quad = np.array([0,0,0, 0,0,  1,0,0, 1,0,  1,1,0, 1,1,  0,0,0, 0,0,  1,1,0, 1,1,  0,1,0, 0,1], 'f4')
vbo = ctx.buffer(quad)
vao = ctx.vertex_array(prog, [(vbo, '3f 2f', 'Position', 'UV0')])
fbo = ctx.simple_framebuffer((W, H))

def setu(name, val):
    if name in prog:
        prog[name].value = val

def render(mode, t=3.0, params=(0, 0, 0, 0), params2=(0, 0, 0, 0), extra=None, intensity=1.0):
    fbo.use(); fbo.clear(0, 0, 0, 1)
    t_col.use(0); t_dep.use(1); t_hand.use(2); t_aux.use(3)
    setu('DiffuseSampler', 0); setu('DepthSampler', 1); setu('HandDepthSampler', 2); setu('AuxSampler', 3)
    setu('Mode', mode); setu('Time', t); setu('Intensity', intensity); setu('Calm', 0.0)
    setu('OutSize', (W, H)); setu('Params', tuple(params)); setu('Params2', tuple(params2))
    setu('Extra', tuple(extra) if extra is not None else tuple([0.0] * 16)); setu('DepthRange', (0.05, 512.0))
    vao.render(moderngl.TRIANGLES)
    data = fbo.read(components=3)
    return Image.frombytes('RGB', (W, H), data).transpose(Image.FLIP_TOP_BOTTOM)

taq = [0, 1, 2, 3, 4, 5, 6, 7, 8]
taq = [4, 0, 2, 8, 7, 1, 3, 6, 5]
SPECS = {
    1: dict(params=(2 / 3, 0.0, 1 / 3, 0), params2=(0, 0, 0, 0)),
    4: dict(params=(5, 8, 3, 0.4), extra=[float(x) for x in taq] + [0.0] * 7),
    5: dict(params=(6, 0, 0, 0)),
    6: dict(params=(0.45, 0, 0, 0)),
    8: dict(params=(0.42, 0.56, 0.6, 0)),
    12: dict(params2=(0.56, 0.48, 0.3, 0.7)),
}
NAMES = {1: 'triptych', 2: 'mirror', 3: 'antipodes', 4: 'taquin', 5: 'kaleido', 6: 'vertigo', 7: 'portrait', 8: 'wandering',
         9: 'melt', 10: 'whirl', 11: 'fisheye', 12: 'shatter', 13: 'noir', 14: 'scarlet', 15: 'negative', 16: 'thermal',
         17: 'eightbit', 18: 'pocket', 19: 'vhs', 20: 'crt', 21: 'anaglyph', 22: 'tipsy', 23: 'tunnel', 24: 'sketch',
         25: 'source', 26: 'psyche', 27: 'myopia', 28: 'echo', 29: 'wetlens', 30: 'comic', 31: 'cinema', 32: 'goggles', 33: 'swap'}

if __name__ == '__main__':
    modes = [int(a) for a in sys.argv[1:]] or list(NAMES)
    thumbs = []
    for m in modes:
        im = render(m, **SPECS.get(m, {}))
        im.save(f'/tmp/fx_{m:02d}_{NAMES[m]}.png')
        th = im.resize((480, 270))
        ImageDraw.Draw(th).text((6, 4), f'{m} {NAMES[m]}', fill=(255, 255, 0))
        thumbs.append(th)
    cols = 3
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * 480, rows * 270))
    for i, th in enumerate(thumbs):
        sheet.paste(th, ((i % cols) * 480, (i // cols) * 270))
    sheet.save('/tmp/fx_sheet.png')
    print('rendered', len(thumbs))
