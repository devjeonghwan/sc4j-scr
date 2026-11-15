package org.fogofwarlabs.sc4j.scr.game.extension;

import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Extends the game data of the SC:R mod: the installed data once it is loaded, and each game's own tables once the game is created. A mod
 * built from the SC:R mod's sources adds an implementation with a public no-argument constructor and names it in the resource
 * {@code META-INF/services/org.fogofwarlabs.sc4j.scr.game.extension.ScrContentExtension} of its jar; {@link ScrContentExtensionUtils} calls
 * the named implementations in the order of that file. The SC:R mod itself names none.
 */
public interface ScrContentExtension {
    /**
     * Extends the installed data right after it is loaded, before any game is made from it: unit types, graphics and interface art added
     * through {@link GameData#getAddedUnitTypes()} and {@link GameData#getAddedGraphics()} belong here, so every game, replay and screen of
     * the session sees them.
     *
     * @param installed the installed data
     * @throws IOException if a file the extension reads cannot be read
     */
    default void extendInstalledData(@NotNull GameData installed) throws IOException {
    }

    /**
     * Extends a game's own tables right after the game is created over its copy of the installed tables, before a Use Map Settings map
     * writes its unit, upgrade and technology settings, so such a map's own values still replace the values written here. Every game, live
     * or replay, passes here.
     *
     * @param game the game's own data
     */
    default void extendGameData(@NotNull GameData game) {
    }
}
