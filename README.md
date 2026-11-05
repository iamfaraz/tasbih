# Tasbih

A tasbih counter and prayer times app for Android phones and Wear OS watches, with optional Zikr ring support.

Count on your watch's rotating bezel or a Zikr ring, and the count follows you across devices. Tasbih also works out prayer times for your location, reminds you before each prayer's time ends, and shows how long is left.

Built and tested on a Samsung phone running Android 17, a Galaxy Watch6 Classic and a Zikr ring. Other devices should work, but haven't been tested.

## Features

### Dhikr

- **33 / 33 / 34 sequence** (the default): Subhan Allah ×33, Alhamdulillah ×33 and Allahu Akbar ×34. It moves to the next dhikr by itself, and starts another round after 100.
- **Single dhikr:** Subhan Allah, Alhamdulillah, Allahu Akbar, La ilaha illa Allah, Astaghfirullah, Salawat, Subhan Allahi wa bihamdihi, Subhan Allahil Azim, La hawla wa la quwwata illa billah and Hasbunallahu wa nimal wakil.
- **Your own dhikr:** add up to eight.
- **Vibrate every** 10, 33, 99, 100 or 1000 counts, or at the end of each dhikr in the sequence.
- **All-time totals** that survive resets.

### Counting

| Device | How it counts |
|---|---|
| Zikr ring | Each press. The ring's number takes priority over everything else |
| Watch | Tap the screen, or turn the bezel or crown clockwise. Turning back does nothing |
| Phone | Shows the count, and doesn't add to it |

### Prayer times

- Calculated on the device for your current location, or for a city you search for.
- **School of thought:** Hanafi, Shafi'i, Maliki or Hanbali.
- **Calculation method:** Karachi, Muslim World League, ISNA, Egypt, Umm al-Qura or Dubai.
- Shows Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha and Tahajjud.
- **Hijri date** that changes at Maghrib, with a ±2 day adjustment for local moon sighting.

| | Hanafi | Shafi'i, Maliki, Hanbali |
|---|---|---|
| Asr starts when a shadow is | Twice an object's length | Once its length |
| Isha starts after the | White twilight (18°) | Red twilight (15°) |

Tahajjud starts at the last third of the night, measured from Maghrib to Fajr, for every school. Isha is treated as lasting until Fajr.

> **Calculated times can differ from your local mosque by a few minutes.** The twilight angles are conventional approximations, so please check them against a local timetable.

### Alerts

- **Prayer time alerts** with a system sound or any audio file. Each prayer can be switched on or off.
- **"Have you prayed?" reminders** 10 to 60 minutes before a prayer's time ends.
- **Prayer time left:** an optional Live Update showing the time left and a route from start to end, with a **Mark as prayed** button. It returns at the next prayer.
- **Prayer alerts on watch:** the watch works out its own alerts, so they still arrive away from the phone. They only fire while the watch is being worn.

### Watch

- Bead ring that fills as you count.
- Complication for watch faces, in short, long and ranged styles.
- Tile showing the dhikr, count and progress.
- Buzzes at milestones while being worn, even when the count came from the ring.

### Zikr ring

- The ring's number sets the counters on every press, on connecting and every five minutes. For example, 70 gives Subhan Allah 33, Alhamdulillah 33 and Allahu Akbar 4.
- Holding the ring's button to reset it clears the sequence, keeping all-time totals.
- Buzzes at milestones, including counts made on the watch.
- Battery and charging state in Settings on the phone and watch.
- The ring shows its own prayer alerts, using your location and school.
- A foreground service keeps it connected with the app closed.

The ring's screen can't show custom text on the firmware tested, and its number can't be set from the phone.

## Install

Download both APKs from the same [release](../../releases/latest):

- `tasbih-phone-<version>.apk`
- `tasbih-watch-<version>.apk`

### Phone

Open the APK on your phone and allow installs from your browser or file manager when asked.

### Watch

Watches can't install an APK by tapping it, so use [adb](https://developer.android.com/tools/releases/platform-tools) over Wi-Fi:

1. On the watch, open **Settings > About watch > Software** and tap **Software version** seven times.
2. Open **Settings > Developer options** and turn on **Wireless debugging**. Make sure the watch is on the same Wi-Fi as your computer.
3. Tap **Pair new device** and run these with the details shown:

```sh
adb pair <watch-ip>:<pairing-port>
adb connect <watch-ip>:<debug-port>
adb -s <watch-ip>:<debug-port> install tasbih-watch-<version>.apk
```

Installing over a build signed with a different key fails. Uninstall that build first, which clears its saved counts.

### First run

1. Open Tasbih on the phone and allow location and notifications.
2. Open Tasbih on the watch.
3. To use a ring, go to **Settings > Zikr ring**, press the ring to wake it, and pick it from the list.
4. For reliable alerts, go to **Settings > Apps > Tasbih > Battery** on the phone and choose **Unrestricted**.

## Build from source

You need Android Studio or JDK 21, and the Android SDK with platform 36.

```sh
./gradlew testDebugUnitTest assembleDebug
```

Debug builds are signed with your local debug key. Install both from the same computer, because **the phone and watch apps only sync when they share a signing key and package name.**

Debug builds also include an adb-only hook for exploring the ring's protocol. It isn't part of release builds.

### Project layout

| Module | Contents |
|---|---|
| `shared` | Counter state, sync, prayer times, settings and shared UI |
| `mobile` | Phone app, alerts and ring connection |
| `wear` | Watch app, watch alerts, complication and tile |

### How sync works

State moves between the phone and watch over the Wearable Data Layer, which queues it until they reconnect.

Each device keeps its own tally, and the total is their sum. A reset records what has been counted instead of erasing it, so merging is a per-device maximum and counts from both sides survive being offline. See `TasbihState`. Settings use the newest change. Prayer settings come from the phone.

The ring isn't merged like this. It's the source of truth, so `syncToRing` works out the counters directly from its number.

## Releases

Releases are built by the **Release** workflow, which runs only when started by hand:

1. Open **Actions > Release > Run workflow**.
2. Enter a version such as `0.2.0`, and optionally a summary.

It runs the tests, builds and signs both APKs, tags the commit and publishes the release.

### Changelog

Each release's notes are written automatically:

1. The optional summary you entered.
2. A list of every commit since the previous release, taken from their subject lines.
3. A link comparing the two versions.

So clear commit messages make a clear changelog. You can edit the notes on GitHub after publishing.

### Signing

The workflow needs these repository secrets:

| Secret | Value |
|---|---|
| `TASBIH_KEYSTORE_BASE64` | The release keystore, base64 encoded |
| `TASBIH_KEYSTORE_PASSWORD` | Its password |
| `TASBIH_KEY_ALIAS` | The key's alias |

**Keep a backup of the keystore.** Without it, future releases can't update installed copies.

## Known issues

- **Ring shutdowns:** the ring has sometimes switched off shortly after receiving its prayer setup. The cause isn't confirmed, so Tasbih only sends that setup when something changes. A shutdown also clears the ring's count, and so the counters.
- **Brief ring disconnects** can happen, and it reconnects by itself.
- **Switching dhikr with a ring:** the chosen dhikr takes the ring's number, which also adds to its all-time total.
- **Marking a prayer** applies to today's list, so last night's Isha can only be marked from the notification before dawn.
- **Samsung's Now Bar** draws Live Updates in its own way, so the notification may look different on other phones.

## License

Tasbih is source-available under the [PolyForm Noncommercial License 1.0.0](LICENSE).

You can read, use, change and share it for any non-commercial purpose, including personal use, study and use by charities and educational institutions. Commercial use isn't allowed. Because of that limit, this isn't open source in the formal sense.

Copyright 2026 Faraz Ahmad

## Credits

- Prayer times use [adhan-java](https://github.com/batoulapps/adhan-java) by Batoul Apps.
- [DM Sans](https://github.com/googlefonts/dm-fonts) and [Amiri](https://github.com/aliftype/amiri) are bundled under the SIL Open Font License. See [`licenses`](licenses).

This project isn't affiliated with or endorsed by any ring manufacturer, Samsung or Google.
