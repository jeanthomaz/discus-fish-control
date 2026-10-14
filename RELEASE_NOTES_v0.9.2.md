# Discus Fish Control / Acará Controle 0.9.2

## Changes

- Add a dedicated Dawn and dusk section with editable start/end time pickers and an explicit save action, translated as Amanhecer e anoitecer in Brazilian Portuguese.
- Read the current times from the lamp and write both zero-light anchor records when saving.
- Preview the daylight window on the graph with a shaded interval and tappable dawn/dusk markers.
- Keep the eight color changes in their own list, with removal and full-capacity feedback. New color points are placed at a free minute inside the saved cycle.
- Validate the same-day ordering observed in the original app. Identify saved color points outside a proposed window without silently moving them.
- Wait for both anchor writes before reporting overall success. Track each accepted write so a partial failure retains the last known lamp state and offers retry feedback.

## Validation

Java sources compile against Android SDK 36. All 74 host-side checks passed, including dawn/dusk command encoding, time-window boundaries, available-time selection, conflict feedback, completion/failure callbacks, partial anchor writes, and disconnect cancellation.

On-device UI and physical-lamp behavior still need verification. Bluetooth write completion confirms the transport operation; it does not independently verify persisted firmware state.

Build a fresh APK in Android Studio to try this version.
