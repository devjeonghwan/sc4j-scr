package org.fogofwarlabs.sc4j.scr.game.resource;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.entity.column.ColumnKey;
import org.fogofwarlabs.sc4j.engine.entity.column.ColumnReset;
import org.fogofwarlabs.sc4j.engine.entity.column.IntColumn;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.movement.MovementStates;
import org.fogofwarlabs.sc4j.scr.game.movement.UnitUnstacking;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.order.UnitOrderEligibility;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitInitializer;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitPositioning;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Executes worker resource orders against retained movement targets and pooled orders.
 * <p>
 * The resource state of the native unit union is registered as unit pool columns ({@link #RESOURCE_COLUMNS}): the
 * resource amount, the resource area, the computer claim byte and the being-gathered byte. The columns keep their values
 * when a slot is reused, as the union fields do; unit creation and the unit reset methods set them.
 * <p>
 * The worker gathering state of the native unit union is registered as unit pool columns ({@link #WORKER_COLUMNS}): the
 * target resource position, the gathering byte and the carried amount, with the same reuse rule as the resource state.
 */
public final class UnitHarvesting {
    /**
     * The horizontal position of the worker's target resource ({@link Unit#getTargetResourceX()}).
     */
    public static final @NotNull ColumnKey<IntColumn> TARGET_RESOURCE_X_COLUMN = ColumnKey.ofInt("targetResourceX", 0, ColumnReset.KEEP);
    /**
     * The vertical position of the worker's target resource ({@link Unit#getTargetResourceY()}).
     */
    public static final @NotNull ColumnKey<IntColumn> TARGET_RESOURCE_Y_COLUMN = ColumnKey.ofInt("targetResourceY", 0, ColumnReset.KEEP);
    /**
     * The BYTE that is nonzero while the worker gathers ({@link Unit#getGatheringResourceByte()}).
     */
    public static final @NotNull ColumnKey<IntColumn> GATHERING_COLUMN = ColumnKey.ofInt("gatheringResource", 0, ColumnReset.KEEP);
    /**
     * The resource amount the worker carries ({@link Unit#getCarriedResourceAmount()}).
     */
    public static final @NotNull ColumnKey<IntColumn> CARRIED_AMOUNT_COLUMN = ColumnKey.ofInt("carriedResourceAmount", 0, ColumnReset.KEEP);
    /**
     * The unit pool columns of the worker gathering state, in registration order.
     */
    public static final @NotNull List<@NotNull ColumnKey<?>> WORKER_COLUMNS = List.of(
            TARGET_RESOURCE_X_COLUMN,
            TARGET_RESOURCE_Y_COLUMN,
            GATHERING_COLUMN,
            CARRIED_AMOUNT_COLUMN
    );

    /**
     * The resource amount WORD ({@link Unit#getResources()}).
     */
    public static final @NotNull ColumnKey<IntColumn> RESOURCE_AMOUNT_COLUMN = ColumnKey.ofInt("resources", 0, ColumnReset.KEEP);
    /**
     * The resource area BYTE ({@link Unit#getResourceAreaIndex()}).
     */
    public static final @NotNull ColumnKey<IntColumn> RESOURCE_AREA_COLUMN = ColumnKey.ofInt("resourceAreaIndex", 0, ColumnReset.KEEP);
    /**
     * The BYTE set when a computer town assigns a worker to the resource and cleared by town merging
     * ({@link Unit#getResourceClaimByte()}).
     */
    public static final @NotNull ColumnKey<IntColumn> RESOURCE_CLAIM_COLUMN = ColumnKey.ofInt("resourceClaimedByComputer", 0, ColumnReset.KEEP);
    /**
     * The BYTE that is nonzero while a worker gathers from the resource ({@link Unit#getResourceBeingGatheredByte()}).
     */
    public static final @NotNull ColumnKey<IntColumn> RESOURCE_BEING_GATHERED_COLUMN = ColumnKey.ofInt("resourceBeingGathered", 0, ColumnReset.KEEP);
    /**
     * The unit pool columns of the resource state, in registration order.
     */
    public static final @NotNull List<@NotNull ColumnKey<?>> RESOURCE_COLUMNS = List.of(RESOURCE_AMOUNT_COLUMN, RESOURCE_AREA_COLUMN,
                                                                                        RESOURCE_CLAIM_COLUMN, RESOURCE_BEING_GATHERED_COLUMN);

    /**
     * Order state: waiting in the resource's gathering queue.
     */
    public static final int STATE_WAITING = 2;

    /**
     * Order state: moving to the resource or the depot.
     */
    private static final int STATE_MOVING = 1;
    /**
     * Order state: reaching the resource.
     */
    private static final int STATE_ARRIVING = 3;
    /**
     * Order state: working at the resource (inside the gas structure or mining).
     */
    private static final int STATE_WORKING = 5;

    /**
     * The range in pixels at which a worker reaches its mineral field.
     */
    private static final int MINERAL_REACH = 10;

    /**
     * The frames a worker stays inside a gas structure.
     */
    private static final int GAS_HARVEST_FRAMES = 37;
    /**
     * The frames before a worker retries to leave a gas structure.
     */
    private static final int GAS_EXIT_RETRY_FRAMES = 18;
    /**
     * The frames of one mining cycle.
     */
    private static final int MINING_FRAMES = 75;
    /**
     * The frames a worker without a depot waits before it searches again.
     */
    private static final int DEPOT_RETRY_FRAMES = 75;

    /**
     * The distance in pixels of a gas structure exit toward the depot.
     */
    private static final int GAS_EXIT_DISTANCE = 32;
    /**
     * The fraction bits of a fixed-point direction vector.
     */
    private static final int FIXED_POINT_SHIFT = 8;
    /**
     * The shifts of the X and Y WORDs of a clipped segment end.
     */
    private static final int CLIPPED_POSITION_X_SHIFT = Integer.SIZE;
    private static final int CLIPPED_POSITION_Y_SHIFT = Integer.SIZE + Short.SIZE;
    /**
     * The half size in pixels of the fallback box a worker leaves a gas structure in (below and left).
     */
    private static final int GAS_EXIT_BOX_LOW = 128;
    /**
     * The half size in pixels of the fallback box a worker leaves a gas structure in (above and right).
     */
    private static final int GAS_EXIT_BOX_HIGH = 127;
    /**
     * The distance in pixels of the mining target point ahead of the worker.
     */
    private static final int MINING_TARGET_DISTANCE = 20;

    /**
     * The resources of one load.
     */
    private static final int RESOURCE_LOAD = 8;
    /**
     * The gas of one load from a depleted gas structure.
     */
    private static final int DEPLETED_GAS_LOAD = 2;
    /**
     * The remaining minerals below which a mineral field shows its smallest frame set.
     */
    private static final int MINERALS_LOW = 250;
    /**
     * The remaining minerals below which a mineral field shows its second frame set.
     */
    private static final int MINERALS_MEDIUM = 500;
    /**
     * The remaining minerals below which a mineral field shows its third frame set.
     */
    private static final int MINERALS_HIGH = 750;

    /**
     * The larva sides of a hatchery: left, top, right and bottom.
     */
    private static final int LARVA_SIDES = 4;
    /**
     * The count added to the side a worker returns on.
     */
    private static final int LARVA_SIDE_BONUS = 25;
    /**
     * The side count from which no bonus is added.
     */
    private static final int LARVA_SIDE_LIMIT = 100;

    /**
     * Removes a waiting worker from a resource's gathering queue and clears its queue links.
     *
     * @param resource the resource whose queue holds the worker
     * @param worker   the waiting worker
     */
    public static void unlinkWaitingWorker(@NotNull Unit resource, @NotNull Unit worker) {
        Unit previous = worker.getPreviousGatheringWorker();
        Unit next = worker.getNextGatheringWorker();

        if (previous == null) {
            resource.setFirstGatheringWorker(next);
        } else {
            previous.setNextGatheringWorker(next);
        }

        if (next == null) {
            resource.setLastGatheringWorker(previous);
        } else {
            next.setPreviousGatheringWorker(previous);
        }

        worker.setPreviousGatheringWorker(null);
        worker.setNextGatheringWorker(null);
    }


    private final @NotNull Game game;

    /**
     * Creates a {@link UnitHarvesting} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public UnitHarvesting(@NotNull Game game) {
        this.game = game;
    }


    /**
     * Checks worker, gas structure, completion, and ownership eligibility.
     *
     * @param worker the gathering worker
     * @param target the gas structure
     * @return {@code true} if the worker may gather gas at the target, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    public boolean canGatherGas(@NotNull Unit worker, @NotNull Unit target) {
        int type = target.getUnitId();

        return (this.game.requireGameData().getUnitTypes().getFlags(worker.getUnitId()) & UnitTypeFlags.WORKER) != 0 &&
               this.game.requireGameData().getUnitTypes().isGasStructure(type) && (target.getStatus() & UnitStatusFlags.COMPLETED) != 0 &&
               target.getOwnerIndex() == worker.getOwnerIndex();
    }


    /**
     * Moves toward a selected or searched mineral field and enters its gathering queue.
     *
     * @param worker           the gathering worker
     * @param instructionLimit the order transition animation budget
     * @throws IllegalStateException if the target mineral field or the worker has no sprite
     */
    public void moveToMinerals(@NotNull Unit worker, int instructionLimit) {
        if (worker.getCarryingFlags() != 0 && (worker.getCarryingFlags() & CarryingFlags.RESOURCES) == 0) {
            this.stopAndFinishOrder(worker, instructionLimit);

            return;
        }

        this.startGathering(worker);

        Unit target = worker.getOrderTargetUnit();

        if (target == null || !this.game.requireGameData().getUnitTypes().isMineralField(target.getUnitId())) {
            Sprite sprite = worker.requireSprite();
            int positionX = sprite.getX();
            int positionY = sprite.getY();

            if (worker.getTargetResourceX() != 0 || worker.getTargetResourceY() != 0) {
                positionX = worker.getTargetResourceX();
                positionY = worker.getTargetResourceY();
                worker.setTargetResource(worker.getTargetResourceUnit(), 0, 0);
            }

            target = this.game.getComputerResources().findNearbyMineral(worker, positionX, positionY);

            if (target == null) {
                this.stopAndFinishOrder(worker, instructionLimit);

                return;
            }

            worker.setOrderTargetUnit(target);
            worker.setOrderTargetX(target.requireSprite().getX());
            worker.setOrderTargetY(target.requireSprite().getY());
            worker.setPrimaryOrderState(0);
        }

        if (worker.getPrimaryOrderState() == 0) {
            if (!this.game.getUnitTargetMovement().moveToTargetReset(worker, target)) {
                return;
            }

            worker.setTargetResource(target, worker.getOrderTargetX(), worker.getOrderTargetY());
            worker.setPrimaryOrderState(STATE_MOVING);

            return;
        }

        if (worker.getPrimaryOrderState() == STATE_MOVING) {
            if (!this.game.getUnitTargetMovement().isTargetInRange(worker, target, MINERAL_REACH) &&
                !this.game.getEngineGame().getMovement().isAtDestination(worker)) {
                return;
            }

            if (target.isResourceBeingGathered()) {
                Sprite sprite = worker.requireSprite();
                Unit replacement = this.game.getComputerResources().findMineral(worker, sprite.getX(), sprite.getY(), false);

                if (replacement != null) {
                    this.moveToReplacement(worker, replacement);

                    return;
                }
            }

            worker.setPrimaryOrderState(STATE_ARRIVING);
        }

        if (worker.getPrimaryOrderState() == STATE_ARRIVING && this.game.getEngineGame().getMovement().isAtDestination(worker)) {
            if ((worker.getStatus() & UnitStatusFlags.IMMOVABLE) != 0) {
                Sprite sprite = worker.requireSprite();
                Unit replacement = this.game.getComputerResources().findNearbyMineral(worker, sprite.getX(), sprite.getY());

                if (replacement == null) {
                    this.game.getOrders().finishOrder(worker, instructionLimit);
                } else {
                    this.moveToReplacement(worker, replacement);
                }

                return;
            }

            if ((worker.getCarryingFlags() & CarryingFlags.MINERALS) != 0) {
                this.game.getOrders().issueUntargeted(worker, ScrOrderIds.RETURN_MINERALS, instructionLimit);
            } else {
                worker.setDirectionTargetX(worker.getOrderTargetX());
                worker.setDirectionTargetY(worker.getOrderTargetY());

                Sprite targetSprite = target.requireSprite();

                this.game.getOrders().queueFront(worker, ScrOrderIds.WAIT_FOR_MINERALS,
                                                 target, targetSprite.getX(), targetSprite.getY(), ScrUnitIds.NONE);
                this.game.getOrders().finishOrder(worker, instructionLimit);
            }
        }
    }

    /**
     * Keeps collision reset behind queued harvesting, or restores ordinary collision and movement.
     *
     * @param worker           the worker completing a harvesting interruption
     * @param instructionLimit the order transition animation budget
     * @throws IllegalStateException         if the order pool is missing or the worker has no sprite
     * @throws UnsupportedOperationException if spatial collision state or the path pool is missing
     * @throws NullPointerException          if the world was built without game data
     */
    public void resetHarvestCollision(@NotNull Unit worker, int instructionLimit) {
        OrderPool pool = this.game.getOrderPool();

        if (pool == null) {
            throw Exceptions.newIllegalStateException("Harvest collision reset requires the order pool.");
        }

        OrderPool.Order next = worker.getFirstOrder();

        if (next != null) {
            int order = next.getOrderId();

            if (order == ScrOrderIds.HARVEST1 ||
                order >= ScrOrderIds.MOVE_TO_GAS && order <= ScrOrderIds.HARVEST_GAS ||
                order >= ScrOrderIds.MOVE_TO_MINERALS && order <= ScrOrderIds.GATHER_WAIT_INTERRUPTED) {
                this.game.getOrders().queueReplacingSameOrders(worker,
                                                               ScrOrderIds.RESET_HARVEST_COLLISION,
                                                               null,
                                                               0, 0,
                                                               ScrUnitIds.NONE);

                this.game.getOrders().completeOrder(worker, instructionLimit);

                return;
            }
        }

        worker.setStatus(worker.getStatus() & ~(UnitStatusFlags.NO_COLLIDE | UnitStatusFlags.GATHERING));

        UnitSpatialIndex spatial = this.game.getUnitSystem().getUnitSpatialIndex();

        if (spatial == null) {
            throw Exceptions.newUnsupportedOperationException("Harvest collision reset requires spatial collision state.");
        }

        spatial.notifyCollision(worker, this.game.requireGameData().getUnitTypes());

        if (worker.getFirstOrder() == null) {
            int idle = this.game.requireGameData().getUnitTypes().getReturnToIdleOrder(worker.getUnitId());

            this.game.getOrders().queueReplacingSameOrders(worker, idle, null, 0, 0, ScrUnitIds.NONE);
        }

        if (worker.getPathIndex() >= 0) {
            PathPool paths = this.game.getPathSystem().getPathPool();

            if (paths == null) {
                throw Exceptions.newUnsupportedOperationException("Harvest collision reset requires the path pool.");
            }

            paths.release(worker.getPathIndex());
            worker.setPathIndex(-1);
        }

        worker.setMovementState(MovementStates.INIT);

        Sprite sprite = worker.requireSprite();

        UnitInitializer.updateGroundElevationPathFlag(worker, sprite.getElevationLevel());
        this.game.getOrders().completeOrder(worker, instructionLimit);
    }

    /**
     * Starts mining an unoccupied resource and retains the interruption order.
     * <p>
     * An invalid target issues a new mineral-search order.
     *
     * @param worker           the waiting worker
     * @param instructionLimit the order transition animation budget
     */
    public void waitForMinerals(@NotNull Unit worker, int instructionLimit) {
        Unit target = worker.getOrderTargetUnit();

        if (target == null || !this.game.requireGameData().getUnitTypes().isMineralField(target.getUnitId())) {
            this.game.getOrders().issueUntargeted(worker, ScrOrderIds.MOVE_TO_MINERALS, instructionLimit);

            return;
        }

        this.waitForResource(worker, target, instructionLimit);
    }

    /**
     * Moves a worker to its gas structure, retaining the shared resource interruption orders.
     *
     * @param worker           the gathering worker
     * @param instructionLimit the order transition animation budget
     * @throws IllegalStateException if the target has no sprite
     */
    public void moveToGas(@NotNull Unit worker, int instructionLimit) {
        if (worker.getCarryingFlags() != 0 && (worker.getCarryingFlags() & CarryingFlags.RESOURCES) == 0) {
            this.stopAndFinishOrder(worker, instructionLimit);

            return;
        }

        this.startGathering(worker);

        Unit target = worker.getOrderTargetUnit();

        if (worker.getPrimaryOrderState() == 0) {
            if (target == null) {
                this.stopAndFinishOrder(worker, instructionLimit);
            } else if (this.game.requireGameData().getUnitTypes().isMineralField(target.getUnitId())) {
                this.game.getOrders().queueFront(worker, ScrOrderIds.MOVE_TO_MINERALS,
                                                 target, worker.getOrderTargetX(), worker.getOrderTargetY(), ScrUnitIds.NONE);
                this.game.getOrders().finishOrder(worker, instructionLimit);
            } else {
                if (!this.game.getUnitTargetMovement().moveToTargetReset(worker, target)) {
                    return;
                }

                worker.setTargetResource(target, worker.getOrderTargetX(), worker.getOrderTargetY());
                worker.setPrimaryOrderState(STATE_ARRIVING);
            }
        } else if (worker.getPrimaryOrderState() == STATE_ARRIVING && this.game.getEngineGame().getMovement().isAtDestination(worker)) {
            if (target == null || !this.canGatherGas(worker, target)) {
                this.stopAndFinishOrder(worker, instructionLimit);
            } else if ((worker.getStatus() & UnitStatusFlags.IMMOVABLE) != 0) {
                worker.setPrimaryOrderState(0);
            } else if ((worker.getCarryingFlags() & CarryingFlags.GAS) != 0) {
                this.game.getOrders().issueUntargeted(worker, ScrOrderIds.RETURN_GAS, instructionLimit);
            } else {
                Sprite sprite = target.requireSprite();

                this.game.getOrders().queueFront(worker, ScrOrderIds.WAIT_FOR_GAS,
                                                 target, sprite.getX(), sprite.getY(), ScrUnitIds.NONE);
                this.game.getOrders().finishOrder(worker, instructionLimit);
            }
        }
    }

    /**
     * Joins the gas structure's common FIFO gathering queue.
     *
     * @param worker           the waiting worker
     * @param instructionLimit the order transition animation budget
     */
    public void waitForGas(@NotNull Unit worker, int instructionLimit) {
        Unit target = worker.getOrderTargetUnit();

        if (target != null &&
            this.canGatherGas(worker, target) &&
            (worker.getCarryingFlags() == 0 || (worker.getCarryingFlags() & CarryingFlags.RESOURCES) != 0)) {
            this.waitForResource(worker, target, instructionLimit);
        } else {
            this.stopAndFinishOrder(worker, instructionLimit);
        }
    }

    /**
     * Hides a gas gatherer, waits for extraction, then searches the actual structure exit.
     * <p>
     * Initial hiding and the harvest timer can return before exit placement; failed placement sets a retry timer before returning.
     *
     * @param worker           the gas gatherer
     * @param instructionLimit the order and carrying-image instruction budget
     * @throws NullPointerException          if the world was built without game data
     * @throws IllegalStateException         if the worker, the target or the depot has no sprite
     * @throws UnsupportedOperationException if the unit creation context is not established
     */
    public void harvestGas(@NotNull Unit worker, int instructionLimit) {
        Unit target = worker.getOrderTargetUnit();
        Sprite sprite = worker.requireSprite();

        if ((sprite.getFlags() & Sprite.FLAG_HIDDEN) == 0 &&
            (target == null ||
             !this.canGatherGas(worker, target) ||
             worker.getCarryingFlags() != 0 && (worker.getCarryingFlags() & CarryingFlags.RESOURCES) == 0)) {
            this.game.getEngineGame().getMovement().stop(worker);
            this.game.getOrders().completeOrder(worker, instructionLimit);

            if (worker.getGatherTarget() != null) {
                this.releaseResource(worker, worker.getGatherTarget(), instructionLimit);
            }

            return;
        }

        this.startGathering(worker);

        if (worker.getPrimaryOrderState() == 0) {
            this.game.getUnitLifecycle().hide(worker, false);
            worker.setPrimaryOrderState(STATE_WORKING);
            worker.setOrderTimer(GAS_HARVEST_FRAMES);

            return;
        }

        if (worker.getPrimaryOrderState() != STATE_WORKING || worker.getOrderTimer() != 0) {
            return;
        }

        Unit depot = this.game.getUnitResourceSearch().findDepot(worker);
        int horizontal = sprite.getX();
        int vertical = sprite.getY();
        int heading = worker.getHeading();
        int[] position = new int[UnitPositioning.POSITION_COORDINATES];
        boolean placed = false;

        if (depot != null && target != null) {
            Sprite targetSprite = target.requireSprite();
            Sprite depotSprite = depot.requireSprite();

            heading = FlingyMotionUtils.calculateDirection(depotSprite.getX() - targetSprite.getX(), depotSprite.getY() - targetSprite.getY());

            long offset = FlingyMotionUtils.calculateDirectionVector(heading - FlingyMotionUtils.DIRECTION_QUARTER_TURN,
                                                                     GAS_EXIT_DISTANCE << FIXED_POINT_SHIFT);
            int offsetX = (int) offset >> FIXED_POINT_SHIFT;
            int offsetY = (int) (offset >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT) >> FIXED_POINT_SHIFT;
            ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
            int left = targetSprite.getX() - units.getDimensionLeft(target.getUnitId()) - units.getDimensionRight(worker.getUnitId()) - 1;
            int upper = targetSprite.getY() - units.getDimensionUp(target.getUnitId()) - units.getDimensionDown(worker.getUnitId()) - 1;
            int right = targetSprite.getX() + units.getDimensionRight(target.getUnitId()) + units.getDimensionLeft(worker.getUnitId()) + 1;
            int lower = targetSprite.getY() + units.getDimensionDown(target.getUnitId()) + units.getDimensionUp(worker.getUnitId()) + 1;

            horizontal = depotSprite.getX() + offsetX;
            vertical   = depotSprite.getY() + offsetY;

            long clipped = UnitUnstacking.clipSegment(
                    UnitUnstacking.packRectangle(targetSprite.getX() + offsetX, targetSprite.getY() + offsetY, horizontal, vertical),
                    UnitUnstacking.packRectangle(left, upper, right, lower)
            );

            if (clipped != Long.MIN_VALUE) {
                horizontal = (short) (clipped >>> CLIPPED_POSITION_X_SHIFT);
                vertical   = (short) (clipped >>> CLIPPED_POSITION_Y_SHIFT);
            }

            placed = this.game.getUnitPositioning().find(worker, horizontal, vertical, left, upper, right, lower, true, position);
        }

        if (!placed) {
            placed = this.game.getUnitPositioning().find(worker,
                                                         horizontal, vertical,
                                                         horizontal - GAS_EXIT_BOX_LOW, vertical - GAS_EXIT_BOX_LOW,
                                                         horizontal + GAS_EXIT_BOX_HIGH, vertical + GAS_EXIT_BOX_HIGH,
                                                         true,
                                                         position);
        }

        if (!placed) {
            worker.setOrderTimer(GAS_EXIT_RETRY_FRAMES);

            return;
        }

        // Reset the next, current and facing directions before moving out of the gas structure.
        worker.resetDirection(this.game, heading);
        this.game.getUnitPositioning().move(worker, position[UnitPositioning.X_INDEX], position[UnitPositioning.Y_INDEX]);
        this.game.getUnitVisibility().show(worker, this.game.getUnitSystem().requireUnitCreationContext().isFoliageEnabled());

        if (target != null) {
            this.gatherResources(worker, target, instructionLimit);
        }

        if (worker.getGatherTarget() != null) {
            this.releaseResource(worker, worker.getGatherTarget(), instructionLimit);
        }

        this.removeOrder(worker, ScrOrderIds.RESET_HARVEST_COLLISION);

        if (worker.getFirstOrder() == null) {
            int nextOrder = (worker.getCarryingFlags() & CarryingFlags.GAS) != 0
                            ? ScrOrderIds.RETURN_GAS
                            : this.game.requireGameData().getUnitTypes().getReturnToIdleOrder(worker.getUnitId());

            this.game.getOrders().queueReplacingSameOrders(worker, nextOrder, null, 0, 0, ScrUnitIds.NONE);
        }

        this.game.getOrders().queueFront(worker, ScrOrderIds.RESET_HARVEST_COLLISION, null, 0, 0, ScrUnitIds.NONE);
        this.game.getOrders().completeOrder(worker, instructionLimit);
    }

    /**
     * Advances mineral mining facing and its work timer.
     *
     * @param worker           the mining worker
     * @param instructionLimit the work animation instruction budget
     * @throws IllegalStateException if the worker has no sprite
     */
    public void mineMinerals(@NotNull Unit worker, int instructionLimit) {
        Unit target = worker.getOrderTargetUnit();

        if (target == null || !this.game.requireGameData().getUnitTypes().isMineralField(target.getUnitId())) {
            if (worker.isGatheringResource()) {
                this.releaseResource(worker, worker.getGatherTarget(), instructionLimit);
            }

            this.removeOrder(worker, ScrOrderIds.GATHERING_INTERRUPTED);
            this.game.getOrders().issueUntargeted(worker, ScrOrderIds.MOVE_TO_MINERALS, instructionLimit);

            return;
        }

        this.startGathering(worker);

        if (worker.getPrimaryOrderState() == 0) {
            Sprite sprite = worker.requireSprite();
            int horizontal = worker.getDirectionTargetX() - sprite.getX();
            int vertical = worker.getDirectionTargetY() - sprite.getY();

            if ((horizontal != 0 || vertical != 0) && FlingyMotionUtils.calculateDirection(horizontal, vertical) != worker.getHeading()) {
                return;
            }

            long offset = FlingyMotionUtils.calculateDirectionVector(worker.getHeading(), MINING_TARGET_DISTANCE << FIXED_POINT_SHIFT);

            worker.setOrderTargetX(sprite.getX() + ((int) offset >> FIXED_POINT_SHIFT));
            worker.setOrderTargetY(sprite.getY() + ((int) (offset >>> FlingyMotionUtils.DIRECTION_VECTOR_VERTICAL_SHIFT) >> FIXED_POINT_SHIFT));
            this.game.getIscriptExecutor().runSpriteAnimation(sprite, worker, IscriptExecutor.ANIMATION_ALMOST_BUILT, 1, instructionLimit);
            worker.setOrderTimer(MINING_FRAMES);
            worker.setPrimaryOrderState(STATE_WORKING);
        } else if (worker.getPrimaryOrderState() == STATE_WORKING && worker.getOrderTimer() == 0) {
            this.game.getIscriptExecutor().runSpriteAnimation(worker.requireSprite(),
                                                              worker,
                                                              IscriptExecutor.ANIMATION_GROUND_ATTACK_TO_IDLE,
                                                              1,
                                                              instructionLimit);
            this.gatherResources(worker, target, instructionLimit);
            this.releaseResource(worker, target, instructionLimit);
            this.removeOrder(worker, ScrOrderIds.GATHERING_INTERRUPTED);

            int order = (worker.getCarryingFlags() & CarryingFlags.MINERALS) != 0
                        ? ScrOrderIds.RETURN_MINERALS
                        : ScrOrderIds.MOVE_TO_MINERALS;

            this.game.getOrders().issueUntargeted(worker, order, instructionLimit);
        }
    }

    /**
     * Releases a worker's claim on a resource and lets the first eligible waiting worker of its gathering queue begin gathering.
     * <p>
     * The worker's claim clears before the null-resource return.
     *
     * @param worker           the releasing worker
     * @param resource         the claimed resource, or {@code null} to only clear the worker's claim
     * @param instructionLimit the order transition animation budget
     */
    public void releaseResource(@NotNull Unit worker, @Nullable Unit resource, int instructionLimit) {
        worker.setGatheringResource(false);
        worker.setGatherTarget(null);

        if (resource == null) {
            return;
        }

        resource.setResourceBeingGathered(false);

        for (Unit waiting = resource.getLastGatheringWorker(); waiting != null; waiting = waiting.getPreviousGatheringWorker()) {
            if (UnitOrderEligibility.isDisabled(waiting)) {
                continue;
            }

            int order = waiting.getPrimaryOrder();

            if (order != ScrOrderIds.WAIT_FOR_GAS && order != ScrOrderIds.WAIT_FOR_MINERALS) {
                continue;
            }

            waiting.setGatherTarget(null);
            unlinkWaitingWorker(resource, waiting);
            this.removeOrder(waiting, ScrOrderIds.GATHER_WAIT_INTERRUPTED);
            this.beginGathering(waiting, resource, instructionLimit);

            return;
        }
    }

    /**
     * Releases an interrupted mining claim or removes a waiting worker before the next order.
     *
     * @param worker           the interrupted worker
     * @param instructionLimit the order transition animation budget
     */
    public void interruptGathering(@NotNull Unit worker, int instructionLimit) {
        if (worker.getPrimaryOrder() == ScrOrderIds.GATHERING_INTERRUPTED) {
            if (worker.isGatheringResource()) {
                this.releaseResource(worker, worker.getGatherTarget(), instructionLimit);
            }
        } else if (worker.getGatherTarget() != null) {
            unlinkWaitingWorker(worker.getGatherTarget(), worker);
            worker.setGatherTarget(null);
        }

        this.game.getOrders().completeOrder(worker, instructionLimit);
    }

    /**
     * Returns carried resources at an active depot and resumes the saved resource target or computer order.
     * <p>
     * The follow-up order is queued and finished before carrying-image removal and player resource accounting.
     * <p>
     * The carrying-image storage check follows queueing and order completion. Resource credit and the carrying-state reset follow successful
     * image removal.
     *
     * @param worker           the returning worker
     * @param instructionLimit the order transition animation budget
     * @throws NullPointerException  if the world was built without game data
     * @throws IllegalStateException if the target or the worker has no sprite
     */
    public void returnMinerals(@NotNull Unit worker, int instructionLimit) {
        if ((worker.getCarryingFlags() & CarryingFlags.RESOURCES) == 0) {
            this.stopAndFinishOrder(worker, instructionLimit);

            return;
        }

        this.startGathering(worker);

        Unit target = worker.getOrderTargetUnit();

        if (target == null || !this.game.getUnitResourceSearch().isActiveDepot(target)) {
            target = null;
            worker.setPrimaryOrderState(0);
        }

        if (worker.getPrimaryOrderState() == 0) {
            if (target == null) {
                target = this.game.getUnitResourceSearch().findDepot(worker);
            }

            if (target == null) {
                // Without a depot a computer worker returns to its computer order; any other worker waits.
                if (worker.getComputerRecord() != null) {
                    this.game.getOrders().issueUntargeted(worker, ScrOrderIds.COMPUTER_AI, instructionLimit);
                } else {
                    worker.setPrimaryOrderCountdown(DEPOT_RETRY_FRAMES);
                }
            } else {
                worker.setOrderTargetUnit(target);

                if (!this.game.getUnitTargetMovement().moveToTargetReset(worker, target)) {
                    return;
                }

                worker.setPrimaryOrderState(STATE_MOVING);
            }

            return;
        }

        if (worker.getPrimaryOrderState() != STATE_MOVING || !this.game.getEngineGame().getMovement().isAtDestination(worker)) {
            return;
        }

        if ((worker.getStatus() & UnitStatusFlags.IMMOVABLE) != 0) {
            worker.setOrderTargetUnit(null);
            worker.setPrimaryOrderState(0);

            return;
        }

        Unit resource = worker.getTargetResourceUnit();
        Sprite resourceSprite = resource == null ? null : resource.getSpriteOrNull();
        // A worker with a computer record continues with its computer order.
        int nextOrder = worker.getComputerRecord() != null
                        ? ScrOrderIds.COMPUTER_AI
                        : (worker.getCarryingFlags() & CarryingFlags.GAS) != 0 ? ScrOrderIds.MOVE_TO_GAS : ScrOrderIds.MOVE_TO_MINERALS;

        this.game.getOrders().queueFront(worker, nextOrder,
                                         resource,
                                         resourceSprite == null ? 0 : resourceSprite.getX(),
                                         resourceSprite == null ? 0 : resourceSprite.getY(),
                                         ScrUnitIds.NONE);
        this.game.getOrders().finishOrder(worker, instructionLimit);
        this.game.getIscriptExecutor().removeCarryImages(worker);

        Player player = this.game.getPlayerTable().requirePlayer(worker.getOwnerIndex());
        int amount = worker.getCarriedResourceAmount();

        if ((worker.getCarryingFlags() & CarryingFlags.MINERALS) != 0) {
            player.setMinerals(player.getMinerals() + amount);
            player.setGatheredMinerals(player.getGatheredMinerals() + amount);
            this.game.getComputerUnits().notifyResourcesReturned(worker.getOwnerIndex(), amount, 0);
        } else if ((worker.getCarryingFlags() & CarryingFlags.GAS) != 0) {
            player.setGas(player.getGas() + amount);
            player.setGatheredGas(player.getGatheredGas() + amount);
            this.game.getComputerUnits().notifyResourcesReturned(worker.getOwnerIndex(), 0, amount);
        }

        worker.setCarryingFlags(0);
        worker.setCarriedResourceAmount(0);

        if (target.getUnitId() >= ScrUnitIds.ZERG_HATCHERY && target.getUnitId() <= ScrUnitIds.ZERG_HIVE) {
            Sprite depotSprite = target.requireSprite();
            Sprite workerSprite = worker.requireSprite();
            int upper = depotSprite.getY() - this.game.requireGameData().getUnitTypes().getDimensionUp(target.getUnitId());
            int lower = depotSprite.getY() + this.game.requireGameData().getUnitTypes().getDimensionDown(target.getUnitId());
            int left = depotSprite.getX() - this.game.requireGameData().getUnitTypes().getDimensionLeft(target.getUnitId());
            int side = workerSprite.getY() < upper
                       ? Unit.LARVA_SIDE_UPPER
                       : workerSprite.getY() > lower
                         ? Unit.LARVA_SIDE_LOWER
                         : workerSprite.getX() < left
                           ? Unit.LARVA_SIDE_LEFT
                           : Unit.LARVA_SIDE_RIGHT;

            for (int index = 0; index < LARVA_SIDES; index++) {
                int count = target.getLarvaSideCount(index);

                if (count != 0) {
                    count--;
                }

                if (index == side && count < LARVA_SIDE_LIMIT) {
                    count += LARVA_SIDE_BONUS;
                }

                switch (index) {
                    case Unit.LARVA_SIDE_LEFT -> target.setResources(count);
                    case Unit.LARVA_SIDE_UPPER -> target.setSecondLarvaSideCount(count);
                    case Unit.LARVA_SIDE_RIGHT -> target.setThirdLarvaSideCount(count);
                    case Unit.LARVA_SIDE_LOWER -> target.setFourthLarvaSideCount(count);
                }
            }
        }
    }

    /**
     * Takes one load from a resource into a worker: a mineral field or supported gas structure gives {@link #RESOURCE_LOAD}, a nearly depleted one
     * what is left (minerals) or a small load (gas). The resource updates its remaining amount, frame set and depletion; the worker gets its carried
     * resource and carrying image.
     *
     * @param worker           the gathering worker
     * @param resource         the resource
     * @param instructionLimit the animation instruction budget
     * @throws IllegalStateException if the resource has no sprite
     */
    void gatherResources(@NotNull Unit worker, @NotNull Unit resource, int instructionLimit) {
        int type = resource.getUnitId();
        boolean minerals = this.game.requireGameData().getUnitTypes().isMineralField(type);

        if (!minerals && !this.game.requireGameData().getUnitTypes().isGasStructure(type)) {
            return;
        }

        int available = resource.getResources();
        boolean small = available < RESOURCE_LOAD;
        int gathered = small ? (minerals ? available : DEPLETED_GAS_LOAD) : RESOURCE_LOAD;
        int remaining = small ? 0 : available - gathered;

        if (small && minerals) {
            this.game.getUnitLifecycle().kill(resource, instructionLimit);
        } else {
            resource.setResources(remaining);
        }

        // A gas structure left below one load shows the geyser-depleted message.
        if (!minerals && !small && remaining < RESOURCE_LOAD) {
            this.game.getInterfaceLink().getCommandErrorEvents().raiseGasDepleted(resource);
        }

        if (minerals && !small) {
            int animation = remaining < MINERALS_LOW
                            ? IscriptExecutor.ANIMATION_SPECIAL_STATE_1
                            : remaining < MINERALS_MEDIUM
                              ? IscriptExecutor.ANIMATION_SPECIAL_STATE_2
                              : remaining < MINERALS_HIGH ? IscriptExecutor.ANIMATION_ALMOST_BUILT : IscriptExecutor.ANIMATION_WORKING_TO_IDLE;

            if (resource.getResourceAnimation() != animation) {
                resource.setResourceAnimation(animation);
                this.game.getIscriptExecutor().runSpriteAnimation(resource.requireSprite(), resource, animation, 1, instructionLimit);
            }

            if (remaining == 0) {
                this.game.getUnitLifecycle().kill(resource, instructionLimit);
            }
        }

        if (gathered == 0) {
            return;
        }

        if ((worker.getCarryingFlags() & CarryingFlags.RESOURCES) != 0) {
            this.game.getIscriptExecutor().removeCarryImages(worker);
            worker.setCarryingFlags(0);
        }

        if (worker.getCarryingFlags() == 0) {
            worker.setCarryingFlags(minerals ? CarryingFlags.MINERALS : CarryingFlags.GAS);
            worker.setCarriedResourceAmount(gathered);

            if (minerals) {
                this.game.getIscriptExecutor().addMineralCarryImage(worker, small, instructionLimit);
            } else {
                this.game.getIscriptExecutor().addGasCarryImage(worker, type, small, instructionLimit);
            }
        }
    }

    /**
     * Enters the resource FIFO queue or starts gathering when the resource is free.
     *
     * @param worker           the waiting worker
     * @param target           the resource
     * @param instructionLimit the order transition animation budget
     */
    private void waitForResource(@NotNull Unit worker, @NotNull Unit target, int instructionLimit) {
        if (worker.getPrimaryOrderState() != 0) {
            return;
        }

        worker.setPrimaryOrderCountdown(0);
        worker.setPrimaryOrderState(STATE_WAITING);

        if (target.isResourceBeingGathered()) {
            if (worker.getGatherTarget() == null) {
                worker.setGatherTarget(target);
                worker.setPreviousGatheringWorker(null);
                worker.setNextGatheringWorker(target.getFirstGatheringWorker());

                if (target.getFirstGatheringWorker() == null) {
                    target.setLastGatheringWorker(worker);
                } else {
                    target.getFirstGatheringWorker().setPreviousGatheringWorker(worker);
                }

                target.setFirstGatheringWorker(worker);
            }

            this.game.getOrders().queueFront(worker, ScrOrderIds.GATHER_WAIT_INTERRUPTED, null, 0, 0, ScrUnitIds.NONE);

            return;
        }

        this.beginGathering(worker, target, instructionLimit);
    }

    /**
     * Claims the resource and queues the matching gas or mineral work order.
     *
     * @param worker           the gathering worker
     * @param target           the resource
     * @param instructionLimit the order transition animation budget
     * @throws IllegalStateException if the resource has no sprite
     */
    private void beginGathering(@NotNull Unit worker, @NotNull Unit target, int instructionLimit) {
        target.setResourceBeingGathered(true);
        worker.setGatherTarget(target);
        worker.setGatheringResource(true);

        boolean gas = worker.getPrimaryOrder() == ScrOrderIds.WAIT_FOR_GAS;

        if (!gas) {
            this.game.getOrders().queueFront(worker, ScrOrderIds.GATHERING_INTERRUPTED, null, 0, 0, ScrUnitIds.NONE);
        }

        Sprite sprite = target.requireSprite();

        this.game.getOrders().queueFront(worker, gas ? ScrOrderIds.HARVEST_GAS : ScrOrderIds.MINING_MINERALS,
                                         target, sprite.getX(), sprite.getY(), ScrUnitIds.NONE);
        this.game.getOrders().finishOrder(worker, instructionLimit);
    }

    /**
     * Sets the gathering status of a worker that does not have it yet and queues the harvest collision reset in front.
     *
     * @param worker the gathering worker
     */
    private void startGathering(@NotNull Unit worker) {
        if ((worker.getStatus() & UnitStatusFlags.GATHERING) == 0) {
            worker.setStatus(worker.getStatus() | UnitStatusFlags.GATHERING);
            this.game.getOrders().queueFront(worker, ScrOrderIds.RESET_HARVEST_COLLISION, null, 0, 0, ScrUnitIds.NONE);
        }
    }

    /**
     * Retargets a worker to a replacement mineral field: moves toward it and, once the move starts, stores it as the resource target and the order
     * target.
     *
     * @param worker      the gathering worker
     * @param replacement the replacement mineral field
     * @throws IllegalStateException if the replacement mineral field has no sprite
     */
    private void moveToReplacement(@NotNull Unit worker, @NotNull Unit replacement) {
        if (!this.game.getUnitTargetMovement().moveToTargetReset(worker, replacement)) {
            return;
        }

        Sprite replacementSprite = replacement.requireSprite();

        worker.setTargetResource(replacement, replacementSprite.getX(), replacementSprite.getY());
        worker.setOrderTargetUnit(replacement);
        worker.setOrderTargetX(replacementSprite.getX());
        worker.setOrderTargetY(replacementSprite.getY());
    }

    /**
     * Stops a worker, faces it toward its movement destination and finishes its order.
     *
     * @param worker           the worker
     * @param instructionLimit the order transition animation budget
     */
    private void stopAndFinishOrder(@NotNull Unit worker, int instructionLimit) {
        this.game.getUnitPointMovement().stopAndFaceDestination(worker);
        this.game.getOrders().finishOrder(worker, instructionLimit);
    }

    /**
     * Removes the first matching queued order while preserving linked-list traversal order.
     *
     * @param worker  the worker holding the queue
     * @param orderId the order identifier
     * @throws NullPointerException          if the world was built without game data
     * @throws UnsupportedOperationException if the order pool is not initialized
     */
    private void removeOrder(@NotNull Unit worker, int orderId) {
        for (OrderPool.Order order = worker.getFirstOrder(); order != null; order = order.getNext()) {
            if (order.getOrderId() == orderId) {
                this.game.requireOrderPool().remove(worker, order, this.game.requireGameData().getOrderTypes());

                return;
            }
        }
    }
}
