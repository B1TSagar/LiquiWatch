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
Add-Type -ReferencedAssemblies System.Drawing -Path (Join-Path $PSScriptRoot 'RingGlass.cs')
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
# (the song's knob rounder still, a lens standing off the tube: its dome, rim light and shade, inner shadow and shadow
# deeper, and a glint toward the light)
$knobLook.Shadow = 2.4; $knobLook.Dome = 5; $knobLook.Light = 2.8; $knobLook.Shade = 5; $knobLook.Inner = 0.55; $knobLook.Gloss = 0.45
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
# the hour beads, and the beads themselves), its dark outline deepened and its light softened from tools\ring\hands_src (the pictures as they were
# first made, kept so this can run again from them; their generator is gone, so they're deepened, not redrawn).
foreach ($f in Get-ChildItem (Join-Path $PSScriptRoot 'hands_src\*.png')) { $src = New-Object System.Drawing.Bitmap $f.FullName
  $b = [RingGlass]::BoostHand($src); $src.Dispose(); $b.Save((Join-Path $dir $f.Name), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() }
# The hands' shadows: each hand's glass, softened and darkened, which the face draws as it draws the hand but a
# little lower, so the hands stand up off the face and the panels under them.
foreach ($n in 'hour', 'minute', 'second') { $src = New-Object System.Drawing.Bitmap (Join-Path $dir "exp_${n}_tube.png")
  $b = [RingGlass]::Shadow($src, 0.35, 2); $src.Dispose(); $b.Save((Join-Path $dir "exp_${n}_shadow.png"), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() }
# Where a hand passes over a panel, the panel's glass rises to meet it: for each hand, in its glass's picture's
# frame made 12 px taller (for the glass risen round its tip), from its pivot (px down that picture, where the face
# turns it: ring.pl checks) out to its glass's tip, as wide as its glass, falling away over 12, 11 or 8 px, 13.5 px
# high (10.125 for the thin second hand). Each lit as it will face once turned: a picture for each 10 degrees. They show
# only on the panels, from 6 px inside their edges (fully at 12), where the hands' glass is back on top of them
# (below): a mask of that, over the box that holds the panels, at half size (it's soft).
$M = 12; $from = 6; $to = 12
foreach ($h in @(@('hour', 109, 106, 9, 12, 13.5), @('minute', 159, 156, 7, 11, 13.5), @('second', 182.93, 180, 5, 8, 10.125))) {
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
