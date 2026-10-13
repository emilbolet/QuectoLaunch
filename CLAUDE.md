# QuectoLaunch

An Android home-screen launcher: a simplified take on Microsoft Launcher, with its own name and visual design (no Microsoft branding, icons or assets).

The owner is in Denmark and uses a Danish phone, so Danish sorting and text matter.

## Status

**The original six-step roadmap is done**, plus six additions made afterwards: icons per dock row, the rename to QuectoLaunch with app ID `dk.emilbolet.quectolaunch`, multiple home pages, widgets, the grid layout for the app list, the "Home screens" overview for adding, reordering and removing pages, the Personal/Work switch in the app list, icons in the long-press menus, and the clock and search bar as widgets on the grid. The dock's quick toggles have been removed again. Each has a "keep this working" section below. Everything is committed on `main` and pushed to `origin` (github.com/emilbolet/QuectoLaunch, public since 2026-10-13).

Nothing is in progress. What could come next is under "Ideas and leftovers"; the owner picks.

### How we work

- One feature at a time: build it, check it on the emulator, update this file (a "keep this working" section, the architecture list, the ideas list), then commit.
- After each feature, tell the owner plainly what was tested and what wasn't, and what behaves differently from what they might expect.
- When a request is ambiguous, ask before building (the owner once wrote "rows in the app drawer" and meant icons per dock row).
- Features are checked on the emulator, in English. Since 2026-10-13 the owner also has a debug build on their own phone (Pixel 9 Pro, Android 17, Danish, with a second user profile); nothing has been tested there systematically.

### This machine

There is no system Java; use the JDK bundled with the Android Studio flatpak:

```
export JAVA_HOME=/var/lib/flatpak/app/com.google.AndroidStudio/x86_64/stable/active/files/extra/jbr
```

`adb` and `emulator` are not on `PATH`; they live in `~/Android/Sdk/platform-tools` and `~/Android/Sdk/emulator`. Start the emulator in the background with `~/Android/Sdk/emulator/emulator -avd Medium_Phone_API_37.0 -no-snapshot-save -no-boot-anim -no-audio`. If Gradle can't find the SDK, `local.properties` needs `sdk.dir` (Android Studio creates this when the project is opened once).

The project folder is still called `SimpleLauncher`. The `mobile-mcp` server is registered for this project but can't see the emulator until `adb` is on `PATH`; plain `adb` works fine instead.

### Testing on the emulator

- QuectoLaunch is the emulator's default home app and has "always allow" for creating widgets. The owner sometimes uses the emulator window too, so the layout may differ from what a test left behind.
- Screenshots: `adb exec-out screencap -p > file.png`. Element positions: `adb shell uiautomator dump /sdcard/u.xml`, then read the `bounds`.
- Drags: `adb shell input draganddrop x1 y1 x2 y2 1500` (long-presses, then drags). For a drag that pauses on the way, chain `adb shell input motionevent DOWN|MOVE|UP x y` with sleeps (moves must exceed the touch slop, about 21 px).
- Double-tap: `adb shell 'input tap X Y & sleep 0.15; input tap X Y; wait'`.
- The saved layout: `adb exec-out run-as dk.emilbolet.quectolaunch cat databases/home.db > home.db` (also `-wal` and `-shm`), then `sqlite3`.
- Swipes that start within about 60 px of the left or right screen edge are taken by Android's Back gesture.

## Stack

- Kotlin, Jetpack Compose, Material 3, single activity (`MainActivity`).
- AGP 9.4.1 (built-in Kotlin, so no `org.jetbrains.kotlin.android` plugin), Kotlin Compose plugin 2.4.21, Gradle 9.8.1, Compose BOM 2026.09.00, compileSdk 37.0, targetSdk 35, minSdk 26. Upgrading these is fine if something requires it. (The bundled JDK is 25, which needs Gradle 9.1+.)
- Room 2.8.5 with KSP 2.3.12 for the home layout; DataStore Preferences 1.2.1 for settings.
- No dependency injection framework, no navigation library. Keep dependencies minimal.

## Architecture

```
app/src/main/java/dk/emilbolet/quectolaunch/
  MainActivity.kt          Edge-to-edge, goes back to plain home on Home press (onNewIntent) and onStop; widget bind/configure results
  GestureService.kt        AccessibilityService used only for lock screen and open notifications
  LauncherViewModel.kt     App sections, home layout and settings (StateFlow); drawerOpen, query, drawerShowsWork, openFolderId, settingsOpen (Compose state)
  data/
    AppInfo.kt             Wraps LauncherActivityInfo; `key` = component + user
    Alphabet.kt            Locale-aware sort (Collator) and A–Z sections
    AppRepository.kt       LauncherApps: load apps, change callback, icons (LruCache, icon pack), shortcuts, launch, uninstall, web search
    IconPack.kt            Parses an icon pack's appfilter.xml and resolves its drawables
    HomeDatabase.kt        Room: HomeItem and HomeMeta (page count) entities, HomeDao (drop/remove/folder/widget/page rules live here, in transactions)
    HomeModels.kt          Grid constants, HomeEntry/HomeLayout (what the UI draws), DragSource, DropTarget
    Settings.kt            LauncherSettings (theme mode, accent, grid size, dock size and labels, app list layout, icon pack, gestures) and SettingsStore (DataStore)
    Widgets.kt             WidgetHost (AppWidgetHost/Manager: list, bind, configure, create views) and LauncherWidgetHostView
    SystemControls.kt      The gesture actions and the way to the accessibility settings
  ui/
    LauncherApp.kt         Root: home, folder popup, drawer, drag overlay; root pointer input that follows drags
    HomeScreen.kt          Default-home button, grid, EntryCell (icon + label + menu), scrim behind the open dock
    BuiltInWidgets.kt      The launcher's own widgets: clock and search bar (plain composables)
    Widgets.kt             WidgetLayer (Android and built-in widgets drawn over a page's cells), resize handles
    WidgetPicker.kt        The launcher's own widgets, then installed widgets by app (in-tree overlay)
    PageManager.kt         "Home screens" overview: page previews, drag to reorder, add, remove (in-tree overlay)
    Dock.kt                Swipe-up dock (AnchoredDraggable): two app rows
    FolderPopup.kt         Open folder with renamable title (in-tree overlay, not a dialog)
    DragDrop.kt            DragState: long-press menus, drag tracking, drop target from grid/dock bounds
    AppIcon.kt             AppIcon, EntryIcon (app or folder tile), AppMenu (shortcuts, Remove, App info, Uninstall), MenuIcon
    SettingsScreen.kt      Theme, accent color, icon pack, grid size, app list layout and gestures with the accessibility disclosure (in-tree overlay)
    AppDrawer.kt           Search field, Personal/Work switch, sectioned grid or list with sticky headers, results, letter bubble
    AlphabetScroller.kt    The A–Z strip
    theme/Theme.kt         Material 3; wallpaper colors on Android 12+ or a scheme built from the accent color
```

Patterns to keep:
- All system calls about apps go through `AppRepository`. UI never calls `PackageManager`/`LauncherApps` directly.
- Use `LauncherApps` (not `PackageManager.queryIntentActivities`) so work-profile apps work.
- Heavy work (loading apps, decoding icons, parsing XML) off the main thread.
- All user-facing text in `res/values/strings.xml`. Add Danish translations in `res/values-da/strings.xml` as features are added.
- Drags are tracked by `DragState.trackDrag` on the root Box (Initial pass), not by the icon that was long-pressed, so a drag survives the drawer or folder closing under it. Anything icons can be dragged out of must be drawn in the main Compose tree, not in a Dialog/Popup window.
- Icons report long-press with `dragState.longPress(source, menuKey)`; the menu shows while `dragState.menuKey` matches and closes when the finger moves (drag starts).
- No `QUERY_ALL_PACKAGES`. The `<queries>` block in the manifest gives visibility of launchable apps.

## What step 1 does (keep this working)

- Home screen: wallpaper (window shows it via the theme), clock + date (since a widget, see "Clock and search bar as widgets"), "Set as default home app" button (RoleManager `ROLE_HOME`) above the grid until default.
- Swipe up anywhere on home, or tap "Apps", to open the drawer.
- Drawer: alphabetical, with sticky letter headers. Apps are shown as a grid under each letter (the default) or as a list, one app per line; see "App list layout" below.
- **A–Z scroller (must-have feature for the owner):** strip on the right edge; touch/drag jumps instantly with `scrollToItem`, big letter bubble in the centre while touching, haptic tick per letter, current section highlighted during normal scrolling. Only letters that have apps are shown. Sorting uses the phone's locale, so in Danish Æ, Ø, Å are separate sections at the end, while accented letters like É fold into E in languages that treat them that way.
- Search: starts-with matches first, then word-start, then contains. Enter opens the top result; a "search the web" row is always last (it hands the query to whichever app answers `ACTION_WEB_SEARCH`).
- Long-press: App info, Uninstall (hidden for system and work-profile apps).
- Drawer closes on Back, Home, pull-down at top of list, and when the activity stops.

## What step 2 does (keep this working)

- Home: grid of pinned apps (5 columns × 7 rows by default, the top and bottom row normally holding the clock and search bar) with labels, a search bar (a widget; opens the drawer with the keyboard up), a small chevron (opens the drawer), and a 5-slot dock without labels.
- Layout is saved in Room (`home.db`, table `home_items`: container = home (-1) / dock (-2) / folder id, page, position, appKey, title). Home and dock positions = row × 100 + column (`cellPosition`), so they don't depend on the grid or dock size.
- Long-press an app in the drawer or on home: menu with app shortcuts (default launcher only), Remove (home only), App info, Uninstall. Keep holding and move to drag; the drawer/folder closes and the icon follows the finger; the cell under the finger lights up.
- Drop rules (`HomeDao.drop`): empty cell → place; app on app → new folder; app on folder → joins it; folder on something → swap. Dropping outside the grid and dock cancels.
- Folders: tap to open a popup, tap the title to rename, drag apps out. A folder left with one app turns back into that app.
- Uninstalled apps are removed from the layout (on the removal callback, and at startup for apps removed while we weren't running). Disabled/unavailable apps are only hidden.
- Not done yet: reordering inside a folder.

## What step 3 does (keep this working)

- The dock is a bottom sheet (`AnchoredDraggableState<DockValue>`, created in `LauncherApp`). Collapsed: one row of apps (5 by default). Expanded: it grows upwards to show a second row, turns solid, and dims the rest of the screen.
- Dock spacing, measured with 5 icons on a 411 dp wide screen: icons are 52 dp, 14 dp from the dock's top and bottom edge and 13 dp from its left and right edge, with 27 dp between icons. The dock's cells are exactly as wide as the home grid's, so dock icons line up with the grid columns above when both have the same count; insetting the dock's ends would break that.
- Gesture rule: a swipe up that starts on the dock expands it (the dock's `anchoredDraggable` consumes the drag); a swipe up anywhere else opens the drawer (`detectVerticalDragGestures` on the home screen).
- The dock collapses on swipe down, tap outside, Back, Home and when returning from another app (`LauncherViewModel.homeResets`).
- Dragging: holding an icon over the collapsed dock for 500 ms opens it so the second row can be reached. `DragState.dockRowBounds` holds the visible part of each row; the open dock is checked before the grid it covers.
- The expanded dock used to have quick toggles under the second row (flashlight, Wi‑Fi, Bluetooth, sound, settings). The owner had them removed on 2026-10-13; don't bring them back unasked.

## What step 4 does (keep this working)

- Long-press on empty home space shows a small menu: Widgets, Home screens, Launcher settings. The settings screen closes on Back or Home; changes apply and save immediately.
- Theme: follow system / light / dark. Use `MaterialTheme.colorScheme.isDark`, not `isSystemInDarkTheme()`, to ask whether the launcher is dark.
- Accent color: "Use wallpaper colors" (Android 12+ only, the default there) or one of 8 presets. A preset builds the whole color scheme from that hue in `accentColorScheme` (HSL-based; no extra library).
- A color of one's own: the rainbow swatch (first in the row) opens two sliders, Color (hue) and Vividness (saturation). There is no lightness slider because `accentColorScheme` only uses the accent's hue and saturation. The sliders show whenever the saved accent is not one of the presets. Changes are saved while dragging, so the settings screen itself is the preview.
- Grid size: 4–6 columns, 4–9 rows. Icons shrink when cells get small. Items outside a smaller grid stay in the database and are just not drawn. The dock has its own setting, "Icons per dock row" (4–7, both rows), which works the same way.
- "Show app names in the dock" (`dockLabels`, off by default) puts a one-line label under each dock icon, in both rows. The icons are then at most 44 dp and the dock's rows are 92 dp high instead of 80 (`dockRowHeight`), which keeps 14 dp between the dock's edge and the icon above it or the name below it, the same as without names. (At 80 dp the icons sat 8 dp from the top and the centre icon touched the grab handle.) The labels are white over the wallpaper while the dock is closed and take the theme's text color once it is more than half open, because the open dock is solid.
- Settings load asynchronously; `MainActivity` draws nothing until they arrive so the wrong theme never flashes.
- Room is at schema version 6 (1→2 and 2→3 re-numbered home and dock positions; 3→4 added the widget columns; 4→5 added the `home_meta` table with the page count; 5→6 added `home_items.builtIn` and `home_meta.layoutVersion`). Add a migration for any further schema change.

## What step 5 does (keep this working)

- Settings has an "Icon pack" section listing installed packs (apps answering `org.adw.launcher.THEMES` or `com.novalauncher.THEME`; both are in `<queries>`), plus "Default icons".
- `IconPack.load` reads `res/xml/appfilter.xml` (or `assets/appfilter.xml`) through `createPackageContext` and maps component → drawable name; drawables are looked up by name (`drawable`, then `mipmap`). Apps the pack doesn't cover keep their own icon.
- `AppRepository.setIconPack` swaps the pack and clears the icon cache. `iconVersion` (Compose state) goes up on every cache clear and is a key in `AppIcon`, so icons on screen reload.
- The pack is loaded before the first settings value is published (`LauncherViewModel.settings`), so startup never shows the default icons first.
- If the chosen pack is updated it is re-read; if it is uninstalled the launcher falls back to default icons.
- Not done: `iconback`/`iconmask`/`iconupon`/`scale` for unthemed apps, calendar/clock dynamic icons, per-app icon choice.
- To test without a real pack: build a tiny app with an activity filtering `org.adw.launcher.THEMES`, a `res/xml/appfilter.xml` and a couple of drawables.

## What step 6 does (keep this working)

- Settings → Gestures → "Home screen gestures", **off by default**. When on: double-tap on empty home space locks the phone (Android 9+), swipe down on home opens notifications. Swipe up still opens the drawer; icons keep their own taps.
- Both actions go through `GestureService` (`performGlobalAction`). It requests no events and `canRetrieveWindowContent="false"`. Never use `DevicePolicyManager.lockNow()` (it disables fingerprint/face for the next unlock).
- Google Play policy: turning the switch on without accessibility access first shows the in-app disclosure dialog; only "Continue" opens the system accessibility settings. Keep that order, and keep the disclosure text accurate if the service ever does more.
- If the setting is on but the service is off, settings shows a red hint and the gestures show a toast.
- To test: `adb shell settings put secure enabled_accessibility_services dk.emilbolet.quectolaunch/dk.emilbolet.quectolaunch.GestureService` enables the service. For a double-tap from adb: `adb shell 'input tap 540 1100 & sleep 0.15; input tap 540 1100; wait'` (two separate `input tap` calls are too slow).

## Home pages (keep this working)

- The home grid is a `HorizontalPager`; swipe sideways between pages. Dots above the chevron show the page (hidden with one page). The dock is the same on every page; the clock and search bar are widgets and sit on one page.
- While an icon is dragged, one spare empty page exists at the end; holding the icon at the left or right screen edge for 600 ms turns the page. Dropping on the spare page creates it. Pages can also be added in the "Home screens" overview (next section).
- `HomeItem.page` is the page (0 for dock and folder contents); `HomeLayout.pages` is the list the UI draws; `DropTarget.page` comes from `DragState.gridPage` (the pager's current page).
- The number of pages is saved (`home_meta.pages`), so a page stays when it becomes empty. Pages are only removed by the user, in the overview. (Before the overview existed, empty pages were closed up automatically at startup and on a Home press; that is gone.)
- The page count the UI uses is the larger of the saved count and the highest page in use + 1 (`buildLayout`, `HomeDao.pageCount`). `HomeDao.growPages()` saves the new count after anything lands on the spare page.
- Home press (`onNewIntent`) goes to the first page; returning from an app (`onStop`) keeps the current page.

## Home screens overview (keep this working)

- Long-press empty home space → "Home screens" (`PageManager`). The owner calls pages "home screens"; the UI text does too, the code says "page".
- Every page is a small preview (icons and folders in their cells, widgets as grey blocks), two per row, with its number underneath. The current page has an accent border. Tap a preview to go to that page.
- Add: the + tile at the end adds an empty page (`HomeDao.addPage`).
- Remove: the ✕ under a preview. An empty page goes at once; a page with content asks first, then its apps, folders (with contents) and widgets are deleted and the widget IDs given back (`HomeDao.removePage` returns them). Later pages move one step left.
- **There is always at least one page:** with one page left there is no ✕, and `HomeDao.removePage` refuses as well.
- Reorder: hold a preview and drag it; the others make room, and the drop calls `HomeDao.reorderPage(from, to)`.
- Pages have no ID. A preview's Compose key is the smallest item ID on the page (empty pages: a negative counter), so it stays put when the saved order arrives. Between the drop and that moment, `PendingMove` keeps showing the new order.
- Not done: auto-scroll while dragging a preview (matters only with more pages than fit on screen), reordering for screen-reader users, a limit on the number of pages.

## Widgets (keep this working)

- Add: long-press empty home space → Widgets → tap one. `MainActivity.addWidget` then runs up to three steps: bind (`bindAppWidgetIdIfAllowed`; the first time Android shows its own "Create widget and allow access?" dialog), the widget's setup screen if it needs one, and `LauncherViewModel.placePendingWidget`, which puts it in the first free spot from the current page on (a new page if needed) and scrolls there.
- A widget is a `HomeItem` with `appWidgetId`, `widgetProvider`, `spanX`, `spanY`; `position` is its top-left cell. `WidgetEntry` is what the UI gets. Widgets live on home pages only, never in the dock or folders.
- Drawing: each page is the grid of `DropCell`s with `WidgetLayer` on top, which places an `AndroidView` (the `AppWidgetHostView`) over the cells the widget covers.
- Long-press: `LauncherWidgetHostView` detects it in `onInterceptTouchEvent` (so it also works on the widget's buttons) and calls `dragState.longPress`, the same entry point as icons. Menu: Resize, Remove. Keep holding and move to drag; the cells it would cover light up, and the drop is ignored unless they are all free.
- Resize: handles on the right and bottom edge, one cell per step, only into free cells. Tap elsewhere or Back to finish. The handles use `systemGestureExclusion` because a full-width widget's handle sits in Android's Back-gesture zone.
- Nothing can be dropped onto a widget (`HomeDao.drop` checks). Swiping pages over a widget works because Compose's `AndroidView` only hands moves to the view after the pager had its chance.
- The host listens between `onStart` and `onStop`. Widget rows are removed (and the ID given back) when the widget is removed, its app is uninstalled, or its info is gone at startup.
- Widgets outside a smaller grid are hidden like apps.

## App list layout (keep this working)

- Settings → "App list layout": Grid (default) or List (`LauncherSettings.drawerLayout`).
- Grid: each letter keeps its sticky header, with its apps in rows of icons with labels underneath. The number of columns is the home screen's columns setting; there is no separate one. A letter's last row is left-aligned.
- Both layouts are the same `LazyColumn` in `AlphabeticalList`; in the grid a list item is one row of apps (`AppGridRow`), so the A–Z scroller's `headerIndex` counts rows, not apps.
- Grid cells use the same long-press entry point as list rows (`DragSource.FromDrawer`, menu key `drawer:<app key>`).
- Search results are always a list, in both layouts.

## Personal and work apps (keep this working)

- When any app has `AppInfo.isWorkProfile`, the app list shows a Personal / Work switch under the search field and lists only the chosen half. Without such apps there is no switch and the list is unchanged.
- The A–Z strip only shows letters the chosen half has. Each half starts at the top when switched to.
- Search looks through both halves whichever is chosen, and the switch is hidden while searching. Work apps are told apart by the briefcase badge on their icon.
- The choice (`LauncherViewModel.drawerShowsWork`) lasts while the launcher is running; it starts on Personal and is not saved.
- `isWorkProfile` is true for every profile other than the phone's own user, so apps from a Private Space or a cloned-apps profile would also land under "Work".
- To test: `adb shell pm create-user --profileOf 0 --managed TestWork`, then `adb shell am start-user 10`; `adb shell pm remove-user 10` afterwards.

## Menu icons (keep this working)

- Every item in a long-press menu has an icon in front of its label: the app menu, the folder menu, the widget menu and the empty-space menu. A new menu item should get one too.
- Fixed actions use `MenuIcon` with an outlined Material icon from `material-icons-core` (Close for Remove, Info, Delete for Uninstall, Home, Settings). Icons the core set lacks are vector drawables (`ic_widgets`, `ic_resize`); the extended icon library is deliberately not a dependency.
- App shortcuts show their own icon in its own colors (`AppShortcut.icon`, loaded with `getShortcutIconDrawable` together with the shortcut list, off the main thread). A shortcut without an icon gets an empty gap so the labels stay in line.

## Clock and search bar as widgets (keep this working)

- The clock and the search bar are "built-in widgets": `HomeItem.builtIn` is `clock` or `search` (`BuiltInWidget`), and the UI gets a `WidgetEntry` with `builtIn` set and no `appWidgetId`/`info`. `HomeItem.isWidget` is true for both kinds, so every widget rule (spans, nothing dropped on them, not in the dock or folders, move, resize, hidden outside a smaller grid) applies unchanged.
- They are plain composables (`BuiltInWidgetContent`), drawn by `WidgetLayer`/`WidgetCell` in place of the `AndroidView`. No widget host, no Android widget ID. Long-press goes through `dragState.longPress` like everything else; the menu is Resize and Remove. A tap on the search bar opens the drawer with the keyboard up; a tap on the clock does nothing.
- The clock's text follows the widget's size (the date is dropped when it is very low); the search bar is at most 48 dp high.
- Add again: the Widgets list starts with a "QuectoLaunch" group (Clock, Search bar). A new one is a full row wide and one row high and goes to the first free row from the current page on. More than one of each is allowed.
- One-time step, also how a new installation gets them. Before, both were drawn outside the grid on every page. Now:
  - `SettingsStore.addRowsForBuiltIns` adds 2 to the saved rows (flag `rows_for_built_ins`), before the first settings value is published. The default is now 7 rows.
  - `HomeDao.placeBuiltIns` (guarded by `home_meta.layoutVersion`) moves every home item one row down, puts the clock on row 0 and the search bar on the last row of page 0, both full width. It runs before the first layout is published (`LauncherViewModel.layout`).
  - Each half is safe to repeat and they don't depend on each other's success, because one is in DataStore and the other in Room.
- `HomeLayout.isEmpty` ignores built-in widgets, so the "swipe up for your apps" hint still shows on a new installation.
- Known differences from before: icons sit a little higher than they did (the clock's row is lower than its old area, the search bar's row is higher); the chevron is below the search bar instead of above it; pages other than the first have two empty rows where the clock and search bar used to show.

## Ideas and leftovers

Nothing here is started; ask the owner before picking one.

- Reordering apps inside a folder.
- Suggested to the owner, not agreed: notification dots (needs notification access, with a disclosure like the gestures), hidden apps, a recent/frequent apps row in the drawer, contacts/calculator/settings results in search, renaming or re-iconing a single app, icon size and label on/off, backup and restore of layout and settings, more gestures with choosable actions.
- A hint when a widget drop or resize is refused because the cells are taken (today it is silently ignored).
- Clock widget: opening the clock app on tap, a choice of styles. Search widget: a narrow icon-only form.
- Widgets: left/top resize handles, respecting a widget's own minimum and maximum size, dragging a widget straight out of the picker, widgets in the dock.
- Icon packs: `iconback`/`iconmask`/`iconupon`/`scale` for unthemed apps, dynamic calendar/clock icons.
- Not tested systematically on a real phone, in Danish, with a work profile, or with a real Play Store icon pack.
- Work profile: swiping sideways between Personal and Work, a "pause work apps" button, remembering the choice across restarts, telling a Private Space apart from a work profile.
- App list: a continuous grid without letter headings (Microsoft Launcher's "vertical grid"), its own column count, search results as a grid.

### GitHub releases

- `.github/workflows/build.yml`: every push to `main` and every pull request runs `assembleDebug lint`. A pushed tag `v<versionName>` also builds the release APK, signs it and publishes it as a GitHub release named `QuectoLaunch-<versionName>.apk`, with `fastlane/.../en-US/changelogs/<versionCode>.txt` as the notes. The tag must equal `v` + `versionName` or the job fails.
- The APK is signed in the workflow with `apksigner`, not by Gradle, so `app/build.gradle.kts` has no signing settings and F-Droid builds the same unsigned APK. Keep it that way.
- The key comes from four repository secrets (`SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`), set on 2026-10-13. The key itself is on the owner's machine, outside the repo; its certificate SHA-256 is `4cbe52cb…d973e65f` (CN=Emil Bolet). Never put a signing key or its password in the repo, and never replace the key: updates only install over a release signed with the same one.
- CI uses Temurin JDK 25, the same major version as the Android Studio JDK the project has always been built with (the owner preferred that over trying 21). Release 0.1 (tag `v0.1`, commit `5ff086e`) was published this way on 2026-10-13. GitHub warns that `checkout@v4`, `setup-java@v4` and `setup-gradle@v4` are due for their next major versions.
- A GitHub-signed APK and an F-Droid-signed one can't update each other: a user has to stay with one source.

### Getting it into F-Droid

The owner wants to submit the app to F-Droid. Done in the repo on 2026-10-13:

- `dependenciesInfo` is off in `app/build.gradle.kts` (F-Droid rejects the encrypted dependency list AGP adds by default). Keep it off.
- No hard-coded web service: "search the web" uses `ACTION_WEB_SEARCH` and shows a toast if no app handles it (it used to fall back to a google.com URL). In-app text doesn't mention the Play Store. Keep both that way, and keep the app free of the internet permission.
- Store listing in `fastlane/metadata/android/` (`en-US` and `da`): title, short description (under 80 characters, no full stop), full description, `changelogs/<versionCode>.txt`, icon and five screenshots. The screenshots are from the emulator in English. The descriptions deliberately don't name Microsoft Launcher. Every release needs a new changelog file, and a feature that changes what the listing says needs the descriptions updated.
- `fdroid/dk.emilbolet.quectolaunch.yml` is a draft of the build recipe for the `fdroiddata` merge request. It has not been through `fdroid lint` or `fdroid build`.
- `./gradlew assembleRelease` works (unsigned APK) and the result runs on the emulator; lint has no errors.

Still open, and the owner's to do: open the merge request on GitLab (the GitHub repo is public since 2026-10-13). `main` is pushed and `v0.1` is tagged; the recipe has that commit's hash. Not checked: whether F-Droid's build server supports this toolchain (AGP 9.4.1, Gradle 9.8.1, compileSdk 37); only the Android Studio JDK 25 has ever built it. `ic_launcher_foreground.xml` still calls itself a placeholder icon.

### Out of scope
- The Microsoft Launcher "Feed" page (news, calendar, to-do cards) has been deliberately dropped.
- Custom Recents screen (not possible for third-party launchers).
- Daily wallpaper (maybe later).

## Commands

```
./gradlew assembleDebug        # build
./gradlew assembleRelease      # unsigned release APK, what F-Droid builds
./gradlew installDebug         # install on connected device
./gradlew lint                 # lint
adb shell am start -n dk.emilbolet.quectolaunch/.MainActivity
```

The application ID, namespace and code package are `dk.emilbolet.quectolaunch`. Changing the ID makes Android treat it as a new app (saved layout and settings are not carried over).
