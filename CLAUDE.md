# RemoteCompose demo project

Server-driven UI experiment: `rcBack/` (Ktor JVM server creating RemoteCompose
binary documents) + `rcClient/` (Android player app).

**Before working on anything RemoteCompose-related, read `REMOTECOMPOSE.md`**
— it documents the alpha12 API, the density/pixel model, host↔document state
patterns, and eight empirically-discovered pitfalls (broken themed colors,
HostAction payload ids, multi-firing clicks, StateLayout positioning, ...).
Most of them produce documents that generate fine and only break on device.

Rules of thumb:
- `support/` is a stale full AndroidX checkout — reference only, never scan it.
- To learn the current API, download `-sources.jar` from
  `dl.google.com/android/maven2/androidx/compose/remote/...` (see guide §2).
- Verify server UI changes with a real emulator screenshot, not just a 200.
- AGP 9 client: built-in Kotlin — never apply `org.jetbrains.kotlin.android`.
