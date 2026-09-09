package dev.aaa1115910.bv.component.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.ui.theme.AccentBrush
import dev.aaa1115910.bv.ui.theme.BVColor

/**
 * 分组标题那根竖条的渐变。
 *
 * 不能直接用 AccentBrush：那是横向渐变，铺在 3dp 宽的竖条上等于把整条渐变压进 3 个像素，
 * 最后只会看到一坨混色。竖条得用竖向的。
 */
private val AccentBrushVertical = Brush.verticalGradient(
    colors = listOf(BVColor.Pink, BVColor.Violet, BVColor.Cyan)
)

/**
 * 设置内容页的统一骨架。
 *
 * 之前每个内容页各写各的排版：有的 Column + verticalScroll，有的在 Column 里直接塞
 * LazyColumn，左右边距有写 48dp 的也有不写的，还都用 displaySmall 把分类名又打了一遍——
 * 左边导航正高亮着同一个词，右边再来一个巨大的标题纯属重复，也把内容挤到了屏幕下半部分。
 *
 * 这里统一成「小一号的标题 + 一句说明 + 左对齐的设置项列表」，
 * 标题固定不滚动，列表自己滚，各页看起来才像同一个应用里的页面。
 */
@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    content: LazyListScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        SettingsPageHeader(title = title, subtitle = subtitle)
        // weight 而不是 fillMaxSize，理由同 SettingsNav：
        // 否则列表会按整页高度测量，标题占掉的那一截就把末尾几项顶到屏幕外面
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsPageHeader(
    title: String,
    subtitle: String?
) {
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 18.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = BVColor.TextPrimary
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = BVColor.TextTertiary
            )
        }
    }
}

/**
 * 长列表里的分组小标题。
 *
 * 播放设置这类页面一口气十来项，中间不分段的话找东西全靠一行行读；
 * 用一根渐变短竖线加一个小标签把它切成几段，和左侧导航的选中条是同一套语言。
 */
@Composable
fun SettingsGroupTitle(
    modifier: Modifier = Modifier,
    text: String
) {
    Row(
        modifier = modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AccentBrushVertical)
        )
        // 3dp 竖条 + 13dp 间距，正好让标签落在 28dp——
        // 也就是设置项图标的左边缘（外层 12dp 加 tv ListItem 自带的 16dp 内边距）
        Spacer(modifier = Modifier.width(13.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = BVColor.TextSecondary
        )
    }
}

/** 只读信息用的面板，比一堆裸 Text 更像个「卡片」 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 和 SettingListItem 一样的 12dp 外边距，两种页面的左边缘才对得上
            .padding(horizontal = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

/** 卡片里的一行键值：左边图标 + 名称，右边取值，取值右对齐成一列 */
@Composable
fun SettingsInfoRow(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(18.dp),
            imageVector = icon,
            contentDescription = null,
            tint = BVColor.TextTertiary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = BVColor.TextSecondary
        )
        Spacer(modifier = Modifier.width(24.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = BVColor.TextPrimary
            )
        }
    }
}

/**
 * 带占用条的一行，用在内存/存储这种「可用多少 / 共多少」上。
 *
 * 光给两个数字要在脑子里算一次比例才知道紧不紧张，
 * 底下压一根细条，一眼就能看出还剩多少。
 */
@Composable
fun SettingsUsageRow(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    /** 已用比例，0f~1f；拿不到真实数据时传 null，只显示文字 */
    usedFraction: Float?
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsInfoRow(icon = icon, label = label, value = value)
        if (usedFraction != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.07f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(usedFraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccentBrush)
                )
            }
        }
    }
}
