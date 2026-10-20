#!/usr/bin/env bash
set -euo pipefail
umask 077

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
key_name="sms-forwarder-debug.jks"
primary_dir="${SMS_FORWARDER_SIGNING_DIR:-$HOME/.local/share/android-sms-forwarder/signing}"
backup_dir="${SMS_FORWARDER_SIGNING_BACKUP_DIR:-$HOME/Documents/Backups/android-sms-forwarder/signing}"
working_key="$project_dir/signing/$key_name"
expected="$(cat "$project_dir/signing/keystore.sha256")"
temporary=""
trap 'if [[ -n "$temporary" ]]; then rm -f -- "$temporary"; fi' EXIT

if [[ ! "$expected" =~ ^[0-9a-f]{64}$ ]]; then
    printf 'Invalid signing/keystore.sha256. Restore the pinned checksum.\n' >&2
    exit 1
fi

if [[ "$primary_dir" != /* || "$backup_dir" != /* ]]; then
    printf 'Signing storage directories must be absolute paths.\n' >&2
    exit 1
fi

verify_key() {
    local actual
    actual="$(shasum -a 256 "$1" | awk '{print $1}')"
    if [[ "$actual" != "$expected" ]]; then
        printf 'Signing key mismatch: %s\nExpected SHA-256: %s\nRefusing to replace or generate a key.\n' "$1" "$expected" >&2
        return 1
    fi
}

keys=("$primary_dir/$key_name" "$backup_dir/$key_name" "$working_key")
source_key=""
for key in "${keys[@]}"; do
    if [[ -e "$key" || -L "$key" ]]; then
        if [[ ! -f "$key" ]]; then
            printf 'Signing key is not a readable regular file: %s\n' "$key" >&2
            exit 1
        fi
        verify_key "$key"
        source_key="${source_key:-$key}"
    fi
done

if [[ -z "$source_key" ]]; then
    printf 'Signing key is missing. Restore the existing key to one of:\n' >&2
    printf '  %s\n' "${keys[@]}" >&2
    printf 'A new key will not be generated.\n' >&2
    exit 1
fi

mkdir -p -- "$primary_dir" "$backup_dir" "$(dirname -- "$working_key")"
chmod 700 "$primary_dir" "$backup_dir"
for key in "${keys[@]}"; do
    if [[ ! -e "$key" ]]; then
        temporary="$(mktemp "$key.tmp.XXXXXX")"
        cat -- "$source_key" > "$temporary"
        if ! ln "$temporary" "$key" 2>/dev/null; then
            verify_key "$key"
        fi
        rm -f -- "$temporary"
        temporary=""
    fi
    verify_key "$key"
    chmod 600 "$key"
done

printf 'Signing key verified.\nPrimary: %s\nBackup: %s\n' "${keys[0]}" "${keys[1]}"
