# Add project specific ProGuard rules here.
# Retrofit / OkHttp / kotlinx.serialization are already handled by consumer rules.
-keepattributes Signature
-keepattributes *Annotation*

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.lanraragi.reader.data.model.** { kotlinx.serialization.KSerializer serializer(...); }
