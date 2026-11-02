# Hourly Steps

Standalone Wear OS app (Pixel Watch 4, Wear OS 6 / API 36) that reminds you at :50 of each hour
when you are short of an hourly step goal derived from your daily goal.

- Default: 7,000 steps/day over 12 reminders (8:50 AM – 7:50 PM) = 584 steps/hour.
- When behind pace, the reminder adds the catch-up rate: `312 more steps this hour (660/hr to catch up today)`.
- Once the daily goal is met, goal reminders stop. Optional **Inactivity reminders** keep checking
  against a smaller per-hour threshold (default 250).
- Reaching this hour's goal is celebrated once per hour: a "da-da-daaa" buzz, then a
  "🎉 Hourly goal achieved!" alert that opens a fireworks screen. If the app is open, the
  fireworks show straight away. If the watch screen is asleep, it is lit briefly so the alert is
  visible on wrist raise, unless Do Not Disturb or Bedtime mode is on. Turn it off with
  **Celebrate goals** in Settings.
- Your week doesn't have to be Monday to Friday. Use one plan or two, each with its own goal and
  hours, and set every day of the week to Plan A, Plan B or Off.
- **Pause reminders** for the rest of today, through tomorrow, for a week, or until you resume.
  Steps keep counting.
- See your progress without opening the app: a **tile** with two rings (today outside, this hour
  inside), watch-face **complications** for this hour and for today, and a **Today** page (swipe
  left on Home) showing each hour's result and why a reminder did or didn't fire.
- Reminders stay out of the way: they skip during tracked workouts and sleep, have **Snooze 5 min**
  and **Pause today** buttons, and can optionally buzz once more near the end of the hour with
  **Second nudge**. A skipped reminder is explained on the Today page.

Design: [docs/HourlyStepsPlan.md](docs/HourlyStepsPlan.md). Planned v2 features: [docs/V2Plan.md](docs/V2Plan.md).

## Build and test

Requires JDK 17+ (Android Studio's bundled JBR works) and Android SDK platform 36.

```bash
./gradlew assembleDebug testDebugUnitTest
```

## Install on the watch

Enable ADB debugging and wireless debugging on the watch, pair it, then:

```bash
./gradlew installDebug
```

## Try it on an emulator

Use a Wear OS 6 (API 36) emulator, for example the "Wear OS Large Round" device with the
`system-images;android-36;android-wear-signed;x86_64` image. On Wear OS 4 and later the
emulator's Health Services generates synthetic walking steps automatically (roughly 100–200 per
minute), so passive step data starts arriving as soon as the app is granted permission. The old
`whs.synthetic.user.START_WALKING` adb broadcasts only apply to Wear OS 3 images; to change the
generated data use the Wear Health Services panel in Android Studio's emulator controls.

To exercise both sides of a check with constant walking, change the goal instead of the steps:
a high daily goal (for example 30,000) produces a shortfall reminder, a low one (1,000) puts the
day in the goal-met state.

Debug builds have **Run check now**, **Show celebration** and **Slot length** buttons at the bottom
of Settings. Run check now runs the check immediately, ignoring the charging skip. Show celebration
opens the fireworks screen. Slot length cycles 30, 60 and 120 minutes; screen text still says "this
hour" until the real interval setting ships. To trigger a celebration from the background instead, for example with the
watch on its face or asleep, debug builds also accept:

```bash
adb shell am broadcast -a com.hourlysteps.debug.CELEBRATE -p com.hourlysteps
```

Sleep comes from Health Services' synthetic events. No workout app is on the emulator, so debug
builds can pretend one is running. Clock jumps make the app re-register, which re-delivers the true
state, so set this after any jump:

```bash
adb shell am broadcast -a whs.synthetic.user.START_SLEEPING com.google.android.wearable.healthservices
```

```bash
adb shell am broadcast -a whs.synthetic.user.STOP_SLEEPING com.google.android.wearable.healthservices
```

```bash
adb shell am broadcast -a com.hourlysteps.debug.SET_ACTIVITY --es state EXERCISE -p com.hourlysteps
```

Useful adb commands while testing:

```bash
adb shell dumpsys alarm | grep -A2 "com.hourlysteps.action.CHECK"
```

```bash
adb logcat -s ReminderCheck ReminderScheduler RescheduleReceiver
```

```bash
adb shell dumpsys battery set ac 1
```

```bash
adb shell dumpsys battery reset
```

Exact alarms are off by default on Wear OS. The app's "On-time reminders" button opens the
system "Alarms and reminders" list, where you pick Hourly Steps and turn it on. Without it, checks
run some time in the 10 minutes before :50.

Checklist for reminder changes, because each of these behaves differently on Wear OS:

- **App open:** no pop-up appears, but the watch should vibrate.
- **Watch face showing:** the alert pops up and the channel pattern plays.
- **Screen asleep:** the screen wakes, then the alert pops up and the channel pattern plays.
- **Do Not Disturb on:** the screen stays asleep and nothing buzzes.

To sleep the screen and jump the clock to just before a check:

```bash
adb shell input keyevent KEYCODE_SLEEP
```

```bash
adb shell cmd time_detector set_auto_detection_enabled false
```

```bash
adb shell cmd alarm set-time <epoch-milliseconds>
```

Turn automatic time back on afterwards with the same `time_detector` command set to `true`.

Wear OS only plays a notification channel's own vibration for a background app, and shows the
pop-up only for a channel that vibrates. A channel's pattern can't change after creation, so the
vibration strength is part of the channel id, and "Off" uses an imperceptible 1 ms pulse.

To add the tile and attach complications on an emulator:

```bash
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile --ecn component com.hourlysteps/.surfaces.HourlyStepsTileService
```

```bash
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --ecn component com.google.android.wearable.watchface.rwf/com.google.android.wearable.watchface.athletica.AthleticaWatchFaceService
```

```bash
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication --ecn component com.hourlysteps/.surfaces.TodayComplicationService --ecn watchface com.google.android.wearable.watchface.rwf/com.google.android.wearable.watchface.athletica.AthleticaWatchFaceService --ei slot 0 --ei type 5
```

In the complication command, `type` is 5 for a gauge, 3 for short text and 4 for long text. On a
real watch, add the tile from the tile list and pick complications in the watch face editor.

## Layout

| Package | Role |
|---|---|
| `domain` | Pure logic: schedule and slots, goal maths, catch-up pace, check decision, next-alarm time, day status, today summary. Fully unit-tested. |
| `data` | Settings (DataStore), raw step points and check log (Room), Health Services passive listener. |
| `reminder` | Exact-alarm chain, the slot check, notifications and vibration, reschedule on boot/time change. |
| `celebration` | Goal celebration trigger, fireworks screen. |
| `surfaces` | Tile, complications, and the ring and complication text they share with Home. |
| `ui` | Compose for Wear OS Material 3: Home rings, Today page, Settings, plan and week editors, pause, pickers, permissions. |
