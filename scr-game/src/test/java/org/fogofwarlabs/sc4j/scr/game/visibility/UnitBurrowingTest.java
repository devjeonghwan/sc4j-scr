package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that unburrowing keeps the sprite flags other than the burrowed flag.
 */
@RequiresInstallation
final class UnitBurrowingTest {
    /**
     * The instruction budget of every order and script call.
     */
    private static final int INSTRUCTION_LIMIT = 10000;
    /**
     * The pool index of the created unit.
     */
    private static final int UNIT_POOL_INDEX = SelectedUnitScenario.UNIT_POOL_INDEX;
    /**
     * The selected sprite flag, which unburrowing keeps.
     */
    private static final int SELECTED_SPRITE_FLAG = 0x08;
    /**
     * The sprite flags of a selected ({@code 0x08}) and a burrowed ({@code 0x40}) sprite.
     */
    private static final int SELECTED_OR_BURROWED_FLAGS = 0x48;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }

    /**
     * Creates a {@link Game} instance on a small installed-tileset map with one completed unit of the given type, owned and selected by the first
     * player ({@link SelectedUnitScenario#createGame}).
     *
     * @param unitType the unit type
     * @return the created {@link Game} instance
     */
    private static @NotNull Game createGame(int unitType) {
        Assertions.assertNotNull(gameData);

        return SelectedUnitScenario.createGame(gameData, unitType);
    }

    @Test
    void update_unburrowing_should_clear_only_the_burrowed_sprite_flag() {
        Game game = createGame(ScrUnitIds.ZERG_ZERGLING);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit zergling = unitPool.getUnit(UNIT_POOL_INDEX);
        Sprite sprite = zergling.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        game.getUnitBurrowing().setBurrowed(zergling);
        sprite.setFlags(sprite.getFlags() | Sprite.FLAG_SELECTED);
        zergling.setPrimaryOrderState(0);
        game.getUnitBurrowing().updateUnburrowing(zergling, INSTRUCTION_LIMIT);

        Assertions.assertEquals(SELECTED_SPRITE_FLAG, sprite.getFlags() & SELECTED_OR_BURROWED_FLAGS, "Unburrowing keeps the selection flags.");
    }
}
