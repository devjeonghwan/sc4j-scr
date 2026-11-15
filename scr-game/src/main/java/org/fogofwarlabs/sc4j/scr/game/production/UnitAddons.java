package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.entity.column.ColumnKey;
import org.fogofwarlabs.sc4j.engine.entity.column.ColumnReset;
import org.fogofwarlabs.sc4j.engine.entity.column.IntColumn;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.order.ScrOrderSystem;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.presentation.NotificationEvents;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.RegionPathSearch;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Maintains Terran building to add-on relationships and add-on construction.
 * <p>
 * An add-on under construction is the parent building's current build unit under the {@link ScrOrderIds#BUILD_ADDON} secondary order. A completed
 * add-on is attached as the parent's {@link Unit#getAddon() add-on} and shares its owner; a parent that lifts off or dies leaves the add-on neutral,
 * and a Terran building completed or landed at the matching position reclaims it.
 * <p>
 * The add-on build type of the native unit union is registered as a unit pool column ({@link #ADDON_COLUMNS}). The
 * column keeps its value when a slot is reused, as the union field does; unit creation and the unit reset methods set it.
 */
public final class UnitAddons {
    /**
     * The add-on build type ({@link Unit#getAddonBuildType()}).
     */
    public static final @NotNull ColumnKey<IntColumn> ADDON_BUILD_TYPE_COLUMN = ColumnKey.ofInt("addonBuildType", 0, ColumnReset.KEEP);
    /**
     * The unit pool columns of the add-on state, in registration order.
     */
    public static final @NotNull List<@NotNull ColumnKey<?>> ADDON_COLUMNS = List.of(
            ADDON_BUILD_TYPE_COLUMN
    );

    private static final int FIXED_POINT_FRACTION_BITS = 8;
    private static final int FOOTPRINT_CENTER_DIVISOR = 2;
    private static final int CLEARANCE_BORDER_SIDES = 2;

    /**
     * The {@link ScrOrderIds#PLACE_ADDON} states: moving the building to its landed position, placing the add-on.
     */
    private static final int PLACE_STATE_START = 0;
    private static final int PLACE_STATE_PLACING = 1;

    /**
     * Tests whether a building type can attach an add-on type.
     *
     * @param buildingType the parent building type
     * @param addonType    the add-on type
     * @return {@code true} if the building type takes the add-on type, {@code false} otherwise
     */
    public static boolean canAttach(int buildingType, int addonType) {
        return switch (ScrUnitIds.orNone(addonType)) {
            case ScrUnitIds.TERRAN_COMSAT_STATION, ScrUnitIds.TERRAN_NUCLEAR_SILO -> buildingType == ScrUnitIds.TERRAN_COMMAND_CENTER;
            case ScrUnitIds.TERRAN_CONTROL_TOWER -> buildingType == ScrUnitIds.TERRAN_STARPORT;
            case ScrUnitIds.TERRAN_COVERT_OPS, ScrUnitIds.TERRAN_PHYSICS_LAB -> buildingType == ScrUnitIds.TERRAN_SCIENCE_FACILITY;
            case ScrUnitIds.TERRAN_MACHINE_SHOP -> buildingType == ScrUnitIds.TERRAN_FACTORY;
            default -> false;
        };
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link UnitAddons} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitAddons(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Cancels the selected building's incomplete add-on.
     *
     * @param owner            the command player
     * @param instructionLimit the cancellation budget
     * @return {@code true} if an add-on was canceled, {@code false} otherwise
     * @throws UnsupportedOperationException if selection storage is absent
     */
    public boolean cancelSelected(int owner, int instructionLimit) {
        UnitSelection selection = this.game.getSelection();

        if (selection == null) {
            throw Exceptions.newUnsupportedOperationException("Add-on cancellation requires initialized selection storage.");
        }

        Unit building = selection.getSelectedUnit(owner, 0);

        if (building == null || selection.getSelectedUnit(owner, 1) != null || building.getOwnerIndex() != owner) {
            return false;
        }

        if (building.isDestroyed()) {
            return false;
        }

        int status = building.getStatus();

        if ((status & UnitStatusFlags.COMPLETED) == 0 || !UnitQueries.isGroundedBuilding(building) ||
            building.getSecondaryOrder() != ScrOrderIds.BUILD_ADDON) {
            return false;
        }

        Unit addon = building.getCurrentBuildUnit();

        if (addon == null || (addon.getStatus() & UnitStatusFlags.COMPLETED) != 0) {
            return false;
        }

        this.game.getUnitConstruction().cancelBuildingUnit(addon, instructionLimit);

        return true;
    }


    /**
     * Moves the building to its landed position for the requested add-on and creates the incomplete add-on.
     * <p>
     * A grounded building elsewhere lifts off first and a flying building lands first, each followed by a queued {@link ScrOrderIds#PLACE_ADDON}. The
     * order target is converted from the building center to the add-on center before supply, resources and placement are checked.
     *
     * @param building         the building executing {@link ScrOrderIds#PLACE_ADDON}
     * @param instructionLimit the order and unit creation budget
     * @throws IllegalStateException         if the creation context is missing
     * @throws NullPointerException          if the world was built without game data
     * @throws UnsupportedOperationException if the unit accounting or the unit pool is not initialized
     */
    public void placeAddon(@NotNull Unit building, int instructionLimit) {
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        int addonType = building.getAddonBuildType();

        if (addonType == ScrUnitIds.NONE || (units.getFlags(addonType) & UnitTypeFlags.ADDON) == 0) {
            return;
        }

        int positionX = (short) building.getOrderTargetX();
        int positionY = (short) building.getOrderTargetY();

        if (building.getPrimaryOrderState() == PLACE_STATE_START) {
            if (UnitQueries.isGroundedBuilding(building)) {
                int distance = RegionPathSearch.calculateDistance(0, (positionX << FIXED_POINT_FRACTION_BITS) - building.getFixedPositionX(),
                                                                  0, (positionY << FIXED_POINT_FRACTION_BITS) - building.getFixedPositionY()) >>
                               FIXED_POINT_FRACTION_BITS;

                if (distance == 0) {
                    this.game.getEngineGame().getMovement().setDestination(building, positionX, positionY);
                    building.setDirectionTargetX(positionX);
                    building.setDirectionTargetY(positionY);
                    building.setPrimaryOrderState(PLACE_STATE_PLACING);
                } else {
                    this.game.getOrders().issue(building,
                                                ScrOrderIds.BUILDING_LIFTOFF,
                                                null,
                                                positionX, positionY,
                                                ScrUnitIds.NONE,
                                                false,
                                                instructionLimit);
                    this.game.getOrders().queueReplacingSameOrders(building, ScrOrderIds.PLACE_ADDON, null, positionX, positionY, ScrUnitIds.NONE);
                }
            } else {
                this.game.getOrders().issue(building,
                                            ScrOrderIds.BUILDING_LAND,
                                            null,
                                            positionX, positionY,
                                            ScrUnitIds.NONE,
                                            false,
                                            instructionLimit);
                this.game.getOrders().queueReplacingSameOrders(building, ScrOrderIds.PLACE_ADDON, null, positionX, positionY, ScrUnitIds.NONE);
            }
        }

        if (building.getPrimaryOrderState() != PLACE_STATE_PLACING || !this.game.getEngineGame().getMovement().isAtDestination(building)) {
            return;
        }

        int buildingType = building.getUnitId();

        positionX = positionX - units.getPlacementWidth(buildingType) / FOOTPRINT_CENTER_DIVISOR + units.getAddonHorizontalOffset(addonType) +
                    units.getPlacementWidth(addonType) / FOOTPRINT_CENTER_DIVISOR;
        positionY = positionY - units.getPlacementHeight(buildingType) / FOOTPRINT_CENTER_DIVISOR + units.getAddonVerticalOffset(addonType) +
                    units.getPlacementHeight(addonType) / FOOTPRINT_CENTER_DIVISOR;

        building.setOrderTargetX(positionX);
        building.setOrderTargetY(positionY);

        // The cost and supply test shows its error; a blocked site shows "You can't build there.".
        if (!this.game.getCostTests().testUnit(this.game, building.getOwnerIndex(), addonType, true, true)) {
            this.game.getOrders().finishOrder(building, instructionLimit);

            return;
        }

        if (!this.game.getUnitPlacement().canPlaceBuilding(building, building.getOwnerIndex(), addonType, positionX, positionY, true, false)) {
            this.game.getInterfaceLink().getCommandErrorEvents().raiseBlockedBuildLocation(this.game, building);
            this.game.getOrders().finishOrder(building, instructionLimit);

            return;
        }

        UnitCreationContext context = this.game.getUnitSystem().getUnitCreationContext();

        if (context == null) {
            throw Exceptions.newIllegalStateException("Add-on construction requires initialized creation context.");
        }

        Unit addon = this.game.getUnitStorageConstructor().create(context,
                                                                  this.game.getPlayerTable().requireUnitAccounting(),
                                                                  addonType,
                                                                  positionX, positionY,
                                                                  building.getOwnerIndex(),
                                                                  this.game.getUnitSystem().requireUnitPool().getCreationTag(building.getIndex()),
                                                                  instructionLimit);

        if (addon == null) {
            // The building's owner sees the pending creation message.
            this.game.getUnitStorageConstructor().notifyCreationFailure(building.getOwnerIndex());

            return;
        }

        this.game.getCostTests().spend(this.game, building.getOwnerIndex());
        this.game.getOrders().issueUntargeted(building, units.getReturnToIdleOrder(buildingType), instructionLimit);
        ScrOrderSystem.setSecondaryOrder(building, ScrOrderIds.BUILD_ADDON);
        building.setCurrentBuildUnit(addon);
        this.game.getOrders().issueUntargeted(addon, ScrOrderIds.INCOMPLETE_BUILDING, instructionLimit);
        this.game.getComputerUnits().notifyCreated(building, addon, instructionLimit);
        addon.setParentUnit(building);
    }

    /**
     * Advances the add-on under construction. A lost add-on clears the request; a lifted parent cancels an incomplete add-on; a completed add-on is
     * connected. Each ending gives a building with a computer record its {@code units.dat} Comp AI Idle order before the secondary order becomes
     * {@link ScrOrderIds#NOTHING}.
     *
     * @param building         the parent building
     * @param instructionLimit the cancellation, construction, add-on connection and order budget
     */
    public void buildAddon(@NotNull Unit building, int instructionLimit) {
        Unit addon = building.getCurrentBuildUnit();

        if (addon == null) {
            this.finishAddon(building, instructionLimit);

            return;
        }

        if (!UnitQueries.isGroundedBuilding(building) && (addon.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
            this.game.getUnitConstruction().cancelBuildingUnit(addon, instructionLimit);
            this.finishAddon(building, instructionLimit);

            return;
        }

        this.game.getUnitTerranConstruction().resumeBuilding(addon, instructionLimit);

        if ((addon.getStatus() & UnitStatusFlags.COMPLETED) != 0) {
            this.game.getInterfaceLink().getNotificationEvents().raise(NotificationEvents.Kind.WORK_DONE, building);
            this.connect(building, addon, instructionLimit);
            this.finishAddon(building, instructionLimit);
        }
    }

    /**
     * Finishes the {@link ScrOrderIds#BUILD_ADDON} secondary order: the add-on type becomes {@link ScrUnitIds#NONE}, a building with a computer
     * record takes its Comp AI Idle order, and the secondary order becomes {@link ScrOrderIds#NOTHING}.
     *
     * @param building         the parent building
     * @param instructionLimit the order budget
     * @throws NullPointerException if the world was built without game data
     */
    private void finishAddon(@NotNull Unit building, int instructionLimit) {
        building.setAddonBuildType(ScrUnitIds.NONE);

        if (building.getComputerRecord() != null) {
            this.game.getOrders().issueUntargeted(
                    building,
                    this.game.requireGameData().getUnitTypes().getComputerIdleOrder(building.getUnitId()),
                    instructionLimit
            );
        }

        ScrOrderSystem.setSecondaryOrder(building, ScrOrderIds.NOTHING);
    }

    /**
     * Connects the first neutral add-on at this building's add-on position.
     *
     * @param building         the completed or landed Terran building
     * @param instructionLimit the ownership and animation budget
     */
    public void findAndConnect(@NotNull Unit building, int instructionLimit) {
        Unit addon = this.findConnecting(building);

        if (addon != null) {
            this.connect(building, addon, instructionLimit);
        }
    }

    private @Nullable Unit findConnecting(@NotNull Unit building) {
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
        int buildingType = building.getUnitId();
        Sprite sprite = building.requireSprite();
        int placementWidth = units.getPlacementWidth(buildingType);
        int placementHeight = units.getPlacementHeight(buildingType);
        int left = sprite.getX() - placementWidth / FOOTPRINT_CENTER_DIVISOR - TerrainTileset.TILE_SIZE;
        int upper = sprite.getY() - placementHeight / FOOTPRINT_CENTER_DIVISOR - TerrainTileset.TILE_SIZE;
        int tileX = (sprite.getX() - placementWidth / FOOTPRINT_CENTER_DIVISOR) / TerrainTileset.TILE_SIZE;
        int tileY = (sprite.getY() - placementHeight / FOOTPRINT_CENTER_DIVISOR) / TerrainTileset.TILE_SIZE;
        UnitSpatialIndex spatial = this.game.getUnitSystem().requireUnitSpatialIndex();
        int count = spatial.collectPlacementCandidates(left,
                                                       upper,
                                                       left + placementWidth + CLEARANCE_BORDER_SIDES * TerrainTileset.TILE_SIZE,
                                                       upper + placementHeight + CLEARANCE_BORDER_SIDES * TerrainTileset.TILE_SIZE);

        for (int index = 0; index < count; index++) {
            Unit candidate = spatial.getPlacementCandidate(index);
            int candidateType = candidate.getUnitId();

            if ((units.getFlags(candidateType) & UnitTypeFlags.ADDON) == 0 || candidate.getOwnerIndex() != PlayerTable.NEUTRAL_PLAYER_ID ||
                !canAttach(buildingType, candidateType)) {
                continue;
            }

            Sprite candidateSprite = candidate.requireSprite();
            int candidateX = candidateSprite.getX() - units.getPlacementWidth(candidateType) / FOOTPRINT_CENTER_DIVISOR -
                             units.getAddonHorizontalOffset(candidateType);
            int candidateY = candidateSprite.getY() - units.getPlacementHeight(candidateType) / FOOTPRINT_CENTER_DIVISOR -
                             units.getAddonVerticalOffset(candidateType);

            if (candidateX / TerrainTileset.TILE_SIZE == tileX && candidateY / TerrainTileset.TILE_SIZE == tileY) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Attaches an add-on to a building. The add-on takes the building's owner and plays its landing animation.
     * <p>
     * Only a connecting building that is itself a Nuclear Silo gives its linked unit ({@link UnitQueries#getLinkedUnit}) its owner; the stored
     * missile of a connected Nuclear Silo add-on keeps its owner.
     *
     * @param building         the parent building
     * @param addon            the add-on
     * @param instructionLimit the ownership and animation budget
     * @throws IllegalStateException if the add-on has no sprite
     */
    public void connect(@NotNull Unit building, @NotNull Unit addon, int instructionLimit) {
        building.setAddon(addon);
        this.game.getUnitOwnership().changeOwner(addon, building.getOwnerIndex(), false, instructionLimit);
        this.game.getIscriptExecutor().runSpriteAnimation(addon.requireSprite(), addon, IscriptExecutor.ANIMATION_LANDING, 1, instructionLimit);

        Unit linkedUnit = UnitQueries.getLinkedUnit(building);

        if (building.getUnitId() == ScrUnitIds.TERRAN_NUCLEAR_SILO && linkedUnit != null) {
            this.game.getUnitOwnership().setOwner(linkedUnit, building.getOwnerIndex(), false, instructionLimit);
        }
    }

    /**
     * Leaves the attached add-on to the neutral player and plays its lift-off animation.
     *
     * @param building         the parent building lifting off or dying
     * @param instructionLimit the ownership and animation budget
     * @throws IllegalStateException if the add-on has no sprite
     */
    public void abandon(@NotNull Unit building, int instructionLimit) {
        Unit addon = building.getAddon();

        if (addon == null) {
            return;
        }

        this.game.getUnitOwnership().makeNeutral(addon, instructionLimit);
        building.setAddon(null);
        // The add-on-detached notification comes after the add-on became neutral.
        this.game.getInterfaceLink().getNotificationEvents().raise(NotificationEvents.Kind.ADDON_DETACHED, addon);
        this.game.getIscriptExecutor().runSpriteAnimation(addon.requireSprite(), addon, IscriptExecutor.ANIMATION_LIFT_OFF, 1, instructionLimit);
    }
}
