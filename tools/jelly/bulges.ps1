# Usage: powershell -File tools\jelly\bulges.ps1
# Makes the bulge pictures (exp_*_bulge*.png) the jelly glass draws: each glass element's own glass
# (its pictures, drawn one over the other) pushed outward by a smooth bump on soft shoulders, as the hand
# necks pull the glass, kept as only what turns the glass as it is into the risen glass (the edge's old
# bright line under the bump dimmed away, so the bump is the edge itself rising). Prints each picture's
# name, size and reference point, which jelly.pl's %PIC must match.
#
# Arc: around a circle (centre Cx,Cy, the edge's bright line at radius Edge-1), sampled at angle Ang
# (clockwise from 12). Line: a straight edge through Cx,Cy (on its bright line), facing outward at angle
# Ang. Either way the picture comes out facing up (outward = up), cropped; its reference point is the
# circle's centre or the edge point. Coordinates are continuous: pixel i spans i..i+1.
# (PowerShell names ignore case: no two variables here may differ only in case.)
Add-Type -AssemblyName System.Drawing
$dir = Join-Path $PSScriptRoot '..\..\watchfaces\src\expedition\res\drawable-nodpi'
function Make-Bulge([string]$Src, [string]$Mode, [double]$Cx, [double]$Cy, [double]$Edge = 0, [double]$Ang = 0,
      [double]$Hgt = 3.5, [double]$Sig = 8.4, [double]$Ramp = 12, [double]$Glow = 0.16, [string]$Out) {
$first = [System.Drawing.Image]::FromFile("$dir\$($Src.Split(',')[0]).png"); $sw = $first.Width; $sh = $first.Height; $first.Dispose()
$bmp = New-Object System.Drawing.Bitmap $sw, $sh
$gr = [System.Drawing.Graphics]::FromImage($bmp)
foreach ($n in $Src.Split(',')) { $im = [System.Drawing.Image]::FromFile("$dir\$n.png"); $gr.DrawImage($im, 0, 0, $sw, $sh); $im.Dispose() }
$gr.Dispose()
$pm = New-Object 'double[,,]' $sw, $sh, 4
for ($j = 0; $j -lt $sh; $j++) { for ($i = 0; $i -lt $sw; $i++) { $c = $bmp.GetPixel($i, $j); $fa = $c.A / 255.0
  $pm[$i, $j, 0] = $c.R * $fa; $pm[$i, $j, 1] = $c.G * $fa; $pm[$i, $j, 2] = $c.B * $fa; $pm[$i, $j, 3] = $c.A } }
function sample([double]$px, [double]$py) {
  $px -= 0.5; $py -= 0.5
  $i0 = [math]::Floor($px); $j0 = [math]::Floor($py); $fx = $px - $i0; $fy = $py - $j0; $acc = @(0.0, 0.0, 0.0, 0.0)
  foreach ($k in @(@(0, 0, ((1 - $fx) * (1 - $fy))), @(1, 0, ($fx * (1 - $fy))), @(0, 1, ((1 - $fx) * $fy)), @(1, 1, ($fx * $fy)))) {
    $ii = $i0 + $k[0]; $jj = $j0 + $k[1]; if ($ii -lt 0 -or $jj -lt 0 -or $ii -ge $sw -or $jj -ge $sh) { continue }
    for ($ch = 0; $ch -lt 4; $ch++) { $acc[$ch] += $pm[$ii, $jj, $ch] * $k[2] } }
  $acc }
$ca = [math]::Cos($Ang * [math]::PI / 180); $sa = [math]::Sin($Ang * [math]::PI / 180)
# local (lx, ly): picture frame, facing up; to the source: turned by Ang about the centre / edge point
function toSrc([double]$lx, [double]$ly) { @(($Cx + $lx * $ca - $ly * $sa), ($Cy + $lx * $sa + $ly * $ca)) }
if ($Mode -eq 'arc') { $ext = $Edge + $Hgt + 4; $x0 = -$ext; $x1 = $ext; $y0 = -$ext; $y1 = 0.0; $sigDeg = $Sig / ($Edge - 1) * 180 / [math]::PI }
else { $x0 = -3 * $Sig; $x1 = 3 * $Sig; $y0 = -($Hgt + 5); $y1 = $Ramp + 2 }
$cw = [int][math]::Ceiling($x1 - $x0); $chh = [int][math]::Ceiling($y1 - $y0)
$canvas = New-Object 'int[,,]' $cw, $chh, 4
$minX = $cw; $minY = $chh; $maxX = -1; $maxY = -1
for ($j = 0; $j -lt $chh; $j++) { for ($i = 0; $i -lt $cw; $i++) {
  $lx = $x0 + $i + 0.5; $ly = $y0 + $j + 0.5
  if ($Mode -eq 'arc') {
    $rad = [math]::Sqrt($lx * $lx + $ly * $ly); if ($rad -lt 0.01) { continue }
    $tdeg = [math]::Atan2($lx, -$ly) * 180 / [math]::PI
    $bump = [math]::Exp(-($tdeg / $sigDeg) * ($tdeg / $sigDeg))
    $rmp = [math]::Min(1.0, [math]::Max(0.0, ($rad - ($Edge - $Ramp)) / $Ramp))
    $rs = $rad - $Hgt * $bump * $rmp
    $ps = toSrc ($lx / $rad * $rs) ($ly / $rad * $rs)
    $out_v = $rad - $Edge   # how far out of the edge (fringe position below)
  } else {
    $bump = [math]::Exp(-($lx / $Sig) * ($lx / $Sig))
    $vv = -$ly; $rmp = [math]::Min(1.0, [math]::Max(0.0, ($vv + $Ramp) / $Ramp))
    $vs = $vv - $Hgt * $bump * $rmp
    $ps = toSrc $lx (-$vs)
    $out_v = $vv - 1
  }
  $s = sample $ps[0] $ps[1]
  $po = toSrc $lx $ly; $o = sample $po[0] $po[1]
  # red fringe just inside the risen edge, as on the necks
  $f = [math]::Exp(-[math]::Pow(($out_v - ($Hgt * $bump - 2.2)) / 0.9, 2)) * $bump * 0.35
  $aw = $s[3]; $cr = $s[0]; $cg = $s[1]; $cb = $s[2]
  # thin edges leave a dark gap under the risen line: fill it with a soft glass glow
  $rise = $Hgt * $bump; $inGap = [math]::Min(1.0, [math]::Max(0.0, $out_v + 1.5)) * [math]::Min(1.0, [math]::Max(0.0, ($rise - $out_v - 1) / 1.5))
  $gl = $Glow * $inGap * [math]::Min(1.0, $bump * 1.5)
  $cr = $cr * (1 - $gl) + 235 * $gl; $cg = $cg * (1 - $gl) + 235 * $gl; $cb = $cb * (1 - $gl) + 230 * $gl; $aw = $aw * (1 - $gl) + 255 * $gl
  $cr += 175 * $f * 120 / 255; $cg += 110 * $f * 120 / 255; $cb += 102 * $f * 120 / 255; $aw = $aw + (255 - $aw) * $f * 120 / 255
  # X over O = W: Xa = (Wa - Oa) / (1 - Oa), X = W - (1 - Xa) O (premultiplied), which holds over anything
  $oa = $o[3] / 255
  $xa = ($aw / 255 - $oa) / [math]::Max(1 - $oa, 0.001)
  $cr = $cr - (1 - $xa) * $o[0]; $cg = $cg - (1 - $xa) * $o[1]; $cb = $cb - (1 - $xa) * $o[2]; $lim = 255 * $xa + 0.5
  if ($xa -lt 0 -or $xa -gt 1 -or [math]::Min($cr, [math]::Min($cg, $cb)) -lt -0.5 -or [math]::Max($cr, [math]::Max($cg, $cb)) -gt $lim) {
    # No overlay does that over anything where the risen glass is darker (the edge's old bright line, its glass
    # moved out: left showing, it cut the bump off from the glass) or brighter without more cover: there it's
    # darkened or lightened as it shows over the panels' dark (28) instead.
    $fw = ($cr + $cg + $cb + (1 - $xa) * ($o[0] + $o[1] + $o[2])) / 3 + (255 - $aw) / 255 * 28; $fo = ($o[0] + $o[1] + $o[2]) / 3 + (1 - $oa) * 28
    if ($fw -lt $fo) { $xa = 1 - $fw / [math]::Max($fo, 0.001); $cr = 0; $cg = 0; $cb = 0 }
    else { $xa = ($fw - $fo) / [math]::Max(255 - $fo, 0.001); $cr = 255 * $xa; $cg = $cr; $cb = 252 * $xa } }
  $af = 255 * [math]::Min(1.0, [math]::Max(0.0, $xa))
  if ($af -lt 1) { continue }
  $m = 255 / $af
  $canvas[$i, $j, 0] = [int][math]::Min(255.0, $af); $canvas[$i, $j, 1] = [int][math]::Min(255.0, [math]::Max(0.0, $cr * $m)); $canvas[$i, $j, 2] = [int][math]::Min(255.0, [math]::Max(0.0, $cg * $m)); $canvas[$i, $j, 3] = [int][math]::Min(255.0, [math]::Max(0.0, $cb * $m))
  if ($i -lt $minX) { $minX = $i }; if ($i -gt $maxX) { $maxX = $i }; if ($j -lt $minY) { $minY = $j }; if ($j -gt $maxY) { $maxY = $j } } }
$pw = $maxX - $minX + 1; $ph = $maxY - $minY + 1
$res = New-Object System.Drawing.Bitmap $pw, $ph
for ($j = 0; $j -lt $ph; $j++) { for ($i = 0; $i -lt $pw; $i++) { $q = @($canvas[($i + $minX), ($j + $minY), 0], $canvas[($i + $minX), ($j + $minY), 1], $canvas[($i + $minX), ($j + $minY), 2], $canvas[($i + $minX), ($j + $minY), 3])
  $res.SetPixel($i, $j, [System.Drawing.Color]::FromArgb($q[0], $q[1], $q[2], $q[3])) } }
$res.Save("$dir\$Out.png", [System.Drawing.Imaging.ImageFormat]::Png)
# the reference point (local 0,0) in the cropped picture
"{0,-22} {1} {2} {3} {4}" -f $Out, $pw, $ph, ((0 - $x0) - $minX), ((0 - $y0) - $minY)
}

# The dials (centre 52,52 in their 104 px pictures), the date pill (162x68: caps round 34,34 and 128,34,
# edges at y 8.5 and 59.5), the day and night window (78x45: a dome round 39,38.5 on a flat edge).
Make-Bulge -Src 'exp_dial_body,exp_dial_rim' -Mode arc -Cx 52 -Cy 52 -Edge 44.5 -Ang 0 -Sig 8.35 -Ramp 14.5 -Out exp_dial_bulge
Make-Bulge -Src 'exp_pill_body,exp_pill_rim' -Mode arc -Cx 34 -Cy 34 -Edge 26.5 -Ang 270 -Ramp 12 -Out exp_pill_bulge_cap
Make-Bulge -Src 'exp_pill_body,exp_pill_rim' -Mode line -Cx 81 -Cy 8.5 -Ang 0 -Ramp 12 -Out exp_pill_bulge_top
Make-Bulge -Src 'exp_pill_body,exp_pill_rim' -Mode line -Cx 81 -Cy 59.5 -Ang 180 -Ramp 12 -Out exp_pill_bulge_bot
Make-Bulge -Src 'exp_window_body,exp_window_rim' -Mode arc -Cx 39 -Cy 38.5 -Edge 33 -Ang 0 -Ramp 12 -Out exp_window_bulge_cap
Make-Bulge -Src 'exp_window_body,exp_window_rim' -Mode line -Cx 39 -Cy 38.5 -Ang 180 -Ramp 10 -Out exp_window_bulge_line
