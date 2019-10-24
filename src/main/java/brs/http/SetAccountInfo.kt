package brs.http

import brs.Attachment
import brs.Constants
import brs.DependencyProvider
import brs.http.JSONResponses.INCORRECT_ACCOUNT_DESCRIPTION_LENGTH
import brs.http.JSONResponses.INCORRECT_ACCOUNT_NAME_LENGTH
import brs.http.common.Parameters.DESCRIPTION_PARAMETER
import brs.http.common.Parameters.NAME_PARAMETER
import com.google.gson.JsonElement
import javax.servlet.http.HttpServletRequest

internal class SetAccountInfo(private val dp: DependencyProvider) : CreateTransaction(dp, arrayOf(APITag.ACCOUNTS, APITag.CREATE_TRANSACTION), NAME_PARAMETER, DESCRIPTION_PARAMETER) {

    override fun processRequest(request: HttpServletRequest): JsonElement {

        val name = request.getParameter(NAME_PARAMETER).orEmpty().trim { it <= ' ' }
        val description = request.getParameter(DESCRIPTION_PARAMETER).orEmpty().trim { it <= ' ' }

        if (name.length > Constants.MAX_ACCOUNT_NAME_LENGTH) {
            return INCORRECT_ACCOUNT_NAME_LENGTH
        }

        if (description.length > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH) {
            return INCORRECT_ACCOUNT_DESCRIPTION_LENGTH
        }

        val account = dp.parameterService.getSenderAccount(request)
        val attachment = Attachment.MessagingAccountInfo(dp, name, description, dp.blockchain.height)
        return createTransaction(request, account, attachment)
    }
}