package it.java.brs

import com.google.gson.JsonObject
import it.common.AbstractIT
import it.common.BlockMessageBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test

abstract class ProcessASingleBlockTest : AbstractIT() {
    abstract fun getDbUrl(): String
    private lateinit var jsonFirstBlock: JsonObject

    @Before
    fun setUp() {
        jsonFirstBlock = BlockMessageBuilder()
            .payloadLength(0)
            .totalAmountPlanck(0)
            .totalFeePlanck(0)
            .generationSignature("305a98571a8b96f699449dd71eff051fc10a3475bce18c7dac81b3d9316a9780")
            .generatorPublicKey("a44e4299354f59919329a0bfbac7d6858873ef06c8db3a6a90158f581478bd38")
            .payloadHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            .blockSignature("0271d3d9adae0636d8d7c4a3612848f694690157693847734441f3a499410a0cadfc65aac43802afe39e22725465b9301eae89ae59fdd554835607a40c3b370b")
            .transactions(null)
            .version(3)
            .nonce("31687")
            .previousBlock("3444294670862540038")
            .timestamp(683)
            .previousBlockHash("065d8826c197cc2fc7059b15fedc7d700bc56320095eafb4c1ab115ba0a3979e")
            .toJson()
        setupIT(getDbUrl())
    }

    @After
    fun tearDown() {
        tearDownIT()
    }

    @Test
    fun canProcessASingleBlock() {
        processBlock(jsonFirstBlock)
        Thread.sleep(200)
    }

    @Test
    fun canRollback() {
        processBlock(jsonFirstBlock)
        Thread.sleep(200)
        rollback(0)
    }
}
