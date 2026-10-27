# Vohcarb app

The mobile app for **Vohcarb**, an open-source vocabulary app for learning words daily.
Kotlin Multiplatform + Compose Multiplatform. **Android first**; iOS later from the same code.

The API lives in [vohcarb-backend](https://github.com/Tomiloba21/vohcarb-backend).

## Run it

Requirements: JDK 21, Android SDK (Android Studio installs it), an emulator or a phone with USB debugging.

1. Start the backend on your PC (`./gradlew bootRun` in `vohcarb-backend`, with the word catalog loaded,
   see its README). It listens on `http://localhost:8080` with dev login on.
2. Build and install the app:

   ```bash
   ./gradlew :androidApp:installDebug
   ```

   Or open this folder in Android Studio and run the `androidApp` configuration.
3. Sign in with any email. The server address defaults to `http://10.0.2.2:8080`, which is the PC's
   `localhost` as seen from the emulator. On a real phone use your PC's LAN address, e.g.
   `http://192.168.1.20:8080` (same Wi-Fi, and allow port 8080 through the Windows firewall).

Debug builds allow plain HTTP; release builds don't.

```bash
./gradlew :shared:testAndroidHostTest   # unit tests (shared code, run on the JVM)
./gradlew :androidApp:assembleDebug     # APK in androidApp/build/outputs/apk/debug/
```

## Layout

| Module | What |
|---|---|
| `shared/` | Almost everything: API client, view models and Compose UI, in `commonMain` |
| `androidApp/` | The Android shell: `Application`, `MainActivity`, manifest, icon |

Code in `shared/src/commonMain/kotlin/app/vohcarb/` is organised **by feature**, like the backend:

| Package | What |
|---|---|
| `core/api` | `ApiClient` (Ktor): server address, bearer token, problem-details errors, sign-out on 401 |
| `core/session` | `SessionStore`: token and server address, kept in `Settings` (SharedPreferences on Android) |
| `core/ui` | Theme (paper-and-ink colours, serif type) and shared components |
| `auth` | Dev sign-in |
| `catalog` | Word DTOs and catalog API (words, search, categories, word of the day) |
| `feed` | **Today**: one word per screen, swipe up for the next; words are marked seen as they're shown |
| `word` | The full word screen and the views shared with the feed |
| `progress` | Saved / liked / known / seen flags |
| `saved` | **Words**: your saved, liked, known and seen lists |
| `search` | Prefix search over the catalog |
| `practice` | **Practice**: spaced-repetition quiz; answers are uploaded with the time they happened |
| `settings` | **Settings**: level range, words per day, topics, reminder times; account; credits |
| `user` | `/me` and preferences API |

`AppContainer` wires everything together (no DI framework); each screen gets a `ViewModel` from it.

## Status

| Phase | What |
|---|---|
| 1 | Project setup, dev sign-in, feed, word screen, saved lists, search, practice (online), settings, credits |

Next, roughly in order:

1. **Offline first**: a local database (SQLDelight or Room KMP) for words and flags, synced with
   `POST /api/v1/me/sync`; practice offline with a copy of the backend's `Scheduler.kt`.
2. **Groups**: create/join with invite codes, shared word lists, group word of the day, leaderboard.
3. **Home-screen widget** (Jetpack Glance) with the word of the day.
4. **Push reminders** (FCM) once the Firebase project exists, and **Google Sign-In**.
5. Themes and polish; iOS on a Mac.

## Credits

Word data: [Open English WordNet](https://github.com/globalwordnet/english-wordnet) (CC BY 4.0) and
[FrequencyWords](https://github.com/hermitdave/FrequencyWords) (CC BY-SA 4.0), shown in the app's Credits screen.

## License

[Apache 2.0](LICENSE).
