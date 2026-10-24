# Alarmist

An alarm clock that makes you get up. To turn it off, you first complete a mission: solve a few sums, walk a
number of steps, or scan the barcode on something in your bathroom. Free and open source, in the spirit of
Alarmy, without ads or tracking.

## What it does
- **Missions** (up to five per alarm): math, memory tiles, Simon, tap the numbers in order, typing, shaking,
  steps, squats, scanning a barcode or QR code, finding a photographed spot, tapping an NFC tag.
- **Wake-up check:** a while after you dismiss the alarm, it asks if you're still awake, and rings again if
  you don't answer.
- **Optional anti-cheat:** keep the alarm screen in front, lock the volume, block the power menu while ringing.
- **Built to ring:** Android's alarm clock API (exempt from battery saving), rings after a reboot even before you
  unlock, survives the app being killed, and a reliability check explains the phone settings that can stop alarms.
- **Private:** no internet permission, no ads, no tracking, no Google Play Services. Backups go to a file you choose.

## Status
Early days. It has been tested on a small number of phones and on the Android emulator. Phones differ a lot in
how they treat alarms and sensors, so reports from other devices are very welcome.

An F-Droid submission is planned. Until then you can build it yourself (below).

## How it's made
Alarmist is built entirely by [Claude](https://claude.com/product/claude-code), Anthropic's AI model: it writes
all of the code. I'm not a Kotlin or Java programmer. I decide what the app should do, guide the work, review
the results and test every build on real phones. Commits carry a `Co-Authored-By: Claude` line.

If you spot code that looks odd, you're probably right. Please open an issue or a pull request.

## Help wanted
Use it, fork it, change it. All of these help:
- **Test it on your phone** and tell us how it went, good or bad: [docs/TESTING.md](docs/TESTING.md) lists what to
  try. The phone maker, model and Android version matter most for alarms.
- **Report bugs and ideas** in [Issues](../../issues).
- **Send pull requests:** fixes, new missions (they're plugins), translations, docs. See
  [CONTRIBUTING.md](CONTRIBUTING.md).

## Build
JDK 17 and an Android SDK (`local.properties` with `sdk.dir=…`, or `ANDROID_HOME`).

```bash
scripts/check.sh                    # build, unit tests, lint, format check (prints errors only)
./gradlew :app:installFdroidDebug   # install a debug build on a phone or emulator
scripts/release-check.sh            # the release build is reproducible and has no network permission
```

How the app works: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Each module also has a `README.md`.

## License
[GPL-3.0-or-later](LICENSE).
