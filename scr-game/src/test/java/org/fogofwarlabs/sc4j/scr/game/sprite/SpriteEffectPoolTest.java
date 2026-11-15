package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.type.constant.SpriteIds;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.NoSuchAlgorithmException;

/**
 * Checks effect pool allocation, recycling, fog registration and intrusive list ordering.
 */
final class SpriteEffectPoolTest {
    /**
     * The SHA-256 digest of the decompressed allocation capture.
     */
    private static final @NotNull String ALLOCATION_CAPTURE_HASH = "f8baffc8376b6b99e971f38d0f6b6009b22fe377fabc3b4359feb4b6099d0d61";
    /**
     * The SHA-256 digest of the decompressed fog registration capture.
     */
    private static final @NotNull String FOG_REGISTRATION_CAPTURE_HASH = "7170313f6dd95212f5f8d5c2fd626243a7398cd738ffe63bac19b8a80b1a6f92";
    /**
     * The byte capacity of the written record buffer, enough for the record of the largest pool.
     */
    private static final int RECORD_BUFFER_BYTES = 20100;
    /**
     * The elevation level of the sprite supplied to the successful initializations.
     */
    private static final int SUPPLIED_SPRITE_ELEVATION = 4;
    /**
     * The pool capacities of the capture cases.
     */
    private static final int @NotNull [] POOL_CAPACITIES = {1, 2, 3, 17, 500, 1000};
    /**
     * The number of initialization patterns of every pool capacity.
     */
    private static final int PATTERN_COUNT = 4;
    /**
     * The pattern whose initializations all succeed.
     */
    private static final int ALL_SUCCESSFUL_PATTERN = 0;
    /**
     * The pattern whose initializations succeed on the even steps only.
     */
    private static final int ALTERNATE_PATTERN = 1;
    /**
     * The step period of the alternate pattern.
     */
    private static final int ALTERNATE_PERIOD = 2;
    /**
     * The pattern whose initializations fail on every third step.
     */
    private static final int THIRD_FAILING_PATTERN = 2;
    /**
     * The step period of the failing steps of the third-failing pattern.
     */
    private static final int FAILING_PERIOD = 3;
    /**
     * The allocations each case makes past the pool capacity.
     */
    private static final int EXTRA_ALLOCATIONS = 3;
    /**
     * The pool index of the first allocated effect.
     */
    private static final int STARTUP_POOL_INDEX = 0;
    /**
     * The DWORD fields of a record before the slots: capacity, pattern, step, allocated index and the four list ends.
     */
    private static final int HEADER_WORDS = 8;
    /**
     * The DWORD fields of one slot in the capture, including its padding field.
     */
    private static final int SLOT_WORDS = 5;
    /**
     * The DWORD fields of a record after the slots.
     */
    private static final int TAIL_WORDS = 0;
    /**
     * The value a record holds for a slot without a sprite.
     */
    private static final int NO_SPRITE_VALUE = -1;
    /**
     * The value a record holds for the allocated index of a step that initializes no effect.
     */
    private static final int NO_EFFECT_INDEX = -1;
    /**
     * The value a record holds for a slot with a sprite.
     */
    private static final int SPRITE_VALUE = 0;
    /**
     * The byte capacity of the written fog registration record, the size of one captured record.
     */
    private static final int FOG_RECORD_CAPACITY = 116;
    /**
     * The number of fog registration cases.
     */
    private static final int FOG_CASE_COUNT = 512;
    /**
     * The capacity of the effect pool of the fog registration cases.
     */
    private static final int FOG_POOL_CAPACITY = 4;
    /**
     * The life that marks an untouched slot, plus its slot index.
     */
    private static final int UNTOUCHED_LIFE = 0xA5000000;
    /**
     * The number of allocations of one fog registration case.
     */
    private static final int FOG_INVOCATION_COUNT = 6;
    /**
     * The period of the case and invocation sums without a copied sprite.
     */
    private static final int NO_SPRITE_PERIOD = 3;
    /**
     * The period of the cases whose copied sprites record their metadata: every odd case.
     */
    private static final int METADATA_CASE_PERIOD = 2;
    /**
     * The sprite type of the copied sprites, which the fog registration does not depend on.
     */
    private static final int COPIED_SPRITE_TYPE = 0;
    /**
     * The life of an allocated effect before its sprite is attached.
     */
    private static final int INITIAL_LIFE = 1;
    /**
     * The first life of an effect with a copied sprite.
     */
    private static final int COPIED_LIFE = 176;
    /**
     * The number of distinct lives of effects with a copied sprite, from {@link #COPIED_LIFE}.
     */
    private static final int COPIED_LIFE_COUNT = 3;
    /**
     * The mask of the case index byte in the recorded metadata value.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The DWORD fields before the slots of a fog registration record.
     */
    private static final int FOG_HEADER_WORDS = 7;
    /**
     * The captured DWORD fields of one slot of a fog registration record.
     */
    private static final int FOG_SLOT_WORDS = 5;
    /**
     * The DWORD fields after the slots of a fog registration record: the last metadata key and value.
     */
    private static final int FOG_TAIL_WORDS = 2;

    @Test
    void matches_native_sprite_effect_allocation_cases() throws Exception {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_SPRITE_EFFECT_ALLOCATION, ALLOCATION_CAPTURE_HASH);
        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer writtenRecord = ByteBuffer.allocate(RECORD_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        Sprite suppliedSprite = SpriteScenario.createSprite(SpriteIds.CURSOR_MARKER, 0, 0, SUPPLIED_SPRITE_ELEVATION);

        for (int capacity : POOL_CAPACITIES) {
            for (int pattern = 0; pattern < PATTERN_COUNT; pattern++) {
                SpriteEffectPool pool = new SpriteEffectPool(capacity);
                SpriteEffect startup = pool.allocate();

                Assertions.assertNotNull(startup);
                Assertions.assertEquals(STARTUP_POOL_INDEX, startup.getPoolIndex());

                pool.finishInitialization(startup, suppliedSprite);

                for (int step = 0; step < capacity + EXTRA_ALLOCATIONS; step++) {
                    boolean successful = pattern == ALL_SUCCESSFUL_PATTERN ||
                                         (pattern == ALTERNATE_PATTERN && step % ALTERNATE_PERIOD == 0) ||
                                         (pattern == THIRD_FAILING_PATTERN && step % FAILING_PERIOD != 0);
                    SpriteEffect effect = pool.allocate();

                    if (effect != null) {
                        pool.finishInitialization(effect, successful ? suppliedSprite : null);
                    }

                    writtenRecord.clear();
                    writtenRecord.putInt(capacity).putInt(pattern).putInt(step);
                    writtenRecord.putInt(effect != null && successful ? effect.getPoolIndex() : NO_EFFECT_INDEX);
                    writtenRecord.putInt(pool.getFirstFreeIndex()).putInt(pool.getLastFreeIndex());
                    writtenRecord.putInt(pool.getFirstActiveIndex()).putInt(pool.getLastActiveIndex());

                    int activeCount = 0;

                    for (int poolIndex = 0; poolIndex < capacity; poolIndex++) {
                        SpriteEffect slot = pool.getSpriteEffect(poolIndex);

                        writtenRecord.putInt(pool.getPreviousIndex(poolIndex)).putInt(pool.getNextIndex(poolIndex));
                        writtenRecord.putInt(slot.getLife());
                        writtenRecord.putInt(slot.getSpriteOrNull() == null ? NO_SPRITE_VALUE : SPRITE_VALUE);

                        Assertions.assertEquals(pool.isAllocated(poolIndex), pool.isActive(poolIndex));

                        if (pool.isActive(poolIndex)) {
                            activeCount++;
                        }
                    }

                    Assertions.assertEquals(activeCount, pool.getAllocatedCount());

                    SpriteEffectAssert.assertRecord(expected, writtenRecord, HEADER_WORDS, capacity, SLOT_WORDS, TAIL_WORDS);
                }
            }
        }

        Assertions.assertEquals(0, expected.remaining());
    }

    @RequiresInstallation
    @Test
    void matches_native_fog_registration_pool_cases() throws IOException, NoSuchAlgorithmException {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_FOG_REGISTRATION, FOG_REGISTRATION_CAPTURE_HASH);
        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer output = ByteBuffer.allocate(FOG_RECORD_CAPACITY).order(ByteOrder.LITTLE_ENDIAN);

        for (int caseIndex = 0; caseIndex < FOG_CASE_COUNT; caseIndex++) {
            SpriteEffectPool pool = new SpriteEffectPool(FOG_POOL_CAPACITY);

            for (int slotIndex = 0; slotIndex < FOG_POOL_CAPACITY; slotIndex++) {
                pool.getSpriteEffect(slotIndex).setLife(UNTOUCHED_LIFE + slotIndex);
            }

            int cloneCount = 0;
            int metadataCount = 0;
            int lastMetadataKey = 0;
            int lastMetadataValue = 0;

            for (int invocation = 0; invocation < FOG_INVOCATION_COUNT; invocation++) {
                SpriteEffect effect = pool.allocate(false);
                int returnedIndex = -1;

                if (effect != null) {
                    cloneCount++;

                    Sprite copied = (caseIndex + invocation) % NO_SPRITE_PERIOD == 0
                                    ? null
                                    : SpriteScenario.createSprite(COPIED_SPRITE_TYPE, 0, 0, 0, invocation);

                    effect.setLife(INITIAL_LIFE);

                    if (copied != null) {
                        effect.setLife(COPIED_LIFE + caseIndex % COPIED_LIFE_COUNT);
                    }

                    pool.finishInitialization(effect, copied);

                    if (copied != null) {
                        returnedIndex = effect.getPoolIndex();

                        if (caseIndex % METADATA_CASE_PERIOD != 0) {
                            metadataCount++;
                            lastMetadataKey   = invocation + 1;
                            lastMetadataValue = COPIED_LIFE + caseIndex % COPIED_LIFE_COUNT | ((caseIndex & BYTE_MASK) << Short.SIZE);
                        }
                    }
                }

                output.clear();
                output.putInt(returnedIndex).putInt(cloneCount).putInt(metadataCount);
                output.putInt(pool.getFirstFreeIndex()).putInt(pool.getLastFreeIndex());
                output.putInt(pool.getFirstActiveIndex()).putInt(pool.getLastActiveIndex());

                for (int slotIndex = 0; slotIndex < FOG_POOL_CAPACITY; slotIndex++) {
                    SpriteEffect stored = pool.getSpriteEffect(slotIndex);
                    Sprite storedSprite = stored.getSpriteOrNull();

                    output.putInt(pool.getPreviousIndex(slotIndex)).putInt(pool.getNextIndex(slotIndex));
                    output.putInt(stored.getLife());
                    output.putInt(storedSprite == null ? -1 : storedSprite.getPoolIndex());
                }

                output.putInt(lastMetadataKey).putInt(lastMetadataValue);

                SpriteEffectAssert.assertRecord(expected, output, FOG_HEADER_WORDS, FOG_POOL_CAPACITY, FOG_SLOT_WORDS, FOG_TAIL_WORDS);
            }
        }

        Assertions.assertFalse(expected.hasRemaining());
    }
}
