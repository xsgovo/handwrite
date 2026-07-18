plugins {
    id("handwrite.android.library")
    id("handwrite.android.compose")
}

android {
    namespace = "com.xsgovo.handwrite.core.designsystem"
}

dependencies {
    implementation(project(":core:model"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
}
