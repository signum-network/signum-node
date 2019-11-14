package brs.common

object TestInfrastructure {
    const val IN_MEMORY_H2_DB_URL = "jdbc:h2:mem;DB_CLOSE_ON_EXIT=FALSE"
    const val IN_MEMORY_SQLITE_DB_URL = "jdbc:sqlite::memory:"

    const val TEST_API_PORT = 8885
}
