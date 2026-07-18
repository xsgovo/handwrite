plugins {
    id("handwrite.android.library")
    id("handwrite.android.compose")
}

android {
    namespace = "com.xsgovo.handwrite.core.rendering"
}

dependencies {
    implementation(project(":core:model"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
}
