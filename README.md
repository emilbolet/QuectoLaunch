# QuectoLaunch

A small Android home-screen app: a simplified take on the Microsoft Launcher layout, with its own name and design.

> [!WARNING]
> **This project is vibe coded.** All of the code was written by an AI (Claude Code), directed by the owner in plain-language conversation. No human has reviewed the code line by line.
>
> It has mostly been run on an Android emulator, in English, with limited use on one real phone. There are no automated tests. Treat it as a personal experiment: read the code before you rely on it, and use it at your own risk.

## What it does

- **Home screen:** your wallpaper, a grid of pinned apps and widgets, and a dock. The clock and the search bar are widgets too: hold one to move, resize or remove it, and add it again from the Widgets list.
- **App list:** swipe up anywhere outside the dock. Every installed app (including work-profile apps), sorted A–Z by your phone's language, as a grid of icons under sticky letter headings. You can switch to a plain list in settings. On a phone with a work profile, a Personal / Work switch at the top chooses which apps are listed.
- **A–Z scroller:** touch or drag the letters on the right edge of the app list to jump. A big letter bubble shows where you are and the phone gives a light tick on each new letter.
- **Search:** tap the search bar on home, or type at the top of the app list. Names that start with what you typed come first; Enter opens the top result, and there is always a "search the web" row.
- **Pinning apps:** hold an app in the list and drag it onto the home screen or the dock. Hold an icon on home to move it.
- **Home screens:** swipe sideways between them. Hold an empty spot and choose Home screens to add, remove or reorder them (hold and drag a preview to move it). There is always at least one. You can also make a new one by dragging an icon to the right edge of the screen and holding it there.
- **Widgets:** hold an empty spot on the home screen and choose Widgets. Hold a widget to resize, move or remove it.
- **Folders:** drop one app on another. Tap the folder to open it, tap its title to rename it.
- **Long-press an app:** its shortcuts (while QuectoLaunch is your default home app), Remove, Change icon, App info, Uninstall.
- **Icon packs:** choose one in settings. Apps the pack doesn't cover are styled to match where the pack allows it, calendar and clock icons show the date and time where the pack has them, and "Change icon" lets you pick any icon from a pack for a single app.
- **Swipe-up dock:** swipe up on the dock for a second row of apps.
- **Settings:** hold an empty spot on the home screen and choose Launcher settings. Light/dark theme, accent color (wallpaper colors, presets or your own), transparency of the app list, dock, folders and menus, icon pack, grid size, icons per dock row, app names in the dock, app list layout, and gestures.
- **Gestures (optional, off by default):** double-tap an empty spot to lock the phone, swipe down for notifications. These need Android's accessibility access, which the app explains before asking.

## Running it

1. Open this folder in Android Studio (File → Open) and let it sync.
2. Plug in your phone with USB debugging on (or start an emulator) and press Run.
3. On the phone, tap **Set as default home app**, or go to Settings → Apps → Default apps → Home app.

To go back to your normal launcher, change the same setting back.

From a terminal: `./gradlew installDebug` (see `CLAUDE.md` for the Java and SDK paths on this machine).

## Releases

A GitHub Actions workflow (`.github/workflows/build.yml`) builds and lints every push. Pushing a tag also publishes a signed APK as a GitHub release.

To make a release:

1. Set `versionName` and `versionCode` in `app/build.gradle.kts`, and add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (it becomes the release notes).
2. Commit, then tag the commit with `v` plus the version name and push the tag: `git tag v0.1 && git push origin v0.1`.

The workflow refuses to release if the tag and `versionName` differ. A version with a suffix, such as `0.2-beta1`, is published as a pre-release.

### Signing key (one-time setup)

Android only installs an update over an existing app if both are signed with the same key, so the key must be created once and kept. Create it outside the repository:

```
keytool -genkeypair -keystore quectolaunch-release.jks -alias release \
    -keyalg RSA -keysize 4096 -validity 10000
```

Then add four repository secrets under Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | the output of `base64 -w0 quectolaunch-release.jks` |
| `SIGNING_KEYSTORE_PASSWORD` | the keystore password |
| `SIGNING_KEY_ALIAS` | `release` |
| `SIGNING_KEY_PASSWORD` | the key password (the same as the keystore password unless you chose another) |

Back up the keystore file and its password somewhere safe. If they are lost, no update can be installed over an earlier release; users would have to uninstall first and lose their layout.

## For developers

`CLAUDE.md` describes the architecture, the patterns to keep, and what each feature is expected to do. It is also the instruction file the AI works from, so it shows how the project was built.

The application ID and code package are `dk.emilbolet.quectolaunch`.
