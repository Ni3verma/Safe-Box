# preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# hide the original source file name.
-renamesourcefileattribute SourceFile

# Keep custom exceptions.
-keep public class * extends java.lang.Exception

# Preserve class names of UI screen actions for safe action logging via simpleName.
-keepnames class com.andryoga.safebox.ui.**.*ScreenAction*
-keepnames class com.andryoga.safebox.ui.**.*ScreenAction*$*
-keepnames class com.andryoga.safebox.ui.**.*Action*
-keepnames class com.andryoga.safebox.ui.**.*Action*$*

# LoginViewModel logs which WrapOpenOutcome a failed fingerprint unwrap produced; keep the names readable.
-keepnames class com.andryoga.safebox.security.envelope.WrapOpenOutcome
-keepnames class com.andryoga.safebox.security.envelope.WrapOpenOutcome$*

-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite