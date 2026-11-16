package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Keeps the state the Sync records hash at one frame, for the mismatch report ({@link SyncRing#describe}) and the desync dump
 * ({@link DesyncDump}): the frame, the generator state, every player's minerals and gas, the active units in hash order with their
 * hash inputs and minimap visibility, tile rows without the excluded flag and the visibility block. A full capture
 * ({@link #captureCurrent}) holds every tile row and the sprites of the checked types as well.
 */
final class SyncState {
    private static final int UNIT_INDEX = 0;
    private static final int UNIT_NEXT = 1;
    private static final int UNIT_OWNER = 2;
    private static final int UNIT_TYPE = 3;
    private static final int UNIT_SHIELDS = 4;
    private static final int UNIT_ENERGY = 5;
    private static final int UNIT_X = 6;
    private static final int UNIT_Y = 7;
    private static final int UNIT_HIT_POINTS = 8;
    private static final int UNIT_RAW_HIT_POINTS = 9;
    private static final int UNIT_MINIMAP = 10;
    private static final int UNIT_VALUES = 11;

    private static final int SPRITE_INDEX = 0;
    private static final int SPRITE_TYPE = 1;
    private static final int SPRITE_ROW = 2;
    private static final int SPRITE_X = 3;
    private static final int SPRITE_Y = 4;
    private static final int SPRITE_VISIBILITY = 5;
    private static final int SPRITE_VALUES = 6;

    private static final int INITIAL_ENTRIES = 64;
    private static final int GROWTH_FACTOR = 2;
    private static final int WORD_MASK = 0xFFFF;
    private static final int BYTE_MASK = 0xFF;
    private static final int SORT_KEY_SHIFT = Integer.SIZE;
    private static final long SORT_OFFSET_MASK = 0xFFFFFFFFL;

    /**
     * Appends one formatted line ending with a line feed on every platform.
     *
     * @param text      the receiving text
     * @param format    the line format
     * @param arguments the format arguments
     */
    static void appendLine(@NotNull StringBuilder text, @NotNull String format, @NotNull Object @NotNull ... arguments) {
        text.append(String.format(format, arguments)).append('\n');
    }

    private static int @NotNull [] ensureCapacity(int @NotNull [] values, int length) {
        return values.length >= length ? values : Arrays.copyOf(values, Math.max(length, Math.max(INITIAL_ENTRIES, values.length * GROWTH_FACTOR)));
    }

    /**
     * Orders entries of a flat value array by the value at an entry offset.
     *
     * @param values      the entries
     * @param length      the used values
     * @param entryValues the values of one entry
     * @param keyOffset   the offset of the sort key in an entry
     * @return the entry offsets in key order
     */
    private static int @NotNull [] sortEntries(int @NotNull [] values, int length, int entryValues, int keyOffset) {
        long[] keys = new long[length / entryValues];

        for (int entry = 0; entry < keys.length; entry++) {
            int offset = entry * entryValues;

            keys[entry] = (long) values[offset + keyOffset] << SORT_KEY_SHIFT | offset;
        }

        Arrays.sort(keys);

        int[] offsets = new int[keys.length];

        for (int entry = 0; entry < keys.length; entry++) {
            offsets[entry] = (int) (keys[entry] & SORT_OFFSET_MASK);
        }

        return offsets;
    }

    private boolean captured;
    private int frame;
    private int generator;
    private final int @NotNull [] minerals;
    private final int @NotNull [] gas;

    private int @NotNull [] units;
    private int unitLength;

    private int tileFirstRow;
    private int tileWidth;
    private int @NotNull [] tiles;
    private int tileLength;

    private int @NotNull [] visibility;
    private int @NotNull [] sprites;
    private int spriteLength;

    /**
     * Creates an empty {@link SyncState} instance.
     */
    SyncState() {
        this.captured  = false;
        this.frame     = 0;
        this.generator = 0;
        this.minerals  = new int[PlayerTable.PLAYER_SLOTS];
        this.gas       = new int[PlayerTable.PLAYER_SLOTS];

        this.units      = new int[0];
        this.unitLength = 0;

        this.tileFirstRow = 0;
        this.tileWidth    = 0;
        this.tiles        = new int[0];
        this.tileLength   = 0;

        this.visibility   = new int[0];
        this.sprites      = new int[0];
        this.spriteLength = 0;
    }

    /**
     * @return {@code true} if a state was captured, {@code false} otherwise
     */
    boolean isCaptured() {
        return this.captured;
    }


    /**
     * Captures the state a record hashes: the values of every kind, the hashed tile row and the record's visibility block.
     *
     * @param game       the game state
     * @param tileRow    the hashed tile row, taken modulo the map height
     * @param visibility the record's visibility block, which is never changed after it was taken
     */
    void capture(@NotNull Game game, int tileRow, int @NotNull [] visibility) {
        GameMap map = game.getMap();

        this.captureValues(game);
        this.captureTiles(map, Math.floorMod(tileRow, map.getHeight()), 1);

        this.visibility   = visibility;
        this.spriteLength = 0;
    }

    /**
     * Captures the current state in full: the values of every kind, every tile row, the visibility block and the sprites of the
     * checked types.
     *
     * @param game    the game state
     * @param checked the checked sprite types ({@link SyncChecksumUtils#createCheckedSpriteTypes})
     */
    void captureCurrent(@NotNull Game game, boolean @NotNull [] checked) {
        GameMap map = game.getMap();

        this.captureValues(game);
        this.captureTiles(map, 0, map.getHeight());

        this.visibility = SyncChecksumUtils.calculateVisibilityRows(game, checked);

        this.captureSprites(game, checked);
    }

    private void captureValues(@NotNull Game game) {
        this.captured  = true;
        this.frame     = game.getCurrentFrame();
        this.generator = game.getRandom().getState();

        for (int slot = 0; slot < PlayerTable.PLAYER_SLOTS; slot++) {
            Player player = game.getPlayerTable().getPlayer(slot);

            this.minerals[slot] = player == null ? 0 : player.getMinerals();
            this.gas[slot]      = player == null ? 0 : player.getGas();
        }

        UnitPool pool = game.getUnitSystem().getUnitPool();
        int length = 0;
        int previous = -1;

        for (int index = pool == null ? 0 : pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
            Unit unit = pool.getUnit(index);

            this.units = ensureCapacity(this.units, length + UNIT_VALUES);

            if (previous >= 0) {
                this.units[previous + UNIT_NEXT] = index;
            }

            this.units[length + UNIT_INDEX]          = index;
            this.units[length + UNIT_NEXT]           = 0;
            this.units[length + UNIT_OWNER]          = unit.getOwnerIndex();
            this.units[length + UNIT_TYPE]           = unit.getUnitId();
            this.units[length + UNIT_SHIELDS]        = unit.getShieldPoints();
            this.units[length + UNIT_ENERGY]         = unit.getEnergy() & WORD_MASK;
            this.units[length + UNIT_X]              = (short) unit.getPositionX();
            this.units[length + UNIT_Y]              = (short) unit.getPositionY();
            this.units[length + UNIT_HIT_POINTS]     = UnitQueries.getHitPoints(unit);
            this.units[length + UNIT_RAW_HIT_POINTS] = unit.getHitPoints();
            this.units[length + UNIT_MINIMAP]        = SyncChecksumUtils.getMinimapVisibilityFlags(unit) & BYTE_MASK;

            previous = length;
            length += UNIT_VALUES;
        }

        this.unitLength = length;
    }

    private void captureTiles(@NotNull GameMap map, int firstRow, int rows) {
        int width = map.getWidth();
        int length = rows * width;

        this.tiles = ensureCapacity(this.tiles, length);

        for (int offset = 0; offset < length; offset++) {
            this.tiles[offset] = map.getTileFlags(firstRow * width + offset) & SyncChecksumUtils.TILE_FLAG_MASK;
        }

        this.tileFirstRow = firstRow;
        this.tileWidth    = width;
        this.tileLength   = length;
    }

    private void captureSprites(@NotNull Game game, boolean @NotNull [] checked) {
        SpritePool pool = game.getSpriteSystem().getSpritePool();
        int length = 0;

        for (int row = 0; pool != null && row < SyncChecksumUtils.SPRITE_ROWS; row++) {
            for (int index = pool.getFirstRowIndex(row); index >= 0; index = pool.getNextRowIndex(index)) {
                Sprite sprite = pool.getSprite(index);
                int type = sprite.getSpriteTypeId();

                if (type < 0 || type >= checked.length || !checked[type]) {
                    continue;
                }

                this.sprites = ensureCapacity(this.sprites, length + SPRITE_VALUES);

                this.sprites[length + SPRITE_INDEX]      = index;
                this.sprites[length + SPRITE_TYPE]       = type;
                this.sprites[length + SPRITE_ROW]        = row;
                this.sprites[length + SPRITE_X]          = sprite.getX();
                this.sprites[length + SPRITE_Y]          = sprite.getY();
                this.sprites[length + SPRITE_VISIBILITY] = sprite.getVisibilityFlags() & BYTE_MASK;

                length += SPRITE_VALUES;
            }
        }

        this.spriteLength = length;
    }

    /**
     * Creates a copy that later captures into this state do not change.
     *
     * @return the copy
     */
    @NotNull SyncState copy() {
        SyncState copy = new SyncState();

        copy.captured  = this.captured;
        copy.frame     = this.frame;
        copy.generator = this.generator;

        System.arraycopy(this.minerals, 0, copy.minerals, 0, this.minerals.length);
        System.arraycopy(this.gas, 0, copy.gas, 0, this.gas.length);

        copy.units      = Arrays.copyOf(this.units, this.unitLength);
        copy.unitLength = this.unitLength;

        copy.tileFirstRow = this.tileFirstRow;
        copy.tileWidth    = this.tileWidth;
        copy.tiles        = Arrays.copyOf(this.tiles, this.tileLength);
        copy.tileLength   = this.tileLength;

        copy.visibility   = this.visibility.clone();
        copy.sprites      = Arrays.copyOf(this.sprites, this.spriteLength);
        copy.spriteLength = this.spriteLength;

        return copy;
    }

    /**
     * Appends up to a limit of units in hash order as {@code index:owner:type s e x,y hp} with the raw hit points.
     *
     * @param text  the receiving text
     * @param limit the most units appended
     */
    void describeUnits(@NotNull StringBuilder text, int limit) {
        text.append(", ").append(this.unitLength / UNIT_VALUES).append(" units:");

        for (int offset = 0; offset < this.unitLength && offset < limit * UNIT_VALUES; offset += UNIT_VALUES) {
            text.append(String.format(" %d:%d:%d s%d e%d %d,%d hp%d", this.units[offset + UNIT_INDEX], this.units[offset + UNIT_OWNER],
                                      this.units[offset + UNIT_TYPE], this.units[offset + UNIT_SHIELDS], this.units[offset + UNIT_ENERGY],
                                      this.units[offset + UNIT_X], this.units[offset + UNIT_Y], this.units[offset + UNIT_RAW_HIT_POINTS]));
        }
    }

    /**
     * Appends the minerals and gas of players {@code 0..11}, the generator state and the frame.
     *
     * @param text the receiving text
     */
    void describeResources(@NotNull StringBuilder text) {
        text.append(", resources:");

        for (int slot = 0; slot < PlayerTable.PLAYER_SLOTS; slot++) {
            text.append(' ').append(this.minerals[slot]).append('/').append(this.gas[slot]);
        }

        text.append(String.format(", generator 0x%08X, frame %d", this.generator, this.frame));
    }

    /**
     * Appends the state as lines that start with the section name: the frame, the generator state, the players, the tile rows, the
     * visibility rows, the units sorted by pool index (each with the next unit in hash order) and the sprites sorted by pool index.
     *
     * @param text    the receiving text
     * @param section the section name
     */
    void write(@NotNull StringBuilder text, @NotNull String section) {
        if (!this.captured) {
            appendLine(text, "%s not kept", section);

            return;
        }

        appendLine(text, "%s frame %d", section, this.frame);
        appendLine(text, "%s generator 0x%08X", section, this.generator);

        for (int slot = 0; slot < PlayerTable.PLAYER_SLOTS; slot++) {
            appendLine(text, "%s player %02d minerals %d gas %d", section, slot, this.minerals[slot], this.gas[slot]);
        }

        for (int offset = 0; offset < this.tileLength; offset += this.tileWidth) {
            text.append(String.format("%s tile row %03d", section, this.tileFirstRow + offset / this.tileWidth));

            for (int column = 0; column < this.tileWidth; column++) {
                text.append(String.format(" %08X", this.tiles[offset + column]));
            }

            text.append('\n');
        }

        for (int row = 0; row < this.visibility.length; row++) {
            appendLine(text, "%s visibility row %03d 0x%02X", section, row, this.visibility[row]);
        }

        for (int offset : sortEntries(this.units, this.unitLength, UNIT_VALUES, UNIT_INDEX)) {
            appendLine(text, "%s unit %04d next %04d owner %d type %d shields %d energy %d x %d y %d hp %d raw %d minimap 0x%02X",
                       section, this.units[offset + UNIT_INDEX], this.units[offset + UNIT_NEXT], this.units[offset + UNIT_OWNER],
                       this.units[offset + UNIT_TYPE], this.units[offset + UNIT_SHIELDS], this.units[offset + UNIT_ENERGY],
                       this.units[offset + UNIT_X], this.units[offset + UNIT_Y], this.units[offset + UNIT_HIT_POINTS],
                       this.units[offset + UNIT_RAW_HIT_POINTS], this.units[offset + UNIT_MINIMAP]);
        }

        for (int offset : sortEntries(this.sprites, this.spriteLength, SPRITE_VALUES, SPRITE_INDEX)) {
            appendLine(text, "%s sprite %04d type %d row %03d x %d y %d visibility 0x%02X",
                       section, this.sprites[offset + SPRITE_INDEX], this.sprites[offset + SPRITE_TYPE],
                       this.sprites[offset + SPRITE_ROW], this.sprites[offset + SPRITE_X], this.sprites[offset + SPRITE_Y],
                       this.sprites[offset + SPRITE_VISIBILITY]);
        }
    }
}
