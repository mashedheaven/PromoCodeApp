# Keep line numbers so release stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit, OkHttp, kotlinx.serialization, Room, Hilt and Play services all ship
# their own consumer rules; nothing app-specific needs keeping beyond the
# serializers generated for our wire models.
-keepclassmembers class com.promocodeapp.data.remote.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.promocodeapp.data.remote.**$$serializer { *; }

# Retrofit inspects generic signatures of suspend functions at runtime.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
