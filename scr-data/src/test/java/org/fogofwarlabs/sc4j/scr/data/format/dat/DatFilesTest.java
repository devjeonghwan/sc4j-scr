package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests the DAT schemas against the real files of a StarCraft: Remastered installation.
 */
@RequiresInstallation
final class DatFilesTest {
    private static @Nullable CascStorage storage;

    @BeforeAll
    static void openStorage() throws IOException {
        storage = CascStorage.open(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeStorage() {
        if (storage != null) {
            storage.close();
        }
    }


    @Test
    void parse_should_read_installed_flingy_dat() throws IOException {
        DatSchema schema = FlingyDat.createSchema();
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        DatTable table = schema.parse(activeStorage.readFile("arr/flingy.dat"));

        Assertions.assertEquals(3135, schema.getExpectedSize());
        Assertions.assertEquals(130, table.get("Sprite", 0));
        Assertions.assertEquals(133, table.get("Sprite", 1));
    }

    @Test
    void parse_should_read_installed_sprites_dat() throws IOException {
        DatSchema schema = SpritesDat.createSchema();
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        DatTable table = schema.parse(activeStorage.readFile("arr/sprites.dat"));

        Assertions.assertEquals(3229, schema.getExpectedSize());
        Assertions.assertEquals(589, table.get("Image", 0));
        Assertions.assertEquals(591, table.get("Image", 1));
    }

    @Test
    void parse_should_read_installed_images_dat() throws IOException {
        DatSchema schema = ImagesDat.createSchema();
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        DatTable table = schema.parse(activeStorage.readFile("arr/images.dat"));

        Assertions.assertEquals(37962, schema.getExpectedSize());
        Assertions.assertEquals(1, table.get("GRP File", 0));
        Assertions.assertEquals(3, table.get("GRP File", 2));
    }

    @Test
    void parse_should_read_installed_orders_dat() throws IOException {
        DatSchema schema = OrdersDat.createSchema();
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        DatTable table = schema.parse(activeStorage.readFile("arr/orders.dat"));

        Assertions.assertEquals(4158, schema.getExpectedSize());
        Assertions.assertEquals(903, table.get("Label", 0));
        Assertions.assertEquals(905, table.get("Label", 1));
    }

    @Test
    void parse_should_read_installed_upgrades_dat() throws IOException {
        DatSchema schema = UpgradesDat.createSchema();
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        DatTable table = schema.parse(activeStorage.readFile("arr/upgrades.dat"));

        Assertions.assertEquals(1281, schema.getExpectedSize());

        // Terran Infantry Armor, Terran Vehicle Plating, Terran Ship Plating.
        Assertions.assertEquals(100, table.get("Ore Cost", 0));
        Assertions.assertEquals(100, table.get("Ore Cost", 1));
        Assertions.assertEquals(150, table.get("Ore Cost", 2));
    }

    @Test
    void parse_should_read_installed_techdata_dat() throws IOException {
        DatSchema schema = TechdataDat.createSchema();
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        DatTable table = schema.parse(activeStorage.readFile("arr/techdata.dat"));

        Assertions.assertEquals(836, schema.getExpectedSize());

        // Stim Pack, Lockdown, Spider Mines, Scanner Sweep.
        Assertions.assertEquals(100, table.get("Ore Cost", 0));
        Assertions.assertEquals(200, table.get("Ore Cost", 1));
        Assertions.assertEquals(100, table.get("Ore Cost", 3));
        Assertions.assertEquals(0, table.get("Ore Cost", 4));
    }

    @Test
    void parse_should_read_installed_sfxdata_dat() throws IOException {
        DatSchema schema = SfxdataDat.createSchema();
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        DatTable table = schema.parse(activeStorage.readFile("arr/sfxdata.dat"));

        Assertions.assertEquals(10296, schema.getExpectedSize());
        Assertions.assertEquals(0, table.get("Sound File", 0));
        Assertions.assertEquals(1, table.get("Sound File", 1));
        Assertions.assertEquals(2, table.get("Sound File", 2));
    }

    @Test
    void parse_should_read_installed_portdata_dat() throws IOException {
        DatSchema schema = PortdataDat.createSchema();
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        DatTable table = schema.parse(activeStorage.readFile("arr/portdata.dat"));

        Assertions.assertEquals(1320, schema.getExpectedSize());
        Assertions.assertEquals(1, table.get("Idle Dir", 0));
        Assertions.assertEquals(3, table.get("Idle Dir", 1));
    }

    @Test
    void parse_should_read_installed_mapdata_dat() throws IOException {
        DatSchema schema = MapdataDat.createSchema();
        CascStorage activeStorage = storage;

        Assertions.assertNotNull(activeStorage);

        DatTable table = schema.parse(activeStorage.readFile("arr/mapdata.dat"));

        Assertions.assertEquals(260, schema.getExpectedSize());
        Assertions.assertEquals(1, table.get("Mission Dir", 0));
        Assertions.assertEquals(2, table.get("Mission Dir", 1));
    }
}
