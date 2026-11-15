package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Raises the sounds the simulation plays at a unit.
 * <p>
 * Sounds play at the unit's sprite position, or at the map origin for a unit without a sprite. The consumer applies channel, volume and timing rules.
 * Callers choose random sounds through the synchronized random source; this system draws no random number.
 */
public final class UnitSounds {
    /**
     * The sound race index for a unit type without a race group flag.
     */
    public static final int RACE_NONE = 3;

    /**
     * The sound race index of a Zerg unit type.
     */
    private static final int RACE_ZERG = 0;
    /**
     * The sound race index of a Terran unit type.
     */
    private static final int RACE_TERRAN = 1;
    /**
     * The sound race index of a Protoss unit type.
     */
    private static final int RACE_PROTOSS = 2;

    private final @NotNull Game game;

    /**
     * Creates a {@link UnitSounds} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitSounds(@NotNull Game game) {
        this.game = game;
    }

    /**
     * @param unit the unit
     * @return the sound race index of the first StarEdit group flag in Zerg, Terran, Protoss order, or {@link #RACE_NONE}
     * @throws NullPointerException if the world was built without game data
     */
    public int getRace(@NotNull Unit unit) {
        int flags = this.game.requireGameData().getUnitTypes().getGroupFlags(unit.getUnitId());

        if ((flags & UnitGroupFlags.ZERG) != 0) {
            return RACE_ZERG;
        }

        if ((flags & UnitGroupFlags.TERRAN) != 0) {
            return RACE_TERRAN;
        }

        return (flags & UnitGroupFlags.PROTOSS) != 0 ? RACE_PROTOSS : RACE_NONE;
    }


    /**
     * Plays a sound at a unit.
     *
     * @param soundId the sound table index
     * @param unit    the source unit
     */
    public void play(int soundId, @NotNull Unit unit) {
        Sprite sprite = unit.getSpriteOrNull();

        this.game.getInterfaceLink().getSoundEvents().add(soundId,
                                                          sprite == null ? 0 : sprite.getX(), sprite == null ? 0 : sprite.getY(),
                                                          unit.getUnitId());
    }
}
