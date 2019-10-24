package brs.http

import brs.Constants.FEE_QUANT
import brs.common.QuickMocker
import brs.feesuggestions.FeeSuggestion
import brs.feesuggestions.FeeSuggestionCalculator
import brs.http.common.ResultFields.CHEAP_FEE_RESPONSE
import brs.http.common.ResultFields.PRIORITY_FEE_RESPONSE
import brs.http.common.ResultFields.STANDARD_FEE_RESPONSE
import brs.util.safeGetAsLong
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SuggestFeeTest {

    private lateinit var t: SuggestFee

    private lateinit var feeSuggestionCalculator: FeeSuggestionCalculator

    @Before
    fun setUp() {
        feeSuggestionCalculator = mock()

        t = SuggestFee(feeSuggestionCalculator)
    }

    @Test
    fun processRequest() {
        val request = QuickMocker.httpServletRequest()

        val cheap = 1 * FEE_QUANT
        val standard = 5 * FEE_QUANT
        val priority = 10 * FEE_QUANT
        val feeSuggestion = FeeSuggestion(cheap, standard, priority)

        whenever(feeSuggestionCalculator.giveFeeSuggestion()).doReturn(feeSuggestion)

        val result = t.processRequest(request) as JsonObject

        assertEquals(cheap, result.get(CHEAP_FEE_RESPONSE).safeGetAsLong())
        assertEquals(standard, result.get(STANDARD_FEE_RESPONSE).safeGetAsLong())
        assertEquals(priority, result.get(PRIORITY_FEE_RESPONSE).safeGetAsLong())
    }
}