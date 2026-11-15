package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.frame.UnitPhase;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImagePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Verifies psi provider fields, their power bounds and the power state of buildings inside them.
 */
@RequiresInstallation
final class UnitPsiTest {
    /**
     * The tileset of the test maps.
     */
    private static final int TILESET = 0;
    /**
     * The owner of every test unit but the second Pylon.
     */
    private static final int PLAYER = 0;
    /**
     * The owner of the second Pylon.
     */
    private static final int OTHER_PLAYER = 1;

    private static final int INSTRUCTION_LIMIT = 10000;
    private static final int MAP_SIZE = 64;
    private static final int TILE_COUNT = MAP_SIZE * MAP_SIZE;
    private static final int UNIT_CAPACITY = 4;
    private static final int SPRITE_CAPACITY = 8;
    private static final int IMAGE_CAPACITY = 64;
    private static final int ORDER_CAPACITY = 8;
    private static final int DISPLAY_REFRESH_LENGTH = 6;

    private static final int PYLON_X = 512;
    private static final int SECOND_PYLON_X = 1536;
    private static final int GATEWAY_X = 608;
    private static final int UNIT_Y = 512;
    private static final int POWERED_EDGE_OFFSET_X = 255;
    private static final int UNPOWERED_OFFSET_X = 256;
    private static final int UNPOWERED_OFFSET_Y = 160;
    private static final int CORNER_OFFSET = 128;
    private static final int INSIDE_CORNER_OFFSET_X = 64;

    private static final int HIDDEN_FLAG = 0x20;
    private static final int DISABLED_STATUS = 0x400;
    private static final int DISABLE_ANIMATION = 24;
    private static final int ENABLE_ANIMATION = 27;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            IgnoreException.execute(
                    gameData::close
            );
        }
    }

    private static @NotNull Game createState() {
        Game game = Game.create(gameData, new GameMap(MAP_SIZE, MAP_SIZE, TILESET, new int[TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(SPRITE_CAPACITY);
        game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);
        game.initializeOrderPool(ORDER_CAPACITY);
        game.initializeExecutionScope();

        return game;
    }

    private static @NotNull Unit createUnit(@NotNull Game game, int unitType, int positionX, int owner) {
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(UnitPoolScenario.allocate(unitPool));

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertTrue(game.getUnitInitializer().initialize(unit, unitType, positionX, UNIT_Y, owner, INSTRUCTION_LIMIT));

        unit.setStatus(unit.getStatus() | UnitStatusFlags.COMPLETED);
        unit.setPrimaryOrder(ScrOrderIds.NOTHING);

        return unit;
    }


    @Test
    void psi_provider_powers_owner_field_within_bounds_and_unlinks_on_removal() {
        Game game = createState();
        Unit first = createUnit(game, ScrUnitIds.PROTOSS_PYLON, PYLON_X, PLAYER);
        Unit second = createUnit(game, ScrUnitIds.PROTOSS_PYLON, SECOND_PYLON_X, OTHER_PLAYER);

        game.getPsi().initializeProvider(first, INSTRUCTION_LIMIT);

        SpriteImagePool imagePool = game.getSpriteSystem().getImagePool();

        Assertions.assertNotNull(imagePool);

        int allocatedImages = imagePool.getAllocatedCount();

        game.getPsi().initializeProvider(first, INSTRUCTION_LIMIT);

        Assertions.assertEquals(allocatedImages, imagePool.getAllocatedCount());

        Sprite fieldSprite = first.getPsiFieldSprite();

        Assertions.assertNotNull(fieldSprite);

        Assertions.assertEquals(HIDDEN_FLAG, fieldSprite.getFlags() & Sprite.FLAG_HIDDEN);
        Assertions.assertEquals(0, fieldSprite.getVisibilityFlags());
        Assertions.assertTrue(game.getPsi().isPowered(PLAYER, PYLON_X, UNIT_Y));
        Assertions.assertFalse(game.getPsi().isPowered(OTHER_PLAYER, PYLON_X, UNIT_Y));
        Assertions.assertTrue(game.getPsi().isPowered(PLAYER, PYLON_X + POWERED_EDGE_OFFSET_X, UNIT_Y));
        Assertions.assertFalse(game.getPsi().isPowered(PLAYER, PYLON_X + UNPOWERED_OFFSET_X, UNIT_Y));
        Assertions.assertFalse(game.getPsi().isPowered(PLAYER, PYLON_X, UNIT_Y + UNPOWERED_OFFSET_Y));
        Assertions.assertFalse(game.getPsi().isPowered(PLAYER, PYLON_X + CORNER_OFFSET, UNIT_Y + CORNER_OFFSET));
        Assertions.assertTrue(game.getPsi().isPowered(PLAYER, PYLON_X + INSIDE_CORNER_OFFSET_X, UNIT_Y + CORNER_OFFSET));

        game.getPsi().initializeProvider(second, INSTRUCTION_LIMIT);

        Assertions.assertSame(first, second.getNextPsiProvider());
        Assertions.assertSame(second, first.getPreviousPsiProvider());

        game.getPsi().removeProvider(first);

        Assertions.assertNull(second.getNextPsiProvider());
        Assertions.assertNull(first.getPsiFieldSprite());
        Assertions.assertFalse(game.getPsi().isPowered(PLAYER, PYLON_X, UNIT_Y));
        Assertions.assertTrue(game.getPsi().isPowered(OTHER_PLAYER, SECOND_PYLON_X, UNIT_Y));

        game.getPsi().removeProvider(second);

        Assertions.assertEquals(0, game.getPsi().getFirstProviderIndex());
        Assertions.assertNull(second.getPsiFieldSprite());
        Assertions.assertEquals(1, game.getBuildingPowerRefresh());
    }

    @Test
    void initialize_provider_should_activate_queued_idle_order_and_power_field() {
        Game game = createState();
        Unit pylon = createUnit(game, ScrUnitIds.PROTOSS_PYLON, PYLON_X, PLAYER);

        pylon.setPrimaryOrder(ScrOrderIds.INITIALIZE_PSI_PROVIDER);
        pylon.setStatus(pylon.getStatus() | UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE);

        Assertions.assertNotNull(gameData);

        int idleOrder = gameData.getUnits().get("Return to Idle", ScrUnitIds.PROTOSS_PYLON);

        game.getOrders().queueFront(pylon, idleOrder, null, 0, 0, ScrUnitIds.NONE);
        game.getPsi().initializeProvider(pylon, INSTRUCTION_LIMIT);

        Assertions.assertEquals(idleOrder, pylon.getPrimaryOrder());
        Assertions.assertNull(pylon.getFirstOrder());
        Assertions.assertNull(pylon.getLastOrder());

        OrderPool orderPool = game.getOrderPool();

        Assertions.assertNotNull(orderPool);

        Assertions.assertEquals(0, orderPool.getAllocatedCount());
        Assertions.assertNotNull(pylon.getPsiFieldSprite());
        Assertions.assertTrue(game.getPsi().isPowered(PLAYER, PYLON_X, UNIT_Y));
    }

    @Test
    void psi_provider_creation_and_removal_enable_and_disable_completed_gateway() {
        Game game = createState();
        Unit pylon = createUnit(game, ScrUnitIds.PROTOSS_PYLON, PYLON_X, PLAYER);
        Unit gateway = createUnit(game, ScrUnitIds.PROTOSS_GATEWAY, GATEWAY_X, PLAYER);

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        unitPool.insertCreatedUnit(gateway);
        gateway.setStatus(gateway.getStatus() | UnitStatusFlags.COMPLETED | UnitStatusFlags.DISABLED);

        UnitPhase.Context context = new UnitPhase.Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER,
                                                          UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER);

        context.getDisplayState().initializeDisplayRefresh(new long[DISPLAY_REFRESH_LENGTH]);

        int[] playerTypes = new int[PlayerTable.PLAYER_SLOTS];

        playerTypes[PLAYER] = PlayerTypes.HUMAN;

        game.getPsi().initializeProvider(pylon, INSTRUCTION_LIMIT);
        game.getUnitPhase().refreshBuildingPower(context, playerTypes, INSTRUCTION_LIMIT);

        Assertions.assertEquals(0, gateway.getStatus() & UnitStatusFlags.DISABLED);

        Sprite gatewaySprite = gateway.getSpriteOrNull();

        Assertions.assertNotNull(gatewaySprite);

        SpriteImage poweredGatewayImage = gatewaySprite.getMainImage();

        Assertions.assertNotNull(poweredGatewayImage);

        Assertions.assertEquals(ENABLE_ANIMATION, poweredGatewayImage.getAnimationType());

        game.getPsi().removeProvider(pylon);
        game.getUnitPhase().refreshBuildingPower(context, playerTypes, INSTRUCTION_LIMIT);

        Assertions.assertEquals(DISABLED_STATUS, gateway.getStatus() & UnitStatusFlags.DISABLED);

        SpriteImage unpoweredGatewayImage = gatewaySprite.getMainImage();

        Assertions.assertNotNull(unpoweredGatewayImage);

        Assertions.assertEquals(DISABLE_ANIMATION, unpoweredGatewayImage.getAnimationType());
        Assertions.assertEquals(0, game.getBuildingPowerRefresh());
    }
}
