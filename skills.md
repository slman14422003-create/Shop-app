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

## chat firebase app id
- ChatFirebaseConfig.APP_ID now 1:631188161296:android:bc4e11dc05e84448081b95 (registered for com.shopmanager.app); ERROR_INVALID_CERT_HASH means the APK signing SHA-1 is not added to that app in Firebase

## chat upgrade task
- new pure logic (data/chat): ChatSearch (Arabic folding + highlight ranges), QuickReplies, ChatRetention, ChatNotifyRules (notify/baseline decision used by ChatMessageWatcher.process), PlaybackSpeeds, ImageSizing, LinkExtractor/HtmlMeta, MessageConversion types
- new android helpers: ChatImageCodec (compress <=1024px/80KB JPEG, Base64 in message doc), LinkPreviewRepository (https only, public hosts only, LruCache), VoiceTranscriber (m4a -> PCM -> SpeechRecognizer EXTRA_AUDIO_SOURCE, needs API 33+)
- messages: type image + fields image/imgW/imgH; chat doc field pinned {id,from,type,text}
- firebase-chat/firestore.rules CHANGED (image type, pinned, delete of messages older than 30d): MUST be published in the chat project
- notifications: per-chat MessagingStyle + group summary (id 1904), RemoteInput reply + mark read in NotificationActionReceiver, cancel on open chat / unread 0, vibration is part of channel id suffix _vib, PIN hides text via lockScreenPrivacy
- settings: chatSoundsEnabled (master), chatIncomingSoundEnabled, chatSendSoundEnabled, chatVibrationEnabled, chatLinkPreviewEnabled, chatQuickReplies, chatAutoDeleteDays (0/30/90/180) + confirm dialog; cleanup runs from ChatSessionViewModel.scheduleCleanup at most every 12h
- convert message: AssistantViewModel.planChatConversion/applyChatConversion -> ChatConversion (existing items/customers only, suggestions otherwise); wired in ShopManagerApp via AssistantMessageConverter
- tests added: ChatSearchTest QuickRepliesTest ChatRetentionTest ChatNotifyRulesTest PlaybackSpeedsTest ImageSizingTest LinkPreviewTest ChatConversionTest
- not compiled here (no gradle/kotlinc offline): verify via ci testDebugUnitTest

## brand dialog + red important star + settings redesign task
- new ui/common/AppFormDialog.kt: brand-identity form dialog (accent header tile with icon, title/subtitle, content slot, accent save pill, tonal cancel); emphasisColor switches header tile to red when the item is important
- ui/common/ImportantStarButton.kt: new ImportantRed color (theme aware), star button now red, new ImportantToggleCard (full-width "important item" row, red border/fill when on)
- MaterialEditDialog and MaterialCatalogScreen.QuantityEntryDialog (the quantity dialog) now use AppFormDialog + ImportantToggleCard; QuantityEntryDialog also hides the stepper and pins quantity to 1 for fixed fraction units, same rule as MaterialEditDialog
- QuantityStepper: accent filled plus button, tonal minus; UnitPill: selected pill uses accent fill with white text (also affects MissingFinderScreen)
- gold star replaced by ImportantRed in MaterialsListSection (ring, star, row tint), MaterialsChrome tab star, MissingFinderScreen summary star, MaterialsReportImage star paint
- settings: SettingsComponents row icons sit in tinted rounded tiles, account card has accent storefront tile + version chip, new SettingsSectionHeader, accent pill button on promo cards
- SettingsScreen order is now: account, server banner, display, security, notifications (+blocked banner), sounds and chat, sync and backup, app, help and assistant; LazyColumn spacing 10dp
- AssistantSettingsScreen: uses SettingsGroup/switchItem rows and SettingsSectionHeader, ConfirmDialog uses GlassAlertDialog
- not compiled here (no gradle offline): verify via ci

## two priority levels task (important vs very important)
- Material has two separate flags: important (yellow star, "important") and veryImportant (red star, "very important"); Material.level (0/1/2) and Material.flagged helpers in MaterialsModels.kt; old documents keep important=true and read as level 1
- Firestore field veryImportant added (MaterialsRepository map/add/backup); setPriority(id, level, topOrder) writes both flags (selecting one clears the other); setImportant(true) now means level 1
- MaterialsViewModel.setLevel and addMaterial(level = ...); dialogs (MaterialEditDialog, QuantityEntryDialog) now show two separate cards (important yellow, very important red) and onSave returns level 0/1/2
- list row star cycles none -> yellow -> red -> none; row tint and ring use priorityColor(level); tabs are now: very important / important / missing / all; all-tab sorts higher level first
- report image star color follows level; MissingFinder star stays yellow (level 1); ImportantStarButton has tone param (default yellow)
- not compiled here: verify via ci

## add-material bottom sheet + decluttered options
- new ui/common/AppFormSheet.kt: ModalBottomSheet form (same container/shape as debts QuickAddSheet) with header tile, content slot and accent save button; also PrioritySelector (one segmented row: normal / important yellow / very important red)
- MaterialEditDialog and QuantityEntryDialog now use AppFormSheet instead of the centered dialog; cancel button removed (swipe down or tap outside)
- layout order: name (edit only), quantity stepper (only for kilo / none), unit chips in one scrolling row, priority selector, optional note (collapsed behind "add note" chip unless it already has text)
- UnitPicker is now a horizontal scrolling chip row (also used in MissingFinderScreen); QuantityStepper min height 72dp
- AppFormDialog / ImportantToggleCard kept but no longer used by these screens
- not compiled here: verify via ci

## merged important tab + off-thread prices + dashboard input bar task
- MaterialsTabBar: tabs are now important (very important + important merged, label "هامة", shows red star and yellow star) / missing / all; indices 0/1/2; default listTab 0 always (empty-state fallback removed)
- MaterialsList: flaggedItems = level > 0 sorted level desc then Arabic name, so red rows come first then yellow; in tab 0 (not searching) it renders as one flat section without group headers; star cycle none -> yellow -> red -> none unchanged
- row line prices now come from rememberComputed (AppCompute single background thread) together with the summaries (index 4 = id -> price map); rows show the price once ready
- MaterialsScreen: duplicate changed-price snapshotFlow merged into one and run with flowOn(AppCompute.dispatcher); text share builds on AppCompute.dispatcher
- ShopManagerApp: dashboard (newHome) box reserve is animated in the layout phase: full pill height when the bottom bar is shown, only the nav bar inset when hidden (HIGH tier only, same as AppBottomBar); no recomposition per frame
- open question: the request line about the "crystal" controlling a level via a tap and stopping it was unclear; not implemented
- not compiled here: verify via ci

## final numbers only task
- cause: AnimatedAmount (ui/common/MotionExtras.kt) started at 0 and counted up on HIGH tier, so debt rows, person detail and dashboard hero showed intermediate numbers; AnimatedCounterText routed to it on HIGH tier
- change: AnimatedAmount now renders format(amount) directly (same signature, all call sites untouched); AnimatedCounterText always uses the AppCompute formatting path
- not compiled here: verify via ci

## drawOutline unresolved reference in LiquidGlass.kt task
- cause: drawOutline is a DrawScope extension function and was called in drawBehind without its import, so compileDebugKotlin failed at LiquidGlass.kt line 102
- change: added imports androidx.compose.ui.graphics.drawscope.drawOutline and androidx.compose.ui.graphics.drawscope.Stroke; the call now uses Stroke directly
- only file touched besides this one: ui/common/LiquidGlass.kt
- not compiled here: verify via ci
- correction: the drawOutline import itself failed to resolve (UNRESOLVED_IMPORT) in this Compose version; removed it and draw the rim with drawPath over a Path built from the Outline (Rectangle / Rounded / Generic), Stroke 1dp, same alpha
- not compiled here: verify via ci

## build, baseline profile and notification improvements task
- baseline profile: new workflow .github/workflows/baseline-profile.yml (manual dispatch): enables KVM, boots an API 34 x86_64 emulator, runs gradle :app:generateReleaseBaselineProfile -PbaselineProfile=true, validates the generated file (non-empty, at least 100 lines), copies it to app/src/main/baseline-prof.txt, uploads it as an artifact, commits it to main and optionally dispatches release.yml (bot pushes do not trigger push workflows, so dispatch is explicit)
- isolation: the :baselineprofile module and the androidx.baselineprofile app plugin load only when -PbaselineProfile is passed (settings.gradle.kts include and app/build.gradle.kts apply are conditional), so normal builds and release.yml are unchanged; the plugin is declared apply false in the root build file at 1.5.0 (stable, supports AGP 9 without newDsl=false)
- normal release builds pick up app/src/main/baseline-prof.txt natively through AGP; R8 rewrites the rules to the obfuscated names
- generator: BaselineProfileGenerator.kt opens the app, scrolls, visits the four bottom tabs by their text labels, goes back; every gesture is null-safe so onboarding or lock screens cannot fail the run; deps: macro 1.5.0, uiautomator 2.3.0, ext-junit 1.2.1
- release.yml: setup-gradle cache-read-only on non-main refs
- notifications (NotificationHelper.kt): setOnlyAlertOnce on overdue, due-reminder and note-done; note-done expires after 6h, debt-paid after 24h
- already present and left unchanged: R8 full mode, locale filters, JankStats tier downgrade, 60Hz motion scaling in MotionSpecs, grouped notifications with summaries, chat reply and mark-read actions, immutable PendingIntents
- not done on purpose: abiFilters (would break the x86_64 emulator install), icon library swap, FCM replacement of the realtime service, quiet hours (needs settings UI)
- not compiled here: verify via ci; first baseline run needs a manual dispatch of Generate Baseline Profile
