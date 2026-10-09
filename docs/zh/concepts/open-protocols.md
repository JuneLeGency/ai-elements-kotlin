# 公开协议原则

跨进程的数据遵循公开、版本化协议。内部 Message、Part、ChatEvent 是统一映射，不能直接当作自造网络格式。

扩展优先放在协议已有位置：AI SDK data-* 与 metadata、AG-UI state/activity、MCP _meta、A2A DataPart 和 metadata、ACP _meta。
协议实现不需要改变 UI；额外展示语义应成为模型字段，在上游映射，而不是让 UI 猜测工具名字。

优先使用官方 SDK 和参考实现。协议变更需要真实实现录制的 fixture，并在可用时执行本地或公开端点的 live 测试。
具体规范与兼容版本见[协议支持](../protocols/index.md)。
