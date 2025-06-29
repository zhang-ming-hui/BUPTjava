#!/usr/bin/env python3
"""
简单的HTTP静态文件服务器
用于托管聊天室的前端页面，避免刷新时回到登录页面
"""

import http.server
import socketserver
import os
import sys

# 设置服务器参数
PORT = 3001
DIRECTORY = "."

class CustomHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def end_headers(self):
        # 添加CORS头部，避免跨域问题
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', '*')
        super().end_headers()

    def log_message(self, format, *args):
        # 简化日志输出
        print(f"[{self.address_string()}] {format % args}")

def main():
    print(f"启动HTTP服务器...")
    print(f"端口: {PORT}")
    print(f"目录: {os.path.abspath(DIRECTORY)}")
    print(f"访问地址: http://localhost:{PORT}/index.html")
    print("按 Ctrl+C 停止服务器")
    
    try:
        with socketserver.TCPServer(("", PORT), CustomHTTPRequestHandler) as httpd:
            httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n服务器已停止")
    except OSError as e:
        if e.errno == 10048:  # Windows下端口被占用的错误码
            print(f"错误: 端口 {PORT} 已被占用，请更换端口或关闭占用该端口的程序")
        else:
            print(f"启动服务器时出错: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main() 