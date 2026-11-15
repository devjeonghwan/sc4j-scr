package org.fogofwarlabs.sc4j.scr.game.player;

/**
 * Retains player identity, game setup and resource balances for one player slot.
 * <p>
 * Command identity is independent of the slot's position in the game. Resource writes preserve Java integer overflow and do not clamp negative
 * values.
 */
public final class Player {
    /**
     * The victory state of a player whose connection dropped.
     */
    public static final int VICTORY_STATE_DROPPED = 1;
    /**
     * The victory state of a defeated player, also of a player who left.
     */
    public static final int VICTORY_STATE_DEFEATED = 2;

    /**
     * The mask of the byte the type, race, team and victory state keep.
     */
    private static final int BYTE_MASK = 0xFF;

    private int commandPlayerIndex;
    private int id;
    private int type;
    private int race;
    private int team;

    private int victoryState;

    private int minerals;
    private int gas;

    private int gatheredMinerals;
    private int gatheredGas;

    private int color;

    /**
     * Creates a {@link Player} instance.
     *
     * @param commandPlayerIndex the stored command player index, independent of the record position
     * @param id                 the recorded issuer comparison value, preserving all {@code 32} bits
     * @param type               the player type, truncated to its unsigned byte representation
     * @param race               the player race, truncated to its unsigned byte representation
     * @param team               the player team, truncated to its unsigned byte representation
     */
    public Player(int commandPlayerIndex, int id, int type, int race, int team) {
        this.setRecord(commandPlayerIndex, id, type, race, team);

        this.victoryState = 0;

        this.minerals = 0;
        this.gas      = 0;

        this.gatheredMinerals = 0;
        this.gatheredGas      = 0;

        this.color = 0;
    }

    /**
     * @return the stored command player index, preserving all {@code 32} bits
     */
    public int getCommandPlayerIndex() {
        return this.commandPlayerIndex;
    }

    /**
     * @return the recorded issuer comparison value, preserving all {@code 32} bits
     */
    public int getId() {
        return this.id;
    }

    /**
     * @return the player type
     */
    public int getType() {
        return this.type;
    }

    /**
     * Sets the player type, including the human or computer departure state.
     *
     * @param type the new player type, truncated to its unsigned byte representation
     */
    public void setType(int type) {
        this.type = type & BYTE_MASK;
    }

    /**
     * @return the player race
     */
    public int getRace() {
        return this.race;
    }

    /**
     * @return the player team
     */
    public int getTeam() {
        return this.team;
    }

    /**
     * @return the player outcome byte used by trigger eligibility
     */
    public int getVictoryState() {
        return this.victoryState;
    }

    /**
     * Sets the player outcome without applying victory, defeat or player-removal effects.
     *
     * @param victoryState the outcome, truncated to its unsigned byte representation
     */
    public void setVictoryState(int victoryState) {
        this.victoryState = victoryState & BYTE_MASK;
    }

    /**
     * @return the minerals
     */
    public int getMinerals() {
        return this.minerals;
    }

    /**
     * Sets the minerals.
     *
     * @param minerals the new minerals
     */
    public void setMinerals(int minerals) {
        this.minerals = minerals;
    }

    /**
     * @return the gas
     */
    public int getGas() {
        return this.gas;
    }

    /**
     * Sets the gas.
     *
     * @param gas the new gas
     */
    public void setGas(int gas) {
        this.gas = gas;
    }

    /**
     * @return the cumulative gathered minerals, including the starting minerals
     */
    public int getGatheredMinerals() {
        return this.gatheredMinerals;
    }

    /**
     * Sets the cumulative gathered minerals.
     *
     * @param gatheredMinerals the new cumulative gathered minerals
     */
    public void setGatheredMinerals(int gatheredMinerals) {
        this.gatheredMinerals = gatheredMinerals;
    }

    /**
     * @return the cumulative gathered gas
     */
    public int getGatheredGas() {
        return this.gatheredGas;
    }

    /**
     * Sets the cumulative gathered gas.
     *
     * @param gatheredGas the new cumulative gathered gas
     */
    public void setGatheredGas(int gatheredGas) {
        this.gatheredGas = gatheredGas;
    }

    /**
     * @return the display color index selecting the player's {@code tunit.pcx} remapping row
     */
    public int getColor() {
        return this.color;
    }

    /**
     * Sets the display color index.
     * <p>
     * The color affects presentation only and is not part of the simulation state hash.
     *
     * @param color the color index
     */
    public void setColor(int color) {
        this.color = color;
    }

    /**
     * Sets the identity fields used by classic player-table writes. Command index and identifier keep all bits; type, race and team use their low
     * bytes.
     *
     * @param commandPlayerIndex the stored command player index, independent of the record position
     * @param id                 the recorded issuer comparison value, preserving all {@code 32} bits
     * @param type               the player type, truncated to its unsigned byte representation
     * @param race               the player race, truncated to its unsigned byte representation
     * @param team               the player team, truncated to its unsigned byte representation
     */
    public void setRecord(int commandPlayerIndex, int id, int type, int race, int team) {
        this.commandPlayerIndex = commandPlayerIndex;
        this.id                 = id;
        this.type               = type & BYTE_MASK;
        this.race               = race & BYTE_MASK;
        this.team               = team & BYTE_MASK;
    }


    /**
     * Tests whether the player is out of the game: a player who left ({@link PlayerTypes#PLAYER_LEFT}, {@link PlayerTypes#COMPUTER_LEFT}), or a human
     * or computer player whose victory state is {@link #VICTORY_STATE_DROPPED} or {@link #VICTORY_STATE_DEFEATED}.
     *
     * @return {@code true} if the player is out of the game, {@code false} otherwise
     */
    public boolean isFinished() {
        if (this.type == PlayerTypes.PLAYER_LEFT || this.type == PlayerTypes.COMPUTER_LEFT) {
            return true;
        }

        return (this.type == PlayerTypes.HUMAN || this.type == PlayerTypes.COMPUTER) &&
               (this.victoryState == VICTORY_STATE_DROPPED || this.victoryState == VICTORY_STATE_DEFEATED);
    }

    /**
     * Tests whether the player takes part in the game: a computer, human, rescuable or neutral player that is not out of the game
     * ({@link #isFinished}).
     *
     * @return {@code true} if the player is active, {@code false} otherwise
     */
    public boolean isActive() {
        if ((this.type < PlayerTypes.COMPUTER || this.type > PlayerTypes.RESCUE_ACTIVE) && this.type != PlayerTypes.NEUTRAL) {
            return false;
        }

        return !this.isFinished();
    }
}
