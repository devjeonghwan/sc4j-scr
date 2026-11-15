package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.dat.SpritesDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitAvailabilityFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Computes the network Sync hashes and drawing checks from the retained game state.
 * <p>
 * Resource and active-unit hashes alternate, and each record combines its kind hash with a tile row and slot masks.
 */
public final class SyncChecksumUtils {
    /**
     * The local slot masks' contribution of a game whose comparison mask is the local player's bit
     * ({@link #calculateSlotMasks}).
     */
    public static final int SLOT_MASKS = 0x10100;
    /**
     * The tile flag bit left out of the row hash.
     */
    public static final int TILE_FLAG_MASK = 0xBFFFFFFF;

    /**
     * The number of sprite types in the visibility checks.
     */
    public static final int SPRITE_TYPES = SpritesDat.SPRITE_COUNT;
    /**
     * The number of sprite row lists.
     */
    public static final int SPRITE_ROWS = GameMap.MAXIMUM_DIMENSION;
    /**
     * The number of drawn rows in the visibility fold.
     */
    public static final int FOLD_ROWS = 16;

    private static final int HASH_ROTATION = 3;
    private static final int FOLD_ROTATION = 1;
    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;
    private static final int FIRST_EXCLUDED_SPECIAL_TYPE = ScrUnitIds.SPECIAL_FLOOR_MISSILE_TRAP;
    private static final int LAST_EXCLUDED_SPECIAL_TYPE = ScrUnitIds.SPECIAL_RIGHT_WALL_FLAME_TRAP;
    private static final int DARK_SWARM_TYPE = ScrUnitIds.SPELL_DARK_SWARM;
    private static final int DISRUPTION_WEB_TYPE = ScrUnitIds.SPELL_DISRUPTION_WEB;
    private static final int DETECTION_STATUS_SHIFT = Byte.SIZE;
    private static final int DETECTION_STATUS_MASK = (UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED) >>> DETECTION_STATUS_SHIFT;

    /**
     * Computes the resource hash: minerals and gas from the last player down, then generator state and frame count.
     *
     * @param game the game state
     * @return the hash
     */
    public static int calculateResourceHash(@NotNull Game game) {
        int[] minerals = new int[PlayerTable.PLAYER_SLOTS];
        int[] gas = new int[PlayerTable.PLAYER_SLOTS];

        for (int slot = 0; slot < PlayerTable.PLAYER_SLOTS; slot++) {
            Player player = game.getPlayerTable().getPlayer(slot);

            if (player != null) {
                minerals[slot] = player.getMinerals();
                gas[slot]      = player.getGas();
            }
        }

        int hash = minerals[PlayerTable.PLAYER_SLOTS - 1];

        for (int slot = PlayerTable.PLAYER_SLOTS - 1; slot >= 1; slot--) {
            hash = Integer.rotateLeft(Integer.rotateLeft(hash, HASH_ROTATION) ^ gas[slot], HASH_ROTATION);
            hash ^= minerals[slot - 1];
        }

        hash = Integer.rotateLeft(Integer.rotateLeft(hash, HASH_ROTATION) ^ gas[0], HASH_ROTATION);
        hash = Integer.rotateLeft(Integer.rotateLeft(hash, HASH_ROTATION) ^ game.getRandom().getState(), HASH_ROTATION) ^ game.getCurrentFrame();

        return Integer.rotateLeft(hash, HASH_ROTATION);
    }

    /**
     * Computes the active-unit list hash; hidden units do not contribute.
     *
     * @param game the game state
     * @return the hash
     */
    public static int calculateUnitHash(@NotNull Game game) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        if (pool == null) {
            return 0;
        }

        int hash = 0;

        for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
            hash = Integer.rotateLeft(hash ^ calculateUnitHash(pool.getUnit(index)), HASH_ROTATION);
        }

        return hash;
    }

    /**
     * Computes one unit hash from its type, shields, energy, position and rounded hit points.
     *
     * @param unit the unit
     * @return the hash
     */
    public static int calculateUnitHash(@NotNull Unit unit) {
        int hash = unit.getUnitId();

        hash = Integer.rotateLeft(hash, HASH_ROTATION) ^ unit.getShieldPoints();
        hash = Integer.rotateLeft(hash, HASH_ROTATION) ^ unit.getEnergy() & WORD_MASK;
        hash = Integer.rotateLeft(hash, HASH_ROTATION) ^ (short) unit.getPositionX();
        hash = Integer.rotateLeft(hash, HASH_ROTATION) ^ (short) unit.getPositionY();
        hash = Integer.rotateLeft(hash, HASH_ROTATION) ^ UnitQueries.getHitPoints(unit);

        return Integer.rotateLeft(hash, HASH_ROTATION);
    }

    /**
     * Computes a tile row hash after masking its excluded flag and rotating each accumulated value.
     *
     * @param map the map
     * @param row the row, taken modulo the map height
     * @return the hash
     */
    public static int calculateTileRowHash(@NotNull GameMap map, int row) {
        int width = map.getWidth();
        int base = Math.floorMod(row, map.getHeight()) * width;

        int hash = 0;

        for (int column = 0; column < width; column++) {
            hash = Integer.rotateLeft(hash ^ map.getTileFlags(base + column) & TILE_FLAG_MASK, HASH_ROTATION);
        }

        return hash;
    }

    /**
     * Computes the local slot masks' contribution to a record: the local comparison mask and its explored companion (the mask
     * shifted by {@code 8}), shifted by {@code 8} less the local player. A game whose comparison mask is the local player's bit
     * gives {@link #SLOT_MASKS}; a replay view of every player's vision folds to {@code 0xFFFF}.
     *
     * @param comparisonMask the local comparison mask
     * @param localPlayer    the local player
     * @return the slot masks' contribution
     */
    public static int calculateSlotMasks(int comparisonMask, int localPlayer) {
        return ((comparisonMask << Byte.SIZE) ^ comparisonMask) << (Byte.SIZE - localPlayer);
    }

    /**
     * Computes the record WORD from the kind hash, tile row hash and slot masks.
     *
     * @param kindHash  the kind's hash
     * @param rowHash   the tile row hash
     * @param slotMasks the local slot masks' contribution ({@link #calculateSlotMasks})
     * @return the WORD the Sync command carries
     */
    public static int calculateRecordWord(int kindHash, int rowHash, int slotMasks) {
        int value = slotMasks ^ rowHash ^ kindHash;

        return (value >>> Short.SIZE ^ value) & WORD_MASK;
    }

    /**
     * Creates the visibility-check flags for the sprites of the non-building unit types with the
     * {@link UnitAvailabilityFlags#NON_NEUTRAL} availability flag.
     *
     * @param game the game state
     * @return one flag per sprite type
     * @throws NullPointerException if the world was built without game data
     */
    public static boolean @NotNull [] createCheckedSpriteTypes(@NotNull Game game) {
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();
        ScrFlingyTypeView flingy = game.requireGameData().getFlingyTypes();
        boolean[] flags = new boolean[SPRITE_TYPES];

        for (int unit = 0; unit < UnitsDat.UNIT_COUNT; unit++) {
            if ((units.getFlags(unit) & UnitTypeFlags.BUILDING) != 0 || (units.getAvailabilityFlags(unit) & UnitAvailabilityFlags.NON_NEUTRAL) == 0) {
                continue;
            }

            int sprite = flingy.getSprite(units.getFlingyType(unit));

            if (sprite >= 0 && sprite < flags.length) {
                flags[sprite] = true;
            }
        }

        return flags;
    }

    /**
     * Computes the visibility XOR for every sprite row, considering only checked sprite types.
     *
     * @param game    the game state
     * @param checked the checked sprite types ({@link #createCheckedSpriteTypes})
     * @return one byte per sprite row ({@link #SPRITE_ROWS})
     */
    public static int @NotNull [] calculateVisibilityRows(@NotNull Game game, boolean @NotNull [] checked) {
        int[] rows = new int[SPRITE_ROWS];
        SpritePool pool = game.getSpriteSystem().getSpritePool();

        if (pool == null) {
            return rows;
        }

        for (int row = 0; row < SPRITE_ROWS; row++) {
            for (int index = pool.getFirstRowIndex(row); index >= 0; index = pool.getNextRowIndex(index)) {
                Sprite sprite = pool.getSprite(index);
                int type = sprite.getSpriteTypeId();

                if (type >= 0 && type < checked.length && checked[type]) {
                    rows[row] ^= sprite.getVisibilityFlags() & BYTE_MASK;
                }
            }
        }

        return rows;
    }

    /**
     * Computes the masked visibility fold from the first drawn row through the bounded row window.
     *
     * @param rows     the row bytes ({@link #calculateVisibilityRows})
     * @param firstRow the first row
     * @param mask     the player bit
     * @return the byte
     */
    public static int calculateVisibilityFold(int @NotNull [] rows, int firstRow, int mask) {
        int hash = 0;
        int last = Math.min(firstRow + FOLD_ROWS - 1, SPRITE_ROWS - 1);

        for (int row = firstRow; row <= last; row++) {
            hash = Integer.rotateLeft(hash ^ rows[row] & mask, FOLD_ROTATION);
        }

        int word = hash >>> Short.SIZE ^ hash;

        return (word >>> Byte.SIZE ^ word) & BYTE_MASK;
    }

    /**
     * Computes the unit visibility parity with the minimap exclusions and detection masks.
     *
     * @param game the game state
     * @return the parity byte
     * @throws NullPointerException if the world was built without game data
     */
    public static int calculateUnitVisibilityParity(@NotNull Game game) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        if (pool == null) {
            return 0;
        }

        ScrUnitTypeView units = game.requireGameData().getUnitTypes();

        int parity = 0;

        for (int player = PlayerTable.PLAYER_SLOTS - 1; player >= 0; player--) {
            for (int index = pool.getFirstOwnerIndex(player); index != 0; index = pool.getNextOwnerIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (isExcludedFromMinimap(units, unit.getUnitId())) {
                    continue;
                }

                parity ^= getMinimapVisibilityFlags(unit);
            }
        }

        return parity & BYTE_MASK;
    }

    /**
     * Counts locally visible minimap units with the parity exclusions and detection masks.
     *
     * @param game the game state
     * @param slot the local player's slot
     * @return the count
     * @throws NullPointerException if the world was built without game data
     */
    public static int countMinimapUnits(@NotNull Game game, int slot) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        if (pool == null) {
            return 0;
        }

        ScrUnitTypeView units = game.requireGameData().getUnitTypes();

        int count = 0;

        for (int player = PlayerTable.PLAYER_SLOTS - 1; player >= 0; player--) {
            for (int index = pool.getFirstOwnerIndex(player); index != 0; index = pool.getNextOwnerIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (isExcludedFromMinimap(units, unit.getUnitId())) {
                    continue;
                }

                if ((getMinimapVisibilityFlags(unit) & 1 << slot) != 0) {
                    count++;
                }
            }
        }

        return count;
    }

    private static boolean isExcludedFromMinimap(@NotNull ScrUnitTypeView units, int type) {
        return (units.getFlags(type) & UnitTypeFlags.SUBUNIT) != 0 ||
               type >= FIRST_EXCLUDED_SPECIAL_TYPE && type <= LAST_EXCLUDED_SPECIAL_TYPE ||
               type == DARK_SWARM_TYPE || type == DISRUPTION_WEB_TYPE;
    }

    private static int getMinimapVisibilityFlags(@NotNull Unit unit) {
        Sprite sprite = unit.getSpriteOrNull();
        int flags = sprite == null ? 0 : sprite.getVisibilityFlags();

        if ((unit.getStatus() >>> DETECTION_STATUS_SHIFT & DETECTION_STATUS_MASK) != 0) {
            flags &= unit.getDetectionFlags();
        }

        return flags;
    }


    private SyncChecksumUtils() {
    }
}
