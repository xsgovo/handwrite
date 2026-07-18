plugins {
    id("handwrite.android.library")
    id("handwrite.android.compose")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xsgovo.handwrite.feature.library"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:document"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
