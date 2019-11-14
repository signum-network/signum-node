package it.java.brs.variations

import brs.common.TestInfrastructure
import it.java.brs.AlternativeRealityTest

class AlternativeRealityTest_H2 : AlternativeRealityTest() {
    override fun getDbUrl(): String {
        return TestInfrastructure.IN_MEMORY_H2_DB_URL
    }
}
