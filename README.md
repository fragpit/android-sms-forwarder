# Android SMS Forwarder

Minimal Kotlin Android app for a separate SIM phone. It receives incoming SMS,
reconstructs multipart messages, and forwards them to Telegram through the
Telegram Bot API over HTTPS. It can also notify Telegram about missed cellular
calls, including the caller number and call time, and low battery charge.

## Build APK

The project builds in Docker, so local Gradle and Android SDK installation is not
required.

```bash
task apk
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Signing key storage

Every build reuses the existing signing key. `task prepare-signing` verifies its
pinned SHA-256 checksum and maintains three copies:

- Primary: `~/.local/share/android-sms-forwarder/signing/sms-forwarder-debug.jks`.
- Backup: `~/Documents/Backups/android-sms-forwarder/signing/sms-forwarder-debug.jks`.
- Working copy: `signing/sms-forwarder-debug.jks` in this checkout.

On the first run, an existing working copy is copied to the primary and backup
locations. Later, any missing copy is restored from a verified surviving copy.
Keys have file permissions `600`; primary and backup directories have `700`.
Include the backup directory in your external backups to protect against losing
this computer or its disk.

`task apk` checks the key before building the Docker image. Gradle also verifies
it before signing, including for direct Gradle builds. Docker receives the
working key through a read-only mount, and signing keys are excluded from both
Git and the Docker build context. Keep `signing/keystore.sha256` in Git: it is a
public checksum, not the private key.

If all copies are missing or any copy has a different checksum, the build stops.
It never generates a replacement key automatically. Restore the original key
from backup; a newly generated key cannot update an app signed with an older one.

Override the persistent directories with `SMS_FORWARDER_SIGNING_DIR` and
`SMS_FORWARDER_SIGNING_BACKUP_DIR` when needed. Both must be absolute paths.

## Telegram bot setup

1. Open Telegram and start a chat with `@BotFather`.
2. Send `/newbot`.
3. Follow BotFather prompts and copy the bot token.
4. Paste the token into the app field `Telegram Bot Token`.

## Get chat_id

1. Send any message to your new bot from the target Telegram chat.
2. Open this URL in a browser, replacing `<TOKEN>` with your bot token:

   ```text
   https://api.telegram.org/bot<TOKEN>/getUpdates
   ```

3. Find `chat.id` in the JSON response.
4. Paste that value into `Telegram Chat ID`.

For a group chat, add the bot to the group first, send a group message, and then
read `chat.id` from `getUpdates`.

## SMS permissions

On first launch, press `Grant permissions` in the app and allow SMS and
notification permissions. If Android does not show the prompt:

1. Open Android Settings.
2. Go to Apps.
3. Open `SMS Forwarder`.
4. Open Permissions.
5. Allow `SMS`.
6. On Android 13 and newer, also allow Notifications.

The app requests `RECEIVE_SMS`, `READ_SMS`, `INTERNET`,
`RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, and the Android 13+
notification permission.

## Missed call notifications

Enable `Notify missed calls`, press `Save settings`, and allow `Phone` and
`Call logs` permissions (`READ_PHONE_STATE` and `READ_CALL_LOG`). This switch
works independently of `Forward SMS` and sends alerts to the same Telegram chat.
The SMS sender filter does not apply to calls.

The app checks Android's call log after a call ends and sends only calls marked
as missed. Answered, outgoing, rejected, and blocked calls do not produce alerts.
Hidden or unavailable numbers are shown with an explanatory label. Calls from
before the switch was enabled are not imported. Detection runs in background
work, so delivery can be delayed by Android's battery restrictions.

Android may restrict call-log permission for sideloaded apps. If the permission
cannot be granted, check the app's Android settings and installer restrictions.
SMS forwarding continues to work without call-log access.

To check on a phone, leave an incoming call unanswered and verify that Telegram
shows `Пропущенный вызов`, the number, and the time. Then answer a call and
reject another call; neither should generate an alert. With internet disabled,
miss a call and restore connectivity to check queued delivery.

## Keep alive messages

Enable `Send keep alive`, set `Keep alive interval (hours)`, and press
`Save settings`. The default interval is 24 hours; any positive whole number
of hours is accepted. Keep alive is off by default and works independently
of SMS and missed call forwarding, using the same Telegram bot and chat.

The first message arrives after the selected interval and includes the phone's
current time. Changing the interval starts a new countdown; saving unchanged
settings or reopening the app preserves it. Turning the switch off and saving,
or resetting app data, cancels the schedule. It survives reboot and app updates.

Keep alive uses a wake-up alarm that can run while the phone is in Doze, and
sends directly during that wake-up. On Android 12+, allow `Alarms & reminders`
using `Allow scheduled alerts` if the app shows this button. Without this
access the app uses an inexact alarm, so delivery can be delayed. Phones that
exempt the app from battery optimization may already allow exact alarms.

Each alarm schedules the next one, so a crash or process kill at the wrong
moment could stop the chain. A 15-minute background watchdog re-arms keep alive
and low battery alarms from their saved deadlines; an overdue deadline fires
right away. The watchdog only runs while one of these features is enabled.

Delivery requires internet access. Failed requests get up to two background
retries within the current interval. Missed heartbeats do not accumulate, so
reconnecting does not send a backlog. The app holds a short wake lock only while
attempting delivery; it does not keep the phone awake between messages.
Keep alive confirms that the app can reach Telegram; it does not verify SMS or
call-log permissions.

To verify delivery during sleep on a connected test phone, build the test APK:

```bash
docker run --rm --platform linux/amd64 -v "$PWD:/workspace" -w /workspace \
  android-sms-forwarder-builder gradle --no-daemon \
  -Dorg.gradle.vfs.watch=false :app:assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class \
  dev.local.smsforwarder.keepalive.KeepAliveSchedulerTest \
  dev.local.smsforwarder.test/androidx.test.runner.AndroidJUnitRunner
```

Install the current debug APK first and configure Telegram with keep alive
enabled. The device test temporarily forces Doze and sends one real heartbeat,
then restores the original schedule and power state. It also checks that
unchanged settings preserve the deadline and disabling keep alive cancels it.

## Low battery notifications

Enable `Notify low battery`, set `Low battery threshold (%)`, and press
`Save settings`. The threshold accepts whole percentages from 1 to 100 and
defaults to 20%. The feature is off by default and uses the same Telegram bot
and chat independently of SMS, missed calls, and keep alive.

The app checks charge shortly after enabling the feature or changing the
threshold, then every 15 minutes using wake-up alarms. Allow `Alarms & reminders`
using `Allow scheduled alerts` when prompted. Android may delay checks without
this access or under device-specific power restrictions. The schedule survives
reboot and app updates without keeping a service or wake lock running between
checks.

An alert includes the observed charge, threshold, and detection time. Charge
must be strictly below the threshold: with 20% configured, 20% does not alert
and 19% does. Enabling the feature when already below the threshold also sends
an alert, including while charging. Only one alert is created until a check
observes charge at or above the threshold again. Saving unchanged settings or
restarting the app does not repeat it. Changing the threshold starts a new check.

Failed sends use the encrypted retry queue. Observed charge recovery, disabling
the feature, changing the threshold, or resetting app data removes pending
battery alerts so they do not arrive after they become irrelevant. Battery
alerts share the queue's size, age, and retry limits.

## Disable battery optimization

For stable background delivery:

1. Open Android Settings.
2. Go to Battery.
3. Open Battery optimization or App battery usage.
4. Select `SMS Forwarder`.
5. Choose Unrestricted or Do not optimize.

Different vendors rename this screen. On Xiaomi, Huawei, Oppo, Vivo, Samsung,
and similar devices, also check autostart/background activity restrictions.

## Security notes

- SMS contents and caller numbers are never logged.
- Delivered SMS and call history are not stored by the app. Call monitoring
  retains only an activation time and the last processed call-log row ID.
- Undelivered SMS, missed-call alerts, and battery alerts share an encrypted
  retry queue, capped at 50 messages, 12 delivery attempts, and 24 hours.
- Network security config disables cleartext traffic and uses HTTPS for
  `api.telegram.org`.
- The encrypted preferences file is excluded from Android backup and device
  transfer.

## Sender filter

The optional sender filter accepts comma or newline separated numbers. Empty
filter means all senders are forwarded.

## Reset app data

Use `Reset app data` inside the app to clear Telegram settings, sender filter,
retry queue, and duplicate cache. Android Settings can also clear everything:

1. Open Android Settings.
2. Go to Apps.
3. Open `SMS Forwarder`.
4. Open Storage.
5. Press Clear storage.

<!-- Full-access chat visibility check. -->
