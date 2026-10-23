# Expedition: waves that run with the edge bulges

Date: 2026-10-23. Status: design agreed in chat, awaiting review of this write-up.

## What was asked

- The wave should curve to fit the edge waves and keep pace with them from where they start.
- The snap back should get the new wave too.
- A tap should start a wave at the centre of the glass element, faded out by the time it reaches the edge.
- The hands' risen glass and the waves should stand taller.

Agreed in chat:
- **Shape:** "B, bowing forward from the first frame".
- **Panels:** all five.
- **Taps:** the ripple replaces today's tap bulges.
- **Heights:** 1.5× the glass, and the same height for the waves.
- **Drawing method:** the flipbook.

## Words used here

| Term | Meaning |
|---|---|
| **Panels** | The five glass elements: the dials at 9, 3 and 6 o'clock (`heart`, `steps`, `readout`), the date pill (`pill`), and the day and night window (`window`). |
| **Edge bulges** | The bumps of a panel's own rim that `tools/jelly/jelly.pl` runs round its edge. |
| **Pulses** | The edge bulges sent at each tick while the seconds hand crosses a panel. There are two per tick, one where the hand's line meets the edge on each side. Each runs to its end of the glass over 2 s, eased (0.4, 0, 0.6, 1), at 8 frames a second. It fades (`$FADE`) and sinks (scaleY 1 to 0.2) over 1.5 s. |
| **Snap back** | The two bulges `jelly.pl` sends a tick after the hand leaves a dial. They start where it left and run back round both ways to where it came in, over 1.5 s at 16 frames a second, fading. |
| **Today's wave** | `ring.pl`'s `wave_0` and `wave_1`: a copy of the seconds hand's risen glass, turned 20° ahead over each second while the hand is over a panel. |
| **Today's tap** | `jelly.pl`'s `ring_tap` (the dials) and `$tap_pill`. Four bulges from the top and bottom of the rim meet at the sides in 1 s. |

## What the face does

### The wave (replaces today's wave)

At every tick while the seconds hand crosses a panel, a ridge of risen glass stretches between that tick's two pulses.

- **Ends:** in every frame, its ends are on the two pulses, wherever each has got to on its own path (round an arc, or along a straight edge).
- **Bow:** it bows forward and stays inside the glass.
  - Its middle stands off the midpoint of the line between the two ends, towards the side where the pulses are heading. That target is the midpoint of the two pulses' destinations. On a dial that's the exit point. On the pill and the window it's the ends of the edges or caps the pulses run along.
  - It stands off by min(0.22 × the ends' distance apart, 0.75 × the room). The room is the distance from that midpoint to the glass's edge in that direction.
  - The curve is the quadratic through the two ends and that middle point. It's straight when the target lies on the line between the ends.
- **Timing:** it runs, fades and sinks with its pulses: the same 2 s, the same fade and sink over 1.5 s, at their 8 frames a second.
  - Frame k shows the wave as it is k/8 s into the run, and is shown from k/8 s to (k + 1)/8 s.
  - So there are 12 visible frames a run.
- **Missing pulse:** if a tick has only one pulse on a panel (the other is held off the face), it gets no wave.

### The snap back

The same kind of wave runs between the two snap-back bulges.

- It sweeps across the dial from where the hand left towards where it came in, bowing that way.
- It runs over 1.5 s at 16 frames a second, so frame k is shown from k/16 s to (k + 1)/16 s, giving 24 frames.
- It fades with its bulges, using `$FADE` over 1.5 s. It doesn't sink, because they don't.

### Taps (replace today's tap)

A tap on a dial or the date pill sends a ring of risen glass outward from the panel's centre.

- **Shape:** a circle on the dials, and the pill's own outline on the pill.
- **Motion:** it grows from 10% to full size in 1 s, eased (0.2, 0.6, 0.4, 1), at 16 frames a second.
- **Fade:** it fades from full to nothing over the same second. At full size it sits just inside the edge, so it's gone by the time it gets there.
- **Picture:** it's one picture scaled about the centre. Its ridge is thin and faint while small and full-width at the edge, like a ripple forming.
- **Trigger:** the same page odd-or-even flip as today, with two copies taking turns.
  - Its alpha curve keeps the copy that resets unseen, as today's tap bulges are.
- The day and night window has no tap, as today.

### Heights (1.5×)

| Glass | Before | After |
|---|---|---|
| Risen under the hour and minute hands | 6 px | 9 px |
| Risen under the seconds hand | 4.5 px | 6.75 px |
| Waves, snap-back waves and tap rings | 4.5 px | 6.75 px |
| Edge bulges | 3.5 px | 5.25 px |

- The waves, snap-back waves and tap rings are, across, a sine-shaped swell: from the glass's own level up to a crest on the wave's line, 6.75 px up, where it meets the bulges, and back down to that level, 10 px either side, easing in and out so it has no edge. The user arrived at this in chat on 2026-10-23, in place of the seconds hand's flat-topped profile: first "a curved cylinder", then "a dip after the peak, somewhat like a sin curve", then "up then dip down", then "dip only enough to join back at original length".
- Each falloff stays as it is.

### Unchanged

Everything else stays as it is:
- the hands, their melts, the minute ring and the panels' pages;
- the pulses' and snap backs' own bulges (only taller);
- when anything moves.

## How it's built

The face's XML and pictures are generated by the scripts in `tools/`. The pattern is the same as `ring_glass.ps1`, which writes `frames.txt` for `ring.pl` to read.

| File | Change |
|---|---|
| `tools/jelly/jelly.pl` | **Pairs** each tick's two pulses on a panel, and each dial's two snap-back bulges. **Plans:** with `PLAN=1`, it works out every wave frame's curve, fade and sink, and writes `tools/jelly/fronts.txt`, then stops. **Emits:** on a normal run, it reads `tools/jelly/fronts_frames.txt` and places each frame picture, shown only in its window of its run. The windows use the same `from_curve` and `until_curve` the stretches of a bulge's way use. **Taps:** the ring replaces `ring_tap` and `$tap_pill`. |
| `tools/jelly/fronts.ps1` (new) | Draws each wave frame as risen glass along its curve, using the lighting of `ring_glass.ps1`'s `Ridge`, with no turn. Its slopes are brighter facing the top left and darker facing away, and the glass is a touch milkier as it rises. It's drawn with its frame's fade and sink built in, and clipped to its panel from 1 px inside the edge, where the bulge takes over. It also draws the two tap rings (`exp_ripple_dial`, `exp_ripple_pill`), and writes each picture's position and size to `tools/jelly/fronts_frames.txt`. |
| `tools/ring/ring_glass.ps1` | The hands' risen glass heights: 9, 9 and 6.75. |
| `tools/jelly/bulges.ps1` | Edge bulge height 5.25, the default `Hgt`. Its printed sizes go into `jelly.pl`'s `%PIC`. |
| `tools/ring/ring.pl` | Today's wave (`wave_0`, `wave_1`) is taken out. The hands' risen glass (`ridges`) stays. |
| `README.md` | *Jelly glass*, the waves, *The minute ring and its glass*, and the heights are updated. |

- **Picture names:**
  - `exp_front_<panel>_s<start second>_f<frame>` for the waves;
  - `exp_front_<dial>_snap_f<frame>` for the snap backs;
  - `exp_ripple_dial` and `exp_ripple_pill` for the tap rings.
- **Placement:** the waves and the tap rings go into each panel's `jelly_<panel>` group, beside its bulges. That puts them over its glass and under the hands.
- **One run present at a time:** each run sits under a Condition that's true only from one second before it starts until its last frame. Its windows are triggered as its start second arrives. When the Condition ends, the run is simply gone, so it never plays back at all.
  - This is a change from what was said in chat, where runs were to go back unseen while the face animated anyway. It's simpler, and no more costly.
  - The seconds are counted round the minute, so a run that starts at second 59 carries on into second 0.
  - Each Condition holds one comparison, as every Condition on the face does.
- **Build order:**
  1. `powershell -File tools\ring\ring_glass.ps1`
  2. `powershell -File tools\jelly\bulges.ps1`, then copy the printed sizes into `%PIC`
  3. `PLAN=1 perl tools/jelly/jelly.pl < tools/ring/expedition_source.xml`
  4. `powershell -File tools\jelly\fronts.ps1`
  5. `perl tools/jelly/jelly.pl < tools/ring/expedition_source.xml | perl tools/ring/ring.pl > watchfaces/src/expedition/res/raw/watchface.xml`
- **Expected size:** about 600 wave pictures. The real count and the added picture memory get reported.

## Checks

### In the generators

They stop with an error, as they already do:
- **Ends:** in every frame, the wave's ends are within 0.5 px of its two bulges at that moment, worked out with the same easing.
- **Inside:** every point of every wave curve is at least 1 px inside its panel, apart from the last 3 px at each end, which run into the bulges on the rim. A panel's outline here is the one `jelly.pl` runs the bulges along: the dials at r 43.5; the pill's caps at r 25.5 with its edges at y 109.5 and 160.5; the window's dome at r 32 on its base at y 96.5.
- **Bow:** every wave bows towards its pulses' target. A wave that is straight, because its target lies on the line between its ends, is allowed.
- **Run windows:** each run's Condition covers all of its frames, and starts at least one second before its first frame.
- **Lists match:** `fronts_frames.txt` and the pictures agree on names and sizes.
- **Existing checks:** whole-pixel positions, balanced brackets, `%PIC` against the bulge pictures.

### On the watch (Galaxy Watch4 Classic)

The face is installed with `./gradlew :watchfaces:installExpeditionDebug`. It updates in place, since it now has this computer's key.
1. **Looks:** record the screen (`adb shell screenrecord`) through the 3 o'clock dial's crossing (seconds 11 to 19) and its snap back (second 21), and through 12 o'clock for the pill and the window. Step through the frames (`ffmpeg`) and check:
   - the ends sit on the bulges;
   - each wave bows forward;
   - there's no sliver along a rim;
   - the snap back sweeps across the dial.
2. **Taps:** send taps with `adb shell input tap` on each dial and the pill, and record. Check the ring starts at the centre and is gone by the edge.
3. **Cost:** measure the watch's CPU over a minute with the screen on (`top`), first with today's face and then with the new one. Measure once without music, and once with music if some is playing.
   - **Target:** within about 10 points of today's face.
   - **If over:** the first cut is the waves' frame rate, 8 to 6 a second. Anything that changes the look is agreed first.
   - The added picture memory is reported too.
