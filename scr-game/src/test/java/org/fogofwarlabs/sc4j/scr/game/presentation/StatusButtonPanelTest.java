package org.fogofwarlabs.sc4j.scr.game.presentation;

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
import java.util.Arrays;

/**
 * Checks status-panel callbacks, replay controls and inclusive pointer bounds.
 */
final class StatusButtonPanelTest {
    /**
     * The SHA-256 digest of the empty panel callback capture.
     */
    private static final @NotNull String EMPTY_PANEL_CAPTURE_HASH = "7407d51f153d413bec5a1940b19422512b1a956384821dcce7700127432db969";
    /**
     * The SHA-256 digest of the installed button callback capture.
     */
    private static final @NotNull String INSTALLED_CALLBACK_CAPTURE_HASH = "565c3448e6421eb9841e8d5e46da8f8ddb1e6aaaf71d328b23386279c6975458";
    /**
     * The SHA-256 digest of the replay button condition capture.
     */
    private static final @NotNull String CONDITION_CAPTURE_HASH = "e9a446c813e216702431c942d84b9d20dd314324730ad06a46142ac25030408d";
    /**
     * The SHA-256 digest of the replay button presentation capture.
     */
    private static final @NotNull String PRESENTATION_CAPTURE_HASH = "a11ff72339d3b8351341cf035a520638f7402591d70715677956aad03682d868";
    /**
     * The SHA-256 digest of the hidden statistics panel capture.
     */
    private static final @NotNull String HIDDEN_STATISTICS_CAPTURE_HASH = "2dd697222f3b2b8725e678b26b4f5b2d66b96f4432628b52a16f5c9b416a7c60";

    /**
     * The cases of the replay button condition capture.
     */
    private static final int CONDITION_CASE_COUNT = 2048;
    /**
     * The cases of each callback capture.
     */
    private static final int CALLBACK_CASE_COUNT = 512;
    /**
     * The children of the statistics panel.
     */
    private static final int CAPTURE_STATISTICS_CHILD_COUNT = 57;
    /**
     * The nodes of the empty panel: the root and its ten children.
     */
    private static final int CAPTURE_EMPTY_NODE_COUNT = 11;
    /**
     * The nodes of the replay panel: the root and its fifteen children.
     */
    private static final int CAPTURE_REPLAY_NODE_COUNT = 16;
    /**
     * The children of the replay panel, whose icons and button data the panel keeps.
     */
    private static final int CAPTURE_REPLAY_ICON_COUNT = 15;
    /**
     * The signed root bounds of one callback case.
     */
    private static final int CAPTURE_BOUND_COUNT = 4;
    /**
     * The four label strings of the replay panel.
     */
    private static final int REPLAY_LABEL_COUNT = 4;
    /**
     * The five label visibility counters of the replay panel.
     */
    private static final int REPLAY_LABEL_COUNTER_COUNT = 5;
    /**
     * The twelve localization results of the replay panel.
     */
    private static final int REPLAY_LOCALIZED_TEXT_COUNT = 12;
    /**
     * The bytes of every replacement label and localization buffer.
     */
    private static final int REPLACEMENT_LABEL_BYTES = 1;
    /**
     * A hover child identity of the replay panel's label slots.
     */
    private static final int REPLAY_LABEL_HOVER_CHILD = 12;
    /**
     * The ascending button order of an unshifted panel.
     */
    private static final int @NotNull [] BUTTON_ORDER = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    /**
     * The signed root bounds of an unshifted panel.
     */
    private static final int @NotNull [] ROOT_BOUNDS = {496, 354, 639, 479};

    /**
     * The identity of the empty panel root, which a pointer inside the root hits.
     */
    private static final int DEFAULT_ROOT = 10;
    /**
     * The root flags of the empty panel after the startup.
     */
    private static final int INITIAL_ROOT_FLAGS = 0x40000802;
    /**
     * The identifier of the first button node.
     */
    private static final int FIRST_BUTTON = 1;
    /**
     * The identifier of the first button node whose replay condition the capture records.
     */
    private static final int FIRST_CONDITION_BUTTON = 7;
    /**
     * The identifier of the last button node.
     */
    private static final int LAST_BUTTON = 9;
    /**
     * The tab stop node flag.
     */
    private static final int TAB_STOP_FLAG = 0x4;
    /**
     * The icon of a button without an icon.
     */
    private static final int NO_ICON = 0xFFFF;
    /**
     * The focused child of a panel without focus.
     */
    private static final int NO_FOCUS = 0;
    /**
     * The hover child of labels initialized without a hover child.
     */
    private static final int NO_HOVER_CHILD = 0;
    /**
     * The horizontal placement offset of an unshifted panel.
     */
    private static final int NO_HORIZONTAL_OFFSET = 0;
    /**
     * The first reference after the button references, which the startup keeps.
     */
    private static final int KEPT_REFERENCE = 9;
    /**
     * The value of {@link #KEPT_REFERENCE} supplied to the startup.
     */
    private static final int KEPT_REFERENCE_VALUE = 10;
    /**
     * The tooltip values supplied to the startup, distinct and nonzero.
     */
    private static final long @NotNull [] STARTUP_TOOLTIP_VALUES = {1, 2, 3, 4, 5, 6, 7};
    /**
     * The index of the last tooltip value.
     */
    private static final int LAST_TOOLTIP = 6;
    /**
     * The last tooltip value supplied to the startup.
     */
    private static final int LAST_TOOLTIP_VALUE = 7;
    /**
     * The button data and reference of a button slot that the startup clears.
     */
    private static final int CLEARED_SLOT_VALUE = 0;
    /**
     * The tooltip value that a hit test clears.
     */
    private static final int CLEARED_TOOLTIP_VALUE = 0;
    /**
     * The right edge of the root bounds, which belongs to the root.
     */
    private static final int ROOT_RIGHT = 639;
    /**
     * The bottom edge of the root bounds, which belongs to the root.
     */
    private static final int ROOT_BOTTOM = 479;
    /**
     * The horizontal pointer coordinate just past the root.
     */
    private static final int PAST_ROOT_RIGHT = 640;
    /**
     * The vertical pointer coordinate just past the root.
     */
    private static final int PAST_ROOT_BOTTOM = 480;
    /**
     * The hit result of a pointer outside the root.
     */
    private static final int NO_HIT = 0;
    /**
     * The pointer coordinate of a pointer at the screen origin, outside the root.
     */
    private static final int OUTSIDE_POINTER_COORDINATE = 0;
    /**
     * The command state of the presentation cases.
     */
    private static final int COMMAND_STATE = 0;

    /**
     * The identity of the panel refreshed by the fixture display state.
     */
    private static final int FIXTURE_PANEL_IDENTITY = 12345;
    /**
     * The statistics root identity of the hidden statistics cases.
     */
    private static final int STATISTICS_ROOT_IDENTITY = 54321;
    /**
     * The value of a set redraw or refresh flag.
     */
    private static final int SET_FLAG = 1;
    /**
     * The value of a clear redraw or refresh flag.
     */
    private static final int CLEAR_FLAG = 0;
    /**
     * The replay mode of a replay.
     */
    private static final int REPLAY_MODE = 1;
    /**
     * The empty button set.
     */
    private static final int EMPTY_BUTTON_SET = 0xE4;
    /**
     * A hover identity that the clean refresh keeps.
     */
    private static final int STALE_HOVER_IDENTITY = 999;
    /**
     * The hover identity of a selection without a hovered node.
     */
    private static final int NO_HOVER_IDENTITY = 0;
    /**
     * The refresh control of the panel redraw flag.
     */
    private static final int PANEL_DIRTY_CONTROL = 1;
    /**
     * The refresh control of the statistics redraw flag.
     */
    private static final int STATISTICS_DIRTY_CONTROL = 3;
    /**
     * The selection control of the hover identity.
     */
    private static final int HOVER_IDENTITY_CONTROL = 6;

    /**
     * Reads fixture QWORDs and narrows them to the original signed integer fields.
     *
     * @param records the recorded values
     * @param count   the number of fields to read
     * @return the decoded integer fields
     */
    private static int @NotNull [] readIntegers(@NotNull ByteBuffer records, int count) {
        int[] values = new int[count];

        for (int index = 0; index < count; index++) {
            values[index] = (int) records.getLong();
        }

        return values;
    }


    @RequiresInstallation
    @Test
    void matches_native_empty_panel_callback_cases() throws Exception {
        this.comparePanelCases(GameTestResources.CAPTURE_EMPTY_STATUS_PANEL, EMPTY_PANEL_CAPTURE_HASH);
    }

    @RequiresInstallation
    @Test
    void matches_native_installed_button_callback_cases() throws Exception {
        this.comparePanelCases(GameTestResources.CAPTURE_INSTALLED_STATUS_PANEL_CALLBACK, INSTALLED_CALLBACK_CAPTURE_HASH);
    }

    @RequiresInstallation
    @Test
    void startup_panel_clears_button_slots_and_hit_tests_inclusive_bounds() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            int[] references = new int[StatusButtonPanel.REFERENCE_COUNT];

            for (int index = 0; index < references.length; index++) {
                references[index] = index + 1;
            }

            StatusButtonPanel panel = StatusButtonPanel.createAfterStartup(data.loadStatusButtonDefinition(),
                                                                           NO_HORIZONTAL_OFFSET,
                                                                           references,
                                                                           STARTUP_TOOLTIP_VALUES);

            Assertions.assertEquals(INITIAL_ROOT_FLAGS, panel.getFlags(DEFAULT_ROOT));
            Assertions.assertEquals(NO_FOCUS, panel.getFocusedChild());

            for (int identifier = FIRST_BUTTON; identifier <= LAST_BUTTON; identifier++) {
                Assertions.assertEquals(TAB_STOP_FLAG, panel.getFlags(identifier));
                Assertions.assertEquals(NO_ICON, panel.getIcon(identifier));
                Assertions.assertEquals(CLEARED_SLOT_VALUE, panel.getButtonData(identifier));
                Assertions.assertEquals(CLEARED_SLOT_VALUE, panel.getReference(identifier - 1));
            }

            Assertions.assertEquals(KEPT_REFERENCE_VALUE, panel.getReference(KEPT_REFERENCE));
            Assertions.assertEquals(LAST_TOOLTIP_VALUE, panel.getTooltipValue(LAST_TOOLTIP));
            Assertions.assertEquals(NO_HIT, panel.refreshEmptySelection(PAST_ROOT_RIGHT, ROOT_BOTTOM));
            Assertions.assertEquals(NO_HIT, panel.refreshEmptySelection(ROOT_RIGHT, PAST_ROOT_BOTTOM));
            Assertions.assertEquals(DEFAULT_ROOT, panel.refreshEmptySelection(ROOT_RIGHT, ROOT_BOTTOM));
            Assertions.assertEquals(CLEARED_TOOLTIP_VALUE, panel.getTooltipValue(LAST_TOOLTIP));
        }
    }

    @Test
    void matches_native_replay_button_condition_cases() throws Exception {
        byte[] bytes = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_REPLAY_BUTTON_CONDITION, CONDITION_CAPTURE_HASH);
        ByteBuffer records = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        for (int index = 0; index < CONDITION_CASE_COUNT; index++) {
            int replayMode = records.getInt();
            int networkGame = records.getInt();
            int localPlayer = records.getInt();
            int participatingPlayerMask = records.getInt();
            int speedSetting = records.getInt();
            int replaySpeed = records.getInt();

            for (int identifier = FIRST_CONDITION_BUTTON; identifier <= LAST_BUTTON; identifier++) {
                Assertions.assertEquals(records.getInt(),
                                        StatusButtonPanel.evaluateReplayButtonCondition(identifier,
                                                                                        replayMode,
                                                                                        networkGame, localPlayer, participatingPlayerMask,
                                                                                        speedSetting, replaySpeed),
                                        "Replay condition case " + index + ":" + identifier);
            }
        }

        Assertions.assertFalse(records.hasRemaining());
    }

    @RequiresInstallation
    @Test
    void matches_native_replay_button_presentation_cases() throws Exception {
        byte[] bytes = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_REPLAY_BUTTON_PRESENTATION, PRESENTATION_CAPTURE_HASH);
        ByteBuffer records = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            byte[] definition = data.loadReplayStatusButtonDefinition();

            for (int index = 0; index < CALLBACK_CASE_COUNT; index++) {
                int paused = (int) records.getLong();
                int hideButtons = (int) records.getLong();
                int networkGame = (int) records.getLong();
                int localPlayer = (int) records.getLong();
                int participatingPlayerMask = (int) records.getLong();
                int speedSetting = (int) records.getLong();
                int replaySpeed = (int) records.getLong();

                int[] flags = readIntegers(records, CAPTURE_REPLAY_NODE_COUNT);
                int[] counters = readIntegers(records, StatusButtonPanel.BUTTON_SLOT_COUNT);
                int[] references = readIntegers(records, StatusButtonPanel.REFERENCE_COUNT);

                int[] icons = new int[CAPTURE_REPLAY_ICON_COUNT];

                Arrays.fill(icons, FIRST_BUTTON, StatusButtonPanel.BUTTON_SLOT_COUNT, NO_ICON);

                StatusButtonPanel panel = new StatusButtonPanel(definition,
                                                                flags,
                                                                new int[CAPTURE_REPLAY_NODE_COUNT],
                                                                BUTTON_ORDER,
                                                                ROOT_BOUNDS,
                                                                NO_FOCUS,
                                                                icons,
                                                                new long[CAPTURE_REPLAY_ICON_COUNT],
                                                                references,
                                                                new long[StatusButtonPanel.TOOLTIP_VALUE_COUNT]);

                panel.refreshReplayButtons(paused,
                                           hideButtons,
                                           networkGame, localPlayer, participatingPlayerMask,
                                           speedSetting, replaySpeed,
                                           COMMAND_STATE,
                                           counters,
                                           OUTSIDE_POINTER_COORDINATE, OUTSIDE_POINTER_COORDINATE);

                for (int identifier = 0; identifier < CAPTURE_REPLAY_NODE_COUNT; identifier++) {
                    Assertions.assertEquals((int) records.getLong(), panel.getFlags(identifier), "Replay flags " + index + ":" + identifier);
                }

                for (int identifier = 0; identifier < CAPTURE_REPLAY_NODE_COUNT; identifier++) {
                    Assertions.assertEquals((int) records.getLong(), panel.getStyle(identifier), "Replay styles " + index + ":" + identifier);
                }

                for (int identifier = 0; identifier < CAPTURE_REPLAY_ICON_COUNT; identifier++) {
                    Assertions.assertEquals((int) records.getLong(), panel.getIcon(identifier), "Replay icons " + index + ":" + identifier);
                }

                for (int identifier = 0; identifier < CAPTURE_REPLAY_ICON_COUNT; identifier++) {
                    Assertions.assertEquals(records.getLong(), panel.getButtonData(identifier), "Replay data " + index + ":" + identifier);
                }

                for (int position = 0; position < StatusButtonPanel.REFERENCE_COUNT; position++) {
                    Assertions.assertEquals((int) records.getLong(), panel.getReference(position), "Replay reference " + index + ":" + position);
                }
            }
        }

        Assertions.assertFalse(records.hasRemaining());
    }

    @RequiresInstallation
    @Test
    void matches_native_hidden_statistics_panel_cases() throws Exception {
        byte[] bytes = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_HIDDEN_STATISTICS_PANEL, HIDDEN_STATISTICS_CAPTURE_HASH);
        ByteBuffer records = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            StatusButtonPanel panel = new StatusButtonPanel(data.loadReplayStatusButtonDefinition(),
                                                            new int[CAPTURE_REPLAY_NODE_COUNT],
                                                            new int[CAPTURE_REPLAY_NODE_COUNT],
                                                            BUTTON_ORDER,
                                                            ROOT_BOUNDS,
                                                            NO_FOCUS,
                                                            new int[CAPTURE_REPLAY_ICON_COUNT],
                                                            new long[CAPTURE_REPLAY_ICON_COUNT],
                                                            new int[StatusButtonPanel.REFERENCE_COUNT],
                                                            new long[StatusButtonPanel.TOOLTIP_VALUE_COUNT]);

            for (int index = 0; index < CALLBACK_CASE_COUNT; index++) {
                int dirty = records.getInt();
                int secondaryDirty = records.getInt();
                int[] childFlags = new int[CAPTURE_STATISTICS_CHILD_COUNT];

                for (int child = 0; child < childFlags.length; child++) {
                    childFlags[child] = records.getInt();
                }

                UnitPhase.Context context = new UnitPhase.Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER,
                                                                  UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER);

                context.getDisplayState().initializeDisplayRefresh(new long[]{FIXTURE_PANEL_IDENTITY, SET_FLAG, SET_FLAG, dirty, 0, 0});
                context.getDisplayState().initializeEmptySelectionPanel(panel, new long[]{REPLAY_MODE, 0, 0,
                                                                                          EMPTY_BUTTON_SET, EMPTY_BUTTON_SET, EMPTY_BUTTON_SET,
                                                                                          0, 0, 0, FIXTURE_PANEL_IDENTITY});
                context.getDisplayState().initializeEmptySelectionStatistics(STATISTICS_ROOT_IDENTITY, childFlags, secondaryDirty);

                context.getDisplayState().refreshEmptySelectionStatistics();

                int expectedDirty = records.getInt();
                long[] displayRefresh = context.getDisplayState().getDisplayRefreshState();

                Assertions.assertNotNull(displayRefresh);

                Assertions.assertEquals(expectedDirty, displayRefresh[STATISTICS_DIRTY_CONTROL], "Statistics dirty " + index);
                Assertions.assertEquals(records.getInt(), context.getDisplayState().getStatisticsSecondaryDirty(), "Statistics secondary " + index);

                childFlags[0] |= StatusButtonPanel.VISIBLE_FLAG;

                context.getDisplayState().initializeEmptySelectionStatistics(STATISTICS_ROOT_IDENTITY, childFlags, secondaryDirty);

                context.getDisplayState().initializeDisplayRefresh(new long[]{FIXTURE_PANEL_IDENTITY, SET_FLAG, SET_FLAG, SET_FLAG, 0, 0});

                long[] rejectedDisplay = context.getDisplayState().getDisplayRefreshState();
                long[] rejectedSelection = context.getDisplayState().getPanelSelectionState();
                int retainedSecondaryDirty = context.getDisplayState().getStatisticsSecondaryDirty();

                Assertions.assertNotNull(rejectedDisplay);
                Assertions.assertNotNull(rejectedSelection);

                Assertions.assertThrows(UnsupportedOperationException.class, context.getDisplayState()::refreshEmptySelectionStatistics);
                Assertions.assertArrayEquals(rejectedDisplay, context.getDisplayState().getDisplayRefreshState());
                Assertions.assertArrayEquals(rejectedSelection, context.getDisplayState().getPanelSelectionState());
                Assertions.assertEquals(retainedSecondaryDirty, context.getDisplayState().getStatisticsSecondaryDirty());
            }
        }

        Assertions.assertFalse(records.hasRemaining());
    }

    @RequiresInstallation
    @Test
    void initialize_replay_labels_should_keep_the_previous_labels_when_a_node_string_is_missing() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            StatusButtonPanel panel = new StatusButtonPanel(data.loadReplayStatusButtonDefinition(),
                                                            new int[CAPTURE_REPLAY_NODE_COUNT],
                                                            new int[CAPTURE_REPLAY_NODE_COUNT],
                                                            BUTTON_ORDER,
                                                            ROOT_BOUNDS,
                                                            NO_FOCUS,
                                                            new int[CAPTURE_REPLAY_ICON_COUNT],
                                                            new long[CAPTURE_REPLAY_ICON_COUNT],
                                                            new int[StatusButtonPanel.REFERENCE_COUNT],
                                                            new long[StatusButtonPanel.TOOLTIP_VALUE_COUNT]);
            byte[][] text = {{'a'}, {'b'}, {'c'}, {'d'}};
            byte[][] incompleteText = {{'e'}, null, {'g'}, {'h'}};

            panel.initializeReplayLabels(new ReplayStatusText(new byte[REPLAY_LABEL_COUNT][0], new byte[REPLAY_LOCALIZED_TEXT_COUNT][0]),
                                         text,
                                         new int[REPLAY_LABEL_COUNTER_COUNT],
                                         NO_HOVER_CHILD);

            ReplayStatusText replacement = new ReplayStatusText(new byte[REPLAY_LABEL_COUNT][REPLACEMENT_LABEL_BYTES],
                                                                new byte[REPLAY_LOCALIZED_TEXT_COUNT][REPLACEMENT_LABEL_BYTES]);

            RuntimeException exception = Assertions.assertThrows(RuntimeException.class,
                                                                 () -> panel.initializeReplayLabels(replacement,
                                                                                                    incompleteText,
                                                                                                    new int[REPLAY_LABEL_COUNTER_COUNT],
                                                                                                    REPLAY_LABEL_HOVER_CHILD));

            for (int index = 0; index < REPLAY_LABEL_COUNT; index++) {
                Assertions.assertArrayEquals(text[index], panel.getReplayLabelText(index), "Replay label " + index);
            }

            Assertions.assertInstanceOf(IllegalArgumentException.class, exception);
        }
    }

    /**
     * Compares the complete empty-panel callback state with the fixture records.
     *
     * @param resource     the fixture resource
     * @param expectedHash the fixture byte digest
     * @throws Exception if the capture or the installed data cannot be read
     */
    private void comparePanelCases(@NotNull String resource, @NotNull String expectedHash) throws Exception {
        byte[] bytes = GameTestResources.readNativeCapture(resource, expectedHash);
        ByteBuffer records = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            byte[] definition = data.loadStatusButtonDefinition();

            for (int index = 0; index < CALLBACK_CASE_COUNT; index++) {
                int[] flags = readIntegers(records, CAPTURE_EMPTY_NODE_COUNT);
                int[] styles = readIntegers(records, CAPTURE_EMPTY_NODE_COUNT);
                int[] order = readIntegers(records, StatusButtonPanel.BUTTON_COUNT);
                int[] bounds = readIntegers(records, CAPTURE_BOUND_COUNT);
                int focus = (int) records.getLong();
                int[] references = readIntegers(records, StatusButtonPanel.REFERENCE_COUNT);
                long[] tooltip = new long[StatusButtonPanel.TOOLTIP_VALUE_COUNT];

                for (int position = 0; position < tooltip.length; position++) {
                    tooltip[position] = records.getLong();
                }

                int[] icons = readIntegers(records, StatusButtonPanel.BUTTON_SLOT_COUNT);
                long[] buttonData = new long[StatusButtonPanel.BUTTON_SLOT_COUNT];

                for (int position = 0; position < buttonData.length; position++) {
                    buttonData[position] = records.getLong();
                }

                int pointerX = (int) records.getLong();
                int pointerY = (int) records.getLong();

                StatusButtonPanel panel = new StatusButtonPanel(definition,
                                                                flags,
                                                                styles,
                                                                order,
                                                                bounds,
                                                                focus,
                                                                icons,
                                                                buttonData,
                                                                references,
                                                                tooltip);

                UnitPhase.Context context = new UnitPhase.Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER,
                                                                  UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER);

                context.getDisplayState().initializeDisplayRefresh(new long[]{FIXTURE_PANEL_IDENTITY, 0, 0, 0, 0, 0});
                context.getDisplayState().initializeEmptySelectionPanel(panel, new long[]{0, 0, 0,
                                                                                          EMPTY_BUTTON_SET, EMPTY_BUTTON_SET, EMPTY_BUTTON_SET,
                                                                                          STALE_HOVER_IDENTITY, pointerX, pointerY,
                                                                                          FIXTURE_PANEL_IDENTITY});

                long[] initialDisplay = context.getDisplayState().getDisplayRefreshState();
                long[] initialSelection = context.getDisplayState().getPanelSelectionState();

                Assertions.assertNotNull(initialDisplay);
                Assertions.assertNotNull(initialSelection);

                context.getDisplayState().refreshDisplayPanel();

                Assertions.assertArrayEquals(initialDisplay, context.getDisplayState().getDisplayRefreshState(),
                                             "Clean panel remains unchanged " + index);
                Assertions.assertArrayEquals(initialSelection, context.getDisplayState().getPanelSelectionState());

                long[] cleanSelection = context.getDisplayState().getPanelSelectionState();

                Assertions.assertNotNull(cleanSelection);

                Assertions.assertEquals(STALE_HOVER_IDENTITY, cleanSelection[HOVER_IDENTITY_CONTROL]);

                context.getDisplayState().initializeDisplayRefresh(new long[]{FIXTURE_PANEL_IDENTITY, SET_FLAG, 0, 0, 0, 0});
                context.getDisplayState().refreshDisplayPanel();

                long[] refreshedDisplay = context.getDisplayState().getDisplayRefreshState();

                Assertions.assertNotNull(refreshedDisplay);

                Assertions.assertEquals(SET_FLAG, refreshedDisplay[PANEL_DIRTY_CONTROL]);

                long[] refreshedSelection = context.getDisplayState().getPanelSelectionState();

                Assertions.assertNotNull(refreshedSelection);

                int hit = refreshedSelection[HOVER_IDENTITY_CONTROL] == NO_HOVER_IDENTITY ? NO_HIT : DEFAULT_ROOT;

                for (int identifier = 0; identifier < CAPTURE_EMPTY_NODE_COUNT; identifier++) {
                    Assertions.assertEquals((int) records.getLong(), panel.getFlags(identifier), "Flags case " + index + ":" + identifier);
                }

                for (int identifier = 0; identifier < StatusButtonPanel.BUTTON_SLOT_COUNT; identifier++) {
                    Assertions.assertEquals((int) records.getLong(), panel.getIcon(identifier), "Icon case " + index + ":" + identifier);
                }

                for (int identifier = 0; identifier < StatusButtonPanel.BUTTON_SLOT_COUNT; identifier++) {
                    Assertions.assertEquals(records.getLong(), panel.getButtonData(identifier), "Button data case " + index + ":" + identifier);
                }

                Assertions.assertEquals((int) records.getLong(), panel.getFocusedChild(), "Focus case " + index);

                for (int position = 0; position < StatusButtonPanel.REFERENCE_COUNT; position++) {
                    Assertions.assertEquals((int) records.getLong(), panel.getReference(position), "Reference case " + index + ":" + position);
                }

                for (int position = 0; position < StatusButtonPanel.TOOLTIP_VALUE_COUNT; position++) {
                    Assertions.assertEquals(records.getLong(), panel.getTooltipValue(position), "Tooltip case " + index + ":" + position);
                }

                Assertions.assertEquals((int) records.getLong(), hit, "Hit case " + index);

                context.getDisplayState().finishDisplayPanelFrame();

                long[] finishedDisplay = context.getDisplayState().getDisplayRefreshState();

                Assertions.assertNotNull(finishedDisplay);

                Assertions.assertEquals(CLEAR_FLAG, finishedDisplay[PANEL_DIRTY_CONTROL]);

                long[] finishedSelection = context.getDisplayState().getPanelSelectionState();

                Assertions.assertNotNull(finishedSelection);

                context.getDisplayState().finishDisplayPanelFrame();

                Assertions.assertArrayEquals(finishedDisplay, context.getDisplayState().getDisplayRefreshState());
                Assertions.assertArrayEquals(finishedSelection, context.getDisplayState().getPanelSelectionState());
            }
        }

        Assertions.assertFalse(records.hasRemaining());
    }
}
