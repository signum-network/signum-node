package brs.services.impl;

import brs.*;
import brs.BurstException.ValidationException;
import brs.assetexchange.AssetExchange;
import brs.at.AT;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.crypto.Crypto;
import brs.crypto.EncryptedData;
import brs.http.ParameterException;
import brs.services.ATService;
import brs.services.AccountService;
import brs.services.AliasService;
import brs.services.DGSGoodsStoreService;
import brs.util.Convert;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static brs.common.TestConstants.TEST_SECRET_PHRASE;
import static brs.http.common.Parameters.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class ParameterServiceImplTest {

  private ParameterServiceImpl t;

  private AccountService accountServiceMock;
  private AliasService aliasServiceMock;
  private AssetExchange assetExchangeMock;
  private DGSGoodsStoreService dgsGoodsStoreServiceMock;
  private Blockchain blockchainMock;
  private BlockchainProcessor blockchainProcessorMock;
  private TransactionProcessor transactionProcessorMock;
  private ATService atServiceMock;

  @Before
  public void setUp() {
    accountServiceMock = mock(AccountService.class);
    aliasServiceMock = mock(AliasService.class);
    assetExchangeMock = mock(AssetExchange.class);
    dgsGoodsStoreServiceMock = mock(DGSGoodsStoreService.class);
    blockchainMock = mock(Blockchain.class);
    blockchainProcessorMock = mock(BlockchainProcessor.class);
    transactionProcessorMock = mock(TransactionProcessor.class);
    atServiceMock = mock(ATService.class);

    t = new ParameterServiceImpl(accountServiceMock, aliasServiceMock, assetExchangeMock, dgsGoodsStoreServiceMock, blockchainMock, blockchainProcessorMock, transactionProcessorMock, atServiceMock);
  }

  @Test
  public void getAccount() throws BurstException {
    final String accountId = "123";
    final Account mockAccount = mock(Account.class);

    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(ACCOUNT_PARAMETER, accountId));

    when(accountServiceMock.getAccount(eq(123L))).thenReturn(mockAccount);

    assertEquals(mockAccount, t.getAccount(req));
  }

  @Test(expected = ParameterException.class)
  public void getAccount_MissingAccountWhenNoAccountParameterGiven() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();
    t.getAccount(req);
  }

  @Test(expected = ParameterException.class)
  public void getAccount_UnknownAccountWhenIdNotFound() throws BurstException {
    final String accountId = "123";
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(ACCOUNT_PARAMETER, accountId));

    when(accountServiceMock.getAccount(eq(123L))).thenReturn(null);

    t.getAccount(req);
  }

  @Test(expected = ParameterException.class)
  public void getAccount_IncorrectAccountWhenRuntimeExceptionOccurs() throws BurstException {
    final String accountId = "123";
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(ACCOUNT_PARAMETER, accountId));

    when(accountServiceMock.getAccount(eq(123L))).thenThrow(new RuntimeException());

    t.getAccount(req);
  }

  @Test
  public void getAccounts() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();
    final String accountID1 = "123";
    final String accountID2 = "321";
    final String[] accountIds = new String[]{accountID1, accountID2};

    when(req.getParameterValues(eq(ACCOUNT_PARAMETER))).thenReturn(accountIds);

    final Account mockAccount1 = mock(Account.class);
    final Account mockAccount2 = mock(Account.class);

    when(accountServiceMock.getAccount(eq(123L))).thenReturn(mockAccount1);
    when(accountServiceMock.getAccount(eq(321L))).thenReturn(mockAccount2);

    final List<Account> result = t.getAccounts(req);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(mockAccount1, result.get(0));
    assertEquals(mockAccount2, result.get(1));
  }

  @Test
  public void getAccounts_emptyResultWhenEmptyAccountValueGiven() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();
    final String[] accountIds = new String[]{""};

    when(req.getParameterValues(eq(ACCOUNT_PARAMETER))).thenReturn(accountIds);

    final List<Account> result = t.getAccounts(req);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  public void getAccounts_emptyResultWhenNullAccountValueGiven() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();
    final String[] accountIds = new String[]{null};

    when(req.getParameterValues(eq(ACCOUNT_PARAMETER))).thenReturn(accountIds);

    final List<Account> result = t.getAccounts(req);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }


  @Test(expected = ParameterException.class)
  public void getAccounts_missingAccountWhenNoParametersNull() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();
    when(req.getParameterValues(eq(ACCOUNT_PARAMETER))).thenReturn(null);

    t.getAccounts(req);
  }

  @Test(expected = ParameterException.class)
  public void getAccounts_missingAccountWhenNoParametersGiven() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();
    final String[] accountIds = new String[0];

    when(req.getParameterValues(eq(ACCOUNT_PARAMETER))).thenReturn(accountIds);

    t.getAccounts(req);
  }

  @Test(expected = ParameterException.class)
  public void getAccounts_unknownAccountWhenNotFound() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();
    final String accountID1 = "123";
    final String[] accountIds = new String[]{accountID1};

    when(req.getParameterValues(eq(ACCOUNT_PARAMETER))).thenReturn(accountIds);

    when(accountServiceMock.getAccount(eq(123L))).thenReturn(null);

    t.getAccounts(req);
  }

  @Test(expected = ParameterException.class)
  public void getAccounts_incorrectAccountWhenRuntimeExceptionOccurs() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();
    final String accountID1 = "123";
    final String[] accountIds = new String[]{accountID1};

    when(req.getParameterValues(eq(ACCOUNT_PARAMETER))).thenReturn(accountIds);

    when(accountServiceMock.getAccount(eq(123L))).thenThrow(new RuntimeException());

    t.getAccounts(req);
  }

  @Test
  public void getSenderAccount_withSecretPhrase() throws ParameterException {
    final String secretPhrase = TEST_SECRET_PHRASE;
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(SECRET_PHRASE_PARAMETER, secretPhrase));

    final Account mockAccount = mock(Account.class);

    when(accountServiceMock.getAccount(eq(Crypto.getPublicKey(secretPhrase)))).thenReturn(mockAccount);

    assertEquals(mockAccount, t.getSenderAccount(req));
  }

  @Test
  public void getSenderAccount_withPublicKey() throws ParameterException {
    final String publicKey = "123";
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(PUBLIC_KEY_PARAMETER, publicKey));

    final Account mockAccount = mock(Account.class);

    when(accountServiceMock.getAccount(eq(Convert.parseHexString(publicKey)))).thenReturn(mockAccount);

    assertEquals(mockAccount, t.getSenderAccount(req));
  }

  @Test(expected = ParameterException.class)
  public void getSenderAccount_withPublicKey_runtimeExceptionGivesParameterException() throws ParameterException {
    final String publicKey = "123";
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(PUBLIC_KEY_PARAMETER, publicKey));

    when(accountServiceMock.getAccount(eq(Convert.parseHexString(publicKey)))).thenThrow(new RuntimeException());

    t.getSenderAccount(req);
  }

  @Test(expected = ParameterException.class)
  public void getSenderAccount_missingSecretPhraseAndPublicKey() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();
    t.getSenderAccount(req);
  }

  @Test(expected = ParameterException.class)
  public void getSenderAccount_noAccountFoundResultsInUnknownAccount() throws ParameterException {
    final String publicKey = "123";
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(PUBLIC_KEY_PARAMETER, publicKey));

    when(accountServiceMock.getAccount(eq(Convert.parseHexString(publicKey)))).thenReturn(null);

    t.getSenderAccount(req);
  }

  @Test
  public void getAliasByAliasId() throws ParameterException {
    final Alias mockAlias = mock(Alias.class);

    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(ALIAS_PARAMETER, "123"));

    when(aliasServiceMock.getAlias(eq(123L))).thenReturn(mockAlias);

    assertEquals(mockAlias, t.getAlias(req));
  }

  @Test
  public void getAliasByAliasName() throws ParameterException {
    final Alias mockAlias = mock(Alias.class);

    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(ALIAS_NAME_PARAMETER, "aliasName"));

    when(aliasServiceMock.getAlias(eq("aliasName"))).thenReturn(mockAlias);

    assertEquals(mockAlias, t.getAlias(req));
  }

  @Test(expected = ParameterException.class)
  public void getAlias_wrongAliasFormatIsIncorrectAlias() throws ParameterException {
    t.getAlias(QuickMocker.httpServletRequest(new MockParam(ALIAS_PARAMETER, "Five")));
  }

  @Test(expected = ParameterException.class)
  public void getAlias_noAliasOrAliasNameGivenIsMissingAliasOrAliasName() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();
    t.getAlias(req);
  }

  @Test(expected = ParameterException.class)
  public void noAliasFoundIsUnknownAlias() throws ParameterException {
    final Alias mockAlias = mock(Alias.class);

    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(ALIAS_PARAMETER, "123"));

    when(aliasServiceMock.getAlias(eq(123L))).thenReturn(null);

    assertEquals(mockAlias, t.getAlias(req));
  }

  @Test
  public void getAsset() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(ASSET_PARAMETER, "123"));

    final Asset mockAsset = mock(Asset.class);

    when(assetExchangeMock.getAsset(eq(123L))).thenReturn(mockAsset);

    assertEquals(mockAsset, t.getAsset(req));
  }

  @Test(expected = ParameterException.class)
  public void getAsset_missingIdIsMissingAsset() throws ParameterException {
    t.getAsset(QuickMocker.httpServletRequest());
  }

  @Test(expected = ParameterException.class)
  public void getAsset_wrongIdFormatIsIncorrectAsset() throws ParameterException {
    t.getAsset(QuickMocker.httpServletRequest(new MockParam(ASSET_PARAMETER, "twenty")));
  }

  @Test(expected = ParameterException.class)
  public void getAsset_assetNotFoundIsUnknownAsset() throws ParameterException {
    when(assetExchangeMock.getAsset(eq(123L))).thenReturn(null);

    t.getAsset(QuickMocker.httpServletRequest(new MockParam(ASSET_PARAMETER, "123")));
  }

  @Test
  public void getGoods() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(GOODS_PARAMETER, "1")
    );

    final DigitalGoodsStore.Goods mockGoods = mock(DigitalGoodsStore.Goods.class);

    when(dgsGoodsStoreServiceMock.getGoods(eq(1L))).thenReturn(mockGoods);

    assertEquals(mockGoods, t.getGoods(req));
  }

  @Test(expected = ParameterException.class)
  public void getGoods_missingGoods() throws ParameterException {
    t.getGoods(QuickMocker.httpServletRequest());
  }

  @Test(expected = ParameterException.class)
  public void getGoods_unknownGoods() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(GOODS_PARAMETER, "1")
    );

    when(dgsGoodsStoreServiceMock.getGoods(eq(1L))).thenReturn(null);

    t.getGoods(req);
  }

  @Test(expected = ParameterException.class)
  public void getGoods_incorrectGoods() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(GOODS_PARAMETER, "notANumber")
    );

    t.getGoods(req);
  }

  @Test
  public void getPurchase() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PURCHASE_PARAMETER, "1")
    );

    final DigitalGoodsStore.Purchase mockPurchase = mock(DigitalGoodsStore.Purchase.class);

    when(dgsGoodsStoreServiceMock.getPurchase(eq(1L))).thenReturn(mockPurchase);

    assertEquals(mockPurchase, t.getPurchase(req));
  }

  @Test(expected = ParameterException.class)
  public void getPurchase_missingPurchase() throws ParameterException {
    t.getPurchase(QuickMocker.httpServletRequest());
  }

  @Test(expected = ParameterException.class)
  public void getPurchase_unknownPurchase() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PURCHASE_PARAMETER, "1")
    );

    when(dgsGoodsStoreServiceMock.getPurchase(eq(1L))).thenReturn(null);

    t.getPurchase(req);
  }

  @Test(expected = ParameterException.class)
  public void getPurchase_incorrectPurchase() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PURCHASE_PARAMETER, "notANumber")
    );

    t.getPurchase(req);
  }

  @Test
  public void getEncryptMessage_isNotText() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(MESSAGE_TO_ENCRYPT_PARAMETER, "beef123"),
        new MockParam(SECRET_PHRASE_PARAMETER, TEST_SECRET_PHRASE),
        new MockParam(MESSAGE_TO_ENCRYPT_IS_TEXT_PARAMETER, "false"));

    final Account mockRecipientAccount = mock(Account.class);
    when(mockRecipientAccount.getPublicKey()).thenReturn(new byte[0]);

    EncryptedData encryptedDataMock = mock(EncryptedData.class);

    when(mockRecipientAccount.encryptTo(eq(Convert.parseHexString("beef123")), eq(TEST_SECRET_PHRASE))).thenReturn(encryptedDataMock);

    assertEquals(encryptedDataMock, t.getEncryptedMessage(req, mockRecipientAccount, null));
  }

  @Test(expected = ParameterException.class)
  public void getEncryptMessage_missingRecipientParameterException() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(MESSAGE_TO_ENCRYPT_PARAMETER, "beef123"),
        new MockParam(SECRET_PHRASE_PARAMETER, TEST_SECRET_PHRASE),
        new MockParam(MESSAGE_TO_ENCRYPT_IS_TEXT_PARAMETER, "false"));

    EncryptedData encryptedDataMock = mock(EncryptedData.class);

    assertEquals(encryptedDataMock, t.getEncryptedMessage(req, null, null));
  }

  @Test
  public void getEncryptMessage_isText() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(MESSAGE_TO_ENCRYPT_PARAMETER, "message"),
        new MockParam(SECRET_PHRASE_PARAMETER, TEST_SECRET_PHRASE),
        new MockParam(MESSAGE_TO_ENCRYPT_IS_TEXT_PARAMETER, "true"));

    final Account mockRecipientAccount = mock(Account.class);
    when(mockRecipientAccount.getPublicKey()).thenReturn(new byte[0]);

    EncryptedData encryptedDataMock = mock(EncryptedData.class);

    when(mockRecipientAccount.encryptTo(eq(Convert.toBytes("message")), eq(TEST_SECRET_PHRASE))).thenReturn(encryptedDataMock);

    assertEquals(encryptedDataMock, t.getEncryptedMessage(req, mockRecipientAccount, null));
  }

  @Test
  public void getEncryptMessage_encryptMessageAndNonce() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ENCRYPTED_MESSAGE_DATA_PARAMETER, "abc"),
        new MockParam(ENCRYPTED_MESSAGE_NONCE_PARAMETER, "123"));

    EncryptedData result = t.getEncryptedMessage(req, null, null);

    assertEquals((byte) -85, result.getData()[0]);
    assertEquals((byte) 18, result.getNonce()[0]);
  }

  @Test(expected = ParameterException.class)
  public void getEncryptMessage_encryptMessageAndNonce_runtimeExceptionIncorrectEncryptedMessage() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ENCRYPTED_MESSAGE_DATA_PARAMETER, "zz"),
        new MockParam(ENCRYPTED_MESSAGE_NONCE_PARAMETER, "123"));

    t.getEncryptedMessage(req, null, null);
  }

  @Test(expected = ParameterException.class)
  public void getEncryptMessage_encryptionRuntimeExceptionParameterException() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(MESSAGE_TO_ENCRYPT_PARAMETER, "invalidHexNumber"),
        new MockParam(SECRET_PHRASE_PARAMETER, TEST_SECRET_PHRASE),
        new MockParam(MESSAGE_TO_ENCRYPT_IS_TEXT_PARAMETER, "false"));

    final Account mockAccount = mock(Account.class);
    when(accountServiceMock.getAccount(eq(Crypto.getPublicKey(TEST_SECRET_PHRASE)))).thenReturn(mockAccount);

    t.getEncryptedMessage(req, mockAccount, null);
  }

  @Test
  public void getEncryptMessage_messageToSelf_messageNullReturnsNull() throws ParameterException {
    assertNull(t.getEncryptedMessage(QuickMocker.httpServletRequest(), null, null));
  }

  @Test
  public void getEncryptToSelfMessage_isNotText() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(MESSAGE_TO_ENCRYPT_TO_SELF_PARAMETER, "beef123"),
        new MockParam(SECRET_PHRASE_PARAMETER, TEST_SECRET_PHRASE),
        new MockParam(MESSAGE_TO_ENCRYPT_TO_SELF_IS_TEXT_PARAMETER, "false"));

    final Account mockAccount = mock(Account.class);
    when(accountServiceMock.getAccount(eq(Crypto.getPublicKey(TEST_SECRET_PHRASE)))).thenReturn(mockAccount);

    EncryptedData encryptedDataMock = mock(EncryptedData.class);

    when(mockAccount.encryptTo(eq(Convert.parseHexString("beef123")), eq(TEST_SECRET_PHRASE))).thenReturn(encryptedDataMock);

    assertEquals(encryptedDataMock, t.getEncryptToSelfMessage(req));
  }

  @Test(expected = ParameterException.class)
  public void getEncryptToSelfMessage_isNotText_notHexParameterException() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(MESSAGE_TO_ENCRYPT_TO_SELF_PARAMETER, "zzz"),
        new MockParam(SECRET_PHRASE_PARAMETER, TEST_SECRET_PHRASE),
        new MockParam(MESSAGE_TO_ENCRYPT_TO_SELF_IS_TEXT_PARAMETER, "false"));

    final Account mockAccount = mock(Account.class);
    when(accountServiceMock.getAccount(eq(Crypto.getPublicKey(TEST_SECRET_PHRASE)))).thenReturn(mockAccount);

    EncryptedData encryptedDataMock = mock(EncryptedData.class);

    when(mockAccount.encryptTo(eq(Convert.parseHexString("beef123")), eq(TEST_SECRET_PHRASE))).thenReturn(encryptedDataMock);

    assertEquals(encryptedDataMock, t.getEncryptToSelfMessage(req));
  }

  @Test
  public void getEncryptToSelfMessage_encryptMessageToSelf_isText() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(MESSAGE_TO_ENCRYPT_TO_SELF_PARAMETER, "message"),
        new MockParam(SECRET_PHRASE_PARAMETER, TEST_SECRET_PHRASE),
        new MockParam(MESSAGE_TO_ENCRYPT_TO_SELF_IS_TEXT_PARAMETER, "true"));

    final Account mockAccount = mock(Account.class);
    when(accountServiceMock.getAccount(eq(Crypto.getPublicKey(TEST_SECRET_PHRASE)))).thenReturn(mockAccount);

    EncryptedData encryptedDataMock = mock(EncryptedData.class);

    when(mockAccount.encryptTo(eq(Convert.toBytes("message")), eq(TEST_SECRET_PHRASE))).thenReturn(encryptedDataMock);

    assertEquals(encryptedDataMock, t.getEncryptToSelfMessage(req));
  }

  @Test
  public void getEncryptToSelfMessage_encryptMessageAndNonce() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ENCRYPT_TO_SELF_MESSAGE_DATA, "abc"),
        new MockParam(ENCRYPT_TO_SELF_MESSAGE_NONCE, "123"));

    EncryptedData result = t.getEncryptToSelfMessage(req);

    assertEquals((byte) -85, result.getData()[0]);
    assertEquals((byte) 18, result.getNonce()[0]);
  }

  @Test(expected = ParameterException.class)
  public void getEncryptToSelfMessage_encryptMessageAndNonce_runtimeExceptionIncorrectEncryptedMessage() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ENCRYPT_TO_SELF_MESSAGE_DATA, "zz"),
        new MockParam(ENCRYPT_TO_SELF_MESSAGE_NONCE, "123"));

    t.getEncryptToSelfMessage(req);
  }

  @Test(expected = ParameterException.class)
  public void getEncryptToSelfMessage_encryptionRuntimeExceptionParameterException() throws ParameterException {
    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(MESSAGE_TO_ENCRYPT_TO_SELF_PARAMETER, "invalidHexNumber"),
        new MockParam(SECRET_PHRASE_PARAMETER, TEST_SECRET_PHRASE),
        new MockParam(MESSAGE_TO_ENCRYPT_TO_SELF_IS_TEXT_PARAMETER, "false"));

    final Account mockAccount = mock(Account.class);
    when(accountServiceMock.getAccount(eq(Crypto.getPublicKey(TEST_SECRET_PHRASE)))).thenReturn(mockAccount);

    t.getEncryptToSelfMessage(req);
  }

  @Test
  public void getEncryptToSelfMessage_messageToSelf_messageNullReturnsNull() throws ParameterException {
    assertNull(t.getEncryptToSelfMessage(QuickMocker.httpServletRequest()));
  }

  @Test
  public void getSecretPhrase() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(SECRET_PHRASE_PARAMETER, TEST_SECRET_PHRASE));

    assertEquals(TEST_SECRET_PHRASE, t.getSecretPhrase(req));
  }

  @Test(expected = ParameterException.class)
  public void getSecretPhrase_phraseMissingParameterException() throws ParameterException {
    t.getSecretPhrase(QuickMocker.httpServletRequest());
  }

  @Test
  public void getNumberOfConfirmations() throws ParameterException {
    when(blockchainMock.getHeight()).thenReturn(6);
    assertEquals(5, t.getNumberOfConfirmations(QuickMocker.httpServletRequest(new MockParam(NUMBER_OF_CONFIRMATIONS_PARAMETER, "5"))));
  }

  @Test
  public void getNumberOfConfirmations_emptyNumberOfConfirmationsIs0() throws ParameterException {
    assertEquals(0, t.getNumberOfConfirmations(QuickMocker.httpServletRequest()));
  }

  @Test(expected = ParameterException.class)
  public void getNumberOfConfirmations_wrongFormatNumberOfConfirmationsParameterException() throws ParameterException {
    t.getNumberOfConfirmations(QuickMocker.httpServletRequest(new MockParam(NUMBER_OF_CONFIRMATIONS_PARAMETER, "noNumber")));
  }

  @Test(expected = ParameterException.class)
  public void getNumberOfConfirmations_numberOfConfirmationsBiggerThanBlockchainHeightParameterException() throws ParameterException {
    when(blockchainMock.getHeight()).thenReturn(4);
    assertEquals(5, t.getNumberOfConfirmations(QuickMocker.httpServletRequest(new MockParam(NUMBER_OF_CONFIRMATIONS_PARAMETER, "5"))));
  }

  @Test
  public void getHeight() throws ParameterException {
    when(blockchainMock.getHeight()).thenReturn(6);
    when(blockchainProcessorMock.getMinRollbackHeight()).thenReturn(4);
    assertEquals(5, t.getHeight(QuickMocker.httpServletRequest(new MockParam(HEIGHT_PARAMETER, "5"))));
  }

  @Test
  public void getHeight_missingHeightParameterIsMinus1() throws ParameterException {
    assertEquals(-1, t.getHeight(QuickMocker.httpServletRequest()));
  }

  @Test(expected = ParameterException.class)
  public void getHeight_wrongFormatHeightParameterException() throws ParameterException {
    assertEquals(-1, t.getHeight(QuickMocker.httpServletRequest(new MockParam(HEIGHT_PARAMETER, "five"))));
  }

  @Test(expected = ParameterException.class)
  public void getHeight_negativeHeightParameterException() throws ParameterException {
    t.getHeight(QuickMocker.httpServletRequest(new MockParam(HEIGHT_PARAMETER, "-1")));
  }

  @Test(expected = ParameterException.class)
  public void getHeight_heightGreaterThanBlockchainHeightParameterException() throws ParameterException {
    when(blockchainMock.getHeight()).thenReturn(5);
    t.getHeight(QuickMocker.httpServletRequest(new MockParam(HEIGHT_PARAMETER, "6")));
  }

  @Test(expected = ParameterException.class)
  public void getHeight_heightUnderMinRollbackHeightParameterException() throws ParameterException {
    when(blockchainMock.getHeight()).thenReturn(10);
    when(blockchainProcessorMock.getMinRollbackHeight()).thenReturn(12);
    t.getHeight(QuickMocker.httpServletRequest(new MockParam(HEIGHT_PARAMETER, "10")));
  }

  @Test
  public void parseTransaction_transactionBytes() throws ValidationException, ParameterException {
    final Transaction mockTransaction = mock(Transaction.class);

    when(transactionProcessorMock.parseTransaction(any(byte[].class))).thenReturn(mockTransaction);

    assertEquals(mockTransaction, t.parseTransaction("123", null));
  }

  @Test(expected = ParameterException.class)
  public void parseTransaction_transactionBytes_validationExceptionParseHexStringOccurs() throws ParameterException {
    t.parseTransaction("ZZZ", null);
  }

  @Test(expected = ParameterException.class)
  public void parseTransaction_transactionBytes_runTimeExceptionOccurs() throws ValidationException, ParameterException {
    when(transactionProcessorMock.parseTransaction(any(byte[].class))).thenThrow(new RuntimeException());

    t.parseTransaction("123", null);
  }

  @Test
  public void parseTransaction_transactionJSON() throws ValidationException, ParameterException {
    final Transaction mockTransaction = mock(Transaction.class);

    when(transactionProcessorMock.parseTransaction(any(JsonObject.class))).thenReturn(mockTransaction);

    assertEquals(mockTransaction, t.parseTransaction(null, "{}"));
  }

  @Test(expected = ParameterException.class)
  public void parseTransaction_transactionJSON_validationExceptionOccurs() throws ParameterException, ValidationException {
    when(transactionProcessorMock.parseTransaction(any(JsonObject.class))).thenThrow(new BurstException.NotValidException(""));

    t.parseTransaction(null, "{}");
  }

  @Test(expected = ParameterException.class)
  public void parseTransaction_transactionJSON_runTimeExceptionOccurs() throws ParameterException, ValidationException {
    when(transactionProcessorMock.parseTransaction(any(JsonObject.class))).thenThrow(new RuntimeException());

    t.parseTransaction(null, "{}");
  }

  @Test(expected = ParameterException.class)
  public void parseTransaction_transactionJSON_parseExceptionTransactionProcessorOccurs() throws ParameterException {
    t.parseTransaction(null, "badJson");
  }

  @Test(expected = ParameterException.class)
  public void parseTransaction_missingRequiredTransactionBytesOrJson() throws ParameterException {
    t.parseTransaction(null, null);
  }

  @Test
  public void getAT() throws ParameterException {
    final long atId = 123L;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(AT_PARAMETER, atId)
    );

    final AT mockAT = mock(AT.class);

    when(atServiceMock.getAT(eq(atId))).thenReturn(mockAT);
    when(atServiceMock.getAT(eq(atId), ArgumentMatchers.anyInt())).thenReturn(mockAT);

    assertEquals(mockAT, t.getAT(req));
  }

  @Test(expected = ParameterException.class)
  public void getAT_missingAT() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    t.getAT(req);
  }

  @Test(expected = ParameterException.class)
  public void getAT_incorrectAT() throws ParameterException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(AT_PARAMETER, "notLongId")
    );

    t.getAT(req);
  }

  @Test(expected = ParameterException.class)
  public void getAT_unknownAT() throws ParameterException {
    final long atId = 123L;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(AT_PARAMETER, atId)
    );

    when(atServiceMock.getAT(eq(atId))).thenReturn(null);

    t.getAT(req);
  }

}
