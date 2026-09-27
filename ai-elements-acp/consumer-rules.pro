# The ACP Kotlin SDK maps its messages with kotlinx.serialization (generated serializers are kept by
# the kotlinx-serialization rules); kotlin-logging binds SLF4J when present.
-keep class com.agentclientprotocol.model.** { *; }
-dontwarn org.slf4j.**
-dontwarn io.github.oshai.kotlinlogging.**
