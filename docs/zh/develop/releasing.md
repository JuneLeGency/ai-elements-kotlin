# 发布流程

代码、双语文档和 GitHub Pages 位于同一个仓库，不需要单独的 organization 或网站仓库。
Maven Central 用于分发 Android 库；GitHub Release 用于说明和 Demo APK；Pages 展示文档。

## 一次性配置

在 Central Portal 验证 io.github.junelegency，生成 Publisher User Token。
GitHub 仓库保存 MAVEN_CENTRAL_USERNAME、MAVEN_CENTRAL_PASSWORD、SIGNING_KEY 和需要时的 SIGNING_KEY_PASSWORD。
签名公钥需可从公共 keyserver 获取。密钥不要进入源码或聊天记录。
Pages 选择 GitHub Actions 为来源，并设置 PAGES_ENABLED=true。

## 每次发布

确认 main 上的提交已经验证。同步 VERSION_NAME、CHANGELOG、中英文 README 和安装页的版本与发布状态，再创建 vX.Y.Z tag。
Release 工作流会拒绝版本不一致、未完成发布说明或仍保留 Snapshot 安装提示的候选版本。
完整 CI 通过后才会签名并上传全部 20 个 AAR 和 BOM，随后创建 GitHub Release。

源码构建成功和 publishToMavenLocal 成功不能代替 Central 账号、命名空间、签名及远程发布验证。
详细维护命令见[英文发布指南](/ai-elements-kotlin/develop/releasing/)。
