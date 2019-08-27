package brs.http

import brs.BurstException
import brs.DigitalGoodsStore.Purchase
import brs.common.AbstractUnitTest
import brs.common.QuickMocker
import brs.common.QuickMocker.MockParam
import brs.services.DGSGoodsStoreService
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Before
import org.junit.Test

import brs.http.JSONResponses.MISSING_SELLER
import brs.http.common.Parameters.*
import brs.http.common.ResultFields.PURCHASES_RESPONSE
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever

class GetDGSPendingPurchasesTest : AbstractUnitTest() {

    private var t: GetDGSPendingPurchases? = null

    private var mockDGSGoodStoreService: DGSGoodsStoreService? = null

    @Before
    fun setUp() {
        mockDGSGoodStoreService = mock<DGSGoodsStoreService>()

        t = GetDGSPendingPurchases(mockDGSGoodStoreService!!)
    }

    @Test
    @Throws(BurstException::class)
    fun processRequest() {
        val sellerId = 123L
        val firstIndex = 1
        val lastIndex = 2

        val req = QuickMocker.httpServletRequest(
                MockParam(SELLER_PARAMETER, sellerId),
                MockParam(FIRST_INDEX_PARAMETER, firstIndex),
                MockParam(LAST_INDEX_PARAMETER, lastIndex)
        )

        val mockPurchase = mock<Purchase>()

        val mockPurchaseIterator = mockCollection<Purchase>(mockPurchase)
        whenever(mockDGSGoodStoreService!!.getPendingSellerPurchases(eq(sellerId), eq(firstIndex), eq(lastIndex))).doReturn(mockPurchaseIterator)

        val result = t!!.processRequest(req) as JsonObject
        assertNotNull(result)

        val resultPurchases = result.get(PURCHASES_RESPONSE) as JsonArray

        assertNotNull(resultPurchases)
        assertEquals(1, resultPurchases.size().toLong())
    }

    @Test
    @Throws(BurstException::class)
    fun processRequest_missingSeller() {
        val req = QuickMocker.httpServletRequest(
                MockParam(SELLER_PARAMETER, 0L)
        )

        assertEquals(MISSING_SELLER, t!!.processRequest(req))
    }

}