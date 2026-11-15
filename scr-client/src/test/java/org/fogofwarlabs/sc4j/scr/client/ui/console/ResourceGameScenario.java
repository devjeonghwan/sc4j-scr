package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;

/**
 * Builds games with one human player whose minerals, gas and supply counters the resource displays read.
 * <p>
 * The supply counters are written by their game-context offsets, because their layout is EUD-visible game state.
 */
public final class ResourceGameScenario {
    /**
     * The player slot of the built games.
     */
    public static final int PLAYER = 0;

    /**
     * The game-context offsets of the Zerg provided supply, used supply and supply cap DWORDs of player {@code 0}.
     */
    private static final int SUPPLY_PROVIDED_OFFSET = 0x3054;
    private static final int SUPPLY_USED_OFFSET = 0x3084;
    private static final int SUPPLY_CAPS_OFFSET = 0x30B4;
    /**
     * The distance between the supply counters of two races: three groups of a DWORD per player slot.
     */
    private static final int SUPPLY_RACE_SIZE = 0x90;

    /**
     * Creates a {@link Game} instance whose player {@link #PLAYER} has the given race and resources.
     *
     * @param accounting the unit accounting of the game
     * @param race       the player's race: {@code 0} Zerg, {@code 1} Terran, {@code 2} Protoss
     * @param minerals   the player's minerals
     * @param gas        the player's gas
     * @return the created {@link Game} instance
     */
    public static @NotNull Game createGame(@NotNull UnitAccounting accounting, int race, int minerals, int gas) {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        Player player = new Player(PLAYER, PLAYER, PlayerTypes.HUMAN, race, 0);

        player.setMinerals(minerals);
        player.setGas(gas);

        game.getPlayerTable().setPlayer(PLAYER, player);
        game.getPlayerTable().setUnitAccounting(accounting);

        return game;
    }

    /**
     * Sets the half-supply counters of player {@link #PLAYER} for one race.
     *
     * @param accounting the unit accounting
     * @param race       the supply race: {@code 0} Zerg, {@code 1} Terran, {@code 2} Protoss
     * @param used       the used half-supply DWORD
     * @param provided   the provided half-supply DWORD
     * @param cap        the half-supply cap DWORD
     */
    public static void setSupply(@NotNull UnitAccounting accounting, int race, int used, int provided, int cap) {
        int raceOffset = race * SUPPLY_RACE_SIZE + PLAYER * Integer.BYTES;

        accounting.writeContextDword(SUPPLY_USED_OFFSET + raceOffset, used);
        accounting.writeContextDword(SUPPLY_PROVIDED_OFFSET + raceOffset, provided);
        accounting.writeContextDword(SUPPLY_CAPS_OFFSET + raceOffset, cap);
    }


    private ResourceGameScenario() {
    }
}
