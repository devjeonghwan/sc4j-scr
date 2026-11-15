package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.fogofwarlabs.sc4j.engine.client.command.CommandTarget;
import org.fogofwarlabs.sc4j.engine.client.command.CommandTranslator;
import org.fogofwarlabs.sc4j.engine.client.command.OrderedSelection;
import org.fogofwarlabs.sc4j.engine.client.input.InputEvent;
import org.fogofwarlabs.sc4j.engine.client.input.KeyBindingTable;
import org.fogofwarlabs.sc4j.engine.client.input.PointerButton;
import org.fogofwarlabs.sc4j.engine.command.CommandSink;
import org.fogofwarlabs.sc4j.engine.command.CommandType;
import org.fogofwarlabs.sc4j.engine.entity.Unit;
import org.fogofwarlabs.sc4j.engine.player.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that the interface mode waits in the engine targeting mode: the engine translator issues a targeting or placement mode at a left
 * press through the target handler, a refused target keeps the mode, a right press cancels it, and an invalid mode stays normal.
 */
final class InterfaceModeTest {
    private static final int UNIT_ORDER = 10;
    private static final int GROUND_ORDER = 6;
    private static final int ALTERNATE_ORDER = 7;
    private static final int BUILDING_TYPE = 109;
    private static final int PRESS_X = 40;
    private static final int PRESS_Y = 30;

    /**
     * Records the handled clicks and refuses the first targeting click.
     */
    private static final class RecordingHandler implements InterfaceMode.TargetHandler {
        private final @NotNull InterfaceMode mode;
        private final @NotNull List<String> clicks;

        private RecordingHandler(@NotNull InterfaceMode mode) {
            this.mode   = mode;
            this.clicks = new ArrayList<>();
        }

        @Override
        public boolean clickTarget(@NotNull CommandTarget target, boolean queued) {
            this.clicks.add("target " + this.mode.getUnitTargetOrder() + " at " + target.getX() + "," + target.getY());

            if (this.clicks.size() == 1) {
                return false;
            }

            this.mode.clear();

            return true;
        }

        @Override
        public boolean clickPlacement(@NotNull CommandTarget target) {
            this.clicks.add("place " + this.mode.getPlacementType());

            return true;
        }
    }

    /**
     * Fails if the translator submits a command itself: the SC:R clicks submit through the command input.
     */
    private static final class FailingSink implements CommandSink {
        @Override
        public <C> void submit(@NotNull Player player, @NotNull CommandType<C> type, @NotNull C command) {
            Assertions.fail("no engine command expected");
        }
    }

    private static @NotNull InputEvent createPress(@NotNull PointerButton button) {
        return InputEvent.createButtonPressed(button, 0, PRESS_X, PRESS_Y, false);
    }

    @Test
    void the_engine_translator_should_issue_and_cancel_the_waiting_mode() {
        InterfaceMode mode = new InterfaceMode();
        RecordingHandler handler = new RecordingHandler(mode);
        OrderedSelection selection = new OrderedSelection(InterfaceSelection.CAPACITY);
        CommandTranslator translator = new CommandTranslator(new Player(0), new FailingSink(), selection, new KeyBindingTable<>(),
                                                             (x, y) -> CommandTarget.at((int) x, (int) y, null), mode.getTargeting());

        mode.setTargetHandler(handler);
        selection.select(new Unit(1));

        Assertions.assertFalse(translator.handleInput(createPress(PointerButton.LEFT)), "a normal mode leaves the press to the selection box");

        mode.startTargeting(UNIT_ORDER, GROUND_ORDER, ALTERNATE_ORDER);

        Assertions.assertTrue(mode.getTargeting().isActive());
        Assertions.assertTrue(translator.handleInput(createPress(PointerButton.LEFT)));
        Assertions.assertEquals(UNIT_ORDER, mode.getUnitTargetOrder(), "a refused target keeps the mode");
        Assertions.assertTrue(translator.handleInput(createPress(PointerButton.LEFT)));
        Assertions.assertEquals(-1, mode.getUnitTargetOrder(), "a sent order ends the mode");
        Assertions.assertEquals(-1, mode.getGroundTargetOrder());

        mode.startPlacement(ButtonAction.TERRAN_BUILD, BUILDING_TYPE);

        Assertions.assertEquals(BUILDING_TYPE, mode.getPlacementType());
        Assertions.assertTrue(translator.handleInput(createPress(PointerButton.RIGHT)), "a right press cancels");
        Assertions.assertEquals(-1, mode.getPlacementType());
        Assertions.assertSame(ButtonAction.TERRAN_BUILD, mode.getPlacementAction(), "the placing action is kept");

        mode.startPlacement(ButtonAction.PROTOSS_BUILD, BUILDING_TYPE);

        Assertions.assertTrue(translator.handleInput(createPress(PointerButton.LEFT)));
        Assertions.assertFalse(mode.getTargeting().isActive(), "an issued placement ends the mode");

        mode.startPlacement(ButtonAction.TERRAN_BUILD, -1);

        Assertions.assertFalse(mode.getTargeting().isActive(), "no building type stays normal");
        Assertions.assertEquals(List.of("target " + UNIT_ORDER + " at " + PRESS_X + "," + PRESS_Y,
                                        "target " + UNIT_ORDER + " at " + PRESS_X + "," + PRESS_Y,
                                        "place " + BUILDING_TYPE), handler.clicks);
    }
}
