# o.m.a. (Olrite's Meteor Addon)

A 2b2t Meteor Client addon mainly for basehunting and quality of life improvements.

## Supported Minecraft versions


| Branch           | Minecraft   | Meteor Client                                                        |
| ---------------- | ----------- | -------------------------------------------------------------------- |
| `master`         | **26.1.2**  | [Latest](https://meteorclient.com/)                                  |
| `legacy/1.21.11` | **1.21.11** | [Archive #85](https://meteorclient.com/api/download?version=1.21.11) |
| `legacy/1.21.4`  | **1.21.4**  | [Archive #42](https://meteorclient.com/api/download?version=1.21.4)  |


Check out the branch matching your Minecraft version before building/installing.

## Install (Pre-Built JAR)

Download the JAR for your Minecraft version from [GitHub Releases](https://github.com/Olrite/oma/releases) and place the matching JAR in your `mods` folder alongside the correct Meteor Client version.

## Build from source

```bash
git clone https://github.com/Olrite/oma.git
cd oma
git checkout <branch-for-your-mc-version>
```

On Linux:

```bash
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

# Modules
### Utility
- Better Auto Log, Auto Shulker, Elytra Swap, Chat Tracker, Player History, DiscordNotifications, PortalMaker, AntiSpam, Dub Counter, GrimAirPlace, Map Exporter

### Renders
- SignRender, ChestESP, DecorESP, MobItemESP, PearlOwner

### Movement
- Auto eFly, Auto Pitch40, SearchArea, Smart eFly, GrimScaffold

### Hunting
- Better Stash Finder, Better New Chunks, TrailFollower (requires Baritone)

### HUD
- TotemCount, CrystalCount, Dub Count, Sign Display, ETA, Lag Detector, EntityList

## Credits
Credit to [xqyet](https://github.com/xqyet), [Asteroide](https://github.com/asteroide-development), [miles352](https://github.com/miles352), [dekrom](https://github.com/dekrom), and [etianl](https://github.com/etianl) for some of the modules that I built upon.

## Disclaimer
Review the code yourself before use. This is a utility addon for anarchy servers; use at your own risk.