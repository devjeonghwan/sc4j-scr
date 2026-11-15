package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.jetbrains.annotations.NotNull;

/**
 * Retains the current tip number and the startup-display choice.
 * Opening a campaign mission uses its mapped tip when requested; other openings advance the stored tip.
 * Stepping wraps at the original-game or expansion tip range.
 */
public final class GameTips {
    /**
     * The last tip of the original game ({@code 0x4f}).
     */
    public static final int LAST_ORIGINAL_TIP = 0x4f;
    /**
     * The last tip of Brood War ({@code 0x66}).
     */
    public static final int LAST_EXPANSION_TIP = 0x66;
    /**
     * The first tip, where the numbers wrap to.
     */
    public static final int FIRST_TIP = 1;
    /**
     * A mission without a tip of its own in the table.
     */
    private static final int NO_TIP = 0xffff;
    /**
     * The mapped tip number of each campaign mission, or {@link #NO_TIP} when no mission-specific tip is selected.
     */
    private static final int @NotNull [] MISSION_TIPS = {
            NO_TIP, 1, 2, 4, 8, 11, 14, 17, 20, 23, 26, 29, 32, 35, 38, 41, 44, 47, 50, 53, 56, 58, 60, 62, 64, 66, 68, 70, 72, 74, 76, 78, 80,
            81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, NO_TIP, 95, 96, NO_TIP, 97, 98, 98, 98, 98, 98, 98, 99, 100, 101, 102,
            NO_TIP, NO_TIP, NO_TIP
    };

    private final boolean expansion;

    private int number;
    private boolean showAtStartup;

    /**
     * Creates a {@link GameTips} instance from the stored settings.
     *
     * @param expansion     whether the game is a Brood War game
     * @param number        the stored tip number ({@code tipnum})
     * @param showAtStartup the stored "Show Tips at Startup" choice ({@code tip})
     */
    public GameTips(boolean expansion, int number, boolean showAtStartup) {
        this.expansion = expansion;

        this.number        = number;
        this.showAtStartup = showAtStartup;
    }

    /**
     * @return the shown tip number ({@code tipnum})
     */
    public int getNumber() {
        return this.number;
    }

    /**
     * @return {@code true} if the tips open at a mission start ({@code tip}), {@code false} otherwise
     */
    public boolean isShowAtStartup() {
        return this.showAtStartup;
    }

    /**
     * Sets the "Show Tips at Startup" choice (the check box's).
     *
     * @param showAtStartup whether the tips open at a mission start
     */
    public void setShowAtStartup(boolean showAtStartup) {
        this.showAtStartup = showAtStartup;
    }


    /**
     * Selects a campaign mission tip or advances the stored tip.
     * An out-of-range selected tip becomes the first tip before applying the step.
     *
     * @param missionStart    whether opening at a mission start
     * @param campaignMission the campaign mission index, or zero outside campaigns
     * @return the tip number to show
     */
    public int open(boolean missionStart, int campaignMission) {
        int step = 1;

        if (missionStart && campaignMission != 0) {
            this.number = campaignMission < MISSION_TIPS.length ? MISSION_TIPS[campaignMission] : NO_TIP;
            step        = 0;
        }

        int last = this.expansion ? LAST_EXPANSION_TIP : LAST_ORIGINAL_TIP;

        if (this.number - FIRST_TIP < 0 || this.number - FIRST_TIP > last - FIRST_TIP) {
            this.number = FIRST_TIP;
        }

        return this.step(step);
    }

    /**
     * Moves to another tip, wrapping at the selected game range.
     *
     * @param delta one for Next Tip, negative one for Previous Tip, or zero to retain the tip
     * @return the tip number to show
     */
    public int step(int delta) {
        int last = this.expansion ? LAST_EXPANSION_TIP : LAST_ORIGINAL_TIP;
        int next = this.number + delta;

        if (next > last) {
            next = FIRST_TIP;
        } else if (next < FIRST_TIP) {
            next = last;
        }

        this.number = next;

        return next;
    }
}
