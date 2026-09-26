# Security

Please report vulnerabilities privately via GitHub Security Advisories on this repository rather
than a public issue.

Handling of credentials in this project:
- API keys and OAuth tokens are encrypted with an Android Keystore AES-GCM key (`SecretStore`),
  never logged, and never part of exception messages.
- OAuth uses PKCE and a one-shot listener bound to 127.0.0.1; the `state` parameter is verified.
- WebViews that render Mermaid / KaTeX load only bundled assets with `securityLevel: 'strict'`.
