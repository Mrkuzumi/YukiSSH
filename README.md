# YukiSSH

Android SSH 客户端 — 简洁、超轻量的远程终端工具。

<p align="center">
  <img src="screenshots/pic3.jpg" alt="连接列表" width="280">
  <img src="screenshots/pic1.jpg" alt="编辑连接" width="280">
  <img src="screenshots/pic2.jpg" alt="终端界面" width="280">
</p>

## 功能

- 管理多组 SSH 连接配置（增删改查）
- 内置终端模拟器，支持 ANSI 转义序列 / xterm-256color
- UTF-8 及 CJK 字符显示
- 硬件键盘支持（Enter、Tab、Backspace 等）
- 一键复制终端内容到剪贴板
- Material Design 3 风格界面
- 最低支持 Android 8.0 (API 26)

## 技术栈

| 模块 | 选型 |
|------|------|
| 语言 | Kotlin |
| SSH 库 | [JSch (mwiede fork)](https://github.com/mwiede/jsch) |
| 异步 | Kotlin Coroutines |
| UI | Jetpack AppCompat + Material Components + RecyclerView |
| 终端渲染 | 自研 `TerminalView`，基于 Android Canvas |

## 构建

```bash
# 克隆项目
git clone https://github.com/Mrkuzumi/YukiSSH.git

# 用 Android Studio 打开项目根目录，Sync Gradle，然后 Run
```

> 需要 Android SDK 34 和 JDK 17。

## 使用

1. 点击右下角 **+** 添加 SSH 连接
2. 填写主机地址、端口（默认 22）、用户名、密码
3. 点击已保存的连接卡片进入终端
4. 点击终端界面可唤出键盘输入，点击复制按钮可将终端内容复制到剪贴板


## 免责声明

### AI 生成声明

本项目代码 **100% 由 AI 生成**（Claude Opus 4），包括但不限于所有 Kotlin 源码、XML 布局、Gradle 构建脚本及本 README。项目处于早期开发阶段，将持续迭代。

### 安全警告

**本应用不适合处理敏感环境或生产服务器。**

- SSH 连接密码以 **明文** 形式存储在 `SharedPreferences` 中，任何拥有 root 权限或能够访问应用私有目录的攻击者均可读取
- 主机密钥校验已 **关闭**（`StrictHostKeyChecking: no`），无法防御中间人攻击（MITM）
- 未实现密钥认证（仅支持密码登录）
- 未实现会话日志脱敏

如果你需要在敏感环境中使用 SSH 客户端，建议使用 [Termux](https://termux.dev/)、[JuiceSSH](https://juicessh.com/) 等经过安全审计的成熟方案。

### 隐私声明

本应用 **不收集、不上传、不分享** 任何用户数据。所有连接配置仅存储在设备本地。但请注意：由于密码为明文存储，建议仅在已加密的设备存储的受信任设备上使用。

### 免责条款

本软件按"原样"提供，不提供任何形式的明示或暗示担保。在任何情况下，作者均不对因使用本软件而产生的任何损害承担责任，包括但不限于数据丢失、服务器入侵、系统故障或业务中断。

## 开发计划

- [ ] 密钥认证（SSH Key / Ed25519 / RSA）
- [ ] 密码加密存储（Android Keystore）
- [ ] 主机密钥指纹校验（known_hosts）
- [ ] SFTP 文件管理
- [ ] 终端字体 / 配色主题切换
- [ ] Socks5 代理转发
- [ ] 跳板机支持
- [ ] Widget 小组件

## 贡献

本项目为个人学习项目，暂不接受外部 PR。欢迎提交 Issue 反馈问题或建议。

## 许可证

MIT License — 详见 [LICENSE](LICENSE)。
