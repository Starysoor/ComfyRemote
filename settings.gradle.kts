pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Keep Aliyun as a fallback for developers on constrained networks,
        // but do not let a mirror outage shadow dependencies available from
        // the canonical repositories (especially Android Lint tooling).
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
    }
}
rootProject.name = "ComfyRemote"
include(":app")
