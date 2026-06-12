# RemoteCompose Field Guide

Everything learned building this project against `androidx.compose.remote`
**1.0.0-alpha12** (June 2026). Written for humans and AI assistants alike —
read this before touching `rcBack/src/main/kotlin/Server.kt` or the client's
player code. The library is experimental: APIs churn between alphas, and
several behaviors below were discovered empirically, not from docs.

## 1. What this is

RemoteCompose is AndroidX's server-driven UI system. A **creation** library
(JVM or Android) writes a compact binary *document* (layouts, text, colors,
state variables, expressions, click actions). A **player**
(`RemoteDocumentPlayer` composable / `RemoteComposePlayer` view) renders it
and runs all state and animation locally — no server roundtrips after load.

```
rcBack  (Ktor 3, JVM)                       rcClient (Android)
Server.kt --creates--> binary doc --HTTP--> RemoteDocumentPlayer renders it
   ▲                                            │ host actions (named, typed)
   └------- next request bakes new state ◄------┘ host persists what it cares about
```

- `GET /ui/users?w=<dp>&h=<dp>&d=<density>&waves=<n>&favs=<csv-ids>`
- `GET /ui/users/{id}?w=&h=&d=&fav=0|1`

## 2. Studying the API (do this, not guesswork)

- Published versions: `curl https://dl.google.com/android/maven2/androidx/compose/remote/group-index.xml`
- **Download the `-sources.jar`** for the exact alpha you target:
  `https://dl.google.com/android/maven2/androidx/compose/remote/<artifact>/<ver>/<artifact>-<ver>-sources.jar`
  and read it. This is the single most effective move — the alphas differ
  substantially and online docs lag.
- The `support/` directory in this repo is a full AndroidX checkout from the
  alpha04 era. It is **reference only and stale** — never grep it broadly
  (hundreds of unrelated projects) and don't trust it for current APIs.
- Artifact platforms: `remote-creation`/`-core`/`-jvm` and `remote-core` have
  JVM variants (server OK). `remote-creation-compose` (the `@Composable`
  authoring API with `RemoteText` etc.) and the players are **Android-only**.

## 3. Server-side authoring (alpha12 DSL)

The modern API is `androidx.compose.remote.creation.dsl`: `createRcBuffer`,
`RcScope` (with `Box/Column/Row/Text/Spacer/StateLayout`), `Modifier`
(`padding/size/background/clip/onClick/ripple/verticalScroll/weight/...`).
It's all `@RestrictTo(LIBRARY_GROUP)` — usable from a plain JVM module (no
Android lint there), but expect breaking changes per alpha.

Profile boilerplate (apiLevel 8 at alpha12):

```kotlin
val profile = RcProfile(
    Profile(CoreDocument.DOCUMENT_API_LEVEL, RcProfiles.PROFILE_ANDROIDX, JvmRcPlatformServices())
    { info, p, _ -> RemoteComposeWriter(p, hTag(DOC_WIDTH, info.width), hTag(DOC_HEIGHT, info.height)) },
    experimental = true,   // adds PROFILE_EXPERIMENTAL + the DOC_PROFILES header tag
)
createRcBuffer(profile, hTag(Header.DOC_WIDTH, w), hTag(Header.DOC_HEIGHT, h),
    hTag(Header.DOC_CONTENT_DESCRIPTION, desc), hTag(Header.FEATURE_CLICK_VERSION, 1)) { ...RcScope... }
```

Ops are validated at *write time* against `Operations.getOperations(apiLevel,
profiles)`; an unsupported op throws `Operation N is not supported for this
version`. The player picks its parser map from the same `DOC_PROFILES` header,
so writer and player agree as long as you declare what you use.

### State, expressions, actions

- Variables: `remoteFloat(v)` / `remoteInteger(v)` / `remoteBool(v)` /
  `remoteText(s)`; named variants exist for host-visible variables.
- Expressions evaluate on the player: `RcFloat`/`RcInteger` support
  arithmetic operators; time sources `hour()`, `minutes()`, `animationTime()`,
  `continuousSeconds()`; `sin/cos/clamp`; live text via
  `createTextFromFloat(value, whole, decimal, RcTextFromFloatSpec)` and
  `RcText + RcText` concatenation (`textMerge`).
- Click actions: `Modifier.onClick { setValue(v, expr); hostAction("name") }` —
  multiple actions per click work fine in one block.
- `StateLayout(intVar) { childA; childB }` switches (and interpolates) between
  child trees by integer state — the building block for tabs, toggles,
  segmented controls.
- Theming: colors registered with `remoteThemedColor(...)` resolve light/dark
  on the player following the system theme.

### Host ↔ document communication

- Document → host: click actions can fire **named host actions**. With a
  payload: `HostAction(name, HostNamedActionOperation.INT_TYPE, valueId)` —
  see pitfall 2: `valueId` is the id of a registered integer variable
  (`writer.addInteger(v)`), *not* the literal.
  The client receives them in `RemoteDocumentPlayer(onNamedAction = { name, value, stateUpdater -> })`.
- Host → document: bake values into the next document request via query
  params (what this project does — simple and stateless), or push into a
  running document through named variables/`StateUpdater`.
- **Persistence pattern used here**: document state gives instant UI; every
  mutation also fires a host action; the host mirrors the value (memory for
  session state, SharedPreferences for durable state) and sends it back as a
  query param so the server bakes the correct initial value into the next
  document.

## 4. The density/pixel model (biggest foot-gun)

The alpha12 player lays out **everything in raw pixels** — modifier
dimensions, paddings, clip radii, *and* font sizes. Nothing is multiplied by
density on the device. Therefore:

- The client reports `w`/`h` (dp) and `d` (density) with every request.
- The server multiplies every dp/sp value by density when writing
  (`Ui.dp()` / `Ui.sp()` in `Server.kt`).
- Don't bother with the alternatives — all tested and broken at alpha12:
  - `DOC_DENSITY_BEHAVIOR = DENSITY_BEHAVIOR_DP` scales padding/offset/border
    but **not** exact sizes or clip radii.
  - `ROOT_CONTENT_BEHAVIOR` (`SIZING_SCALE`+`SCALE_FILL_WIDTH`) needs the
    DEPRECATED profile, must be written before the root component (the DSL
    can't), and scales by the wrong factor anyway (~density instead of
    width-ratio: content overflows).

## 5. Pitfalls (each cost real debugging time)

1. **`remoteThemedColor(Int, Int)` is broken** at alpha12: it routes ARGB
   *values* into the id-based `addThemedColor(short, short)` overload →
   garbage/negative variable ids → player crashes with
   `ArrayIndexOutOfBoundsException` in `IntMap.findKey` (negative key) during
   `setDocument`. Fix: `remoteThemedColor(remoteColor(light), remoteColor(dark))`.
2. **`HostAction(name, INT_TYPE, x)`**: `x` is a *variable id*. Register the
   payload (`writer.addInteger(v)`, raw id = `(id % 0x100000000L).toInt()`).
   Passing a literal compiles, then delivers a meaningless number (e.g. 1935)
   to the host.
3. **Add `hTag(Header.FEATURE_CLICK_VERSION, 1)`** — without it the player
   ALSO runs a legacy gesture detector and each tap fires click actions ~3×.
4. **`StateLayout` directly inside a `Column`** paints its active child at the
   column's origin (overlapping earlier siblings). Wrap it in a `Box`.
5. **`CircleShape` clip isn't applied to backgrounds**; use
   `RoundedRectShape(r,r,r,r)` with `r = size/2` for circular avatars.
6. Player parse/init errors don't crash the app — the view renders a red
   triangle + message and prints the stack to `System.err`
   (`adb logcat | grep -A15 "System.err"`).
7. Document state lives in the loaded document instance. Navigate away and
   back = new document = state reset, unless the host mirrors it (see
   persistence pattern above).
8. Server-side doc generation failures surface as Ktor 500s; the stack is in
   the server log, and write-time op validation catches most mistakes early.

## 6. Client-side notes

- `RemoteDocumentPlayer(document = CoreDocument, documentWidth/Height = px, onNamedAction = ...)`;
  build the `CoreDocument` with `RemoteDocument(bytes).document`. Both are
  `@RestrictTo` — annotate call sites with `@SuppressLint("RestrictedApi")`.
- AGP 9 has **built-in Kotlin**: do NOT apply `org.jetbrains.kotlin.android`
  (hard error). Keep `org.jetbrains.kotlin.plugin.compose`, and use a
  top-level `kotlin { compilerOptions { jvmTarget } }` block instead of the
  removed `android.kotlinOptions`.
- androidx libs from the 2026.05 BOM require `compileSdk 37`.
- Ktor 3 client: `HttpResponse.readBytes()` → `readRawBytes()`.
- Emulator reaches the host machine at `http://10.0.2.2:8080`
  (`usesCleartextTraffic="true"` is set in the manifest).

## 7. Verification workflow that works

```bash
cd rcBack && ./gradlew run &                       # server on :8080
curl -s -o /dev/null -w "%{http_code} %{size_download}B" \
  "localhost:8080/ui/users?w=448&h=997&d=3.0"      # doc generates without 500
cd rcClient && ./gradlew installDebug
adb shell am start -n com.mohsen.rcclient/.MainActivity
adb exec-out screencap -p > /tmp/s.png             # look at it, don't assume
adb shell input tap X Y / input swipe ...          # drive interactions
adb logcat -d | grep -E "RemoteUiRepo|System.err"  # requests + player errors
```

Screenshot after every server change — several pitfalls above produced
documents that *generated* fine and only failed (or mis-rendered) on device.

## 8. Version facts (as of 2026-06)

Latest RemoteCompose: `1.0.0-alpha12` (versions sort oddly: `alpha010` came
between `alpha09` and `alpha11`). Stack pinned in this repo: server
Kotlin 2.4.0 / Ktor 3.5.0 / Gradle 9.5.1; client AGP 9.2.1 / compileSdk 37 /
Compose BOM 2026.05.01 / navigation-compose 2.9.8 / Ktor client 3.5.0.
When bumping the alpha: re-download sources jars, re-check every pitfall —
some are bugs that will get fixed (and workarounds may then need removal).
