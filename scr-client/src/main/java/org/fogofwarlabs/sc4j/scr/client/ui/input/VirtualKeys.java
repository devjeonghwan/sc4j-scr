package org.fogofwarlabs.sc4j.scr.client.ui.input;

import org.fogofwarlabs.sc4j.engine.client.input.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Maps engine keys to the Windows virtual-key values stored by the input manager, and names the virtual-key values the
 * interface tables use.
 * Modifier keys share the generic Shift, Control and Alt codes; keypad Enter shares the Return code.
 */
public final class VirtualKeys {
    /**
     * The virtual key of Backspace.
     */
    public static final int BACKSPACE = 0x08;
    /**
     * The virtual key of Tab.
     */
    public static final int TAB = 0x09;
    /**
     * The virtual key of Return, also produced by keypad Enter.
     */
    public static final int RETURN = 0x0D;
    /**
     * The generic virtual key of both Shift keys.
     */
    public static final int SHIFT = 0x10;
    /**
     * The generic virtual key of both Control keys.
     */
    public static final int CONTROL = 0x11;
    /**
     * The generic virtual key of both Alt keys.
     */
    public static final int MENU = 0x12;
    /**
     * The virtual key of Pause.
     */
    public static final int PAUSE = 0x13;
    /**
     * The virtual key of Caps Lock.
     */
    public static final int CAPITAL = 0x14;
    /**
     * The virtual key of Escape.
     */
    public static final int ESCAPE = 0x1B;
    /**
     * The virtual key of the space bar.
     */
    public static final int SPACE = 0x20;
    /**
     * The virtual key of Page Up.
     */
    public static final int PRIOR = 0x21;
    /**
     * The virtual key of Page Down.
     */
    public static final int NEXT = 0x22;
    /**
     * The virtual key of End.
     */
    public static final int END = 0x23;
    /**
     * The virtual key of Home.
     */
    public static final int HOME = 0x24;
    /**
     * The virtual key of the left arrow.
     */
    public static final int LEFT = 0x25;
    /**
     * The virtual key of the up arrow.
     */
    public static final int UP = 0x26;
    /**
     * The virtual key of the right arrow.
     */
    public static final int RIGHT = 0x27;
    /**
     * The virtual key of the down arrow.
     */
    public static final int DOWN = 0x28;
    /**
     * The virtual key of Print Screen.
     */
    public static final int SNAPSHOT = 0x2C;
    /**
     * The virtual key of Insert.
     */
    public static final int INSERT = 0x2D;
    /**
     * The virtual key of Delete.
     */
    public static final int DELETE = 0x2E;
    /**
     * The virtual key of the digit {@code 0}; the digits {@code 1} through {@code 9} follow it.
     */
    public static final int DIGIT_0 = 0x30;
    /**
     * The virtual key of the letter {@code A}; the letters {@code B} through {@code Z} follow it.
     */
    public static final int LETTER_A = 0x41;
    /**
     * The virtual key of the left Windows key.
     */
    public static final int LEFT_WINDOWS = 0x5B;
    /**
     * The virtual key of the right Windows key.
     */
    public static final int RIGHT_WINDOWS = 0x5C;
    /**
     * The virtual key of the application (menu) key.
     */
    public static final int APPLICATIONS = 0x5D;
    /**
     * The virtual key of keypad {@code 0}; keypad {@code 1} through {@code 9} follow it.
     */
    public static final int NUMPAD_0 = 0x60;
    /**
     * The virtual key of keypad {@code *}.
     */
    public static final int MULTIPLY = 0x6A;
    /**
     * The virtual key of keypad {@code +}.
     */
    public static final int ADD = 0x6B;
    /**
     * The virtual key of keypad {@code -}.
     */
    public static final int SUBTRACT = 0x6D;
    /**
     * The virtual key of keypad {@code .}.
     */
    public static final int DECIMAL = 0x6E;
    /**
     * The virtual key of keypad {@code /}.
     */
    public static final int DIVIDE = 0x6F;
    /**
     * The virtual key of F1; F2 through F24 follow it.
     */
    public static final int F1 = 0x70;
    /**
     * The virtual key of F2.
     */
    public static final int F2 = 0x71;
    /**
     * The virtual key of F5.
     */
    public static final int F5 = 0x74;
    /**
     * The virtual key of F6.
     */
    public static final int F6 = 0x75;
    /**
     * The virtual key of F11.
     */
    public static final int F11 = 0x7A;
    /**
     * The virtual key of Num Lock.
     */
    public static final int NUM_LOCK = 0x90;
    /**
     * The virtual key of Scroll Lock.
     */
    public static final int SCROLL_LOCK = 0x91;
    /**
     * The virtual key of {@code ;}.
     */
    public static final int OEM_SEMICOLON = 0xBA;
    /**
     * The virtual key of {@code =} (the plus key).
     */
    public static final int OEM_PLUS = 0xBB;
    /**
     * The virtual key of {@code ,}.
     */
    public static final int OEM_COMMA = 0xBC;
    /**
     * The virtual key of {@code -}.
     */
    public static final int OEM_MINUS = 0xBD;
    /**
     * The virtual key of {@code .}.
     */
    public static final int OEM_PERIOD = 0xBE;
    /**
     * The virtual key of {@code /}.
     */
    public static final int OEM_SLASH = 0xBF;
    /**
     * The virtual key of the grave accent.
     */
    public static final int OEM_GRAVE = 0xC0;
    /**
     * The virtual key of {@code [}.
     */
    public static final int OEM_LEFT_BRACKET = 0xDB;
    /**
     * The virtual key of the backslash.
     */
    public static final int OEM_BACKSLASH = 0xDC;
    /**
     * The virtual key of {@code ]}.
     */
    public static final int OEM_RIGHT_BRACKET = 0xDD;
    /**
     * The virtual key of the apostrophe.
     */
    public static final int OEM_APOSTROPHE = 0xDE;

    /**
     * The number of the last function key that has a virtual key, F24.
     */
    private static final int LAST_FUNCTION_NUMBER = 24;

    /**
     * Maps the input key to its virtual key code.
     *
     * @param key the key
     * @return the virtual-key code, or {@code -1} when the key has none here
     */
    public static int of(@NotNull Key key) {
        int letter = key.getLetterIndex();

        if (letter >= 0) {
            return LETTER_A + letter;
        }

        int digit = key.getDigit();

        if (digit >= 0) {
            return DIGIT_0 + digit;
        }

        int function = key.getFunctionNumber();

        if (function >= 1 && function <= LAST_FUNCTION_NUMBER) {
            return F1 + function - 1;
        }

        int keypadDigit = key.getKeypadDigit();

        if (keypadDigit >= 0) {
            return NUMPAD_0 + keypadDigit;
        }

        return switch (key) {
            case SPACE -> SPACE;
            case ENTER, KEYPAD_ENTER -> RETURN;
            case ESCAPE -> ESCAPE;
            case TAB -> TAB;
            case BACKSPACE -> BACKSPACE;
            case INSERT -> INSERT;
            case DELETE -> DELETE;
            case HOME -> HOME;
            case END -> END;
            case PAGE_UP -> PRIOR;
            case PAGE_DOWN -> NEXT;
            case LEFT -> LEFT;
            case UP -> UP;
            case RIGHT -> RIGHT;
            case DOWN -> DOWN;
            case LEFT_SHIFT, RIGHT_SHIFT -> SHIFT;
            case LEFT_CONTROL, RIGHT_CONTROL -> CONTROL;
            case LEFT_ALT, RIGHT_ALT -> MENU;
            case LEFT_SUPER -> LEFT_WINDOWS;
            case RIGHT_SUPER -> RIGHT_WINDOWS;
            case MENU -> APPLICATIONS;
            case CAPS_LOCK -> CAPITAL;
            case NUM_LOCK -> NUM_LOCK;
            case SCROLL_LOCK -> SCROLL_LOCK;
            case PAUSE -> PAUSE;
            case PRINT_SCREEN -> SNAPSHOT;
            case KEYPAD_MULTIPLY -> MULTIPLY;
            case KEYPAD_ADD -> ADD;
            case KEYPAD_SUBTRACT -> SUBTRACT;
            case KEYPAD_DECIMAL -> DECIMAL;
            case KEYPAD_DIVIDE -> DIVIDE;
            case SEMICOLON -> OEM_SEMICOLON;
            case EQUAL -> OEM_PLUS;
            case COMMA -> OEM_COMMA;
            case MINUS -> OEM_MINUS;
            case PERIOD -> OEM_PERIOD;
            case SLASH -> OEM_SLASH;
            case GRAVE_ACCENT -> OEM_GRAVE;
            case LEFT_BRACKET -> OEM_LEFT_BRACKET;
            case BACKSLASH -> OEM_BACKSLASH;
            case RIGHT_BRACKET -> OEM_RIGHT_BRACKET;
            case APOSTROPHE -> OEM_APOSTROPHE;
            default -> -1;
        };
    }

    private VirtualKeys() {
    }
}
