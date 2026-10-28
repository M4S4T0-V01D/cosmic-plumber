# Cosmic Plumber

**Chicago, 1986.** The pipes are acting weird, the alderman wants his envelope, and something keeps beaming up the
sewer rats. You're the newest plumber at Galactic Drain & Sewer. Drive your van across the city, fix the cosmic
plumbing, and try not to get a parking ticket.

A pixel-art Android game, written in plain Kotlin on the Android framework with no game engine or libraries.

## Features

- **Drive the van yourself** around a top-down Chicago with the river, bridges, the Loop, traffic and gas stations.
  Gas costs 89 cents and it's a ripoff.
- **The clipboard** holds your service calls: the client, the address, the problem and the pay.
- **No cell phones, it's 1986.** When your pager beeps, find a payphone and call Dolores at dispatch for new jobs.
  You can also buy a CB radio.
- **Pipe puzzles.** Rotate the pipe tiles to route the water from the main to the drain before the pressure blows.
- **Wrench rhythm.** Guitar Hero, but plumbing. Bolts scroll down three lanes (HOT, GAS, COLD) and you tighten each
  one on the beat. Every job gets its own synthesized song.
- **Day/night cycle.** Headlights, street lamps and lit windows come on at dusk. Night calls pay time-and-a-half.
- **Money and upgrades** from Sal's Plumbing Supply & Auto Body: a V8 engine, a bigger gas tank, a torque wrench,
  a pressure gauge, an "alderman envelope" and the CB radio, plus paint jobs for the van.
- **Built-in updater.** Settings › Updates checks this repository's GitHub releases. It downloads the new APK,
  verifies its SHA-256 against `latest.json`, and hands it to Android's installer.

## Install

Download `CosmicPlumber.apk` from the [latest release](https://github.com/M4S4T0-V01D/cosmic-plumber/releases/latest)
and open it on your phone. You'll need to allow installs from your browser or file manager. Android 10 or newer is
required.

## Build

The build needs JDK 17 and the Android SDK (platform 36).

```sh
./build.sh testDebugUnitTest assembleDebug   # unit tests + debug APK
./build.sh assembleRelease                   # signed release APK -> ./CosmicPlumber.apk (needs keystore.properties)
./release.sh "What changed"                  # bump version, build, push, publish a GitHub release
```

The signing key and `keystore.properties` stay on the build machine and are never committed.

## Code map

| File | What it does |
| --- | --- |
| `Game.kt`, `Save.kt`, `Model.kt` | Game loop and view, scenes, save data, jobs, upgrades and payouts |
| `Gfx.kt`, `CityArt.kt`, `City.kt` | Pixel renderer (180-pixel-high frame, nearest-neighbor scaling), sprites and the city map |
| `DriveScene.kt` | Driving, traffic, payphones, gas and the day/night lighting |
| `PipeScene.kt` | Pipe puzzle generator and minigame |
| `RhythmScene.kt`, `Audio.kt` | Wrench rhythm game, song composer and synth |
| `TitleScene.kt`, `HqScene.kt`, `ClipboardScene.kt`, `JobScenes.kt`, `ShopScene.kt`, `SettingsScene.kt` | Screens |
| `Updater.kt` | GitHub release updater |
