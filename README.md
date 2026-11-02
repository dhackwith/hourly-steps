# Hourly Steps

Standalone Wear OS app (Pixel Watch 4, Wear OS 6 / API 36) that reminds you at :50 of each hour
when you are short of an hourly step goal derived from your daily goal.

- Default: 7,000 steps/day over 12 reminders (8:50 AM – 7:50 PM) = 584 steps/hour.
- When behind pace, the reminder adds the catch-up rate: `312 more steps this hour (660/hr to catch up today)`.
- Once the daily goal is met, goal reminders stop. Optional **Inactivity reminders** keep checking
  against a smaller per-hour threshold (default 250).

Design: [docs/HourlyStepsPlan.md](docs/HourlyStepsPlan.md).

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

Debug builds have a **Run check now** button at the bottom of Settings that runs the :50 check
immediately, ignoring the charging skip.

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
run inside a 10-minute window around :50.

## Layout

| Package | Role |
|---|---|
| `domain` | Pure logic: goal maths, catch-up pace, check decision, next-alarm time, hour bucketing. Fully unit-tested. |
| `data` | Settings (DataStore), per-hour step buckets and check log (Room), Health Services passive listener. |
| `reminder` | Exact-alarm chain, the :50 check, notifications and vibration, reschedule on boot/time change. |
| `ui` | Compose for Wear OS Material 3: Home ring, Settings, value pickers, permissions. |
