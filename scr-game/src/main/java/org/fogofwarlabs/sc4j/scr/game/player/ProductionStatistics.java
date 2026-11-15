package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Retains integer samples and float accumulators of player statistics (actions and production counters per player and category), with the retained
 * window of past samples a replay advances.
 */
public final class ProductionStatistics {
    /**
     * The number of values in one statistics sample.
     */
    public static final int VALUE_COUNT = 48;
    /**
     * The total category, which also receives every change of {@link #SUMMED_CATEGORY}.
     */
    public static final int TOTAL_CATEGORY = 0;
    /**
     * The category whose changes also add to {@link #TOTAL_CATEGORY}.
     */
    public static final int SUMMED_CATEGORY = 1;
    /**
     * The category of completed unit production.
     */
    public static final int PRODUCTION_CATEGORY = 2;
    /**
     * The statistics categories of one player: the values are {@code [owner * CATEGORY_COUNT + category]}.
     */
    public static final int CATEGORY_COUNT = 4;

    /**
     * The sample field of the frame.
     */
    private static final int FRAME_FIELD = 0;
    /**
     * The sample field of the elapsed time.
     */
    private static final int ELAPSED_FIELD = 1;
    /**
     * The sample fields before the values: frame and elapsed time.
     */
    private static final int SAMPLE_HEADER_FIELDS = 2;
    /**
     * The DWORD fields of one retained sample as {@link #getSampleField(int, int)} numbers them: frame, elapsed, values.
     */
    private static final int SAMPLE_FIELD_COUNT = SAMPLE_HEADER_FIELDS + VALUE_COUNT;
    /**
     * The initial number of retained samples.
     */
    private static final int INITIAL_SAMPLE_CAPACITY = 128;
    /**
     * The retained window of elapsed time in milliseconds.
     */
    private static final int SAMPLE_WINDOW = 6000;
    /**
     * The factor a full sample window grows its capacity by.
     */
    private static final int SAMPLE_CAPACITY_GROWTH = 2;
    /**
     * The float bits of the accumulator decay factor of a replay at the slowest speed.
     */
    private static final int SLOWEST_DECAY_FACTOR_BITS = 0x3F7F0D3D;
    /**
     * The float bits of the accumulator decay factor of a replay at the slower speed.
     */
    private static final int SLOWER_DECAY_FACTOR_BITS = 0x3F7F5E8B;
    /**
     * The float bits of the accumulator decay factor of a replay at the slow speed.
     */
    private static final int SLOW_DECAY_FACTOR_BITS = 0x3F7F873C;
    /**
     * The float bits of the accumulator decay factor of a replay at the normal speed.
     */
    private static final int NORMAL_DECAY_FACTOR_BITS = 0x3F7F9E7F;
    /**
     * The float bits of the accumulator decay factor of a replay at the fast speed.
     */
    private static final int FAST_DECAY_FACTOR_BITS = 0x3F7FAE7F;
    /**
     * The float bits of the accumulator decay factor of a replay at the faster speed.
     */
    private static final int FASTER_DECAY_FACTOR_BITS = 0x3F7FBA22;
    /**
     * The float bits of the accumulator decay factor of a replay at the fastest speed.
     */
    private static final int FASTEST_DECAY_FACTOR_BITS = 0x3F7FC2DD;
    /**
     * The multiplier that folds each statistics value and retained sample field into the diagnostic hash.
     */
    private static final int HASH_MULTIPLIER = 31;

    /**
     * One retained sample: its frame, elapsed time and values.
     */
    private static final class Sample {
        private int frame;
        private int elapsed;

        private final int @NotNull [] values;

        private Sample() {
            this.frame   = 0;
            this.elapsed = 0;

            this.values = new int[VALUE_COUNT];
        }
    }

    /**
     * Creates a retained sample window with a new {@link Sample} in every record.
     *
     * @param capacity the number of sample records
     * @return the created sample records
     */
    private static @NotNull Sample @NotNull [] createSamples(int capacity) {
        Sample[] samples = new Sample[capacity];

        for (int index = 0; index < capacity; index++) {
            samples[index] = new Sample();
        }

        return samples;
    }

    private boolean samplePresent;
    private final int @NotNull [] sampleValues;
    private final float @NotNull [] accumulatedValues;

    private @NotNull Sample @NotNull [] samples;
    private int firstSample;
    private int sampleCount;

    /**
     * Creates a {@link ProductionStatistics} instance from explicitly established native storage.
     *
     * @param samplePresent     whether the original sample count QWORD is nonzero
     * @param sampleValues      the first sample's values, one group of categories per player slot
     * @param accumulatedValues the float accumulators, one group of categories per player slot
     * @throws IllegalArgumentException if either snapshot has the wrong size
     */
    public ProductionStatistics(boolean samplePresent, int @NotNull [] sampleValues, float @NotNull [] accumulatedValues) {
        if (sampleValues.length != VALUE_COUNT || accumulatedValues.length != VALUE_COUNT) {
            throw Exceptions.newIllegalArgumentException("Native production statistics require 48 values in each snapshot.");
        }

        this.samplePresent     = samplePresent;
        this.sampleValues      = sampleValues.clone();
        this.accumulatedValues = accumulatedValues.clone();

        this.samples     = createSamples(INITIAL_SAMPLE_CAPACITY);
        this.firstSample = 0;
        this.sampleCount = 0;
    }

    /**
     * @return the retained native accounting sample count
     */
    public int getSampleCount() {
        return this.sampleCount;
    }

    /**
     * @param position the chronological retained sample position
     * @param field    the DWORD field: frame, elapsed time, then the values
     * @return the stored DWORD bits
     * @throws IndexOutOfBoundsException if the position lies outside the retained samples or the field outside the sample fields
     */
    public int getSampleField(int position, int field) {
        Objects.checkIndex(position, this.sampleCount);
        Objects.checkIndex(field, SAMPLE_FIELD_COUNT);

        if (position == this.sampleCount - 1 && field >= SAMPLE_HEADER_FIELDS) {
            return this.sampleValues[field - SAMPLE_HEADER_FIELDS];
        }

        Sample sample = this.samples[(this.firstSample + position) % this.samples.length];

        return switch (field) {
            case FRAME_FIELD -> sample.frame;
            case ELAPSED_FIELD -> sample.elapsed;
            default -> sample.values[field - SAMPLE_HEADER_FIELDS];
        };
    }

    /**
     * @param index the storage index in {@code 0..VALUE_COUNT - 1}
     * @return the current sample DWORD bits
     */
    public int getSampleValue(int index) {
        return this.sampleValues[index];
    }

    /**
     * @param index the storage index in {@code 0..VALUE_COUNT - 1}
     * @return the accumulated scalar float
     */
    public float getAccumulatedValue(int index) {
        return this.accumulatedValues[index];
    }


    /**
     * Applies the fresh accounting reset and the first sample insertion.
     *
     * @param frame the native main-context frame DWORD
     */
    public void initializeSample(int frame) {
        Arrays.fill(this.sampleValues, 0);
        Arrays.fill(this.accumulatedValues, 0.0f);

        for (Sample sample : this.samples) {
            sample.frame   = 0;
            sample.elapsed = 0;

            Arrays.fill(sample.values, 0);
        }

        this.samplePresent    = true;
        this.firstSample      = 0;
        this.sampleCount      = 1;
        this.samples[0].frame = frame;
    }

    /**
     * Advances the replay accounting window and the float decay.
     * <p>
     * A replay reads the unscaled duration of its recorded speed. The decay factors keep the original float results for those exact inputs; the
     * configuration-dependent publication is skipped in a replay.
     *
     * @param duration the recorded replay speed's unscaled duration
     * @throws UnsupportedOperationException if the duration is not an unscaled game speed duration or the first sample is not initialized
     */
    public void advanceReplayFrame(int duration) {
        int factorBits = switch (duration) {
            case GameSpeed.SLOWEST_DURATION -> SLOWEST_DECAY_FACTOR_BITS;
            case GameSpeed.SLOWER_DURATION -> SLOWER_DECAY_FACTOR_BITS;
            case GameSpeed.SLOW_DURATION -> SLOW_DECAY_FACTOR_BITS;
            case GameSpeed.NORMAL_DURATION -> NORMAL_DECAY_FACTOR_BITS;
            case GameSpeed.FAST_DURATION -> FAST_DECAY_FACTOR_BITS;
            case GameSpeed.FASTER_DURATION -> FASTER_DECAY_FACTOR_BITS;
            case GameSpeed.FASTEST_DURATION -> FASTEST_DECAY_FACTOR_BITS;
            default -> throw Exceptions.newUnsupportedOperationException("Replay accounting requires an original unscaled duration.");
        };

        if (this.sampleCount == 0) {
            throw Exceptions.newUnsupportedOperationException("Replay accounting requires its initialized first sample.");
        }

        int capacity = this.samples.length;
        Sample previous = this.samples[(this.firstSample + this.sampleCount - 1) % capacity];

        System.arraycopy(this.sampleValues, 0, previous.values, 0, VALUE_COUNT);

        int frame = previous.frame + 1;
        int elapsed = previous.elapsed + duration;

        if (this.sampleCount == capacity) {
            Sample[] expanded = createSamples(capacity * SAMPLE_CAPACITY_GROWTH);

            for (int sampleIndex = 0; sampleIndex < this.sampleCount; sampleIndex++) {
                expanded[sampleIndex] = this.samples[(this.firstSample + sampleIndex) % capacity];
            }

            this.samples     = expanded;
            this.firstSample = 0;
            capacity *= SAMPLE_CAPACITY_GROWTH;
        }

        Sample next = this.samples[(this.firstSample + this.sampleCount) % capacity];

        next.frame   = frame;
        next.elapsed = elapsed;

        System.arraycopy(this.sampleValues, 0, next.values, 0, VALUE_COUNT);

        this.sampleCount++;

        while (Integer.compareUnsigned(elapsed - this.samples[this.firstSample].elapsed, SAMPLE_WINDOW) > 0) {
            this.firstSample = (this.firstSample + 1) % capacity;
            this.sampleCount--;
        }

        float factor = Float.intBitsToFloat(factorBits);

        for (int valueIndex = 0; valueIndex < VALUE_COUNT; valueIndex++) {
            this.accumulatedValues[valueIndex] *= factor;
        }
    }

    /**
     * Records a statistics change of one player and category: the change adds to the sample value and, converted from its unsigned DWORD to a float,
     * to the accumulator; a {@link #SUMMED_CATEGORY} change also adds to {@link #TOTAL_CATEGORY}. An owner outside the player slots, a category from
     * {@link #CATEGORY_COUNT} on, or a statistics storage without a sample records nothing.
     *
     * @param owner    the unsigned DWORD player index
     * @param category the signed DWORD category
     * @param change   the increment DWORD bits
     * @throws UnsupportedOperationException if a negative category addresses unrepresented memory
     */
    public void record(int owner, int category, int change) {
        if (Integer.compareUnsigned(owner, PlayerTable.PLAYER_SLOTS) >= 0 || category >= CATEGORY_COUNT || !this.samplePresent) {
            return;
        }

        if (category < 0) {
            throw Exceptions.newUnsupportedOperationException("Negative statistics categories address unrepresented native storage.");
        }

        int index = owner * CATEGORY_COUNT + category;

        this.sampleValues[index] += change;

        if (category == SUMMED_CATEGORY) {
            this.sampleValues[owner * CATEGORY_COUNT + TOTAL_CATEGORY] += change;
        }

        float increment = (float) Integer.toUnsignedLong(change);

        this.accumulatedValues[index] += increment;

        if (category == SUMMED_CATEGORY) {
            this.accumulatedValues[owner * CATEGORY_COUNT + TOTAL_CATEGORY] += increment;
        }
    }

    /**
     * Folds the sample presence, both established statistics snapshots and the retained samples into a diagnostic hash.
     *
     * @param hash the preceding diagnostic hash
     * @return the hash including the sample presence, both established statistics snapshots and the retained samples
     */
    public int computeStateHash(int hash) {
        hash = HASH_MULTIPLIER * hash + (this.samplePresent ? 1 : 0);

        for (int index = 0; index < VALUE_COUNT; index++) {
            hash = HASH_MULTIPLIER * hash + this.sampleValues[index];
            hash = HASH_MULTIPLIER * hash + Float.floatToRawIntBits(this.accumulatedValues[index]);
        }

        hash = HASH_MULTIPLIER * hash + this.sampleCount;

        for (int sampleIndex = 0; sampleIndex < this.sampleCount; sampleIndex++) {
            for (int field = 0; field < SAMPLE_FIELD_COUNT; field++) {
                hash = HASH_MULTIPLIER * hash + this.getSampleField(sampleIndex, field);
            }
        }

        return hash;
    }
}
