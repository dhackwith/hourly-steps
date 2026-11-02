# Hourly Steps — Design Plan

Standalone Wear OS app for Google Pixel Watch 4 (Wear OS 6, API 36).
Fitbit-style "Reminders to Move" with a user-configurable daily step goal that is
spread across the hours of the day.

---

## 1. Product definition

### Core behaviour

| Rule | Detail |
|---|---|
| Daily goal | Default 7,000 steps. User-editable (range 1,000–30,000, step 100). |
| Reminder window | First reminder time and last reminder time, both inclusive. Default 8:50 AM → 7:50 PM (12 slots). Only the hour matters; the minute is always :50. |
| Hourly goal | `ceil(dailyGoal / numberOfReminderSlots)` where slots = lastHour − firstHour + 1. Default 7000 / 12 = **584**. Derived, shown read-only in Settings, recalculated whenever the daily goal or window changes. |
| Check time | At :50 of each hour inside the window. |
| What is measured | Steps taken since the top of the current hour (:00 → :50). |
| Pass | steps ≥ hourly goal → do nothing. |
| Fail | steps < hourly goal → vibrate and post a notification: "**312 more steps** this hour". When behind the daily pace, append the catch-up figure: "312 more steps this hour (640/hr to catch up today)". |
| Daily goal met | Once today's total ≥ daily goal, hourly goal reminders stop for the rest of the day. The Home ring switches to a "goal met" state. |
| Inactivity reminders (optional, default off) | Separate toggle. When on, checks continue at :50 after the daily goal is met, against a separate, smaller **inactivity threshold** (default 250 steps/hour, user-editable). Shortfall → vibrate + notification "Time to move: 180 more steps this hour". No catch-up figure in this mode. |
| Catch-up figure | `catchUpPerHour = ceil((dailyGoal − todayTotal) / remainingSlots)` where remainingSlots counts the current slot through the last slot inclusive. Shown in the reminder and on Home whenever it exceeds the hourly goal. It is informational: the pass/fail threshold stays the base hourly goal. |
| Notification lifetime | Auto-dismiss at the top of the next hour. Also dismissed early if the user reaches the goal between :50 and :00. |

### Daily goal met early, and catching up

Two situations the base rule does not cover:

**Goal met early.** The hourly goal exists only to reach the daily goal, so by default the
app goes quiet once the daily total is reached (e.g. a long morning walk). Users who still
want to be nudged off the couch turn on **Inactivity reminders**. That mode reuses the same
:50 schedule and window but compares against its own threshold (default 250 steps, the
Fitbit convention) and posts on its own notification channel, so it can be silenced
independently in system settings. Modes are mutually exclusive per check: before the daily
goal is met the check is a *goal* check; after, it is an *inactivity* check (or nothing if the
toggle is off).

**Behind pace.** If the user misses the 8:50 and 9:50 slots, the base hourly goal of 584 can no
longer deliver 7,000 by 7:50 PM. At each check the app computes the pace needed over the
remaining slots and shows it in parentheses:

```
today 1,200 / 7,000 at 10:50, slots left incl. current = 10
catchUp = ceil((7000 − 1200) / 10) = 580   → not above 584, nothing extra shown
today 400 / 7,000 at 10:50
catchUp = ceil((7000 − 400) / 10) = 660    → "212 more steps this hour (660/hr to catch up today)"
```

The threshold that triggers the reminder stays the base hourly goal; the catch-up number is
guidance so the user can decide how hard to push. (A v2 option, "Adaptive hourly goal", could
make the catch-up figure the live threshold instead. Off the table for v1.) When
`remainingSlots` is 0 or the daily goal is already met, no catch-up figure is shown. Home shows
the same two numbers: the ring is this hour against the hourly goal, with a caption
"660/hr to catch up" when applicable.

### Hourly goal celebration

Modelled on the Pixel Watch's built-in hourly reminder, which buzzes and shows a celebration
when the hour's goal is reached.

- **When.** The first time in an hour that this hour's steps reach the target, checked after
  every batch of step data. Once per hour, only inside the reminder window, and only when the
  **Celebrate goals** setting is on (default on).
- **Target.** The hourly goal while the daily goal was still open at the start of the hour. After
  the daily goal is met, the inactivity threshold if inactivity reminders are on; otherwise no
  celebration. Basing this on the total at the start of the hour means the batch that crosses
  the hourly and daily goals together still celebrates the hour.
- **What the user sees.**
  - App open: the fireworks screen opens immediately.
  - Watch face showing: a "da-da-daaa" buzz and a "🎉 Hourly goal achieved!" alert; tapping it
    opens the fireworks.
  - Screen asleep (always-on): the screen is lit for a few seconds, then the same buzz and
    alert appear. Skipped while Do Not Disturb or Bedtime filters notifications.
- **Fireworks screen.** Looping bursts drawn on a Canvas, "Hourly goal achieved!", the steps this
  hour and today's total. Closes on tap, swipe, or after 7 seconds. With system animations off it
  shows a still frame.
- **Platform limit.** Third-party Wear OS apps cannot open a full-screen activity from the
  background. Full-screen intents are ignored while the watch is in always-on mode, and become an
  ordinary alert when the screen is on. So the fireworks need one tap unless the app is open. The
  notification still carries a full-screen intent in case a future Wear OS release honours it.

### Slot counting (decided)

Slots are counted inclusively: every :50 from the first reminder hour to the last reminder
hour. The default window is 8:50 AM → 7:50 PM, which is 12 slots and 7000 / 12 = 583.3 → **584**
steps/hour. (8:50 PM as the last reminder would be 13 slots and 539/hour; the user can choose
that in Settings, and the derived hourly goal updates live so the trade-off is visible.)

### Deliberate simplifications for v1

- No adaptive threshold. The catch-up figure is shown, but the pass/fail line stays the base hourly goal.
- No day-of-week schedule. One window applies every day. (Easy v2 addition.)
- No phone companion. The app is fully standalone (`com.google.android.wearable.standalone = true`).
- No cloud sync, no accounts, no analytics.

### Nice-to-haves (v2, after core ships)

- Tile: current-hour ring, steps this hour / goal, countdown to next check.
- Watch-face complication (short text / ranged value) with steps this hour.
- Per-day-of-week enable toggle.
- History screen: last 7 days, hours hit vs missed.
- Skip when the watch is off-body (`TYPE_LOW_LATENCY_OFFBODY_DETECT`).

---

## 2. Platform choices and why

| Concern | Choice | Alternatives considered |
|---|---|---|
| Step source | **Health Services** `PassiveMonitoringClient` + `PassiveListenerService` for `DataType.STEPS`. Battery-optimised, delivered in the background, interval data points with start/end timestamps, so bucketing by hour is exact. | `SensorManager TYPE_STEP_COUNTER` (cumulative since boot, batched, needs a live listener to read a value, resets on reboot). Health Connect on Wear OS (aggregate reads, but batched/laggy and not meant for near-real-time). |
| Hourly check trigger | **`AlarmManager.setExactAndAllowWhileIdle`** at the next :50, re-armed after each fire. One-shot chain, not a repeating alarm. | `WorkManager` periodic (±10 min drift, unacceptable at :50). Health Services `PassiveGoal` (fires when a threshold is *reached*, not when it is missed, and only for daily cumulative types). |
| Exact-alarm permission | Request `SCHEDULE_EXACT_ALARM` (denied by default on Android 14+). Onboarding checks `canScheduleExactAlarms()` and deep-links to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Fallback: `setWindow(:47, 6 min)` and a warning banner in Settings. | `USE_EXACT_ALARM` is for alarm-clock/calendar apps only; a reminder app does not qualify under Play policy. |
| UI | **Compose for Wear OS, Material 3** (`androidx.wear.compose:compose-material3`) + Horologist for pickers/time text. | Views / legacy Wear Material 2.5 — no reason on API 36. |
| Settings storage | Proto DataStore. | Preferences DataStore (fine too, Proto gives a typed schema). |
| Step storage | Room, one row per (localDate, hour). | DataStore map — Room is cleaner for queries and history. |
| DI | Hilt. | Manual DI (acceptable for this size; Hilt keeps services/receivers tidy). |
| Language / build | Kotlin 2.x, AGP 8.x, compileSdk 36, targetSdk 36, minSdk 30 (Wear OS 3; only tested on Pixel Watch 4). | — |

Library versions to pin at project creation (check the AndroidX release page; as of
mid-2026 Health Services client stable is 1.0.0 with 1.1.0 at RC):
`androidx.health:health-services-client`, `androidx.wear.compose:compose-material3`,
`androidx.wear.compose:compose-foundation`, `androidx.wear.compose:compose-navigation`,
`com.google.android.horologist:horologist-compose-layout`, `androidx.datastore:datastore`,
`androidx.room:room-ktx`, `androidx.hilt`, `androidx.wear.tiles` + `androidx.wear.protolayout` (v2),
`androidx.wear.watchface:watchface-complications-data-source-ktx` (v2).

---

## 3. Architecture

```
app/
├── HourlyStepsApp.kt                 (Hilt application, creates notification channel)
├── di/                               (Hilt modules: DataStore, Room, Health Services client, AlarmManager)
├── domain/
│   ├── GoalCalculator.kt             hourlyGoal(...), catchUpPerHour(...), remainingSlots(...) — pure functions
│   ├── CheckDecider.kt               decide(settings, stepsThisHour, todayTotal, hour) → CheckResult — pure function
│   ├── ReminderSchedule.kt           nextCheckTime(now, window) — pure function
│   └── model/                        Settings, HourBucket, CheckResult (None | GoalShortfall(need, catchUp?) | InactivityShortfall(need))
├── data/
│   ├── settings/SettingsRepository.kt    (Proto DataStore)
│   ├── steps/
│   │   ├── StepsPassiveListenerService.kt  (Health Services callback → Room)
│   │   ├── StepsRepository.kt              stepsSince(hourStart), todayTotal(), flush()
│   │   └── db/ HourlyStepsDao, HourlyStepsEntity, AppDatabase
│   └── log/CheckLogRepository.kt     (one row per check: time, steps, goal, notified) — v1 lite, feeds v2 history
├── reminder/
│   ├── ReminderScheduler.kt          arm/cancel next :50 alarm, exact vs fallback
│   ├── ReminderReceiver.kt           BroadcastReceiver at :50 → check logic (goAsync)
│   ├── RescheduleReceiver.kt         BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED
│   └── ReminderNotifier.kt           notification + VibrationEffect
└── ui/
    ├── MainActivity.kt
    ├── navigation/                   SwipeDismissableNavHost: home, settings, goal, window, permissions
    ├── home/HomeScreen.kt            ring: steps this hour / hourly goal; today total; next check countdown
    ├── settings/                     SettingsScreen, DailyGoalPicker, ReminderWindowPicker
    └── onboarding/PermissionsScreen.kt
```

### Data flow

1. **Ingest** — On first launch (after permissions) `StepsRepository.startPassiveMonitoring()`
   calls `setPassiveListenerServiceAsync(StepsPassiveListenerService::class, config(STEPS))`.
   Health Services wakes the service with batches of `IntervalDataPoint<Long>`. Each point
   is converted to wall-clock `Instant`s via `getStartInstant(bootInstant)` and added to the
   Room bucket for the hour that contains its **end** time. (Points rarely straddle an hour;
   when they do, attribute to the end hour — simplest and errs toward the user.)
2. **Check** — `ReminderReceiver` fires at HH:50:
   1. `flushAsync()` on the passive client so any batched steps land first (await, 5 s timeout).
   2. `stepsThisHour = dao.stepsFor(today, hour)`; `todayTotal = dao.totalFor(today)`.
   3. Skip conditions: outside window, "skip while charging" on and `BatteryManager.isCharging`.
   4. `CheckDecider.decide(...)`:
      - `todayTotal < dailyGoal` → goal check. If `stepsThisHour < hourlyGoal` →
        `GoalShortfall(need = hourlyGoal − stepsThisHour, catchUp = catchUpPerHour if > hourlyGoal)`.
      - `todayTotal ≥ dailyGoal` and inactivity reminders on → inactivity check. If
        `stepsThisHour < inactivityThreshold` → `InactivityShortfall(need)`.
      - otherwise `None`.
   5. `ReminderNotifier.notify(result)` picks the channel and text from the result type.
   6. Write a `CheckLog` row (includes mode). Re-arm the next alarm. Done inside `goAsync()`
      with a wake lock held by the receiver (total work < 1 s).
3. **Early dismiss** — `StepsPassiveListenerService`, after writing a batch, checks whether a
   reminder notification is showing for the current hour and the bucket now meets the threshold
   that produced it (hourly goal or inactivity threshold); if so, cancels it.
4. **Settings change** — any change to goal or window → recompute hourly goal (Flow) and re-arm
   the alarm so the new window takes effect immediately.

### Scheduling details

- `nextCheckTime(now)`: next HH:50 ≥ now whose hour is within `[firstHour, lastHour]`; if past
  the last slot today, first slot tomorrow. Pure function, DST-safe by using `ZonedDateTime`.
- Exact path: `setExactAndAllowWhileIdle(RTC_WAKEUP, next, pendingIntent)`. Doze allows one
  such alarm per ~9 min per app; we need one per hour, fine.
- Fallback path (permission denied): `setWindow(RTC_WAKEUP, next − 3 min, 6 min)`. Receiver
  computes "steps since top of hour" regardless of exact fire time, so the only cost is timing.
- Re-arm sources: after each fire, after settings change, `BOOT_COMPLETED`,
  `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, and on every app foreground (cheap
  idempotent "ensure armed").

### Notification and vibration

- Two channels, both `IMPORTANCE_HIGH` with vibration: `hourly_goal` and `inactivity`.
  Separate channels let the user silence inactivity nudges in system settings without
  touching goal reminders. DND and Bedtime mode on Wear OS suppress both automatically —
  respect that, no override.
- Goal content: title "Time to move", text "312 more steps this hour", second line
  "660/hr to catch up today" only when behind pace. Inactivity content: title "Time to move",
  text "180 more steps this hour". Tap → `MainActivity`.
- `setTimeoutAfter(millisUntilTopOfHour)` so it clears itself.
- Explicit `Vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))`
  in addition to the channel setting, so the haptic is reliable even if the channel's vibration
  was changed by the user.
- Setting: vibration strength (off / short / long).

### Permissions

| Permission | Type | When requested |
|---|---|---|
| `ACTIVITY_RECOGNITION` | runtime | onboarding, before starting passive monitoring |
| `POST_NOTIFICATIONS` | runtime | onboarding |
| `SCHEDULE_EXACT_ALARM` | special access (settings deep link) | onboarding, with explanation and "continue without" |
| `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK` | normal | manifest |
| `TURN_SCREEN_ON` | normal (API 34+) | manifest; lets the celebration light a sleeping screen |
| `USE_FULL_SCREEN_INTENT` | special access (API 34+) | manifest; granted by default when sideloaded. Google Play only allows it for alarm and calling apps, so remove it before a Play release. |

Manifest also needs `<uses-feature android:name="android.hardware.type.watch" />`, the
standalone meta-data, and the `PassiveListenerService` declared with
`android:permission="com.google.android.wearable.healthservices.permission.PASSIVE_DATA_BINDING"`.

### Screens (Compose, Material 3 for Wear)

1. **Home** — `CircularProgressIndicator` ring (steps this hour / hourly goal), big number in
   the centre, "today 4,210 / 7,000" below, "next check 14:50" as a caption. When behind pace
   an extra caption reads "660/hr to catch up". When the daily goal is met the ring fills in
   the success colour with a check mark; if inactivity reminders are on, the ring instead
   tracks this hour against the inactivity threshold with a "goal met" badge. Chip → Settings.
2. **Settings** — list: Daily goal (chip, shows 7,000), Reminder window (chip, shows
   8:50 AM – 7:50 PM), derived "≈ 584 steps / hour" caption, **Inactivity reminders** (toggle,
   default off), **Inactivity threshold** (chip, shows 250, only enabled when the toggle is on),
   Vibration, Skip while charging, exact-alarm warning banner if permission missing.
3. **Daily goal picker** — Horologist/Material 3 `Picker`, step 100.
4. **Window picker** — two hour pickers (first / last), validated first ≤ last.
5. **Inactivity threshold picker** — `Picker`, 50–1,000 in steps of 50.
6. **Permissions / onboarding** — one card per permission with status and action.

---

## 4. Edge cases to handle explicitly

- **Day rollover and time-zone / DST change** — buckets are keyed by local date + hour; a
  re-arm on `TIME_SET` / `TIMEZONE_CHANGED` prevents a stale alarm. Spring-forward skips an
  hour (no check), fall-back repeats an hour (accept one merged bucket; document the choice).
- **Health Services batching** — without `flushAsync` the :50 read can miss up to several
  minutes of steps. Always flush, with a timeout so the receiver never hangs.
- **Reboot** — Health Services keeps the passive registration across reboots, but re-register
  on `BOOT_COMPLETED` anyway (idempotent) and re-arm the alarm.
- **App force-stopped** — alarms and passive registration are cleared by the system until the
  user opens the app. Home screen's "ensure armed" covers recovery.
- **Permission revoked later** — `PassiveListenerService.onPermissionLost()` → stop alarms,
  show a persistent "needs permission" state on Home.
- **Watch on charger / off wrist** — "skip while charging" default on. Off-body detection is v2.
- **Steps between :50 and :00** — count toward the day total and the hour bucket, but the
  check already happened; this matches Fitbit behaviour. The early-dismiss rule makes the
  notification go away if the user walks it off in those 10 minutes.
- **Window spanning midnight** (e.g. 10 PM → 6 AM) — out of scope for v1; validate first ≤ last.

---

## 5. Testing strategy

- **Unit (JVM)** — `GoalCalculator` (default window → 12 slots → 584; 8:50 PM last → 13 slots → 539;
  rounding up, bounds; `catchUpPerHour` at various hours including the last slot and when
  already ahead of pace), `CheckDecider` (goal shortfall with and without catch-up, goal met with
  inactivity off → None, goal met with inactivity on → inactivity shortfall or None, last slot
  with remainingSlots = 1), `ReminderSchedule.nextCheckTime` across window edges, midnight, DST
  transitions, and `HourBucket` attribution of straddling interval points. These are pure
  functions; aim for full coverage.
- **Instrumented (Wear OS 6 / API 36 emulator)** — Health Services synthetic data:
  ```
  adb shell am broadcast -a "whs.USE_SYNTHETIC_PROVIDERS" com.google.android.wearable.healthservices
  adb shell am broadcast -a "whs.synthetic.user.START_WALKING" com.google.android.wearable.healthservices
  adb shell am broadcast -a "whs.synthetic.user.STOP_EXERCISE" com.google.android.wearable.healthservices
  ```
  plus setting the emulator clock to HH:49 and watching the receiver fire.
  Verify: bucket fills, notification appears when short, no notification when met, catch-up line
  appears only when behind pace, no goal reminder after the daily goal is met, inactivity reminder
  appears after the daily goal when the toggle is on, auto-dismiss at :00, early dismiss when the
  threshold is reached after :50.
- **On-device (Pixel Watch 4 over wireless ADB)** — a full day of real use. Check Battery
  usage in Settings: Health Services passive + 12 exact alarms/day should be negligible.
- **Dev-only debug screen** — "Fire check now", "Add 100 synthetic steps", "Show next alarm time".
  Stripped from release.

---

## 6. Milestones

| # | Milestone | Done when |
|---|---|---|
| M0 | Project scaffold | Wear module builds, runs on emulator and watch, empty Home screen, Hilt wired. |
| M1 | Settings + goal maths | Proto DataStore settings (incl. inactivity toggle and threshold), pickers, derived hourly goal shown live, `GoalCalculator` + `CheckDecider` unit tests green. |
| M2 | Step ingestion | Passive listener writes Room buckets; Home shows steps this hour and today, live via Flow. |
| M3 | Reminder engine | Alarm chain, receiver, flush, notification + vibration, early dismiss, reschedule receivers, permission onboarding. |
| M4 | Hardening | Edge cases in §4, instrumented tests, on-watch battery check, release build with R8. |
| M5 | v2 extras (optional) | Tile, complication, history, off-body skip, day-of-week schedule. |

M3 is the largest piece; M0–M4 is roughly a focused week of evenings.

---

## 7. Open questions for you

None outstanding.

Decided: "skip while charging" is on by default.
Decided: minSdk 30, targetSdk/compileSdk 36. Tested target is Pixel Watch 4.
Decided: no Tile or complication in v1; notifications first.
Decided: dependencies are pinned to the newest releases that still compile against API 36 with
AGP 8.13 (Compose 1.11, Wear Compose 1.6, core 1.18, activity 1.12, lifecycle 2.10). The newest
AndroidX releases require compileSdk 37 and AGP 9.1; upgrading is a separate step.
Decided: v1 uses a small hand-wired `AppContainer` instead of Hilt, and Preferences DataStore
instead of Proto DataStore, to keep the build simple. Either can be swapped later.
Decided: 12 slots by default (8:50 AM → 7:50 PM, 584 steps/hour), slots counted inclusively.
Decided: hourly goal reminders stop once the daily goal is met; optional inactivity reminders
(default off, 250 steps/hour) continue after it. Catch-up pace is shown in parentheses but the
reminder threshold stays the base hourly goal.

Decided: the inactivity threshold is per hour slot (steps since the top of the hour), default 250.
