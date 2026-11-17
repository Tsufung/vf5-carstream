# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Car App Library classes
-keep class androidx.car.app.** { *; }
-dontwarn androidx.car.app.**

# Keep Gson model classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.vf5plus.carstream.Bookmark { *; }
-dontwarn com.google.gson.**

# Keep WebView JavaScript interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Android Auto service
-keep class com.vf5plus.carstream.VF5CarStreamService { *; }
