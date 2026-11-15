package org.fogofwarlabs.sc4j.scr.game.terrain;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatSchema;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Checks contour storage and placement queries.
 */
final class TerrainContoursTest {
    /**
     * The terrain groups of the tileset input and the tile identifiers that its groups list.
     */
    private static final int TILESET_GROUP_COUNT = 3;
    private static final int TILES_PER_GROUP = 16;
    private static final int LISTED_TILE_COUNT = 32;
    /**
     * The pixel margin around the map and the pixel steps of the placement query grid.
     */
    private static final int QUERY_MARGIN = 8;
    private static final int QUERY_STEP_X = 3;
    private static final int QUERY_STEP_Y = 5;
    /**
     * The number of unit types of the extent table input, which is also the first unit type past that table, and the number of extent WORDs of
     * each unit type.
     */
    private static final int UNIT_TYPE_COUNT = 1;
    private static final int UNIT_EXTENT_WORDS = 4;
    /**
     * The number of footprint control WORDs.
     */
    private static final int CONTROL_COUNT = 12;

    private static void verifyContours(int width, int height,
                                       @NotNull String encodedMiniatureFlags,
                                       @NotNull String encodedTileIdentifiers,
                                       @NotNull String expectedBoundaryDigest,
                                       @NotNull String expectedPlacementDigest) throws Exception {
        ByteBuffer groups = ByteBuffer.allocate(TILESET_GROUP_COUNT * TerrainTileset.CV5_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int tileIdentifier = 0; tileIdentifier < LISTED_TILE_COUNT; tileIdentifier++) {
            int offset = (tileIdentifier / TILES_PER_GROUP) * TerrainTileset.CV5_RECORD_SIZE + TerrainTileset.CV5_MEGATILES_OFFSET +
                         (tileIdentifier % TILES_PER_GROUP) * Short.BYTES;

            groups.putShort(offset, (short) tileIdentifier);
        }

        ByteBuffer tileBytes = ByteBuffer.wrap(HexFormat.of().parseHex(encodedTileIdentifiers)).order(ByteOrder.LITTLE_ENDIAN);
        int[] tileIdentifiers = new int[width * height];

        for (int tileIndex = 0; tileIndex < tileIdentifiers.length; tileIndex++) {
            tileIdentifiers[tileIndex] = Short.toUnsignedInt(tileBytes.getShort());
        }

        TerrainTileset tileset = TerrainTileset.parse(groups.array(), HexFormat.of().parseHex(encodedMiniatureFlags));
        GameMap map = new GameMap(width, height, 0, tileIdentifiers, tileset.expandTileFlags(tileIdentifiers));
        TerrainContours collision = TerrainContours.create(map, tileset);
        MessageDigest digest = MessageDigest.getInstance(TerrainRegionCheck.DIGEST_ALGORITHM);

        updateBoundaryDigest(digest, collision);

        Assertions.assertEquals(expectedBoundaryDigest, HexFormat.of().formatHex(digest.digest()));

        DatSchema schema = new DatSchema().fieldU16x4("Unit Size Left", "Unit Size Up", "Unit Size Right", "Unit Size Down", UNIT_TYPE_COUNT);
        ByteBuffer unitBytes = ByteBuffer.allocate(UNIT_EXTENT_WORDS * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        unitBytes.putShort((short) 7).putShort((short) 8).putShort((short) 9).putShort((short) 10);

        DatTable units = schema.parse(unitBytes.array());

        for (int unitType : new int[]{0, 0x8000, 0x8001, 0x8008, 0x803F, 0xFFFF}) {
            for (int positionY = -QUERY_MARGIN; positionY < height * TerrainTileset.TILE_SIZE + QUERY_MARGIN; positionY += QUERY_STEP_Y) {
                for (int positionX = -QUERY_MARGIN; positionX < width * TerrainTileset.TILE_SIZE + QUERY_MARGIN; positionX += QUERY_STEP_X) {
                    digest.update((byte) (collision.isPlacementClear(unitType, positionX, positionY, UnitsDat.createTypeTable(units)) ? 1 : 0));
                }
            }
        }

        Assertions.assertEquals(expectedPlacementDigest, HexFormat.of().formatHex(digest.digest()));

        int[] retainedControls = new int[CONTROL_COUNT];

        for (int controlIndex = 0; controlIndex < retainedControls.length; controlIndex++) {
            retainedControls[controlIndex] = collision.getControl(controlIndex);
        }

        Assertions.assertThrows(IndexOutOfBoundsException.class,
                                () -> collision.initializeCollisionFootprint(UNIT_TYPE_COUNT, UnitsDat.createTypeTable(units)));

        for (int controlIndex = 0; controlIndex < retainedControls.length; controlIndex++) {
            Assertions.assertEquals(retainedControls[controlIndex], collision.getControl(controlIndex));
        }

        for (int invalidIndex : new int[]{-1, CONTROL_COUNT, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            RuntimeException expectedFailure = Assertions.assertThrows(IndexOutOfBoundsException.class,
                                                                       () -> Objects.checkIndex(invalidIndex, CONTROL_COUNT));
            RuntimeException actualFailure = Assertions.assertThrows(IndexOutOfBoundsException.class, () -> collision.getControl(invalidIndex));

            Assertions.assertEquals(expectedFailure.getClass(), actualFailure.getClass());
            Assertions.assertEquals(expectedFailure.getMessage(), actualFailure.getMessage());
        }

        Assertions.assertEquals(32767, collision.getControl(0));
        Assertions.assertEquals(32769, collision.getControl(1));
        Assertions.assertEquals(32769, collision.getControl(2));
        Assertions.assertEquals(32767, collision.getControl(3));
        Assertions.assertEquals(1, collision.getControl(4));
        Assertions.assertEquals(65535, collision.getControl(5));
        Assertions.assertEquals(65535, collision.getControl(6));
        Assertions.assertEquals(1, collision.getControl(7));
        Assertions.assertEquals(32768, collision.getControl(8));
        Assertions.assertEquals(32768, collision.getControl(9));
        Assertions.assertEquals(32768, collision.getControl(10));
        Assertions.assertEquals(32768, collision.getControl(11));
    }

    private static void updateBoundaryDigest(@NotNull MessageDigest digest, @NotNull TerrainContours collision) {
        ByteBuffer boundary = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int direction = 0; direction < TerrainContours.DIRECTION_COUNT; direction++) {
            for (int boundaryIndex = 0; boundaryIndex < collision.getBoundaryCount(direction); boundaryIndex++) {
                boundary.putLong(0, collision.getBoundary(direction, boundaryIndex));
                digest.update(boundary.array());
            }
        }
    }

    private static @NotNull Stream<Arguments> getContourCases() {
        return Stream.of(
                Arguments.of(8, 8,
                             "01000100010001000100010001000100010001000100010001000100010001009faa1f5ceef75dd7b6f07087dc071794642605b79d71cca1" +
                             "df1f55646b7208e42f95f9cb18d0edd240bec41f3e5f6399fedd6126e00a2f300ec286c283d6b0bbc029f313ac0e00ad70f5e322f04d8a09" +
                             "05e766e43258cb2f69cc7876e6650f61ec7fbf367e2d2d57834406311f3a91904b3e56e28067854412ff1b412c5ec9d07dd206645bd60bce" +
                             "eab390e9f71f04c2fa660434380e06508cb1970623cc4bdcdee7223b496785ced5519e8cbbf1983f38449bbf722f56432834d97af801df73" +
                             "df4f632f41d4315cf78f6263cf1ef980633a13b2837cd3d104f7d1ba6879870e184f14739259b26316278097ce4738a4dd48dd7c5415491f" +
                             "b0e6911b60df37d4b9234fad08f026ea61b1707e4b5fad61743dc8ff6c050a2d1be75f6ca7f174b21674742d160ee3a111679bbaf140ba86" +
                             "e781ed482ffce20d1ac4417c9149a371bb392a0482b47cf5669755e59b0587b7a308a8901142a93de3853570a9e22bf16240c2abc671aa94" +
                             "c3d417df1362b286d286ad737b201522cc6d4c79f0aed2bb9347f2fa4db7ed444a0ffc946a7d07396bb9493f01158d1795850031b94b7633" +
                             "c1bd8f353be5200d0794a891622aeb54956b54c292deef34b3e62570f0a267ffdb7a671dff90c45e2cc31751b06c3191360d9ec4a9d039a7" +
                             "5005de14192de7258082d9ef8cbfbe721a8fbfd2810ee1a6f9b96e778256fffa8774db7c62875f8548c0465ffa59ca7f4f49d1435b337789" +
                             "d4950ea8ce59418ca9405f25ec230c31fd3728ac0e2a77ae40b43ae39692995cb577d571573e02f1772a3c0c0531be77de42e0c32c5ee239" +
                             "b18c413adc5e368bae1e130ebbc6cb8274ac59c26de5faca5bf13a601b41eb72f53dcd215b20ca114fd8d33b92b11cd4e8f8f02e2d5b4378" +
                             "c3c0c8cc583ff6fc39e37db07da0548db6be8bb6d21a204f9eb02038cd69e22ff5345d32ca5b0e847edc105ab609d4f38a49b9f0fb0711da" +
                             "ea94ab5cc870e9983162b6a1b1d0808bd716cacf1856618f5a76ed3463710d8afe8ce027c62b3cb149b61a4704806929afce8f199fd2d6df" +
                             "8cd5d574dff078791ed9982ee08366c1e062e82eae7b0bf7e42aaea9dfbfe72daf604eb1c53360ae2dbc9ed34cd63c9a615dee231f9b9e88" +
                             "6d9190ed4365ee521328deee9eea25d03dfcde66aa887340f93d1bcafd9f1609e689b4a1cf579f99ca509cf3051cc13887c81d38b3853004" +
                             "2354c7e10178249eef800e6764af7c0b309a71a2a2a7603757c3e6b31c4080c400bdb694dd3fea8d523b422644f8f1de8d5012b10d6115ff" +
                             "956cde7afd4402f466049826736c46d40686ed8a1f7996ddf7ec46e31d709c76456d9b1bcadb97812b442082d14af7556d611e504423aa75" +
                             "44d38148dec1d34af7d9578bf06e05a1",
                             "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                             "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                             "00000000000000000000000000000000",
                             "9d4feade897c4c485f32a3b0ad07cb7b539a4ebe29c94f0fa3aaff3c1784ef95",
                             "1ede5f359c6c05dfbc9c719853c30cd7ce7c46e7883c7076b43bddb5b6cadaaf"),
                Arguments.of(12, 8,
                             "0100010001000100010001000100010001000100010001000100010001000100a1e37003a136369bfe106b364005e37aba8e609459b97595" +
                             "c26087e164d4c8dc58e8037c14508cf62e77e722dea7d78e2e9ad1dad337d6155faf8f95cabd289577d0b3af62982ea442f6fac531f6e756" +
                             "f64c784dd8a17d2436b4817552b0ee1b2c67f812b8cf05e54342374b9083cd8e0cac57f7260c21b703c0771be40f9afd54e7b4504a4e7bbe" +
                             "9564a92f6fdb6ece2ef71316cc59505896f64364b4f55e13ab3e8b5563332a4f81cb6baf769ee3ad319c67aca3bb7ebe6cd8ee9039881bf1" +
                             "179d1f76f83984decc43d9fb411b83807828f2266679a547f79ea520a8836d0d7a29d96505d0e55c1a19b53e9bb511ba4e732de3d373e778" +
                             "32606729dfd74933d0e15d16d98047f48447b1e4375d7b5defb9a2817f0d6536c4f1c25ea6b1848b2aa5f7d90c05d3592ea301afd246c497" +
                             "0086979b2524e05c88ab4609445a93ff2669f1a376d05b56fb41ff9b19255a1112f43e91dea82b8c9dc0404e92dc114117009d4c1d7e3779" +
                             "259dae5966ad91e0d5006e5bc3c8fc6de3f26f07a14ecc2c1d3f18ccea094456c3cacaee37e8924ff2dc1dcd95b2b8ebb49e2c7728170aa6" +
                             "b80c58f8e69ef8289e9ae01064bac7b61db0c4473f6be02fa55bf576128d171e0d0a454136f4861395e363dc45b7748d080c1d63ecd5728f" +
                             "61a8b1ce9a87625a4ed1f43b04287d4941c7a0f9835d99e24aefd4abd83977ef402ea4f3221cf5dcf9a9b004c118cb7fd5ce63c2dccad2f6" +
                             "3a80f845805d923947537c0da8948889e89a3602d886b565f8ecefa2064bfa9f64d17b8a8feacb3e987ca62586b2276555c6a008b0f6df0f" +
                             "35c1b5d6ed1c521cf1017e1b8482f5f41d5573709c0a563eacc2fe5afa6064575d6cc9f6ec16a9ca2c6dd99aac21f8f1dfc97b241c309dad" +
                             "4d572420b0c0e9ff1c7609053654c85262300ddaf29e02d2df8bbaf94fa3062640a3da39208481720806864cf2a263686382ae87fcc811c2" +
                             "11c20fe1a28beb7532052952bf6f2d9188f08fa8c99e0a0f248e7a5440adaf9537576339b6edea6fe5c761eb42c995a444ff4094bf8c1780" +
                             "2553f97f04f9d757f32d1f7d184f33ba38610614c04611225c040122184d6aa4e0a1471a63ba904c2ec1bebb83a8c13b0886de8609b0afde" +
                             "648dc857ec66e5c5079373b5866de6e809e5c109383cf4946338d684d63a21e7d52d2b2994f9a0ad474cc96eaada2f859a93c3b341bec4d3" +
                             "c058270d50bbe09b880da5b8e81bfd9b676779b4d95b31f9c2ef20618d23dcb46cb628db457ecf7bc9a7c62c0ca28153556150253f78d467" +
                             "7c4befb6424c4a39749a4304b4838921526f1d938d1bfe270e933ccbe0062d435024c941dfefee2530ec18d4fb9112ec207df9f996112f54" +
                             "eca562dab56f95caa4a7486faf715549",
                             "1b00010013001a00170005001c0016000c0004000a001e0019000e000a00050014001700060015001a001200000015000000120006001000" +
                             "0700020005000c000a000300170017000c0010000c001f000900000005001800140013000d0007000e000000120008001a001a0014000500" +
                             "17000f000c0012000800090011000c001f0011001a00110011001d00190007001e001d000d000800160002000f00160010001f0006000100" +
                             "020000000e00030000001100030015001a0002001f001000",
                             "ca9d2350b0e9951649b1199cde1b054de5ec3c8d20fc7076c9f797077015186e",
                             "6cb75dcbd3d19b5a573f8ab7fbe8569f94d99a9ddb2be34d3ef9c91af8ed6f6e"),
                Arguments.of(12, 8,
                             "0100010001000100010001000100010001000100010001000100010001000100ae6dbee94b8c52a11195c72235bd95f7e1da8f6cf7e25847" +
                             "3286b34b91cda410c985aa55e738bbced05fad127c36da356df21836193e3c7ed04584cd60ad0fae4a63775c41e154bee2c1c0cb7574b4de" +
                             "ea4c40177a4d0210b77e7eb4ccb19e7574370300adb396ec1887819822e71e8d1c144eec8096348e96c1936abaf8f438fb3a42e235dea390" +
                             "58c7586cfefff484542e876ab5d1e87339364cba67094f1a5ca7b49ff7003452c36a66ce2be9779854a9b012dd9ff397bf6b3e38cc520760" +
                             "8e3fd85f89eb571c6390863791bff65f4927bef78d799196ceb772cfe45a8685be5197a811dfd1a648d60c15859ced5ffdfdb591be8887af" +
                             "272ed5e091062f329c31834a37d2b9ccbe5636b11f45df91fdc77caa866eb46f1f5f8bf48a1ec50050605be7509ce24b5dd2a473b1ad202e" +
                             "19992a1b97329e94a70dcb56c8b00e731945ee18f5ff0d25447a4e8c8dc7a109a6c752b353cd3c77b011d682b29f54e962ef3085c4df57e7" +
                             "a02f0da6ad658c586a925d56de890c64f7f1431a17070de2ac8a5deebadaa18975cbfed5e47f9e1eb7664a28cdfc27bd1c8580c17a63909e" +
                             "3a731a7fad50137a69d134a255a7877d25fa671670958066b00a6f617ad6a19f1df543f010cebaeb4f905857ac5fe5b92cad2861a1ad50ea" +
                             "e129acc0d2151eca17c4f7c229dbc075d549257f687004fbbe5a5bbc764e274ffa5bf2005b50162a1b2cebc3b9b159dec6bf9cf0bdb48555" +
                             "dd15931411a7da1720ffb4de5a509a9fc773a0c29dcce9d0e53cf7f6009cef48c7cdfbe0a0c1530ad183fef61c638a5c4af312751ec2171b" +
                             "0d42671cdf6fa6c83206e854abfa8aa09250db95416c023ebc360b2d0fcfe26bb495f08bb2aa6713676cb1b71a16bb11162b6679256622f7" +
                             "9de37e8e1cb623a43125b2f729e494d0ee293255516ad365d3610c2f7d33574b87d3fdf73b09c95530d241cfd4417482bb5bc3cd15b0277b" +
                             "d46305f471c9321d9f6bed4fa13e8fea834eb178ce385feb2c2b77e09b450db83a7184c14be2b4f449833dc8b4a2476edfc18ab23c311091" +
                             "71630f9e9fb3d53267a1577363531b774b5b052b5a57bf00baa4bf425200f5e847ff295b459da16528a0ce34b56bdcecbee07f4b141b451d" +
                             "20e765ab8fc49c49b0b56de1dca4d7e9ab94b1c085e584fb6aeeea7f78f06d2f3ef528602df781cd48e401d20e82ca915d83df0716c095fd" +
                             "065353db2e442615e0762c495a6092f8a86b723ec2cad1a9119b8970e891fcecc38aa4b6fa90b2a6d022161c4d2b6e1ddf286d290333b01b" +
                             "b0a6af5e9cfd899d30b1b66a79fea3cc633ce3d8add68cf62f80c970c5010d4a57dd5ff88e5e5b623ab17faff18da859480401fbc1688d71" +
                             "c20e87f79bb0626bd79bf644fd0425c4",
                             "1d00090001000f0002000b0011000700040010000c001b001e000700030004001f0009001f0003000100170002001a001a00140003001a00" +
                             "1f000f0001000e0013001e000100010003001c001c0017000000120008000200130009001f001600150007001f001a001900070019001c00" +
                             "030007001d001c00170004000900030014000f001b00110000000f0008001a0016001f0011000000130014001800030009001e000e001f00" +
                             "13000500100002000d0009000f0004001600000013001700",
                             "a5b53589fd053de2c29768f7ae488ba5b2539ec7fa46a411d508d3266b4b999a",
                             "a3bb878aec576681542761e4548480a27800eda5edb2a76ed62ed8be3316bc5c"),
                Arguments.of(8, 16,
                             "010001000100010001000100010001000100010001000100010001000100010090c32a6d4f5804592d59416d7b4b5be6decb91cc153ba776" +
                             "c0fdabb1b48efd5fd136d9d639bc680dc299450e5f546eb06731f079361f41b1c409a3f11b2832658d980d262cbcabbeadc8864a11474137" +
                             "eea44c969f1c40df77d4b3d789b57848f1a7fe24dea3a8994b7e3a01a82b3c04886802d1e631c19f78769510fbcd14c72d011e65632abe28" +
                             "06cfd520ca0de7b75af8f3cc97e593bc7e4a04f7329b18890cfe4df721e4f8cefdf2db32cbcbe6299d3db66a0c125dd5b8dd242b1c0a369b" +
                             "797edb35e1f69b819af3284ffd773ad2bc8d23f7e07be09be2116fe3e0ed58a01409d988ff489cd1de43d81d6066844c6bec0f7988190285" +
                             "a7a48677b082893242575654531f05ea635a84652da26392b3533cf5073d411ca4d4272517e8b1d623973d678e0c3ce18ec3acb062fe6cd2" +
                             "95dad732ad4e6fbe4973f47a33903a197dfff51638fc62a3396189427bc8ee7acdec2926e11a87b9c767b5368158ae4d6dd094b47f97b69d" +
                             "4118fef1ddf6f36b3270b254c2f63eeb4e9b95f4c8c58bfeda57645e77734c6d3b5517ce345484def745d8122204abc8ca3c8e196acfbc4f" +
                             "30aa5bbb5de91bbc813c189b8f5765e50e1a6f7354fda93de153598eeff7bc4c98faf7df463115a756a38bdd5c5f4f484a57d417d70c950c" +
                             "eb413cb29a9fde42fcb9d1f680c188ff6c2d0e9388bc4168a61fd79580becfc1fdb801bf45880f9dda640785de209789414fe9b8384f37d1" +
                             "f28a9a38612e8ce03c6a80b29cd333446d8823d44516e7248990290de1c9404f17919d95fb2aaa67bfc15b12d0cc5132c48b3e839720157d" +
                             "d4a8196a1376edd6ce98d75df3e7f578949acf16a78c01585c016bf35d7109823fdc236f7bd222d7b5c40637fb82461dce2c9ea074de970a" +
                             "3d9800ea88a1b6fb9e25773cb9338012d5d24a45ac1a54018426715fa738e902037c72ea0c7e7eaa07496dfee1f829747c0bfde3f5c9a06a" +
                             "0c6062e60c3a87bcfd4ef1ef83cd2b8c4731743b838695b996695fc999a7680c7605ecc79d735898a6fbf1d265cbb0c81e39c84082ff8271" +
                             "ad4633a989d3b894afe1cce584a15302d2c92f688f0c001497a626ea8da9275b215923a419e22a8b3f40bcc5cf44d285b0e0d1cd680e01db" +
                             "c3c1ff7f88860ef60503a67ca9d1a39a0b3d8faab0e182330f1e108b1ff9250a749ae03bcdd6d92d44111768bca7da6c7753f6dacd697b39" +
                             "bca4b4cc8de55074d5e1e6288178c653cc45683b9b15e4383981fc651171a34db96783993e9109f86e7446fcfc0b2244e1b1cf39dd404eb4" +
                             "d421857cb4d4cfeb81404278ea6c6f5d1bbab326ff3d057ae1144c8066c86acd1973840dac2dc312310a585ce46bfb4d83371e8809184f3d" +
                             "1c3c69f1dd4469dc9f7a6b70cc8faaa7",
                             "060009001a001c000000100014001d001f00000000000f001f0002001c0018000e001b00160017001b00170013000c0013000b0003001500" +
                             "1b00130017000b001b000b0005001f000d000a001b001100030006000c0001000d000d0014000a0008001c0014000a00050018000d000800" +
                             "190016000d000a00130011001c0003000800190001001d00060002001b001d000b001a000f0015001d001a001f00040007000d000d000d00" +
                             "0e00020001001a000000090017001c0006000000030009000100020010001200090009000f0000000c001200010008001500130002001d00" +
                             "140002001d0015000f001b00030007001900110009001d001500160000001f00",
                             "38de3d2a9316474c49f7ce2867b79e950bbffbb019953b732a37a09309f93b96",
                             "e96a13ee51fd88d815b0b6d16c3faedc826cb2d53604de0037fa85eea9b1fbb3")
        );
    }


    @RequiresInstallation
    @Test
    void matches_native_contour_boundary_cases() throws Exception {
        String[] replayFiles = {GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT, GameTestResources.REPLAY_PVP_FORTRESS};
        String[] expected = {"c087695e0fab95897465e7db5e511a2c1a17a28f419c89830aace51a715e03d7",
                             "9519cf3c91d5a4e501501a54f0c97481eca7ba0b4fad9f17d23785c6c87a3081"};

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            for (int caseIndex = 0; caseIndex < replayFiles.length; caseIndex++) {
                ReplayMapScenario replayMap = ReplayMapScenario.load(storage, replayFiles[caseIndex]);
                TerrainContours collision = TerrainContours.create(replayMap.getMap(), replayMap.getTerrain());
                MessageDigest digest = MessageDigest.getInstance(TerrainRegionCheck.DIGEST_ALGORITHM);

                updateBoundaryDigest(digest, collision);

                Assertions.assertEquals(expected[caseIndex], HexFormat.of().formatHex(digest.digest()), replayFiles[caseIndex]);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getContourCases")
    void matches_native_terrain_contour_and_placement_query_cases(int width, int height,
                                                                  @NotNull String encodedMiniatureFlags,
                                                                  @NotNull String encodedTileIdentifiers,
                                                                  @NotNull String expectedBoundaryDigest,
                                                                  @NotNull String expectedPlacementDigest) throws Exception {
        verifyContours(width, height, encodedMiniatureFlags, encodedTileIdentifiers, expectedBoundaryDigest, expectedPlacementDigest);
    }
}
