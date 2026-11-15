package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.frame.UnitPhase;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Checks replay-label formatting and caller state updates.
 */
@RequiresInstallation
final class ReplayStatusTextTest {
    /**
     * The SHA-256 digest of the replay label capture.
     */
    private static final @NotNull String LABEL_CAPTURE_HASH = "044284d94326926522895b16f345fd97bd18aec607659fd89dcb7e989c2163e1";
    /**
     * The SHA-256 digest of the replay display capture.
     */
    private static final @NotNull String DISPLAY_CAPTURE_HASH = "f47137e5c5be82ebd9f218e79442c0d2f49487bcfdbfc1daa9b7e03f5b0f447e";

    /**
     * The cases of each capture.
     */
    private static final int CAPTURE_CASE_COUNT = 32;

    /**
     * The input DWORDs of one capture case.
     */
    private static final int CAPTURE_INPUT_COUNT = 8;
    /**
     * The label flags of one capture case.
     */
    private static final int CAPTURE_LABEL_FLAG_COUNT = 5;
    /**
     * The labels of one capture case.
     */
    private static final int CAPTURE_LABEL_COUNT = 4;
    /**
     * The bytes of one captured label buffer.
     */
    private static final int CAPTURE_LABEL_BYTES = 100;
    /**
     * The bytes of one initial label buffer.
     */
    private static final int INITIAL_LABEL_BYTES = 99;
    /**
     * The byte that ends a captured label.
     */
    private static final int LABEL_TERMINATOR = 0;

    /**
     * The flags of the button nodes before a whole display refresh.
     */
    private static final int INITIAL_TAB_STOP_FLAG = 4;

    /**
     * The capture input of the current replay frame.
     */
    private static final int FRAME_INPUT = 0;
    /**
     * The capture input of the total replay frames.
     */
    private static final int TOTAL_FRAMES_INPUT = 1;
    /**
     * The capture input of the target replay frame.
     */
    private static final int TARGET_FRAME_INPUT = 2;
    /**
     * The capture input of the selected frame duration.
     */
    private static final int FRAME_PERIOD_INPUT = 3;
    /**
     * The capture input of the pause state.
     */
    private static final int PAUSED_INPUT = 4;
    /**
     * The capture input of the pause message state.
     */
    private static final int PAUSE_MESSAGE_INPUT = 5;
    /**
     * The capture input of the signed speed setting.
     */
    private static final int SPEED_SETTING_INPUT = 6;
    /**
     * The capture input of the replay speed multiplier.
     */
    private static final int REPLAY_SPEED_INPUT = 7;

    /**
     * The nodes of the replay panel: the root and its fifteen children.
     */
    private static final int PANEL_NODE_COUNT = 16;
    /**
     * The children of the replay panel, whose icons and button data the panel keeps.
     */
    private static final int PANEL_CHILD_COUNT = 15;
    /**
     * The identifier of the first button node.
     */
    private static final int FIRST_BUTTON = 1;
    /**
     * The identifier of the last button node.
     */
    private static final int LAST_BUTTON = 9;
    /**
     * The identifier of the first label node.
     */
    private static final int FIRST_LABEL_NODE = 10;
    /**
     * The node whose icon shows the replay progress.
     */
    private static final int PROGRESS_NODE = 11;
    /**
     * The ascending button order of the replay panel.
     */
    private static final int @NotNull [] BUTTON_ORDER = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    /**
     * The signed root bounds of an unshifted panel.
     */
    private static final int @NotNull [] ROOT_BOUNDS = {496, 354, 639, 479};
    /**
     * The focused child of a panel without focus.
     */
    private static final int NO_FOCUS = 0;
    /**
     * The hover child of labels initialized without a hover child.
     */
    private static final int NO_HOVER_CHILD = 0;
    /**
     * The case period of the cases whose label nodes start visible.
     */
    private static final int VISIBLE_CASE_PERIOD = 4;
    /**
     * The flags of a label node that starts hidden.
     */
    private static final int HIDDEN_LABEL_FLAGS = 0;
    /**
     * The period of the label visibility counters that start running.
     */
    private static final int RUNNING_COUNTER_PERIOD = 3;
    /**
     * The label visibility counter of a running counter.
     */
    private static final int RUNNING_COUNTER = 1;
    /**
     * The label visibility counter of a stopped counter.
     */
    private static final int STOPPED_COUNTER = 0;
    /**
     * The pointer coordinate of a pointer at the screen origin, outside the panel.
     */
    private static final int OUTSIDE_POINTER_COORDINATE = 0;

    /**
     * The identity of the replay panel root.
     */
    private static final int REPLAY_PANEL_IDENTITY = 15;
    /**
     * The value of a set panel redraw flag.
     */
    private static final int PANEL_DIRTY = 1;
    /**
     * The replay mode of a replay.
     */
    private static final int REPLAY_MODE = 1;
    /**
     * The empty button set.
     */
    private static final int EMPTY_BUTTON_SET = 0xE4;
    /**
     * The button visibility counters of the replay display.
     */
    private static final int REPLAY_VISIBILITY_COUNTER_COUNT = 10;
    /**
     * The refresh control of the panel redraw flag.
     */
    private static final int PANEL_DIRTY_CONTROL = 1;
    /**
     * The selection control of the current button set.
     */
    private static final int CURRENT_BUTTON_SET_CONTROL = 3;
    /**
     * The selection control of the displayed button set.
     */
    private static final int DISPLAYED_BUTTON_SET_CONTROL = 5;
    /**
     * The selection control of the hover identity.
     */
    private static final int HOVER_IDENTITY_CONTROL = 6;

    /**
     * Finds the end of a captured label, which a zero byte terminates unless it fills its buffer.
     *
     * @param buffer the captured label bytes
     * @param start  the offset of the label in the buffer
     * @return the offset of the terminator, or the end of the label buffer when it has none
     */
    private static int findLabelEnd(byte @NotNull [] buffer, int start) {
        int end = start;

        while (end < start + CAPTURE_LABEL_BYTES && buffer[end] != LABEL_TERMINATOR) {
            end++;
        }

        return end;
    }


    @Test
    void matches_native_replay_label_cases() throws Exception {
        this.compareLabels(false);
    }

    @Test
    void matches_native_replay_display_cases() throws Exception {
        this.compareLabels(true);
    }

    /**
     * Compares labels and callback state using the configured display caller.
     *
     * @param wholeDisplay whether to execute the complete display caller
     * @throws Exception            if a capture or the installed data cannot be read
     * @throws NullPointerException if the display state has no panel selection or display refresh state
     */
    private void compareLabels(boolean wholeDisplay) throws Exception {
        byte[] bytes = wholeDisplay ? GameTestResources.readNativeCapture(GameTestResources.CAPTURE_REPLAY_DISPLAY, DISPLAY_CAPTURE_HASH)
                                    : GameTestResources.readNativeCapture(GameTestResources.CAPTURE_REPLAY_LABEL, LABEL_CAPTURE_HASH);
        ByteBuffer records = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        String[] strings = {"Replay", "Time: %s", "Seeking: %s", "Speed", "Slowest", "Slower", "Slow", "Normal", "Fast", "Faster", "Fastest",
                            "Paused"};
        byte[][] localized = Arrays.stream(strings).map(value -> value.getBytes(StandardCharsets.ISO_8859_1)).toArray(byte[][]::new);

        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            for (int caseIndex = 0; caseIndex < CAPTURE_CASE_COUNT; caseIndex++) {
                int[] inputs = new int[CAPTURE_INPUT_COUNT];

                for (int index = 0; index < inputs.length; index++) {
                    inputs[index] = records.getInt();
                }

                int[] expectedFlags = new int[CAPTURE_LABEL_FLAG_COUNT];

                for (int index = 0; index < CAPTURE_LABEL_FLAG_COUNT; index++) {
                    expectedFlags[index] = records.getInt();
                }

                int expectedProgress = records.getInt();
                byte[] expectedBuffers = new byte[CAPTURE_LABEL_COUNT * CAPTURE_LABEL_BYTES];

                records.get(expectedBuffers);

                int[] flags = new int[PANEL_NODE_COUNT];

                if (wholeDisplay) {
                    Arrays.fill(flags, FIRST_BUTTON, StatusButtonPanel.BUTTON_SLOT_COUNT, INITIAL_TAB_STOP_FLAG);
                }

                Arrays.fill(flags, FIRST_LABEL_NODE, FIRST_LABEL_NODE + CAPTURE_LABEL_FLAG_COUNT,
                            caseIndex % VISIBLE_CASE_PERIOD == 0 ? StatusButtonPanel.VISIBLE_FLAG : HIDDEN_LABEL_FLAGS);

                StatusButtonPanel panel = new StatusButtonPanel(data.loadReplayStatusButtonDefinition(),
                                                                flags,
                                                                new int[PANEL_NODE_COUNT],
                                                                BUTTON_ORDER,
                                                                ROOT_BOUNDS,
                                                                NO_FOCUS,
                                                                new int[PANEL_CHILD_COUNT],
                                                                new long[PANEL_CHILD_COUNT],
                                                                new int[StatusButtonPanel.REFERENCE_COUNT],
                                                                new long[StatusButtonPanel.TOOLTIP_VALUE_COUNT]);

                byte[][] initialLabels = new byte[CAPTURE_LABEL_COUNT][INITIAL_LABEL_BYTES];

                for (byte[] label : initialLabels) {
                    Arrays.fill(label, (byte) 'Z');
                }

                ReplayStatusText labels = new ReplayStatusText(initialLabels, localized);

                int[] counters = new int[CAPTURE_LABEL_FLAG_COUNT];

                for (int index = 0; index < CAPTURE_LABEL_FLAG_COUNT; index++) {
                    counters[index] = (caseIndex + index) % RUNNING_COUNTER_PERIOD == 0 ? RUNNING_COUNTER : STOPPED_COUNTER;
                }

                byte[] previousText = "old".getBytes(StandardCharsets.ISO_8859_1);

                panel.initializeReplayLabels(labels, new byte[][]{previousText, previousText, previousText, previousText}, counters, NO_HOVER_CHILD);

                UnitPhase.Context context = new UnitPhase.Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER,
                                                                  UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER);

                if (wholeDisplay) {
                    context.getDisplayState().initializeDisplayRefresh(new long[]{REPLAY_PANEL_IDENTITY, PANEL_DIRTY, 0, 0, 0, 0});
                    context.getDisplayState().initializeEmptySelectionPanel(panel, new long[]{REPLAY_MODE, 0, 0,
                                                                                              EMPTY_BUTTON_SET, EMPTY_BUTTON_SET, EMPTY_BUTTON_SET,
                                                                                              0, 0, 0, REPLAY_PANEL_IDENTITY});
                    context.getDisplayState().initializeReplayDisplay(new int[]{inputs[PAUSED_INPUT], 0, 0, 0, 0, inputs[SPEED_SETTING_INPUT],
                                                                                inputs[REPLAY_SPEED_INPUT], 0, inputs[PAUSE_MESSAGE_INPUT]},
                                                                      new int[REPLAY_VISIBILITY_COUNTER_COUNT]);
                    context.getDisplayState().finishReplayDisplayPanelFrame(inputs[FRAME_INPUT], inputs[TOTAL_FRAMES_INPUT],
                                                                            inputs[TARGET_FRAME_INPUT],
                                                                            inputs[FRAME_PERIOD_INPUT]);
                } else {
                    panel.refreshReplayLabels(inputs[FRAME_INPUT], inputs[TOTAL_FRAMES_INPUT], inputs[TARGET_FRAME_INPUT],
                                              inputs[FRAME_PERIOD_INPUT],
                                              inputs[PAUSED_INPUT], inputs[PAUSE_MESSAGE_INPUT],
                                              inputs[SPEED_SETTING_INPUT], inputs[REPLAY_SPEED_INPUT],
                                              OUTSIDE_POINTER_COORDINATE, OUTSIDE_POINTER_COORDINATE);
                }

                for (int label = 0; label < CAPTURE_LABEL_COUNT; label++) {
                    int start = label * CAPTURE_LABEL_BYTES;
                    int end = findLabelEnd(expectedBuffers, start);

                    Assertions.assertArrayEquals(Arrays.copyOfRange(expectedBuffers, start, end), labels.getText(label), "label " + label);
                }

                Assertions.assertEquals(expectedProgress, panel.getIcon(PROGRESS_NODE), "progress case " + caseIndex);

                for (int index = 0; index < CAPTURE_LABEL_FLAG_COUNT; index++) {
                    Assertions.assertEquals(expectedFlags[index], panel.getFlags(index + FIRST_LABEL_NODE));
                }

                for (int index = 0; index < CAPTURE_LABEL_COUNT; index++) {
                    byte[] expectedText = new byte[CAPTURE_LABEL_BYTES];

                    records.get(expectedText);

                    int length = findLabelEnd(expectedText, 0);

                    Assertions.assertArrayEquals(Arrays.copyOf(expectedText, length), panel.getReplayLabelText(index), "text case " + caseIndex);
                }

                if (wholeDisplay) {
                    for (int index = FIRST_BUTTON; index <= LAST_BUTTON; index++) {
                        Assertions.assertEquals(records.getInt(), panel.getFlags(index));
                    }

                    for (int index = FIRST_BUTTON; index <= LAST_BUTTON; index++) {
                        Assertions.assertEquals(records.getInt(), panel.getStyle(index));
                    }

                    long[] selection = context.getDisplayState().getPanelSelectionState();

                    if (selection == null) {
                        throw Exceptions.newNullPointerException();
                    }

                    for (int index = CURRENT_BUTTON_SET_CONTROL; index <= DISPLAYED_BUTTON_SET_CONTROL; index++) {
                        Assertions.assertEquals(records.getInt(), selection[index]);
                    }

                    int expectedDisplayRefresh = records.getInt();
                    long[] displayRefresh = context.getDisplayState().getDisplayRefreshState();

                    if (displayRefresh == null) {
                        throw Exceptions.newNullPointerException();
                    }

                    Assertions.assertEquals(expectedDisplayRefresh, displayRefresh[PANEL_DIRTY_CONTROL]);
                    Assertions.assertEquals(records.getInt(), selection[HOVER_IDENTITY_CONTROL]);
                }
            }
        }

        Assertions.assertFalse(records.hasRemaining());
    }
}
