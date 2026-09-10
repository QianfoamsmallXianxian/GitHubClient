package com.githubclient.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局尺寸规范。
 *
 * 目的：统一各页面的间距 / 圆角 / 图标尺寸，消除「有的用 Spacer 手摆、
 * 有的用 spacedBy」两套写法混用导致的视觉不齐。
 *
 * 用法：
 *  - 列表 / 表单条目间距：Column(verticalArrangement = Arrangement.spacedBy(Dimens.Sm))
 *  - 页面四周留白：Modifier.padding(Dimens.ScreenPadding)
 *  - 卡片内部留白：Modifier.padding(Dimens.CardPadding)
 *  - 按钮 / 卡片圆角：Dimens.Corner
 */
object Dimens {
    /** 4dp：图标与文字之间的紧贴间距 */
    val Xs = 4.dp

    /** 8dp：条目之间、按钮之间的标准间距 */
    val Sm = 8.dp

    /** 12dp：稍宽的分组间距 */
    val Md = 12.dp

    /** 16dp：页面留白、卡片内边距 */
    val Lg = 16.dp

    /** 24dp：大区块之间 */
    val Xl = 24.dp

    /** 页面四周统一留白 */
    val ScreenPadding = 16.dp

    /** 卡片内部统一留白 */
    val CardPadding = 16.dp

    /** 列表内容统一留白 */
    val ListPadding = 12.dp

    /** 小图标（列表行内） */
    val IconSm = 20.dp

    /** 标准图标 */
    val IconMd = 24.dp

    /** 头像 / 大图标 */
    val IconLg = 40.dp

    /** 头像（账号卡片） */
    val AvatarMd = 44.dp

    /** 头像（对话框） */
    val AvatarSm = 36.dp

    /** 按钮 / 输入框高度 */
    val FieldHeight = 52.dp

    /** 统一圆角 */
    val Corner = 12.dp

    /** 小圆角 */
    val CornerSm = 8.dp

    /** 卡片阴影 */
    val CardElevation = 1.dp

    /** 分割线粗细 */
    val Divider = 1.dp
}
