# 发布

在 `main` 上推送 tag 触发 `.github/workflows/release.yml` 的 Release workflow：

1. 校验稳定版 `vX.Y.Z` tag、CHANGELOG 的准确最终版本标题、匹配的 `VERSION_NAME`、README 和安装页中一致且无未发布提示的 BOM 坐标，以及该提交属于 `main`。
2. 在同一提交上运行可复用 CI：API 检查、单元测试、lint、release/R8、独立 Maven 消费工程、完整文档网站，以及带参考服务的模拟器 E2E。
3. 使用 `publishAndReleaseToMavenCentral` 发布全部签名模块和 BOM，基于 [vanniktech/gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)。
4. 以该版本 CHANGELOG 为说明创建 GitHub release，并附上 Demo APK。

## 每个仓库配置一次

- 在 [Central Portal](https://central.sonatype.com) 验证 `io.github.junelegency` namespace，创建 user token。
- 创建 GPG 签名密钥，将公钥发布到 key server。
- 添加仓库 secrets：`MAVEN_CENTRAL_USERNAME`、`MAVEN_CENTRAL_PASSWORD`（token）、`SIGNING_KEY`（ASCII-armoured 私钥）、`SIGNING_KEY_PASSWORD`。
- 文档网站：GitHub Pages 的 source 设为 GitHub Actions，仓库变量 `PAGES_ENABLED=true`；CI 在每次 push 到 main 后部署 `site/`。

## 每次发布

```bash
# 1. In CHANGELOG.md, rename "## X.Y.Z (unreleased)" to "## X.Y.Z"; set VERSION_NAME=X.Y.Z in gradle.properties.
# 2. Commit, tag and push:
git commit -am "Release X.Y.Z"
git tag vX.Y.Z
git push origin main vX.Y.Z
# 3. Bump VERSION_NAME to the next -SNAPSHOT and open a new "## (unreleased)" section.
```

从首个公开版本起保留稳定公开 API，包括 0.x。修改声明或接受 API diff 前阅读 [兼容性策略](api-compatibility.md)。

打 tag 前运行 `tools/check-published-consumer.sh`、`tools/build-docs.sh`、`./gradlew apiCheck` 及受影响的设备/live test。同步更新安装页 BOM 版本和 snapshot 提示、README、CHANGELOG、roadmap。确认已部署网站及 API symbol 页面可访问。Central namespace 验证、签名凭证、Pages 配置是维护者先决条件，发布到本地不能验证这些配置。

所有必需 job 成功前不进行远程发布。Demo APK 在 Central 发布之前构建，避免打包失败留下半完成的 release。Release 附件 APK 使用 CI debug key 签名，安装前需卸载之前的版本。
