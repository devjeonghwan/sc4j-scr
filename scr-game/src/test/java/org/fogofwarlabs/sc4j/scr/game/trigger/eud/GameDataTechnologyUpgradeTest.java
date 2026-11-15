package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace.GameDataRegion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks technology and upgrade storage through game-data writes.
 */
final class GameDataTechnologyUpgradeTest {
    /**
     * The mask of the low BYTE.
     */
    private static final int BYTE_MASK = 0xFF;

    private static final int GAME_DATA = 0x57F0F0;

    private static final int CLASSIC_AVAILABLE = 0xDD34;
    private static final int CLASSIC_RESEARCHED = 0xDE54;
    private static final int LEGACY_RESEARCHING = 0xDF74;
    private static final int EXPANSION_RESEARCHED = 0x10050;
    private static final int RESEARCHING = 0x10140;
    private static final int CLASSIC_LEVEL = 0xE1C0;
    private static final int LEGACY_UPGRADING = 0xE3E8;
    private static final int EXPANSION_MAXIMUM = 0x10188;
    private static final int UPGRADING = 0x102F0;

    private static final int CLASSIC_TECHNOLOGY_COUNT = 24;
    private static final int EXPANSION_TECHNOLOGY_COUNT = 20;
    private static final int RESEARCHING_BYTES = 6;
    private static final int CLASSIC_UPGRADE_COUNT = 46;
    private static final int EXPANSION_UPGRADE_COUNT = 15;

    /**
     * Creates a {@link ClassicAddressSpace} instance backed by the supplied world tables.
     *
     * @param technology the technology storage, or {@code null}
     * @param upgrades   the upgrade storage, or {@code null}
     * @return the created {@link ClassicAddressSpace} instance
     */
    private static @NotNull ClassicAddressSpace createAddressSpace(@Nullable PlayerTechnology technology, @Nullable PlayerUpgrades upgrades) {
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new GameDataRegion(null, PlayerProgressTestUtils.createPlayerScores(), null, null, technology, upgrades, null, null));

        return space;
    }


    @Test
    void technology_tables_use_classic_game_data_offsets() {
        PlayerTechnology technology = PlayerProgressTestUtils.createPlayerTechnology(true);
        ClassicAddressSpace space = createAddressSpace(technology, PlayerProgressTestUtils.createPlayerUpgrades(true));

        space.writeDword(GAME_DATA + CLASSIC_AVAILABLE + 2 * CLASSIC_TECHNOLOGY_COUNT + 4, 0x00000100);

        Assertions.assertFalse(technology.isAvailable(2, ScrTechIds.SCANNER_SWEEP));
        Assertions.assertTrue(technology.isAvailable(2, ScrTechIds.TANK_SIEGE_MODE));
        Assertions.assertEquals(1, technology.readContextByte(CLASSIC_AVAILABLE + 2 * CLASSIC_TECHNOLOGY_COUNT + 5));

        technology.setResearched(1, ScrTechIds.OPTICAL_FLARE, true);

        Assertions.assertEquals(1, space.readDword(GAME_DATA + EXPANSION_RESEARCHED + EXPANSION_TECHNOLOGY_COUNT + 4) >>> 16 & BYTE_MASK);

        technology.setResearching(3, ScrTechIds.SPAWN_BROODLINGS, true);

        Assertions.assertEquals(0x20, space.readDword(GAME_DATA + RESEARCHING + 3 * RESEARCHING_BYTES - 2) >>> 24 & BYTE_MASK);

        space.writeDword(GAME_DATA + RESEARCHING + 3 * RESEARCHING_BYTES + 2, 0);

        Assertions.assertFalse(technology.isResearching(3, ScrTechIds.CONSUME));
    }

    @Test
    void researched_byte_keeps_the_written_value_and_counts_as_researched() {
        PlayerTechnology technology = PlayerProgressTestUtils.createPlayerTechnology(true);
        ClassicAddressSpace space = createAddressSpace(technology, PlayerProgressTestUtils.createPlayerUpgrades(true));

        space.writeDword(GAME_DATA + CLASSIC_RESEARCHED, 0x07);

        Assertions.assertEquals(7, technology.readContextByte(CLASSIC_RESEARCHED));
        Assertions.assertTrue(technology.isResearched(0, ScrTechIds.STIM_PACKS), "A nonzero byte counts as researched.");
    }

    @Test
    void upgrade_tables_use_classic_game_data_offsets() {
        PlayerUpgrades upgrades = PlayerProgressTestUtils.createPlayerUpgrades(true);
        ClassicAddressSpace space = createAddressSpace(PlayerProgressTestUtils.createPlayerTechnology(true), upgrades);

        upgrades.setLevel(4, ScrUpgradeIds.TERRAN_SHIP_WEAPONS, 3);

        Assertions.assertEquals(3, space.readDword(GAME_DATA + CLASSIC_LEVEL + 4 * CLASSIC_UPGRADE_COUNT + 8) >>> 8 & BYTE_MASK);

        space.writeDword(GAME_DATA + EXPANSION_MAXIMUM + 2 * EXPANSION_UPGRADE_COUNT + 2, 0x00000500);

        Assertions.assertEquals(5, upgrades.getMaximumLevel(2, ScrUpgradeIds.ARGUS_TALISMAN));

        upgrades.setUpgrading(1, ScrUpgradeIds.UNUSED_60, true);

        Assertions.assertEquals(0x10, space.readDword(GAME_DATA + UPGRADING + 8 + 4) >>> 24 & BYTE_MASK);
    }

    @Test
    void legacy_bits_are_inert_storage() {
        PlayerTechnology technology = PlayerProgressTestUtils.createPlayerTechnology(true);
        PlayerUpgrades upgrades = PlayerProgressTestUtils.createPlayerUpgrades(true);
        ClassicAddressSpace space = createAddressSpace(technology, upgrades);

        space.writeDword(GAME_DATA + LEGACY_RESEARCHING, -1);
        space.writeDword(GAME_DATA + LEGACY_UPGRADING, -1);

        Assertions.assertEquals(-1, space.readDword(GAME_DATA + LEGACY_RESEARCHING));
        Assertions.assertFalse(technology.isResearching(0, ScrTechIds.STIM_PACKS));
        Assertions.assertFalse(upgrades.isUpgrading(0, ScrUpgradeIds.TERRAN_INFANTRY_ARMOR));
    }

    @Test
    void technology_offsets_are_unsupported_without_world_tables() {
        ClassicAddressSpace space = createAddressSpace(null, null);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> space.readDword(GAME_DATA + CLASSIC_AVAILABLE));
    }
}
