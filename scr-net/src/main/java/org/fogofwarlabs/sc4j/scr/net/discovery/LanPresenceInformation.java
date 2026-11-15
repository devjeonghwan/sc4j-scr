package org.fogofwarlabs.sc4j.scr.net.discovery;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A LAN player advertisement with optional nested attributes and hosted-lobby information. Parsing preserves the last recognized attribute value
 * and decodes the advertised GUID. Encoding keeps the field insertion order and omits optional values that are absent.
 */
public final class LanPresenceInformation {
    /**
     * The instance name prefix of a presence record.
     */
    public static final @NotNull String PREFIX = "PNM:";
    /**
     * {@code 'SEXP'}, the program identifier of StarCraft.
     */
    public static final long STARCRAFT_PROGRAM_IDENTIFIER = 0x53455850L;
    /**
     * The region attribute value of a LAN player.
     */
    public static final @NotNull String UNKNOWN_REGION = "??";
    /**
     * The address text of a hosted lobby.
     */
    public static final @NotNull String LOBBY_ADDRESS = "0.0.0.0:6112";

    private static final @NotNull String ATTRIBUTES = "attributes";
    private static final @NotNull String PROGRAM_IDENTIFIER = "program_id";
    private static final @NotNull String TOON_NAME = "toon_name";
    private static final @NotNull String LOBBY_INFORMATION = "lobby_info";
    private static final @NotNull String GUID = "guid";
    private static final @NotNull String FILTER = "filter";

    private static final @NotNull String REGION = "Region";
    private static final @NotNull String LAN_TOON_NAME = "LanToonName";
    private static final @NotNull String BATTLE_TAG = "BattleTag";

    /**
     * The repeated attribute field of the {@code Attributes} message, and its tag.
     */
    private static final int ATTRIBUTE_FIELD = 1;
    private static final long ATTRIBUTE_TAG = ProtobufUtils.createTag(ATTRIBUTE_FIELD, ProtobufUtils.WIRE_TYPE_LENGTH_DELIMITED);

    /**
     * The fields of the {@code LobbyInfo} message; fields {@code 2} and {@code 4} are unnamed and always hold {@code 0} and {@code 1}.
     */
    private static final int LOBBY_PROGRAM_IDENTIFIER_FIELD = 1;
    private static final int LOBBY_SECOND_FIELD = 2;
    private static final int LOBBY_NAME_FIELD = 3;
    private static final int LOBBY_FOURTH_FIELD = 4;
    private static final int LOBBY_ADDRESS_FIELD = 5;
    private static final long LOBBY_NAME_TAG = ProtobufUtils.createTag(LOBBY_NAME_FIELD, ProtobufUtils.WIRE_TYPE_LENGTH_DELIMITED);
    private static final long LOBBY_SECOND_VALUE = 0;
    private static final long LOBBY_FOURTH_VALUE = 1;

    private static final int GUID_LENGTH = 16;

    /**
     * The initial capacities of the encoded {@code Attributes} and {@code LobbyInfo} messages.
     */
    private static final int ATTRIBUTES_CAPACITY = 64;
    private static final int LOBBY_CAPACITY = 32;

    /**
     * Creates a {@link LanPresenceInformation} instance from a presence record.
     *
     * @param record the record
     * @return the created {@link LanPresenceInformation} instance, or {@code null} if the record is not a presence record
     * @throws IOException if a nested value is malformed
     */
    public static @Nullable LanPresenceInformation of(@NotNull LanRecord record) throws IOException {
        String toonName = record.findText(TOON_NAME);

        if (!record.getName().startsWith(PREFIX) || toonName == null) {
            return null;
        }

        String region = null;
        String lanToonName = null;
        String battleTag = null;
        byte[] attributes = record.findBytes(ATTRIBUTES);

        if (attributes != null) {
            ByteBuffer input = ByteBuffer.wrap(attributes);

            while (input.hasRemaining()) {
                long tag = ProtobufUtils.readVariableInteger(input);

                if (tag != ATTRIBUTE_TAG) {
                    ProtobufUtils.skipField(input, (int) (tag & ProtobufUtils.WIRE_TYPE_MASK));

                    continue;
                }

                LanRecord.Attribute attribute = LanRecord.parseAttribute(ProtobufUtils.readBytes(input));

                switch (attribute.getKey()) {
                    case REGION -> region = attribute.getText();
                    case LAN_TOON_NAME -> lanToonName = attribute.getText();
                    case BATTLE_TAG -> battleTag = attribute.getText();
                    default -> {
                    }
                }
            }
        }

        String lobbyName = null;
        byte[] lobbyInformation = record.findBytes(LOBBY_INFORMATION);

        if (lobbyInformation != null) {
            ByteBuffer input = ByteBuffer.wrap(lobbyInformation);

            lobbyName = "";

            while (input.hasRemaining()) {
                long tag = ProtobufUtils.readVariableInteger(input);

                if (tag == LOBBY_NAME_TAG) {
                    lobbyName = new String(ProtobufUtils.readBytes(input), StandardCharsets.UTF_8);
                } else {
                    ProtobufUtils.skipField(input, (int) (tag & ProtobufUtils.WIRE_TYPE_MASK));
                }
            }
        }

        String guidText = record.findText(GUID);
        byte[] guid;

        try {
            guid = guidText == null ? new byte[0] : Base64.getDecoder().decode(guidText);
        } catch (IllegalArgumentException exception) {
            guid = new byte[0];
        }

        return new LanPresenceInformation(toonName, region, lanToonName, battleTag, lobbyName, guid);
    }

    /**
     * Creates a {@link LanPresenceInformation} instance of a sc4j player: region {@link #UNKNOWN_REGION}, the toon name as
     * {@code LanToonName}, no BattleTag.
     *
     * @param toonName  the toon name
     * @param lobbyName the hosted lobby's name, or {@code null} while not hosting
     * @param guid      the {@code 16} guid bytes of this run
     * @return the created {@link LanPresenceInformation} instance
     * @throws IllegalArgumentException if the guid does not have {@code 16} bytes
     */
    public static @NotNull LanPresenceInformation create(@NotNull String toonName, @Nullable String lobbyName, byte @NotNull [] guid) {
        if (guid.length != GUID_LENGTH) {
            throw Exceptions.newIllegalArgumentException("A presence guid has {0} bytes.", GUID_LENGTH);
        }

        return new LanPresenceInformation(toonName, UNKNOWN_REGION, toonName, null, lobbyName, guid);
    }


    private final @NotNull String toonName;

    private final @Nullable String region;
    private final @Nullable String lanToonName;
    private final @Nullable String battleTag;

    private final @Nullable String lobbyName;

    private final byte @NotNull [] guid;

    /**
     * Creates a {@link LanPresenceInformation} instance.
     *
     * @param toonName    the toon name ({@code toon_name}, also the instance name after {@link #PREFIX})
     * @param region      the {@code Region} attribute, or {@code null} to write no {@code attributes}
     * @param lanToonName the {@code LanToonName} attribute, or {@code null} for none
     * @param battleTag   the {@code BattleTag} attribute, or {@code null} for none
     * @param lobbyName   the name of the hosted lobby ({@code lobby_info}), or {@code null} while not hosting
     * @param guid        the {@code 16} guid bytes
     */
    public LanPresenceInformation(@NotNull String toonName,
                                  @Nullable String region, @Nullable String lanToonName, @Nullable String battleTag,
                                  @Nullable String lobbyName,
                                  byte @NotNull [] guid) {
        this.toonName = toonName;

        this.region      = region;
        this.lanToonName = lanToonName;
        this.battleTag   = battleTag;

        this.lobbyName = lobbyName;

        this.guid = guid.clone();
    }

    /**
     * @return the toon name
     */
    public @NotNull String getToonName() {
        return this.toonName;
    }

    /**
     * @return the {@code LanToonName} attribute, or {@code null} if the record has none
     */
    public @Nullable String getLanToonName() {
        return this.lanToonName;
    }

    /**
     * @return the {@code BattleTag} attribute, or {@code null} if the record has none
     */
    public @Nullable String getBattleTag() {
        return this.battleTag;
    }

    /**
     * @return the hosted lobby's name, or {@code null} while the player hosts no lobby
     */
    public @Nullable String getLobbyName() {
        return this.lobbyName;
    }

    /**
     * @return a copy of the guid bytes
     */
    public byte @NotNull [] getGuid() {
        return this.guid.clone();
    }


    /**
     * Creates a {@link LanRecord} instance in the SDK's attribute order.
     *
     * @param revision the record revision
     * @return the created {@link LanRecord} instance
     */
    public @NotNull LanRecord toRecord(long revision) {
        LanRecord.Builder builder = LanRecord.createBuilder(PREFIX + this.toonName, revision);

        if (this.region != null) {
            ByteArrayOutputStream attributes = new ByteArrayOutputStream(ATTRIBUTES_CAPACITY);

            ProtobufUtils.writeBytes(attributes, ATTRIBUTE_FIELD, LanRecord.encodeAttribute(REGION, this.region.getBytes(StandardCharsets.UTF_8)));

            if (this.lanToonName != null) {
                ProtobufUtils.writeBytes(attributes,
                                         ATTRIBUTE_FIELD,
                                         LanRecord.encodeAttribute(LAN_TOON_NAME, this.lanToonName.getBytes(StandardCharsets.UTF_8)));
            }

            if (this.battleTag != null) {
                ProtobufUtils.writeBytes(attributes,
                                         ATTRIBUTE_FIELD,
                                         LanRecord.encodeAttribute(BATTLE_TAG, this.battleTag.getBytes(StandardCharsets.UTF_8)));
            }

            builder.add(ATTRIBUTES, attributes.toByteArray());
        }

        builder.add(PROGRAM_IDENTIFIER, Long.toString(STARCRAFT_PROGRAM_IDENTIFIER)).add(TOON_NAME, this.toonName);

        if (this.lobbyName != null) {
            ByteArrayOutputStream lobby = new ByteArrayOutputStream(LOBBY_CAPACITY);

            ProtobufUtils.writeVariableInteger(lobby, LOBBY_PROGRAM_IDENTIFIER_FIELD, STARCRAFT_PROGRAM_IDENTIFIER);
            ProtobufUtils.writeVariableInteger(lobby, LOBBY_SECOND_FIELD, LOBBY_SECOND_VALUE);
            ProtobufUtils.writeBytes(lobby, LOBBY_NAME_FIELD, this.lobbyName.getBytes(StandardCharsets.UTF_8));
            ProtobufUtils.writeVariableInteger(lobby, LOBBY_FOURTH_FIELD, LOBBY_FOURTH_VALUE);
            ProtobufUtils.writeBytes(lobby, LOBBY_ADDRESS_FIELD, LOBBY_ADDRESS.getBytes(StandardCharsets.UTF_8));
            builder.add(LOBBY_INFORMATION, lobby.toByteArray());
        }

        return builder.add(GUID, Base64.getEncoder().encodeToString(this.guid)).add(FILTER, "").build();
    }
}
