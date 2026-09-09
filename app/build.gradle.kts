import java.util.Properties

plugins {
    id("com.android.application")
    // AGP 9.0+ 已内置 Kotlin Android 支持，无需 org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

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
    // 用最新平台编译。⚠️ targetSdk 刻意保持 36（见 defaultConfig），勿随 compileSdk 一并升 37
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

    // 剔除包内非运行必需资源。
    // ⚠️ AGP 9.x 会强制保留 app-metadata.properties 与 version-control-info.textproto，
    //    写了也排不掉（曾列在此处属无效规则），故不再列举
    packaging {
        resources.excludes += setOf(
            // kotlinx-coroutines 的调试探针，仅 debug 构建需要
            "DebugProbesKt.bin",
            // AGP 写入的依赖版本戳（约 60 个）。⚠️ 这些文件位于 META-INF 顶层，
            // 必须写 META-INF/*.version；写成 META-INF/**/*.version 匹配不到、静默失效
            "META-INF/*.version",
            // 依赖自带的许可证文本
            "META-INF/**/LICENSE.txt",
        )
    }

    defaultConfig {
        applicationId = "com.example.shuati"
        minSdk = 34
        // targetSdk 刻意停在 36 而非 37：⚠️ 勿随 compileSdk 一并升级，升到 Android 17 前需完整回归测试
        // （通知/AlarmManager、SAF 导入导出、AI 网络、查重等核心路径）。
        // Lint 的 OldTargetApi 警告为已知且有意保留，留作届时做 Android 17 适配的提醒，勿随手抑制。
        targetSdk = 36
        // versionName：展示用版本号 = 年月日 + 两位迭代版号（当天第一版 01，第二版 02……）
        // versionCode：系统用的递增整数，每次发版 +1（不可回退，与 versionName 无关）
        // 注：设为 50 = 当时的仓库总提交数（干净解耦计数器）；因小于已装机 release 的 2026090204，
        //     覆盖安装需先卸载旧版：adb uninstall com.example.shuati（会清本地数据，先备份导出）
        versionCode = 50
        versionName = "2026090204"
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
            // ⚠️ 只需 material3 的 opt-in：项目未依赖 androidx.compose.material（material 2）核心库，//    再加 ExperimentalMaterialApi 会报「opt-in requirement marker unresolved」警告
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
}

dependencies {
    // 基础组件
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose BOM（2026.08.00 = Compose 1.12 stable）：统一托管下列 ui / material3 的版本
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

    // DataStore：本项目以 Preferences 存 JSON，无需 protobuf
    // ⚠️ 1.2.0 修复了「R8 优化下 DataStore 抛 UnsatisfiedLinkError」（本项目开了 R8），且默认存储
    //    改为 FileStorage 降低 CorruptionException。勿退回 1.1.x：1.1.4/1.1.5 有 Gradle 元数据损坏问题。
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // JSON 序列化。⚠️ 版本需与 Kotlin 编译器匹配：本项目 Kotlin 2.4.10，
    //    1.11.0 基于 Kotlin 2.3.20（1.12.0 才对齐 2.4.10，但仍是 RC），故暂取 1.11.0
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Excel 读写：已改用自研轻量实现 logic/LightXlsx.kt（xlsx = ZIP + XML），仅支持 .xlsx。
    // ⚠️ 勿再引入 Apache POI —— 它曾让 APK 膨胀到 69MB。

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // 密钥本地加密存储（AI 配置 Key）。1.1.0-alpha06 虽已停滞，但 EncryptedSharedPreferences 无替代，勿升
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // 网络请求（AI 大模型 / 搜索引擎，OpenAI 兼容）
    // ⚠️ OkHttp 5.x 已移除 okhttp-android 工件与 gzip(RequestBody) 旧 API，本项目只用标准用法
    // ⚠️ Retrofit 3.0 唯一的变更是内置 OkHttp 3.14 → 4.12；本项目显式用 OkHttp 5.5.0，
    //    Gradle 会覆盖其传递依赖，故升级仅为保持版本新鲜、对运行时行为无影响。
    //    3.x 与 2.x 前向二进制兼容，API 无变化。
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    testImplementation("junit:junit:4.13.2")
}
