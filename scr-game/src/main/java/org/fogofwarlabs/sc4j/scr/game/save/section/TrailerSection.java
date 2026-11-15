package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.map.MapStringSection;
import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameContext;
import org.fogofwarlabs.sc4j.scr.game.state.GameSetup;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes and restores tagged trailer parts after the foliage.
 * Each part contains its tag, a plain DWORD stored size and its stored bytes:
 * <ul>
 * <li>{@code SHDR}: a section of the WORD {@link #HEADER_VERSION}, a zero WORD, the DWORD number of {@code SKIN} entries
 * and the DWORD number of {@code SOWN} entries; written only when there are skin entries;</li>
 * <li>{@code SKIN}: a section of the skin map in key order, six bytes per unit: the DWORD unit key (pool index + 1), then
 * the high and the low byte of the unit's skin WORD ({@link Unit#getSkin()}); written only when there are entries;</li>
 * <li>{@code SOWN}: a section of the sprite owner map in key order, eight bytes per registered sprite: the DWORD sprite
 * key (pool index + 1) and the registered DWORD ({@link SpritePool#getRegistrationValue}); a freed sprite keeps its
 * registration; written only when there are entries;</li>
 * <li>{@code LMTS}: a section of the seven pool limit DWORDs ({@link GameContext#getLimits()});</li>
 * <li>{@code STR } or {@code STRx} ({@code STRx} for an extended string section): the map string section bytes as they
 * are, not a section.</li>
 * </ul>
 */
public final class TrailerSection implements SaveSection {
    /**
     * The tag of the header part.
     */
    static final int HEADER_TAG = 'S' | 'H' << Byte.SIZE | 'D' << Short.SIZE | 'R' << (Integer.SIZE - Byte.SIZE);
    /**
     * The tag of the unit skin part.
     */
    static final int SKIN_TAG = 'S' | 'K' << Byte.SIZE | 'I' << Short.SIZE | 'N' << (Integer.SIZE - Byte.SIZE);
    /**
     * The tag of the sprite owner part.
     */
    static final int OWNER_TAG = 'S' | 'O' << Byte.SIZE | 'W' << Short.SIZE | 'N' << (Integer.SIZE - Byte.SIZE);
    /**
     * The tag of the limits part.
     */
    static final int LIMITS_TAG = 'L' | 'M' << Byte.SIZE | 'T' << Short.SIZE | 'S' << (Integer.SIZE - Byte.SIZE);
    /**
     * The tag of a classic string section.
     */
    static final int STRING_TAG = 'S' | 'T' << Byte.SIZE | 'R' << Short.SIZE | ' ' << (Integer.SIZE - Byte.SIZE);
    /**
     * The tag of an extended string section.
     */
    static final int EXTENDED_STRING_TAG = 'S' | 'T' << Byte.SIZE | 'R' << Short.SIZE | 'x' << (Integer.SIZE - Byte.SIZE);

    private static final int HEADER_VERSION = 1;
    private static final int HEADER_SIZE = 0x0C;
    private static final int HEADER_VERSION_OFFSET = 0x00;
    private static final int HEADER_SKIN_COUNT = 0x04;
    private static final int HEADER_OWNER_COUNT = 0x08;
    private static final int SKIN_ENTRY_SIZE = 6;
    private static final int OWNER_ENTRY_SIZE = 8;
    private static final int BYTE_MASK = 0xFF;

    /**
     * Reads the pool limits from a trailer.
     *
     * @param input the file input at the trailer
     * @return the seven limits, or {@code null} when the trailer has no {@code LMTS} part
     */
    public static int @Nullable [] readLimits(@NotNull SaveFileReader input) {
        while (input.getRemaining() > 0) {
            int tag = input.readDword();
            int size = input.readDword();

            if (tag == LIMITS_TAG) {
                ByteBuffer limits = ByteBuffer.wrap(input.readSection(GameSetup.LIMIT_COUNT * Integer.BYTES)).order(ByteOrder.LITTLE_ENDIAN);
                int[] values = new int[GameSetup.LIMIT_COUNT];

                for (int index = 0; index < GameSetup.LIMIT_COUNT; index++) {
                    values[index] = limits.getInt(index * Integer.BYTES);
                }

                return values;
            }

            input.seek(input.getPosition() + size);
        }

        return null;
    }

    /**
     * Reads the map string section from a trailer.
     *
     * @param input the file input at the trailer
     * @return the string section, or {@code null} when the trailer has none
     */
    public static @Nullable MapStringSection readStrings(@NotNull SaveFileReader input) {
        while (input.getRemaining() > 0) {
            int tag = input.readDword();
            int size = input.readDword();

            if (tag == STRING_TAG || tag == EXTENDED_STRING_TAG) {
                return new MapStringSection(input.readBytes(size), tag == EXTENDED_STRING_TAG);
            }

            input.seek(input.getPosition() + size);
        }

        return null;
    }

    private static void writePart(@NotNull SaveFileWriter output, int tag, byte @NotNull [] payload) {
        SaveFileWriter part = new SaveFileWriter();

        part.writeSection(payload);

        byte[] stored = part.toByteArray();

        output.writeDword(tag);
        output.writeDword(stored.length);
        output.writeBytes(stored);
    }

    private static byte @NotNull [] createHeader(int skinCount, int ownerCount) {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        header.putShort(HEADER_VERSION_OFFSET, (short) HEADER_VERSION);
        header.putInt(HEADER_SKIN_COUNT, skinCount);
        header.putInt(HEADER_OWNER_COUNT, ownerCount);

        return header.array();
    }

    /**
     * Collects the units the skin map holds: every allocated unit, in key order (the map's entry is erased by the
     * release).
     *
     * @param units the unit pool
     * @return the units with a skin entry
     */
    private static @NotNull List<Unit> collectSkins(@NotNull UnitPool units) {
        List<Unit> skins = new ArrayList<>();

        for (int poolIndex = 1; poolIndex <= units.getCapacity(); poolIndex++) {
            if (units.isAllocated(poolIndex)) {
                skins.add(units.getUnit(poolIndex));
            }
        }

        return skins;
    }

    private static byte @NotNull [] createSkinRecords(@NotNull List<Unit> skins) {
        ByteBuffer entries = ByteBuffer.allocate(skins.size() * SKIN_ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (Unit unit : skins) {
            entries.putInt(unit.getIndex());
            entries.put((byte) (unit.getSkin() >>> Byte.SIZE));
            entries.put((byte) unit.getSkin());
        }

        return entries.array();
    }

    private static void readSkins(@NotNull ByteBuffer entries, @NotNull UnitPool units) {
        while (entries.hasRemaining()) {
            int key = entries.getInt();
            int high = entries.get() & BYTE_MASK;
            int low = entries.get() & BYTE_MASK;

            if (key < 1 || key > units.getCapacity()) {
                throw SaveFileReader.createFailure("The saved skin map has the unit key {0}.", key);
            }

            units.getUnit(key).setSkin(high << Byte.SIZE | low);
        }
    }

    private static int countOwners(@NotNull SpritePool sprites) {
        int count = 0;

        for (int poolIndex = 0; poolIndex < sprites.getCapacity(); poolIndex++) {
            if (sprites.isRegistered(poolIndex)) {
                count++;
            }
        }

        return count;
    }

    private static byte @NotNull [] createOwnerRecords(@NotNull SpritePool sprites, int count) {
        ByteBuffer entries = ByteBuffer.allocate(count * OWNER_ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int poolIndex = 0; poolIndex < sprites.getCapacity(); poolIndex++) {
            if (!sprites.isRegistered(poolIndex)) {
                continue;
            }

            entries.putInt(poolIndex + 1);
            entries.putInt(sprites.getRegistrationValue(poolIndex));
        }

        return entries.array();
    }

    private static void readOwners(@NotNull ByteBuffer entries, @NotNull SpritePool sprites) {
        while (entries.hasRemaining()) {
            int key = entries.getInt();
            int value = entries.getInt();

            if (key < 1 || key > sprites.getCapacity()) {
                throw SaveFileReader.createFailure("The saved sprite owner map has the sprite key {0}.", key);
            }

            sprites.restoreRegistration(key - 1, value);
        }
    }

    private static byte @NotNull [] createLimitRecords(int @NotNull [] limits) {
        ByteBuffer values = ByteBuffer.allocate(GameSetup.LIMIT_COUNT * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int limit : limits) {
            values.putInt(limit);
        }

        return values.array();
    }


    /**
     * Creates a {@link TrailerSection} instance.
     */
    public TrailerSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     * @throws UnsupportedOperationException if the unit pool or the sprite pool is not initialized
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        Game game = context.getGame();
        List<Unit> skins = collectSkins(game.getUnitSystem().requireUnitPool());
        SpritePool sprites = game.getSpriteSystem().requireSpritePool();
        int owners = countOwners(sprites);
        MapStringSection strings = SaveGameUtils.require(game.getInterfaceLink().getMessageLines().getMapStringSection(), "map string section");

        if (!skins.isEmpty()) {
            writePart(output, HEADER_TAG, createHeader(skins.size(), owners));
            writePart(output, SKIN_TAG, createSkinRecords(skins));
        }

        if (owners != 0) {
            writePart(output, OWNER_TAG, createOwnerRecords(sprites, owners));
        }

        writePart(output, LIMITS_TAG, createLimitRecords(SaveGameUtils.require(game.getGameContext().getLimits(), "limits")));
        output.writeDword(strings.isExtended() ? EXTENDED_STRING_TAG : STRING_TAG);
        output.writeDword(strings.getSize());
        output.writeBytes(strings.toByteArray());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     * @throws UncheckedIOException          if a part's stored size differs from the bytes read for it
     * @throws UnsupportedOperationException if the unit pool or the sprite pool is not initialized
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        Game game = context.getGame();
        int skinCount = 0;
        int ownerCount = 0;

        while (input.getRemaining() > 0) {
            int tag = input.readDword();
            int size = input.readDword();
            int end = input.getPosition() + size;

            if (tag == HEADER_TAG) {
                ByteBuffer header = ByteBuffer.wrap(input.readSection(HEADER_SIZE)).order(ByteOrder.LITTLE_ENDIAN);

                skinCount  = header.getInt(HEADER_SKIN_COUNT);
                ownerCount = header.getInt(HEADER_OWNER_COUNT);
            } else if (tag == SKIN_TAG) {
                readSkins(ByteBuffer.wrap(input.readSection(skinCount * SKIN_ENTRY_SIZE)).order(ByteOrder.LITTLE_ENDIAN),
                          game.getUnitSystem().requireUnitPool());
            } else if (tag == OWNER_TAG) {
                readOwners(ByteBuffer.wrap(input.readSection(ownerCount * OWNER_ENTRY_SIZE)).order(ByteOrder.LITTLE_ENDIAN),
                           game.getSpriteSystem().requireSpritePool());
            } else {
                // The limits sized the pools before the game started (SaveGameFile#getLimits) and the string section is the
                // started game's (SaveGameFile#getStringSection); unknown parts are skipped.
                input.seek(end);
            }

            if (input.getPosition() != end) {
                throw SaveFileReader.createFailure("The saved trailer part 0x{0} has {1} stored bytes but {2} were read.",
                                                   Integer.toHexString(tag), size, input.getPosition() - end + size);
            }
        }
    }
}
