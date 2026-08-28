# kotlinx-serialization keeps generated serializers reachable
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.aubergine.civops.** {
    *** Companion;
}
-keepclasseswithmembers class io.aubergine.civops.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.aubergine.civops.**$$serializer { *; }

# Room entities are reflected over by the generated DAOs
-keep class io.aubergine.civops.data.db.** { *; }
