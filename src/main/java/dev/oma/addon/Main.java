package dev.oma.addon;

import com.mojang.logging.LogUtils;
import dev.oma.addon.modules.HUD.CrystalCount;
import dev.oma.addon.modules.HUD.DubCountGUI;
import dev.oma.addon.modules.HUD.ETA;
import dev.oma.addon.modules.HUD.EntityList;
import dev.oma.addon.modules.HUD.LagDetector;
import dev.oma.addon.modules.HUD.SignDisplay;
import dev.oma.addon.modules.HUD.TotemCount;
import dev.oma.addon.modules.Hunting.Pitch40Plus;
import dev.oma.addon.modules.Hunting.BetterNewChunks;
import dev.oma.addon.modules.Hunting.BetterStashFinder;
import dev.oma.addon.modules.Hunting.DecorESP;
import dev.oma.addon.modules.Hunting.DubCount;
import dev.oma.addon.modules.Hunting.EBounce;
import dev.oma.addon.modules.Hunting.ElytraPlus;
import dev.oma.addon.modules.Hunting.ItemFinder;
import dev.oma.addon.modules.Hunting.ItemHighlight;
import dev.oma.addon.modules.Hunting.MobDimension;
import dev.oma.addon.modules.Hunting.PearlOwner;
import dev.oma.addon.modules.Hunting.PortalPredicter;
import dev.oma.addon.modules.Hunting.PortalSkipDetector;
import dev.oma.addon.modules.Hunting.SmartEFly;
import dev.oma.addon.modules.Hunting.TrailFollower;
import dev.oma.addon.modules.Hunting.WeirdBlockESP;
import dev.oma.addon.modules.Hunting.searcharea.SearchArea;
import dev.oma.addon.modules.Utility.AntiSpam;
import dev.oma.addon.modules.Utility.AutoRekit;
import dev.oma.addon.modules.Utility.BetterAutoLog;
import dev.oma.addon.modules.Utility.AutoShulker;
import dev.oma.addon.modules.Utility.DiscordNotifications;
import dev.oma.addon.modules.Utility.PortalMaker;
import dev.oma.addon.modules.Utility.RenderNotifications;
import dev.oma.addon.modules.Utility.LeakLogger;
import dev.oma.addon.modules.Utility.MapArchiver;
import dev.oma.addon.modules.Utility.ElytraSwap;
import dev.oma.addon.modules.Hunting.SignRender;
import dev.oma.addon.modules.Utility.FpsLimiter;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Main extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category MOD = new Category("omaUtil");
    public static final Category HUNT = new Category("omaHunt");
    public static final HudGroup HUD_GROUP = new HudGroup("oma");


    @Override
    public void onInitialize() {
        LOG.info("Initializing oma.");

        // Modules
         Modules.get().add(new PortalMaker());
         Modules.get().add(new DiscordNotifications());
         Modules.get().add(new BetterStashFinder());
         Modules.get().add(new PortalSkipDetector());
         Modules.get().add(new PortalPredicter());
         Modules.get().add(new Pitch40Plus());
         Modules.get().add(new BetterNewChunks());
         Modules.get().add(new ItemFinder());
         Modules.get().add(new MobDimension());
         Modules.get().add(new PearlOwner());
         Modules.get().add(new SignRender());
         Modules.get().add(new DecorESP());
         Modules.get().add(new WeirdBlockESP());
         Modules.get().add(new SearchArea());
         Modules.get().add(new AntiSpam());
         Modules.get().add(new BetterAutoLog());
         Modules.get().add(new SmartEFly());
         Modules.get().add(new AutoShulker());
         Modules.get().add(new ElytraPlus());
         Modules.get().add(new EBounce());
         Modules.get().add(new DubCount());
         Modules.get().add(new RenderNotifications());
         Modules.get().add(new LeakLogger());
         Modules.get().add(new MapArchiver());
         Modules.get().add(new ElytraSwap());
         Modules.get().add(new ItemHighlight());
         Modules.get().add(new AutoRekit());
         Modules.get().add(new FpsLimiter());
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
        Modules.registerCategory(MOD);
        Modules.registerCategory(HUNT);
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
