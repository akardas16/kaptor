package com.akardas.kaptor.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.akardas.kaptor.db.KaptorDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(KaptorDatabase.Schema, "ktor_inspector.db")
}
