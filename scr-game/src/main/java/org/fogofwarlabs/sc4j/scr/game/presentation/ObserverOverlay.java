package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.base.io.AuditedResourceUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Retains the parsed subscriber node of the verified SD observer overlay resource. Browser rendering and subscriber delivery are not represented
 * here.
 */
public final class ObserverOverlay {
    /**
     * The identifier of the parsed subscriber record.
     */
    private static final int RECORD_IDENTIFIER = 1;
    /**
     * The record type of a web browser control.
     */
    private static final int WEB_BROWSER_TYPE = 15;
    /**
     * The flag that makes the parsed record visible.
     */
    private static final int VISIBLE_FLAG = StatusButtonPanel.VISIBLE_FLAG;

    /**
     * The SHA-256 digest of the audited observer overlay resource.
     */
    private static final @NotNull String RESOURCE_DIGEST = "a41a06fa0d9ca09dd36ff3ed948873168100a6e2d813e60a492644abe10e24ae";

    /**
     * Creates an {@link ObserverOverlay} instance after validating the audited resource.
     *
     * @param contents the complete {@code SD/rez/BroadcastObserver.ui.json} resource
     * @return the created {@link ObserverOverlay} instance
     * @throws UnsupportedOperationException if the resource differs from the audited asset
     */
    public static @NotNull ObserverOverlay parse(byte @NotNull [] contents) {
        AuditedResourceUtils.require(contents, RESOURCE_DIGEST, "Unaudited observer overlay resource");

        return new ObserverOverlay();
    }

    private ObserverOverlay() {
    }

    /**
     * @return the parsed record identifier
     */
    public int getRecordIdentifier() {
        return RECORD_IDENTIFIER;
    }

    /**
     * @return the web browser record type
     */
    public int getRecordType() {
        return WEB_BROWSER_TYPE;
    }

    /**
     * @return the visible flag of the parsed record
     */
    public int getParsedFlags() {
        return VISIBLE_FLAG;
    }

    /**
     * @return the runtime flags immediately after parsing, before callbacks
     */
    public int getInitialRuntimeFlags() {
        return 0;
    }

    /**
     * @return the runtime flags after binding initialization and before subscriber enablement
     */
    public int getBindingRuntimeFlags() {
        return 0;
    }
}
