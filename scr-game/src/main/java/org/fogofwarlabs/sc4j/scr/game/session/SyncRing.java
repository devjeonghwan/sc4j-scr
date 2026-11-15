package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Retains the Sync records of a game and their diagnostic hash inputs in a fixed ring.
 * <p>
 * Each record combines the current state hash with drawing samples; the outgoing turn sends the newest record.
 * Drawing values are sampled before the frame, and its state hash is computed after commands execute. The first drawn row,
 * the visibility block and its fold are those of the first drawing after the previous turn step ({@link #updateDrawing}):
 * below 24 turns per second the drawings of frames without a turn step leave them.
 * <p>
 * Only a multiplayer session alternates the unit and resource kinds and takes drawing values. Outside one (single player
 * and replay playback) every record has kind {@code 0}, whose hash is the record index, and its drawing values stay clear.
 */
public final class SyncRing {
    /**
     * The records in the ring.
     */
    public static final int RECORDS = 16;

    /**
     * The record kind outside a multiplayer session, whose hash is the record index.
     */
    private static final int KIND_NONE = 0;
    private static final int KIND_UNITS = 1;
    private static final int KIND_RESOURCES = 2;
    private static final int KIND_COUNT = 2;
    private static final int FIRST_ROW_MARGIN = 4;
    /**
     * The records made at load, before the first drawing.
     */
    private static final int LOAD_RECORDS = 3;

    /**
     * The values of one unit record: pool index, owner and six hash inputs.
     */
    private static final int UNIT_INPUTS = 8;
    private static final int UNIT_OWNER = 1;
    private static final int UNIT_TYPE = 2;
    private static final int UNIT_SHIELDS = 3;
    private static final int UNIT_ENERGY = 4;
    private static final int UNIT_X = 5;
    private static final int UNIT_Y = 6;
    private static final int UNIT_HIT_POINTS = 7;

    /**
     * The resource inputs: minerals and gas of every player, then the generator state and frame.
     */
    private static final int RESOURCE_VALUES_PER_PLAYER = 2;
    private static final int RESOURCE_GAS = 1;
    private static final int RESOURCE_PLAYER_VALUES = RESOURCE_VALUES_PER_PLAYER * PlayerTable.PLAYER_SLOTS;
    private static final int RESOURCE_GENERATOR = RESOURCE_PLAYER_VALUES;
    private static final int RESOURCE_FRAME = RESOURCE_GENERATOR + 1;
    private static final int RESOURCE_TRAILER_VALUES = 2;
    private static final int RESOURCE_INPUTS = RESOURCE_PLAYER_VALUES + RESOURCE_TRAILER_VALUES;

    private static final int INITIAL_KEPT_UNITS = 64;
    private static final int KEPT_INPUTS_GROWTH_FACTOR = 2;
    private static final int REPORTED_UNITS = 48;

    private static final int KIND_INDEX_BYTE = 1;
    private static final int RECORD_INDEX_SHIFT = 4;
    private static final int KIND_MASK = 0xF;
    private static final int HASH_BYTE = 2;
    private static final int VISIBILITY_FOLD_BYTE = 4;
    private static final int FIRST_ROW_BYTE = 5;
    private static final int MINIMAP_COUNT_BYTE = 6;

    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    private final int localSlot;
    private final boolean multiplayer;

    private final int @NotNull [] words;
    private final int @NotNull [] kinds;
    private final int @NotNull [] folds;
    private final int @NotNull [] rows;
    private final int @NotNull [] counts;
    private final int @NotNull [] parities;
    private final int @NotNull [] @NotNull [] visibility;

    /**
     * Diagnostics: the frame each record was made in and its hash inputs ({@link #describe}).
     */
    private final long @NotNull [] frames;
    private final int @NotNull [] @Nullable [] inputs;
    private final int @NotNull [] inputLengths;

    private boolean @NotNull [] checkedSprites;
    private int @NotNull [] sampledVisibility;

    /**
     * Whether a turn step ran since the last drawing values were taken, so that the next drawing takes them.
     */
    private boolean drawingDue;
    private int drawnRow;
    private int drawnFold;
    private int @NotNull [] drawnVisibility;

    private int updates;
    private boolean sampled;
    private int sampledFold;
    private int sampledRow;
    private int sampledCount;
    private int sampledParity;
    private int previousCount;
    private int previousParity;

    /**
     * Creates an empty {@link SyncRing} instance.
     *
     * @param localSlot   the local player's slot
     * @param multiplayer whether the game runs in a multiplayer session, whose records alternate the unit and resource kinds
     *                    and take drawing values
     */
    public SyncRing(int localSlot, boolean multiplayer) {
        this.localSlot   = localSlot;
        this.multiplayer = multiplayer;

        this.words      = new int[RECORDS];
        this.kinds      = new int[RECORDS];
        this.folds      = new int[RECORDS];
        this.rows       = new int[RECORDS];
        this.counts     = new int[RECORDS];
        this.parities   = new int[RECORDS];
        this.visibility = new int[RECORDS][];

        this.frames       = new long[RECORDS];
        this.inputs       = new int[RECORDS][];
        this.inputLengths = new int[RECORDS];

        this.checkedSprites    = new boolean[0];
        this.sampledVisibility = new int[SyncChecksumUtils.SPRITE_ROWS];

        for (int index = 0; index < RECORDS; index++) {
            this.visibility[index] = new int[SyncChecksumUtils.SPRITE_ROWS];
        }

        this.drawingDue      = false;
        this.drawnRow        = 0;
        this.drawnFold       = 0;
        this.drawnVisibility = new int[SyncChecksumUtils.SPRITE_ROWS];

        this.updates        = 0;
        this.sampled        = false;
        this.sampledFold    = 0;
        this.sampledRow     = 0;
        this.sampledCount   = 0;
        this.sampledParity  = 0;
        this.previousCount  = 0;
        this.previousParity = 0;
    }

    /**
     * @return the number of records made
     */
    public int getUpdates() {
        return this.updates;
    }

    /**
     * @return {@code true} if the drawing values of the next record were sampled and the record is not yet made, {@code false} otherwise
     */
    public boolean isSampled() {
        return this.sampled;
    }


    /**
     * Takes the drawing values of a frame's drawing when it is the first after a turn step, from the state the frame starts
     * from: the first drawn row, the visibility block and its fold. A drawing that follows no new turn step keeps the values
     * of the previous one.
     *
     * @param game      the game state
     * @param cameraRow the camera's top tile row
     */
    public void updateDrawing(@NotNull Game game, int cameraRow) {
        if (!this.drawingDue) {
            return;
        }

        if (this.checkedSprites.length == 0) {
            this.checkedSprites = SyncChecksumUtils.createCheckedSpriteTypes(game);
        }

        int firstRow = Math.max(0, cameraRow - FIRST_ROW_MARGIN);
        int[] block = SyncChecksumUtils.calculateVisibilityRows(game, this.checkedSprites);

        this.drawingDue      = false;
        this.drawnRow        = firstRow & BYTE_MASK;
        this.drawnFold       = SyncChecksumUtils.calculateVisibilityFold(block, firstRow, 1 << this.localSlot);
        this.drawnVisibility = block;
    }

    /**
     * Takes the drawing's values for the next record in a turn step: the first drawn row, the visibility block and its fold
     * of the first drawing after the previous turn step ({@link #updateDrawing}), and the parity and minimap count of the
     * drawing before the frame's. The records made at load find them clear. The turn step makes the next drawing take new
     * drawing values. Outside a multiplayer session the turn step takes none: the drawing values stay clear.
     *
     * @param game the game state
     * @throws IllegalStateException if the values sampled for the next record were not used yet
     */
    public void sampleDrawing(@NotNull Game game) {
        if (this.sampled) {
            throw Exceptions.newIllegalStateException("The Sync record of the previous frame was not made; its commands-executed call is missing.");
        }

        if (!this.multiplayer) {
            this.sampled = true;

            return;
        }

        boolean drawn = this.updates + 1 > LOAD_RECORDS;

        this.sampledRow        = drawn ? this.drawnRow : 0;
        this.sampledFold       = drawn ? this.drawnFold : 0;
        this.sampledVisibility = drawn ? this.drawnVisibility : new int[SyncChecksumUtils.SPRITE_ROWS];
        this.sampledCount      = drawn ? this.previousCount : 0;
        this.sampledParity     = drawn ? this.previousParity : 0;

        this.previousCount  = SyncChecksumUtils.countMinimapUnits(game, this.localSlot) & BYTE_MASK;
        this.previousParity = SyncChecksumUtils.calculateUnitVisibilityParity(game);
        this.sampled        = true;
        this.drawingDue     = true;
    }

    /**
     * Makes the next record: the WORD of its kind from the current state and the drawing values of the last
     * {@link #sampleDrawing}. Outside a multiplayer session the kind is {@code 0} and its hash the record index.
     *
     * @param game the game state
     * @throws IllegalStateException if the drawing values were not sampled for this record
     */
    public void update(@NotNull Game game) {
        if (!this.sampled) {
            throw Exceptions.newIllegalStateException("The Sync record's drawing values were not sampled.");
        }

        this.sampled = false;
        this.updates++;

        int index = this.updates % RECORDS;
        int kind = KIND_NONE;

        if (this.multiplayer) {
            kind = this.updates % KIND_COUNT == 1 ? KIND_RESOURCES : KIND_UNITS;
        }

        int hash = switch (kind) {
            case KIND_UNITS -> SyncChecksumUtils.calculateUnitHash(game);
            case KIND_RESOURCES -> SyncChecksumUtils.calculateResourceHash(game);
            default -> index;
        };

        int rowHash = SyncChecksumUtils.calculateTileRowHash(game.getMap(), this.updates - 1);
        int slotMasks = SyncChecksumUtils.calculateSlotMasks(game.getLocalVision().getComparisonMask(), this.localSlot);

        this.words[index]      = SyncChecksumUtils.calculateRecordWord(hash, rowHash, slotMasks);
        this.kinds[index]      = kind;
        this.rows[index]       = this.sampledRow;
        this.folds[index]      = this.sampledFold;
        this.counts[index]     = this.sampledCount;
        this.parities[index]   = this.sampledParity;
        this.visibility[index] = this.sampledVisibility;
        this.frames[index]     = game.getCurrentFrame();

        if (kind != KIND_NONE) {
            this.keepInputs(game, index, kind);
        }
    }

    /**
     * Keeps the record's hash inputs for {@link #describe}: per active unit (in hash order) its pool index, owner, type,
     * shields, energy, x, y and hit points; or the resources, generator state and frame.
     *
     * @param game  the game state
     * @param index the record index
     * @param kind  the record kind
     */
    private void keepInputs(@NotNull Game game, int index, int kind) {
        int[] kept = this.inputs[index];
        int length = 0;

        if (kind == KIND_UNITS) {
            UnitPool pool = game.getUnitSystem().getUnitPool();

            for (int unitIndex = pool == null ? 0 : pool.getFirstCreatedIndex(true); unitIndex != 0;
                 unitIndex = pool.getNextCreatedIndex(unitIndex)) {
                if (kept == null || kept.length < length + UNIT_INPUTS) {
                    kept = Arrays.copyOf(kept == null ? new int[0] : kept,
                                         Math.max(INITIAL_KEPT_UNITS * UNIT_INPUTS, (length + UNIT_INPUTS) * KEPT_INPUTS_GROWTH_FACTOR));
                }

                Unit unit = pool.getUnit(unitIndex);

                kept[length++] = unitIndex;
                kept[length++] = unit.getOwnerIndex();
                kept[length++] = unit.getUnitId();
                kept[length++] = unit.getShieldPoints();
                kept[length++] = unit.getEnergy() & WORD_MASK;
                kept[length++] = unit.getPositionX();
                kept[length++] = unit.getPositionY();
                kept[length++] = unit.getHitPoints();
            }
        } else {
            if (kept == null || kept.length < RESOURCE_INPUTS) {
                kept = new int[Math.max(INITIAL_KEPT_UNITS * UNIT_INPUTS, RESOURCE_INPUTS)];
            }

            for (int slot = 0; slot < PlayerTable.PLAYER_SLOTS; slot++) {
                Player player = game.getPlayerTable().getPlayer(slot);

                kept[length++] = player == null ? 0 : player.getMinerals();
                kept[length++] = player == null ? 0 : player.getGas();
            }

            kept[length++] = game.getRandom().getState();
            kept[length++] = game.getCurrentFrame();
        }

        this.inputs[index]       = kept;
        this.inputLengths[index] = length;
    }

    /**
     * Describes the record a Sync command names, for a mismatch report: its kind, the frame it was made in and its hash
     * inputs (kind 1: up to {@value #REPORTED_UNITS} units as {@code index:owner:type s e x,y hp}; kind 2: minerals/gas of
     * players 0..11, the generator state and the frame; kind 0 keeps none).
     *
     * @param sync the seven command bytes
     * @return the description
     */
    public @NotNull String describe(byte @NotNull [] sync) {
        int index = (sync[KIND_INDEX_BYTE] & BYTE_MASK) >>> RECORD_INDEX_SHIFT;
        int[] kept = this.inputs[index];
        int length = this.inputLengths[index];

        StringBuilder text = new StringBuilder(String.format("record %d kind %d made in frame %d (sent kind %d)", index, this.kinds[index],
                                                             this.frames[index], sync[KIND_INDEX_BYTE] & KIND_MASK));

        if (kept == null) {
            return text.toString();
        }

        if (this.kinds[index] == KIND_UNITS) {
            text.append(", ").append(length / UNIT_INPUTS).append(" units:");

            for (int offset = 0; offset < length && offset < REPORTED_UNITS * UNIT_INPUTS; offset += UNIT_INPUTS) {
                text.append(String.format(" %d:%d:%d s%d e%d %d,%d hp%d", kept[offset], kept[offset + UNIT_OWNER],
                                          kept[offset + UNIT_TYPE], kept[offset + UNIT_SHIELDS], kept[offset + UNIT_ENERGY],
                                          kept[offset + UNIT_X], kept[offset + UNIT_Y], kept[offset + UNIT_HIT_POINTS]));
            }
        } else {
            text.append(", resources:");

            for (int offset = 0; offset < RESOURCE_PLAYER_VALUES; offset += RESOURCE_VALUES_PER_PLAYER) {
                text.append(' ').append(kept[offset]).append('/').append(kept[offset + RESOURCE_GAS]);
            }

            text.append(String.format(", generator 0x%08X, frame %d", kept[RESOURCE_GENERATOR], kept[RESOURCE_FRAME]));
        }

        return text.toString();
    }

    /**
     * Creates the Sync command of the newest record.
     *
     * @return the seven command bytes
     */
    public byte @NotNull [] createCommand() {
        int index = this.updates % RECORDS;
        int word = this.words[index];

        return new byte[]{ReplayCommandIds.SYNC, (byte) (this.kinds[index] | index << RECORD_INDEX_SHIFT), (byte) word,
                          (byte) (word >>> Byte.SIZE), (byte) this.folds[index], (byte) this.rows[index], (byte) this.counts[index]};
    }

    /**
     * Checks a received Sync command against the named record using the sender's slot: minimap count parity,
     * visibility fold from the sent first row, state hash WORD and record kind.
     *
     * @param sync       the seven command bytes
     * @param senderSlot the sender's slot
     * @return {@code null} if the command matches, otherwise the failed checks with the local record's values
     */
    public @Nullable String check(byte @NotNull [] sync, int senderSlot) {
        int index = (sync[KIND_INDEX_BYTE] & BYTE_MASK) >>> RECORD_INDEX_SHIFT;
        int kind = sync[KIND_INDEX_BYTE] & KIND_MASK;
        int word = LittleEndianUtils.readUnsigned16(sync, HASH_BYTE);
        int fold = sync[VISIBILITY_FOLD_BYTE] & BYTE_MASK;
        int firstRow = sync[FIRST_ROW_BYTE] & BYTE_MASK;
        int count = sync[MINIMAP_COUNT_BYTE] & BYTE_MASK;

        StringBuilder failed = new StringBuilder();

        if (((this.parities[index] >>> senderSlot ^ count) & 1) != 0) {
            failed.append(String.format(" parity 0x%02X (count bit %d)", this.parities[index], count & 1));
        }

        int expectedFold = SyncChecksumUtils.calculateVisibilityFold(this.visibility[index], firstRow, 1 << senderSlot);

        if (expectedFold != fold) {
            failed.append(String.format(" fold 0x%02X", expectedFold));
        }

        if (this.words[index] != word) {
            failed.append(String.format(" word 0x%04X", this.words[index]));
        }

        if (this.kinds[index] != kind) {
            failed.append(" kind ").append(this.kinds[index]);
        }

        return failed.isEmpty() ? null
                                : String.format("record %d (update %d):%s", index,
                                                this.updates - ((this.updates - index) % RECORDS + RECORDS) % RECORDS,
                                                failed);
    }
}
