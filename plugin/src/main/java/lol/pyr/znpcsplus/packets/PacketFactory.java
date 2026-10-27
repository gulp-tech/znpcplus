package lol.pyr.znpcsplus.packets;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import lol.pyr.znpcsplus.api.entity.PropertyHolder;
import lol.pyr.znpcsplus.entity.PacketEntity;
import lol.pyr.znpcsplus.util.NamedColor;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PacketFactory {
    CompletableFuture<Void> spawnPlayer(Player player, PacketEntity entity, PropertyHolder properties);

    void spawnEntity(Player player, PacketEntity entity, PropertyHolder properties);

    void destroyEntity(Player player, PacketEntity entity, PropertyHolder properties);

    void teleportEntity(Player player, PacketEntity entity);

    CompletableFuture<Void> addTabPlayer(Player player, PacketEntity entity, PropertyHolder properties);

    void removeTabPlayer(Player player, PacketEntity entity);

    void createTeam(Player player, PacketEntity entity, NamedColor namedColor);

    void removeTeam(Player player, PacketEntity entity);

    void sendAllMetadata(Player player, PacketEntity entity, PropertyHolder properties);

    void sendEquipment(Player player, PacketEntity entity, Equipment equipment);

    void sendMetadata(Player player, PacketEntity entity, List<EntityData<?>> data);

    void sendHeadRotation(Player player, PacketEntity entity, float yaw, float pitch);

    void sendHandSwing(Player player, PacketEntity entity, boolean offHand);

    void setPassengers(Player player, int vehicle, int... passengers);

    void sendAllAttributes(Player player, PacketEntity entity, PropertyHolder properties);

    void sendAttribute(Player player, PacketEntity entity, WrapperPlayServerUpdateAttributes.Property property);

    void updateDisplayName(Player player, PacketEntity entity, Component displayName);

    // --- Bedrock / Geyser optimization hooks (no-op by default) ---

    default boolean isBedrock(Player player) {
        return false;
    }

    /** Effective NPC view distance for this player. */
    default int getViewDistance(Player player, int normalViewDistance) {
        return normalViewDistance;
    }

    /** Whether per-tick rotation packets should be skipped for this player. */
    default boolean skipRotation(Player player) {
        return false;
    }

    /** Hysteresis margin (in blocks) applied after an NPC became visible. */
    default int viewDistanceMargin() {
        return 0;
    }
}
