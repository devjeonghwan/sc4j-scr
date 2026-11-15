package org.fogofwarlabs.sc4j.scr.game.sprite;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.fogofwarlabs.sc4j.scr.data.type.ScrSpriteTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ImageIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.presentation.LocalSelection;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Links the selection circles and the health bar of the sprites into their image lists, and unlinks them again.
 * <p>
 * The solid selection circle (images {@code 561}..{@code 570}), the dashed team-selection circle (images {@code 571}..{@code 580}) and the health bar
 * (image {@code 560}) are image nodes of the sprite's own image list, taken from two pools of their own: one of {@link #CIRCLE_CAPACITY} circles and
 * one of {@link #HEALTH_BAR_CAPACITY} health bars. The image pool is never used, so it stays the same whatever is selected. A pool without a free
 * node creates nothing, and the sprite flag of the missing circle stays cleared.
 * <p>
 * A new circle goes just above the tail image when that image draws a shadow, otherwise below it as the new tail, so it is drawn before the images it
 * lies under; the health bar goes above the head image and is drawn after the images below it. Images created later are linked by their own reference
 * image and may land on either side of these nodes.
 * <p>
 * The nodes follow the local player only: the local selection array, the flash timer while the sprite is visible to the local comparison mask, and
 * the team marks of the local player's teammates. Their sprite flags ({@link #LOCAL_SPRITE_FLAGS}) and the flash timer are local state, which the
 * state hash leaves out. The colors come from the local relation table of the game context ({@link #COLOR_OWN}, {@link #COLOR_ALLIED} and
 * {@link #COLOR_ENEMY}), which {@link #updateLocalRelations} rebuilds at the game start and after the alliance changes of the local player, linking
 * the nodes again in the new colors.
 */
public final class SelectionImages {
    /**
     * The number of circle nodes, solid and dashed together.
     */
    public static final int CIRCLE_CAPACITY = 80;
    /**
     * The number of health bar nodes.
     */
    public static final int HEALTH_BAR_CAPACITY = 12;
    /**
     * The circle color of the local player's units, and of every player's units in a replay except the neutral player.
     */
    public static final int COLOR_OWN = 0;
    /**
     * The circle color of the units of a player the local player is allied to, and of the neutral player in a replay.
     */
    public static final int COLOR_ALLIED = 1;
    /**
     * The circle color of the other players' units.
     */
    public static final int COLOR_ENEMY = 2;
    /**
     * The sprite flags of the selection nodes, which follow the local player: the solid circle, the team mark count and the selection.
     */
    public static final int LOCAL_SPRITE_FLAGS = Sprite.FLAG_SELECTION_CIRCLE | Sprite.FLAG_TEAM_SELECTION_CIRCLE_1 |
                                                 Sprite.FLAG_TEAM_SELECTION_CIRCLE_2 | Sprite.FLAG_SELECTED;

    /**
     * The number of circle sizes, the images of one kind of circle.
     */
    private static final int CIRCLE_SIZES = 10;
    /**
     * The sprite flags of the team mark count: the number of the local player's teammates whose selection holds the unit.
     */
    private static final int TEAM_MARK_MASK = Sprite.FLAG_TEAM_SELECTION_CIRCLE_1 | Sprite.FLAG_TEAM_SELECTION_CIRCLE_2;
    /**
     * The bit position of the team mark count in the sprite flags.
     */
    private static final int TEAM_MARK_SHIFT = 1;
    /**
     * One team mark in the sprite flags.
     */
    private static final int TEAM_MARK = 1 << TEAM_MARK_SHIFT;
    /**
     * The largest flag value with at most one team mark; a larger count already has its dashed circle.
     */
    private static final int SINGLE_TEAM_MARK_FLAGS = TEAM_MARK;
    /**
     * The flash timer bit that shows the solid circle while the timer runs.
     */
    private static final int FLASH_VISIBLE_BIT = 0x08;
    /**
     * The distance in pixels from the middle of the solid circle's first frame down to the health bar.
     */
    private static final int HEALTH_BAR_DISTANCE = 8;
    /**
     * The shift that halves the solid circle's frame height, giving the distance from the top of the frame to its middle.
     */
    private static final int CIRCLE_MIDDLE_SHIFT = 1;
    /**
     * The mask of the low BYTE of a value.
     */
    private static final int BYTE_MASK = 0xFF;

    /**
     * Tests whether an image is a selection node: a health bar or a solid or dashed circle.
     *
     * @param image the image
     * @return {@code true} if the image is a health bar or a selection circle, {@code false} otherwise
     */
    public static boolean isSelectionImage(@NotNull SpriteImage image) {
        return image.getModifier() == SpriteImage.MODIFIER_HEALTH_BAR || image.getModifier() == SpriteImage.MODIFIER_SELECTION_CIRCLE;
    }

    /**
     * @param sprite the sprite
     * @return the team mark count of the sprite flags, from {@code 0} through {@code 3}
     */
    public static int getTeamMarkCount(@NotNull Sprite sprite) {
        return (sprite.getFlags() & TEAM_MARK_MASK) >>> TEAM_MARK_SHIFT;
    }

    /**
     * Finds the first circle of a kind from the tail of a sprite's image list toward its head.
     *
     * @param sprite     the sprite
     * @param firstImage the first image of the kind: {@link ImageIds#SELECTION_CIRCLE_22PIXELS} or {@link ImageIds#SELECTION_CIRCLE_DASHED_22PIXELS}
     * @return the circle, or {@code null} when the list holds none
     */
    private static @Nullable SpriteImage findCircle(@NotNull Sprite sprite, int firstImage) {
        for (int position = sprite.getImages().size() - 1; position >= 0; position--) {
            SpriteImage image = sprite.getImages().get(position);

            if (image.getImageTypeId() - firstImage >= 0 && image.getImageTypeId() - firstImage < CIRCLE_SIZES) {
                return image;
            }
        }

        return null;
    }

    private final @NotNull Game game;

    private final @NotNull SpriteImagePool circles;
    private final @NotNull SpriteImagePool healthBars;

    /**
     * Creates a {@link SelectionImages} instance with full circle and health bar pools.
     *
     * @param game the game whose sprites take the nodes
     */
    public SelectionImages(@NotNull Game game) {
        this.game = game;

        this.circles    = new SpriteImagePool(CIRCLE_CAPACITY);
        this.healthBars = new SpriteImagePool(HEALTH_BAR_CAPACITY);
    }

    /**
     * @return the pool of the solid and dashed circle nodes
     */
    public @NotNull SpriteImagePool getCirclePool() {
        return this.circles;
    }

    /**
     * @return the pool of the health bar nodes
     */
    public @NotNull SpriteImagePool getHealthBarPool() {
        return this.healthBars;
    }

    /**
     * Reads the circle color of an owner from the local relation table ({@link #updateLocalRelations}).
     *
     * @param owner the owner of the unit or sprite, from {@code 0} through {@code 11}
     * @return the circle color
     */
    public int getCircleColor(int owner) {
        return this.game.getGameContext().getLocalRelation(owner);
    }


    /**
     * Rebuilds the local relation table and links the nodes again in its colors. In a replay every player is {@link #COLOR_OWN} but the neutral
     * player, who is {@link #COLOR_ALLIED}; in a game the local player is {@link #COLOR_OWN}, a player the local player's alliance row names
     * {@link #COLOR_ALLIED} and every other player {@link #COLOR_ENEMY}. Then every unit of the local selection array up to the first empty slot that
     * the local player does not own (every unit in a replay) is deselected and selected again in its slot, and in a team game every such unit of the
     * teammates' selections, from the first player to the last, loses one team mark and takes it again. An observer ({@link LocalVision#isObserver})
     * changes nothing.
     */
    public void updateLocalRelations() {
        if (this.game.getLocalVision().isObserver()) {
            return;
        }

        UnitSelection selection = this.game.getSelection();
        boolean replay = this.game.isReplayPlayback();
        UnitCreationContext context = this.game.getUnitSystem().getUnitCreationContext();
        int localPlayer = context == null ? -1 : context.getLocalPlayer();
        PlayerRelations relations = this.game.getPlayerTable().getPlayerRelations();
        boolean localPlayerKnown = localPlayer >= 0 && localPlayer < PlayerTable.PLAYER_SLOTS;

        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            int color;

            if (replay) {
                color = player == PlayerTable.NEUTRAL_PLAYER_ID ? COLOR_ALLIED : COLOR_OWN;
            } else if (player == localPlayer) {
                color = COLOR_OWN;
            } else {
                color = relations != null && localPlayerKnown && relations.getAlliance(localPlayer, player) != 0 ? COLOR_ALLIED : COLOR_ENEMY;
            }

            this.game.getGameContext().setLocalRelation(player, color);
        }

        LocalSelection localSelection = this.game.getInterfaceLink().getLocalSelection();
        UnitPool units = this.game.getUnitSystem().getUnitPool();

        for (int slot = 0; slot < LocalSelection.CAPACITY && units != null; slot++) {
            int poolIndex = localSelection.getSelectedPoolIndex(slot);

            if (poolIndex == 0) {
                break;
            }

            Unit unit = units.getUnit(poolIndex);

            if (replay || unit.getOwnerIndex() != localPlayer) {
                this.deselectUnit(unit);
                this.selectUnit(unit, slot);
            }
        }

        if (selection == null || selection.getTeamSize() == 0) {
            return;
        }

        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            if (!this.isTeammate(selection, slot)) {
                continue;
            }

            for (int position = 0; position < UnitSelection.MAXIMUM_SELECTED; position++) {
                Unit unit = selection.getSelectedUnit(slot, position);

                if (unit == null) {
                    break;
                }

                Sprite sprite = unit.getSpriteOrNull();

                if (sprite != null && (replay || unit.getOwnerIndex() != localPlayer)) {
                    this.removeTeamMark(sprite);
                    this.addTeamMark(sprite, this.getCircleColor(unit.getOwnerIndex()), selection.getTeamSize());
                }
            }
        }
    }

    /**
     * Replaces the local selection: deselects every previously selected unit in slot order up to the first empty slot, then selects the new units
     * with their slot as selection index. The caller has already stored the new units in the local selection array, which the health bars read.
     *
     * @param previous the previous local selection array, {@code null} for an empty slot
     * @param selected the new selection in slot order
     */
    public void changeLocalSelection(@Nullable Unit @NotNull [] previous, @NotNull Unit @NotNull [] selected) {
        for (Unit unit : previous) {
            if (unit == null) {
                break;
            }

            this.deselectUnit(unit);
        }

        for (int slot = 0; slot < selected.length; slot++) {
            this.selectUnit(selected[slot], slot);
        }
    }

    /**
     * Selects a unit of the local selection: an invincible unit shows only the solid circle, others the health bar and the solid circle, in the unit
     * owner's circle color.
     *
     * @param unit           the selected unit
     * @param selectionIndex the slot of the unit in the local selection array
     * @throws NullPointerException if the world was built without game data
     */
    public void selectUnit(@NotNull Unit unit, int selectionIndex) {
        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null) {
            return;
        }

        int color = this.getCircleColor(unit.getOwnerIndex());

        if ((this.game.requireGameData().getUnitTypes().getFlags(unit.getUnitId()) & UnitTypeFlags.INVINCIBLE) != 0) {
            this.showSelectionCircle(sprite, color);

            sprite.setFlags(sprite.getFlags() | Sprite.FLAG_SELECTED);
            sprite.setSelectionIndex(selectionIndex);

            return;
        }

        this.select(sprite, selectionIndex, color);
    }

    /**
     * Deselects a unit of the local selection: an invincible unit loses its solid circle, others their health bar and solid circle.
     *
     * @param unit the deselected unit
     * @throws NullPointerException if the world was built without game data
     */
    public void deselectUnit(@NotNull Unit unit) {
        Sprite sprite = unit.getSpriteOrNull();

        if (sprite == null) {
            return;
        }

        if ((this.game.requireGameData().getUnitTypes().getFlags(unit.getUnitId()) & UnitTypeFlags.INVINCIBLE) != 0) {
            this.hideSelectionCircle(sprite);

            sprite.setFlags(sprite.getFlags() & ~Sprite.FLAG_SELECTED);

            return;
        }

        this.deselect(sprite);
    }

    /**
     * Selects a sprite that is not selected yet: stores the selection index, sets {@link Sprite#FLAG_SELECTED}, links the health bar and, unless the
     * sprite already shows it, the solid circle.
     *
     * @param sprite         the sprite
     * @param selectionIndex the slot in the local selection array
     * @param color          the circle color
     */
    public void select(@NotNull Sprite sprite, int selectionIndex, int color) {
        if ((sprite.getFlags() & Sprite.FLAG_SELECTED) != 0) {
            return;
        }

        sprite.setSelectionIndex(selectionIndex);
        sprite.setFlags(sprite.getFlags() | Sprite.FLAG_SELECTED);

        this.createHealthBar(sprite);
        this.showSelectionCircle(sprite, color);
    }

    /**
     * Deselects a selected sprite: clears {@link Sprite#FLAG_SELECTED}, unlinks the first health bar from the head and then the solid circle.
     *
     * @param sprite the sprite
     * @return the color of the removed solid circle, or {@code 0}
     */
    public int deselect(@NotNull Sprite sprite) {
        if ((sprite.getFlags() & Sprite.FLAG_SELECTED) == 0) {
            return 0;
        }

        sprite.setFlags(sprite.getFlags() & ~Sprite.FLAG_SELECTED);

        for (SpriteImage image : sprite.getImages()) {
            if (image.getModifier() == SpriteImage.MODIFIER_HEALTH_BAR) {
                this.destroyNode(this.healthBars, image);

                break;
            }
        }

        return this.hideSelectionCircle(sprite);
    }

    /**
     * Shows the solid circle without selecting the sprite, unless it already shows it; when no circle node is free
     * {@link Sprite#FLAG_SELECTION_CIRCLE} stays cleared.
     *
     * @param sprite the sprite
     * @param color  the circle color
     */
    public void showSelectionCircle(@NotNull Sprite sprite, int color) {
        if ((sprite.getFlags() & Sprite.FLAG_SELECTION_CIRCLE) == 0 && this.createCircle(sprite, color, ImageIds.SELECTION_CIRCLE_22PIXELS) != null) {
            sprite.setFlags(sprite.getFlags() | Sprite.FLAG_SELECTION_CIRCLE);
        }
    }

    /**
     * Hides the solid circle: clears {@link Sprite#FLAG_SELECTION_CIRCLE} and unlinks the first solid circle from the tail.
     *
     * @param sprite the sprite
     * @return the color of the removed circle, or {@code 0}
     */
    public int hideSelectionCircle(@NotNull Sprite sprite) {
        if ((sprite.getFlags() & Sprite.FLAG_SELECTION_CIRCLE) == 0) {
            return 0;
        }

        sprite.setFlags(sprite.getFlags() & ~Sprite.FLAG_SELECTION_CIRCLE);

        return this.destroyCircle(sprite, ImageIds.SELECTION_CIRCLE_22PIXELS);
    }

    /**
     * Counts one more teammate's selection, up to one less than the team size; the count wraps within its two flag bits. The mark that brings the
     * count to one, or wraps it to zero, links a dashed circle; when no circle node is free the count is cleared.
     *
     * @param sprite   the sprite
     * @param color    the circle color
     * @param teamSize the slots of a team, or {@code 0} outside a team game
     */
    public void addTeamMark(@NotNull Sprite sprite, int color, int teamSize) {
        if (getTeamMarkCount(sprite) == (teamSize - 1 & BYTE_MASK)) {
            return;
        }

        int previousFlags = sprite.getFlags();
        int flags = (previousFlags + TEAM_MARK ^ previousFlags) & TEAM_MARK_MASK ^ previousFlags;

        sprite.setFlags(flags);

        if ((flags & TEAM_MARK_MASK) <= SINGLE_TEAM_MARK_FLAGS &&
            this.createCircle(sprite, color, ImageIds.SELECTION_CIRCLE_DASHED_22PIXELS) == null) {
            sprite.setFlags(sprite.getFlags() & ~TEAM_MARK_MASK);
        }
    }

    /**
     * Counts one teammate's selection less; the last one unlinks the first dashed circle from the tail.
     *
     * @param sprite the sprite
     */
    public void removeTeamMark(@NotNull Sprite sprite) {
        int count = getTeamMarkCount(sprite);

        if (count == 0) {
            return;
        }

        int flags = sprite.getFlags() & ~TEAM_MARK_MASK | (count - 1) << TEAM_MARK_SHIFT;

        sprite.setFlags(flags);

        if ((flags & TEAM_MARK_MASK) == 0) {
            this.destroyCircle(sprite, ImageIds.SELECTION_CIRCLE_DASHED_22PIXELS);
        }
    }

    /**
     * Clears every team mark and unlinks the first dashed circle from the tail.
     *
     * @param sprite the sprite
     * @return the color of the removed circle, or {@code 0}
     */
    public int clearTeamMarks(@NotNull Sprite sprite) {
        if ((sprite.getFlags() & TEAM_MARK_MASK) == 0) {
            return 0;
        }

        sprite.setFlags(sprite.getFlags() & ~TEAM_MARK_MASK);

        return this.destroyCircle(sprite, ImageIds.SELECTION_CIRCLE_DASHED_22PIXELS);
    }

    /**
     * Counts a running flash timer down by one frame and shows or hides the solid circle: the timer stops at once when the sprite is not visible to
     * the local comparison mask ({@link LocalVision#getComparisonMask}); the circle shows in the owner's circle color while timer bit {@code 3} is
     * set, and at zero while the sprite is selected, and is hidden otherwise.
     *
     * @param sprite the sprite
     * @param owner  the owner of the sprite's unit, or of the sprite
     */
    public void updateFlash(@NotNull Sprite sprite, int owner) {
        if (sprite.getFlashTimer() == 0) {
            return;
        }

        int timer = sprite.getFlashTimer() - 1;

        if ((sprite.getVisibilityFlags() & this.game.getLocalVision().getComparisonMask() & BYTE_MASK) == 0) {
            timer = 0;
        }

        sprite.setFlashTimer(timer);

        if ((timer & FLASH_VISIBLE_BIT) != 0 || timer == 0 && (sprite.getFlags() & Sprite.FLAG_SELECTED) != 0) {
            this.showSelectionCircle(sprite, this.getCircleColor(owner));
        } else {
            this.hideSelectionCircle(sprite);
        }
    }

    /**
     * Changes the sprite type and links the circles and the health bar again for the new type: a selected sprite gets a new health bar and solid
     * circle, a sprite showing only the solid circle a new circle; then the dashed circle is unlinked and linked again while its team marks stay. A
     * circle that finds no free node clears its flag.
     *
     * @param sprite       the sprite
     * @param spriteTypeId the new {@code sprites.dat} index
     */
    public void changeSpriteType(@NotNull Sprite sprite, int spriteTypeId) {
        sprite.setSpriteTypeId(spriteTypeId);

        if ((sprite.getFlags() & Sprite.FLAG_SELECTED) != 0) {
            int color = this.deselect(sprite);

            sprite.setFlags(sprite.getFlags() | Sprite.FLAG_SELECTED);
            this.createHealthBar(sprite);
            this.showSelectionCircle(sprite, color);
        } else if ((sprite.getFlags() & Sprite.FLAG_SELECTION_CIRCLE) != 0) {
            this.showSelectionCircle(sprite, this.hideSelectionCircle(sprite));
        }

        if ((sprite.getFlags() & TEAM_MARK_MASK) == 0 || sprite.getImages().isEmpty()) {
            return;
        }

        int color = this.destroyCircle(sprite, ImageIds.SELECTION_CIRCLE_DASHED_22PIXELS);

        if (this.createCircle(sprite, color, ImageIds.SELECTION_CIRCLE_DASHED_22PIXELS) == null) {
            sprite.setFlags(sprite.getFlags() & ~TEAM_MARK_MASK);
        }
    }

    /**
     * Replaces the images of a sprite around its selection nodes: unlinks the health bar and the circles, runs the replacement, then links the nodes
     * again. The health bar comes back with the solid circle only when both were shown; the team mark count is counted up again from zero with the
     * color of the removed dashed circle.
     *
     * @param sprite      the sprite
     * @param replacement the replacement of the sprite's images, which leaves at least one image
     */
    public void replaceImages(@NotNull Sprite sprite, @NotNull Runnable replacement) {
        int previousFlags = sprite.getFlags();
        int color = (previousFlags & Sprite.FLAG_SELECTED) != 0 ? this.deselect(sprite) : this.hideSelectionCircle(sprite);
        int teamColor = this.clearTeamMarks(sprite);

        replacement.run();

        if ((previousFlags & Sprite.FLAG_SELECTION_CIRCLE) != 0 && this.restoreSelection(sprite, previousFlags)) {
            this.showSelectionCircle(sprite, color);
        }

        UnitSelection selection = this.game.getSelection();
        int teamSize = selection == null ? 0 : selection.getTeamSize();
        int teamMarks = (previousFlags & TEAM_MARK_MASK) >>> TEAM_MARK_SHIFT;

        for (int mark = teamMarks; mark > 0; mark--) {
            this.addTeamMark(sprite, teamColor, teamSize);
        }
    }

    /**
     * Restores the selection of a sprite that showed its solid circle before its images were replaced: a selected sprite is selected again with a new
     * health bar.
     *
     * @param sprite        the sprite
     * @param previousFlags the sprite flags before the replacement
     * @return {@code true} if the solid circle is to be shown again, {@code false} otherwise
     */
    private boolean restoreSelection(@NotNull Sprite sprite, int previousFlags) {
        if ((previousFlags & Sprite.FLAG_SELECTED) == 0) {
            return true;
        }

        if ((sprite.getFlags() & Sprite.FLAG_SELECTED) != 0) {
            return false;
        }

        sprite.setFlags(sprite.getFlags() | Sprite.FLAG_SELECTED);
        this.createHealthBar(sprite);

        return true;
    }

    /**
     * Unlinks the health bars and circles of every sprite slot in slot order, as a save does before it writes the pools: a selected sprite is
     * deselected, another one hides its solid circle, and every sprite loses its team marks.
     */
    public void detachBeforeSave() {
        SpritePool sprites = this.game.getSpriteSystem().getSpritePool();

        if (sprites == null) {
            return;
        }

        for (int poolIndex = 0; poolIndex < sprites.getCapacity(); poolIndex++) {
            Sprite sprite = sprites.getSprite(poolIndex);

            if ((sprite.getFlags() & Sprite.FLAG_SELECTED) != 0) {
                this.deselect(sprite);
            } else {
                this.hideSelectionCircle(sprite);
            }

            this.clearTeamMarks(sprite);
        }
    }

    /**
     * Selects the local selection again after a save, in slot order up to the first empty slot, then marks again the selections of the local player's
     * teammates in a team game, from the last player to the first.
     */
    public void restoreAfterSave() {
        LocalSelection localSelection = this.game.getInterfaceLink().getLocalSelection();
        UnitPool units = this.game.getUnitSystem().getUnitPool();

        for (int slot = 0; slot < LocalSelection.CAPACITY && units != null; slot++) {
            int poolIndex = localSelection.getSelectedPoolIndex(slot);

            if (poolIndex == 0) {
                break;
            }

            this.selectUnit(units.getUnit(poolIndex), slot);
        }

        UnitSelection selection = this.game.getSelection();

        if (selection == null || selection.getTeamSize() == 0) {
            return;
        }

        for (int slot = PlayerTable.PLAYABLE_PLAYER_SLOTS - 1; slot >= 0; slot--) {
            if (!this.isTeammate(selection, slot)) {
                continue;
            }

            for (int position = 0; position < UnitSelection.MAXIMUM_SELECTED; position++) {
                Unit unit = selection.getSelectedUnit(slot, position);

                if (unit == null) {
                    break;
                }

                Sprite sprite = unit.getSpriteOrNull();

                if (sprite != null) {
                    this.addTeamMark(sprite, this.getCircleColor(unit.getOwnerIndex()), selection.getTeamSize());
                }
            }
        }
    }

    /**
     * Tests whether a player slot is a teammate of the local slot: another slot of the local slot's force.
     *
     * @param selection the selections, which know the local slot
     * @param slot      the player slot from {@code 0} through {@code 7}
     * @return {@code true} if the slot is another slot of the local slot's force, {@code false} otherwise
     */
    private boolean isTeammate(@NotNull UnitSelection selection, int slot) {
        int localSlot = selection.getLocalPlayerIndex();
        Player localPlayer = localSlot >= 0 && localSlot < PlayerTable.PLAYER_SLOTS ? this.game.getPlayerTable().getPlayer(localSlot) : null;
        Player player = this.game.getPlayerTable().getPlayer(slot);

        return localPlayer != null && player != null && slot != localSlot && player.getTeam() == localPlayer.getTeam();
    }

    /**
     * Links a circle node: just above the tail image when it draws a shadow, otherwise below it as the new tail. The circle takes the sprite type's
     * circle size and vertical offset, keeps its color in the modifier data and runs its Init script.
     *
     * @param sprite     the sprite
     * @param color      the circle color
     * @param firstImage {@link ImageIds#SELECTION_CIRCLE_22PIXELS} or {@link ImageIds#SELECTION_CIRCLE_DASHED_22PIXELS}
     * @return the linked circle, or {@code null} when no circle node is free
     * @throws IllegalStateException if the sprite has no image
     * @throws NullPointerException  if the world was built without game data
     */
    private @Nullable SpriteImage createCircle(@NotNull Sprite sprite, int color, int firstImage) {
        if (sprite.getImages().isEmpty()) {
            throw Exceptions.newIllegalStateException("A selection circle needs the tail image of its sprite.");
        }

        SpriteImage circle = this.circles.allocate();

        if (circle == null) {
            return null;
        }

        SpriteImage tail = sprite.getImages().getLast();

        if (tail.getModifier() == SpriteImage.MODIFIER_SHADOW) {
            sprite.insertImageAbove(tail, circle);
        } else {
            sprite.insertImageBelow(tail, circle);
        }

        ScrSpriteTypeView spriteTypes = this.game.requireGameData().getSpriteTypes();
        int imageType = firstImage + spriteTypes.getSelectionCircle(sprite.getSpriteTypeId());

        circle.initializeNativeStorage(imageType,
                                       0, spriteTypes.getSelectionCircleOffset(sprite.getSpriteTypeId()),
                                       circle.getFlags() | SpriteImage.FLAG_REDRAW,
                                       SpriteImage.MODIFIER_SELECTION_CIRCLE);
        circle.setModifierData1(color & BYTE_MASK);
        this.initializeScript(sprite, circle);

        return circle;
    }

    /**
     * Links a health bar node above the head image. The bar lies the circle's half height and {@link #HEALTH_BAR_DISTANCE} pixels below the circle's
     * offset, keeps the pool index of the local selection array's unit at the sprite's selection index in its modifier data, and runs its Init
     * script.
     *
     * @param sprite the selected sprite
     * @throws NullPointerException if the world was built without game data
     */
    private void createHealthBar(@NotNull Sprite sprite) {
        SpriteImage healthBar = this.healthBars.allocate();

        if (healthBar == null) {
            return;
        }

        sprite.insertHeadImage(healthBar);

        ScrSpriteTypeView spriteTypes = this.game.requireGameData().getSpriteTypes();
        int circleImage = ImageIds.SELECTION_CIRCLE_22PIXELS + spriteTypes.getSelectionCircle(sprite.getSpriteTypeId());
        int circleHeight = this.loadGraphics(circleImage).getFrameHeight(0);
        int offsetY = (circleHeight >> CIRCLE_MIDDLE_SHIFT) + HEALTH_BAR_DISTANCE + spriteTypes.getSelectionCircleOffset(sprite.getSpriteTypeId());
        int selectionIndex = sprite.getSelectionIndex();
        int unitPoolIndex = selectionIndex < LocalSelection.CAPACITY
                            ? this.game.getInterfaceLink().getLocalSelection().getSelectedPoolIndex(selectionIndex)
                            : 0;

        healthBar.initializeNativeStorage(ImageIds.HEALTH_BAR, 0, offsetY, SpriteImage.FLAG_REDRAW, SpriteImage.MODIFIER_HEALTH_BAR);
        healthBar.setModifierData1(unitPoolIndex);
        this.initializeScript(sprite, healthBar);
    }

    /**
     * Sets the graphics and the iscript of a new node and runs its Init animation.
     *
     * @param sprite the node's sprite
     * @param node   the linked node
     * @throws NullPointerException if the world was built without game data
     */
    private void initializeScript(@NotNull Sprite sprite, @NotNull SpriteImage node) {
        int scriptIdentifier = this.game.requireGameData().getImageTypes().getIscript(node.getImageTypeId());

        node.setGraphics(this.loadGraphics(node.getImageTypeId()));
        node.setScriptIdentifier(scriptIdentifier);
        this.game.getIscriptExecutor().runAnimation(node, sprite, null, scriptIdentifier, IscriptExecutor.ANIMATION_INIT);
    }

    /**
     * Loads the graphics of an image type.
     *
     * @param imageType the {@code images.dat} index
     * @return the graphics
     * @throws IllegalStateException if the graphics cannot be read
     * @throws NullPointerException  if the world was built without game data
     */
    private @NotNull GrpFile loadGraphics(int imageType) {
        try {
            return this.game.requireGameData().getGrpFile(imageType);
        } catch (IOException exception) {
            throw Exceptions.newIllegalStateException(exception, "Failed to load the GRP for image {0}", imageType);
        }
    }

    /**
     * Unlinks the first circle of a kind from the tail and returns it to its pool.
     *
     * @param sprite     the sprite
     * @param firstImage the first image of the kind
     * @return the color of the removed circle, or {@code 0} when the list holds none
     */
    private int destroyCircle(@NotNull Sprite sprite, int firstImage) {
        SpriteImage circle = findCircle(sprite, firstImage);

        if (circle == null) {
            return 0;
        }

        int color = circle.getModifierData1() & BYTE_MASK;

        this.destroyNode(this.circles, circle);

        return color;
    }

    /**
     * Unlinks a node from its sprite and returns it after the free head of its pool.
     *
     * @param pool the node's pool
     * @param node the linked node
     */
    private void destroyNode(@NotNull SpriteImagePool pool, @NotNull SpriteImage node) {
        Sprite sprite = node.getSprite();

        if (sprite != null) {
            sprite.removeImage(node);
        }

        pool.recycle(node);
    }
}
