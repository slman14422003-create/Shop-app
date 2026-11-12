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

## assistant requests task
- Lexicon.kt: IMPORTANT_WORDS (هامة/مهم/ضروري/مستعجل...), more Levantine verbs, need/finished/confirm/cancel words, pack words (شوال/تنكة/دزينة...), filler stop words
- EngineModels.Command.important; AssistantEngine: scoring boosts ADD_SHORTAGE for plain add verbs and importance words, penalizes ADD_MATERIAL when importance word present, build() sets important, describe() appends flag
- AssistantActions.addShortages: sets star (setImportant with top order) on new items, or on existing shortage when asked important; undo removes added and unstars
- AssistantViewModel.process: ADD_SHORTAGE skips the confirm card unless risky/reasons (undo button stays on result)
- tests: shortage_important_and_levantine added in AssistantEngineTest (not run offline)

## google login + device lock + chat notifications task
- ChatAuthRepository.kt: email/password removed; signInWithGoogle(activity) uses OAuthProvider("google.com") with prompt=select_account (no web client id needed, only Google provider enabled in Firebase Auth of shop-app-1670f)
- device binding: transaction on devices/{ANDROID_ID} + users/{uid}.deviceId; device_taken / account_other_device errors sign the user out again
- ui/chat/GoogleLoginScreen.kt: AuthGate wraps ShopManagerApp in MainActivity (after LockScreen); no guest path; signOut removed from ChatSessionViewModel, ChatListScreen, ConversationScreen, ShopManagerApp; ChatAuthScreen.kt deleted
- firebase-chat/firestore.rules: users.deviceId + devices collection; MUST be published in the chat Firebase project
- ChatMessageWatcher.kt (started/stopped from RemoteChangeWatcher): chats listener, baseline per chat in prefs "chat_notif_baseline", skips open chat via ChatOpenTracker; NotifKind.CHAT + NotifSound.DROP (water_drop_soft); NotificationHelper.showChatMessageNotification uses MessagingStyle; tap -> NotificationAction.OpenChat
- sync notification: channel id changed to realtime_sync_hidden_v3 (IMPORTANCE_NONE); old channel id could not be lowered by the app after creation
- not compiled here: verify via ci

## login error fix
- chatErrorMessage now walks the cause chain (transaction may wrap FirebaseFirestoreException) and the generic message appends exception type/code for diagnosis
- deviceId fallback is a hash of Build fields (stable across clear data) instead of a random UUID stored in prefs

## chat login scoped to chat tab
- cause of "Default FirebaseApp is not initialized": Google OAuthProvider flow needs the default FirebaseApp; ChatFirebase now initializes the chat project (shop-app-1670f) as the DEFAULT app; shop project stays the named "shopApp" instance
- login gate removed from MainActivity (app opens normally); sign-in panel (ChatLoginPanel) now lives inside the chat tab only
