package lol.pyr.znpcsplus.tasks;

import lol.pyr.znpcsplus.api.event.NpcDespawnEvent;
import lol.pyr.znpcsplus.api.event.NpcSpawnEvent;
import lol.pyr.znpcsplus.entity.EntityPropertyImpl;
import lol.pyr.znpcsplus.entity.EntityPropertyRegistryImpl;
import lol.pyr.znpcsplus.npc.NpcEntryImpl;
import lol.pyr.znpcsplus.npc.NpcImpl;
import lol.pyr.znpcsplus.npc.NpcRegistryImpl;
import lol.pyr.znpcsplus.packets.PacketFactory;
import lol.pyr.znpcsplus.user.User;
import lol.pyr.znpcsplus.user.UserManager;
import lol.pyr.znpcsplus.util.LookType;
import lol.pyr.znpcsplus.util.NpcLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Visibility / look-at / knockback processor.
 *
 * Optimizations vs upstream:
 *  - one online-player snapshot per cycle instead of calling Bukkit.getOnlinePlayers()
 *    once per NPC and player.getLocation() once per NPC/player pair;
 *  - NPC Bukkit location is resolved once per NPC per cycle;
 *  - per-player effective view distance (Bedrock players see fewer NPCs);
 *  - hysteresis margin so NPCs do not flicker at the distance boundary;
 *  - rotation packets are skipped entirely for players configured as Bedrock.
 */
public class NpcProcessorTask extends BukkitRunnable {
    private final NpcRegistryImpl npcRegistry;
    private final EntityPropertyRegistryImpl propertyRegistry;
    private final UserManager userManager;
    private final PacketFactory packetFactory;

    EntityPropertyImpl<Integer> viewDistanceProperty;
    EntityPropertyImpl<LookType> lookProperty;
    EntityPropertyImpl<Double> lookDistanceProperty;
    EntityPropertyImpl<Boolean> lookReturnProperty;
    EntityPropertyImpl<Boolean> permissionRequiredProperty;
    EntityPropertyImpl<String> permissionNodeProperty;
    EntityPropertyImpl<Boolean> playerKnockbackProperty;
    EntityPropertyImpl<String> playerKnockbackExemptPermissionProperty;
    EntityPropertyImpl<Double> playerKnockbackDistanceProperty;
    EntityPropertyImpl<Double> playerKnockbackVerticalProperty;
    EntityPropertyImpl<Double> playerKnockbackHorizontalProperty;
    EntityPropertyImpl<Integer> playerKnockbackCooldownProperty;
    EntityPropertyImpl<Boolean> playerKnockbackSoundProperty;
    EntityPropertyImpl<Sound> playerKnockbackSoundNameProperty;
    EntityPropertyImpl<Float> playerKnockbackSoundVolumeProperty;
    EntityPropertyImpl<Float> playerKnockbackSoundPitchProperty;

    public NpcProcessorTask(NpcRegistryImpl npcRegistry, EntityPropertyRegistryImpl propertyRegistry, UserManager userManager, PacketFactory packetFactory) {
        this.npcRegistry = npcRegistry;
        this.propertyRegistry = propertyRegistry;
        this.userManager = userManager;
        this.packetFactory = packetFactory;

        this.viewDistanceProperty = propertyRegistry.getByName("view_distance", Integer.class);
        this.lookProperty = propertyRegistry.getByName("look", LookType.class);
        this.lookDistanceProperty = propertyRegistry.getByName("look_distance", Double.class);
        this.lookReturnProperty = propertyRegistry.getByName("look_return", Boolean.class);
        this.permissionRequiredProperty = propertyRegistry.getByName("permission_required", Boolean.class);
        this.permissionNodeProperty = propertyRegistry.getByName("premission_required_perm", String.class);
        this.playerKnockbackProperty = propertyRegistry.getByName("player_knockback", Boolean.class);
        this.playerKnockbackExemptPermissionProperty = propertyRegistry.getByName("player_knockback_exempt_permission", String.class);
        this.playerKnockbackDistanceProperty = propertyRegistry.getByName("player_knockback_distance", Double.class);
        this.playerKnockbackVerticalProperty = propertyRegistry.getByName("player_knockback_vertical", Double.class);
        this.playerKnockbackHorizontalProperty = propertyRegistry.getByName("player_knockback_horizontal", Double.class);
        this.playerKnockbackCooldownProperty = propertyRegistry.getByName("player_knockback_cooldown", Integer.class);
        this.playerKnockbackSoundProperty = propertyRegistry.getByName("player_knockback_sound", Boolean.class);
        this.playerKnockbackSoundNameProperty = propertyRegistry.getByName("player_knockback_sound_name", Sound.class);
        this.playerKnockbackSoundVolumeProperty = propertyRegistry.getByName("player_knockback_sound_volume", Float.class);
        this.playerKnockbackSoundPitchProperty = propertyRegistry.getByName("player_knockback_sound_pitch", Float.class);
    }

    private static final class PlayerSnapshot {
        private final Player player;
        private final Location location;
        private final World world;
        private final boolean skipRotation;

        private PlayerSnapshot(Player player, boolean skipRotation) {
            this.player = player;
            this.location = player.getLocation();
            this.world = this.location.getWorld();
            this.skipRotation = skipRotation;
        }
    }

    public void run() {
        // Snapshot players once. This avoids O(NPC * player) calls to
        // Bukkit.getOnlinePlayers()/getLocation()/getWorld() every cycle.
        List<PlayerSnapshot> snapshots = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            snapshots.add(new PlayerSnapshot(player, packetFactory.skipRotation(player)));
        }
        if (snapshots.isEmpty()) return;

        int margin = packetFactory.viewDistanceMargin();

        double lookDistance;
        boolean lookReturn;
        boolean permissionRequired;
        String permissionRequiredPerm = null;
        boolean playerKnockback;
        String playerKnockbackExemptPermission = null;
        double playerKnockbackDistance = 0;
        double playerKnockbackVertical = 0;
        double playerKnockbackHorizontal = 0;
        int playerKnockbackCooldown = 0;
        boolean playerKnockbackSound = false;
        Sound playerKnockbackSoundName = null;
        float playerKnockbackSoundVolume = 0;
        float playerKnockbackSoundPitch = 0;

        for (NpcEntryImpl entry : npcRegistry.getProcessable()) {
            NpcImpl npc = entry.getNpc();
            if (!npc.isEnabled()) continue;

            World npcWorld = npc.getWorld();
            if (npcWorld == null) continue;
            Location npcLocation = npc.getBukkitLocation();
            if (npcLocation == null) continue;
            double npcX = npcLocation.getX();
            double npcY = npcLocation.getY();
            double npcZ = npcLocation.getZ();

            double closestDist = Double.MAX_VALUE;
            Player closest = null;
            LookType lookType = npc.getProperty(lookProperty);
            boolean perPlayerLook = lookType.equals(LookType.PER_PLAYER);
            int normalViewDistance = npc.getProperty(viewDistanceProperty);
            lookDistance = NumberConversions.square(npc.getProperty(lookDistanceProperty));
            lookReturn = npc.getProperty(lookReturnProperty);
            permissionRequired = npc.getProperty(permissionRequiredProperty);
            if (permissionRequired) permissionRequiredPerm = npc.getProperty(permissionNodeProperty);
            playerKnockback = npc.getProperty(playerKnockbackProperty);
            if (playerKnockback) {
                playerKnockbackExemptPermission = npc.getProperty(playerKnockbackExemptPermissionProperty);
                playerKnockbackDistance = NumberConversions.square(npc.getProperty(playerKnockbackDistanceProperty));
                playerKnockbackVertical = npc.getProperty(playerKnockbackVerticalProperty);
                playerKnockbackHorizontal = npc.getProperty(playerKnockbackHorizontalProperty);
                playerKnockbackCooldown = npc.getProperty(playerKnockbackCooldownProperty);
                playerKnockbackSound = npc.getProperty(playerKnockbackSoundProperty);
                playerKnockbackSoundName = npc.getProperty(playerKnockbackSoundNameProperty);
                playerKnockbackSoundVolume = npc.getProperty(playerKnockbackSoundVolumeProperty);
                playerKnockbackSoundPitch = npc.getProperty(playerKnockbackSoundPitchProperty);
            }

            for (PlayerSnapshot snapshot : snapshots) {
                Player player = snapshot.player;
                if (!npcWorld.equals(snapshot.world)) {
                    if (npc.isVisibleTo(player)) npc.hide(player);
                    continue;
                }
                if (permissionRequired && !player.hasPermission(permissionRequiredPerm != null ? permissionRequiredPerm : "znpcsplus.npc." + entry.getId())) {
                    if (npc.isVisibleTo(player)) npc.hide(player);
                    continue;
                }

                double dx = npcX - snapshot.location.getX();
                double dy = npcY - snapshot.location.getY();
                double dz = npcZ - snapshot.location.getZ();
                double distance = dx * dx + dy * dy + dz * dz;

                int viewDistance = packetFactory.getViewDistance(player, normalViewDistance);
                double showDistance = NumberConversions.square(viewDistance);
                double hideDistance = NumberConversions.square(viewDistance + margin);
                boolean currentlyVisible = npc.isVisibleTo(player);
                boolean inRange = currentlyVisible ? distance <= hideDistance : distance <= showDistance;

                // visibility
                if (!inRange && currentlyVisible) {
                    NpcDespawnEvent event = new NpcDespawnEvent(player, entry);
                    Bukkit.getPluginManager().callEvent(event);
                    if (!event.isCancelled()) npc.hide(player);
                }
                if (inRange) {
                    if (!currentlyVisible) {
                        NpcSpawnEvent event = new NpcSpawnEvent(player, entry);
                        Bukkit.getPluginManager().callEvent(event);
                        if (event.isCancelled()) continue;
                        npc.show(player);
                    }
                    if (perPlayerLook) {
                        if (lookDistance >= distance) {
                            NpcLocation expected = npc.lookingAt(player);
                            if (!snapshot.skipRotation) npc.setHeadRotation(player, expected.getYaw(), expected.getPitch());
                        } else if (lookReturn) {
                            if (!snapshot.skipRotation) npc.setHeadRotation(player, npc.getLocation().getYaw(), npc.getLocation().getPitch());
                        }
                    } else {
                        if (distance < closestDist) {
                            closestDist = distance;
                            closest = player;
                        }
                    }

                    // player knockback
                    User user = userManager.get(player.getUniqueId());
                    if (playerKnockbackExemptPermission == null || !player.hasPermission(playerKnockbackExemptPermission)) {
                        if (playerKnockback && distance <= playerKnockbackDistance && user.canKnockback(playerKnockbackCooldown)) {
                            double x = npcX - snapshot.location.getX();
                            double z = npcZ - snapshot.location.getZ();
                            double angle = Math.atan2(z, x);
                            double knockbackX = -Math.cos(angle) * playerKnockbackHorizontal;
                            double knockbackZ = -Math.sin(angle) * playerKnockbackHorizontal;
                            player.setVelocity(player.getVelocity().add(new Vector(knockbackX, playerKnockbackVertical, knockbackZ)));
                            if (playerKnockbackSound)
                                player.playSound(snapshot.location, playerKnockbackSoundName, playerKnockbackSoundVolume, playerKnockbackSoundPitch);
                        }
                    }
                }
            }
            // look property
            if (!perPlayerLook) {
                if (lookType.equals(LookType.CLOSEST_PLAYER)) {
                    if (closest != null && lookDistance >= closestDist) {
                        NpcLocation expected = npc.lookingAt(closest);
                        if (!expected.equals(npc.getLocation()))
                            npc.setHeadRotation(expected.getYaw(), expected.getPitch());
                    } else if (lookReturn) {
                        npc.setHeadRotation(npc.getLocation().getYaw(), npc.getLocation().getPitch());
                    }
                } else if (lookType.equals(LookType.FIXED)) {
                    npc.setHeadRotation(npc.getLocation().getYaw(), npc.getLocation().getPitch());
                }
            }
        }
    }
}
