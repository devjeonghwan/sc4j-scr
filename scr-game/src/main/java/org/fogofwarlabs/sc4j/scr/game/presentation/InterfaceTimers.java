package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Schedules the repeating interface timers shared by the client's dialogs, controls and briefing slots. Owners are compared by identity and can
 * remove all their timers when they close.
 * <p>
 * Arming replaces an existing timer or allocates one and sorts it by the wrapping due-time key; newly armed timers precede equal keys. A pump fires
 * only timers strictly past their deadline, at most once each, and preserves the saved successor across callbacks before reinserting surviving fired
 * timers.
 */
public final class InterfaceTimers {
    /**
     * The callback of one timer.
     */
    public interface Callback {
        /**
         * Runs when the timer is due.
         *
         * @param hostTimeMilliseconds the host millisecond clock of the pump
         */
        void onTimer(int hostTimeMilliseconds);
    }

    /**
     * One repeating timer node.
     */
    private static final class Timer {
        private final @NotNull Object owner;
        private final int identifier;
        private @NotNull Callback callback;

        private int start;
        private int interval;
        private @Nullable Timer next;

        private Timer(@NotNull Object owner, int identifier, @NotNull Callback callback) {
            this.owner      = owner;
            this.identifier = identifier;
            this.callback   = callback;

            this.start    = 0;
            this.interval = 0;
            this.next     = null;
        }
    }

    private static @Nullable Timer find(@Nullable Timer head, @NotNull Object owner) {
        for (Timer current = head; current != null; current = current.next) {
            if (current.owner == owner) {
                return current;
            }
        }

        return null;
    }

    private static @Nullable Timer find(@Nullable Timer head, @NotNull Object owner, int identifier) {
        for (Timer current = head; current != null; current = current.next) {
            if (current.owner == owner && current.identifier == identifier) {
                return current;
            }
        }

        return null;
    }

    private @Nullable Timer pendingHead;
    private @Nullable Timer firedHead;

    /**
     * Creates an {@link InterfaceTimers} instance with empty timer lists.
     */
    public InterfaceTimers() {
        this.pendingHead = null;
        this.firedHead   = null;
    }

    /**
     * @param owner      the owning dialog or control
     * @param identifier the timer identifier
     * @return {@code true} if the timer is armed, {@code false} otherwise
     */
    public boolean isArmed(@NotNull Object owner, int identifier) {
        return find(this.pendingHead, owner, identifier) != null || find(this.firedHead, owner, identifier) != null;
    }

    /**
     * @param owner      the owning dialog or control
     * @param identifier the timer identifier
     * @return the start of the armed timer (the clock of its arming or last firing)
     * @throws IllegalStateException if the timer is not armed
     */
    public int getStart(@NotNull Object owner, int identifier) {
        Timer timer = find(this.pendingHead, owner, identifier);

        if (timer == null) {
            timer = find(this.firedHead, owner, identifier);
        }

        if (timer == null) {
            throw Exceptions.newIllegalStateException("The timer {0} of {1} is not armed.", identifier, owner);
        }

        return timer.start;
    }


    /**
     * Arms or re-arms a repeating timer.
     *
     * @param owner                the owning dialog or control
     * @param identifier           the timer identifier
     * @param interval             the interval in milliseconds
     * @param hostTimeMilliseconds the host millisecond clock
     * @param callback             the callback the timer runs
     */
    public void arm(@NotNull Object owner, int identifier, int interval, int hostTimeMilliseconds, @NotNull Callback callback) {
        Timer timer = this.unlinkPending(owner, identifier);

        if (timer == null) {
            timer = this.unlinkFired(owner, identifier);
        }

        if (timer == null) {
            timer = new Timer(owner, identifier, callback);
        }

        timer.callback = callback;
        timer.interval = interval;
        timer.start    = hostTimeMilliseconds;

        this.insertSorted(timer);
    }

    /**
     * Kills a timer; ignores a timer that is not armed.
     *
     * @param owner      the owning dialog or control
     * @param identifier the timer identifier
     */
    public void kill(@NotNull Object owner, int identifier) {
        if (this.unlinkPending(owner, identifier) == null) {
            this.unlinkFired(owner, identifier);
        }
    }

    /**
     * Kills every timer of an owner (a dialog or control that closes).
     *
     * @param owner the owning dialog or control
     */
    public void killAll(@NotNull Object owner) {
        Timer timer;

        while ((timer = find(this.pendingHead, owner)) != null) {
            this.unlinkPending(timer);
        }

        while ((timer = find(this.firedHead, owner)) != null) {
            this.unlinkFired(owner, timer.identifier);
        }
    }

    /**
     * Pumps due timers and reinserts surviving repeating timers.
     *
     * @param hostTimeMilliseconds the host millisecond clock, read once by the pump
     */
    public void pump(int hostTimeMilliseconds) {
        Timer timer = this.pendingHead;

        while (timer != null) {
            if (hostTimeMilliseconds - timer.interval - timer.start <= 0) {
                break;
            }

            Timer successor = timer.next;

            timer.start = hostTimeMilliseconds;

            timer.callback.onTimer(hostTimeMilliseconds);

            if (this.unlinkPending(timer)) {
                timer.next     = this.firedHead;
                this.firedHead = timer;
            }

            if (successor == null) {
                break;
            }

            boolean successorPending = false;

            for (Timer current = this.pendingHead; current != null; current = current.next) {
                if (current == successor) {
                    successorPending = true;

                    break;
                }
            }

            if (!successorPending) {
                break;
            }

            timer = successor;
        }

        while (this.firedHead != null) {
            Timer fired = this.firedHead;

            this.firedHead = fired.next;

            this.insertSorted(fired);
        }
    }

    private void insertSorted(@NotNull Timer timer) {
        int key = -(timer.interval + timer.start);
        Timer previous = null;
        Timer current = this.pendingHead;

        while (current != null && key < -(current.interval + current.start)) {
            previous = current;
            current  = current.next;
        }

        timer.next = current;

        if (previous == null) {
            this.pendingHead = timer;
        } else {
            previous.next = timer;
        }
    }

    private @Nullable Timer unlinkPending(@NotNull Object owner, int identifier) {
        Timer timer = find(this.pendingHead, owner, identifier);

        if (timer != null) {
            this.unlinkPending(timer);
        }

        return timer;
    }

    private boolean unlinkPending(@NotNull Timer timer) {
        Timer previous = null;

        for (Timer current = this.pendingHead; current != null; current = current.next) {
            if (current == timer) {
                if (previous == null) {
                    this.pendingHead = current.next;
                } else {
                    previous.next = current.next;
                }

                return true;
            }

            previous = current;
        }

        return false;
    }

    private @Nullable Timer unlinkFired(@NotNull Object owner, int identifier) {
        Timer previous = null;

        for (Timer current = this.firedHead; current != null; current = current.next) {
            if (current.owner == owner && current.identifier == identifier) {
                if (previous == null) {
                    this.firedHead = current.next;
                } else {
                    previous.next = current.next;
                }

                return current;
            }

            previous = current;
        }

        return null;
    }
}
