package org.ozafry.nexuspos.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

class DesktopDatabaseDriverFactory(
    private val customPath: String? = null
) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        val path = if (customPath != null) {
            customPath
        } else {
            val userHome = System.getProperty("user.home")
            val appDir = File(userHome, ".nexuspos")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            File(appDir, "nexuspos.db").absolutePath
        }

        val dbFile = File(path)
        val isNewDb = !dbFile.exists() || dbFile.length() == 0L
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        if (isNewDb) {
            NexusDatabase.Schema.create(driver)
        }
        return driver
    }
}
