package it.java.brs.variations

import brs.common.TestInfrastructure
import it.java.brs.ProcessASingleBlockTest

class ProcessASingleBlockTest_H2 : ProcessASingleBlockTest() {
    override fun getDbUrl(): String {
        return TestInfrastructure.IN_MEMORY_H2_DB_URL
    }
}
