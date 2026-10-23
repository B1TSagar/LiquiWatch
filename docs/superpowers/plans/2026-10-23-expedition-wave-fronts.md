# Expedition Wave Fronts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Waves that stretch between each tick's two edge bulges and run with them, on all five panels. Snap-back waves on the dials. A centre ripple for taps. Glass 1.5× taller.

**Architecture:**
- `jelly.pl` already knows every bulge's path. It pairs the two bulges each wave runs between and works out each wave frame's curve.
- `fronts.ps1` draws each frame as a picture of risen glass.
- `jelly.pl` then shows each picture only in its own window of its run. Each run lives under a Condition that exists only around its seconds.
- The tap ripple is one ring picture, scaled up from the panel's centre while it fades.

**Tech Stack:** Perl 5 (core modules only), Windows PowerShell 5.1 with inline C# (`Add-Type`, System.Drawing), Watch Face Format XML, Gradle/AGP 8.6.1, adb, ffmpeg, Python 3 with PIL (scratch checks only).

**Spec:** `docs/superpowers/specs/2026-10-23-expedition-wave-fronts-design.md`

## Global Constraints

**Watch Face Format rules on Samsung's renderer:**
- Every `x`, `y`, `width` and `height` on a Group, PartImage or PartDraw is a whole number. A fraction drops the element on the watch.
- Every animated Group or PartImage carries `$AMB` (`<Variant mode="AMBIENT" target="alpha" value="0" />`). Without it, the renderer hangs in ambient.
- Every Condition holds one `<Compare>`.

**Panel outlines, in face px, as `jelly.pl` runs the bulges:**
- Dials: r 43.5 round (125,225) `heart`, (325,225) `steps` and (225,325) `readout`.
- Pill: caps r 25.5 round (178,135) and (272,135), with edges at y 109.5 and 160.5.
- Window: dome r 32 round (225,96.5), with a flat base at y 96.5 from x 193 to 257.

**The wave between two bulges, P1 and P2:**
- **Target T:** the midpoint of the two bulges' destinations.
- **M and c:** M is the midpoint of P1 and P2. c is |P2 − P1|. A frame with c < 1 px is skipped.
- **n:** the unit normal to P1–P2, pointing to T's side.
- **Room:** the distance from M along n to the outline.
- **Sag:** min(0.22 × c, 0.75 × room).
- **Curve:** the quadratic P1 → K → P2, where K = M + 2 × sag × n.

**Heights:**
- Risen glass under the hands: hour 9, minute 9, seconds 6.75 (from 6, 6, 4.5).
- Wave, snap-back and tap-ring glass: across, a sine-shaped swell, 6.75 px high at its crest on the line and back down to the glass's level 10 px either side (`RidgeAlong`'s `half`), easing in and out. The user arrived at this on 2026-10-23 in place of the seconds hand's flat-topped profile.
- Edge bulges: `Hgt` 5.25 (from 3.5).

**Timing**, using `jelly.pl`'s existing constants:

| What | Duration | Easing | Frame rate | Fade and sink |
|---|---|---|---|---|
| Pulses | `$RUN` = 2 s | `@EASE` = (0.4, 0, 0.6, 1) | `$PFPS` = 8 | `$FADE` and sink (scaleY 1 → 0.2, curve `0.5 0 1 1`) over `$SINK` = 1.5 s |
| Snap backs | `$D` = 1.5 s | `@EASE` | `$FPS` = 16 | `$FADE` over 1.5 s, no sink |
| Taps | `$DT` = 1 s | scale 0.1 → 1, curve `0.2 0.6 0.4 1` | `$FPS` = 16 | alpha `$FADE` |

**Picture names:**
- `exp_front_<panel>_s<SS>_f<kk>`, with SS the run's start second and kk the frame, both two digits.
- `exp_front_<dial>_snap_f<kk>`.
- `exp_ripple_dial` and `exp_ripple_pill`.

**Run gating:**
- A run starting at second S is drawn only under `((([SECOND] + (61 − S)) % 60) <= 3)`, so from S−1 to S+2.
- Its windows are triggered by `((([SECOND] + (60 − S)) % 60) <= 2)`.

**Toolchain:** `JAVA_HOME="C:/Program Files/Android/openjdk/jdk-21.0.8"`, `ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"`, and `ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`.

**The watch:**
- The watch is the `adb devices` entry `192.168.1.40:<port>` (or `adb-RZAWB01Z3VJ…`). Its serial is called `$W` below.
- The face installs in place with `adb install -r`, since every app is now on this PC's key. Never uninstall anything.
- The renderer process is `com.samsung.wear.watchface.runtime`.

**Commits:** only with the user's OK, on a branch `expedition-wave-fronts`. Each task ends with a checkpoint listing the files it should have changed.

## Review Focus

1. **Minute wrap:** a pill or window run starting at second 58 or 59 must stay drawn and keep playing into seconds 0 and 1. Task 5's gating check evaluates every run's Condition and trigger for all 60 seconds.
2. **Ambient:** every new animated element needs `$AMB`, or the face freezes black in ambient. Task 5 checks every animated element in the strings `window_xml` and `ripple_xml` return. Many existing elements carry it on a parent instead: 107 of today's 229.
3. **Stale pictures:** if the plan changes after `fronts.ps1` ran, the face would show the old curves. Task 5 refuses to run when the recomputed plan differs from `fronts.txt`, or when `fronts.txt`'s SHA-256 differs from the one `fronts.ps1` recorded in `fronts_frames.txt`.
4. **Pulse counts:** a panel can have a tick with one pulse (the other held off the face) or, by a bug, more than two. Task 3 dies on more than two and reports the one-pulse ticks, which get no wave.
5. **Meeting bulges:** frames where the two bulges meet (the snap back's first frame, the pulses' last) must be skipped without breaking their run. Task 3 checks every run keeps at least 3 frames, and that its skipped frames are only at its start or end.

---

### Task 1: Baseline on the watch

**Files:**
- Create: `docs/superpowers/plans/2026-10-23-expedition-wave-fronts-measurements.md`

**Interfaces:**
- Produces: the baseline CPU (renderer % and total, averaged over 60 s) and renderer memory (`TOTAL PSS`, `Graphics`) that Task 6 compares against.

- [ ] **Step 1: Find the watch and confirm today's face is up**

Run: `"$ADB" devices -l` and `"$ADB" -s $W shell dumpsys package com.liquiwatch.face.expedition | grep versionName`
Expected: the SM_R895F entry is `device`, and the face is `versionName=1.0-r37`. Ask the user to make sure LiquiWatch · Expedition is the active face.

- [ ] **Step 2: Keep the screen awake without touching settings**

In the background, run `while true; do "$ADB" -s $W shell input keyevent KEYCODE_WAKEUP; sleep 5; done` for the duration of the measurement. Stop it afterwards.

- [ ] **Step 3: Measure 60 s of CPU with no music**

Run: `"$ADB" -s $W shell 'top -b -d 1 -n 60 -p $(pidof com.samsung.wear.watchface.runtime)'`. Average the renderer's `%CPU`. Do one more pass without `-p` and average the total.
Expected: numbers in the README's range, roughly 30–100% of 200%.

- [ ] **Step 4: Record the renderer's memory**

Run: `"$ADB" -s $W shell dumpsys meminfo com.samsung.wear.watchface.runtime | grep -E "TOTAL PSS|Graphics"`

- [ ] **Step 5: With music, if the user plays some**

Ask the user to play music on the phone, then repeat Step 3.

- [ ] **Step 6: Write the measurements file**

Write the date, the face version, each number with how it was taken, and whether music was playing.

### Task 1b: Spike — does the renderer play animations inside a Condition that just turned on?

Nothing on the face does this yet, and the whole run design depends on it. Prove it before building anything.

**Files:** none kept. Scratch copies only, and the face is reinstalled from the repo afterwards.

- [ ] **Step 1: Build a test face**

Into a scratch copy of today's `watchface.xml`, just inside the `jelly_steps` group, add one run gated exactly as Task 5 will gate it, using an existing picture.
- Condition `((([SECOND] + 51) % 60) <= 3)`, which is true at seconds 9–12 (S = 10).
- Inside it, one `window_xml`-style pair of groups showing `exp_dial_bulge` at the top of the 3 o'clock dial during 0–1 s of a 2 s window animation, triggered by `((([SECOND] + 50) % 60) <= 2)`.

Build it with `./gradlew :watchfaces:assembleExpeditionDebug` from a scratch copy of the face XML swapped in, and install it with `adb install -r`.

- [ ] **Step 2: Record and look**

Record 70 s and look at second 10.
Expected: the bulge picture shows at the top of the 3 o'clock dial for about 1 s from second 10, and at no other time.
- If it does, carry on.
- If it doesn't, stop and tell the user: the run design needs the always-present, odd-or-even copies `jelly.pl` uses for taps instead.

- [ ] **Step 3: Put the real face back**

Restore the repo's `watchface.xml` and reinstall it.

### Task 2: Taller glass

**Files:**
- Modify: `tools/ring/ring_glass.ps1:258-268`. The heights in the `foreach ($h in …)` list become 9, 9 and 6.75. The comment's "6 px high (4.5 for the thin second hand, as its waves are: ring.pl)" becomes "9 px high (6.75 for the thin second hand, as the waves are: fronts.ps1)".
- Modify: `tools/jelly/bulges.ps1:18`, where `[double]$Hgt = 3.5` becomes `5.25`.
- Modify: `tools/jelly/jelly.pl:46-53`, so `%PIC` matches the sizes `bulges.ps1` prints.
- Regenerated: `watchfaces/src/expedition/res/drawable-nodpi/exp_ridge_*.png` and `exp_*_bulge*.png`, and `tools/ring/frames.txt`.

**Interfaces:**
- Produces: the taller bulge pictures and their `%PIC` entries `[name, w, h, rx, ry]`, which Tasks 3 and 5 keep using unchanged.

- [ ] **Step 1: Change the heights and run both scripts**

Run: `powershell -File tools\ring\ring_glass.ps1`, then `powershell -File tools\jelly\bulges.ps1`
Expected: `bulges.ps1` prints six lines of `name w h rx ry`.

- [ ] **Step 2: See the check fail, then copy the sizes**

Run: `perl tools/jelly/jelly.pl < tools/ring/expedition_source.xml > /dev/null`
Expected: FAIL with `… copy what bulges.ps1 prints into %PIC` (if any size changed). Copy the six printed lines into `%PIC` and rerun. Expected: PASS, printing its stretch count to stderr.

- [ ] **Step 3: Only the right pictures changed**

Run: `git status --short watchfaces/src/expedition/res/drawable-nodpi | grep -v -E 'exp_ridge_|_bulge'`
Expected: empty.
- If other pictures show as changed, compare their pixels with Python PIL.
- If the pixels are identical, restore those files with `git checkout`. If the pixels differ, stop and report.

- [ ] **Step 4: Regenerate the face, so the taller bulges sit right**

Run: `perl tools/jelly/jelly.pl < tools/ring/expedition_source.xml | perl tools/ring/ring.pl > "$SCRATCH/watchface.xml" && python -c "import xml.etree.ElementTree as E; E.parse(r'$SCRATCH/watchface.xml')" && cp "$SCRATCH/watchface.xml" watchfaces/src/expedition/res/raw/watchface.xml`
Expected: exit 0. Task 3 compares its own output against this file.

- [ ] **Step 5: Checkpoint**

`git status` lists only the files above, plus `watchface.xml`.

### Task 3: The wave plan (`PLAN=1 jelly.pl`)

**Files:**
- Modify: `tools/jelly/jelly.pl`. All changes are in the pulses block (lines 140–266), plus new subs above it.
- Create (generated): `tools/jelly/fronts.txt`

**Interfaces:**
- Consumes: the pulse specs `[$geom, $end, \@v]` and the snap-back values `$o`, `$up` and `$snap` already computed in the pulses block.
- Produces:
  - **`fronts.txt`**, one item per line, with numbers in face px to 3 decimals:
    - `outline <panel> circle <cx> <cy> <r>`
    - `outline pill stadium <x1> <x2> <cy> <r>`
    - `outline window dome <cx> <cy> <r>`
    - `ripple <name> <panel> 7`: a ring 7 px inside that panel's outline.
    - `front <name> <panel> <x1> <y1> <kx> <ky> <x2> <y2> <height> <alpha>`, where alpha runs from 0 to 1.
  - **`@RUNS`**, a list of `{ panel, start, dur, fps, frames => [ { name, t0, t1, line } ] }`. Here `dur` is the window animation's duration (2 for pulses, 1.5 for snaps) and `fps` its frame rate (`$PFPS` = 8 for pulses, `$FPS` = 16 for snaps). `t0` and `t1` are fractions of `dur`. Task 5 reads this.
  - **`sub field($panel, $x, $y)`**: the signed distance to the panel's outline, negative inside.
    - Circle: |p − c| − r.
    - Stadium: the distance to the segment (x1..x2, cy), minus r.
    - Dome: max(|p − c| − r, y − cy).

- [ ] **Step 1: Write the checks first**

Inside the new `plan_fronts`, every frame dies with a named message unless all of these hold:
- **Ends:** each end is within 0.5 px of its bulge. The bulge is recomputed with `bulge_at` from its own spec, not from the pair.
- **Inside:** every one of 65 samples along the curve, apart from those within 3 px of an end, has `field <= -1`.
- **Bow:** `(K − M)·(T − M) >= 0`.
- **Frame count:** each run keeps at least 3 frames, and skipped frames (c < 1) are only at its start or end.
- **Pulse count:** a panel with more than 2 pulses in one second dies.

- [ ] **Step 2: Add `curve`, `bulge_at` and `plan_fronts` (`$ENV{PLAN}` writes the file)**

- **`sub curve($tau, $x1, $y1, $x2, $y2)`:** the CSS-style cubic-bezier progress the watch uses. Clamp tau to [0,1], bisect u for `bez(u, x1, x2) == tau` over 60 steps, and return `bez(u, y1, y2)`. `bez` already exists.
- **`sub bulge_at($geom, $from, $to, $t, $dur)`:** returns `[x, y]`. With `p = curve($t / $dur, @EASE)` and `a = $from + ($to − $from) × p`:
  - an arc gives `(cx + r sin a°, cy − r cos a°)`;
  - a line gives `(a, y)`.
- **Panel names:** `$pulse`, `$line` and `$round` take a panel name first, and the dial loop passes `$n`. Each spec records it as its 4th element. The snap backs push `[$n, $cx, $cy, $r, $o, $up, $snap]` onto `@SNAPS`.
- **Pulse runs:** for each panel and second S, take the specs whose `$v->[S % 4]{S}` exists. With exactly two, it's a run with `dur` 2.
  - Frames go k = 0, 1, … with t = k / `$PFPS`, while t < `$SINK` and opacity(t) ≥ 0.02.
  - opacity(t) = min(255, max(0, −curve(t / 1.5, 0, −900, 0.6, 0))) / 255, which is today's `$FADE` on a 0 → `$ON` value.
  - height(t) = 6.75 × (1 − 0.8 × curve(t / 1.5, 0.5, 0, 1, 1)).
  - Window: `t0 = t / 2` and `t1 = (t + 1/8) / 2`.
  - T is the midpoint of `bulge_at(…, $dur)` for both bulges.
- **Snap runs:** from `@SNAPS`, `dur` is 1.5 and t = k / 16. The bulges go from o to o + up and from o + 360 to o + up. There's no sink. Window: `t0 = t / 1.5`, `t1 = (t + 1/16) / 1.5`.
- **The curve itself:** per the Global Constraints. The room is marched along n in 0.25 px steps until `field >= 0`.
- **`PLAN=1`:** writes `fronts.txt` next to the script: the `outline` lines, then `ripple exp_ripple_dial heart 7` and `ripple exp_ripple_pill pill 7`, then the `front` lines. It prints `N runs, M frames, K one-pulse ticks` to stderr and exits 0 without printing the face.

- [ ] **Step 3: Run it**

Run: `PLAN=1 perl tools/jelly/jelly.pl < tools/ring/expedition_source.xml`
Expected: exit 0, about 40–60 runs and 500–700 frames, and `fronts.txt` written. `grep -o 'exp_front_steps_s[0-9]*' tools/jelly/fronts.txt | sort -u` lists s11 to s19.

- [ ] **Step 4: See a check fire**

Run: `perl -pe 's/0\.75 \* \$room/3 * \$room/' tools/jelly/jelly.pl > "$SCRATCH/jelly_bad.pl"; PLAN=1 perl "$SCRATCH/jelly_bad.pl" < tools/ring/expedition_source.xml`
Expected: FAIL with the inside-the-glass message. Use whatever expression Step 2 wrote for the room factor. `$SCRATCH` is the session scratchpad.

- [ ] **Step 5: Checkpoint**

`jelly.pl` and `fronts.txt` changed. The face output, from a run without `PLAN`, is unchanged: diff it against today's `watchface.xml`.

### Task 4: The wave pictures (`fronts.ps1`)

**Files:**
- Create: `tools/ring/RingGlass.cs`. This is `ring_glass.ps1`'s C# class moved out of its here-string unchanged, plus the new methods below.
- Modify: `tools/ring/ring_glass.ps1:18`. The here-string is replaced by `Add-Type -ReferencedAssemblies System.Drawing -Path (Join-Path $PSScriptRoot 'RingGlass.cs')`.
- Create: `tools/jelly/fronts.ps1`, which loads the same file.
- Create (generated): `watchfaces/src/expedition/res/drawable-nodpi/exp_front_*.png`, `exp_ripple_dial.png`, `exp_ripple_pill.png` and `tools/jelly/fronts_frames.txt`

**Interfaces:**
- Consumes: `fronts.txt`, from Task 3.
- Produces: `fronts_frames.txt`.
  - Its first line is `sha256 <hex of fronts.txt>`.
  - Then one line per picture, `<name> <x> <y> <w> <h>`. These are whole face px, the box the picture is placed in. Ripple boxes are around the panel named in their `ripple` line.

- [ ] **Step 1: Move the class, and prove nothing changed**

Move the class to `RingGlass.cs` and run `powershell -File tools\ring\ring_glass.ps1`.
Expected: `git status --short watchfaces/src/expedition/res/drawable-nodpi` shows nothing new beyond Task 2's pictures.

- [ ] **Step 2: Add the new methods to `RingGlass`, next to `Ridge`, sharing its lighting**

- **`public static double Field(string kind, double[] o, double x, double y)`:** the same formulas as `jelly.pl`'s `field`.
- **`public static double[] Quad(double x1, double y1, double kx, double ky, double x2, double y2, int n)`:** n + 1 points on the quadratic, as an interleaved x,y array.
- **`public static double[] Outline(string kind, double[] o, double inset)`:** a closed polyline about 1 px apart, `inset` px inside the outline.
- **`public static Bitmap RidgeAlong(double[] pts, bool closed, double height, double hw, double fw, double alpha, string kind, double[] o, out int x0, out int y0)`:** risen glass along the polyline.
  - **Profile:** the crest runs `hw` px either side of the line, and the glass falls away over `fw` px.
  - **Lighting:** `Ridge` and `RidgeAlong` call one shared private method for the per-pixel lighting, taking `t` (the fall fraction), `height`, `fw` and the screen fall direction. Lift it out of `Ridge`, which then calls it after its `Rot`. `RidgeAlong`'s fall direction is the unit vector from the nearest polyline point to the pixel.
  - **Alpha:** multiplied by `alpha` and by `Sat(-Field - 1)`, which clips it from 1 px inside the edge.
  - **Crop:** cropped to its non-zero pixels. `x0` and `y0` are the crop's whole-pixel origin in face px.

Rerun `ring_glass.ps1`. Expected: still no picture changes beyond Task 2's, which shows the lighting is unchanged after the lift.

**`fronts.ps1`:**
- Load `RingGlass.cs`. Read `fronts.txt` and delete every existing `exp_front_*.png` and `exp_ripple_*.png` first.
- Draw fronts with `Quad(…, 48)`, `closed=false`, `hw=5`, `fw=8`, and the line's height and alpha.
- Draw ripples with `Outline(kind, o, 7)`, `closed=true`, height 6.75, alpha 1.
- Save each picture as PNG. Write `fronts_frames.txt`, starting with `sha256 ` plus `(Get-FileHash fronts.txt -Algorithm SHA256).Hash.ToLower()`.

- [ ] **Step 3: Run it**

Run: `powershell -File tools\jelly\fronts.ps1`
Expected: it prints the picture count and the total of w × h × 4 bytes. `(Get-ChildItem …\exp_front_*.png).Count` equals the number of `front` lines, and `fronts_frames.txt` has the `sha256` line, a line for each picture, and the two ripples.

- [ ] **Step 4: Look at them**

A throwaway Python script in `$SCRATCH` lays `exp_dial_body.png` and `exp_dial_rim.png` at (273,173) on a dark 450×450 canvas. It overlays the frames of `exp_front_steps_s15_f00`…`f11` at their `fronts_frames.txt` boxes, one tile per frame, and saves a contact sheet. Do the same for `exp_front_steps_snap_*` and one pill run. Open the sheets with Read.
Expected:
- the ridge runs rim to rim and bows towards the dial's lower left, where the exit is;
- it sinks and fades frame by frame;
- the snap back sweeps from lower left to upper left;
- nothing is drawn outside the glass.

- [ ] **Step 5: Checkpoint**

`RingGlass.cs`, `ring_glass.ps1`, the new script, the pictures and `fronts_frames.txt`.

### Task 5: The face (`jelly.pl` XML, the ripple, the old wave out)

**Files:**
- Modify: `tools/jelly/jelly.pl`:
  - **Old taps out:** delete `sub ring_tap`, the dials' `ring_tap` loop under `# the dials: a tap sends bulges…`, and the pill's tap block (`@loop`, `$part`, `$along`, `$tap_pill`). Keep `$add{window} = ''`.
  - **New code:** the frame and ripple XML, and the run checks.
- Modify: `tools/ring/ring.pl`:
  - Delete the waves inside the `for my $n (qw(hour minute second))` loop: from `next unless $n eq 'second';` through the `$add .= join '', map { … "wave_$_" … } 0, 1` statement. Keep the loop's own closing `}`, which ends that last line after `0, 1`.
  - Delete `sub panel_d`, which only the waves used.
- Regenerated: `watchfaces/src/expedition/res/raw/watchface.xml`

**Interfaces:**
- Consumes: `@RUNS` and `field` from Task 3. `fronts_frames.txt`, from Task 4, is read into `%FRAME = (name => [x, y, w, h])`.
- Produces:
  - `sub gate_expr($S)` and `sub trig_expr($S)`, the exact expression strings the XML uses.
  - `sub window_xml($trig, $dur, $fps, $t0, $t1, $name, $x, $y, $w, $h)`.
  - `sub ripple_xml($n, $cx, $cy, $pic)`.

- [ ] **Step 1: Write the checks first**

At the top of a normal run (no `PLAN`):
- **Stale plan:** die with `re-run PLAN=1 jelly.pl and fronts.ps1` unless the recomputed `fronts.txt` text equals the file.
- **Stale pictures:** die with `re-run fronts.ps1` unless `fronts_frames.txt`'s `sha256` line equals `Digest::SHA::sha256_hex` of `fronts.txt`'s bytes.
- **Frames list:** die unless every planned picture is in `fronts_frames.txt` and its PNG's header size matches. Reuse the `%PIC` check's header read.
- **Gating:** for every run and every second 0–59, take the strings `gate_expr(S)` and `trig_expr(S)` return. Put the second in for `[SECOND]` and `<` for `&lt;`, and evaluate the result in Perl. Die unless the gate is true exactly at S−1 … S+2 and the trigger exactly at S … S+2, mod 60.
- **Ambient:** in each string `window_xml` and `ripple_xml` return, die if any `<Group` or `<PartImage` holding a `<Transform` with an `<Animation` lacks `$AMB` as its own child.

- [ ] **Step 2: Implement the emission**

- **`window_xml`:** nests as the existing stretches do. Every `anim` here passes `$fps`: `$PFPS` = 8 for pulse runs, so the face keeps drawing 8 frames a second while a panel is crossed, and `$FPS` = 16 for snap backs.
  - If `t0 > 0.001`, an outer Group `x,y,w,h` with `alpha="0"`, `$AMB` and `<Transform target="alpha" value="($trig ? $ON : 0)">` plus `anim($dur, from_curve($t0), $fps)`.
  - If `t1 < 0.999`, an inner Group `0,0,w,h` with `$AMB`, the same transform and `anim($dur, until_curve($t1), $fps)`.
  - `<PartImage x="0" y="0" width=w height=h>$AMB<Image resource=name /></PartImage>`.
  - When `t0 <= 0.001`, the until Group alone takes `x,y`.
- **Each run:**

  ```
  <Condition><Expressions><Expression name="c0">((([SECOND] + G) % 60) &lt;= 3)</Expression></Expressions><Compare expression="c0">…frames…</Compare></Condition>
  ```

  The Expression is `gate_expr(S)`, with G = 61 − S. The frames use `$trig = trig_expr(S)` = `"((([SECOND] + " . (60 − S) . ") % 60) &lt;= 2)"`.
- **`ripple_xml`:** two copies, one for each condition `tap($n)` returns.
  - A Group at the picture's box, offset from the planned panel's centre to ($cx, $cy). Its pivot is that centre as fractions of the box, with `alpha="0"` and `$AMB`.
  - `<Transform target="alpha" value="(w ? $ON : 0)">` with `anim($DT, $FADE)`.
  - `scaleX` and `scaleY` transforms `(w ? 1 : 0.1)` with `anim($DT, '0.2 0.6 0.4 1')`.
  - The PartImage inside.
- **Panels:** heart (125,225), steps (325,225) and readout (225,325) use `exp_ripple_dial`; the pill (225,135) uses `exp_ripple_pill`.
- **Order in each `$add{panel}`:** the ripple, then that panel's runs, then the existing pulses and snap bulges.

- [ ] **Step 3: Build the face and see the checks pass**

Run: `perl tools/jelly/jelly.pl < tools/ring/expedition_source.xml | perl tools/ring/ring.pl > "$SCRATCH/watchface.xml" && python -c "import xml.etree.ElementTree as E; E.parse(r'$SCRATCH/watchface.xml')" && cp "$SCRATCH/watchface.xml" watchfaces/src/expedition/res/raw/watchface.xml`
Expected:
- both scripts exit 0 and the XML parses;
- `grep -c 'wave_' watchfaces/src/expedition/res/raw/watchface.xml` is 0;
- `grep -c 'exp_front_' …` equals the frame count.

The face only gets overwritten after everything passes.

- [ ] **Step 4: See a check fire**

Touch one line of `fronts.txt` (change a digit), rerun the Step 3 pipeline into `$SCRATCH` only, and confirm it fails with `re-run PLAN=1 jelly.pl and fronts.ps1`. Then restore it with `PLAN=1 perl …`.

- [ ] **Step 5: Gradle build**

Run, from the repo root with the Global Constraints' `JAVA_HOME` and `ANDROID_HOME` exported: `./gradlew --console=plain :watchfaces:assembleExpeditionDebug`
Expected: `BUILD SUCCESSFUL`. Report the APK's size against today's 4.7 MB.

- [ ] **Step 6: Checkpoint**

`jelly.pl`, `ring.pl` and `watchface.xml`.

### Task 6: On the watch, and the README

**Files:**
- Modify: `README.md`:
  - *Jelly glass* (line 38): the waves, the snap back's wave, taps and heights.
  - *The minute ring and its glass*: "The waves" (line 135) and the steps that list ring.pl's waves.
  - Every "6 px", "4.5" or "3.5" height of the risen glass and bulges.
  - *Making it again*: add `PLAN=1 jelly.pl` and `fronts.ps1` to the order.
- Modify: `docs/superpowers/plans/2026-10-23-expedition-wave-fronts-measurements.md`, adding the after numbers.

**Interfaces:**
- Consumes: the APK from Task 5, and the baseline from Task 1.

- [ ] **Step 1: Install in place**

Run: `"$ADB" -s $W install -r watchfaces/build/outputs/apk/expedition/debug/watchfaces-expedition-debug.apk`
Expected: `Success`, with versionName still `1.0-r37`. The face shows after 30–40 s, as the README warns.

- [ ] **Step 2: Record the crossings and snap backs**

With the wake loop from Task 1 running:
1. Note `"$ADB" -s $W shell date +%S`.
2. Run `"$ADB" -s $W shell screenrecord --time-limit 70 /sdcard/fronts.mp4`.
3. Pull it to `$SCRATCH` and run `ffmpeg -i fronts.mp4 -vf fps=16 frames/%04d.png`.

Make contact sheets of the steps dial at seconds 11–22, the heart and readout dials at their crossings, and the pill and window at seconds 55–5. Open them with Read.
Expected, matching Task 4's sheets:
- the wave's ends are on the bulges in every frame;
- it bows forward;
- there's no sliver along a rim;
- the snap back sweeps the dial;
- runs at seconds 58–59 carry on into 0–1.

- [ ] **Step 3: Record taps**

While recording 10 s, run `"$ADB" -s $W shell input tap 325 225`, then the same at `125 225` and `225 325`. Tap `225 135` only while no song is playing, since then the pill opens Cover Display.
Expected: a ring grows from each tapped panel's centre and is gone by its edge, and the panel turns its page as before.

- [ ] **Step 4: Measure, as in Task 1 Steps 3–5**

Expected:
- **CPU:** the renderer's is within about 10 points of the baseline.
- **Memory:** the renderer's `TOTAL PSS` is within 4 MB of the baseline. Task 4 printed the unpacked size of the wave pictures, about 8 MB if every one is held at once.

If CPU is over:
- set the pulse waves' frame step to 1/6 s, rerun Tasks 3–5 and measure again;
- ask the user before any cut that changes the look.

If memory is over: draw the waves at half size and place them at double that size. In `fronts.ps1`, draw at 0.5 scale and write each box at full face size. In `jelly.pl`, nothing changes, since the boxes are already in face px. The face already does this with its soft masks. Look at the sheets again for a softened crest, then measure again.

- [ ] **Step 5: Update the README and the measurements file**

Describe what the face now does, in the README's voice, with the measured cost.

- [ ] **Step 6: Checkpoint**

The README, the measurements file and the face are on the watch. Show the user the contact sheets.
