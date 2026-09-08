package dev.aaa1115910.bv.component.search

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.size.Size
import dev.aaa1115910.bv.ui.theme.BVColor

/** 搜索词条目：默认透明贴在背景上，获焦时整条亮成品牌粉 */
@Composable
private fun searchKeywordColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = BVColor.TextSecondary,
    focusedContainerColor = BVColor.Pink,
    focusedContentColor = Color.White,
    pressedContainerColor = BVColor.PinkDeep,
    pressedContentColor = Color.White
)

@Composable
fun SearchKeyword(
    modifier: Modifier = Modifier,
    keyword: String,
    leadingIcon: String,
    trailingIcon: @Composable() (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    // 热搜列表里每个 item 都会走到这，不 remember 的话每次重组都会新建一个 ImageLoader
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data(data = leadingIcon)
            .size(Size.ORIGINAL)
            .build(),
        imageLoader = imageLoader,
        contentScale = ContentScale.FillHeight
    )

    if (leadingIcon != "" && painter.state is AsyncImagePainter.State.Success) {
        DenseListItem(
            modifier = modifier,
            selected = false,
            shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.small),
            colors = searchKeywordColors(),
            onClick = onClick,
            headlineContent = {
                Text(
                    text = keyword,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Image(
                    modifier = Modifier.height(16.dp),
                    painter = painter,
                    contentDescription = null,
                )
            },
            trailingContent = trailingIcon
        )
    } else {
        DenseListItem(
            modifier = modifier,
            selected = false,
            shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.small),
            colors = searchKeywordColors(),
            onClick = onClick,
            headlineContent = {
                Text(
                    text = keyword,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = trailingIcon
        )
    }
}