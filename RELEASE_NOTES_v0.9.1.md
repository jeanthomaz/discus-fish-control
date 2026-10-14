# Discus Fish Control / Acará Controle 0.9.1

## Changes

- Remove saved times by disabling and clearing their fixed slot on the lamp. Unsaved drafts are removed locally.
- Reuse removed positions after reconnecting by recognizing the cleared record.
- Show the occupied-position count and keep the full Add time button tappable with explanatory feedback.
- Correct the ten-record interpretation from 0.9.0: the original protocol has eight adjustable color points and two zero-light cycle anchors. Label the anchors and preserve their zero intensities.
- Show saving/syncing/removing states, followed by a short notification and nearby success or failure text, in English and Brazilian Portuguese.
- Report weekly-repeat success only after both BLE commands complete. Failed, disconnected, or timed-out operations do not produce success feedback.
- Mark edited times as unsaved until their write succeeds.

## Validation

- Java compilation against Android SDK 36 passed.
- 52 host-side checks passed for command encoding, all ten removal records, reserved zero-light slots, cleared-record handling after reconnect, batch success/failure, and disconnect cancellation.
- Physical-lamp behavior and the UI still need on-device verification. Bluetooth write completion confirms the transport operation; it does not independently verify the lamp's persisted firmware state.

Build a new APK in Android Studio; the existing 0.9.0 APK is unchanged.
