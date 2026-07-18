plugins {
    id("handwrite.android.library")
    id("handwrite.android.compose")
}

android {
    namespace = "com.xsgovo.handwrite.feature.export"
}

dependencies {
    implementation(project(":core:document"))
    implementation(project(":core:rendering"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
}
