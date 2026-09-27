# --- kotlinx.serialization -------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.fitcoach.app.**$$serializer { *; }
-keepclassmembers class com.fitcoach.app.** { *** Companion; }
-keepclasseswithmembers class com.fitcoach.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.fitcoach.app.data.remote.dto.** { *; }
-keep class com.fitcoach.app.ai.dto.** { *; }
-keep class com.fitcoach.app.data.club.model.** { *; }

# --- Retrofit / OkHttp -----------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-keepattributes Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# --- Room / Hilt / WorkManager: правила приходят с библиотеками (consumer rules). ---------

# --- ZXing ------------------------------------------------------------------------------
-dontwarn com.google.zxing.**

# --- Логи: убираем отладочные вызовы в релизе --------------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
