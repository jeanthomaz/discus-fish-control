# Discus Fish Control / Acará Controle

An Android app for controlling OceanTech NOUR aquarium lights over Bluetooth Low Energy (BLE). The app is named **Discus Fish Control** in English and **Acará Controle** in Brazilian Portuguese.

Choose English or Brazilian Portuguese from the language button in the app header. The selection is saved on the device.

## Features

- Find nearby OceanTech lamps and connect over BLE.
- Control power and switch between automatic and manual modes.
- Adjust white, blue, green, and red channels in manual mode.
- View the daily light schedule as a four-channel graph and an ordered visual timeline.
- Set the dawn start and dusk end in a dedicated editor, preview the daylight window, and save both times to the lamp.
- Configure up to eight color changes inside that window; the lamp has ten records including dawn and dusk.
- Remove a color change from the lamp and reuse its position; the Add time button explains when all eight color positions are occupied.
- Preview edits on the graph, avoid duplicate active times, and save each point to the lamp.
- Switch the app between English and Brazilian Portuguese; the choice is remembered.
- Configure weekly schedule days and synchronize the lamp clock.
- See nearby success or failure feedback for clock sync, weekly repeat, time saves, and removal after the Bluetooth write completes.
- Filter scan results to the OceanTech device name pattern `TO-L-XXXX`.

## Daily light cycle

In **Schedule > Dawn and dusk**, tap each time and choose **Save dawn and dusk**. In Brazilian Portuguese, these controls are **Amanhecer e anoitecer**. Dawn marks the start at 0%; dusk marks the end at 0%. The four channels transition through your saved color points between them.

The NOUR 1.0.8 app treats these as a same-day cycle: dawn is earlier than every active color change and dusk is later than every active color change. The editor identifies any saved point outside the proposed window so you can move or remove it first. To extend the cycle, save the new limits before adding color points outside the old window.

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

This is an independent community project based on BLE behavior and commands observed in OceanTech's older Android app. Version 0.9.2 is a development build. The earlier app version was confirmed to connect to and control the maintainer's lamp. The removal frames, dawn/dusk commands, window validation, and Bluetooth completion/failure paths passed 74 host-side checks, and the Java sources compile against Android SDK 36. The updated UI and actual lamp behavior still need validation on hardware.

See the repository's [Releases page](https://github.com/jeanthomaz/DiscusFishControl/releases) for release notes and downloadable builds when available.

## License

No license has been selected yet. Until one is added, the source is not granted an open-source license.
