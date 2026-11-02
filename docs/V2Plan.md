# Hourly Steps v2 — Feature Plan

Builds on the v1 design in [HourlyStepsPlan.md](HourlyStepsPlan.md). Covers the ten features
chosen from the competitor review, in the order they should be built. Each phase is its own
feature branch, compiled and unit-tested before commit, and verified on the Wear OS 6 emulator.

| # | Feature | Phase |
|---|---|---|
| 0 | Fix: reminder when the screen is asleep | A |
| 2 | Reminder days, each assigned to one of two plans or off | A |
| 8 | Different daily goals per plan | A |
| 3 | Pause reminders | A |
| 11 | Reminder interval (30 / 60 / 120 min) | A (model), E (UI) |
| 1 | Tile and watch-face complication | B |
| 4 | Today's hours view with last check result | B |
| 5 | Skip during workouts and sleep | C |
| 6 | Snooze and a second nudge | C |
| 9 | Daily goal celebration | D |
| 7 | Streaks with rest days | D |

Why this order: features 2, 8, 3 and 11 all change what a "slot" and a "day" mean. Doing that
model work first means the tile, the hours view, streaks and the nudges are built once, on the
final model, instead of being reworked.

---

## Phase A — Schedule model, pause, and the asleep-screen fix (done)

### 0. Reminder when the screen is asleep

**Found on the emulator.** With the screen asleep the reminder only added a dot to the watch
face. Separately, the app's own vibration was dropped in the background, so the Off / Short /
Long setting had no effect.

**Fix.**
- The reminder wakes the screen first, like the celebration (`Attention`, skipped under Do Not
  Disturb / Bedtime), so the alert is visible.
- Wear OS only plays a channel's own pattern for a background app, and pops up the alert only
  for a channel that vibrates. Channel ids now include the vibration strength, with only the
  current strength's channels kept. "Off" uses an imperceptible 1 ms pulse so the alert appears.
- Changing the vibration strength creates new channels, so a change made to a channel in system
  settings does not carry over to the new strength.

**Also fixed.** `Clock.systemDefaultZone()` captured the time zone once, so a running app kept
the old zone after travel. `SystemZoneClock` follows the device.

### 2 + 8 + 11. The schedule model

Today: one window (`firstHour`..`lastHour`), one `dailyGoal`, 60-minute slots, every day.

New model:

```kotlin
data class DayPlan(
    val dailyGoal: Int = 7000,
    val startMinute: Int = 480,    // first slot starts; minutes of the day
    val endMinute: Int = 1200,     // last slot ends, exclusive; 1440 is midnight
)

enum class DayAssignment { PLAN_A, PLAN_B, OFF }

data class Schedule(
    val mode: ScheduleMode = ScheduleMode.EVERY_DAY,   // EVERY_DAY | TWO_PLANS
    val planA: DayPlan = DayPlan(),                    // the only plan in EVERY_DAY mode
    val planB: DayPlan = DayPlan(),
    val assignments: Map<DayOfWeek, DayAssignment> = DayOfWeek.entries.associateWith { PLAN_A },
    val slotMinutes: Int = 60,                         // 30 | 60 | 120
) {
    fun planFor(day: DayOfWeek): DayPlan?             // null when the day is off
}
```

- **Any week shape.** Weeks are not assumed to be Monday to Friday plus a weekend. In
  TWO_PLANS mode each day of the week is assigned to Plan A, Plan B, or Off, so a
  Thursday-to-Sunday worker, a three-days-off week, or alternating days all work. In EVERY_DAY
  mode every day uses Plan A and days can only be toggled on or off.
- **Slots.** A day's slots run from `startMinute` in steps of `slotMinutes`, the last ending at
  `endMinute` (shorter if the window is not a multiple). The default 8:00 → 20:00 with 60-minute slots is the same 12
  slots as v1 (checks at 8:50 … 19:50). The check fires at `slotEnd − lead`, where lead is a
  sixth of the slot: 5 min for 30, 10 min for 60, 20 min for 120.
- **Slot goal** = `ceil(dailyGoal / slotCount)`, per plan. The two plans can have different
  goals (feature 8) and different hours (feature 2).
- **Days off** (feature 2): no checks, no celebrations, and the Home ring shows "Day off".
  Days off are streak-neutral (see feature 7).
- **Window must not cross midnight.** Night-shift windows stay out of scope; the model allows
  them later without migration because start and end are plain minutes.
- **Catch-up pace** is unchanged: deficit over the remaining slots of today's plan.

**Settings UI.** Settings becomes grouped:

```
Goal & schedule
  Schedule .............. Every day / Two plans
  Plan A ................ 7,000 · 8:50 AM–7:50 PM    (opens plan editor; the only plan in Every day mode)
  Plan B ................ 5,000 · 9:50 AM–5:50 PM    (opens plan editor; Two plans mode only)
  Week .................. A A A A A B Off            (opens the week editor)
  Reminder interval ..... Every hour                 (Phase E; hidden until then)
Reminders
  Pause reminders ....... Off / Paused until Mon
  Second nudge .......... Off                         (Phase C)
  Skip during workouts .. On                          (Phase C)
  Skip while asleep ..... On                          (Phase C)
  Skip while charging ... On
  Vibration ............. Short
Celebrations
  Celebrate hourly goal . On
  Celebrate daily goal .. On                          (Phase D)
Permissions
Debug (debug builds)
```

The plan editor is one screen: Daily goal, First reminder, Last reminder, with the live
"12 reminders · ≈ 584 steps/hour" caption.

The week editor is a scrolling list of seven full-width day buttons, starting on the locale's
first day of the week. Seven chips in one row would be too small to tap on a round watch. With
two plans a tap cycles each day A → B → Off, with the button coloured by plan; with one plan a
tap toggles On → Off. A caption at the top summarises the result, for example
"A: Thu–Sun · B: Mon · Off: Tue, Wed". Plan names are fixed ("Plan A", "Plan B") in this version;
renaming can come later.

**Storage.** Settings stay in Preferences DataStore with new keys; old keys migrate on first
read (old `dailyGoal`/`firstHour`/`lastHour` become Plan A, mode EVERY_DAY, all days on).

**Step storage.** Replace the hourly buckets with raw interval points so any slot length can
be summed:

```
step_points(startMillis, endMillis, steps)      indexed on endMillis
```

Room version 2 with a migration that converts each `hourly_steps` row into one point spanning
that hour. Retention: points 14 days. The `day_summary` table moves to Phase D with streaks, the
only thing that reads it.

**Engine changes.** `ReminderSchedule.nextCheckTime(now, schedule)` walks days forward,
skipping days off and paused days. `CheckDecider` and `CelebrationDecider` take a `Slot` and a
`DayPlan` instead of hours. `HomeViewModel` tracks "this slot" rather than "this hour".

### 3. Pause reminders

- **Options.** Rest of today · Tomorrow too (2 days) · One week · Until I resume.
  Stored as `pausedUntil: LocalDate?` (null = not paused; `LocalDate.MAX` = until resumed).
- **While paused:** alarms cancelled, no checks, no celebrations, step counting continues.
  Home ring greys out with "Paused until Mon · Resume". The tile shows the same.
- **Resume:** Settings or the Home button. Re-arms for the next slot. A pause that ends by date
  re-arms through the next-check calculation (the alarm for the first slot after the pause is
  set when the pause starts).
- **Streaks:** paused days are neutral, like days off (feature 7).
- **Quick access:** a "Pause today" action on the reminder notification (Phase C, with snooze).

**Tests.** Slot generation for 30/60/120 with the default and odd windows; plan selection per
day for an irregular week (e.g. A A Off B B A Off); days off and paused days skipped by
`nextCheckTime`, including a pause that spans several days off; DST transitions with
30-minute slots; migration of hourly rows to points; settings key migration.

---

## Phase B — Glanceability (done)

### 1. Tile and watch-face complication

**Tile** (`androidx.wear.tiles` 1.6.2 + `protolayout` 1.4.2, both fine on API 36):

- Layout: two concentric arcs drawn with core ProtoLayout. The outer arc is **today** (total
  against the daily goal, lilac, turning amber once the goal is met); the inner arc is **this
  slot** (steps against its target, cyan). Centre: the slot's step count large, "of 584" below
  it, then "Today 3,100 / 7,000" and the catch-up line when behind. Each caption is coloured to
  match its ring, so the two are never confused. Other states: goal met, day off, paused.
- The Home screen draws the same pair with the same colours, and both use one `RingContent`,
  so the app and the tile always match.
- Tap anywhere opens the app. Resuming a pause happens on Home.
- Freshness: refreshes at the slot boundary or after 5 minutes, whichever is sooner, plus
  `TileService.getUpdater().requestUpdate()` after step batches (at most once a minute), checks
  and settings changes.

**Complications** (`watchface-complications-data-source-ktx` 1.3.0): two data sources, because a
single slot is too small for two rings. A watch face can show both side by side.

- "Hourly Steps: this hour" and "Hourly Steps: today".
- Types: `RANGED_VALUE` (a gauge with "412"), `SHORT_TEXT` ("412" titled "of 584"), and
  `LONG_TEXT` ("412 of 584 this hour").
- Tap opens the app. Updated with `ComplicationDataSourceUpdateRequester.requestUpdateAll()` on
  the same triggers as the tile. The manifest declares a 5-minute update period as the fallback,
  so a still wearer sees the new hour within 5 minutes.
- Day off / paused shows "Off" / "Paused" with the gauge empty. "This hour" shows "--" outside
  the reminder window.

All three read one `DayStatus`, computed in one place, so they never disagree.

### 4. Today's hours view

- Home is a horizontal pager: **Rings** → **Today** → **Streak** (Phase D). Settings stays on the
  edge button of the first page. The stock page indicator sits where that button is, so the
  rings page draws its own dots above it.
- **Today page:** one row per slot: time, steps / goal, and a status glyph:
  ✓ hit · ✗ short · – skipped or daily goal already met · • running now. A skipped row names the
  reason inline (charging, late, paused, no check), which is quicker on a watch than tapping
  it. Upcoming slots show their goal. Rows come from `step_points` joined with `check_log`.
- A slot that started with the daily goal already met is not counted as short, since no reminder
  applied.
- Also shows "3 of 12 hours hit" and "Last check 2:50 PM: reminded" at the top, so a user who
  didn't get a reminder can see whether the check ran. This is the answer to "it didn't buzz, is
  it broken?".

---

## Phase C — Smarter reminders

### 5. Skip during workouts and sleep

- Health Services passive monitoring reports `UserActivityState`: `EXERCISE`, `PASSIVE`,
  `ASLEEP`, `UNKNOWN`. Request it with `setShouldUserActivityInfoBeRequested(true)` and
  persist the latest state and its change time from `onUserActivityInfoReceived`.
- At check time: `ASLEEP` → skip ("asleep"); `EXERCISE` → skip ("workout"). Both behind
  settings, default on. A celebration still fires if the slot goal is reached during a workout
  (a walk workout should still celebrate), but not while asleep.
- Skip-reason precedence: paused > day off > late > asleep > workout > charging.
- **Limitation to document:** the exercise state is set only while an app tracks a workout
  through Health Services (the Fitbit app on Pixel Watch does). Auto-detected activity is not
  reported. Off-wrist detection stays out; charging covers the common case.
- Emulator: `whs.synthetic.user.START_SLEEPING` / `STOP_SLEEPING` broadcasts exist on the
  Wear OS 6 image; exercise state can be driven by starting a workout in the Fitbit or a
  sample exercise app.

### 6. Snooze and a second nudge

- **Second nudge** (setting, default off): after a shortfall at `slotEnd − lead`, arm a
  follow-up at `slotEnd − lead/2` (:55 for hourly slots). It re-reads steps; still short →
  vibrate again and update the notification text; met → cancel. Uses `setAlarmClock` so it is
  not subject to Doze's nine-minute spacing for exact alarms. Never fires after the slot ends.
- **Notification actions:** "Snooze 5 min" (same follow-up mechanism, manual) and
  "Pause today". Wear OS shows actions under the notification body.
- **Check log** records nudge outcomes so the Today page can show "reminded twice".

**Tests.** Follow-up time per slot length; no follow-up when the first check was skipped or met;
snooze never crosses the slot end; nudge cancelled when the goal is met in between.

---

## Phase D — Motivation

### 9. Daily goal celebration

- Detect `todayTotal` crossing `dailyGoal` in a step batch, once per day, any time of day.
- Bigger moment than the hourly one: gold palette, longer fireworks, "Daily goal achieved!",
  the total, and the streak line ("3 days in a row").
- If the hourly and daily goals cross in the same batch, show only the daily celebration.
- Same channel and the same screen-wake rule as the hourly celebration; its own setting.

### 7. Streaks with rest days

- **Rule.** The streak is the number of consecutive *counted* days ending today (or yesterday,
  if today is still in progress) on which the daily goal was met. Days off and paused days are
  skipped: they neither extend nor break the streak. A counted day that misses the goal resets
  it to zero.
- **Rest days** are the pause feature (3): "Pause reminders · Rest of today" marks today
  streak-neutral. No separate concept, no "streak freeze" items.
- **Computed** from `day_summary`, which the end-of-day job writes (the last slot's check, or a
  midnight alarm when the window ends earlier). Current and longest streak are cached in
  DataStore for the tile.
- **UI.** Third pager page: streak count, longest streak, and a 7-day strip (✓ met, ✗ missed,
  – rest). The daily celebration shows the new count.

**Tests.** Streak across days off, paused spans, a missed day, today in progress, and the
longest-streak bookkeeping.

---

## Phase E — Reminder interval UI

### 11. Custom reminder intervals

The model from Phase A already supports it; this phase exposes it.

- Setting "Reminder interval": Every 30 minutes · Every hour (default) · Every 2 hours.
- Captions update: "24 reminders · ≈ 292 steps per 30 min".
- Lead time per slot length as above; the second nudge halves it.
- Notification text says "this half hour" / "this hour" / "these two hours".
- Health Services batches steps up to a few minutes; the flush before each check already
  covers 30-minute slots, but verify on the emulator that a 30-minute slot's count is complete
  at `slotEnd − 5 min`.

---

## Cross-cutting

- **Dependencies to add:** tiles 1.6.2, protolayout + protolayout-material3 1.4.2,
  watchface-complications-data-source-ktx 1.3.0, wear-tooling-preview 1.0.0. All compile
  against API 36 with AGP 8.13.
- **Permissions:** no new runtime permissions. Activity state uses the existing
  `ACTIVITY_RECOGNITION` grant.
- **Battery:** the extra alarms are one follow-up per shortfall and one end-of-day job. Tile and
  complication updates are throttled to once a minute and only while steps are arriving.
- **Settings growth:** the single Settings list becomes grouped with headers; each group stays
  under six rows so it still fits a round screen without hunting.
- **Docs:** README gets the new emulator checklist (asleep-screen reminder, sleep broadcasts,
  tile and complication setup); HourlyStepsPlan.md gets a pointer to this file.

## Decisions (confirmed)

1. **Week shape:** each day is assigned to Plan A, Plan B, or Off. No Monday-to-Friday
   assumption; a four-on / three-off week is a first-class case.
2. **Interval options:** 30 / 60 / 120 minutes.
3. **Streak rule:** a missed counted day resets to zero, no automatic grace day. Days off and
   paused days are neutral.
4. **Second nudge:** off by default.
5. **Tile:** two rings in different colours, today's total outside and this slot inside; the
   Home screen gets the same pair.
