# NipoVPN Android

NipoVPN Android is the Android client application for **NipoVPN**.

The main NipoVPN core is developed in the main repository:

https://github.com/MortezaBashsiz/nipovpn

NipoVPN is a proxy/VPN-style project designed to hide real HTTP/S traffic inside fake HTTP requests.

## Features

* Android client for NipoVPN
* Built with Kotlin
* Jetpack Compose UI
* Uses the native NipoVPN core
* Supports Android 7.0+ (`minSdk 24`)
* Package name: `net.sudoer.nipo`

## Project Structure

```text
nipovpn-android/
├── README.md
└── NipoVPN/
    ├── app/
    ├── gradle/
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradlew
    └── gradlew.bat
```

## Requirements

* Android Studio
* JDK 11 or newer
* Android SDK
* Gradle wrapper included in the project

## Build

Clone the repository:

```bash
git clone https://github.com/MortezaBashsiz/nipovpn-android.git
cd nipovpn-android/NipoVPN
```

Build debug APK:

```bash
./gradlew assembleDebug
```

Build release APK:

```bash
./gradlew assembleRelease
```

The generated APK files will be available under:

```text
NipoVPN/app/build/outputs/apk/
```

## Run in Android Studio

1. Open Android Studio.
2. Select **Open**.
3. Choose the `NipoVPN` directory.
4. Let Gradle sync.
5. Select a device or emulator.
6. Click **Run**.

## Main Core

This Android application depends on the main NipoVPN core project:

```text
https://github.com/MortezaBashsiz/nipovpn
```

The core project contains the main networking logic, proxy engine, native binaries, and protocol implementation.

## Releases

Android releases are published from GitHub Actions.

You can download APK builds from the repository release page:

```text
https://github.com/MortezaBashsiz/nipovpn-android/releases
```

## License

This project follows the same license as the main NipoVPN project.

See the main repository for more details:

```text
https://github.com/MortezaBashsiz/nipovpn
```

## Author

Morteza Bashsiz

```text
https://github.com/MortezaBashsiz
```
