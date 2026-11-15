package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Verifies decoded {@code GCFG} version blocks, {@code BFIX} flags and missing-extension defaults.
 */
final class ReplayGameVersionTest {
    private static final int VERSION_BLOCK_SIZE = 25;
    private static final int VERSION_BLOCK_BUILD_OFFSET = 4;
    private static final int VERSION_BLOCK_KIND_OFFSET = 0x10;
    private static final int BUILD = 8152;
    private static final byte KIND = 5;

    @Test
    void get_game_version_should_keep_zero_values_without_the_sections() throws IOException {
        ReplayGameVersion version = ReplayFile.parse(ExtendedReplayScenario.createReplay(new byte[0], false)).getGameVersion();

        Assertions.assertFalse(version.hasVersionSection());
        Assertions.assertFalse(version.hasFlagsSection());
        Assertions.assertEquals(0, version.getBuild());
        Assertions.assertEquals(0, version.getKind());
        Assertions.assertEquals(0L, version.getFlags());
    }

    @Test
    void get_game_version_should_decode_version_block_and_flags() throws IOException {
        byte[] block = new byte[VERSION_BLOCK_SIZE];

        ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 1).putInt(VERSION_BLOCK_BUILD_OFFSET, BUILD);
        block[VERSION_BLOCK_KIND_OFFSET] = KIND;

        byte[] flags = {1, 0, 0, 0, 0, 0, 0, 0};
        byte[] flagsSection = ExtendedReplayScenario.encodeSection(flags, flags);
        byte[] versionSection = ExtendedReplayScenario.encodeSection(block, ExtendedReplayScenario.compress(block));
        ByteArrayOutputStream extensions = new ByteArrayOutputStream();

        extensions.write(ExtendedReplayScenario.encodeExtension("SKIN", new byte[]{1, 2, 3}));
        extensions.write(ExtendedReplayScenario.encodeExtension("BFIX", flagsSection));
        extensions.write(ExtendedReplayScenario.encodeExtension("GCFG", versionSection));

        ReplayGameVersion version = ReplayFile.parse(ExtendedReplayScenario.createReplay(extensions.toByteArray(), true)).getGameVersion();

        Assertions.assertTrue(version.hasVersionSection());
        Assertions.assertTrue(version.hasFlagsSection());
        Assertions.assertEquals(BUILD, version.getBuild());
        Assertions.assertEquals(KIND, version.getKind());
        Assertions.assertEquals(1L, version.getFlags());
    }
}
