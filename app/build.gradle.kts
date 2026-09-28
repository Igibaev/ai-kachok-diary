import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// ---------------------------------------------------------------------------------------------
// White-label: каждый файл brands/<name>.properties становится product flavor <name>.
// Новый клиент = новый файл + (опционально) папка app/src/<name>/res с логотипом и иконкой.
// ---------------------------------------------------------------------------------------------
val brandFiles: List<File> = rootProject.file("brands")
    .listFiles { f -> f.isFile && f.extension == "properties" }
    ?.sortedBy { it.name }
    ?: emptyList()
require(brandFiles.isNotEmpty()) { "Не найдено ни одного файла brands/*.properties" }

fun loadBrand(file: File): Properties = Properties().apply {
    file.reader(Charsets.UTF_8).use { load(it) }
}

fun Properties.str(key: String, default: String = ""): String =
    getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: default

fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun String.toConstName(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase()

/** Строковые поля бренда, попадающие в BuildConfig (см. BrandConfig.kt). */
val brandStringKeys = listOf(
    "appName", "brandName", "aiCoachName", "defaultLanguage",
    "clubName", "clubCity", "clubAddress", "clubHours", "clubPhone", "clubWhatsapp",
    "clubInstagram", "clubMapUrl", "clubWebsite", "clubDataUrl", "newsUrl",
    "accentColor", "accentOnColor", "backgroundColor", "surfaceColor", "cardColor",
    "aiProxyUrl", "aiProxyToken", "aiModel", "privacyPolicyUrl"
)

/**
 * Секрет прокси не хранится в отслеживаемом brands/<name>.properties: приоритет у переменной окружения
 * AI_PROXY_TOKEN_<NAME> и gitignored-файла brands/<name>.secrets.properties.
 */
fun brandSecret(file: File, key: String, fallback: String): String {
    val name = file.nameWithoutExtension
    System.getenv("${key.toConstName()}_${name.uppercase()}")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val secrets = file.resolveSibling("$name.secrets.properties")
    if (secrets.isFile) loadBrand(secrets).str(key).takeIf { it.isNotEmpty() }?.let { return it }
    return fallback
}

/** Внешние адреса бренда: только https (политика конфиденциальности обещает шифрование в транзите);
 *  http разрешён лишь для локальной отладки на loopback (10.0.2.2 / localhost). */
fun requireHttps(file: File, p: Properties, key: String) {
    val value = p.str(key)
    val localDebug = Regex("^http://(10\\.0\\.2\\.2|localhost|127\\.0\\.0\\.1)(:\\d+)?(/.*)?$").matches(value)
    require(value.isBlank() || value.startsWith("https://") || localDebug) {
        "Бренд ${file.name}: $key должен начинаться с https:// (получено: $value); http допустим только для 10.0.2.2/localhost."
    }
}


/** Контраст акцента к фону (WCAG relative luminance). Слишком тёмный акцент на тёмном фоне — ошибка сборки. */
fun luminance(hex: String): Double {
    val clean = hex.trim().removePrefix("#").takeLast(6)
    val rgb = clean.toLongOrNull(16) ?: return 0.0
    fun channel(v: Long): Double { val c = v / 255.0; return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
    return 0.2126 * channel((rgb shr 16) and 0xFF) + 0.7152 * channel((rgb shr 8) and 0xFF) + 0.0722 * channel(rgb and 0xFF)
}
fun contrast(a: String, b: String): Double {
    val la = luminance(a); val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}
brandFiles.forEach { file ->
    val p = loadBrand(file)
    val ratio = contrast(p.str("accentColor", "#C8FF00"), p.str("backgroundColor", "#0D0D0D"))
    require(ratio >= 3.0) {
        "Бренд ${file.name}: контраст accentColor/backgroundColor = ${"%.2f".format(ratio)} (< 3.0). Выберите более светлый акцент или более тёмный фон."
    }
    // Тема тёмная: текст (#F5F5F5/#AAAAAA) задан константами, светлый фон дал бы невидимый UI.
    require(luminance(p.str("backgroundColor", "#0D0D0D")) < 0.2) {
        "Бренд ${file.name}: backgroundColor слишком светлый — тема приложения тёмная (см. docs/WHITE_LABEL.md)."
    }
    listOf("aiProxyUrl", "clubDataUrl", "newsUrl", "privacyPolicyUrl").forEach { requireHttps(file, p, it) }
}

val appVersionCode: Int = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

// Подпись релиза: переменные окружения KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD.
// Без них release подписывается debug-ключом (удобно для демо, НЕ для Google Play).
val keystorePath: String? = System.getenv("KEYSTORE_PATH") ?: (project.findProperty("KEYSTORE_PATH") as String?)

android {
    namespace = "com.fitcoach.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitcoach.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    flavorDimensions += "brand"
    productFlavors {
        brandFiles.forEach { file ->
            val p = loadBrand(file)
            val flavorName = file.nameWithoutExtension
            create(flavorName) {
                dimension = "brand"
                applicationId = p.str("applicationId", "com.fitcoach.$flavorName")
                versionName = p.str("versionName", "1.0.0")

                resValue("string", "app_name", p.str("appName", "FitCoach AI"))
                resValue("color", "ic_launcher_background", p.str("backgroundColor", "#0D0D0D"))
                resValue("color", "ic_launcher_foreground", p.str("accentColor", "#C8FF00"))

                buildConfigField("String", "BRAND_ID", quoted(flavorName))
                brandStringKeys.forEach { key ->
                    val value = if (key == "aiProxyToken") brandSecret(file, key, p.str(key)) else p.str(key)
                    buildConfigField("String", key.toConstName(), quoted(value))
                }
            }
        }
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/LICENSE*", "META-INF/AL2.0", "META-INF/LGPL2.1")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.splashscreen)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Kotlin Serialization
    implementation(libs.kotlin.serialization.json)

    // Coroutines
    implementation(libs.coroutines.android)

    // Navigation
    implementation(libs.navigation.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.workmanager)

    // Security
    implementation(libs.security.crypto)

    // Coil
    implementation(libs.coil.compose)

    // QR (пропуск участника)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
