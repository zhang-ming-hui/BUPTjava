@echo off
echo ========================================
echo     聊天室系统 - 多用户测试模式
echo ========================================
echo.

echo 1. 启动Java后端服务器（多用户登录模式）...
start "ChatServer-TestMode" cmd /k "echo 多用户测试模式已启用 && java -cp .;libs/* com.example.chatserver.ChatServer"

echo 2. 等待后端服务器启动...
timeout /t 3 /nobreak > nul

echo 3. 启动前端HTTP服务器...
start "HTTPServer" cmd /k "python server.py"

echo.
echo ========================================
echo 系统启动完成！测试模式已启用
echo ========================================
echo.
echo 📝 服务器信息：
echo - Java后端服务器: ws://localhost:8000
echo - 前端HTTP服务器: http://localhost:3001
echo.
echo 🔧 测试模式特性：
echo - 允许同一用户在多个浏览器标签页登录
echo - 支持同时测试多个用户账号
echo - 适合功能测试和演示
echo.
echo 🌐 访问地址: http://localhost:3001/index.html
echo.
echo 💡 测试建议：
echo - 打开多个浏览器标签页
echo - 使用不同用户名登录测试群聊功能
echo - 或在隐私/无痕模式下测试
echo.
pause 