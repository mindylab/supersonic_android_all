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
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.use
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.publication.services.content.Content
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
            Log.d("EbookParser", "Extracting text for link: ${link?.href}")
            
            // Priority 1: Content Service (Experimental but best for semantic text)
            val locator = link?.let { Locator(href = it.url(), mediaType = it.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.BINARY) }
            val content = publication.content(locator)
            
            if (content != null) {
                val chapterText = StringBuilder()
                val elements = content.elements()
                val targetHref = link?.url()?.toString()

                for (element in elements) {
                    // If we have a targetHref, we stop when we leave that resource
                    if (targetHref != null && element.locator.href.toString() != targetHref) {
                        break
                    }
                    if (element is Content.TextualElement) {
                        element.text?.let { if (it.isNotBlank()) chapterText.append(it).append("\n\n") }
                    }
                }
                
                val result = chapterText.toString().trim()
                if (result.isNotBlank()) {
                    Log.d("EbookParser", "Extracted ${result.length} chars via Content API")
                    return@withContext Result.success(result)
                }
            }

            // Priority 2: Direct Resource Read + HTML Render (Fallback)
            val fullText = StringBuilder()
            if (link != null) {
                val resource = publication.get(link)
                if (resource != null) {
                    val bytes = resource.use { it.read().getOrElse { ByteArray(0) } }
                    if (bytes.isNotEmpty()) {
                        val text = bytes.decodeToString()
                        fullText.append(renderHtml(text))
                    }
                }
            } else {
                for (readingLink in publication.readingOrder) {
                    val resource = publication.get(readingLink) ?: continue
                    val bytes = resource.use { it.read().getOrElse { ByteArray(0) } }
                    if (bytes.isNotEmpty()) {
                        val text = bytes.decodeToString()
                        val rendered = renderHtml(text)
                        if (rendered.isNotBlank()) {
                            fullText.append(rendered).append("\n\n")
                        }
                    }
                }
            }

            val resultText = fullText.toString().trim()
            if (resultText.isNotBlank()) {
                Log.d("EbookParser", "Extracted ${resultText.length} chars via Resource Fallback")
                return@withContext Result.success(resultText)
            }

            Result.failure<String>(Exception("No text content could be extracted."))
        } catch (e: Exception) {
            Log.e("EbookParser", "Error extracting text", e)
            Result.failure<String>(e)
        }
    }

    private fun renderHtml(html: String): String {
        if (!html.contains("<html", ignoreCase = true) && !html.contains("<body", ignoreCase = true)) return html
        
        var text = html
        // Remove style/script/head
        text = text.replace(Regex("<head>.*?</head>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("<script.*?>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("<style.*?>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        
        // Block tags to newlines
        text = text.replace(Regex("<(p|div|h[1-6]|li|br|tr|blockquote|title|header|footer).*?>", RegexOption.IGNORE_CASE), "\n")
        
        // Strip remaining tags
        text = text.replace(Regex("<[^>]*>"), " ")
        
        // HTML Entities
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
        
        // Clean up whitespace: normalize spaces and merge excessive newlines
        return text.split("\n")
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .trim()
    }
}
