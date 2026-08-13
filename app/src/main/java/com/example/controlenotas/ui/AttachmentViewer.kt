package com.example.controlenotas.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.controlenotas.util.renderPdfFirstPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Mostra o anexo da nota em tela cheia, com zoom.
 *
 * É o que acontece ao tocar na foto já anexada: antes o toque reabria a câmera,
 * o que atrapalhava quem só queria conferir o que está escrito na nota. Para
 * trocar o anexo continuam existindo os botões "Foto" e "PDF / arquivo".
 */
@Composable
fun FullscreenAttachmentDialog(
    path: String,
    isPdf: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isPdf) {
                PdfPage(path = path, modifier = Modifier.fillMaxSize())
            } else {
                ZoomableContent(modifier = Modifier.fillMaxSize()) { contentModifier ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Anexo da nota em tela cheia",
                        contentScale = ContentScale.Fit,
                        modifier = contentModifier
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fechar",
                    tint = Color.White
                )
            }
        }
    }
}

/** Primeira página do PDF renderizada em resolução maior, para dar zoom. */
@Composable
private fun PdfPage(path: String, modifier: Modifier = Modifier) {
    var page by remember(path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val bitmap = withContext(Dispatchers.IO) { renderPdfFirstPage(File(path), 1800) }
        page = bitmap
        failed = bitmap == null
    }

    val bitmap = page
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> ZoomableContent(modifier = Modifier.fillMaxSize()) { contentModifier ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF da nota em tela cheia",
                    contentScale = ContentScale.Fit,
                    modifier = contentModifier
                )
            }

            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Não foi possível abrir este PDF.", color = Color.White)
            }

            else -> CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = Color.White
            )
        }
    }
}

/**
 * Envolve o conteúdo com zoom por pinça e arraste. Dois toques alternam entre o
 * tamanho normal e 2x, que é o gesto que todo mundo já espera de um visualizador.
 */
@Composable
private fun ZoomableContent(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) reset() else scale = 2f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content(
            Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}
