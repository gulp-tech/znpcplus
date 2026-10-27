package lol.pyr.znpcsplus.optimization;

import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import lol.pyr.znpcsplus.config.ConfigManager;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-Bedrock-player outgoing packet budget.
 *
 * Bedrock clients (via Geyser) are far more sensitive to large bursts of entity
 * spawn/metadata packets than Java clients: a single tick with dozens of NPCs
 * appearing at once is enough to cause a visible FPS drop. Instead of sending
 * everything immediately, we:
 *
 *  - drop packets that Bedrock cannot use anyway (tab list, attributes, ...),
 *  - coalesce repeated per-entity packets (metadata / rotation / teleport),
 *  - release at most N packets per tick per player.
 *
 * Java players are not affected: their packets are sent synchronously as before.
 */
public final class BedrockPacketThrottle {

    private final BedrockDetector detector;
    private final ConfigManager configManager;
    private final ConcurrentHashMap<UUID, QueueHolder> queues = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /** Cached getEntityId() accessor per wrapper class (avoids reflection cost). */
    private static final ClassValue<Method> ENTITY_ID_GETTER = new ClassValue<Method>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                Method method = type.getMethod("getEntityId");
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
                return null;
            }
        }
    };

    private static final class QueueHolder {
        private final Player player;
        private final LinkedHashMap<String, Runnable> pending = new LinkedHashMap<>();

        private QueueHolder(Player player) {
            this.player = player;
        }
    }

    public BedrockPacketThrottle(BedrockDetector detector, ConfigManager configManager) {
        this.detector = detector;
        this.configManager = configManager;
    }

    public boolean isBedrock(Player player) {
        return detector.isBedrock(player);
    }

    /**
     * Entry point for every packet the plugin sends to a player.
     *
     * @param sender the actual send operation (may be executed later)
     */
    public void send(Player player, PacketWrapper<?> packet, Runnable sender) {
        if (!detector.isBedrock(player)) {
            sender.run();
            return;
        }
        if (shouldDrop(packet)) return;

        String key = coalesceKey(packet);
        if (key == null) key = "unique:" + sequence.incrementAndGet();

        QueueHolder holder = queues.computeIfAbsent(player.getUniqueId(), ignored -> new QueueHolder(player));
        int maxQueued = configManager.getConfig().geyserMaxQueuedPackets();
        synchronized (holder.pending) {
            if (maxQueued > 0 && holder.pending.size() >= maxQueued) {
                Iterator<String> iterator = holder.pending.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            // Replacing an existing key keeps its FIFO position but sends only
            // the most recent state (e.g. the newest head rotation / metadata).
            holder.pending.put(key, sender);
        }
    }

    /** Called once per tick from the plugin scheduler. */
    public void tick() {
        if (queues.isEmpty()) return;
        int perTick = configManager.getConfig().geyserPacketsPerTick();
        int limit = perTick <= 0 ? Integer.MAX_VALUE : perTick;

        for (Map.Entry<UUID, QueueHolder> entry : queues.entrySet()) {
            QueueHolder holder = entry.getValue();
            if (!holder.player.isOnline()) {
                queues.remove(entry.getKey(), holder);
                continue;
            }
            List<Runnable> batch = new ArrayList<>(Math.min(limit, 64));
            synchronized (holder.pending) {
                Iterator<Map.Entry<String, Runnable>> iterator = holder.pending.entrySet().iterator();
                while (iterator.hasNext() && batch.size() < limit) {
                    batch.add(iterator.next().getValue());
                    iterator.remove();
                }
                if (holder.pending.isEmpty()) queues.remove(entry.getKey(), holder);
            }
            for (Runnable runnable : batch) {
                try {
                    runnable.run();
                } catch (Throwable throwable) {
                    // Never let one broken packet stop the queue draining.
                    throwable.printStackTrace();
                }
            }
        }
    }

    private boolean shouldDrop(PacketWrapper<?> packet) {
        if (!configManager.getConfig().geyserOptimizationsEnabled()) return false;
        if (configManager.getConfig().geyserDropRotationPackets()
            && (packet instanceof WrapperPlayServerEntityHeadLook
            || packet instanceof WrapperPlayServerEntityRotation
            || packet instanceof WrapperPlayServerEntityAnimation)) return true;
        if (configManager.getConfig().geyserDropEquipmentPackets()
            && packet instanceof WrapperPlayServerEntityEquipment) return true;
        if (configManager.getConfig().geyserDropAttributePackets()
            && packet instanceof WrapperPlayServerUpdateAttributes) return true;
        if (configManager.getConfig().geyserDropTabListPackets()
            && (packet instanceof WrapperPlayServerPlayerInfo
            || packet instanceof WrapperPlayServerPlayerInfoUpdate
            || packet instanceof WrapperPlayServerPlayerInfoRemove)) return true;
        if (configManager.getConfig().geyserDropTeamPackets()
            && packet instanceof WrapperPlayServerTeams) return true;
        return false;
    }

    private String coalesceKey(PacketWrapper<?> packet) {
        if (!(packet instanceof WrapperPlayServerEntityMetadata)
            && !(packet instanceof WrapperPlayServerEntityHeadLook)
            && !(packet instanceof WrapperPlayServerEntityRotation)
            && !(packet instanceof WrapperPlayServerEntityTeleport)) return null;
        Method getter = ENTITY_ID_GETTER.get(packet.getClass());
        if (getter == null) return null;
        try {
            Object id = getter.invoke(packet);
            return packet.getClass().getSimpleName() + ":" + id;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public void forget(UUID uuid) {
        queues.remove(uuid);
        detector.forget(uuid);
    }

    public void clear() {
        queues.clear();
        detector.clear();
    }
}
