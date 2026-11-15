package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that relocating a hidden unit reveals it.
 */
@RequiresInstallation
final class UnitRelocationTest {
    private static final int MARINE_POSITION = 2900;
    private static final int RELOCATED_X = 2960;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void startGame() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime  = RunningGameScenario.start(gameData);
    }

    @AfterAll
    static void close() {
        runtime = null;

        if (gameData != null) {
            gameData.close();
        }

        gameData = null;
    }


    @Test
    void relocate_should_reveal_hidden_unit() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, MARINE_POSITION, MARINE_POSITION, RunningGameScenario.HUMAN);

            game.getUnitLifecycle().hide(marine, false);

            Sprite hiddenSprite = marine.getSpriteOrNull();

            Assertions.assertNotNull(hiddenSprite);
            Assertions.assertNotEquals(0, hiddenSprite.getFlags() & Sprite.FLAG_HIDDEN);

            game.getUnitRelocation().relocate(marine, RELOCATED_X, MARINE_POSITION);

            Sprite relocatedSprite = marine.getSpriteOrNull();

            Assertions.assertNotNull(relocatedSprite);
            Assertions.assertEquals(0, relocatedSprite.getFlags() & Sprite.FLAG_HIDDEN, "Relocation unhides the unit.");
        });
    }
}
