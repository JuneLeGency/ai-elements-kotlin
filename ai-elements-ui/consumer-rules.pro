# Applied to apps that use ai-elements-ui (R8 / ProGuard).

# WebView JS bridges used by Mermaid and KaTeX rendering (height and error callbacks).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
