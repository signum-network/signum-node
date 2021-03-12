package brs.at;

import brs.Account;
import brs.Blockchain;
import brs.Burst;
import brs.common.QuickMocker;
import brs.common.TestConstants;
import brs.db.BurstKey;
import brs.db.VersionedBatchEntityTable;
import brs.db.VersionedEntityTable;
import brs.db.store.ATStore;
import brs.db.store.AccountStore;
import brs.db.store.Stores;
import brs.fluxcapacitor.FluxCapacitor;
import brs.props.PropertyService;
import brs.props.Props;
import brs.util.Convert;
import org.mockito.ArgumentMatchers;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.*;

public class AtTestHelper {

    private static List<AT> addedAts = new ArrayList<>();
    private static Consumer<AT> onAtAdded;

    // Hello World example compiled with BlockTalk v0.0.0
    static byte[] HELLO_WORLD_CREATION_BYTES = getCreationBytes(1, Convert.parseHexString("3033040300000000350001010000001e0100000007283507030000000012270000001a0100000033100101000000320a03350401020000001002000000110200000033160102000000010200000048656c6c6f2c20573310010200000001020000006f726c6400000000331101020000000102000000000000000000000033120102000000010200000000000000000000003313010200000032050413"));

    // Echo example compiled with BlockTalk v0.0.0
    static byte[] ECHO_CREATION_BYTES = getCreationBytes(1, Convert.parseHexString("3033040300000000350001010000001e0100000007283507030000000012270000001a010000003310010100000032090335040102000000100200000035050102000000100200000035060102000000100200000035070102000000100200000033100101000000320a0335040102000000100200000011020000003316010200000011020000003313010200000011020000003312010200000011020000003311010200000011020000003310010200000032050413"));

    // Tip Thanks example compiled with BlockTalk v0.0.0
    static byte[] TIP_THANKS_CREATION_BYTES = getCreationBytes(2, Convert.parseHexString("12fb0000003033040301000000350001020000001e02000000072835070301000000122c0000001a0600000033100102000000320a0335040103000000100300000011030000003316010300000001030000005468616e6b20796f33100103000000010300000075210000000000003311010300000001030000000000000000000000331201030000000103000000000000000000000033130103000000320504350004030000001003000000010300000000e87648170000001003000000110400000011030000000703000000040000001003000000110300000003040000001f03000000040000000f1afa00000033160100000000320304130103000000d70faeecffc5c4e41003000000110000000013"));

    // Raising funds for burst faucet example compiled with BlockTalk v0.0.1
    static byte[] FUNDS_FOR_FAUCET_CREATION_BYTES = getCreationBytes(7, Convert.parseHexString("01110000001200000000000000320b033504011000000012b50400003033040304000000350001050000001e05000000072835070304000000320a033504010700000035060306000000350c0308000000350d030900000012250100001a1d0000003500040a000000100a000000010b00000000e8764817000000100b000000110b000000110a000000070a0000000b000000100a000000110a000000030b0000001f0a0000000b0000000f1a240100003500040a000000100a000000010b0000001400000000000000100b000000110b000000110a000000080a0000000b000000100a000000010b0000006400000000000000100b000000110b000000110a000000090a0000000b000000100a000000110b000000331601010000003302040b00000013020300000006000000020200000007000000030b000000100b000000110b000000020a00000000000000070a0000000b000000100a000000110a0000001b0a0000000b1a9e010000020a00000001000000070a000000020000001b0a0000000b1a9401000012d50100001ad401000012e40500001ad4010000020a00000008000000070a00000000000000100a000000110a0000001e0a0000000b1acf01000012cf0400001ad4010000123303000013010b00000000008a5d78456301100b000000010b0000000800000000000000100b000000110a0000003316010a000000110b0000003317010b000000010c000000486f6c64204646463310010c000000010c00000020746f20737570703311010c000000010c0000006f727420425552533312010c000000010c00000054000000000000003313010c000000010d00000046464600000000003318010d000000010d00000000000000000000003319010d0000003207043500010e000000100e0000001100000000010b0000009cffffffffffffff100b000000110b000000020a00000000000000070a0000000b000000100a000000110a000000030b0000001f0a0000000b0000000f1a32030000030b000000100b000000110b000000020a00000000000000070a0000000b000000100a000000110a000000030b000000200a0000000b0000000f1a32030000030b000000100b000000110000000013020b00000011000000140b00000009000000020b000000110000000e0a0000000b000000100a000000030b000000100b000000110b000000110a000000070a0000000b000000100a000000110a000000030b0000001f0a0000000b0000000f1a1a040000020b000000110000000e0a0000000b000000100a0000003500040a000000100a000000110b000000110a000000070a0000000b000000100a000000110a000000030b000000200a0000000b0000000f1a1a040000020b000000110000000e0a0000000b000000100a000000110b000000331601020000003302040b0000001ab4040000030b000000100b000000020b000000110000000e0a0000000b000000100a00000033160102000000110d0000003317010d00000033180100000000010c000000536f7272792c206e3310010c000000010c0000006f20656e6f7567683311010c000000010c0000002042555253542e003312010c000000010c00000000000000000000003313010c000000110a0000003319010a00000032080413030b000000100b00000011000000001010000000110100000013030b000000100b000000110b000000020a00000003000000070a0000000b000000100a000000110a000000030b0000001f0a0000000b0000000f1ae3050000331601000000003509040a000000100a000000110b000000020a00000003000000070a0000000b000000100a000000110a000000030b000000200a0000000b0000000f1ade050000030b000000100b000000331601020000003317010300000033180100000000010c0000005468616e6b20796f3310010c000000010c0000007520666f722073753311010c000000010c00000070706f7274696e673312010c000000010c0000002e000000000000003313010c000000110a0000003319010a00000032080412620000001ae305000012e405000013030b000000100b000000110b000000020a00000003000000070a0000000b000000100a000000110a000000030b0000001f0a0000000b0000000f1a31060000331601020000003302040300000013"));

    static void setupMocks() {
        Stores mockStores = mock(Stores.class);
        ATStore mockAtStore = mock(ATStore.class);
        FluxCapacitor mockFluxCapacitor = QuickMocker.latestValueFluxCapacitor();
        //noinspection unchecked
        BurstKey.LongKeyFactory<AT> atLongKeyFactory = mock(BurstKey.LongKeyFactory.class);
        //noinspection unchecked
        BurstKey.LongKeyFactory<AT.ATState> atStateLongKeyFactory = mock(BurstKey.LongKeyFactory.class);
        mockStatic(Burst.class);
        Blockchain mockBlockchain = mock(Blockchain.class);
        PropertyService mockPropertyService = mock(PropertyService.class);
        //noinspection unchecked
        VersionedEntityTable<AT> mockAtTable = mock(VersionedEntityTable.class);
        //noinspection unchecked
        VersionedBatchEntityTable<Account> mockAccountTable = mock(VersionedBatchEntityTable.class);
        //noinspection unchecked
        VersionedEntityTable<AT.ATState> mockAtStateTable = mock(VersionedEntityTable.class);
        AccountStore mockAccountStore = mock(AccountStore.class);
        //noinspection unchecked
        BurstKey.LongKeyFactory<Account> mockAccountKeyFactory = mock(BurstKey.LongKeyFactory.class);
        Account mockAccount = mock(Account.class);
        mockStatic(Account.class);

        doAnswer(invoke -> {
            AT at = invoke.getArgument(0);
            addedAts.add(at);
            if (onAtAdded != null) {
                onAtAdded.accept(at);
            }
            return null;
        }).when(mockAtTable).insert(ArgumentMatchers.any());
        when(mockAccount.getBalanceNQT()).thenReturn(TestConstants.TEN_BURST);
        when(mockAccountStore.getAccountTable()).thenReturn(mockAccountTable);
        when(mockAccountStore.setOrVerify(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                .thenReturn(true);
        doAnswer(invoke -> addedAts.stream()
                .map(AT::getId)
                .map(AtApiHelper::getLong)
                .collect(Collectors.toList()))
                .when(mockAtStore).getOrderedATs();
        doAnswer(invoke -> {
            Long atId = invoke.getArgument(0);
            for (AT addedAt : addedAts) {
                if (AtApiHelper.getLong(addedAt.getId()) == atId) {
                    return addedAt;
                }
            }
            return null;
        }).when(mockAtStore).getAT(ArgumentMatchers.anyLong());
        when(mockAtTable.getAll(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(addedAts);
        when(Account.getOrAddAccount(ArgumentMatchers.anyLong())).thenReturn(mockAccount);
        when(Account.getAccount(ArgumentMatchers.anyLong())).thenReturn(mockAccount);
        when(mockAccountTable.get(ArgumentMatchers.any())).thenReturn(mockAccount);
        when(mockStores.getAccountStore()).thenReturn(mockAccountStore);
        when(mockAccountStore.getAccountKeyFactory()).thenReturn(mockAccountKeyFactory);
        when(mockAtStore.getAtStateTable()).thenReturn(mockAtStateTable);
        when(mockPropertyService.getBoolean(ArgumentMatchers.eq(Props.ENABLE_AT_DEBUG_LOG))).thenReturn(true);
        when(mockAtStore.getAtTable()).thenReturn(mockAtTable);
        when(Burst.getPropertyService()).thenReturn(mockPropertyService);
        when(Burst.getBlockchain()).thenReturn(mockBlockchain);
        when(mockBlockchain.getHeight()).thenReturn(Integer.MAX_VALUE);
        when(mockAtStore.getAtDbKeyFactory()).thenReturn(atLongKeyFactory);
        when(mockAtStore.getAtStateDbKeyFactory()).thenReturn(atStateLongKeyFactory);
        when(mockStores.getAtStore()).thenReturn(mockAtStore);
        when(Burst.getStores()).thenReturn(mockStores);
        when(Burst.getFluxCapacitor()).thenReturn(mockFluxCapacitor);
    }

    static void clearAddedAts() {
        addedAts.clear();
        assertEquals(0, AT.getOrderedATs().size());
    }

    static void setOnAtAdded(Consumer<AT> onAtAdded) {
        AtTestHelper.onAtAdded = onAtAdded;
    }

    private static short currentAtVersion() {
        return 2;
    }

    private static void putLength(int nPages, int length, ByteBuffer buffer) {
        if (nPages * 256 <= 256) {
            buffer.put((byte) (length));
        } else if (nPages * 256 <= 32767) {
            buffer.putShort((short) length);
        } else {
            buffer.putInt(length);
        }
    }

    public static byte[] getCreationBytes(int codePages, byte[] code) {
        short cpages = ((short) codePages);
        short dpages = 1;
        short cspages = 1;
        short uspages = 1;
        long minActivationAmount = TestConstants.TEN_BURST;
        byte[] data = new byte[0];
        int creationLength = 4; // version + reserved
        creationLength += 8; // pages
        creationLength += 8; // minActivationAmount
        creationLength += cpages * 256 <= 256 ? 1 : (cpages * 256 <= 32767 ? 2 : 4); // code size
        creationLength += code.length;
        creationLength += dpages * 256 <= 256 ? 1 : (dpages * 256 <= 32767 ? 2 : 4); // data size
        creationLength += data.length;

        ByteBuffer creation = ByteBuffer.allocate(creationLength);
        creation.order(ByteOrder.LITTLE_ENDIAN);
        creation.putShort(currentAtVersion());
        creation.putShort((short) 0);
        creation.putShort(cpages);
        creation.putShort(dpages);
        creation.putShort(cspages);
        creation.putShort(uspages);
        creation.putLong(minActivationAmount);
        putLength(cpages, code.length, creation);
        creation.put(code);
        putLength(dpages, data.length, creation);
        creation.put(data);
        return creation.array();
    }

    public static void addHelloWorldAT() {
        AT.addAT(1L, TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED, "HelloWorld", "Hello World AT", AtTestHelper.HELLO_WORLD_CREATION_BYTES, Integer.MAX_VALUE);
    }

    public static void addEchoAT() {
        AT.addAT(2L, TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED, "Echo", "Message Echo AT", AtTestHelper.ECHO_CREATION_BYTES, Integer.MAX_VALUE);
    }

    public static void addTipThanksAT() {
        AT.addAT(3L, TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED, "TipThanks", "Tip Thanks AT", AtTestHelper.TIP_THANKS_CREATION_BYTES, Integer.MAX_VALUE);
    }

    public static void addFundsForFaucetAT() {
        AT.addAT(4L, TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED, "FundsForFaucet", "FUNDS FOR FAUCET", AtTestHelper.FUNDS_FOR_FAUCET_CREATION_BYTES, Integer.MAX_VALUE);
    }
}
