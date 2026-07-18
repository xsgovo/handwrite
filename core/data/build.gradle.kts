plugins {
    id("handwrite.android.library")
}

android {
    namespace = "com.xsgovo.handwrite.core.data"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:document"))
}
