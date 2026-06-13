package com.hunted.mod.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class HuntedConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue    PREP_TIME_SECONDS;
    public static final ModConfigSpec.IntValue    BROADCAST_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue    EVENT_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue    CHEST_SPAWN_RADIUS;
    public static final ModConfigSpec.ConfigValue<String> MSG_EVENT_START;
    public static final ModConfigSpec.ConfigValue<String> MSG_CHEST_SPAWNED;
    public static final ModConfigSpec.ConfigValue<String> MSG_TARGET_ACQUIRED;
    public static final ModConfigSpec.ConfigValue<String> MSG_COORDS_BROADCAST;
    public static final ModConfigSpec.ConfigValue<String> MSG_TARGET_KILLED;
    public static final ModConfigSpec.ConfigValue<String> MSG_EVENT_END;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("timing");
        PREP_TIME_SECONDS = b
            .comment("Countdown seconds before the chest spawns. Default: 60")
            .defineInRange("prepTimeSeconds", 60, 10, 600);
        BROADCAST_INTERVAL_SECONDS = b
            .comment("How often (seconds) coords broadcast. Default: 5")
            .defineInRange("broadcastIntervalSeconds", 5, 3, 60);
        EVENT_DURATION_SECONDS = b
            .comment("Event duration in seconds. Default: 3600 (1 hour)")
            .defineInRange("eventDurationSeconds", 3600, 60, 86400);
        b.pop().push("world");
        CHEST_SPAWN_RADIUS = b
            .comment("Random radius from spawn to place chest. Default: 200")
            .defineInRange("chestSpawnRadius", 200, 20, 2000);
        b.pop().push("messages");
        MSG_EVENT_START = b
            .define("eventStart",
                "§5§l☠ HUNTED ☠ §r§eA cursed chest spawns in §c{time}s§e! Claim the crown — become the target!");
        MSG_CHEST_SPAWNED = b
            .define("chestSpawned",
                "§5§l☠ HUNTED ☠ §r§aThe §5Cursed Crown§a chest has appeared at §f{x}, {y}, {z}§a!");
        MSG_TARGET_ACQUIRED = b
            .define("targetAcquired",
                "§5§l☠ HUNTED ☠ §r§c{player} §ehas claimed the §5Cursed Crown§e! §cEVERYONE HUNT THEM!");
        MSG_COORDS_BROADCAST = b
            .define("coordsBroadcast",
                "§5☠ §cTARGET §f{player} §7» §e{x}, {y}, {z} §7| {dir} §7| §e{dist}m");
        MSG_TARGET_KILLED = b
            .define("targetKilled",
                "§5§l☠ §r§b{killer} §ehas slain §c{target}§e! The §5Cursed Crown§e dropped!");
        MSG_EVENT_END = b
            .define("eventEnd", "§5§l☠ HUNTED ☠ §r§7The hunt is over.");
        b.pop();
        SPEC = b.build();
    }
}
