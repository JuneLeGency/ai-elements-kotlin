# kotlinx.serialization: keep generated serializers of our @Serializable models.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes *Annotation*, InnerClasses
# WebView JS bridges (Mermaid / KaTeX height + error callbacks).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
