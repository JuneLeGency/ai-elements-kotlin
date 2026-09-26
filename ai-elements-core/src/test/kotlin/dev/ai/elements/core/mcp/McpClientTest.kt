package dev.ai.elements.core.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Header rules of the MCP 2026-07-28 Streamable HTTP transport. */
class McpClientTest {

    @Test
    fun headerValues_useTheBase64SentinelWhenNotPlainAscii() {
        assertEquals("us-west1", McpClient.encodeHeaderValue(JsonPrimitive("us-west1")))
        assertEquals("=?base64?SGVsbG8sIOS4lueVjA==?=", McpClient.encodeHeaderValue(JsonPrimitive("Hello, 世界")))
        assertEquals("=?base64?IHBhZGRlZCA=?=", McpClient.encodeHeaderValue(JsonPrimitive(" padded ")))
        assertEquals("=?base64?bGluZTEKbGluZTI=?=", McpClient.encodeHeaderValue(JsonPrimitive("line1\nline2")))
        assertEquals("=?base64?PT9iYXNlNjQ/bGl0ZXJhbD89?=", McpClient.encodeHeaderValue(JsonPrimitive("=?base64?literal?=")))
        assertEquals("true", McpClient.encodeHeaderValue(JsonPrimitive(true)))
        assertEquals("42", McpClient.encodeHeaderValue(JsonPrimitive(42)))
    }

    private fun schema(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun xMcpHeader_annotationsAreCollectedAndValidated() {
        val ok = schema("""{"type":"object","properties":{"region":{"type":"string","x-mcp-header":"Region"},"q":{"type":"string"},
            "opts":{"type":"object","properties":{"tenant":{"type":"integer","x-mcp-header":"Tenant"}}}}}""")
        assertEquals(mapOf(listOf("region") to "Region", listOf("opts", "tenant") to "Tenant"), McpClient.mcpHeaderParams(ok))
        // Invalid: number type, duplicate names (case-insensitive), non-token names.
        assertNull(McpClient.mcpHeaderParams(schema("""{"properties":{"x":{"type":"number","x-mcp-header":"X"}}}""")))
        assertNull(McpClient.mcpHeaderParams(schema("""{"properties":{"a":{"type":"string","x-mcp-header":"Id"},"b":{"type":"string","x-mcp-header":"id"}}}""")))
        assertNull(McpClient.mcpHeaderParams(schema("""{"properties":{"a":{"type":"string","x-mcp-header":"bad name"}}}""")))
    }

    @Test
    fun wwwAuthenticate_challengeParameters() {
        val c = McpClient.parseChallenge("""Bearer error="invalid_token", resource_metadata="https://mcp.example.com/.well-known/oauth-protected-resource", scope="files:read files:write"""")
        assertEquals("https://mcp.example.com/.well-known/oauth-protected-resource", c["resource_metadata"])
        assertEquals("files:read files:write", c["scope"])
    }
}
