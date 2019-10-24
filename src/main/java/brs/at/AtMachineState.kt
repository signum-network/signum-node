/*
 * Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file license.txt
 in the root project directory or http://www.opensource.org/licenses/mit-license.php.
*/

package brs.at

import brs.DependencyProvider
import brs.fluxcapacitor.FluxValues
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.experimental.and


open class AtMachineState {
    protected val dp: DependencyProvider
    val creationBlockHeight: Int
    val sleepBetween: Int
    val apCode: ByteBuffer
    internal val transactions: MutableMap<ByteBuffer, AtTransaction>
    var version: Short = 0
        private set
    private var gBalance: Long = 0
    private var pBalance: Long = 0
    val machineState: MachineState
    private var cSize: Int = 0
    private var dSize: Int = 0
    private var cUserStackBytes: Int = 0
    private var cCallStackBytes: Int = 0
    var id: ByteArray? = null
    var creator: ByteArray? = null
    internal var waitForNumberOfBlocks: Int = 0
    private var freezeWhenSameBalance: Boolean = false
    private var minActivationAmount: Long = 0
    lateinit var apData: ByteBuffer
    var height: Int = 0

    internal var a1: ByteArray
        get() = machineState.a1
        set(a1) {
            this.machineState.a1 = a1.clone()
        }

    internal var a2: ByteArray
        get() = machineState.a2
        set(a2) {
            this.machineState.a2 = a2.clone()
        }

    internal var a3: ByteArray
        get() = machineState.a3
        set(a3) {
            this.machineState.a3 = a3.clone()
        }

    internal var a4: ByteArray
        get() = machineState.a4
        set(a4) {
            this.machineState.a4 = a4.clone()
        }

    internal var b1: ByteArray
        get() = machineState.b1
        set(b1) {
            this.machineState.b1 = b1.clone()
        }

    internal var b2: ByteArray
        get() = machineState.b2
        set(b2) {
            this.machineState.b2 = b2.clone()
        }

    internal var b3: ByteArray
        get() = machineState.b3
        set(b3) {
            this.machineState.b3 = b3.clone()
        }

    internal var b4: ByteArray
        get() = machineState.b4
        set(b4) {
            this.machineState.b4 = b4.clone()
        }

    val apCodeBytes: ByteArray
        get() = apCode.array()

    val apDataBytes: ByteArray
        get() = apData.array()

    private val transactionBytes: ByteArray
        get() {
            val b = ByteBuffer.allocate((creator!!.size + 8) * transactions.size)
            b.order(ByteOrder.LITTLE_ENDIAN)
            for (tx in transactions.values) {
                b.put(tx.recipientId)
                b.putLong(tx.amount)
            }
            return b.array()
        }

    protected var state: ByteArray
        get() {
            val stateBytes = machineState.machineStateBytes
            val dataBytes = apData.array()

            val b = ByteBuffer.allocate(stateSize)
            b.order(ByteOrder.LITTLE_ENDIAN)

            b.put(stateBytes)
            b.putLong(gBalance)
            b.putLong(pBalance)
            b.putInt(waitForNumberOfBlocks)
            b.put(dataBytes)

            return b.array()
        }
        private set(state) {
            val b = ByteBuffer.allocate(state.size)
            b.order(ByteOrder.LITTLE_ENDIAN)
            b.put(state)
            b.flip()

            val stateSize = this.machineState.size
            val newMachineState = ByteArray(stateSize)
            b.get(newMachineState, 0, stateSize)
            this.machineState.setMachineState(newMachineState)

            gBalance = b.long
            pBalance = b.long
            waitForNumberOfBlocks = b.int

            val newApData = ByteArray(b.capacity() - b.position())
            b.get(newApData)
            this.apData = ByteBuffer.allocate(newApData.size)
            this.apData.order(ByteOrder.LITTLE_ENDIAN)
            this.apData.put(newApData)
            this.apData.clear()
        }

    private val stateSize: Int
        get() = this.machineState.size + 8 + 8 + 4 + apData.capacity()

    //these bytes are digested with MD5
    val bytes: ByteArray
        get() {
            val txBytes = transactionBytes
            val stateBytes = machineState.machineStateBytes
            val dataBytes = apData.array()

            val b = ByteBuffer.allocate(id!!.size + txBytes.size + stateBytes.size + dataBytes.size)
            b.order(ByteOrder.LITTLE_ENDIAN)

            b.put(id!!)
            b.put(stateBytes)
            b.put(dataBytes)
            b.put(txBytes)

            return b.array()
        }

    protected constructor(dp: DependencyProvider, atId: ByteArray, creator: ByteArray, version: Short,
                          stateBytes: ByteArray, cSize: Int, dSize: Int, cUserStackBytes: Int, cCallStackBytes: Int,
                          creationBlockHeight: Int, sleepBetween: Int,
                          freezeWhenSameBalance: Boolean, minActivationAmount: Long, apCode: ByteArray) {
        this.dp = dp
        this.id = atId
        this.creator = creator
        this.version = version
        this.machineState = MachineState()
        this.state = stateBytes
        this.cSize = cSize
        this.dSize = dSize
        this.cUserStackBytes = cUserStackBytes
        this.cCallStackBytes = cCallStackBytes
        this.creationBlockHeight = creationBlockHeight
        this.sleepBetween = sleepBetween
        this.freezeWhenSameBalance = freezeWhenSameBalance
        this.minActivationAmount = minActivationAmount

        this.apCode = ByteBuffer.allocate(apCode.size)
        this.apCode.order(ByteOrder.LITTLE_ENDIAN)
        this.apCode.put(apCode)
        this.apCode.clear()

        transactions = mutableMapOf()
    }

    protected constructor(dp: DependencyProvider, atId: ByteArray, creator: ByteArray, creationBytes: ByteArray, height: Int) {
        this.dp = dp
        this.version = dp.atConstants.atVersion(height)
        this.id = atId
        this.creator = creator

        val b = ByteBuffer.allocate(creationBytes.size)
        b.order(ByteOrder.LITTLE_ENDIAN)

        b.put(creationBytes)
        b.clear()

        this.version = b.short

        b.short //future: reserved for future needs

        val pageSize = dp.atConstants.pageSize(height).toInt()
        val codePages = b.short
        val dataPages = b.short
        val callStackPages = b.short
        val userStackPages = b.short

        this.cSize = codePages * pageSize
        this.dSize = dataPages * pageSize
        this.cCallStackBytes = callStackPages * pageSize
        this.cUserStackBytes = userStackPages * pageSize

        this.minActivationAmount = b.long

        var codeLen: Int
        if (codePages * pageSize < pageSize + 1) {
            codeLen = b.get().toInt()
            if (codeLen < 0)
                codeLen += (java.lang.Byte.MAX_VALUE + 1) * 2
        } else if (codePages * pageSize < java.lang.Short.MAX_VALUE + 1) {
            codeLen = b.short.toInt()
            if (codeLen < 0)
                codeLen += (java.lang.Short.MAX_VALUE + 1) * 2
        } else {
            codeLen = b.int
        }
        val code = ByteArray(codeLen)
        b.get(code, 0, codeLen)

        this.apCode = ByteBuffer.allocate(cSize)
        this.apCode.order(ByteOrder.LITTLE_ENDIAN)
        this.apCode.put(code)
        this.apCode.clear()

        var dataLen: Int
        if (dataPages * pageSize < 257) {
            dataLen = b.get().toInt()
            if (dataLen < 0)
                dataLen += (java.lang.Byte.MAX_VALUE + 1) * 2
        } else if (dataPages * pageSize < java.lang.Short.MAX_VALUE + 1) {
            dataLen = b.short.toInt()
            if (dataLen < 0)
                dataLen += (java.lang.Short.MAX_VALUE + 1) * 2
        } else {
            dataLen = b.int
        }
        val data = ByteArray(dataLen)
        b.get(data, 0, dataLen)

        this.apData = ByteBuffer.allocate(this.dSize + this.cCallStackBytes + this.cUserStackBytes)
        this.apData.order(ByteOrder.LITTLE_ENDIAN)
        this.apData.put(data)
        this.apData.clear()

        this.height = height
        this.creationBlockHeight = height
        this.waitForNumberOfBlocks = 0
        this.sleepBetween = 0
        this.freezeWhenSameBalance = false
        this.transactions = mutableMapOf()
        this.gBalance = 0
        this.pBalance = 0
        this.machineState = MachineState()
    }

    internal fun addTransaction(tx: AtTransaction) {
        val recipId = ByteBuffer.wrap(tx.recipientId)
        val oldTx = transactions[recipId]
        if (oldTx == null) {
            transactions[recipId] = tx
        } else {
            val newTx = AtTransaction(tx.senderId,
                    tx.recipientId,
                    oldTx.amount + tx.amount,
                    tx.message ?: oldTx.message)
            transactions[recipId] = newTx
        }
    }

    internal fun clearTransactions() {
        transactions.clear()
    }

    fun getTransactions(): Collection<AtTransaction> {
        return transactions.values
    }

    fun getcCallStackBytes(): Int {
        return cCallStackBytes
    }

    protected fun setcCallStackBytes(cCallStackBytes: Int) {
        this.cCallStackBytes = cCallStackBytes
    }

    fun getcUserStackBytes(): Int {
        return cUserStackBytes
    }

    protected fun setcUserStackBytes(cUserStackBytes: Int) {
        this.cUserStackBytes = cUserStackBytes
    }

    fun getcSize(): Int {
        return cSize
    }

    protected fun setcSize(cSize: Int) {
        this.cSize = cSize
    }

    fun getdSize(): Int {
        return dSize
    }

    protected fun setdSize(dSize: Int) {
        this.dSize = dSize
    }

    fun getgBalance(): Long {
        return gBalance
    }

    fun setgBalance(gBalance: Long) {
        this.gBalance = gBalance
    }

    fun getpBalance(): Long {
        return pBalance
    }

    fun setpBalance(pBalance: Long) {
        this.pBalance = pBalance
    }

    fun freezeOnSameBalance(): Boolean {
        return this.freezeWhenSameBalance
    }

    fun minActivationAmount(): Long {
        return this.minActivationAmount
    }

    fun setMinActivationAmount(minActivationAmount: Long) {
        this.minActivationAmount = minActivationAmount
    }

    fun setFreeze(freeze: Boolean) {
        this.freezeWhenSameBalance = freeze
    }

    inner class MachineState internal constructor() {
        private val flags = ByteArray(2)
        internal val jumps = TreeSet<Int>()
        var running: Boolean = false
        var stopped: Boolean = false
        var finished: Boolean = false
        var dead: Boolean = false
        internal var pc: Int = 0
        internal var pcs: Int = 0
        internal var opc: Int = 0
        internal var cs: Int = 0
        internal var us: Int = 0
        internal var err: Int = 0
        internal var steps: Int = 0
        internal var a1 = ByteArray(8)
        internal var a2 = ByteArray(8)
        internal var a3 = ByteArray(8)
        internal var a4 = ByteArray(8)
        internal var b1 = ByteArray(8)
        internal var b2 = ByteArray(8)
        internal var b3 = ByteArray(8)
        internal var b4 = ByteArray(8)

        internal val machineStateBytes: ByteArray
            get() {
                val bytes = ByteBuffer.allocate(size)
                bytes.order(ByteOrder.LITTLE_ENDIAN)

                if (dp.fluxCapacitor.getValue(FluxValues.AT_FIX_BLOCK_2)) {
                    flags[0] = ((if (running) 1 else 0)
                            or ((if (stopped) 1 else 0) shl 1)
                            or ((if (finished) 1 else 0) shl 2)
                            or ((if (dead) 1 else 0) shl 3)).toByte()
                    flags[1] = 0
                }

                bytes.put(flags)

                bytes.putInt(machineState.pc)
                bytes.putInt(machineState.pcs)
                bytes.putInt(machineState.cs)
                bytes.putInt(machineState.us)
                bytes.putInt(machineState.err)

                bytes.put(a1)
                bytes.put(a2)
                bytes.put(a3)
                bytes.put(a4)
                bytes.put(b1)
                bytes.put(b2)
                bytes.put(b3)
                bytes.put(b4)

                return bytes.array()
            }

        internal val size: Int
            get() = 2 + 4 + 4 + 4 + 4 + 4 + 4 * 8 + 4 * 8

        init {
            pcs = 0
            reset()
        }

        internal fun reset() {
            pc = pcs
            opc = 0
            cs = 0
            us = 0
            err = -1
            steps = 0
            if (!jumps.isEmpty())
                jumps.clear()
            flags[0] = 0
            flags[1] = 0
            running = false
            stopped = true
            finished = false
            dead = false
        }

        fun setMachineState(machineState: ByteArray) {
            val bf = ByteBuffer.allocate(size)
            bf.order(ByteOrder.LITTLE_ENDIAN)
            bf.put(machineState)
            bf.flip()

            bf.get(flags, 0, 2)
            running = flags[0] and 1.toByte() == 1.toByte()
            stopped = flags[0].toInt().ushr(1) and 1 == 1
            finished = flags[0].toInt().ushr(2) and 1 == 1
            dead = flags[0].toInt().ushr(3) and 1 == 1

            pc = bf.int
            pcs = bf.int
            cs = bf.int
            us = bf.int
            err = bf.int
            bf.get(a1, 0, 8)
            bf.get(a2, 0, 8)
            bf.get(a3, 0, 8)
            bf.get(a4, 0, 8)
            bf.get(b1, 0, 8)
            bf.get(b2, 0, 8)
            bf.get(b3, 0, 8)
            bf.get(b4, 0, 8)
        }

        fun getSteps(): Long {
            return steps.toLong()
        }
    }
}