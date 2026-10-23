# Expedition wave fronts: measurements

## Setup

- **Watch:** Galaxy Watch4 Classic (SM-R895F), over adb on Wi-Fi.
- **Renderer process:** `com.samsung.wear.watchface.runtime`.
- **Screen:** kept on by sending `KEYCODE_WAKEUP` every 5 s. No setting was changed, and the screen was awake at the end of every run.
- **CPU:** `top -b -d 1 -n 61 -p <renderer pid>`, ignoring the first sample.
  - **Renderer:** its `%CPU`, averaged.
  - **Total:** the cpu line's total minus idle, averaged.
  - Both are out of 200%, for the watch's two cores.
- **Memory:** `dumpsys meminfo com.samsung.wear.watchface.runtime` after each run.

## Baseline: today's face (Expedition 1.0-r37, from the repo), 2026-10-23 about 00:50

A song was playing (Cover Display's album art on the face, the song on the pill). There was no run without music, because that would have meant pausing the user's music.

| Run | Renderer CPU | Total CPU used | TOTAL PSS | SWAP PSS |
|---|---|---|---|---|
| 1 (wake loop also polled `dumpsys power`) | 115.3% | 166.5% | 131,422 KB | 44,243 KB |
| 2 | 116.1% | 160.0% | 139,164 KB | 50,090 KB |
| 3 | 122.3% | 176.8% | 142,517 KB | 51,090 KB |
| **Mean** | **117.9%** | **167.8%** | **137,701 KB** | |

**Note:** the renderer's PSS drifted by 11 MB over these three minutes, as the watch swapped. The watch had 1.27 of 1.32 GB used and 643 MB of swap used. So a 4 MB memory budget is within the noise of single runs, and memory is compared on averages of three runs, alongside the wave pictures' exact unpacked size.

## After the change

(Task 6.)

## After: each run one animated picture, its bulges in it (2026-10-23, 03:31)

Same method, a song playing, on the charger. The face: 175 groups (299 with the waves on their own pictures and the bulges on transforms), 45 animated WebPs (1.04 MB).

| Run | Renderer CPU | Total CPU used | TOTAL PSS |
|---|---|---|---|
| 1 | 118.3% | 166.1% | 145,249 KB |
| 2 | 107.4% | 168.7% | (not read) |
| **Mean** | **112.9%** | **167.4%** | |

Before this change the same build with transform bulges measured 122.0% (the original face 118.4%) under the same conditions. Memory is one reading, inside the 11 MB drift noted above; a fair comparison still needs averaged runs of both faces.
