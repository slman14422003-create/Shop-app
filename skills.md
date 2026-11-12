# skills

## last task
- fix: ci failure in :app:compileDebugUnitTestKotlin
- cause: KnownPerson requires `balance` (no default); test at AssistantEngineTest.kt:343 omitted it
- change: app/src/test/java/com/shopmanager/app/assistant/engine/AssistantEngineTest.kt:343 -> added `0.0` as third arg

## notes
- ci runs: gradle testDebugUnitTest assembleDebug assembleRelease; a test compile error fails the whole build
- when changing a data class in EngineModels.kt, grep tests for constructor calls
- release signing falls back to debug key unless RELEASE_KEYSTORE_BASE64 secret is set

## ci speed task
- workflow: .github/workflows/release.yml now runs only `gradle assembleRelease` (removed testDebugUnitTest, assembleDebug, lintRelease)
- release now publishes only shop-manager-release.apk + SHA256SUMS.txt in a single step (removed debug apk, 10s wait, second upload)
- to run tests again: add `testDebugUnitTest` back to the build step or use a separate workflow

## assistant home redesign task
- AssistantHomeScreen.kt: home is now robot only (tap opens full chat) + input bar; greeting/status text removed
- last reply card (result/answer/plain text) auto-hides after 30s (REPLY_VISIBLE_MS); confirm/clarify cards stay until acted on; entries remain in the action log and chat history
- undo button stays on the result card during the 30s and in the log list
- stats, bento cards, chips, suggestions, notes, last actions moved into AppSheet opened from a Dashboard icon next to the bell in the top bar
- AssistantViewModel.execute: message time refreshed when a confirm card turns into a result so the 30s counts from execution
- not compiled here (no gradle offline): verify via ci
