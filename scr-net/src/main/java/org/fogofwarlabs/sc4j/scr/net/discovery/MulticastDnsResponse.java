package org.fogofwarlabs.sc4j.scr.net.discovery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the records of a response to one query: the answers, each with the additional records it brings, and the
 * known-answer suppression of the query.
 * <p>
 * A record goes into the response once: an answer identical to an earlier answer is left out, and an additional record
 * identical to an answer or to an earlier additional record is left out; an additional record stays linked to the first answer
 * that brought it. A known answer suppresses every identical answer or additional record it covers
 * ({@link MulticastDnsMessage.Record#isSuppressedBy}), and an additional record goes away with its suppressed answer.
 */
public final class MulticastDnsResponse {
    /**
     * A record of the response and the answer that brought it, {@code null} for an answer.
     */
    private static final class Entry {
        private final MulticastDnsMessage.@NotNull Record record;
        private final @Nullable Entry answer;

        private boolean suppressed;

        private Entry(MulticastDnsMessage.@NotNull Record record, @Nullable Entry answer) {
            this.record = record;
            this.answer = answer;

            this.suppressed = false;
        }
    }

    private static boolean containsIdentical(@NotNull List<Entry> entries, MulticastDnsMessage.@NotNull Record record) {
        for (Entry entry : entries) {
            if (entry.record.isIdentical(record)) {
                return true;
            }
        }

        return false;
    }

    private static @NotNull List<MulticastDnsMessage.Record> collectSent(@NotNull List<Entry> entries) {
        List<MulticastDnsMessage.Record> records = new ArrayList<>(entries.size());

        for (Entry entry : entries) {
            if (!entry.suppressed) {
                records.add(entry.record);
            }
        }

        return records;
    }


    private final @NotNull List<Entry> answers;
    private final @NotNull List<Entry> additionals;

    /**
     * Creates a {@link MulticastDnsResponse} instance without records.
     */
    public MulticastDnsResponse() {
        this.answers     = new ArrayList<>();
        this.additionals = new ArrayList<>();
    }

    /**
     * @return the answers to send, in the order they were added
     */
    public @NotNull List<MulticastDnsMessage.Record> getAnswers() {
        return collectSent(this.answers);
    }

    /**
     * @return the additional records to send, in the order they were added
     */
    public @NotNull List<MulticastDnsMessage.Record> getAdditionals() {
        List<Entry> sent = new ArrayList<>(this.additionals.size());

        for (Entry additional : this.additionals) {
            if (!containsIdentical(this.answers, additional.record)) {
                sent.add(additional);
            }
        }

        return collectSent(sent);
    }


    /**
     * Adds an answer and the additional records it brings, unless an identical answer is already in the response.
     *
     * @param answer      the answer
     * @param additionals the additional records of the answer
     */
    public void addAnswer(MulticastDnsMessage.@NotNull Record answer, @NotNull List<MulticastDnsMessage.Record> additionals) {
        if (containsIdentical(this.answers, answer)) {
            return;
        }

        Entry entry = new Entry(answer, null);

        this.answers.add(entry);

        for (MulticastDnsMessage.Record additional : additionals) {
            if (!containsIdentical(this.additionals, additional)) {
                this.additionals.add(new Entry(additional, entry));
            }
        }
    }

    /**
     * Applies the known answers of the query: each suppresses the identical answers and additional records it covers, then
     * the additional records of suppressed answers are dropped.
     *
     * @param knownAnswers the answer section of the query
     */
    public void suppressKnownAnswers(@NotNull List<MulticastDnsMessage.Record> knownAnswers) {
        for (MulticastDnsMessage.Record knownAnswer : knownAnswers) {
            for (List<Entry> entries : List.of(this.answers, this.additionals)) {
                for (Entry entry : entries) {
                    if (!entry.suppressed && entry.record.isSuppressedBy(knownAnswer)) {
                        entry.suppressed = true;
                    }
                }
            }
        }

        for (Entry additional : this.additionals) {
            if (additional.answer != null && additional.answer.suppressed) {
                additional.suppressed = true;
            }
        }
    }
}
