package lol.pyr.znpcsplus.optimization;

import lol.pyr.znpcsplus.config.ConfigManager;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort detection of Geyser / Floodgate (Bedrock) players.
 *
 * We deliberately do NOT hard-depend on Geyser or Floodgate: the plugin already
 * has them as soft-dependencies. Reflection is used when their APIs are present,
 * with cheap heuristics as a fallback. Results are cached per UUID because the
 * check happens on the hot packet path.
 */
public final class BedrockDetector {

    private final ConfigManager configManager;
    private final ConcurrentHashMap<UUID, Boolean> cache = new ConcurrentHashMap<>();

    private Object floodgateApi;
    private Method floodgateCheck;
    private Object geyserApi;
    private Method geyserCheck;

    public BedrockDetector(ConfigManager configManager) {
        this.configManager = configManager;
        resolveApis();
    }

    private void resolveApis() {
        // Floodgate: org.geysermc.floodgate.api.FloodgateApi#getInstance().isFloodgatePlayer(UUID)
        try {
            Class<?> clazz = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = clazz.getMethod("getInstance").invoke(null);
            Method check = clazz.getMethod("isFloodgatePlayer", UUID.class);
            this.floodgateApi = instance;
            this.floodgateCheck = check;
        } catch (Throwable ignored) {
            // Floodgate not installed or incompatible version
        }

        // Geyser API (2.2+): org.geysermc.geyser.api.GeyserApi#api().isBedrockPlayer(UUID)
        try {
            Class<?> clazz = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object instance = clazz.getMethod("api").invoke(null);
            Method check = clazz.getMethod("isBedrockPlayer", UUID.class);
            this.geyserApi = instance;
            this.geyserCheck = check;
        } catch (Throwable ignored) {
            // Older Geyser relies on Floodgate for player identification
        }
    }

    public boolean isBedrock(Player player) {
        if (player == null) return false;
        if (!configManager.getConfig().geyserOptimizationsEnabled()) return false;
        Boolean cached = cache.get(player.getUniqueId());
        if (cached != null) return cached;
        boolean result = detect(player);
        cache.put(player.getUniqueId(), result);
        return result;
    }

    private boolean detect(Player player) {
        UUID uuid = player.getUniqueId();

        if (floodgateApi != null && floodgateCheck != null) {
            try {
                Object value = floodgateCheck.invoke(floodgateApi, uuid);
                if (value instanceof Boolean) return (Boolean) value;
            } catch (Throwable ignored) {
                // fall through to the next strategy
            }
        }

        if (geyserApi != null && geyserCheck != null) {
            try {
                Object value = geyserCheck.invoke(geyserApi, uuid);
                if (value instanceof Boolean) return (Boolean) value;
            } catch (Throwable ignored) {
                // fall through to the next strategy
            }
        }

        // Floodgate commonly issues UUIDs with zero most-significant bits for
        // Bedrock-linked players. Configurable because some proxies override it.
        if (configManager.getConfig().geyserUuidMsbZero() && uuid.getMostSignificantBits() == 0L) {
            return true;
        }

        String name = player.getName().toLowerCase(Locale.ROOT);
        String rawPrefixes = configManager.getConfig().geyserNamePrefixes();
        if (rawPrefixes != null && !rawPrefixes.isEmpty()) {
            return Arrays.stream(rawPrefixes.split(","))
                .map(String::trim)
                .filter(prefix -> !prefix.isEmpty())
                .anyMatch(name::startsWith);
        }
        return false;
    }

    public void forget(UUID uuid) {
        cache.remove(uuid);
    }

    public void clear() {
        cache.clear();
    }
}
