# The libraries ship their own consumer rules (serialization models, WebView bridges).
# Keep serializers of the demo's own @Serializable models (conversations, settings).
-keepclassmembers @kotlinx.serialization.Serializable class dev.ai.elements.demo.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
