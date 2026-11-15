package org.fogofwarlabs.sc4j.scr.net.golden;

import org.fogofwarlabs.sc4j.scr.core.test.TestResourceUtils;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderPacket;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Verifies that every native provider packet log of {@code captures/lan} decodes and re-encodes to the native bytes.
 */
final class LanCaptureTest {
    private static final @NotNull String CAPTURE_SUFFIX = ".txt";
    private static final @NotNull String HOST = "H";
    private static final @NotNull String FIELD_SEPARATOR = " ";
    private static final int SENDER_FIELD = 1;
    private static final int PACKET_FIELD = 2;
    private static final int FIELD_COUNT = 3;

    private static void decodeCapture(@NotNull Path capture) throws IOException {
        List<String> lines = Files.readAllLines(capture, StandardCharsets.US_ASCII);
        List<String> rejected = new ArrayList<>();
        List<String> reencodedDifferent = new ArrayList<>();
        int hostPackets = 0;
        int joinerPackets = 0;

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String[] fields = lines.get(lineIndex).split(FIELD_SEPARATOR);

            if (fields.length < FIELD_COUNT) {
                continue;
            }

            if (HOST.equals(fields[SENDER_FIELD])) {
                hostPackets++;
            } else {
                joinerPackets++;
            }

            byte[] encoded = HexFormat.of().parseHex(fields[PACKET_FIELD]);
            ProviderPacket packet;

            try {
                packet = ProviderPacket.parse(encoded);
            } catch (IOException exception) {
                rejected.add("line " + (lineIndex + 1) + ": " + exception.getMessage());

                continue;
            }

            byte[] reencoded = ProviderPacket.create(packet.getSequence(), packet.getAcknowledgement(),
                                                     packet.getType(), packet.getSubtype(),
                                                     packet.getSenderIdentifier(), packet.getFlags(),
                                                     packet.getPayloadBytes()).getEncodedBytes();

            if (!Arrays.equals(reencoded, encoded)) {
                reencodedDifferent.add("line " + (lineIndex + 1));
            }
        }

        Assertions.assertTrue(hostPackets > 0 && joinerPackets > 0,
                              capture.getFileName() + " holds " + hostPackets + " host and " + joinerPackets + " joiner packets.");
        Assertions.assertEquals(List.of(), rejected, capture.getFileName() + ": native packets rejected by ProviderPacket.parse.");
        Assertions.assertEquals(List.of(), reencodedDifferent, capture.getFileName() + ": re-encoded packets differ from the native bytes.");
    }


    /**
     * Creates one test per log {@code <name>.txt} (one provider packet per line, {@code <milliseconds> <H|J> <hex>}): the log
     * must hold packets of both peers, every packet must decode with {@link ProviderPacket#parse}, and re-encoding its
     * decoded fields with {@link ProviderPacket#create} must reproduce the native bytes exactly.
     * <p>
     * The protocol tests that replay the logs ({@code ProviderSessionTest}, {@code LanLobbyTest}, {@code LanSyncCaptureTest}) read
     * the same fixtures.
     *
     * @return the tests of the logs
     * @throws IOException if the folder cannot be listed
     */
    @TestFactory
    @NotNull Stream<DynamicTest> lan_capture() throws IOException {
        return TestResourceUtils.listFiles(TestResourceUtils.getPath(ProviderTestResources.CAPTURE_FOLDER), CAPTURE_SUFFIX)
                                .stream()
                                .map(capture -> DynamicTest.dynamicTest(ProviderTestResources.CAPTURE_FOLDER + "/" + capture.getFileName(),
                                                                        capture.toUri(),
                                                                        () -> decodeCapture(capture)));
    }
}
