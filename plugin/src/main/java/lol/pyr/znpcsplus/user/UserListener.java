package lol.pyr.znpcsplus.user;

import lol.pyr.znpcsplus.optimization.BedrockPacketThrottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class UserListener implements Listener {
    private final UserManager manager;
    private final BedrockPacketThrottle throttle;

    public UserListener(UserManager manager, BedrockPacketThrottle throttle) {
        this.manager = manager;
        this.throttle = throttle;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.get(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.remove(event.getPlayer().getUniqueId());
        throttle.forget(event.getPlayer().getUniqueId());
    }
}
