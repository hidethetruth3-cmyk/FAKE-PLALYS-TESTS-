package com.fakeplayermod;

import com.fakeplayermod.mixin.ConnectionAccessor;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketFlow;

public class FakeConnection extends Connection {
    private final EmbeddedChannel fakeChannel = new EmbeddedChannel();

    public FakeConnection(PacketFlow flow) {
        super(flow);
        ((ConnectionAccessor) (Object) this).fakeplayer$setChannel(fakeChannel);
    }

    /** Packets sent to the fake client pile up in the embedded channel; throw them away. */
    public void drain() {
        fakeChannel.outboundMessages().clear();
    }

    @Override
    public void setReadOnly() {}

    @Override
    public void handleDisconnection() {}

    @Override
    public void setListenerForServerboundHandshake(PacketListener listener) {}

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> info, T listener) {}
}
