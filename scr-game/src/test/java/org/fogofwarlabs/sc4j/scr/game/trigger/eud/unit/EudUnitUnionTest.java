package org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatSchema;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks kind-dependent union fields, pointer shadows and sprite conversion.
 */
final class EudUnitUnionTest {
    /**
     * The size of a classic unit table entry.
     */
    private static final int UNIT_ENTRY_SIZE = 336;
    private static final int FIRST_ENTRY = 0x59CCA8;
    private static final int SECOND_ENTRY = FIRST_ENTRY + UNIT_ENTRY_SIZE;
    private static final int THIRD_ENTRY = FIRST_ENTRY + 2 * UNIT_ENTRY_SIZE;
    private static final int FOURTH_ENTRY = FIRST_ENTRY + 3 * UNIT_ENTRY_SIZE;

    /**
     * The address of the first entry of the classic sprite table.
     */
    private static final int SPRITE_TABLE_ADDRESS = 0x629D98;
    /**
     * The size of a classic sprite table entry.
     */
    private static final int SPRITE_ENTRY_SIZE = 36;

    private static final int SPRITE_POINTER = 0x0C;
    private static final int FIRST_UNION_DWORD = 0xC0;
    private static final int SECOND_UNION_DWORD = 0xC4;
    private static final int THIRD_UNION_DWORD = 0xC8;
    private static final int FOURTH_UNION_DWORD = 0xCC;
    private static final int FIFTH_UNION_DWORD = 0xD0;
    private static final int SIXTH_UNION_DWORD = 0xD4;
    private static final int SEVENTH_UNION_DWORD = 0xD8;

    private final @NotNull UnitPool unitPool;
    private final @NotNull SpritePool spritePool;
    private final @NotNull ClassicAddressSpace addressSpace;

    /**
     * Creates a {@link EudUnitUnionTest} instance with synthetic type and unit/sprite pools.
     *
     * @throws IOException if the synthetic DAT table cannot be parsed
     */
    EudUnitUnionTest() throws IOException {
        this.unitPool     = new UnitPool(ClassicUnitTableRegion.ENTRY_COUNT);
        this.spritePool   = new SpritePool(ClassicSpriteTableRegion.ENTRY_COUNT);
        this.addressSpace = new ClassicAddressSpace();

        ByteBuffer flags = ByteBuffer.allocate(UnitsDat.UNIT_COUNT * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int unitTypeIdentifier = 0; unitTypeIdentifier < UnitsDat.UNIT_COUNT; unitTypeIdentifier++) {
            int value = 0;

            if (unitTypeIdentifier == ScrUnitIds.TERRAN_SCV) {
                value = UnitTypeFlags.WORKER;
            } else if (unitTypeIdentifier == ScrUnitIds.TERRAN_COMMAND_CENTER ||
                       unitTypeIdentifier == ScrUnitIds.RESOURCE_VESPENE_GEYSER) {
                value = UnitTypeFlags.BUILDING;
            }

            if (unitTypeIdentifier == ScrUnitIds.RESOURCE_MINERAL_FIELD || unitTypeIdentifier == ScrUnitIds.RESOURCE_VESPENE_GEYSER) {
                value |= UnitTypeFlags.RESOURCE_CONTAINER;
            }

            flags.putInt(value);
        }

        DatTable units = new DatSchema().fieldU32("Special Ability Flags", UnitsDat.UNIT_COUNT).parse(flags.array());

        this.addressSpace.add(new ClassicUnitTableRegion(this.unitPool, UnitsDat.createTypeTable(units), this.spritePool));
    }

    /**
     * Initializes the existing pool slot with the requested unit type.
     *
     * @param slot     the zero-based classic slot
     * @param unitType the unit type of the fixture
     * @return the initialized pool unit
     */
    private @NotNull Unit initializeUnit(int slot, int unitType) {
        Unit unit = this.unitPool.getUnit(slot + 1);

        unit.setUnitId(unitType);

        return unit;
    }

    @Test
    void building_members_copy_their_bytes() {
        Unit center = this.initializeUnit(0, ScrUnitIds.TERRAN_COMMAND_CENTER);
        Unit addon = this.initializeUnit(1, ScrUnitIds.TERRAN_COMSAT_STATION);

        this.addressSpace.writeDword(FIRST_ENTRY + FIRST_UNION_DWORD, SECOND_ENTRY);

        Assertions.assertSame(addon, center.getAddon());

        this.addressSpace.writeDword(FIRST_ENTRY + SECOND_UNION_DWORD, 0x0123_0107);

        Assertions.assertEquals(0x107, center.getAddonBuildType());
        Assertions.assertEquals(0x123, center.getUpgradeResearchTime());

        this.addressSpace.writeDword(FIRST_ENTRY + THIRD_UNION_DWORD, 0x0503_0201);

        Assertions.assertEquals(1, center.getResearchType());
        Assertions.assertEquals(2, center.getUpgradeType());
        Assertions.assertTrue(center.isLanding(), "The landing byte is nonzero.");
        Assertions.assertEquals(0x0503_0201, this.addressSpace.readDword(FIRST_ENTRY + THIRD_UNION_DWORD), "The raw landing byte reads back.");

        this.addressSpace.writeDword(FIRST_ENTRY + FOURTH_UNION_DWORD, 0xFFFF_0409);

        Assertions.assertEquals(4, center.getUpgradeLevel());
        Assertions.assertEquals(0x0409, this.addressSpace.readDword(FIRST_ENTRY + FOURTH_UNION_DWORD), "The upper WORD is not served.");
        Assertions.assertEquals(0, this.addressSpace.readDword(FIRST_ENTRY + FIFTH_UNION_DWORD), "A Command Center has no member at 0xD0.");

        this.addressSpace.writeDword(FIRST_ENTRY + FIFTH_UNION_DWORD, 77);

        Assertions.assertEquals(0, this.addressSpace.readDword(FIRST_ENTRY + FIFTH_UNION_DWORD));
    }

    @Test
    void geyser_is_served_as_a_building_before_its_resource_members() {
        Unit geyser = this.initializeUnit(0, ScrUnitIds.RESOURCE_VESPENE_GEYSER);

        this.addressSpace.writeDword(FIRST_ENTRY + SECOND_UNION_DWORD, 0x0002_0001);

        Assertions.assertEquals(1, geyser.getAddonBuildType());

        this.addressSpace.writeDword(FIRST_ENTRY + FIFTH_UNION_DWORD, 0x0100_0000 | 0x2000 | 5000);

        Assertions.assertEquals(5000 | 0x2000, geyser.getResources());
        Assertions.assertEquals(0x0100_0000 | 0x2000 | 5000, this.addressSpace.readDword(FIRST_ENTRY + FIFTH_UNION_DWORD));

        this.addressSpace.writeDword(FIRST_ENTRY + SEVENTH_UNION_DWORD, 0x0000_0107);

        Assertions.assertEquals(7, geyser.getResourceAreaIndex());
        Assertions.assertEquals(0x0107, this.addressSpace.readDword(FIRST_ENTRY + SEVENTH_UNION_DWORD));
    }

    @Test
    void worker_pointers_keep_shadows_per_member() {
        Unit worker = this.initializeUnit(0, ScrUnitIds.TERRAN_SCV);

        this.initializeUnit(1, ScrUnitIds.RESOURCE_MINERAL_FIELD);

        this.addressSpace.writeDword(FIRST_ENTRY + THIRD_UNION_DWORD, SECOND_ENTRY);

        Assertions.assertEquals(SECOND_ENTRY, this.addressSpace.readDword(FIRST_ENTRY + THIRD_UNION_DWORD));

        this.addressSpace.writeDword(FIRST_ENTRY + FIFTH_UNION_DWORD, FIRST_ENTRY + 3);

        Assertions.assertEquals(FIRST_ENTRY + 3, this.addressSpace.readDword(FIRST_ENTRY + FIFTH_UNION_DWORD),
                                "Not an entry start: the shadow is read.");
        Assertions.assertEquals(SECOND_ENTRY, this.addressSpace.readDword(FIRST_ENTRY + THIRD_UNION_DWORD),
                                "Another member's shadow is independent.");

        this.addressSpace.writeDword(FIRST_ENTRY + FIFTH_UNION_DWORD, SECOND_ENTRY);

        Assertions.assertEquals(SECOND_ENTRY, this.addressSpace.readDword(FIRST_ENTRY + FIFTH_UNION_DWORD));

        this.addressSpace.writeDword(SECOND_ENTRY + SIXTH_UNION_DWORD, FIRST_ENTRY);

        Assertions.assertEquals(FIRST_ENTRY, this.addressSpace.readDword(SECOND_ENTRY + SIXTH_UNION_DWORD), "The mineral's first gatherer.");

        this.addressSpace.writeDword(FIRST_ENTRY + FOURTH_UNION_DWORD, 0x0801_0010);

        Assertions.assertEquals(0x10, worker.getRepairTimer());
        Assertions.assertEquals(0x0801_0010, this.addressSpace.readDword(FIRST_ENTRY + FOURTH_UNION_DWORD));
    }

    @Test
    void hangar_fighter_vulture_and_pylon_members_store_their_fields() {
        Unit carrier = this.initializeUnit(0, ScrUnitIds.PROTOSS_CARRIER);
        Unit interceptor = this.initializeUnit(1, ScrUnitIds.PROTOSS_INTERCEPTOR);
        Unit pylon = this.initializeUnit(2, ScrUnitIds.PROTOSS_PYLON);
        Unit vulture = this.initializeUnit(3, ScrUnitIds.TERRAN_VULTURE);

        this.addressSpace.writeDword(FIRST_ENTRY + THIRD_UNION_DWORD, 0x7F03_0405);

        Assertions.assertEquals(5, carrier.getHangarCount(false));
        Assertions.assertEquals(4, carrier.getHangarCount(true));
        Assertions.assertEquals(0x0003_0405, this.addressSpace.readDword(FIRST_ENTRY + THIRD_UNION_DWORD), "The fourth byte is not served.");
        Assertions.assertEquals(0, this.addressSpace.readDword(FIRST_ENTRY + FOURTH_UNION_DWORD));

        this.addressSpace.writeDword(SECOND_ENTRY + FIRST_UNION_DWORD, FIRST_ENTRY);

        Assertions.assertSame(carrier, interceptor.getHangarParent());

        this.addressSpace.writeDword(SECOND_ENTRY + FOURTH_UNION_DWORD, 2);

        Assertions.assertEquals(2, this.addressSpace.readDword(SECOND_ENTRY + FOURTH_UNION_DWORD));

        int psiFieldSprite = SPRITE_TABLE_ADDRESS + 7 * SPRITE_ENTRY_SIZE;

        this.addressSpace.writeDword(THIRD_ENTRY + FIFTH_UNION_DWORD, psiFieldSprite);

        Assertions.assertSame(this.spritePool.getSprite(7), pylon.getPsiFieldSprite());
        Assertions.assertEquals(psiFieldSprite, this.addressSpace.readDword(THIRD_ENTRY + FIFTH_UNION_DWORD));

        this.addressSpace.writeDword(FOURTH_ENTRY + FIRST_UNION_DWORD, 0x1203);

        Assertions.assertEquals(3, vulture.getSpiderMineCount());
        Assertions.assertEquals(0, this.addressSpace.readDword(FOURTH_ENTRY + SECOND_UNION_DWORD));
    }

    @Test
    void sprite_pointer_converts_back_or_keeps_its_shadow() {
        Unit unit = this.initializeUnit(0, ScrUnitIds.TERRAN_MARINE);

        this.addressSpace.writeDword(FIRST_ENTRY + SPRITE_POINTER, SPRITE_TABLE_ADDRESS + 4 * SPRITE_ENTRY_SIZE);

        Assertions.assertSame(this.spritePool.getSprite(4), unit.getSpriteOrNull());

        this.addressSpace.writeDword(FIRST_ENTRY + SPRITE_POINTER, SPRITE_TABLE_ADDRESS + 1);

        Assertions.assertSame(this.spritePool.getSprite(4), unit.getSpriteOrNull(), "Not an entry start: only the shadow changes.");
        Assertions.assertEquals(SPRITE_TABLE_ADDRESS + 1, this.addressSpace.readDword(FIRST_ENTRY + SPRITE_POINTER));

        this.addressSpace.writeDword(FIRST_ENTRY + SPRITE_POINTER, 0);

        Assertions.assertNull(unit.getSpriteOrNull());
        Assertions.assertEquals(0, this.addressSpace.readDword(FIRST_ENTRY + SPRITE_POINTER));
    }
}
