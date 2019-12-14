package brs.assetexchange

import brs.common.AbstractUnitTest
import brs.db.TradeStore
import brs.db.sql.SqlEntityTable
import brs.entity.Trade
import brs.services.impl.AssetTradeServiceImpl
import com.nhaarman.mockitokotlin2.*
import org.jooq.SortField
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AssetTradeServiceImplTest : AbstractUnitTest() {

    private lateinit var t: AssetTradeServiceImpl

    private lateinit var mockTradeStore: TradeStore
    private lateinit var mockTradeTable: SqlEntityTable<Trade>

    @Before
    fun setUp() {
        mockTradeStore = mock()
        mockTradeTable = mock()

        whenever(mockTradeStore.tradeTable).doReturn(mockTradeTable)

        t = AssetTradeServiceImpl(mockTradeStore)
    }

    @Test
    fun getAssetTrades() {
        val assetId = 123L
        val from = 1
        val to = 5

        val mockTradesIterator = mock<Collection<Trade>>()

        whenever(mockTradeStore.getAssetTrades(eq(assetId), eq(from), eq(to))).doReturn(mockTradesIterator)

        assertEquals(mockTradesIterator, t.getAssetTrades(assetId, from, to))
    }

    @Test
    fun getAccountAssetTrades() {
        val accountId = 12L
        val assetId = 123L
        val from = 1
        val to = 5

        val mockAccountAssetTradesIterator = mock<Collection<Trade>>()

        whenever(mockTradeStore.getAccountAssetTrades(eq(accountId), eq(assetId), eq(from), eq(to))).doReturn(mockAccountAssetTradesIterator)

        assertEquals(mockAccountAssetTradesIterator, t.getAccountAssetTrades(accountId, assetId, from, to))
    }

    @Test
    fun getAccountTrades() {
        val accountId = 123L
        val from = 1
        val to = 5

        val mockTradesIterator = mock<Collection<Trade>>()

        whenever(mockTradeStore.getAccountTrades(eq(accountId), eq(from), eq(to))).doReturn(mockTradesIterator)

        assertEquals(mockTradesIterator, t.getAccountTrades(accountId, from, to))
    }

    @Test
    fun getCount() {
        val count = 5

        whenever(mockTradeTable.count).doReturn(count)

        assertEquals(count.toLong(), t.count.toLong())
    }

    @Test
    fun getTradeCount() {
        val assetId = 123L
        val count = 5

        whenever(mockTradeStore.getTradeCount(eq(assetId))).doReturn(count)

        assertEquals(count.toLong(), t.getTradeCount(assetId).toLong())
    }

    @Test
    fun getAllTrades() {
        val from = 1
        val to = 2

        val mockTradeIterator = mockCollection<Trade>()

        whenever(mockTradeTable.getAll(eq(from), eq(to), any<Collection<SortField<*>>>())).doReturn(mockTradeIterator)

        assertEquals(mockTradeIterator, t.getAllTrades(from, to))
    }
}
