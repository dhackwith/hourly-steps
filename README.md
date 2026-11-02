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

## Try it with synthetic steps (emulator)

```bash
adb shell am broadcast -a "whs.USE_SYNTHETIC_PROVIDERS" com.google.android.wearable.healthservices
adb shell am broadcast -a "whs.synthetic.user.START_WALKING" com.google.android.wearable.healthservices
adb shell am broadcast -a "whs.synthetic.user.STOP_EXERCISE" com.google.android.wearable.healthservices
```

Debug builds have a **Run check now** button at the bottom of Settings that runs the :50 check
immediately, ignoring the charging skip.

## Layout

| Package | Role |
|---|---|
| `domain` | Pure logic: goal maths, catch-up pace, check decision, next-alarm time, hour bucketing. Fully unit-tested. |
| `data` | Settings (DataStore), per-hour step buckets and check log (Room), Health Services passive listener. |
| `reminder` | Exact-alarm chain, the :50 check, notifications and vibration, reschedule on boot/time change. |
| `ui` | Compose for Wear OS Material 3: Home ring, Settings, value pickers, permissions. |
