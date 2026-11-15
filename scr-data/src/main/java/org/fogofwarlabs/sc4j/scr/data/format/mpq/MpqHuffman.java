package org.fogofwarlabs.sc4j.scr.data.format.mpq;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Decompresses MPQ Huffman streams with adaptive tree weights and a versioned symbol cache.
 * Missing input bytes read as zero; invalid table types, pool overflow and cyclic trees fail.
 */
final class MpqHuffman {
    private static final int BYTE_MASK = 0xFF;
    private static final int ITEM_CAPACITY = 0x203;
    private static final int SYMBOL_COUNT = 0x102;
    private static final int END_OF_STREAM = 0x100;
    private static final int NEW_LITERAL = 0x101;
    private static final int CACHE_SIZE = 0x80;
    private static final int CACHE_INDEX_BITS = 7;
    private static final int NONE = -1;
    /**
     * The list head sentinel index.
     */
    private static final int HEAD = ITEM_CAPACITY;
    /**
     * The byte symbols {@code 0} to {@code 0xFF}, the leaves a weight table can create.
     */
    private static final int BYTE_SYMBOL_COUNT = 0x100;
    /**
     * The items a new literal adds: a copy of the lightest item and the new symbol.
     */
    private static final int BRANCH_ITEM_COUNT = 2;
    /**
     * A literal byte needs more than eight buffered bits; fewer load the next WORD.
     */
    private static final int LITERAL_MINIMUM_BITS = Byte.SIZE + 1;
    private static final int HEXADECIMAL_RADIX = 16;
    private static final int HEXADECIMAL_DIGITS_PER_BYTE = 2;

    /**
     * The nine weight tables, {@code 0x102} bytes each; only the first {@code 0x100} bytes of each are read.
     */
    private static final byte @NotNull [] @NotNull [] WEIGHT_TABLES = {
            createWeightTable(
                    "0a0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                    "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                    "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                    "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                    "00000000000000000000000000000002"
            ),
            createWeightTable(
                    "5416160d0c08060506050603040403050e0b141313090b0605040302030202020d070906060403020403030303030202090604040404030203020202" +
                    "020302040803040709050303030302020203020203020202020202020201010102010202060a08080607040304040202040203030403070709060403" +
                    "03020102020202020a0202030202010102020206030502030201010101010101010101020301010102010101010101020404040709080c0201010101" +
                    "010101010101010102010103040102040501010101010101020101010401010101010201010101010101010102010101010101010301010101010101" +
                    "0201010101010102020101020202064b"
            ),
            createWeightTable(
                    "0000000000000000000327000023000000000000000000000000000000000000ff0101010101010102020101060e1004060805040403030202030301" +
                    "0102010101040204020202010104010102030302030103060401010101010102010201010129071612400a0a112501031710262a100123232f100607" +
                    "02090101010101"
            ),
            createWeightTable(
                    "ff0b07050b020202060202010402010309010101030401010201010102010101050101010d0101010101010101010101020101030101010101010102" +
                    "010101010a0402010603020101010101030101010502030403030302010101020102030301030101020501010403050103010303020104030a060101" +
                    "01010101010101010202010a0205010102070217010501010e0101010101010101010101010101010101010101010101010101010101010101010101" +
                    "010101010101010101010101060201040501010201010101020101010101010101010101010101010101010101010101010101010701010201010101" +
                    "02010101010101010201010101010111"
            ),
            createWeightTable("fffb989a848563643e3e222213131817"),
            createWeightTable(
                    "fff19d9e9a9b9a9793938c8e868880827c7c7273696b5f6055564a4b404137372f2f272721211b1c1717131310100d0d0b0b09090808070706050504" +
                    "04041918"
            ),
            createWeightTable(
                    "c3cbf541ff7bf72100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                    "00000000bfccf240fd7cf722000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                    "00000000000000007a46"
            ),
            createWeightTable(
                    "c3d9ef3df97ce91efdabf12cfc5bfe170000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                    "00000000bdd9ec3df57de81dfbaef02cfb5cff1800000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000706c"
            ),
            createWeightTable(
                    "bac5da33e36dd818e594da23df4ad110eeafe42cea5ade15f487e921f643fc1200000000000000000000000000000000000000000000000000000000" +
                    "00000000b0c7d833e36bd618e795d823db49d011e9b2e22be85cdd15f187e720f744ff13000000000000000000000000000000000000000000000000" +
                    "00000000000000005f9e"
            ),
            };

    private static byte @NotNull [] createWeightTable(@NotNull String digits) {
        byte[] table = new byte[SYMBOL_COUNT];

        for (int index = 0; index * HEXADECIMAL_DIGITS_PER_BYTE < digits.length(); index++) {
            int start = index * HEXADECIMAL_DIGITS_PER_BYTE;

            table[index] = (byte) Integer.parseInt(digits.substring(start, start + HEXADECIMAL_DIGITS_PER_BYTE), HEXADECIMAL_RADIX);
        }

        return table;
    }

    /**
     * Decompresses one Huffman stream.
     *
     * @param input    the compressed data after the method mask byte
     * @param capacity the output capacity
     * @return the decompressed bytes, or {@code null} if the stream fails
     */
    static byte @Nullable [] decompress(byte @NotNull [] input, int capacity) {
        return new MpqHuffman(input).decode(capacity);
    }


    private final byte @NotNull [] input;
    private int inputPosition;
    private int bitBuffer;
    private int bitCount;

    private final int @NotNull [] value;
    private final int @NotNull [] weight;
    private final int @NotNull [] parent;
    private final int @NotNull [] child;
    private final int @NotNull [] previous;
    private final int @NotNull [] next;
    private int itemCount;

    private final int @NotNull [] itemsByByte;
    private boolean adaptive;
    private int version;

    private final int @NotNull [] cacheVersion;
    private final int @NotNull [] cacheBitCount;
    private final int @NotNull [] cacheTarget;

    /**
     * Creates a {@link MpqHuffman} instance.
     *
     * @param input the compressed data after the method mask byte
     */
    private MpqHuffman(byte @NotNull [] input) {
        this.input         = input;
        this.inputPosition = 0;
        this.bitBuffer     = 0;
        this.bitCount      = 0;

        this.value     = new int[ITEM_CAPACITY + 1];
        this.weight    = new int[ITEM_CAPACITY + 1];
        this.parent    = new int[ITEM_CAPACITY + 1];
        this.child     = new int[ITEM_CAPACITY + 1];
        this.previous  = new int[ITEM_CAPACITY + 1];
        this.next      = new int[ITEM_CAPACITY + 1];
        this.itemCount = 0;

        this.itemsByByte = new int[SYMBOL_COUNT];
        this.adaptive    = false;
        this.version     = 0;

        this.cacheVersion  = new int[CACHE_SIZE];
        this.cacheBitCount = new int[CACHE_SIZE];
        this.cacheTarget   = new int[CACHE_SIZE];
    }

    private byte @Nullable [] decode(int capacity) {
        if (capacity == 0) {
            return new byte[0];
        }

        // The stream starts with a full DWORD in the bit buffer.
        this.bitBuffer     = this.readInput(Integer.BYTES);
        this.inputPosition = Integer.BYTES;
        this.bitCount      = Integer.SIZE;

        int type = this.readByte();

        if (type >= WEIGHT_TABLES.length || !this.buildTree(WEIGHT_TABLES[type])) {
            return null;
        }

        this.adaptive = type == 0;

        byte[] output = new byte[capacity];
        int outputSize = 0;

        while (true) {
            if (this.bitCount < Byte.SIZE) {
                this.refillWord();
            }

            int symbol = this.decodeSymbol();

            if (symbol < 0) {
                return null;
            }

            if (symbol == NEW_LITERAL) {
                symbol = this.readByte();

                if (!this.insertBranch(symbol) || !this.adaptive && !this.incrementWeights(this.itemsByByte[symbol])) {
                    return null;
                }
            }

            if (symbol == END_OF_STREAM) {
                break;
            }

            output[outputSize++] = (byte) symbol;

            if (outputSize == capacity) {
                break;
            }

            if (this.adaptive && !this.incrementWeights(this.itemsByByte[symbol])) {
                return null;
            }
        }

        return Arrays.copyOf(output, outputSize);
    }

    private int decodeSymbol() {
        int cacheIndex = this.bitBuffer & (CACHE_SIZE - 1);
        int item;

        if (this.cacheVersion[cacheIndex] == this.version) {
            int cachedBitCount = this.cacheBitCount[cacheIndex];

            if (cachedBitCount <= CACHE_INDEX_BITS) {
                this.bitBuffer >>>= cachedBitCount;
                this.bitCount -= cachedBitCount;

                return this.cacheTarget[cacheIndex];
            }

            this.bitBuffer >>>= CACHE_INDEX_BITS;
            this.bitCount -= CACHE_INDEX_BITS;
            item = this.cacheTarget[cacheIndex];

            return this.walkTree(item, cacheIndex, false);
        }

        return this.walkTree(this.previous[HEAD], cacheIndex, true);
    }

    private int walkTree(int startItem, int cacheIndex, boolean fillCache) {
        int item = startItem;
        int steps = 0;
        int seventhItem = NONE;

        do {
            item = this.child[item];

            int bit = this.bitBuffer & 1;

            this.bitBuffer >>>= 1;
            this.bitCount--;

            if (this.bitCount == 0) {
                // An exhausted buffer reloads a whole DWORD.
                this.bitBuffer = this.readInput(Integer.BYTES);
                this.inputPosition += Integer.BYTES;
                this.bitCount  = Integer.SIZE;
            }

            if (bit != 0) {
                item = this.next[item];
            }

            if (item == NONE || item == HEAD || ++steps > ITEM_CAPACITY) {
                return -1;
            }

            if (steps == CACHE_INDEX_BITS) {
                seventhItem = item;
            }
        } while (this.child[item] != NONE);

        if (fillCache) {
            if (steps <= CACHE_INDEX_BITS) {
                for (int index = cacheIndex & ((1 << steps) - 1); index < CACHE_SIZE; index += 1 << steps) {
                    this.cacheVersion[index]  = this.version;
                    this.cacheBitCount[index] = steps;
                    this.cacheTarget[index]   = this.value[item];
                }
            } else {
                this.cacheVersion[cacheIndex]  = this.version;
                this.cacheBitCount[cacheIndex] = steps;
                this.cacheTarget[cacheIndex]   = seventhItem;
            }
        }

        return this.value[item];
    }

    private boolean buildTree(byte @NotNull [] weights) {
        int maximumWeight = 0;

        Arrays.fill(this.itemsByByte, NONE);
        this.previous[HEAD] = HEAD;
        this.next[HEAD]     = HEAD;

        for (int symbol = 0; symbol < BYTE_SYMBOL_COUNT; symbol++) {
            int symbolWeight = weights[symbol] & BYTE_MASK;

            if (symbolWeight == 0) {
                continue;
            }

            int item = this.createItem(symbol, symbolWeight);

            this.insertBefore(item, HEAD);
            this.itemsByByte[symbol] = item;

            maximumWeight = this.placeByWeight(item, this.next[HEAD], maximumWeight);
        }

        for (int symbol : new int[]{END_OF_STREAM, NEW_LITERAL}) {
            int item = this.createItem(symbol, 1);

            this.insertBefore(item, this.next[HEAD]);
            this.itemsByByte[symbol] = item;
        }

        int lowItem = this.next[HEAD];

        while (lowItem != HEAD) {
            int highItem = this.next[lowItem];

            if (highItem == HEAD) {
                break;
            }

            if (this.itemCount == ITEM_CAPACITY) {
                return false;
            }

            int parentItem = this.createItem(0, this.weight[highItem] + this.weight[lowItem]);

            this.insertBefore(parentItem, HEAD);
            this.child[parentItem] = lowItem;
            this.parent[lowItem]   = parentItem;
            this.parent[highItem]  = parentItem;

            maximumWeight = this.placeByWeight(parentItem, this.next[highItem], maximumWeight);
            lowItem       = this.next[highItem];
        }

        this.version = 1;

        return true;
    }

    /**
     * Places a tail item lighter than the heaviest one before the first item from {@code searchStart} that weighs at least as much.
     *
     * @param item          the item at the tail
     * @param searchStart   the first item the search visits
     * @param maximumWeight the heaviest weight placed so far
     * @return the heaviest weight placed after this item
     */
    private int placeByWeight(int item, int searchStart, int maximumWeight) {
        int itemWeight = this.weight[item];

        if (Integer.compareUnsigned(itemWeight, maximumWeight) >= 0) {
            return itemWeight;
        }

        int position = searchStart;

        while (position != HEAD && Integer.compareUnsigned(this.weight[position], itemWeight) < 0) {
            position = this.next[position];
        }

        this.unlink(item);
        this.insertBefore(item, position);

        return maximumWeight;
    }

    private boolean insertBranch(int symbol) {
        if (this.itemCount + BRANCH_ITEM_COUNT > ITEM_CAPACITY) {
            return false;
        }

        // The lightest item becomes a parent of its copy and of the new symbol.
        int lightest = this.next[HEAD];
        int copy = this.createItem(this.value[lightest], this.weight[lightest]);

        this.insertBefore(copy, lightest);
        this.parent[copy]                      = lightest;
        this.itemsByByte[this.value[lightest]] = copy;

        int added = this.createItem(symbol, 0);

        this.insertBefore(added, this.next[HEAD]);
        this.parent[added]       = lightest;
        this.itemsByByte[symbol] = added;
        this.child[lightest]     = added;

        return this.incrementWeights(added);
    }

    private boolean incrementWeights(int startItem) {
        int guard = 0;

        // Each increment swaps the item with the last lighter item of its run, then continues at its parent.
        for (int item = startItem; item != NONE; item = this.parent[item]) {
            if (++guard > ITEM_CAPACITY) {
                return false;
            }

            int itemWeight = ++this.weight[item];
            int last;
            int position = item;

            do {
                last     = position;
                position = this.next[position];
            } while (position != HEAD && Integer.compareUnsigned(this.weight[position], itemWeight) < 0);

            if (last == item) {
                continue;
            }

            this.unlink(last);
            this.insertBefore(last, item);
            this.unlink(item);
            this.insertBefore(item, position);

            int lastParentChild = this.child[this.parent[last]];

            if (this.child[this.parent[item]] == item) {
                this.child[this.parent[item]] = last;
            }

            if (lastParentChild == last) {
                this.child[this.parent[last]] = item;
            }

            int itemParent = this.parent[item];

            this.parent[item] = this.parent[last];
            this.parent[last] = itemParent;
            this.version++;
        }

        return true;
    }

    private int createItem(int itemValue, int itemWeight) {
        int item = this.itemCount++;

        this.value[item]    = itemValue;
        this.weight[item]   = itemWeight;
        this.parent[item]   = NONE;
        this.child[item]    = NONE;
        this.previous[item] = NONE;
        this.next[item]     = NONE;

        return item;
    }

    private void insertBefore(int item, int position) {
        int before = this.previous[position];

        this.previous[item]     = before;
        this.next[item]         = position;
        this.next[before]       = item;
        this.previous[position] = item;
    }

    private void unlink(int item) {
        if (this.previous[item] == NONE) {
            return;
        }

        this.next[this.previous[item]] = this.next[item];
        this.previous[this.next[item]] = this.previous[item];
        this.previous[item]            = NONE;
        this.next[item]                = NONE;
    }

    private int readByte() {
        // Fewer than 9 buffered bits load the next WORD.
        if (this.bitCount < LITERAL_MINIMUM_BITS) {
            this.refillWord();
        }

        int byteValue = this.bitBuffer & BYTE_MASK;

        this.bitBuffer >>>= Byte.SIZE;
        this.bitCount -= Byte.SIZE;

        return byteValue;
    }

    private void refillWord() {
        this.bitBuffer |= this.readInput(Short.BYTES) << this.bitCount;
        this.inputPosition += Short.BYTES;
        this.bitCount += Short.SIZE;
    }

    private int readInput(int length) {
        int result = 0;

        for (int index = 0; index < length; index++) {
            int position = this.inputPosition + index;
            int inputByte = position < this.input.length ? this.input[position] & BYTE_MASK : 0;

            result |= inputByte << (index * Byte.SIZE);
        }

        return result;
    }
}
