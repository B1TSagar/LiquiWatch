# Usage: perl tools/jelly/jelly.pl < tools/ring/expedition_source.xml | perl tools/ring/ring.pl > watchface.xml
#   (the Expedition face as it was, before ring.pl made it over: see ring.pl; STATIC=1 for a check)
# The glass as liquid in a flexible skin. At each tick while the seconds hand crosses a glass element,
# a bulge of the glass's own edge runs from where the hand crosses it to where the hand will leave,
# sinking and fading before it gets there; as the hand leaves a dial, two run back round to where it
# came in. A tap on a panel sends two from its top and two from its bottom, fading as they meet at
# the sides. The bulge pictures are the glass itself risen (bulges.ps1). Any bulges the face already
# has are taken out first, so this can be run on the face as it is. With STATIC=1 every stretch of the
# runs (the taps' and those back round a dial) is drawn still at its middle instead, to check on the
# watch that each sits on its edge.
use strict; use warnings; local $/; my $s = <STDIN>;
my $PI = 4 * atan2(1, 1);
sub fmt { my $v = sprintf "%.3f", $_[0]; $v =~ s/\.?0+$//; $v = 0 if $v eq '-0'; $v }
# Groups and pictures take whole-pixel positions only (a fraction drops the element on the watch).
sub ip { my $v = shift; int($v + ($v < 0 ? -0.5 : 0.5)) }
use POSIX ();
sub rad_of { $_[0] * 4 * atan2(1, 1) / 180 }
sub clampn { my ($v, $lo, $hi) = @_; $v < $lo ? $lo : $v > $hi ? $hi : $v }
sub acosd { atan2(sqrt(1 - $_[0] * $_[0]), $_[0]) * 45 / atan2(1, 1) }

# ---- out with the old ---------------------------------------------------------------------------
for my $name (qw(jelly_heart jelly_steps jelly_readout jelly_pill jelly_window jelly_beads pub_pg_heart pub_pg_steps pub_pg_readout pub_pg_pill)) {
  my $p = index($s, "<Group name=\"$name\""); next if $p < 0;
  pos($s) = $p; my $d = 0;
  while ($s =~ /<(\/?)Group\b[^>]*?(\/?)>/g) { next if $2; $d += $1 ? -1 : 1; if ($d == 0) { substr($s, $p, $+[0] - $p) = ''; last } } }

# ---- curves: the even ease round the edge, and the alpha curves (a value going 0 -> 1) ----------
my @EASE = (0.4, 0, 0.6, 1);
sub bez { my ($u, $p1, $p2) = @_; 3 * (1 - $u) ** 2 * $u * $p1 + 3 * (1 - $u) * $u ** 2 * $p2 + $u ** 3 }
sub ease_time { my $p = shift; return 0 if $p <= 0; return 1 if $p >= 1;   # when the ease reaches progress p
  my ($lo, $hi) = (0, 1); for (1 .. 60) { my $m = ($lo + $hi) / 2; if (bez($m, $EASE[1], $EASE[3]) < $p) { $lo = $m } else { $hi = $m } }
  bez(($lo + $hi) / 2, $EASE[0], $EASE[2]) }
# An alpha transform rests at this when its bulge has run: -1, clamped to nothing (a fraction stops the
# face drawing at all). The curves overshoot it the other way, far enough to show full.
my $ON = -1;
my $FADE = '0 ' . fmt(900 / $ON) . ' 0.6 0';   # full about half the way, then fading slowly to nothing
my $K = 3000 / $ON;               # steep enough that a window opens and shuts within a frame
sub until_curve { my $t = shift; join ' ', '0.333333', $K, '0.666667', fmt($K * (1 - 1 / $t)) }   # shown until t
sub from_curve  { my $t = shift; join ' ', '0.333333', -$K, '0.666667', fmt($K * (1 - $t) / $t) } # shown from t
my $FPS = 16;   # the bulges' frame rate: while one runs the watch works the whole face out each frame (12 looked jerky)
my $AMB = '<Variant mode="AMBIENT" target="alpha" value="0" />';
sub anim { my ($d, $c, $f) = @_; "<Animation duration=\"$d\" interpolation=\"CUBIC_BEZIER\" controls=\"$c\" fps=\"" . ($f // $FPS) . "\" />" }
sub xform { my ($target, $w, $a, $b, $d, $c) = @_; "<Transform target=\"$target\" value=\"($w ? " . fmt($b) . " : " . fmt($a) . ")\">" . anim($d, $c) . "</Transform>" }

# ---- pictures: size and the reference point (circle centre / edge point) in them, as bulges.ps1 prints
my %PIC = (
  dial      => [qw(exp_dial_bulge 41 18 21 49)],
  pill_cap  => [qw(exp_pill_bulge_cap 40 16 20 31)],
  pill_top  => [qw(exp_pill_bulge_top 43 16 21.2 5.5)],
  pill_bot  => [qw(exp_pill_bulge_bot 42 15 21.2 5.5)],
  win_cap   => [qw(exp_window_bulge_cap 40 16 20.5 37.5)],
  win_line  => [qw(exp_window_bulge_line 42 15 21.2 5.5)],
);
my $objects = 0;

# One stretch of a bulge's way, shown while the way's progress is in [p0, p1]: its motion is linear
# in the ease, so the same curve carries every stretch; outside its window it runs on unseen (a group
# doesn't cut off what lies outside it, so each stretch is shown only in its window).
#   arc:  [arc => pic, cx, cy, r, from, to]     angles clockwise from 12
#   line: [line => pic, y, x_from, x_to, turn]  turn 0 (facing up) or 180 (facing down)
sub seg_len { my $g = shift; $g->[0] eq 'arc' ? abs($g->[5] - $g->[6]) * $PI / 180 * $g->[4] : abs($g->[4] - $g->[3]) }
sub stretch { my ($w, $d, $g, $L0, $L1, $Lp) = @_; $objects++;
  my ($t0, $t1) = (ease_time($L0 / $Lp), ease_time($L1 / $Lp));
  my ($name, $pw, $ph, $rx, $ry) = @{ $PIC{ $g->[1] } };
  my $at = sub { my ($from, $to) = @_; ($from + ($to - $from) * (0 - $L0) / ($L1 - $L0), $from + ($to - $from) * ($Lp - $L0) / ($L1 - $L0)) };
  my $leaf_alpha = $t0 > 0.001 ? '<Transform target="alpha" value="(' . $w . " ? $ON : 0)\">" . anim($d, from_curve($t0)) . '</Transform>' : '';
  my $until = $t1 < 0.999 ? sub { my ($W, $H, $in) = @_; "<Group x=\"0\" y=\"0\" width=\"$W\" height=\"$H\" alpha=\"0\">$AMB<Transform target=\"alpha\" value=\"($w ? $ON : 0)\">" . anim($d, until_curve($t1)) . "</Transform>$in</Group>" } : sub { $_[2] };
  my $fade = sub { my ($x, $y, $W, $H, $in) = @_; "<Group x=\"" . ip($x) . "\" y=\"" . ip($y) . "\" width=\"$W\" height=\"$H\" alpha=\"0\">$AMB<Transform target=\"alpha\" value=\"($w ? $ON : 0)\">" . anim($d, $FADE) . "</Transform>$in</Group>" };
  if ($ENV{STATIC}) {
    if ($g->[0] eq 'arc') { my $S = 70; my $m = ($g->[5] + $g->[6]) / 2;
      return "<Group x=\"" . ip($g->[2] - $S) . "\" y=\"" . ip($g->[3] - $S) . "\" width=\"140\" height=\"140\" pivotX=\"0.5\" pivotY=\"0.5\" angle=\"" . fmt($m) . "\"><PartImage x=\"" . ip($S - $rx) . "\" y=\"" . ip($S - $ry) . "\" width=\"$pw\" height=\"$ph\"><Image resource=\"$name\" /></PartImage></Group>" }
    my ($rxx, $ryy) = $g->[5] ? ($pw - $rx, $ph - $ry) : ($rx, $ry); my $m = ($g->[3] + $g->[4]) / 2;
    return "<PartImage x=\"" . ip($m - $rxx) . "\" y=\"" . ip($g->[2] - $ryy) . "\" width=\"$pw\" height=\"$ph\"" . ($g->[5] ? " pivotX=\"0.5\" pivotY=\"0.5\" angle=\"$g->[5]\"" : '') . "><Image resource=\"$name\" /></PartImage>" }
  if ($g->[0] eq 'arc') {
    my ($cx, $cy) = @$g[2, 3];
    my $S = 1 + int(1 + (sort { $b <=> $a } map { sqrt(($_->[0] - $rx) ** 2 + ($_->[1] - $ry) ** 2) } [0, 0], [$pw, 0], [0, $ph], [$pw, $ph])[0]);
    my ($a0, $a1) = $at->(@$g[5, 6]);
    my $leaf = "<PartImage x=\"" . ip($S - $rx) . "\" y=\"" . ip($S - $ry) . "\" width=\"$pw\" height=\"$ph\"" . ($leaf_alpha ? ' alpha="0"' : '') . ">$AMB<Image resource=\"$name\" />$leaf_alpha</PartImage>";
    my $turn = "<Group x=\"0\" y=\"0\" width=\"" . 2 * $S . "\" height=\"" . 2 * $S . "\" pivotX=\"0.5\" pivotY=\"0.5\" angle=\"" . fmt($a0) . "\">" . xform('angle', $w, $a0, $a1, $d, join(' ', @EASE)) . "$leaf</Group>";
    return $fade->($cx - $S, $cy - $S, 2 * $S, 2 * $S, $until->(2 * $S, 2 * $S, $turn));
  }
  my ($ey, $xf, $xt, $turnDeg) = @$g[2 .. 5];
  my ($rxx, $ryy) = $turnDeg ? ($pw - $rx, $ph - $ry) : ($rx, $ry);
  my ($x0, $x1) = $at->($xf, $xt);
  my ($bx, $bw) = (ip((($xf < $xt) ? $xf : $xt) - $pw), abs($xt - $xf) + 2 * $pw);
  my ($by, $bh) = (ip($ey - $ph - 2), 2 * $ph + 4);
  my $leaf = "<PartImage x=\"0\" y=\"0\" width=\"$pw\" height=\"$ph\"" . ($turnDeg ? " pivotX=\"0.5\" pivotY=\"0.5\" angle=\"$turnDeg\"" : '') . ($leaf_alpha ? ' alpha="0"' : '') . ">$AMB<Image resource=\"$name\" />$leaf_alpha</PartImage>";
  my $move = "<Group x=\"" . ip($x0 - $rxx - $bx) . "\" y=\"" . ip($ey - $ryy - $by) . "\" width=\"$pw\" height=\"$ph\">" . xform('x', $w, $x0 - $rxx - $bx, $x1 - $rxx - $bx, $d, join(' ', @EASE)) . "$leaf</Group>";
  $fade->($bx, $by, $bw, $bh, $until->($bw, $bh, $move));
}
# A bulge's whole way: its stretches one after another.
sub way { my ($w, $d, @segs) = @_; my $Lp = 0; $Lp += seg_len($_) for @segs; my ($L, $out) = (0, '');
  for my $g (@segs) { my $l = seg_len($g); $out .= stretch($w, $d, $g, $L, $L + $l, $Lp); $L += $l } $out }
# A tap: bulges from the top and the bottom, two from each, fading as they meet at the sides.
sub ring_tap { my ($w, $d, $pic, $cx, $cy, $r) = @_;
  join '', map { way($w, $d, [arc => $pic, $cx, $cy, $r, $_, $_ + 90]) . way($w, $d, [arc => $pic, $cx, $cy, $r, $_, $_ - 90]) } 0, 180 }

# ---- when -------------------------------------------------------------------------------------
my $T = '[SECOND]';   # (the quartz face's whole seconds: compared with half seconds below, + 0.0417 changes nothing)
# Each run goes on a count that only goes up (a step each time it should run), in two copies, one
# taking the odd steps and one the even: so each step starts one copy's run while the other goes
# back unseen, at the same moment. (A run on a condition would go back on its own when the condition
# ended, more animation each time; with all the runs, the face was hardly ever still.)
sub pair { my $c = shift; ("(($c % 2) &gt; 0.5)", "(($c % 2) &lt; 0.5)") }
my $NOW = '(([HOUR_0_23] * 60) + [MINUTE])';
sub each_minute_at { my $sec = shift; "($NOW + (($T &gt;= " . ($sec - 0.5) . ') ? 1 : 0))' }
# a tap turns the panel's page, so its odd or even (published from its slot, below) flips
sub tap { my $n = shift; ("([REFERENCE.pg_$n] &gt; 1.5)", "(([REFERENCE.pg_$n] &gt; 0.5) &amp;&amp; ([REFERENCE.pg_$n] &lt; 1.5))") }
my %when;
# how long a run takes: the hand's 1.5 s, a tap's 1 s
my $D = 1.5; my $DT = 1;

# ---- the elements -------------------------------------------------------------------------------
my %add;
# the dials: a tap sends bulges round from the top and the bottom
for (['heart', 125, 225], ['steps', 325, 225], ['readout', 225, 325]) { my ($n, $cx, $cy) = @$_;
  $add{$n} = join '', map { ring_tap($_, $DT, 'dial', $cx, $cy, 43.5) } tap($n) }
# the date pill: caps round (178,135) and (272,135), r 25.5; edges at y 109.5 and 160.5
{ my ($L, $R, $cy, $r, $top, $bot) = (178, 272, 135, 25.5, 109.5, 160.5);
  # a tap: from the top's middle and the bottom's middle, two bulges from each, fading as they meet at
  # the ends; the outline measured from the top's middle, clockwise, twice round (so a way may cross
  # where it starts)
  my @loop = ([line => 'pill_top', $top, 225, $R, 0], [arc => 'pill_cap', $R, $cy, $r, 0, 180], [line => 'pill_bot', $bot, $R, $L, 180],
              [arc => 'pill_cap', $L, $cy, $r, 180, 360], [line => 'pill_top', $top, $L, 225, 0]);
  my $part = sub { my ($g, $f0, $f1) = @_; my @h = @$g;   # the stretch of a piece of outline between two of its fractions
    if ($h[0] eq 'arc') { @h[5, 6] = map { $g->[5] + ($g->[6] - $g->[5]) * $_ } $f0, $f1 } else { @h[3, 4] = map { $g->[3] + ($g->[4] - $g->[3]) * $_ } $f0, $f1 } \@h };
  my $along = sub { my ($s0, $s1) = @_; my ($c, @segs) = (0);   # the outline from s0 to s1, either way
    my ($lo, $hi) = $s0 < $s1 ? ($s0, $s1) : ($s1, $s0);
    for my $g (@loop, @loop) { my $l = seg_len($g); my ($u, $v) = (($lo > $c ? $lo : $c), ($hi < $c + $l ? $hi : $c + $l));
      push @segs, ($s1 > $s0 ? $part->($g, ($u - $c) / $l, ($v - $c) / $l) : $part->($g, ($v - $c) / $l, ($u - $c) / $l)) if $v - $u > 0.01; $c += $l }
    $s1 > $s0 ? @segs : reverse @segs };
  my $round = 0; $round += seg_len($_) for @loop;
  my $tap_pill = sub { my $w = shift;
    join '', map { my $b = $_ + $round; way($w, $DT, $along->($b, $b + $round / 4)) . way($w, $DT, $along->($b, $b - $round / 4)) } 0, $round / 2 };
  $add{pill} = join '', map { $tap_pill->($_) } tap('pill');
}
# (the day and night window, a dome round (225,96.5), r 32, on a flat edge at y 96.5, isn't tapped)
$add{window} = '';

# ---- pulsing with the hand (the quartz face, its hand ticking once a second) -------------------
# At each tick while the seconds hand crosses a glass element, a bulge runs from where the hand's
# centre line meets the glass's edge on to the end of the glass (where the hand leaves it), slowly,
# sinking and fading before it gets there: as if each tick pushed the glass on.
# As the hand leaves a dial, two bulges run back round from where it left to where it came in.
{
  my $RUN = 2; my $SINK = 1.5; my $PFPS = 8;   # a pulse's run, its fading and sinking (s), and its frame rate
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
  my $size = sub { my ($pw, $ph, $rx, $ry) = @_; 1 + int(1 + (sort { $b <=> $a } map { sqrt(($_->[0] - $rx) ** 2 + ($_->[1] - $ry) ** 2) } [0, 0], [$pw, 0], [0, $ph], [$pw, $ph])[0]) };
  # The pulses: a new one each tick, each running 2 s and going back unseen over 2 s, so four of them
  # take turns (by the second, in fours). Each sits, without moving there, where the hand crosses the
  # edge at the second its run starts (outer), and runs from there to the end (inner, turned or moved
  # on by the distance), so it always starts at the hand. A run started goes on to its end though
  # the hand leaves the glass meanwhile. The hand stands only at whole seconds, so where it crosses
  # the edge is worked out here, for the seconds each starts a run, and the face only looks it up (it
  # worked it out itself, with sin and acos, which made the pulses half of all the face works out).
  #   at: the hand's angle (degrees) -> (where it crosses the edge, whether it does)
  # The second a pulse's run starts, as the face works it out (its own, or going back, its next one's):
  my $starts = sub { my $p = shift; "((([SECOND] + 62) - (([SECOND] + " . (62 - $p) . ") % 4)) % 60)" };
  for my $p (0 .. 3) { for my $S (0 .. 59) { my $m = ($S + 4 - $p) % 4;   # (checked against what it's meant to be)
    die "pulse ${p}'s start at $S\n" if (($S + 62) - (($S + 62 - $p) % 4)) % 60 != ($S + ($m < 2 ? -$m : 4 - $m) + 60) % 60 } }
  # (Each pulse is noted here, where its glass is, and made at the end, once every pulse's runs are
  # known: see the waiting below.)
  my (@specs, @snaps);
  my $pulse = sub { my ($geom, $at, $end) = @_;
    # each copy's runs: the seconds they start (every fourth) that the hand crosses the edge, and where
    my @v = map { my $p = $_; +{ map { my ($v, $on) = $at->(6 * $_ + 0.26); $on ? ($_ => $v) : () } grep { $_ % 4 == $p } 0 .. 59 } } 0 .. 3;
    push @specs, [$geom, $end, \@v]; "\x01$#specs\x01" };
  # holds: a copy's run start => the second it goes back, for those that wait (below)
  my $make = sub { my ($geom, $end, $vs, $holds) = @_; my ($kind, $pic, @g) = @$geom; my ($name, $pw, $ph, $rx, $ry) = @{ $PIC{$pic} };
    join '', map { my $p = $_; my %v = %{ $vs->[$p] }; my %h = %{ $holds->[$p] };
      my $st = $starts->($p); my $m = "(([SECOND] + " . (4 - $p) . ') % 4)';   # 0 and 1 its run, 2 and 3 going back
      my $look = sub { my ($f, $else) = @_; my $e = fmt($else); $e = "(($st == $_) ? " . fmt($f->($v{$_})) . " : $e)" for sort { $b <=> $a } keys %v; $e };
      # waiting, after the run from s, until r (from s + 2): where that run left it
      my $waits = sub { my ($s, $r) = @_; "((([SECOND] + " . (60 - ($s + 2) % 60) . ") % 60) &lt; " . ($r - $s - 2) . ')' };
      my $held = sub { my ($f, $else) = @_; my $e = $else; $e = '(' . $waits->($_, $h{$_}) . ' ? ' . fmt($f->($v{$_})) . " : $e)" for sort { $b <=> $a } keys %h; $e };
      my $go = "(($m &lt; 1.5) &amp;&amp; (" . join(' || ', map { "($st == $_)" } sort { $a <=> $b } keys %v) . '))';
      my $on = %h ? "($go || " . join(' || ', map { $waits->($_, $h{$_}) } sort { $a <=> $b } keys %h) . ')' : $go;
      my $run = sub { "<Transform target=\"$_[0]\" value=\"" . $held->(sub { $end - $_[0] }, "(($m &lt; 1.5) ? " . $look->(sub { $end - $_[0] }, 0) . ' : 0)') . '">' . anim($RUN, join(' ', @EASE), $PFPS) . '</Transform>' };
      # it fades, and sinks into the edge: shrinking from the edge's line in the picture
      my $sink = "<Transform target=\"alpha\" value=\"($on ? $ON : 0)\">" . anim($SINK, $FADE, $PFPS) . '</Transform>'
               . "<Transform target=\"scaleY\" value=\"($on ? 0.2 : 1)\">" . anim($SINK, '0.5 0 1 1', $PFPS) . '</Transform>';
      !%v ? () : $kind eq 'arc' ? do { $objects++; my ($cx, $cy, $r) = @g; my $S = $size->($pw, $ph, $rx, $ry); my $box = "width=\"" . 2 * $S . "\" height=\"" . 2 * $S . "\" pivotX=\"0.5\" pivotY=\"0.5\"";
        "<Group x=\"" . ip($cx - $S) . "\" y=\"" . ip($cy - $S) . "\" $box angle=\"" . fmt($end) . "\">$AMB<Transform target=\"angle\" value=\"" . $held->(sub { $_[0] }, $look->(sub { $_[0] }, $end)) . '" />'
        . "<Group x=\"0\" y=\"0\" $box angle=\"0\">" . $run->('angle')
        . "<Group x=\"" . ip($S - $rx) . "\" y=\"" . ip($S - $ry) . "\" width=\"$pw\" height=\"$ph\" pivotX=\"0.5\" pivotY=\"" . fmt(($ry - $r) / $ph) . "\" alpha=\"0\">$AMB$sink"
        . "<PartImage x=\"0\" y=\"0\" width=\"$pw\" height=\"$ph\">$AMB<Image resource=\"$name\" /></PartImage></Group></Group></Group>" }
      : do { $objects++; my ($y, $turn) = @g; my ($rxx, $ryy) = $turn ? ($pw - $rx, $ph - $ry) : ($rx, $ry);
        "<Group x=\"" . ip($end - $rxx) . "\" y=\"" . ip($y - $ryy) . "\" width=\"$pw\" height=\"$ph\">$AMB<Transform target=\"x\" value=\"" . $held->(sub { $_[0] - $rxx }, $look->(sub { $_[0] - $rxx }, $end - $rxx)) . '" />'
        . "<Group x=\"0\" y=\"0\" width=\"$pw\" height=\"$ph\" pivotX=\"0.5\" pivotY=\"" . fmt($ryy / $ph) . "\" alpha=\"0\">" . $run->('x') . $sink
        . "<PartImage x=\"0\" y=\"0\" width=\"$pw\" height=\"$ph\"" . ($turn ? " pivotX=\"0.5\" pivotY=\"0.5\" angle=\"$turn\"" : '') . ">$AMB<Image resource=\"$name\" /></PartImage></Group></Group>" } } 0 .. 3 };
  # where the hand's line meets a straight edge at height y, between x0 and x1 (turn: 180 facing down);
  # held within the face, as the hand nears 3 or 9 o'clock and the line meets the edge far off
  # (the edges' runs go the hand's way, to their right-hand end)
  my $line = sub { my ($pic, $y, $x0, $x1, $turn) = @_;
    $pulse->([line => $pic, $y, $turn], sub { my $t = rad_of($_[0]); my $x = clampn(225 + (225 - $y) * sin($t) / clampn(cos($t), 0.05, 1), 0, 450);
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
      $add{$n} .= $pulse->([arc => 'dial', $cx, $cy, $r], sub { my @c = $crossn->($cx, $cy, $r, $_[0]); (clampn($c[$k], @{ $lim[$k] }), $c[2]) }, $out) }
    # back round, both ways, from the exit to where it came in, a tick after the hand's line has left the dial
    my $snap = int(($phi + $beta) / 6 + 2) % 60;
    my $o = $out - 360 * int($out / 360); my $i = $in - 360 * int($in / 360);
    my $up = $i - $o; $up += 360 while $up <= 0;   # the near way, round from where it leaves to where it came in
    $add{$n} .= join '', map { way($_, $D, [arc => 'dial', $cx, $cy, $r, $o, $o + $up]) . way($_, $D, [arc => 'dial', $cx, $cy, $r, $o + 360, $o + $up]) }
      pair(each_minute_at($snap));
    push @snaps, $snap; $when{$n} = "-/$snap" }
  # Pulses where the hand crosses a round piece of glass (the pill's ends, the window's dome), shown on
  # its own part of the circle (vis: the angle -> shown), running to that part's end (ends: the angles
  # round the circle where the part ends, or the hand leaves it).
  my $round = sub { my ($add, $pic, $cx, $cy, $r, $vis, @ends) = @_;
    for my $k (0, 1) {
      my $at = sub { my @c = $crossn->($cx, $cy, $r, $th12->($_[0])); ($c[$k], $c[2] && $vis->($c[$k])) };
      my $end = $arc_end->($at, @ends);
      $$add .= $pulse->([arc => $pic, $cx, $cy, $r], $at, $end) if defined $end } };
  # the pill: its straight edges, or its round ends where the hand crosses one (each end's outer half)
  { my $tan = do { my ($dx, $dy) = (272 - 225, 135 - 225); my $D2 = $dx * $dx + $dy * $dy;   # where the hand leaves the right end
      atan2($dx, -$dy) * 180 / $PI + atan2(25.5, sqrt($D2 - 25.5 * 25.5)) * 180 / $PI + 90 };
    $round->(\$add{pill}, 'pill_cap', 178, 135, 25.5, sub { sin(rad_of($_[0])) < 0 }, 0, 180);
    $round->(\$add{pill}, 'pill_cap', 272, 135, 25.5, sub { sin(rad_of($_[0])) > 0 }, $tan) }
  $add{pill} .= $line->('pill_bot', 160.5, 178, 272, 180) . $line->('pill_top', 109.5, 178, 272, 0);
  # the window: its dome (the upper half of its circle) and its flat base
  $round->(\$add{window}, 'win_cap', 225, 96.5, 32, sub { cos(rad_of($_[0])) > 0 }, 90);
  $add{window} .= $line->('win_line', 96.5, 193, 257, 180);
  # A copy whose run ends its glass's crossing doesn't go back at once, unseen as that is: with the hand
  # gone, the face would animate two seconds more just for that. It waits where the run left it until
  # the face animates anyway for the two seconds it takes (another glass's pulses running, or a dial's
  # water running back), and goes back then, before its next run.
  my %busy; for my $sp (@specs) { for my $v (@{ $sp->[2] }) { for (keys %$v) { $busy{$_}++; $busy{($_ + 1) % 60}++ } } }
  $busy{$_}++ for @snaps;
  my $waited = 0;
  my @made = map { my ($geom, $end, $vs) = @$_;
    my @holds = map { my $v = $_; my %h;
      for my $s (keys %$v) { next if exists $v->{($s + 4) % 60};
        my ($gap) = sort { $a <=> $b } map { (($_ - $s) % 60) || 60 } keys %$v;   # to its next run (a minute on, if no other)
        for my $r ($s + 2 .. $s + $gap - 2) { next unless $busy{$r % 60} && $busy{($r + 1) % 60}; if ($r > $s + 2) { $h{$s} = $r; $waited++ } last } }
      \%h } @$vs;
    $make->($geom, $end, $vs, \@holds) } @specs;
  s/\x01(\d+)\x01/$made[$1]/g for values %add;
  print STDERR "$waited pulses wait to go back\n";
}

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
printf STDERR "%d bulge stretches; in/out seconds: %s\n", $objects, join(", ", map { "$_ $when{$_}" } sort keys %when);
