package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.session.ShuffleUtils;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits the replay vision of the game's {@link LocalVision} with a pending mask and a reveal-all option.
 * Opening takes the pending values from the vision; OK applies them to it ({@link LocalVision#acceptDialog}); Cancel leaves the
 * vision as it is. Rows retain the supplied order; {@link ShuffleUtils#shuffle} orders them by the game seed.
 */
public final class VisionDialog {
    /**
     * One listed player.
     */
    public static final class Row {
        private final int player;
        private final @NotNull String name;
        private final int color;

        /**
         * Creates a {@link Row} instance.
         *
         * @param player the player slot
         * @param name   the player name
         */
        public Row(int player, @NotNull String name) {
            this(player, name, 0);
        }

        /**
         * Creates a {@link Row} instance with a color swatch.
         *
         * @param player the player slot
         * @param name   the label: in a replay {@code "[" + race tag + "] " + name}
         * @param color  the swatch color {@code 0xRRGGBBAA}: the player's minimap color, {@code 0} for none
         */
        public Row(int player, @NotNull String name, int color) {
            this.player = player;
            this.name   = name;
            this.color  = color;
        }

        /**
         * @return the player slot
         */
        public int getPlayer() {
            return this.player;
        }

        /**
         * @return the player name
         */
        public @NotNull String getName() {
            return this.name;
        }

        /**
         * @return the swatch color {@code 0xRRGGBBAA}, or {@code 0} for none
         */
        public int getColor() {
            return this.color;
        }
    }

    private final @NotNull List<Row> rows;
    private @Nullable LocalVision vision;
    private boolean open;
    private int pendingMask;
    private boolean pendingReveal;

    /**
     * Creates a {@link VisionDialog} instance that is closed and edits no vision.
     */
    public VisionDialog() {
        this.rows          = new ArrayList<>();
        this.vision        = null;
        this.open          = false;
        this.pendingMask   = 0;
        this.pendingReveal = false;
    }

    /**
     * @return {@code true} if the dialog is open, {@code false} otherwise
     */
    public boolean isOpen() {
        return this.open;
    }

    /**
     * @return the listed players
     */
    public @NotNull List<Row> getRows() {
        return this.rows;
    }

    /**
     * @param row the row index
     * @return {@code true} if the row's vision box is checked, {@code false} otherwise
     */
    public boolean isChecked(int row) {
        return (this.pendingMask >> this.rows.get(row).getPlayer() & 1) != 0;
    }

    /**
     * @return {@code true} if "Reveal Entire Map" is checked, {@code false} otherwise
     */
    public boolean isRevealChecked() {
        return this.pendingReveal;
    }


    /**
     * Opens the dialog with the replay vision mask and the reveal of a vision, which OK changes.
     *
     * @param players the listed players
     * @param vision  the vision the dialog edits
     */
    public void open(@NotNull List<Row> players, @NotNull LocalVision vision) {
        this.rows.clear();
        this.rows.addAll(players.subList(0, Math.min(PlayerTable.PLAYABLE_PLAYER_SLOTS, players.size())));

        this.vision        = vision;
        this.open          = true;
        this.pendingMask   = vision.getReplayMask();
        this.pendingReveal = vision.isRevealed();
    }

    /**
     * Toggles a row's vision box, which also unchecks "Reveal Entire Map".
     *
     * @param row the row index
     */
    public void toggle(int row) {
        this.pendingMask ^= 1 << this.rows.get(row).getPlayer();
        this.pendingReveal = false;
    }

    /**
     * Toggles "Reveal Entire Map".
     */
    public void toggleReveal() {
        this.pendingReveal = !this.pendingReveal;
    }

    /**
     * Applies the pending state to the edited vision and closes.
     */
    public void accept() {
        if (this.vision != null) {
            this.vision.acceptDialog(this.pendingMask, this.pendingReveal);
        }

        this.open = false;
    }

    /**
     * Closes without changes.
     */
    public void cancel() {
        this.open = false;
    }
}
