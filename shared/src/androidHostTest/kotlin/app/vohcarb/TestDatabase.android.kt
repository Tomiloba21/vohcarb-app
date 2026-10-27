package app.vohcarb

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.vohcarb.db.VohcarbDatabase
import java.io.File

// A file rather than ":memory:", so every thread sees the same database.
actual fun testDriver(): SqlDriver {
    val file = File.createTempFile("vohcarb-test", ".db").apply { deleteOnExit() }
    return JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").also { VohcarbDatabase.Schema.create(it) }
}
