plugins {
    id("handwrite.android.library")
    id("handwrite.android.compose")
}

android {
    namespace = "com.xsgovo.handwrite.feature.settings"
}

dependencies {
    implementation(project(":core:document"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
}
