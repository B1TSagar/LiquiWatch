# Usage: perl tools/jelly/jelly.pl < tools/ring/expedition_source.xml | perl tools/ring/ring.pl > watchface.xml
#   (the Expedition face as it was, before ring.pl made it over: see ring.pl; PLAN=1 plans the waves)
# The glass as liquid in a flexible skin. At each tick while the seconds hand crosses a glass element,
# a bulge of the glass's own edge sets off from where the hand crosses it toward where the hand will leave,
# getting smaller and fading as it nears the end, a wave of risen glass between each two; as the hand leaves
# a dial, two run back round to where it came in, a wave between them. A tap on a panel sends a ring
# out from its centre. The bulge pictures are the glass itself risen (bulges.ps1). Any bulges the face
# already has are taken out first, so this can be run on the face as it is.
use strict; use warnings; local $/; my $s = <STDIN>;
my $PI = 4 * atan2(1, 1);
sub fmt { my $v = sprintf "%.3f", $_[0]; $v =~ s/\.?0+$//; $v = 0 if $v eq '-0'; $v }
# Groups and pictures take whole-pixel positions only (a fraction drops the element on the watch).
sub ip { my $v = shift; int($v + ($v < 0 ? -0.5 : 0.5)) }
use POSIX ();
use List::Util qw(sum0);
sub rad_of { $_[0] * 4 * atan2(1, 1) / 180 }
sub clampn { my ($v, $lo, $hi) = @_; $v < $lo ? $lo : $v > $hi ? $hi : $v }
sub acosd { atan2(sqrt(1 - $_[0] * $_[0]), $_[0]) * 45 / atan2(1, 1) }

# ---- out with the old ---------------------------------------------------------------------------
for my $name (qw(jelly_heart jelly_steps jelly_readout jelly_pill jelly_window jelly_beads pub_pg_heart pub_pg_steps pub_pg_readout pub_pg_pill)) {
  my $p = index($s, "<Group name=\"$name\""); next if $p < 0;
  pos($s) = $p; my $d = 0;
  while ($s =~ /<(\/?)Group\b[^>]*?(\/?)>/g) { next if $2; $d += $1 ? -1 : 1; if ($d == 0) { substr($s, $p, $+[0] - $p) = ''; last } } }

# ---- curves: the even ease round the edge, and the fade -----------------------------------------
my @EASE = (0.4, 0, 0.6, 1);
sub bez { my ($u, $p1, $p2) = @_; 3 * (1 - $u) ** 2 * $u * $p1 + 3 * (1 - $u) * $u ** 2 * $p2 + $u ** 3 }
# An alpha transform rests at this when its bulge has run: -1, clamped to nothing (a fraction stops the
# face drawing at all). The fade overshoots it the other way, far enough to show full.
my $ON = -1;
my $FADE = '0 ' . fmt(900 / $ON) . ' 0.6 0';   # full about half the way, then fading slowly to nothing
my $FPS = 16;   # the snap backs' and the tap rings' frame rate: while one runs the watch works the whole face out each frame (12 looked jerky)
my $AMB = '<Variant mode="AMBIENT" target="alpha" value="0" />';
sub anim { my ($d, $c, $f) = @_; "<Animation duration=\"$d\" interpolation=\"CUBIC_BEZIER\" controls=\"$c\" fps=\"" . ($f // $FPS) . "\" />" }

# ---- pictures: size and the reference point (circle centre / edge point) in them, as bulges.ps1 prints
my %PIC = (
  dial      => [qw(exp_dial_bulge 41 18 21 49)],
  pill_cap  => [qw(exp_pill_bulge_cap 40 16 20 31)],
  pill_top  => [qw(exp_pill_bulge_top 43 16 21.2 5.5)],
  pill_bot  => [qw(exp_pill_bulge_bot 42 15 21.2 5.5)],
  win_cap   => [qw(exp_window_bulge_cap 40 16 20.5 37.5)],
  win_line  => [qw(exp_window_bulge_line 42 15 21.2 5.5)],
);

# ---- when -------------------------------------------------------------------------------------
# a tap turns the panel's page, so its odd or even (published from its slot, below) flips
sub tap { my $n = shift; ("([REFERENCE.pg_$n] &gt; 1.5)", "(([REFERENCE.pg_$n] &gt; 0.5) &amp;&amp; ([REFERENCE.pg_$n] &lt; 1.5))") }
my %when;
# how long a run takes: a snap back's 1.5 s, a tap's 1 s
my $D = 1.5; my $DT = 1;

# ---- the elements -------------------------------------------------------------------------------
my %add;
# (a tap on a dial or the date pill sends a ring out from its centre: see the waves below; the day and night
# window isn't tapped)
$add{window} = '';

# ---- the waves: a ridge of risen glass between two bulges, running with them -----------------------
# The face can't draw a curve that changes as it goes, so each run is an animated picture, a frame each 1/fps s:
# fronts.ps1 draws its waves from what PLAN=1 writes to fronts.txt, and pack_waves.py draws its two bulges over
# them, on the picture's own clock (moved by the face on their own, they changed on another eighth of a second
# from the wave's, now before it and now after), and packs them.
# CSS-style cubic bezier progress, as the watch eases an animation: the curve's y where its x is tau
sub curve { my ($tau, $x1, $y1, $x2, $y2) = @_; $tau = clampn($tau, 0, 1); my ($lo, $hi) = (0, 1);
  for (1 .. 60) { my $m = ($lo + $hi) / 2; if (bez($m, $x1, $x2) < $tau) { $lo = $m } else { $hi = $m } }
  bez(($lo + $hi) / 2, $y1, $y2) }
# A bulge's edge g: [arc => pic, cx, cy, r] (its place an angle, clockwise from 12) or [line => pic, y, turn]
# (its place an x; turn 180: facing down). Where it is at a place on its edge:
sub where { my ($g, $at) = @_; $g->[0] eq 'arc' ? [$g->[2] + $g->[4] * sin(rad_of($at)), $g->[3] - $g->[4] * cos(rad_of($at))] : [$at, $g->[2]] }
# where a bulge is, t seconds into its run of dur seconds from `from` to `to`
sub bulge_at { my ($g, $from, $to, $t, $dur) = @_; where($g, $from + ($to - $from) * curve($t / $dur, @EASE)) }
# A way round a glass's edge: its stretches one after another, [arc => pic, cx, cy, r, from, to] or
# [line => pic, y, x_from, x_to, turn], run as one in dur s on the ease. Its edge and place t s into it:
sub seg_len { my $g = shift; $g->[0] eq 'arc' ? abs($g->[6] - $g->[5]) * $PI / 180 * $g->[4] : abs($g->[4] - $g->[3]) }
sub way_at { my ($way, $t, $dur) = @_; my $L = 0; $L += seg_len($_) for @$way; $L *= curve($t / $dur, @EASE);
  for my $i (0 .. $#$way) { my $g = $way->[$i]; my $l = seg_len($g);
    if ($L <= $l || $i == $#$way) { my $u = $l > 0 ? clampn($L / $l, 0, 1) : 1;
      return $g->[0] eq 'arc' ? ([@$g[0 .. 4]], $g->[5] + ($g->[6] - $g->[5]) * $u) : ([line => @$g[1, 2, 5]], $g->[3] + ($g->[4] - $g->[3]) * $u) }
    $L -= $l } }
# where a bulge's picture lies at a place on its edge, sunk to s of its height (toward the edge) and sx of its width
# (about its middle), as a group would place it: the map from the picture onto the face, [a, b, c, d, e, f] for
# x' = a x + b y + c, y' = d x + e y + f
sub pic_map { my ($g, $at, $s, $sx) = @_; $sx //= 1; my (undef, $pw, $ph, $rx, $ry) = @{ $PIC{ $g->[1] } };
  if ($g->[0] eq 'arc') {   # turned round the centre (rx, ry in it), sunk toward the edge's row in it (ry - r)
    my ($cx, $cy, $r) = @$g[2 .. 4]; my ($co, $si) = (cos(rad_of($at)), sin(rad_of($at))); my $v = ($ry - $r) * (1 - $s) - $ry;
    return [$sx * $co, -$s * $si, $cx - $rx * $sx * $co - $v * $si, $sx * $si, $s * $co, $cy - $rx * $sx * $si + $v * $co] }
  my ($y, $turn) = @$g[2, 3]; my ($rxx, $ryy) = $turn ? ($pw - $rx, $ph - $ry) : ($rx, $ry);   # (turned in its own box)
  $turn ? [-$sx, 0, $at + $sx * ($pw - $rxx), 0, -$s, $y + $s * ($ph - $ryy)] : [$sx, 0, $at - $sx * $rxx, 0, $s, $y - $s * $ryy] }
sub bulge_map { my ($g, $from, $to, $t, $dur, $s, $sx) = @_; pic_map($g, $from + ($to - $from) * curve($t / $dur, @EASE), $s, $sx) }
# each panel's glass as the bulges run round it, and how far a point is outside it (negative inside)
my %OUTLINE = (heart => [circle => 125, 225, 43.5], steps => [circle => 325, 225, 43.5], readout => [circle => 225, 325, 43.5],
               pill => [stadium => 178, 272, 135, 25.5], window => [dome => 225, 96.5, 32]);
sub field { my ($p, $x, $y) = @_; my ($k, @o) = @{ $OUTLINE{$p} };
  return sqrt(($x - $o[0]) ** 2 + ($y - $o[1]) ** 2) - $o[2] if $k eq 'circle';
  if ($k eq 'stadium') { my $px = clampn($x, $o[0], $o[1]); return sqrt(($x - $px) ** 2 + ($y - $o[2]) ** 2) - $o[3] }
  my ($d, $flat) = (sqrt(($x - $o[0]) ** 2 + ($y - $o[1]) ** 2) - $o[2], $y - $o[1]); $d > $flat ? $d : $flat }
sub dist { sqrt(($_[0][0] - $_[1][0]) ** 2 + ($_[0][1] - $_[1][1]) ** 2) }
sub fmt3 { sprintf '%.3f', $_[0] }
# The wave between bulges a and b: the quadratic from one to the other whose middle stands off the midpoint
# of the line between them, toward t (the midpoint of where they're heading), by 0.22 of their distance
# apart, or by 0.75 of the room to the glass's edge that way if that's less. Undef while they're under 1 px apart.
sub wave_curve { my ($p, $a, $b, $t) = @_; my @m = (($a->[0] + $b->[0]) / 2, ($a->[1] + $b->[1]) / 2);
  my $c = dist($a, $b); return undef if $c < 1;
  my @n = (-($b->[1] - $a->[1]) / $c, ($b->[0] - $a->[0]) / $c); my $side = ($t->[0] - $m[0]) * $n[0] + ($t->[1] - $m[1]) * $n[1];
  @n = map { -$_ } @n if $side < 0;
  my $room = 0; $room += 0.25 while $room < 200 && field($p, $m[0] + $room * $n[0], $m[1] + $room * $n[1]) < 0;
  my $most = 0.75 * $room; my $sag = abs($side) < 1e-9 ? 0 : 0.22 * $c < $most ? 0.22 * $c : $most;
  # and less where that would still cross the glass's edge (a straight edge has no curve to give it room)
  my $k; for (1 .. 40) { $k = [$m[0] + 2 * $sag * $n[0], $m[1] + 2 * $sag * $n[1]];
    last unless grep { my $u = $_ / 32; field($p, map { (1 - $u) ** 2 * $a->[$_] + 2 * $u * (1 - $u) * $k->[$_] + $u * $u * $b->[$_] } 0, 1) > 0 } 1 .. 31;
    $sag *= 0.8 }
  { m => \@m, k => $k } }
my @RUNS;   # every run: { prefix, panel, start, fps, open, frames => [ { name, line (its wave's, if drawn), bulges => [lines] } ] }
my $ALONE = 0;   # runs whose bulges go alone (under 3 waves, or a tick with only one bulge)
my $FRONT_H = 4.5;   # how high the waves (and the tap rings) stand, px
# A run's frames, one each 1/fps s from the first shown until tmax or it has faded (pos: t -> its bulges, one or
# two; look: t -> [height, alpha]; maps: t -> each bulge's [picture, bulge_map]). Each frame draws the bulges, and
# the wave between two but where they are under 1 px apart, or where its middle can't stand 1 px inside the glass
# (a sliver along the rim), at the run's start or end. A run with under 3 waves, or whose wave would come and go,
# draws its bulges alone (a crossing's last tick, whose bulges start together at the rim). Every wave drawn stays in
# its glass and bows forward. Its picture shows from its start second for as many seconds as it lasts (open).
sub add_run { my ($panel, $prefix, $start, $fps, $tmax, $pos, $target, $look, $maps) = @_; my (@fr, $kept); $kept = '';
  for (my $k = 0; $k / $fps < $tmax; $k++) { my $t = $k / $fps; my ($h, $al) = @{ $look->($t) };
    if ($al < 0.03) { last if @fr; next }   # not shown yet (its bulges' fade starts at nothing), or faded (too faint to draw)
    my @end = @{ $pos->($t) }; my $w = @end == 2 ? wave_curve($panel, @end, $target) : undef; my ($a, $b) = @end;
    my $name = sprintf '%s_f%02d', $prefix, $k; my $keep = 0;
    if ($w) {
      my @at = map { my $u = $_ / 64; [map { (1 - $u) ** 2 * $a->[$_] + 2 * $u * (1 - $u) * $w->{k}[$_] + $u * $u * $b->[$_] } 0, 1] } 0 .. 64;
      for (@at) { my $f = field($panel, @$_); die sprintf("%s: the wave leaves its glass by %.2f px\n", $name, $f) if $f > 0.25 }
      die "$name: the wave bows away from where its bulges are heading\n"
        if ($w->{k}[0] - $w->{m}[0]) * ($target->[0] - $w->{m}[0]) + ($w->{k}[1] - $w->{m}[1]) * ($target->[1] - $w->{m}[1]) < -1e-9;
      $keep = field($panel, @{ $at[32] }) <= -1.5 }   # (its middle a pixel and a half in: at one, the glass's inner edge can cut it all away)
    $kept .= $keep ? 1 : 0;
    push @fr, { name => $name, line => $keep ? join(' ', 'front', $name, $panel, map(fmt3($_), @$a, @{ $w->{k} }, @$b, $h, $al)) : undef,
      bulges => [map { join ' ', 'bulge', $name, $PIC{ $_->[0] }[0], (map { sprintf '%.4f', $_ } @{ $_->[1] }), fmt3($al) } @{ $maps->($t) }] } }
  if (($kept =~ tr/1//) < 3 || $kept =~ /10+1/) { $_->{line} = undef for @fr; $ALONE++ }
  push @RUNS, { prefix => $prefix, panel => $panel, start => $start, fps => $fps, open => POSIX::ceil($tmax), frames => \@fr } }
# what PLAN=1 writes: the panels' outlines, the tap rings (inset, height), and each run (its start second and frame
# rate) with its frames: each frame's wave, if drawn, then its bulges (picture, map onto the face, alpha)
sub fronts_text { join '', (map { my ($k, @o) = @{ $OUTLINE{$_} }; join(' ', 'outline', $_, $k, map(fmt3($_), @o)) . "\n" } qw(heart steps readout pill window)),
  "ripple exp_ripple_dial heart 7 $FRONT_H\n", "ripple exp_ripple_pill pill 7 $FRONT_H\n",
  map { my $r = $_; ("run $r->{prefix} $r->{panel} $r->{start} $r->{fps}\n", map { ($_->{line} ? "$_->{line}\n" : ()), map { "$_\n" } @{ $_->{bulges} } } @{ $r->{frames} }) } @RUNS }

# ---- pulsing with the hand (the quartz face, its hand ticking once a second) -------------------
# At each tick while the seconds hand crosses a glass element, a bulge sets off from where the hand's
# centre line meets the glass's edge toward the end of the glass (where the hand leaves it), slowly,
# getting smaller and fading to nothing a second and a half after the tick: as if each tick pushed the glass on.
# As the hand leaves a glass, two bulges run back round from where it left to where it came in.
{
  # a pulse's run to the end of its glass (s: how fast it goes), its life (s: shrinking, full about half the way and
  # fading to nothing by then, near the end of its glass), and its frame rate
  my $RUN = 2; my $SINK = 1.5; my $PFPS = 8;
  # Where the hand's line, at angle th, meets a circle round (cx, cy), radius r: at th + 90 +- acos(d / r)
  # round the circle's centre (clockwise from 12), d the line's distance from it; and whether it does.
  my $crossn = sub { my ($cx, $cy, $r, $th) = @_; my ($dx, $dy) = ($cx - 225, $cy - 225);
    my $rel = rad_of($th - atan2($dx, -$dy) * 180 / $PI); my $d = sqrt($dx * $dx + $dy * $dy) * sin($rel);
    my $ac = acosd(clampn($d / $r, -1, 1)); ($th + 90 + $ac, $th + 90 - $ac, abs($d) < $r && cos($rel) > 0) };
  my $th12 = sub { my $t = $_[0] + 180; $t - 360 * POSIX::floor($t / 360) - 180 };   # near 12 o'clock, counted across 0
  # The longest run of seconds in the minute a crossing shows (num: the hand's angle -> (value, shown)).
  my $run = sub { my $num = shift; my @at = map { [ $num->(6 * $_ + 0.26) ] } 0 .. 59; my ($best, $len) = (undef, 0);
    for my $k (0 .. 59) { next unless $at[$k][1] && !$at[($k + 59) % 60][1]; my $l = 0; $l++ while $l < 60 && $at[($k + $l) % 60][1];
      ($best, $len) = ($k, $l) if $l > $len }
    ($best, $len, \@at) };
  # Where a run along an arc ends: the first of the angles (round the circle) past where the crossing
  # last shows, the way it was going, in the crossing's own count.
  my $arc_end = sub { my ($num, @c) = @_; my ($best, $len, $at) = $run->($num); return undef unless defined $best && $len > 1;
    my ($last, $prev) = map { $at->[($best + $len - $_) % 60][0] } 1, 2; my $up = $last >= $prev; my $end;
    for my $c (@c) { my $k = $c + 360 * ($up ? POSIX::ceil(($last - $c) / 360) : POSIX::floor(($last - $c) / 360));
      $end = $k if !defined $end || abs($k - $last) < abs($end - $last) }
    $end };
  # The pulses: a new one each tick, each its own picture (below). Each starts where the hand crosses the edge at
  # its tick, and runs toward the end, so it always starts at the hand. The hand stands only at whole seconds, so
  # where it crosses the edge is worked out here, for the seconds it does.
  #   at: the hand's angle (degrees) -> (where it crosses the edge, whether it does)
  my (@specs, @SNAPS);
  # A glass's snap back: as the hand leaves it (at the angle `leaves`), two bulges run back from where it left to
  # where it came in, one each way round its edge (the two ways), and fade, as water does that has hit the far side
  # of its glass; a tick after the hand's line has left it (drawn in its wave's picture: below).
  my $snap_back = sub { my ($n, $leaves, @ways) = @_; my $snap = int($leaves / 6 + 2) % 60;
    push @SNAPS, [$n, $snap, @ways]; $when{$n} = "-/$snap" };
  my $pulse = sub { my ($panel, $geom, $at, $end) = @_;
    push @specs, [$geom, $end, { map { my ($v, $on) = $at->(6 * $_ + 0.26); $on ? ($_ => $v) : () } 0 .. 59 }, $panel] };
  # where the hand's line meets a straight edge at height y, between x0 and x1 (turn: 180 facing down);
  # held within the face, as the hand nears 3 or 9 o'clock and the line meets the edge far off
  # (the edges' runs go the hand's way, to their right-hand end)
  my $line = sub { my ($panel, $pic, $y, $x0, $x1, $turn) = @_;
    $pulse->($panel, [line => $pic, $y, $turn], sub { my $t = rad_of($_[0]); my $x = clampn(225 + (225 - $y) * sin($t) / clampn(cos($t), 0.05, 1), 0, 450);
      ($x, cos($t) > 0.05 && $x >= $x0 && $x <= $x1) }, $x1) };
  # The dials: the pulses run round to where the hand will leave; as it leaves, two bulges run back
  # round from there to where it came in, both ways, and fade, as water does that has hit the far
  # side of its glass.
  for (['heart', 125, 225], ['steps', 325, 225], ['readout', 225, 325]) { my ($n, $cx, $cy) = @$_; my $r = 43.5;
    my ($dx, $dy) = ($cx - 225, $cy - 225); my $phi = atan2($dx, -$dy) * 180 / $PI;
    $phi += 360 if $phi < 0;   # (as the hand counts its angle: 0 to 360)
    my $beta = atan2($r, sqrt($dx * $dx + $dy * $dy - $r * $r)) * 180 / $PI;   # the hand's half-turn across it
    # (In each crossing's own count: the near one turns down from in + 360 to out, the far one up from in to out.)
    my ($in, $out) = ($phi - $beta - 90, $phi + $beta + 90);
    my @lim = ([$out, $in + 360], [$in, $out]);
    for my $k (0, 1) {
      $pulse->($n, [arc => 'dial', $cx, $cy, $r], sub { my @c = $crossn->($cx, $cy, $r, $_[0]); (clampn($c[$k], @{ $lim[$k] }), $c[2]) }, $out) }
    # its snap back, round from the exit to where it came in, the near way and the far
    my $o = $out - 360 * int($out / 360); my $i = $in - 360 * int($in / 360);
    my $up = $i - $o; $up += 360 while $up <= 0;
    $snap_back->($n, $phi + $beta, [[arc => 'dial', $cx, $cy, $r, $o, $o + $up]], [[arc => 'dial', $cx, $cy, $r, $o + 360, $o + $up]]) }
  # Pulses where the hand crosses a round piece of glass (the pill's ends, the window's dome), shown on
  # its own part of the circle (vis: the angle -> shown), running to that part's end (ends: the angles
  # round the circle where the part ends, or the hand leaves it).
  my $round = sub { my ($panel, $pic, $cx, $cy, $r, $vis, @ends) = @_;
    for my $k (0, 1) {
      my $at = sub { my @c = $crossn->($cx, $cy, $r, $th12->($_[0])); ($c[$k], $c[2] && $vis->($c[$k])) };
      my $end = $arc_end->($at, @ends);
      $pulse->($panel, [arc => $pic, $cx, $cy, $r], $at, $end) if defined $end } };
  # the pill: its straight edges, or its round ends where the hand crosses one (each end's outer half)
  { my $tan = do { my ($dx, $dy) = (272 - 225, 135 - 225); my $D2 = $dx * $dx + $dy * $dy;   # where the hand leaves the right end
      atan2($dx, -$dy) * 180 / $PI + atan2(25.5, sqrt($D2 - 25.5 * 25.5)) * 180 / $PI + 90 };
    $round->('pill', 'pill_cap', 178, 135, 25.5, sub { sin(rad_of($_[0])) < 0 }, 0, 180);
    $round->('pill', 'pill_cap', 272, 135, 25.5, sub { sin(rad_of($_[0])) > 0 }, $tan);
    # its snap back, from where the hand left its right end over the top and along the bottom to where it came in
    # at its left end (where it left, mirrored: the pill and the hand's way across it are even about x 225)
    $snap_back->('pill', $tan - 90, [[arc => 'pill_cap', 272, 135, 25.5, $tan, 0], [line => 'pill_top', 109.5, 272, 178, 0], [arc => 'pill_cap', 178, 135, 25.5, 360, 360 - $tan]],
      [[arc => 'pill_cap', 272, 135, 25.5, $tan, 180], [line => 'pill_bot', 160.5, 272, 178, 180], [arc => 'pill_cap', 178, 135, 25.5, 180, 360 - $tan]]) }
  $line->('pill', 'pill_bot', 160.5, 178, 272, 180); $line->('pill', 'pill_top', 109.5, 178, 272, 0);
  # the window: its dome (the upper half of its circle) and its flat base
  $round->('window', 'win_cap', 225, 96.5, 32, sub { cos(rad_of($_[0])) > 0 }, 90);
  $line->('window', 'win_line', 96.5, 193, 257, 180);
  # its snap back, from the corner where the hand leaves it over the dome and along the base to the one where it came in
  $snap_back->('window', atan2(257 - 225, 225 - 96.5) * 180 / $PI, [[arc => 'win_cap', 225, 96.5, 32, 90, -90]], [[line => 'win_line', 96.5, 257, 193, 180]]);
  # The runs: each tick's bulges round a panel (and a wave between two), and each glass's snap back (two, and a
  # wave between them), each run an animated picture of its own; the waves fade, and the pulses' shrink, as their
  # bulges do.
  my %by; for my $i (0 .. $#specs) { my ($v, $panel) = @{ $specs[$i] }[2, 3]; push @{ $by{$panel}{$_} }, $i for keys %$v }
  my $sunk = sub { 1 - 0.8 * curve($_[0] / $SINK, 0.5, 0, 1, 1) };   # a pulse's bulge's size (and its wave's height) as it shrinks
  for my $panel (qw(heart steps readout pill window)) {
    for my $S (sort { $a <=> $b } keys %{ $by{$panel} }) { my @ix = @{ $by{$panel}{$S} };
      die "$panel: " . @ix . " bulges at second $S\n" if @ix > 2;
      my @way = map { my ($geom, $end, $v) = @{ $specs[$_] }; [$geom, $v->{$S}, $end] } @ix;
      my @dest = map { bulge_at(@$_, $RUN, $RUN) } @way;
      add_run($panel, sprintf('exp_front_%s_s%02d', $panel, $S), $S, $PFPS, $SINK,
        sub { [map { bulge_at(@$_, $_[0], $RUN) } @way] },
        [(sum0(map { $_->[0] } @dest)) / @dest, (sum0(map { $_->[1] } @dest)) / @dest],
        sub { [$FRONT_H * $sunk->($_[0]), clampn(-curve($_[0] / $SINK, 0, 900 / $ON, 0.6, 0), 0, 255) / 255] },
        sub { my $t = shift; [map { [$_->[0][1], bulge_map(@$_, $t, $RUN, ($sunk->($t)) x 2)] } @way] }) }
    for (grep { $_->[0] eq $panel } @SNAPS) { my ($n, $snap, @ways) = @$_;
      my $on = sub { my $t = shift; map { [way_at($_, $t, $D)] } @ways };   # each bulge's edge and place on it
      my $pos = sub { [map { where(@$_) } $on->($_[0])] };
      add_run($n, "exp_front_${n}_snap", $snap, $FPS, $D, $pos, where(way_at($ways[0], $D, $D)),
        sub { [$FRONT_H, clampn(-curve($_[0] / $D, 0, 900 / $ON, 0.6, 0), 0, 255) / 255] },
        sub { [map { [$_->[0][1], pic_map(@$_, 1)] } $on->($_[0])] }) } }
  if ($ENV{PLAN}) {
    (my $dir = __FILE__) =~ s{[^/\\]*$}{}; open my $f, '>:raw', "${dir}fronts.txt" or die "can't write ${dir}fronts.txt: $!\n"; print $f fronts_text(); close $f;
    my ($frames, $waves) = (0, 0); for (@RUNS) { $frames += @{ $_->{frames} }; $waves += grep { $_->{line} } @{ $_->{frames} } }
    printf STDERR "%d runs (%d with their bulges alone), %d frames (%d with a wave)\n", scalar @RUNS, $ALONE, $frames, $waves; exit 0 }
}

# ---- the waves and the tap rings, into each panel ----------------------------------------------------
# Each run is one animated picture, its wave and its two bulges (pack_waves.py packs its frames, one each 1/fps s
# from the run's start), there only from its start second until it has played (a Condition on the second), and
# played as that Condition brings it in: so it starts as the hand ticks. (A Group per frame, each shown in its own
# slice of the run, tripled the face's Groups, and the renderer hung on it after a cold start.)
my %WAVE;   # each run's animated picture's box, from waves.txt
sub gate_expr { "((([SECOND] + " . (60 - $_[0]) . ") % 60) &lt;= $_[1])" }   # S to S + open
sub wave_name { (my $n = $_[0]) =~ s/^exp_front_/exp_wave_/; $n }
sub wave_xml { my ($r) = @_; my ($x, $y, $w, $h) = @{ $WAVE{ wave_name($r->{prefix}) } };
  '<Condition><Expressions><Expression name="c0">' . gate_expr($r->{start}, $r->{open}) . '</Expression></Expressions><Compare expression="c0">'
  . "<PartAnimatedImage x=\"$x\" y=\"$y\" width=\"$w\" height=\"$h\">$AMB"
  . '<AnimationController play="ON_VISIBLE" loopCount="1" beforePlaying="HIDE" afterPlaying="HIDE" />'
  . '<AnimatedImage resource="' . wave_name($r->{prefix}) . '" format="WEBP" /></PartAnimatedImage></Compare></Condition>' }
# A tap on a panel sends a ring out from its centre, fading by the time it gets to the edge: two copies, the
# page's odd and even, as today's tap bulges took turns. %FRAME: the rings' boxes, from fronts_frames.txt.
my %FRAME;
sub ripple_xml { my ($n, $cx, $cy, $pic) = @_; my ($fx, $fy, $w, $h) = @{ $FRAME{$pic} };
  # (drawn round the heart dial's centre, or the pill's: see fronts_text)
  my ($dx, $dy) = $pic eq 'exp_ripple_pill' ? (($OUTLINE{pill}[1] + $OUTLINE{pill}[2]) / 2, $OUTLINE{pill}[3]) : @{ $OUTLINE{heart} }[1, 2];
  my ($x, $y) = ($fx + $cx - $dx, $fy + $cy - $dy); my $piv = 'pivotX="' . fmt(($cx - $x) / $w) . '" pivotY="' . fmt(($cy - $y) / $h) . '"';
  join '', map { "<Group x=\"$x\" y=\"$y\" width=\"$w\" height=\"$h\" $piv alpha=\"0\">$AMB<Transform target=\"alpha\" value=\"($_ ? $ON : 0)\">" . anim($DT, $FADE) . '</Transform>'
    . join('', map { "<Transform target=\"$_->[0]\" value=\"($_->[1] ? 1 : 0.1)\">" . anim($DT, '0.2 0.6 0.4 1') . '</Transform>' } [scaleX => $_], [scaleY => $_])
    . "<PartImage x=\"0\" y=\"0\" width=\"$w\" height=\"$h\">$AMB<Image resource=\"$pic\" /></PartImage></Group>" } tap($n) }
# every element of a wave's or a ring's carries its ambient variant (an animated one without it hangs the renderer)
sub with_amb { my ($what, $xml) = @_; die "$what: an element without its ambient variant\n" if $xml =~ /<(?:Group|PartImage|PartAnimatedImage)\b[^>]*>(?!\Q$AMB\E)/; $xml }
{ # what fronts.ps1 drew and pack_waves.py packed must be from this plan, and their pictures as listed
  use Digest::SHA qw(sha256_hex);
  (my $dir = __FILE__) =~ s{[^/\\]*$}{}; my $pics = "${dir}../../watchfaces/src/expedition/res/drawable-nodpi/";
  my $read = sub { open my $f, '<:raw', "$dir$_[0]" or die "no $dir$_[0]: $_[1]\n"; my $all = <$f>; close $f; $all };
  my $plan = $read->('fronts.txt', 'run PLAN=1 jelly.pl, then fronts.ps1 and pack_waves.py');
  die "fronts.txt isn't this plan: re-run PLAN=1 jelly.pl, fronts.ps1 and pack_waves.py\n" unless $plan eq fronts_text();
  my $drawn = $read->('fronts_frames.txt', 'run fronts.ps1'); my @l = split /\r?\n/, $drawn;
  die "fronts_frames.txt wasn't drawn from this fronts.txt: re-run fronts.ps1 and pack_waves.py\n" unless shift(@l) eq 'sha256 ' . sha256_hex($plan);
  for (@l) { my ($n, @b) = split; $FRAME{$n} = \@b }
  my @w = split /\r?\n/, $read->('waves.txt', 'run pack_waves.py');
  my %art = map { (split / /)[2] => 1 } map { @{ $_->{bulges} } } map { @{ $_->{frames} } } @RUNS;   # the bulge pictures drawn in them
  my $art = join '', map { open my $p, '<:raw', "$pics$_.png" or die "no picture $pics$_.png: re-run bulges.ps1\n"; local $/; my $all = <$p>; close $p; $all } sort keys %art;
  die "waves.txt wasn't packed from this fronts_frames.txt and these bulges: re-run pack_waves.py\n" unless shift(@w) eq 'sha256 ' . sha256_hex($drawn . $art);
  for (@w) { my ($n, @b) = split; $WAVE{$n} = \@b }
  for my $n ('exp_ripple_dial', 'exp_ripple_pill') { my $b = $FRAME{$n} or die "$n isn't in fronts_frames.txt: re-run fronts.ps1\n";
    open my $p, '<:raw', "$pics$n.png" or die "no picture $pics$n.png: re-run fronts.ps1\n"; read $p, my $head, 24; close $p;
    my ($pw, $ph) = unpack 'N2', substr($head, 16, 8); die "$n is ${pw}x$ph, not $b->[2]x$b->[3]: re-run fronts.ps1\n" if $pw != $b->[2] || $ph != $b->[3] }
  for my $r (@RUNS) { my $n = wave_name($r->{prefix}); my $b = $WAVE{$n} or die "$n isn't in waves.txt: re-run pack_waves.py\n";
    open my $p, '<:raw', "$pics$n.webp" or die "no picture $pics$n.webp: re-run pack_waves.py\n"; read $p, my $head, 30; close $p;
    die "$n.webp isn't an animated WebP\n" unless substr($head, 0, 4) eq 'RIFF' && substr($head, 8, 8) eq 'WEBPVP8X';
    my ($pw, $ph) = map { 1 + unpack('V', substr($head, $_, 3) . "\0") } 24, 27;
    die "$n.webp is ${pw}x$ph, not $b->[2]x$b->[3]: re-run pack_waves.py\n" if $pw != $b->[2] || $ph != $b->[3];
    # its gate, as the watch works it out, every second of the minute: open from its start second for as long as it lasts
    for my $sec (0 .. 59) { (my $e = gate_expr($r->{start}, $r->{open})) =~ s/\[SECOND\]/$sec/g; $e =~ s/&lt;/</g; my $on = eval($e) ? 1 : 0; die "can't work out $e: $@" if $@;
      die "$n: its gate is wrong at second $sec\n" if $on != (($sec - $r->{start} + 60) % 60 <= $r->{open} ? 1 : 0) } } }
for my $p (qw(heart steps readout pill window)) {
  my %at = (heart => [125, 225], steps => [325, 225], readout => [225, 325], pill => [225, 135]);
  my $ring = $at{$p} ? with_amb("the ${p}'s tap ring", ripple_xml($p, @{ $at{$p} }, $p eq 'pill' ? 'exp_ripple_pill' : 'exp_ripple_dial')) : '';
  my $runs = join '', map { with_amb($_->{prefix}, wave_xml($_)) } grep { $_->{panel} eq $p } @RUNS;
  $add{$p} = $ring . $runs . ($add{$p} // '') }

# ---- into the face ------------------------------------------------------------------------------
# Where an element's glass ends: after its body picture and the Condition drawing its rim.
sub after_rim { my $body = shift; my $p = index($s, $body); die "no $body" if $p < 0;
  my $c = index($s, '<Condition>', $p); pos($s) = $c; my $d = 0;
  while ($s =~ /<(\/?)Condition>/g) { $d += $1 ? -1 : 1; return $+[0] if $d == 0 } die "unclosed" }
# what an insertion point sits in must be at the face's origin (the bulges are placed in its coordinates)
sub at_origin { my $p = shift; my $pre = substr($s, 0, $p); my @st;
  while ($pre =~ /<(\/?)(Group|PartImage|PartDraw|ComplicationSlot)\b([^>]*?)(\/?)>/g) { next if $4; if ($1) { pop @st } else { push @st, $3 } }
  for (@st) { my ($x) = /\bx="([^"]*)"/; my ($y) = /\by="([^"]*)"/; die "inserting inside an offset parent ($x,$y)" if ($x // 0) != 0 || ($y // 0) != 0 } }
my @ins;
for (['heart', '<PartImage x="73" y="173" width="104" height="104">'], ['steps', '<PartImage x="273" y="173" width="104" height="104">'],
     ['readout', '<PartImage x="173" y="273" width="104" height="104">'], ['pill', '<PartImage x="144" y="101" width="162" height="68">'],
     ['window', '<PartImage x="186" y="58" width="78" height="45">']) {
  my $p = after_rim($_->[1] . $AMB . '<Image resource="exp_' . ($_->[0] =~ /pill|window/ ? $_->[0] : 'dial') . '_body" />'); at_origin($p);
  push @ins, [$p, "<Group name=\"jelly_$_->[0]\" x=\"0\" y=\"0\" width=\"450\" height=\"450\">$add{$_->[0]}</Group>"] }
# each tapped panel publishes its page's odd or even (1 or 2) for the bulges outside its slot
for (['heart', 11], ['steps', 12], ['readout', 13], ['pill', 14]) { my ($n, $id) = @$_;
  my $q = index($s, "slotId=\"$id\""); my $c = index($s, '<Complication type="RANGED_VALUE">', $q); die "slot $id" if $q < 0 || $c < 0;
  push @ins, [$c + length('<Complication type="RANGED_VALUE">'), "<Group name=\"pub_pg_$n\" x=\"5\" y=\"5\" width=\"2\" height=\"2\" alpha=\"255\"><Transform target=\"alpha\" value=\"(1 + (round([COMPLICATION.RANGED_VALUE_VALUE]) % 2))\" /><Reference name=\"pg_$n\" source=\"alpha\" defaultValue=\"0\" /><PartDraw x=\"0\" y=\"0\" width=\"2\" height=\"2\"><Rectangle x=\"0\" y=\"0\" width=\"2\" height=\"2\"><Fill color=\"#01FFFFFF\" /></Rectangle></PartDraw></Group>"] }
for (sort { $b->[0] <=> $a->[0] } @ins) { substr($s, $_->[0], 0) = $_->[1] }

# ---- checks: what broke the face on the watch before, and pictures that no longer match %PIC ----
for my $e ($s =~ /(?:value="|<Expression name="[^"]*">)([^"<]*)/g) {
  my $open = () = $e =~ /\(/g; my $close = () = $e =~ /\)/g;
  die "brackets don't balance: $e\n" if $open != $close }
die "a fraction of a pixel (the watch drops the element): $1\n" if $s =~ /(<(?:Group|PartImage|PartDraw) [^>]*\b(?:x|y|width|height)="-?\d*\.\d+")/;
{ (my $dir = __FILE__) =~ s{[^/\\]*$}{}; $dir .= '../../watchfaces/src/expedition/res/drawable-nodpi/';
  for my $p (values %PIC) { my ($name, $w, $h) = @$p;
    open my $f, '<:raw', "$dir$name.png" or die "no picture $dir$name.png\n"; read $f, my $head, 24; close $f;
    my ($pw, $ph) = unpack 'N2', substr($head, 16, 8);
    die "$name is ${pw}x$ph, not ${w}x$h: copy what bulges.ps1 prints into %PIC\n" if $pw != $w || $ph != $h } }
print $s;
printf STDERR "snap backs (glass and second): %s\n", join(", ", map { "$_ $when{$_}" } sort keys %when);
