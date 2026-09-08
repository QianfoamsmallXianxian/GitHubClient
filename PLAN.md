# GitHub Android 客户端方案（Kotlin + Jetpack Compose）

## 1. 项目目标
构建一个功能接近 GitHub 网页版的 Android 原生客户端，支持：
- GitHub 账户登录：OAuth 网页授权 + 个人访问令牌 PAT 两种方式
- 仓库浏览、代码浏览、提交历史
- Issues / Pull Requests / Releases / Discussions
- GitHub Actions：查看工作流、手动触发、实时日志
- 内置工具链管理器：一键检测、下载、管理常见构建工具

工具链管理器按“场景 C”实现：APP 内置管理器，检测 JDK、Gradle、Android SDK、NDK、CMake、Node.js、Python、Go 等工具，缺失时自动下载并统一管理，不承诺首版在手机本地完整执行 GitHub Actions。

---

## 2. 技术栈
| 层 | 选型 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository + UseCase |
| 语言 | Kotlin 100% |
| 网络 | Retrofit + OkHttp + kotlinx.serialization |
| 数据库 | Room（本地缓存、工具链状态、账号信息） |
| 状态 | StateFlow + ViewModel |
| 导航 | Navigation Compose |
| 依赖注入 | Hilt |
| 图片 | Coil |
| OAuth | Custom Tabs + PKCE |
| 日志 | Timber |
| 下载 | OkHttp 断点下载 / DownloadManager |
| 文件解压 | java.util.zip / commons-compress |
| 终端命令 | 受限 shell（需用户授权） |

---

## 3. 模块划分
建议采用单仓库多模块结构（首版可先单模块，预留拆分）：

app
├── core
│   ├── network（Retrofit、OkHttp、GitHub API）
│   ├── database（Room）
│   ├── model（数据模型）
│   ├── auth（OAuth、PAT 管理）
│   ├── ui（通用 Compose 组件）
│   └── common（工具、扩展）
├── feature
│   ├── login
│   ├── home
│   ├── repo
│   ├── code
│   ├── issues
│   ├── pulls
│   ├── releases
│   ├── actions
│   └── toolchain

---

## 4. 核心功能清单

### 4.1 登录与授权
- OAuth Web 登录：Custom Tabs 打开 GitHub 授权页，PKCE 防 CSRF，回调获取 access token
- PAT 登录：用户粘贴 fine-grained personal access token，APP 用 `Authorization: Bearer <PAT>` 调 API
- 账号信息展示：头像、用户名、公开仓库数
- 多账号切换（首版可只支持单账号）
- Token 加密存储：Android Keystore + EncryptedSharedPreferences / DataStore

### 4.2 首页
- 当前用户的仓库列表
- Star 列表、关注列表
- 动态流（可选，后续版本）
- 搜索仓库、代码、用户

### 4.3 仓库详情
- 仓库信息：Star、Fork、Watch、Issue 数、语言、License
- 分支/标签列表
- 代码树浏览
- 提交历史
- README 渲染（Markdown）
- Star / Fork / Watch 操作

### 4.4 代码浏览
- 目录树递归浏览
- 文件内容查看，语法高亮
- 原始文件下载
- 文件历史

### 4.5 Issues / PR
- Issues 列表（open/closed/all）、筛选、标签、里程碑
- Issue 详情：评论、参与者、标签
- 创建 Issue、评论
- PR 列表与详情
- PR 的 diff 查看
- Merge 状态展示

### 4.6 Releases
- Release 列表
- 附件下载
- 发布说明 Markdown 渲染

### 4.7 GitHub Actions
- 仓库 workflows 列表
- workflow runs 列表
- 手动触发 workflow_dispatch（支持输入参数）
- 实时日志流（分段拉取）
- run 状态、耗时、触发人、commit 信息
- 取消 run、重跑 run
- 日志导出/复制

### 4.8 工具链管理器（场景 C）
- 检测项：
  - JDK（Temurin）
  - Gradle
  - Android SDK（platform-tools、build-tools、platforms）
  - NDK
  - CMake
  - Node.js
  - Python
  - Go
- 检测逻辑：检查 `PATH`、常见安装目录、`which` 命令、版本号
- 下载逻辑：
  - 从官方源或镜像下载压缩包
  - 校验 SHA-256
  - 解压到 `/sdcard/Download/GitHubClient/toolchain/<tool>/`
  - 记录版本、路径、状态到 Room
- 管理：查看已安装版本、更新、卸载、手动添加路径
- 一键修复：检测缺失项后批量提示下载
- 网络要求：支持断点续传、下载进度显示

---

## 5. 系统架构

### 5.1 分层
Compose UI → ViewModel → UseCase → Repository → DataSource
- DataSource：RemoteDataSource（GitHub API）、LocalDataSource（Room）、ToolchainDataSource（文件系统 + shell）
- Repository 统一调度，决定网络/缓存策略

### 5.2 网络层
- Retrofit + OkHttp
- 拦截器：自动附加 token、处理 401/403、分页 Link 头解析、限流处理
- API 版本：GitHub REST API v3 + GraphQL API v4（搜索、部分聚合查询）
- SSE/流式：Actions 日志通过分段轮询获取

### 5.3 数据层
Room 表：
- accounts（账号、token 引用、头像、用户名）
- repo_cache（仓库列表缓存）
- workflow_cache（workflows、runs）
- toolchain_items（工具名、版本、路径、状态、下载 URL、checksum）
- download_tasks（下载任务、进度、状态）

### 5.4 安全
- Token 存 Android Keystore 加密，Room 只存引用
- 网络仅 HTTPS
- 下载前校验 checksum
- 不记录明文 token 到日志

---

## 6. 主要页面路由
- /login
- /home
- /search
- /repo/{owner}/{name}
- /repo/{owner}/{name}/tree/{branch}/{path}
- /repo/{owner}/{name}/commits
- /repo/{owner}/{name}/issues
- /repo/{owner}/{name}/issues/{number}
- /repo/{owner}/{name}/pulls
- /repo/{owner}/{name}/pulls/{number}
- /repo/{owner}/{name}/actions
- /repo/{owner}/{name}/actions/runs/{run_id}
- /toolchain
- /settings

---

## 7. 构建环境与 APK
- minSdk 26（Android 8.0），targetSdk 34+
- 包名建议：com.githubclient.app
- 签名：debug 自动；release 需生成 keystore
- ProGuard/R8 配置：保留 Retrofit、kotlinx.serialization、OkHttp 相关
- 版本目录：libs.versions.toml

---

## 8. 里程碑建议
M1：项目骨架 + 网络层 + 登录（OAuth/PAT）+ 首页仓库列表
M2：代码浏览 + Issues/PR 基础浏览
M3：Actions 查看/触发/日志
M4：工具链管理器（检测 + 下载 + 管理）
M5：搜索、Releases、多账号、体验优化

---

## 9. 风险与限制
- 手机本地跑完整 Actions 不现实，首版不做场景 A
- GitHub 未认证 API 限流 60 次/小时，登录后 5000 次/小时
- 下载大型工具（Android SDK/NDK）需要较大存储空间
- Android 沙箱限制 shell 能力，工具检测需用户授权或使用可用路径
- Markdown 渲染需引入专门库，复杂 HTML 需 WebView 兜底

---

## 10. 下一步
确认方案后，按以下顺序创建项目文件：
1. Gradle 项目骨架 + 版本目录
2. core 模块基础类
3. 登录模块
4. 网络/API
5. UI 页面
