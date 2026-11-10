package io.github.zpan2327.liquidglass.demo

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import io.github.zpan2327.liquidglass.LiquidGlassNavigationBar
import io.github.zpan2327.liquidglass.LiquidGlassScaffold
import io.github.zpan2327.liquidglass.LiquidTab
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The whole demo: a full-bleed photo behind the bar. Nothing here is specific to the component —
 * it is the same three calls any consumer would make.
 */
class LiquidGlassDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme {
                DemoPage()
            }
        }
    }
}

@Composable
private fun DemoPage() {
    val context = LocalContext.current
    val background = remember { loadBackground(context) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    val tabs = remember {
        listOf(
            LiquidTab(label = "首页", icon = MiuixIcons.Home),
            LiquidTab(label = "音乐", icon = MiuixIcons.Music),
            LiquidTab(label = "搜索", icon = MiuixIcons.Search),
            LiquidTab(label = "设置", icon = MiuixIcons.Settings),
        )
    }

    LiquidGlassScaffold(
        bottomBar = {
            LiquidGlassNavigationBar(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onTabClick = { selectedIndex = it },
            )
        },
    ) {
        Image(
            bitmap = background,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun loadBackground(context: Context): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open("bg.jpg").use { BitmapFactory.decodeStream(it, null, bounds) }

    var sampleSize = 1
    val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
    while (maxDimension / sampleSize > 2048) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = context.assets.open("bg.jpg").use { BitmapFactory.decodeStream(it, null, options) }
    return checkNotNull(decoded) { "无法解码 assets/bg.jpg" }.asImageBitmap()
}
