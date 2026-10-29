plugins {
    kotlin("multiplatform") version "2.4.21"
}

group = "com.azanify"
version = "1.21.1"

kotlin {
    // Device + both simulator architectures: CI picks the one matching the runner.
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Intentionally none: the shared core stays dependency-free so a broken
            // third-party version can never block the iOS build.
        }
    }
}
