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

class SkinPayloadsTest {

    private static RegistryAccess.Frozen registries;

    @BeforeAll
    static void captureRegistries() {
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    @DisplayName("a profile keeps its texture property across the wire")
    void texturesSurviveTheWire() {
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
