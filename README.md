# Discus Fish Control / Acará Controle

An Android app for controlling OceanTech NOUR aquarium lights over Bluetooth Low Energy (BLE). The app is named **Discus Fish Control** in English and **Acará Controle** in Brazilian Portuguese.

Choose English or Brazilian Portuguese from the language button in the app header. The selection is saved on the device.

## Features

- Find nearby OceanTech lamps and connect over BLE.
- Control power and switch between automatic and manual modes.
- Adjust white, blue, green, and red channels in manual mode.
- View the daily light schedule as a four-channel graph and an ordered visual timeline.
- Add up to ten schedule points, see each point’s channel levels at a glance, and edit its time and intensity.
- Preview edits on the graph, avoid duplicate active times, and save each point to the lamp.
- Switch the app between English and Brazilian Portuguese; the choice is remembered.
- Configure weekly schedule days and synchronize the lamp clock.
- Filter scan results to the OceanTech device name pattern `TO-L-XXXX`.

## Compatibility

- Android 6.0 (API 23) or later.
- OceanTech NOUR BLE aquarium lights. The selected model defaults to **NOUR freshwater**.
- Bluetooth permissions are required. Android may also request nearby-device or location permission depending on its version.

## Build

1. Open this project directory in Android Studio.
2. Install Android SDK Platform 36 when prompted and use JDK 17 or later.
3. Allow Gradle sync to finish.
4. Select **Build > Build APK(s)** to create a debug APK.

The project uses Java and Android Gradle Plugin 8.13.2.

## Project status

This is an independent community project based on BLE behavior and commands observed in OceanTech's older Android app. Version 0.9.0 is a development build. The earlier app version was confirmed to connect to and control the maintainer's lamp. The new schedule editor and language selection compile, but still need validation on hardware.

See the repository's **Releases** page for release notes and downloadable builds when available.

## License

No license has been selected yet. Until one is added, the source is not granted an open-source license.
