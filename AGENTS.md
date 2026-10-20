# AGENTS.md

## Project Overview

This repository contains a minimal Kotlin Android app for a dedicated SIM phone.
The app receives incoming SMS, reconstructs multipart messages, filters senders,
and forwards messages to Telegram through the Telegram Bot API over HTTPS.
It also detects missed cellular calls from the Android call log and sends
caller number and time notifications through the same encrypted delivery queue.

Primary code paths:

- `app/src/main/java/dev/local/smsforwarder/calls/`: phone-state broadcasts,
  delayed call-log scans, missed-call classification, and the monitoring cursor.
- `app/src/main/java/dev/local/smsforwarder/sms/`: SMS receiving, parsing,
  duplicate detection entrypoints, retry scheduling, boot handling, and worker
  based Telegram delivery.
- `app/src/main/java/dev/local/smsforwarder/storage/`: encrypted preferences,
  settings, duplicate cache, and bounded pending message queue.
- `app/src/main/java/dev/local/smsforwarder/telegram/`: Telegram formatting,
  HTTP client, and send result types.
- `app/src/main/java/dev/local/smsforwarder/ui/`: Compose settings screen,
  activity, view model, and theme.
- `app/src/main/res/`: Android manifest resources, backup rules, network
  security config, icons, colors, themes, and strings.

## Build And Verification

Use Docker-backed tasks from `Taskfile.yml`; local Gradle and Android SDK are not
required.

Common commands:

```bash
task apk
task clean
```

`task apk` builds the debug APK at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Every APK build runs `task prepare-signing`. It reuses the key from
`~/.local/share/android-sms-forwarder/signing/sms-forwarder-debug.jks`, maintains
a backup in `~/Documents/Backups/android-sms-forwarder/signing/`, and restores
the ignored working copy at `signing/sms-forwarder-debug.jks` when needed.
The key checksum is pinned in the tracked `signing/keystore.sha256` file.
Never generate a replacement key or change that checksum to bypass a mismatch.
If all copies are missing, restore the original key from backup. Signing keys
must not be committed or included in the Docker build context.

For Gradle-level checks inside the builder image, prefer:

```bash
docker run --rm --platform linux/amd64 -v "$PWD:/workspace" -w /workspace android-sms-forwarder-builder gradle --no-daemon -Dorg.gradle.vfs.watch=false :app:assembleDebug
```

Run a full APK build after changes that affect Kotlin, Android resources,
manifest entries, dependencies, signing, Dockerfile, or Gradle configuration.

## Coding Conventions

- Keep the app small and direct. Prefer local Android/Kotlin APIs already used
  in the repository over new frameworks or background mechanisms.
- Kotlin targets JVM 17. Keep code compatible with `compileSdk = 36`,
  `minSdk = 26`, and `targetSdk = 36`.
- Compose UI lives in `ui/`. Preserve the existing Material 3 style and simple
  settings-screen workflow.
- Use WorkManager for retry delivery paths and Android receivers/services for
  SMS, boot, and foreground delivery behavior.
- Do not add broad logging. SMS contents, Telegram tokens, chat IDs, and queued
  message bodies must not be logged.
- Keep comments sparse. Add comments only for exported objects/functions or for
  behavior that would otherwise be hard to audit.
- Do not introduce test cases that lock in known-bad behavior. If existing
  behavior looks wrong, call it out and test the intended behavior.

## Security And Privacy

- Store settings, pending messages, and duplicate cache only through
  `SecurePreferences`.
- Preserve backup exclusions in `backup_rules.xml` and `data_extraction_rules.xml`
  for encrypted preference data.
- Keep cleartext traffic disabled. Telegram delivery should remain HTTPS-only
  through `api.telegram.org`.
- Do not persist delivered SMS history. Pending messages should stay bounded by
  queue size, retry attempts, and age.
- Be careful with manifest permissions and exported components. Only broaden
  permissions or exported surfaces when required for Android platform behavior.

## Repository Hygiene

- Do not commit generated Gradle output, APKs, captures, IDE files, local
  properties, or signing keys.
- Existing local changes may be user work. Inspect `git status --short` before
  editing and avoid reverting unrelated changes.
- Keep README updates aligned with actual `Taskfile.yml`, manifest permissions,
  and user-visible settings.
- After editing Markdown files, run:

  ```bash
  markdownlint-cli2 --config ~/.markdownlint-cli2.yaml AGENTS.md
  ```
