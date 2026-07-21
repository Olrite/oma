package dev.oma.addon.util;

import meteordevelopment.meteorclient.mixininterface.IChatHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class LogUtils {
    protected static MinecraftClient mc = MinecraftClient.getInstance();

    public static void info(String txt) {
        assert mc.world != null;

        MutableText message = Text.literal("");
        message.append(txt);

        IChatHud chatHud = (IChatHud) mc.inGameHud.getChatHud();
        chatHud.meteor$add(message,0);
    }
}