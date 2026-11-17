# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# unrar5j (CBR / RAR archives)
# Keep the public extraction entry points. The library's unused command-line
# debug helper references Swing, which is absent from Android.
-keep class be.stef.rar.Unrar5j { *; }
-keep class be.stef.rar.ExtractionResult { *; }
-dontwarn be.stef.**
-dontwarn javax.swing.**
-dontwarn java.awt.**
