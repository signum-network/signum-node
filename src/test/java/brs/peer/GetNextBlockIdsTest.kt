package brs.peer

import brs.Blockchain
import brs.Genesis
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers

import java.util.ArrayList

import org.junit.Assert.*

@RunWith(JUnit4::class)
class GetNextBlockIdsTest {
    private var getNextBlockIds: GetNextBlockIds? = null
    private var mockBlockchain: Blockchain? = null
    private var mockPeer: Peer? = null

    @Before
    fun setUpGetNextBlocksTest() {
        mockBlockchain = mock<Blockchain>()
        mockPeer = mock<Peer>()
        val blocks = ArrayList<Long>()
        for (i in 0..99) {
            blocks.add((i + 1).toLong())
        }
        whenever(mockBlockchain!!.getBlockIdsAfter(eq(Genesis.GENESIS_BLOCK_ID), any())).doReturn(blocks)
        getNextBlockIds = GetNextBlockIds(mockBlockchain)
    }

    @Test
    fun testGetNextBlocks() {
        val request = JsonObject()
        request.addProperty("blockId", java.lang.Long.toUnsignedString(Genesis.GENESIS_BLOCK_ID))
        val responseElement = getNextBlockIds!!.processRequest(request, mockPeer)
        assertNotNull(responseElement)
        assertTrue(responseElement is JsonObject)
        val response = responseElement.asJsonObject
        assertTrue(response.has("nextBlockIds"))
        val nextBlocksElement = response.get("nextBlockIds")
        assertNotNull(nextBlocksElement)
        assertTrue(nextBlocksElement is JsonArray)
        val nextBlocks = nextBlocksElement.asJsonArray
        assertEquals(100, nextBlocks.size().toLong())
        for (nextBlock in nextBlocks) {
            assertNotNull(nextBlock)
            assertTrue(nextBlock.isJsonPrimitive)
        }
    }

    @Test
    fun testGetNextBlocks_noIdSpecified() {
        val request = JsonObject()
        val responseElement = getNextBlockIds!!.processRequest(request, mockPeer)
        assertNotNull(responseElement)
        assertTrue(responseElement is JsonObject)
        val response = responseElement.asJsonObject
        assertTrue(response.has("nextBlockIds"))
        val nextBlocksElement = response.get("nextBlockIds")
        assertNotNull(nextBlocksElement)
        assertTrue(nextBlocksElement is JsonArray)
        val nextBlocks = nextBlocksElement.asJsonArray
        assertEquals(0, nextBlocks.size().toLong())
    }
}