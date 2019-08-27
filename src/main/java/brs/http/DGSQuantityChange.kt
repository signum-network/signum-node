package brs.http

import brs.*
import brs.http.JSONResponses.INCORRECT_DELTA_QUANTITY
import brs.http.JSONResponses.MISSING_DELTA_QUANTITY
import brs.http.JSONResponses.UNKNOWN_GOODS
import brs.services.ParameterService
import brs.util.Convert
import com.google.gson.JsonElement

import javax.servlet.http.HttpServletRequest


import brs.http.common.Parameters.DELTA_QUANTITY_PARAMETER
import brs.http.common.Parameters.GOODS_PARAMETER

internal class DGSQuantityChange internal constructor(private val parameterService: ParameterService, private val blockchain: Blockchain, apiTransactionManager: APITransactionManager) : CreateTransaction(arrayOf(APITag.DGS, APITag.CREATE_TRANSACTION), apiTransactionManager, GOODS_PARAMETER, DELTA_QUANTITY_PARAMETER) {

    @Throws(BurstException::class)
    internal override fun processRequest(req: HttpServletRequest): JsonElement {

        val account = parameterService.getSenderAccount(req)
        val goods = parameterService.getGoods(req)
        if (goods.isDelisted || goods.sellerId != account.getId()) {
            return UNKNOWN_GOODS
        }

        val deltaQuantity: Int
        try {
            val deltaQuantityString = Convert.emptyToNull(req.getParameter(DELTA_QUANTITY_PARAMETER))
                    ?: return MISSING_DELTA_QUANTITY
            deltaQuantity = Integer.parseInt(deltaQuantityString)
            if (deltaQuantity > Constants.MAX_DGS_LISTING_QUANTITY || deltaQuantity < -Constants.MAX_DGS_LISTING_QUANTITY) {
                return INCORRECT_DELTA_QUANTITY
            }
        } catch (e: NumberFormatException) {
            return INCORRECT_DELTA_QUANTITY
        }

        val attachment = Attachment.DigitalGoodsQuantityChange(goods.id, deltaQuantity, blockchain.height)
        return createTransaction(req, account, attachment)

    }

}