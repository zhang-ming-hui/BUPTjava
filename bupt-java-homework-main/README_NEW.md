# 网络聊天室项目

## 项目概述

这是一个基于Java Socket + 多线程的网络聊天室系统，支持WebSocket协议，具有完整的群聊、私聊、文件传输等功能。

## 技术栈

- **后端**: Java Socket + 多线程
- **前端**: HTML + CSS + JavaScript (WebSocket)
- **数据存储**: 文件系统 (JSON格式)
- **协议**: WebSocket

## 功能特性

### 1. 用户管理
- ✅ 用户注册和登录
- ✅ 用户漫游功能（同名用户只能在一台计算机上登录）
- ✅ 用户在线状态管理
- ✅ 用户列表动态更新

### 2. 群聊功能
- ✅ 创建群组
- ✅ 加入/离开群组
- ✅ 群组消息实时聊天
- ✅ 群组列表显示

### 3. 私聊功能
- ✅ 用户间点对点私聊
- ✅ 私聊消息历史记录

4. 小组聊天功能
✅ 群内创建小组
✅ 邀请群成员加入小组（仅未加入其他小组的成员会收到邀请，已在其他小组的成员不会收到邀请，且创建者会收到提示）
✅ 群成员可被多个小组邀请，但只能加入一个小组
✅ 小组内独立聊天，群中未加入小组成员不能收到小组消息
✅ 支持群内多个小组并发聊天
✅ 用户不能同时加入两个小组

### 5. 消息管理
- ✅ 消息自动保存到文件
- ✅ 重新登录可查看历史消息
- ✅ 未读消息计数（用星星显示）
- ✅ 消息已读状态管理

### 6. 文件传输功能
- ✅ 群组文件共享
- ✅ 私聊文件传输
- ✅ 图片发送和显示
- ✅ 文件下载功能

### 7. 聊天窗口管理
- ✅ 无限制打开聊天窗口
- ✅ 多窗口并发聊天

## 项目结构

```
com.example.chatserver/
├── ChatServer.java         # 主服务器类
├── User.java               # 用户类
├── Message.java            # 消息类
├── Group.java              # 群组类
├── SubGroup.java           # 小组类
├── UserManager.java        # 用户管理器
├── MessageManager.java     # 消息管理器
├── GroupManager.java       # 群组管理器
├── FileManager.java        # 文件管理器
├── COSFileUploader.java    # 云存储
└── DBConnection.java       # 数据库连接
```

## 安装和运行

### 1. 环境要求
- Java 8 或更高版本
- 现代浏览器（支持WebSocket）

### 2. 编译和运行

#### Windows:
```bash
# 双击运行编译脚本
compile.bat
```

#### Linux/Mac:
```bash

javac -cp ".;libs/gson-2.11.0.jar;libs/cos_api-5.6.247.jar;libs/mysql-connector-j-9.3.0.jar" source/com/example/chatserver/*.java
java -cp ".;source;libs/gson-2.11.0.jar;libs/cos_api-5.6.247.jar;libs/mysql-connector-j-9.3.0.jar" com.example.chatserver.ChatServer
```

### 3. 访问客户端
在浏览器中打开 `index.html` 文件

## 使用说明

### 1. 登录
- 输入用户名和密码
- 系统会自动创建新用户（如果用户不存在）

### 2. 群聊
- 点击"创建群组"按钮创建新群组
- 点击群组列表中的群组加入
- 在群组聊天窗口中发送消息

### 3. 私聊
- 点击用户列表中的用户名开始私聊
- 支持多窗口私聊

### 4. 小组聊天
- 在群组中点击"创建小组"按钮
- 选择要邀请的群成员
- 小组内独立聊天

### 5. 文件传输
- 点击"发送文件"按钮选择文件
- 支持图片和普通文件
- 文件会自动保存到服务器

### 6. 消息历史
- 重新登录后自动加载历史消息
- 未读消息会用星星标记
- 点击聊天窗口自动标记为已读

## 数据存储

### 文件结构
```
项目根目录/
├── users.txt              # 用户数据
├── messages/              # 消息存储目录
│   ├── private_*.txt      # 私聊消息
│   ├── group_*.txt        # 群聊消息
│   └── subgroup_*.txt     # 小组消息
└── files/                 # 文件存储目录
    └── *.文件             # 上传的文件
```

## 协议说明

### WebSocket消息格式
所有消息使用 `|` 分隔符分隔字段：

1. **登录**: `LOGIN|用户名|密码`
2. **群聊消息**: `GROUP_MESSAGE|群组名|消息内容`
3. **私聊消息**: `PRIVATE_MESSAGE|目标用户|消息内容`
4. **文件上传**: `UPLOAD_FILE|聊天ID|聊天类型|文件名|文件数据`

## 特色功能

### 1. 用户漫游
- 同名用户在其他地方登录时，自动踢出之前的连接
- 确保用户唯一性

### 2. 小组聊天
- 群内可以创建多个小组
- 小组聊天独立于群聊
- 用户不能同时加入两个小组

### 3. 消息持久化
- 所有消息自动保存到文件
- 支持离线消息查看
- 未读消息提醒

### 4. 文件管理
- 支持各种文件类型
- 图片自动预览
- 文件大小显示

## 开发说明

### 面向对象设计
项目包含8个主要类，每个类都有明确的职责：

1. **ChatServer**: 主服务器，处理WebSocket连接
2. **User**: 用户实体类
3. **Message**: 消息实体类
4. **Group**: 群组实体类
5. **SubGroup**: 小组实体类
6. **UserManager**: 用户管理
7. **MessageManager**: 消息管理
8. **GroupManager**: 群组管理
9. **FileManager**: 文件管理

### 并发处理
- 使用ConcurrentHashMap保证线程安全
- 每个客户端连接使用独立线程处理
- 支持多用户同时在线

## 故障排除

### 常见问题

1. **编译错误**: 确保Java版本正确，类路径设置正确
2. **连接失败**: 检查端口8000是否被占用
3. **文件上传失败**: 检查files目录权限
4. **消息不显示**: 检查messages目录权限

### 日志文件
服务器运行时会输出详细日志，包括：
- 用户连接/断开
- 消息发送/接收
- 错误信息

## 扩展功能

### 可能的改进
1. 数据库存储替代文件存储
2. 消息加密
3. 语音/视频聊天
4. 表情包支持
5. 消息撤回功能
6. 群组权限管理

## 许可证

本项目仅供学习和研究使用。

## Windows 下编译与运行（PowerShell）

1. 编译所有 Java 源文件：
```powershell
Get-ChildItem -Recurse .\bupt-java-homework-main\bupt-java-homework-main\source\com\example\chatserver\*.java | ForEach-Object { $_.FullName } > sources.txt
javac -encoding UTF-8 -cp .\bupt-java-homework-main\bupt-java-homework-main\libs\* -d .\bupt-java-homework-main\bupt-java-homework-main\bin @sources.txt
```

2. 运行服务端（确保所有依赖 jar 都在 classpath，解决 slf4j 报错）：
```powershell
java -cp .\bupt-java-homework-main\bupt-java-homework-main\bin;\
.\bupt-java-homework-main\bupt-java-homework-main\libs\slf4j-api-2.0.7.jar;\
.\bupt-java-homework-main\bupt-java-homework-main\libs\slf4j-simple-2.0.7.jar;\
.\bupt-java-homework-main\bupt-java-homework-main\libs\cos_api-5.6.247.jar;\
.\bupt-java-homework-main\bupt-java-homework-main\libs\gson-2.11.0.jar;\
.\bupt-java-homework-main\bupt-java-homework-main\libs\commons-codec-1.18.0.jar;\
.\bupt-java-homework-main\bupt-java-homework-main\libs\commons-logging-1.2.jar;\
.\bupt-java-homework-main\bupt-java-homework-main\libs\httpclient-4.5.13.jar;\
.\bupt-java-homework-main\bupt-java-homework-main\libs\httpcore-4.4.16.jar;\
.\bupt-java-homework-main\bupt-java-homework-main\libs\mysql-connector-j-9.3.0.jar \
com.example.chatserver.ChatServer
```

> 注意：如遇 slf4j 相关报错，务必用上述命令手动列出所有 jar。 