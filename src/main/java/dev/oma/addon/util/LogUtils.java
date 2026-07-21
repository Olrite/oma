package dev.oma.addon.util;

import meteordevelopment.meteorclient.mixininterface.IChatHud;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

public class LogUtils {
    protected static Minecraft mc = Minecraft.getInstance();

    public static void info(String txt) {
        assert mc.level != null;

        MutableComponent message = Component.literal("");
        message.append(txt);

        IChatHud chatHud = (IChatHud) mc.inGameHud.getChatHud();
        chatHud.meteor$add(message,0);
    }
}