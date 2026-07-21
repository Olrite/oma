package dev.oma.addon;

import com.mojang.logging.LogUtils;
import dev.oma.addon.hud.EntityList;
import dev.oma.addon.hud.TotemCount;
import dev.oma.addon.hud.CrystalCount;
import dev.oma.addon.hud.DubCountGUI;
import dev.oma.addon.hud.SignDisplay;
import dev.oma.addon.hud.ETA;
import dev.oma.addon.hud.LagDetector;
import dev.oma.addon.modules.Hunting.NewChunksPlus;
import dev.oma.addon.modules.Movement.AFKVanillaFly;
import dev.oma.addon.modules.Movement.ElytraRedeploy;
import dev.oma.addon.modules.Movement.GrimScaffold;
import dev.oma.addon.modules.Utility.GrimAirPlace;
import dev.oma.addon.modules.Hunting.StashFinderPlus;
import dev.oma.addon.modules.Hunting.TrailFollower;
import dev.oma.addon.modules.Movement.Pitch40Util;
import dev.oma.addon.modules.Movement.searcharea.SearchArea;
import dev.oma.addon.modules.Render.ChestESP;
import dev.oma.addon.modules.Render.ModItemESP;
import dev.oma.addon.modules.Render.PearlOwner;
import dev.oma.addon.modules.Render.SignRender;
import dev.oma.addon.modules.Render.VanityESP;
import dev.oma.addon.modules.Utility.AntiSpam;
import dev.oma.addon.modules.Utility.AutoLogPlus;
import dev.oma.addon.modules.Utility.AutoShulker;
import dev.oma.addon.modules.Utility.DiscordNotifications;
import dev.oma.addon.modules.Utility.DubCount;
import dev.oma.addon.modules.Utility.PortalMaker;
import dev.oma.addon.modules.Utility.PlayerHistory;
import dev.oma.addon.modules.Utility.ChatTracker;
import dev.oma.addon.modules.Utility.MapExporter;
import dev.oma.addon.modules.Utility.ElytraSwap;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Main extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category UTILS = new Category("omaUtil");
    public static final Category RENDER = new Category("omaRender");
    public static final Category MOVEMENT = new Category("omaMovement");
    public static final Category HUNTING = new Category("omaHunting");
    public static final HudGroup HUD_GROUP = new HudGroup("oma");


    @Override
    public void onInitialize() {
        LOG.info("Initializing o.m.a.");

        // Modules
         Modules.get().add(new PortalMaker());
         Modules.get().add(new DiscordNotifications());
         Modules.get().add(new StashFinderPlus());
         Modules.get().add(new Pitch40Util());
         Modules.get().add(new NewChunksPlus());
         Modules.get().add(new ChestESP());
         Modules.get().add(new ModItemESP());
         Modules.get().add(new PearlOwner());
         Modules.get().add(new SignRender());
         Modules.get().add(new VanityESP());
         Modules.get().add(new SearchArea());
         Modules.get().add(new AntiSpam());
         Modules.get().add(new AutoLogPlus());
         Modules.get().add(new AFKVanillaFly());
         Modules.get().add(new AutoShulker());
         Modules.get().add(new ElytraRedeploy());
         Modules.get().add(new DubCount());
         Modules.get().add(new GrimScaffold());
         Modules.get().add(new GrimAirPlace());
         Modules.get().add(new PlayerHistory());
         Modules.get().add(new ChatTracker());
         Modules.get().add(new MapExporter());
         Modules.get().add(new ElytraSwap());
         
         // Only add modules that require Baritone if Baritone is available
         try {
             Class.forName("baritone.api.BaritoneAPI");;
             Modules.get().add(new TrailFollower());
             LOG.info("TrailFollower loaded (Baritone detected)");
         } catch (ClassNotFoundException e) {
             LOG.info("TrailFollower not loaded (Baritone not found)");
         }
         
        // Commands
        // Commands.add(new CommandExample());

        // HUD
        Hud.get().register(EntityList.INFO);
        Hud.get().register(TotemCount.INFO);
        Hud.get().register(CrystalCount.INFO);
        Hud.get().register(DubCountGUI.INFO);
        Hud.get().register(SignDisplay.INFO);
        Hud.get().register(ETA.INFO);
        Hud.get().register(LagDetector.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(UTILS);
        Modules.registerCategory(RENDER);
        Modules.registerCategory(MOVEMENT);
        Modules.registerCategory(HUNTING);
    }

    @Override
    public String getPackage() {
        return "dev.oma.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Olrite", "oma");
    }
}
