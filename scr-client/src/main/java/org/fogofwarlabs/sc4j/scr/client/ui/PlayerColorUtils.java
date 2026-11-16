package org.fogofwarlabs.sc4j.scr.client.ui;

import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerColorRows;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceAssets;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves the color a player is shown in, the same in the chat, the unit wireframes, the observer panels, the minimap pings and the dialogs.
 */
public final class PlayerColorUtils {
    /**
     * Returns the color a player is shown in ({@link PlayerColorRows#getPlayerColor}): its custom color when the game keeps its custom colors,
     * otherwise the tileset palette entry of its color row's first byte; without color rows (a color past the {@code tunit.pcx} rows), its color's
     * minimap color.
     *
     * @param players the player table
     * @param assets  the tileset interface assets
     * @param player  the player slot
     * @return the packed {@code 0xRRGGBBAA} color
     */
    public static int getPlayerColor(@NotNull PlayerTable players, @NotNull InterfaceAssets assets, int player) {
        boolean slot = player >= 0 && player < PlayerTable.PLAYER_SLOTS;
        PlayerColorRows rows = slot ? players.getPlayerColorRows() : null;

        if (rows != null) {
            return rows.getPlayerColor(player, assets::getPaletteColor);
        }

        Player record = slot ? players.getPlayer(player) : null;

        return assets.getMinimapPlayerColor(record != null ? record.getColor() : player);
    }

    private PlayerColorUtils() {
    }
}
