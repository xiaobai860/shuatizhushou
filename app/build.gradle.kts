plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import java.util.Properties

android {
    namespace = "com.example.shuati"
    // AGP 8.9.2 官方支持上限为 36（本机无 android-37 稳定平台；代码未使用 API 37 专属符号）
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.shuati"
        minSdk = 34
        targetSdk = 36
        // 版本号规则：
        //  - versionName（外显版本号，规则不变）：YYYYMMDD + 两位当日迭代，例 2026082903
        //  - versionCode（应用内部总迭代数 / 判断升级降级的字段）：独立累计计数器，
        //    每次执行 assemble*/bundle*/install*/build 自动 +1（从 1 开始），与 versionName 解耦、单调递增。
        val buildNumberFile = rootProject.file("app/buildNumber.properties")
        val buildProps = Properties()
        if (buildNumberFile.exists()) {
            buildNumberFile.inputStream().use { buildProps.load(it) }
        }
        val isBuildTask = gradle.startParameter.taskNames.any {
            it.contains("assemble", ignoreCase = true) ||
            it.contains("bundle", ignoreCase = true) ||
            it.contains("install", ignoreCase = true) ||
            it.equals("build", ignoreCase = true)
        }
        var buildNumber = buildProps.getProperty("buildNumber")?.toIntOrNull() ?: 0
        if (isBuildTask) {
            buildNumber += 1
            buildProps.setProperty("buildNumber", buildNumber.toString())
            buildNumberFile.parentFile.mkdirs()
            buildNumberFile.outputStream().use { buildProps.store(it, "Auto-incremented build number (versionCode)") }
        }
        versionCode = buildNumber
        versionName = "2026082903"
        multiDexEnabled = true
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi"
        )
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose BOM（1.11.x 与 AGP 8.9 兼容；backdrop 需要 Compose ≥1.11）
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // DataStore (preferences + protobuf not needed; we store JSON via Preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Excel / XLSX parsing (Apache POI)
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.xmlbeans:xmlbeans:5.2.0")
    implementation("org.apache.commons:commons-compress:1.27.1")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // 密钥本地加密存储（AI 配置 Key）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // 网络请求（AI 大模型 / 搜索引擎，OpenAI 兼容）
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
