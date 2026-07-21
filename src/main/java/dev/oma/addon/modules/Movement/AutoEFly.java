package dev.oma.addon.modules.Movement;

import dev.oma.addon.Main;
import dev.oma.addon.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

public class AutoEFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("Delay in ticks before redeploying the elytra after landing.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> requireElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("require-elytra")
        .description("Only redeploy if an elytra is equipped in the chest slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireForwardMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("require-forward-movement")
        .description("Only redeploy if the player is moving forward.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> useFirework = sgGeneral.add(new BoolSetting.Builder()
        .name("use-firework")
        .description("Automatically use a firework rocket after elytra activates.")
        .defaultValue(true)
        .build()
    );

    private boolean wasFlying = false;
    private int tickCounter = 0;
    private boolean waitingToActivate = false;
    private boolean needsFirework = false;

    public AutoEFly() {
        super(Main.MOVEMENT, "Auto eFly", "Automatically jumps and redeploys elytra when touching the ground.");
    }

    @Override
    public void onActivate() {
        wasFlying = false;
        tickCounter = 0;
        waitingToActivate = false;
        needsFirework = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // Check if player has elytra equipped
        boolean hasElytra = mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
        
        if (requireElytra.get() && !hasElytra) {
            wasFlying = false;
            return;
        }

        boolean isCurrentlyFlying = mc.player.isFallFlying();
        boolean isOnGround = mc.player.onGround();

        // Track if player was flying
        if (isCurrentlyFlying) {
            wasFlying = true;
            tickCounter = 0;
            waitingToActivate = false;
            
            // Use firework if needed
            if (needsFirework && useFirework.get()) {
                Utils.firework(mc, false);
                needsFirework = false;
            }
        }

        // If waiting to activate and now in the air, send the activation packet
        if (waitingToActivate && !isOnGround && hasElytra) {
            mc.player.connection.send(
                new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
            );
            waitingToActivate = false;
            wasFlying = false;
            tickCounter = 0;
            needsFirework = true; // Mark that we need to use a firework once gliding starts
        }

        // If player was flying, is now on ground, and not currently flying
        if (wasFlying && isOnGround && !isCurrentlyFlying && !waitingToActivate) {
            // Check forward movement requirement
            if (requireForwardMovement.get()) {
                boolean isMovingForward = mc.options.keyUp.isDown();
                if (!isMovingForward) {
                    wasFlying = false;
                    return;
                }
            }

            tickCounter++;
            
            if (tickCounter >= tickDelay.get()) {
                // Jump and mark as waiting to activate
                mc.player.jumpFromGround();
                waitingToActivate = true;
                tickCounter = 0;
            }
        } else if (!isOnGround && !waitingToActivate) {
            // Reset counter if player is in the air but not in our activation flow
            tickCounter = 0;
        }
    }
}
