package org.fogofwarlabs.sc4j.scr.game.save.pool;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores unit records, list heads and finder membership.
 * <p>
 * The units of the active, dying, hidden and map-revealer lists and their subunits (a type with the {@code units.dat} subunit
 * flag) are saved: a section of one flag byte per slot, a plain DWORD count, a section with per saved unit the DWORD
 * zero-based slot and the classic {@code 336}-byte unit ({@link UnitSaveRecordUtils}, links and owner links as handles), the plain
 * DWORD head of the cloaked-unit list, eight plain DWORD heads and tails of the unit lists and twelve plain DWORD heads
 * of the owner lists. Before converting, the writer takes every saved unit that has finder edges out of the unit finder
 * and marks it; afterwards it puts the marked units back from the last slot down, which the reader repeats, so a saved
 * game and the game that saved it have the same finder order. The reader clears the whole pool, restores the saved units
 * and lists, and links every other slot into the free list.
 */
public final class UnitPoolSection implements SaveSection {
    private static final int SLOT_SIZE = Integer.BYTES;
    private static final int LIST_HEAD_COUNT = 8;
    private static final int CLOAKED_HEAD_COUNT = 1;
    private static final int BYTE_MASK = 0xFF;

    /**
     * The size of an entry: the DWORD slot and the record.
     */
    public static final int ENTRY_SIZE = SLOT_SIZE + UnitSaveRecordUtils.SIZE;
    /**
     * The size of the plain DWORDs after the entries: the cloaked-unit head, the unit list heads and the owner heads.
     */
    public static final int HEADS_SIZE = (CLOAKED_HEAD_COUNT + LIST_HEAD_COUNT + PlayerTable.PLAYER_SLOTS) * Integer.BYTES;

    private static final int RECORD_PREVIOUS = 0x00;
    private static final int RECORD_NEXT = 0x04;
    private static final int RECORD_PREVIOUS_OWNER = 0x68;
    private static final int RECORD_NEXT_OWNER = 0x6C;
    private static final int RECORD_GENERATION = 0xA5;

    /**
     * Creates a {@link UnitPoolSection} instance.
     */
    public UnitPoolSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        Game game = context.getGame();
        UnitPool pool = SaveGameUtils.require(game.getUnitSystem().getUnitPool(), "unit pool");
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();
        SaveReferences references = new SaveReferences(game);
        byte[] saved = new byte[pool.getCapacity()];
        int count = 0;

        for (int head : new int[]{pool.getFirstCreatedIndex(true), pool.getFirstDeadIndex(), pool.getFirstCreatedIndex(false),
                                  pool.getFirstRevealerIndex()}) {
            for (int poolIndex = head; poolIndex != 0; poolIndex = pool.getNextCreatedIndex(poolIndex)) {
                Unit subunit = pool.getUnit(poolIndex).getSubunit();

                if (subunit != null) {
                    int subunitType = subunit.getUnitId();

                    if (subunitType < ScrUnitIds.NONE && (units.getFlags(subunitType) & UnitTypeFlags.SUBUNIT) != 0) {
                        saved[subunit.getIndex() - 1] = 1;
                    }
                }

                saved[poolIndex - 1] = 1;
            }
        }

        for (byte flag : saved) {
            count += flag;
        }

        output.writeSection(saved);
        output.writeDword(count);

        if (count > 0) {
            ByteBuffer entries = ByteBuffer.allocate(count * ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);

            for (int slot = saved.length - 1; slot >= 0; slot--) {
                if (saved[slot] != 0) {
                    UnitSaveRecordUtils.leaveUnitFinder(game, pool.getUnit(slot + 1));
                }
            }

            for (int slot = 0; slot < saved.length; slot++) {
                if (saved[slot] == 0) {
                    continue;
                }

                int entry = entries.position();
                int poolIndex = slot + 1;
                ByteBuffer record = entries.slice(entry + SLOT_SIZE, UnitSaveRecordUtils.SIZE).order(ByteOrder.LITTLE_ENDIAN);

                entries.putInt(entry, slot);
                UnitSaveRecordUtils.write(pool.getUnit(poolIndex), record, units, references);
                record.putInt(RECORD_PREVIOUS, references.encodeUnitIndex(pool.getPreviousCreatedIndex(poolIndex)));
                record.putInt(RECORD_NEXT, references.encodeUnitIndex(pool.getNextCreatedIndex(poolIndex)));
                record.putInt(RECORD_PREVIOUS_OWNER, references.encodeUnitIndex(pool.getPreviousOwnerIndex(poolIndex)));
                record.putInt(RECORD_NEXT_OWNER, references.encodeUnitIndex(pool.getNextOwnerIndex(poolIndex)));
                record.put(RECORD_GENERATION, (byte) pool.getGeneration(poolIndex));
                entries.position(entry + ENTRY_SIZE);
            }

            for (int slot = saved.length - 1; slot >= 0; slot--) {
                if (saved[slot] != 0) {
                    UnitSaveRecordUtils.enterUnitFinder(game, pool.getUnit(slot + 1));
                }
            }

            output.writeSection(entries.array());
        }

        output.writeDword(references.encodeUnit(game.getUnitCloaking().getFirstCloakedUnit()));

        for (int head : pool.getListHeads()) {
            output.writeDword(references.encodeUnitIndex(head));
        }

        for (int owner = 0; owner < PlayerTable.PLAYER_SLOTS; owner++) {
            output.writeDword(references.encodeUnitIndex(pool.getFirstOwnerIndex(owner)));
        }
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     * @throws UncheckedIOException if the saved unit count or a saved slot does not fit the unit pool
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        Game game = context.getGame();
        UnitPool pool = SaveGameUtils.require(game.getUnitSystem().getUnitPool(), "unit pool");
        PlayerUpgrades upgrades = game.getPlayerTable().getPlayerUpgrades();
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();
        SaveReferences references = new SaveReferences(game);
        byte[] saved = input.readSection(pool.getCapacity());
        int count = input.readDword();
        int[] heads = new int[LIST_HEAD_COUNT];

        pool.clear();

        if (game.getUnitSystem().getUnitSpatialIndex() != null) {
            game.getUnitSystem().getUnitSpatialIndex().clear();
        }

        if (count != 0 && Integer.compareUnsigned(count, pool.getCapacity()) >= 0) {
            throw SaveFileReader.createFailure("The saved game has {0} units for a pool of {1}.", count, pool.getCapacity());
        }

        if (count > 0) {
            ByteBuffer entries = ByteBuffer.wrap(input.readSection(count * ENTRY_SIZE)).order(ByteOrder.LITTLE_ENDIAN);

            for (int entryIndex = 0; entryIndex < count; entryIndex++) {
                int entry = entryIndex * ENTRY_SIZE;
                int slot = entries.getInt(entry);
                ByteBuffer record = entries.slice(entry + SLOT_SIZE, UnitSaveRecordUtils.SIZE).order(ByteOrder.LITTLE_ENDIAN);

                if (slot < 0 || slot >= pool.getCapacity()) {
                    throw SaveFileReader.createFailure("A saved unit names the slot {0} of a pool of {1}.", slot, pool.getCapacity());
                }

                Unit unit = pool.getUnit(slot + 1);

                UnitSaveRecordUtils.read(unit, record, units, upgrades, references);
                pool.markLoaded(slot + 1,
                                record.get(RECORD_GENERATION) & BYTE_MASK,
                                references.decodeUnitIndex(record.getInt(RECORD_PREVIOUS)),
                                references.decodeUnitIndex(record.getInt(RECORD_NEXT)),
                                references.decodeUnitIndex(record.getInt(RECORD_PREVIOUS_OWNER)),
                                references.decodeUnitIndex(record.getInt(RECORD_NEXT_OWNER)));
            }
        }

        for (int slot = saved.length - 1; slot >= 0; slot--) {
            if (saved[slot] != 0) {
                UnitSaveRecordUtils.enterUnitFinder(game, pool.getUnit(slot + 1));
            }
        }

        Unit firstCloakedUnit = references.decodeUnit(input.readDword());

        for (int head = 0; head < LIST_HEAD_COUNT; head++) {
            heads[head] = references.decodeUnitIndex(input.readDword());
        }

        pool.restoreListHeads(heads);
        pool.rebuildFreeList();

        for (int owner = 0; owner < PlayerTable.PLAYER_SLOTS; owner++) {
            pool.setFirstOwnerIndexRaw(owner, references.decodeUnitIndex(input.readDword()));
        }

        UnitSaveRecordUtils.restoreImplicitLinks(game, firstCloakedUnit);
    }
}
