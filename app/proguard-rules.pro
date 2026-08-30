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

# 依赖库可选类缺失（不影响运行，R8 建议忽略）
# errorprone 注解：Tink 加密库（security-crypto）静态分析用，运行期不需要
-dontwarn com.google.errorprone.annotations.**
