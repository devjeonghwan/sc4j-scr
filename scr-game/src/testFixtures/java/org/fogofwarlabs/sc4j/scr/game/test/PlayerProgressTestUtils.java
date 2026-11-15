package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.animation.AnimationLibrary;
import org.fogofwarlabs.sc4j.engine.animation.AnimationSet;
import org.fogofwarlabs.sc4j.engine.content.registry.ContentDefinition;
import org.fogofwarlabs.sc4j.engine.content.registry.ContentRegistry;
import org.fogofwarlabs.sc4j.engine.content.table.GameContent;
import org.fogofwarlabs.sc4j.engine.content.table.PlayerProgressTable;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedGraphics;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedUnitTypes;
import org.fogofwarlabs.sc4j.scr.content.load.ScrContentImporter;
import org.fogofwarlabs.sc4j.scr.data.format.dat.*;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.state.ContextExpansion;
import org.fogofwarlabs.sc4j.scr.game.state.GameContextBytes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * Creates the per-player storage views for tests that need them outside a game: every view serves an engine progress table, so each call creates
 * a progress table of its own with the SC:R shape (twelve players, every DAT entry of zeroed DAT tables and the SC:R per-player columns).
 */
public final class PlayerProgressTestUtils {
    /**
     * The animation library of the empty DAT tables: one set without animations, which every image entry {@code 0} names.
     */
    private static final @NotNull AnimationLibrary ANIMATIONS = new AnimationLibrary(List.of(), List.of(),
                                                                                     List.of(new AnimationSet.Builder(0, "Empty").build()));
    /**
     * The content of the progress tables, built once from zeroed DAT tables.
     */
    private static final @NotNull ContentDefinition CONTENT = createContent();

    /**
     * Creates a {@link PlayerProgressTable} instance of the SC:R shape with every value at its initial value.
     *
     * @return the created {@link PlayerProgressTable} instance
     */
    public static @NotNull PlayerProgressTable createProgressTable() {
        return GameContent.create(CONTENT, CONTENT.getPlayerCount()).getProgress();
    }

    /**
     * Creates a {@link PlayerUpgrades} instance over a new progress table with its own expansion BYTE and no game context: an access that addresses
     * a byte outside the upgrade tables stops.
     *
     * @param expansionEnabled whether the expansion BYTE of the game context is nonzero
     * @return the created {@link PlayerUpgrades} instance
     */
    public static @NotNull PlayerUpgrades createPlayerUpgrades(boolean expansionEnabled) {
        return createPlayerUpgrades(new ContextExpansion(expansionEnabled), null);
    }

    /**
     * Creates a {@link PlayerUpgrades} instance over a new progress table.
     *
     * @param expansion the expansion BYTE of the game context
     * @param context   supplies the view of the whole game context, or {@code null} when no game context exists
     * @return the created {@link PlayerUpgrades} instance
     */
    public static @NotNull PlayerUpgrades createPlayerUpgrades(@NotNull ContextExpansion expansion, @Nullable Supplier<GameContextBytes> context) {
        return new PlayerUpgrades(createProgressTable(), expansion, context);
    }

    /**
     * Creates a {@link PlayerTechnology} instance over a new progress table with the normal game defaults, its own expansion BYTE and no game
     * context: an access that addresses a byte outside the technology tables stops.
     *
     * @param expansionEnabled whether the expansion BYTE of the game context is nonzero
     * @return the created {@link PlayerTechnology} instance
     */
    public static @NotNull PlayerTechnology createPlayerTechnology(boolean expansionEnabled) {
        return createPlayerTechnology(new ContextExpansion(expansionEnabled), null);
    }

    /**
     * Creates a {@link PlayerTechnology} instance over a new progress table with the normal game defaults.
     *
     * @param expansion the expansion BYTE of the game context
     * @param context   supplies the view of the whole game context, or {@code null} when no game context exists
     * @return the created {@link PlayerTechnology} instance
     */
    public static @NotNull PlayerTechnology createPlayerTechnology(@NotNull ContextExpansion expansion, @Nullable Supplier<GameContextBytes> context) {
        return new PlayerTechnology(createProgressTable(), expansion, context);
    }

    /**
     * Creates a {@link PlayerUnitAvailability} instance over a new progress table, allowing every unit type for every player.
     *
     * @return the created {@link PlayerUnitAvailability} instance
     */
    public static @NotNull PlayerUnitAvailability createPlayerUnitAvailability() {
        return new PlayerUnitAvailability(createProgressTable());
    }

    /**
     * Creates a {@link PlayerUnitCounts} instance over the unit counts of a new progress table, every counter zero and no linked successor.
     *
     * @return the created {@link PlayerUnitCounts} instance
     */
    public static @NotNull PlayerUnitCounts createPlayerUnitCounts() {
        return PlayerUnitCounts.ofUnitCounts(createProgressTable());
    }

    /**
     * Creates a {@link UnitAccounting} instance over a new progress table with zeroed per-player state.
     *
     * @return the created {@link UnitAccounting} instance
     */
    public static @NotNull UnitAccounting createUnitAccounting() {
        return new UnitAccounting(createProgressTable());
    }

    /**
     * Creates a {@link PlayerScores} instance over a new progress table with every counter zero.
     *
     * @return the created {@link PlayerScores} instance
     */
    public static @NotNull PlayerScores createPlayerScores() {
        return new PlayerScores(createProgressTable());
    }

    private static @NotNull ContentDefinition createContent() {
        ContentRegistry registry = new ContentRegistry();

        try {
            new ScrContentImporter(createTable(UnitsDat.createSchema()), createTable(WeaponsDat.createSchema()),
                                   createTable(FlingyDat.createSchema()), createTable(SpritesDat.createSchema()),
                                   createTable(ImagesDat.createSchema()), createTable(OrdersDat.createSchema()),
                                   createTable(UpgradesDat.createSchema()), createTable(TechdataDat.createSchema()),
                                   createTable(SfxdataDat.createSchema()),
                                   ANIMATIONS, new ScrAddedUnitTypes(), new ScrAddedGraphics(ANIMATIONS.getSets().size()))
                    .register(registry);
        } catch (IOException exception) {
            throw Exceptions.newUncheckedIOException(exception);
        }

        PlayerProgressColumns.register(registry);

        return registry.build();
    }

    private static @NotNull DatTable createTable(@NotNull DatSchema schema) throws IOException {
        return schema.parse(new byte[schema.getExpectedSize()]);
    }


    private PlayerProgressTestUtils() {
    }
}
