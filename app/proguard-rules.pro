# --- SQLCipher: JNI looks up these classes/methods by name -------------------------------------
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }

# --- Argon2Kt: JNI bindings ---------------------------------------------------------------------
-keep class com.lambdapioneer.argon2kt.** { *; }

# --- zxcvbn: dictionaries/keyboards are loaded as resources by class-relative lookup ------------
-keep class com.nulabinc.zxcvbn.** { *; }
-keepclassmembers class com.nulabinc.zxcvbn.** { *; }

# --- Strip all logging from release builds so nothing sensitive can reach logcat ----------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Make reverse engineering marginally harder without breaking crash de-obfuscation.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
