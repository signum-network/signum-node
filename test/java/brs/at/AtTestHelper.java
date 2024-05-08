package brs.at;

import brs.Account;
import brs.Blockchain;
import brs.Signum;
import brs.common.QuickMocker;
import brs.common.TestConstants;
import brs.db.SignumKey;
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
    static byte[] HELLO_WORLD_CREATION_BYTES = getCreationBytes((short)2, 1, Convert.parseHexString("3033040300000000350001010000001e0100000007283507030000000012270000001a0100000033100101000000320a03350401020000001002000000110200000033160102000000010200000048656c6c6f2c20573310010200000001020000006f726c6400000000331101020000000102000000000000000000000033120102000000010200000000000000000000003313010200000032050413"));

    // Echo example compiled with BlockTalk v0.0.0
    static byte[] ECHO_CREATION_BYTES = getCreationBytes((short)2, 1, Convert.parseHexString("3033040300000000350001010000001e0100000007283507030000000012270000001a010000003310010100000032090335040102000000100200000035050102000000100200000035060102000000100200000035070102000000100200000033100101000000320a0335040102000000100200000011020000003316010200000011020000003313010200000011020000003312010200000011020000003311010200000011020000003310010200000032050413"));

    // Tip Thanks example compiled with BlockTalk v0.0.0
    static byte[] TIP_THANKS_CREATION_BYTES = getCreationBytes((short)2, 2, Convert.parseHexString("12fb0000003033040301000000350001020000001e02000000072835070301000000122c0000001a0600000033100102000000320a0335040103000000100300000011030000003316010300000001030000005468616e6b20796f33100103000000010300000075210000000000003311010300000001030000000000000000000000331201030000000103000000000000000000000033130103000000320504350004030000001003000000010300000000e87648170000001003000000110400000011030000000703000000040000001003000000110300000003040000001f03000000040000000f1afa00000033160100000000320304130103000000d70faeecffc5c4e41003000000110000000013"));

    // Hello World example compiled with BlockTalk v0.0.0
    static byte[] HELLO_WORLD_CREATION_BYTES_V3 = getCreationBytes((short)3, 1, Convert.parseHexString("3033040300000000350001010000001e0100000007283507030000000012270000001a0100000033100101000000320a03350401020000001002000000110200000033160102000000010200000048656c6c6f2c20573310010200000001020000006f726c6400000000331101020000000102000000000000000000000033120102000000010200000000000000000000003313010200000032050413"));

    // Echo example compiled with BlockTalk v0.0.0
    static byte[] ECHO_CREATION_BYTES_V3 = getCreationBytes((short)3, 1, Convert.parseHexString("3033040300000000350001010000001e0100000007283507030000000012270000001a010000003310010100000032090335040102000000100200000035050102000000100200000035060102000000100200000035070102000000100200000033100101000000320a0335040102000000100200000011020000003316010200000011020000003313010200000011020000003312010200000011020000003311010200000011020000003310010200000032050413"));

    // Tip Thanks example compiled with BlockTalk v0.0.0
    static byte[] TIP_THANKS_CREATION_BYTES_V3 = getCreationBytes((short)3, 2, Convert.parseHexString("12fb0000003033040301000000350001020000001e02000000072835070301000000122c0000001a0600000033100102000000320a0335040103000000100300000011030000003316010300000001030000005468616e6b20796f33100103000000010300000075210000000000003311010300000001030000000000000000000000331201030000000103000000000000000000000033130103000000320504350004030000001003000000010300000000e87648170000001003000000110400000011030000000703000000040000001003000000110300000003040000001f03000000040000000f1afa00000033160100000000320304130103000000d70faeecffc5c4e41003000000110000000013"));

    static void setupMocks() {
        Stores mockStores = mock(Stores.class);
        ATStore mockAtStore = mock(ATStore.class);

        FluxCapacitor mockFluxCapacitor = QuickMocker.latestValueFluxCapacitor();
        //noinspection unchecked
        SignumKey.LongKeyFactory<AT> atLongKeyFactory = mock(SignumKey.LongKeyFactory.class);
        //noinspection unchecked
        SignumKey.LongKeyFactory<AT.ATState> atStateLongKeyFactory = mock(SignumKey.LongKeyFactory.class);
        mockStatic(Signum.class);
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
        SignumKey.LongKeyFactory<Account> mockAccountKeyFactory = mock(SignumKey.LongKeyFactory.class);
        Account mockAccount = mock(Account.class);
        Account.Balance mockAccountBalance = mock(Account.Balance.class);
        mockStatic(Account.class);

        doAnswer(invoke -> {
            AT at = invoke.getArgument(0);
            addedAts.add(at);
            if (onAtAdded != null) {
                onAtAdded.accept(at);
            }
            return null;
        }).when(mockAtTable).insert(ArgumentMatchers.any());
        when(mockAccount.getBalanceNqt()).thenReturn(TestConstants.TEN_SIGNA);
        when(mockAccountBalance.getBalanceNqt()).thenReturn(TestConstants.TEN_SIGNA);
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
        doAnswer(invoke -> {
          Long atId = invoke.getArgument(0);
          for (AT addedAt : addedAts) {
            if (AtApiHelper.getLong(addedAt.getId()) == atId) {
              return addedAt;
            }
          }
          return null;
        }).when(mockAtStore).getAT(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt());
        when(mockAtStore.getATs(ArgumentMatchers.anyCollection())).thenReturn(addedAts);
        when(mockAtTable.getAll(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(addedAts);
        when(Account.getOrAddAccount(ArgumentMatchers.anyLong())).thenReturn(mockAccount);
        when(Account.getAccount(ArgumentMatchers.anyLong())).thenReturn(mockAccount);
        when(Account.getAccountBalance(ArgumentMatchers.anyLong())).thenReturn(mockAccountBalance);
        when(mockAccountTable.get(ArgumentMatchers.any())).thenReturn(mockAccount);
        when(mockStores.getAccountStore()).thenReturn(mockAccountStore);
        when(mockAccountStore.getAccountKeyFactory()).thenReturn(mockAccountKeyFactory);
        when(mockAtStore.getAtStateTable()).thenReturn(mockAtStateTable);
        when(mockPropertyService.getBoolean(ArgumentMatchers.eq(Props.ENABLE_AT_DEBUG_LOG))).thenReturn(true);
        when(mockPropertyService.getInt(ArgumentMatchers.eq(Props.BRS_AT_PROCESSOR_CACHE_BLOCK_COUNT))).thenReturn(-1);
        when(mockAtStore.getAtTable()).thenReturn(mockAtTable);
        when(Signum.getPropertyService()).thenReturn(mockPropertyService);
        when(Signum.getBlockchain()).thenReturn(mockBlockchain);
        when(mockBlockchain.getHeight()).thenReturn(Integer.MAX_VALUE);
        when(mockAtStore.getAtDbKeyFactory()).thenReturn(atLongKeyFactory);
        when(mockAtStore.getAtStateDbKeyFactory()).thenReturn(atStateLongKeyFactory);
        when(mockStores.getAtStore()).thenReturn(mockAtStore);
        when(Signum.getStores()).thenReturn(mockStores);
        when(Signum.getFluxCapacitor()).thenReturn(mockFluxCapacitor);
    }

    static void clearAddedAts() {
        addedAts.clear();
        assertEquals(0, AT.getOrderedATs().size());
    }

    static void setOnAtAdded(Consumer<AT> onAtAdded) {
        AtTestHelper.onAtAdded = onAtAdded;
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

    public static byte[] getCreationBytes(short version, int codePages, byte[] code) {
        short cpages = ((short) codePages);
        short dpages = 1;
        short cspages = 1;
        short uspages = 1;
        long minActivationAmount = TestConstants.TEN_SIGNA;
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
        creation.putShort(version);
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
        AT.addAT(1L, TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED, "HelloWorld", "Hello World AT", AtTestHelper.HELLO_WORLD_CREATION_BYTES, Integer.MAX_VALUE, 0L);
    }

    public static void addEchoAT() {
        AT.addAT(2L, TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED, "Echo", "Message Echo AT", AtTestHelper.ECHO_CREATION_BYTES, Integer.MAX_VALUE, 0L);
    }

    public static void addTipThanksAT() {
        AT.addAT(3L, TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED, "TipThanks", "Tip Thanks AT", AtTestHelper.TIP_THANKS_CREATION_BYTES, Integer.MAX_VALUE, 0L);
    }

    public static void addHelloWorldATV3() {
      AT.addAT(1L, TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED, "HelloWorld", "Hello World AT", AtTestHelper.HELLO_WORLD_CREATION_BYTES_V3, Integer.MAX_VALUE, 0L);
    }

    public static void addEchoATV3() {
        AT.addAT(2L, TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED, "Echo", "Message Echo AT", AtTestHelper.ECHO_CREATION_BYTES_V3, Integer.MAX_VALUE, 0L);
    }

    public static void addTipThanksATV3() {
        AT.addAT(3L, TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED, "TipThanks", "Tip Thanks AT", AtTestHelper.TIP_THANKS_CREATION_BYTES_V3, Integer.MAX_VALUE, 0L);
    }

}
