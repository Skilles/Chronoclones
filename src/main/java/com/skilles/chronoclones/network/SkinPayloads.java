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
import net.minecraft.util.Util;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * Asking who wrote a routine, so a clone can wear their face.
 *
 * <p>A clone syncs its author's id and name, which is enough to draw the silhouette vanilla derives
 * from a UUID and nothing more: the client's skin manager reads textures off a profile's properties,
 * and a profile built from an id and a name has none. So the client asks the server, once per
 * author, and the server answers with the real profile.
 *
 * <p>Once per author rather than once per clone: an anchor with a splitter runs four clones and a
 * base may hold dozens of anchors, all of them very often the same person's.
 */
public final class SkinPayloads {

    private SkinPayloads() {}

    /** Client -> server: "whose face is this?" */
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

    /**
     * Server -> client: the author's profile, or nothing if this server cannot say.
     *
     * @param profile complete with its texture properties, which is the whole point of asking
     */
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

    /**
     * What this server has worked out, and when.
     *
     * <p>Misses are remembered too, or every client walking past an unresolvable author would send
     * the server looking again. They are remembered for a while rather than forever: an id that
     * cannot be resolved on an offline-mode server never will be, but one that failed because the
     * profile service was having a bad afternoon deserves another try eventually.
     *
     * @param at when this was learned, for deciding whether a miss has gone stale
     */
    private record Known(Optional<GameProfile> profile, long at) {

        boolean staleMiss(long now) {
            return profile.isEmpty() && now - at >= RETRY_MISSES_AFTER_MS;
        }
    }

    private static final Map<UUID, Known> KNOWN = new ConcurrentHashMap<>();

    /**
     * How long a failed lookup stands before the server will try that author again.
     *
     * <p>Deliberately long. The common reason a lookup fails is that the id is not a real account
     * -- every id on an offline-mode server, and every routine traded in from one -- and retrying
     * those achieves nothing but traffic. Half an hour costs an outage one stale session and costs
     * everything else nothing.
     */
    private static final long RETRY_MISSES_AFTER_MS = 30L * 60L * 1000L;

    /** Lookups in flight, so a hundred clients asking at once send one query rather than a hundred. */
    private static final Set<UUID> LOOKING = ConcurrentHashMap.newKeySet();

    /**
     * A ceiling on what one server remembers, since the ids come from clients.
     *
     * <p>Generous: a server with more than this many distinct routine authors on it has bigger
     * things to think about than a skin cache.
     */
    private static final int MAX_REMEMBERED = 512;

    public static void handleRequest(Request request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        Known known = KNOWN.get(request.author());
        if (known != null && !known.staleMiss(System.currentTimeMillis())) {
            context.reply(new Reply(request.author(), known.profile()));
            return;
        }

        // Somebody on this server right now: their profile is already here, textures and all.
        ServerPlayer online = server.getPlayerList().getPlayer(request.author());
        if (online != null) {
            remember(request.author(), Optional.of(online.getGameProfile()));
            context.reply(new Reply(request.author(), Optional.of(online.getGameProfile())));
            return;
        }

        // Otherwise it is a lookup, which may go out to the network, so it does not happen on a
        // thread anybody is waiting on. Whoever asked gets an answer when it lands.
        if (!LOOKING.add(request.author())) {
            return;
        }
        Util.backgroundExecutor().execute(() -> {
            Optional<GameProfile> found;
            try {
                found = server.services().profileResolver().fetchById(request.author());
            } catch (RuntimeException failed) {
                Chronoclones.LOGGER.debug("Could not look up the author {}", request.author(), failed);
                found = Optional.empty();
            }
            Optional<GameProfile> resolved = found;
            server.execute(() -> {
                LOOKING.remove(request.author());
                remember(request.author(), resolved);
                // To everybody, not just whoever asked: the others are looking at the same clones
                // and would each ask their own question a moment later.
                PacketDistributor.sendToAllPlayers(new Reply(request.author(), resolved));
            });
        });
    }

    private static void remember(UUID author, Optional<GameProfile> profile) {
        if (KNOWN.size() >= MAX_REMEMBERED) {
            KNOWN.clear();
        }
        KNOWN.put(author, new Known(profile, System.currentTimeMillis()));
    }

    /** Forgets everything when the server does, so a restart re-reads whatever changed. */
    public static void clear() {
        KNOWN.clear();
        LOOKING.clear();
    }
}
