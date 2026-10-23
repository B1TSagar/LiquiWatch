# Usage: python tools/ring/refract.py   (needs Pillow and numpy)
# The glass bending what's behind it. Each piece's rim pulls in what lies beyond its edge, most at the very edge and
# none a little way in, so its middle stays clear (nothing is magnified); the domed window and the moon's lens squeeze
# their pictures toward the rim instead, as a domed crystal does (their middles a touch bigger, as a dome can't help).
#  - the backdrops (exp_bg_*): each as seen through the glass that sits on it, the dials, the date pill, the hour
#    beads and the ring's tube, bent once here, so it costs the watch nothing;
#  - the album art, which the watch gets live: ring.pl lays a copy of it in each dial's, the pill's and each bead's
#    box, shrunk about the glass's middle so its edge shows what's that far beyond it, and cut to the glass's rim by
#    a picture of it (exp_refr_*, blend mode DST_IN), fading out a little way in: on the art's soft blur that looks as
#    the bent backdrops do. Where each copy goes is written to refract.txt for ring.pl;
#  - the window's sky (exp_dn_*) and the moon (moon_*), from their originals in refract_src.
import os, re
import numpy as np
from PIL import Image

here = os.path.dirname(os.path.abspath(__file__))
exp = os.path.join(here, '..', '..', 'watchfaces', 'src', 'expedition', 'res', 'drawable-nodpi')
main = os.path.join(here, '..', '..', 'watchfaces', 'src', 'main', 'res', 'drawable-nodpi')
src = os.path.join(here, 'refract_src')

def load(p):   # premultiplied, 0..1
    a = np.asarray(Image.open(p).convert('RGBA')).astype(np.float64) / 255
    a[..., :3] *= a[..., 3:4]; return a
def save(a, p):
    al = a[..., 3:4]; c = np.where(al > 0, a[..., :3] / np.maximum(al, 1e-9), 0)
    im = Image.fromarray((np.clip(np.concatenate([c, al], -1), 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA')
    (im.convert('RGB') if al.min() > 0.999 else im).save(p, optimize=True)   # (an opaque backdrop as the originals are)
def sample(img, qx, qy):   # bilinear at pixel coordinates (centres at .5), clamped to the picture
    h, w = img.shape[:2]; qx = np.clip(qx - 0.5, 0, w - 1.001); qy = np.clip(qy - 0.5, 0, h - 1.001)
    x0 = qx.astype(int); y0 = qy.astype(int); fx = (qx - x0)[..., None]; fy = (qy - y0)[..., None]
    return (img[y0, x0] * (1 - fx) * (1 - fy) + img[y0, x0 + 1] * fx * (1 - fy) + img[y0 + 1, x0] * (1 - fx) * fy + img[y0 + 1, x0 + 1] * fx * fy)
def grid(h, w, ss):   # the points a picture's pixels are worked out at, ss x ss to a pixel
    y, x = (np.mgrid[0:h * ss, 0:w * ss] + 0.5) / ss; return x, y
def down(a, h, w, ss): return a.reshape(h, ss, w, ss, -1).mean(axis=(1, 3))

# the shapes: how far in from the edge a point is (px) and the edge's outward direction there
def circle(cx, cy, r):
    def f(x, y):
        dx, dy = x - cx, y - cy; q = np.maximum(np.hypot(dx, dy), 1e-9); return r - q, dx / q, dy / q
    return f
def stadium(x0, x1, cy, r):   # caps round (x0, cy) and (x1, cy)
    def f(x, y):
        dx, dy = x - np.clip(x, x0, x1), y - cy; q = np.maximum(np.hypot(dx, dy), 1e-9); return r - q, dx / q, dy / q
    return f
def tube(cx, cy, ri, ro):   # the ring's tube: its inner wall bends inward, its outer outward
    def f(x, y):
        dx, dy = x - cx, y - cy; q = np.maximum(np.hypot(dx, dy), 1e-9); inner = q - ri < ro - q
        s = np.where(inner, -1, 1); return np.minimum(q - ri, ro - q), s * dx / q, s * dy / q
    return f

# Each piece: its shape, how far in its rim bends (w) and how far beyond its edge the very edge shows (A), px.
# The bend falls off as (1 - d / w)^2, d how far in. The beads, spheres, bend all the way in.
xml = open(os.path.join(here, 'expedition_source.xml'), encoding='utf-8').read()
BEADS = [(int(x) + 12.5, int(y) + 12.5) for x, y in re.findall(r'<PartImage x="(\d+)" y="(\d+)" width="25" height="25"><Variant mode="AMBIENT" target="alpha" value="\d+" /><Image resource="exp_bead" />', xml)]
assert len(BEADS) == 11, f'{len(BEADS)} hour beads in expedition_source.xml, not 11'
DIAL = (43.5, 16, 30); PILL = (25.5, 12, 22); BEAD = (7.6, 7.6, 8); TUBE = (10, 10)
GLASS = ([(circle(cx, cy, DIAL[0]), DIAL[1], DIAL[2]) for cx, cy in ((125, 225), (325, 225), (225, 325))]
         + [(stadium(178, 272, 135, PILL[0]), PILL[1], PILL[2])]
         + [(circle(cx, cy, BEAD[0]), BEAD[1], BEAD[2]) for cx, cy in BEADS]
         + [(tube(225, 225, 204, 224), TUBE[0], TUBE[1])])   # (ring_glass.ps1's tube walls)

def bent(img, ss=2):
    """img as seen through all the glass: inside a piece, from the point its rim's bend takes it to"""
    h, w = img.shape[:2]; x, y = grid(h, w, ss); qx, qy, on = x.copy(), y.copy(), np.zeros_like(x)
    for shape, wd, A in GLASS:
        d, nx, ny = shape(x, y); t = np.clip(1 - d / wd, 0, 1) ** 2 * (d > 0)
        qx += nx * A * t; qy += ny * A * t; on = np.maximum(on, (t > 0) * 1.0)
    on = down(on[..., None], h, w, ss)   # (where nothing bends, the backdrop's own pixels, untouched)
    return img * (1 - on) + down(sample(img, qx, qy), h, w, ss) * on

def rim_mask(w, h, shape, wd, ss=4):
    """the album copy's cut: all of it at the glass's edge, fading out wd px in, nothing outside the glass"""
    x, y = grid(h, w, ss); d, _, _ = shape(x, y)
    a = down((np.clip(1 - d / wd, 0, 1) ** 2 * (d > 0))[..., None], h, w, ss)[..., 0]
    return np.dstack([a, a, a, a])   # white, premultiplied

def domed(img, cx, cy, m=0.92, k=6, ss=4):
    """squeezed toward the rim of its disc (round cx, cy, up to its top): radius u R shows what's at
    (m u + (1 - m) u^k) R, so the rim stays put, the middle is 1/m as big and the outer part squeezed; outside it
    the picture is left as it is"""
    h, w = img.shape[:2]; R = cy - np.argmax((img[..., 3] > 0.5).any(axis=1))   # (its top: the dome's highest point)
    x, y = grid(h, w, ss); dx, dy = x - cx, y - cy; L = np.hypot(dx, dy); u = L / R
    f = np.where(L < R, m + (1 - m) * np.clip(u, 0, 1) ** (k - 1), 1)   # (m u + (1 - m) u^k) / u
    out = down(sample(img, cx + dx * f, cy + dy * f), h, w, ss)
    inside = down((L < R)[..., None] * 1.0, h, w, ss)
    return img * (1 - inside) + out * inside

if __name__ == '__main__':
    # the backdrops
    for n in ('gold', 'ocean', 'ember', 'violet', 'forest'):
        a = load(os.path.join(main, f'bg_{n}.png')); b = bent(a)
        x, y = grid(450, 450, 1); far = np.ones((450, 450), bool)
        for shape, wd, A in GLASS: far &= shape(x, y)[0] < -1.5
        assert np.abs(b - a)[far].max() < 1e-6, f'bg_{n}: changed outside the glass'
        save(b, os.path.join(exp, f'exp_bg_{n}.png'))
    # the album art's cuts and where its copies go: (mask, the glass's box on the face, the copy's place in it)
    lines = []
    def copy(mask, gx, gy, gw, gh, cx, cy, sx, sy):
        W, H = round(450 * sx), round(450 * sy); X, Y = round(cx * (1 - W / 450)) - gx, round(cy * (1 - H / 450)) - gy
        lines.append(f'copy {mask} {gx} {gy} {gw} {gh} {X} {Y} {W} {H}')
        return W / 450, H / 450
    r, wd, A = DIAL
    save(rim_mask(88, 88, circle(44, 44, r), wd), os.path.join(exp, 'exp_refr_dial.png'))
    for cx, cy in ((125, 225), (325, 225), (225, 325)):
        s, _ = copy('exp_refr_dial', cx - 44, cy - 44, 88, 88, cx, cy, r / (r + A), r / (r + A))
        assert abs(r / s - r - A) < 1, f'the dial copy bends {r / s - r:.1f} px, not {A}'
    r, wd, A = PILL
    save(rim_mask(146, 52, stadium(26, 120, 26, r), wd), os.path.join(exp, 'exp_refr_pill.png'))
    sx, sy = copy('exp_refr_pill', 152, 109, 146, 52, 225, 135, 72.5 / (72.5 + A), r / (r + A))   # (its ends and its sides alike)
    assert abs(72.5 / sx - 72.5 - A) < 1 and abs(r / sy - r - A) < 1, 'the pill copy bends wrongly'
    r, wd, A = BEAD
    save(rim_mask(17, 17, circle(8.5, 8.5, r), wd), os.path.join(exp, 'exp_refr_bead.png'))
    for cx, cy in BEADS:
        s, _ = copy('exp_refr_bead', int(cx - 8.5), int(cy - 8.5), 17, 17, cx, cy, r / (r + A), r / (r + A))
        assert abs(r / s - r - A) < 1, f'the bead copy bends {r / s - r:.1f} px, not {A}'
    open(os.path.join(here, 'refract.txt'), 'w', newline='\n').write('\n'.join(lines) + '\n')
    # the window's sky and the moon, under their domes
    for f in sorted(os.listdir(src)):
        a = load(os.path.join(src, f))
        if f.startswith('exp_dn_'): b = domed(a, 34, 33.5)   # the window's dome: round the middle of its flat base
        elif f.startswith('moon_'): b = domed(a, a.shape[1] / 2, a.shape[0] / 2)
        else: raise SystemExit(f'what is {f}?')
        save(b, os.path.join(exp, f))
    print(f'backdrops 5, album copies {len(lines)}, domes {len(os.listdir(src))}')
