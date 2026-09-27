"""An OAuth-protected MCP server for live tests of the client's MCP authorization.

Built only from the official `mcp` SDK: `MCPServer(auth=..., auth_server_provider=...)` serves the
protected resource (401 + RFC 9728 metadata) and an authorization server (RFC 8414 metadata,
RFC 7591 dynamic registration, authorization code + PKCE, RFC 8707 resource indicators).

The authorization endpoint approves at once — it stands in for a real sign-in page, which a
test must never automate — so the full client flow can run headless:

    uv run python mcp_auth_server.py        # http://127.0.0.1:8791/mcp
"""

from __future__ import annotations

import os
import secrets
import time

import uvicorn
from mcp.server.auth.provider import (
    AccessToken,
    AuthorizationCode,
    AuthorizationParams,
    OAuthAuthorizationServerProvider,
    RefreshToken,
    construct_redirect_uri,
)
from mcp.server.auth.settings import AuthSettings, ClientRegistrationOptions
from mcp.server.mcpserver import MCPServer
from mcp.server.transport_security import TransportSecuritySettings
from mcp.shared.auth import OAuthClientInformationFull, OAuthToken

PORT = int(os.environ.get("MCP_AUTH_PORT", "8791"))
BASE = os.environ.get("MCP_AUTH_URL", f"http://127.0.0.1:{PORT}")
SCOPE = "notes"


class AutoApproveProvider(OAuthAuthorizationServerProvider[AuthorizationCode, RefreshToken, AccessToken]):
    """In-memory authorization server that grants every request (test only)."""

    def __init__(self) -> None:
        self.clients: dict[str, OAuthClientInformationFull] = {}
        self.codes: dict[str, AuthorizationCode] = {}
        self.access: dict[str, AccessToken] = {}
        self.refresh: dict[str, RefreshToken] = {}

    async def get_client(self, client_id: str) -> OAuthClientInformationFull | None:
        return self.clients.get(client_id)

    async def register_client(self, client_info: OAuthClientInformationFull) -> None:
        self.clients[client_info.client_id] = client_info

    async def authorize(self, client: OAuthClientInformationFull, params: AuthorizationParams) -> str:
        code = secrets.token_urlsafe(24)
        self.codes[code] = AuthorizationCode(
            code=code,
            scopes=params.scopes or [SCOPE],
            expires_at=time.time() + 300,
            client_id=client.client_id,
            code_challenge=params.code_challenge,
            redirect_uri=params.redirect_uri,
            redirect_uri_provided_explicitly=params.redirect_uri_provided_explicitly,
            resource=params.resource,
            subject="test-user",
        )
        return construct_redirect_uri(str(params.redirect_uri), code=code, state=params.state)

    async def load_authorization_code(self, client: OAuthClientInformationFull, authorization_code: str) -> AuthorizationCode | None:
        code = self.codes.get(authorization_code)
        return code if code and code.client_id == client.client_id else None

    async def exchange_authorization_code(self, client: OAuthClientInformationFull, authorization_code: AuthorizationCode) -> OAuthToken:
        self.codes.pop(authorization_code.code, None)
        return self._issue(client.client_id, authorization_code.scopes, authorization_code.resource)

    async def load_refresh_token(self, client: OAuthClientInformationFull, refresh_token: str) -> RefreshToken | None:
        token = self.refresh.get(refresh_token)
        return token if token and token.client_id == client.client_id else None

    async def exchange_refresh_token(
        self, client: OAuthClientInformationFull, refresh_token: RefreshToken, scopes: list[str]
    ) -> OAuthToken:
        self.refresh.pop(refresh_token.token, None)
        return self._issue(client.client_id, scopes or refresh_token.scopes, refresh_token.resource)

    async def load_access_token(self, token: str) -> AccessToken | None:
        access = self.access.get(token)
        if access and access.expires_at and access.expires_at < time.time():
            return None
        return access

    async def revoke_token(self, token: AccessToken | RefreshToken) -> None:
        self.access.pop(token.token, None)
        self.refresh.pop(token.token, None)

    def _issue(self, client_id: str, scopes: list[str], resource: str | None) -> OAuthToken:
        access, refresh = secrets.token_urlsafe(24), secrets.token_urlsafe(24)
        self.access[access] = AccessToken(token=access, client_id=client_id, scopes=scopes, expires_at=int(time.time()) + 3600, resource=resource, subject="test-user")
        self.refresh[refresh] = RefreshToken(token=refresh, client_id=client_id, scopes=scopes, resource=resource, subject="test-user")
        return OAuthToken(access_token=access, token_type="Bearer", expires_in=3600, refresh_token=refresh, scope=" ".join(scopes))


mcp = MCPServer(
    name="ai-elements-protected-notes",
    title="AI Elements protected notes",
    auth_server_provider=AutoApproveProvider(),
    auth=AuthSettings(
        issuer_url=BASE,
        resource_server_url=f"{BASE}/mcp",
        required_scopes=[SCOPE],
        client_registration_options=ClientRegistrationOptions(enabled=True, valid_scopes=[SCOPE], default_scopes=[SCOPE]),
    ),
)


@mcp.tool(title="Who am I")
def whoami() -> str:
    """Report that the request was authorized."""
    return "Signed in: this call carried a valid OAuth access token."


app = mcp.streamable_http_app(transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=False))

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=PORT)
