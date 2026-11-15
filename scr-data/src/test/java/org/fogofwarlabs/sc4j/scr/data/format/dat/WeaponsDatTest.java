package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests the {@code arr/weapons.dat} schema against the real file of a StarCraft: Remastered installation.
 */
@RequiresInstallation
final class WeaponsDatTest {
    private static final int GAUSS_RIFLE = ScrWeaponIds.GAUSS_RIFLE;

    private static @Nullable DatTable weaponsTable;

    @BeforeAll
    static void loadWeaponsDat() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            weaponsTable = WeaponsDat.createSchema().parse(storage.readFile("arr/weapons.dat"));
        }
    }


    @Test
    void get_expected_size_should_match_weapons_dat_file_size() {
        Assertions.assertEquals(5460, WeaponsDat.createSchema().getExpectedSize());
    }

    @Test
    void parse_should_read_gauss_rifle_values() {
        DatTable activeWeaponsTable = weaponsTable;

        Assertions.assertNotNull(activeWeaponsTable);

        // Gauss Rifle: 6 damage, cooldown 15, maximum range 128 (4 tiles).
        Assertions.assertEquals(6, activeWeaponsTable.get("Damage Amount", GAUSS_RIFLE));
        Assertions.assertEquals(15, activeWeaponsTable.get("Weapon Cooldown", GAUSS_RIFLE));
        Assertions.assertEquals(128, activeWeaponsTable.get("Maximum Range", GAUSS_RIFLE));
    }
}
