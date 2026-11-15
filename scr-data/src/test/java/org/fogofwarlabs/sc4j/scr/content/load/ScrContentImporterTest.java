package org.fogofwarlabs.sc4j.scr.content.load;

import org.fogofwarlabs.sc4j.engine.animation.AnimationLibrary;
import org.fogofwarlabs.sc4j.engine.animation.AnimationSet;
import org.fogofwarlabs.sc4j.engine.content.definition.UnitTrait;
import org.fogofwarlabs.sc4j.engine.content.definition.WeaponTarget;
import org.fogofwarlabs.sc4j.engine.content.registry.ContentDefinition;
import org.fogofwarlabs.sc4j.engine.content.registry.ContentRegistry;
import org.fogofwarlabs.sc4j.engine.content.table.GameContent;
import org.fogofwarlabs.sc4j.engine.content.type.*;
import org.fogofwarlabs.sc4j.engine.entity.column.IntColumn;
import org.fogofwarlabs.sc4j.scr.content.*;
import org.fogofwarlabs.sc4j.scr.content.table.ScrDatBindingUtils;
import org.fogofwarlabs.sc4j.scr.content.table.ScrDatKind;
import org.fogofwarlabs.sc4j.scr.data.format.dat.*;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrImageTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrSpriteTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrWeaponTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.WeaponTargetFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * Checks that the SC:R content importer registers every DAT entry at its native index with its DAT values.
 */
final class ScrContentImporterTest {
    private static final int MARINE = 0;
    private static final int GAUSS_RIFLE = 0;
    private static final int ARMOR_UPGRADE = 1;
    private static final int MARINE_HIT_POINTS = 40 << 8;
    private static final int MARINE_MINERALS = 50;
    private static final int MARINE_GAS = 7;
    private static final int MARINE_FLINGY = 9;
    private static final int GAUSS_RIFLE_DAMAGE = 6;
    private static final int GAUSS_RIFLE_RANGE = 128;
    private static final int SELECTION_SPRITE = 200;
    private static final int SELECTION_CIRCLE = 4;
    private static final int HEALTH_BAR = 22;
    private static final int INFESTED_TYPE = 110;
    private static final int INFESTED_VALUE = 50;
    private static final int TERRAN_UPGRADE_RACE = 1;
    private static final int ALL_RACES = 4;
    private static final int OUTSIDE_FLINGY = 255;
    private static final int ADDED_DRAW_FUNCTION = 9;
    private static final int ADDED_SELECTION_CIRCLE = 3;
    private static final int ADDED_TOP_SPEED = 1234;
    private static final int ADDED_DAMAGE = 12;
    private static final int ADDED_SPLASH_RADIUS = 40;
    private static final int OVERLAY_X = -7;
    private static final int OVERLAY_Y = 53;
    /**
     * The animation library of the empty DAT tables: one set without animations, which every image entry {@code 0} names.
     */
    private static final @NotNull AnimationLibrary ANIMATIONS = new AnimationLibrary(List.of(), List.of(),
                                                                                     List.of(new AnimationSet.Builder(0, "Empty").build()));

    private static @NotNull DatTable createTable(@NotNull DatSchema schema) throws IOException {
        return schema.parse(new byte[schema.getExpectedSize()]);
    }

    private static @NotNull ContentDefinition importContent(@NotNull DatTable units, @NotNull DatTable weapons, @NotNull DatTable upgrades)
    throws IOException {
        ContentRegistry registry = new ContentRegistry();

        new ScrContentImporter(units, weapons, createTable(FlingyDat.createSchema()), createTable(SpritesDat.createSchema()),
                               createTable(ImagesDat.createSchema()), createTable(OrdersDat.createSchema()), upgrades,
                               createTable(TechdataDat.createSchema()), createTable(SfxdataDat.createSchema()), ANIMATIONS, new ScrAddedUnitTypes(),
                               new ScrAddedGraphics(ANIMATIONS.getSets().size()))
                .register(registry);

        return registry.build();
    }

    private static @NotNull DatTable createOrders() throws IOException {
        DatTable orders = createTable(OrdersDat.createSchema());

        for (int order = 0; order < OrdersDat.ORDER_COUNT; order++) {
            orders.set(OrdersDat.WEAPON, order, ScrWeapons.NATIVE_NONE);
            orders.set(OrdersDat.TECHNOLOGY, order, ScrTechs.NATIVE_NONE);
        }

        return orders;
    }

    @Test
    void register_should_register_every_kind_at_its_native_index() throws IOException {
        ContentRegistry registry = new ContentRegistry();

        new ScrContentImporter(createTable(UnitsDat.createSchema()), createTable(WeaponsDat.createSchema()), createTable(FlingyDat.createSchema()),
                               createTable(SpritesDat.createSchema()), createTable(ImagesDat.createSchema()), createOrders(),
                               createTable(UpgradesDat.createSchema()), createTable(TechdataDat.createSchema()), createTable(SfxdataDat.createSchema()),
                               ANIMATIONS, new ScrAddedUnitTypes(), new ScrAddedGraphics(ANIMATIONS.getSets().size()))
                .register(registry);

        ContentDefinition content = registry.build();

        Assertions.assertEquals(ScrUnits.getTypes(), content.getUnitTypes().getTypes().getTypes());
        Assertions.assertEquals(ScrWeapons.getTypes(), content.getWeaponTypes().getTypes().getTypes());
        Assertions.assertEquals(ScrFlingies.getTypes(), content.getMotionProfiles().getTypes().getTypes());
        Assertions.assertEquals(ScrSprites.getTypes(), content.getSpriteTypes().getTypes().getTypes());
        Assertions.assertEquals(ScrImages.getTypes(), content.getImageTypes().getTypes().getTypes());
        Assertions.assertEquals(ScrUpgrades.getTypes(), content.getUpgradeTypes().getTypes().getTypes());
        Assertions.assertEquals(ScrTechs.getTypes(), content.getTechTypes().getTypes().getTypes());
        Assertions.assertEquals(ScrOrders.getTypes(), content.getOrderTypes().getTypes().getTypes());
        Assertions.assertEquals(ScrSounds.getTypes(), content.getSoundTypes().getTypes().getTypes());
        Assertions.assertEquals(ScrRaces.getTypes(), content.getFactions().getTypes().getTypes());
        Assertions.assertEquals(ScrResources.getTypes(), content.getResourceKinds().getTypes());
        Assertions.assertEquals(ScrContentImporter.PLAYER_COUNT, content.getPlayerCount());

        for (int nativeId = 0; nativeId < ScrUnits.COUNT; nativeId++) {
            Assertions.assertEquals(nativeId, ScrUnits.get(nativeId).getIndex());
        }

        Assertions.assertEquals(ContentType.NO_INDEX, GameContent.create(content, ScrContentImporter.PLAYER_COUNT).getOrderTypes().getWeaponIndex(0));
    }

    @Test
    void register_should_copy_dat_values_into_engine_and_extension_columns() throws IOException {
        DatTable units = createTable(UnitsDat.createSchema());
        DatTable weapons = createTable(WeaponsDat.createSchema());
        DatTable upgrades = createTable(UpgradesDat.createSchema());

        units.set(UnitsDat.HIT_POINTS, MARINE, MARINE_HIT_POINTS);
        units.set(UnitsDat.MINERAL_COST, MARINE, MARINE_MINERALS);
        units.set(UnitsDat.GAS_COST, MARINE, MARINE_GAS);
        units.set(UnitsDat.FLINGY_TYPE, MARINE, MARINE_FLINGY);
        units.set(UnitsDat.GROUND_WEAPON, MARINE, GAUSS_RIFLE);
        units.set(UnitsDat.AIR_WEAPON, MARINE, ScrWeapons.NATIVE_NONE);
        units.set(UnitsDat.ARMOR_UPGRADE, MARINE, ScrUpgrades.NATIVE_NONE);
        units.set(UnitsDat.GROUP_FLAGS, MARINE, UnitGroupFlags.TERRAN | UnitGroupFlags.MEN);
        units.set(UnitsDat.FLAGS, MARINE, UnitTypeFlags.WORKER | UnitTypeFlags.ORGANIC);
        units.set(UnitsDat.INFESTED_TYPE, INFESTED_TYPE - ScrDatKind.UNITS.getFirstType(UnitsDat.INFESTED_TYPE), INFESTED_VALUE);
        weapons.set(WeaponsDat.DAMAGE_AMOUNT, GAUSS_RIFLE, GAUSS_RIFLE_DAMAGE);
        weapons.set(WeaponsDat.MAXIMUM_RANGE, GAUSS_RIFLE, GAUSS_RIFLE_RANGE);
        weapons.set(WeaponsDat.DAMAGE_UPGRADE, GAUSS_RIFLE, ARMOR_UPGRADE);
        weapons.set(WeaponsDat.TARGET_FLAGS, GAUSS_RIFLE, WeaponTargetFlags.GROUND | WeaponTargetFlags.AIR);
        upgrades.set(UpgradesDat.RACE, ARMOR_UPGRADE, TERRAN_UPGRADE_RACE);
        upgrades.set(UpgradesDat.RACE, 0, ALL_RACES);

        ContentDefinition content = importContent(units, weapons, upgrades);
        GameContent game = GameContent.create(content, ScrContentImporter.PLAYER_COUNT);
        IntColumn infested = game.getUnitTypes().getExtensionColumns().get(ScrDatKind.UNITS.getExtensionKey(UnitsDat.INFESTED_TYPE));
        IntColumn groupFlags = game.getUnitTypes().getExtensionColumns().get(ScrDatKind.UNITS.getExtensionKey(UnitsDat.GROUP_FLAGS));

        Assertions.assertEquals(MARINE_HIT_POINTS, game.getUnitTypes().getHitPoints(MARINE));
        Assertions.assertEquals(MARINE_MINERALS, game.getUnitTypes().getCost(MARINE, ScrResources.MINERALS.getIndex()));
        Assertions.assertEquals(MARINE_GAS, game.getUnitTypes().getCost(MARINE, ScrResources.GAS.getIndex()));
        Assertions.assertEquals(MARINE_FLINGY, game.getUnitTypes().getMotionProfileIndex(MARINE));
        Assertions.assertEquals(GAUSS_RIFLE, game.getUnitTypes().getGroundWeaponIndex(MARINE));
        Assertions.assertEquals(ContentType.NO_INDEX, game.getUnitTypes().getAirWeaponIndex(MARINE));
        Assertions.assertEquals(ContentType.NO_INDEX, game.getUnitTypes().getArmorUpgradeIndex(MARINE));
        Assertions.assertEquals(ScrRaces.TERRAN.getIndex(), game.getUnitTypes().getFactionIndex(MARINE));
        Assertions.assertTrue(game.getUnitTypes().hasTrait(MARINE, UnitTrait.WORKER));
        Assertions.assertFalse(game.getUnitTypes().hasTrait(MARINE, UnitTrait.BUILDING));
        Assertions.assertEquals(UnitGroupFlags.TERRAN | UnitGroupFlags.MEN, groupFlags.get(MARINE));
        Assertions.assertEquals(INFESTED_VALUE, infested.get(INFESTED_TYPE));
        Assertions.assertEquals(GAUSS_RIFLE_DAMAGE, game.getWeaponTypes().getDamage(GAUSS_RIFLE));
        Assertions.assertEquals(GAUSS_RIFLE_RANGE, game.getWeaponTypes().getMaximumRange(GAUSS_RIFLE));
        Assertions.assertEquals(ARMOR_UPGRADE, game.getWeaponTypes().getUpgradeIndex(GAUSS_RIFLE));
        Assertions.assertTrue(game.getWeaponTypes().hasTarget(GAUSS_RIFLE, WeaponTarget.AIR));
        Assertions.assertTrue(game.getWeaponTypes().hasTarget(GAUSS_RIFLE, WeaponTarget.BUILDING));
        Assertions.assertFalse(game.getWeaponTypes().hasTarget(GAUSS_RIFLE, WeaponTarget.OWN_UNIT));
        Assertions.assertEquals(ScrRaces.TERRAN.getIndex(), game.getUpgradeTypes().getFactionIndex(ARMOR_UPGRADE));
        Assertions.assertEquals(ContentType.NO_INDEX, game.getUpgradeTypes().getFactionIndex(0));
    }

    @Test
    void register_should_copy_partial_fields_at_their_first_type() throws IOException {
        DatTable sprites = createTable(SpritesDat.createSchema());
        int element = SELECTION_SPRITE - ScrDatKind.SPRITES.getFirstType(SpritesDat.SELECTION_CIRCLE);
        ContentRegistry registry = new ContentRegistry();

        sprites.set(SpritesDat.SELECTION_CIRCLE, element, SELECTION_CIRCLE);
        sprites.set(SpritesDat.HEALTH_BAR_WIDTH, element, HEALTH_BAR);

        new ScrContentImporter(createTable(UnitsDat.createSchema()), createTable(WeaponsDat.createSchema()), createTable(FlingyDat.createSchema()),
                               sprites, createTable(ImagesDat.createSchema()), createOrders(), createTable(UpgradesDat.createSchema()),
                               createTable(TechdataDat.createSchema()), createTable(SfxdataDat.createSchema()), ANIMATIONS, new ScrAddedUnitTypes(),
                               new ScrAddedGraphics(ANIMATIONS.getSets().size()))
                .register(registry);

        GameContent game = GameContent.create(registry.build(), ScrContentImporter.PLAYER_COUNT);
        IntColumn healthBars = game.getSpriteTypes().getExtensionColumns().get(ScrDatKind.SPRITES.getExtensionKey(SpritesDat.HEALTH_BAR_WIDTH));

        Assertions.assertEquals(SELECTION_CIRCLE, game.getSpriteTypes().getSelectionSize(SELECTION_SPRITE));
        Assertions.assertEquals(0, game.getSpriteTypes().getSelectionSize(element));
        Assertions.assertEquals(HEALTH_BAR, healthBars.get(SELECTION_SPRITE));
        Assertions.assertEquals(0, healthBars.get(element));
    }

    @Test
    void register_should_reject_a_reference_outside_its_table() throws IOException {
        DatTable units = createTable(UnitsDat.createSchema());

        units.set(UnitsDat.FLINGY_TYPE, MARINE, OUTSIDE_FLINGY);

        Assertions.assertThrows(IllegalArgumentException.class, () -> importContent(units, createTable(WeaponsDat.createSchema()),
                                                                                    createTable(UpgradesDat.createSchema())));
    }

    @Test
    void register_should_register_added_graphics_after_the_native_entries() throws IOException {
        DatTable flingy = createTable(FlingyDat.createSchema());
        DatTable sprites = createTable(SpritesDat.createSchema());
        DatTable images = createTable(ImagesDat.createSchema());
        ScrAddedGraphics graphics = new ScrAddedGraphics(ANIMATIONS.getSets().size());
        ImageType imageType = new ImageType("added image");
        SpriteType spriteType = new SpriteType("added sprite");
        MotionProfile flingyType = new MotionProfile("added flingy");
        int imageId = graphics.addImage(new ScrAddedImage.Builder(imageType, 0, images).setValue(ImagesDat.DRAW_FUNCTION, ADDED_DRAW_FUNCTION).build());
        int spriteId = graphics.addSprite(new ScrAddedSprite.Builder(spriteType, SELECTION_SPRITE, sprites)
                                                  .setValue(SpritesDat.IMAGE, imageId)
                                                  .setValue(SpritesDat.SELECTION_CIRCLE, ADDED_SELECTION_CIRCLE)
                                                  .build());
        int flingyId = graphics.addFlingy(new ScrAddedFlingy.Builder(flingyType, 0, flingy)
                                                  .setValue(FlingyDat.SPRITE, spriteId)
                                                  .setValue(FlingyDat.TOP_SPEED, ADDED_TOP_SPEED)
                                                  .build());
        ContentRegistry registry = new ContentRegistry();

        new ScrContentImporter(createTable(UnitsDat.createSchema()), createTable(WeaponsDat.createSchema()), flingy, sprites, images, createOrders(),
                               createTable(UpgradesDat.createSchema()), createTable(TechdataDat.createSchema()), createTable(SfxdataDat.createSchema()),
                               ANIMATIONS, new ScrAddedUnitTypes(), graphics)
                .register(registry);

        GameContent game = GameContent.create(registry.build(), ScrContentImporter.PLAYER_COUNT);
        ScrImageTypeView imageView = ImagesDat.createTypeTable(ScrDatBindingUtils.bind(ScrDatKind.IMAGES, game, images),
                                                               ScrDatBindingUtils.bindAddedColumns(ScrDatKind.IMAGES, game));
        ScrSpriteTypeView spriteView = SpritesDat.createTypeTable(ScrDatBindingUtils.bind(ScrDatKind.SPRITES, game, sprites),
                                                                  ScrDatBindingUtils.bindAddedColumns(ScrDatKind.SPRITES, game));
        ScrFlingyTypeView flingyView = FlingyDat.createTypeTable(ScrDatBindingUtils.bind(ScrDatKind.FLINGY, game, flingy),
                                                                 ScrDatBindingUtils.bindAddedColumns(ScrDatKind.FLINGY, game));

        Assertions.assertEquals(ScrAddedGraphics.FIRST_IMAGE_ID, imageId);
        Assertions.assertEquals(ScrAddedGraphics.FIRST_SPRITE_ID, spriteId);
        Assertions.assertEquals(ScrAddedGraphics.FIRST_FLINGY_ID, flingyId);
        Assertions.assertTrue(graphics.isFrozen());
        Assertions.assertEquals(imageId, imageType.getIndex());
        Assertions.assertEquals(spriteId, spriteType.getIndex());
        Assertions.assertEquals(flingyId, flingyType.getIndex());
        Assertions.assertEquals(imageId, game.getSpriteTypes().getImageIndex(spriteId));
        Assertions.assertEquals(spriteId, game.getMotionProfiles().getSpriteIndex(flingyId));
        Assertions.assertEquals(ADDED_TOP_SPEED, game.getMotionProfiles().getTopSpeed(flingyId));
        Assertions.assertEquals(ADDED_SELECTION_CIRCLE, game.getSpriteTypes().getSelectionSize(spriteId));
        Assertions.assertEquals(ADDED_DRAW_FUNCTION, imageView.getDrawFunction(imageId));
        Assertions.assertEquals(0, imageView.getDrawFunction(0));
        Assertions.assertEquals(imageId, spriteView.getImage(spriteId));
        Assertions.assertEquals(ADDED_SELECTION_CIRCLE, spriteView.getSelectionCircle(spriteId));
        Assertions.assertEquals(spriteId, flingyView.getSprite(flingyId));
        Assertions.assertEquals(ADDED_TOP_SPEED, flingyView.getTopSpeed(flingyId));
        Assertions.assertThrows(IllegalStateException.class, () -> graphics.addImage(new ScrAddedImage.Builder(new ImageType("late"), 0, images).build()));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ScrAddedImage.Builder(new ImageType("overlay"), 0, images)
                .setValue(ImagesDat.ATTACK_OVERLAY, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ScrAddedSprite.Builder(new SpriteType("outside"), ScrAddedGraphics.FIRST_SPRITE_ID,
                                                                                                 sprites));
    }

    @Test
    void register_should_register_added_weapons_after_the_none_weapon() throws IOException {
        DatTable units = createTable(UnitsDat.createSchema());
        DatTable weapons = createTable(WeaponsDat.createSchema());
        ScrAddedUnitTypes added = new ScrAddedUnitTypes();
        WeaponType weaponType = new WeaponType("added weapon");
        int weaponId = added.addWeapon(new ScrAddedWeapon.Builder(weaponType, GAUSS_RIFLE, weapons)
                                               .setValue(WeaponsDat.DAMAGE_AMOUNT, ADDED_DAMAGE)
                                               .setValue(WeaponsDat.OUTER_SPLASH_RADIUS, ADDED_SPLASH_RADIUS)
                                               .setValue(WeaponsDat.TARGET_FLAGS, WeaponTargetFlags.AIR | WeaponTargetFlags.GROUND)
                                               .build());
        ContentRegistry registry = new ContentRegistry();

        new ScrContentImporter(units, weapons, createTable(FlingyDat.createSchema()), createTable(SpritesDat.createSchema()),
                               createTable(ImagesDat.createSchema()), createOrders(), createTable(UpgradesDat.createSchema()),
                               createTable(TechdataDat.createSchema()), createTable(SfxdataDat.createSchema()), ANIMATIONS, added,
                               new ScrAddedGraphics(ANIMATIONS.getSets().size()))
                .register(registry);

        GameContent game = GameContent.create(registry.build(), ScrContentImporter.PLAYER_COUNT);
        ScrWeaponTypeView view = WeaponsDat.createTypeTable(ScrDatBindingUtils.bind(ScrDatKind.WEAPONS, game, weapons),
                                                            ScrDatBindingUtils.bindAddedColumns(ScrDatKind.WEAPONS, game));
        DatTable gameUnits = ScrDatBindingUtils.bind(ScrDatKind.UNITS, game, units);

        Assertions.assertEquals(ScrWeapons.NATIVE_NONE + 1, weaponId);
        Assertions.assertEquals(ScrAddedUnitTypes.FIRST_WEAPON_ID, weaponId);
        Assertions.assertEquals(ScrWeapons.COUNT, weaponType.getIndex());
        Assertions.assertTrue(added.containsWeapon(weaponId));
        Assertions.assertFalse(added.containsWeapon(ScrWeapons.NATIVE_NONE));
        Assertions.assertEquals(ADDED_DAMAGE, view.getDamageAmount(weaponId));
        Assertions.assertEquals(ADDED_SPLASH_RADIUS, view.getOuterSplashRadius(weaponId));
        Assertions.assertEquals(WeaponTargetFlags.AIR | WeaponTargetFlags.GROUND, view.getTargetFlags(weaponId));
        Assertions.assertEquals(0, view.getDamageAmount(GAUSS_RIFLE));
        Assertions.assertEquals(ADDED_DAMAGE, game.getWeaponTypes().getDamage(weaponType.getIndex()));
        Assertions.assertEquals(weaponId, ScrNativeIds.toNativeWeapon(ScrNativeIds.fromNativeWeapon(weaponId)));
        Assertions.assertEquals(ContentType.NO_INDEX, ScrNativeIds.fromNativeWeapon(ScrWeapons.NATIVE_NONE));

        // A unit type that names the added weapon keeps its id through the engine's weapon reference.
        gameUnits.set(UnitsDat.GROUND_WEAPON, MARINE, weaponId);

        Assertions.assertEquals(weaponId, gameUnits.get(UnitsDat.GROUND_WEAPON, MARINE));
        Assertions.assertEquals(weaponType.getIndex(), game.getUnitTypes().getGroundWeaponIndex(MARINE));
        Assertions.assertThrows(IllegalStateException.class, () -> added.addWeapon(new ScrAddedWeapon.Builder(new WeaponType("late"), 0, weapons).build()));
    }

    @Test
    void an_added_image_should_hold_its_own_overlay_offsets() throws IOException {
        DatTable images = createTable(ImagesDat.createSchema());
        ScrAddedImage plain = new ScrAddedImage.Builder(new ImageType("plain"), 0, images).build();
        ScrAddedImage image = new ScrAddedImage.Builder(new ImageType("with offsets"), 0, images)
                .setOverlayOffsets(ScrImageTypeView.OVERLAY_ATTACK, new int[][]{{0, 0}, {OVERLAY_X, OVERLAY_Y}})
                .build();
        int packed = image.getOverlayOffset(ScrImageTypeView.OVERLAY_ATTACK, 1, 0);

        Assertions.assertFalse(plain.hasOverlayOffsets(ScrImageTypeView.OVERLAY_ATTACK));
        Assertions.assertTrue(image.hasOverlayOffsets(ScrImageTypeView.OVERLAY_ATTACK));
        Assertions.assertFalse(image.hasOverlayOffsets(ScrImageTypeView.OVERLAY_SPECIAL));
        Assertions.assertEquals(OVERLAY_X, (short) packed);
        Assertions.assertEquals(OVERLAY_Y, packed >> Short.SIZE);
        Assertions.assertThrows(IllegalArgumentException.class, () -> image.getOverlayOffset(ScrImageTypeView.OVERLAY_ATTACK, 2, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> image.getOverlayOffset(ScrImageTypeView.OVERLAY_SPECIAL, 0, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ScrAddedImage.Builder(new ImageType("odd"), 0, images)
                .setOverlayOffsets(ScrImageTypeView.OVERLAY_ATTACK, new int[][]{{0, 0}, {1}}));
    }
}
