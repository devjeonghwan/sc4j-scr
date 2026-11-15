package org.fogofwarlabs.sc4j.scr.client.glue;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogDefinition;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.fogofwarlabs.sc4j.scr.game.presentation.InterfaceTimers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Slides glue panels in after a delay on a {@code 25}-millisecond timer, moving one quarter of the remaining distance and
 * snapping below four pixels.
 * Controls following the highest listed panel identifier remain hidden until the panels lock.
 * Leaving doubles the outward step each tick. Sounds play at the first move, lock and start of leaving.
 */
final class GlueSwish {
    private static final int TICK_MILLISECONDS = 25;
    /**
     * The swish timer identifier of the screen dialog ({@code 7}).
     */
    private static final int SWISH_TIMER = 7;
    /**
     * The divisor of the remaining distance a panel moves per entering tick.
     */
    private static final int ENTERING_STEP_DIVISOR = 4;
    private static final int SNAP_DISTANCE = 4;
    private static final int FIRST_OUT_STEP = 1;
    private static final int ENTRY_SIZE = 2;
    private static final int OUT_STEP_MULTIPLIER = 2;

    /**
     * The direction a panel comes from and leaves to.
     */
    enum Direction {
        /**
         * The panel enters from and leaves to the left.
         */
        LEFT,
        /**
         * The panel enters from and leaves to the top.
         */
        UP,
        /**
         * The panel enters from and leaves to the right.
         */
        RIGHT,
        /**
         * The panel enters from and leaves to the bottom.
         */
        DOWN;

        static @NotNull Direction of(int code) {
            return values()[Math.floorMod(code, values().length)];
        }
    }

    /**
     * The phase of the slide.
     */
    enum Phase {
        /**
         * No panel listed: the controls show at once.
         */
        NONE,
        /**
         * Waiting for the delay or moving in.
         */
        ENTERING,
        /**
         * In place; the controls show.
         */
        LOCKED,
        /**
         * Moving out.
         */
        LEAVING,
        /**
         * Out of the screen.
         */
        GONE
    }

    /**
     * One listed panel and its offset from its place.
     */
    private static final class Panel {
        private final @NotNull DialogControl control;
        private final @NotNull Direction direction;

        private int offsetX;
        private int offsetY;
        private int outStep;

        private Panel(@NotNull DialogControl control, @NotNull Direction direction) {
            this.control   = control;
            this.direction = direction;

            this.offsetX = 0;
            this.offsetY = 0;
            this.outStep = FIRST_OUT_STEP;
        }
    }

    /**
     * Finds the step a panel moves toward its place in one entering tick: the whole offset below the snap distance, a quarter of it otherwise.
     *
     * @param offset the panel's offset from its place on one axis
     * @return the step to add to the offset
     */
    private static int findEnteringStep(int offset) {
        return Math.abs(-offset) < SNAP_DISTANCE ? -offset : -offset / ENTERING_STEP_DIVISOR;
    }

    private final @NotNull List<Panel> panels;

    private int delayTicks;
    private int highestControl;
    private @NotNull Phase phase;
    private boolean moved;

    private boolean armed;
    private int interfaceWidth;
    private @Nullable GlueAudio audio;

    GlueSwish() {
        this.panels = new ArrayList<>();

        this.delayTicks     = 0;
        this.highestControl = Integer.MIN_VALUE;
        this.phase          = Phase.NONE;
        this.moved          = false;

        this.armed          = false;
        this.interfaceWidth = 0;
        this.audio          = null;
    }

    /**
     * @return the phase
     */
    @NotNull Phase getPhase() {
        return this.phase;
    }

    /**
     * @param control a control identifier
     * @return the control's horizontal offset from its place
     */
    int getOffsetX(int control) {
        Panel panel = this.findPanel(control);

        return panel == null ? 0 : panel.offsetX;
    }

    /**
     * @param control a control identifier
     * @return the control's vertical offset from its place
     */
    int getOffsetY(int control) {
        Panel panel = this.findPanel(control);

        return panel == null ? 0 : panel.offsetY;
    }

    /**
     * @param dialog       the dialog
     * @param controlIndex a child index of the dialog
     * @return {@code true} if the control is hidden by the slide: it comes after the listed panel with the highest identifier
     * and the panels are not locked, {@code false} otherwise
     */
    boolean isControlHidden(@NotNull DialogDefinition dialog, int controlIndex) {
        if (this.phase == Phase.NONE || this.phase == Phase.LOCKED) {
            return false;
        }

        List<DialogControl> controls = dialog.getControls();

        for (int index = 0; index < controlIndex; index++) {
            if (controls.get(index).getId() == this.highestControl) {
                return true;
            }
        }

        return false;
    }

    /**
     * Finds the first listed panel of a control.
     *
     * @param control a control identifier
     * @return the panel, or {@code null} if the control is not listed
     */
    private @Nullable Panel findPanel(int control) {
        for (Panel panel : this.panels) {
            if (panel.control.getId() == control) {
                return panel;
            }
        }

        return null;
    }


    /**
     * Lists the panels and moves them off-screen.
     *
     * @param dialog         the dialog
     * @param entries        pairs of control identifier and direction code
     * @param delay          the delay in milliseconds before the panels move
     * @param dialogLeft     the dialog's left edge
     * @param interfaceWidth the interface width
     */
    void startEntering(@NotNull DialogDefinition dialog, int @NotNull [] entries, int delay, int dialogLeft, int interfaceWidth) {
        this.panels.clear();

        for (int index = 0; index + 1 < entries.length; index += ENTRY_SIZE) {
            DialogControl control = ScrDialogControlUtils.findControl(dialog, entries[index]);

            if (control == null) {
                continue;
            }

            Panel panel = new Panel(control, Direction.of(entries[index + 1]));
            int right = control.getX() + control.getWidth() - 1;
            int bottom = control.getY() + control.getHeight() - 1;

            switch (panel.direction) {
                case LEFT -> panel.offsetX = -(dialogLeft + 1 + right);
                case UP -> panel.offsetY = -(bottom + 1);
                case RIGHT -> panel.offsetX = interfaceWidth - control.getX();
                case DOWN -> panel.offsetY = GlueCanvas.INTERFACE_HEIGHT - control.getY();
            }

            this.panels.add(panel);
            this.highestControl = Math.max(this.highestControl, control.getId());
        }

        this.delayTicks = delay / TICK_MILLISECONDS;
        this.phase      = this.panels.isEmpty() ? Phase.NONE : Phase.ENTERING;
        this.moved      = false;
    }

    /**
     * Starts leaving: the panels slide out.
     *
     * @param audio the glue audio
     * @return {@code true} if there are panels to slide, {@code false} otherwise
     */
    boolean startLeaving(@NotNull GlueAudio audio) {
        if (this.panels.isEmpty()) {
            this.phase = Phase.GONE;

            return false;
        }

        audio.stopSounds();
        audio.play(GlueAudio.Sound.SWISH_OUT);
        this.phase = Phase.LEAVING;

        return true;
    }

    /**
     * Runs the timer up to a time.
     *
     * @param time           the time in milliseconds
     * @param interfaceWidth the interface width
     * @param audio          the glue audio
     * @param timers         the client's timer list
     */
    void update(long time, int interfaceWidth, @NotNull GlueAudio audio, @NotNull InterfaceTimers timers) {
        this.interfaceWidth = interfaceWidth;
        this.audio          = audio;

        boolean running = this.phase == Phase.ENTERING || this.phase == Phase.LEAVING;

        if (running && !this.armed) {
            // The 25 ms swish timer 7 in the client's timer list; the list's pump fires it at most once per frame.
            timers.arm(this, SWISH_TIMER, TICK_MILLISECONDS, (int) time, now -> this.tick(timers));
            this.armed = true;
        } else if (!running && this.armed) {
            timers.kill(this, SWISH_TIMER);
            this.armed = false;
        }
    }

    private void tick(@NotNull InterfaceTimers timers) {
        GlueAudio output = this.audio;

        if (output == null) {
            return;
        }

        if (this.phase == Phase.ENTERING) {
            this.advanceEntering(output);
        } else if (this.phase == Phase.LEAVING) {
            this.advanceLeaving();
        }

        if (this.phase != Phase.ENTERING && this.phase != Phase.LEAVING) {
            timers.kill(this, SWISH_TIMER);
            this.armed = false;
        }
    }


    private void advanceEntering(@NotNull GlueAudio audio) {
        if (this.delayTicks > 0) {
            this.delayTicks--;

            return;
        }

        if (!this.moved) {
            this.moved = true;
            audio.play(GlueAudio.Sound.SWISH_IN);
        }

        boolean moving = false;

        for (Panel panel : this.panels) {
            if (panel.offsetX != 0 || panel.offsetY != 0) {
                moving = true;
            }

            panel.offsetX += findEnteringStep(panel.offsetX);
            panel.offsetY += findEnteringStep(panel.offsetY);
        }

        if (moving) {
            return;
        }

        audio.play(GlueAudio.Sound.SWISH_LOCK);
        this.phase = Phase.LOCKED;
    }

    private void advanceLeaving() {
        boolean gone = true;

        for (Panel panel : this.panels) {
            DialogControl control = panel.control;
            int left = control.getX() + panel.offsetX;
            int top = control.getY() + panel.offsetY;
            int right = left + control.getWidth() - 1;
            int bottom = top + control.getHeight() - 1;
            boolean out = switch (panel.direction) {
                case LEFT -> right <= 0;
                case UP -> bottom <= 0;
                case RIGHT -> left >= this.interfaceWidth;
                case DOWN -> top >= GlueCanvas.INTERFACE_HEIGHT;
            };

            gone &= out;

            switch (panel.direction) {
                case LEFT -> panel.offsetX -= panel.outStep;
                case UP -> panel.offsetY -= panel.outStep;
                case RIGHT -> panel.offsetX += panel.outStep;
                case DOWN -> panel.offsetY += panel.outStep;
            }

            panel.outStep *= OUT_STEP_MULTIPLIER;
        }

        if (gone) {
            this.phase = Phase.GONE;
        }
    }


    /**
     * Removes the swish timer (the screen closes).
     *
     * @param timers the client's timer list
     */
    void close(@NotNull InterfaceTimers timers) {
        timers.killAll(this);
        this.armed = false;
    }
}
