package com.akardas.kaptor.store

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the platform SQL driver backing the inspector database.
 *
 * Android needs a [android.content.Context]; iOS and JVM construct the driver with no external
 * dependency. See the `actual` declarations in each source set.
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
