# Usage: python tools/ring/tint.py   (needs Pillow)
# The glass's accent tint, for ring.pl to lay under each panel's glass (the dials, the date pill, the day and night
# window) and each hour bead's, tinted their shade of the accent: white pictures whose alpha holds the tint. It's
# deepest at the glass's edge on the side the light comes from (the top left), less round the far side, and fades
# into clear glass smoothly a good way in, so the colour shows through as a hint. Each picture is the size of its
# glass's own pictures (exp_dial_body, ...), the bead's as its 25 px.
import math, os
from PIL import Image

res = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'watchfaces', 'src', 'expedition', 'res', 'drawable-nodpi')
LX, LY = -math.sqrt(0.5), -math.sqrt(0.5)   # toward the light: the top left

def tint(w, h, inside, peak, reach, low):
    """inside(x, y) -> (how far in from the edge, px; the edge's outward direction there); peak: the tint at the edge on
    the lit side; reach: how far in it fades by e (smoothly on, a hint further in); low: the far side's share of it"""
    im = Image.new('RGBA', (w, h), (255, 255, 255, 0)); px = im.load()
    for j in range(h):
        for i in range(w):
            d, (nx, ny) = inside(i + 0.5, j + 0.5)
            if d <= 0: continue
            lit = max(0.0, (1 + nx * LX + ny * LY) / 2)   # 1 facing the light, 0 facing away
            a = peak * (low + (1 - low) * lit ** 1.5) * min(1, d / 1.2) * math.exp(-(d / reach) ** 1.2)
            px[i, j] = (255, 255, 255, round(255 * a))
    return im

def circle(cx, cy, r):
    def f(x, y):
        dx, dy = x - cx, y - cy; q = math.hypot(dx, dy) or 1e-9; return r - q, (dx / q, dy / q)
    return f
def stadium(x0, x1, cy, r):   # caps round (x0, cy) and (x1, cy)
    def f(x, y):
        nx = min(max(x, x0), x1); dx, dy = x - nx, y - cy; q = math.hypot(dx, dy) or 1e-9; return r - q, (dx / q, dy / q)
    return f
def dome(cx, cy, r, base):   # the upper part of a circle, cut by a flat base at y = base
    def f(x, y):
        dx, dy = x - cx, y - cy; q = math.hypot(dx, dy) or 1e-9; dr = r - q; db = base - y
        return (dr, (dx / q, dy / q)) if dr < db else (db, (0, 1))
    return f

# the shapes as the bands of smoke they replace reached (their outer edges), in each glass's own pictures
out = {'exp_tint_dial': tint(104, 104, circle(52, 52, 43.8), 1.0, 11, 0.45),
       'exp_tint_pill': tint(162, 68, stadium(34, 128, 34, 25.75), 1.0, 9.5, 0.45),
       'exp_tint_window': tint(78, 45, dome(39, 38.5, 32.75, 38.75), 1.0, 9.5, 0.45),
       'exp_tint_bead': tint(25, 25, circle(12.5, 12.5, 7.6), 1.0, 3.2, 0.45)}
for n, im in out.items(): im.save(os.path.join(res, n + '.png'))
print(' '.join('%s %dx%d' % (n, im.width, im.height) for n, im in out.items()))
