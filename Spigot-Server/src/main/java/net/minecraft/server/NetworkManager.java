package net.minecraft.server;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import cc.bukkit.starlink.PacketStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.Queue;
import javax.annotation.Nullable;
import javax.crypto.SecretKey;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class NetworkManager extends SimpleChannelInboundHandler<Packet<?>> {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final Marker a = MarkerManager.getMarker("NETWORK");
    public static final Marker b = MarkerManager.getMarker("NETWORK_PACKETS", NetworkManager.a);
    public static final AttributeKey<EnumProtocol> c = AttributeKey.valueOf("protocol");
    public static final LazyInitVar<NioEventLoopGroup> d = new LazyInitVar<>(() -> {
        return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).build());
    });
    public static final LazyInitVar<EpollEventLoopGroup> e = new LazyInitVar<>(() -> {
        return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).build());
    });
    public static final LazyInitVar<DefaultEventLoopGroup> f = new LazyInitVar<>(() -> {
        return new DefaultEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Local Client IO #%d").setDaemon(true).build());
    });
    private final EnumProtocolDirection h;
    private final Queue<NetworkManager.QueuedPacket> packetQueue = Queues.newConcurrentLinkedQueue();
    private boolean handled = false; // StarLink - free packet queue
    private EnumProtocol protocol; // StarLink - avoid map lookup
    private PacketStream stream; public PacketStream stream() { if (stream == null) { stream = PacketStream.create(this); return stream; } else return stream; }// StarLink
    public Channel channel;
    public SocketAddress socketAddress;
    // Spigot Start
    public java.util.UUID spoofedUUID;
    public com.mojang.authlib.properties.Property[] spoofedProfile;
    public boolean preparing = true;
    // Spigot End
    private PacketListener packetListener;
    private IChatBaseComponent m;
    private boolean n;
    private boolean o;
    private int p;
    private int q;
    private float r;
    private float s;
    private int t;
    private boolean u;

    public NetworkManager(EnumProtocolDirection enumprotocoldirection) {
        this.h = enumprotocoldirection;
    }

    public void channelActive(ChannelHandlerContext channelhandlercontext) throws Exception {
        super.channelActive(channelhandlercontext);
        this.channel = channelhandlercontext.channel();
        this.socketAddress = this.channel.remoteAddress();
        // Spigot Start
        this.preparing = false;
        // Spigot End

        try {
            this.setProtocol(EnumProtocol.HANDSHAKING);
        } catch (Throwable throwable) {
            NetworkManager.LOGGER.fatal(throwable);
        }

    }

    public void setProtocol(EnumProtocol enumprotocol) {
        this.channel.attr(NetworkManager.c).set(enumprotocol);
        protocol = enumprotocol; // StarLink - avoid map lookup
        this.channel.config().setAutoRead(true);
        NetworkManager.LOGGER.debug("Enabled auto read");
    }

    public void channelInactive(ChannelHandlerContext channelhandlercontext) throws Exception {
        this.close(new ChatMessage("disconnect.endOfStream", new Object[0]));
    }

    public void exceptionCaught(ChannelHandlerContext channelhandlercontext, Throwable throwable) {
        if (throwable instanceof SkipEncodeException) {
            NetworkManager.LOGGER.debug("Skipping packet due to errors", throwable.getCause());
        } else {
            boolean flag = !this.u;

            this.u = true;
            if (this.channel.isOpen()) {
                if (throwable instanceof TimeoutException) {
                    NetworkManager.LOGGER.debug("Timeout", throwable);
                    this.close(new ChatMessage("disconnect.timeout", new Object[0]));
                } else {
                    ChatMessage chatmessage = new ChatMessage("disconnect.genericReason", new Object[]{"Internal Exception: " + throwable});

                    if (flag) {
                        NetworkManager.LOGGER.debug("Failed to sent packet", throwable);
                        this.sendPacket(new PacketPlayOutKickDisconnect(chatmessage), (future) -> {
                            this.close(chatmessage);
                        });
                        this.stopReading();
                    } else {
                        NetworkManager.LOGGER.debug("Double fault", throwable);
                        this.close(chatmessage);
                    }
                }

            }
        }
        if (MinecraftServer.getServer().isDebugging()) throwable.printStackTrace(); // Spigot
    }

    protected void channelRead0(ChannelHandlerContext channelhandlercontext, Packet<?> packet) throws Exception {
        if (this.channel.isOpen()) {
            try {
                a(packet, this.packetListener);
            } catch (CancelledPacketHandleException cancelledpackethandleexception) {
                ;
            }

            ++this.p;
        }

    }

    private static <T extends PacketListener> void a(Packet<T> packet, PacketListener packetlistener) {
        packet.a((T) packetlistener); // CraftBukkit - decompile error
    }

    public void setPacketListener(PacketListener packetlistener) {
        Validate.notNull(packetlistener, "packetListener", new Object[0]);
        NetworkManager.LOGGER.debug("Set listener of {} to {}", this, packetlistener);
        this.packetListener = packetlistener;
    }

    public void sendPacket(Packet<?> packet) {
        this.sendPacket(packet, (GenericFutureListener) null);
    }

    public void sendPacket(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> genericfuturelistener) {
	if (!channel.isRegistered()) return; // StarLink
        if (this.isConnected()) {
            if (!handled) { this.o(); handled = true; } // StarLink - free packet queue
            this.b(packet, genericfuturelistener);
        } else {
            this.packetQueue.add(new NetworkManager.QueuedPacket(packet, genericfuturelistener));
        }

    }

    private void b(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> genericfuturelistener) {
        EnumProtocol enumprotocol = packet.protocol();//EnumProtocol.a(packet); // StarLink - avoid map lookup
        EnumProtocol enumprotocol1 = protocol;//(EnumProtocol) this.channel.attr(NetworkManager.c).get(); // StarLink - avoid map lookup

        ++this.q;
        if (enumprotocol1 != enumprotocol) {
            NetworkManager.LOGGER.debug("Disabled auto read");
            this.channel.config().setAutoRead(false);
        }

        if (false && this.channel.eventLoop().inEventLoop()) { // StarLink
            if (enumprotocol != enumprotocol1) {
                this.setProtocol(enumprotocol);
            }

            ChannelFuture channelfuture = this.channel.writeAndFlush(packet);

            if (genericfuturelistener != null) {
                channelfuture.addListener(genericfuturelistener);
            }

            channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } else {
            this.channel.eventLoop().execute(() -> {
                if (enumprotocol != enumprotocol1) {
                    this.setProtocol(enumprotocol);
                }

                ChannelFuture channelfuture1 = this.channel.writeAndFlush(packet);

                if (genericfuturelistener != null) {
                    channelfuture1.addListener(genericfuturelistener);
                }

                channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            });
        }

        // Paper start
        java.util.List<Packet> extraPackets = packet.getExtraPackets();
        if (extraPackets != null && !extraPackets.isEmpty()) {
            for (Packet extraPacket : extraPackets) {
                this.b(extraPacket, genericfuturelistener);
            }
        }
        // Paper end
    }
    // StarLink start - multiple packets, copied from above
    public void sendPacketAsync(Packet<?> packet) {
	if (!channel.isRegistered())
	    return;
	
	if (!isConnected()) {
	    packetQueue.add(new NetworkManager.QueuedPacket(packet, null));
	    return;
	}
	
        EnumProtocol enumprotocol = packet.protocol();
        EnumProtocol enumprotocol1 = protocol;

        ++this.q;
        if (enumprotocol1 != enumprotocol) {
            NetworkManager.LOGGER.debug("Disabled auto read");
            this.channel.config().setAutoRead(false);
            this.setProtocol(enumprotocol);
        }

        this.channel.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

    }
    
    public void sendPackets(Object[] objects) {
	if (!channel.isRegistered())
	    return;
	
	if (!isConnected()) {
	    for (Object e : objects)
                packetQueue.add(new NetworkManager.QueuedPacket((Packet<?>) e, null));
	    return;
	}
	
        this.channel.eventLoop().execute(() -> {
            for (Object e : objects) {
        	Packet<?> packet = (Packet<?>) e;
        	
        	if (!isConnected()) {
        	    packetQueue.add(new NetworkManager.QueuedPacket(packet, null));
        	    return;
        	}
        	
                ++this.q;
        	
        	EnumProtocol packetProtocol = packet.protocol();
                if (packetProtocol != protocol) {
                    NetworkManager.LOGGER.debug("Disabled auto read");
                    this.channel.config().setAutoRead(false);
                    this.setProtocol(packetProtocol);
                }
                
                this.channel.write(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            }
            
            this.channel.flush();
        });
    }
    // StarLink end

    private void o() {
        if (this.channel != null && this.channel.isOpen()) {
            Queue queue = this.packetQueue;

            synchronized (this.packetQueue) {
                NetworkManager.QueuedPacket networkmanager_queuedpacket;

                while ((networkmanager_queuedpacket = (NetworkManager.QueuedPacket) this.packetQueue.poll()) != null) {
                    this.b(networkmanager_queuedpacket.a, networkmanager_queuedpacket.b);
                }

            }
        }
    }

    public void a() {
        if (!handled) this.o(); // StarLink - free packet queue
        if (this.packetListener instanceof LoginListener) {
            ((LoginListener) this.packetListener).tick();
        }

        if (this.packetListener instanceof PlayerConnection) {
            ((PlayerConnection) this.packetListener).tick();
        }

        if (false && this.channel != null) { // StarLink - already did
            this.channel.flush();
        }

        if (this.t++ % 20 == 0) {
            this.s = this.s * 0.75F + (float) this.q * 0.25F;
            this.r = this.r * 0.75F + (float) this.p * 0.25F;
            this.q = 0;
            this.p = 0;
        }

    }

    public SocketAddress getSocketAddress() {
        return this.socketAddress;
    }

    public void close(IChatBaseComponent ichatbasecomponent) {
        // Spigot Start
        this.preparing = false;
        // Spigot End
        if (this.channel.isOpen()) {
            this.channel.close(); // We can't wait as this may be called from an event loop.
            this.m = ichatbasecomponent;
        }

    }

    public boolean isLocal() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    public void a(SecretKey secretkey) {
        this.n = true;
        this.channel.pipeline().addBefore("splitter", "decrypt", new PacketDecrypter(MinecraftEncryption.a(2, secretkey)));
        this.channel.pipeline().addBefore("prepender", "encrypt", new PacketEncrypter(MinecraftEncryption.a(1, secretkey)));
    }

    public boolean isConnected() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean h() {
        return this.channel == null;
    }

    public PacketListener i() {
        return this.packetListener;
    }

    @Nullable
    public IChatBaseComponent j() {
        return this.m;
    }

    public void stopReading() {
        this.channel.config().setAutoRead(false);
    }

    public void setCompressionLevel(int i) {
        if (i >= 0) {
            if (this.channel.pipeline().get("decompress") instanceof PacketDecompressor) {
                ((PacketDecompressor) this.channel.pipeline().get("decompress")).a(i);
            } else {
                this.channel.pipeline().addBefore("decoder", "decompress", new PacketDecompressor(i));
            }

            if (this.channel.pipeline().get("compress") instanceof PacketCompressor) {
                ((PacketCompressor) this.channel.pipeline().get("compress")).a(i);
            } else {
                this.channel.pipeline().addBefore("encoder", "compress", new PacketCompressor(i));
            }
        } else {
            if (this.channel.pipeline().get("decompress") instanceof PacketDecompressor) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof PacketCompressor) {
                this.channel.pipeline().remove("compress");
            }
        }

    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (this.o) {
                NetworkManager.LOGGER.warn("handleDisconnection() called twice");
            } else {
                this.o = true;
                if (this.j() != null) {
                    this.i().a(this.j());
                } else if (this.i() != null) {
                    this.i().a(new ChatMessage("multiplayer.disconnect.generic", new Object[0]));
                }
                this.packetQueue.clear(); // Free up packet queue.
            }

        }
    }

    static class QueuedPacket {

        private final Packet<?> a;
        @Nullable
        private final GenericFutureListener<? extends Future<? super Void>> b;

        public QueuedPacket(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> genericfuturelistener) {
            this.a = packet;
            this.b = genericfuturelistener;
        }
    }

    // Spigot Start
    public SocketAddress getRawAddress()
    {
        return this.channel.remoteAddress();
    }
    // Spigot End
}
