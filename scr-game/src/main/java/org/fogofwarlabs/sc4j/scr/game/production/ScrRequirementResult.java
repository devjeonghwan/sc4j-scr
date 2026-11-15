package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.requirement.RequirementResult;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the result of an SC:R requirement test as the engine sees it: the native result word, which is {@code 0} when the
 * requirement holds and otherwise a failure code, {@link ScrRequirementSystem#FAILED_WITHOUT_CODE}, and possibly
 * {@link ScrRequirementSystem#DISABLED}. One shared instance exists per result word, so a check allocates nothing.
 */
public final class ScrRequirementResult implements RequirementResult {
    /**
     * The number of result words without the disabled flag: every failure code below {@code 0x100} and
     * {@link ScrRequirementSystem#FAILED_WITHOUT_CODE}.
     */
    private static final int CODE_COUNT = ScrRequirementSystem.FAILED_WITHOUT_CODE + 1;

    private static final @NotNull ScrRequirementResult @NotNull [] RESULTS = new ScrRequirementResult[CODE_COUNT * 2];

    static {
        for (int code = 0; code < CODE_COUNT; code++) {
            RESULTS[code]              = new ScrRequirementResult(code);
            RESULTS[CODE_COUNT + code] = new ScrRequirementResult(code | ScrRequirementSystem.DISABLED);
        }
    }

    /**
     * Returns the shared result of a native result word.
     *
     * @param result the native result word
     * @return the shared {@link ScrRequirementResult} instance of the word
     * @throws IllegalArgumentException if the word is not a requirement test result
     */
    public static @NotNull ScrRequirementResult of(int result) {
        int code = result & ~ScrRequirementSystem.DISABLED;

        if (code < 0 || code >= CODE_COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid requirement test result: {0}", result);
        }

        return RESULTS[ScrRequirementSystem.isDisabled(result) ? CODE_COUNT + code : code];
    }


    private final int result;

    private ScrRequirementResult(int result) {
        this.result = result;
    }

    /**
     * @return the native result word
     */
    public int getResult() {
        return this.result;
    }

    /**
     * @return {@code true} if the requirement is met, {@code false} otherwise
     */
    @Override
    public boolean isMet() {
        return ScrRequirementSystem.isAccepted(this.result);
    }

    /**
     * @return the failure code of the result, or {@code 0} when it holds or failed without a code
     */
    public int getFailureCode() {
        return ScrRequirementSystem.getFailureCode(this.result);
    }
}
