# Maestro — From-Scratch Playbook & Org Demo Guide

> A complete, reproducible walkthrough for creating and running Maestro UI tests
> for **any** app, plus a ready-to-present demo script. Everything here was
> verified on this machine against the `Medium_Phone_API_35` emulator.
>
> Companion docs: [`MAESTRO-ANALYSIS.md`](./MAESTRO-ANALYSIS.md) (why/where it fits
> vs our Appium stack).

---

## 0. The mental model (say this first in the demo)

Maestro testing is always the **same three moving parts**, no matter the app:

```
  ┌─────────────┐      ┌──────────────────┐      ┌──────────────────────┐
  │ 1. A DEVICE │  →   │ 2. THE APP on it │  →   │ 3. A YAML FLOW that  │
  │ (emulator or│      │ (installed via    │      │ Maestro runs against │
  │  real phone)│      │  adb/Play/Cloud)  │      │ it: tap/type/assert  │
  └─────────────┘      └──────────────────┘      └──────────────────────┘
```

- Maestro **does not** need a physical device (an emulator works) and **does not**
  install apps itself — you install, it drives. (See analysis doc.)
- A "test" is a **Flow**: a `.yaml` file of human-readable steps. No compilation.

---

## 1. One-time setup (per machine)

| Step | Command / Action | Verify |
|---|---|---|
| Java 17+ | already installed | `java -version` → 21 ✅ |
| Android SDK + `adb` + an emulator image | Android Studio (already present) | `adb --version` ✅ |
| Maestro CLI | Unzip `maestro.zip`, add `...\maestro\bin` to `PATH` | `maestro -v` |

**Add Maestro to PATH permanently (Windows):**
```powershell
setx PATH "$env:PATH;C:\Users\kesha\Downloads\maestro\maestro\bin"
# restart the terminal, then:
maestro --help
```

---

## 2. Get a device (no phone required)

Either open an emulator from Android Studio, **or** let Maestro start one:

```powershell
maestro start-device --platform=android      # creates/starts an AVD
adb devices                                   # confirm: emulator-5554  device
```

> `web` platform is fully device-free: `maestro start-device --platform=web`.

---

## 3. Put the app on the device

Maestro launches by **`appId`** (Android package / iOS bundle id). Get the app on
the device using whichever fits:

```powershell
# a) You have the APK:
adb install path\to\app.apk

# b) It's a pre-installed / system app (Settings, Chrome) — nothing to do.

# c) Reuse this repo's installer for the org app, then hand off to Maestro:
mvn -Pmobile-myntra test     # installs com.myntra.android via Play Store

# d) Maestro Cloud installs the uploaded binary automatically (see §9).
```

Find an app's `appId`:
```powershell
adb shell pm list packages | Select-String "settings"   # com.android.settings
```

---

## 4. Write your first Flow (anatomy)

A Flow is YAML with a small header (`appId` + `---`) then a list of steps:

```yaml
appId: com.android.settings      # which app to drive
---
- launchApp:                     # step 1: open the app
    stopApp: true
- tapOn:                         # step 2: interact
    text: "Search settings.*"    #   selectors match by text (regex ok)
    optional: true               #   don't fail if it's absent
- inputText: "Bluetooth"         # step 3: type
- assertVisible:                 # step 4: verify (this is the actual test)
    text: "Bluetooth.*"
- takeScreenshot: result         # step 5: evidence for the report
```

**This exact flow is verified and passing** in this repo:
`src/test/resources/maestro/flows/demo_settings_search.yaml`.

### How elements are matched (selectors)
Maestro finds elements through the OS accessibility tree, in this priority:
1. **Visible text** — `tapOn: "Login"` (most common, most readable)
2. **Accessibility id / resource-id** — `tapOn: { id: "com.app:id/login_btn" }`
3. **Other traits** — `index`, `enabled`, `checked`, relative position, etc.

Regex is allowed (`"Search.*"`), and `optional: true` makes a step non-fatal —
useful for first-run dialogs.

### Core command vocabulary (demo cheat-sheet)
| Category | Commands |
|---|---|
| App lifecycle | `launchApp`, `stopApp`, `killApp`, `clearState` (not on system apps) |
| Interaction | `tapOn`, `doubleTapOn`, `longPressOn`, `inputText`, `eraseText`, `pressKey` |
| Movement | `scroll`, `scrollUntilVisible`, `swipe`, `back` |
| Assertions | `assertVisible`, `assertNotVisible`, `assertTrue` |
| Waiting | *(usually none — auto-waits);* `extendedWaitUntil` when needed |
| Evidence/flow | `takeScreenshot`, `startRecording`, `runFlow` (sub-flows), `repeat` |
| Logic | `runScript` (sandboxed JS), `onFlowStart` / `onFlowComplete` hooks |

---

## 5. Author flows

- Flows are plain `.yaml` files — write them by hand in any editor and iterate with
  `maestro test` (edit-and-rerun is instant, no compile step).
- Match elements by visible **text** first, then **id / accessibility id /
  content-description / testTag**. Inspect the running app to confirm selectors.

---

## 6. Run the tests

```powershell
# Single flow
maestro test src\test\resources\maestro\flows\demo_settings_search.yaml

# A whole folder (uses config.yaml)
maestro test src\test\resources\maestro\flows

# Only tagged flows
maestro test flows --include-tags=smoke

# Pass environment variables into a flow (${VAR})
maestro test flows\login.yaml -e USERNAME=demo -e PASSWORD=secret

# Continuous mode: re-runs on file save (tight authoring loop)
maestro test -c flows\demo_settings_search.yaml
```

---

## 7. Reports & debugging (show this in the demo)

- **Every run** writes screenshots + logs + a UI hierarchy dump to:
  `C:\Users\<you>\.maestro\tests\<timestamp>\` — open it to show failure evidence.
- **Machine-readable report** for CI dashboards (verified working):
  ```powershell
  maestro test flows --format JUNIT --output target\maestro\report.xml
  maestro test flows --format HTML  --output target\maestro\report.html
  ```
  The JUnit XML slots into the same CI test reporting our Surefire output uses.

---

## 8. Fit it into THIS repo (optional Maven profile)

So the team runs Maestro with one familiar command, mirroring `-Pmobile-myntra`.
Add an `exec-maven-plugin` execution guarded by a `maestro` profile:

```xml
<profile>
  <id>maestro</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.2.0</version>
        <executions>
          <execution>
            <id>run-maestro-flows</id>
            <phase>integration-test</phase>
            <goals><goal>exec</goal></goals>
            <configuration>
              <executable>maestro</executable>
              <arguments>
                <argument>test</argument>
                <argument>src/test/resources/maestro/flows</argument>
                <argument>--format</argument><argument>JUNIT</argument>
                <argument>--output</argument>
                <argument>${project.build.directory}/maestro/report.xml</argument>
              </arguments>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</profile>
```
Then: `mvn -Pmaestro verify` (device/emulator must be running first).

---

## 9. Scale in CI / cloud (the org pitch)

- **Maestro Cloud** installs your uploaded binary and runs flows in parallel on
  hosted devices — no CI-hosted emulators needed:
  ```bash
  maestro cloud --apiKey $KEY app.apk src/test/resources/maestro/flows
  ```
- **GitHub Actions**: official Maestro action runs flows on push/PR and exposes a
  console URL output. Local emulator alternative: boot an AVD in the runner, then
  `maestro test`.

---

## 10. Ready-to-present demo script (~10 minutes)

> Goal: prove "install nothing bespoke, write English-like YAML, watch it drive a
> real app, get a report." Uses only a stock emulator + a system app — zero org
> dependencies, so it always works.

1. **Frame it (30s).** "Appium needs Java code, a server, drivers. Maestro needs a
   YAML file. Watch." Show the 3-part mental model (§0).
2. **Start a device (1m).** `maestro start-device --platform=android` (or show the
   already-running emulator). `adb devices`.
3. **Show the flow (1m).** Open `flows/demo_settings_search.yaml` — read it aloud;
   it's self-explanatory. Contrast with `MyntraAppTest.java` + its page objects.
4. **Run it live (2m).**
   ```powershell
   maestro test src\test\resources\maestro\flows\demo_settings_search.yaml
   ```
   Point at each step turning `COMPLETED`.
5. **Author a flow (2m).** Open a flow in an editor, add a step, rerun with
   `maestro test`. Emphasise the instant edit-and-rerun loop (no compile).
6. **Break it on purpose (1.5m).** Change the assertion to a wrong string, rerun,
   then open `~/.maestro/tests/<timestamp>/` to show the screenshot + hierarchy
   Maestro captured automatically. This sells the debugging story.
7. **Show the report (1m).**
   ```powershell
   maestro test src\test\resources\maestro\flows --format HTML --output report.html
   ```
   Open `report.html`.
8. **Land the CI message (1m).** Mention Maestro Cloud / GitHub Action for parallel
   runs, and the `-Pmaestro` Maven profile so it lives beside our existing suites.

**Backup / real-app segment:** once the org APK is installed
(`adb install app.apk`), swap in `flows/myntra_search.yaml` to show it working on
an actual product app.

---

## 11. Applying it to an org application (checklist)

1. Get the app's `appId`: `adb shell pm list packages | findstr <name>`.
2. Install it: `adb install app.apk` (or your CI build output).
3. Author the flow YAML by clicking through the target journey and recording each
   step (launch, taps, text) as a command; save under `flows/`.
4. Add `assertVisible` checks at each checkpoint — those are your test oracles.
5. Extract reusable pieces (login, dismiss-cookie) into sub-flows via `runFlow`.
6. Tag flows (`tags: [smoke]`) and wire `--include-tags` into CI.
7. Publish JUnit/HTML reports; optionally push to Maestro Cloud for parallelism.

---

### Verified artifacts in this repo
| File | Purpose | Status |
|---|---|---|
| `src/test/resources/maestro/config.yaml` | workspace config | ✅ |
| `.../flows/smoke_launch.yaml` | launch-only proof (Chrome) | ✅ passed |
| `.../flows/demo_settings_search.yaml` | interactive demo (tap/type/assert) | ✅ passed |
| `.../flows/myntra_search.yaml` | real org-app template (needs app installed) | template |
