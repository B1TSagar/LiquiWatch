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
    public class Look { public double Tint, Rim, Shadow, Drop, In, Out, Light = 1, Shade = 1, Edge = 1, Dome = 1, Inner, InnerReach, InnerSoft = 1, Gloss; }
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
                    if (gr < 0) Blend(ref A, ref R, ref G, ref B, 0, 0, 0, k.Dome * 0.025 * -gr);
                    // a glint on its dome toward the light, as a bead of glass catches it (Gloss: how bright)
                    if (k.Gloss > 0) { double gx = (x - (zx + 0.42 * hx * ldx)) / (0.30 * hx), gy = (y - (zy + 0.42 * hy * ldy)) / (0.26 * hy);
                        Blend(ref A, ref R, ref G, ref B, 1, 1, 1, k.Gloss * Math.Exp(-(gx * gx + gy * gy))); } }
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
    // A hand's glass picture with its dark outline deepened as the ring's is and its light softened: above the milky
    // body (12%, as milky as the ring's tube already) the glow and the hair-thin highlight 0.8 times as strong (at
    // 1.6 they read as a white outline), the dark outline 1.5 times as deep; the body, its shape, its cuts (where a
    // picture ends for the next to carry on) and its soft shadow as they were, so every picture still meets the next.
    public static Bitmap BoostHand(Bitmap src) {
        var bmp = new Bitmap(src.Width, src.Height, PixelFormat.Format32bppArgb);
        for (int j = 0; j < src.Height; j++) for (int i = 0; i < src.Width; i++) { Color c = src.GetPixel(i, j); double a = c.A / 255.0, lum = (c.R + c.G + c.B) / 765.0;
            double lit = a > 0.12 ? Math.Min(0.95, 0.12 + (a - 0.12) * 0.8) : a, dark = a > 0.12 ? Sat(a * 1.5) : a, t = Sat((lum - 0.35) / 0.15);
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
            double sx, sy; Rot(ox / Math.Max(d, 1e-9), oy / Math.Max(d, 1e-9), turn, out sx, out sy);   // which way it falls away, on the screen
            // (its slopes lit at half what their steepness gives: fully, they drew a wide bright band along the hand)
            bmp.SetPixel(i, j, Risen(t <= 0 ? 1 : (1 - t) * (1 - t), t <= 0 ? 0 : 0.5 * 2 * height / fw * (1 - t), sx, sy, 1)); }
        return bmp; }
    // Risen glass `z` of the way up (1 on its crest), sloping by `slope` (its rise per px) and falling away toward (sx, sy)
    // on the screen: its slopes brighter where they face the top left, darker where they face away, the glass a touch
    // milkier as it rises; its cover times `alpha`.
    static Color Risen(double z, double slope, double sx, double sy, double alpha, double gloss = 0) {
        double facing = -0.6 * sx - 0.8 * sy, A = 0, R = 0, G = 0, B = 0;
        Blend(ref A, ref R, ref G, ref B, 1, 1, 1, 0.03 * z);
        Blend(ref A, ref R, ref G, ref B, 0, 0, 0, 0.30 * slope * Math.Max(-facing, 0));
        Blend(ref A, ref R, ref G, ref B, 1, 1, 1, slope * (0.04 + 0.35 * Math.Max(facing, 0)));
        if (gloss > 0) Blend(ref A, ref R, ref G, ref B, 1, 1, 1, gloss);   // a glint (none under the hands)
        int ia = (int)Math.Round(255 * Sat(A) * alpha);
        return ia == 0 ? Color.FromArgb(0, 0, 0, 0) : Color.FromArgb(ia, (int)Math.Round(255 * Sat(R / A)), (int)Math.Round(255 * Sat(G / A)), (int)Math.Round(255 * Sat(B / A))); }
    // A panel's outline (kind and numbers as jelly.pl writes them to fronts.txt: a circle cx cy r, a stadium x1 x2 cy r,
    // a dome cx cy r on its flat base), and how far (x, y) is outside it, negative inside
    public static double Field(string kind, double[] o, double x, double y) {
        if (kind == "circle") return Hyp(x - o[0], y - o[1]) - o[2];
        if (kind == "stadium") { double px = x < o[0] ? o[0] : x > o[1] ? o[1] : x; return Hyp(x - px, y - o[2]) - o[3]; }
        return Math.Max(Hyp(x - o[0], y - o[1]) - o[2], y - o[1]); }
    // n + 1 points along the quadratic from (x1, y1) by (kx, ky) to (x2, y2): x, y, x, y, ...
    public static double[] Quad(double x1, double y1, double kx, double ky, double x2, double y2, int n) {
        var p = new double[2 * (n + 1)];
        for (int q = 0; q <= n; q++) { double u = (double)q / n; p[2 * q] = (1 - u) * (1 - u) * x1 + 2 * u * (1 - u) * kx + u * u * x2; p[2 * q + 1] = (1 - u) * (1 - u) * y1 + 2 * u * (1 - u) * ky + u * u * y2; }
        return p; }
    // the outline `inset` px in, round once, a point about every pixel: x, y, x, y, ... (a circle or a stadium)
    public static double[] Outline(string kind, double[] o, double inset) {
        var p = new System.Collections.Generic.List<double>();
        if (kind == "circle") { double r = o[2] - inset; int n = (int)Math.Ceiling(2 * Math.PI * r);
            for (int q = 0; q < n; q++) { double a = 2 * Math.PI * q / n; p.Add(o[0] + r * Math.Sin(a)); p.Add(o[1] - r * Math.Cos(a)); } }
        else if (kind == "stadium") { double r = o[3] - inset; int ne = (int)Math.Ceiling(o[1] - o[0]), nc = (int)Math.Ceiling(Math.PI * r);
            for (int q = 0; q < ne; q++) { p.Add(o[0] + (o[1] - o[0]) * q / ne); p.Add(o[2] - r); }                                   // the top, left to right
            for (int q = 0; q < nc; q++) { double a = Math.PI * q / nc; p.Add(o[1] + r * Math.Sin(a)); p.Add(o[2] - r * Math.Cos(a)); }  // round the right end
            for (int q = 0; q < ne; q++) { p.Add(o[1] - (o[1] - o[0]) * q / ne); p.Add(o[2] + r); }                                   // the bottom, right to left
            for (int q = 0; q < nc; q++) { double a = Math.PI * q / nc; p.Add(o[0] - r * Math.Sin(a)); p.Add(o[2] + r * Math.Cos(a)); } } // round the left end
        else throw new ArgumentException("no outline for " + kind);
        return p.ToArray(); }
    // A wave in a panel's glass along a line of points (x, y, x, y, ... on the face; closed: back to its start): across it,
    // a sine's hump `half` px either side of the line, up from the glass's own level to a crest on the line `height` px
    // up and back down to that level, easing in and out so it has no edge. Lit as Ridge's: its slopes brighter where they
    // face the top left, darker where they face away, a glint on the steepest that face the light; its cover times
    // `alpha`, and only inside the outline (kind, o: as Field's), from 1 px in. Cropped to what's drawn: (x0, y0) is its
    // corner on the face.
    public static Bitmap RidgeAlong(double[] pts, bool closed, double height, double half, double alpha, string kind, double[] o, out int x0, out int y0) {
        int n = pts.Length / 2, segs = closed ? n : n - 1; double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9, m = half + 1;
        for (int q = 0; q < n; q++) { minX = Math.Min(minX, pts[2 * q]); maxX = Math.Max(maxX, pts[2 * q]); minY = Math.Min(minY, pts[2 * q + 1]); maxY = Math.Max(maxY, pts[2 * q + 1]); }
        int bx = (int)Math.Floor(minX - m), by = (int)Math.Floor(minY - m), bw = (int)Math.Ceiling(maxX + m) - bx, bh = (int)Math.Ceiling(maxY + m) - by;
        var c = new Color[bw, bh]; int u0 = bw, v0 = bh, u1 = -1, v1 = -1;
        for (int j = 0; j < bh; j++) for (int i = 0; i < bw; i++) {
            double px = bx + i + 0.5, py = by + j + 0.5, best = 1e18, nx = 0, ny = 0;
            for (int q = 0; q < segs; q++) {   // the nearest point of the line
                double ax = pts[2 * q], ay = pts[2 * q + 1], dx = pts[2 * ((q + 1) % n)] - ax, dy = pts[2 * ((q + 1) % n) + 1] - ay, l2 = dx * dx + dy * dy;
                double s = l2 > 0 ? Sat(((px - ax) * dx + (py - ay) * dy) / l2) : 0, qx = ax + s * dx, qy = ay + s * dy, d2 = (px - qx) * (px - qx) + (py - qy) * (py - qy);
                if (d2 < best) { best = d2; nx = qx; ny = qy; } }
            double d = Math.Sqrt(best), inside = Sat(-Field(kind, o, px, py) - 1);
            if (d >= half || inside <= 0) { c[i, j] = Color.FromArgb(0, 0, 0, 0); continue; }
            double a = Math.PI * d / (2 * half), z = Math.Cos(a) * Math.Cos(a), slope = height * Math.PI / (2 * half) * Math.Sin(2 * a);   // (its height as a share of the crest's, and its fall per px)
            double sx = (px - nx) / Math.Max(d, 1e-9), sy = (py - ny) / Math.Max(d, 1e-9), lit = Sat(-0.6 * sx - 0.8 * sy);   // (it falls away from the line)
            c[i, j] = Risen(z, slope, sx, sy, alpha * inside, 0.4 * lit * Math.Pow(Sat(slope), 3));
            if (c[i, j].A > 0) { u0 = Math.Min(u0, i); v0 = Math.Min(v0, j); u1 = Math.Max(u1, i); v1 = Math.Max(v1, j); } }
        if (u1 < 0) throw new InvalidOperationException("nothing of it is inside its glass");
        var bmp = new Bitmap(u1 - u0 + 1, v1 - v0 + 1, PixelFormat.Format32bppArgb);
        for (int j = v0; j <= v1; j++) for (int i = u0; i <= u1; i++) bmp.SetPixel(i - u0, j - v0, c[i, j]);
        x0 = bx + u0; y0 = by + v0; return bmp; }
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
