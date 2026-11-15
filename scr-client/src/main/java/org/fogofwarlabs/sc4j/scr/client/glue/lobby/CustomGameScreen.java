package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueContext;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueGameRequest;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueMessagePopup;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ComboBox;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.session.setup.GameTemplate;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lists custom-game maps, game types and player-slot choices, then builds the selected game request.
 */
public final class CustomGameScreen extends FileBrowserScreen {
    private static final @NotNull Logger LOGGER = Logger.of(CustomGameScreen.class);

    private static final int GAME_TYPE = 17;
    private static final int FIRST_TYPE_SLOT = 20;
    private static final int FIRST_RACE_SLOT = 28;
    private static final int SLOT_COUNT = PlayerTable.PLAYABLE_PLAYER_SLOTS;
    /**
     * The game types offered by the single-player custom-game selector.
     */
    private static final @NotNull Set<Integer> SINGLE_PLAYER_TYPES = Set.of(GameTemplate.TYPE_MELEE, GameTemplate.TYPE_FREE_FOR_ALL,
                                                                            GameTemplate.TYPE_USE_MAP_SETTINGS);
    private static final int PLAYER_SLOT = 0;

    /**
     * The slot type values of the slot type lists.
     */
    private static final int SLOT_CLOSED = 0;
    private static final int SLOT_COMPUTER = 1;

    private static final int RACE_RANDOM = ScrRaceIds.RANDOM;
    /**
     * The race names of the race lists, in the order of {@link #RACE_VALUES}.
     */
    private static final @NotNull String @NotNull [] RACE_KEYS = {"strZERG", "strTERRAN", "strPROTOSS", "strRANDOM"};
    private static final int @NotNull [] RACE_VALUES = {ScrRaceIds.ZERG, ScrRaceIds.TERRAN, ScrRaceIds.PROTOSS, RACE_RANDOM};

    private @NotNull List<GameTemplates.Template> templates;
    private @Nullable ComboBox gameType;
    private final @NotNull List<ComboBox> slotTypes;
    private final @NotNull List<ComboBox> slotRaces;
    private int startSlots;

    /**
     * Creates a {@link CustomGameScreen} instance.
     */
    public CustomGameScreen() {
        super(MAP_EXTENSIONS);

        this.templates  = List.of();
        this.gameType   = null;
        this.slotTypes  = new ArrayList<>();
        this.slotRaces  = new ArrayList<>();
        this.startSlots = 0;
    }

    /**
     * Called after the dialog is loaded: hide, disable or fill controls here.
     * <p>
     * Loads the game templates, fills the game-type and slot controls, then opens the map list.
     *
     * @param glueContext the shared services
     */
    @Override
    protected void onOpen(@NotNull GlueContext glueContext) {
        this.templates = GameTemplates.loadOrCreateDefaults(glueContext.getCanvas().getStorage(), LOGGER);

        ComboBox types = this.addComboBox(GAME_TYPE);
        // Restore the selected game type when it is offered, otherwise select the first type.
        int remembered = glueContext.getSettings().getCreateGameType();
        int[] typeIds = GameTemplates.getTypeIds(this.templates, template -> SINGLE_PLAYER_TYPES.contains(template.getTypeId()));
        int selected = 0;

        for (int index = 0; index < typeIds.length; index++) {
            types.addItem(GameTemplates.getTypeName(glueContext.getGluText(), typeIds[index]), typeIds[index]);

            if (typeIds[index] == remembered) {
                selected = index;
            }
        }

        types.select(selected);
        this.gameType = types;

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ComboBox type = this.addComboBox(FIRST_TYPE_SLOT + slot);

            if (slot == PLAYER_SLOT) {
                type.addItem(glueContext.getPlayerName(), SLOT_COMPUTER);
                type.select(0);
                type.setEnabled(false);
            } else {
                type.addItem(glueContext.getGluText().get("slotCLOSED"), SLOT_CLOSED);
                type.addItem(glueContext.getGluText().get("slotCOMPUTER"), SLOT_COMPUTER);
                type.select(SLOT_COMPUTER);
            }

            this.slotTypes.add(type);

            ComboBox race = this.addComboBox(FIRST_RACE_SLOT + slot);

            for (int index = 0; index < RACE_KEYS.length; index++) {
                race.addItem(glueContext.getNetworkText().get(RACE_KEYS[index]), RACE_VALUES[index]);
            }

            race.select(RACE_KEYS.length - 1);
            this.slotRaces.add(race);
        }

        super.onOpen(glueContext);
    }

    /**
     * @param glueContext the shared services
     * @return the root folder of the list
     */
    @Override
    protected @NotNull Path getRoot(@NotNull GlueContext glueContext) {
        return glueContext.getFolders().getMapDirectory();
    }

    /**
     * @param glueContext the shared services
     * @return the file to show selected when the screen opens (its folder is opened), or {@code null} for the root
     */
    @Override
    protected @Nullable Path getRememberedFile(@NotNull GlueContext glueContext) {
        return glueContext.getLastMap();
    }

    /**
     * Called when the selected file changes.
     * <p>
     * Remembers a selected map and shows the slot controls for its start slots.
     *
     * @param file the selected file, or {@code null} for a folder or no selection
     */
    @Override
    protected void onFileSelected(@Nullable Path file) {
        if (file != null) {
            this.getContext().setLastMap(file);
        }

        MapInformation map = this.getSelectedMap();

        this.startSlots = map == null ? 0 : map.getStartSlots();
        this.updateSlots();
    }

    /**
     * Called after the user changed the selection of a list or combo box, or the value of a slider.
     * <p>
     * Applies the file list's selection, stores a chosen game type and updates the shown slot controls.
     *
     * @param control the control id
     */
    @Override
    protected void onSelectionChanged(int control) {
        super.onSelectionChanged(control);

        if (control == GAME_TYPE) {
            if (this.gameType != null) {
                this.getContext().getSettings().setCreateGameType(this.gameType.getSelectedValue(GameTemplate.TYPE_MELEE));
            }

            this.updateSlots();
        }
    }

    /**
     * @return the first template of the selected game type, Melee without one
     */
    private @NotNull GameTemplate getTemplate() {
        int typeId = this.gameType == null ? GameTemplate.TYPE_MELEE : this.gameType.getSelectedValue(GameTemplate.TYPE_MELEE);
        List<GameTemplates.Template> subtypes = GameTemplates.getSubtypes(this.templates, typeId);

        return subtypes.isEmpty() ? GameTemplate.MELEE : subtypes.getFirst().getTemplate();
    }

    private void updateSlots() {
        int shown = this.getTemplate().isUseMapSettings() ? Math.min(1, this.startSlots) : this.startSlots;

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            this.slotTypes.get(slot).setVisible(slot < shown);
            this.slotRaces.get(slot).setVisible(slot < shown);
        }
    }

    /**
     * Called when Ok is chosen on a file.
     * <p>
     * Checks the map's start slots and the template, resolves the local race and starts the custom game.
     *
     * @param file the file
     */
    @Override
    protected void onFileChosen(@NotNull Path file) {
        GlueContext glueContext = this.getContext();

        if (this.startSlots == 0) {
            this.showPopup(new GlueMessagePopup(GlueMessagePopup.Kind.OK_SPLIT,
                                                this.getPaletteFolder(),
                                                glueContext.getGluText().get("maperrorTITLE"),
                                                glueContext.getGluText().get("maperrorINVALID"),
                                                confirmed -> {
                                                }));

            return;
        }

        // Reject a template whose required team count exceeds the map's start slots.
        if (!this.getTemplate().isSupportedBy(this.startSlots)) {
            this.showError(glueContext.getGluText().get(CreateGameScreen.BAD_GAME_TYPE_KEY));

            return;
        }

        boolean useMapSettings = this.getTemplate().isUseMapSettings();
        List<Integer> computers = new ArrayList<>();

        for (int slot = 1; !useMapSettings && slot < this.startSlots; slot++) {
            if (this.slotTypes.get(slot).getSelectedValue(SLOT_CLOSED) == SLOT_COMPUTER) {
                computers.add(this.slotRaces.get(slot).getSelectedValue(RACE_RANDOM));
            }
        }

        int[] computerRaces = computers.stream().mapToInt(Integer::intValue).toArray();
        // Resolve the local random race before opening the ready room or starting the game.
        int race = MeleeGameSetup.resolveLobbyRace(glueContext.getGlobalRandom(), this.slotRaces.get(PLAYER_SLOT).getSelectedValue(RACE_RANDOM));
        GlueGameRequest request = GlueGameRequest.createCustom(file,
                                                               glueContext.getPlayerName(),
                                                               glueContext.isExpansion(),
                                                               this.getTemplate(),
                                                               race,
                                                               computerRaces);

        // Use Map Settings opens the briefing ready room before starting the game.
        if (request.getKind() == GlueGameRequest.Kind.USE_MAP_SETTINGS) {
            this.leave(() -> glueContext.getNavigator().openReadyRoom(request));
        } else {
            this.leave(() -> glueContext.getNavigator().start(request));
        }
    }
}
