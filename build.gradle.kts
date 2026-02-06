plugins {
    // id("com.android.application") version "8.10.1" apply false // <-- Check this version
    //  id("com.android.library") version "8.10.1" apply false
    // id("org.jetbrains.kotlin.android") version "2.0.21" apply false // <-- Check this version

    alias(libs.plugins.android.application) apply false // Use alias from TOML
    alias(libs.plugins.android.library) apply false    // Use alias from TOML
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false     // Use alias from TOML

}
// ... rest of the file
