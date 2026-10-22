# The Expedition face's glass, drawn as Cover Display draws its glass (the shader in its Glass.kt: a slab of
# glass with a rounded rim, lit from the top left): the minute ring's tube round the edge (its progress tube), the
# song's progress knob along it (its slider knob) and the marks (capsules standing across the ring: one for each
# minute, from wall to wall of the tube, and a longer one for each hour, from its outer wall in past its inner
# one); and the panels (the dials, the date pill, the day and night window and its moon's lens), in the same
# glass. The face can't look through glass as it draws, so each picture holds what the glass does to
# what's behind it whatever that is, as one see-through overlay: its milky tint, a gentle gradient through the
# body (lighter toward the top left), the light on its rim (a soft glow inside the edge and a hair-thin highlight,
# brightest top left and bottom right, the rim darker where it turns away, a faint dark outline, a touch of
# rainbow along the bottom edges), and the soft shadow under it that lifts it off the face. What's behind is
# softened by the face itself (the song's line is drawn soft). The marks are drawn where they sit; the knob at
# 12 o'clock, and the face turns it into place, so it's drawn lit as it will face then.
# Usage: powershell -File tools\ring\ring_glass.ps1   (writes the pictures into the face's drawable-nodpi folder,
# and their frames into frames.txt for ring.pl, with each mark's radius, kind, half length and half width)
$ErrorActionPreference = 'Stop'
$dir = Join-Path $PSScriptRoot '..\..\watchfaces\src\expedition\res\drawable-nodpi'
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System; using System.Drawing; using System.Drawing.Imaging;
public static class RingGlass {
    public const double CX = 225, CY = 225, DP = 2.0;   // the face's centre; the watch's px per dp
    // distance from (x, y) to the arc at radius r from angle c - a to c + a (radians, clockwise from 12)
    static double Spine(double x, double y, double c, double a, double r) {
        double dx = x - CX, dy = CY - y, rho = Math.Sqrt(dx * dx + dy * dy), rel = Math.Atan2(dx, dy) - c;
        while (rel > Math.PI) rel -= 2 * Math.PI; while (rel < -Math.PI) rel += 2 * Math.PI;
        if (Math.Abs(rel) <= a) return Math.Abs(rho - r);
        double e = c + Math.Sign(rel) * a, ex = CX + r * Math.Sin(e), ey = CY - r * Math.Cos(e);
        return Math.Sqrt((x - ex) * (x - ex) + (y - ey) * (y - ey)); }
    // a knob along the ring at radius r: at angle deg, half its whole length (round ends and all) and
    // half its thickness across (px)
    static double Knob(double x, double y, double deg, double halfLen, double halfThick, double r) { return Spine(x, y, deg * Math.PI / 180, Math.Max(halfLen - halfThick, 0) / r, r) - halfThick; }
    // a mark: a knob standing across the ring instead, its length out from the centre, its middle at radius r
    static double Mark(double x, double y, double deg, double halfLen, double halfThick, double r) {
        double c = deg * Math.PI / 180, ux = Math.Sin(c), uy = -Math.Cos(c), s = Math.Max(halfLen - halfThick, 0);
        double t = (x - (CX + r * ux)) * ux + (y - (CY + r * uy)) * uy; t = t < -s ? -s : t > s ? s : t;
        double px = CX + (r + t) * ux, py = CY + (r + t) * uy;
        return Math.Sqrt((x - px) * (x - px) + (y - py) * (y - py)) - halfThick; }
    // a piece of glass: the tube (kind 0, a whole ring: radius R, HalfThick each side), a mark (1) or the knob (2);
    // or, in a panel's own picture, a disc (3: radius R round X1, Y1), a pill (4: radius R round X1..X2 along Y1)
    // or a dome (5: a disc of radius R round X1, Y1, cut flat at Y2, its corners rounded by K)
    public class Piece { public int Kind; public double Deg, HalfLen, HalfThick, R, X1, Y1, X2, Y2, K; }
    static double Hyp(double x, double y) { return Math.Sqrt(x * x + y * y); }
    static double Field(Piece g, double x, double y) {
        switch (g.Kind) {
            case 0: return Math.Abs(Hyp(x - CX, y - CY) - g.R) - g.HalfThick;
            case 1: return Mark(x, y, g.Deg, g.HalfLen, g.HalfThick, g.R);
            case 2: return Knob(x, y, g.Deg, g.HalfLen, g.HalfThick, g.R);
            case 3: return Hyp(x - g.X1, y - g.Y1) - g.R;
            case 4: return Hyp(x - Math.Max(g.X1, Math.Min(g.X2, x)), y - g.Y1) - g.R;
            default: { double a = Hyp(x - g.X1, y - g.Y1) - g.R, b = y - g.Y2, h = Math.Max(g.K - Math.Abs(a - b), 0) / g.K; return Math.Max(a, b) + h * h * g.K / 4; } } }
    // how it looks, as Cover Display's GlassLook has it (px): how milky (white), its rim's width, and its soft
    // shadow: how strong (Cover Display's 0.25), how far it drops and how far it reaches in and out; and how
    // much more than Cover Display's its rim's light, its shading where the rim turns away, its dark outline
    // and the gradient through its body (lighter toward the top left, darker toward the bottom right: a dome);
    // and an inner shadow along its dark side (the edge facing the bottom right on the screen): how dark, how
    // far in it reaches and how soft it is (px)
    public class Look { public double Tint, Rim, Shadow, Drop, In, Out, Light = 1, Shade = 1, Edge = 1, Dome = 1, Inner, InnerReach, InnerSoft = 1; }
    static double Sat(double v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    static double Smooth(double e0, double e1, double x) { double t = Sat((x - e0) / (e1 - e0)); return t * t * (3 - 2 * t); }
    static double Frac(double v) { return v - Math.Floor(v); }
    static void Rot(double x, double y, double deg, out double rx, out double ry) {   // clockwise on the screen
        double a = deg * Math.PI / 180, c = Math.Cos(a), s = Math.Sin(a); rx = x * c - y * s; ry = x * s + y * c; }
    // the overlay so far (alpha a, colour premultiplied): what's behind, blended toward colour c by t
    static void Blend(ref double a, ref double r, ref double g, ref double b, double cr, double cg, double cb, double t) {
        t = Sat(t); r = r * (1 - t) + cr * t; g = g * (1 - t) + cg * t; b = b * (1 - t) + cb * t; a = 1 - (1 - a) * (1 - t); }
    // the middle and half size a piece's body gradient and rainbow run across, at (x, y)
    static void Middle(Piece g, double x, double y, out double zx, out double zy, out double hx, out double hy) {
        switch (g.Kind) {
            case 0: { double dx = x - CX, dy = y - CY, rho = Math.Max(Hyp(dx, dy), 1e-6); zx = CX + dx / rho * g.R; zy = CY + dy / rho * g.R; hx = hy = g.HalfThick; return; }
            case 1: { double c = g.Deg * Math.PI / 180, sn = Math.Abs(Math.Sin(c)), cs = Math.Abs(Math.Cos(c));
                zx = CX + g.R * Math.Sin(c); zy = CY - g.R * Math.Cos(c); hx = sn * g.HalfLen + cs * g.HalfThick; hy = cs * g.HalfLen + sn * g.HalfThick; return; }
            case 2: zx = CX; zy = CY - g.R; hx = g.HalfLen; hy = g.HalfThick; return;
            case 3: zx = g.X1; zy = g.Y1; hx = hy = g.R; return;
            case 4: zx = (g.X1 + g.X2) / 2; zy = g.Y1; hx = (g.X2 - g.X1) / 2 + g.R; hy = g.R; return;
            default: zx = g.X1; zy = (g.Y1 - g.R + g.Y2) / 2; hx = g.R; hy = (g.Y2 - (g.Y1 - g.R)) / 2; return; } }
    // A piece's overlay on the pixels (x0, y0, w, h), as it is once the face turns it by `turn` degrees: all of
    // it (part 0), or (for a panel, whose rim the hands cut and whose shadow is a picture of its own) its body,
    // its milky tint and dome (1), its rim, the light and shade over that (2), or its shadow (3).
    public static Bitmap Render(int x0, int y0, int w, int h, Piece g, Look k, double turn, int part) {
        var bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb); bool body = part < 2, rim = part == 0 || part == 2, cast = part == 0 || part == 3;
        double sdx, sdy, ldx, ldy, ux, uy, ix, iy; Rot(0, k.Drop, -turn, out sdx, out sdy); Rot(-0.40, -0.92, -turn, out ldx, out ldy); Rot(1, 0, -turn, out ux, out uy);
        Rot(0.6 * k.InnerReach, 0.8 * k.InnerReach, -turn, out ix, out iy);   // toward the dark side, away from the light
        double mzx, mzy, mhx, mhy; Middle(g, CX, 0, out mzx, out mzy, out mhx, out mhy);
        double cap = g.Kind == 0 ? g.HalfThick : g.Kind <= 2 ? Math.Min(g.HalfLen, g.HalfThick) : Math.Min(mhx, mhy);
        double rimW = Math.Max(Math.Min(k.Rim, cap * 0.9), 1), bandW = Math.Max(rimW * 0.6, 2 * DP);
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            double x = x0 + i + 0.5, y = y0 + j + 0.5, d = Field(g, x, y);
            double s = cast ? 1 - Smooth(-k.In, k.Out, Field(g, x - sdx, y - sdy)) : 0, shadow = k.Shadow * 0.22 * s * s;
            double cover = Smooth(1, -1, d), A = 0, R = 0, G = 0, B = 0;
            if (cover > 0 && part != 3) {
                double nx = Field(g, x + 0.5, y) - Field(g, x - 0.5, y), ny = Field(g, x, y + 0.5) - Field(g, x, y - 0.5), nl = Math.Sqrt(nx * nx + ny * ny);
                if (nl > 1e-9) { nx /= nl; ny /= nl; }
                double sx, sy; Rot(nx, ny, turn, out sx, out sy);   // which way the rim faces on the screen
                double depth = -d, xr = Sat(depth / rimW), f = 1 - xr * xr * (3 - 2 * xr);
                double zx, zy, hx, hy; Middle(g, x, y, out zx, out zy, out hx, out hy);
                double gr = Math.Max(-1, Math.Min(1, (x - zx) / hx)) * ldx + Math.Max(-1, Math.Min(1, (y - zy) / hy)) * ldy;
                if (body) { Blend(ref A, ref R, ref G, ref B, 1, 1, 1, k.Tint);
                    Blend(ref A, ref R, ref G, ref B, 1, 1, 1, k.Dome * (0.03 + 0.025 * gr));
                    if (gr < 0) Blend(ref A, ref R, ref G, ref B, 0, 0, 0, k.Dome * 0.025 * -gr); }
                if (rim) {
                double facing = -0.6 * sx - 0.8 * sy, toward = Math.Max(facing, 0), away = Math.Max(-facing, 0);
                Blend(ref A, ref R, ref G, ref B, 0, 0, 0, k.Shade * 0.10 * f * away);
                // the inner shadow: where a step toward the dark side leaves the glass, the edge there shades it
                // (under the rim's light, so the hair-thin highlight along the bottom right still shows)
                Blend(ref A, ref R, ref G, ref B, 0, 0, 0, k.Inner * Smooth(-k.InnerSoft, k.InnerSoft, Field(g, x + ix, y + iy)));
                double band = 1 - Sat(depth / bandW); band *= band;
                double hair = Math.Exp(-Math.Pow((depth - 0.9 * DP) / (0.45 * DP), 2));
                Blend(ref A, ref R, ref G, ref B, 1, 1, 1, k.Light * (0.12 * band * (0.25 + 0.75 * toward * toward) + hair * (0.05 + 0.55 * toward * toward + 0.25 * away * away)));
                double u = ((x - zx) * ux + (y - zy) * uy) / Math.Max(hx, 1), rb = hair * Math.Pow(Math.Max(sy, 0), 6) * Math.Exp(-Math.Pow((u + 0.15) / 0.28, 2)) * 0.20;
                if (rb > 0.002) { double h0 = 0.12 + u * 1.2;
                    Blend(ref A, ref R, ref G, ref B, Sat(Math.Abs(Frac(h0) * 6 - 3) - 1), Sat(Math.Abs(Frac(h0 + 0.6667) * 6 - 3) - 1), Sat(Math.Abs(Frac(h0 + 0.3333) * 6 - 3) - 1), rb); }
                Blend(ref A, ref R, ref G, ref B, 0, 0, 0, k.Edge * 0.20 * (1 - Smooth(0, 0.8 * DP, depth))); } }
            // the shadow where the glass isn't, the glass where it is (its edge antialiased)
            double a = 1 - ((1 - cover) * (1 - shadow) + cover * (1 - A)); int ia = (int)Math.Round(255 * Sat(a));
            if (ia == 0) { bmp.SetPixel(i, j, Color.FromArgb(0, 0, 0, 0)); continue; }
            bmp.SetPixel(i, j, Color.FromArgb(ia, (int)Math.Round(255 * Sat(cover * R / a)), (int)Math.Round(255 * Sat(cover * G / a)), (int)Math.Round(255 * Sat(cover * B / a)))); }
        return bmp; }
    // A hand's soft shadow from its glass picture: where its glass is (its alpha, from faint up), softened by a
    // box blur of `blur` px three times over, dark to `strength`.
    public static Bitmap Shadow(Bitmap src, double strength, int blur) {
        int w = src.Width, h = src.Height; var a = new double[w, h];
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) a[i, j] = Smooth(0.02, 0.15, src.GetPixel(i, j).A / 255.0);
        for (int pass = 0; pass < 3; pass++) { var t = new double[w, h];
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) { double s = 0; int n = 0; for (int k = -blur; k <= blur; k++) { int x = i + k; if (x >= 0 && x < w) s += a[x, j]; n++; } t[i, j] = s / n; }
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) { double s = 0; int n = 0; for (int k = -blur; k <= blur; k++) { int y = j + k; if (y >= 0 && y < h) s += t[i, y]; n++; } a[i, j] = s / n; } }
        var bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) bmp.SetPixel(i, j, Color.FromArgb((int)Math.Round(255 * Sat(strength * a[i, j])), 0, 0, 0));
        return bmp; }
    // A hand's glass picture with its light deepened as the ring's is: above the milky body (12%, as milky as the
    // ring's tube already) the glow and the hair-thin highlight 1.6 times as strong, the dark outline 1.5 times as
    // deep; the body, its shape, its cuts (where a picture ends for the next to carry on) and its soft shadow as
    // they were, so every picture still meets the next as it did.
    public static Bitmap BoostHand(Bitmap src) {
        var bmp = new Bitmap(src.Width, src.Height, PixelFormat.Format32bppArgb);
        for (int j = 0; j < src.Height; j++) for (int i = 0; i < src.Width; i++) { Color c = src.GetPixel(i, j); double a = c.A / 255.0, lum = (c.R + c.G + c.B) / 765.0;
            double lit = a > 0.12 ? Math.Min(0.95, 0.12 + (a - 0.12) * 1.6) : a, dark = a > 0.12 ? Sat(a * 1.5) : a, t = Sat((lum - 0.35) / 0.15);
            bmp.SetPixel(i, j, Color.FromArgb((int)Math.Round(255 * (t * lit + (1 - t) * dark)), c.R, c.G, c.B)); }
        return bmp; }
    // A panel's glass risen to meet a hand that passes over it, as a liquid climbs what it touches: under the hand
    // (its glass `hw` px either side of its axis, from the face's centre, at (cx, cy) in the picture, up to its tip,
    // `len` px out) the glass stands `height` px up, and from the hand's edge it falls away to the panel over `fw` px,
    // steepest at the hand (so it hugs the hand, the hand's edge running down into the panel). Lit as it will face
    // once the face turns it by `turn` degrees: its slopes brighter where they face the top left, darker where they
    // face away, the glass a touch milkier as it rises.
    public static Bitmap Ridge(int w, int h, double cx, double cy, double len, double hw, double fw, double height, double turn) {
        var bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb); double top = cy - (len - hw);
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            double ox = i + 0.5 - cx, oy = j + 0.5 - Math.Max(top, Math.Min(cy, j + 0.5)), d = Hyp(ox, oy), t = (d - hw) / fw;   // (from the nearest point of the axis)
            if (t >= 1) { bmp.SetPixel(i, j, Color.FromArgb(0, 0, 0, 0)); continue; }
            double z = t <= 0 ? 1 : (1 - t) * (1 - t), slope = t <= 0 ? 0 : 2 * height / fw * (1 - t), sx, sy;
            Rot(ox / Math.Max(d, 1e-9), oy / Math.Max(d, 1e-9), turn, out sx, out sy);   // which way it falls away, on the screen
            double facing = -0.6 * sx - 0.8 * sy, A = 0, R = 0, G = 0, B = 0;
            Blend(ref A, ref R, ref G, ref B, 1, 1, 1, 0.03 * z);
            Blend(ref A, ref R, ref G, ref B, 0, 0, 0, 0.30 * slope * Math.Max(-facing, 0));
            Blend(ref A, ref R, ref G, ref B, 1, 1, 1, slope * (0.04 + 0.35 * Math.Max(facing, 0)));
            int ia = (int)Math.Round(255 * Sat(A));
            bmp.SetPixel(i, j, ia == 0 ? Color.FromArgb(0, 0, 0, 0) : Color.FromArgb(ia, (int)Math.Round(255 * Sat(R / A)), (int)Math.Round(255 * Sat(G / A)), (int)Math.Round(255 * Sat(B / A)))); }
        return bmp; }
    // Where the hands' risen glass shows: on the panels (the dials, the date pill, the day and night window, where
    // the face places them), from `from` px inside their edges, fully `to` px in; over the face's box (x0, y0, w, h),
    // drawn at `scale` of its size.
    public static Bitmap PanelsMask(int x0, int y0, int w, int h, double scale, double from, double to) {
        var panels = new Piece[] { new Piece { Kind = 3, X1 = 125, Y1 = 225, R = 44.5 }, new Piece { Kind = 3, X1 = 325, Y1 = 225, R = 44.5 }, new Piece { Kind = 3, X1 = 225, Y1 = 325, R = 44.5 },
            new Piece { Kind = 4, X1 = 178, Y1 = 135, X2 = 272, R = 26.5 }, new Piece { Kind = 5, X1 = 225, Y1 = 96.5, Y2 = 97.5, R = 33, K = 6 } };
        int n = (int)Math.Round(w * scale), m = (int)Math.Round(h * scale); var bmp = new Bitmap(n, m, PixelFormat.Format32bppArgb);
        for (int j = 0; j < m; j++) for (int i = 0; i < n; i++) { double x = x0 + (i + 0.5) * w / n, y = y0 + (j + 0.5) * h / m, d = 1e9;
            foreach (var p in panels) d = Math.Min(d, Field(p, x, y));
            bmp.SetPixel(i, j, Color.FromArgb((int)Math.Round(255 * Smooth(-from, -to, d)), 255, 255, 255)); }
        return bmp; }
    // a mask with another's opaque parts added: the more opaque of the two, pixel by pixel (they're the same size)
    public static Bitmap Union(Bitmap a, Bitmap b) { var bmp = new Bitmap(a.Width, a.Height, PixelFormat.Format32bppArgb);
        for (int j = 0; j < a.Height; j++) for (int i = 0; i < a.Width; i++) bmp.SetPixel(i, j, Color.FromArgb(Math.Max(a.GetPixel(i, j).A, b.GetPixel(i, j).A), 255, 255, 255));
        return bmp; }
    // the box (x0, y0, x1, y1, within the picture) of what's drawn
    public static int[] Used(Bitmap b) { int x0 = b.Width, y0 = b.Height, x1 = -1, y1 = -1;
        for (int j = 0; j < b.Height; j++) for (int i = 0; i < b.Width; i++) if (b.GetPixel(i, j).A > 0) { x0 = Math.Min(x0, i); y0 = Math.Min(y0, j); x1 = Math.Max(x1, i); y1 = Math.Max(y1, j); }
        return new[] { x0, y0, x1, y1 }; }
}
'@
$IN = 204; $OUT = 224; $MID = ($IN + $OUT) / 2   # the tube's walls and middle (px from the face's centre): ring.pl's
function piece($kind, $deg, $len, $thick, $r) { $p = New-Object 'RingGlass+Piece'; $p.Kind = $kind; $p.Deg = $deg; $p.HalfLen = $len; $p.HalfThick = $thick; $p.R = $r; $p }
function look($tint, $rim, $shadow, $drop, $in, $out, $reach, $soft) { $l = New-Object 'RingGlass+Look'; $l.Tint = $tint; $l.Rim = $rim; $l.Shadow = $shadow; $l.Drop = $drop; $l.In = $in; $l.Out = $out
  $l.Light = 2.2; $l.Shade = 4; $l.Edge = 2; $l.Dome = 3; $l.Inner = 0.4; $l.InnerReach = $reach; $l.InnerSoft = $soft; $l }
# The looks, after Cover Display's (its dp at the watch's 2 px): the tube as its progress tube (12% milky, a
# 3 dp rim); the marks as its frosted labels (16% milky: matte), their rim and shadow sized to them; the knob as
# its slider knob, a little more milky (10%, from 6%). All of them deeper than Cover Display's, which takes much
# of its depth from bending what's behind the glass (the face can't): the rim's light 2.2 times as bright, its
# shading where it turns away 4 times as deep, its dark outline twice, the dome through its body 3 times, and
# each shadow 6.4 times as strong; and an inner shadow along each dark side (40% at the edge), reaching in about
# a third of the way across.
$tube = look 0.12 6 1.6 8 16 28 3 2; $markLook = look 0.16 8 1.6 3 3 8 1.4 1; $knobLook = look 0.10 12 1.6 5 6 12 3.5 2.5
# a minute's mark: across the tube from wall to wall, wide enough to show the line of the accent in it; an
# hour's: from the outer wall in past the inner one, thicker; the song's knob: Cover Display's slider knob, a
# little thicker than the tube, along its middle
$minute = @((($OUT - $IN) / 2), 3.9, $MID); $hour = @((($OUT - 195) / 2), 6.0, (($OUT + 195) / 2)); $knob = @(19.0, 11.5, $MID)
# draw over the box (cut to the face) and keep only what's drawn: the picture and its frame on the face
function draw($name, $x0, $y0, $x1, $y1, $p, $l, $turn) {
  $x0 = [math]::Max(0, $x0); $y0 = [math]::Max(0, $y0); $x1 = [math]::Min(449, $x1); $y1 = [math]::Min(449, $y1)
  $b = [RingGlass]::Render($x0, $y0, ($x1 + 1 - $x0), ($y1 + 1 - $y0), $p, $l, $turn, 0); $u = [RingGlass]::Used($b)
  $c = $b.Clone((New-Object System.Drawing.Rectangle $u[0], $u[1], ($u[2] + 1 - $u[0]), ($u[3] + 1 - $u[1])), $b.PixelFormat); $b.Dispose()
  $c.Save((Join-Path $dir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png); $c.Dispose()
  ,@(($x0 + $u[0]), ($y0 + $u[1]), ($u[2] + 1 - $u[0]), ($u[3] + 1 - $u[1])) }
$lines = @()
# the tube, the whole ring with its shadow
$f = draw 'exp_ring_tube' 0 0 449 449 (piece 0 0 0 (($OUT - $IN) / 2) $MID) $tube 0
$lines += "exp_ring_tube {0} {1} {2} {3}" -f $f[0], $f[1], $f[2], $f[3]
# the marks, each where it sits (they don't move)
foreach ($m in 0..59) { $k = if ($m % 5) { $minute } else { $hour }; $deg = $m * 6; $a = $deg * [math]::PI / 180
  $cx = 225 + $k[2] * [math]::Sin($a); $cy = 225 - $k[2] * [math]::Cos($a); $reach = $k[0] + 12
  $name = 'exp_ring_mk{0:00}' -f $m; $f = draw $name ([math]::Floor($cx - $reach)) ([math]::Floor($cy - $reach)) ([math]::Ceiling($cx + $reach)) ([math]::Ceiling($cy + $reach)) (piece 1 $deg $k[0] $k[1] $k[2]) $markLook 0
  $lines += "{0} {1} {2} {3} {4} {5} {6} {7} {8}" -f $name, $f[0], $f[1], $f[2], $f[3], $k[2], $(if ($m % 5) { 'minute' } else { 'hour' }), $k[0], $k[1] }
# the song's knob on its own, at 12 o'clock: the face turns it to where the song is, gliding. It's lit (and its
# shadow falls) as it will face once turned: one picture for each 10 degrees it's turned to, so its light is
# never more than 5 degrees off. All 36 share one frame, the box that holds each of them.
$p = piece 2 0 $knob[0] $knob[1] $knob[2]; $box = @(180, 0, 270, 50)
$all = foreach ($k in 0..35) { ,[RingGlass]::Render($box[0], $box[1], ($box[2] + 1 - $box[0]), ($box[3] + 1 - $box[1]), $p, $knobLook, ($k * 10), 0) }
$u = @(999, 999, -1, -1); foreach ($b in $all) { $v = [RingGlass]::Used($b); $u = @([math]::Min($u[0], $v[0]), [math]::Min($u[1], $v[1]), [math]::Max($u[2], $v[2]), [math]::Max($u[3], $v[3])) }
# (the frame centred on 12 o'clock, as the face's box for the knob turns about its middle)
$half = [math]::Max(225 - ($box[0] + $u[0]), ($box[0] + $u[2] + 1) - 225); $u[0] = 225 - $half - $box[0]; $u[2] = 225 + $half - 1 - $box[0]
foreach ($k in 0..35) { $name = 'exp_ring_knob{0:00}' -f $k
  $c = $all[$k].Clone((New-Object System.Drawing.Rectangle $u[0], $u[1], ($u[2] + 1 - $u[0]), ($u[3] + 1 - $u[1])), $all[$k].PixelFormat); $c.Save((Join-Path $dir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png); $c.Dispose(); $all[$k].Dispose()
  $lines += "{0} {1} {2} {3} {4} {5}" -f $name, ($box[0] + $u[0]), ($box[1] + $u[1]), ($u[2] + 1 - $u[0]), ($u[3] + 1 - $u[1]), ($k * 10) }
# The panels, in the same glass (each in its own pictures' frames, as the face places them): the dials (one set
# of pictures for all three), the date pill, the day and night window, and the moon's lens in it. A panel's glass
# is two pictures, as it was, so the face still cuts its rim away where a hand's glass melts into it: its body
# (the milky tint and the dome) and its rim (the light and shade over that); its shadow is a picture of its own,
# under all of them. Clearer than the ring, a light milkiness (7%, and the dome two thirds the ring's), the rim 6 dp wide, the
# inner shadow reaching 8 px in (6 on the window, which is shallower). The moon's lens, which nothing cuts, is one
# picture, a small shadow in its frame.
function shape($kind, $x1, $y1, $x2, $y2, $r, $k) { $p = New-Object 'RingGlass+Piece'; $p.Kind = $kind; $p.X1 = $x1; $p.Y1 = $y1; $p.X2 = $x2; $p.Y2 = $y2; $p.R = $r; $p.K = $k; $p }
function clearer($l) { $l.Tint = 0.07; $l.Dome = 2; $l }
function panel($name, $w, $h, $p, $l) {
  foreach ($part in @(@('body', 1), @('rim', 2))) { $b = [RingGlass]::Render(0, 0, $w, $h, $p, $l, 0, $part[1]); $b.Save((Join-Path $dir "exp_${name}_$($part[0]).png"), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() }
  $m = 48; $b = [RingGlass]::Render(-$m, -$m, ($w + 2 * $m), ($h + 2 * $m), $p, $l, 0, 3); $u = [RingGlass]::Used($b)
  $c = $b.Clone((New-Object System.Drawing.Rectangle $u[0], $u[1], ($u[2] + 1 - $u[0]), ($u[3] + 1 - $u[1])), $b.PixelFormat); $b.Dispose()
  $c.Save((Join-Path $dir "exp_${name}_shadow.png"), [System.Drawing.Imaging.ImageFormat]::Png); $c.Dispose()
  "exp_${name}_shadow {0} {1} {2} {3}" -f ($u[0] - $m), ($u[1] - $m), ($u[2] + 1 - $u[0]), ($u[3] + 1 - $u[1]) }   # (from the panel's pictures' corner)
$lines += panel 'dial' 104 104 (shape 3 52 52 0 0 44.5 0) (clearer (look 0 12 1.6 8 16 28 8 4))
$lines += panel 'pill' 162 68 (shape 4 34 34 128 0 26.5 0) (clearer (look 0 12 1.6 8 16 28 8 4))
$lines += panel 'window' 78 45 (shape 5 39 38.5 0 39.5 33 6) (clearer (look 0 12 1.6 8 16 28 6 3))
$b = [RingGlass]::Render(0, 0, 48, 48, (shape 3 24 24 0 0 20.5 0), (clearer (look 0 8 1.6 2 3 3 5 2)), 0, 0); $b.Save((Join-Path $dir 'exp_moon_glass.png'), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose()
# The hour beads (exp_bead, one of the hands' pictures, 25 px, the bead 7.2 px round its middle) get the panels'
# glass laid over them, where they sit, lit as the panels are: their milky body and dome, the light on their rim
# (softer: the bead has its own bright rim) and its shade, the inner shadow and a small shadow; not the dark outline,
# which the bead has. ring.pl lays it over each bead and over its melt into the second hand's tip as that passes
# it (the melt is drawn turned to each bead, so it can't be lit from the top left itself).
$l = clearer (look 0 5 1.6 2 2 4 2 1.5); $l.Light = 1.2; $l.Edge = 0
$b = [RingGlass]::Render(0, 0, 25, 25, (shape 3 12.5 12.5 0 0 7.2 0), $l, 0, 0); $b.Save((Join-Path $dir 'exp_bead_glass.png'), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose()
# The hands' glass: every picture of it (the tubes, the hub, their melts into each other, into the panels and into
# the hour beads, and the beads themselves), its light deepened from tools\ring\hands_src (the pictures as they were
# first made, kept so this can run again from them; their generator is gone, so they're deepened, not redrawn).
foreach ($f in Get-ChildItem (Join-Path $PSScriptRoot 'hands_src\*.png')) { $src = New-Object System.Drawing.Bitmap $f.FullName
  $b = [RingGlass]::BoostHand($src); $src.Dispose(); $b.Save((Join-Path $dir $f.Name), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() }
# The hands' shadows: each hand's glass, softened and darkened, which the face draws as it draws the hand but a
# little lower, so the hands stand up off the face and the panels under them.
foreach ($n in 'hour', 'minute', 'second') { $src = New-Object System.Drawing.Bitmap (Join-Path $dir "exp_${n}_tube.png")
  $b = [RingGlass]::Shadow($src, 0.35, 2); $src.Dispose(); $b.Save((Join-Path $dir "exp_${n}_shadow.png"), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() }
# Where a hand passes over a panel, the panel's glass rises to meet it: for each hand, in its glass's picture's
# frame made 12 px taller (for the glass risen round its tip), from its pivot (px down that picture, where the face
# turns it: ring.pl checks) out to its glass's tip, as wide as its glass, falling away over 12, 11 or 8 px, 6 px
# high (4.5 for the thin second hand, as its waves are: ring.pl). Each lit as it will face once turned: a picture for each 10 degrees. They show
# only on the panels, from 6 px inside their edges (fully at 12), where the hands' glass is back on top of them
# (below): a mask of that, over the box that holds the panels, at half size (it's soft).
$M = 12; $from = 6; $to = 12
foreach ($h in @(@('hour', 109, 106, 9, 12, 6), @('minute', 159, 156, 7, 11, 6), @('second', 182.93, 180, 5, 8, 4.5))) {
  $src = New-Object System.Drawing.Bitmap (Join-Path $dir "exp_$($h[0])_tube.png"); $w = $src.Width; $ht = $src.Height + $M; $src.Dispose()
  foreach ($k in 0..35) { $b = [RingGlass]::Ridge($w, $ht, ($w / 2), ($h[1] + $M), $h[2], $h[3], $h[4], $h[5], ($k * 10))
    $b.Save((Join-Path $dir ('exp_ridge_{0}_{1:00}.png' -f $h[0], $k)), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() }
  $lines += "exp_ridge_{0} {1} {2}" -f $h[0], $M, $h[1] }   # (its margin and pivot)
$box = @(80, 63, 290, 307); $b = [RingGlass]::PanelsMask($box[0], $box[1], $box[2], $box[3], 0.5, $from, $to); $b.Save((Join-Path $dir 'exp_ridge_mask.png'), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose()
$lines += "exp_ridge_mask {0} {1} {2} {3}" -f $box
# The hands' glass and smoke stop at the panels: the face's masks for them (over its box 41 to 409, at half size)
# take them off the panels, the glass a little beyond them too, where the face draws it melting into them (the
# necks). Here they're put back from 6 px inside the panels' edges (fully at 12, past the necks), so the hands pass
# over the panels, on top of them.
$inner = [RingGlass]::PanelsMask(41, 41, 368, 368, 0.5, $from, $to)
foreach ($n in 'tube', 'smoke') { $m = New-Object System.Drawing.Bitmap (Join-Path $dir "exp_${n}_mask.png"); $b = [RingGlass]::Union($m, $inner); $m.Dispose()
  $b.Save((Join-Path $dir "exp_${n}_mask_top.png"), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() }
$inner.Dispose()
$lines | Set-Content -Encoding ascii (Join-Path $PSScriptRoot 'frames.txt'); "{0} pictures" -f ($lines.Count + 13 + 3 * 35)
