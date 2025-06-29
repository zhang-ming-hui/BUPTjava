// 全局变量
let ws;
let currentUser = '';
let activeChats = new Map();
let currentChat = null;
let userList = [];
let groupList = [];
let subgroupList = [];
let unreadCounts = new Map();
// 聊天消息缓存，key: target, value: 消息数组
let chatMessages = new Map();
let pendingUpload = null; // {type: 'image'|'file', file: File}

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    console.log('聊天室初始化完成');
    setupEventListeners();
    
    // 检查测试模式状态
    const testModeCheckbox = document.getElementById('testMode');
    if (testModeCheckbox) {
        testModeCheckbox.checked = isTestMode();
    }
    
    // 检查是否有保存的登录状态
    checkSavedLogin();
});

// 设置事件监听器
function setupEventListeners() {
    // 登录表单事件
    document.getElementById('username').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') login();
    });
    document.getElementById('password').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') login();
    });

    // 标签页切换
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            switchTab(btn.dataset.tab);
        });
    });

    // 文件拖拽上传
    const fileUploadArea = document.querySelector('.file-upload-area');
    if (fileUploadArea) {
        fileUploadArea.addEventListener('dragover', (e) => {
            e.preventDefault();
            fileUploadArea.style.borderColor = '#667eea';
            fileUploadArea.style.backgroundColor = '#f8f9ff';
        });

        fileUploadArea.addEventListener('dragleave', (e) => {
            e.preventDefault();
            fileUploadArea.style.borderColor = '#e1e5e9';
            fileUploadArea.style.backgroundColor = 'transparent';
        });

        fileUploadArea.addEventListener('drop', (e) => {
            e.preventDefault();
            fileUploadArea.style.borderColor = '#e1e5e9';
            fileUploadArea.style.backgroundColor = 'transparent';
            handleFileDrop(e.dataTransfer.files);
        });
    }
}

// 登录功能
function login() {
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const loginBtn = document.getElementById('loginBtn');
    const loginStatus = document.getElementById('loginStatus');

    if (!username || !password) {
        showNotification('请输入用户名和密码', 'error');
        return;
    }

    // 更新登录按钮状态
    loginBtn.disabled = true;
    loginBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 登录中...';
    loginStatus.innerHTML = '';
    loginStatus.className = 'login-status';

    try {
        ws = new WebSocket('ws://localhost:8000');
        
        ws.onopen = () => {
            console.log('WebSocket连接已建立');
            // 发送登录请求
            sendMessage('LOGIN|' + username + '|' + password);
        };

        ws.onmessage = (event) => {
            console.log('[前端收到消息]', event.data); // 加这句确认格式
            handleServerMessage(event.data);
        };


        ws.onerror = (error) => {
            console.error('WebSocket错误:', error);
            handleLoginError('连接服务器失败，请检查服务器是否启动');
        };
        
        ws.onclose = (event) => {
            console.log('WebSocket连接已关闭:', event.code, event.reason);
            if (event.code !== 1000) {
                handleLoginError('与服务器的连接已断开');
            }
        };
    } catch (error) {
        console.error('创建WebSocket连接失败:', error);
        handleLoginError('无法连接到服务器，请检查服务器是否启动');
    }
}

// 处理登录错误
function handleLoginError(message) {
    const loginBtn = document.getElementById('loginBtn');
    const loginStatus = document.getElementById('loginStatus');
    
    loginBtn.disabled = false;
    loginBtn.innerHTML = '<i class="fas fa-sign-in-alt"></i> 登录';
    loginStatus.innerHTML = message;
    loginStatus.className = 'login-status error';
    
    if (ws) {
        ws.close();
    }
}

// 处理服务器消息
function handleServerMessage(message) {
    console.log('收到服务器消息:', message);
    
    const parts = message.split('|');
    const command = parts[0];
    
    switch (command) {
        case 'LOGIN_SUCCESS':
            console.log('前端解析 LOGIN_SUCCESS:', parts);
            handleLoginSuccess(parts[1]);
            break;
        case 'LOGIN_FAILED':
            handleLoginFailed(parts[1]);
            break;
        case 'USER_LIST':
            updateUserList(JSON.parse(parts[1]));
            break;
        case 'GROUP_LIST':
            updateGroupList(JSON.parse(parts[1]));
            break;
        case 'SUBGROUP_LIST':
            updateSubgroupList(JSON.parse(parts[1]));
            break;
        case 'USER_JOINED':
            addUserToList(parts[1]);
            break;
        case 'USER_LEFT':
            removeUserFromList(parts[1]);
            break;
        case 'GROUP_CREATED':
            showNotification('群组创建成功: ' + parts[1], 'success');
            break;
        case 'GROUP_CREATE_FAILED':
            showNotification('群组创建失败: ' + parts[1], 'error');
            break;
        case 'SUBGROUP_CREATED':
            console.log('小组创建成功:', parts[1]);
            showNotification('小组创建成功: ' + parts[1], 'success');
            break;
        case 'SUBGROUP_CREATE_FAILED':
            console.log('小组创建失败:', parts[1]);
            showNotification('小组创建失败: ' + parts[1], 'error');
            break;
        case 'GROUP_JOINED':
            console.log('成功加入群组:', parts[1]);
            showNotification('已加入群组: ' + parts[1], 'success');
            // 请求更新群组列表以确保状态同步
            sendMessage('GET_USER_GROUPS');
            break;
        case 'GROUP_JOIN_FAILED':
            showNotification('加入群组失败: ' + parts[1], 'error');
            break;
        case 'SUBGROUP_JOINED':
            showNotification('已加入小组: ' + parts[1], 'success');
            break;
        case 'SUBGROUP_JOIN_FAILED':
            showNotification('加入小组失败: ' + parts[1], 'error');
            break;
        case 'GROUP_MESSAGE':
            handleGroupMessage(parts[1], parts[2], parts[3], parts[4]);
            break;
        case 'SUBGROUP_MESSAGE':
            handleSubgroupMessage(parts[1], parts[2], parts[3], parts[4]);
            break;
        case 'PRIVATE_MESSAGE':
            handlePrivateMessage(parts[1], parts[2], parts[3], parts[4]);
            break;
        case 'FILE_MESSAGE':
            handleFileMessage(parts[1], parts[2], parts[3], parts[4], parts[5]);
            break;
        case 'IMAGE_MESSAGE':
            handleImageMessage(parts[1], parts[2], parts[3], parts[4], parts[5]);
            break;
        case 'SUBGROUP_INVITE':
            console.log('收到SUBGROUP_INVITE消息:', parts);
            handleSubgroupInvite(parts[1], parts[2], parts[3]);
            break;
        case 'GROUP_MEMBERS':
            handleGroupMembers(parts[1], JSON.parse(parts[2]));
            break;
        case 'GROUP_INVITE':
            handleGroupInvite(parts[1], parts[2]);
            break;
        case 'INVITE_TO_GROUP_SUCCESS':
            showNotification(parts[1], 'success');
            break;
        case 'INVITE_TO_GROUP_FAILED':
            showNotification('邀请失败: ' + parts[1], 'error');
            break;
        case 'SUBGROUP_INVITABLE_USERS':
            if (parts.length >= 4) {
                try {
                    const users = JSON.parse(parts[3]);
                    handleSubgroupInvitableUsers(parts[1], parts[2], users);
                } catch (e) {
                    console.error('解析可邀请用户列表失败:', e);
                }
            }
            break;
        
        case 'CHAT_HISTORY':
            if (parts.length >= 4) {
                try {
                    const messages = JSON.parse(parts[3]);
                    handleChatHistory(parts[1], parts[2], messages);
                } catch (e) {
                    console.error('解析历史消息失败:', e);
                }
            }
            break;
        
        case 'KICKED':
            handleKicked(parts[1]);
            break;
        default:
            console.log('未知命令:', command);
    }
}

// 处理登录成功
function handleLoginSuccess(userData) {
    currentUser = userData;
    document.getElementById('currentUser').textContent = currentUser;
    
    // 只在非测试模式下保存登录信息
    if (!isTestMode()) {
        const username = document.getElementById('username').value;
        const password = document.getElementById('password').value;
        saveLoginInfo(username, password);
    }
    
    // 只有 WebSocket ready 时才显示主界面
    if (ws && ws.readyState === WebSocket.OPEN) {
        document.getElementById('loginScreen').style.display = 'none';
        document.getElementById('mainApp').classList.remove('hidden');
        document.title = '智能聊天室 - ' + currentUser;
        const mode = isTestMode() ? '（测试模式）' : '';
        showNotification(`登录成功！${mode}`, 'success');
    } else {
        ws.onopen = () => {
            document.getElementById('loginScreen').style.display = 'none';
            document.getElementById('mainApp').classList.remove('hidden');
            document.title = '智能聊天室 - ' + currentUser;
            const mode = isTestMode() ? '（测试模式）' : '';
            showNotification(`登录成功！${mode}`, 'success');
        };
    }
}

// 处理登录失败
function handleLoginFailed(reason) {
    handleLoginError('登录失败: ' + (reason || '用户名或密码错误'));
}

// 处理被踢出
function handleKicked(reason) {
    showNotification('您已被踢出: ' + reason, 'error');
    setTimeout(() => {
        location.reload();
    }, 2000);
}

// 更新用户列表
function updateUserList(users) {
    userList = users;
    const usersList = document.getElementById('usersList');
    const onlineCount = document.getElementById('onlineCount');
    
    usersList.innerHTML = '';
    onlineCount.textContent = users.length;
    
    users.forEach(user => {
        if (user !== currentUser) {
            const userItem = createUserItem(user);
            usersList.appendChild(userItem);
        }
    });
}

// 创建用户列表项
function createUserItem(username) {
    const div = document.createElement('div');
    div.className = 'list-item';
    div.dataset.user = username;
    
    const unreadCount = unreadCounts.get(`USER_${username}`) || 0;
    
    div.innerHTML = `
        <div class="user-avatar">${username.charAt(0).toUpperCase()}</div>
        <div class="user-info">
            <div class="user-name">${username}</div>
            <div class="user-status">在线</div>
        </div>
        ${unreadCount > 0 ? `<span class="unread-badge">${unreadCount}</span>` : ''}
    `;
    
    div.onclick = () => openChat(`USER_${username}`);
    return div;
}

// 更新群组列表
function updateGroupList(groups) {
    console.log('更新群组列表:', groups);
    groupList = groups;
    const groupsList = document.getElementById('groupsList');
    
    groupsList.innerHTML = '';
    
    if (groups.length === 0) {
        groupsList.innerHTML = '<div class="empty-list">暂无群组</div>';
        return;
    }
    
    groups.forEach(group => {
        const groupItem = createGroupItem(group);
        groupsList.appendChild(groupItem);
    });
}

// 创建群组列表项
function createGroupItem(group) {
    const div = document.createElement('div');
    div.className = 'list-item';
    div.dataset.group = group.name;

    const unreadCount = unreadCounts.get(`GROUP_${group.name}`) || 0;

    div.innerHTML = `
        <div class="user-avatar">
            <i class="fas fa-layer-group"></i>
        </div>
        <div class="user-info">
            <div class="user-name">${group.name}</div>
        </div>
        ${unreadCount > 0 ? `<span class="unread-badge">${unreadCount}</span>` : ''}
        ${!group.joined ? `<button class="btn-small join-btn" onclick="joinGroup('${group.name}', event)">加群</button>` : ''}
    `;

    div.onclick = (e) => {
        // 避免点击加群按钮时也触发打开聊天
        if (e.target.classList.contains('join-btn')) return;
        openChat(`GROUP_${group.name}`);
    };
    return div;
}

// 新增加群方法
function joinGroup(groupName, event) {
    event.stopPropagation();
    sendMessage(`JOIN_GROUP|${groupName}`);
}

// 更新小组列表
function updateSubgroupList(subgroups) {
    console.log('更新小组列表:', subgroups);
    subgroupList = subgroups;
    const subgroupsList = document.getElementById('subgroupsList');
    
    subgroupsList.innerHTML = '';
    
    if (subgroups.length === 0) {
        subgroupsList.innerHTML = '<div class="empty-list">暂无小组</div>';
        return;
    }
    
    subgroups.forEach(subgroup => {
        const subgroupItem = createSubgroupItem(subgroup);
        subgroupsList.appendChild(subgroupItem);
    });
}

// 创建小组列表项
function createSubgroupItem(subgroup) {
    const div = document.createElement('div');
    div.className = 'list-item';
    div.dataset.subgroup = subgroup.name;
    
    const unreadCount = unreadCounts.get(`SUBGROUP_${subgroup.name}`) || 0;
    
    div.innerHTML = `
        <div class="user-avatar">
            <i class="fas fa-users-cog"></i>
        </div>
        <div class="user-info">
            <div class="user-name">${subgroup.name} (${subgroup.parentGroup})</div>
        </div>
        ${unreadCount > 0 ? `<span class="unread-badge">${unreadCount}</span>` : ''}
    `;
    
    div.onclick = () => openChat(`SUBGROUP_${subgroup.name}`);
    return div;
}

// 打开聊天
function openChat(target) {
    target = normalizeTarget(target);
    // 隐藏欢迎界面
    const welcomeScreen = document.getElementById('welcomeScreen');
    if (welcomeScreen) {
        welcomeScreen.style.display = 'none';
    }
    
    // 如果聊天窗口不存在，创建它
    if (!activeChats.has(target)) {
        const chatWindow = createChatWindow(target);
        activeChats.set(target, chatWindow);
    }
    
    // 隐藏所有聊天窗口
    activeChats.forEach((chatWindow, chatTarget) => {
        chatWindow.style.display = 'none';
    });
    
    // 显示目标聊天窗口
    const targetChatWindow = activeChats.get(target);
    if (targetChatWindow) {
        targetChatWindow.style.display = 'flex';
        document.getElementById('chatContainer').appendChild(targetChatWindow);
    }
    
    // 更新当前聊天
    currentChat = target;
    
    // 更新活动状态
    updateActiveChat(target);
    
    // 清除未读计数
    unreadCounts.set(target, 0);
    updateUnreadBadges();
    
    // 加载历史消息
    loadChatHistory(target);
}

// 加载聊天历史消息
function loadChatHistory(target) {
    const targetName = target.replace(/^(USER_|GROUP_|SUBGROUP_)/, '');
    let chatType;
    
    if (target.startsWith('USER_')) {
        chatType = 'PRIVATE';
    } else if (target.startsWith('GROUP_')) {
        chatType = 'GROUP';
    } else if (target.startsWith('SUBGROUP_')) {
        chatType = 'SUBGROUP';
    }
    
    if (chatType) {
        console.log('请求历史消息:', { targetName, chatType });
        sendMessage(`GET_MESSAGES|${targetName}|${chatType}`);
    }
}

// 创建聊天窗口
function createChatWindow(target) {
    const chatWindow = document.createElement('div');
    chatWindow.className = 'chat-window';
    chatWindow.dataset.target = target;
    
    const targetName = target.replace(/^(USER_|GROUP_|SUBGROUP_)/, '');
    
    // 根据聊天类型生成不同的标题栏
    let headerActions = '';
    if (target.startsWith('SUBGROUP_')) {
        headerActions = `
            <div class="chat-header-actions">
                <button class="btn-icon" onclick="showInviteToSubgroupModal('${targetName}')" title="邀请成员">
                    <i class="fas fa-user-plus"></i>
                </button>
            </div>
        `;
    }
    
    chatWindow.innerHTML = `
        <div class="chat-header">
            <div class="chat-title">
                <i class="fas fa-${target.startsWith('USER_') ? 'user' : target.startsWith('GROUP_') ? 'layer-group' : 'users-cog'}"></i>
                ${targetName}
            </div>
            ${headerActions}
        </div>
        <div class="chat-messages" id="messages_${target}"></div>
        <div class="chat-input-area">
            <div class="chat-input-container">
                <div class="chat-input">
                    <textarea 
                        id="input_${target}" 
                        placeholder="输入消息..." 
                        rows="1"
                        onkeydown="handleMessageKeydown(event, '${target}')"
                        oninput="autoResizeTextarea(this)"
                    ></textarea>
                    <div class="chat-input-actions">
                        <button class="btn-icon" onclick="showFileUploadModal()" title="发送文件">
                            <i class="fas fa-paperclip"></i>
                        </button>
                        <button class="btn-icon" onclick="showImageUploadModal()" title="发送图片">
                            <i class="fas fa-image"></i>
                        </button>
                        <button class="send-btn" onclick="sendMessageToChat('${target}')" title="发送">
                            <i class="fas fa-paper-plane"></i>
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    return chatWindow;
}

// 更新活动聊天
function updateActiveChat(target) {
    // 移除所有活动状态
    document.querySelectorAll('.list-item').forEach(item => {
        item.classList.remove('active');
    });
    
    // 添加活动状态到当前聊天
    const targetName = target.replace(/^(USER_|GROUP_|SUBGROUP_)/, '');
    const listItem = document.querySelector(`[data-${target.startsWith('USER_') ? 'user' : target.startsWith('GROUP_') ? 'group' : 'subgroup'}="${targetName}"]`);
    if (listItem) {
        listItem.classList.add('active');
    }
}

// 处理消息输入
function handleMessageKeydown(event, target) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessageToChat(target);
    }
}

// 自动调整文本框高度
function autoResizeTextarea(textarea) {
    textarea.style.height = 'auto';
    textarea.style.height = Math.min(textarea.scrollHeight, 100) + 'px';
}

// 发送消息到聊天
function sendMessageToChat(target) {
    if (pendingUpload) {
        sendPendingUpload();
        return;
    }
    const input = document.getElementById(`input_${target}`);
    const message = input.value.trim();
    if (!message) return;
    const targetName = target.replace(/^(USER_|GROUP_|SUBGROUP_)/, '');
    const targetType = target.startsWith('USER_') ? 'PRIVATE' : 
                      target.startsWith('GROUP_') ? 'GROUP' : 'SUBGROUP';
    // 发送消息到服务器
    sendMessage(`${targetType}_MESSAGE|${targetName}|${message}`);
    // 清空输入框
    input.value = '';
    input.style.height = 'auto';
    // 添加消息到聊天窗口
    addMessageToChat(target, currentUser, message, 'sent');
}

// 处理群组消息
function handleGroupMessage(groupName, sender, content, timestamp) {
    console.log('收到群组消息:', { groupName, sender, content, timestamp });
    
    const target = `GROUP_${groupName}`;
    
    // 确保聊天窗口存在
    if (!activeChats.has(target)) {
        console.log('创建新的群组聊天窗口:', target);
        const chatWindow = createChatWindow(target);
        activeChats.set(target, chatWindow);
        // 不立即显示，只创建
    }
    
    addMessageToChat(target, sender, content, 'received', timestamp);
    
    if (currentChat !== target) {
        incrementUnreadCount(target);
    }
}

// 处理小组消息
function handleSubgroupMessage(subgroupName, sender, content, timestamp) {
    const target = `SUBGROUP_${subgroupName}`;
    
    // 确保聊天窗口存在
    if (!activeChats.has(target)) {
        const chatWindow = createChatWindow(target);
        activeChats.set(target, chatWindow);
        // 不立即显示，只创建
    }
    
    addMessageToChat(target, sender, content, 'received', timestamp);
    
    if (currentChat !== target) {
        incrementUnreadCount(target);
    }
}

// 处理私聊消息
function handlePrivateMessage(sender, content, timestamp) {
    const target = `USER_${sender}`;
    
    // 确保聊天窗口存在
    if (!activeChats.has(target)) {
        const chatWindow = createChatWindow(target);
        activeChats.set(target, chatWindow);
        // 不立即显示，只创建
    }
    
    addMessageToChat(target, sender, content, 'received', timestamp);
    
    if (currentChat !== target) {
        incrementUnreadCount(target);
    }
}

// 处理文件消息
function handleFileMessage(target, sender, fileName, fileSize, timestamp) {
    let chatTarget;
    if (target.startsWith('USER_')) {
        chatTarget = `USER_${target}`;
    } else if (target.startsWith('GROUP_')) {
        chatTarget = `GROUP_${target}`;
    } else if (target.startsWith('SUBGROUP_')) {
        chatTarget = `SUBGROUP_${target}`;
    } else {
        // 如果没有前缀，根据目标类型判断
        chatTarget = target;
    }
    
    // 确保聊天窗口存在
    if (!activeChats.has(chatTarget)) {
        const chatWindow = createChatWindow(chatTarget);
        activeChats.set(chatTarget, chatWindow);
        // 不立即显示，只创建
    }
    
    addFileMessageToChat(chatTarget, sender, fileName, fileSize, 'received', timestamp);
    
    if (currentChat !== chatTarget) {
        incrementUnreadCount(chatTarget);
    }
}

// 处理图片消息
function handleImageMessage(target, sender, fileUrl, imageSize, timestamp) {
    console.log('[图片消息] handleImageMessage', {target, sender, fileUrl, imageSize, timestamp});
    if (!activeChats.has(target)) {
        const chatWindow = createChatWindow(target);
        activeChats.set(target, chatWindow);
    }
    addImageMessageToChat(target, sender, fileUrl, imageSize, sender === currentUser ? 'sent' : 'received', timestamp);
    if (currentChat !== target) {
        incrementUnreadCount(target);
    }
}

// 添加文件消息到聊天窗口
function addFileMessageToChat(target, sender, fileName, fileSize, type, timestamp) {
    // 写入缓存
    if (!chatMessages.has(target)) chatMessages.set(target, []);
    chatMessages.get(target).push({
        sender,
        content: 'FILE:' + fileName,
        type,
        timestamp: timestamp || Date.now(),
        fileName,
        fileSize
    });
    const messagesContainer = document.getElementById(`messages_${target}`);
    if (!messagesContainer) return;
    const time = timestamp ? new Date(parseInt(timestamp)).toLocaleTimeString() : new Date().toLocaleTimeString();
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${type}`;
    messageDiv.innerHTML = `
        <div class="message-info">
            <span class="sender">${sender}</span>
            <span class="message-time">${time}</span>
        </div>
        <div class="message-content">
            <a class='file-link' href="files/${encodeURIComponent(fileName)}" download>
                <span class='file-icon'><i class='fas fa-file-alt'></i></span>
                <span>${fileName}</span>
                <span style='font-size:12px;color:#888;'>${fileSize}</span>
                <button class='download-btn' onclick="event.stopPropagation();event.preventDefault();window.open('files/${encodeURIComponent(fileName)}')"><i class='fas fa-download'></i></button>
            </a>
        </div>
    `;
    messagesContainer.appendChild(messageDiv);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// 添加图片消息到聊天窗口
function addImageMessageToChat(target, sender, fileUrl, imageSize, type, timestamp) {
    if (!fileUrl.startsWith('http')) {
        console.error('[图片消息] fileUrl 非直链，忽略：', fileUrl);
        return;
    }
    console.log('[图片消息] addImageMessageToChat', {target, sender, fileUrl, imageSize, type, timestamp});
    if (!chatMessages.has(target)) chatMessages.set(target, []);
    chatMessages.get(target).push({
        sender,
        content: 'IMAGE:' + fileUrl,
        type,
        timestamp: timestamp || Date.now(),
        imageUrl: fileUrl,
        imageSize
    });
    const messagesContainer = document.getElementById(`messages_${target}`);
    if (!messagesContainer) return;
    const time = timestamp ? new Date(parseInt(timestamp)).toLocaleTimeString() : new Date().toLocaleTimeString();
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${type}`;
    messageDiv.innerHTML = `
        <div class="message-info">
            <span class="sender">${sender}</span>
            <span class="message-time">${time}</span>
        </div>
        <div class="message-content">
            <img src="${fileUrl}" alt="图片" style="max-width: 180px; max-height: 180px; border-radius:10px; cursor:pointer;" onclick="openImageModal('${fileUrl}')" />
            <span style="font-size:12px;color:#888;">${imageSize}</span>
        </div>
    `;
    messagesContainer.appendChild(messageDiv);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

function addMessageToChat(target, sender, content, type, timestamp) {
    // 写入缓存
    if (!chatMessages.has(target)) chatMessages.set(target, []);
    chatMessages.get(target).push({
        sender,
        content,
        type,
        timestamp: timestamp || Date.now()
    });
    const messagesContainer = document.getElementById(`messages_${target}`);
    if (!messagesContainer) return;
    const time = timestamp ? new Date(parseInt(timestamp)).toLocaleTimeString() : new Date().toLocaleTimeString();
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${type}`;
    messageDiv.innerHTML = `
        <div class="message-info">
            <span class="sender">${sender}</span>
            <span class="message-time">${time}</span>
        </div>
        <div class="message-content">${escapeHtml(content)}</div>
    `;
    messagesContainer.appendChild(messageDiv);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// 增加未读消息计数
function incrementUnreadCount(target) {
    const currentCount = unreadCounts.get(target) || 0;
    unreadCounts.set(target, currentCount + 1);
    updateUnreadBadges();
}

// 更新未读消息徽章
function updateUnreadBadges() {
    // 更新用户列表未读徽章
    userList.forEach(user => {
        if (user !== currentUser) {
            const target = `USER_${user}`;
            const unreadCount = unreadCounts.get(target) || 0;
            const listItem = document.querySelector(`[data-user="${user}"]`);
            if (listItem) {
                let badge = listItem.querySelector('.unread-badge');
                if (unreadCount > 0) {
                    if (!badge) {
                        badge = document.createElement('span');
                        badge.className = 'unread-badge';
                        listItem.appendChild(badge);
                    }
                    badge.textContent = unreadCount;
                } else if (badge) {
                    badge.remove();
                }
            }
        }
    });
    
    // 更新群组列表未读徽章
    groupList.forEach(group => {
        const target = `GROUP_${group.name}`;
        const unreadCount = unreadCounts.get(target) || 0;
        const listItem = document.querySelector(`[data-group="${group.name}"]`);
        if (listItem) {
            let badge = listItem.querySelector('.unread-badge');
            if (unreadCount > 0) {
                if (!badge) {
                    badge = document.createElement('span');
                    badge.className = 'unread-badge';
                    listItem.appendChild(badge);
                }
                badge.textContent = unreadCount;
            } else if (badge) {
                badge.remove();
            }
        }
    });
    
    // 更新小组列表未读徽章
    subgroupList.forEach(subgroup => {
        const target = `SUBGROUP_${subgroup.name}`;
        const unreadCount = unreadCounts.get(target) || 0;
        const listItem = document.querySelector(`[data-subgroup="${subgroup.name}"]`);
        if (listItem) {
            let badge = listItem.querySelector('.unread-badge');
            if (unreadCount > 0) {
                if (!badge) {
                    badge = document.createElement('span');
                    badge.className = 'unread-badge';
                    listItem.appendChild(badge);
                }
                badge.textContent = unreadCount;
            } else if (badge) {
                badge.remove();
            }
        }
    });
}

// 切换标签页
function switchTab(tabName) {
    // 更新标签按钮状态
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    document.querySelector(`[data-tab="${tabName}"]`).classList.add('active');
    
    // 更新标签内容
    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.remove('active');
    });
    document.getElementById(`${tabName}Tab`).classList.add('active');
}

// 全局关闭所有modal
function closeAllModals() {
    document.querySelectorAll('.modal').forEach(m => m.classList.add('hidden'));
    // 动态创建的群组邀请modal
    const groupInviteModal = document.getElementById('groupInviteModal');
    if (groupInviteModal) groupInviteModal.remove();
}

// 显示创建群组模态框
function showCreateGroupModal() {
    closeAllModals();
    document.getElementById('createGroupModal').classList.remove('hidden');
}

// 显示创建小组模态框
function showCreateSubgroupModal() {
    closeAllModals();
    // 填充群组选择下拉框
    const parentGroupSelect = document.getElementById('parentGroup');
    parentGroupSelect.innerHTML = '<option value="">请选择群组</option>';
    groupList.forEach(group => {
        const option = document.createElement('option');
        option.value = group.name;
        option.textContent = group.name;
        parentGroupSelect.appendChild(option);
    });
    // 监听群组选择变化
    parentGroupSelect.onchange = function() {
        const selectedGroup = this.value;
        const memberSelection = document.getElementById('memberSelection');
        memberSelection.innerHTML = '';
        if (selectedGroup) {
            // 从服务器获取群组成员
            sendMessage(`GET_GROUP_MEMBERS|${selectedGroup}`);
        }
    };
    document.getElementById('createSubgroupModal').classList.remove('hidden');
}

// 创建群组
function createGroup() {
    const groupName = document.getElementById('groupName').value.trim();
    
    if (!groupName) {
        showNotification('请输入群组名称', 'error');
        return;
    }
    
    sendMessage(`CREATE_GROUP|${groupName}`);
    closeModal('createGroupModal');
    
    // 清空表单
    document.getElementById('groupName').value = '';
}

// 创建小组
function createSubgroup() {
    const parentGroup = document.getElementById('parentGroup').value;
    const subgroupName = document.getElementById('subgroupName').value.trim();
    
    console.log('创建小组:', { parentGroup, subgroupName });
    
    if (!parentGroup || !subgroupName) {
        showNotification('请选择群组并输入小组名称', 'error');
        return;
    }
    
    // 获取选中的成员
    const selectedMembers = [];
    document.querySelectorAll('#memberSelection input[type="checkbox"]:checked').forEach(checkbox => {
        selectedMembers.push(checkbox.value);
    });
    
    console.log('选中的成员:', selectedMembers);
    
    // 允许只有创建者的小组
    const message = `CREATE_SUBGROUP|${parentGroup}|${subgroupName}|${selectedMembers.join(',')}`;
    console.log('发送创建小组消息:', message);
    sendMessage(message);
    
    closeModal('createSubgroupModal');
    
    // 清空表单
    document.getElementById('parentGroup').value = '';
    document.getElementById('subgroupName').value = '';
    document.querySelectorAll('#memberSelection input[type="checkbox"]').forEach(checkbox => {
        checkbox.checked = false;
    });
}

// 修改 showFileUploadModal 只选择文件
function showFileUploadModal() {
    const input = document.createElement('input');
    input.type = 'file';
    input.onchange = (e) => {
        const file = e.target.files[0];
        if (!file) return;
        pendingUpload = { type: 'file', file };
        renderPendingUpload();
    };
    input.click();
}

// 修改 showImageUploadModal 只选择图片
function showImageUploadModal() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.onchange = (e) => {
        const file = e.target.files[0];
        if (!file) return;
        pendingUpload = { type: 'image', file };
        renderPendingUpload();
    };
    input.click();
}

// 渲染输入区下方的待发送内容
function renderPendingUpload() {
    let container = document.getElementById('pendingUploadContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'pendingUploadContainer';
        const inputArea = document.querySelector('.chat-input-area');
        inputArea.appendChild(container);
    }
    if (!pendingUpload) {
        container.innerHTML = '';
        return;
    }
    if (pendingUpload.type === 'image') {
        const url = URL.createObjectURL(pendingUpload.file);
        container.innerHTML = `<div style="display:flex;align-items:center;gap:10px;">
            <img src="${url}" style="max-width:60px;max-height:60px;border-radius:6px;"/>
            <span>${pendingUpload.file.name}</span>
            <button onclick="cancelPendingUpload()">取消</button>
        </div>`;
    } else if (pendingUpload.type === 'file') {
        container.innerHTML = `<div style="display:flex;align-items:center;gap:10px;">
            <span style="font-size:18px;"><i class='fas fa-file-alt'></i></span>
            <span>${pendingUpload.file.name}</span>
            <button onclick="cancelPendingUpload()">取消</button>
        </div>`;
    }
}

function cancelPendingUpload() {
    pendingUpload = null;
    renderPendingUpload();
}

// 发送待上传文件/图片（只发普通文本消息 IMAGE:直链 或 FILE:直链）
async function sendPendingUpload() {
    if (!pendingUpload || !currentChat) return;
    const file = pendingUpload.file;
    try {
        let url, fileName, fileSize;
        if (pendingUpload.type === 'image') {
            ({ url, fileName, fileSize } = await uploadImageFile(file));
            ensureWsReady(() => {
                try {
                    ws.send(`IMAGE:${url}`);
                    addMessageToChat(currentChat, currentUser, `IMAGE:${url}`, 'sent', Date.now());
                } catch (e) {
                    showNotification('图片消息发送失败', 'error');
                }
            });
        } else if (pendingUpload.type === 'file') {
            ({ url, fileName, fileSize } = await uploadFile(file));
            ensureWsReady(() => {
                try {
                    ws.send(`FILE:${url}`);
                    addMessageToChat(currentChat, currentUser, `FILE:${url}`, 'sent', Date.now());
                } catch (e) {
                    showNotification('文件消息发送失败', 'error');
                }
            });
        }
    } catch (e) {
        showNotification(pendingUpload.type === 'image' ? '图片上传失败' : '文件上传失败', 'error');
    }
    pendingUpload = null;
    renderPendingUpload();
}

// 处理文件拖拽
function handleFileDrop(files) {
    const fileList = document.getElementById('fileList');
    fileList.innerHTML = '';
    
    Array.from(files).forEach(file => {
        const fileItem = document.createElement('div');
        fileItem.className = 'file-item';
        fileItem.innerHTML = `
            <i class="fas fa-file"></i>
            <div class="file-info">
                <div class="file-name">${file.name}</div>
                <div class="file-size">${formatFileSize(file.size)}</div>
            </div>
        `;
        fileList.appendChild(fileItem);
    });
}

// 上传图片到 Spring Boot
async function uploadImageFile(file) {
    const formData = new FormData();
    formData.append('file', file);
    const resp = await fetch('http://localhost:8080/upload_image', { method: 'POST', body: formData });
    if (!resp.ok) throw new Error('上传失败');
    return await resp.json(); // {url, fileName, fileSize}
}

// 上传文件到 Spring Boot
async function uploadFile(file) {
    const formData = new FormData();
    formData.append('file', file);
    const resp = await fetch('http://localhost:8080/upload_file', { method: 'POST', body: formData });
    if (!resp.ok) throw new Error('上传失败');
    return await resp.json(); // {url, fileName, fileSize}
}

// 图片选择事件处理
// 修改图片选择事件处理
document.getElementById('imageInput').addEventListener('change', function(event) {
    const file = event.target.files[0];
    if (!file) return;
    console.log('[图片上传] 选择文件:', file.name, '大小:', file.size);
    if (!currentChat) {
        showNotification('请先选择一个聊天', 'error');
        return;
    }
    if (file.size > 5 * 1024 * 1024) {
        showNotification('图片大小不能超过5MB', 'error');
        return;
    }
    const reader = new FileReader();
    reader.onload = function(e) {
        let base64Data = e.target.result;
        if (typeof base64Data === 'string' && base64Data.includes(',')) {
            base64Data = base64Data.split(',')[1];
        }
        if (!base64Data || /[^A-Za-z0-9+/=]/.test(base64Data)) {
            showNotification('图片编码异常，上传失败', 'error');
            return;
        }
        const targetName = currentChat.replace(/^(USER_|GROUP_|SUBGROUP_)/, '');
        const chatType = currentChat.startsWith('USER_') ? 'PRIVATE' :
            currentChat.startsWith('GROUP_') ? 'GROUP' : 'SUBGROUP';
        const message = {
            type: 'IMAGE_UPLOAD',
            target: targetName,
            chatType: chatType,
            fileName: file.name,
            fileSize: file.size,
            fileType: file.type,
            data: base64Data
        };
        console.log('[图片上传] 发送消息:', message);
        ensureWsReady(() => {
            ws.send(JSON.stringify(message));
            console.log('[图片上传] 已通过 WebSocket 发送');
        });
    };
    reader.readAsDataURL(file);
    event.target.value = '';
});

// 存储当前的小组邀请信息
let currentSubgroupInvite = null;

// 处理小组邀请
function handleSubgroupInvite(subgroupName, parentGroup, inviter) {
    closeAllModals();
    console.log('收到小组邀请:', {subgroupName, parentGroup, inviter});
    // 保存邀请信息
    currentSubgroupInvite = { subgroupName, parentGroup, inviter };
    // 显示邀请通知
    showNotification(`${inviter} 邀请您加入小组 "${subgroupName}"`, 'info');
    // 填充模态框内容
    document.getElementById('subgroupInviteMessage').textContent = `${inviter} 邀请您加入小组`;
    document.getElementById('inviterName').textContent = inviter;
    document.getElementById('parentGroupName').textContent = parentGroup;
    document.getElementById('subgroupNameSpan').textContent = subgroupName;
    // 显示确认模态框
    document.getElementById('subgroupInviteConfirmModal').classList.remove('hidden');
}

// 接受小组邀请
function acceptSubgroupInvite() {
    if (currentSubgroupInvite) {
        sendMessage(`ACCEPT_SUBGROUP_INVITE|${currentSubgroupInvite.subgroupName}`);
        showNotification('已接受小组邀请', 'success');
        closeSubgroupInviteModal();
    }
}

// 拒绝小组邀请
function declineSubgroupInvite() {
    if (currentSubgroupInvite) {
        sendMessage(`DECLINE_SUBGROUP_INVITE|${currentSubgroupInvite.subgroupName}`);
        showNotification('已拒绝小组邀请', 'info');
        closeSubgroupInviteModal();
    }
}

// 关闭小组邀请模态框
function closeSubgroupInviteModal() {
    document.getElementById('subgroupInviteConfirmModal').classList.add('hidden');
    currentSubgroupInvite = null;
}

// 显示小组邀请模态框
function showInviteToSubgroupModal(subgroupName) {
    closeAllModals();
    console.log('显示小组邀请模态框:', subgroupName);
    // 设置小组名称
    document.getElementById('inviteSubgroupName').value = subgroupName;
    // 请求获取可邀请的用户列表
    // 需要先找到这个小组的父群组
    const subgroup = subgroupList.find(sg => sg.name === subgroupName);
    if (subgroup) {
        console.log('找到小组信息:', subgroup);
        sendMessage(`GET_SUBGROUP_INVITABLE_USERS|${subgroup.parentGroup}|${subgroupName}`);
    }
    document.getElementById('inviteToSubgroupModal').classList.remove('hidden');
}

// 发送小组邀请
function sendSubgroupInvites() {
    const subgroupName = document.getElementById('inviteSubgroupName').value;
    const selectedUsers = [];
    
    document.querySelectorAll('#inviteSubgroupUserSelection input[type="checkbox"]:checked').forEach(checkbox => {
        selectedUsers.push(checkbox.value);
    });
    
    if (selectedUsers.length === 0) {
        showNotification('请选择要邀请的用户', 'error');
        return;
    }
    
    console.log('发送小组邀请:', { subgroupName, selectedUsers });
    
    // 发送邀请
    selectedUsers.forEach(username => {
        sendMessage(`INVITE_TO_SUBGROUP|${subgroupName}|${username}`);
    });
    
    showNotification(`已向 ${selectedUsers.length} 位用户发送小组邀请`, 'success');
    closeModal('inviteToSubgroupModal');
    
    // 清空选择
    document.querySelectorAll('#inviteSubgroupUserSelection input[type="checkbox"]').forEach(checkbox => {
        checkbox.checked = false;
    });
}

// 处理可邀请用户列表
function handleSubgroupInvitableUsers(parentGroup, subgroupName, users) {
    console.log('收到可邀请用户列表:', { parentGroup, subgroupName, users });
    
    const userSelection = document.getElementById('inviteSubgroupUserSelection');
    userSelection.innerHTML = '';
    
    if (!users || users.length === 0) {
        userSelection.innerHTML = '<div class="empty-list">暂无可邀请的用户</div>';
        return;
    }
    
    users.forEach(username => {
        const userDiv = document.createElement('div');
        userDiv.className = 'user-checkbox';
        userDiv.innerHTML = `
            <label>
                <input type="checkbox" value="${username}">
                <span class="checkmark"></span>
                ${username}
            </label>
        `;
        userSelection.appendChild(userDiv);
    });
}

// 处理聊天历史消息
function handleChatHistory(chatId, chatType, messages) {
    console.log('收到历史消息:', { chatId, chatType, messages });
    let target;
    if (chatType === 'PRIVATE') {
        target = `USER_${chatId}`;
    } else if (chatType === 'GROUP') {
        target = `GROUP_${chatId}`;
    } else if (chatType === 'SUBGROUP') {
        target = `SUBGROUP_${chatId}`;
    }
    if (!target) {
        console.warn('目标聊天窗口不存在:', target);
        return;
    }
    // 只创建窗口，不再请求历史消息，避免递归
    if (!activeChats.has(target)) {
        console.warn('目标聊天窗口不存在，自动创建:', target);
        const chatWindow = createChatWindow(target);
        activeChats.set(target, chatWindow);
        document.getElementById('chatContainer').appendChild(chatWindow);
    }
    const messagesContainer = document.getElementById(`messages_${target}`);
    if (!messagesContainer) {
        console.warn('消息容器不存在:', `messages_${target}`);
        return;
    }
    // 合并历史消息和本地缓存，去重（按时间戳+内容+发送者）
    let localMsgs = chatMessages.get(target) || [];
    let allMsgs = [...messages.map(m => ({...m, type: m.sender === currentUser ? 'sent' : 'received'})), ...localMsgs];
    // 去重
    let seen = new Set();
    let merged = [];
    for (let msg of allMsgs) {
        let key = `${msg.sender}|${msg.content}|${msg.timestamp}`;
        if (!seen.has(key)) {
            seen.add(key);
            merged.push(msg);
        }
    }
    // 只保留最新100条
    const MAX_HISTORY = 100;
    if (merged.length > MAX_HISTORY) {
        merged = merged.slice(-MAX_HISTORY);
    }
    // 更新缓存
    chatMessages.set(target, merged);
    // 批量渲染优化：拼接 HTML 字符串后一次性 innerHTML
    let html = '';
    merged.sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));
    for (let msg of merged) {
        const isSelf = msg.sender === currentUser;
        const time = msg.timestamp ? new Date(parseInt(msg.timestamp)).toLocaleTimeString() : new Date().toLocaleTimeString();
        if (msg.content && msg.content.startsWith('FILE:')) {
            const fileUrl = msg.content.substring(5);
            let fileName = msg.fileName || (fileUrl.split('/').pop().split('?')[0]);
            const fileSize = msg.fileSize || '';
            html += `<div class="message ${msg.type} ${isSelf ? 'sent' : 'received'}">
                <div class="message-info">
                    <span class="sender">${msg.sender}</span>
                    <span class="message-time">${time}</span>
                </div>
                <div class="message-content">
                    <a class='file-link' href="${fileUrl}" target="_blank" download>
                        <span class='file-icon'><i class='fas fa-file-alt'></i></span>
                        <span>${fileName}</span>
                        <span style='font-size:12px;color:#888;'>${fileSize}</span>
                        <button class='download-btn' onclick="event.stopPropagation();event.preventDefault();window.open('${fileUrl}')"><i class='fas fa-download'></i></button>
                    </a>
                </div>
            </div>`;
        } else if (msg.content && msg.content.startsWith('IMAGE:')) {
            const imgUrl = msg.content.substring(6);
            const imageSize = msg.imageSize || '';
            html += `<div class="message ${msg.type} ${isSelf ? 'sent' : 'received'}">
                <div class="message-info">
                    <span class="sender">${msg.sender}</span>
                    <span class="message-time">${time}</span>
                </div>
                <div class="message-content">
                    <img src="${imgUrl}" alt="图片" style="max-width: 180px; max-height: 180px; border-radius:10px; cursor:pointer;" onclick="openImageModal('${imgUrl}')" />
                    <span style="font-size:12px;color:#888;">${imageSize}</span>
                </div>
            </div>`;
        } else {
            html += `<div class="message ${msg.type} ${isSelf ? 'sent' : 'received'}">
                <div class="message-info">
                    <span class="sender">${msg.sender}</span>
                    <span class="message-time">${time}</span>
                </div>
                <div class="message-content">${escapeHtml(msg.content)}</div>
            </div>`;
        }
    }
    messagesContainer.innerHTML = html;
    // 渲染后自动滚动到底部
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
    console.log(`已加载 ${merged.length} 条历史消息到 ${target}`);
}

// 关闭模态框
function closeModal(modalId) {
    document.getElementById(modalId).classList.add('hidden');
}

// 显示通知
function showNotification(message, type = 'info') {
    const notificationArea = document.getElementById('notificationArea');
    const notification = document.createElement('div');
    notification.className = `notification ${type}`;
    
    const icon = type === 'success' ? 'check-circle' : 
                 type === 'error' ? 'exclamation-circle' : 'info-circle';
    
    notification.innerHTML = `
        <i class="fas fa-${icon}"></i>
        <span>${message}</span>
    `;
    
    notificationArea.appendChild(notification);
    
    // 3秒后自动移除
    setTimeout(() => {
        notification.remove();
    }, 3000);
}

// 工具函数
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function downloadFile(fileName) {
    const link = document.createElement('a');
    link.href = `files/${fileName}`;
    link.download = fileName;
    link.click();
}

// 优化 openImageModal，支持大图预览
function openImageModal(imageSrc) {
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.style.display = 'flex';
    modal.style.alignItems = 'center';
    modal.style.justifyContent = 'center';
    modal.style.background = 'rgba(0,0,0,0.7)';
    modal.innerHTML = `
        <div class="modal-content" style="background:none;box-shadow:none;max-width:90vw;max-height:90vh;display:flex;align-items:center;justify-content:center;">
            <img src="${imageSrc}" style="max-width:80vw;max-height:80vh;border-radius:12px;box-shadow:0 4px 24px rgba(0,0,0,0.25);" alt="预览图片">
            <button class="close-btn" style="position:absolute;top:30px;right:40px;font-size:32px;color:#fff;background:none;border:none;cursor:pointer;z-index:10;" onclick="this.closest('.modal').remove()"><i class="fas fa-times"></i></button>
        </div>
    `;
    document.body.appendChild(modal);
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.remove();
        }
    });
}

// 发送消息到服务器
function sendMessage(message) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(message);
    } else {
        console.error('WebSocket连接未建立');
    }
}

// 退出登录
function logout() {
    if (confirm('确定要退出登录吗？')) {
        // 清除保存的登录信息
        clearSavedLogin();
        
        if (ws) {
            ws.close();
        }
        location.reload();
    }
}

// 显示设置
function showSettings() {
    showNotification('设置功能开发中...', 'info');
}

// 处理群组成员响应
function handleGroupMembers(groupName, members) {
    const memberSelection = document.getElementById('memberSelection');
    memberSelection.innerHTML = '';
    
    if (members && members.length > 0) {
        members.forEach(member => {
            if (member !== currentUser) {
                const memberItem = document.createElement('div');
                memberItem.className = 'member-item';
                memberItem.innerHTML = `
                    <input type="checkbox" id="member_${member}" value="${member}">
                    <label for="member_${member}">${member}</label>
                `;
                memberSelection.appendChild(memberItem);
            }
        });
    } else {
        memberSelection.innerHTML = '<p>该群组没有其他成员</p>';
    }
}

// 显示调试信息
function showDebugInfo() {
    const debugInfo = {
        '当前用户': currentUser,
        '在线用户列表': userList,
        '群组列表': groupList,
        '小组列表': subgroupList,
        '活跃聊天窗口': Array.from(activeChats.keys()),
        '当前聊天': currentChat,
        '未读消息计数': Object.fromEntries(unreadCounts),
        'WebSocket状态': ws ? ws.readyState : 'null'
    };
    
    console.log('=== 调试信息 ===');
    console.table(debugInfo);
    
    alert('调试信息已输出到控制台，请按F12打开开发者工具查看Console标签页');
}

// 显示邀请用户模态框
function showInviteUserModal() {
    closeAllModals();
    const modal = document.getElementById('inviteUserModal');
    const groupSelect = document.getElementById('inviteTargetGroup');
    const userSelection = document.getElementById('inviteUserSelection');
    // 填充群组选项
    groupSelect.innerHTML = '<option value="">请选择群组</option>';
    groupList.filter(group => group.joined).forEach(group => {
        const option = document.createElement('option');
        option.value = group.name;
        option.textContent = group.name;
        groupSelect.appendChild(option);
    });
    // 填充用户选项
    userSelection.innerHTML = '';
    userList.forEach(user => {
        if (user !== currentUser) {
            const div = document.createElement('div');
            div.className = 'member-item';
            div.innerHTML = `
                <label>
                    <input type="checkbox" value="${user}">
                    <span>${user}</span>
                </label>
            `;
            userSelection.appendChild(div);
        }
    });
    modal.classList.remove('hidden');
}

// 发送群组邀请
function sendGroupInvites() {
    const groupName = document.getElementById('inviteTargetGroup').value;
    const userSelection = document.getElementById('inviteUserSelection');
    const selectedUsers = Array.from(userSelection.querySelectorAll('input[type="checkbox"]:checked'))
        .map(checkbox => checkbox.value);
    
    if (!groupName) {
        showNotification('请选择群组', 'error');
        return;
    }
    
    if (selectedUsers.length === 0) {
        showNotification('请选择要邀请的用户', 'error');
        return;
    }
    
    // 为每个用户发送邀请
    selectedUsers.forEach(user => {
        sendMessage(`INVITE_TO_GROUP|${groupName}|${user}`);
    });
    
    showNotification(`已向 ${selectedUsers.length} 个用户发送群组邀请`, 'success');
    closeModal('inviteUserModal');
}

// 处理群组邀请
function handleGroupInvite(groupName, inviter) {
    console.log('收到群组邀请:', { groupName, inviter });
    
    // 创建邀请确认模态框
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.id = 'groupInviteModal';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h3>群组邀请</h3>
            </div>
            <div class="modal-body">
                <p><strong>${inviter}</strong> 邀请您加入群组:</p>
                <p class="group-name"><i class="fas fa-layer-group"></i> ${groupName}</p>
                <p>是否接受这个邀请？</p>
            </div>
            <div class="modal-footer">
                <button class="btn-secondary" onclick="rejectGroupInvite('${groupName}', '${inviter}')">拒绝</button>
                <button class="btn-primary" onclick="acceptGroupInvite('${groupName}', '${inviter}')">接受</button>
            </div>
        </div>
    `;
    
    document.body.appendChild(modal);
    modal.classList.remove('hidden');
    
    // 添加背景点击关闭
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            rejectGroupInvite(groupName, inviter);
        }
    });
}

// 接受群组邀请
function acceptGroupInvite(groupName, inviter) {
    console.log('接受群组邀请:', groupName);
    sendMessage(`JOIN_GROUP|${groupName}`);
    showNotification(`已接受 ${inviter} 的群组邀请: ${groupName}`, 'success');
    closeGroupInviteModal();
}

// 拒绝群组邀请  
function rejectGroupInvite(groupName, inviter) {
    console.log('拒绝群组邀请:', groupName);
    showNotification(`已拒绝 ${inviter} 的群组邀请: ${groupName}`, 'info');
    closeGroupInviteModal();
}

// 关闭群组邀请模态框
function closeGroupInviteModal() {
    const modal = document.getElementById('groupInviteModal');
    if (modal) {
        modal.remove();
    }
}

// 检查保存的登录状态
function checkSavedLogin() {
    // 如果是测试模式，不自动填充登录信息
    if (isTestMode()) {
        console.log('测试模式已启用，跳过自动登录');
        return;
    }
    
    const savedUser = localStorage.getItem('chatroom_username');
    const savedPassword = localStorage.getItem('chatroom_password');
    
    if (savedUser && savedPassword) {
        console.log('发现保存的登录信息，显示切换用户选项...');
        document.getElementById('username').value = savedUser;
        document.getElementById('password').value = savedPassword;
        
        // 显示切换用户选项
        showUserSwitchOption(savedUser, savedPassword);
    }
}

// 检查是否为测试模式
function isTestMode() {
    return localStorage.getItem('chatroom_test_mode') === 'true';
}

// 切换测试模式
function toggleTestMode() {
    const testMode = document.getElementById('testMode').checked;
    localStorage.setItem('chatroom_test_mode', testMode.toString());
    
    if (testMode) {
        // 清除保存的登录信息
        clearSavedLogin();
        // 清空输入框
        document.getElementById('username').value = '';
        document.getElementById('password').value = '';
        // 隐藏用户切换选项
        const loginStatus = document.getElementById('loginStatus');
        loginStatus.style.display = 'none';
        
        showNotification('测试模式已启用，可以在多个标签页登录不同用户', 'success');
    } else {
        showNotification('测试模式已禁用，将启用会话保持功能', 'info');
        // 重新检查登录状态
        setTimeout(() => {
            checkSavedLogin();
        }, 100);
    }
}

// 显示用户切换选项
function showUserSwitchOption(savedUser, savedPassword) {
    const loginStatus = document.getElementById('loginStatus');
    loginStatus.innerHTML = `
        <div class="user-switch-option">
            <p>发现已保存的登录信息: <strong>${savedUser}</strong></p>
            <div class="switch-buttons">
                <button class="btn-secondary btn-small" onclick="clearSavedLogin(); location.reload();">切换用户</button>
                <button class="btn-primary btn-small" onclick="autoLogin('${savedUser}', '${savedPassword}')">继续登录</button>
            </div>
        </div>
    `;
    loginStatus.className = 'login-status info';
    loginStatus.style.display = 'block';
}

// 自动登录
function autoLogin(username, password) {
    const loginBtn = document.getElementById('loginBtn');
    const loginStatus = document.getElementById('loginStatus');
    
    loginBtn.disabled = true;
    loginBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 自动登录中...';
    loginStatus.innerHTML = '正在恢复会话...';
    loginStatus.className = 'login-status info';

    try {
        ws = new WebSocket('ws://localhost:8000');
        
        ws.onopen = () => {
            console.log('WebSocket连接已建立');
            sendMessage('LOGIN|' + username + '|' + password);
        };
        
        ws.onmessage = (event) => {
            console.log('收到服务器消息:', event.data);
            handleServerMessage(event.data);
        };
        
        ws.onerror = (error) => {
            console.error('WebSocket错误:', error);
            handleAutoLoginError('自动登录失败，请手动登录');
        };
        
        ws.onclose = (event) => {
            console.log('WebSocket连接已关闭:', event.code, event.reason);
            if (event.code !== 1000) {
                handleAutoLoginError('连接已断开');
            }
        };
    } catch (error) {
        console.error('创建WebSocket连接失败:', error);
        handleAutoLoginError('无法连接到服务器');
    }
}

// 处理自动登录错误
function handleAutoLoginError(message) {
    const loginBtn = document.getElementById('loginBtn');
    const loginStatus = document.getElementById('loginStatus');
    
    loginBtn.disabled = false;
    loginBtn.innerHTML = '<i class="fas fa-sign-in-alt"></i> 登录';
    loginStatus.innerHTML = message + '，请重新输入登录信息';
    loginStatus.className = 'login-status error';
    
    // 清除保存的登录信息
    clearSavedLogin();
    
    if (ws) {
        ws.close();
    }
}

// 保存登录信息
function saveLoginInfo(username, password) {
    localStorage.setItem('chatroom_username', username);
    localStorage.setItem('chatroom_password', password);
    localStorage.setItem('chatroom_login_time', Date.now().toString());
}

// 清除保存的登录信息
function clearSavedLogin() {
    localStorage.removeItem('chatroom_username');
    localStorage.removeItem('chatroom_password');
    localStorage.removeItem('chatroom_login_time');
}

// 工具函数，统一 target key 格式
function normalizeTarget(target) {
    if (target.startsWith('USER_') || target.startsWith('GROUP_') || target.startsWith('SUBGROUP_')) {
        return target.toUpperCase();
    }
    return target;
}

// 上传文件和图片时自动重连
function ensureWsReady(callback) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        callback();
    } else {
        showNotification('连接未就绪，请刷新页面重新登录', 'error');
    }
}

function renderMessage(message) {
    // ... 其他类型处理 ...
    if (message.type === 'IMAGE' || (typeof message.content === 'string' && message.content.startsWith('IMAGE:'))) {
        let fileUrl = message.content.startsWith('IMAGE:') ? message.content.substring(6) : '';
        if (fileUrl) {
            return `<div class="chat-message image-message"><img src="${fileUrl}" alt="图片" style="max-width:200px;max-height:200px;" /></div>`;
        } else {
            return `<div class="chat-message image-message">[图片消息]</div>`;
        }
    }
    // ... 其他类型处理 ...
}