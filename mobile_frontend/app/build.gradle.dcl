androidApplication {
    namespace = "org.example.app"

    dependencies {
        implementation("org.apache.commons:commons-text:1.11.0")
        implementation(project(":utilities"))
        // Material Components for Android (for theming and styles, non-Compose)
        implementation("com.google.android.material:material:1.12.0")
        // AndroidX AppCompat for compatibility resources
        implementation("androidx.appcompat:appcompat:1.7.0")
        // Core KTX (optional helpers)
        implementation("androidx.core:core-ktx:1.13.1")
    }
}
