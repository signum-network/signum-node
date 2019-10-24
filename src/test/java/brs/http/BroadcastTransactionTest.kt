package brs.http

import brs.BurstException
import brs.Transaction
import brs.TransactionProcessor
import brs.http.common.Parameters.TRANSACTION_BYTES_PARAMETER
import brs.http.common.Parameters.TRANSACTION_JSON_PARAMETER
import brs.http.common.ResultFields.ERROR_CODE_RESPONSE
import brs.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE
import brs.http.common.ResultFields.FULL_HASH_RESPONSE
import brs.http.common.ResultFields.TRANSACTION_RESPONSE
import brs.services.ParameterService
import brs.services.TransactionService
import brs.util.JSON
import brs.util.convert.parseHexString
import brs.util.convert.toHexString
import brs.util.safeGetAsLong
import brs.util.safeGetAsString
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.servlet.http.HttpServletRequest

class BroadcastTransactionTest {

    private lateinit var t: BroadcastTransaction

    private lateinit var transactionProcessorMock: TransactionProcessor
    private lateinit var parameterServiceMock: ParameterService
    private lateinit var transactionServiceMock: TransactionService

    @Before
    fun setUp() {
        this.transactionProcessorMock = mock()
        this.parameterServiceMock = mock()
        this.transactionServiceMock = mock()

        t = BroadcastTransaction(transactionProcessorMock, parameterServiceMock, transactionServiceMock)
    }

    @Test
    fun processRequest() {
        val mockTransactionBytesParameter = "mockTransactionBytesParameter"
        val mockTransactionJson = "mockTransactionJson"

        val mockTransactionStringId = "mockTransactionStringId"
        val mockTransactionFullHash = "deadbeef".parseHexString()

        val request = mock<HttpServletRequest>()
        val mockTransaction = mock<Transaction>()

        whenever(mockTransaction.stringId).doReturn(mockTransactionStringId)
        whenever(mockTransaction.fullHash).doReturn(mockTransactionFullHash)

        whenever(request.getParameter(TRANSACTION_BYTES_PARAMETER)).doReturn(mockTransactionBytesParameter)
        whenever(request.getParameter(TRANSACTION_JSON_PARAMETER)).doReturn(mockTransactionJson)

        whenever(parameterServiceMock.parseTransaction(eq(mockTransactionBytesParameter), eq(mockTransactionJson))).doReturn(mockTransaction)

        val result = t.processRequest(request) as JsonObject

        verify(transactionProcessorMock).broadcast(eq(mockTransaction))

        assertEquals(mockTransactionStringId, result.get(TRANSACTION_RESPONSE).safeGetAsString())
        assertEquals(mockTransactionFullHash.toHexString(), result.get(FULL_HASH_RESPONSE).safeGetAsString())
    }

    @Test
    fun processRequest_validationException() {
        val mockTransactionBytesParameter = "mockTransactionBytesParameter"
        val mockTransactionJson = "mockTransactionJson"

        val request = mock<HttpServletRequest>()
        val mockTransaction = mock<Transaction>()

        whenever(request.getParameter(TRANSACTION_BYTES_PARAMETER)).doReturn(mockTransactionBytesParameter)
        whenever(request.getParameter(TRANSACTION_JSON_PARAMETER)).doReturn(mockTransactionJson)

        whenever(parameterServiceMock.parseTransaction(eq(mockTransactionBytesParameter), eq(mockTransactionJson))).doReturn(mockTransaction)

        doAnswer { throw BurstException.NotCurrentlyValidException("") }.whenever(transactionServiceMock).validate(eq(mockTransaction))

        val result = t.processRequest(request) as JsonObject

        assertEquals(4L, result.get(ERROR_CODE_RESPONSE).safeGetAsLong())
        assertNotNull(result.get(ERROR_DESCRIPTION_RESPONSE))
    }

    @Test
    fun requirePost() {
        assertTrue(t.requirePost())
    }
}