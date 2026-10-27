package lol.pyr.znpcsplus.user;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerServerData;
import lol.pyr.znpcsplus.config.ConfigManager;

public class ClientPacketListener implements PacketListener {
    private final ConfigManager configManager;

    public ClientPacketListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!configManager.getConfig().fakeEnforceSecureChat()) return;
        if (event.getPacketType() == PacketType.Play.Server.SERVER_DATA) {
            WrapperPlayServerServerData packet = new WrapperPlayServerServerData(event);
            packet.setEnforceSecureChat(true);
            event.setByteBuf(packet.getBuffer());
        } else if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            WrapperPlayServerJoinGame packet = new WrapperPlayServerJoinGame(event);
            packet.setEnforcesSecureChat(true);
            event.setByteBuf(packet.getBuffer());
        }
    }
}
