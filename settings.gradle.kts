pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "handwrite"
include(":app")
include(":core:model")
include(":core:document")
include(":core:data")
include(":core:rendering")
include(":core:designsystem")
include(":feature:editor")
include(":feature:library")
include(":feature:settings")
include(":feature:export")
