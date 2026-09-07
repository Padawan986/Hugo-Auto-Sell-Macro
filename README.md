# Hugo SMP Auto-Sell / Orders

Client-side Fabric mod for **Minecraft 1.21.11** that automates emptying a chest and selling via `/sell` — or delivering via `/order <item>` — on **Hugo SMP**.

> **Note:** This is a client-side helper macro. Use at your own risk and follow the server rules of Hugo SMP.

---

##  Features

- **Automatic chest emptying** — look at a chest, press **K**, the macro takes items out on a configurable interval
- **Sell mode** — fully automatic `/sell` GUI handling
- **Order mode** — automatic `/order <item>` flow based on the item taken from the chest:
  1. Sends `/order <item-id>` (dynamically, e.g. `/order pumpkin`)
  2. Picks the matching order entry with the **exact item id** (never mixes up e.g. `pumpkin` and `carved_pumpkin`), highest price first
  3. Clicks **"Ganzes Inventar liefern"** (deliver whole inventory)
  4. Clicks **"Bestätigen"** (confirm)
- **Two rebindable keybinds** (Minecraft → Controls → *Hugo SMP Auto-Sell*):
  - **K** — start / stop the macro
  - **J** — switch between `/sell` and `/order <item>` mode
- **Hotbar overlay** — shows status and next run countdown without covering chat/action-bar messages
- **Tutorial screen** on first start (`/autosell tutorial` to reopen)
- **Auto-updater** — checks `version.txt` on startup; if a newer version exists, an in-game popup asks **Yes / No**. On Yes it downloads the latest GitHub release, verifies the version inside the JAR, replaces the installed file and offers a restart button
- Languages: **Deutsch + English**

##  Requirements

- Minecraft **1.21.11**
- Fabric Loader **≥ 0.15.0**
- Fabric API
- Java **21+**

## Installation

1. Download the latest `hugo-autosell-1.21.x-<version>.jar` from the
   [Releases](https://github.com/Padawan986/Hugo-Auto-Sell-Macro/releases) page
2. Put it into your `mods` folder (remove older versions of the mod)
3. Start the game

##  Usage

1. Place/look at the chest you want to farm
2. Look at the chest and press **K** (or `/autosell toggle`)
3. Press **J** (or `/autosell mode`) to switch between `/sell` and `/order <item>`

### Commands (`/autosell …`)

| Command | Description |
|---|---|
| `/autosell toggle` | Start / stop the macro |
| `/autosell mode` | Switch `/sell` ↔ `/order <item>` (same as **J**) |
| `/autosell command toggle` | Same as `mode` |
| `/autosell command <befehl>` | Set custom sell command |
| `/autosell interval <sekunden>` | Interval between runs (5–3600 s) |
| `/autosell protecthotbar <true\|false>` | Keep hotbar items untouched |
| `/autosell clearchest` | Forget saved chest position |
| `/autosell tutorial` | Show the tutorial again |
| `/autosell status` | Show status, mode, interval, chest |

## 🔨 Build from source

```bash
gradle build
```

The finished JAR lands in `build/libs/`.

### Releasing a new version (for maintainers)

1. Bump `mod_version` in `gradle.properties` **and** `MOD_VERSION` in `AutoSellMod.java`
2. Build, then create a GitHub Release with the JAR as asset
3. Update `version.txt` in `main` to the new version — the in-game updater compares against it

##  Links

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/hugosmp-auto-sell-order
- Issues: https://github.com/Padawan986/Hugo-Auto-Sell-Macro/issues

##  License

MIT — see [LICENSE](LICENSE).

---

## 🇩🇪 Deutsch

Clientseitige Fabric-Mod für **Minecraft 1.21.11**, die auf **Hugo SMP** automatisch eine Kiste leert und per `/sell` verkauft — oder per `/order <Item>` liefert.

**Funktionen:** Automatisches Kisten-Leeren per Tastendruck (**K**), Sell-Modus mit `/sell`-GUI-Automatisierung, Order-Modus mit `/order <Item>`-Ablauf (passende Order mit **exakter Item-ID**, höchster Preis zuerst, „Ganzes Inventar liefern“, „Bestätigen“), Modus-Wechsel per **J**, Hotbar-Overlay mit Countdown, Tutorial beim ersten Start, **Auto-Updater mit Ja/Nein-Popup** sowie deutsche und englische Texte.

**Voraussetzungen:** Minecraft 1.21.11, Fabric Loader ≥ 0.15.0, Fabric API, Java 21+.

**Installation:** Neueste `hugo-autosell-1.21.x-<Version>.jar` von der
[Releases-Seite](https://github.com/Padawan986/Hugo-Auto-Sell-Macro/releases) laden, in den `mods`-Ordner legen (alte Version entfernen), Spiel starten.

**Bedienung:** Kiste anvisieren → **K** drücken → mit **J** zwischen `/sell` und `/order <Item>` wechseln. Alle Befehle siehe Tabelle oben (`/autosell status`, `/autosell tutorial`, …).
