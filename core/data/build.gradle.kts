plugins {
    id("handwrite.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.room)
}

android {
    namespace = "com.xsgovo.handwrite.core.data"
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val capitalizedName = variant.name.replaceFirstChar(Char::uppercase)
        variant.sources.java?.addStaticSourceDirectory(
            "build/generated/java/generate${capitalizedName}Proto/java",
        )
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:document"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)
    implementation(libs.hilt.android)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.room.testing)
}
