### This project has been archived. I haven't been on 2b2t for a while now and no longer have motivation to continue this project due to some recent personal events. You can see some other Meteor Client addons [here](https://anticope.pages.dev/addons/?). Thank you everyone for downloading and showing support for this addon! <3

# cozisAddon
A 2b2t Meteor Client utility based addon.

### Original Modules
**<ins>Utility</ins>**
+ AutoLog Plus - A module that disconnects the player according to specific parameters (Based off of [Numby-hack](https://github.com/cqb13/Numby-hack))
+ Auto Shulker - When your inventory is full it places and opens a shulker box and then puts your items in the shulker box
+ Elytra Swap - When your equipped elytra reaches a configurable durability threshold, it swaps to a new one
+ Chat Tracker - Logs chat to a file with optional filtering
+ Player History - Logs player information when they are spotted

**<ins>Renders</ins>**
+ SignRender - Displays sign text above the sign and is visible through walls
+ ChestESP - An ESP for chests/double chests that have shulkers inside them and has a memory of chests in render distance
+ VanityESP - An ESP for vanity items such as banners and item frames
+ Mob Item ESP - An ESP for mobs that spawn with items that they normally wouldn't spawn with

**<ins>Movement</ins>**
+ Elytra Redeploy - Jumps and flies away if you hit the ground with this enabled

**<ins>HUD</ins>**
+ TotemCount - A HUD module that shows the amount of totems in your inventory
+ CrystalCount - A HUD module that shows the amount of end crystals in your inventory
+ Dub Count - A HUD module that shows what is output from the Dub Counter module 
+ Sign Display - A HUD module that displays all signs within render distance in a list
+ ETA - Displays the estimated time left to reach the baritone goal/goal in GUI based on current speed
+ Lag Detector - Displays the server's TPS and if there are lagbacks also shows a lag warning when lagging

### Ported Modules
**<ins>Utility</ins>**
+ DiscordNotifications - ported from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon) - **I added the ability test the Discord webhook**
+ PortalMaker - originally from [xqyet](https://github.com/xqyet)
+ AntiSpam - ported from [Asteroide](https://github.com/asteroide-development/Asteroide)
+ Dub Counter - ported from [IKEA](https://github.com/Nooniboi/Public-Ikea) - **I changed this module a bit to allow the HUD element to work properly**
+ GrimAirPlace - ported from [Jeff Mod](https://github.com/miles352/meteor-stashhunting-addon)
+ Map Exporter - originally from [VexTheIV](https://github.com/Vextheiv)

**<ins>Renders</ins>**
+ PearlOwner - ported from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon)

**<ins>Hunting</ins>**
+ StashFinderPlus - ported from [Jeff Mod](https://github.com/miles352/meteor-stashhunting-addon) - **I added the ability to bulk add potential stashes to waypoint, auto-disconnect when a stash is found and added more information when a Discord webhook is sent**
+ NewChunksPlus - ported from [Trouser Streak](https://github.com/etianl/Trouser-Streak)
+ TrailFollower - originally from [WarriorLost](https://github.com/WarriorLost) - **I added an option to auto disconnect depending on chunk loading speeds and a cardinal direction priority mode**

**<ins>Movement</ins>**
+ Pitch40Util - ported from [Jeff Mod](https://github.com/miles352/meteor-stashhunting-addon)
+ SearchArea - ported from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon)
+ AFKVanillaFly - originally from [xqyet](https://github.com/xqyet)
+ GrimScaffold - ported from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon)

**<ins>HUD</ins>**
+ EntityList - ported from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon)

#### Manual Build
```bash
git clone https://github.com/CoziSoftware/cozisAddon/
cd cozisAddon/
./gradlew build
```

**Disclaimer:** This code is safe however I wouldn't trust what you download from a 2b2t player so if you feel inclined, please look over the codebase to ensure your own security.
