pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // ADDED: JitPack repository for GitHub-hosted libraries like TarsosDSP
        maven { url = uri("https://jitpack.io") }
        // REMOVED: The 0110.be/releases/TarsosDSP/ repository, as JitPack will handle TarsosDSP
        maven {
            name = "be.0110.repoReleases"
            url = uri("https://mvn.0110.be/releases")
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // ADDED: JitPack repository
        maven { url = uri("https://jitpack.io") }
        // REMOVED: The 0110.be/releases/TarsosDSP/ repository
        maven {
            name = "be.0110.repoReleases"
            url = uri("https://mvn.0110.be/releases")
        }
    }
}

rootProject.name = "Haptic Beat"
include(":app")
