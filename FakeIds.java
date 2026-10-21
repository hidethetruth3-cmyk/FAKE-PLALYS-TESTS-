package com.fakeplayermod;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Fake players get UUIDs with a magic prefix so the client can recognise them and apply the black skin. */
public final class FakeIds {
    public static final long MAGIC = 0xFA4EB07DL;

    public static UUID forName(String name) {
        long low = UUID.nameUUIDFromBytes(("fakeplayer:" + name).getBytes(StandardCharsets.UTF_8)).getLeastSignificantBits();
        return new UUID((MAGIC << 32) | (name.hashCode() & 0xFFFFFFFFL), low);
    }

    public static boolean isFake(UUID id) {
        return id != null && (id.getMostSignificantBits() >>> 32) == MAGIC;
    }
    private FakeIds() {}
}
