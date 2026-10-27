package lol.pyr.znpcsplus.config;

import lol.pyr.znpcsplus.storage.NpcStorageType;
import space.arim.dazzleconf.annote.ConfComments;
import space.arim.dazzleconf.annote.ConfKey;
import space.arim.dazzleconf.annote.SubSection;

import static space.arim.dazzleconf.annote.ConfDefault.*;

public interface MainConfig {
    @ConfKey("view-distance")
    @ConfComments("How far away do you need to be from any NPC for it to disappear, measured in blocks")
    @DefaultInteger(32)
    int viewDistance();

    @ConfKey("line-spacing")
    @ConfComments("The height between hologram lines, measured in blocks")
    @DefaultDouble(0.3D)
    double lineSpacing();

    @ConfKey("check-for-updates")
    @ConfComments("Should the plugin check for available updates and notify admins about them?")
    @DefaultBoolean(true)
    boolean checkForUpdates();

    @ConfKey("debug-enabled")
    @ConfComments({
        "Should debug mode be enabled?",
        "This is used in development to test various things, you probably don't want to enable this"
    })
    @DefaultBoolean(false)
    boolean debugEnabled();

    @ConfKey("storage-type")
    @ConfComments("The storage type to use. Available storage types: YAML, SQLITE, MYSQL")
    @DefaultString("YAML")
    NpcStorageType storageType();

    @ConfKey("database-config")
    @ConfComments("The database config. Only used if storage-type is MYSQL")
    @SubSection
    DatabaseConfig databaseConfig();

    @ConfKey("disable-skin-fetcher-warnings")
    @ConfComments("Set this to true if you don't want to be warned in the console when a skin fails to resolve")
    @DefaultBoolean(false)
    boolean disableSkinFetcherWarnings();

    @ConfKey("auto-save-interval")
    @ConfComments("How often to auto-save npcs, set this to -1 to disable. This value will only apply on restart")
    @DefaultInteger(300)
    int autoSaveInterval();

    default boolean autoSaveEnabled() {
        return autoSaveInterval() != -1;
    }

    @ConfKey("look-property-distance")
    @ConfComments("How far should the look property work from in blocks")
    @DefaultDouble(10)
    double lookPropertyDistance();

    @ConfKey("tab-hide-delay")
    @ConfComments({
        "The amount of time to wait before removing the npc from the player list (aka tab) in ticks",
        "If you're on 1.19.2 or above changing this value will have almost no effect since npcs are hidden in tab",
        "WARNING: Setting this value too low may cause issues with player npcs spawning"
    })
    @DefaultInteger(60)
    int tabHideDelay();

    @ConfKey("tab-display-name")
    @ConfComments("The display name to use for npcs in the player list (aka tab)")
    @DefaultString("ZNPC[{id}]")
    String tabDisplayName();

    @ConfKey("fake-enforce-secure-chat")
    @ConfComments("Should the plugin fake the enforce secure chat packet to hide the popup?")
    @DefaultBoolean(false)
    boolean fakeEnforceSecureChat();

    // ---------------------------------------------------------------------
    // Geyser / Bedrock optimizations (see lol.pyr.znpcsplus.optimization)
    // ---------------------------------------------------------------------

    @ConfKey("geyser-optimizations-enabled")
    @ConfComments("Enable the Bedrock/Geyser packet optimizations. Java players are never throttled.")
    @DefaultBoolean(true)
    boolean geyserOptimizationsEnabled();

    @ConfKey("geyser-view-distance")
    @ConfComments("NPC visibility distance for Bedrock players in blocks. Set to -1 to use the normal view-distance.")
    @DefaultInteger(24)
    int geyserViewDistance();

    @ConfKey("view-distance-margin")
    @ConfComments("Extra blocks kept before an NPC is hidden again (hysteresis, prevents spawn/despawn flicker).")
    @DefaultInteger(4)
    int viewDistanceMargin();

    @ConfKey("geyser-packets-per-tick")
    @ConfComments("Maximum NPC packets sent to one Bedrock player per tick. 0 = unlimited (not recommended).")
    @DefaultInteger(8)
    int geyserPacketsPerTick();

    @ConfKey("geyser-max-queued-packets")
    @ConfComments("Maximum queued packets per Bedrock player before the oldest is dropped. 0 = unlimited.")
    @DefaultInteger(256)
    int geyserMaxQueuedPackets();

    @ConfKey("geyser-max-hologram-lines")
    @ConfComments("Maximum hologram lines spawned for Bedrock players. -1 = unlimited.")
    @DefaultInteger(3)
    int geyserMaxHologramLines();

    @ConfKey("geyser-drop-rotation-packets")
    @ConfComments("Skip head-rotation / body-rotation / swing-animation packets for Bedrock players. Large FPS win.")
    @DefaultBoolean(true)
    boolean geyserDropRotationPackets();

    @ConfKey("geyser-drop-equipment-packets")
    @ConfComments("Skip entity-equipment packets for Bedrock players (they are rarely visible on Bedrock).")
    @DefaultBoolean(true)
    boolean geyserDropEquipmentPackets();

    @ConfKey("geyser-drop-attribute-packets")
    @ConfComments("Skip entity-attribute packets for Bedrock players (NPC attributes are cosmetic).")
    @DefaultBoolean(true)
    boolean geyserDropAttributePackets();

    @ConfKey("geyser-drop-tab-list-packets")
    @ConfComments("Skip player-info / tab-list packets for Bedrock players. Also avoids async skin fetching for player NPCs.")
    @DefaultBoolean(true)
    boolean geyserDropTabListPackets();

    @ConfKey("geyser-drop-team-packets")
    @ConfComments("Skip scoreboard-team packets for Bedrock players (may show nametags). Off by default.")
    @DefaultBoolean(false)
    boolean geyserDropTeamPackets();

    @ConfKey("geyser-name-prefixes")
    @ConfComments("Comma-separated username prefixes used as a fallback Bedrock detector (when Geyser/Floodgate API is absent).")
    @DefaultString(".,*")
    String geyserNamePrefixes();

    @ConfKey("geyser-uuid-msb-zero")
    @ConfComments("Treat players whose UUID most-significant-bits are zero as Bedrock (Floodgate heuristic).")
    @DefaultBoolean(true)
    boolean geyserUuidMsbZero();
}
