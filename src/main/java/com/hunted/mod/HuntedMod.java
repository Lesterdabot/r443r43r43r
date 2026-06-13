package com.hunted.mod;

import com.hunted.mod.client.HuntedHud;
import com.hunted.mod.client.HuntedHudPacket;
import com.hunted.mod.command.HuntedCommand;
import com.hunted.mod.config.HuntedConfig;
import com.hunted.mod.event.HuntedEventManager;
import com.hunted.mod.item.HuntedItems;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import org.slf4j.Logger;

@Mod(HuntedMod.MODID)
public class HuntedMod {

    public static final String MODID = "hunted";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HuntedMod(IEventBus modEventBus, ModContainer modContainer) {
        HuntedItems.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, HuntedConfig.SPEC);

        NeoForge.EVENT_BUS.register(HuntedEventManager.class);
        NeoForge.EVENT_BUS.register(HuntedCommand.class);

        // Register network packet
        modEventBus.addListener(this::registerPayloads);

        // Register client-side HUD only on client
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(HuntedHud.class);
        }

        LOGGER.info("[Hunted] Cursed Crown mod loaded.");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
            HuntedHudPacket.TYPE,
            HuntedHudPacket.CODEC,
            new DirectionalPayloadHandler<>(
                // Client handler
                (pkt, ctx) -> {
                    ctx.enqueueWork(() -> {
                        HuntedHud.eventActive   = pkt.eventActive();
                        HuntedHud.isTarget      = pkt.isTarget();
                        HuntedHud.targetName    = pkt.targetName();
                        HuntedHud.targetX       = pkt.targetX();
                        HuntedHud.targetY       = pkt.targetY();
                        HuntedHud.targetZ       = pkt.targetZ();
                        HuntedHud.eventSecsLeft = pkt.eventSecsLeft();
                        HuntedHud.distToTarget  = pkt.distToTarget();
                        HuntedHud.dirToTarget   = pkt.dirToTarget();
                    });
                },
                // Server handler (unused — this packet only goes to client)
                (pkt, ctx) -> {}
            )
        );
    }
}
