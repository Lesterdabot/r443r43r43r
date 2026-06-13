package com.hunted.mod.event;

import com.hunted.mod.HuntedMod;
import com.hunted.mod.config.HuntedConfig;
import com.hunted.mod.item.HuntedItems;
import net.minecraft.core.BlockPos;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.hunted.mod.client.HuntedHudPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.*;

public class HuntedEventManager {

    public enum Phase { IDLE, PREP, ACTIVE }

    private static Phase           phase               = Phase.IDLE;
    private static MinecraftServer server              = null;

    private static int prepTicksLeft     = 0;
    private static int lastPrepAnnounced = -1;

    private static BlockPos    chestPos   = null;
    private static ServerLevel chestLevel = null;

    private static UUID targetUUID         = null;
    private static int  broadcastTicksLeft = 0;
    private static int  eventTicksLeft     = 0;

    private static boolean scanningForNewTarget = false;
    private static int     scanCooldown         = 0;

    private static int particleTick = 0;
    private static int soundTick    = 0;
    private static int groundCrownTick = 0;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        server = e.getServer();
    }

    public static boolean startEvent() {
        if (phase != Phase.IDLE) return false;
        int secs = HuntedConfig.PREP_TIME_SECONDS.get();
        prepTicksLeft     = secs * 20;
        lastPrepAnnounced = secs;
        phase             = Phase.PREP;
        broadcast(HuntedConfig.MSG_EVENT_START.get().replace("{time}", String.valueOf(secs)));
        return true;
    }

    public static Phase  getPhase()      { return phase; }
    public static String getTargetName() {
        if (server == null || targetUUID == null) return "none";
        ServerPlayer p = server.getPlayerList().getPlayer(targetUUID);
        return p != null ? p.getName().getString() : "none";
    }

    /** Force stop the event */
    public static boolean stopEvent() {
        if (phase == Phase.IDLE) return false;
        broadcast("§5§l☠ HUNTED ☠ §r§7Event force-stopped by admin.");
        // Remove crown from target if they have it
        if (server != null && targetUUID != null) {
            ServerPlayer t = server.getPlayerList().getPlayer(targetUUID);
            if (t != null) removeAllCrowns(t);
        }
        // Delete any ground crowns
        if (server != null) {
            for (ServerLevel level : server.getAllLevels())
                level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(-30000, -64, -30000, 30000, 320, 30000),
                    ie -> ie.getItem().is(HuntedItems.CURSED_CROWN.get()))
                    .forEach(ie -> ie.discard());
        }
        reset();
        return true;
    }

    /** Force the win condition right now */
    public static boolean forceWin() {
        if (phase != Phase.ACTIVE) return false;
        endEventWithWinner();
        return true;
    }

    /** Set remaining event time in seconds */
    public static boolean setTime(int seconds) {
        if (phase != Phase.ACTIVE) return false;
        eventTicksLeft = seconds * 20;
        broadcast("§5☠ §eEvent timer set to §c" + seconds + "s §eby admin.");
        return true;
    }

    /** Give the crown to a specific player for testing */
    public static boolean giveCrown(ServerPlayer player) {
        if (phase != Phase.ACTIVE) return false;
        // Remove from current target if any
        if (targetUUID != null) {
            ServerPlayer current = server.getPlayerList().getPlayer(targetUUID);
            if (current != null) removeAllCrowns(current);
        }
        // Delete ground crowns
        for (ServerLevel level : server.getAllLevels())
            level.getEntitiesOfClass(ItemEntity.class,
                new AABB(-30000, -64, -30000, 30000, 320, 30000),
                ie -> ie.getItem().is(HuntedItems.CURSED_CROWN.get()))
                .forEach(ie -> ie.discard());

        ItemStack cur = player.getInventory().offhand.get(0);
        if (!cur.isEmpty()) player.getInventory().add(cur);
        player.getInventory().offhand.set(0, new ItemStack(HuntedItems.CURSED_CROWN.get(), 1));
        scanningForNewTarget = false;
        setTarget(player);
        return true;
    }

    /** Get target UUID for tp command */
    public static UUID getTargetUUID() { return targetUUID; }
    public static int  getEventSecsLeft() { return eventTicksLeft / 20; }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        if (server == null) return;
        switch (phase) {
            case PREP   -> tickPrep();
            case ACTIVE -> tickActive();
            default     -> {}
        }
    }

    private static void tickPrep() {
        prepTicksLeft--;
        int secsLeft = prepTicksLeft / 20;
        if (secsLeft != lastPrepAnnounced) {
            lastPrepAnnounced = secsLeft;
            if (secsLeft > 0 && (secsLeft % 10 == 0 || secsLeft <= 5))
                broadcast("§5☠ §eCursed chest spawning in §c" + secsLeft + "s§e!");
        }
        if (prepTicksLeft <= 0) spawnChest();
    }

    private static void spawnChest() {
        if (server == null) { reset(); return; }
        ServerLevel overworld = server.overworld();
        int radius = HuntedConfig.CHEST_SPAWN_RADIUS.get();
        Random rand = new Random();

        BlockPos landPos = null;
        for (int attempt = 0; attempt < 200; attempt++) {
            int x = rand.nextInt(radius * 2) - radius;
            int z = rand.nextInt(radius * 2) - radius;
            int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (overworld.getFluidState(candidate).isEmpty()
                    && overworld.getFluidState(candidate.below()).isEmpty()
                    && y > overworld.getMinBuildHeight()) {
                landPos = candidate;
                break;
            }
        }
        if (landPos == null) {
            int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            landPos = new BlockPos(0, y, 0);
        }

        overworld.setBlock(landPos, Blocks.CHEST.defaultBlockState(), 3);
        chestPos   = landPos;
        chestLevel = overworld;

        if (overworld.getBlockEntity(landPos) instanceof ChestBlockEntity chest) {
            chest.clearContent();
            chest.setItem(13, new ItemStack(HuntedItems.CURSED_CROWN.get(), 1));
            chest.setChanged();
        }

        phase              = Phase.ACTIVE;
        eventTicksLeft     = HuntedConfig.EVENT_DURATION_SECONDS.get() * 20;
        broadcastTicksLeft = HuntedConfig.BROADCAST_INTERVAL_SECONDS.get() * 20;

        overworld.playSound(null, landPos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0f, 0.5f);
        overworld.playSound(null, landPos, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.8f, 0.3f);
        var bolt = EntityType.LIGHTNING_BOLT.create(overworld);
        if (bolt != null) {
            bolt.setPos(landPos.getX() + 0.5, landPos.getY(), landPos.getZ() + 0.5);
            bolt.setVisualOnly(true);
            overworld.addFreshEntity(bolt);
        }

        for (ServerPlayer p : server.getPlayerList().getPlayers())
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));

        int totalMins = HuntedConfig.EVENT_DURATION_SECONDS.get() / 60;
        broadcast(HuntedConfig.MSG_CHEST_SPAWNED.get()
            .replace("{x}", String.valueOf(landPos.getX()))
            .replace("{y}", String.valueOf(landPos.getY()))
            .replace("{z}", String.valueOf(landPos.getZ())));
        broadcast("§5☠ §eThe hunt lasts §c" + totalMins + " minutes§e. Last one holding the crown wins!");
        sendTitleToAll(
            Component.literal("§5§l☠ THE HUNT BEGINS ☠"),
            Component.literal("§eChest at §f" + landPos.getX() + ", " + landPos.getY() + ", " + landPos.getZ())
        );
        sendHudPackets();
        HuntedMod.LOGGER.info("[Hunted] Chest spawned at {}", landPos);
    }

    private static void tickActive() {
        if (scanningForNewTarget) {
            scanCooldown--;
            if (scanCooldown <= 0) { scanCooldown = 20; doNewTargetScan(); }
        }

        if (targetUUID != null || scanningForNewTarget) {
            eventTicksLeft--;
            int secsLeft = eventTicksLeft / 20;
            if (eventTicksLeft % 20 == 0) {
                if (secsLeft == 1800) { broadcast("§5☠ §e30 minutes remaining!"); sendTitleToAll(Component.literal("§e30 Minutes Left"), Component.literal("§7Keep hunting...")); }
                if (secsLeft == 600)  { broadcast("§5☠ §e10 minutes remaining!"); sendTitleToAll(Component.literal("§e10 Minutes Left"), Component.literal("§cCan they survive?")); }
                if (secsLeft == 300)  { broadcast("§5☠ §c5 minutes remaining!"); sendTitleToAll(Component.literal("§c5 Minutes Left!"), Component.literal("§eFinish the hunt!")); }
                if (secsLeft == 60)   { broadcast("§5☠ §c1 minute remaining!"); sendTitleToAll(Component.literal("§c1 Minute Left!"), Component.literal("§eWho holds the crown?!")); }
                if (secsLeft == 30)   broadcast("§5☠ §c30 seconds!");
                if (secsLeft == 10)   { broadcast("§5☠ §c10 seconds!"); sendTitleToAll(Component.literal("§c10..."), Component.literal("")); }
                if (secsLeft <= 5 && secsLeft > 0) broadcast("§5☠ §c" + secsLeft + "...");
            }
            if (eventTicksLeft <= 0) { endEventWithWinner(); return; }
        }

        if (scanningForNewTarget || targetUUID == null) return;

        broadcastTicksLeft--;
        if (broadcastTicksLeft <= 0) {
            broadcastTargetCoords();
            broadcastTicksLeft = HuntedConfig.BROADCAST_INTERVAL_SECONDS.get() * 20;
        }

        // Refresh glow every 3s independently
        if ((20 * 20 - broadcastTicksLeft) % 60 == 0) {
            ServerPlayer t = server.getPlayerList().getPlayer(targetUUID);
            if (t != null) t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false));
        }

        particleTick++;
        if (particleTick >= 20) { particleTick = 0; spawnTargetParticles(); sendHudPackets(); }

        soundTick++;
        if (soundTick >= 200) {
            soundTick = 0;
            ServerPlayer t = server.getPlayerList().getPlayer(targetUUID);
            if (t != null) {
                SoundEvent choir = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("hunted", "crown.choir"));
                ((ServerLevel) t.level()).playSound(null, t.blockPosition(),
                    choir, SoundSource.MASTER, 0.6f, 1.0f);
            }
        }
    }

    private static void endEventWithWinner() {
        ServerPlayer winner = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (playerHasCrown(p)) { winner = p; break; }

        if (winner != null) {
            String name = winner.getName().getString();
            broadcast("§5§l☠ HUNTED OVER ☠ §r§a" + name + " §esurvived the hunt and §6§lWINS!");
            sendTitleToAll(Component.literal("§6§l" + name + " WINS!"), Component.literal("§aSurvived the Cursed Crown!"));
            spawnFireworks(winner);
            ((ServerLevel) winner.level()).playSound(null, winner.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
            winner.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 120, 0, false, false));
            winner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60, 4, false, false));
            winner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 4, false, false));
            removeAllCrowns(winner);
        } else {
            broadcast("§5§l☠ HUNTED OVER ☠ §r§7Nobody was holding the crown!");
        }
        reset();
    }

    private static void spawnFireworks(ServerPlayer winner) {
        ServerLevel level = (ServerLevel) winner.level();
        Random rand = new Random();
        for (int i = 0; i < 5; i++) {
            FireworkRocketEntity rocket = new FireworkRocketEntity(level,
                winner.getX() + (rand.nextDouble() - 0.5) * 4,
                winner.getY() + 1,
                winner.getZ() + (rand.nextDouble() - 0.5) * 4,
                buildFirework());
            level.addFreshEntity(rocket);
        }
    }

    private static ItemStack buildFirework() {
        ItemStack fw = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion explosion = new FireworkExplosion(
            FireworkExplosion.Shape.LARGE_BALL,
            new IntArrayList(new int[]{0x8B00FF, 0xBF00FF, 0x6600CC}),
            new IntArrayList(new int[]{0x330066}),
            true,
            true
        );
        fw.set(DataComponents.FIREWORKS, new Fireworks(2, List.of(explosion)));
        return fw;
    }

    private static void spawnTargetParticles() {
        ServerPlayer target = server.getPlayerList().getPlayer(targetUUID);
        if (target == null) return;
        double x = target.getX(), y = target.getY(), z = target.getZ();
        ServerLevel level = (ServerLevel) target.level();
        for (int i = 0; i < 8; i++)
            level.sendParticles(ParticleTypes.DRAGON_BREATH, x, y + (i * 1.5), z, 3, 0.2, 0.1, 0.2, 0.01);
        level.sendParticles(ParticleTypes.WITCH, x, y + 0.5, z, 8, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1.8, z, 4, 0.2, 0.1, 0.2, 0.05);
    }

    private static void doNewTargetScan() {
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (playerHasCrown(p)) { scanningForNewTarget = false; groundCrownTick = 0; setTarget(p); return; }

        // Check if crown is on the ground
        boolean crownOnGround = false;
        for (ServerLevel level : server.getAllLevels()) {
            if (!level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(-30000, -64, -30000, 30000, 320, 30000),
                    ie -> ie.getItem().is(HuntedItems.CURSED_CROWN.get())).isEmpty()) {
                crownOnGround = true;
                break;
            }
        }

        if (crownOnGround) {
            // Auto-delete crown after 10 seconds on ground
            groundCrownTick++;
            if (groundCrownTick >= 200) {
                for (ServerLevel level : server.getAllLevels()) {
                    level.getEntitiesOfClass(ItemEntity.class,
                        new AABB(-30000, -64, -30000, 30000, 320, 30000),
                        ie -> ie.getItem().is(HuntedItems.CURSED_CROWN.get()))
                        .forEach(ie -> ie.discard());
                }
                broadcast("§5☠ §eThe crown faded — hunt over!");
                reset();
            }
            return;
        }

        broadcast("§5☠ §eThe crown has vanished! Hunt over.");
        reset();
    }

    private static void broadcastTargetCoords() {
        if (targetUUID == null) return;
        ServerPlayer target = server.getPlayerList().getPlayer(targetUUID);
        if (target == null) return;

        int tx = (int) target.getX(), ty = (int) target.getY(), tz = (int) target.getZ();
        int secsLeft = eventTicksLeft / 20;
        String timeStr = (secsLeft / 60) + "m " + (secsLeft % 60) + "s";

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.getUUID().equals(targetUUID)) {
                viewer.sendSystemMessage(Component.literal(
                    "§5☠ §cYou have the crown! §7Time left: §e" + timeStr));
                continue;
            }
            double dx = tx - viewer.getX(), dz = tz - viewer.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            viewer.sendSystemMessage(Component.literal(
                HuntedConfig.MSG_COORDS_BROADCAST.get()
                    .replace("{player}", target.getName().getString())
                    .replace("{x}", String.valueOf(tx))
                    .replace("{y}", String.valueOf(ty))
                    .replace("{z}", String.valueOf(tz))
                    .replace("{dir}", getCardinalDirection(dx, dz))
                    .replace("{dist}", String.valueOf(dist))
                + " §7| §e" + timeStr));
        }
    }

    @SubscribeEvent
    public static void onChestOpen(PlayerInteractEvent.RightClickBlock e) {
        if (phase != Phase.ACTIVE || chestPos == null || targetUUID != null) return;
        if (!e.getPos().equals(chestPos)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        if (chestLevel.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(HuntedItems.CURSED_CROWN.get())) {
                    chest.removeItem(i, 1); chest.setChanged(); break;
                }
            }
        }
        ItemStack cur = player.getInventory().offhand.get(0);
        if (!cur.isEmpty()) player.getInventory().add(cur);
        player.getInventory().offhand.set(0, new ItemStack(HuntedItems.CURSED_CROWN.get(), 1));
        chestLevel.setBlock(chestPos, Blocks.AIR.defaultBlockState(), 3);

        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0f, 0.6f);
        SoundEvent crownClaimed = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("hunted", "crown.claimed"));
        level.playSound(null, player.blockPosition(), crownClaimed, SoundSource.MASTER, 1.0f, 1.0f);
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setPos(player.getX(), player.getY(), player.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        setTarget(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (phase != Phase.ACTIVE || targetUUID == null) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getUUID().equals(targetUUID) || !player.isAlive()) return;

        // Step 1: purge ALL crowns from main inventory (prevents duplication)
        player.getInventory().items.replaceAll(s -> s.is(HuntedItems.CURSED_CROWN.get()) ? ItemStack.EMPTY : s);

        // Step 2: ensure exactly one crown is in offhand
        if (!player.getInventory().offhand.get(0).is(HuntedItems.CURSED_CROWN.get())) {
            ItemStack curOff = player.getInventory().offhand.get(0);
            if (!curOff.isEmpty()) player.getInventory().add(curOff);
            player.getInventory().offhand.set(0, new ItemStack(HuntedItems.CURSED_CROWN.get(), 1));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDrops(LivingDropsEvent e) {
        if (targetUUID == null) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (player.getUUID().equals(targetUUID) && !player.isAlive()) return;
        e.getDrops().removeIf(drop -> drop.getItem().is(HuntedItems.CURSED_CROWN.get()));
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent e) {
        if (phase != Phase.ACTIVE || targetUUID == null) return;
        if (!(e.getEntity() instanceof ServerPlayer dead)) return;
        if (!dead.getUUID().equals(targetUUID)) return;

        String killer = "the environment";
        if (e.getSource().getEntity() instanceof ServerPlayer k) killer = k.getName().getString();

        broadcast(HuntedConfig.MSG_TARGET_KILLED.get()
            .replace("{killer}", killer).replace("{target}", dead.getName().getString()));
        sendTitleToAll(
            Component.literal("§c" + dead.getName().getString() + " §eWAS SLAIN!"),
            Component.literal("§7by §b" + killer + " §7— §eGrab the crown!"));

        targetUUID = null; scanningForNewTarget = true; scanCooldown = 40;
    }

    /** When target logs off, remove crown and start scanning */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (phase != Phase.ACTIVE || targetUUID == null) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getUUID().equals(targetUUID)) return;

        // Remove crown from their inventory so it doesn't dupe on relog
        removeAllCrowns(player);

        broadcast("§5☠ §c" + player.getName().getString() + " §elogged out with the crown! §7It was lost...");
        targetUUID           = null;
        scanningForNewTarget = true;
        groundCrownTick      = 0;
        scanCooldown         = 40;
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (phase != Phase.ACTIVE || chestPos == null || targetUUID != null) return;
        if (!e.getPos().equals(chestPos)) return;
        if (e.getPlayer() instanceof ServerPlayer p)
            p.sendSystemMessage(Component.literal("§c[Hunted] This chest is protected!"));
        e.setCanceled(true);
    }

    private static void sendTitleToAll(Component title, Component subtitle) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    private static void setTarget(ServerPlayer player) {
        targetUUID = player.getUUID();
        broadcastTicksLeft = HuntedConfig.BROADCAST_INTERVAL_SECONDS.get() * 20;
        particleTick = soundTick = 0;
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false));
        broadcast(HuntedConfig.MSG_TARGET_ACQUIRED.get().replace("{player}", player.getName().getString()));
        HuntedMod.LOGGER.info("[Hunted] New target: {}", player.getName().getString());
    }

    private static boolean playerHasCrown(ServerPlayer p) {
        return p.getInventory().items.stream().anyMatch(s -> s.is(HuntedItems.CURSED_CROWN.get()))
            || p.getInventory().offhand.stream().anyMatch(s -> s.is(HuntedItems.CURSED_CROWN.get()));
    }

    private static void removeAllCrowns(ServerPlayer p) {
        p.getInventory().offhand.replaceAll(s -> s.is(HuntedItems.CURSED_CROWN.get()) ? ItemStack.EMPTY : s);
        p.getInventory().items.replaceAll(s -> s.is(HuntedItems.CURSED_CROWN.get()) ? ItemStack.EMPTY : s);
    }

    private static String getCardinalDirection(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dz, dx));
        if (a < 0) a += 360;
        if (a >= 337.5 || a < 22.5) return "East →";
        if (a < 67.5)  return "SE ↘";
        if (a < 112.5) return "South ↓";
        if (a < 157.5) return "SW ↙";
        if (a < 202.5) return "West ←";
        if (a < 247.5) return "NW ↖";
        if (a < 292.5) return "North ↑";
        return "NE ↗";
    }

    private static void broadcast(String msg) {
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }

    private static void sendHudPackets() {
        if (server == null) return;
        boolean active = phase == Phase.ACTIVE;
        int secsLeft = eventTicksLeft / 20;
        ServerPlayer currentTarget = targetUUID != null ? server.getPlayerList().getPlayer(targetUUID) : null;
        int tx = currentTarget != null ? (int) currentTarget.getX() : 0;
        int ty = currentTarget != null ? (int) currentTarget.getY() : 0;
        int tz = currentTarget != null ? (int) currentTarget.getZ() : 0;
        String tName = currentTarget != null ? currentTarget.getName().getString() : "";

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            boolean viewerIsTarget = targetUUID != null && viewer.getUUID().equals(targetUUID);
            int dist = 0;
            String dir = "";
            if (!viewerIsTarget && currentTarget != null) {
                double dx = tx - viewer.getX(), dz = tz - viewer.getZ();
                dist = (int) Math.sqrt(dx * dx + dz * dz);
                dir = getCardinalDirection(dx, dz);
            } else if (viewerIsTarget) {
                tx = (int) viewer.getX();
                ty = (int) viewer.getY();
                tz = (int) viewer.getZ();
            }
            HuntedHudPacket pkt = new HuntedHudPacket(active, viewerIsTarget, tName, tx, ty, tz, secsLeft, dist, dir);
            PacketDistributor.sendToPlayer(viewer, pkt);
        }
    }

    private static void reset() {
        phase = Phase.IDLE; prepTicksLeft = 0; lastPrepAnnounced = -1;
        chestPos = null; chestLevel = null; targetUUID = null;
        broadcastTicksLeft = 0; eventTicksLeft = 0;
        scanningForNewTarget = false; scanCooldown = 0;
        particleTick = 0; soundTick = 0; groundCrownTick = 0;
        // Send reset HUD to all players
        if (server != null) {
            HuntedHudPacket resetPkt = new HuntedHudPacket(false, false, "", 0, 0, 0, 0, 0, "");
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                PacketDistributor.sendToPlayer(p, resetPkt);
        }
        HuntedMod.LOGGER.info("[Hunted] Reset to IDLE.");
    }
}
