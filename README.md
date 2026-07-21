# o.m.a.

A 2b2t Meteor Client utility addon, ported and maintained from [cozisAddon](https://github.com/Olrite/cozisAddon).

## Supported Minecraft versions

| Branch | Minecraft | Meteor Client |
|--------|-----------|---------------|
| `master` | **26.1.2** | [Latest](https://meteorclient.com/) |
| `legacy/1.21.11` | **1.21.11** | [Archive #85](https://meteorclient.com/api/download?version=1.21.11) |
| `legacy/1.21.4` | **1.21.4** | [Archive #42](https://meteorclient.com/api/download?version=1.21.4) |

Check out the branch matching your Minecraft version before building.

## Install (releases)

Download the JAR for your Minecraft version from [GitHub Releases](https://github.com/Olrite/oma/releases):

- `oma-1.0-mc1.21.4.jar`
- `oma-1.0-mc1.21.11.jar`
- `oma-1.0-mc26.1.2.jar`

Place the matching JAR in your `mods` folder alongside the correct Meteor Client version.

## Build from source

```bash
git clone https://github.com/Olrite/oma.git
cd oma
git checkout <branch-for-your-mc-version>
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The built JAR is in `build/libs/`.

**Java requirements:**
- `legacy/*` branches: Java **21**
- `master` (26.1.2): Java **25**

## Releases

Releases use **two-part tags** only: `vX.Y` (e.g. `v1.0`).

Pushing a tag triggers CI to build all three Minecraft versions and create a **draft** release containing:

- `oma-X.Y-mc1.21.4.jar`
- `oma-X.Y-mc1.21.11.jar`
- `oma-X.Y-mc26.1.2.jar`

Review the draft on GitHub and publish manually when ready.

## Modules

### Utility
- AutoLog Plus, Auto Shulker, Elytra Swap, Chat Tracker, Player History
- DiscordNotifications, PortalMaker, AntiSpam, Dub Counter, GrimAirPlace, Map Exporter

### Renders
- SignRender, ChestESP, VanityESP, Mob Item ESP, PearlOwner

### Movement
- Elytra Redeploy, Pitch40Util, SearchArea, AFKVanillaFly, GrimScaffold

### Hunting
- StashFinderPlus, NewChunksPlus, TrailFollower (requires Baritone)

### HUD
- TotemCount, CrystalCount, Dub Count, Sign Display, ETA, Lag Detector, EntityList

## Attribution

Based on [cozisAddon](https://github.com/Olrite/cozisAddon) by cozidev / CoziSoftware. Many modules were ported from community addons — see the original README for credits.

## Disclaimer

Review the code yourself before use. This is a utility addon for anarchy servers; use at your own risk.
