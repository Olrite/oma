# oma (Olrite's Meteor Addon)
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
- Auto Rekit - Sorts your inventory to match a saved kit loadout when opening a container or pressing a keybind
- Better Auto Log - Gives more customization for Auto Log
- Auto Shulker - Automatically places and fills Shulker Boxes
- Elytra Swap - Automatically swaps Elytras when durability and certain conditions are met
- Leak Logger - Logs chat messages when possible leaks are messaged
- Render Notifications - Gives notifications and logs when certain items or mobs are rendered
- Discord Notifications - Sends notifications to a Discord webhook
- Portal Maker - Automatically makes and lights a Nether Portal
- Anti Spam - Combines similar chat messages or hides them completely based on provided keywords
- Map Archiver - Saves the map in the user's hand as a png file
- FPS Limiter - Caps FPS when the game window is unfocused, or optionally while other modules are enabled
### Hunting
- Better Stash Finder - Finds stashes in chunks and logs them to a file and/or sends a webhook
- Better New Chunks - Detects new chunks and old chunks in the world
- Trail Follower - Automatically follows trails in all dimensions
- Elytra Plus - Elytra quality-of-life changes
- eBounce - Bounce elyta fly for highway travel, with optional auto XP-bottle elytra repair
- Pitch40 Plus - Auto enables Pitch40, and sets your bounds as you reach highest point each climb
- Area Searcher - Either loads chunks in a rectangle , or spirals endlessly from you
- Smart eFly - Maintains a level Y-flight with fireworks and smooth pitch control
- Portal Skip Detector - Searches loaded chunks for air-pocket patterns that indicate a portal-skip
- Portal Predicter - Scans around a target position for where a Nether portal could spawn
- Sign Render - Detects signs in render distance and outputs their text to chat and HUD
- Item Highlight - Highlights user specified items or illegal/valuable items in open containers
- Item Finder - Highlights containers that contain shulkers or user specified items/blocks
- Decor ESP - Highlights decorative user-placed items
- Misplace ESP - Highlights blocks placed in unnatural rotations or positions
- Mob Dimension - ESP for mobs and animals that do not normally spawn in the current dimension
- Pearl Owner - Displays the name of the player who threw an ender pearl
### HUD
- Totem Count - Displays the count of Totems in the user's inventory
- Crystal Count - Displays the count of Crystals in the user's inventory
- Dub Counter - Counts how many chests are in render distance
- Sign Display - Displays nearby sign text
- ETA - Displays estimated time to get to Baritone goal
- Lag Detector - Detects lagbacks by analyzing the server's TPS and packet timings
- Entity List - Displays nearby entities in a list

## Credits
Credit to [xqyet](https://github.com/xqyet), [Asteroide](https://github.com/asteroide-development), [miles352](https://github.com/miles352), [dekrom](https://github.com/dekrom), and [etianl](https://github.com/etianl) for some of the module bases that I built upon.

## Disclaimer
Review the code yourself before use. This is a utility addon for anarchy servers; use at your own risk.
