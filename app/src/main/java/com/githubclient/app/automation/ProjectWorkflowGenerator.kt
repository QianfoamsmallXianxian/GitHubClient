package com.githubclient.app.automation

/**
 * 根据上传的源码文件清单，自动判断项目类型并生成对应的 GitHub Actions workflow。
 *
 * 判定顺序（先命中先返回）：
 *   Android -> Maven -> CMake -> Rust -> Go -> Node -> Python
 *
 * 识别不出来就返回 null，绝不硬塞一个跑不通的 workflow。
 */
object ProjectWorkflowGenerator {

    /** 生成文件固定写到这里 */
    const val WORKFLOW_PATH = ".github/workflows/build.yml"

    private const val WORKFLOW_DIR = ".github/workflows/"

    fun generate(files: Map<String, String>): String? {
        val paths = files.keys.map { it.replace('\\', '/') }

        return when {
            isAndroid(paths) -> ANDROID
            has(paths, "pom.xml") -> MAVEN
            has(paths, "CMakeLists.txt") -> CMAKE
            has(paths, "Cargo.toml") -> RUST
            has(paths, "go.mod") -> GO
            has(paths, "package.json") -> NODE
            paths.any { it.endsWith("requirements.txt") || it.endsWith("pyproject.toml") } -> PYTHON
            else -> null
        }
    }

    /**
     * 把 files 里 .github/workflows/ 下的 yml/yaml 全部替换成本次生成的。
     * 返回新的 map；若识别不出项目类型则原样返回。
     */
    fun applyTo(files: Map<String, String>): Map<String, String> {
        val generated = generate(files) ?: return files
        val kept = files.filterKeys { key ->
            val k = key.replace('\\', '/')
            !(k.startsWith(WORKFLOW_DIR) && (k.endsWith(".yml") || k.endsWith(".yaml")))
        }
        return kept + (WORKFLOW_PATH to generated)
    }

    private fun has(paths: List<String>, name: String): Boolean =
        paths.any { it == name || it.endsWith("/$name") }

    private fun isAndroid(paths: List<String>): Boolean {
        val hasGradle = paths.any {
            it.endsWith("settings.gradle.kts") || it.endsWith("settings.gradle") ||
                it.endsWith("build.gradle.kts") || it.endsWith("build.gradle")
        }
        val hasManifest = paths.any { it.endsWith("AndroidManifest.xml") }
        // 两个都命中才算 Android，避免把普通 JVM Gradle 项目误判成 Android
        return hasGradle && hasManifest
    }

    // ==================== 模板 ====================

    private val ANDROID = """
name: Build Android

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build debug APK
        run: gradle assembleDebug --no-daemon

      - name: Build release APK
        run: gradle assembleRelease --no-daemon
        continue-on-error: true

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: '**/build/outputs/apk/debug/*.apk'
          if-no-files-found: warn

      - name: Upload release APK
        uses: actions/upload-artifact@v4
        with:
          name: app-release-apk
          path: '**/build/outputs/apk/release/*.apk'
          if-no-files-found: ignore
""".trimIndent()

    private val MAVEN = """
name: Build Maven

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Build
        run: mvn -B package --file pom.xml -DskipTests

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: maven-target
          path: target/*.jar
          if-no-files-found: warn
""".trimIndent()

    private val CMAKE = """
name: Build CMake

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Install build deps
        run: |
          sudo apt-get update
          sudo apt-get install -y build-essential cmake ninja-build

      - name: Configure
        run: cmake -S . -B build -DCMAKE_BUILD_TYPE=Release

      - name: Build
        run: cmake --build build --config Release -j

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: cmake-build
          path: build/
          if-no-files-found: warn
""".trimIndent()

    private val RUST = """
name: Build Rust

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Install Rust
        uses: dtolnay/rust-toolchain@stable

      - name: Build
        run: cargo build --release --verbose

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: rust-target
          path: target/release/
          if-no-files-found: warn
""".trimIndent()

    private val GO = """
name: Build Go

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Go
        uses: actions/setup-go@v5
        with:
          go-version: 'stable'

      - name: Build
        run: go build ./...

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: go-build
          path: |
            ./*.exe
            ./*.bin
            ./bin/
          if-no-files-found: ignore
""".trimIndent()

    private val NODE = """
name: Build Node

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install dependencies
        run: |
          if [ -f package-lock.json ]; then npm ci; else npm install; fi

      - name: Build
        run: npm run build --if-present

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: node-dist
          path: |
            dist/
            build/
          if-no-files-found: ignore
""".trimIndent()

    private val PYTHON = """
name: Build Python

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.12'

      - name: Install dependencies
        run: |
          python -m pip install --upgrade pip
          if [ -f requirements.txt ]; then pip install -r requirements.txt; fi
          if [ -f pyproject.toml ]; then pip install build; fi

      - name: Build
        run: |
          if [ -f pyproject.toml ]; then python -m build; fi

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: python-dist
          path: dist/
          if-no-files-found: ignore
""".trimIndent()
}
