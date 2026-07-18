plugins {
    id("handwrite.kotlin.library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:document"))
    api(libs.junit4)
}
