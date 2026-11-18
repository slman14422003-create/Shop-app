# المساعد المحلي — حزمة التعديلات (المراحل 1–5)

انسخ مجلد `app/` فوق مشروعك (يدمج الملفات بنفس المسارات). الملفات المعدّلة الوحيدة الموجودة سابقاً:
`AppRoutes.kt` (+ مساران) · `ShopManagerApp.kt` (ربط المساعد + مساران + تبديل الرئيسية) · `ui/settings/SettingsScreen.kt` (بطاقة "المساعد المحلي").
لم يُلمس: الدردشة المحلية/Firebase، ولا أي مستودع، ولا قواعد Firestore.

## المرحلة 2 — محرك الفهم (assistant/engine، Kotlin نقي)
EngineModels · ArabicText · NumberParser · DateParser · Lexicon · EntityMatcher · Calculator · AssistantEngine
اختبارات: app/src/test/.../AssistantEngineTest.kt (≈126 حالة في 12 اختباراً). التشغيل: `./gradlew :app:testDebugUnitTest --tests "*AssistantEngineTest"`
كل اختبار يطبع قائمة الإخفاقات كاملة مرة واحدة.

## المرحلة 3 — التنفيذ والتراجع
AssistantActions (كتابة عبر نفس المستودعات + SelfChangeLedger + ChangeHistory + DebtActivityLog + نسخ احتياطي) · AssistantQueries · AssistantStore · AssistantModels · AssistantViewModel (مكدّس تراجع لآخر 10).

## المرحلة 4 — الروبوت والرئيسية
ui/RobotAvatar · ui/AssistantHomeScreen. الرئيسية الكلاسيكية (DashboardScreen) لم تُحذف: تعود من إعدادات المساعد ← "الرئيسية الجديدة".

## المرحلة 5 — الدردشة والإعدادات
ui/AssistantChatScreen · ui/AssistantCardViews · ui/AssistantSettingsScreen
