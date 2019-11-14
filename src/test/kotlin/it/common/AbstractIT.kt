package it.common

import brs.Burst
import brs.common.TestInfrastructure
import brs.objects.Props
import brs.peer.ProcessBlock
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.mock
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.*

@RunWith(JUnit4::class)
abstract class AbstractIT {

    private lateinit var processBlock: ProcessBlock

    private lateinit var burst: Burst

    /**
     * Must be called by subclasses.
     */
    fun setupIT(dbUrl: String) {
        println("Setting up IT for test " + this.javaClass.toString())
        burst = Burst(testProperties(dbUrl), false)
        processBlock = ProcessBlock(burst.dp.blockchainService, burst.dp.blockchainProcessorService)
    }

    /**
     * Must be called by subclasses
     */
    fun tearDownIT() {
        try {
            burst.shutdown(false)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun testProperties(dbUrl: String): Properties {
        val props = Properties()

        props.setProperty(Props.DEV_TESTNET.name, "true")
        props.setProperty(Props.DEV_OFFLINE.name, "true")
        props.setProperty(Props.DEV_DB_URL.name, dbUrl)
        props.setProperty(Props.DB_MAX_ROLLBACK.name, "1440")
        props.setProperty(Props.DB_CONNECTIONS.name, "1")

        props.setProperty(Props.API_SERVER.name, "on")
        props.setProperty(Props.API_LISTEN.name, "127.0.0.1")
        props.setProperty(Props.DEV_API_PORT.name, TestInfrastructure.TEST_API_PORT.toString())
        props.setProperty(Props.API_ALLOWED.name, "*")
        props.setProperty(Props.API_UI_DIR.name, "html/ui")

        return props
    }

    fun processBlock(jsonFirstBlock: JsonObject) {
        processBlock.processRequest(jsonFirstBlock, mock())
    }

    fun rollback(height: Int) {
        burst.dp.blockchainProcessorService.popOffTo(height)
    }
}
