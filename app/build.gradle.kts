plugins {
    id("com.android.application")
    // AGP 9.0+ 已内置 Kotlin Android 支持，无需 org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import java.util.Properties

// ── 本地签名配置（keystore.properties 被 .gitignore 忽略，不入库） ──
// 文件内容示例（由本地创建，勿提交）：
//   storeFile=D:/Android/paibanrili/paibanrili
//   storePassword=xxx
//   keyAlias=key0
//   keyPassword=xxx
// 若文件不存在（如 CI / 无密钥环境），release 自动退化为 unsigned 变体，不影响 debug 开发。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSigning = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.example.shuati"
    // AGP 9.2.0 支持 compileSdk/targetSdk 37（本机已安装 android-37 平台）
    compileSdk = 37

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    packaging {
        resources.excludes += setOf(
            // kotlinx-coroutines 的调试探针（DebugProbesKt.bin）只应存在于 debug 构建，
            // release 不应携带。同理排除其它可能的调试残留。
            "DebugProbesKt.bin",
            // AGP 自动写入的依赖版本戳，运行时不需要（约 60 个 *.version），位于 META-INF 子目录下
            "META-INF/*.version",
            // 依赖自带的 LICENSE 文本，非运行必需
            "META-INF/**/LICENSE.txt",
            // 构建/版本控制元数据，非运行必需
            "META-INF/com/android/build/gradle/app-metadata.properties",
            "META-INF/version-control-info.textproto",
        )
    }

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
        // 2026-09-02 迭代：移除测试题库模块、release 包瘦身（排除 META-INF 版本戳/LICENSE）
        versionName = "2026090201"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // R8 混淆 + 资源压缩，显著减小包体（APK 曾达 69MB，主要是 Apache POI 库撑起）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 若本地配置了 keystore.properties 则自动签名 release（产物为 -release.apk，可直装）
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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
    // Core（1.18.0 比 1.15 新 3 个 minor，主要是 minSdk 23+ 适配 + Java 21 内部优化，API 兼容）
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose BOM（2026.08.00 = Compose 1.12 stable，2026-08-12 发布；含新组件与性能改进）
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
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

    // JSON serialization（1.10.x 进入 API 稳定期，与 1.7.3 兼容）
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // Excel / XLSX 读写：已改用自研轻量实现 logic/LightXlsx.kt（xlsx=ZIP+XML），
    // 彻底移除 Apache POI（poi-ooxml/poi/xmlbeans/commons-compress），大幅减小包体。
    // 仅支持 .xlsx，不再支持 .xls。

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // 密钥本地加密存储（AI 配置 Key）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // 网络请求（AI 大模型 / 搜索引擎，OpenAI 兼容）
    // OkHttp 5.x 移除了 okhttp-android 单独工件、移除 gzip(RequestBody) 旧 API；
    // 本项目仅使用 OkHttpClient.Builder + Retrofit 标准用法（AiExplainService.kt:46），无任何 4→5 breaking API 调用。
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    testImplementation("junit:junit:4.13.2")
}
