# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# kotlinx.serialization
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.githubclient.app.**$$serializer { *; }
-keepclassmembers class com.githubclient.app.** { *** Companion; }
-keepclasseswithmembers class com.githubclient.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# 忽略 R8 缺失类
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.tukaani.xz.**
-dontwarn org.apache.commons.compress.**
