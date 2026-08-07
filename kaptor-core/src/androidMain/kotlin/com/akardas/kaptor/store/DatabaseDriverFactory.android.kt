package com.akardas.kaptor.store

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.akardas.kaptor.db.KaptorDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(KaptorDatabase.Schema, context, "ktor_inspector.db")
}
