# TC2 Maestro Smoke Suite

Runs against a **real Android device connected over USB** (no emulator, no Studio).

- **App under test:** Total Connect 2.0 (`com.alarmnet.tc2`)
- **Device:** `51271XEKB9RDB9` (from `adb devices`)

## Two ways to run — pick one

**A) The framework way (recommended — one command, no Maestro flags to learn):**

```powershell
mvn test -Pmaestro "-Dtc2.username=<user>" "-Dtc2.password=<pass>"
```

Every `flows/*.yaml` becomes a TestNG test (`MaestroSmokeTest`), runs in the same
`mvn test` + Allure report as the Selenium/Appium suites. Add a new flow → it's
auto-discovered, no code change. **Quote each `-D`** so PowerShell doesn't split on
the dot. This is all a new joiner needs.

**B) The raw CLI way (for quick local authoring / debugging a single flow):**

```powershell
maestro test src\test\resources\maestro\flows\tc2_01_launch.yaml `
  -e TC2_USERNAME=<user> -e TC2_PASSWORD=<pass>
```

How A works under the hood: `MaestroRunner` (in `src/main/java/.../maestro/`) shells
out to the Maestro CLI via `cmd /c maestro test <flow> -e ...`, streams its output to
DEBUG logs, and asserts exit code 0. Credentials come from `-Dtc2.*` or the
`TC2_USERNAME`/`TC2_PASSWORD` env vars — never committed, and masked in logs.

---

## 1. Prerequisites (one-time)

1. On the phone: enable **Developer Options** (tap Build Number 7×) → turn on **USB debugging**.
2. Plug in via USB and **accept the "Allow USB debugging?"** prompt.
3. Confirm the laptop sees it:
   ```powershell
   adb devices
   # → 51271XEKB9RDB9   device
   ```
4. The app must already be installed (Maestro drives but does not install):
   ```powershell
   adb shell pm list packages | findstr tc2
   # → package:com.alarmnet.tc2
   ```

---

## 2. The exact command to launch the app on the connected device

Maestro launches by **`appId`** (the package name). The launch is declared inside
the flow (`launchApp`), and you trigger the flow with `maestro test`.

> `maestro` is on your user PATH, so in **any new terminal** just type `maestro …`
> (no full path). If a terminal was open before PATH was set, reopen it.

### One device connected (Maestro auto-targets it)

```powershell
maestro test src\test\resources\maestro\flows\tc2_01_launch.yaml
```

### Force a specific device (multiple devices/emulators attached)

`--device` is a **global** flag, so it goes **before** `test`:

```powershell
maestro --device 51271XEKB9RDB9 test src\test\resources\maestro\flows\tc2_01_launch.yaml
```

### Raw adb equivalent (what "launch" means under the hood — for reference only)

```powershell
adb -s 51271XEKB9RDB9 shell monkey -p com.alarmnet.tc2 -c android.intent.category.LAUNCHER 1
```

Maestro's `launchApp` does this plus screen inspection and synchronization — you do
**not** run the adb command yourself; it's here only to explain the mechanism.

---

## 3. Run the whole smoke suite

```powershell
# All smoke-tagged flows, with credentials + a JUnit report
maestro test src\test\resources\maestro\flows --include-tags smoke `
  -e TC2_USERNAME=<user> -e TC2_PASSWORD=<pass> `
  --format junit --output target\maestro\tc2-smoke.xml
```

Last verified run: **8/8 flows passed in ~4m 42s** on device `51271XEKB9RDB9`.

### The 8 smoke cases

| # | Flow | What it proves |
|---|------|----------------|
| 1 | `tc2_01_launch.yaml`     | App cold-starts to the Login Page (`SIGN IN` visible). |
| 2 | `tc2_02_login.yaml`      | Username + password typed, SIGN IN → login screen gone. |
| 3 | `tc2_03_devices.yaml`    | DEVICES tab → device-list screen (`automation_manage_device_menu`). |
| 4 | `tc2_04_cameras.yaml`    | CAMERAS tab → cameras screen (`add_camera_layout`, "Camera"). |
| 5 | `tc2_05_activity.yaml`   | ACTIVITY tab → events list (`events_layout`). |
| 6 | `tc2_06_partitions.yaml` | PARTITIONS tab → partitions + `ARM AWAY` controls. |
| 7 | `tc2_07_nav_menu.yaml`   | Nav drawer opens → `Settings` / `Sign Out` visible. |
| 8 | `tc2_08_signout.yaml`    | Nav drawer → Sign Out → confirm OK → back on Login Page. |

Cases 2–8 sign in fresh via the **reusable login sub-flow** (`subflows/tc2_login.yaml`),
called with `runFlow`. That sub-flow is Maestro's shared-setup mechanism — the
equivalent of a login PageObject / `@BeforeMethod` in the Java suite. Fix the login
once, every case benefits.

---

## 4. Inspecting a screen to get selectors (replaces Appium Inspector / Studio)

No GUI inspector is needed. With the target screen on the device, dump the live UI
tree as JSON:

```powershell
maestro hierarchy > screen.json
```

Open `screen.json`, search the visible label (e.g. `SIGN IN`), and read the sibling
attributes — `resource-id`, `text`, `accessibilityText`. Those are your selectors.
Snapshots of the TC2 login + dashboard screens are saved under `ui-snapshots\` for
reference.

**Selector priority:** visible `text` → `id` (resource-id) → `accessibilityText`.

Key TC2 selectors discovered this way:

| Element | Selector |
|---|---|
| Username field | `id: com.alarmnet.tc2:id/edit_txt_username` |
| Password field | `id: com.alarmnet.tc2:id/edit_txt_password` |
| Sign-in button | `id: com.alarmnet.tc2:id/btn_login` (text `SIGN IN`) |
| Toolbar nav icon (any logged-in screen) | `id: com.alarmnet.tc2:id/navigation_menu` |
| Tabs | `text: HOME` / `PARTITIONS` / `DEVICES` / `CAMERAS` / `ACTIVITY` |

---

## 5. Credentials (never hardcoded)

Login flows read username/password from environment variables, so no secret is
committed to source:

```powershell
maestro test src\test\resources\maestro\flows\tc2_02_login.yaml `
  -e TC2_USERNAME=<user> -e TC2_PASSWORD=<pass>
```

Do **not** add an `env:` block with default values to a flow — those defaults
override values passed via `-e` at runtime (this silently blanked the fields during
bring-up).
