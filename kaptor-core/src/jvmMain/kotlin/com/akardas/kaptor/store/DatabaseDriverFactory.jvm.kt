package com.akardas.kaptor.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.akardas.kaptor.db.KaptorDatabase

/**
 * JVM driver factory. Defaults to an in-memory database (handy for unit tests); pass a JDBC
 * url such as `jdbc:sqlite:/path/to/ktor_inspector.db` to persist to disk.
 */
actual class DatabaseDriverFactory(
    private val jdbcUrl: String = JdbcSqliteDriver.IN_MEMORY,
) {
    actual fun createDriver(): SqlDriver =
        JdbcSqliteDriver(jdbcUrl).also { KaptorDatabase.Schema.create(it) }
}
