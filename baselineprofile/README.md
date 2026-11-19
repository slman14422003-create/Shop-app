# توليد Baseline Profile حقيقي

الملف `app/src/main/baseline-prof.txt` الحالي حل مؤقت يدوي. للحصول على ملف مبني على تشغيل فعلي:

1. في `settings.gradle.kts` أضف: `include(":baselineprofile")`.
2. في `build.gradle.kts` (الجذر) أضف إلى `plugins`:
   `id("com.android.test") version "9.5.0" apply false`
   `id("androidx.baselineprofile") version "1.4.1" apply false`  (راجع أحدث إصدار متوافق مع AGP لديك)
3. في `app/build.gradle.kts` أضف `id("androidx.baselineprofile")` إلى `plugins`، وفي `dependencies`:
   `baselineProfile(project(":baselineprofile"))`
4. وصّل جهازاً حقيقياً (Android 9+) ثم شغّل: `./gradlew :app:generateBaselineProfile`
5. يُنسخ الناتج تلقائياً إلى `app/src/main` ويُغنيك عن الملف اليدوي (احذف `baseline-prof.txt` القديم).

ملاحظة: لم تُبنَ هذه الوحدة في بيئة المراجعة؛ الإصدارات أعلاه تقدير يجب التحقق منه.
