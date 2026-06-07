# Preserve line numbers in release stack traces for Play Console crash reports.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve generic signatures so Firestore can resolve POJO field types via reflection.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Firestore maps documents to data classes by reflection — keep model classes,
# their no-arg constructors, and getter/setter shapes intact.
-keep class tools.mo3ta.bazeed.data.** { *; }
-keepclassmembers class tools.mo3ta.bazeed.data.** {
    <init>(...);
    public <fields>;
    public *** get*();
    public *** is*();
    public void set*(***);
}

# Firebase / Play Services ship consumer rules, but be defensive against
# overly-eager R8 stripping of reflection entry points.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Kotlin coroutines reflection-loaded MainDispatcherFactory.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Strip Log calls from release builds.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
