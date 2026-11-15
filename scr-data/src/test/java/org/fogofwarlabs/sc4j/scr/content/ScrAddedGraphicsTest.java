package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.scr.data.format.iscript.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

/**
 * Checks that added iscript scripts are assembled after the installed file with the installed code unchanged.
 */
final class ScrAddedGraphicsTest {
    private static final int HEADER_OFFSET = 4;
    private static final int CODE_OFFSET = 16;
    private static final int TABLE_OFFSET = 20;
    private static final int FILE_SIZE = 28;
    private static final int GOTO_SIZE = 3;
    private static final int WALK_FRAME = 0x11;
    private static final int WALK_WAIT = 2;
    private static final int DEATH_FRAME = 0x22;

    /**
     * Creates an iscript with one script ({@code 0}, type {@code 1}): animation {@code 0} plays frame {@code 0} and ends, animation {@code 1} is
     * absent.
     */
    private static @NotNull IscriptFile createInstalled() throws IOException {
        byte[] data = new byte[FILE_SIZE];
        int[] words = {TABLE_OFFSET, 0, 0x4353, 0x4550, 1, 0, CODE_OFFSET, 0};

        for (int index = 0; index < words.length; index++) {
            data[index * 2]     = (byte) words[index];
            data[index * 2 + 1] = (byte) (words[index] >>> Byte.SIZE);
        }

        data[CODE_OFFSET]      = (byte) IscriptOpcodes.PLAYFRAM;
        data[CODE_OFFSET + 3]  = (byte) IscriptOpcodes.END;
        data[TABLE_OFFSET + 2] = HEADER_OFFSET;
        data[TABLE_OFFSET + 4] = (byte) 0xFF;
        data[TABLE_OFFSET + 5] = (byte) 0xFF;

        return IscriptFile.parse(data);
    }

    @Test
    void create_iscript_should_place_added_code_after_the_installed_file() throws IOException {
        IscriptFile installed = createInstalled();
        ScrAddedGraphics graphics = ScrAddedGraphics.create(installed);
        IscriptCode walk = new IscriptCode.Builder().label("walk")
                                                    .instruction(IscriptOpcodes.PLAYFRAM, WALK_FRAME)
                                                    .instruction(IscriptOpcodes.WAIT, WALK_WAIT)
                                                    .jump("walk")
                                                    .build();
        IscriptCode death = new IscriptCode.Builder().instruction(IscriptOpcodes.PLAYFRAM, DEATH_FRAME).instruction(IscriptOpcodes.END).build();
        int scriptId = graphics.addScript(new ScrAddedScript.Builder(0, installed).setAnimation(1, death).build());
        int walkingId = graphics.addScript(new ScrAddedScript.Builder(0, installed).setAnimation(0, walk).build());

        Assertions.assertSame(installed, new ScrAddedGraphics(1).createIscript(installed));

        IscriptFile scripts = graphics.createIscript(installed);
        int deathOffset = scripts.getAnimationOffset(scriptId, 1);
        int walkOffset = scripts.getAnimationOffset(walkingId, 0);
        IscriptInstruction jump = scripts.decodeInstruction(walkOffset + walk.getLength() - GOTO_SIZE);

        Assertions.assertEquals(1, scriptId);
        Assertions.assertEquals(2, walkingId);
        Assertions.assertArrayEquals(Arrays.copyOfRange(installed.copyData(), HEADER_OFFSET, FILE_SIZE),
                                     Arrays.copyOfRange(scripts.copyData(), HEADER_OFFSET, FILE_SIZE));
        Assertions.assertEquals(CODE_OFFSET, scripts.getAnimationOffset(0, 0));
        Assertions.assertEquals(CODE_OFFSET, scripts.getAnimationOffset(scriptId, 0));
        Assertions.assertEquals(FILE_SIZE, deathOffset);
        Assertions.assertEquals(DEATH_FRAME, scripts.decodeInstruction(deathOffset).getOperands()[0]);
        Assertions.assertEquals(0, scripts.getAnimationOffset(walkingId, 1));
        Assertions.assertEquals(WALK_FRAME, scripts.decodeInstruction(walkOffset).getOperands()[0]);
        Assertions.assertEquals(IscriptOpcodes.GOTO, jump.getOpcode());
        Assertions.assertEquals(walkOffset + walk.getLabelPosition("walk"), jump.getOperands()[0]);

        IscriptHeader header = scripts.findHeader(walkingId);

        Assertions.assertNotNull(header);
        Assertions.assertEquals(1, header.getType());
    }

    @Test
    void add_should_give_ids_after_the_native_tables_until_frozen() throws IOException {
        IscriptFile installed = createInstalled();
        ScrAddedGraphics graphics = ScrAddedGraphics.create(installed);
        ScrAddedScript script = new ScrAddedScript.Builder(0, installed).build();

        Assertions.assertTrue(graphics.isEmpty());
        Assertions.assertEquals(1, graphics.getFirstScriptId());
        Assertions.assertEquals(1, graphics.addScript(script));
        Assertions.assertFalse(graphics.containsImage(ScrAddedGraphics.FIRST_IMAGE_ID));
        Assertions.assertEquals(ScrAddedGraphics.FIRST_IMAGE_ID - 1, graphics.getOverlayImage(ScrAddedGraphics.FIRST_IMAGE_ID - 1));

        graphics.freeze();

        Assertions.assertThrows(IllegalStateException.class, () -> graphics.addScript(script));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ScrAddedScript.Builder(1, installed));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ScrAddedScript.Builder(0, installed).setAnimationOffset(2, 0));
    }

    @Test
    void build_should_refuse_operands_outside_the_opcode_layout() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new IscriptCode.Builder().instruction(IscriptOpcodes.WAIT));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new IscriptCode.Builder().instruction(IscriptOpcodes.WAIT, 256));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new IscriptCode.Builder().instruction(IscriptOpcodes.SETHORPOS, 128));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new IscriptCode.Builder().jump("missing").build());
        Assertions.assertThrows(IllegalArgumentException.class, () -> new IscriptCode.Builder().label("twice").label("twice"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new IscriptCode.Builder().build());
    }
}
