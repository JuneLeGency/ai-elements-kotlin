# The A2A Java SDK discovers transports and HTTP clients with ServiceLoader and maps JSON with Gson/protobuf.
-keep class org.a2aproject.sdk.** { *; }
-keep class * implements org.a2aproject.sdk.client.http.A2AHttpClientProvider { *; }
-keep class * implements org.a2aproject.sdk.client.transport.spi.ClientTransportProvider { *; }
-dontwarn org.a2aproject.sdk.**
-dontwarn jakarta.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.jspecify.annotations.**
