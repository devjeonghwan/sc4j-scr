package org.fogofwarlabs.sc4j.scr.game.creep;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.game.system.GameSystem;
import org.fogofwarlabs.sc4j.engine.lifecycle.UnitRemainsHook;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Removes the creep of a dying Extractor before its geyser is restored ({@link CreepField#removeExtractorCreep}): the first SC:R hook of the
 * unit remains point. It keeps no unit; the geyser restore that follows does. The hook holds no state of its own; it finds the SC:R game when it
 * is attached.
 */
public final class ExtractorCreepHook implements UnitRemainsHook, GameSystem {
    private @Nullable Game game;

    /**
     * Creates an {@link ExtractorCreepHook} instance; it calls the SC:R game it finds when it is attached.
     */
    public ExtractorCreepHook() {
        this.game = null;
    }

    /**
     * Resolves what this system uses from the game. Called once, after every system exists.
     * <p>
     * Binds the SC:R game system of the game.
     *
     * @param game the game
     */
    @Override
    public void attach(org.fogofwarlabs.sc4j.engine.game.@NotNull Game game) {
        this.game = game.getModSystem(Game.SYSTEM_KEY);
    }

    /**
     * Called while a unit in the world dies, after it left its owner. A hook that keeps the unit turns it into what remains of it.
     * <p>
     * A dying Extractor releases its creep at its sprite position; the hook gives no answer.
     *
     * @param unit             the dying unit
     * @param instructionLimit the instruction limit of each animation program run the hook starts
     * @return {@code false}: the hook never keeps the unit
     * @throws IllegalArgumentException      if the unit is not an SC:R unit
     * @throws IllegalStateException         if the hook is not attached or the Extractor has no sprite
     * @throws UnsupportedOperationException if an Extractor dies without creep storage
     */
    @Override
    public boolean keepsUnit(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, int instructionLimit) {
        Unit scrUnit = Unit.of(unit);

        if (scrUnit.getUnitId() != ScrUnitIds.ZERG_EXTRACTOR) {
            return false;
        }

        Game attached = this.requireGame();
        CreepField creep = attached.getCreepField();

        if (creep == null) {
            throw Exceptions.newUnsupportedOperationException("Extractor death requires creep storage.");
        }

        Sprite sprite = scrUnit.requireSprite();

        creep.removeExtractorCreep(attached, ScrUnitIds.ZERG_EXTRACTOR, sprite.getX(), sprite.getY(), true);

        return false;
    }

    private @NotNull Game requireGame() {
        Game attached = this.game;

        if (attached == null) {
            throw Exceptions.newIllegalStateException("The SC:R Extractor creep hook is not attached.");
        }

        return attached;
    }
}
