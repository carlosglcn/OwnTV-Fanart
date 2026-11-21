package tv.own.owntv.glcn

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.MediaType

/**
 * glcn: the playback report's "live groups" section — for each playlist, every live group in the provider's order with
 * the provider's own group id, how many channels it holds and its first few (in playlist order, with their numbers
 * and stream ids). Enough to tell, from a tester's report alone, whether a group holds channels it should not, or the
 * right ones in another order — without asking them to look.
 */
object GroupsReport {
    fun build(): String? = runCatching {
        val koin = GlobalContext.get()
        val sources = koin.get<SourceDao>()
        val categories = koin.get<CategoryDao>()
        val channels = koin.get<ChannelDao>()
        runBlocking {
            buildString {
                appendLine("--- live groups ---")
                for (src in sources.getAllOnce()) {
                    val cats = categories.observe(listOf(src.id), MediaType.LIVE).first()
                    appendLine("[${src.name}] ${cats.size} groups")
                    for (c in cats) {
                        val n = channels.countInCategoryOnce(c.id)
                        val first = channels.firstInCategory(c.id, FIRST_ROWS)
                            .joinToString(" | ") { ch -> listOfNotNull(ch.number?.let { "#$it" }, ch.name, ch.remoteId?.let { "($it)" }).joinToString(" ") }
                        appendLine("  ${c.name} [${c.remoteId}] · $n · $first")
                    }
                }
            }
        }
    }.getOrNull()

    private const val FIRST_ROWS = 6
}
