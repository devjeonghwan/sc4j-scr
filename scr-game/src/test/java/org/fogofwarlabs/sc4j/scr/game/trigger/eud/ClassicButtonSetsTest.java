package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.presentation.HostButtonSets;
import org.fogofwarlabs.sc4j.scr.game.presentation.HostButtonSets.Button;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * Checks that a button-table commit rebuilds the written host table or restores the original.
 */
@RequiresInstallation
final class ClassicButtonSetsTest {
    private static final int BUTTON_SET_SIZE = 12;
    private static final int BUTTONS_FIELD = 4;
    private static final int CONNECTED_SET_FIELD = 8;
    private static final int BUTTON_CONDITION_FIELD = 4;
    private static final int FIRST_BUTTON_ARRAY = 0x5177D8;
    private static final long FIRST_BUTTON_CONDITION = 0x140183EB0L;
    private static final int UNREADABLE_ADDRESS = 0x00100000;
    private static final int WORD_MASK = 0xFFFF;

    @Test
    void button_set_commit_rebuilds_the_written_host_table_or_restores_the_original() {
        Path executable = ExternalInput.INSTALLATION.resolveFile(GameData.EXECUTABLE_DIRECTORY, GameData.EXECUTABLE_FILE_NAME);

        NativeCodeConstants constants = new NativeCodeConstants();

        Assertions.assertTrue(constants.loadExecutable(executable));

        HostButtonSets host = new HostButtonSets();
        ClassicButtonSets sets = ClassicButtonSets.create(constants, host);

        Assertions.assertNotNull(sets);

        ClassicAddressSpace space = new ClassicAddressSpace();

        space.addAll(sets.getRegions());

        Assertions.assertEquals(6, space.readDword(ClassicButtonSets.TABLE_BASE));
        Assertions.assertEquals(FIRST_BUTTON_ARRAY, space.readDword(ClassicButtonSets.TABLE_BASE + BUTTONS_FIELD));
        Assertions.assertEquals(0xFFFF, space.readDword(ClassicButtonSets.TABLE_BASE + CONNECTED_SET_FIELD) & WORD_MASK);

        sets.commit(space);

        Assertions.assertFalse(host.isReplaced(), "Without a write nothing is committed.");

        space.writeDword(ClassicButtonSets.TABLE_BASE, 3);
        sets.commit(space);

        Assertions.assertTrue(host.isReplaced());
        Assertions.assertEquals(3, host.getCount(0));

        Button[] marine = host.getButtons(0);

        Assertions.assertNotNull(marine);
        Assertions.assertEquals(3, marine.length);

        Assertions.assertEquals(1, marine[0].getPosition());
        Assertions.assertEquals(228, marine[0].getIcon());
        Assertions.assertEquals(FIRST_BUTTON_CONDITION, marine[0].getCondition());
        Assertions.assertEquals(664, marine[0].getEnabledString());
        Assertions.assertEquals(0xFFFF, host.getConnectedSet(0));
        Assertions.assertNotNull(host.getButtons(1), "The commit rebuilds every entry.");

        // A condition without a function record empties that entry only.
        space.writeDword(FIRST_BUTTON_ARRAY + BUTTON_CONDITION_FIELD, 0x12345678);
        sets.commit(space);

        Assertions.assertTrue(host.isReplaced());
        Assertions.assertEquals(0, host.getCount(0));
        Assertions.assertNull(host.getButtons(0));
        Assertions.assertEquals(0xFFFF, host.getConnectedSet(0));
        Assertions.assertNotNull(host.getButtons(1));

        // A button array the emulation cannot read restores the table the game started with.
        space.writeDword(ClassicButtonSets.TABLE_BASE + BUTTON_SET_SIZE + BUTTONS_FIELD, UNREADABLE_ADDRESS);
        sets.commit(space);

        Assertions.assertFalse(host.isReplaced());
    }
}
