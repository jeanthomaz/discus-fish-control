# Discus Fish Control / Acará Controle 0.8.0

This is an early development release of the Android BLE controller for OceanTech NOUR aquarium lights.

## Highlights

- Localize the app name as **Discus Fish Control** in English and **Acará Controle** in Brazilian Portuguese.
- Connect to lamps advertising the OceanTech `TO-L-XXXX` name pattern.
- Control lamp power, automatic/manual mode, and four manual light channels.
- Inspect the daily automatic schedule in a graph and edit one time slot at a time.
- Configure weekly repeat days and synchronize the lamp clock.
- Prevent saving two active schedule entries at the same time.
- Respect the device's status-bar safe area while scrolling.

## Build and installation

There is no prebuilt APK attached to this release. Open the source project in Android Studio, install Android SDK Platform 36, use JDK 17 or later, then choose **Build > Build APK(s)**.

## Validation status

The previous app version connected to and controlled the maintainer's NOUR freshwater lamp. The 0.8.0 schedule editor, scan filter, and status-bar changes have not yet been validated on the physical lamp. Treat this as a development build.
