# Helping test Alarmist

Alarms behave differently on every phone: battery savers, lock screens and sensors vary by maker and Android
version. Testing on your phone, and telling us how it went, is one of the most useful things you can do.
Please report results, good or bad, in an issue with your phone maker, model and Android version.

## Everyday checks (no tools needed)
Before you start: open the reliability check (shield icon on the alarm list) and fix what it suggests.
Each editor also has **Preview**, which rings the alarm 10 seconds later through the real path; lock the phone
to see the full ringing screen.

**Ringing**
- Set an alarm a few minutes ahead and lock the phone. Does the screen turn on and show the alarm over the lock
  screen? Does the sound rise gradually? Vibration?
- Snooze: does it ring again after the snooze time? Dismiss from the screen and from the notification.
- Leave it untouched: it goes quiet after a few minutes, rings again, and finally shows a "Missed" notification.
- Restart the phone with an alarm due soon and **don't unlock it**. Does it still ring?
- Leave the phone overnight on battery with an alarm set. Did it ring on time? If not, the event log at the bottom
  of the reliability check says what happened.

**Missions** (add them in the editor; "Try it" runs one without an alarm)
- Each mission you use: is it clear what to do? Too hard or too easy?
- While ringing: opening a mission mutes the alarm; stop for 30 seconds and it rings again; leave the screen and
  it rings at once.
- **Steps:** walk with the phone in your hand. Does it count roughly one per step? Does shaking count?
- **Squats, shake:** do real squats or shakes count, and does walking around not count?
- **Barcode, photo, NFC:** register something in your bathroom, then try it at night with the lights on and off.

**Wake-up check and anti-cheat** (in the editor)
- Turn on the wake-up check: after dismissing, does "Still awake?" appear and ring again if you don't answer?
  Also in silent mode and Do Not Disturb?
- "Keep the alarm screen in front": while ringing, press Home. Does the alarm screen come back?
- "Block the power menu": while ringing, hold the power button. Is the menu closed?

**Everything else**
- Back up your alarms (Settings), restore them on another phone.
- Dark theme, a 24-hour clock, a large font size, TalkBack: anything hard to read or use?

## Developer checks (adb)
Install a debug build: `./gradlew :app:installFdroidDebug`. Logs: `adb logcat -s Alarmist`. Debug builds have
**Test alarm** buttons above the alarm list (rings in under two minutes; "Quick test" runs a full auto-silence
cycle in about five).

- **Process death while ringing:** `adb shell am crash com.orthohin.alarmist.debug`. Ringing resumes in seconds.
- **Reboot before unlock (direct boot):** set a PIN, snooze an alarm, `adb reboot`, don't unlock. It rings over the
  lock screen.
- **App update while snoozed:** reinstall; the snooze is booked again.
- **Doze:** deep idle won't start within an hour of an alarm, so move the clock: screen off,
  `adb shell dumpsys battery unplug`, `adb shell cmd alarm set-time <due − 2 h, ms>`,
  `adb shell dumpsys deviceidle force-idle deep`, then `cmd alarm set-time <due − 15 s>`. It rings on time.
  Clean up: `cmd alarm set-time $(date +%s%3N)`, `dumpsys deviceidle unforce`, `dumpsys battery reset`.
- **Missed and late rings:** `am force-stop` the app, move the clock past an alarm (5 min: it rings late at
  once; 20 min: a "Didn't ring" notification), then start the app.
- **Permissions revoked:** `adb shell pm revoke <package> android.permission.POST_NOTIFICATIONS` (or CAMERA,
  ACTIVITY_RECOGNITION): the reliability check flags it, and a mission that can't run offers "Skip this mission".
- **Emulator sensors:** `adb emu sensor set acceleration x:y:z` fakes motion (gravity is on y when upright); each
  sensor mission's README has a recipe.
- **Step recordings:** see CONTRIBUTING.md.
