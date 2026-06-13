package com.hunted.mod.command;

import com.hunted.mod.event.HuntedEventManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

public class HuntedCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("hunted")

            // /hunted start
            .then(Commands.literal("start")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    boolean ok = HuntedEventManager.startEvent();
                    if (ok) ctx.getSource().sendSuccess(() -> Component.literal("§a[Hunted] Event started!"), true);
                    else ctx.getSource().sendFailure(Component.literal("§c[Hunted] Already running! Phase: " + HuntedEventManager.getPhase()));
                    return ok ? 1 : 0;
                })
            )

            // /hunted stop
            .then(Commands.literal("stop")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    boolean ok = HuntedEventManager.stopEvent();
                    if (ok) ctx.getSource().sendSuccess(() -> Component.literal("§a[Hunted] Event stopped."), true);
                    else ctx.getSource().sendFailure(Component.literal("§c[Hunted] No event running."));
                    return ok ? 1 : 0;
                })
            )

            // /hunted win
            .then(Commands.literal("win")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    boolean ok = HuntedEventManager.forceWin();
                    if (ok) ctx.getSource().sendSuccess(() -> Component.literal("§a[Hunted] Forced win!"), true);
                    else ctx.getSource().sendFailure(Component.literal("§c[Hunted] Event not active."));
                    return ok ? 1 : 0;
                })
            )

            // /hunted time <seconds>
            .then(Commands.literal("time")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 86400))
                    .executes(ctx -> {
                        int secs = IntegerArgumentType.getInteger(ctx, "seconds");
                        boolean ok = HuntedEventManager.setTime(secs);
                        if (ok) ctx.getSource().sendSuccess(() -> Component.literal("§a[Hunted] Time set to " + secs + "s."), true);
                        else ctx.getSource().sendFailure(Component.literal("§c[Hunted] Event not active."));
                        return ok ? 1 : 0;
                    })
                )
            )

            // /hunted crown [player]
            .then(Commands.literal("crown")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    // Give to self
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                        ctx.getSource().sendFailure(Component.literal("§cMust be a player."));
                        return 0;
                    }
                    boolean ok = HuntedEventManager.giveCrown(player);
                    if (ok) ctx.getSource().sendSuccess(() -> Component.literal("§a[Hunted] Crown given to you."), true);
                    else ctx.getSource().sendFailure(Component.literal("§c[Hunted] Event not active."));
                    return ok ? 1 : 0;
                })
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        boolean ok = HuntedEventManager.giveCrown(target);
                        if (ok) ctx.getSource().sendSuccess(() -> Component.literal("§a[Hunted] Crown given to " + target.getName().getString() + "."), true);
                        else ctx.getSource().sendFailure(Component.literal("§c[Hunted] Event not active."));
                        return ok ? 1 : 0;
                    })
                )
            )

            // /hunted tp
            .then(Commands.literal("tp")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    UUID targetUUID = HuntedEventManager.getTargetUUID();
                    if (targetUUID == null) {
                        ctx.getSource().sendFailure(Component.literal("§c[Hunted] No target right now."));
                        return 0;
                    }
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                        ctx.getSource().sendFailure(Component.literal("§cMust be a player."));
                        return 0;
                    }
                    ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayer(targetUUID);
                    if (target == null) {
                        ctx.getSource().sendFailure(Component.literal("§c[Hunted] Target is offline."));
                        return 0;
                    }
                    player.teleportTo((net.minecraft.server.level.ServerLevel) target.level(),
                        target.getX(), target.getY(), target.getZ(),
                        player.getYRot(), player.getXRot());
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[Hunted] Teleported to " + target.getName().getString() + "."), true);
                    return 1;
                })
            )

            // /hunted status
            .then(Commands.literal("status")
                .executes(ctx -> {
                    String phase  = HuntedEventManager.getPhase().name();
                    String target = HuntedEventManager.getTargetName();
                    int secsLeft  = HuntedEventManager.getEventSecsLeft();
                    int mins = secsLeft / 60, secs = secsLeft % 60;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§6[Hunted] §ePhase: §f" + phase
                        + (phase.equals("ACTIVE") ? "  §eTarget: §c" + target + "  §eTime: §f" + mins + "m " + secs + "s" : "")),
                        false);
                    return 1;
                })
            )
        );
    }
}
