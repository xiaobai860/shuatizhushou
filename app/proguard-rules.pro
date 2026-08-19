# Keep Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.example.shuati.model.**$$serializer { *; }
-keepclassmembers class com.example.shuati.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.shuati.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Apache POI
-dontwarn org.apache.poi.**
-keep class org.apache.poi.** { *; }
