pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "thermal-printer-sdk"

val localTestApk = file("local-test-apk/build.gradle.kts")
if (localTestApk.exists()) {
    include(":local-test-apk")
}
