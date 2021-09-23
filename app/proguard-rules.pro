# preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# hide the original source file name.
-renamesourcefileattribute SourceFile

# Keep custom exceptions.
-keep public class * extends java.lang.Exception