# Alarm Clock — repository instructions

Android alarm clock (phone + Galaxy Watch) for a sideloaded personal install on a Galaxy S24 Ultra
(Android 17 / One UI 9). Kotlin + Jetpack Compose, multi-module, manual DI.

- **Read `docs/ARCHITECTURE.md` before changing code.** It defines modules, contracts, conventions,
  testing and git rules. Requirements are in `docs/SPEC.md`.
- Build: `export ANDROID_HOME=/opt/android-sdk; ./gradlew <task> --console=plain -q --max-workers=2`.
  HTTP 429 from Maven Central = wait and retry.
- Every user-visible string in English (`values/`) and Thai (`values-th/`), module-prefixed names.
- UI uses only `core:designsystem` components/tokens so all 12 themes work.
- Never put AI model names in code, resources, comments or commit messages.
- Commit trailers (exactly):
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
  `Claude-Session: https://claude.ai/code/session_01VfVBtDkmR1M8gCAmrw5C9E`
