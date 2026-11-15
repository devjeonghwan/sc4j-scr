package org.fogofwarlabs.sc4j.scr.client.glue.campaign;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.scr.client.glue.*;
import org.fogofwarlabs.sc4j.scr.client.glue.popup.GlueMissionPopup;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Shows campaign race locks and unlocked mission choices, then navigates to the chosen campaign entry.
 */
public final class CampaignScreen extends GlueScreen {
    private static final @NotNull Logger LOGGER = Logger.of(CampaignScreen.class);

    private static final @NotNull String DIALOG = "gluCmpgn";
    private static final @NotNull String EXPANSION_DIALOG = "gluExpCmpgn";
    private static final @NotNull String PALETTE_FOLDER = "glue/palcs";

    private static final int LOAD_SAVED = 5;
    private static final int PROTOSS = 6;
    private static final int TERRAN = 7;
    private static final int ZERG = 8;
    private static final int CANCEL = 9;
    private static final int PLAY_CUSTOM = 10;
    private static final int LOAD_REPLAY = 11;
    /**
     * Panels {@code 1} and {@code 3} slide in from the left and the right.
     */
    private static final int @NotNull [] SWISH = {1, 0, 3, 2};
    private static final int SWISH_DELAY_MILLISECONDS = 0;

    /**
     * The controls containing the left and right frame halves.
     */
    private static final int LEFT_FRAME = 12;
    private static final int RIGHT_FRAME = 13;
    private static final float FRAME_SHIFT_FACTOR = 0.5f;

    /**
     * The lock text controls of the first, second and third locked race.
     */
    private static final int LOCK_FIRST = -10;
    private static final int LOCK_SECOND = -11;
    private static final int LOCK_THIRD = -12;
    private static final int NOT_LOCKED = 0;

    private static final int LAST_TERRAN_MISSION = 11;
    private static final int LAST_ZERG_MISSION = 21;
    private static final int LAST_EXPANSION_PROTOSS_MISSION = 39;
    private static final int LAST_EXPANSION_TERRAN_MISSION = 48;

    /**
     * The network text keys of the locked race confirmations.
     */
    private static final @NotNull String EXPANSION_TERRAN_LOCKED_KEY = "strRACE_ERR_TERRAN_EXP";
    private static final @NotNull String EXPANSION_ZERG_LOCKED_KEY = "strRACE_ERR_ZERG_EXP";
    private static final @NotNull String PROTOSS_LOCKED_KEY = "strRACE_ERR_PROTOSS";
    private static final @NotNull String ZERG_LOCKED_KEY = "strRACE_ERR_ZERG";

    /**
     * Raises campaign progress to the entry's mission when it is later.
     *
     * @param character the character profile
     * @param expansion whether the game was a Brood War game
     * @param entry     the campaign cursor after the walk
     * @throws IOException if the ID file cannot be written
     */
    public static void advanceProgress(@NotNull CharacterFile character, boolean expansion, CampaignEpisodes.@NotNull Entry entry) throws IOException {
        CharacterFile.Race race = entry.getProgressRace();

        if (character.getCampaignProgress(expansion, race) < entry.getMission()) {
            character.setCampaignProgress(expansion, race, entry.getMission());
        }
    }

    /**
     * Logs a campaign progress save that failed, through the logger of the class that saved it.
     *
     * @param logger    the logger of the saving class
     * @param character the character whose ID file could not be written
     * @param exception the failure
     */
    public static void logSaveFailure(@NotNull Logger logger, @NotNull CharacterFile character, @NotNull IOException exception) {
        logger.warning("CHARACTER SAVE FAILED: " + character.getName() + ": " + exception.getMessage());
    }

    private final boolean expansion;
    private final int @NotNull [] locks;

    /**
     * Creates a {@link CampaignScreen} instance.
     *
     * @param expansion {@code true} for the Brood War campaigns ({@code gluExpCmpgn})
     */
    public CampaignScreen(boolean expansion) {
        super(expansion ? EXPANSION_DIALOG : DIALOG, PALETTE_FOLDER, getPaletteBackground(PALETTE_FOLDER));

        this.expansion = expansion;
        this.locks     = new int[CharacterFile.Race.values().length];
    }

    /**
     * Called after the dialog is loaded: hide, disable or fill controls here.
     * <p>
     * Initializes panel transitions and shows the campaign locks of the player's character.
     *
     * @param glueContext the shared services
     */
    @Override
    protected void onOpen(@NotNull GlueContext glueContext) {
        this.setSwish(SWISH, SWISH_DELAY_MILLISECONDS);
        this.computeLocks(glueContext.getCharacter());

        for (int lock : this.locks) {
            if (lock != NOT_LOCKED) {
                this.bindControl(lock).setVisible(true);
            }
        }
    }

    private void computeLocks(@NotNull CharacterFile character) {
        int zergProgress = character.getCampaignProgress(this.expansion, CharacterFile.Race.ZERG);
        int terranProgress = character.getCampaignProgress(this.expansion, CharacterFile.Race.TERRAN);
        int protossProgress = character.getCampaignProgress(this.expansion, CharacterFile.Race.PROTOSS);

        if (this.expansion) {
            if (terranProgress == 0 && protossProgress <= LAST_EXPANSION_PROTOSS_MISSION) {
                this.locks[CharacterFile.Race.TERRAN.ordinal()] = LOCK_FIRST;
            }

            if (zergProgress == 0) {
                this.locks[CharacterFile.Race.ZERG.ordinal()] = protossProgress <= LAST_EXPANSION_PROTOSS_MISSION
                                                                ? LOCK_SECOND
                                                                : terranProgress <= LAST_EXPANSION_TERRAN_MISSION ? LOCK_THIRD : NOT_LOCKED;
            }
        } else {
            if (zergProgress == 0 && terranProgress <= LAST_TERRAN_MISSION) {
                this.locks[CharacterFile.Race.ZERG.ordinal()] = LOCK_FIRST;
            }

            if (protossProgress == 0) {
                this.locks[CharacterFile.Race.PROTOSS.ordinal()] = terranProgress <= LAST_TERRAN_MISSION
                                                                   ? LOCK_SECOND
                                                                   : zergProgress <= LAST_ZERG_MISSION ? LOCK_THIRD : NOT_LOCKED;
            }
        }
    }

    /**
     * Draws the screen and its popup.
     * <p>
     * Positions the frame halves at the window edges first.
     *
     * @param canvas the glue canvas
     * @throws IOException           if an image cannot be read
     * @throws IllegalStateException if the screen is not open
     */
    @Override
    public void draw(@NotNull GlueCanvas canvas) throws IOException {
        int extraWidth = (int) canvas.getInterfaceWidth() - GlueCanvas.INTERFACE_WIDTH;

        this.requireDialogState().requireControl(LEFT_FRAME).setShift((int) (extraWidth * FRAME_SHIFT_FACTOR * -1.0f));
        this.requireDialogState().requireControl(RIGHT_FRAME).setShift((int) (extraWidth * FRAME_SHIFT_FACTOR));
        super.draw(canvas);
    }

    /**
     * Called when a control is activated by a click, its hotkey, Esc or Enter.
     * <p>
     * Selects a campaign race or handles the campaign screen's navigation.
     *
     * @param control the control id
     */
    @Override
    protected void onActivate(int control) {
        switch (control) {
            case PROTOSS -> this.chooseRace(CharacterFile.Race.PROTOSS);
            case TERRAN -> this.chooseRace(CharacterFile.Race.TERRAN);
            case ZERG -> this.chooseRace(CharacterFile.Race.ZERG);
            case CANCEL -> this.leave(() -> this.getContext().getNavigator().show(GlueNavigator.Destination.MAIN_MENU));
            case PLAY_CUSTOM -> this.leave(() -> this.getContext().getNavigator().show(GlueNavigator.Destination.CUSTOM_GAME));
            case LOAD_REPLAY -> this.leave(() -> this.getContext().getNavigator().show(GlueNavigator.Destination.REPLAY_LIST));
            case LOAD_SAVED -> this.leave(() -> this.getContext().getNavigator().show(GlueNavigator.Destination.SAVED_GAME_LIST));
            default -> {
            }
        }
    }

    private void chooseRace(CharacterFile.@NotNull Race race) {
        if (this.locks[race.ordinal()] == NOT_LOCKED) {
            this.startRace(race);

            return;
        }

        String key = this.expansion ? race == CharacterFile.Race.TERRAN ? EXPANSION_TERRAN_LOCKED_KEY : EXPANSION_ZERG_LOCKED_KEY
                                    : race == CharacterFile.Race.PROTOSS ? PROTOSS_LOCKED_KEY : ZERG_LOCKED_KEY;

        this.showPopup(new GlueMessagePopup(GlueMessagePopup.Kind.OK_CANCEL,
                                            this.getPaletteFolder(),
                                            this.getContext().getNetworkText().get(key),
                                            null,
                                            confirmed -> {
                                                if (confirmed) {
                                                    this.startRace(race);
                                                }
                                            }));
    }

    private void startRace(CharacterFile.@NotNull Race race) {
        int progress = this.getContext().getCharacter().getCampaignProgress(this.expansion, race);
        List<CampaignEpisodes.Entry> unlocked = CampaignEpisodes.getUnlocked(this.expansion, race, progress);

        if (unlocked.size() <= 1) {
            this.startEntry(race, CampaignEpisodes.createFirstEntry(this.expansion, race));

            return;
        }

        this.showPopup(new GlueMissionPopup(this.getPaletteFolder(), unlocked, chosen -> {
            if (chosen != null) {
                this.startEntry(race, chosen);
            }
        }));
    }

    /**
     * Raises the player's campaign progress of the chosen race to the entry's mission and plays the entry.
     *
     * @param race  the chosen race
     * @param entry the chosen entry of the chosen race's episode
     */
    private void startEntry(CharacterFile.@NotNull Race race, CampaignEpisodes.@NotNull Entry entry) {
        GlueContext glueContext = this.getContext();
        CharacterFile character = glueContext.getCharacter();
        int progress = character.getCampaignProgress(this.expansion, race);

        if (progress < entry.getMission()) {
            try {
                character.setCampaignProgress(this.expansion, race, entry.getMission());
            } catch (IOException exception) {
                logSaveFailure(LOGGER, character, exception);
            }
        }

        this.leave(() -> glueContext.getNavigator().playCampaign(entry));
    }
}
