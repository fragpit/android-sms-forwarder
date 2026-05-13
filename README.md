# Android SMS Forwarder

Minimal Kotlin Android app for a separate SIM phone. It receives incoming SMS,
reconstructs multipart messages, and forwards them to Telegram through the
Telegram Bot API over HTTPS.

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

`task apk` creates `signing/sms-forwarder-debug.jks` on the first run. Keep this
file if you want future APK builds to install over the previous version. If the
signing key is deleted, Android will reject an update over the already installed
app and you will need to uninstall the old app first.

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

- SMS contents are never logged.
- Delivered SMS history is not stored.
- Undelivered SMS are stored only in an encrypted retry queue, capped at 50
  messages and 24 hours.
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
