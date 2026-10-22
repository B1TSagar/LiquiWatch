# R8 rules for the release build.
-keepattributes SourceFile,LineNumberTable
# Samsung's Health Data SDK talks to Samsung Health over Parcelables and reflection.
-keep class com.samsung.android.sdk.health.data.** { *; }
-dontwarn com.samsung.android.sdk.health.data.**
# It was built with data binding, which this app doesn't use.
-dontwarn androidx.databinding.**
