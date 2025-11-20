package me.avinas.vanderwaals.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage
import me.avinas.vanderwaals.R

@Composable
fun WallpaperImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    GlideImage(
        imageModel = { imageUrl },
        modifier = modifier,
        imageOptions = ImageOptions(
            contentScale = contentScale,
            contentDescription = contentDescription
        ),
        loading = {
            Box(Modifier.matchParentSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        },
        failure = {
            Box(Modifier.matchParentSize()) {
                Icon(
                    painter = painterResource(R.drawable.ic_error_image),
                    contentDescription = "Failed to load",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    )
}
