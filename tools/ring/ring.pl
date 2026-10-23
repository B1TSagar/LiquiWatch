# Usage (after ring_glass.ps1 and tools\jelly\bulges.ps1 have drawn the pictures):
#   perl tools/jelly/jelly.pl < tools/ring/expedition_source.xml | perl tools/ring/ring.pl > watchfaces/src/expedition/res/raw/watchface.xml
# (expedition_source.xml is the Expedition face as it was, with its compass card, which this makes over)
# The minute ring: the compass card, its glass tube and the knob at 12 o'clock go, and a thinner glass
# tube in their place carries the minutes (a glass knob across it for each, with a line of the accent in
# it, and bigger ones for the hours, reaching out past its walls; 12 o'clock's line red) and the song's
# progress (Cover Display's: a line in the album's colour along the tube from 12 o'clock to a clear glass
# knob). The knobs are ring_glass.ps1's pictures. The heading is left to the compass page of the 6 o'clock
# dial.
use strict; use warnings; local $/; my $s = <STDIN>;
# ring_glass.ps1's pictures: their frames (x, y, w, h), and each mark's radius, kind, half length and half width
my %FRAME; { (my $f = __FILE__) =~ s{[^/\\]*$}{frames.txt}; open my $h, '<', $f or die "no $f (run ring_glass.ps1)\n";
  local $/ = "\n"; for (<$h>) { my ($n, @v) = split; $FRAME{$n} = \@v if $n } }
sub fmt { my $v = sprintf "%.3f", $_[0]; $v =~ s/\.?0+$//; $v = 0 if $v eq '-0'; $v }
my $AMB = '<Variant mode="AMBIENT" target="alpha" value="0" />';
# the element (with all inside it) that starts at $p: [start, end]
sub span { my $p = shift; my ($tag) = substr($s, $p, 40) =~ /^<(\w+)/ or die "no element at $p\n";
  return [$p, index($s, '>', $p) + 1] if substr($s, $p, index($s, '>', $p) - $p + 1) =~ m{/>$};
  pos($s) = $p; my $d = 0;
  while ($s =~ /<(\/?)$tag\b[^>]*?(\/?)>/g) { next if $2; $d += $1 ? -1 : 1; return [$p, $+[0]] if $d == 0 } die "unclosed $tag at $p\n" }
sub cut { my ($what, $p) = @_; die "$what not found\n" if $p < 0; my $r = span($p); substr($s, $r->[0], $r->[1] - $r->[0]) = ''; $r->[1] - $r->[0] }
# (the innermost element of this kind holding position $p)
sub around { my ($tag, $p) = @_; my $q = $p; while (($q = rindex($s, "<$tag", $q - 1)) >= 0) { my $r = span($q); return $q if $r->[1] > $p } -1 }

# ---- the ring ----------------------------------------------------------------------------------
my ($IN, $OUT) = (204, 224);            # the tube's walls (px from the face's centre: flush with its edge); it was 190 to 219.75
my $R = ($IN + $OUT) / 2;               # its middle: where the marks, the line and the song's knob ride
my $K = ($OUT - $IN) / 29.75;           # how much thinner it is
# where a ring at radius r (thickness t) of the old tube goes: across the tube in proportion; the
# shadows outside its walls move with the walls
sub remap { my ($r, $t, $white) = @_;
  return ($r + $IN - 190, $t) if $r + $t / 2 <= 190.01;
  return ($r + $OUT - 219.75, $t) if $r - $t / 2 >= 219.49;
  # (thin bright edges keep a little more of their width, to stay seen; the smoke narrows with the tube)
  ($IN + ($r - 190) * $K, ($t <= 1.6 ? 1.2 : $t * ($white && $t < 5 ? sqrt $K : $K))) }
# The tube's glass as Cover Display's progress tube is: lightly frosted and milky (its white haze, bright
# rims and shadows), and smoked as the face's panels are: the compass tube's bands, deepening toward its
# walls, in the panels' darker, muted shade of the accent (it had the hands' deep one): the panels' shade
# of the album's colour while a song plays, else each backdrop's panel colour; and as deep as the dials'
# (at the walls it builds up to what theirs does at their edge: about 72%, from 67%)
sub tube_ring { my ($x, $y, $w, $h) = @_; my $r = $w / 2; abs($x - $y) < 0.01 && abs($w - $h) < 0.01 && abs($x + $r - 225) < 0.02 && $r > 175 && $r < 230 }
sub deeper { my $a = int($_[0] * 1.15 + 0.5); $a > 255 ? 255 : $a }
my $smoke = 0;
$s =~ s{(<Ellipse x="([\d.]+)" y="([\d.]+)" width="([\d.]+)" height="([\d.]+)">\s*<Stroke color="#)([0-9A-F]{2})([0-9A-F]{6}" thickness="[\d.]+">\s*<Transform target="color" value="colorArgb\()(\d+)(, )\(\[REFERENCE\.smoke_r\] - 1\), \(\[REFERENCE\.smoke_g\] - 1\), \(\[REFERENCE\.smoke_b\] - 1\)\)}{
  tube_ring($2, $3, $4, $5) ? do { $smoke++; $1 . sprintf('%02X', deeper(hex $6)) . $7 . deeper($8) . "$9([REFERENCE.glass_r] - 1), ([REFERENCE.glass_g] - 1), ([REFERENCE.glass_b] - 1))" } : $& }ge;
my %PANEL;   # each backdrop's panel colour, as its dials' smoke has it
$s =~ s{<ListOption id="(\w+)">((?:(?!</ListOption>).)*?<Ellipse x="86\.1"[^>]*>\s*<Stroke color="#[0-9A-F]{2}([0-9A-F]{6})"(?:(?!</ListOption>).)*)</ListOption>}{
  my ($id, $body, $panel) = ($1, $2, $3); $PANEL{$id} = $panel;
  $body =~ s{(<Ellipse x="([\d.]+)" y="([\d.]+)" width="([\d.]+)" height="([\d.]+)">\s*<Stroke color="#)([0-9A-F]{2})([0-9A-F]{6})"}{
    tube_ring($2, $3, $4, $5) && $7 ne 'FFFFFF' && $7 ne '000000' ? do { $smoke++; $1 . sprintf('%02X', deeper(hex $6)) . "$panel\"" } : $& }ge;
  "<ListOption id=\"$id\">$body</ListOption>" }gse;
die "the panels' colours weren't found\n" unless keys %PANEL == 6;
die "the tube's smoke wasn't found ($smoke)\n" unless $smoke == 84;
# The panels' glass takes the accent on the side the light comes from: tint.py's pictures, white with the tint in
# their alpha (deepest at the edge at the top left, fading smoothly a good way into the glass), in place of the bands
# of smoke round each panel's edge (the shapes the album's copy draws in the panels' shade, in every backdrop's copy
# too), each tinted that copy's shade: the album's (worked out, as the bands were) or the backdrop's own. The hour
# beads get it too, with the dials. (After the tube's smoke has taken the panels' shade: its rings aren't bands.)
{ my %band = map { $_ => 1 } grep { !(/^Ellipse x="([\d.]+)" y="([\d.]+)" width="([\d.]+)" height="([\d.]+)"$/ && tube_ring($1, $2, $3, $4)) }
    $s =~ /<((?:Ellipse|RoundRectangle|Arc) [^>]*)>\s*<(?:Stroke|Fill) color="#[0-9A-F]{8}"[^>]*>\s*<Transform target="color" value="colorArgb\(\d+, \(\[REFERENCE\.glass_r\]/g;
  die "the panels' bands of smoke weren't found (" . (keys %band) . " shapes)\n" unless keys %band == 28;
  my @beads = $s =~ m{<PartImage x="(\d+)" y="(\d+)" width="25" height="25"><Variant mode="AMBIENT" target="alpha" value="\d+" /><Image resource="exp_bead" />}g;
  die "the hour beads weren't found (or not 11: " . @beads / 2 . ")\n" unless @beads == 22;
  my $pic = sub { my ($n, $x, $y, $w, $h, $tint) = @_; my $fixed = $tint =~ /^#/;
    "<PartImage x=\"$x\" y=\"$y\" width=\"$w\" height=\"$h\"" . ($fixed ? " tintColor=\"$tint\"" : '') . ">$AMB" . ($fixed ? '' : "<Transform target=\"tintColor\" value=\"$tint\" />") . "<Image resource=\"$n\" /></PartImage>" };
  my ($drawn, $pics) = (0, 0);
  # each drawing with bands in it: its bands out (the drawing too, if that leaves it empty), the tint after it
  $s =~ s{(<PartDraw x="(\d+)" y="(\d+)" width="\d+" height="\d+">)(.*?)</PartDraw>}{
    my ($all, $head, $x, $y, $in) = ($&, $1, $2, $3, $4); my ($n, $tint, %kind) = (0);   # (before the match below takes $&)
    $in =~ s{<((?:Ellipse|RoundRectangle|Arc) [^>]*)>\s*<(Stroke|Fill) color="#([0-9A-F]{2})([0-9A-F]{6})"[^>]*?(?:/>|>(?:\s*<Transform target="color" value="([^"]*)" />)?\s*</\2>)\s*</(?:Ellipse|RoundRectangle|Arc)>}{
      if ($band{$1}) { $n++; my $shape = $1; my $t = $5 ? $5 =~ s/^colorArgb\(\d+, /colorRgb(/r : "#FF$4"; $tint //= $t;
        die "a panel's bands in two shades in one drawing\n" if $tint ne $t;
        $kind{ $shape =~ /^Ellipse/ ? 'dial' : $shape =~ /centerX="39"|^RoundRectangle x="(?:11\.78|10\.71|9\.79|8\.72)"/ ? 'window' : 'pill' } = 1; '' } else { $& } }gse;
    if (!$n) { $all } else { $drawn += $n;
      my $tints = join '', ($kind{dial} ? (map { $pic->('exp_tint_dial', @$_, 104, 104, $tint) } [73, 173], [273, 173], [173, 273]) : ()),
        ($kind{pill} ? $pic->('exp_tint_pill', 144, 101, 162, 68, $tint) : ()),
        ($kind{dial} ? (map { $pic->('exp_tint_bead', $beads[2 * $_], $beads[2 * $_ + 1], 25, 25, $tint) } 0 .. 10) : ()),
        ($kind{window} ? $pic->('exp_tint_window', $x, $y, 78, 45, $tint) : ());   # (the window's drawing sits where its pictures do)
      $pics += () = $tints =~ /<PartImage /g;
      ($in =~ /<(?:Ellipse|RoundRectangle|Arc|Line|Rectangle)\b/ ? "$head$in</PartDraw>" : '') . $tints } }gse;
  die "the panels' bands weren't all taken out ($drawn of 196)\n" unless $drawn == 196;
  die "the panels' tint went in $pics times, not 7 x 16\n" unless $pics == 7 * 16 }
my $rings = 0;
$s =~ s{<Ellipse x="([\d.]+)" y="([\d.]+)" width="([\d.]+)" height="([\d.]+)">(\s*<Stroke color="([^"]*)" thickness=")([\d.]+)"}{
  my ($x, $y, $w, $h, $mid, $col, $t) = ($1, $2, $3, $4, $5, $6, $7); my $r = $w / 2;
  if (abs($x - $y) < 0.01 && abs($w - $h) < 0.01 && abs($x + $r - 225) < 0.02 && $r > 175 && $r < 230) {
    my ($r2, $t2) = remap($r, $t, $col =~ /FFFFFF$/); $rings++;
    '<Ellipse x="' . fmt(225 - $r2) . '" y="' . fmt(225 - $r2) . '" width="' . fmt(2 * $r2) . '" height="' . fmt(2 * $r2) . '">' . $mid . fmt($t2) . '"' }
  else { $& } }ge;
# Its glass itself is Cover Display's progress tube: ring_glass.ps1's picture of what that glass does over what's
# behind it (milky, its rim lit from the top left, its shadow), in place of the rings of light and shade the
# compass tube had; the tube's smoke and the song's line stay under it.
{ my $p = index($s, '<PartDraw', index($s, '<Group name="cache_ring"')); my $r = span($p); my $draw = substr($s, $p, $r->[1] - $p);
  die "the tube's glass isn't the ring's first drawing\n" unless $draw =~ /^<PartDraw x="0" y="0" width="450" height="450">\Q$AMB\E<Ellipse / && 11 == (() = $draw =~ /<Ellipse /g) && $draw !~ /<(?!\/?(?:PartDraw|Variant|Ellipse|Stroke|LinearGradient)\b)\w/;
  my ($x, $y, $w, $h) = @{ $FRAME{exp_ring_tube} // die "no frame for the tube (run ring_glass.ps1)\n" };
  substr($s, $p, $r->[1] - $p) = "<PartImage x=\"$x\" y=\"$y\" width=\"$w\" height=\"$h\">$AMB<Image resource=\"exp_ring_tube\" /></PartImage>" }

# ---- out with the compass ------------------------------------------------------------------------
my $gone = 0;
$gone += cut('the compass slot', index($s, '<ComplicationSlot x="189" y="0" width="72" height="46" slotId="32"'));
$gone += cut('the card', index($s, '<Group name="card"'));
$gone += cut('the knob\'s lens', index($s, '<Group name="knob_lens"'));
{ my $p = index($s, '<Image resource="exp_knob" />'); $gone += cut('the knob', around('PartImage', $p)) }
# the knob's smoke (one for the album's colour, and the backdrops' in a ListConfiguration)
while ((my $p = index($s, '<PartDraw x="195" y="0" width="60" height="30"')) >= 0) {
  my $l = around('ListConfiguration', $p); my $c = around('Condition', $p);
  $gone += ($l >= 0 && span($l)->[1] - $l < 10000) ? cut("its smoke", $l) : cut("its smoke", $c) }   # (a small ListConfiguration of its own, or a Condition)
die "the heading is still read: $1\n" if $s =~ /(\[REFERENCE\.hdg_\w+\])/;

# ---- the song's progress: Cover Display's line, along the middle of the tube ---------------------
my $lines = 0;
$s =~ s{<PartDraw x="26" y="26" width="398" height="398">(.*?)<Arc centerX="199" centerY="199" width="391" height="391"(.*?)thickness="1\.6"}{
  $lines++; '<PartDraw x="' . fmt(225 - $R - 1) . '" y="' . fmt(225 - $R - 1) . '" width="' . fmt(2 * $R + 2) . '" height="' . fmt(2 * $R + 2) . '">' . $1
  . '<Arc centerX="' . fmt($R + 1) . '" centerY="' . fmt($R + 1) . '" width="' . fmt(2 * $R) . '" height="' . fmt(2 * $R) . '"' . $2 . 'thickness="4"' }gse;
die "the progress line wasn't found (or not 7 times: $lines)\n" unless $lines == 7;
# It's under the tube's glass, so drawn soft, as Cover Display's frosted glass shows it (blurred by 0.8 dp): a
# core with two fainter, wider strokes round it, as much colour across it as the line had; and its colour bleeds
# into the glass round it, a soft glow most of the way across the tube (two wider, fainter strokes still).
sub soft { my $arc = shift; join '', map { my ($t, $a) = @$_; (my $x = $arc) =~ s/thickness="4"/thickness="$t"/ or die "no line in $arc\n";
    if ($a < 255) { my $h = sprintf '%02X', $a; $x =~ s/<Stroke color="#FF/<Stroke color="#$h/; $x =~ s/colorRgb\(/colorArgb($a, /g } $x } [20, 26], [12, 48], [6.4, 64], [4.4, 128], [2.8, 255] }
my $soft = $s =~ s{(<Arc centerX="[^"]*" centerY="[^"]*" width="${\ fmt(2 * $R)}" height="${\ fmt(2 * $R)}" startAngle="0" endAngle="0\.01">.*?</Arc>)}{soft($1)}gse;
die "the progress line's arcs weren't found (or not 7: $soft)\n" unless $soft == 7;
# ---- moving on: each second's step glides, a new place is jumped to ----------------------------------
# The line's end and the song's knob glide on each second's step over the second, at 8 frames a second.
# Gliding from one place to the next swept them round the ring on a song switch (or a seek, or a resume), so
# the line is drawn to where the song was a second ago, without gliding, and the knob and the line's last
# stretch stand there, turned there without gliding, and glide only that second's step on from it: two
# copies of each, taking turns by the second, each gliding on in its own second and back unseen in the
# other's. A new place is a new second-ago, there at once.
my $GLIDE = '<Animation duration="1" interpolation="LINEAR" fps="8" />';
my ($P) = substr($s, index($s, '<Group name="song_knob"'), 3000) =~ m{<Transform target="angle" value="([^"]*)">\Q$GLIDE\E</Transform>} or die "no gliding song knob\n";
(my $P1 = $P) =~ s{\Q((8 * [SECOND]) / 8)\E}{([SECOND] - 1)} or die "no second in the song's angle\n";   # where the song was a second ago
my ($x, $y, $w, $h) = @{ $FRAME{exp_ring_knob00} // die "no frame for the knob (run ring_glass.ps1)\n" };
die "the knob isn't in the middle at 12 o'clock\n" unless $x + $w / 2 == 225;
# (a box from the knob at 12 o'clock to a pixel past the centre: with the pivot on its edge, pivotY 1, the
# watch didn't draw it)
my $size = "y=\"0\" width=\"$w\" height=\"226\" pivotX=\"0.5\" pivotY=\"" . sprintf('%.9f', 225 / 226) . '"'; my $box = "x=\"$x\" $size";
my $step = sub { my $q = shift; "((([SECOND] % 2) == $q) ? ($P - $P1) : 0)" };   # copy q's glide: on in its own seconds, back in the others
my $copy = sub { my ($n, $q, $in) = @_; "<Group name=\"$n$q\" $box alpha=\"255\">$AMB<Transform target=\"alpha\" value=\"((([SECOND] % 2) == $q) ? 255 : 0)\" /><Transform target=\"angle\" value=\"$P1\" />$in</Group>" };
my $held = $s =~ s{<Transform target="endAngle" value="\Q$P\E">\Q$GLIDE\E</Transform>}{<Transform target="endAngle" value="$P1" />}g;
die "the progress line's glide wasn't found (or not 35 times, 5 strokes in each of its 7 colours: $held)\n" unless $held == 35;
my $crisp;   # the line inside the knob (below)
# the line's last stretch, in its colours (the album's, and each backdrop's while that isn't known), from the
# second-ago on by the step, drawn in the knob's box; after the line, so under the marks as it is
{ my $c = around('Condition', index($s, '<PartDraw x="' . fmt(225 - $R - 1) . '" y="' . fmt(225 - $R - 1) . '"')); my $l = span(span($c)->[1]);
  die "no backdrops' line after the album's\n" unless substr($s, $l->[0], 30) =~ /^<ListConfiguration id="bg">/;
  my $line = substr($s, $c, $l->[1] - $c); die "the line's colours aren't 7\n" unless 7 == (() = $line =~ /<PartDraw /g);
  my $tip = sub { my $q = shift; (my $t = $line) =~ s{<PartDraw x="[^"]*" y="[^"]*" width="[^"]*" height="[^"]*">}{<PartDraw x="0" y="0" width="$w" height="226">}g;
    $t =~ s{<Arc centerX="[^"]*" centerY="[^"]*"}{'<Arc centerX="' . (225 - $x) . '" centerY="225"'}ge;
    $t =~ s{<Transform target="endAngle" value="\Q$P1\E" />}{'<Transform target="endAngle" value="' . $step->($q) . "\">$GLIDE</Transform>"}ge; $t };
  substr($s, $l->[1], 0) = $copy->('song_tip_', 0, $tip->(0)) . $copy->('song_tip_', 1, $tip->(1));
  # Inside the knob the line shows crisp, as through Cover Display's knob, which is a clear lens (at the line's
  # own width, 4 px: it doesn't magnify it): its core stroke, in its colours, from 4 degrees back (or 12 o'clock, if the song
  # is nearer it than that) to the knob's middle, where the line ends, in the knob's box.
  ($crisp = $line) =~ s{<PartDraw x="[^"]*" y="[^"]*" width="[^"]*" height="[^"]*">(<Variant [^>]*/>)?(.*?)</PartDraw>}{
    my ($amb, $in) = ($1 // '', $2); my ($core) = $in =~ m{(<Arc (?:(?!</Arc>).)*?thickness="2\.8"(?:(?!</Arc>).)*</Arc>)}s or die "no core stroke in the line\n";
    $core =~ s{^<Arc centerX="[^"]*" centerY="[^"]*" (width="[^"]*" height="[^"]*") startAngle="0" endAngle="0\.01">}{sprintf '<Arc centerX="%d" centerY="225" %s startAngle="356" endAngle="360">', 225 - $x, $1}e
      && $core =~ s{<Transform target="endAngle" value="[^"]*" />}{<Transform target="startAngle" value="(360 - clamp($P, 0, 4))" />}
      && $core =~ s{thickness="2\.8" cap="ROUND"}{thickness="4" cap="BUTT"} or die "the line's core stroke isn't as it should be\n";
    "<PartDraw x=\"0\" y=\"0\" width=\"$w\" height=\"226\">$amb$core</PartDraw>" }gse;
  die "the line inside the knob isn't in 7 colours\n" unless 7 == (() = $crisp =~ /<PartDraw /g) }

# ---- the song's knob: Cover Display's slider knob, gliding along the tube -------------------------
# Over the marks, not melted into them: ring_glass.ps1's picture of it at 12 o'clock, in the box down to the
# face's centre, turned to where the song is. It's lit as it will face once turned: a picture for each 10
# degrees, picked by the angle, which is worked out once and published (in tens of degrees: an alpha holds 1
# to 255).
{ my $p = index($s, '<Group name="song_knob"'); my $r = span($p);
  my $pub = sub { my ($n, $v) = @_; "<Group name=\"pub_$n\" x=\"5\" y=\"5\" width=\"2\" height=\"2\" alpha=\"255\"><Transform target=\"alpha\" value=\"$v\" /><Reference name=\"$n\" source=\"alpha\" defaultValue=\"0\" />"
    . '<PartDraw x="0" y="0" width="2" height="2"><Rectangle x="0" y="0" width="2" height="2"><Fill color="#01FFFFFF" /></Rectangle></PartDraw></Group>' };
  my $pics = join '', map { my $n = sprintf 'exp_ring_knob%02d', $_; $FRAME{$n} or die "no frame for $n\n";
    "<Condition><Expressions><Expression name=\"c0\">([REFERENCE.song_turn] == " . ($_ + 1) . ")</Expression></Expressions><Compare expression=\"c0\">"
    . "<PartImage x=\"0\" y=\"$y\" width=\"$w\" height=\"$h\"><Image resource=\"$n\" /></PartImage></Compare></Condition>" } 0 .. 35;
  substr($s, $r->[0], $r->[1] - $r->[0]) = $pub->('song_turn', "(1 + (round($P / 10) % 36))")
    . join '', map { $copy->('song_knob_', $_, "<Group name=\"song_knob_glide_$_\" x=\"0\" $size><Transform target=\"angle\" value=\"" . $step->($_) . "\">$GLIDE</Transform>$crisp$pics</Group>") } 0, 1 }

# ---- the minutes: a glass knob across the tube for each, a bigger one for each hour -----------------
# Each holds a line of the accent (the album's colour while a song plays with one, else the backdrop's),
# a thicker one for an hour, whose knob reaches in past the tube's inner wall; 12 o'clock's line is red.
# They lie on the tube, over its glass and the song's line, under the song's knob. The knobs are
# pictures in the glass of the face's own panels, each drawn where it sits, and smoked as the panels are:
# under each, its shape a little inside its edge in the panels' shade (the album's while a song plays with
# one, else the backdrop's), as deep as the dials' smoke is on average across its band. The lines are drawn
# over the smoke, under the glass.
{ my $PI = 4 * atan2(1, 1); my $RED = '#FFFF453A'; my $SMOKE = 107;
  # each kind: the line's half length and width (the radius of its knob's middle is in the frames)
  my %LINE = (minute => [6, 1.8], hour => [12, 3.4]);
  my @MARK = map { my $n = sprintf 'exp_ring_mk%02d', $_; my $f = $FRAME{$n} // die "no frame for $n\n"; die "no size for $n (run ring_glass.ps1)\n" unless @$f >= 8; [$n, @$f] } 0 .. 59;   # [name, x, y, w, h, radius, kind, half length, half width]
  # the backdrops' accents, as the song's line takes them (its core stroke, full strength)
  my %acc; while ($s =~ /<ListOption id="(\w+)">\s*<Condition>(?:(?!<\/ListOption>).)*?thickness="2.8" cap="ROUND"(?:(?!<\/ListOption>).)*?<\/ListOption>/gs) { my $o = $1; ($acc{$o}) = $& =~ /<Stroke color="(#[0-9A-F]{8})" thickness="2.8" cap="ROUND" \/>/ }
  die "the backdrops' accents weren't found\n" unless keys %acc == 6;
  my ($ALBUM) = $s =~ /<Stroke color="#[0-9A-F]{8}" thickness="2.8" cap="ROUND"><Transform target="color" value="([^"]*album_r[^"]*)"/ or die "no album colour\n";
  my $lines = sub { my ($colour, $smoke) = @_; my $out = '';
    for my $m (0 .. 59) { my $a = $m * 6 * $PI / 180; my ($r, $len, $wid) = @{ $MARK[$m] }[5, 7, 8]; my $half = $len - $wid;
      $out .= sprintf '<Line startX="%s" startY="%s" endX="%s" endY="%s"><Stroke color="%s" thickness="%s" cap="ROUND"%s</Line>',
        fmt(225 + ($r - $half) * sin $a), fmt(225 - ($r - $half) * cos $a), fmt(225 + ($r + $half) * sin $a), fmt(225 - ($r + $half) * cos $a),
        $smoke->[0], fmt(2 * $wid - 1), $smoke->[1] ? "><Transform target=\"color\" value=\"$smoke->[1]\" /></Stroke>" : ' />' }
    # (each line's colour bleeds into its glass: two wider, fainter strokes of it under the line)
    for my $m (0 .. 59) { my $a = $m * 6 * $PI / 180; my $r = $MARK[$m][5]; my ($half, $t) = @{ $LINE{ $MARK[$m][6] } };
      for ([3, 28], [2, 56], [1, 255]) { my ($k, $al) = @$_; my $c = $m ? $colour->[0] : $RED; my $tr = $m ? $colour->[1] : undef;
        if ($al < 255) { my $h = sprintf '%02X', $al; $c =~ s/^#../#$h/; $tr =~ s/colorRgb\(/colorArgb($al, / if $tr }
        $out .= sprintf '<Line startX="%s" startY="%s" endX="%s" endY="%s"><Stroke color="%s" thickness="%s" cap="ROUND"%s</Line>',
          fmt(225 + ($r - $half) * sin $a), fmt(225 - ($r - $half) * cos $a), fmt(225 + ($r + $half) * sin $a), fmt(225 - ($r + $half) * cos $a),
          $c, fmt($k * $t), $tr ? "><Transform target=\"color\" value=\"$tr\" /></Stroke>" : ' />' } }
    "<PartDraw x=\"0\" y=\"0\" width=\"450\" height=\"450\"><Variant mode=\"AMBIENT\" target=\"alpha\" value=\"120\" />$out</PartDraw>" };
  my $SONG = '([REFERENCE.song_on] &gt; 128) &amp;&amp; ([REFERENCE.album_r] &gt; 0.5)';
  my $A = sprintf '%02X', $SMOKE;
  my $marks = "<Condition><Expressions><Expression name=\"c0\">$SONG</Expression></Expressions><Compare expression=\"c0\">"
    . $lines->(['#FFFFFFFF', $ALBUM], ["#$A$PANEL{gold}", "colorArgb($SMOKE, ([REFERENCE.glass_r] - 1), ([REFERENCE.glass_g] - 1), ([REFERENCE.glass_b] - 1))"])
    . "</Compare></Condition><ListConfiguration id=\"bg\">"
    . join('', map { "<ListOption id=\"$_\"><Condition><Expressions><Expression name=\"c0\">(!($SONG))</Expression></Expressions><Compare expression=\"c0\">" . $lines->([$acc{$_}], ["#$A$PANEL{$_}"]) . '</Compare></Condition></ListOption>' } qw(gold ocean ember violet forest black))
    . '</ListConfiguration>';
  $marks .= join '', map { my ($n, $x, $y, $w, $h) = @$_; "<PartImage x=\"$x\" y=\"$y\" width=\"$w\" height=\"$h\">$AMB<Image resource=\"$n\" /></PartImage>" } @MARK;
  # on the tube: after its glass (in cache_ring), with the song's knob moved after them
  my $c = around('Condition', index($s, '<Group name="song_knob_0"')); my $r = span($c); my $knob = substr($s, $r->[0], $r->[1] - $r->[0]); substr($s, $r->[0], $r->[1] - $r->[0]) = '';
  my $after = span(index($s, '<Group name="cache_ring"'))->[1];
  substr($s, $after, 0) = "<Group name=\"minute_marks\" x=\"0\" y=\"0\" width=\"450\" height=\"450\">$marks</Group>$knob" }

# ---- the hands: standing up off the face, their lines glowing into their glass ------------------------
# Each hand's line bleeds its colour into the hand's glass: two wider, fainter strokes of its core under it, in
# its drawing widened to hold them (6 px each side, its pivot still in its middle); the second hand's glass is
# thin, so one, narrower and fainter (two filled it, a coloured line).
{ my %hand = ('222 125 6 111' => 'hour', '222 75 6 161' => 'minute', '221 50 8 198' => 'second'); my $glows = 0;
  $s =~ s{<PartDraw x="(\d+)" y="(\d+)" width="(\d+)" height="(\d+)" pivotX="0\.5"( pivotY="[\d.]+">)(.*?)</PartDraw>}{
    my ($x, $y, $w, $h, $py, $in) = ($1, $2, $3, $4, $5, $6);
    if ($hand{"$x $y $w $h"}) { $glows++;
      $in =~ s{\b(startX|endX)="([\d.]+)"}{"$1=\"" . fmt($2 + 6) . '"'}ge; $in =~ s{(<Ellipse x=")([\d.]+)"}{$1 . fmt($2 + 6) . '"'}ge;
      my ($core, $t) = $in =~ m{(<Line [^>]*><Stroke color="#FF(?!FFFFFF)[0-9A-F]{6}" thickness="([\d.]+)".*?</Line>)}s or die "no line in the $hand{\"$x $y $w $h\"} hand\n";
      my $glow = join '', map { my ($k, $a) = @$_; (my $g = $core) =~ s/thickness="[\d.]+"/'thickness="' . fmt($k * $t) . '"'/e;
        my $hx = sprintf '%02X', $a; $g =~ s/<Stroke color="#FF/<Stroke color="#$hx/; $g =~ s/colorRgb\(/colorArgb($a, /; $g } $hand{"$x $y $w $h"} eq 'second' ? ([2.4, 20]) : ([4, 26], [2.6, 51]);
      $in =~ s{(<Line )}{$glow$1};
      '<PartDraw x="' . ($x - 6) . "\" y=\"$y\" width=\"" . ($w + 12) . "\" height=\"$h\" pivotX=\"0.5\"$py$in</PartDraw>" }
    else { $& } }gse;
  die "the hands' lines weren't found (or not 21: $glows)\n" unless $glows == 21 }
# And each casts a soft shadow, its glass's shape (ring_glass.ps1's picture of it) turned as it turns but 5 px
# lower, on the face and the panels: just before the hands' layer, not in it, as that layer takes all it has drawn
# before its smoke off the panels (so the second hand's smoke doesn't cross them).
my %TUBE;   # each hand's glass: its frame in the hands' glass (at 41, 41 on the face), its pivot and its turn
my $shadows = '';
{ my $c = index($s, '<Group name="cache_hands"'); die "no hands\n" if $c < 0; my $tubes = index($s, '<Group name="tubes" x="41" y="41"', $c); die "no hands' glass\n" if $tubes < 0;
  for my $n (qw(hour minute second)) { my $p = index($s, "<Image resource=\"exp_${n}_tube\" />", $tubes); my $q = rindex($s, '<PartImage', $p);
    my ($x, $y, $w, $h, $piv, $turn) = substr($s, $q, $p - $q) =~ m{^<PartImage x="(\d+)" y="(\d+)" width="(\d+)" height="(\d+)" (pivotX="0\.5" pivotY="[\d.]+")><Transform target="angle" value="([^"]*)" />$} or die "the $n hand's glass isn't as it was\n";
    $TUBE{$n} = [$x, $y, $w, $h, $piv, $turn];
    $shadows .= '<PartImage x="' . (41 + $x) . '" y="' . (41 + $y + 5) . "\" width=\"$w\" height=\"$h\" $piv>$AMB<Transform target=\"angle\" value=\"$turn\" /><Image resource=\"exp_${n}_shadow\" /></PartImage>" } }
# Where a hand passes over a panel, the panel's glass rises to meet it: ring_glass.ps1's pictures of that, in the
# hand's glass's frame made taller past its tip, turned as the hand turns, and lit as they face: one for each 10
# degrees, picked by the hand's angle. On the panels only (a mask of them), under the hands' shadows, before the
# hands' layer as they are.
{ my ($bx, $by, $bw, $bh) = @{ $FRAME{exp_ridge_mask} // die "no frame for the panels' mask (run ring_glass.ps1)\n" };
  my $add = "<Group name=\"ridges\" x=\"$bx\" y=\"$by\" width=\"$bw\" height=\"$bh\">$AMB";
  for my $n (qw(hour minute second)) { my ($x, $y, $w, $h, $piv, $turn) = @{ $TUBE{$n} };
    my ($m, $row) = @{ $FRAME{"exp_ridge_$n"} // die "no frame for the ${n}'s risen glass (run ring_glass.ps1)\n" };
    my ($py) = $piv =~ /pivotY="([\d.]+)"/; die "the $n hand doesn't turn where its risen glass does ($py of $h, not $row)\n" unless abs($py * $h - $row) < 0.01;
    my $frame = 'x="' . (41 + $x - $bx) . '" y="' . (41 + $y - $m - $by) . "\" width=\"$w\" height=\"" . ($h + $m) . '" pivotX="0.5" pivotY="' . sprintf('%.9f', ($row + $m) / ($h + $m)) . '"';
    my $lit = sub { my $in = shift; join '', map { "<Condition><Expressions><Expression name=\"c0\">((round(($turn) / 10) % 36) == $_)</Expression></Expressions><Compare expression=\"c0\">"
      . $in->(sprintf('exp_ridge_%s_%02d', $n, $_)) . '</Compare></Condition>' } 0 .. 35 };
    $add .= $lit->(sub { "<PartImage $frame><Transform target=\"angle\" value=\"$turn\" /><Image resource=\"$_[0]\" /></PartImage>" }) }
  $add .= "<PartImage x=\"0\" y=\"0\" width=\"$bw\" height=\"$bh\" blendMode=\"DST_IN\"><Image resource=\"exp_ridge_mask\" /></PartImage></Group>";
  substr($s, index($s, '<Group name="cache_hands"'), 0) = $add . $shadows }
# The hands pass over the panels, on top of them: their glass and smoke, which stopped at the panels (the glass
# melting into them there, the necks), carry on over them from a little inside their edges, past the necks, where
# the risen glass under them begins (ring_glass.ps1's masks for them, with the panels' insides put back).
{ my $n = $s =~ s{<Image resource="exp_(tube|smoke)_mask" />}{<Image resource="exp_$1_mask_top" />}g;
  die "the hands' glass and smoke masks weren't found (or not 3: $n)\n" unless $n == 3 }

# ---- the panels' glass: the ring's (ring_glass.ps1 draws their pictures in it) ----------------------
# Their bodies and rims are the same pictures as before, drawn anew; their shadows are pictures of their own,
# under all the panels (so one doesn't shade the next), each placed from its panel's pictures' corner.
{ my $c = index($s, '<Group name="cache_panels"'); die "no panels\n" if $c < 0; my $in = index($s, '>', $c) + 1; my $add = '';
  for (['dial', 73, 173], ['dial', 273, 173], ['dial', 173, 273], ['pill', 144, 101], ['window', 186, 58]) { my ($n, $px, $py) = @$_;
    die "the $n isn't where its shadow goes ($px, $py)\n" unless index($s, "<PartImage x=\"$px\" y=\"$py\"", $c) > $c;   # (its tint, under the glass, sits there too)
    my ($x, $y, $w, $h) = @{ $FRAME{"exp_${n}_shadow"} // die "no frame for the ${n}'s shadow (run ring_glass.ps1)\n" };
    $add .= '<PartImage x="' . ($px + $x) . '" y="' . ($py + $y) . "\" width=\"$w\" height=\"$h\">$AMB<Image resource=\"exp_${n}_shadow\" /></PartImage>" }
  substr($s, $in, 0) = $add }

my $BEAD = 110;   # how much of the hour beads' own bright ring shows under their glass (of 255)
# ---- the hour beads: the panels' glass over them ----------------------------------------------------
# ring_glass.ps1's picture of it (milky, lit from the top left, a small shadow) over each bead, after them all
# in the ring's layer and not on their Conditions: it stays while a bead gives way to its melt into the second
# hand's tip (drawn later, with the hands), so a bead looks the same as the hand passes.
{ my $r = span(index($s, '<Group name="cache_ring"')); my $end = $r->[1] - length('</Group>');
  my @at = substr($s, $r->[0], $end - $r->[0]) =~ m{<PartImage x="(\d+)" y="(\d+)" width="25" height="25"><Variant mode="AMBIENT" target="alpha" value="\d+" /><Image resource="exp_bead" />}g;
  die "the hour beads weren't found (or not 11: " . @at / 2 . ")\n" unless @at == 22;
  substr($s, $end, 0) = join '', map { "<PartImage x=\"$at[2 * $_]\" y=\"$at[2 * $_ + 1]\" width=\"25\" height=\"25\">$AMB<Image resource=\"exp_bead_glass\" /></PartImage>" } 0 .. 10;
  # (and their own bright ring, the hands' glass, dimmed under it, so they look as the panels do)
  my $dim = $s =~ s{(<PartImage x="\d+" y="\d+" width="25" height="25")(><Variant mode="AMBIENT" target="alpha" value="\d+" /><Image resource="exp_bead" />)}{$1 alpha="$BEAD"$2}g;
  die "the hour beads weren't dimmed (or not 11: $dim)\n" unless $dim == 11 }

# ---- the glass bending what's behind it ---------------------------------------------------------------
# refract.py's: the backdrops as seen through the glass that sits on them, in place of the lenses (which bent only
# the dials' and the pill's backdrop, and magnified it); and the album art bent the same way as the watch draws it:
# a copy of it in each dial's, the pill's and each bead's box, shrunk about the glass's middle so its edge shows
# what lies beyond it, cut to the glass's rim by refract.py's picture of it (refract.txt says where each goes).
{ my $r = span(index($s, '<ListConfiguration id="bg">')); my $bg = substr($s, $r->[0], $r->[1] - $r->[0]);
  my $n = $bg =~ s{resource="bg_(\w+)"}{resource="exp_bg_$1"}g; die "the backdrops aren't 5 ($n)\n" unless $n == 5;
  substr($s, $r->[0], $r->[1] - $r->[0]) = $bg;
  for my $part (qw(heart steps readout date)) { my $q = index($s, "resource=\"lens_gold_$part\""); cut("the ${part}'s lens", $q < 0 ? -1 : around('ListConfiguration', $q)) }
  die "a lens is left\n" if $s =~ /resource="lens_/;
  (my $f = __FILE__) =~ s{[^/\\]*$}{refract.txt}; open my $h, '<', $f or die "no $f (run refract.py)\n";
  my @copy = map { [split] } grep { /^copy / } do { local $/ = "\n"; <$h> }; die "not 15 copies of the album art\n" unless @copy == 15;
  for my $type (qw(SMALL_IMAGE PHOTO_IMAGE)) { my $art = "<PartImage x=\"0\" y=\"0\" width=\"450\" height=\"450\"><Image resource=\"[COMPLICATION.$type]\" /></PartImage>";
    my $i = 0; my $k = lc substr($type, 0, 1);
    my $n = $s =~ s{\Q$art\E}{$art . join '', map { my (undef, $m, $gx, $gy, $gw, $gh, $x, $y, $w, $h) = @$_; $i++;
      "<Group name=\"bent_${k}$i\" x=\"$gx\" y=\"$gy\" width=\"$gw\" height=\"$gh\"><PartImage x=\"$x\" y=\"$y\" width=\"$w\" height=\"$h\"><Image resource=\"[COMPLICATION.$type]\" /></PartImage>"
      . "<PartImage x=\"0\" y=\"0\" width=\"$gw\" height=\"$gh\" blendMode=\"DST_IN\"><Image resource=\"$m\" /></PartImage></Group>" } @copy}e;
    die "the album art ($type) wasn't found, or more than once ($n)\n" unless $n == 1 } }

# ---- checks ----------------------------------------------------------------------------------------
for my $e ($s =~ /(?:value="|<Expression name="[^"]*">)([^"<]*)/g) { my $o = () = $e =~ /\(/g; my $c = () = $e =~ /\)/g; die "brackets don't balance: $e\n" if $o != $c }
die "a fraction of a pixel: $1\n" if $s =~ /(<(?:Group|PartImage|PartDraw) [^>]*\b(?:x|y|width|height)="-?\d*\.\d+")/;
{ (my $dir = __FILE__) =~ s{[^/\\]*$}{}; $dir .= '../../watchfaces/src/expedition/res/drawable-nodpi/';
  for my $n ($s =~ /resource="(exp_ring_[a-z0-9]+|exp_[a-z]+_shadow|exp_ridge_[a-z0-9_]+|exp_[a-z]+_mask_top|exp_bead_glass)"/g) { die "no picture $n (run ring_glass.ps1)\n" unless -e "$dir$n.png" }
  for my $n ($s =~ /resource="(exp_bg_[a-z]+|exp_refr_[a-z]+)"/g) { die "no picture $n (run refract.py)\n" unless -e "$dir$n.png" } }
print $s;
printf STDERR "tube smoke in the panels' shade %d, tube rings moved %d, compass parts taken out (%d bytes), progress lines %d\n", $smoke, $rings, $gone, $lines;
