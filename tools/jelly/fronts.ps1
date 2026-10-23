# Usage: powershell -File tools\jelly\fronts.ps1   (after: PLAN=1 perl tools/jelly/jelly.pl < tools/ring/expedition_source.xml;
#   then: python tools/jelly/pack_waves.py)
# Draws the waves jelly.pl planned in fronts.txt: each frame of each wave as a sine's hump of risen glass along its
# curve, as high as its line says (4.5 px, less as it sinks) and $HALF px either side of it, lit as the hands' risen
# glass is (RingGlass.RidgeAlong, sharing Ridge's light), with the frame's fade in it, inside its panel's glass from
# 1 px in, where the bulge on the rim takes over; and the tap rings, 7 px inside a dial's and the pill's outline.
# The waves' frames go to frames\, for pack_waves.py to draw each frame's bulges over and pack each run's into one
# animated picture for the face; the rings go to the face. Writes each picture's box on the face to
# fronts_frames.txt, after the hash of the fronts.txt drawn.
$ErrorActionPreference = 'Stop'
$dir = Join-Path $PSScriptRoot '..\..\watchfaces\src\expedition\res\drawable-nodpi'
$frames = Join-Path $PSScriptRoot 'frames'
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -Path (Join-Path $PSScriptRoot '..\ring\RingGlass.cs')
$plan = Join-Path $PSScriptRoot 'fronts.txt'
$HALF = 10   # how far the hump spreads either side of its crest (px)
New-Item -ItemType Directory -Force $frames | Out-Null
foreach ($d in $dir, $frames) { Get-ChildItem $d -Filter 'exp_front_*.png' | Remove-Item }; Get-ChildItem $dir -Filter 'exp_ripple_*.png' | Remove-Item
$outline = @{}; $lines = @('sha256 ' + (Get-FileHash $plan -Algorithm SHA256).Hash.ToLower()); $bytes = 0
function save($name, $b, $x0, $y0, $to) { $b.Save((Join-Path $to "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
  $script:bytes += 4 * $b.Width * $b.Height; $script:lines += "$name $x0 $y0 $($b.Width) $($b.Height)"; $b.Dispose() }
foreach ($l in Get-Content $plan) { $f = $l -split ' '; $x0 = 0; $y0 = 0
  if ($f[0] -eq 'outline') { $outline[$f[1]] = @($f[2], [double[]]$f[3..($f.Count - 1)]) }
  elseif ($f[0] -eq 'ripple') { $o = $outline[$f[2]]   # name panel inset height
    $b = [RingGlass]::RidgeAlong([RingGlass]::Outline($o[0], $o[1], [double]$f[3]), $true, [double]$f[4], $HALF, 1, $o[0], $o[1], [ref]$x0, [ref]$y0); save $f[1] $b $x0 $y0 $dir }
  elseif ($f[0] -eq 'front') { $o = $outline[$f[2]]; $v = [double[]]$f[3..10]   # x1 y1 kx ky x2 y2 height alpha
    $b = [RingGlass]::RidgeAlong([RingGlass]::Quad($v[0], $v[1], $v[2], $v[3], $v[4], $v[5], 48), $false, $v[6], $HALF, $v[7], $o[0], $o[1], [ref]$x0, [ref]$y0); save $f[1] $b $x0 $y0 $frames }
  elseif ($f[0] -notin 'run', 'bulge') { throw "fronts.txt: what is '$l'?" } }   # (a frame's bulges are pack_waves.py's)
$lines | Set-Content -Encoding ascii (Join-Path $PSScriptRoot 'fronts_frames.txt')
"{0} pictures, {1:N0} bytes unpacked" -f ($lines.Count - 1), $bytes
