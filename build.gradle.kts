plugins {
    id("com.android.application") version "9.0.0-alpha01" apply false // Use your actual AGP version
    // Remove or comment out the line below:
    // id("org.jetbrains.kotlin.android") version "2.0.21" apply false 
    
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
