package com.skilles.chronoclones.client;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;
import com.skilles.chronoclones.network.SkinPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The faces of the people whose routines are running nearby.
 *
 * <p>Kept here rather than on the clone entity, and keyed by author rather than by clone: one
 * person's routine can be running on four clones of one anchor and on every anchor in a base, and
 * all of them want the same answer.
 *
 * <p>Until the answer arrives a clone wears the silhouette vanilla derives from its author's id,
 * which is what it wore before any of this existed. Nothing waits on the network to draw a frame.
 */
public final class AuthorSkins {

    private AuthorSkins() {}

    private static final Map<UUID, Supplier<PlayerSkin>> KNOWN = new ConcurrentHashMap<>();

    /** Who has been asked about, so walking past a row of anchors sends one question. */
    private static final Set<UUID> ASKED = ConcurrentHashMap.newKeySet();

    /**
     * The skin to draw this author with, asking the server about them the first time they are seen.
     */
    public static PlayerSkin of(UUID author) {
        Supplier<PlayerSkin> known = KNOWN.get(author);
        if (known != null) {
            return known.get();
        }

        // Somebody on this server right now is already in the tab list, skin and all. That is the
        // ordinary case -- most clones are running somebody's own routine while they watch -- and
        // it needs no question asked of anybody.
        Supplier<PlayerSkin> online = fromPlayerList(author);
        if (online != null) {
            KNOWN.put(author, online);
            return online.get();
        }

        if (ASKED.add(author)) {
            ClientPacketDistributor.sendToServer(new SkinPayloads.Request(author));
        }
        return DefaultPlayerSkin.get(author);
    }

    /**
     * The live skin of a player this client can see, or null if they are not here.
     *
     * <p>Read through the entry rather than copied out of it, so somebody changing their skin
     * mid-session changes the clones wearing it.
     */
    private static @Nullable Supplier<PlayerSkin> fromPlayerList(UUID author) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return null;
        }
        PlayerInfo info = connection.getPlayerInfo(author);
        return info == null ? null : info::getSkin;
    }

    /**
     * An answer. A server that could not resolve the author is remembered as such, so the question
     * is not asked again every time a clone comes back into view.
     */
    public static void accept(SkinPayloads.Reply reply) {
        reply.profile().ifPresentOrElse(
                profile -> KNOWN.put(reply.author(), lookupFor(profile)),
                () -> KNOWN.put(reply.author(), unresolved(reply.author())));
    }

    private static Supplier<PlayerSkin> lookupFor(GameProfile profile) {
        // Insecure: a routine's author is not the player holding the connection, so their textures
        // carry no signature this client could check, and refusing them would mean refusing every
        // skin this feature exists to show.
        return Minecraft.getInstance().getSkinManager().createLookup(profile, false);
    }

    private static Supplier<PlayerSkin> unresolved(UUID author) {
        PlayerSkin fallback = DefaultPlayerSkin.get(author);
        return () -> fallback;
    }

    /** Forgotten on disconnect: the next server may know different people by the same ids. */
    public static void forget() {
        KNOWN.clear();
        ASKED.clear();
    }
}
