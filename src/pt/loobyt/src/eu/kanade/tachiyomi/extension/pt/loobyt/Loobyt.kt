package eu.kanade.tachiyomi.extension.pt.loobyt

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Loobyt : ParsedHttpSource() {
    override val name = "Loobyt"
    override val baseUrl = "https://loobyt.org"
    override val lang = "pt-BR"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
        GET(if (page == 1) "$baseUrl/" else "$baseUrl/?page=$page", headers)

    override fun popularMangaSelector() = "a[href*=/manga/]"
    override fun popularMangaNextPageSelector() = "a[rel=next], .pagination a.next"
    override fun popularMangaFromElement(element: Element): SManga = element.toManga()

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/lancamentos?page=$page", headers)
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = element.toManga()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/search?query=${query.replace(" ", "+")}&page=$page", headers)
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = element.toManga()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text()?.trim().orEmpty()
        description = document.selectFirst("meta[name=description]")?.attr("content")
            ?: document.selectFirst("main p, .description, .synopsis")?.text()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("img")?.absUrl("src")
        genre = document.select("a[href*=genre], a[href*=tag], .genres a")
            .joinToString { it.text() }.ifBlank { null }
        status = SManga.UNKNOWN
    }

    override fun chapterListSelector() = "a[href*=/read/]"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val link = if (element.tagName() == "a") element else element.selectFirst("a")
        setUrlWithoutDomain(link?.attr("href").orEmpty())
        name = link?.text()?.trim()?.ifBlank { null } ?: "Capítulo"
    }

    override fun pageListParse(document: Document): List<Page> {
        val images = document.select("img").filter {
            val url = it.absUrl("src").ifBlank { it.absUrl("data-src") }
            url.isNotBlank() && (url.contains("noxmangas.org") || url.contains("mangalivre.org"))
        }
        return images.mapIndexed { index, image ->
            val url = image.absUrl("src").ifBlank { image.absUrl("data-src") }
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Image URLs are returned directly by pageListParse.")

    private fun Element.toManga(): SManga {
        val anchor = if (tagName() == "a") this else selectFirst("a")
        val image = selectFirst("img")
        val titleText = anchor?.text()?.trim().orEmpty()
            .ifBlank { image?.attr("alt").orEmpty() }
            .ifBlank { selectFirst("h2, h3, .title")?.text().orEmpty() }
        return SManga.create().apply {
            title = titleText
            setUrlWithoutDomain(anchor?.attr("href").orEmpty())
            thumbnail_url = image?.absUrl("src")?.ifBlank { image.absUrl("data-src") }
        }
    }
}
