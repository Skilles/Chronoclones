package com.skilles.chronoclones.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Carrying an author's face from the server to the client.
 *
 * <p>The texture property is the entire point of the round trip: a profile that arrives without it
 * resolves to the same UUID-derived silhouette the client could already draw for itself, which is
 * the bug this exists to fix.
 */
class SkinPayloadsTest {

    private static RegistryAccess.Frozen registries;

    @BeforeAll
    static void captureRegistries() {
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    @DisplayName("a profile keeps its texture property across the wire")
    void texturesSurviveTheWire() {
        // Built with its properties: the two-argument constructor gives an immutable empty map,
        // and a profile with no textures is exactly the useless one this payload exists to replace.
        PropertyMap properties = new PropertyMap(
                ImmutableMultimap.of("textures", new Property("textures", "aGVsbG8=", "signed")));
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes("author".getBytes()), "Skilles", properties);

        SkinPayloads.Reply read = roundTrip(new SkinPayloads.Reply(
                profile.id(), Optional.of(profile)));

        assertTrue(read.profile().isPresent(), "the profile did not survive at all");
        GameProfile arrived = read.profile().get();
        assertEquals(profile.id(), arrived.id());
        assertEquals(profile.name(), arrived.name());

        Property textures = arrived.properties().get("textures").iterator().next();
        assertNotNull(textures, "the textures property is what the client needs and it is missing");
        assertEquals("aGVsbG8=", textures.value());
        assertEquals("signed", textures.signature());
    }

    @Test
    @DisplayName("an author the server cannot resolve travels as nothing, not as a broken profile")
    void unresolvedTravelsAsEmpty() {
        UUID author = UUID.nameUUIDFromBytes("stranger".getBytes());

        SkinPayloads.Reply read = roundTrip(new SkinPayloads.Reply(author, Optional.empty()));

        assertEquals(author, read.author());
        assertTrue(read.profile().isEmpty());
    }

    @Test
    @DisplayName("the request carries the author being asked about")
    void requestCarriesTheAuthor() {
        UUID author = UUID.nameUUIDFromBytes("author".getBytes());

        RegistryFriendlyByteBuf buffer = buffer();
        try {
            SkinPayloads.Request.STREAM_CODEC.encode(buffer, new SkinPayloads.Request(author));
            assertEquals(author, SkinPayloads.Request.STREAM_CODEC.decode(buffer).author());
        } finally {
            buffer.release();
        }
    }

    private static SkinPayloads.Reply roundTrip(SkinPayloads.Reply reply) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            SkinPayloads.Reply.STREAM_CODEC.encode(buffer, reply);
            return SkinPayloads.Reply.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
    }
}
