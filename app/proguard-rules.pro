# preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# hide the original source file name.
-renamesourcefileattribute SourceFile

# Keep custom exceptions.
-keep public class * extends java.lang.Exception

-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite