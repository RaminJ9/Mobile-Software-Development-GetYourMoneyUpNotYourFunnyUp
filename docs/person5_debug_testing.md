# Temporary Person 5 UI sandbox

The sandbox exists only in `app/src/debug/`. Release builds do not contain its
Activity, manifest entry or fixtures. The normal launcher and production
navigation are unchanged. No database, authentication or repository is used by
the sandbox. Existing application-level Firebase initialization is unchanged;
the sandbox actions do not call Firebase or perform settlements.

## Launch from Android Studio

1. Select the app's **debug** build variant in **Build Variants**.
2. Open **Run > Edit Configurations**, add an **Android App** configuration, and
   name it **Person 5 UI sandbox**.
3. Set **Module** to `app`. Under **Launch Options**, select **Specified Activity**.
4. Select `com.example.getyourmoneyupnotyourfunnyup.debug.Person5TestActivity`.
5. Select the emulator and run that configuration. No login is needed.

The Activity has no launcher icon/filter. Running the ordinary app configuration
continues to open MainActivity. To return to normal testing, select the ordinary
app configuration again.

Alternatively, after installing the debug APK:

```powershell
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" shell am start -n com.example.getyourmoneyupnotyourfunnyup/.debug.Person5TestActivity
```

## Controls and checks

The persistent amber header labels everything as sample/simulated. **Home** and
**Activity** switch screens without losing session state. **Reset** opens a fresh
sandbox Activity and discards its in-memory state. Rotation retains the session;
process death may reset it. Nothing is persisted.

- Home: switch Recent/Favourites/All, inspect group balances and pending subtitle.
  Choose each chart period, tap slices, dismiss the callout and tap Open group.
  Navigation outside these two screens displays a sample-destination toast.
- Tap Pay now, confirm/cancel an individual payment and Pay all. With **Success**,
  observe Paying, completion and the five-second all-clear collapse. Reset to
  repeat. Chart history is a fixed fixture; simulations do not update a ledger.
- Activity: check Today/Yesterday/Earlier, All/Needs you/Expenses/Payments, unread
  dots and Mark all as read. Confirm/cancel the reminder payment. Tap Try now.
- Choose **Simulate: Failure** before confirming/retrying to test error feedback;
  then choose Success and retry. Choose **Pending** to test queued feedback
  without claiming a successful sync. Reset between completed/queued scenarios.
- **Show offline** toggles Home's supplied offline banner. It does not inspect
  connectivity, start a worker or queue anything.
- Check scrolling, chart popover placement, system-bar insets and large font
  settings on the emulator.

Home and Activity use the production routes and ViewModels, with debug-only
in-memory flows and injected simulated operations. Each operation waits 800ms
and returns the selected status or throws a labelled simulated error. The
screens' action state is independent: paying a Home fixture does not settle its
Activity reminder. There is no shared ledger, background sync, notification or
Person 4 algorithm in this temporary setup.

## Removal

Remove only `app/src/debug/AndroidManifest.xml` and the two Kotlin files in
`app/src/debug/java/com/example/getyourmoneyupnotyourfunnyup/debug/`, then delete
the local sandbox run configuration if desired. No production change must be
reverted. Phase 4 remains deferred.

## Verification

The user previously verified the main project's build, emulator launch, previews
and `testDebugUnitTest` in Android Studio. Those results predate this entry point.
For this change, `assembleDebug testDebugUnitTest` was attempted again here; the
wrapper could not download Gradle 9.5.0 because this session denies network
sockets. Consequently no new Gradle build or emulator success is claimed here.

The supplementary cached Kotlin 2.2.10/Compose source check includes both new
debug Kotlin files and passed; the existing 28 dashboard/feed unit tests passed.
It reports a cached-library deprecation warning for the existing instrumentation
test's `createComposeRule`. This is not a substitute for the configured Gradle
build, manifest merging, APK installation or emulator interaction testing.
`git diff --check` also passed.
