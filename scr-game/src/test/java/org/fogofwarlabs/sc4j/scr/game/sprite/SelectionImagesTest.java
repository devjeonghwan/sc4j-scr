package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.type.ScrSpriteTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ImageIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.SpriteIds;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.presentation.LocalSelection;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.ReplayRunScenario;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.GameStructureView;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks that the selection circles and health bars are image nodes of the sprites' own image lists, linked and unlinked at the selection, flash,
 * team mark, sprite type, image replacement, alliance and save events, and that they stay local state, which the internal
 * {@link Game#computeStateHash} shows because it is the one value that covers the whole represented simulation state.
 */
@RequiresInstallation
final class SelectionImagesTest {
    /**
     * The x coordinate of the first place of the unit grid near the computer's base.
     */
    private static final int UNIT_X = 3560;
    /**
     * The y coordinate of the first place of the unit grid near the computer's base.
     */
    private static final int UNIT_Y = 2160;
    /**
     * The pixel distance between neighboring places of the unit grid.
     */
    private static final int UNIT_SPACING = 32;
    /**
     * The places in one row of the unit grid.
     */
    private static final int UNITS_PER_ROW = 4;
    /**
     * The grid place of the first created unit.
     */
    private static final int FIRST_PLACE = 0;
    /**
     * The grid place of the second created unit.
     */
    private static final int SECOND_PLACE = 1;
    /**
     * The first slot of the local selection array.
     */
    private static final int FIRST_SLOT = 0;
    /**
     * The team size passed with every team mark.
     */
    private static final int TEAM_SIZE = 4;
    /**
     * The flash timer a flash starts from.
     */
    private static final int FLASH_TIMER = 31;
    /**
     * The flash timer after the first update, whose bit {@code 3} shows the circle.
     */
    private static final int FLASH_SHOWN_TIMER = 30;
    /**
     * The first flash timer below {@link #FLASH_SHOWN_TIMER} whose bit {@code 3} is clear, which hides the circle.
     */
    private static final int FLASH_HIDDEN_TIMER = 23;
    /**
     * The flash timer of a sprite that stops flashing.
     */
    private static final int STOPPED_FLASH_TIMER = 0;
    /**
     * The visibility flags of a sprite that every player sees.
     */
    private static final int ALL_PLAYERS = 0xFF;
    /**
     * The visibility flags of a sprite that no player sees.
     */
    private static final int NO_PLAYERS = 0x00;
    /**
     * The solid circle sprite flag.
     */
    private static final int SELECTION_CIRCLE_FLAG = 0x01;
    /**
     * The selected sprite flag.
     */
    private static final int SELECTED_FLAG = 0x08;
    /**
     * The selected and the solid circle sprite flags.
     */
    private static final int SELECTION_FLAGS = 0x09;
    /**
     * The two team mark count sprite flags.
     */
    private static final int TEAM_MARK_FLAGS = 0x06;
    /**
     * The shadow draw function.
     */
    private static final int SHADOW_MODIFIER = 10;
    /**
     * The health bar draw function.
     */
    private static final int HEALTH_BAR_MODIFIER = 11;
    /**
     * The selection circle draw function.
     */
    private static final int SELECTION_CIRCLE_MODIFIER = 13;
    /**
     * The health bar image.
     */
    private static final int HEALTH_BAR_IMAGE = 560;
    /**
     * The circle color of the local player's own units, which a replay uses for every player but the neutral one.
     */
    private static final int OWN_COLOR = 0;
    /**
     * The circle color of allied units.
     */
    private static final int ALLIED_COLOR = 1;
    /**
     * The circle color of enemy units.
     */
    private static final int ENEMY_COLOR = 2;
    /**
     * The neutral player.
     */
    private static final int NEUTRAL_PLAYER = 11;
    /**
     * The capacity of the health bar node pool.
     */
    private static final int HEALTH_BAR_NODE_CAPACITY = 12;
    /**
     * The capacity of the circle node pool.
     */
    private static final int CIRCLE_NODE_CAPACITY = 80;
    /**
     * The sprite type that the Zergling's sprite changes to.
     */
    private static final int WHITE_CIRCLE_SPRITE_TYPE = 300;
    /**
     * The nodes a pool holds for the health bar or the solid circle of one sprite.
     */
    private static final int ONE_NODE = 1;
    /**
     * The circle nodes of a sprite with a solid and a dashed circle.
     */
    private static final int SOLID_AND_DASHED_NODES = 2;
    /**
     * The team mark count after two teammates mark a sprite.
     */
    private static final int TWO_TEAM_MARKS = 2;
    /**
     * The team mark count after one teammate marks a sprite.
     */
    private static final int ONE_TEAM_MARK = 1;
    /**
     * The health bar and the solid circle that a selected unit links.
     */
    private static final int SELECTION_NODE_COUNT = 2;
    /**
     * The distance of the solid circle from the end of an image list whose tail is a shadow.
     */
    private static final int CIRCLE_ABOVE_SHADOW_DISTANCE = 2;
    /**
     * The image type of the image pushed at the head, which the selection links do not depend on.
     */
    private static final int IMAGE_TYPE = 0;
    /**
     * An image whose Init script creates no other image.
     */
    private static final int SINGLE_IMAGE = 241;
    /**
     * The construction mode that prefers the construction image of the unit type.
     */
    private static final int CONSTRUCTION_IMAGE_MODE = 1;
    /**
     * The bits of one player in a recorded alliance DWORD.
     */
    private static final int ALLIANCE_BITS = 2;
    /**
     * The player of the team replay whose synchronized selection the local selection follows.
     */
    private static final int FOLLOWED_PLAYER = 0;
    /**
     * The followed player of a run whose local selection follows no player.
     */
    private static final int NO_PLAYER = -1;
    /**
     * The game context offset of the local relation table.
     */
    private static final int LOCAL_RELATIONS = 0x2C7A;
    /**
     * The host ticks per frame of replay playback.
     */
    private static final int REPLAY_HOST_TICKS_PER_FRAME = 0;

    private static @Nullable GameData gameData;

    /**
     * A local selection array that follows one player's synchronized selection, as the replay viewer's selection may.
     */
    private static final class FollowingLocalSelection implements LocalSelection {
        private final @NotNull Game game;
        private final int @NotNull [] poolIndexes;

        private int changeCount;

        /**
         * Creates a {@link FollowingLocalSelection} instance with an empty selection.
         *
         * @param game the game whose selection images follow the selection
         */
        private FollowingLocalSelection(@NotNull Game game) {
            this.game        = game;
            this.poolIndexes = new int[CAPACITY];

            this.changeCount = 0;
        }

        /**
         * @param position the slot from {@code 0} through {@code 11}
         * @return the pool index of the unit in the slot, or {@code 0} for an empty slot
         */
        @Override
        public int getSelectedPoolIndex(int position) {
            return this.poolIndexes[position];
        }

        /**
         * Sets one local selection slot directly without a command or sound; the selection images do not change.
         *
         * @param position  the slot from {@code 0} through {@code 11}
         * @param poolIndex the pool index of the unit to store, or {@code 0} for an empty slot
         */
        @Override
        public void setSelectedPoolIndexRaw(int position, int poolIndex) {
            this.poolIndexes[position] = poolIndex;
        }

        /**
         * @return the number of selection changes
         */
        private int getChangeCount() {
            return this.changeCount;
        }

        /**
         * @return the selected units in slot order, {@code null} for an empty slot
         */
        private @Nullable Unit @NotNull [] getUnits() {
            UnitPool pool = this.game.getUnitSystem().getUnitPool();
            Unit[] units = new Unit[CAPACITY];

            Assertions.assertNotNull(pool);

            for (int slot = 0; slot < CAPACITY; slot++) {
                units[slot] = this.poolIndexes[slot] == EMPTY_SLOT ? null : pool.getUnit(this.poolIndexes[slot]);
            }

            return units;
        }

        /**
         * Adds the second offspring of a hatched egg to the first free local selection slot when the egg was selected; this selection, which follows
         * a player's selection, ignores it.
         *
         * @param egg    the unit that hatched (the first offspring keeps the egg's unit)
         * @param second the second offspring
         */
        @Override
        public void addHatchedUnit(@NotNull Unit egg, @NotNull Unit second) {
        }

        /**
         * Removes a retired or hidden unit from the local selection array, as the lifecycle deselection clears it from every selection
         * ({@link UnitSelection#removeUnit}), and a selected unit the local player stops seeing or detecting; nothing is sent. The slots go through
         * {@link LocalSelection#removePoolIndex} and the selection images follow the change.
         *
         * @param unit the unit
         */
        @Override
        public void removeUnit(@NotNull Unit unit) {
            int[] slots = this.poolIndexes.clone();
            UnitPool pool = this.game.getUnitSystem().getUnitPool();
            List<Unit> kept = new ArrayList<>();

            Assertions.assertNotNull(pool);

            LocalSelection.removePoolIndex(slots, unit.getIndex());

            for (int slot = 0; slot < CAPACITY && slots[slot] != EMPTY_SLOT; slot++) {
                kept.add(pool.getUnit(slots[slot]));
            }

            this.replace(kept);
        }

        /**
         * Selects the units of a player's synchronized selection that show a sprite.
         *
         * @param player the player
         */
        private void follow(int player) {
            UnitSelection selection = this.game.getSelection();
            List<Unit> units = new ArrayList<>();

            Assertions.assertNotNull(selection);

            for (int position = 0; position < UnitSelection.MAXIMUM_SELECTED; position++) {
                Unit unit = selection.getSelectedUnit(player, position);

                if (unit == null) {
                    break;
                }

                Sprite sprite = unit.getSpriteOrNull();

                if (sprite != null && (sprite.getFlags() & Sprite.FLAG_HIDDEN) == 0 && !sprite.getImages().isEmpty()) {
                    units.add(unit);
                }
            }

            this.replace(units);
        }

        /**
         * Replaces the selection and changes the selection images when it differs.
         *
         * @param selected the new selection in slot order
         */
        private void replace(@NotNull List<Unit> selected) {
            Unit[] previous = this.getUnits();

            Arrays.fill(this.poolIndexes, EMPTY_SLOT);

            for (int slot = 0; slot < selected.size(); slot++) {
                this.poolIndexes[slot] = selected.get(slot).getIndex();
            }

            if (Arrays.equals(previous, this.getUnits())) {
                return;
            }

            this.changeCount++;
            this.game.getSelectionImages().changeLocalSelection(previous, selected.toArray(new Unit[0]));
        }
    }

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }

        gameData = null;
    }

    private static @NotNull GameData requireGameData() {
        Assertions.assertNotNull(gameData);

        return gameData;
    }

    private static @NotNull Sprite requireSprite(@NotNull Unit unit) {
        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        return sprite;
    }

    private static int getImageCount(@NotNull Game game) {
        SpriteImagePool pool = game.getSpriteSystem().getImagePool();

        Assertions.assertNotNull(pool);

        return pool.getAllocatedCount();
    }

    /**
     * Plays the team replay and computes the state hash before every frame, with the local selection following a player's selection or staying empty.
     *
     * @param followedPlayer the player the local selection follows, or {@link #NO_PLAYER}
     * @return the state hashes in frame order
     * @throws IOException    if the replay cannot be read
     * @throws AssertionError if the game data is not loaded
     */
    private static @NotNull List<Long> computeStateHashes(int followedPlayer) throws IOException {
        ReplayFile replay = ReplayFile.parse(GameTestResources.readAllBytes(GameTestResources.REPLAY_UMS_TEAM_FIGHTING_SPIRIT));
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, requireGameData());

        runtime.start(0, 0);

        Game game = runtime.getGame();
        FollowingLocalSelection selection = new FollowingLocalSelection(game);
        List<Long> hashes = new ArrayList<>();

        if (followedPlayer != NO_PLAYER) {
            game.getInterfaceLink().setLocalSelection(selection);
        }

        ReplayRunScenario run = new ReplayRunScenario(runtime, REPLAY_HOST_TICKS_PER_FRAME, frame -> {
            if (followedPlayer != NO_PLAYER) {
                selection.follow(followedPlayer);
            }

            hashes.add(game.computeStateHash());
        });

        run.runTo(Integer.MAX_VALUE);

        Assertions.assertTrue(followedPlayer == NO_PLAYER || selection.getChangeCount() > 0, "The local selection follows the player.");

        return hashes;
    }

    /**
     * Finds the first image of a sprite whose type is a circle of a kind in the sprite type's size.
     *
     * @param sprite     the sprite
     * @param firstImage the first image of the kind
     * @return the position from the head, or {@code -1}
     * @throws AssertionError if the game data is not loaded
     */
    private static int findCircle(@NotNull Sprite sprite, int firstImage) {
        int imageType = firstImage + requireGameData().getSpriteTypes().getSelectionCircle(sprite.getSpriteTypeId());

        for (int position = 0; position < sprite.getImages().size(); position++) {
            if (sprite.getImages().get(position).getImageTypeId() == imageType) {
                return position;
            }
        }

        return -1;
    }

    private @Nullable ReplayRuntime runtime;

    @BeforeEach
    void startGame() throws IOException {
        this.runtime = RunningGameScenario.start(requireGameData());
    }


    @Test
    void select_unit_should_link_the_health_bar_at_the_head_and_the_circle_below_a_plain_tail() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Game game = this.requireGame();
            Unit larva = this.createUnit(ScrUnitIds.ZERG_LARVA, FIRST_PLACE);
            Sprite sprite = requireSprite(larva);
            List<SpriteImage> images = new ArrayList<>(sprite.getImages());
            int imageCount = getImageCount(game);

            Assertions.assertNotEquals(SHADOW_MODIFIER, images.getLast().getModifier(), "The Larva's tail draws no shadow.");

            game.getInterfaceLink().getLocalSelection().setSelectedPoolIndexRaw(FIRST_SLOT, larva.getIndex());
            game.getSelectionImages().selectUnit(larva, FIRST_SLOT);

            SpriteImage healthBar = sprite.getImages().getFirst();
            SpriteImage circle = sprite.getImages().getLast();
            ScrSpriteTypeView spriteTypes = requireGameData().getSpriteTypes();

            Assertions.assertEquals(images, sprite.getImages().subList(1, sprite.getImages().size() - 1), "The images keep their order.");
            Assertions.assertEquals(HEALTH_BAR_IMAGE, healthBar.getImageTypeId());
            Assertions.assertEquals(HEALTH_BAR_MODIFIER, healthBar.getModifier());
            Assertions.assertEquals(larva.getIndex(), healthBar.getModifierData1(), "The bar keeps the local selection's unit.");
            Assertions.assertEquals(sprite.getImages().size() - 1, findCircle(sprite, ImageIds.SELECTION_CIRCLE_22PIXELS));
            Assertions.assertEquals(SELECTION_CIRCLE_MODIFIER, circle.getModifier());
            Assertions.assertEquals((byte) spriteTypes.getSelectionCircleOffset(sprite.getSpriteTypeId()), circle.getOffsetY());
            Assertions.assertEquals(OWN_COLOR, circle.getModifierData1(), "A replay colors the players' units as own.");
            Assertions.assertEquals(SELECTION_FLAGS, sprite.getFlags() & SELECTION_FLAGS);
            Assertions.assertEquals(imageCount, getImageCount(game), "The image pool stays untouched.");
            Assertions.assertEquals(ONE_NODE, game.getSelectionImages().getHealthBarPool().getAllocatedCount());
            Assertions.assertEquals(ONE_NODE, game.getSelectionImages().getCirclePool().getAllocatedCount());

            game.getSelectionImages().deselectUnit(larva);

            Assertions.assertEquals(images, sprite.getImages());
            Assertions.assertEquals(0, sprite.getFlags() & SELECTION_FLAGS);
            Assertions.assertEquals(0, game.getSelectionImages().getHealthBarPool().getAllocatedCount());
            Assertions.assertEquals(0, game.getSelectionImages().getCirclePool().getAllocatedCount());
        });
    }

    @Test
    void select_unit_should_link_the_circle_just_above_a_shadow_tail() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Game game = this.requireGame();
            Unit wraith = this.createUnit(ScrUnitIds.TERRAN_WRAITH, FIRST_PLACE);
            Sprite sprite = requireSprite(wraith);
            SpriteImage shadow = sprite.getImages().getLast();

            Assertions.assertEquals(SHADOW_MODIFIER, shadow.getModifier(), "The Wraith's tail is its shadow.");

            game.getSelectionImages().selectUnit(wraith, FIRST_SLOT);

            Assertions.assertSame(shadow, sprite.getImages().getLast(), "The shadow stays the tail.");
            Assertions.assertEquals(sprite.getImages().size() - CIRCLE_ABOVE_SHADOW_DISTANCE, findCircle(sprite, ImageIds.SELECTION_CIRCLE_22PIXELS),
                                    "The circle lies just above the shadow.");
        });
    }

    @Test
    void images_created_after_the_selection_link_on_their_side_of_the_nodes() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Game game = this.requireGame();
            Unit marine = this.createUnit(ScrUnitIds.TERRAN_MARINE, FIRST_PLACE);
            Sprite sprite = requireSprite(marine);
            SpriteImage primary = sprite.getMainImage();

            Assertions.assertNotNull(primary);

            game.getSelectionImages().selectUnit(marine, FIRST_SLOT);

            List<SpriteImage> selected = new ArrayList<>(sprite.getImages());
            SpriteImage above = game.getIscriptExecutor().createImage(sprite,
                                                                      primary,
                                                                      SINGLE_IMAGE,
                                                                      0, 0,
                                                                      0,
                                                                      marine,
                                                                      RunningGameScenario.INSTRUCTION_LIMIT);
            SpriteImage head = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);

            sprite.insertHeadImage(head);

            List<SpriteImage> expected = new ArrayList<>(selected);

            expected.add(selected.indexOf(primary), above);
            expected.addFirst(head);

            Assertions.assertNotNull(above);
            Assertions.assertEquals(HEALTH_BAR_MODIFIER, selected.getFirst().getModifier());
            Assertions.assertEquals(expected, sprite.getImages(), "An image above the primary image lands below the bar, a pushed head above it.");
        });
    }

    @Test
    void select_unit_should_keep_the_flags_of_a_node_whose_pool_is_empty() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Game game = this.requireGame();
            List<Unit> marines = new ArrayList<>();

            for (int index = 0; index <= SelectionImages.HEALTH_BAR_CAPACITY; index++) {
                marines.add(this.createUnit(ScrUnitIds.TERRAN_MARINE, index));
            }

            for (Unit marine : marines) {
                game.getSelectionImages().selectUnit(marine, FIRST_SLOT);
            }

            Sprite unlinkedBar = requireSprite(marines.getLast());

            Assertions.assertEquals(HEALTH_BAR_NODE_CAPACITY, game.getSelectionImages().getHealthBarPool().getAllocatedCount());
            Assertions.assertNotEquals(0, unlinkedBar.getFlags() & SELECTED_FLAG, "The selection flag stays without a bar.");
            Assertions.assertNotEquals(HEALTH_BAR_MODIFIER, unlinkedBar.getImages().getFirst().getModifier());

            List<Unit> flashed = new ArrayList<>();

            for (int index = game.getSelectionImages().getCirclePool().getAllocatedCount(); index <= SelectionImages.CIRCLE_CAPACITY; index++) {
                flashed.add(this.createUnit(ScrUnitIds.TERRAN_MARINE, index));
            }

            for (Unit unit : flashed) {
                game.getSelectionImages().showSelectionCircle(requireSprite(unit), SelectionImages.COLOR_OWN);
            }

            Sprite unlinkedCircle = requireSprite(flashed.getLast());

            Assertions.assertEquals(CIRCLE_NODE_CAPACITY, game.getSelectionImages().getCirclePool().getAllocatedCount());
            Assertions.assertEquals(0, unlinkedCircle.getFlags() & SELECTION_CIRCLE_FLAG, "A circle without a node clears its flag.");
        });
    }

    @Test
    void team_marks_link_one_dashed_circle_until_the_last_mark_is_removed() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Game game = this.requireGame();
            Sprite sprite = requireSprite(this.createUnit(ScrUnitIds.TERRAN_MARINE, FIRST_PLACE));
            int imageCount = sprite.getImages().size();

            game.getSelectionImages().addTeamMark(sprite, SelectionImages.COLOR_ALLIED, TEAM_SIZE);
            game.getSelectionImages().addTeamMark(sprite, SelectionImages.COLOR_ALLIED, TEAM_SIZE);

            int dashed = findCircle(sprite, ImageIds.SELECTION_CIRCLE_DASHED_22PIXELS);

            Assertions.assertEquals(imageCount + 1, sprite.getImages().size(), "Only the first mark links a dashed circle.");
            Assertions.assertEquals(TWO_TEAM_MARKS, SelectionImages.getTeamMarkCount(sprite));
            Assertions.assertEquals(ALLIED_COLOR, sprite.getImages().get(dashed).getModifierData1());

            game.getSelectionImages().removeTeamMark(sprite);

            Assertions.assertEquals(dashed, findCircle(sprite, ImageIds.SELECTION_CIRCLE_DASHED_22PIXELS));

            game.getSelectionImages().removeTeamMark(sprite);

            Assertions.assertEquals(imageCount, sprite.getImages().size());
            Assertions.assertEquals(0, sprite.getFlags() & TEAM_MARK_FLAGS);
            Assertions.assertEquals(0, game.getSelectionImages().getCirclePool().getAllocatedCount());
        });
    }

    @Test
    void change_sprite_type_should_link_the_circles_again_for_the_new_sprite_type() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Game game = this.requireGame();
            Unit zergling = this.createUnit(ScrUnitIds.ZERG_ZERGLING, FIRST_PLACE);
            Sprite sprite = requireSprite(zergling);

            game.getSelectionImages().selectUnit(zergling, FIRST_SLOT);
            game.getSelectionImages().addTeamMark(sprite, SelectionImages.COLOR_ALLIED, TEAM_SIZE);
            game.getSelectionImages().changeSpriteType(sprite, SpriteIds.WHITE_CIRCLE_INVISIBLE);

            int solid = findCircle(sprite, ImageIds.SELECTION_CIRCLE_22PIXELS);
            int dashed = findCircle(sprite, ImageIds.SELECTION_CIRCLE_DASHED_22PIXELS);

            Assertions.assertEquals(WHITE_CIRCLE_SPRITE_TYPE, sprite.getSpriteTypeId());
            Assertions.assertEquals(HEALTH_BAR_MODIFIER, sprite.getImages().getFirst().getModifier());
            Assertions.assertTrue(solid > 0 && dashed == solid + 1, "The dashed circle is linked again after the solid one.");
            Assertions.assertEquals(ONE_TEAM_MARK, SelectionImages.getTeamMarkCount(sprite), "The team marks stay.");
            Assertions.assertEquals(ONE_NODE, game.getSelectionImages().getHealthBarPool().getAllocatedCount());
            Assertions.assertEquals(SOLID_AND_DASHED_NODES, game.getSelectionImages().getCirclePool().getAllocatedCount());
        });
    }

    @Test
    void initialize_construction_image_should_link_the_selection_images_again_around_the_new_primary_image() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Game game = this.requireGame();
            Unit barracks = this.createUnit(ScrUnitIds.TERRAN_BARRACKS, FIRST_PLACE);
            Sprite sprite = requireSprite(barracks);

            game.getSelectionImages().selectUnit(barracks, FIRST_SLOT);
            game.getIscriptExecutor().initializeConstructionImage(barracks, CONSTRUCTION_IMAGE_MODE, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(HEALTH_BAR_MODIFIER, sprite.getImages().getFirst().getModifier());
            Assertions.assertTrue(findCircle(sprite, ImageIds.SELECTION_CIRCLE_22PIXELS) > 0);
            Assertions.assertEquals(SELECTION_FLAGS, sprite.getFlags() & SELECTION_FLAGS);
            Assertions.assertEquals(ONE_NODE, game.getSelectionImages().getHealthBarPool().getAllocatedCount());
            Assertions.assertEquals(ONE_NODE, game.getSelectionImages().getCirclePool().getAllocatedCount());
        });
    }

    @Test
    void update_flash_should_show_and_hide_the_solid_circle_by_the_timer() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Game game = this.requireGame();
            Sprite sprite = requireSprite(this.createUnit(ScrUnitIds.TERRAN_MARINE, FIRST_PLACE));

            sprite.setVisibilityFlags(ALL_PLAYERS);
            sprite.setFlashTimer(FLASH_TIMER);
            game.getSelectionImages().updateFlash(sprite, PlayerTable.NEUTRAL_PLAYER_ID);

            int circle = findCircle(sprite, ImageIds.SELECTION_CIRCLE_22PIXELS);

            Assertions.assertEquals(FLASH_SHOWN_TIMER, sprite.getFlashTimer());
            Assertions.assertTrue(circle >= 0);
            Assertions.assertEquals(ALLIED_COLOR, sprite.getImages().get(circle).getModifierData1(),
                                    "A replay colors the neutral player's units as allied.");

            while (sprite.getFlashTimer() > FLASH_HIDDEN_TIMER) {
                game.getSelectionImages().updateFlash(sprite, PlayerTable.NEUTRAL_PLAYER_ID);
            }

            Assertions.assertEquals(0, sprite.getFlags() & SELECTION_CIRCLE_FLAG);
            Assertions.assertEquals(0, game.getSelectionImages().getCirclePool().getAllocatedCount());

            sprite.setVisibilityFlags(NO_PLAYERS);
            game.getSelectionImages().updateFlash(sprite, PlayerTable.NEUTRAL_PLAYER_ID);

            Assertions.assertEquals(STOPPED_FLASH_TIMER, sprite.getFlashTimer(), "A sprite the local player does not see stops flashing at once.");
        });
    }

    @Test
    void save_detachment_unlinks_every_node_and_restoration_selects_the_local_selection_again() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Game game = this.requireGame();
            Unit selected = this.createUnit(ScrUnitIds.TERRAN_MARINE, FIRST_PLACE);
            Sprite selectedSprite = requireSprite(selected);
            Sprite flashedSprite = requireSprite(this.createUnit(ScrUnitIds.TERRAN_MARINE, SECOND_PLACE));
            int selectedImages = selectedSprite.getImages().size();

            game.getInterfaceLink().getLocalSelection().setSelectedPoolIndexRaw(FIRST_SLOT, selected.getIndex());
            game.getSelectionImages().selectUnit(selected, FIRST_SLOT);
            game.getSelectionImages().showSelectionCircle(flashedSprite, SelectionImages.COLOR_OWN);
            game.getSelectionImages().addTeamMark(flashedSprite, SelectionImages.COLOR_OWN, TEAM_SIZE);
            game.getSelectionImages().detachBeforeSave();

            Assertions.assertEquals(selectedImages, selectedSprite.getImages().size());
            Assertions.assertEquals(0, selectedSprite.getFlags() & SELECTION_FLAGS);
            Assertions.assertEquals(0, flashedSprite.getFlags() & (SELECTION_CIRCLE_FLAG | TEAM_MARK_FLAGS));
            Assertions.assertEquals(0, game.getSelectionImages().getHealthBarPool().getAllocatedCount());
            Assertions.assertEquals(0, game.getSelectionImages().getCirclePool().getAllocatedCount());

            game.getSelectionImages().restoreAfterSave();

            Assertions.assertEquals(selectedImages + SELECTION_NODE_COUNT, selectedSprite.getImages().size(),
                                    "The local selection is selected again.");
            Assertions.assertEquals(0, flashedSprite.getFlags() & SELECTION_CIRCLE_FLAG, "A flashing circle stays off.");
        });
    }

    @Test
    void alliance_command_recolors_the_circle_of_a_selected_unit_of_the_new_ally() throws IOException {
        ReplayRuntime live = RunningGameScenario.startLiveGame(requireGameData());

        RunningGameScenario.runInCommandDispatch(live, () -> {
            Game game = live.getGame();
            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, UNIT_X, UNIT_Y, RunningGameScenario.COMPUTER);
            Sprite sprite = requireSprite(marine);

            game.getInterfaceLink().getLocalSelection().setSelectedPoolIndexRaw(FIRST_SLOT, marine.getIndex());
            game.getSelectionImages().selectUnit(marine, FIRST_SLOT);

            Assertions.assertEquals(ENEMY_COLOR, sprite.getImages().get(findCircle(sprite, ImageIds.SELECTION_CIRCLE_22PIXELS)).getModifierData1(),
                                    "The computer is the local human's enemy.");

            game.getPlayerDeparture().setAlliances(RunningGameScenario.HUMAN,
                                                   PlayerRelations.ALLIED << RunningGameScenario.HUMAN * ALLIANCE_BITS |
                                                   PlayerRelations.ALLIED << RunningGameScenario.COMPUTER * ALLIANCE_BITS);

            Assertions.assertEquals(ALLIED_COLOR, sprite.getImages().get(findCircle(sprite, ImageIds.SELECTION_CIRCLE_22PIXELS)).getModifierData1(),
                                    "The selected unit of the new ally is selected again in the allied color.");
            Assertions.assertEquals(ALLIED_COLOR, game.getSelectionImages().getCircleColor(RunningGameScenario.COMPUTER));
            Assertions.assertEquals(ONE_NODE, game.getSelectionImages().getCirclePool().getAllocatedCount());
        });
    }

    @Test
    void game_start_fills_the_local_relation_table_that_the_eud_view_serves() {
        Game game = this.requireGame();
        GameStructureView view = new GameStructureView(game, game.getTriggerContext());

        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            int expected = player == NEUTRAL_PLAYER ? ALLIED_COLOR : OWN_COLOR;

            Assertions.assertEquals(expected, view.readContextByte(LOCAL_RELATIONS + player), "A replay colors player " + player + ".");
        }
    }

    /**
     * Compares the internal {@link Game#computeStateHash} before every frame with and without a local selection; it is the one value that covers the
     * whole represented simulation state, so equal hashes show that the selection images, their sprite flags and the flash timer stay out of the
     * simulation.
     */
    @Test
    void local_selections_leave_the_state_hash_unchanged() throws IOException {
        Assertions.assertEquals(computeStateHashes(NO_PLAYER), computeStateHashes(FOLLOWED_PLAYER),
                                "The state hash of every frame is the same whatever the local selection.");
    }

    private @NotNull Game requireGame() {
        Assertions.assertNotNull(this.runtime);

        return this.runtime.getGame();
    }

    /**
     * Creates a completed {@link Unit} instance of the computer player at a place of the unit grid near the computer's base.
     *
     * @param type      the unit type
     * @param gridIndex the place in the grid, row by row
     * @return the created {@link Unit} instance
     * @throws AssertionError if the game is not started
     */
    private @NotNull Unit createUnit(int type, int gridIndex) {
        return RunningGameScenario.createUnit(this.requireGame(),
                                              type,
                                              UNIT_X + gridIndex % UNITS_PER_ROW * UNIT_SPACING, UNIT_Y + gridIndex / UNITS_PER_ROW * UNIT_SPACING,
                                              RunningGameScenario.COMPUTER);
    }
}
