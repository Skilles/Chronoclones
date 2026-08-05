package com.skilles.chronoclones.network;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.authlib.GameProfile;
import com.skilles.chronoclones.Chronoclones;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//? if >=26 {
import net.minecraft.util.Util;
//?} else {
/*import net.minecraft.Util;
*///?}
import org.jspecify.annotations.NonNull;

public final class SkinPayloads {

    private SkinPayloads() {}

    public record Request(UUID author) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Request> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("request_author"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.composite(UUIDUtil.STREAM_CODEC.cast(), Request::author, Request::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Reply(UUID author, Optional<GameProfile> profile) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Reply> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("author_profile"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC.cast(), Reply::author,
                        ByteBufCodecs.optional(ByteBufCodecs.GAME_PROFILE).cast(), Reply::profile,
                        Reply::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record Known(Optional<GameProfile> profile, long at) {

        boolean staleMiss(long now) {
            return profile.isEmpty() && now - at >= RETRY_MISSES_AFTER_MS;
        }
    }

    private static final Map<UUID, Known> KNOWN = new ConcurrentHashMap<>();

    /** Most misses are ids that were never real accounts, so retrying is mostly waste. */
    private static final long RETRY_MISSES_AFTER_MS = 30L * 60L * 1000L;

    private static final Set<UUID> LOOKING = ConcurrentHashMap.newKeySet();

    private static final int MAX_REMEMBERED = 512;

    public static void handleRequest(Request request, ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        Known known = KNOWN.get(request.author());
        if (known != null && !known.staleMiss(System.currentTimeMillis())) {
            com.skilles.chronoclones.platform.PlatformNetwork.sendToPlayer(player,
                    new Reply(request.author(), known.profile()));
            return;
        }

        ServerPlayer online = server.getPlayerList().getPlayer(request.author());
        if (online != null) {
            remember(request.author(), Optional.of(online.getGameProfile()));
            com.skilles.chronoclones.platform.PlatformNetwork.sendToPlayer(player,
                    new Reply(request.author(), Optional.of(online.getGameProfile())));
            return;
        }

        if (!LOOKING.add(request.author())) {
            return;
        }
        Util.backgroundExecutor().execute(() -> {
            Optional<GameProfile> found;
            try {
                //? if >=26 {
                found = server.services().profileResolver().fetchById(request.author());
                //?} else {
                /*com.mojang.authlib.yggdrasil.ProfileResult fetched =
                        server.getSessionService().fetchProfile(request.author(), false);
                found = fetched == null ? Optional.empty() : Optional.of(fetched.profile());
                *///?}
            } catch (RuntimeException failed) {
                Chronoclones.LOGGER.debug("Could not look up the author {}", request.author(), failed);
                found = Optional.empty();
            }
            Optional<GameProfile> resolved = found;
            server.execute(() -> {
                LOOKING.remove(request.author());
                remember(request.author(), resolved);
                com.skilles.chronoclones.platform.PlatformNetwork.sendToAllPlayers(
                        new Reply(request.author(), resolved));
            });
        });
    }

    private static void remember(UUID author, Optional<GameProfile> profile) {
        if (KNOWN.size() >= MAX_REMEMBERED) {
            KNOWN.clear();
        }
        KNOWN.put(author, new Known(profile, System.currentTimeMillis()));
    }

    public static void clear() {
        KNOWN.clear();
        LOOKING.clear();
    }
}
