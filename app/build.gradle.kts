import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    // org.jetbrains.kotlin.android حُذفت: منذ AGP 9.0 دعم Kotlin مدمج داخل
    // AGP نفسه، وتطبيق هذا الـ plugin بشكل منفصل صار خطأ بناء صريحًا (لا
    // مجرد تحذير) — راجع https://kotl.in/gradle/agp-built-in-kotlin.
    // ملاحظة مهمة إن ظهر تضارب/دمج بين ملفات نكهتَي full وpublic بعد هذا
    // التغيير: راجع "Migrate the kotlin.sourceSets{} DSL" في نفس الرابط؛
    // الوضع الافتراضي المتضمَّن في AGP يفترض أن مجلدي app/src/full/java
    // وapp/src/public/java يبقيان مفصولين تلقائيًا دون أي إعداد إضافي.
    // plugin مترجم Compose يبقى — منفصل تمامًا عن دعم Kotlin الأساسي.
    id("org.jetbrains.kotlin.plugin.compose")
}

// يقرأ إعدادات التوقيع أولاً من متغيرات بيئة CI (RELEASE_*)، وإذا لم
// تكن موجودة (بناء محلي) يرجع لملف app/keystore.properties إن وُجد.
val releaseStoreFile: String? = System.getenv("RELEASE_STORE_FILE")
val releaseStorePassword: String? = System.getenv("RELEASE_STORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")

val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

// نفس نمط keystore.properties تماماً: ملف محلي غير مرفوع لأي مستودع
// (راجع .gitignore وapp/ai.properties.example) لتزويد "سيمو المتصل"
// بمفتاح Gemini API وعنوان مرآة اختياري تُخبَز داخل BuildConfig وقت
// البناء، فتعمل على أي جهاز (بما فيها نسخة public التي لا تملك شاشة
// أدوات المطور أصلاً ولذلك لم يكن ممكناً ضبط هذا فيها من قبل) دون أي
// إعداد يدوي بعد التثبيت. بيئة CI تقرأ من متغيرات البيئة أولاً؛ البناء
// المحلي يرجع لملف app/ai.properties إن وُجد. القيمة المحفوظة فعلياً من
// شاشة أدوات المطور (نسخة full فقط، إن ضبطها المطوّر يدوياً) تبقى لها
// الأولوية دوماً فوق هذه القيمة المخبوزة (راجع PreferencesRepository.kt).
val aiPropertiesFile = rootProject.file("app/ai.properties")
val aiProperties = Properties().apply {
    if (aiPropertiesFile.exists()) {
        load(FileInputStream(aiPropertiesFile))
    }
}
val bakedGeminiApiKey: String =
    System.getenv("GEMINI_API_KEY")
        ?: (aiProperties["geminiApiKey"] as String?)
        ?: ""
val bakedGeminiBaseUrl: String =
    System.getenv("GEMINI_BASE_URL")
        ?: (aiProperties["geminiBaseUrl"] as String?)
        ?: ""
val bakedGeminiModel: String =
    System.getenv("GEMINI_MODEL")
        ?: (aiProperties["geminiModel"] as String?)
        ?: ""

val hasEnvSigning = !releaseStoreFile.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()
val hasLocalSigning = keystorePropertiesFile.exists()

// SHA-256 (hex, no colons) of the *release* signing certificate, used by
// SecurityUtils.kt at runtime to detect a repackaged/re-signed APK (a common
// result of apktool decompile → edit → resign → reinstall). Read from a CI
// env var first, then from keystore.properties for local builds, so nothing
// sensitive needs to be hardcoded in source. Get this value once with:
//   keytool -list -v -keystore <your.keystore> -alias <alias> | grep "SHA256:"
// (strip the colons). Leave it unset/blank and the check is skipped entirely
// - it only activates once you provide the real value.
val releaseSignatureSha256: String =
    (System.getenv("RELEASE_SIGNATURE_SHA256")
        ?: keystoreProperties["signatureSha256"] as String?
        ?: "").replace(":", "").uppercase()

android {
    namespace = "com.salman.herbalencyclopedia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.salman.herbalencyclopedia"
        minSdk = 24
        targetSdk = 36
        versionCode = (System.getenv("APP_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("APP_VERSION_NAME") ?: "1.0"

        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "EXPECTED_SIGNATURE_SHA256", "\"$releaseSignatureSha256\"")
        // ميزة إدمن جديدة: بطاقة "معلومات البناء" بأدوات الإدارة تعرض متى
        // بُني الـ APK فعلياً (تاريخ/وقت التصريف نفسه، لا تاريخ التثبيت على
        // الجهاز) — مفيد للتأكد من أن نسخة الاختبار الحالية هي فعلاً آخر
        // بناء، خصوصاً عند تركيب عدة نسخ full/public على نفس الجهاز أو بعد
        // إعادة توزيع APK قديم بالغلط. يُحسَب مرة واحدة وقت تكوين Gradle
        // (وقت البناء الفعلي)، لا وقت تشغيل التطبيق.
        buildConfigField("long", "BUILD_TIME_MILLIS", "${System.currentTimeMillis()}L")

        buildConfigField("String", "BAKED_ONLINE_API_KEY", "\"$bakedGeminiApiKey\"")
        buildConfigField("String", "BAKED_ONLINE_BASE_URL", "\"$bakedGeminiBaseUrl\"")
        buildConfigField("String", "BAKED_ONLINE_MODEL", "\"$bakedGeminiModel\"")
    }

    signingConfigs {
        if (hasEnvSigning || hasLocalSigning) {
            create("release") {
                if (hasEnvSigning) {
                    storeFile = file(releaseStoreFile!!)
                    storePassword = releaseStorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                } else {
                    storeFile = rootProject.file("app/${keystoreProperties["storeFile"]}")
                    storePassword = keystoreProperties["storePassword"] as String
                    keyAlias = keystoreProperties["keyAlias"] as String
                    keyPassword = keystoreProperties["keyPassword"] as String
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // نكهتان (product flavors) من نفس الكود:
    //   - "public": نسخة المستخدم العادي — بلا تسجيل دخول وبلا أي شاشة أو
    //     قدرة إدارية (لوحة التحكم، تعديل الأعشاب/الخلطات، ملاحظات الأدمن،
    //     إعدادات التحديث الإدارية). رابط التحقق من التحديثات نفسه يبقى
    //     فعّالاً كما هو تماماً — هذا غير مرتبط بالأدمن إطلاقاً.
    //   - "full": النسخة الحالية بكل شيء (تسجيل دخول + لوحة تحكم كاملة).
    //
    // الفرق الفعلي بينهما هو أي مجموعة ملفات مصدر تُضاف: كل نكهة لها مجلد
    // مصدر مطابق (app/src/public أو app/src/full) يوفّر نفس الأصناف بنفس
    // التوقيعات العامة (AuthRepository, LoginScreen, AdminXXXScreen) —
    // النسخة العامة (public) منها فارغة/معطَّلة فعلياً، بينما src/main
    // وHerbalNavGraph.kt وباقي الشاشات المشتركة تبقى كما هي بلا أي تعديل
    // إضافي بين النكهتين. راجع كل ملف داخل app/src/public للتفاصيل.
    // ═══════════════════════════════════════════════════════════════════
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "HAS_ADMIN", "true")
        }
        create("public") {
            dimension = "edition"
            buildConfigField("boolean", "HAS_ADMIN", "false")
            // بصمة تطبيق مختلفة كي تُنصَّب النسختان على نفس الجهاز معاً بلا تعارض
            applicationIdSuffix = ".user"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasEnvSigning || hasLocalSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // قاعدة بيانات القاموس المحلي (assets/lexicon/ar_synonyms.db) تُستثنى من
    // ضغط الـ APK الداخلي عمداً: هي أصلاً ملف ثنائي (SQLite) لا يستفيد من
    // إعادة الضغط (لا يصغر تقريباً)، وإبقاؤها بلا ضغط داخل الـ APK يجعل
    // نسخها إلى تخزين التطبيق عند الإقلاع مجرد نسخ بايتات مباشر وأسرع بلا أي
    // خطوة فكّ ضغط إضافية في وقت التشغيل — أبسط وأكثر ضماناً.
    androidResources {
        noCompress += "db"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // --- Compose ---
    // ملاحظة: BOM 2026.08.00 وما بعده يثبّت مكتبات Compose على إصدار 1.12.x
    // التي تفرض compileSdk 37 (غير متوفرة بعد عبر sdkmanager - راجع
    // compileSdk أعلى الملف). لذا نبقى مؤقتًا على 2026.06.01، آخر إصدار BOM
    // متوافق مع compileSdk 36. حدّث الاثنين معًا (compileSdk + BOM) متى صار
    // android-37 متاحًا فعليًا.
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.core:core-splashscreen:1.2.0")

    // --- Storage / images / EXIF ---
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // --- Firebase (project configured manually in HerbalApp.kt, no google-services.json) ---
    // ملاحظة: وحدات KTX (مثل firebase-firestore-ktx) أُزيلت من BoM إصدار 34+
    // (توقفت Google عن إصدارها منذ يوليو 2025)، ودُمجت واجهات KTX داخل
    // الوحدات الرئيسية نفسها. لذلك نعتمد الآن على firebase-firestore و
    // firebase-auth مباشرة بدل الإصدارات المنتهية.
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")

    // --- Coroutines (Task.await() used with Firebase calls) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    // --- Local on-device LLM (LiteRT-LM Kotlin API, arm64, minSdk 24) ---
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
