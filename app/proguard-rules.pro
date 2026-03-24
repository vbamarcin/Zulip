# Project specific ProGuard rules.

# Retrofit needs generic signatures and runtime annotations for converter lookup.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Keep Retrofit service interfaces used via reflection.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Moshi generated adapters and JSON metadata.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter { *; }
-keep class kotlin.Metadata { *; }

# JSON adapters using custom from/to methods.
-keepclasseswithmembers class * {
	@com.squareup.moshi.FromJson <methods>;
	@com.squareup.moshi.ToJson <methods>;
}

-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
