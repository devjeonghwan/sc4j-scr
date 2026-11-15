package org.fogofwarlabs.sc4j.scr.net.discovery;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * Verifies that the attributes of a built or parsed {@link LanRecord} cannot change after its creation.
 */
final class LanRecordTest {
    private static final @NotNull String NAME = "PNM:A";
    private static final long REVISION = 3;

    private static final @NotNull String FIRST_KEY = "first";
    private static final @NotNull String FIRST_VALUE = "one";
    private static final @NotNull String SECOND_KEY = "second";
    private static final @NotNull String SECOND_VALUE = "two";

    @Test
    void parse_should_return_unmodifiable_attributes() throws IOException {
        LanRecord built = LanRecord.createBuilder(NAME, REVISION).add(FIRST_KEY, FIRST_VALUE).build();
        LanRecord parsed = LanRecord.parse(built.encode());
        List<LanRecord.Attribute> attributes = parsed.getAttributes();

        Assertions.assertEquals(FIRST_VALUE, parsed.findText(FIRST_KEY));
        Assertions.assertThrows(UnsupportedOperationException.class, attributes::clear);
        Assertions.assertEquals(1, parsed.getAttributes().size());
    }

    @Test
    void build_should_keep_the_attributes_appended_before_the_call() {
        LanRecord.Builder builder = LanRecord.createBuilder(NAME, REVISION).add(FIRST_KEY, FIRST_VALUE);
        LanRecord built = builder.build();

        builder.add(SECOND_KEY, SECOND_VALUE);

        Assertions.assertEquals(1, built.getAttributes().size());
        Assertions.assertNull(built.findText(SECOND_KEY));
        Assertions.assertThrows(UnsupportedOperationException.class, built.getAttributes()::clear);
    }
}
