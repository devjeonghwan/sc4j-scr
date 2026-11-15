package org.fogofwarlabs.sc4j.scr.game.save;

import org.jetbrains.annotations.NotNull;

/**
 * Names the four saved-game kinds by product, session type and file extension.
 */
public enum SaveGameKind {
    /**
     * A single-player StarCraft game ({@code application/x.sng}).
     */
    SINGLE_PLAYER_ORIGINAL(".sng"),
    /**
     * A multiplayer StarCraft game ({@code application/x.mlt}).
     */
    MULTIPLAYER_ORIGINAL(".mlt"),
    /**
     * A single-player Brood War game ({@code application/x.snx}).
     */
    SINGLE_PLAYER(".snx"),
    /**
     * A multiplayer Brood War game ({@code application/x.mlx}).
     */
    MULTIPLAYER(".mlx");

    /**
     * Selects the kind from the product and session type.
     *
     * @param expansion   whether the game is played as Brood War
     * @param multiplayer whether the game is a network game
     * @return the kind
     */
    public static @NotNull SaveGameKind of(boolean expansion, boolean multiplayer) {
        if (expansion) {
            return multiplayer ? MULTIPLAYER : SINGLE_PLAYER;
        }

        return multiplayer ? MULTIPLAYER_ORIGINAL : SINGLE_PLAYER_ORIGINAL;
    }


    private final @NotNull String extension;

    SaveGameKind(@NotNull String extension) {
        this.extension = extension;
    }

    /**
     * @return the file name extension with its dot
     */
    public @NotNull String getExtension() {
        return this.extension;
    }

    /**
     * @return the directory listing pattern of the kind's files
     */
    public @NotNull String getPattern() {
        return "*" + this.extension;
    }

    /**
     * @return {@code true} if the kind's games are network games, {@code false} otherwise
     */
    public boolean isMultiplayer() {
        return this == MULTIPLAYER || this == MULTIPLAYER_ORIGINAL;
    }
}
