# Vyxel Apps R8 rules
#
# Gson (de)serializes ~70 model classes by reflection. It maps JSON keys to
# FIELD names, allocates via Unsafe (so it needs no constructor), and is always
# handed a Class object or a TypeToken — never a class-name string. It therefore
# never needs a method name or a class name. That is the whole basis of the rule
# below: keep every field name in the app package, and let R8 rename every
# method and class.
#
# This replaced a blanket `-keep class com.vythera.vyxelapps.** { *; }`, which
# kept method names too and so left the entire app effectively unobfuscated.
# Method names are exactly what an automated APK patcher pattern-matches on, so
# keeping them undid most of the value of minification.
#
# Enum constants are static fields, so `<fields>` also covers Gson's enum
# handling (it serializes via name()). Models live across AppData.kt, updater/,
# api/ and expressive/, which is why this is package-wide rather than a list —
# a hand-maintained list of ~70 classes would silently rot as models are added,
# and the failure mode is a null field inside a non-null Kotlin type at runtime.
-keepclassmembers class com.vythera.vyxelapps.** {
    <fields>;
    <init>(...);
}

# Why <init> is kept as well as <fields>:
# Gson leaves any JSON-absent field null, ignoring the Kotlin default — several
# DTOs declare non-null types with defaults (GitHubRepo.name, .owner, .packageId
# …), so instances legitimately carry nulls in non-null-typed fields. Reading
# such a field is harmless, but data-class copy() re-invokes the constructor,
# whose Kotlin non-null parameter checks R8 compiles down to Object.getClass().
# That crashed HomeScreen.kt:210 (`it.copy(source = …)`) on a release build.
# Keeping constructors restores the behaviour the shipped APK relied on.
#
# This is masking a real latent bug: those fields should be nullable (as was
# already done for Release.body). Fixing the DTOs properly would let this rule
# shrink back to just <fields>.

# WorkManager persists the worker's fully-qualified class NAME in its database
# and revives it with Class.forName, so this one class cannot be renamed. Its
# (Context, WorkerParameters) constructor is invoked reflectively too.
-keep class com.vythera.vyxelapps.UpdateCheckWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Reflection metadata needed by Gson TypeToken and Retrofit generic parsing
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Gson TypeToken subclasses capture generics via reflection
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Retrofit — keep service interface methods and their annotations
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# WorkManager's Room database is instantiated by reflection
# (Class.forName(canonicalName + "_Impl")) — R8 must keep the generated
# implementation classes or the app crashes at startup (verified on-device).
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.**

# Shizuku uses AIDL/reflection across its provider
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Kotlin coroutines debug metadata
-dontwarn kotlinx.coroutines.**
