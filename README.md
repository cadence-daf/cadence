<p align="center"><img src="logo.svg" width="128" alt="Cadence logo"></p>

<h1 align="center">Cadence</h1>

<p align="center"><b>Delayed auditory feedback</b></p>

Cadence is an Android app that plays your own voice back to you with a short delay, for speech practice.

## Install

<p align="center">
  <a href="https://github.com/cadence-daf/cadence/releases/latest"><img src=".github/badges/download-apk.svg" width="260" alt="Download the APK from GitHub"></a>
  &nbsp;
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/cadence-daf/cadence"><img src=".github/badges/obtainium.svg" width="260" alt="Add to Obtainium"></a>
</p>

- **Direct download:** on your phone, download the `.apk` file from the [latest release](https://github.com/cadence-daf/cadence/releases/latest) and open it. If Android asks, allow installs from your browser or file manager.
- **With Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium), a free app from another developer, then tap the button above on your phone and tap Add. It shows you when a new version is out.

You need Android 8.0 or newer and earbuds with a microphone. Wired earbuds are recommended.

<p align="center"><b>Private by design: free, no ads, no analytics, no accounts, no internet access.<br>Everything stays on your phone and never leaves it.</b></p>

- **No ads, ever.** Free and open source (GPL-3.0).
- **No analytics, no tracking, no accounts.** Nothing to sign up for.
- **No internet access.** The app does not request the permission, so it cannot send anything anywhere.
- **Your voice is never recorded.** It is played back live and discarded. Only the date and length of each session are saved, on your phone.

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_main.png" width="200" alt="Main screen">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_fine_tune.png" width="200" alt="Fine-tune controls">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_settings.png" width="200" alt="Settings">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_progress.png" width="200" alt="Progress">
</p>

## What it does

Cadence plays your own voice back to you through wired earbuds, delayed by 40 to 200 milliseconds. A gate mutes the playback when you are not speaking, so traffic and construction noise are not played back. Everything runs on the phone, and you need wired earbuds with a microphone.

## Features

- **Delay:** 40 to 200 ms in 10 ms steps. While you practise, the screen shows the actual delay: your setting plus the delay your phone and earbuds add.
- **Only your voice:** a gate plays back only when you speak. A sensitivity slider sets how easily it opens.
- **Automatic voice boost:** quiet speech from a weak headset microphone is raised to a clear level. Background noise does not raise the boost.
- **Pitch shift and volume:** shift your voice from -12 to +12 semitones and set the loudness in your earbuds.
- **Microphone mode:** Voice recognition (recommended) or Unprocessed.
- **Fading program:** lowers the delay in steps you choose (how much, how often, and where it stops). Pause it or step back at any time.
- **Named presets:** save your favourite delay, sensitivity, pitch and volume combinations and give them names.
- **Runs in the background:** the notification has Stop, Mute and a delay button (60, 100, 150 ms), so it works with the screen off.
- **Stops when it should:** practice stops when you unplug the earbuds, when a call comes in, when another app starts playing, or when you swipe the app away.
- **Helpful hints:** a timer shows how long you have practised, and a hint appears if the microphone is too quiet to hear you.
- **Progress:** a daily goal (off, 5, 10, 15 or 20 minutes), a month calendar you can browse back through, a 7 or 30-day line chart and your all-time total. Only the date and length of each session are saved.
- **Daily reminder (optional):** one notification at a time you choose, skipped when you have already practised that day.

## Why wired earbuds

Wired earbuds add the least extra delay on top of the setting you choose. They also avoid the feedback that open-ear earbuds can cause.

## Requirements

- Android 8.0 or newer
- Wired earbuds with a microphone

## Build

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

```bash
cmake -S native-tests -B build/native-tests -DCMAKE_BUILD_TYPE=Debug && cmake --build build/native-tests && ctest --test-dir build/native-tests --output-on-failure
```

## Release

- Bundle: app/build/outputs/bundle/release/app-release.aab
- Signing keys are not stored in this repository.

## License

Cadence is free software under the [GNU General Public License v3.0](LICENSE). libfvad in third_party/libfvad and RNNoise in third_party/rnnoise keep their own BSD licenses.

## Medical disclaimer

Cadence is a practice tool, not a medical device. It does not diagnose or treat any condition.
