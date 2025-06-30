# 项目设置指南

## 前置要求
- Java 8 或更高版本
- Maven 3.6 或更高版本
- MySQL 数据库

## 快速开始

### 1. 克隆项目
```bash
git clone https://github.com/your-username/bupt-java-homework-main.git
cd bupt-java-homework-main
```

### 2. 安装依赖
使用Maven自动下载所有依赖：
```bash
mvn clean compile
```

如果你需要将依赖复制到 `libs` 目录（用于兼容现有的启动脚本）：
```bash
mvn package
```

### 3. 编译项目
```bash
# 使用Maven编译
mvn compile

# 或者使用传统方式编译到bin目录
javac -cp "libs/*" -d bin source/com/example/chatserver/*.java
```

### 4. 运行项目
```bash
# 使用现有的启动脚本
./start_chatserver.bat

# 或者直接运行
java -cp "libs/*;source" com.example.chatserver.ChatServer
```

## 依赖说明

项目现在使用Maven管理以下依赖：
- **Gson 2.11.0** - JSON处理
- **MySQL Connector 9.3.0** - 数据库连接
- **腾讯云COS SDK 5.6.247** - 文件上传
- **Jackson 2.15.2** - JSON处理
- **Apache HttpClient 4.5.13** - HTTP客户端
- **SLF4J 2.0.7** - 日志框架

## 开发说明

### 为什么不上传libs目录到GitHub？
1. **仓库大小**: JAR文件会让Git仓库变得很大（7MB+）
2. **版本管理**: Maven可以自动管理依赖版本
3. **最佳实践**: 现代Java项目都使用构建工具管理依赖
4. **协作便利**: 其他开发者只需运行 `mvn compile` 即可获取所有依赖

### 添加新依赖
在 `pom.xml` 文件的 `<dependencies>` 部分添加新的依赖，然后运行：
```bash
mvn clean compile
```

### 故障排除
如果遇到依赖问题，可以清理并重新下载：
```bash
mvn clean
mvn dependency:resolve
mvn compile
```

## 数据库配置
确保修改 `DBConnection.java` 中的数据库连接信息：
```java
private static final String URL = "jdbc:mysql://your-host:3306/chatserver";
private static final String USER = "your-username";
private static final String PASSWORD = "your-password";
``` 