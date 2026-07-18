plugins {
    id("handwrite.kotlin.library")
}

dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit4)
}
