// Records an MCP Apps session between the official View SDK (`App`) and the official host SDK
// (`AppBridge`), both from @modelcontextprotocol/ext-apps, over an in-memory transport. The Kotlin
// host (ai-elements-mcp-apps) replays the View's messages and is checked against the host's replies.
//
//   cd server/mcp_apps && npm install && npm run record
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { InMemoryTransport } from "@modelcontextprotocol/client";
import { App } from "@modelcontextprotocol/ext-apps";
import { AppBridge } from "@modelcontextprotocol/ext-apps/app-bridge";

const out = resolve(dirname(fileURLToPath(import.meta.url)), "../../ai-elements-mcp-apps/src/test/resources/fixtures/mcp-apps/official-session.jsonl");
const log = [];
const tap = (transport, from) => {
  const send = transport.send.bind(transport);
  transport.send = async (message, options) => { log.push({ from, message: JSON.parse(JSON.stringify(message)) }); return send(message, options); };
  return transport;
};

const [viewSide, hostSide] = InMemoryTransport.createLinkedPair();
const tool = { name: "show_notes_board", description: "Show the notes board", inputSchema: { type: "object", properties: {} }, _meta: { ui: { resourceUri: "ui://notes/board" } } };
const bridge = new AppBridge(
  null,
  { name: "reference-host", version: "1.0.0" },
  { openLinks: {}, serverTools: {}, serverResources: {}, logging: {}, updateModelContext: { text: {} }, message: { text: {} } },
  { hostContext: { theme: "light", displayMode: "inline", availableDisplayModes: ["inline", "fullscreen"], platform: "mobile", locale: "en-US", toolInfo: { id: "call-1", tool } } },
);
bridge.oncalltool = async ({ name, arguments: args }) =>
  name === "save_note"
    ? { content: [{ type: "text", text: `Saved note “${args.title}”.` }] }
    : { content: [{ type: "text", text: "1 note(s)" }], structuredContent: { notes: [{ title: "Milk", content: "2 litres" }] } };
bridge.onreadresource = async ({ uri }) => ({ contents: [{ uri, mimeType: "text/markdown", text: "## Milk\n2 litres" }] });
bridge.onmessage = async () => ({});
bridge.onopenlink = async () => ({});
bridge.onupdatemodelcontext = async () => ({});
bridge.onrequestdisplaymode = async ({ mode }) => ({ mode });
bridge.onsizechange = () => {};
bridge.onloggingmessage = () => {};
const initialized = new Promise((ok) => { bridge.oninitialized = ok; });

const app = new App({ name: "Notes board", version: "1.0.0" }, { availableDisplayModes: ["inline", "fullscreen"] }, { autoResize: false });
const results = new Promise((ok) => { app.ontoolresult = ok; });
app.ontoolinput = () => {};
app.onteardown = async () => ({});

await bridge.connect(tap(hostSide, "host"));
await app.connect(tap(viewSide, "view"));
await initialized;
await bridge.sendToolInput({ arguments: {} });
await bridge.sendToolResult({ content: [{ type: "text", text: "No notes yet." }], structuredContent: { notes: [] } });
await results;

await app.callServerTool({ name: "save_note", arguments: { title: "Milk", content: "2 litres" } });
await app.callServerTool({ name: "board_notes", arguments: {} });
await app.readServerResource({ uri: "notes://all" });
await app.sendMessage({ role: "user", content: [{ type: "text", text: "Summarize the notes on my board" }] });
await app.updateModelContext({ content: [{ type: "text", text: "The notes board shows 1 note(s): Milk" }] });
await app.openLink({ url: "https://modelcontextprotocol.io/extensions/apps" });
await app.requestDisplayMode({ mode: "fullscreen" });
await app.sendSizeChanged({ width: 360, height: 420 });
await app.sendLog({ level: "info", data: "board rendered" });
await bridge.setHostContext({ theme: "dark", displayMode: "fullscreen", availableDisplayModes: ["inline", "fullscreen"], platform: "mobile", locale: "en-US", toolInfo: { id: "call-1", tool } });
await bridge.teardownResource({});
await new Promise((ok) => setTimeout(ok, 20));

mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, log.map((entry) => JSON.stringify(entry)).join("\n") + "\n");
console.log(`wrote ${log.length} messages to ${out}`);
process.exit(0);
