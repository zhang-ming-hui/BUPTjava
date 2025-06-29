@echo off
cd /d %~dp0
cd bupt-java-homework-main
REM 启动 Spring Boot 文件/图片上传服务
start "" cmd /k "cd /d %cd% && mvn spring-boot:run"
REM 启动 Java 聊天服务端
javac -encoding UTF-8 -cp libs/* -d source source/com/example/chatserver/*.java
java -cp "libs/*;source" com.example.chatserver.ChatServer
REM 启动 Python 聊天服务端
python server.py
pause 