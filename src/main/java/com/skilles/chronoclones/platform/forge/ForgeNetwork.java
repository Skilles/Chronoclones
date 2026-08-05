//? if forge {
/*package com.skilles.chronoclones.platform.forge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.compat.CustomPacketPayload;
import com.skilles.chronoclones.compat.PayloadCodecs;
import com.skilles.chronoclones.compat.RegistryFriendlyByteBuf;
import com.skilles.chronoclones.compat.StreamCodec;
import com.skilles.chronoclones.network.ChronoclonesNetwork;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

// One SimpleChannel message wraps every payload: an id picks the codec and the handler.
public final class ForgeNetwork {

    private ForgeNetwork() {}

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Chronoclones.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private static final Map<ResourceLocation, BiConsumer<CustomPacketPayload, ServerPlayer>>
            TO_SERVER_HANDLERS = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, Consumer<CustomPacketPayload>>
            TO_CLIENT_HANDLERS = new ConcurrentHashMap<>();

    public record Wrapper(CustomPacketPayload payload) {}

    @SuppressWarnings("unchecked")
    public static void register() {
        ChronoclonesNetwork.toServer().forEach(entry -> {
            PayloadCodecs.TO_SERVER.put(entry.type().id(), entry.codec().cast());
            TO_SERVER_HANDLERS.put(entry.type().id(), (payload, player) ->
                    ((BiConsumer<CustomPacketPayload, ServerPlayer>) entry.handler())
                            .accept(payload, player));
        });
        ChronoclonesNetwork.toClient().forEach(entry -> {
            PayloadCodecs.TO_CLIENT.put(entry.type().id(), entry.codec().cast());
            TO_CLIENT_HANDLERS.put(entry.type().id(), payload ->
                    ((Consumer<CustomPacketPayload>) entry.handler()).accept(payload));
        });

        CHANNEL.registerMessage(0, Wrapper.class,
                ForgeNetwork::encode, ForgeNetwork::decode, ForgeNetwork::handle);
    }

    private static void encode(Wrapper wrapper, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(wrapper.payload().type().id());
        codecFor(wrapper.payload().type().id()).encode(
                new RegistryFriendlyByteBuf(buffer, null), wrapper.payload());
    }

    private static Wrapper decode(FriendlyByteBuf buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        return new Wrapper(codecFor(id).decode(new RegistryFriendlyByteBuf(buffer, null)));
    }

    @SuppressWarnings("unchecked")
    private static StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> codecFor(
            ResourceLocation id) {
        StreamCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload> codec =
                PayloadCodecs.TO_SERVER.containsKey(id)
                        ? PayloadCodecs.TO_SERVER.get(id)
                        : PayloadCodecs.TO_CLIENT.get(id);
        if (codec == null) {
            throw new IllegalArgumentException("no codec registered for " + id);
        }
        return (StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload>) codec;
    }

    private static void handle(Wrapper wrapper,
                               java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> source) {
        var context = source.get();
        ResourceLocation id = wrapper.payload().type().id();
        if (context.getDirection().getReceptionSide().isServer()) {
            ServerPlayer sender = context.getSender();
            var handler = TO_SERVER_HANDLERS.get(id);
            if (sender != null && handler != null) {
                context.enqueueWork(() -> handler.accept(wrapper.payload(), sender));
            }
        } else {
            var handler = TO_CLIENT_HANDLERS.get(id);
            if (handler != null) {
                context.enqueueWork(() -> handler.accept(wrapper.payload()));
            }
        }
        context.setPacketHandled(true);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection == null || !player.connection.isAcceptingMessages()) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Wrapper(payload));
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(player, payload);
        }
    }

    public static void sendToServer(CustomPacketPayload payload) {
        CHANNEL.sendToServer(new Wrapper(payload));
    }

    public static boolean canSend(ServerPlayer player) {
        return player.connection != null && player.connection.isAcceptingMessages()
                && CHANNEL.isRemotePresent(player.connection.connection);
    }
}
*///?}
