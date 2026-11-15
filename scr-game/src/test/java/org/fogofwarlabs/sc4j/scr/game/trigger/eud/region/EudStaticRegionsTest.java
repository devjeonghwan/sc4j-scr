package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.map.MapStringSection;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicDynamicRegionsUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Checks static storage, live host tables and values, and local interface inputs.
 */
final class EudStaticRegionsTest {
    /**
     * A network game's turns per second below the provider's 24.
     */
    private static final int LOW_TURN_RATE = 8;
    /**
     * The timeout of Fastest at {@link #LOW_TURN_RATE} turns per second.
     */
    private static final int LOW_TURN_RATE_TIMEOUT = 2976;
    /**
     * The turn budget a game loop takes.
     */
    private static final int FRAME_BUDGET = 1000;
    private static final int BYTE_MASK = 0xFF;

    @Test
    void container_self_regions_are_zeroed_storage() {
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.addAll(ClassicStorageRegion.createContainerSelfRegions());

        for (int base : new int[]{0xA141A0, 0xA67830, 0xA67840, 0xA67850, 0xA67860, 0xA67870, 0x190F4920, 0x19184640, 0x19184650}) {
            Assertions.assertEquals(0, space.readDword(base));
        }

        space.writeDword(0xA67830, 7);

        Assertions.assertEquals(7, space.readDword(0xA67830), "The object's own QWORD +0x18 keeps a write.");
    }

    @Test
    void type_two_a_block_and_string_prefix_hold_their_initial_bytes() {
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.addAll(ClassicDynamicRegionsUtils.create(new MapStringSection(new byte[0x40], false)));

        Assertions.assertEquals(0xE770B0, space.readDword(0xE77080 + 0x08), "The block holds the classic address of the 0x12DA buffer.");
        Assertions.assertEquals(0x12DA, space.readDword(0xE77080 + 0x0C));
        Assertions.assertEquals(0x280, space.readDword(0xE77080 + 0x28));
        Assertions.assertEquals(0x1E0, space.readDword(0xE77080 + 0x2C));
        Assertions.assertEquals(0, space.readDword(ClassicDynamicRegionsUtils.STRING_SECTION_BASE - 8),
                                "The static before the string section is zeroed.");

        space.writeDword(ClassicDynamicRegionsUtils.STRING_SECTION_BASE - 4, 5);

        Assertions.assertEquals(5, space.readDword(ClassicDynamicRegionsUtils.STRING_SECTION_BASE - 4));
    }

    @Test
    void random_state_write_replaces_the_state_and_keeps_the_counters() {
        LcgRandom random = new LcgRandom(1234);

        random.next(3);

        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicHostValueRegion(ClassicHostValueRegion.RANDOM_STATE, random::getState, random::writeState));

        Assertions.assertEquals(random.getState(), space.readDword(0x51CA14));

        space.writeDword(0x51CA14, 42);

        Assertions.assertEquals(42, random.getState());
        Assertions.assertEquals(1, random.getTotalRandomCounts(), "The generic copy replaces the DWORD alone.");
    }

    @Test
    void host_tables_serve_and_store_their_live_values() {
        int[] multipliers = {0, 0, 1, 2, 3,
                             0, 0, 0, 0, 0,
                             1, 0, 128, 192, 256,
                             2, 0, 256, 128, 64,
                             3, 0, 256, 256, 256,
                             4, 0, 256, 256, 256};
        byte[] overlays = new byte[ImagesDat.IMAGE_COUNT];

        overlays[5] = 8;

        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicHostTableRegion(0x515B70, multipliers));
        space.addAll(ClassicStorageRegion.createTerrainContourRegions());
        space.add(new ClassicHostByteTableRegion(ClassicHostByteTableRegion.IMAGE_DAMAGE_OVERLAYS,
                                                 overlays.length,
                                                 index -> overlays[index],
                                                 (index, value) -> overlays[index] = (byte) value));

        Assertions.assertEquals(192, space.readDword(0x515B70 + (6 + 5 + 2) * 4), "Explosive against medium.");

        space.writeDword(0x515B70 + (6 + 5 + 2) * 4, 256);

        Assertions.assertEquals(256, multipliers[13]);
        Assertions.assertEquals(0x0001FC00, space.readDword(0x515B58), "The contour steps: WORDs -1024, 1.");
        Assertions.assertEquals(0xFFFF0400, space.readDword(0x515B5C));
        Assertions.assertEquals(8, space.readDword(0x5240D0 + 4) >>> Byte.SIZE & BYTE_MASK);

        space.writeDword(0x5240D0, 0x00000302);

        Assertions.assertEquals(2, overlays[0]);
        Assertions.assertEquals(3, overlays[1]);
    }

    @Test
    void local_interface_values_are_inputs_or_live_tables() {
        LocalInterfaceState state = new LocalInterfaceState();
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicHostValueRegion(LocalInterfaceState.GLUE_PALETTE_ADDRESS, state::getGluePalette, state::writeGluePalette));
        space.add(new ClassicStorageRegion(LocalInterfaceState.SCROLL_STEPS_ADDRESS, state.getScrollStepBytes()));
        space.add(ClassicHostValueRegion.createSinglePlayerTurnBudgetRegion());

        Assertions.assertThrows(UnsupportedOperationException.class, () -> space.readDword(0x50E064), "The glue palette is a local input.");

        state.setGluePalette(4);

        Assertions.assertEquals(4, space.readDword(0x50E064));
        Assertions.assertEquals(64, state.getScrollStep(6, 6), "The fastest row ends with 64 pixels.");

        space.writeDword(0x513B68 + 44, 0x10101010);

        Assertions.assertEquals(16, state.getScrollStep(6, 2));
        Assertions.assertEquals(1000, space.readDword(0x51CEA0));

        space.writeDword(0x51CEA0, 5);

        Assertions.assertEquals(1000, space.readDword(0x51CEA0), "Type 2: the write is ignored.");
    }

    @Test
    void turn_budget_of_a_multiplayer_game_reads_the_live_budget_and_ignores_writes() {
        GameSpeed timing = new GameSpeed(GameSpeed.FASTEST, 1, 0, 0);
        ClassicAddressSpace space = new ClassicAddressSpace();

        timing.startSession(1, LOW_TURN_RATE, 0);
        space.add(ClassicHostValueRegion.createTurnBudgetRegion(timing));

        Assertions.assertEquals(FRAME_BUDGET, space.readDword(ClassicHostValueRegion.TURN_BUDGET), "The game start stores 1000.");

        timing.takeFrameBudget();
        timing.addTurnTimeout();

        Assertions.assertEquals(LOW_TURN_RATE_TIMEOUT, space.readDword(ClassicHostValueRegion.TURN_BUDGET), "0 after the loop, then the timeout.");

        timing.takeFrameBudget();
        space.writeDword(ClassicHostValueRegion.TURN_BUDGET, 5);

        Assertions.assertEquals(LOW_TURN_RATE_TIMEOUT - FRAME_BUDGET, space.readDword(ClassicHostValueRegion.TURN_BUDGET),
                                "A loop without a turn step only takes 1000; the write is ignored.");
    }

    @Test
    void hidden_type_two_storage_ignores_writes() {
        ClassicAddressSpace space = new ClassicAddressSpace();
        List<ClassicRegion> storage = ClassicStorageRegion.createEmulatorStorageRegions();

        space.addAll(storage);

        space.writeDword(0x51CED0, -1);

        Assertions.assertEquals(0, space.readDword(0x51CED0));
    }
}
