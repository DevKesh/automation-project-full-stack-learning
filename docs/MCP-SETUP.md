# Selenium MCP — Setup & Activation

This project uses a **Selenium MCP server** to give the Copilot agent live, authoring-time
"eyes and hands" on a real browser — so it can navigate a URL, inspect the real DOM, and
**verify a locator resolves before writing it** into a page object. This is the modern,
zero-maintenance replacement for a hand-written `LocatorScanner`.

> **Boundary (non-negotiable):** the MCP server is used **only at authoring/verification time**.
> It drives its *own* Chrome session. It NEVER executes your tests. Tests always run through
> `mvn test` → TestNG → your `ThreadLocal<WebDriver>` → `BasePage` (explicit waits, AspectJ/Allure).
> Generated test code must never contain MCP calls — only `BasePage.type/click/getText`.

---

## 1. Source of truth (versioned in this repo)

The canonical server definition lives at the repo root: [`../mcp.json`](../mcp.json)

```json
{
  "mcpServers": {
    "selenium": {
      "command": "npx",
      "args": ["-y", "@angiejones/mcp-selenium@latest"]
    }
  }
}
```

This file is the team's reviewable record of *which* MCP server we standardize on. It is not
auto-discovered by every client (see below) — each developer activates it once in their client.

---

## 2. Activate it in your client

No client auto-loads the repo-root `mcp.json` under that exact name, and clients differ on the
top-level key (`mcpServers` vs `servers`). Pick your client:

### IntelliJ IDEA (GitHub Copilot plugin) — primary
Config file: `%LOCALAPPDATA%\github-copilot\intellij\mcp.json` — uses the **`servers`** key:

```json
{
  "servers": {
    "selenium": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@angiejones/mcp-selenium@latest"]
    }
  }
}
```

Or via UI: **Settings → Languages & Frameworks → GitHub Copilot → Model Context Protocol**,
then use Copilot **Agent mode** in the chat window. Restart the Copilot chat after editing.

### GitHub Copilot CLI
Run `/mcp`, add a **stdio** server: command `npx`, args `-y @angiejones/mcp-selenium@latest`.

### VS Code Copilot (`.vscode/mcp.json`) — uses the **`servers`** key
```json
{ "servers": { "selenium": { "type": "stdio", "command": "npx", "args": ["-y", "@angiejones/mcp-selenium@latest"] } } }
```

---

## 3. Prerequisites

- **Node.js** (v18+; this machine has v22) — `npx` fetches the server on first run, no global install.
- **Google Chrome** installed. The matching ChromeDriver is resolved by Selenium Manager.
- First launch downloads the package; allow a few seconds.

---

## 4. Available MCP tools and how they map to this framework

| MCP tool | Purpose in our workflow |
|---|---|
| `start_browser` / `navigate` | Open the AUT at the requested URL |
| `interact` (click/hover) / `send_keys` / `press_key` | Prove an element is actionable on the live DOM |
| `get_element_text` / `get_element_attribute` | Capture verification text + candidate locator attributes |
| `execute_script` | Advanced DOM reads (scroll, computed styles) |
| `take_screenshot` | Evidence during authoring |
| `close_session` | **Always** called at end of a scan — no lingering browser |

**Locator strategy → framework mapping** (why single-stack Selenium was chosen):

| MCP `by` | `locators/<page>.json` `type` | Selenium `By` |
|---|---|---|
| `id` | `id` | `By.id` |
| `css` | `cssSelector` | `By.cssSelector` |
| `xpath` | `xpath` | `By.xpath` |
| `name` | `name` | `By.name` |
| `class` | `className` | `By.className` |

Locator discovery priority chain: `data-testid → id → name → aria-label → stable className → relative XPath`.

---

## 5. Sanity check

In an agent-mode chat, ask: *"Open Chrome, navigate to https://www.myntra.com, and take a screenshot."*
If a Chrome window launches and a screenshot returns, the server is wired correctly.

See [`Agentic Integration Design Approach - Phase 1.md`](./Agentic%20Integration%20Design%20Approach%20-%20Phase%201.md)
for the full end-to-end architecture this server plugs into.
