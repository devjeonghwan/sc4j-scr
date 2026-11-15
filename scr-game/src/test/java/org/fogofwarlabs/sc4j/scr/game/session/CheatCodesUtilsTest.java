package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.game.state.RuntimeRandom;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks cheat-text hashing, flag toggling, rejected variants and the C runtime generator reseed of a check.
 */
final class CheatCodesUtilsTest {
    private static final int UNRELATED_FLAG = 0x10000;
    /**
     * The flag of the Power Overwhelming cheat.
     */
    private static final int POWER_OVERWHELMING_FLAG = 0x4;
    /**
     * Cheat flags that a text which is no cheat leaves as they are.
     */
    private static final int KEPT_FLAGS = 0x1234;
    /**
     * The result of a text that is no cheat.
     */
    private static final long NOT_A_CHEAT = -1L;
    /**
     * The cheat table hashes of the Show Me The Money and the Noglues cheats and of the Radio Free Zerg text, which leaves the flags unchanged.
     */
    private static final long SHOW_ME_THE_MONEY_HASH = 0x6e26e2b87fe014ccL;
    private static final long NOGLUES_HASH = 0x1ad0844966459a95L;
    private static final long RADIO_FREE_ZERG_HASH = 0x8fbb39123ce4870cL;
    /**
     * The generator state before a check, the time of the check and the key draws a check makes.
     */
    private static final int GAME_GENERATOR_STATE = 0x5EED1234;
    private static final int CHECK_TIME = 0x6543210F;
    private static final int KEY_DRAW_COUNT = 4;

    private static long apply(@NotNull String text, int currentFlags) {
        return CheatCodesUtils.apply(text, currentFlags, new RuntimeRandom(GAME_GENERATOR_STATE), CHECK_TIME);
    }

    @Test
    void apply_should_toggle_each_known_cheat_flag() {
        String[] texts = {"ophelia",
                          "show me the money", "whats mine is mine", "breathe deep",
                          "something for nothing", "medieval man",
                          "modify the phase variance", "food for thought", "the gathering",
                          "black sheep wall", "operation cwal", "power overwhelming",
                          "war aint what it used to be", "there is no cow level", "game over man", "staying alive", "noglues"};
        int[] flags = {0x200,
                       0x10, 0x40000, 0x80000,
                       0x8, 0x1000,
                       0x2000, 0x20000, 0x800,
                       0x1, 0x2, 0x4,
                       0x4000, 0x80, 0x40, 0x100, 0x20000000};

        for (int index = 0; index < texts.length; index++) {
            Assertions.assertEquals(flags[index], apply(texts[index], 0), texts[index]);
            Assertions.assertEquals(UNRELATED_FLAG ^ flags[index], apply(texts[index], UNRELATED_FLAG), texts[index]);
            Assertions.assertEquals(0, apply(texts[index], flags[index]), texts[index] + " toggles off");
        }
    }

    @Test
    void apply_should_lower_case_text_and_reject_variants() {
        Assertions.assertEquals(POWER_OVERWHELMING_FLAG, apply("Power Overwhelming", 0));
        Assertions.assertEquals(POWER_OVERWHELMING_FLAG, apply("POWER OVERWHELMING", 0));
        Assertions.assertEquals(NOT_A_CHEAT, apply("power overwhelming ", 0));
        Assertions.assertEquals(NOT_A_CHEAT, apply("game over", 0));
        Assertions.assertEquals(NOT_A_CHEAT, apply("hello", 0));
        Assertions.assertEquals(NOT_A_CHEAT, apply("", 0));
        Assertions.assertEquals(KEPT_FLAGS, apply("radio free zerg", KEPT_FLAGS));
    }

    @Test
    void apply_should_draw_the_key_from_the_game_generator_and_reseed_it_with_the_time() {
        RuntimeRandom matched = new RuntimeRandom(GAME_GENERATOR_STATE);
        RuntimeRandom discarded = new RuntimeRandom(GAME_GENERATOR_STATE);

        Assertions.assertEquals(POWER_OVERWHELMING_FLAG, CheatCodesUtils.apply("power overwhelming", 0, matched, CHECK_TIME));
        Assertions.assertEquals(NOT_A_CHEAT, CheatCodesUtils.apply("hello", 0, discarded, CHECK_TIME));

        for (RuntimeRandom generator : new RuntimeRandom[]{matched, discarded}) {
            Assertions.assertEquals(CHECK_TIME, generator.getState());
            Assertions.assertEquals(KEY_DRAW_COUNT, generator.getDrawCount());
        }
    }

    @Test
    void hash_should_reproduce_cheat_table_values_from_text() {
        Assertions.assertEquals(SHOW_ME_THE_MONEY_HASH, CheatCodesUtils.hash("show me the money"));
        Assertions.assertEquals(NOGLUES_HASH, CheatCodesUtils.hash("noglues"));
        Assertions.assertEquals(RADIO_FREE_ZERG_HASH, CheatCodesUtils.hash("radio free zerg"));
    }
}
