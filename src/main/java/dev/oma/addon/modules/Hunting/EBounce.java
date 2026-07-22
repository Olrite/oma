package dev.oma.addon.modules.Hunting;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import dev.oma.addon.Main;
import dev.oma.addon.mixin.accessor.PlayerInventoryAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class EBounce extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRepair = settings.createGroup("Auto-Repair");
    private final SettingGroup sgBaritone = settings.createGroup("Baritone Obstacles");

    private final Setting<Boolean> autoSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-sprint")
        .description("Automatically sprint while bouncing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireForward = sgGeneral.add(new BoolSetting.Builder()
        .name("require-forward")
        .description("Only bounce while holding the forward key.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> lockPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-pitch")
        .description("Locks your pitch while bouncing for a consistent glide angle.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("Degrees of pitch to lock to.")
        .defaultValue(65.0)
        .sliderRange(-90.0, 90.0)
        .decimalPlaces(1)
        .visible(lockPitch::get)
        .build()
    );

    private final Setting<Boolean> toggleElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-elytra")
        .description("Equips an elytra from your inventory on activate, and swaps back to a chestplate on deactivate.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> repairElytra = sgRepair.add(new BoolSetting.Builder()
        .name("repair-elytra")
        .description("Uses experience bottles to repair your elytra while bouncing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> damageThreshold = sgRepair.add(new IntSetting.Builder()
        .name("damage-threshold")
        .description("Durability percentage at which repairing starts.")
        .defaultValue(20)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .visible(repairElytra::get)
        .build()
    );

    private final Setting<Integer> repairThreshold = sgRepair.add(new IntSetting.Builder()
        .name("repair-threshold")
        .description("Durability percentage at which repairing stops.")
        .defaultValue(80)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .visible(repairElytra::get)
        .build()
    );

    // Baritone Obstacles

    private final Setting<Boolean> useBaritoneObstacles = sgBaritone.add(new BoolSetting.Builder()
        .name("use-baritone")
        .description("Hands off to Baritone to path around obstacles when bouncing gets stuck.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> stuckTicks = sgBaritone.add(new IntSetting.Builder()
        .name("stuck-ticks")
        .description("Ticks of near-zero horizontal movement before an obstacle is assumed.")
        .defaultValue(40)
        .min(5)
        .sliderRange(5, 200)
        .visible(useBaritoneObstacles::get)
        .build()
    );

    private final Setting<Double> stuckMoveThreshold = sgBaritone.add(new DoubleSetting.Builder()
        .name("stuck-move-threshold")
        .description("Horizontal blocks/tick below which the player is considered stationary.")
        .defaultValue(0.3)
        .min(0.01)
        .sliderRange(0.01, 2.0)
        .visible(useBaritoneObstacles::get)
        .build()
    );

    private final Setting<Integer> obstacleGoalDistance = sgBaritone.add(new IntSetting.Builder()
        .name("obstacle-goal-distance")
        .description("Distance ahead (in the direction you're facing) Baritone paths to when clearing an obstacle.")
        .defaultValue(24)
        .min(5)
        .sliderRange(5, 100)
        .visible(useBaritoneObstacles::get)
        .build()
    );

    private final Setting<Integer> obstacleTimeoutTicks = sgBaritone.add(new IntSetting.Builder()
        .name("obstacle-timeout")
        .description("Max ticks to let Baritone try to clear the obstacle before resuming bouncing anyway.")
        .defaultValue(200)
        .min(20)
        .sliderRange(20, 1200)
        .visible(useBaritoneObstacles::get)
        .build()
    );

    private boolean repairing = false;

    // Baritone obstacle-passing state
    private boolean passingObstacle = false;
    private int stuckTicksCounter = 0;
    private int obstacleTicks = 0;
    private boolean hasLastPos = false;
    private double lastX;
    private double lastZ;
    private double obstacleGoalX;
    private double obstacleGoalZ;
    private Boolean baritoneAvailable = null;

    public EBounce() {
        super(Main.HUNT, "eBounce", "Bounces off the ground with an elytra to travel forward at high speed, with optional auto XP-bottle elytra repair and Baritone obstacle avoidance.");
    }

    @Override
    public void onActivate() {
        repairing = false;
        passingObstacle = false;
        stuckTicksCounter = 0;
        obstacleTicks = 0;
        hasLastPos = false;

        if (toggleElytra.get() && mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
            int slot = findItemSlot(Items.ELYTRA);
            if (slot != -1) {
                equipFromSlot(slot);
            } else {
                warning("No elytra found in inventory!");
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (passingObstacle) cancelBaritone();
        passingObstacle = false;

        if (toggleElytra.get() && mc.player != null && mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            int slot = findBestChestplateSlot();
            if (slot != -1) equipFromSlot(slot);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) return;

        if (repairElytra.get()) {
            if (repairing) {
                tickRepair();
                return;
            } else if (shouldStartRepair()) {
                repairing = true;
                return;
            }
        }

        if (passingObstacle) {
            tickObstacle();
            return;
        }

        if (requireForward.get() && !mc.options.keyUp.isDown()) {
            hasLastPos = false;
            stuckTicksCounter = 0;
            return;
        }

        if (useBaritoneObstacles.get() && isBaritoneLoaded()) {
            updateStuckTracking();
            if (stuckTicksCounter >= stuckTicks.get()) {
                enterObstacleState();
                return;
            }
        }

        doBounce();
    }

    private void doBounce() {
        if (autoSprint.get()) mc.player.setSprinting(true);
        if (lockPitch.get()) mc.player.setXRot(pitch.get().floatValue());
        if (mc.player.onGround()) mc.player.jumpFromGround();

        mc.player.connection.send(
            new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
        );
    }

    // ----------------------------------------------------------------------
    // Baritone obstacle passing
    // ----------------------------------------------------------------------

    private void updateStuckTracking() {
        double x = mc.player.getX();
        double z = mc.player.getZ();
        if (hasLastPos) {
            double moved = Math.hypot(x - lastX, z - lastZ);
            stuckTicksCounter = moved < stuckMoveThreshold.get() ? stuckTicksCounter + 1 : 0;
        }
        lastX = x;
        lastZ = z;
        hasLastPos = true;
    }

    private void enterObstacleState() {
        try {
            Vec3 look = mc.player.getLookAngle();
            double len = Math.hypot(look.x, look.z);
            double dirX = len > 1.0e-6 ? look.x / len : 0;
            double dirZ = len > 1.0e-6 ? look.z / len : 1;

            obstacleGoalX = mc.player.getX() + dirX * obstacleGoalDistance.get();
            obstacleGoalZ = mc.player.getZ() + dirZ * obstacleGoalDistance.get();

            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                .setGoalAndPath(new GoalXZ((int) obstacleGoalX, (int) obstacleGoalZ));

            passingObstacle = true;
            obstacleTicks = 0;
            stuckTicksCounter = 0;
            hasLastPos = false;
            info("Obstacle detected, handing off to Baritone.");
        } catch (Exception e) {
            stuckTicksCounter = 0;
        }
    }

    private void tickObstacle() {
        obstacleTicks++;

        double dist = Math.hypot(obstacleGoalX - mc.player.getX(), obstacleGoalZ - mc.player.getZ());
        if (dist <= 3.0 || obstacleTicks >= obstacleTimeoutTicks.get()) {
            cancelBaritone();
            passingObstacle = false;
            stuckTicksCounter = 0;
            hasLastPos = false;
        }
    }

    private void cancelBaritone() {
        if (!isBaritoneLoaded()) return;
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("cancel");
        } catch (Exception ignored) { }
    }

    private boolean isBaritoneLoaded() {
        if (baritoneAvailable == null) {
            try {
                Class.forName("baritone.api.BaritoneAPI");
                baritoneAvailable = true;
            } catch (ClassNotFoundException e) {
                baritoneAvailable = false;
            }
        }
        return baritoneAvailable;
    }

    @Override
    public String getInfoString() {
        return passingObstacle ? "Obstacle" : null;
    }

    // ----------------------------------------------------------------------
    // Auto-repair (existing feature)
    // ----------------------------------------------------------------------

    private float elytraDurabilityPct() {
        ItemStack stack = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        return (1.0f - (float) stack.getDamageValue() / stack.getMaxDamage()) * 100.0f;
    }

    private boolean shouldStartRepair() {
        return elytraDurabilityPct() <= damageThreshold.get();
    }

    private void tickRepair() {
        if (elytraDurabilityPct() >= repairThreshold.get()) {
            repairing = false;
            return;
        }

        FindItemResult xp = InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE);
        if (!xp.found()) {
            int slot = findItemSlot(Items.EXPERIENCE_BOTTLE);
            if (slot == -1) {
                warning("No experience bottles found, waiting...");
                return;
            }
            InvUtils.move().from(slot).toHotbar(mc.player.getInventory().getSelectedSlot());
            return;
        }

        InvUtils.swap(xp.slot(), true);
        mc.player.setXRot(90.0f);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        InvUtils.swapBack();
    }

    private void equipFromSlot(int slot) {
        if (slot < 0) return;

        int workingSlot = slot;
        boolean movedToHotbar = false;
        ItemStack hotbarOriginal = ItemStack.EMPTY;

        if (slot >= 9) {
            int hotbarSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).isEmpty()) {
                    hotbarSlot = i;
                    break;
                }
            }
            if (hotbarSlot == -1) hotbarSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();

            hotbarOriginal = mc.player.getInventory().getItem(hotbarSlot).copy();
            InvUtils.move().from(slot).toHotbar(hotbarSlot);
            movedToHotbar = true;
            workingSlot = hotbarSlot;
        }

        InvUtils.swap(workingSlot, false);
        mc.options.keyUse.setDown(true);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.options.keyUse.setDown(false);
        InvUtils.swapBack();

        if (movedToHotbar && !hotbarOriginal.isEmpty()) {
            InvUtils.move().fromHotbar(workingSlot).to(slot);
        }
    }

    private int findItemSlot(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    private int findBestChestplateSlot() {
        int bestSlot = -1;
        int bestValue = 0;
        for (int i = 0; i < 36; i++) {
            int value = chestplateValue(mc.player.getInventory().getItem(i).getItem());
            if (value > bestValue) {
                bestValue = value;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int chestplateValue(Item item) {
        if (item == Items.NETHERITE_CHESTPLATE) return 6;
        if (item == Items.DIAMOND_CHESTPLATE) return 5;
        if (item == Items.IRON_CHESTPLATE) return 4;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 3;
        if (item == Items.GOLDEN_CHESTPLATE) return 2;
        if (item == Items.LEATHER_CHESTPLATE) return 1;
        return 0;
    }
}
