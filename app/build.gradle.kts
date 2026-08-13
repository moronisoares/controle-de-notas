import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

/**
 * Chave de assinatura fixa (keystore.properties, fora do Git).
 *
 * Assinar sempre com a mesma chave e o que permite instalar uma versao nova por
 * cima da anterior sem desinstalar o app. Quando o arquivo nao existe (ex.: um
 * clone novo do repositorio), o build cai na chave de depuracao padrao do
 * Android e o APK gerado NAO atualiza uma instalacao feita com a chave fixa.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.controlenotas"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.controlenotas"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "1.7"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("stable") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        val stableSigning = signingConfigs.findByName("stable")

        debug {
            // O APK distribuido e o de depuracao; assinar com a chave fixa e o
            // que torna as atualizacoes instalaveis por cima.
            if (stableSigning != null) signingConfig = stableSigning
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (stableSigning != null) signingConfig = stableSigning
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // PDFBox-Android traz metadados que colidem com outras dependências.
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.compose.runtime:runtime-livedata")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Exportação em segundo plano com progresso e notificação.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Leitura do texto de notas em PDF (Apache 2.0, sem custo).
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
