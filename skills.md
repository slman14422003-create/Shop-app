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
