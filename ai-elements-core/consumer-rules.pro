# Applied to apps that use ai-elements-core (R8 / ProGuard).

# Persisted @Serializable models (provider profiles, OAuth tokens, conversations).
-keepclassmembers @kotlinx.serialization.Serializable class dev.ai.elements.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes *Annotation*, InnerClasses
