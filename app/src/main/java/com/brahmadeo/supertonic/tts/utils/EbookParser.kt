package com.brahmadeo.supertonic.tts.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.use
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.positions
import java.io.File

class EbookParser(private val context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationParser = DefaultPublicationParser(context, httpClient, assetRetriever, null)
    private val publicationOpener = PublicationOpener(publicationParser)

    suspend fun openPublication(file: File): Result<Publication> = withContext(Dispatchers.IO) {
        try {
            Log.d("EbookParser", "Opening file: ${file.absolutePath}")
            val asset = assetRetriever.retrieve(file).getOrElse { error ->
                return@withContext Result.failure<Publication>(Exception("Failed to retrieve asset: ${error.message}"))
            }

            val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrElse { error ->
                return@withContext Result.failure<Publication>(Exception("Failed to open publication: ${error.message}"))
            }

            Result.success(publication)
        } catch (e: Exception) {
            Result.failure<Publication>(e)
        }
    }

    suspend fun extractText(publication: Publication, link: Link? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val startUrl = link?.url()?.toString()
            val startPath = startUrl?.substringBefore('#')
            
            val locator = link?.let { Locator(href = it.url(), mediaType = it.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.BINARY) }
            val content = publication.content(locator)
            
            if (content != null) {
                val chapterText = StringBuilder()
                val elements = content.elements()

                for (element in elements) {
                    val elementUrl = element.locator.href.toString()
                    val elementPath = elementUrl.substringBefore('#')
                    
                    if (startPath != null && elementPath != startPath) {
                        break
                    }
                    
                    if (element is Content.TextualElement) {
                        element.text?.let { if (it.isNotBlank()) chapterText.append(it).append("\n\n") }
                    }
                }
                
                val result = chapterText.toString().trim()
                if (result.isNotBlank()) return@withContext Result.success(result)
            }

            val fallbackText = extractFallback(publication, link)
            if (fallbackText.isNotBlank()) return@withContext Result.success(fallbackText)

            Result.failure<String>(Exception("No text content could be extracted."))
        } catch (e: Exception) {
            Result.failure<String>(e)
        }
    }

    suspend fun extractPages(publication: Publication, pageIndices: List<Int>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val positions = publication.positions()
            val combinedText = StringBuilder()
            
            // Filter only the pages requested
            for (index in pageIndices.sorted()) {
                if (index < 0 || index >= positions.size) continue
                
                val locator = positions[index]
                val content = publication.content(locator)
                
                if (content != null) {
                    val elements = content.elements()
                    // Extract only elements belonging to this page locator
                    for (element in elements) {
                        // Stop if we move to next page's locator
                        if (index + 1 < positions.size && element.locator.href == positions[index+1].href && element.locator.locations.progression == positions[index+1].locations.progression) {
                            break
                        }
                        if (element is Content.TextualElement) {
                            element.text?.let { if (it.isNotBlank()) combinedText.append(it).append(" ") }
                        }
                    }
                    combinedText.append("\n\n")
                }
            }
            
            val result = combinedText.toString().trim()
            if (result.isBlank()) {
                return@withContext Result.failure<String>(Exception("No text found on selected pages."))
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure<String>(e)
        }
    }

    private suspend fun extractFallback(publication: Publication, link: Link?): String {
        val fullText = StringBuilder()
        if (link != null) {
            val bytes = publication.get(link)?.use { it.read().getOrElse { ByteArray(0) } } ?: ByteArray(0)
            val text = bytes.decodeToString()
            fullText.append(renderHtml(text))
        } else {
            for (readingLink in publication.readingOrder) {
                val bytes = publication.get(readingLink)?.use { it.read().getOrElse { ByteArray(0) } } ?: ByteArray(0)
                val text = bytes.decodeToString()
                val rendered = renderHtml(text)
                if (rendered.isNotBlank()) {
                    fullText.append(rendered).append("\n\n")
                }
            }
        }
        return fullText.toString().trim()
    }

    private fun renderHtml(html: String): String {
        if (!html.contains("<html", ignoreCase = true) && !html.contains("<body", ignoreCase = true)) return html
        
        var text = html
        text = text.replace(Regex("<head>.*?</head>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("<script.*?>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("<style.*?>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        
        text = text.replace(Regex("<(p|div|h[1-6]|li|br|tr|blockquote|title|header|footer).*?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<[^>]*>"), " ")
        
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&rdquo;", "\"")
            .replace("&ldquo;", "\"")
        
        return text.split("\n")
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .trim()
    }
}
