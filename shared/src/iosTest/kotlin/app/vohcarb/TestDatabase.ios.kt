package app.vohcarb

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import app.vohcarb.db.VohcarbDatabase

actual fun testDriver(): SqlDriver = inMemoryDriver(VohcarbDatabase.Schema)
