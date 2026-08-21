# PushGate

An Android app blocker with a taper plan and a camera-verified push-up toll.

Your chosen apps get a daily budget that shrinks a little every day for a week. When the budget
runs out, the app closes. If you want back in early, the camera comes on, a live skeleton tracks
your body, and you pay in real push-ups — counted with actual range of motion, plank integrity and
cadence checks, so half-reps and head-bobs score nothing.

Everything runs on the phone. No account, no server, no analytics, and the camera never records.

---

## What it actually does

| | |
|---|---|
| **Blocks apps** | An accessibility service watches which app comes to the front and interrupts blocked ones the instant they open. |
| **Tapers the budget** | Day 1 might be 60 minutes; by day 7 it is 10. Linear, per app, configurable. |
| **Charges push-ups** | Out of budget → do *N* push-ups → get *M* minutes. Each unlock the same day costs more than the last. |
| **Verifies the reps** | MediaPipe Pose on-device, 33 landmarks at 30fps, with a green skeleton overlay and per-rep form judging. |
| **Resists tampering** | Device-admin uninstall protection, a Strict Mode guard on the Settings screens that would switch it off, a cooldown before anything can be undone, and three independent watchdogs. |

---

## Install

1. Go to the [Releases page](../../releases) and download **PushGate-release.apk**.
2. Open it on an Android phone running **8.0 (API 26) or newer**.
3. Android will warn that it is from an unknown source. Allow it — that is normal for an APK that
   does not come from the Play Store.
4. Open PushGate and follow the setup.

To grab the whole project as a zip instead, use the green **Code → Download ZIP** button.

### Permissions it will ask for

| Permission | Why | Optional? |
|---|---|---|
| Accessibility service | The only API Android gives an app to notice and interrupt another app. | **Required** — nothing is blocked without it |
| Camera | Counting push-ups. Frames are processed live and never stored or sent. | **Required** for earned unlocks |
| Notifications | The permanent status line and the "protection is off" alert. | Recommended |
| Device admin | Blocks uninstall while a plan is running. No other powers — no wipe, no lock, no screen reading. | Optional, recommended |

---

## How the enforcement works

```
 blocked app opens
        │
        ▼
 AccessibilityService ── TYPE_WINDOW_STATE_CHANGED ──► foreground package
        │
        ├── not on the list ──────────────────────────► do nothing
        │
        ├── has a paid grant ─────────────────────────► allow, count down the grant
        │
        ├── has quota left ───────────────────────────► allow, count down the budget
        │                                                 │
        │                                                 └── hits zero ──┐
        │                                                                 ▼
        └── out of both ────────────────────────────────────────────► BLOCK SCREEN
                                                                          │
                                                          ┌───────────────┴──────────────┐
                                                          ▼                              ▼
                                                    walk away                    do the push-ups
                                                                                         │
                                                                          camera + MediaPipe Pose
                                                                                         │
                                                                          reps verified ─► grant written
                                                                                         │
                                                                                    app reopens
```

Time is accounted in the service itself — start and stop timestamps around each foreground
transition — rather than polling `UsageStatsManager`, so the countdown is accurate to the second
and the block lands the moment the budget is gone.

Time bought with push-ups is tracked separately from quota time, so paying for two minutes never
also costs you two minutes of tomorrow's budget.

---

## How push-ups are judged

`PoseAnalyzer` feeds CameraX frames to MediaPipe's `pose_landmarker_lite` in LIVE_STREAM mode.
Joint angles are computed from **world landmarks** (metric, hip-centred) rather than normalised
image coordinates, because image coordinates are stretched by the frame's aspect ratio and would
report a straight arm as bent in portrait.

A rep only counts if it survives every one of these:

| Check | What it stops |
|---|---|
| Elbow angle must cross **below** the down threshold and **back above** the up threshold | Half-reps |
| Shoulder–hip–knee must stay within the bend tolerance | Sagging hips, piked bums, head-bobbing |
| Torso must read as horizontal in the frame | Squats, sit-ups, nodding at the phone |
| Rep must take longer than the cadence floor | Frantic bouncing |
| Required landmarks must stay visible throughout | Ducking out of shot at the bottom |
| **One random rep per set must be held at the bottom** | Replaying a pre-recorded video of yourself |

That last one is the interesting one. The counter picks a random rep index at the start of every
set and demands a 1.2-second hold at the bottom of it. A video cannot know which rep will be
chosen, so a replay attack fails the set.

Three strictness presets ship — Forgiving, Standard, Strict — and each threshold is individually
tunable in Settings.

---

## How honest is the tamper resistance?

Straight answer: **very good against impulse, not perfect against determination.** Anyone claiming
otherwise on a non-rooted, non-device-owner Android is lying to you.

**What it stops:**

- Uninstalling the app (device admin blocks it)
- Walking into Settings → Accessibility and switching the service off (Strict Mode guard)
- Opening the app's own App Info page to force-stop or clear data (same guard)
- Swiping the app out of Recents (foreground service restarts itself)
- Killing the process (three watchdogs: WorkManager, AlarmManager, and boot receiver)
- Rebooting to escape (everything re-arms on `BOOT_COMPLETED`)
- Changing the price or the plan mid-urge (cooldown must be served first)

**What it does not stop:**

- Booting into **Safe Mode**, which disables third-party accessibility services
- A **factory reset**
- Setting the system clock forward (the daily rollover is wall-clock based)
- Someone who genuinely films themselves doing push-ups *and* happens to satisfy the random hold

The design bet is that all of these cost more effort than just doing the push-ups, which is the
actual goal. The gap between "annoying" and "impossible" is where the habit change happens.

If you want genuinely unbreakable, the app would need **device-owner** provisioning
(`adb shell dpm set-device-owner`), which allows OS-level package suspension and blocks Safe Mode —
at the cost of a one-time PC setup on a near-fresh device. That is not built here; this is the
no-PC-required tier.

### The Strict Mode guard, precisely

While Strict Mode is armed and no cooldown has matured, the accessibility service watches for
Settings and package-installer windows, and looks for **PushGate's own name** on screen. If it
finds it, it drops you to the home screen and explains why. The check is deliberately narrow — the
rest of Settings stays fully usable.

PushGate's own setup flow needs to send you into Settings (to enable the accessibility service, to
grant the camera permission, to activate device admin). `GuardBypass` opens a short, explicitly
declared window for exactly those detours, and it can only be opened from PushGate's own UI.

---

## Building it yourself

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew :app:assembleDebug
```

The release APK is signed with the debug key unless you provide your own via environment
variables — `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. That keeps
`assembleRelease` producing something installable out of the box.

**No Android Studio? Don't build locally at all.** Push to GitHub and the workflow in
`.github/workflows/build.yml` builds both APKs on every push and uploads them as artifacts. Push a
tag starting with `v` and it publishes a Release with the APKs attached:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

---

## Project layout

```
app/src/main/java/com/pushgate/app/
├── block/          Enforcement: accessibility service, block screen, watchdogs, device admin
│   ├── BlockerAccessibilityService.kt   foreground detection, countdown, Strict Mode guard
│   ├── BlockScreenActivity.kt           the wall + the tamper notice
│   ├── BlockerForegroundService.kt      keeps the process alive, shows the countdown
│   ├── Watchdog.kt                      WorkManager + AlarmManager + boot receiver
│   ├── AdminReceiver.kt                 uninstall protection
│   └── GuardBypass.kt                   the app's own escape hatch through its own guard
├── pose/           Rep detection
│   ├── PoseAnalyzer.kt                  MediaPipe wrapper, CameraX analyzer
│   ├── PushUpCounter.kt                 state machine + every anti-cheat rule
│   ├── PoseMath.kt                      joint angles, plank deviation, orientation
│   └── SkeletonOverlay.kt               the green skeleton
├── quota/          TaperPlan.kt (the curve, the price ladder), DailyRollover.kt
├── data/           Room entities/DAOs, DataStore settings, BlockRepository (all decisions)
└── ui/             Compose: onboarding, home, apps, stats, settings, challenge
```

The one file worth reading first is `data/repo/BlockRepository.kt` — every "may this app run right
now" decision goes through `decide()`, so the policy lives in one place instead of being smeared
across the service and the UI.

---

## Privacy

- No network permission is requested. The app **cannot** phone home.
- Camera frames go from CameraX to MediaPipe to the screen and are then discarded. Nothing is
  written to disk.
- Usage data, rep history and the event log live in a local Room database that is excluded from
  cloud backup and device transfer.

---

## Licence

Do what you like with it.
