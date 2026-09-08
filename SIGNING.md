# 正式发布签名配置指南

当前项目在未配置正式 keystore 时，release APK 会使用 debug 签名。
如果要上架应用商店或正式发布，请按以下步骤配置。

## 第一步：在电脑上生成 keystore

需要安装 JDK，然后执行：
```bash
keytool -genkeypair -v -keystore release.keystore -alias githubclient -keyalg RSA -keysize 2048 -validity 10000
```
按提示设置 keystore 密码和 key 密码。

## 第二步：把 keystore 转成 Base64 字符串

Linux/macOS：
```bash
base64 < release.keystore
```

Windows PowerShell：
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))
```
复制输出的 Base64 字符串。

## 第三步：在 GitHub 仓库配置 Secrets

打开 GitHub 仓库 → Settings → Secrets and variables → Actions → New repository secret，添加四个 secret：

| Secret 名称 | 值 |
|---|---|
| RELEASE_KEYSTORE_BASE64 | 上一步复制的 Base64 字符串 |
| RELEASE_KEYSTORE_PASSWORD | keystore 密码 |
| RELEASE_KEY_ALIAS | 别名（如 githubclient） |
| RELEASE_KEY_PASSWORD | key 密码 |

## 第四步：重新运行 Actions

保存 Secrets 后，重新运行 workflow（或重新 push），
Actions 会自动解码 keystore 并使用正式签名构建 release APK。

## 说明
- 未配置 Secrets 时 release APK 仍能构建，但使用 debug 签名
- keystore 和密码千万不要提交到仓库
- 正式发布前请在 OAuthManager.kt 中配置真实 OAuth Client ID/Secret
