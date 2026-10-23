# Usage: python tools/jelly/pack_waves.py   (after fronts.ps1; needs Pillow)
# Packs each run jelly.pl planned (fronts.txt's `run` lines) into one animated picture for the face: each frame its
# wave as fronts.ps1 drew it (frames/), where it sits, if it has one, and over it the run's bulges (one or two), their pictures mapped onto
# the face as the frame's `bulge` lines say and faded, as the face's own groups would place them; on a canvas the size
# of all of them, one frame each 1/fps s from the run's start (any before it shows stay empty), played once. So the
# bulges keep to the wave on one clock. jelly.pl puts each in a PartAnimatedImage that plays as its run's second
# comes. Writes each picture's box on the face to waves.txt, after the hash of the fronts_frames.txt and the bulge
# pictures packed from.
import hashlib, math, os, re
from PIL import Image

here = os.path.dirname(os.path.abspath(__file__))
face = os.path.join(here, '..', '..', 'watchfaces', 'src', 'expedition', 'res', 'drawable-nodpi')
drawn = open(os.path.join(here, 'fronts_frames.txt'), 'rb').read()
boxes = {}
for line in drawn.decode('ascii').splitlines()[1:]:
    f = line.split()
    boxes[f[0]] = tuple(int(v) for v in f[1:5])
runs = []
k_of = lambda name: int(re.search(r'_f(\d+)$', name).group(1))
for line in open(os.path.join(here, 'fronts.txt'), encoding='ascii'):
    f = line.split()
    if f[0] == 'run':
        runs.append({'prefix': f[1], 'fps': int(f[4]), 'frames': {}, 'bulges': {}})
    elif f[0] == 'front':
        runs[-1]['frames'][k_of(f[1])] = f[1]
    elif f[0] == 'bulge':   # frame picture a b c d e f alpha: the picture's (x, y) lands at (a x + b y + c, d x + e y + f)
        runs[-1]['bulges'].setdefault(k_of(f[1]), []).append((f[2], [float(v) for v in f[3:9]], float(f[9])))
pics = sorted({b[0] for r in runs for bs in r['bulges'].values() for b in bs})
art = {p: open(os.path.join(face, p + '.png'), 'rb').read() for p in pics}
bulge = {p: Image.open(os.path.join(face, p + '.png')).convert('RGBA') for p in pics}

def place(pic, m, alpha, x0, y0, w, h):
    """The bulge picture mapped onto a w x h canvas whose corner is (x0, y0) on the face: Pillow wants the map back,
    from the canvas to the picture; resampled premultiplied (as the watch does), so its edges don't darken."""
    a, b, c, d, e, f = m; det = a * e - b * d; u, v = x0 - c, y0 - f
    back = (e / det, -b / det, (e * u - b * v) / det, -d / det, a / det, (a * v - d * u) / det)
    out = bulge[pic].convert('RGBa').transform((w, h), Image.Transform.AFFINE, back, resample=Image.Resampling.BILINEAR).convert('RGBA')
    out.putalpha(out.getchannel('A').point(lambda q: round(q * alpha)))
    return out

for old in os.listdir(face):
    if old.startswith('exp_wave_') and old.endswith('.webp'):
        os.remove(os.path.join(face, old))
out = ['sha256 ' + hashlib.sha256(drawn + b''.join(art[p] for p in pics)).hexdigest()]
total = 0
for r in runs:
    name = r['prefix'].replace('exp_front_', 'exp_wave_', 1)
    shown = sorted(r['bulges'])
    # every frame shown draws its bulges (one or two, the same all through), and its wave only where one was drawn
    assert shown == list(range(shown[0], shown[-1] + 1)) and len({len(r['bulges'][k]) for k in shown} - {1, 2}) == 0 and len({len(r['bulges'][k]) for k in shown}) == 1, name
    assert set(r['frames']) <= set(shown), name
    xs, ys = [], []
    for n in r['frames'].values():
        x, y, w, h = boxes[n]; xs += [x, x + w]; ys += [y, y + h]
    for bs in r['bulges'].values():
        for pic, (a, b, c, d, e, f), al in bs:
            pw, ph = bulge[pic].size
            for x, y in ((0, 0), (pw, 0), (0, ph), (pw, ph)):
                xs.append(a * x + b * y + c); ys.append(d * x + e * y + f)
    x0, y0 = math.floor(min(xs)), math.floor(min(ys))
    w, h = math.ceil(max(xs)) - x0, math.ceil(max(ys)) - y0
    frames = []
    for k in range(shown[-1] + 1):
        c = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        if k in r['frames']:
            n = r['frames'][k]
            c.alpha_composite(Image.open(os.path.join(here, 'frames', n + '.png')).convert('RGBA'), (boxes[n][0] - x0, boxes[n][1] - y0))
        for pic, m, al in r['bulges'].get(k, []):   # over the wave, as the face drew its bulges over its waves
            c.alpha_composite(place(pic, m, al, x0, y0, w, h))
        frames.append(c)
    # each frame's time, in whole milliseconds that add up to the run's own (1/16 s comes to 62 and 63 by turns)
    ms = [round((k + 1) * 1000 / r['fps']) - round(k * 1000 / r['fps']) for k in range(len(frames))]
    path = os.path.join(face, name + '.webp')
    frames[0].save(path, format='WEBP', save_all=True, append_images=frames[1:], duration=ms, loop=1, lossless=True, method=6)
    # (the encoder merges frames that are the same, the empty ones at a run's start, into one shown as long: the
    # run's time and size must still come out as they went in)
    data, at, played = open(path, 'rb').read(), 12, 0
    while at < len(data):   # each frame's ANMF chunk holds its duration, 24 bits from 12 bytes in
        tag, size = data[at:at + 4], int.from_bytes(data[at + 4:at + 8], 'little')
        if tag == b'ANMF':
            played += int.from_bytes(data[at + 20:at + 23], 'little')
        at += 8 + size + (size & 1)
    assert Image.open(path).size == (w, h) and played == sum(ms), (name, played, sum(ms))
    total += os.path.getsize(path)
    out.append('%s %d %d %d %d %d %d' % (name, x0, y0, w, h, len(frames), r['fps']))
open(os.path.join(here, 'waves.txt'), 'w', encoding='ascii', newline='\n').write('\n'.join(out) + '\n')
print('%d animated pictures, %d bytes' % (len(runs), total))
