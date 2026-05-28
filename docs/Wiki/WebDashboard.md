# Web Dashboard

> **Version:** 1.0.2.6 · **Config:** `config.json` → `webDashboard` section

---

## Overview

BigBangEssentials ships a built-in web dashboard for server monitoring and administration. It runs on an embedded HTTP server with WebSocket support for real-time updates. No external software required.

---

## Setup

1. Set `webDashboard.enabled: true` in `config.json` (default: `true`)
2. Configure `port` (default `8080`) and `websocketPort` (default `8081`)
3. Start the server — the dashboard auto-starts
4. Register a dashboard account in-game: `/dashboard register`
5. Open `http://<server-ip>:8080` in a browser and log in

---

## Account Registration

Players register their dashboard account **in-game** — they do not need to be online at login time after registering.

```
/dashboard register
```

Requires permission `bigbangessentials.dashboard.register`. After registering, the player can log in from the web browser at any time, even when offline.

### Discord Auth (Optional)

If **Simple Discord Link** is installed and configured, players can also authenticate via Discord. The mod is fully optional — standalone account registration works without it.

---

## Config (`config.json` → `webDashboard`)

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Enable the dashboard system |
| `autoStart` | `true` | Start dashboard on server start |
| `port` | `8080` | HTTP port |
| `websocketPort` | `8081` | WebSocket port for live updates |
| `bindAddress` | `"0.0.0.0"` | IP to bind (use `127.0.0.1` for local-only) |
| `enableCORS` | `true` | Allow cross-origin requests |
| `maxThreads` | `4` | Max concurrent request handler threads |
| `apiSettings.enableApiEndpoints` | `true` | Enable REST API endpoints |
| `apiSettings.logLinesToReturn` | `100` | Number of log lines returned by the logs endpoint |
| `securitySettings.allowConfigEditing` | `false` | Allow editing config files via the dashboard |
| `uiSettings.refreshInterval` | `5000` | Dashboard auto-refresh interval (ms) |
| `loggingSettings.logDashboardAccess` | `true` | Log dashboard login events |

---

## Dashboard Pages

| Page | URL | Permission | Description |
|---|---|---|---|
| Overview | `/` | `bigbangessentials.dashboard.view` | Server stats, player count, TPS, memory |
| Players | `/players` | `bigbangessentials.dashboard.view` | Online players, ban/kick/tp actions |
| Console | `/console` | `bigbangessentials.dashboard.manage` | View logs, send commands |
| Admin Controls | `/admin` | `bigbangessentials.dashboard.admin` | Server admin tools |
| Permissions | `/permissions` | `bigbangessentials.dashboard.admin` | Manage permission groups and nodes |
| Config | `/config` | `bigbangessentials.dashboard.admin` | Edit config files (if enabled) |

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/dashboard` | `bigbangessentials.dashboard` | Show dashboard info and URL |
| `/dashboard register` | `bigbangessentials.dashboard.register` | Register your in-game account for web login |
| `/dashboard start` | `bigbangessentials.admin.dashboard` | Start dashboard if stopped |
| `/dashboard stop` | `bigbangessentials.admin.dashboard` | Stop the dashboard |
| `/dashboard status` | `bigbangessentials.admin.dashboard` | Show dashboard status |

---

## Permissions

| Node | Default | Description |
|---|---|---|
| `bigbangessentials.dashboard.register` | ✅ | Register a dashboard account in-game |
| `bigbangessentials.dashboard.view` | 🔒 | View dashboard (read-only) |
| `bigbangessentials.dashboard.manage` | 🔒 | Access console and management tools |
| `bigbangessentials.dashboard.moderator` | 🔒 | Moderator-level dashboard access |
| `bigbangessentials.dashboard.admin` | 🔒 | Full admin dashboard access |
| `bigbangessentials.admin.dashboard` | 🔒 | Start/stop/manage the dashboard server |

---

## File Auto-Update

Dashboard HTML/JS/CSS files are versioned. On every server start, BigBangEssentials checks if the bundled dashboard files in the JAR are newer than the deployed files on disk — if so, they are automatically updated. Customised files will be overwritten if the version number increases.

---

*Back to [Wiki Home](Home)*
