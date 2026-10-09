dependencies {
    implementation(platform("io.github.junelegency:ai-elements-bom:0.3.0"))
    implementation("io.github.junelegency:ai-elements-ui")          // elements (protocol-independent)
    implementation("io.github.junelegency:ai-elements-core")        // AI SDK / AG-UI / model-API backends, agent loop, MCP

    // Optional, as needed:
    implementation("io.github.junelegency:ai-elements-genui")       // generative UI: A2UI surfaces, JsxPreview
    implementation("io.github.junelegency:ai-elements-mcp-apps")    // MCP Apps: interactive views of MCP tools
    implementation("io.github.junelegency:ai-elements-a2a")         // A2A agents (official a2a-java-sdk)
    implementation("io.github.junelegency:ai-elements-acp")         // Agent Client Protocol (official ACP Kotlin SDK)
    implementation("io.github.junelegency:ai-elements-koog")        // JetBrains Koog as the agent runtime
    implementation("io.github.junelegency.harness:harness-core")    // in-app agent
    implementation("io.github.junelegency.harness:harness-filesystem")
    implementation("io.github.junelegency.harness:harness-sandbox-proot") // + harness-shell
}
