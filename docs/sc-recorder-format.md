# SCRECBIN version 0.1 wire format

**English** | [한국어](sc-recorder-format.ko.md)

SCRECBIN is the binary file format of the frame recorder `sc-recorder` (`sc-recorder/`): the observed state of a game, frame by frame, written as typed samples. This document describes the file layout, the samples, the capture rules and the bounds a reader checks.

**Contents**

- [Scope](#scope)
- [File structure](#file-structure)
- [Fixed header: 32 bytes](#fixed-header-32-bytes)
- [Typed metadata](#typed-metadata)
- [Chunk header: 24 bytes](#chunk-header-24-bytes)
- [FRAME payload](#frame-payload)
- [Capture](#capture)
- [END payload](#end-payload)
- [Bounds and verification](#bounds-and-verification)

## Scope

SCRECBIN 0.1 stores typed full snapshots. Other versions and text SCREC files are not read.

All numeric fields are little-endian. The file format version defines every sample payload contract. Sample identifiers select source-defined readers and writers; the file contains no field manifest, class names, schema expressions or per-sample version.

## File structure

```text
Fixed header
Selected sample identifiers
Typed provenance metadata
FRAME chunk, sequence 0
FRAME chunk, sequence 1
...
END chunk, next sequence
Physical EOF
```

Selected identifiers are positive, unique and strictly increasing. Each frame contains exactly one observation for every selected identifier, in the same order.

## Fixed header: 32 bytes

| Offset | Width | Meaning             |
|--------|-------|---------------------|
| 0      | 8     | ASCII SCRECBIN      |
| 8      | 2     | major = 0           |
| 10     | 2     | minor = 1           |
| 12     | 4     | totalHeaderBytes    |
| 16     | 4     | featureFlags = 0    |
| 20     | 4     | selectedSampleCount |
| 24     | 4     | metadataBytes       |
| 28     | 4     | header CRC32C       |

The fixed header is followed by selectedSampleCount unsigned 32-bit identifiers, then exactly metadataBytes of metadata. There is no padding:

```text
totalHeaderBytes = 32 + 4 * selectedSampleCount + metadataBytes
```

Header CRC32C covers the entire header with bytes 28..31 set to zero. The reader validates the size, checksum, known identifiers and ordering before constructing the Record.

## Typed metadata

Each entry consists of tag u16, flags u16, payloadBytes u32 and payload bytes. Flag bit 0 is REQUIRED. Duplicate known tags fail. Unknown optional tags are skipped under bounds; unknown required tags fail.

| Tag | Payload                                                           |
|-----|-------------------------------------------------------------------|
| 1   | producer version, strict UTF-8                                    |
| 2   | executable SHA-256, 32 bytes                                      |
| 3   | requested replay SHA-256, 32 bytes                                |
| 4   | loaded replay evidence, u8                                        |
| 5   | sampling method, u16                                              |
| 6   | polling interval in nanoseconds, u64                              |
| 7   | coverage requirements, u32                                        |
| 8   | permitted digest algorithm, u16: 1 = SHA-256                      |
| 9   | expected first/last frame, two u64 values; all ones means unknown |

Loaded replay evidence: 0 unavailable, 1 loaded header matched, 2 mismatched. Header equality is not complete replay-byte identity. Sampling methods: 0 caller-provided values, 1 read-only external marker polling. Missing provenance does not imply verified capture.

## Chunk header: 24 bytes

| Offset | Width | Meaning                    |
|--------|-------|----------------------------|
| 0      | 2     | kind: 1 FRAME, 2 END       |
| 2      | 2     | chunkFlags = 0             |
| 4      | 4     | payloadBytes               |
| 8      | 8     | sequence, starting at zero |
| 16     | 4     | chunk CRC32C               |
| 20     | 4     | reserved = 0               |

CRC32C covers bytes 0..15, bytes 20..23 and the complete payload, in that order. The checksum field is excluded. Readers verify bounds, sequence and checksum before decoding. CRC detects accidental corruption; it is not an authenticity claim.

## FRAME payload

The 24-byte prefix contains observedFrame u64, elapsedNanoseconds u64, sampleCount u32 and consistency u32.

Consistency: 0 UNVERIFIED, 1 MARKER_STABLE, 2 TORN. Frame numbers increase strictly but may have gaps. Elapsed time is measured when staging begins. MARKER_STABLE describes a successful marker check, not an atomic snapshot.

Each sample has an 18-byte header:

| Width | Meaning                                                     |
|-------|-------------------------------------------------------------|
| 4     | sampleIdentifier                                            |
| 1     | storageMode: 0 VALUES, 1 DIGEST, 2 NONE                     |
| 1     | status: 0 AVAILABLE, 1 READ_FAILED, 2 TORN, 3 NOT_ATTEMPTED |
| 8     | sampledFrame; all ones when unavailable                     |
| 4     | payloadBytes                                                |

Unavailable observations require NONE, no payload and no sampled frame. Available observations require VALUES or DIGEST and a nonnegative sampled frame no later than observedFrame. Sections have no padding.

### VALUES payload

Each concrete `SampleDefinition` supplies a bounded `SampleReader` and canonical `SampleWriter`. Its `getEncodedSize` method reserves reusable storage before writing. The reader and writer must consume exactly the declared payload.

| Identifier | Name            | Model                |
|------------|-----------------|----------------------|
| 1          | players         | PlayersSample        |
| 2          | units           | UnitsSample          |
| 3          | random          | RandomSample         |
| 4          | frame           | FrameStateSample     |
| 5          | random-counters | RandomCountersSample |
| 6          | locations       | LocationsSample      |
| 7          | lists           | UnitListsSample      |
| 8          | context         | ContextSample        |
| 9          | triggers        | TriggersSample       |
| 10         | eud             | EudSample            |
| 11         | eud-hosts       | EudHostsSample       |
| 12         | ai              | AiSample             |
| 13         | camera          | CameraSample         |
| 14         | sync            | SyncSample           |
| 15         | tiles           | TilesSample          |
| 16         | runtime-random  | RuntimeRandomSample  |

The explicit source readers/writers in each sample package define field order, integer widths, optional-value flags and collection bounds. Payload changes require a file format revision.

Player and unit tables are keyed and ordered by identifier. Unit identifiers are native pool slots; target pointers are converted to slots. Location entries are ordered by their identifier. EUD region entries are ordered by virtual key. Visible/hidden unit lists retain native traversal order because order is part of the observed state.

Process addresses are normalized before encoding, so that equal game states give equal payloads in different processes:

- units: the class-dependent union at unit +0x128 is decoded by unit type into a kind byte and a u64 value. Workers (SCV, Drone, Probe), the Nuclear Silo and the Nydus Canal hold a unit pointer, stored as its pool slot (kind 1; all ones when the pointer does not address a pool slot, as observed in a replay that SC4J also rejects). The nuke casters (Ghost and its heroes) and the Pylon hold a pointer outside the unit pool (nuke dot, psi field sprite), stored as presence 0 or 1 (kind 2). Every other type keeps the four raw words (kind 0).
- context: the loaded map or replay path (+0xC4C, 260 bytes, it names the recorder's session directory) and the screen tile (+0xE0, 4 bytes) are zeroed.
- ai: the region table pointer becomes presence 0 or 1; the region row pointers at +32, +40 and +56 become offsets from the unit pool (same game arena) and those at +64 and +72 offsets from the module base; the unit AI record address becomes an offset from the module base.
- sync: the 256-byte draw-order bucket table at 0x140E76B70 (render scratch written by the draw pass) is not recorded; the block starts at 0x140E76C70 and its first QWORD, a game arena pointer, becomes an offset from the unit pool.
- tiles: the map width and height (u16 each, from game context +0xE4 and +0xE6), then width * height tile identifier WORDs in row-major order with the map width as the row length, read from the start of the tile array that the pointer at 0x140E7E478 addresses (the array the creep routines change). The array pointer itself is not recorded.
- runtime-random: a u32 row count, then per row the thread order u16 and the C runtime random state u32 (the `rand`/`srand` state at +0x28 of the runtime thread data). Thread identifiers and addresses are process-local, so a row names its thread by order: the rank of the thread by creation time among the target threads of the latest listing (the threads are listed only for this sample), the initial thread 0. The thread data is found through the fiber local storage slot whose index the executable keeps at 0x140E09508 (TEB +0x17C8, then the slot chunk table); a block is kept only while its first pointer addresses the executable's exception action table 0x140C38280, and a sample fails rather than reading anything else. A thread whose block can no longer be read, or whose slot no longer addresses its block, has exited: it is left out of that sample and the threads are listed again for the next one, which also adds threads created meanwhile. Orders are ranks in the latest listing, so after a relisting threads created after an exited thread move down; the initial thread keeps order 0. A sample fails only if no listed thread holds readable runtime thread data, or if a block that its thread's slot still addresses fails the exception action table check.
- eud and eud-hosts: the emulator, virtual table and host pointers, and pointer-valued words of the object, node and host-head bytes, become 2^62 plus their image offset (pointers into the executable image) or 2^61 (any other pointer, presence only). Zero stays zero, so a game without an emulator encodes as before.

The comparison samples are players, units, random, frame, random-counters, locations, lists, triggers, eud and eud-hosts (`SampleDefinitions.getComparisonDefinitions`). Context, ai, camera and sync hold client, render or process-local state, and tiles and runtime-random hold state that the C runtime random generator of each machine decides (creep tile variants); they are recorded for diagnosis only. Camera (view, cursor, input) is never part of a simulation comparison.

EUD emulator absence is represented explicitly as an empty observation. A nonzero unreadable pointer fails capture rather than producing fabricated bytes. Served values and type-2 heads are recorded only for the region kinds that define them; this is not a whole EUD host-memory snapshot.

### DIGEST payload: 32 bytes

DIGEST stores raw SHA-256 bytes. Its input is:

```text
ASCII SCREC-STATE (11 bytes)
formatMajor u16 = 0
formatMinor u16 = 1
sampleIdentifier u32
canonical VALUES payload
```

The format version and identifier bind the hash to a concrete payload contract. A hash-only observation cannot reconstruct a model; `Frame.getSample` fails explicitly while its `SampleObservation` exposes the digest.

## Capture

The recorder polls the frame counter (game context +0x14C) in a busy loop (`Thread.onSpinWait`), or with `--poll-us N` at a fixed interval, and samples a selected frame as soon as the counter changes, without a settling delay. One warm-up pass of every sampler runs before the first frame is recorded. The native game increments the counter at the start of a frame (0x140319F38), before the frame phase 0x14031C610 enables the RNG and runs the simulation. A pass is therefore accepted only when the counter read before and after it is equal, the RNG block (state, total, gate) is unchanged and its gate is clear in both reads; the gate is set while the frame phase runs. A pass whose counter changed is lost (the frame is missing); a pass whose RNG bracket stays unstable is retried, then written as TORN in strict mode or dropped in tolerant mode.

Coverage modes: strict (`--coverage strict`, the default) requires no gaps, marker-stable frames and the expected boundaries; tolerant (`--coverage tolerant`) requires only available samples and marker-stable frames, so missed and dropped frames are absent. Thinning (`--dense-frames N --stride K`) records every frame of the first N, then the first observed frame of each period of K frames, and every frame from the final expected frame. `--frames A..B` records a window. Frames not recorded appear in the END missing ranges. `--preset test` selects the comparison samples, DIGEST storage, tolerant coverage, `--dense-frames 2400 --stride 24` and `--playback-speed X4`. The game is launched with `--launch --noSound`, or with `--launch` alone under `--sound on`; `--playback-speed X1|X2|X4|X8|MAX` adds `--playbackSpeed=...`.

`validate FILE` reads the whole file and exits 0 when it is valid and its coverage requirements are satisfied with normal termination, 3 when it is valid but partial, and 1 when it is invalid. `inspect FILE` prints the metadata, samples, frame counts and missing ranges.

`SampleDigester` computes the DIGEST of a sample from its values, exactly as a DIGEST observation stores it; programs that build the same sample values (a simulation) compare digests with it.

## END payload

The fixed 64-byte prefix contains:

| Width | Meaning                                                 |
|-------|---------------------------------------------------------|
| 8     | committed frameCount                                    |
| 8     | first observed frame; all ones when none                |
| 8     | last observed frame; all ones when none                 |
| 4     | termination: 0 NORMAL, 1 CANCELLED, 2 TIMEOUT, 3 FAILED |
| 4     | quality flags: bit 0 requirements satisfied             |
| 8     | missing frame count                                     |
| 8     | unavailable sample count                                |
| 8     | torn frame count                                        |
| 4     | missing range count                                     |
| 4     | reserved = 0                                            |

The prefix is followed by pairs of first/last missing frames (two u64 values per inclusive range). Ranges are ordered, disjoint and coalesced. Their total count must match the declared count and the observed frames.

Counts, elapsed times and frame numbers fit in `0..Long.MAX_VALUE`. All-ones sentinels apply only where specified. Inclusive range calculations must not overflow.

END is the final chunk. `readNextFrame` returns null only after validated END and physical EOF. A valid END may report gaps or failed capture. Closing an unfinished writer does not generate a successful END; a missing END is truncated input.

## Bounds and verification

- Header: at most 1 MiB.
- Chunk payload: at most 64 MiB, checked before allocation.
- Selected samples: 1..4096.
- Each sample has a trusted source-defined maximum payload size.
- Collection counts, pointer ranges and arithmetic are checked before allocating or traversing.
- CRC, reserved values, sequence, identifiers and exact payload consumption are validated.
- Unknown identifiers and unsupported format versions fail.
- I/O failure leaves a faulted writer and distinguishable partial output.

Tests cover typed round trips, independent digest vectors, corrupt/recomputed CRC input, unknown/duplicate identifiers, malformed counts and metadata, truncated input, END quality and physical EOF. Native memory fixtures cover restored capture layouts and invalid pointers. Runtime game coverage is reported separately; these checks do not establish lossless polling or large-file performance.
