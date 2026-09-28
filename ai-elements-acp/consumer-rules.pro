# The ACP Kotlin SDK maps its messages with kotlinx.serialization (generated serializers are kept by
# the kotlinx-serialization rules); kotlin-logging binds SLF4J when present.
-keep class com.agentclientprotocol.model.** { *; }
-dontwarn org.slf4j.**
-dontwarn io.github.oshai.kotlinlogging.**
# Ktor (the SDK's WebSocket transport) probes for a JVM debugger with java.lang.management, which
# Android does not have; the probe is off the Android path (Ktor's documented R8 rule).
-dontwarn java.lang.management.**
