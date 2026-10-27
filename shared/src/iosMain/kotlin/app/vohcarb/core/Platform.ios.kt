package app.vohcarb.core

import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

actual fun deviceTimeZone(): String = NSTimeZone.localTimeZone.name
