package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.scr.data.format.replay.*;
import org.fogofwarlabs.sc4j.scr.data.format.text.LegacyTextUtils;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerDeparture;
import org.fogofwarlabs.sc4j.scr.game.state.GameCheats;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Describes a recorded command with the payload fields {@link ReplayCommandExecutor} reads.
 * <p>
 * The description reads the decoded {@link ReplayCommand} with the codec of its type ({@link ReplayCommandCodecs}), the codec the executor
 * reads it with; it never reads the replay stream.
 * An opcode the executor does not handle is reported as unsupported, not guessed.
 */
public final class ReplayCommandDescription {
    /**
     * The hotkey operations: assign, recall and add.
     */
    private static final int HOTKEY_ASSIGN = 0;
    private static final int HOTKEY_RECALL = 1;
    private static final int HOTKEY_ADD = 2;

    /**
     * The cheat bits with a known cheat text, named by {@link #CHEAT_NAMES}; the other bits are reported as hexadecimal values.
     */
    private static final int @NotNull [] CHEAT_FLAGS = {
            GameCheats.BLACK_SHEEP_WALL,
            GameCheats.OPERATION_CWAL,
            GameCheats.POWER_OVERWHELMING,
            GameCheats.SOMETHING_FOR_NOTHING,
            GameCheats.SHOW_ME_THE_MONEY,
            GameCheats.GAME_OVER_MAN,
            GameCheats.THERE_IS_NO_COW_LEVEL,
            GameCheats.STAYING_ALIVE,
            GameCheats.OPHELIA,
            GameCheats.THE_GATHERING,
            GameCheats.MEDIEVAL_MAN,
            GameCheats.MODIFY_THE_PHASE_VARIANCE,
            GameCheats.WAR_AINT_WHAT_IT_USED_TO_BE,
            GameCheats.FOOD_FOR_THOUGHT,
            GameCheats.WHATS_MINE_IS_MINE,
            GameCheats.BREATHE_DEEP,
            GameCheats.NOGLUES
    };
    private static final @NotNull String @NotNull [] CHEAT_NAMES = {
            "black sheep wall", "operation cwal", "power overwhelming", "something for nothing", "show me the money",
            "game over man", "there is no cow level", "staying alive", "ophelia", "the gathering", "medieval man",
            "modify the phase variance", "war aint what it used to be", "food for thought", "whats mine is mine",
            "breathe deep", "noglues"
    };

    /**
     * Names the cheat bits of a recorded {@code 0x12} bit field.
     *
     * @param flags the recorded bit field
     * @return the cheat names, with unnamed bits as hexadecimal values
     */
    public static @NotNull List<String> getCheatNames(int flags) {
        List<String> names = new ArrayList<>();
        int remaining = flags;

        for (int index = 0; index < CHEAT_FLAGS.length; index++) {
            if ((flags & CHEAT_FLAGS[index]) != 0) {
                names.add(CHEAT_NAMES[index]);
                remaining &= ~CHEAT_FLAGS[index];
            }
        }

        for (int bit = 0; bit < Integer.SIZE; bit++) {
            if ((remaining & 1 << bit) != 0) {
                names.add(String.format(Locale.ROOT, "bit 0x%X", 1 << bit));
            }
        }

        return names;
    }

    /**
     * Reads the recorded {@code 0x12} bit field.
     *
     * @param command a cheat command
     * @return the little-endian bit field
     * @throws IllegalArgumentException if the command is not a four-byte cheat command
     */
    public static int getCheatFlags(@NotNull ReplayCommand command) {
        if (command.getTypeId() != ReplayCommandIds.CHEAT || command.getPayload().length != Integer.BYTES) {
            throw Exceptions.newIllegalArgumentException("A cheat command has a four-byte payload.");
        }

        try {
            return ReplayCommandCodecs.CHEAT.read(new CommandInput(command.getPayload())).getValue();
        } catch (IOException exception) {
            throw Exceptions.newIllegalArgumentException("A cheat command has a four-byte payload.");
        }
    }

    /**
     * Describes a decoded command.
     *
     * @param command the decoded command
     * @return the description; malformed or unhandled payloads are named as such
     */
    public static @NotNull ReplayCommandDescription describe(@NotNull ReplayCommand command) {
        byte[] payload = command.getPayload();
        int typeId = command.getTypeId();
        List<String> details = new ArrayList<>();

        if (ReplaySelectionCommand.supports(typeId)) {
            try {
                SelectionRequest request = ReplaySelectionCommand.parse(command);

                details.add(request.getHandleCount() + " handles");

                String name = switch (request.getOperation()) {
                    case REPLACE -> "Select";
                    case ADD -> "Shift select";
                    case REMOVE -> "Deselect";
                };

                return new ReplayCommandDescription(name, details);
            } catch (IOException exception) {
                String message = exception.getMessage();

                return new ReplayCommandDescription("Selection payload invalid", List.of(message == null ? "Unreadable payload." : message));
            }
        }

        if (ReplayTargetCommand.supports(typeId)) {
            try {
                ReplayTargetCommand target = ReplayTargetCommand.parse(command);

                details.add("Position " + target.getX() + ", " + target.getY());
                details.add(String.format(Locale.ROOT,
                                          target.getHandleWidthBytes() == Short.BYTES ? "Target 0x%04X" : "Target 0x%08X",
                                          target.getTargetHandle()));
                details.add("Unit type " + target.getUnitTypeIdentifier() + (target.getQueueFlags() != 0 ? ", queued" : ""));

                if (target.getOperation() == ReplayTargetCommand.Operation.RIGHT_CLICK) {
                    return new ReplayCommandDescription("Right click", details);
                }

                details.add(0, "Order " + target.getOrderIdentifier());

                return new ReplayCommandDescription("Targeted order", details);
            } catch (IOException exception) {
                String message = exception.getMessage();

                return new ReplayCommandDescription("Target payload invalid", List.of(message == null ? "Unreadable payload." : message));
            }
        }

        int expectedLength = getPayloadLength(typeId);

        if (expectedLength < 0) {
            return new ReplayCommandDescription(String.format(Locale.ROOT, "Unsupported opcode 0x%02X", typeId),
                                                List.of(payload.length + " payload bytes; the executor stops here."));
        }

        if (payload.length != expectedLength) {
            return new ReplayCommandDescription(String.format(Locale.ROOT, "Malformed opcode 0x%02X", typeId),
                                                List.of(payload.length + " payload bytes; " + expectedLength + " expected."));
        }

        try {
            return describeFixed(typeId, new CommandInput(payload), details);
        } catch (IOException exception) {
            String message = exception.getMessage();

            return new ReplayCommandDescription(String.format(Locale.ROOT, "Malformed opcode 0x%02X", typeId),
                                                List.of(message == null ? "Unreadable payload." : message));
        }
    }

    /**
     * Describes a fixed-layout command whose payload has the length of its type.
     *
     * @param typeId  the opcode
     * @param input   the payload
     * @param details the list the decoded fields are added to
     * @return the description
     * @throws IOException if the codec of the type cannot read the payload
     */
    private static @NotNull ReplayCommandDescription describeFixed(int typeId,
                                                                   @NotNull CommandInput input,
                                                                   @NotNull List<String> details) throws IOException {
        String name = switch (typeId) {
            case ReplayCommandIds.KEEP_ALIVE -> "Turn marker";
            case ReplayCommandIds.BUILD -> {
                ReplayBuildCommand build = ReplayCommandCodecs.BUILD.read(input);

                details.add("Order " + build.getOrder() + ", unit type " + build.getUnitType());
                details.add("Tile " + build.getTileX() + ", " + build.getTileY());

                yield "Build";
            }
            case ReplayCommandIds.VISION -> {
                details.add(String.format(Locale.ROOT, "Player mask 0x%04X", ReplayCommandCodecs.VISION.read(input).getValue()));

                yield "Shared vision";
            }
            case ReplayCommandIds.ALLIANCE -> {
                details.add(String.format(Locale.ROOT, "Alliance field 0x%08X", ReplayCommandCodecs.ALLIANCE.read(input).getValue()));

                yield "Alliances";
            }
            case ReplayCommandIds.CHEAT -> {
                details.addAll(getCheatNames(ReplayCommandCodecs.CHEAT.read(input).getValue()));

                yield "Cheat";
            }
            case ReplayCommandIds.HOTKEY -> {
                ReplayHotkeyCommand hotkey = ReplayCommandCodecs.HOTKEY.read(input);
                int operation = hotkey.getOperation();

                details.add("Group " + hotkey.getGroup());

                yield switch (operation) {
                    case HOTKEY_ASSIGN -> "Hotkey assign";
                    case HOTKEY_RECALL -> "Hotkey recall";
                    case HOTKEY_ADD -> "Hotkey add";
                    default -> "Hotkey operation " + operation;
                };
            }
            case ReplayCommandIds.CANCEL_BUILD -> "Cancel construction";
            case ReplayCommandIds.CANCEL_MORPH -> "Cancel morph";
            case ReplayCommandIds.STOP -> {
                details.add(ReplayCommandCodecs.STOP.read(input).isSet() ? "Queued" : "Immediate");

                yield "Stop";
            }
            case ReplayCommandIds.CARRIER_STOP -> "Carrier stop";
            case ReplayCommandIds.REAVER_STOP -> "Reaver stop";
            case ReplayCommandIds.RETURN_CARGO -> {
                details.add(ReplayCommandCodecs.RETURN_CARGO.read(input).isSet() ? "Queued" : "Immediate");

                yield "Return cargo";
            }
            case ReplayCommandIds.TRAIN -> {
                details.add("Unit type " + ReplayCommandCodecs.TRAIN.read(input).getValue());

                yield "Train";
            }
            case ReplayCommandIds.CANCEL_TRAIN -> {
                details.add(String.format(Locale.ROOT, "Queue slot 0x%04X", ReplayCommandCodecs.CANCEL_TRAIN.read(input).getValue()));

                yield "Cancel train";
            }
            case ReplayCommandIds.CLOAK -> "Cloak";
            case ReplayCommandIds.DECLOAK -> "Decloak";
            case ReplayCommandIds.UNIT_MORPH -> {
                details.add("Unit type " + ReplayCommandCodecs.UNIT_MORPH.read(input).getValue());

                yield "Unit morph";
            }
            case ReplayCommandIds.UNSIEGE -> {
                details.add(ReplayCommandCodecs.UNSIEGE.read(input).isSet() ? "Queued" : "Immediate");

                yield "Unsiege";
            }
            case ReplayCommandIds.SIEGE -> {
                details.add(ReplayCommandCodecs.SIEGE.read(input).isSet() ? "Queued" : "Immediate");

                yield "Siege";
            }
            case ReplayCommandIds.TRAIN_FIGHTER -> "Train interceptor or scarab";
            case ReplayCommandIds.UNLOAD_ALL -> {
                details.add(ReplayCommandCodecs.UNLOAD_ALL.read(input).isSet() ? "Queued" : "Immediate");

                yield "Unload all";
            }
            case ReplayCommandIds.UNLOAD, ReplayCommandIds.UNLOAD_121 -> {
                ReplayCommandCodec<ReplayValueCommand> codec = typeId == ReplayCommandIds.UNLOAD ? ReplayCommandCodecs.UNLOAD
                                                                                                 : ReplayCommandCodecs.UNLOAD_121;
                String format = typeId == ReplayCommandIds.UNLOAD ? "Unit 0x%04X" : "Unit 0x%08X";

                details.add(String.format(Locale.ROOT, format, codec.read(input).getValue()));

                yield "Unload";
            }
            case ReplayCommandIds.MERGE_ARCHON -> "Archon warp";
            case ReplayCommandIds.HOLD_POSITION -> {
                details.add(ReplayCommandCodecs.HOLD_POSITION.read(input).isSet() ? "Queued" : "Immediate");

                yield "Hold position";
            }
            case ReplayCommandIds.BURROW -> {
                details.add(ReplayCommandCodecs.BURROW.read(input).isSet() ? "Queued" : "Immediate");

                yield "Burrow";
            }
            case ReplayCommandIds.UNBURROW -> "Unburrow";
            case ReplayCommandIds.CANCEL_NUKE -> "Cancel nuke";
            case ReplayCommandIds.LIFT_OFF -> {
                ReplayPositionCommand position = ReplayCommandCodecs.LIFT_OFF.read(input);

                details.add("Position " + position.getX() + ", " + position.getY());

                yield "Lift off";
            }
            case ReplayCommandIds.TECH -> {
                details.add("Technology " + ReplayCommandCodecs.TECH.read(input).getValue());

                yield "Research";
            }
            case ReplayCommandIds.CANCEL_TECH -> "Cancel research";
            case ReplayCommandIds.UPGRADE -> {
                details.add("Upgrade " + ReplayCommandCodecs.UPGRADE.read(input).getValue());

                yield "Upgrade";
            }
            case ReplayCommandIds.CANCEL_UPGRADE -> "Cancel upgrade";
            case ReplayCommandIds.CANCEL_ADDON -> "Cancel add-on";
            case ReplayCommandIds.BUILDING_MORPH -> {
                details.add("Unit type " + ReplayCommandCodecs.BUILDING_MORPH.read(input).getValue());

                yield "Building morph";
            }
            case ReplayCommandIds.STIM -> "Stim pack";
            case ReplayCommandIds.LEAVE_GAME -> {
                int reason = (byte) ReplayCommandCodecs.LEAVE_GAME.read(input).getValue();

                details.add("Reason " + reason + (reason == PlayerDeparture.DROP_REASON ? " (dropped)" : " (left)"));

                yield "Leave game";
            }
            case ReplayCommandIds.MINIMAP_PING -> {
                ReplayPositionCommand position = ReplayCommandCodecs.MINIMAP_PING.read(input);

                details.add("Position " + position.getX() + ", " + position.getY());

                yield "Minimap ping";
            }
            case ReplayCommandIds.MERGE_DARK_ARCHON -> "Dark Archon meld";
            case ReplayCommandIds.CHAT -> {
                ReplayChatCommand chat = ReplayCommandCodecs.CHAT.read(input);
                byte[] text = chat.copyText();

                details.add("Sender " + chat.getSender());
                details.add(LegacyTextUtils.decode(text, 0, text.length));

                yield "Chat";
            }
            default -> throw Exceptions.newIllegalStateException("Missing description for handled opcode {0}", typeId);
        };

        return new ReplayCommandDescription(name, details);
    }

    /**
     * Returns the payload length the executor requires for a fixed-layout opcode that this description names: the command
     * length without the type byte ({@link ReplayCommandUtils#getFixedLength}).
     *
     * @param typeId the opcode
     * @return the byte count, or {@code -1} for selection, target and unhandled opcodes
     */
    static int getPayloadLength(int typeId) {
        return switch (typeId) {
            case ReplayCommandIds.KEEP_ALIVE, ReplayCommandIds.CANCEL_BUILD, ReplayCommandIds.CANCEL_MORPH, ReplayCommandIds.CARRIER_STOP,
                 ReplayCommandIds.REAVER_STOP, ReplayCommandIds.TRAIN_FIGHTER, ReplayCommandIds.MERGE_ARCHON, ReplayCommandIds.CANCEL_NUKE,
                 ReplayCommandIds.CANCEL_TECH, ReplayCommandIds.CANCEL_UPGRADE, ReplayCommandIds.CANCEL_ADDON, ReplayCommandIds.STIM,
                 ReplayCommandIds.MERGE_DARK_ARCHON, ReplayCommandIds.STOP, ReplayCommandIds.RETURN_CARGO, ReplayCommandIds.CLOAK,
                 ReplayCommandIds.DECLOAK, ReplayCommandIds.UNSIEGE, ReplayCommandIds.SIEGE, ReplayCommandIds.UNLOAD_ALL,
                 ReplayCommandIds.HOLD_POSITION, ReplayCommandIds.BURROW, ReplayCommandIds.UNBURROW, ReplayCommandIds.TECH,
                 ReplayCommandIds.UPGRADE, ReplayCommandIds.LEAVE_GAME, ReplayCommandIds.VISION, ReplayCommandIds.HOTKEY,
                 ReplayCommandIds.TRAIN, ReplayCommandIds.CANCEL_TRAIN, ReplayCommandIds.UNIT_MORPH, ReplayCommandIds.UNLOAD,
                 ReplayCommandIds.BUILDING_MORPH, ReplayCommandIds.ALLIANCE, ReplayCommandIds.CHEAT, ReplayCommandIds.LIFT_OFF,
                 ReplayCommandIds.MINIMAP_PING, ReplayCommandIds.UNLOAD_121, ReplayCommandIds.BUILD,
                 ReplayCommandIds.CHAT -> ReplayCommandUtils.getFixedLength(typeId) - 1;
            default -> -1;
        };
    }


    private final @NotNull String name;
    private final @NotNull List<String> details;

    private ReplayCommandDescription(@NotNull String name, @NotNull List<String> details) {
        this.name    = name;
        this.details = List.copyOf(details);
    }

    /**
     * @return the command name
     */
    public @NotNull String getName() {
        return this.name;
    }

    /**
     * @return the decoded fields in payload order
     */
    public @NotNull List<String> getDetails() {
        return this.details;
    }
}
