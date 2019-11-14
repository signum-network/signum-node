package it.java.brs.variations

import brs.common.TestInfrastructure
import it.java.brs.AlternativeRealityTest

class AlternativeRealityTest_Sqlite : AlternativeRealityTest() {
    override fun getDbUrl(): String {
        return TestInfrastructure.IN_MEMORY_SQLITE_DB_URL
    }
}
