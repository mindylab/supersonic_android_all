package com.brahmadeo.supertonic.tts.utils

import android.content.Context
import android.net.Uri
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
import java.io.File

class EbookParser(private val context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationParser = DefaultPublicationParser(context, httpClient, assetRetriever, null)
    private val publicationOpener = PublicationOpener(publicationParser)

    suspend fun openPublication(uri: Uri): Result<Publication> = withContext(Dispatchers.IO) {
        try {
            val url = uri.toAbsoluteUrl()
                ?: return@withContext Result.failure<Publication>(Exception("Failed to convert URI to Readium URL"))

            val asset = assetRetriever.retrieve(url).getOrElse { error: Error ->
                return@withContext Result.failure<Publication>(Exception("Failed to retrieve asset: ${error.message}"))
            }

            val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrElse { error: Error ->
                return@withContext Result.failure<Publication>(Exception("Failed to open publication: ${error.message}"))
            }

            Result.success(publication)
        } catch (e: Exception) {
            Result.failure<Publication>(e)
        }
    }

    suspend fun extractText(publication: Publication, link: Link? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fullText = StringBuilder()
            
            if (link != null) {
                // Extract specific chapter
                val bytes = publication.get(link)?.use { it.read().getOrElse { error: Error -> ByteArray(0) } } ?: ByteArray(0)
                val text = bytes.decodeToString()
                val cleanText = if (text.contains("<html", ignoreCase = true)) stripHtml(text) else text
                fullText.append(cleanText)
            } else {
                // Extract whole book (caution: expensive)
                for (readingLink in publication.readingOrder) {
                    val bytes = publication.get(readingLink)?.use { it.read().getOrElse { error: Error -> ByteArray(0) } } ?: ByteArray(0)
                    val text = bytes.decodeToString()
                    val cleanText = if (text.contains("<html", ignoreCase = true)) stripHtml(text) else text
                    if (cleanText.isNotBlank()) {
                        fullText.append(cleanText).append("\n\n")
                    }
                }
            }

            val resultText = fullText.toString().trim()

            if (resultText.isBlank()) {
                return@withContext Result.failure<String>(Exception("No text content could be extracted."))
            }

            Result.success(resultText)
        } catch (e: Exception) {
            Result.failure<String>(e)
        }
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
