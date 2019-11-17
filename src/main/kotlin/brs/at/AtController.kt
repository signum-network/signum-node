package brs.at

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.objects.FluxValues
import brs.objects.Props
import brs.util.byteArray.skip
import brs.util.convert.toUnsignedString
import brs.util.crypto.Crypto
import brs.util.logging.safeDebug
import brs.util.logging.safeTrace
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLogger
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AtController(private val dp: DependencyProvider) {
    private val debugLogger = if (dp.propertyService.get(Props.ENABLE_AT_DEBUG_LOG)) logger else NOPLogger.NOP_LOGGER

    private fun runSteps(state: AtMachineState): Int {
        state.machineState.running = true
        state.machineState.stopped = false
        state.machineState.finished = false
        state.machineState.dead = false
        state.machineState.steps = 0

        val processor = AtMachineProcessor(dp, state, dp.propertyService.get(Props.ENABLE_AT_DEBUG_LOG))

        state.setFreeze(false)

        val stepFee = dp.atConstants.stepFee(state.creationBlockHeight)

        var numSteps = getNumSteps(state.apCode.get(state.machineState.pc), state.creationBlockHeight)

        while (state.machineState.steps + numSteps <= dp.atConstants.maxSteps(state.height)) {

            if (state.getgBalance() < stepFee * numSteps) {
                debugLogger.safeDebug { "stopped - not enough balance" }
                state.setFreeze(true)
                return 3
            }

            state.setgBalance(state.getgBalance() - stepFee * numSteps)
            state.machineState.steps += numSteps
            val rc = processor.processOp(disassemble = false, determineJumps = false)

            if (rc >= 0) {
                if (state.machineState.stopped) {
                    debugLogger.safeTrace { "stopped" }
                    state.machineState.running = false
                    return 2
                } else if (state.machineState.finished) {
                    debugLogger.safeTrace { "finished" }
                    state.machineState.running = false
                    return 1
                }
            } else {
                when (rc) {
                    -1 -> debugLogger.safeTrace { "error: overflow" }
                    -2 -> debugLogger.safeTrace { "error: invalid code" }
                    else -> debugLogger.safeTrace { "unexpected error" }
                }

                if (state.machineState.jumps.contains(state.machineState.err)) {
                    state.machineState.pc = state.machineState.err
                } else {
                    state.machineState.dead = true
                    state.machineState.running = false
                    return 0
                }
            }
            numSteps = getNumSteps(state.apCode.get(state.machineState.pc), state.creationBlockHeight)
        }

        return 5
    }

    private fun getNumSteps(op: Byte, height: Int): Int {
        return if (op in 0x32..55) dp.atConstants.apiStepMultiplier(height).toInt() else 1
    }

    fun resetMachine(state: AtMachineState) {
        state.machineState.reset()
        listCode(state, disassembly = true, determineJumps = true)
    }

    private fun listCode(state: AtMachineState, disassembly: Boolean, determineJumps: Boolean) {

        val machineProcessor = AtMachineProcessor(dp, state, dp.propertyService.get(Props.ENABLE_AT_DEBUG_LOG))

        val opc = state.machineState.pc
        val osteps = state.machineState.steps

        state.apCode.order(ByteOrder.LITTLE_ENDIAN)
        state.apData.order(ByteOrder.LITTLE_ENDIAN)

        state.machineState.pc = 0
        state.machineState.opc = opc

        while (true) {

            val rc = machineProcessor.processOp(disassembly, determineJumps)
            if (rc <= 0) break

            state.machineState.pc += rc
        }

        state.machineState.steps = osteps
        state.machineState.pc = opc
    }

    /**
     * Validates creation bytes. Does not validate code.
     * @return The total number of pages
     */
    fun checkCreationBytes(creation: ByteArray, height: Int): Int {
        val totalPages: Int
        try {
            val b = ByteBuffer.wrap(creation)
            b.order(ByteOrder.LITTLE_ENDIAN)

            val version = b.short
            if (version != dp.atConstants.atVersion(height)) {
                throw AtException(AtError.INCORRECT_VERSION.description)
            }

            // Ignore reserved bytes
            b.short //future: reserved for future needs

            val codePages = b.short
            if (codePages > dp.atConstants.maxMachineCodePages(height) || codePages < 1) {
                throw AtException(AtError.INCORRECT_CODE_PAGES.description)
            }

            val dataPages = b.short
            if (dataPages > dp.atConstants.maxMachineDataPages(height) || dataPages < 0) {
                throw AtException(AtError.INCORRECT_DATA_PAGES.description)
            }

            val callStackPages = b.short
            if (callStackPages > dp.atConstants.maxMachineCallStackPages(height) || callStackPages < 0) {
                throw AtException(AtError.INCORRECT_CALL_PAGES.description)
            }

            val userStackPages = b.short
            if (userStackPages > dp.atConstants.maxMachineUserStackPages(height) || userStackPages < 0) {
                throw AtException(AtError.INCORRECT_USER_PAGES.description)
            }

            // Ignore the minimum activation amount
            b.long

            val codeLen = getLength(codePages.toInt(), b)
            if (codeLen < 1 || codeLen > codePages * 256) {
                throw AtException(AtError.INCORRECT_CODE_LENGTH.description)
            }
            b.skip(codeLen)

            val dataLen = getLength(dataPages.toInt(), b)
            if (dataLen < 0 || dataLen > dataPages * 256) {
                throw AtException(AtError.INCORRECT_DATA_LENGTH.description)
            }
            b.skip(dataLen)

            totalPages = codePages.toInt() + dataPages.toInt() + userStackPages.toInt() + callStackPages.toInt()

            if (b.position() != b.capacity()) {
                throw AtException(AtError.INCORRECT_CREATION_TX.description)
            }

            // TODO note: run code in demo mode for checking if is valid
        } catch (e: BufferUnderflowException) {
            throw AtException(AtError.INCORRECT_CREATION_TX.description)
        }

        return totalPages
    }

    private fun getLength(nPages: Int, buffer: ByteBuffer): Int {
        var codeLen: Int
        if (nPages * 256 < 257) {
            codeLen = buffer.get().toInt()
            if (codeLen < 0)
                codeLen += (Byte.MAX_VALUE + 1) * 2
        } else if (nPages * 256 < Short.MAX_VALUE + 1) {
            codeLen = buffer.short.toInt()
            if (codeLen < 0)
                codeLen += (Short.MAX_VALUE + 1) * 2
        } else if (nPages * 256 <= Integer.MAX_VALUE) {
            codeLen = buffer.int
        } else {
            throw AtException(AtError.INCORRECT_CODE_LENGTH.description)
        }
        return codeLen
    }

    fun getCurrentBlockATs(freePayload: Int, blockHeight: Int): AtBlock {
        val orderedATs = AT.getOrderedATs(dp)
        val keys = orderedATs.iterator()

        val processedATs = mutableListOf<AT>()

        var payload = 0
        var totalFee: Long = 0
        var totalAmount: Long = 0

        while (payload <= freePayload - costOfOneAT && keys.hasNext()) {
            val id = keys.next()
            val at = AT.getAT(dp, id)!!

            val atAccountBalance = getATAccountBalance(id)
            val atStateBalance = at.getgBalance()

            if (at.freezeOnSameBalance() && atAccountBalance - atStateBalance < at.minActivationAmount()) {
                continue
            }

            if (atAccountBalance >= dp.atConstants.stepFee(at.creationBlockHeight) * dp.atConstants.apiStepMultiplier(at.creationBlockHeight)) {
                try {
                    at.setgBalance(atAccountBalance)
                    at.height = blockHeight
                    at.clearTransactions()
                    at.waitForNumberOfBlocks = at.sleepBetween
                    listCode(at, disassembly = true, determineJumps = true)
                    runSteps(at)

                    var fee = at.machineState.steps * dp.atConstants.stepFee(at.creationBlockHeight)
                    if (at.machineState.dead) {
                        fee += at.getgBalance()
                        at.setgBalance(0L)
                    }
                    at.setpBalance(at.getgBalance())

                    val amount = makeTransactions(at)
                    if (!dp.fluxCapacitorService.getValue(FluxValues.AT_FIX_BLOCK_4, blockHeight)) {
                        totalAmount = amount
                    } else {
                        totalAmount += amount
                    }

                    totalFee += fee
                    AT.addPendingFee(id, fee)

                    payload += costOfOneAT

                    processedATs.add(at)
                } catch (e: Exception) {
                    debugLogger.safeDebug(e) { "Error handling AT" }
                }

            }
        }

        val bytesForBlock: ByteArray?

        bytesForBlock = getBlockATBytes(processedATs, payload)

        return AtBlock(totalFee, totalAmount, bytesForBlock)
    }

    fun validateATs(blockATs: ByteArray?, blockHeight: Int): AtBlock {
        val ats = getATsFromBlock(blockATs)

        val processedATs = mutableListOf<AT>()

        var totalFee: Long = 0
        var totalAmount: Long = 0
        val md5 = Crypto.md5()

        for ((atId, receivedMd5) in ats) {
            val at = AT.getAT(dp, atId)!!
            try {
                at.clearTransactions()
                at.height = blockHeight
                at.waitForNumberOfBlocks = at.sleepBetween

                val atAccountBalance = getATAccountBalance(AtApiHelper.getLong(atId))
                if (atAccountBalance < dp.atConstants.stepFee(at.creationBlockHeight) * dp.atConstants.apiStepMultiplier(
                        at.creationBlockHeight
                    )
                ) {
                    throw AtException("AT has insufficient balance to run")
                }

                if (at.freezeOnSameBalance() && atAccountBalance - at.getgBalance() < at.minActivationAmount()) {
                    throw AtException("AT should be frozen due to unchanged balance")
                }

                if (at.nextHeight() > blockHeight) {
                    throw AtException("AT not allowed to run again yet")
                }

                at.setgBalance(atAccountBalance)

                listCode(at, true, true)

                runSteps(at)

                var fee = at.machineState.steps * dp.atConstants.stepFee(at.creationBlockHeight)
                if (at.machineState.dead) {
                    fee += at.getgBalance()
                    at.setgBalance(0L)
                }
                at.setpBalance(at.getgBalance())

                if (!dp.fluxCapacitorService.getValue(FluxValues.AT_FIX_BLOCK_4, blockHeight)) {
                    totalAmount = makeTransactions(at)
                } else {
                    totalAmount += makeTransactions(at)
                }

                totalFee += fee
                AT.addPendingFee(atId, fee)

                processedATs.add(at)

                val digest = at.getMD5Digest(md5)
                if (!digest.contentEquals(receivedMd5)) {
                    throw AtException("Calculated md5 and received md5 are not matching")
                }
            } catch (e: Exception) {
                throw AtException("ATs error. Block rejected", e)
            }

        }

        for (at in processedATs) {
            at.saveState()
        }

        return AtBlock(totalFee, totalAmount, ByteArray(1))
    }

    private fun getATsFromBlock(blockATs: ByteArray?): Map<ByteArray, ByteArray> {
        if (blockATs == null || blockATs.isEmpty()) {
            return emptyMap()
        }
        if (blockATs.isNotEmpty() && blockATs.size % costOfOneAT != 0) {
            throw AtException("blockATs size must be a multiple of cost of one AT ( $costOfOneAT )")
        }

        val b = ByteBuffer.wrap(blockATs)
        b.order(ByteOrder.LITTLE_ENDIAN)

        val ats = mutableMapOf<ByteArray, ByteArray>()

        while (b.position() < b.capacity()) {
            val atId = ByteArray(AtConstants.AT_ID_SIZE)
            b.get(atId, 0, atId.size)
            val md5 = ByteArray(16)
            b.get(md5, 0, 16)
            if (ats.containsKey(atId)) {
                throw AtException("AT included in block multiple times")
            }
            ats[atId] = md5
        }

        if (b.position() != b.capacity()) {
            throw AtException("bytebuffer not matching")
        }

        return ats
    }

    private fun getBlockATBytes(processedATs: List<AT>, payload: Int): ByteArray? {
        if (payload <= 0) {
            return null
        }

        val b = ByteArray(payload)

        val md5 = Crypto.md5()
        for ((i, at) in processedATs.withIndex()) {
            AtApiHelper.getByteArray(at.id, b, i * 24)
            at.getMD5Digest(md5, b, i * 24 + 8)
        }

        return b
    }

    //platform based implementations
    //platform based
    private fun makeTransactions(at: AT): Long {
        var totalAmount: Long = 0
        if (!dp.fluxCapacitorService.getValue(FluxValues.AT_FIX_BLOCK_4, at.height)) {
            for (tx in at.transactions.values) {
                if (AT.findPendingTransaction(tx.recipientId)) {
                    throw AtException("Conflicting transaction found")
                }
            }
        }
        for (tx in at.transactions.values) {
            totalAmount += tx.amount
            AT.addPendingTransaction(tx)
            logger.safeDebug { "Transaction to ${tx.recipientId.toUnsignedString()}, amount ${tx.amount}" }
        }
        return totalAmount
    }

    //platform based
    private fun getATAccountBalance(id: Long?): Long {
        val atAccount = Account.getAccount(dp, id!!)
        return atAccount?.balancePlanck ?: 0
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AtController::class.java)
        private const val costOfOneAT = AtConstants.AT_ID_SIZE + 16
    }
}
