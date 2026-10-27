ZNPCsPlus-Modern - Geyser/Bedrock optimized build
=================================================

Put ONE of these jars into your server's plugins/ folder (replace the old
ZNPCsPlus jar; the plugin name is still ZNPCsPlus):

  ZNPCsPlus-Modern-Geyser-Optimized-1_16.jar  -> Paper/Spigot 1.16.x - 1.17.x
  ZNPCsPlus-Modern-Geyser-Optimized-1_18.jar  -> Paper/Spigot 1.18+ (incl. Folia)

Requirements: Java 17+, PacketEvents 2.x installed as a separate plugin.
Geyser-Spigot / Floodgate are optional (used for reliable Bedrock detection).

What changed (see config-snippet.yaml for tunables):
  * outgoing NPC packets for Bedrock players are budgeted (max per tick)
    and coalesced, so a crowd of NPCs no longer arrives in one packet burst;
  * tab-list / player-info packets and async skin lookups are skipped for
    Bedrock players; rotation, equipment and attribute spam is dropped;
  * hologram lines are capped for Bedrock players;
  * NpcProcessorTask now uses one player snapshot per cycle, cached NPC
    locations, per-player view distance and a hysteresis margin;
  * Java players keep the original behaviour.

Tuning: lower geyser-packets-per-tick / geyser-view-distance /
geyser-max-hologram-lines if FPS still drops. Set
geyser-optimizations-enabled: false to disable all of the above.
