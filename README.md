# QuectoLaunch

A small Android home-screen app: a simplified take on the Microsoft Launcher layout, with its own name and design.

> [!WARNING]
> **This project is vibe coded.** All of the code was written by an AI (Claude Code), directed by the owner in plain-language conversation. No human has reviewed the code line by line.
>
> It has mostly been run on an Android emulator, in English, with only brief use on one real phone. There are no automated tests. Treat it as a personal experiment: read the code before you rely on it, and use it at your own risk.

## What it does

- **Home screen:** your wallpaper, a clock and date, a grid of pinned apps, a search bar and a dock.
- **App list:** swipe up anywhere outside the dock. Every installed app (including work-profile apps), sorted A–Z by your phone's language, as a grid of icons under sticky letter headings. You can switch to a plain list in settings.
- **A–Z scroller:** touch or drag the letters on the right edge of the app list to jump. A big letter bubble shows where you are and the phone gives a light tick on each new letter.
- **Search:** tap the search bar on home. Names that start with what you typed come first; Enter opens the top result, and there is always a "search the web" row.
- **Pinning apps:** hold an app in the list and drag it onto the home screen or the dock. Hold an icon on home to move it.
- **Home screens:** swipe sideways between them. Hold an empty spot and choose Home screens to add, remove or reorder them (hold and drag a preview to move it). There is always at least one. You can also make a new one by dragging an icon to the right edge of the screen and holding it there.
- **Widgets:** hold an empty spot on the home screen and choose Widgets. Hold a widget to resize, move or remove it.
- **Folders:** drop one app on another. Tap the folder to open it, tap its title to rename it.
- **Long-press an app:** its shortcuts (while QuectoLaunch is your default home app), Remove, App info, Uninstall.
- **Swipe-up dock:** swipe up on the dock for a second row of apps and quick toggles (flashlight, Wi‑Fi, Bluetooth, sound, settings).
- **Settings:** hold an empty spot on the home screen and choose Launcher settings. Light/dark theme, accent color, icon pack, grid size, icons per dock row, app list layout, and gestures.
- **Gestures (optional, off by default):** double-tap an empty spot to lock the phone, swipe down for notifications. These need Android's accessibility access, which the app explains before asking.

## Running it

1. Open this folder in Android Studio (File → Open) and let it sync.
2. Plug in your phone with USB debugging on (or start an emulator) and press Run.
3. On the phone, tap **Set as default home app**, or go to Settings → Apps → Default apps → Home app.

To go back to your normal launcher, change the same setting back.

From a terminal: `./gradlew installDebug` (see `CLAUDE.md` for the Java and SDK paths on this machine).

## For developers

`CLAUDE.md` describes the architecture, the patterns to keep, and what each feature is expected to do. It is also the instruction file the AI works from, so it shows how the project was built.

The application ID and code package are `dk.emilbolet.quectolaunch`.
