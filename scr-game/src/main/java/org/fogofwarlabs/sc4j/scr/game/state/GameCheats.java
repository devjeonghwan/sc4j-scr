package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUpgradeTypeView;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.production.ScrRequirementSystem;
import org.fogofwarlabs.sc4j.scr.game.production.UnitBuildTime;
import org.fogofwarlabs.sc4j.scr.game.trigger.ScrTriggerSystem;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitEnergy;
import org.fogofwarlabs.sc4j.scr.game.visibility.MapVisibility;
import org.jetbrains.annotations.NotNull;

/**
 * Applies recorded cheat commands with the original game-option and game-mode gates, and owns the bits of the cheat
 * flags.
 * <p>
 * Only the Cheat command reaches this class; chat text is never interpreted as a cheat here. The full flags are stored
 * first. A change of {@link #WAR_AINT_WHAT_IT_USED_TO_BE} refreshes the display and a change of {@link #BLACK_SHEEP_WALL}
 * applies the reveal update, both only while the game runs (the only mode in which recorded commands execute). Then the
 * resource bits are consumed, then {@link #SOMETHING_FOR_NOTHING} raises every upgrade below its {@code upgrades.dat} maximum by
 * one level for each human player and refreshes the unit speed effects.
 * <p>
 * The other bits are only stored, and other systems read them: {@link #OPERATION_CWAL} production and construction
 * ({@link Game#isOperationCwal()}, {@link UnitBuildTime}), {@link #POWER_OVERWHELMING} damage
 * ({@link #suppressesDamage}), {@link #FOOD_FOR_THOUGHT} supply ({@link UnitAccounting}), {@link #THE_GATHERING} energy
 * ({@link UnitEnergy}), {@link #MEDIEVAL_MAN} and {@link #MODIFY_THE_PHASE_VARIANCE} the requirements
 * ({@link ScrRequirementSystem}), and the result bits {@link #GAME_OVER_MAN}, {@link #THERE_IS_NO_COW_LEVEL} and
 * {@link #STAYING_ALIVE} the trigger results ({@link ScrTriggerSystem}). The bits {@link #OPHELIA}, {@link #UNIDENTIFIED_CHEAT}
 * and {@link #NOGLUES} are read only by interface, sound and game-start code.
 */
public final class GameCheats {
    /**
     * The Black Sheep Wall bit of the cheat flags: the map is revealed.
     */
    public static final int BLACK_SHEEP_WALL = 0x1;
    /**
     * The Operation Cwal bit of the cheat flags: production and construction run faster ({@link Game#isOperationCwal()}).
     */
    public static final int OPERATION_CWAL = 0x2;
    /**
     * The Power Overwhelming bit of the cheat flags: damage from players that are not human is suppressed.
     */
    public static final int POWER_OVERWHELMING = 0x4;
    /**
     * The Something for Nothing bit of the cheat flags: a one-shot upgrade of every upgrade by one level.
     */
    public static final int SOMETHING_FOR_NOTHING = 0x8;
    /**
     * The Show Me the Money bit of the cheat flags: a one-shot grant of minerals and gas.
     */
    public static final int SHOW_ME_THE_MONEY = 0x10;
    /**
     * The Game Over Man bit of the cheat flags: the local player's trigger pass result becomes defeat.
     */
    public static final int GAME_OVER_MAN = 0x40;
    /**
     * The There Is No Cow Level bit of the cheat flags: the local player's trigger pass result becomes victory.
     */
    public static final int THERE_IS_NO_COW_LEVEL = 0x80;
    /**
     * The Staying Alive bit of the cheat flags: a trigger pass result of defeat or more is cleared.
     */
    public static final int STAYING_ALIVE = 0x100;
    /**
     * The Ophelia bit of the cheat flags; the simulation does not read it.
     */
    public static final int OPHELIA = 0x200;
    /**
     * The The Gathering bit of the cheat flags: energy is unlimited.
     */
    public static final int THE_GATHERING = 0x800;
    /**
     * The Medieval Man bit of the cheat flags: technology availability and research requirements are waived.
     */
    public static final int MEDIEVAL_MAN = 0x1000;
    /**
     * The Modify the Phase Variance bit of the cheat flags: the unit and add-on requirements of production are waived.
     */
    public static final int MODIFY_THE_PHASE_VARIANCE = 0x2000;
    /**
     * The War Aint What It Used to Be bit of the cheat flags: the fog of war is disabled.
     */
    public static final int WAR_AINT_WHAT_IT_USED_TO_BE = 0x4000;
    /**
     * The Food for Thought bit of the cheat flags: the available supply ignores the provided supply.
     */
    public static final int FOOD_FOR_THOUGHT = 0x20000;
    /**
     * The Whats Mine Is Mine bit of the cheat flags: a one-shot grant of minerals.
     */
    public static final int WHATS_MINE_IS_MINE = 0x40000;
    /**
     * The Breathe Deep bit of the cheat flags: a one-shot grant of gas.
     */
    public static final int BREATHE_DEEP = 0x80000;
    /**
     * The bit of the cheat flags toggled by the one stored cheat hash whose text is not known; the simulation does not read it.
     */
    public static final int UNIDENTIFIED_CHEAT = 0x100000;
    /**
     * The Noglues bit of the cheat flags; the simulation does not read it.
     */
    public static final int NOGLUES = 0x20000000;

    /**
     * The one-shot resource bits, consumed in this order.
     */
    private static final int @NotNull [] RESOURCE_FLAGS = {SHOW_ME_THE_MONEY, WHATS_MINE_IS_MINE, BREATHE_DEEP};
    /**
     * The minerals and the gas {@link #SHOW_ME_THE_MONEY} grants each human player.
     */
    private static final int SHOW_ME_THE_MONEY_AMOUNT = 10000;
    /**
     * The minerals of {@link #WHATS_MINE_IS_MINE} and the gas of {@link #BREATHE_DEEP} granted each human player.
     */
    private static final int RESOURCE_CHEAT_AMOUNT = 500;
    /**
     * The player value of no player: the attacking player of a source without a player, or the sender of a cheat without one.
     */
    private static final int NO_PLAYER = -1;

    private final @NotNull Game game;

    /**
     * Creates a {@link GameCheats} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public GameCheats(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies the Power Overwhelming gate of the weapon damage and of the unit damage: while {@link #POWER_OVERWHELMING}
     * is set, damage from a player that is not an occupied human slot, or from no player, is replaced by zero. Only the
     * unit-damage gate tests for no player ({@code -1}); the weapon gate indexes the player record directly.
     *
     * @param sourceOwner the attacking player, or {@code -1} for none
     * @param unitDamage  whether this is the unit-damage gate, which treats no player as not human
     * @return {@code true} if the damage is replaced by zero, {@code false} otherwise
     * @throws UnsupportedOperationException if the weapon gate would read outside the player records
     */
    public boolean suppressesDamage(int sourceOwner, boolean unitDamage) {
        if ((this.game.getCheatFlags() & POWER_OVERWHELMING) == 0) {
            return false;
        }

        if (sourceOwner == NO_PLAYER && unitDamage) {
            return true;
        }

        // The weapon gate requires an owner within the represented player records.
        if (sourceOwner < 0 || sourceOwner >= PlayerTable.PLAYER_SLOTS) {
            throw Exceptions.newUnsupportedOperationException(
                    "Power Overwhelming weapon damage for player {0} reads outside the player records.",
                    sourceOwner
            );
        }

        Player player = this.game.getPlayerTable().getPlayer(sourceOwner);

        return player == null || player.getType() != PlayerTypes.HUMAN;
    }


    /**
     * Applies production acceleration, one-shot resource grants and upgrades to occupied players, with no animation budget
     * limit for the unit speed refreshes.
     *
     * @param flags the recorded full cheat bit field
     */
    public void apply(int flags) {
        this.apply(flags, Integer.MAX_VALUE);
    }

    /**
     * Applies production acceleration, one-shot resource grants and upgrades to occupied players.
     *
     * @param flags            the recorded full cheat bit field
     * @param instructionLimit the animation budget for unit speed refreshes
     */
    public void apply(int flags, int instructionLimit) {
        this.apply(flags, NO_PLAYER, instructionLimit);
    }

    /**
     * Applies a cheat command of a player: as {@link #apply(int, int)}, then writes the cheat-on or cheat-off message
     * through the message lines when the recorded flags differ from the previous ones.
     *
     * @param flags            the recorded full cheat bit field
     * @param sender           the commanding player slot, {@code -1} for none
     * @param instructionLimit the animation budget for unit speed refreshes
     * @throws IllegalStateException if the frame controls or the player upgrade levels are not initialized
     * @throws NullPointerException  if the upgrade cheat runs in a world built without game data
     */
    public void apply(int flags, int sender, int instructionLimit) {
        // Recorded commands only execute while the game runs.
        if (!this.game.areCheatsEnabled()) {
            return;
        }

        GameFrame frame = this.game.getFrame();

        if (frame == null) {
            throw Exceptions.newIllegalStateException("Cheat commands require the initialized frame controls.");
        }

        MapVisibility visibility = frame.getVisibility();

        // A network game with one human applies cheats as a single-player game does.
        if (visibility.getVisibilityOverride() != 0) {
            int humanCount = 0;

            for (int playerIndex = 0; playerIndex < PlayerTable.PLAYABLE_PLAYER_SLOTS; playerIndex++) {
                Player player = this.game.getPlayerTable().getPlayer(playerIndex);

                if (player != null && player.getType() == PlayerTypes.HUMAN) {
                    humanCount++;
                }
            }

            if (humanCount > 1) {
                return;
            }
        }

        int previousFlags = visibility.getCheatFlags();

        visibility.setCheatFlags(flags);

        UnitAccounting accounting = this.game.getPlayerTable().getUnitAccounting();

        if (accounting != null) {
            accounting.setSupplyProvidedIgnored((flags & FOOD_FOR_THOUGHT) != 0);
        }

        if (((previousFlags ^ flags) & WAR_AINT_WHAT_IT_USED_TO_BE) != 0) {
            visibility.refreshDisplay();
        }

        if (((visibility.getCheatFlags() ^ previousFlags) & BLACK_SHEEP_WALL) != 0) {
            visibility.applyRevealCheat(this.game.getMap());
            visibility.refreshDisplay();
        }

        for (int resourceFlag : RESOURCE_FLAGS) {
            if ((visibility.getCheatFlags() & resourceFlag) == 0) {
                continue;
            }

            visibility.setCheatFlags(visibility.getCheatFlags() & ~resourceFlag);

            int amount = resourceFlag == SHOW_ME_THE_MONEY ? SHOW_ME_THE_MONEY_AMOUNT : RESOURCE_CHEAT_AMOUNT;

            for (int playerIndex = 0; playerIndex < PlayerTable.PLAYER_SLOTS; playerIndex++) {
                Player player = this.game.getPlayerTable().getPlayer(playerIndex);

                if (player == null || player.getType() != PlayerTypes.HUMAN) {
                    continue;
                }

                if (resourceFlag != BREATHE_DEEP) {
                    player.setMinerals(player.getMinerals() + amount);
                }

                if (resourceFlag != WHATS_MINE_IS_MINE) {
                    player.setGas(player.getGas() + amount);
                }
            }
        }

        if ((visibility.getCheatFlags() & SOMETHING_FOR_NOTHING) != 0) {
            visibility.setCheatFlags(visibility.getCheatFlags() & ~SOMETHING_FOR_NOTHING);

            PlayerUpgrades upgrades = this.game.getPlayerTable().getPlayerUpgrades();

            if (upgrades == null) {
                throw Exceptions.newIllegalStateException("The upgrade cheat requires player upgrade levels.");
            }

            ScrUpgradeTypeView upgradeTypes = this.game.requireGameData().getUpgradeTypes();

            for (int playerIndex = 0; playerIndex < PlayerTable.PLAYER_SLOTS; playerIndex++) {
                Player player = this.game.getPlayerTable().getPlayer(playerIndex);

                if (player == null || player.getType() != PlayerTypes.HUMAN) {
                    continue;
                }

                for (int upgradeIdentifier = 0; upgradeIdentifier < ScrUpgradeIds.NONE; upgradeIdentifier++) {
                    if (upgradeIdentifier == ScrUpgradeIds.UNUSED_60 ||
                        upgrades.getLevel(playerIndex, upgradeIdentifier) >= upgradeTypes.getMaximumLevel(upgradeIdentifier)) {
                        continue;
                    }

                    upgrades.setLevel(playerIndex, upgradeIdentifier, upgrades.getLevel(playerIndex, upgradeIdentifier) + 1);
                    this.game.getUnitResearch().applyToPlayerUnits(playerIndex, instructionLimit);
                }
            }
        }

        // A changed request writes the cheat-on message when the stored flags are not below the previous ones.
        if (sender >= 0 && flags != previousFlags) {
            this.game.getInterfaceLink().getMessageLines().writeCheatChange(sender,
                                                                            Integer.compareUnsigned(visibility.getCheatFlags(), previousFlags) >= 0);
        }
    }
}
