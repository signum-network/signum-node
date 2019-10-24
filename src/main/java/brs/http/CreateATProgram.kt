package brs.http


import brs.Attachment
import brs.Constants
import brs.DependencyProvider
import brs.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_DESCRIPTION
import brs.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_NAME
import brs.http.JSONResponses.INCORRECT_AUTOMATED_TRANSACTION_NAME_LENGTH
import brs.http.JSONResponses.MISSING_NAME
import brs.http.common.Parameters.CODE_PARAMETER
import brs.http.common.Parameters.CREATION_BYTES_PARAMETER
import brs.http.common.Parameters.CSPAGES_PARAMETER
import brs.http.common.Parameters.DATA_PARAMETER
import brs.http.common.Parameters.DESCRIPTION_PARAMETER
import brs.http.common.Parameters.DPAGES_PARAMETER
import brs.http.common.Parameters.MIN_ACTIVATION_AMOUNT_NQT_PARAMETER
import brs.http.common.Parameters.NAME_PARAMETER
import brs.http.common.Parameters.USPAGES_PARAMETER
import brs.http.common.ResultFields.ERROR_CODE_RESPONSE
import brs.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE
import brs.util.TextUtils
import brs.util.convert.parseHexString
import brs.util.convert.parseUnsignedLong
import brs.util.logging.safeDebug
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.servlet.http.HttpServletRequest

internal class CreateATProgram(private val dp: DependencyProvider) : CreateTransaction(dp, arrayOf(APITag.AT, APITag.CREATE_TRANSACTION), NAME_PARAMETER, DESCRIPTION_PARAMETER, CREATION_BYTES_PARAMETER, CODE_PARAMETER, DATA_PARAMETER, DPAGES_PARAMETER, CSPAGES_PARAMETER, USPAGES_PARAMETER, MIN_ACTIVATION_AMOUNT_NQT_PARAMETER) {

    private val logger = LoggerFactory.getLogger(CreateATProgram::class.java)

    override fun processRequest(request: HttpServletRequest): JsonElement {
        var name: String? = request.getParameter(NAME_PARAMETER)
        val description = request.getParameter(DESCRIPTION_PARAMETER)

        if (name == null) {
            return MISSING_NAME
        }

        name = name.trim { it <= ' ' }
        if (name.length > Constants.MAX_AUTOMATED_TRANSACTION_NAME_LENGTH) {
            return INCORRECT_AUTOMATED_TRANSACTION_NAME_LENGTH
        }

        if (!TextUtils.isInAlphabet(name)) {
            return INCORRECT_AUTOMATED_TRANSACTION_NAME
        }

        if (description != null && description.length > Constants.MAX_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH) {
            return INCORRECT_AUTOMATED_TRANSACTION_DESCRIPTION
        }

        var creationBytes: ByteArray? = null

        if (request.getParameter(CODE_PARAMETER) != null) {
            try {
                val code = request.getParameter(CODE_PARAMETER)
                require(code.length and 1 == 0)

                var data: String? = request.getParameter(DATA_PARAMETER)
                if (data == null) {
                    data = ""
                }
                require(data.length and 1 == 0)

                val cpages = code.length / 2 / 256 + if (code.length / 2 % 256 != 0) 1 else 0
                val dpages = Integer.parseInt(request.getParameter(DPAGES_PARAMETER))
                val cspages = Integer.parseInt(request.getParameter(CSPAGES_PARAMETER))
                val uspages = Integer.parseInt(request.getParameter(USPAGES_PARAMETER))

                require(!(dpages < 0 || cspages < 0 || uspages < 0))

                val minActivationAmount = request.getParameter(MIN_ACTIVATION_AMOUNT_NQT_PARAMETER).parseUnsignedLong()

                var creationLength = 4 // version + reserved
                creationLength += 8 // pages
                creationLength += 8 // minActivationAmount
                creationLength += if (cpages * 256 <= 256) 1 else if (cpages * 256 <= 32767) 2 else 4 // code size
                creationLength += code.length / 2
                creationLength += if (dpages * 256 <= 256) 1 else if (dpages * 256 <= 32767) 2 else 4 // data size
                creationLength += data.length / 2

                val creation = ByteBuffer.allocate(creationLength)
                creation.order(ByteOrder.LITTLE_ENDIAN)
                creation.putShort(dp.atConstants.atVersion(dp.blockchain.height))
                creation.putShort(0.toShort())
                creation.putShort(cpages.toShort())
                creation.putShort(dpages.toShort())
                creation.putShort(cspages.toShort())
                creation.putShort(uspages.toShort())
                creation.putLong(minActivationAmount)
                putLength(cpages, code, creation)
                val codeBytes = code.parseHexString()
                if (codeBytes != null && codeBytes.isNotEmpty()) {
                    creation.put(codeBytes)
                }
                putLength(dpages, data, creation)
                val dataBytes = data.parseHexString()
                if (dataBytes != null && dataBytes.isNotEmpty()) {
                    creation.put(dataBytes)
                }

                creationBytes = creation.array()
            } catch (e: Exception) {
                val response = JsonObject()
                response.addProperty(ERROR_CODE_RESPONSE, 5)
                response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid or not specified parameters")
                return response
            }

        }

        if (creationBytes == null) {
            creationBytes = ParameterParser.getCreationBytes(request)
        }

        val account = dp.parameterService.getSenderAccount(request)
        val attachment = Attachment.AutomatedTransactionsCreation(dp, name, description!!, creationBytes!!, dp.blockchain.height)

        logger.safeDebug { "AT $name added successfully" }
        return createTransaction(request, account, attachment)
    }

    private fun putLength(nPages: Int, string: String, buffer: ByteBuffer) {
        if (nPages * 256 <= 256) {
            buffer.put((string.length / 2).toByte())
        } else if (nPages * 256 <= 32767) {
            buffer.putShort((string.length / 2).toShort())
        } else {
            buffer.putInt(string.length / 2)
        }
    }
}