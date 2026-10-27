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
| `sync` | `SyncEngine`: exchanges the phone's data with the server (below) |
| `core/ui` | Theme (paper-and-ink colours, serif type) and shared components |
| `auth` | Dev sign-in |
| `catalog` | Word DTOs, catalog API, and `WordCache` (words stored on the phone) |
| `feed` | **Today**: one word per screen, swipe up for the next, from `FeedQueue` (words downloaded ahead) |
| `word` | The full word screen and the views shared with the feed |
| `progress` | Saved / liked / known / seen flags: `ProgressRepository` on the phone, sync DTOs |
| `saved` | **Words**: your saved, liked, known and seen lists |
| `groups` | **Groups**: create/join with invite codes, shared word list, group word of the day, leaderboard, members |
| `search` | Prefix search over the catalog |
| `practice` | **Practice**: spaced-repetition quiz run on the phone (`PracticeRepository`, `srs/Scheduler.kt`) |
| `settings` | **Settings**: level range, words per day, topics, reminder times; account; credits |
| `user` | `/me` and preferences API |

`AppContainer` wires everything together (no DI framework); each screen gets a `ViewModel` from it.
The local database schema is in `shared/src/commonMain/sqldelight/app/vohcarb/db/Vohcarb.sq` (SQLDelight).

## Offline first

Screens read and write the phone's database (SQLDelight); `SyncEngine` keeps it in step with the server.

- **Flags** (seen, liked, saved, known) are written locally and marked *dirty*. A sync sends them to
  `POST /me/sync` with the last `revision`, and applies what other devices changed. The server keeps the
  later change; a word edited again while a sync is in flight stays dirty and goes next time.
- **Practice** builds sessions from the local deck (saved and not known, like the server), grades with a
  copy of the backend's `Scheduler.kt` (keep the two identical), and queues answers with their time. After
  upload, the server's card replaces the local one. The server only sends *due* cards, so after a complete
  session fetch, saved words it didn't list are marked as "card elsewhere" rather than treated as new.
- **Feed**: about 30 new words and their details are downloaded ahead; quiz questions are cached per word.
- **When it syncs**: in full on app start/resume, when the network comes back, and from Settings → Sync now;
  just the changes a few seconds after you save, like, practise or swipe.
- Signing out deletes the user's data from the phone.

## Status

| Phase | What |
|---|---|
| 1 | Project setup, dev sign-in, feed, word screen, saved lists, search, practice (online), settings, credits |
| 2 | Offline first: SQLDelight database, sync engine, offline feed and practice, sync status in Settings |
| 3 | Groups: create, join by code, share invites, shared words with notes, "Add to group" on any word, group word of the day, weekly/monthly leaderboard, owner tools (new code, remove member, delete) |

Next, roughly in order:

1. **Home-screen widget** (Jetpack Glance) with the word of the day.
2. **Push reminders** (FCM) once the Firebase project exists, and **Google Sign-In**. Background sync
   (WorkManager) fits here too; today the app syncs while it's open.
3. Themes and polish; iOS on a Mac.

Known limits: groups and user preferences need a connection (groups are shared, live data, so they're
fetched fresh rather than synced); the History list shows words whose details haven't been fetched yet by
their slug; on iOS, "Share invite" will copy the text until a share sheet is added.

## Credits

Word data: [Open English WordNet](https://github.com/globalwordnet/english-wordnet) (CC BY 4.0) and
[FrequencyWords](https://github.com/hermitdave/FrequencyWords) (CC BY-SA 4.0), shown in the app's Credits screen.

## License

[Apache 2.0](LICENSE).
