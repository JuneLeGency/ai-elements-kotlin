android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    packaging.resources.excludes += listOf(
        "META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/INDEX.LIST",
        "META-INF/DEPENDENCIES", "META-INF/beans.xml",
    )
}
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
