# HaptiKit — Project 1 / 6 (warm-up)

Custom vibration patterns per contact & per app. Native Android, Kotlin, Jetpack Compose, Room.

## What's included here (the actual code)
```
HaptiKit/
├── build.gradle.kts                  (project-level)
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts              (module-level, all deps declared)
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/haptikit/app/
        │   ├── MainActivity.kt
        │   ├── data/
        │   │   ├── PatternEntity.kt
        │   │   ├── AssignmentEntity.kt
        │   │   ├── HaptiDao.kt
        │   │   ├── AppDatabase.kt
        │   │   └── HaptiRepository.kt
        │   ├── vibration/
        │   │   └── VibrationEngine.kt
        │   ├── service/
        │   │   └── HaptiNotificationListenerService.kt
        │   └── ui/
        │       ├── PatternListScreen.kt
        │       ├── PatternEditorScreen.kt
        │       └── AssignmentScreen.kt
        └── res/values/
            ├── strings.xml
            └── themes.xml
```

## What YOU need to add locally (generic/generated stuff — don't need it from me)
1. Open the `HaptiKit/` folder in **Android Studio** (Koala+ recommended). It will:
   - generate the `gradle/wrapper/` folder + `gradlew` / `gradlew.bat` automatically
   - create `local.properties` pointing at your Android SDK
   - download every dependency listed in `app/build.gradle.kts` (Compose, Room, Gson, Navigation) into your local Gradle cache — this is the Android equivalent of `node_modules`, you never hand-manage it
2. Add launcher icons: right-click `res` → New → Image Asset → generates the `mipmap-*/ic_launcher.png` set. I left those out since they're generated, not written.
3. First run will trigger a KSP annotation-processing pass for Room — that's expected, just let Gradle sync finish before hitting Run.

## How the pieces fit together
- **Room DB** (`data/`) stores two tables: `patterns` (name + timing/amplitude arrays) and `assignments` (contact lookupKey or app package → pattern id).
- **VibrationEngine** wraps `VibrationEffect.createWaveform()` so nothing else touches the raw vibration API.
- **PatternEditorScreen** — tap-to-record: each tap you make becomes a "beat" segment, gaps between taps become silence segments. Preview plays it back live.
- **AssignmentScreen** — two tabs, Contacts (needs `READ_CONTACTS` permission, granted in-app) and Apps (needs no permission, just queries launchable packages).
- **HaptiNotificationListenerService** — this is the part that makes assignments actually fire. Android doesn't let a third-party app override *another* app's vibration directly; the only mechanism available without root is: listen for the notification via `NotificationListenerService`, look up whether the source (sender or app) has a custom pattern, and re-trigger vibration ourselves. **The user has to manually grant "Notification access"** in system settings — there's a button in the Assignments screen that deep-links there, since Android doesn't allow requesting it as a normal runtime permission.

## Known v0.1 limitations (matches the roadmap's scope)
- Export/import (`HaptiRepository.exportJson/importJson`) is wired at the repository level but I didn't build the file-picker UI (Storage Access Framework) yet — that's a ~30 min add whenever you want it, just say the word.
- Contact-level detection only works for apps that attach `EXTRA_PEOPLE_LIST` to their notifications (most messaging apps do; not universal).
- No app icon / branding yet — cosmetic, do whenever.

## Next when you're ready
Tell me when this one's running and I'll move on to **NightDeck** (Flutter, ambient bedside lock screen).
