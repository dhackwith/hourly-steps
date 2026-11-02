# Hourly Steps v2 — Feature Plan

Builds on the v1 design in [HourlyStepsPlan.md](HourlyStepsPlan.md). Covers the ten features
chosen from the competitor review, in the order they should be built. Each phase is its own
feature branch, compiled and unit-tested before commit, and verified on the Wear OS 6 emulator.

| # | Feature | Phase |
|---|---|---|
| 0 | Fix: reminder when the screen is asleep | A |
| 2 | Reminder days, with separate weekday and weekend plans | A |
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

## Phase A — Schedule model, pause, and the asleep-screen fix

### 0. Reminder when the screen is asleep

**Problem.** The goal celebration only appeared as a notification dot when the watch was in
always-on mode until a brief screen wake was added. The :50 reminder has no such wake, and has
never been tested with the screen asleep.

**Plan.** Reproduce on the emulator (screen asleep, jump the clock to :49:50). If the reminder
is not shown, reuse the celebration's wake (`TURN_SCREEN_ON` wake lock, skipped under Do Not
Disturb / Bedtime) in `ReminderNotifier`. Add the "asleep" case to the emulator checklist in the
README.

### 2 + 8 + 11. The schedule model

Today: one window (`firstHour`..`lastHour`), one `dailyGoal`, 60-minute slots, every day.

New model:

```kotlin
data class DayPlan(
    val enabled: Boolean = true,
    val dailyGoal: Int = 7000,
    val windowStart: LocalTime = LocalTime.of(8, 0),   // first slot starts
    val windowEnd: LocalTime = LocalTime.of(20, 0),    // last slot ends
)

data class Schedule(
    val mode: ScheduleMode = ScheduleMode.EVERY_DAY,   // EVERY_DAY | WEEKDAYS_WEEKEND
    val weekday: DayPlan = DayPlan(),
    val weekend: DayPlan = DayPlan(),                  // used only in WEEKDAYS_WEEKEND
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val slotMinutes: Int = 60,                         // 30 | 60 | 120
) {
    fun planFor(day: DayOfWeek): DayPlan?             // null when the day is off
}
```

- **Slots.** A day's slots are `windowStart`, `windowStart + slotMinutes`, … while
  `slotEnd <= windowEnd`. The default window 8:00 → 20:00 with 60-minute slots is the same 12
  slots as v1 (checks at 8:50 … 19:50). The check fires at `slotEnd − lead`, where lead is a
  sixth of the slot: 5 min for 30, 10 min for 60, 20 min for 120.
- **Slot goal** = `ceil(dailyGoal / slotCount)`, per plan. The weekday and weekend plans can
  have different goals (feature 8) and different hours (feature 2).
- **Days off** (feature 2): no checks, no celebrations, and the Home ring shows "Day off".
  Days off are streak-neutral (see feature 7).
- **Window must not cross midnight.** Night-shift windows stay out of scope; the model allows
  them later without migration because start and end are `LocalTime`s.
- **Catch-up pace** is unchanged: deficit over the remaining slots of today's plan.

**Settings UI.** Settings becomes grouped:

```
Goal & schedule
  Daily goal ............ 7,000            (hidden when mode = weekdays/weekend)
  Schedule .............. Every day / Weekdays & weekend
  Weekdays .............. 7,000 · 8:50 AM–7:50 PM    (opens plan editor)
  Weekend ............... 5,000 · 9:50 AM–5:50 PM    (opens plan editor)
  Reminder days ......... Mon–Fri, Sun               (7 toggle chips)
  Reminder interval ..... Every hour                 (Phase E; hidden until then)
Reminders
  Pause reminders ....... Off / Paused until Mon
  Second nudge .......... On                          (Phase C)
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

**Storage.** Settings stay in Preferences DataStore with new keys; old keys migrate on first
read (old `dailyGoal`/`firstHour`/`lastHour` become the weekday plan, mode EVERY_DAY).

**Step storage.** Replace the hourly buckets with raw interval points so any slot length can
be summed:

```
step_points(startMillis, endMillis, steps)      indexed on endMillis
day_summary(date, steps, dailyGoal, slotsHit, slotsTotal, status)   // written at day end; feeds streaks
```

Room version 2 with a migration that converts each `hourly_steps` row into one point spanning
that hour. Retention: points 14 days, summaries 400 days.

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

**Tests.** Slot generation for 30/60/120 with the default and odd windows; weekday vs weekend
plan selection; days off and paused days skipped by `nextCheckTime`, including a pause that
spans a weekend; DST transitions with 30-minute slots; migration of hourly rows to points;
settings key migration.

---

## Phase B — Glanceability

### 1. Tile and watch-face complication

**Tile** (`androidx.wear.tiles` 1.6.2 + `protolayout-material3` 1.4.2, both fine on API 36):

- Layout: progress arc for this slot, big step count, "of 584", "Today 3,100 / 7,000", and the
  catch-up line when behind. States: goal met (full arc, "Goal met"), day off, paused.
- Tap anywhere opens the app. A second tap target on "Paused" resumes.
- Freshness: `setFreshnessIntervalMillis(5 min)` plus `TileService.getUpdater().requestUpdate()`
  after each step batch, throttled to once a minute, and at slot boundaries.
- Debug: Android Studio's tile preview via `@Preview` with `wear-tooling-preview`.

**Complication** (`watchface-complications-data-source-ktx` 1.3.0):

- Types: `RANGED_VALUE` (this slot's progress, text "412"), `SHORT_TEXT` ("412/584"),
  `LONG_TEXT` ("412 of 584 this hour · 3,100 today").
- Tap opens the app. Update via `ComplicationDataSourceUpdateRequester.requestUpdateAll()` after
  step batches (throttled) and at slot boundaries; the manifest declares a 60-second update
  period as the fallback.
- Day off / paused shows "Off" / "Paused" with the ring empty.

Both read the same `SlotStatus` snapshot the Home screen uses, computed by a small
`SlotStatusProvider` so the three surfaces never disagree.

### 4. Today's hours view

- Home becomes a horizontal pager: **Ring** (today's Home) → **Today** → **Streak** (Phase D).
  Wear Compose Material 3 `HorizontalPager` with the page indicator. Settings stays on the
  edge button of the first page.
- **Today page:** one row per slot: time, steps / goal, and a status glyph:
  ✓ hit · ✗ short · – skipped (charging, workout, asleep, paused, late) · ◦ upcoming. The
  current slot is live. Tapping a skipped row shows the reason. Rows come from `step_points`
  (steps) joined with `check_log` (outcome and skip reason).
- Also shows "Last check 2:50 PM · 584 of 584" at the top, so a user who didn't get a reminder
  can see whether the check ran. This is the answer to "it didn't buzz, is it broken?".

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

- **Second nudge** (setting, default on): after a shortfall at `slotEnd − lead`, arm a
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

## Decisions to confirm before Phase A

1. **Weekday / weekend only, or full per-day editing?** The model stores seven plans either
   way; the UI proposal is two plans plus day toggles. Full per-day editing can come later.
2. **Interval options:** 30 / 60 / 120 minutes. Anything else (45, 90)?
3. **Streak rule:** a missed counted day resets to zero (no automatic grace day).
4. **Second nudge default:** on.
5. **Tile content:** this slot's ring only, or should it also show today's total ring?
