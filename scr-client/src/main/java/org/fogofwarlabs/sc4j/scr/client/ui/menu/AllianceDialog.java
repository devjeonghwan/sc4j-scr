package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Retains pending alliance and shared-vision values for the listed players.
 * Ally and vision controls apply their row enablement rules; Allied Victory changes every nonzero alliance value.
 * The caller reads the packed alliance and vision commands when accepting and closes the dialog after either outcome.
 */
public final class AllianceDialog {
    /**
     * The first ally box.
     */
    public static final int FIRST_ALLY = 1;
    /**
     * The first vision box.
     */
    public static final int FIRST_VISION = 13;
    /**
     * Allied Victory.
     */
    public static final int ALLIED_VICTORY = 25;
    /**
     * Accept.
     */
    public static final int ACCEPT = -2;
    /**
     * Cancel.
     */
    public static final int CANCEL = -3;
    /**
     * The rows of the dialog file.
     */
    public static final int ROWS = 9;

    private static final int PLAYERS = PlayerTable.PLAYER_SLOTS;
    private static final int ALLIANCE_STATE_MASK = 3;
    private static final int ALLIANCE_STATE_BITS = 2;
    /**
     * The mask of the vision command's WORD.
     */
    private static final int VISION_MASK = 0xFFFF;

    /**
     * One listed player.
     */
    public static final class Row {
        private final @NotNull VisionDialog.Row player;

        private final boolean boxes;
        private final boolean allyEnabled;
        private final boolean visionEnabled;

        /**
         * Creates a {@link Row} instance.
         *
         * @param player        the player slot, label and swatch
         * @param boxes         whether the row has its boxes (another force)
         * @param allyEnabled   whether the ally box takes input
         * @param visionEnabled whether the vision box takes input
         */
        public Row(@NotNull VisionDialog.Row player, boolean boxes, boolean allyEnabled, boolean visionEnabled) {
            this.player = player;

            this.boxes         = boxes;
            this.allyEnabled   = allyEnabled;
            this.visionEnabled = visionEnabled;
        }

        /**
         * @return the player slot, label and swatch
         */
        public @NotNull VisionDialog.Row getPlayer() {
            return this.player;
        }

        /**
         * @return {@code true} if the row has alliance and vision controls, {@code false} otherwise
         */
        public boolean hasBoxes() {
            return this.boxes;
        }

        /**
         * @return {@code true} if the ally box takes input, {@code false} otherwise
         */
        public boolean isAllyEnabled() {
            return this.allyEnabled;
        }

        /**
         * @return {@code true} if the vision box takes input, {@code false} otherwise
         */
        public boolean isVisionEnabled() {
            return this.visionEnabled;
        }
    }

    private final @NotNull List<Row> rows;
    private final int @NotNull [] pendingAlliances;

    private int pendingVision;
    private int local;
    private boolean alliedVictoryEnabled;
    private boolean open;

    /**
     * Creates a closed {@link AllianceDialog} instance.
     */
    public AllianceDialog() {
        this.rows             = new ArrayList<>();
        this.pendingAlliances = new int[PLAYERS];

        this.pendingVision        = 0;
        this.local                = 0;
        this.alliedVictoryEnabled = false;
        this.open                 = false;
    }

    /**
     * @return {@code true} if the dialog is open, {@code false} otherwise
     */
    public boolean isOpen() {
        return this.open;
    }

    /**
     * @return the rows
     */
    public @NotNull List<Row> getRows() {
        return this.rows;
    }

    /**
     * @param row the row
     * @return {@code true} if the row's ally box is checked, {@code false} otherwise
     */
    public boolean isAllied(int row) {
        return this.pendingAlliances[this.rows.get(row).getPlayer().getPlayer()] != 0;
    }

    /**
     * @param row the row
     * @return {@code true} if the row's vision box is checked, {@code false} otherwise
     */
    public boolean isVisionShared(int row) {
        return (this.pendingVision >> this.rows.get(row).getPlayer().getPlayer() & 1) != 0;
    }

    /**
     * @return {@code true} if Allied Victory is checked, {@code false} otherwise
     */
    public boolean isAlliedVictory() {
        return this.pendingAlliances[this.local] != PlayerRelations.ALLIED;
    }

    /**
     * @return {@code true} if Allied Victory takes input, {@code false} otherwise
     */
    public boolean isAlliedVictoryEnabled() {
        return this.alliedVictoryEnabled;
    }

    /**
     * @return the pending two-bit alliance values packed in player-slot order
     */
    public int getPackedAlliances() {
        int packed = 0;

        for (int player = 0; player < PLAYERS; player++) {
            packed |= (this.pendingAlliances[player] & ALLIANCE_STATE_MASK) << player * ALLIANCE_STATE_BITS;
        }

        return packed;
    }

    /**
     * @return the vision command's WORD
     */
    public int getVision() {
        return this.pendingVision;
    }


    /**
     * Opens the dialog with the local player's alliances and vision.
     *
     * @param rows                 the rows in list order
     * @param alliances            the local player's alliance bytes of players {@code 0..11}
     * @param vision               the local player's shared vision mask
     * @param local                the local player's slot
     * @param alliedVictoryEnabled whether Allied Victory takes input
     */
    public void open(@NotNull List<Row> rows, int @NotNull [] alliances, int vision, int local, boolean alliedVictoryEnabled) {
        this.rows.clear();
        this.rows.addAll(rows.subList(0, Math.min(ROWS, rows.size())));

        System.arraycopy(alliances, 0, this.pendingAlliances, 0, PLAYERS);

        this.pendingVision        = vision & VISION_MASK;
        this.local                = local;
        this.alliedVictoryEnabled = alliedVictoryEnabled;
        this.open                 = true;
    }

    /**
     * Applies an enabled ally, shared-vision or Allied Victory control to the pending state.
     *
     * @param control the dialog control identifier
     */
    public void press(int control) {
        if (control >= FIRST_ALLY && control < FIRST_ALLY + this.rows.size()) {
            Row row = this.rows.get(control - FIRST_ALLY);

            if (!row.hasBoxes() || !row.isAllyEnabled()) {
                return;
            }

            int player = row.getPlayer().getPlayer();

            this.pendingAlliances[player] = this.pendingAlliances[player] == 0 ? this.pendingAlliances[this.local] : 0;
        } else if (control >= FIRST_VISION && control < FIRST_VISION + this.rows.size()) {
            Row row = this.rows.get(control - FIRST_VISION);

            if (row.hasBoxes() && row.isVisionEnabled()) {
                this.pendingVision ^= 1 << row.getPlayer().getPlayer();
            }
        } else if (control == ALLIED_VICTORY && this.alliedVictoryEnabled) {
            boolean checked = !this.isAlliedVictory();

            for (int player = 0; player < PLAYERS; player++) {
                if (this.pendingAlliances[player] != 0) {
                    this.pendingAlliances[player] = checked ? PlayerRelations.ALLIED_VICTORY : PlayerRelations.ALLIED;
                }
            }
        }
    }


    /**
     * Closes the dialog.
     */
    public void close() {
        this.open = false;
    }
}
