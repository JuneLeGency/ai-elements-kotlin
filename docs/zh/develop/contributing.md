# 参与贡献

先阅读仓库 AGENTS.md、GOAL.md 和 CONTRIBUTING.md。UI 只依赖聊天模型，协议和模型 API 不得反向耦合到 Compose。

- 公开接口变更更新 CHANGELOG、文档和经过审核的 API 基线。
- 协议变更附官方实现录制的 fixture，能运行 live 测试时同时验证。
- UI 使用主题颜色、间距、触控尺寸和无障碍语义；库内字符串提供所有已支持语言。
- 中英文组件说明在 tools/component-usage*.json 维护，示例共用 DocsSamples.kt，不手工修改生成页。
- 不提交密钥、token、机器专用配置或用户数据。

提交前执行[验证命令](testing.md)。安全问题按[安全政策](../project/security.md)私下报告。
