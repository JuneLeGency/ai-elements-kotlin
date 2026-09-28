# Ktor (Koog's HTTP clients) probes for a JVM debugger with java.lang.management, which Android
# does not have; the probe is off the Android path (Ktor's documented R8 rule).
-dontwarn java.lang.management.**
