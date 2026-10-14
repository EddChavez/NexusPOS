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
        var driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        if (isNewDb) {
            NexusDatabase.Schema.create(driver)
        } else {
            try {
                driver.executeQuery(null, "SELECT unit FROM Product LIMIT 1", { it.next() }, 0)
            } catch (e: Exception) {
                try {
                    driver.execute(null, "ALTER TABLE Product ADD COLUMN unit TEXT NOT NULL DEFAULT 'PZA'", 0)
                    driver.execute(null, "ALTER TABLE SaleItem ADD COLUMN unit TEXT NOT NULL DEFAULT 'PZA'", 0)
                } catch (alterEx: Exception) {
                    driver.close()
                    dbFile.delete()
                    driver = JdbcSqliteDriver("jdbc:sqlite:$path")
                    NexusDatabase.Schema.create(driver)
                }
            }
        }
        return driver
    }
}
