package com.naomi.app.web

import com.naomi.web.WebReader
import com.naomi.web.impl.HttpWebReader

/**
 * The connected flavour's answer: a real reader.
 *
 * This is the only file in the app that names a networked type. Everything above
 * it deals in [WebReader], so the rest of the codebase compiles identically in
 * both flavours and cannot come to depend on the network being there.
 */
object WebReaderFactory {
    fun create(): WebReader = HttpWebReader()
}
