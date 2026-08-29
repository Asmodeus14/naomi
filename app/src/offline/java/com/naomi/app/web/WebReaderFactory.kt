package com.naomi.app.web

import com.naomi.web.UnavailableWebReader
import com.naomi.web.WebReader

/**
 * The offline flavour's answer: there is no reader.
 *
 * `:web-impl` is not a dependency of this build, so `HttpWebReader` does not
 * exist to be constructed here — this is not a policy decision made at runtime
 * that could be flipped by a flag, it is the absence of the code. The APK
 * declares no INTERNET permission to match.
 */
object WebReaderFactory {
    fun create(): WebReader = UnavailableWebReader
}
