package org.fogofwarlabs.screcorder.sample;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Checks at compile time that sample definitions, values, samplers, readers, writers and frame lookups must agree on the sample type.
 */
final class SampleTypeContractTest {
    private static void compile(@NotNull Path directory, @NotNull String statement, boolean accepted) throws IOException, URISyntaxException {
        String source = """
                        import java.io.IOException;
                        import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
                        import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
                        import org.fogofwarlabs.screcorder.format.recording.Frame;
                        import org.fogofwarlabs.screcorder.format.recording.RecordWriter;
                        import org.fogofwarlabs.screcorder.sample.player.PlayersSample;
                        import org.fogofwarlabs.screcorder.sample.unit.UnitsSample;
                        import org.fogofwarlabs.screcorder.sample.SampleDefinitions;
                        import org.fogofwarlabs.screcorder.sample.io.SampleReader;
                        import org.fogofwarlabs.screcorder.sample.io.SampleWriter;
                        final class TypeContractFixture {
                            static void check(RecordWriter writer, Frame frame, CaptureContext context,
                                              PlayersSample players, UnitsSample units) throws IOException {
                                %s
                            }
                        }
                        """.formatted(statement);
        Path sourcePath = directory.resolve("TypeContractFixture.java");
        Path classes = Path.of(Sample.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        Files.writeString(sourcePath, source, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        Assertions.assertNotNull(compiler);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            boolean compiled = compiler.getTask(null, manager, diagnostics,
                                                List.of("-classpath", classes.toString(), "-d", directory.toString(), "-proc:none"),
                                                null, manager.getJavaFileObjects(sourcePath)).call();

            Assertions.assertEquals(accepted, compiled, diagnostics.getDiagnostics().toString());

            if (!accepted) {
                Assertions.assertTrue(diagnostics.getDiagnostics().stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
            }
        }
    }


    @Test
    void matching_definition_and_value_compile(@TempDir @NotNull Path directory) throws IOException, URISyntaxException {
        compile(directory, "writer.append(SampleDefinitions.PLAYERS, players); " +
                           "SampleReader<PlayersSample> sampleReader = SampleDefinitions.PLAYERS.getReader(); " +
                           "SampleWriter<PlayersSample> sampleWriter = SampleDefinitions.PLAYERS.getWriter();", true);
    }

    @Test
    void mismatched_definition_and_value_fail_to_compile(@TempDir @NotNull Path directory) throws IOException, URISyntaxException {
        compile(directory, "writer.append(SampleDefinitions.PLAYERS, units);", false);
    }

    @Test
    void mismatched_sampler_assignment_fails_to_compile(@TempDir @NotNull Path directory) throws IOException, URISyntaxException {
        compile(directory, "Sampler<PlayersSample> sampler = SampleDefinitions.UNITS.createSampler(context);", false);
    }

    @Test
    void mismatched_sample_reader_fails_to_compile(@TempDir @NotNull Path directory) throws IOException, URISyntaxException {
        compile(directory, "SampleReader<PlayersSample> sampleReader = SampleDefinitions.UNITS.getReader();", false);
    }

    @Test
    void mismatched_sample_writer_fails_to_compile(@TempDir @NotNull Path directory) throws IOException, URISyntaxException {
        compile(directory, "SampleWriter<PlayersSample> sampleWriter = SampleDefinitions.UNITS.getWriter();", false);
    }

    @Test
    void mismatched_frame_lookup_assignment_fails_to_compile(@TempDir @NotNull Path directory) throws IOException, URISyntaxException {
        compile(directory, "UnitsSample value = frame.getSample(PlayersSample.class);", false);
    }
}
