package dev.oma.addon.modules.Hunting;

import dev.oma.addon.Main;
import dev.oma.addon.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ElytraPlus extends Module {
    private enum OnLanding {
        None,
        RedeployElytra,
        EquipChestplate
    }

    private enum PendingEquip {
        None,
        ElytraForLaunch,
        ChestplateForLanding,
        ElytraForLiftoff
    }

    private enum LiftoffStage {
        Idle,
        Jump,
        WaitForApex
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDoubleTap = settings.createGroup("Double-Tap Launch");
    private final SettingGroup sgRocket = settings.createGroup("Rocket Keybind");
    private final SettingGroup sgLiftoff = settings.createGroup("Auto Liftoff");

    private final Setting<OnLanding> onLanding = sgGeneral.add(new EnumSetting.Builder<OnLanding>()
        .name("on-landing")
        .description("What to do automatically when you land on the ground after gliding.")
        .defaultValue(OnLanding.RedeployElytra)
        .build()
    );

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("Delay in ticks before redeploying the elytra after landing.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .visible(() -> onLanding.get() == OnLanding.RedeployElytra)
        .build()
    );

    private final Setting<Boolean> requireForwardMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("require-forward-movement")
        .description("Only redeploy if the player is moving forward.")
        .defaultValue(false)
        .visible(() -> onLanding.get() == OnLanding.RedeployElytra)
        .build()
    );

    private final Setting<Boolean> useFirework = sgGeneral.add(new BoolSetting.Builder()
        .name("use-firework")
        .description("Automatically use a firework rocket after elytra activates.")
        .defaultValue(true)
        .visible(() -> onLanding.get() == OnLanding.RedeployElytra)
        .build()
    );


    private final Setting<Boolean> doubleTapEquip = sgDoubleTap.add(new BoolSetting.Builder()
        .name("double-jump-equip")
        .description("Double-tap jump while airborne to equip an elytra from your inventory and start gliding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> doubleTapWindow = sgDoubleTap.add(new IntSetting.Builder()
        .name("double-tap-window")
        .description("Max time in milliseconds between jumps to count as a double-tap.")
        .defaultValue(300)
        .min(50)
        .sliderRange(50, 1000)
        .visible(doubleTapEquip::get)
        .build()
    );


    private final Setting<Keybind> fireRocketKey = sgRocket.add(new KeybindSetting.Builder()
        .name("fire-rocket-keybind")
        .description("Fires a firework rocket while gliding when pressed. Does nothing if there is already an active rocket.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Integer> rocketCooldownTicks = sgRocket.add(new IntSetting.Builder()
        .name("rocket-cooldown")
        .description("Ticks to wait between rockets fired with the keybind.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 100)
        .visible(() -> !fireRocketKey.get().equals(Keybind.none()))
        .build()
    );


    private final Setting<Keybind> liftoffKey = sgLiftoff.add(new KeybindSetting.Builder()
        .name("liftoff-keybind")
        .description("Equips an elytra and a firework, jumps, opens the elytra wings at the apex of the jump, then fires a rocket to launch into the sky.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Boolean> liftoffAutoEquipFirework = sgLiftoff.add(new BoolSetting.Builder()
        .name("auto-equip-firework")
        .description("Moves a firework rocket into your hotbar if you don't already have one there.")
        .defaultValue(true)
        .visible(() -> !liftoffKey.get().equals(Keybind.none()))
        .build()
    );

    private final Setting<Boolean> liftoffUseFirework = sgLiftoff.add(new BoolSetting.Builder()
        .name("use-firework")
        .description("Fires a firework rocket as soon as the elytra wings open.")
        .defaultValue(true)
        .visible(() -> !liftoffKey.get().equals(Keybind.none()))
        .build()
    );

    private boolean wasFlying = false;
    private int tickCounter = 0;
    private boolean waitingToActivate = false;
    private boolean needsFirework = false;

    private boolean wasJumpPressed = false;
    private long lastJumpPressTime = 0L;

    private PendingEquip pendingEquip = PendingEquip.None;
    private int equipStage = 0;
    private int equipStageTimer = 0;
    private int equipWorkingSlot = -1;
    private int equipOriginalSlot = -1;
    private int equipHotbarSlot = -1;
    private ItemStack equipHotbarOriginalItem = ItemStack.EMPTY;

    private int rocketCooldown = 0;

    private LiftoffStage liftoffStage = LiftoffStage.Idle;
    private double lastLiftoffMotionY = 0;
    private boolean liftoffNeedsFirework = false;
    private boolean wasLiftoffKeyPressed = false;
    private int liftoffTimeoutTicks = 0;

    public ElytraPlus() {
        super(Main.HUNT, "Elytra Plus", "Elytra quality-of-life: auto-redeploy after landing, double-tap-jump to equip and launch, a rocket keybind, and an auto-liftoff keybind.");
    }

    @Override
    public void onActivate() {
        wasFlying = false;
        tickCounter = 0;
        waitingToActivate = false;
        needsFirework = false;
        wasJumpPressed = false;
        lastJumpPressTime = 0L;
        resetEquipState();
        rocketCooldown = 0;
        liftoffStage = LiftoffStage.Idle;
        lastLiftoffMotionY = 0;
        liftoffNeedsFirework = false;
        wasLiftoffKeyPressed = false;
        liftoffTimeoutTicks = 0;
    }

    private void resetEquipState() {
        pendingEquip = PendingEquip.None;
        equipStage = 0;
        equipStageTimer = 0;
        equipWorkingSlot = -1;
        equipOriginalSlot = -1;
        equipHotbarSlot = -1;
        equipHotbarOriginalItem = ItemStack.EMPTY;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        boolean hasElytra = mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
        boolean isCurrentlyFlying = mc.player.isFallFlying();
        boolean isOnGround = mc.player.onGround();
        boolean jumpPressed = mc.options.keyJump.isDown();
        boolean jumpJustPressed = jumpPressed && !wasJumpPressed;

        if (pendingEquip != PendingEquip.None) {
            tickEquip();
        }

        if (isCurrentlyFlying) {
            wasFlying = true;
            tickCounter = 0;
            waitingToActivate = false;

            if (needsFirework && useFirework.get()) {
                Utils.firework(mc, false);
                needsFirework = false;
            }

            if (liftoffNeedsFirework) {
                Utils.firework(mc, false);
                liftoffNeedsFirework = false;
            }
        }

        boolean liftoffKeyPressed = !liftoffKey.get().equals(Keybind.none()) && liftoffKey.get().isPressed();
        if (liftoffKeyPressed && !wasLiftoffKeyPressed && liftoffStage == LiftoffStage.Idle
            && pendingEquip == PendingEquip.None && isOnGround && !isCurrentlyFlying) {
            startLiftoff();
        }
        wasLiftoffKeyPressed = liftoffKeyPressed;

        if (liftoffStage != LiftoffStage.Idle) {
            tickLiftoff(isOnGround);
        }

        if (doubleTapEquip.get() && jumpJustPressed) {
            long now = System.currentTimeMillis();
            if (!isOnGround && !isCurrentlyFlying && now - lastJumpPressTime < doubleTapWindow.get()
                && !hasElytra && pendingEquip == PendingEquip.None) {
                int slot = findItemSlot(Items.ELYTRA);
                if (slot != -1) beginEquip(slot, PendingEquip.ElytraForLaunch);
            }
            lastJumpPressTime = now;
        }

        if (waitingToActivate && !isOnGround && hasElytra) {
            mc.player.connection.send(
                new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
            );
            waitingToActivate = false;
            wasFlying = false;
            tickCounter = 0;
            needsFirework = true;
        }

        if (wasFlying && isOnGround && !isCurrentlyFlying && !waitingToActivate && pendingEquip == PendingEquip.None) {
            switch (onLanding.get()) {
                case RedeployElytra -> {
                    if (!hasElytra) {
                        wasFlying = false;
                    } else {
                        if (requireForwardMovement.get() && !mc.options.keyUp.isDown()) {
                            wasFlying = false;
                        } else {
                            tickCounter++;
                            if (tickCounter >= tickDelay.get()) {
                                mc.player.jumpFromGround();
                                waitingToActivate = true;
                                tickCounter = 0;
                            }
                        }
                    }
                }
                case EquipChestplate -> {
                    wasFlying = false;
                    if (hasElytra) {
                        int slot = findBestChestplateSlot();
                        if (slot != -1) beginEquip(slot, PendingEquip.ChestplateForLanding);
                    }
                }
                case None -> wasFlying = false;
            }
        } else if (!isOnGround && !waitingToActivate) {
            tickCounter = 0;
        }

        if (hasElytra && isCurrentlyFlying) {
            if (rocketCooldown > 0) rocketCooldown--;
            if (!fireRocketKey.get().equals(Keybind.none()) && fireRocketKey.get().isPressed() && rocketCooldown <= 0) {
                Utils.firework(mc, false);
                rocketCooldown = rocketCooldownTicks.get();
            }
        }

        wasJumpPressed = jumpPressed;
    }

    private void beginEquip(int slot, PendingEquip action) {
        pendingEquip = action;
        equipOriginalSlot = slot;
        equipWorkingSlot = slot;
        equipHotbarSlot = -1;
        equipHotbarOriginalItem = ItemStack.EMPTY;
        equipStage = 1;
        equipStageTimer = 0;
    }

    private void tickEquip() {
        equipStageTimer++;
        if (equipStageTimer < 2) return;

        if (equipStageTimer > 60) {
            resetEquipState();
            return;
        }

        switch (equipStage) {
            case 1 -> {
                if (equipWorkingSlot >= 9) {
                    int hotbarSlot = -1;
                    for (int i = 0; i < 9; i++) {
                        if (mc.player.getInventory().getItem(i).isEmpty()) {
                            hotbarSlot = i;
                            break;
                        }
                    }
                    if (hotbarSlot == -1) hotbarSlot = mc.player.getInventory().getSelectedSlot();

                    equipHotbarOriginalItem = mc.player.getInventory().getItem(hotbarSlot).copy();
                    equipHotbarSlot = hotbarSlot;
                    InvUtils.move().from(equipWorkingSlot).toHotbar(hotbarSlot);
                    equipWorkingSlot = hotbarSlot;
                } else {
                    equipHotbarSlot = equipWorkingSlot;
                    equipHotbarOriginalItem = ItemStack.EMPTY;
                }
                equipStage = 2;
                equipStageTimer = 0;
            }
            case 2 -> {
                InvUtils.swap(equipWorkingSlot, false);
                mc.options.keyUse.setDown(true);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.options.keyUse.setDown(false);
                InvUtils.swapBack();
                equipStage = 3;
                equipStageTimer = 0;
            }
            case 3 -> {
                if (equipOriginalSlot >= 9 && !equipHotbarOriginalItem.isEmpty()) {
                    InvUtils.move().fromHotbar(equipHotbarSlot).to(equipOriginalSlot);
                }
                finishEquip();
            }
        }
    }

    private void finishEquip() {
        PendingEquip action = pendingEquip;
        resetEquipState();

        if (action == PendingEquip.ElytraForLaunch) {
            if (mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                mc.player.connection.send(
                    new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
                );
                wasFlying = false;
                tickCounter = 0;
                needsFirework = true;
            }
        } else if (action == PendingEquip.ChestplateForLanding) {
            info("Equipped chestplate after landing.");
        } else if (action == PendingEquip.ElytraForLiftoff) {
            if (mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                advanceLiftoffAfterElytraEquip();
            } else {
                info("Auto Liftoff: failed to equip an elytra.");
            }
        }
    }

    private void startLiftoff() {
        if (mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            advanceLiftoffAfterElytraEquip();
            return;
        }

        int slot = findItemSlot(Items.ELYTRA);
        if (slot == -1) {
            info("Auto Liftoff: no elytra found in inventory.");
            return;
        }
        beginEquip(slot, PendingEquip.ElytraForLiftoff);
    }

    private void advanceLiftoffAfterElytraEquip() {
        if (liftoffAutoEquipFirework.get()) ensureFireworkInHotbar();
        liftoffStage = LiftoffStage.Jump;
        liftoffTimeoutTicks = 0;
    }

    private void ensureFireworkInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.FIREWORK_ROCKET) return;
        }

        int slot = findItemSlot(Items.FIREWORK_ROCKET);
        if (slot == -1 || slot < 9) return;

        int hotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                hotbarSlot = i;
                break;
            }
        }
        if (hotbarSlot == -1) hotbarSlot = mc.player.getInventory().getSelectedSlot();

        InvUtils.move().from(slot).toHotbar(hotbarSlot);
    }

    private void tickLiftoff(boolean isOnGround) {
        // Safety timeout in case the apex is somehow never detected (e.g. jump blocked by a ceiling).
        if (++liftoffTimeoutTicks > 100) {
            liftoffStage = LiftoffStage.Idle;
            return;
        }

        switch (liftoffStage) {
            case Jump -> {
                if (isOnGround) {
                    mc.player.jumpFromGround();
                    lastLiftoffMotionY = mc.player.getDeltaMovement().y;
                    liftoffStage = LiftoffStage.WaitForApex;
                }
            }
            case WaitForApex -> {
                double motionY = mc.player.getDeltaMovement().y;
                if (lastLiftoffMotionY > 0 && motionY <= 0) {
                    mc.player.connection.send(
                        new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
                    );
                    wasFlying = false;
                    tickCounter = 0;
                    liftoffNeedsFirework = liftoffUseFirework.get();
                    liftoffStage = LiftoffStage.Idle;
                } else {
                    lastLiftoffMotionY = motionY;
                }
            }
            case Idle -> {}
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
