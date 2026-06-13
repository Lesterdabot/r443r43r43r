package com.hunted.mod.client;

import com.hunted.mod.HuntedMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@OnlyIn(Dist.CLIENT)
public class HuntedHud {

    // Data fed from the server via network packets
    public static boolean eventActive   = false;
    public static boolean isTarget      = false;
    public static String  targetName    = "";
    public static int     targetX       = 0;
    public static int     targetY       = 0;
    public static int     targetZ       = 0;
    public static int     eventSecsLeft = 0;
    public static int     distToTarget  = 0;
    public static String  dirToTarget   = "";

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!eventActive) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        GuiGraphics gfx  = event.getGuiGraphics();
        int screenW      = mc.getWindow().getGuiScaledWidth();

        // Time string
        int mins = eventSecsLeft / 60;
        int secs = eventSecsLeft % 60;
        String timeStr = String.format("%d:%02d", mins, secs);

        // Pulse red when under 5 minutes
        boolean lowTime = eventSecsLeft > 0 && eventSecsLeft <= 300;
        // Flash every 20 ticks (1s) when under 1 min
        boolean flash   = eventSecsLeft <= 60 && (System.currentTimeMillis() / 500) % 2 == 0;

        int bgAlpha = flash ? 180 : 140;
        int textColor;

        // Background panel
        int panelW = 220;
        int panelH = isTarget ? 52 : 46;
        int panelX = (screenW - panelW) / 2;
        int panelY = 4;

        // Dark semi-transparent background
        gfx.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2,
            (bgAlpha << 24) | (lowTime ? 0x300010 : 0x0A0016));

        // Border
        int borderColor = flash ? 0xFFFF2020 : (lowTime ? 0xFFAA0080 : 0xFF7700CC);
        gfx.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY - 1, borderColor);
        gfx.fill(panelX - 2, panelY + panelH + 1, panelX + panelW + 2, panelY + panelH + 2, borderColor);
        gfx.fill(panelX - 2, panelY - 2, panelX - 1, panelY + panelH + 2, borderColor);
        gfx.fill(panelX + panelW + 1, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, borderColor);

        int y = panelY + 4;

        if (isTarget) {
            // === TARGET VIEW ===
            // Big warning header
            String header = flash ? "⚠ YOU ARE THE TARGET ⚠" : "☠ YOU ARE THE TARGET ☠";
            textColor = flash ? 0xFF3333 : 0xFF5555;
            int headerW = mc.font.width(header);
            gfx.drawString(mc.font, header, panelX + (panelW - headerW) / 2, y, textColor, true);
            y += 12;

            // Your coords
            String coordStr = String.format("§7Your pos: §e%d, %d, %d", targetX, targetY, targetZ);
            gfx.drawString(mc.font, Component.literal(coordStr), panelX + 4, y, 0xFFFFFF, true);
            y += 11;

            // Timer
            String timerStr = lowTime
                ? "§c⏱ " + timeStr + " remaining!"
                : "§5⏱ §e" + timeStr + " §7remaining";
            gfx.drawString(mc.font, Component.literal(timerStr), panelX + 4, y, 0xFFFFFF, true);
            y += 11;

            // Hunter count hint
            String huntStr = "§7Everyone can see your location!";
            int hw = mc.font.width(huntStr);
            gfx.drawString(mc.font, Component.literal(huntStr), panelX + (panelW - hw) / 2, y, 0xFFFFFF, false);

        } else if (!targetName.isEmpty()) {
            // === HUNTER VIEW ===
            // Header
            String header = "§5☠ §cTARGET: §f" + targetName + " §5☠";
            int headerW = mc.font.width(header);
            gfx.drawString(mc.font, Component.literal(header), panelX + (panelW - headerW) / 2, y, 0xFFFFFF, true);
            y += 12;

            // Coords + direction
            String coordStr = String.format("§e%d, %d, %d  §7%s  §e%dm away",
                targetX, targetY, targetZ, dirToTarget, distToTarget);
            int coordW = mc.font.width(coordStr);
            gfx.drawString(mc.font, Component.literal(coordStr), panelX + (panelW - coordW) / 2, y, 0xFFFFFF, true);
            y += 12;

            // Timer
            String timerStr = lowTime
                ? "§c⏱ " + timeStr + " — FINISH THEM!"
                : "§5⏱ §e" + timeStr + " §7remaining";
            int timerW = mc.font.width(timerStr);
            gfx.drawString(mc.font, Component.literal(timerStr), panelX + (panelW - timerW) / 2, y, 0xFFFFFF, true);

        } else {
            // Chest not yet claimed
            String header = "§5☠ §eFind the §5Cursed Crown§e! §5☠";
            int headerW = mc.font.width(header);
            gfx.drawString(mc.font, Component.literal(header), panelX + (panelW - headerW) / 2, y, 0xFFFFFF, true);
            y += 12;
            String timerStr = "§5⏱ §e" + timeStr + " §7remaining";
            int timerW = mc.font.width(timerStr);
            gfx.drawString(mc.font, Component.literal(timerStr), panelX + (panelW - timerW) / 2, y, 0xFFFFFF, true);
        }
    }
}
