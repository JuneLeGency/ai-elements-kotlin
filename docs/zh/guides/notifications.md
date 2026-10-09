# 运行进度通知

可选 ai-elements-notifications 把运行进度带到通知栏，并在回复完成时通知用户。
Android 16 可使用 Live Update；较旧系统使用受平台能力约束的通知表现。

引入该模块后仍要由 App 按系统要求申请通知权限、管理通知渠道和用户是否开启通知的偏好。
它独立分包，因此只使用聊天 UI 的应用不会被强加这些权限。

具体 public API 与参数见 [notifications API](/ai-elements-kotlin/api/ai-elements-notifications/index.html)。
后台长期任务还涉及应用自己的生命周期设计；展示通知本身不会把进程变成永久后台服务。
