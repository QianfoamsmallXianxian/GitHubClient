# GitHubClient

功能接近 GitHub 网页版的 Android 原生客户端。

## 技术栈
Kotlin + Jetpack Compose + Hilt + Retrofit + Room + kotlinx.serialization

## 构建要求
- Android Studio Ladybug 或更高
- JDK 17
- Android SDK 34

## 构建步骤
1. 用 Android Studio 打开项目目录
2. 等待 Gradle Sync 完成
3. 运行 app 模块到设备或模拟器

## 当前状态
- 项目骨架可编译
- 登录 UI 已就位（OAuth/PAT 待接后端）
- GitHub API 网络层已定义
- Room 数据库与 Token 加密存储已就位
- 工具链管理器基础（检测/下载框架）已就位
- 仓库/Issues/PR/Actions 页面待补充

## 目录结构
见 PLAN.md
