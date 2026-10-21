# ADR 0004: Acknowledged macOS executor and private ChatGPT tunnel

Status: Accepted. Date: 2026-10-21.

## Context

A local Codex plugin does not by itself expose tools to a cloud ChatGPT conversation.
Direct independent MCP database writes also bypass AppState's synchronization journal,
and distributed notifications alone cannot prove that an app command was executed.

## Decision

Keep a single Swift stdio MCP surface. Local Work uses the desktop plugin; cloud chat
uses OpenAI Secure MCP Tunnel to reach it. Cloud connectivity is optional and does not
change offline operation or BYOC storage ownership.

For normal runtime calls the MCP server sends a UUID-tagged command to a private local
mailbox; AppState serially consumes commands and returns a completion/error response.
Only the current user can access the mailbox. Requests expire, responses do not imply
auto-retry, and all arguments are interpreted as tool data. AppState records domain
changes into its existing sync engine before acknowledging them.

Keep focus state as an optional, backward-compatible local runtime field. Store cloud
credentials in Keychain and use a common preferences domain across binary entrypoints.

## Consequences

The current App version must be installed and reachable through Launch Services. The
Mac and tunnel process must be online for cloud calls. Setup needs the user's account
permissions and a runtime key. Tool discovery, local execution and actual cloud-chat
execution are distinct verification checkpoints. Never describe any one as proof of
all three. This design does not create a public SaaS or publish a public plugin.

## Native tunnel lifecycle

Expose the optional runtime in macOS AI settings rather than requiring a terminal.
Default auto-start off; allow explicit connect/stop and start on App launch. The App
owns only its own child processes and stops them on termination. Preserve external
terminal profiles and never import credentials from another process. Store a dedicated
runtime key in Keychain; create a separate private profile that references its child
environment. Allowlist inherited variables, drain and classify diagnostics without
retaining raw output, and validate loopback health URLs before probing. Configuration
creation and local ready checks remain separate from real ChatGPT tool-call acceptance.
