# Applied to apps that use ai-elements-chat (R8 / ProGuard).

# Persisted @Serializable models (conversations).
-keepclassmembers @kotlinx.serialization.Serializable class dev.ai.elements.core.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes *Annotation*, InnerClasses
