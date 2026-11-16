# kotlinx-serialization keeps generated serializers reachable
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.aubergine.ambit.** {
    *** Companion;
}
-keepclasseswithmembers class io.aubergine.ambit.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.aubergine.ambit.**$$serializer { *; }

# Room entities are reflected over by the generated DAOs
-keep class io.aubergine.ambit.data.db.** { *; }
