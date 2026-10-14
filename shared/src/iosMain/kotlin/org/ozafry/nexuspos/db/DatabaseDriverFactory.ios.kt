package org.ozafry.nexuspos.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class NativeDatabaseDriverFactory(
    private val dbName: String = "nexuspos.db"
) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        return NativeSqliteDriver(NexusDatabase.Schema, dbName)
    }
}
