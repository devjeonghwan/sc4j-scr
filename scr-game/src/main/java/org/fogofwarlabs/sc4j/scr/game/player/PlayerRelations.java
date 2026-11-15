package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Retains player alliance bytes and shared-vision masks, initialized from player types, forces and teams.
 */
public final class PlayerRelations {
    /**
     * Alliance BYTE: the target is an enemy.
     */
    public static final int ENEMY = 0;
    /**
     * Alliance BYTE: the target is an ally.
     */
    public static final int ALLIED = 1;
    /**
     * Alliance BYTE: the target is an ally sharing victory.
     */
    public static final int ALLIED_VICTORY = 2;

    /**
     * Context offset of the alliance bytes, {@link PlayerTable#PLAYER_SLOTS} source rows of as many targets.
     */
    public static final int ALLIANCE_OFFSET = 0xE544;
    /**
     * Context offset of the shared-vision DWORDs, one per player.
     */
    public static final int SHARED_VISION_OFFSET = 0xFC;
    /**
     * The context offset after the alliance bytes.
     */
    private static final int ALLIANCE_END = ALLIANCE_OFFSET + PlayerTable.PLAYER_SLOTS * PlayerTable.PLAYER_SLOTS;
    /**
     * The context offset after the shared-vision DWORDs.
     */
    private static final int SHARED_VISION_END = SHARED_VISION_OFFSET + PlayerTable.PLAYER_SLOTS * Integer.BYTES;

    /**
     * Force flag: the force members are allied.
     */
    private static final int FORCE_ALLIED = 0x02;
    /**
     * Force flag: the force members share victory.
     */
    private static final int FORCE_ALLIED_VICTORY = 0x04;
    /**
     * Force flag: the force members share vision.
     */
    private static final int FORCE_SHARED_VISION = 0x08;
    /**
     * The allies option value that allows alliances.
     */
    private static final int ALLIES_ALLOWED = 1;
    /**
     * The mask of an unsigned byte: a player-type, force or force-flag byte, or one byte of a shared-vision DWORD.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The multiplier that folds each alliance byte and shared-vision DWORD into the diagnostic hash.
     */
    private static final int HASH_MULTIPLIER = 31;

    /**
     * Tests whether the game options allow alliances: the allies option is {@value #ALLIES_ALLOWED} and the tournament option is clear. SC4J has no
     * battle.net matchmaking session, so no matchmaking condition refuses alliances.
     *
     * @param alliesOption     the allies option byte
     * @param tournamentOption the tournament option byte
     * @return {@code true} if the options allow alliances, {@code false} otherwise
     */
    public static boolean areAlliancesAllowed(int alliesOption, int tournamentOption) {
        return alliesOption == ALLIES_ALLOWED && tournamentOption == 0;
    }

    /**
     * Checks whether a game-context offset lies in the relation tables.
     *
     * @param contextOffset a game-context offset
     * @return {@code true} if the offset lies in the alliance bytes or the shared-vision DWORDs, {@code false} otherwise
     */
    public static boolean hasContextOffset(int contextOffset) {
        return contextOffset >= ALLIANCE_OFFSET && contextOffset < ALLIANCE_END ||
               contextOffset >= SHARED_VISION_OFFSET && contextOffset < SHARED_VISION_END;
    }

    private final byte @NotNull [] @NotNull [] alliances;
    private final int @NotNull [] sharedVision;

    /**
     * Creates a {@link PlayerRelations} instance for established non-force, non-team initialization.
     *
     * @param playerTypes           the original player-type BYTEs of every slot
     * @param alliedComputerPlayers the established result of the allied computer players test
     * @param forceSettings         the established result of the map-settings test
     * @param teamSettings          the established result of the Top vs Bottom test
     * @throws UnsupportedOperationException if force or team overrides are required
     */
    public PlayerRelations(int @NotNull [] playerTypes, boolean alliedComputerPlayers, boolean forceSettings, boolean teamSettings) {
        this(playerTypes, new int[PlayerTable.PLAYER_SLOTS], alliedComputerPlayers, null, false);

        if (forceSettings || teamSettings) {
            throw Exceptions.newUnsupportedOperationException("Native force and team relations require their original override inputs.");
        }
    }

    /**
     * Creates a {@link PlayerRelations} instance including the map-settings force and Top vs Bottom team branches.
     * <p>
     * With map settings each force applies its flags: among its human players the alliance becomes {@link #ALLIED_VICTORY} with the allied and
     * allied-victory flags, {@link #ALLIED} with only the allied flag, otherwise {@link #ENEMY} (the diagonal is kept only in that last case); then
     * the shared-vision bits between different human force members are set or cleared according to the shared-vision flag. In Top vs Bottom every
     * pair of non-neutral players with nonzero types gets {@link #ALLIED_VICTORY} and shared vision when their teams are equal, including the
     * diagonal, and {@link #ENEMY} otherwise.
     *
     * @param playerTypes           the original player-type BYTEs of every slot
     * @param playerForces          the player force (team) BYTEs of the player records
     * @param alliedComputerPlayers the established result of the allied computer players test
     * @param forceFlags            the FORC flag BYTEs of a map-settings game, or {@code null} without map settings
     * @param teamSettings          the established result of the Top vs Bottom test
     * @throws IllegalArgumentException if an array has the wrong length
     */
    public PlayerRelations(int @NotNull [] playerTypes, int @NotNull [] playerForces,
                           boolean alliedComputerPlayers,
                           int @Nullable [] forceFlags,
                           boolean teamSettings) {
        if (playerTypes.length != PlayerTable.PLAYER_SLOTS || playerForces.length != PlayerTable.PLAYER_SLOTS) {
            throw Exceptions.newIllegalArgumentException("Native player relations require twelve player types and forces.");
        }

        if (forceFlags != null && forceFlags.length != PlayerTable.FORCE_COUNT) {
            throw Exceptions.newIllegalArgumentException("Native force relations require four force flag bytes.");
        }

        this.alliances    = new byte[PlayerTable.PLAYER_SLOTS][PlayerTable.PLAYER_SLOTS];
        this.sharedVision = new int[PlayerTable.PLAYER_SLOTS];

        for (int playerIndex = 0; playerIndex < PlayerTable.PLAYABLE_PLAYER_SLOTS; playerIndex++) {
            this.alliances[playerIndex][playerIndex] = ALLIED;
        }

        for (int playerIndex = PlayerTable.PLAYER_SLOTS - 1; playerIndex >= 0; playerIndex--) {
            this.alliances[PlayerTable.NEUTRAL_PLAYER_ID][playerIndex] = ALLIED;
            this.alliances[playerIndex][PlayerTable.NEUTRAL_PLAYER_ID] = ALLIED;

            if (alliedComputerPlayers && (playerTypes[playerIndex] & BYTE_MASK) == PlayerTypes.COMPUTER) {
                for (int otherPlayer = PlayerTable.PLAYABLE_PLAYER_SLOTS - 1; otherPlayer >= 0; otherPlayer--) {
                    if (otherPlayer != playerIndex && (playerTypes[otherPlayer] & BYTE_MASK) == PlayerTypes.COMPUTER) {
                        this.alliances[playerIndex][otherPlayer] = ALLIED_VICTORY;
                        this.alliances[otherPlayer][playerIndex] = ALLIED_VICTORY;
                    }
                }
            }
        }

        for (int playerIndex = 0; playerIndex < PlayerTable.PLAYER_SLOTS; playerIndex++) {
            this.sharedVision[playerIndex] = 1 << playerIndex;

            int playerType = playerTypes[playerIndex] & BYTE_MASK;

            if (playerType == PlayerTypes.RESCUE_PASSIVE || playerType == PlayerTypes.NEUTRAL) {
                for (int otherPlayer = 0; otherPlayer < PlayerTable.PLAYER_SLOTS; otherPlayer++) {
                    this.alliances[playerIndex][otherPlayer] = ALLIED;
                    this.alliances[otherPlayer][playerIndex] = ALLIED;
                }
            }
        }

        if (forceFlags != null) {
            this.applyForces(playerTypes, playerForces, forceFlags);
        }

        if (teamSettings) {
            this.applyTeams(playerTypes, playerForces);
        }
    }

    /**
     * @param sourcePlayer the source player index
     * @param targetPlayer the target player index
     * @return the original alliance BYTE, retaining the distinction between {@link #ALLIED} and {@link #ALLIED_VICTORY}
     */
    public int getAlliance(int sourcePlayer, int targetPlayer) {
        return Byte.toUnsignedInt(this.alliances[sourcePlayer][targetPlayer]);
    }

    /**
     * Sets one alliance BYTE, as the computer script routines do, without further updates.
     *
     * @param sourcePlayer the player whose row changes
     * @param targetPlayer the target player
     * @param alliance     the alliance BYTE
     */
    public void setAlliance(int sourcePlayer, int targetPlayer, int alliance) {
        this.alliances[sourcePlayer][targetPlayer] = (byte) alliance;
    }

    /**
     * Sets one player's alliance row.
     *
     * @param sourcePlayer the player whose row changes
     * @param alliances    the alliance values of every slot
     * @throws IllegalArgumentException if the row does not have one value per slot
     */
    public void setAlliances(int sourcePlayer, int @NotNull [] alliances) {
        if (alliances.length != PlayerTable.PLAYER_SLOTS) {
            throw Exceptions.newIllegalArgumentException("An alliance row has twelve players.");
        }

        for (int targetPlayer = 0; targetPlayer < PlayerTable.PLAYER_SLOTS; targetPlayer++) {
            this.alliances[sourcePlayer][targetPlayer] = (byte) alliances[targetPlayer];
        }
    }

    /**
     * @param playerIndex the player index
     * @return the player's shared-vision DWORD
     */
    public int getSharedVision(int playerIndex) {
        return this.sharedVision[playerIndex];
    }

    /**
     * Sets one player's shared-vision DWORD.
     *
     * @param playerIndex the player index
     * @param mask        the new mask
     */
    public void setSharedVision(int playerIndex, int mask) {
        this.sharedVision[playerIndex] = mask;
    }

    /**
     * @return a copy of the shared-vision DWORDs of every slot
     */
    public int @NotNull [] getSharedVisionMasks() {
        return this.sharedVision.clone();
    }

    /**
     * @return a copy of the alliance BYTEs in source-player row order
     */
    public byte @NotNull [] getAllianceBytes() {
        byte[] bytes = new byte[PlayerTable.PLAYER_SLOTS * PlayerTable.PLAYER_SLOTS];

        for (int sourcePlayer = 0; sourcePlayer < PlayerTable.PLAYER_SLOTS; sourcePlayer++) {
            System.arraycopy(this.alliances[sourcePlayer], 0, bytes, sourcePlayer * PlayerTable.PLAYER_SLOTS, PlayerTable.PLAYER_SLOTS);
        }

        return bytes;
    }


    /**
     * Reads a byte of the alliance and vision tables by its game-context offset, as an EUD read of the game-data block does.
     *
     * @param contextOffset a game-context offset accepted by {@link #hasContextOffset(int)}
     * @return the unsigned byte
     */
    public int readContextByte(int contextOffset) {
        if (contextOffset >= ALLIANCE_OFFSET) {
            int index = contextOffset - ALLIANCE_OFFSET;

            return Byte.toUnsignedInt(this.alliances[index / PlayerTable.PLAYER_SLOTS][index % PlayerTable.PLAYER_SLOTS]);
        }

        int index = contextOffset - SHARED_VISION_OFFSET;

        return this.sharedVision[index / Integer.BYTES] >>> index % Integer.BYTES * Byte.SIZE & BYTE_MASK;
    }

    /**
     * Writes a byte of the alliance and vision tables by its game-context offset, as an EUD write of the game-data block does.
     *
     * @param contextOffset a game-context offset accepted by {@link #hasContextOffset(int)}
     * @param value         the byte value
     */
    public void writeContextByte(int contextOffset, int value) {
        if (contextOffset >= ALLIANCE_OFFSET) {
            int index = contextOffset - ALLIANCE_OFFSET;

            this.alliances[index / PlayerTable.PLAYER_SLOTS][index % PlayerTable.PLAYER_SLOTS] = (byte) value;

            return;
        }

        int index = contextOffset - SHARED_VISION_OFFSET;
        int shift = index % Integer.BYTES * Byte.SIZE;
        int player = index / Integer.BYTES;

        this.sharedVision[player] = LittleEndianUtils.replaceByte(this.sharedVision[player], shift, value);
    }

    /**
     * Folds the retained alliance and vision state into a diagnostic hash.
     *
     * @param hash the preceding diagnostic hash
     * @return the hash including the retained alliance and vision state
     */
    public int computeStateHash(int hash) {
        for (byte[] row : this.alliances) {
            for (byte alliance : row) {
                hash = HASH_MULTIPLIER * hash + Byte.toUnsignedInt(alliance);
            }
        }

        for (int mask : this.sharedVision) {
            hash = HASH_MULTIPLIER * hash + mask;
        }

        return hash;
    }

    private void applyForces(int @NotNull [] playerTypes, int @NotNull [] playerForces, int @NotNull [] forceFlags) {
        for (int force = PlayerTable.FIRST_FORCE; force <= PlayerTable.FORCE_COUNT; force++) {
            int flags = forceFlags[force - PlayerTable.FIRST_FORCE] & BYTE_MASK;
            int alliance = (flags & FORCE_ALLIED) == 0 ? ENEMY : (flags & FORCE_ALLIED_VICTORY) != 0 ? ALLIED_VICTORY : ALLIED;

            for (int playerIndex = 0; playerIndex < PlayerTable.PLAYABLE_PLAYER_SLOTS; playerIndex++) {
                if ((playerForces[playerIndex] & BYTE_MASK) != force || (playerTypes[playerIndex] & BYTE_MASK) != PlayerTypes.HUMAN) {
                    continue;
                }

                for (int otherPlayer = 0; otherPlayer < PlayerTable.PLAYABLE_PLAYER_SLOTS; otherPlayer++) {
                    if (alliance == ENEMY && otherPlayer == playerIndex) {
                        continue;
                    }

                    if ((playerForces[otherPlayer] & BYTE_MASK) == force && (playerTypes[otherPlayer] & BYTE_MASK) == PlayerTypes.HUMAN) {
                        this.alliances[playerIndex][otherPlayer] = (byte) alliance;
                    }
                }
            }

            for (int playerIndex = 0; playerIndex < PlayerTable.PLAYABLE_PLAYER_SLOTS; playerIndex++) {
                if ((playerForces[playerIndex] & BYTE_MASK) != force || (playerTypes[playerIndex] & BYTE_MASK) != PlayerTypes.HUMAN) {
                    continue;
                }

                for (int otherPlayer = 0; otherPlayer < PlayerTable.PLAYABLE_PLAYER_SLOTS; otherPlayer++) {
                    if (otherPlayer == playerIndex ||
                        (playerForces[otherPlayer] & BYTE_MASK) != force ||
                        (playerTypes[otherPlayer] & BYTE_MASK) != PlayerTypes.HUMAN) {
                        continue;
                    }

                    if ((flags & FORCE_SHARED_VISION) != 0) {
                        this.sharedVision[playerIndex] |= 1 << otherPlayer;
                    } else {
                        this.sharedVision[playerIndex] &= ~(1 << otherPlayer);
                    }
                }
            }
        }
    }

    private void applyTeams(int @NotNull [] playerTypes, int @NotNull [] playerForces) {
        for (int playerIndex = 0; playerIndex < PlayerTable.NEUTRAL_PLAYER_ID; playerIndex++) {
            for (int otherPlayer = 0; otherPlayer < PlayerTable.NEUTRAL_PLAYER_ID; otherPlayer++) {
                if ((playerTypes[playerIndex] & BYTE_MASK) == PlayerTypes.NONE || (playerTypes[otherPlayer] & BYTE_MASK) == PlayerTypes.NONE) {
                    continue;
                }

                boolean sameTeam = (playerForces[playerIndex] & BYTE_MASK) == (playerForces[otherPlayer] & BYTE_MASK);

                this.alliances[playerIndex][otherPlayer] = (byte) (sameTeam ? ALLIED_VICTORY : ENEMY);

                if (sameTeam) {
                    this.sharedVision[playerIndex] |= 1 << otherPlayer;
                }
            }
        }
    }
}
