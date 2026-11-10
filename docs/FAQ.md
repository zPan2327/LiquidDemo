# 常见问题与踩坑记录

这份文档记录的不是「怎么用」，而是**把它接进真实工程时一定会踩的坑**：每一条都写清了现象、根因和改法。
其中标注 miuix 源码位置的，都是读 `miuix-blur` 的实现确认过的，不是猜的。

---

## 1. 玻璃边缘更透明、折射发虚，右半边像没采样

**现象**：胶囊中间的玻璃正常，靠左右两端（尤其右端）明显更透、像只剩一层白膜；折射带也糊。

**根因**：把 `Backdrop.drawBackdrop()` 里的 `size` 当成了 surface（胶囊）的尺寸。

`size` 实际是 **miuix 的采样层尺寸**：

```
层宽 = (胶囊宽 + 2 × padding) / downscaleFactor
```

实测：649 × 176 的胶囊、`padding = 40dp`（110px）、`downscaleFactor = 2`，回调里读到的是 `434 × 198`。
按 434 去截取背景，就等于只抓了胶囊中间那一条（还不到宽度的一半），剩下的部分没有任何采样，只剩
`onDrawSurface` 里的 40% 蒙层。

**改法**：反推 padding，把整层都截下来。

```kotlin
val layerWidth = size.width * downscaleFactor
val pad = (layerWidth - coordinates.size.width) / 2f
```

> 源码位置：`miuix-blur/.../DrawBackdropModifier.kt` → `renderBlurInto()`，
> `fullWidth = size.width + padding * 2`，再 `recordLayer(size = fullWidth / scaleFactor)`。

---

## 2. 折射的「参照物」整体偏上（或偏左）一个 padding

**现象**：玻璃里折射出来的是**胶囊正上方那一行**内容，而不是被胶囊挡住的那一行。偏移量刚好等于
`padding`（40dp ≈ 110px）。用大标题、滑杆、卡片边缘都能量出来。

**根因**：同一个 `size`，两种坐标系混用。

`miuix` 在调用你的 `drawBackdrop` **之前**先做了一次平移：

```kotlin
// DrawBackdropModifier.recordBackdrop()
val scaledPadding = if (padding == 0f) 0f else (padding / scaleFactor).toInt().toFloat()
translate(scaledPadding, scaledPadding) {
    onDrawBackdrop { backdrop.drawBackdrop(..., downscaleFactor = scaleFactor) }
}
```

也就是说：**绘制坐标的原点是 surface 的左上角**，采样层向左、向上各多出 `padding / downscaleFactor`
（负坐标区）。如果按「层左上角 = (0,0)」去算平移，整张位图就少抬了一个 padding，玻璃里出现的就是
上方那几行字。

**改法**：平移量相对 **surface** 计算，而不是相对层：

```kotlin
val surfaceInSource = node.positionInWindow() - sourceLocation
// 采样区域从层的左上角开始（把负方向那圈 padding 也抓进来）
val layerLeft = surfaceInSource.x - pad
// 画的时候，原点回到 surface
translate((bufferRect.left - surfaceInSource.x) / ds, (bufferRect.top - surfaceInSource.y) / ds) {
    drawImage(bitmap.asImageBitmap())
}
```

座标系换算一句话：`1 采样像素 == 1/downscaleFactor 源像素`，而绘制坐标系本身就是「源像素 / ds」，
所以**位图 1px 正好画 1 个单位，只能平移、不能缩放**。缩放写错就会得到 2× 或 4× 的放大内容。

**验证方法**：不要凭肉眼。把页面里一条横贯全宽的边界（卡片边缘、滑杆轨道）放到胶囊后面，量它
在胶囊外和胶囊内的 y 中心差；正常应该只差几个像素（边缘折射带压缩导致），差 100px 就是这条。

---

## 3. 按下时胶囊圆角出现黑色（或彩色）楔形

**现象**：按下/拖动时指示器的圆角处冒出三角形黑块，偶尔带彩色边。

**根因**：折射 + 色散在圆角处的采样会越出采样层。`LiquidGlassNavigationBar` 用的是 miuix 的
`LayerBackdrop`，它背后是整窗内容，越界也能采到东西；换成自定义 `Backdrop`（比如采样一个普通
`View`）就只有本层，越界 = 透明 = 黑（预乘 alpha 下透明就是黑）。

**改法**：显式把采样余量抬到「折射带 + 色散可能越界的最大值」。

```kotlin
effects = {
    padding = maxOf(padding, 40.dp.toPx())   // 别只依赖 lens() 自动给的 24dp
    lens(refractionHeight = 24.dp.toPx(), refractionAmount = 24.dp.toPx())
}
```

`lens()` 内部只会把 `padding` 抬到 `refractionAmount`，色散（`chromaticAberration = 0.5`）和按压放大
需要的余量更大，所以要自己补。容器和指示器**两层**都要补。

---

## 4. 外部内容滚动时，底栏和「选中项」不实时刷新

这是最容易卡住的一条，要分两个层次。

### 4.1 底栏本体：invalidate 不等于重画

**现象**：把 `LiquidGlassNavigationBar` 塞进 `FrameLayout`、背后是普通 `View`（`ScrollView`/`RecyclerView`）
时，页面滚动过程中玻璃完全不动，停在第一帧。

**根因**：`View#invalidate()` 只让宿主 View 的 RenderNode 脏了，Compose 内部那张 `AndroidComposeView`
仍然是干净的，`drawBackdrop` 的绘制节点不会重跑，也就不会重新采样。

**改法**：给组件传一个**会变化的普通值**，让绘制节点的 lambda 重建：

```kotlin
// 宿主：滚动回调里每帧 +1（注意合并到一帧一次，别在 onScrollChanged 里直接加）
redrawTick.intValue++

LiquidGlassNavigationBar(..., redrawSignal = redrawTick.intValue)
```

组件里只需要在 `effects { }` / `layerBlock { }` 里**读一下**它：

```kotlin
@Suppress("UNUSED_EXPRESSION")
redrawSignal
```

为什么必须是普通 `Int` 而不是 `State`：Compose 会把「只捕获稳定值的 lambda」记忆化（`remember`），
lambda 实例不变 → `DrawBackdropElement.equals()` 为 true → `update()` 不执行 → 不 `invalidateDrawCache()`。
捕获一个每帧都变的 `Int`，lambda 必须重建，节点才会更新。

### 4.2 选中项：录制层只在节点重画时才重录

**现象**：先拖一下底栏，松手，再滚动页面 —— 底栏本体是活的，**只有选中项那块玻璃定格**在松手那一刻。
（不先拖底栏有时看不出来，因为按压/松手动画一直在让它重画。）

**根因**：指示器用的是 `CombinedBackdrop(页面背板, tabsBackdrop)`，而 `tabsBackdrop` 是由
`Modifier.layerBackdrop()` 录下来的：

```kotlin
// LayerBackdropModifier.LayerBackdropNode
override fun ContentDrawScope.draw() {
    drawContent()
    recordLayer(backdrop.graphicsLayer) { backdrop.onDraw(this@draw) }   // ← 只有节点重画才重新录
}
```

被录的那一层（`alpha(0f)` 的图标行）平时不会因为页面滚动而失效，所以录制内容一直是旧的。

**改法**：给**录制源那一层**也接上 `redrawSignal`（不只是给指示器接）。三处都要：

| 位置 | 作用 |
| --- | --- |
| 容器 `drawBackdrop` | 底栏玻璃本体 |
| 隐藏图标行 `drawBackdrop`（`layerBackdrop(tabsBackdrop)` 那一层） | 指示器采样用的录制层 |
| 指示器 `drawBackdrop` | 指示器自身 |

---

## 5. 嵌进 `AbstractComposeView` 后胶囊顶边被削平

**现象**：静态看正常，一按下胶囊顶边变成一条直线（圆的被切掉）。

**根因**：`ComposeView` 会按自己的 bounds 裁剪绘制。按下时容器整体放大（`1 + 16dp/width`）、
外面还有 10dp 投影，超出 View 的部分被裁掉。

**改法**：给组件上方留出余量，且只留上方（留下方会把胶囊顶上移）。

```kotlin
IosLiquidGlassNavigationBar(modifier = Modifier.padding(top = 14.dp), ...)
```

---

## 6. 胶囊位置比预期高了一截 / 底部留白多了一截

**现象**：把组件放进 `FrameLayout`，宿主已经用 `margin` 让开了系统导航栏，结果胶囊又被顶高一次。

**根因**：组件内部按 `WindowInsets.navigationBars` 自己算底部 padding（原版是 `8dp + inset`，
无 inset 时 `36dp`）。宿主再让一次就是双份。

**改法**：用 `bottomPadding` 覆盖，交给宿主决定（HyperHand 里是 `7.dp`，对齐原 `GlassBar` 的 78dp 视野）。

---

## 7. 横滑页面时指示器卡在两格之间

**现象**：宿主用 `HorizontalPager` 驱动，松手后指示器停在两个 tab 中间。

**根因**：跟手位置（`position`）是连续值，宿主动画结束后如果不再更新它，指示器就停在最后那个小数上。

**改法**：宿主把跟手值收回成 `null`，组件会 `animateTo` 到 `selectedIndex`（内部用
`DampedDragAnimation.snapToValue()` + `animateToValue()`，不经过弹簧过冲）。

---

## 8. 松手瞬间底栏「闪一下」

**现象**：长按或拖动后松手，胶囊边缘的亮度会跳一帧。

**根因**：`InteractiveHighlight` 的按压进度用了欠阻尼弹簧（`spring(0.5f, 300f)`），松手回 0 时会
过冲到负数，而绘制里有 `if (progress > 0f)` 的保护：这一帧整片 6% 白色叠加被直接关掉，下一帧又亮回来。

**改法**：换成临界阻尼 + 钳位；顺手把白色叠加 `clipPath` 到胶囊形状（否则它会从圆角溢出到页面上）。

```kotlin
private val pressProgressAnimationSpec = spring(1f, 400f, 0.001f)
val progress = pressProgressAnimation.value.coerceIn(0f, 1f)
```

---

## 9. 接上以后很卡

**现象**：滚动掉到个位数帧率。

**根因**：自定义 `Backdrop` 的第一版把整屏（1080×2280）按全分辨率重画到软件 Bitmap，每帧一次。

**改法**（三条一起）：

1. 只抓「采样层」那块区域（胶囊 + padding），别抓整屏；
2. 按 `1 / downscaleFactor` 采样——玻璃本来就要模糊，半分辨率肉眼无差，成本降到 1/4；
3. 同一帧内多次采样共用一张位图（容器和指示器各调一次，区域是包含关系，命中缓存直接复用），
   并在下一个渲染帧开始时失效（`Choreographer.postFrameCallback`）。

模拟器（SwiftShader，无硬件 GPU）实测：优化前几乎不可用，优化后滚动 jank 从 88% 降到 85.7%，
和改动前持平——真正的开销在软件 GPU 本身，真机不受影响。

---

## 10. 这些参数能不能调？

**建议别动**。这一套数值是从 SonyPods / miuix 的成品逐行抄下来的，改动任意一项都会立刻「不像 iOS」：

| 位置 | 值 |
| --- | --- |
| 玻璃体 | `vibrancy()` → `blur(4dp, 4dp)` → `lens(24dp, 24dp)` |
| 底色 | `surfaceContainer.copy(alpha = 0.4f)` |
| 边缘高光 | `alpha = 0.75f`，随设备重力旋转 |
| 指示器（静止） | 无折射，只有 10%/3% 黑白蒙层 |
| 指示器（按压） | `lens(10dp × p, 14dp × p, depthEffect, chromaticAberration = 0.5)` |
| 内阴影 | `radius = 8dp × p`，`black @ 0.15` |

一度试过把折射乘 0.4（因为叠在纯色列表页上文字被撕得明显），结论是**错的方向**：真正的问题是第 1、2 条
的坐标系错误，采样没铺满导致「看着发虚」。修好几何之后 1.0 的原参数是正常的。

---

## 11. 为什么不用 `Modifier.blur()` / `RenderEffect`？

`Modifier.blur` 只能模糊**自身子树**，而这个底栏要采样的是**它背后**的页面；`RenderEffect` 的半径在
Android 12 以下不可控，且拿不到 SDF 折射。液态玻璃的折射需要对圆角矩形做 SDF + `circleMap` 位移 +
RGB 分离采样，只能是 AGSL（`RuntimeShader`），并且只在 `Build.VERSION >= 33` 才有。

`lens()` 里已经用 `isRuntimeShaderSupported()` 做了兜底：不支持时退化成「只有模糊 + 蒙层」，不会崩。
