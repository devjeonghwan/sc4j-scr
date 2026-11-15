package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.scr.data.type.constant.RequirementOpcodes;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Describes the unit production requirement script of an added unit type ({@link ScrAddedUnitType.Builder#setRequirementScript}): a sequence of
 * requirement groups in the words of the installed production scripts ({@link RequirementOpcodes}), which the production test of the SC:R game
 * reads as it reads the script entry of a native unit type. Every group must hold; a group holds when one of its alternatives holds.
 * <p>
 * A unit type of a requirement is a native unit type. The first producer group decides which unit may produce the type: a producer of another
 * type fails as the wrong unit, unless a later alternative of the group names its type.
 */
public final class ScrRequirementScript {
    /**
     * The word capacity a builder starts with.
     */
    private static final int INITIAL_CAPACITY = 8;

    /**
     * Builds {@link ScrRequirementScript} instances.
     */
    public static final class Builder {
        private int @NotNull [] words;
        private int wordCount;

        /**
         * Creates a {@link Builder} instance without requirements: the built script lets any producer of the requirement test produce the type.
         */
        public Builder() {
            this.words     = new int[INITIAL_CAPACITY];
            this.wordCount = 0;
        }

        /**
         * Adds a group that requires the producer to be of a unit type.
         *
         * @param producerType the native unit type of the producer
         * @return this instance for chaining
         * @throws IllegalArgumentException if the type is not a native unit type
         */
        public @NotNull Builder requireProducer(@NotNull UnitType producerType) {
            return this.add(RequirementOpcodes.CURRENT_UNIT).add(ScrUnits.getNativeId(producerType));
        }

        /**
         * Adds a group that requires the owner to have a completed unit of a type.
         *
         * @param unitType the native unit type
         * @return this instance for chaining
         * @throws IllegalArgumentException if the type is not a native unit type
         */
        public @NotNull Builder requireCompleted(@NotNull UnitType unitType) {
            return this.add(ScrUnits.getNativeId(unitType));
        }

        /**
         * Adds a group that requires the owner to have a unit of a type, completed or not.
         *
         * @param unitType the native unit type
         * @return this instance for chaining
         * @throws IllegalArgumentException if the type is not a native unit type
         */
        public @NotNull Builder requireOwned(@NotNull UnitType unitType) {
            return this.add(RequirementOpcodes.MUST_HAVE).add(ScrUnits.getNativeId(unitType));
        }

        /**
         * Adds a group that requires the producer not to be producing, researching or upgrading.
         *
         * @return this instance for chaining
         */
        public @NotNull Builder requireNotBusy() {
            return this.add(RequirementOpcodes.NOT_BUSY);
        }

        /**
         * Adds a group that requires the producer not to be burrowed.
         *
         * @return this instance for chaining
         */
        public @NotNull Builder requireNotBurrowed() {
            return this.add(RequirementOpcodes.NOT_BURROWED);
        }

        /**
         * Adds a group that requires the expansion.
         *
         * @return this instance for chaining
         */
        public @NotNull Builder requireExpansion() {
            return this.add(RequirementOpcodes.EXPANSION);
        }

        /**
         * Makes the next requirement an alternative of the last group instead of a group of its own.
         *
         * @return this instance for chaining
         * @throws IllegalStateException if no requirement precedes it or the last word already joins an alternative
         */
        public @NotNull Builder or() {
            if (this.wordCount == 0 || this.words[this.wordCount - 1] == RequirementOpcodes.OR) {
                throw Exceptions.newIllegalStateException("An alternative needs a requirement before it.");
            }

            return this.add(RequirementOpcodes.OR);
        }

        /**
         * Builds a {@link ScrRequirementScript} instance with the current requirements.
         *
         * @return the built {@link ScrRequirementScript} instance
         * @throws IllegalStateException if the last word joins an alternative that does not follow
         */
        public @NotNull ScrRequirementScript build() {
            if (this.wordCount != 0 && this.words[this.wordCount - 1] == RequirementOpcodes.OR) {
                throw Exceptions.newIllegalStateException("The last alternative of the requirement script is missing.");
            }

            int[] script = Arrays.copyOf(this.words, this.wordCount + 1);

            script[this.wordCount] = RequirementOpcodes.END;

            return new ScrRequirementScript(script);
        }

        private @NotNull Builder add(int word) {
            if (this.wordCount == this.words.length) {
                this.words = Arrays.copyOf(this.words, this.words.length * 2);
            }

            this.words[this.wordCount++] = word;

            return this;
        }
    }

    private final int @NotNull [] words;

    private ScrRequirementScript(int @NotNull [] words) {
        this.words = words;
    }

    /**
     * Copies the words of the script.
     *
     * @return the requirement words, ending with {@link RequirementOpcodes#END}
     */
    public int @NotNull [] copyWords() {
        return this.words.clone();
    }
}
