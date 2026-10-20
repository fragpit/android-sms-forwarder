# Android SMS Forwarder

Minimal Kotlin Android app for a separate SIM phone. It receives incoming SMS,
reconstructs multipart messages, and forwards them to Telegram through the
Telegram Bot API over HTTPS. It can also notify Telegram about missed cellular
calls, including the caller number and call time.

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
- Undelivered SMS and missed-call alerts share an encrypted retry queue, capped
  at 50 messages, 12 delivery attempts, and 24 hours.
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
