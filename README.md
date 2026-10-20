<div align="center">

# IRRemoteCloner

[![Version](https://img.shields.io/badge/version-1.0-blue.svg)](#)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-blueviolet.svg)](#)
[![License](https://img.shields.io/badge/license-not%20set-lightgrey.svg)](#license)

**A pocket infrared remote cloner for Android. Clone, organise, edit and replay IR remotes from your phone.**

[Report a Bug](https://github.com/Huerte/RemoteCloner-Android/issues) · [Request a Feature](https://github.com/Huerte/RemoteCloner-Android/issues)

</div>

---

<p align="center">
  IRRemoteCloner turns an Arduino and your phone into a universal IR remote. Point any remote at the receiver, press a button, and the app records the signal. Replay it later to control the same TV, air conditioner or fan. No account, no internet connection, and no laptop needed, not even to install the board firmware.
</p>

---

## Table of Contents

- [Installation Guide](#installation-guide)
- [What You Need](#what-you-need)
- [What It Does](#what-it-does)
- [How It Works](#how-it-works)
- [Supported Boards](#supported-boards)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [Creator](#creator)
- [License](#license)

---

## Installation Guide

There are two ways to get IRRemoteCloner. Pick whichever fits you.

### Option A: Download the APK (Recommended)

You do not need Android Studio or any technical knowledge for this option.

1. Go to the [**Releases**](https://github.com/Huerte/RemoteCloner-Android/releases) page.
2. Under the latest release, download the **`.apk`** file to your phone.
3. Open the downloaded file.
4. Tap **Install**, then **Open**.

> **Note:** Android may ask you to allow installs from your browser or file manager the first time. This is normal for any app installed outside the Play Store. Allow it for this one install, then you can switch it off again.

---

### Option B: Build from Source (For developers)

Use this if you want to read the code, modify it, or build the APK yourself.

**Prerequisites:**

| What you need   | Where to get it |
|-----------------|-----------------|
| Android Studio (a recent release that supports Android Gradle Plugin 8.10) | [developer.android.com/studio](https://developer.android.com/studio) |
| Git             | [git-scm.com](https://git-scm.com/downloads) |

**Steps:**

1. Clone the repository:
   ```
   git clone https://github.com/Huerte/RemoteCloner-Android.git
   cd RemoteCloner-Android
   ```

2. Build the debug APK:
   ```
   ./gradlew assembleDebug
   ```
   On Windows PowerShell use `.\gradlew assembleDebug`.

You can also open the folder in Android Studio, let Gradle sync, then choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

**Project structure:**

```
RemoteCloner-Android/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/huerte/remotecloner/
│       │   │   ├── data/          # Remote model, JSON format, file storage
│       │   │   ├── serial/        # USB serial, board protocol, firmware flasher
│       │   │   └── ui/            # Jetpack Compose screens and view models
│       │   ├── assets/firmware/   # Bundled Arduino firmware (.hex)
│       │   └── res/               # Resources and USB device filter
│       └── test/                  # JVM unit tests
├── gradle/                        # Gradle wrapper and version catalog
├── build.gradle.kts
├── settings.gradle.kts
└── README.md                      # This file
```

---

## What You Need

| Item | Notes |
|------|-------|
| Android phone with USB host (OTG) support | Android 8.0 or newer |
| USB OTG adapter or cable | Connects the Arduino to your phone |
| Arduino Uno or Nano (ATmega328P) | Both the classic chip and the surface-mount version work |
| IR receiver module (VS1838B or TSOP38238) | Captures signals. Output pin to **D11** |
| IR LED, NPN transistor (for example 2N2222) and a resistor of about 100 ohm | Replays signals. Wired to **D3** |

One Arduino is enough. The **Combined** firmware captures and transmits on the same board, so you only need a single USB port. If you prefer, you can use two boards: one **Receiver** and one **Sender**.

---

## What It Does

The app has five tabs along the bottom of the screen:

| Tab         | What it does |
|-------------|--------------|
| Home        | A dashboard with a card for each page and a count of your remotes and keys. |
| Clone       | Type a key name, tap **Listen for signal**, then press that button on the original remote. Supports auto-save, test send, and batch capture of a whole list of keys. **Insert common keys** fills in POWER, MUTE, VOL_UP, VOL_DOWN, CH_UP, CH_DOWN, the arrow keys, OK, MENU, BACK, HOME, INPUT and the digits 0 to 9. |
| Library     | Create, rename, duplicate, delete, import and export remotes as JSON files. Add notes, search keys, and edit, rename, duplicate, delete, re-learn or test-send any key. A full key editor exposes protocol, address, command, carrier frequency and raw timings. |
| Control     | Fire keys as buttons. Choose **RAW** (exact pulse timings) or **DECODED** (compact protocol command) mode, set 0 to 10 repeats, and run macros: sequences of keys and delays such as `POWER, 500, VOL_UP`. |
| Config      | Choose and connect the receiver and sender boards, set the baud rate, ping, read the live serial log, and **install the firmware on your Arduino** with the Set up board card. |

---

## How It Works

IRRemoteCloner talks to the Arduino over USB serial at 115200 baud using the Android USB Host API.

- The **Receiver** (or Combined) firmware listens for one key press and reports both the decoded values (protocol, address, command) and the raw pulse timings.
- The **Sender** (or Combined) firmware replays a stored key, either from the raw timings or from the decoded values.
- A board is verified when you connect it: the app sends a ping and checks which firmware answers.
- **Set up board** installs the firmware straight from the phone. It resets the Arduino into its bootloader, writes the bundled `.hex` file over USB, reads it back to verify, then starts the new program. It does the same job `avrdude` does on a PC.

Remotes are stored as one JSON file each in the app's private storage, in the same format as the Windows version of the app, so remotes can be moved between the two.

- No internet permission is requested.
- No account or sign-in is needed.
- No data is sent anywhere. Everything stays on your phone.

---

## Supported Boards

| Board | Use the app | Install firmware from the phone |
|-------|-------------|---------------------------------|
| Arduino Uno (ATmega328P, classic or surface-mount, including CH340 clones) | Yes | Yes |
| Arduino Nano (ATmega328P, new bootloader) | Yes | Yes |
| Arduino Nano (ATmega328P, old bootloader) | Yes | Yes, found automatically at the slower 57600 baud |
| Arduino Mega 2560 | Yes, once its firmware is installed | Not yet. Use the Windows app for the one-time install. |

The firmware is built with avr-gcc 7.3.0, Arduino AVR core 1.8.6 and IRremote 4.7.1.

Before writing anything, the installer checks the chip signature and refuses a chip that is not an ATmega328 family part.

---

## Troubleshooting

| Problem | What to try |
|---------|-------------|
| "No USB serial devices found" | Check the OTG adapter, make sure the cable carries data and not just power, then tap **Refresh**. |
| "No reply from the board" when connecting | The board has no IRRemoteCloner firmware yet. Install it with **Set up board** on the Config tab, and make sure the baud rate is 115200. |
| "USB permission denied" | Connect again and tap **OK** on the Android permission dialog. Tick "always" to remember it. |
| "No reply from the bootloader" when installing | Try another data cable, then press the reset button on the Arduino right after tapping **Install firmware**. |
| Nothing is captured | Point the original remote at the receiver from close range. Capture times out after 20 seconds. Check that the receiver output is on D11. |
| A replayed key does nothing | Check the IR LED polarity and that the transistor is wired to D3. Try **RAW** mode, or add a repeat. |

---

## Contributing

Contributions are welcome. Here is how to go from zero to a submitted pull request.

### Getting Started

**Prerequisites:** Android Studio and Git.

**Fork and clone:**

```
# 1. Fork the repo on GitHub, then clone your fork
git clone https://github.com/YOUR_USERNAME/RemoteCloner-Android.git
cd RemoteCloner-Android

# 2. Keep your fork in sync with the original
git remote add upstream https://github.com/Huerte/RemoteCloner-Android.git
```

### Making Changes

**Branch naming:**

```
feat/short-description    # New features
fix/short-description     # Bug fixes
docs/short-description    # Documentation only
chore/short-description   # Maintenance or refactoring
```

**Commit messages:** Use plain English. Describe what changed and why:

```
# Good
git commit -m "fix: keep unknown JSON fields when saving a remote"
git commit -m "feat: add a repeat count to macros"
git commit -m "docs: clarify the OTG requirement in README"

# Avoid
git commit -m "fix stuff"
git commit -m "update"
```

**Code style:**

- Follow the existing patterns: Kotlin with Jetpack Compose, one view model per area.
- Keep the `serial` and `data` code free of Android classes where possible, so it can be unit tested on the JVM. The board protocol and the flasher work this way, through small interfaces.
- Add a unit test for any change to the protocol, the JSON format or the flasher. Run them with `./gradlew testDebugUnitTest`.
- Keep functions short. If something is growing, split it.
- Never swallow exceptions silently with an empty `catch` unless the operation is purely cosmetic.

### Submitting a Pull Request

1. Push your branch to your fork:
   ```
   git push origin feat/your-feature
   ```

2. Open a Pull Request against `Huerte/RemoteCloner-Android:main` on GitHub.

3. In the PR description, briefly explain: what you changed, why, and how to test it.

4. If your change affects the app's screens or behavior, update this README accordingly.

---

## Contributor

<div align="center">
  <table>
    <tr>
      <td align="center"><a href="https://github.com/Huerte"><img src="https://github.com/Huerte.png" width="80px;" alt=""/></a><br /><a href="https://github.com/Huerte"><b>Huerte</b></a><br />Creator</td>
    </tr>
  </table>
</div>

---

## License

No license has been chosen for this project yet. Add a `LICENSE` file and update the badge at the top of this page to match.

---

*Built to make IR remote cloning accessible to everyone.*
