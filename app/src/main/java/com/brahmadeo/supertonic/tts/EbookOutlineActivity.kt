package com.brahmadeo.supertonic.tts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brahmadeo.supertonic.tts.ui.theme.SupertonicTheme
import com.brahmadeo.supertonic.tts.utils.EbookManager
import com.brahmadeo.supertonic.tts.utils.EbookParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import java.io.File

class EbookOutlineActivity : ComponentActivity() {

    private lateinit var ebookParser: EbookParser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ebookParser = EbookParser(this)

        val ebookPath = intent.getStringExtra(EXTRA_URI)
        if (ebookPath == null) {
            finish()
            return
        }

        val ebookFile = File(ebookPath)

        setContent {
            SupertonicTheme {
                OutlineScreen(
                    ebookFile = ebookFile,
                    onTextExtracted = { text ->
                        val resultIntent = Intent()
                        resultIntent.putExtra(EXTRA_TEXT, text)
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun OutlineScreen(
        ebookFile: File,
        onTextExtracted: (String) -> Unit,
        onBack: () -> Unit
    ) {
        var publication by remember { mutableStateOf<Publication?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var isExtracting by remember { mutableStateOf(false) }
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        
        val isPdf = ebookFile.extension.lowercase() == "pdf"

        LaunchedEffect(ebookFile) {
            val result = ebookParser.openPublication(ebookFile)
            publication = result.getOrNull()
            isLoading = false
            if (publication == null) {
                Toast.makeText(this@EbookOutlineActivity, "Failed to open ebook", Toast.LENGTH_SHORT).show()
                onBack()
            } else {
                val title = publication?.metadata?.title ?: "Unknown Title"
                EbookManager.addBook(this@EbookOutlineActivity, title, ebookFile.absolutePath)
                if (isPdf) selectedTabIndex = 1 // Default to Pages for PDF
            }
        }

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(publication?.metadata?.title ?: "Ebook Details") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                    if (!isLoading && publication != null) {
                        TabRow(selectedTabIndex = selectedTabIndex) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text("Chapters") }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text("Pages") }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (publication != null) {
                    when (selectedTabIndex) {
                        0 -> ChapterTab(publication!!, onTextExtracted) { isExtracting = it }
                        1 -> PagesTab(ebookFile, publication!!, onTextExtracted) { isExtracting = it }
                    }
                }

                if (isExtracting) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Extracting text...", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ChapterTab(
        publication: Publication,
        onTextExtracted: (String) -> Unit,
        setExtracting: (Boolean) -> Unit
    ) {
        val toc = publication.tableOfContents
        val links = if (toc.isEmpty()) publication.readingOrder else toc
        
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(links) { link ->
                ChapterItem(link, publication, onTextExtracted, setExtracting)
                link.children.forEach { child ->
                    ChapterItem(child, publication, onTextExtracted, setExtracting, level = 1)
                }
            }
        }
    }

    @Composable
    fun ChapterItem(
        link: Link,
        publication: Publication,
        onTextExtracted: (String) -> Unit,
        setExtracting: (Boolean) -> Unit,
        level: Int = 0
    ) {
        ListItem(
            headlineContent = { 
                Text(
                    text = link.title ?: link.href.toString(), 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = (level * 16).dp)
                ) 
            },
            modifier = Modifier.clickable {
                setExtracting(true)
                CoroutineScope(Dispatchers.Main).launch {
                    val result = ebookParser.extractText(publication, link)
                    setExtracting(false)
                    result.onSuccess { onTextExtracted(it) }
                        .onFailure { Toast.makeText(this@EbookOutlineActivity, it.message, Toast.LENGTH_SHORT).show() }
                }
            }
        )
    }

    @Composable
    fun PagesTab(
        file: File,
        publication: Publication,
        onTextExtracted: (String) -> Unit,
        setExtracting: (Boolean) -> Unit
    ) {
        var pageCount by remember { mutableIntStateOf(0) }
        val selectedPages = remember { mutableStateListOf<Int>() }
        
        LaunchedEffect(publication) {
            pageCount = publication.positions().size
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(pageCount) { index ->
                    PageThumbnailItem(
                        file = file,
                        pageIndex = index,
                        isSelected = selectedPages.contains(index),
                        onClick = {
                            if (selectedPages.contains(index)) {
                                selectedPages.remove(index)
                            } else {
                                selectedPages.add(index)
                            }
                        }
                    )
                }
            }

            if (selectedPages.isNotEmpty()) {
                Button(
                    onClick = {
                        setExtracting(true)
                        CoroutineScope(Dispatchers.Main).launch {
                            val result = ebookParser.extractPages(publication, selectedPages.toList())
                            setExtracting(false)
                            result.onSuccess { onTextExtracted(it) }
                                .onFailure { Toast.makeText(this@EbookOutlineActivity, it.message, Toast.LENGTH_SHORT).show() }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .fillMaxWidth(0.8f)
                ) {
                    Text("Load ${selectedPages.size} Page(s)")
                }
            }
        }
    }

    @Composable
    fun PageThumbnailItem(
        file: File,
        pageIndex: Int,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        var thumbnail by remember { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(file, pageIndex) {
            withContext(Dispatchers.IO) {
                try {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    val page = renderer.openPage(pageIndex)
                    val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    thumbnail = bitmap
                    page.close()
                    renderer.close()
                    pfd.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onClick() }
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                thumbnail?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).align(Alignment.Center),
                    strokeWidth = 2.dp
                )
                
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    )
                }
            }
            Text(
                text = "${pageIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    companion object {
        const val EXTRA_URI = "ebook_uri"
        const val EXTRA_TEXT = "extracted_text"
    }
}
