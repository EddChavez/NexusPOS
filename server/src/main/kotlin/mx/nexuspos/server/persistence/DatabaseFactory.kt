package mx.nexuspos.server.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import javax.sql.DataSource

class DatabaseFactory(
    private val jdbcUrl: String = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/nexuspos",
    private val user: String = System.getenv("DB_USER") ?: "nexus",
    private val password: String = System.getenv("DB_PASSWORD") ?: "nexuspassword"
) {
    val dataSource: DataSource

    init {
        val config = HikariConfig().apply {
            this.jdbcUrl = this@DatabaseFactory.jdbcUrl
            this.username = this@DatabaseFactory.user
            this.password = this@DatabaseFactory.password
            this.maximumPoolSize = 10
            this.minimumIdle = 2
            this.connectionTimeout = 5000
            this.isAutoCommit = false
        }
        dataSource = HikariDataSource(config)
    }

    fun migrate() {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .cleanDisabled(true)
            .load()
        try {
            flyway.migrate()
        } catch (e: org.flywaydb.core.api.exception.FlywayValidateException) {
            println("Flyway validation failed (checksum mismatch). Running repair to align schema history...")
            flyway.repair()
            flyway.migrate()
        }
    }

    fun <T> inTransaction(block: (Connection) -> T): T {
        val connection = dataSource.connection
        return try {
            connection.autoCommit = false
            val result = block(connection)
            connection.commit()
            result
        } catch (e: Exception) {
            try {
                connection.rollback()
            } catch (rollbackEx: Exception) {
                // Log rollback error
            }
            throw e
        } finally {
            connection.close()
        }
    }
}
