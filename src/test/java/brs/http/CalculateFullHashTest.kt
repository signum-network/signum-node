package brs.http

import brs.util.JSON
import com.google.gson.JsonObject
import org.junit.Before
import org.junit.Test

import javax.servlet.http.HttpServletRequest

import brs.http.JSONResponses.MISSING_SIGNATURE_HASH
import brs.http.JSONResponses.MISSING_UNSIGNED_BYTES
import brs.http.common.Parameters.SIGNATURE_HASH_PARAMETER
import brs.http.common.Parameters.UNSIGNED_TRANSACTION_BYTES_PARAMETER
import brs.http.common.ResultFields.FULL_HASH_RESPONSE
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals

class CalculateFullHashTest {

    private var t: CalculateFullHash? = null

    @Before
    fun setUp() {
        t = CalculateFullHash()
    }

    @Test
    fun processRequest() {
        //TODO More sensible values here...
        val mockUnsignedTransactionBytes = "123"
        val mockSignatureHash = "123"
        val expectedFullHash = "fe09cbf95619345cde91e0dee049d55498085a152e19c1009cb8973f9e1b4518"

        val req = mock<HttpServletRequest>()

        whenever(req.getParameter(eq(UNSIGNED_TRANSACTION_BYTES_PARAMETER))).doReturn(mockUnsignedTransactionBytes)
        whenever(req.getParameter(eq(SIGNATURE_HASH_PARAMETER))).doReturn(mockSignatureHash)

        val result = JSON.getAsJsonObject(t!!.processRequest(req))
        assertEquals(expectedFullHash, JSON.getAsString(result.get(FULL_HASH_RESPONSE)))
    }

    @Test
    fun processRequest_missingUnsignedBytes() {
        assertEquals(MISSING_UNSIGNED_BYTES, t!!.processRequest(mock<HttpServletRequest>()))
    }

    @Test
    fun processRequest_missingSignatureHash() {
        val mockUnsignedTransactionBytes = "mockUnsignedTransactionBytes"
        val req = mock<HttpServletRequest>()

        whenever(req.getParameter(eq(UNSIGNED_TRANSACTION_BYTES_PARAMETER))).doReturn(mockUnsignedTransactionBytes)

        assertEquals(MISSING_SIGNATURE_HASH, t!!.processRequest(req))
    }
}