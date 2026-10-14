package org.ozafry.nexuspos.db

import app.cash.sqldelight.db.SqlDriver

interface DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DatabaseDriverFactory): NexusDatabase {
    val driver = driverFactory.createDriver()
    return NexusDatabase(driver)
}
