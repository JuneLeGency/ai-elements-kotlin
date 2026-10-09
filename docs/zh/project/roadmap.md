# 项目进度

当前库支持纯客户端和端侧 Agent 两种方式。聊天模型、协议实现、Compose UI、生成式界面和 harness 能力按模块分层。

首发准备已加入官方 API 基线、独立 Maven 消费工程、组件示例编译、站点检查和模拟器发布门禁。
已有本地验收记录不等同于 Maven Central 或网站已经发布；远程状态以实际 Release 和 Pages 结果为准。

仓库已经公开，中英文网站与真实组件截图已部署到 GitHub Pages。远程 CI 已通过 API、单元测试、lint、R8、独立 Maven 消费工程和模拟器 E2E。
0.3.0 已正式发布到 Maven Central 和 GitHub Release，包含 20 个库、BOM 及 Demo APK。公开 POM 签名与发布密钥匹配。
Tag 门禁报告 75 项通过、10 项按设计退出；系统文件选择器用例首次偶发失败，同一提交的主线、本地单用例和完整重跑通过。
中文源码页面的截图路径已兼容 GitHub Markdown，README 的文档入口统一指向线上站点。
完整历史与逐工作流验证证据见[维护路线图（英文）](/ai-elements-kotlin/project/roadmap/)。
