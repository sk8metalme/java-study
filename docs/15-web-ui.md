# Web UI実装ハンズオン

## 1. はじめに

このドキュメントでは、Thymeleafを使ったWeb UIを実装します。

### 1.1 実装する画面

- ログイン画面
- チャンネル一覧画面
- チャット画面（メッセージ送受信）

### 1.2 技術スタック

- **Thymeleaf**: サーバーサイドテンプレートエンジン
- **Bootstrap 5**: CSSフレームワーク
- **JavaScript**: リアルタイム更新（ポーリング）

---

## 2. ディレクトリ構造

```
src/main/
├── java/com/minislack/presentation/web/
│   └── WebController.java
└── resources/
    ├── templates/           # Thymeleafテンプレート
    │   ├── login.html
    │   ├── channels.html
    │   └── chat.html
    └── static/              # 静的ファイル
        ├── css/
        │   └── style.css
        └── js/
            └── app.js
```

---

## 3. Step 1: Webコントローラーの実装

### 3.1 WebController

**ファイル**: `src/main/java/com/minislack/presentation/web/WebController.java`

```java
package com.minislack.presentation.web;

import java.util.List;
import java.util.Objects;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.minislack.application.channel.ChannelManagementService;
import com.minislack.application.message.MessageService;
import com.minislack.application.user.UserQueryService;
import com.minislack.domain.model.channel.Channel;
import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.message.Message;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;

/**
 * Webコントローラー
 * Thymeleafビューを返す
 */
@Controller
public class WebController {
    
    private final UserQueryService userQueryService;
    private final ChannelManagementService channelService;
    private final MessageService messageService;

    public WebController(@NonNull UserQueryService userQueryService,
                        @NonNull ChannelManagementService channelService,
                        @NonNull MessageService messageService) {
        this.userQueryService = Objects.requireNonNull(userQueryService);
        this.channelService = Objects.requireNonNull(channelService);
        this.messageService = Objects.requireNonNull(messageService);
    }

    /**
     * ログイン画面
     * GET /
     */
    @GetMapping("/")
    @NonNull
    public String index() {
        return "redirect:/login";
    }

    /**
     * ログイン画面
     * GET /login
     */
    @GetMapping("/login")
    @NonNull
    public String login() {
        return "login";
    }

    /**
     * チャンネル一覧画面
     * GET /channels?userId=xxx
     */
    @GetMapping("/channels")
    @NonNull
    public String channels(@RequestParam @NonNull String userId, @NonNull Model model) {
        User user = userQueryService.findById(UserId.of(userId))
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        List<Channel> myChannels = channelService.findChannelsByUser(UserId.of(userId));
        List<Channel> publicChannels = channelService.findPublicChannels();
        
        model.addAttribute("user", user);
        model.addAttribute("myChannels", myChannels);
        model.addAttribute("publicChannels", publicChannels);
        
        return "channels";
    }

    /**
     * チャット画面
     * GET /channels/{channelId}/chat?userId=xxx
     */
    @GetMapping("/channels/{channelId}/chat")
    @NonNull
    public String chat(@PathVariable @NonNull String channelId,
                      @RequestParam @NonNull String userId,
                      @NonNull Model model) {
        User user = userQueryService.findById(UserId.of(userId))
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        List<Message> messages = messageService.getMessages(
            ChannelId.of(channelId),
            UserId.of(userId),
            50,
            0
        );
        
        model.addAttribute("user", user);
        model.addAttribute("channelId", channelId);
        model.addAttribute("messages", messages);
        
        return "chat";
    }
}
```

**学習ポイント**:
- ✅ **@Controller**: ビューを返すコントローラー（`@RestController`ではない）
- ✅ **Model**: ビューにデータを渡す
- ✅ **return "login"**: `templates/login.html`を返す

---

## 4. Step 2: ログイン画面

### 4.1 login.html

**ファイル**: `src/main/resources/templates/login.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login - MiniSlack</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link th:href="@{/css/style.css}" rel="stylesheet">
</head>
<body>
    <div class="container">
        <div class="row justify-content-center mt-5">
            <div class="col-md-6">
                <div class="card">
                    <div class="card-header">
                        <h3>MiniSlack - Login</h3>
                    </div>
                    <div class="card-body">
                        <form id="loginForm">
                            <div class="mb-3">
                                <label for="emailOrUsername" class="form-label">Email or Username</label>
                                <input type="text" class="form-control" id="emailOrUsername" required>
                            </div>
                            <div class="mb-3">
                                <label for="password" class="form-label">Password</label>
                                <input type="password" class="form-control" id="password" required>
                            </div>
                            <button type="submit" class="btn btn-primary w-100">Login</button>
                        </form>
                        <hr>
                        <a href="#" data-bs-toggle="modal" data-bs-target="#registerModal">Create an account</a>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- 登録モーダル -->
    <div class="modal fade" id="registerModal" tabindex="-1">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title">Register</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body">
                    <form id="registerForm">
                        <div class="mb-3">
                            <label for="regUsername" class="form-label">Username</label>
                            <input type="text" class="form-control" id="regUsername" required>
                        </div>
                        <div class="mb-3">
                            <label for="regEmail" class="form-label">Email</label>
                            <input type="email" class="form-control" id="regEmail" required>
                        </div>
                        <div class="mb-3">
                            <label for="regPassword" class="form-label">Password</label>
                            <input type="password" class="form-control" id="regPassword" required>
                        </div>
                        <div class="mb-3">
                            <label for="regDisplayName" class="form-label">Display Name</label>
                            <input type="text" class="form-control" id="regDisplayName" required>
                        </div>
                        <button type="submit" class="btn btn-primary w-100">Register</button>
                    </form>
                </div>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script th:src="@{/js/app.js}"></script>
</body>
</html>
```

### 4.2 JavaScriptの実装

**ファイル**: `src/main/resources/static/js/app.js`

```javascript
// ログインフォーム
document.getElementById('loginForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const emailOrUsername = document.getElementById('emailOrUsername').value;
    const password = document.getElementById('password').value;
    
    try {
        const response = await fetch('/api/v1/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ emailOrUsername, password })
        });
        
        if (response.ok) {
            const data = await response.json();
            // ユーザーIDを保存
            localStorage.setItem('userId', data.user.userId);
            localStorage.setItem('username', data.user.username);
            // チャンネル一覧へ遷移
            window.location.href = `/channels?userId=${data.user.userId}`;
        } else {
            alert('Login failed. Please check your credentials.');
        }
    } catch (error) {
        console.error('Login error:', error);
        alert('An error occurred. Please try again.');
    }
});

// 登録フォーム
document.getElementById('registerForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const username = document.getElementById('regUsername').value;
    const email = document.getElementById('regEmail').value;
    const password = document.getElementById('regPassword').value;
    const displayName = document.getElementById('regDisplayName').value;
    
    try {
        const response = await fetch('/api/v1/users/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, email, password, displayName })
        });
        
        if (response.ok) {
            alert('Registration successful! Please login.');
            location.reload();
        } else {
            const error = await response.json();
            alert(`Registration failed: ${error.message}`);
        }
    } catch (error) {
        console.error('Registration error:', error);
        alert('An error occurred. Please try again.');
    }
});
```

---

## 5. Step 3: チャンネル一覧画面

### 5.1 channels.html

**ファイル**: `src/main/resources/templates/channels.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Channels - MiniSlack</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link th:href="@{/css/style.css}" rel="stylesheet">
</head>
<body>
    <nav class="navbar navbar-dark bg-primary">
        <div class="container-fluid">
            <span class="navbar-brand">MiniSlack</span>
            <span class="text-white" th:text="${user.displayName.value}">User</span>
        </div>
    </nav>

    <div class="container-fluid mt-3">
        <div class="row">
            <!-- サイドバー -->
            <div class="col-md-3">
                <h5>My Channels</h5>
                <div class="list-group">
                    <a th:each="channel : ${myChannels}" 
                       th:href="@{/channels/{id}/chat(id=${channel.channelId.value}, userId=${user.userId.value})}"
                       class="list-group-item list-group-item-action">
                        <strong th:text="${channel.channelName.value}">Channel Name</strong>
                        <br>
                        <small class="text-muted" th:text="${channel.description.value}">Description</small>
                    </a>
                </div>
                
                <h5 class="mt-4">Public Channels</h5>
                <div class="list-group">
                    <div th:each="channel : ${publicChannels}" class="list-group-item">
                        <strong th:text="${channel.channelName.value}">Channel Name</strong>
                        <button class="btn btn-sm btn-primary float-end"
                                th:onclick="'joinChannel(\'' + ${channel.channelId.value} + '\')'">
                            Join
                        </button>
                    </div>
                </div>
                
                <button class="btn btn-success w-100 mt-3" data-bs-toggle="modal" data-bs-target="#createChannelModal">
                    Create Channel
                </button>
            </div>
            
            <!-- メインコンテンツ -->
            <div class="col-md-9">
                <div class="alert alert-info">
                    Select a channel to start chatting!
                </div>
            </div>
        </div>
    </div>

    <!-- チャンネル作成モーダル -->
    <div class="modal fade" id="createChannelModal" tabindex="-1">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title">Create Channel</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body">
                    <form id="createChannelForm">
                        <div class="mb-3">
                            <label for="channelName" class="form-label">Channel Name</label>
                            <input type="text" class="form-control" id="channelName" required>
                        </div>
                        <div class="mb-3">
                            <label for="channelDescription" class="form-label">Description</label>
                            <textarea class="form-control" id="channelDescription" rows="3"></textarea>
                        </div>
                        <div class="mb-3 form-check">
                            <input type="checkbox" class="form-check-input" id="isPublic" checked>
                            <label class="form-check-label" for="isPublic">Public Channel</label>
                        </div>
                        <button type="submit" class="btn btn-primary w-100">Create</button>
                    </form>
                </div>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script th:inline="javascript">
        const userId = [[${user.userId.value}]];
        
        // チャンネル参加
        async function joinChannel(channelId) {
            try {
                const response = await fetch(`/api/v1/channels/${channelId}/join`, {
                    method: 'POST',
                    headers: { 'X-User-Id': userId }
                });
                
                if (response.ok) {
                    location.reload();
                } else {
                    alert('Failed to join channel');
                }
            } catch (error) {
                console.error('Error:', error);
            }
        }
        
        // チャンネル作成
        document.getElementById('createChannelForm')?.addEventListener('submit', async (e) => {
            e.preventDefault();
            
            const channelName = document.getElementById('channelName').value;
            const description = document.getElementById('channelDescription').value;
            const isPublic = document.getElementById('isPublic').checked;
            
            try {
                const response = await fetch('/api/v1/channels', {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        'X-User-Id': userId 
                    },
                    body: JSON.stringify({ channelName, description, isPublic })
                });
                
                if (response.ok) {
                    location.reload();
                } else {
                    alert('Failed to create channel');
                }
            } catch (error) {
                console.error('Error:', error);
            }
        });
    </script>
</body>
</html>
```

**学習ポイント**:
- ✅ **Thymeleaf構文**: `th:text`, `th:each`, `th:href`, `th:onclick`
- ✅ **Bootstrap**: レスポンシブUI
- ✅ **th:inline="javascript"**: ThymeleafからJavaScriptに値を渡す

---

## 6. Step 4: チャット画面

### 6.1 chat.html

**ファイル**: `src/main/resources/templates/chat.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chat - MiniSlack</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <style>
        #messageList {
            height: 500px;
            overflow-y: auto;
            border: 1px solid #ddd;
            padding: 15px;
            background-color: #f8f9fa;
        }
        .message {
            margin-bottom: 15px;
            padding: 10px;
            border-radius: 5px;
            background-color: white;
        }
        .message-header {
            font-weight: bold;
            margin-bottom: 5px;
        }
        .message-time {
            font-size: 0.8em;
            color: #6c757d;
        }
    </style>
</head>
<body>
    <nav class="navbar navbar-dark bg-primary">
        <div class="container-fluid">
            <a class="navbar-brand" th:href="@{/channels(userId=${user.userId.value})}">← Back to Channels</a>
            <span class="text-white" th:text="${user.displayName.value}">User</span>
        </div>
    </nav>

    <div class="container-fluid mt-3">
        <div class="row">
            <div class="col-md-12">
                <!-- メッセージ一覧 -->
                <div id="messageList">
                    <div th:each="message : ${messages}" class="message">
                        <div class="message-header">
                            <span th:text="${message.userId.value}">User</span>
                            <span class="message-time" th:text="${#temporals.format(message.createdAt, 'yyyy-MM-dd HH:mm:ss')}">
                                2025-11-01 12:00:00
                            </span>
                        </div>
                        <div th:text="${message.content.value}">Message content</div>
                    </div>
                </div>

                <!-- メッセージ入力フォーム -->
                <form id="messageForm" class="mt-3">
                    <div class="input-group">
                        <input type="text" class="form-control" id="messageContent" 
                               placeholder="Type a message..." required>
                        <button class="btn btn-primary" type="submit">Send</button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script th:inline="javascript">
        const userId = [[${user.userId.value}]];
        const channelId = [[${channelId}]];

        // メッセージ送信
        document.getElementById('messageForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            
            const content = document.getElementById('messageContent').value;
            
            try {
                const response = await fetch(`/api/v1/channels/${channelId}/messages`, {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        'X-User-Id': userId 
                    },
                    body: JSON.stringify({ content })
                });
                
                if (response.ok) {
                    document.getElementById('messageContent').value = '';
                    loadMessages(); // メッセージを再読み込み
                } else {
                    alert('Failed to send message');
                }
            } catch (error) {
                console.error('Error:', error);
            }
        });

        // メッセージ一覧読み込み
        async function loadMessages() {
            try {
                const response = await fetch(`/api/v1/channels/${channelId}/messages?limit=50&offset=0`, {
                    headers: { 'X-User-Id': userId }
                });
                
                if (response.ok) {
                    const messages = await response.json();
                    displayMessages(messages);
                }
            } catch (error) {
                console.error('Error:', error);
            }
        }

        // メッセージ表示
        function displayMessages(messages) {
            const messageList = document.getElementById('messageList');
            messageList.innerHTML = '';
            
            // 古い順に表示（配列を逆順に）
            messages.reverse().forEach(msg => {
                const messageDiv = document.createElement('div');
                messageDiv.className = 'message';
                
                const headerDiv = document.createElement('div');
                headerDiv.className = 'message-header';
                headerDiv.innerHTML = `
                    <span>${msg.userId}</span>
                    <span class="message-time">${new Date(msg.createdAt).toLocaleString()}</span>
                `;
                
                const contentDiv = document.createElement('div');
                contentDiv.textContent = msg.content;
                
                messageDiv.appendChild(headerDiv);
                messageDiv.appendChild(contentDiv);
                messageList.appendChild(messageDiv);
            });
            
            // 最新メッセージまでスクロール
            messageList.scrollTop = messageList.scrollHeight;
        }

        // 5秒ごとに新しいメッセージをポーリング
        // 注: 5秒間隔は学習用として設定（シンプルで理解しやすい）
        // 本番環境では負荷テストに基づいて調整が必要
        setInterval(loadMessages, 5000);
        
        // 初回読み込み
        loadMessages();
    </script>
</body>
</html>
```

**学習ポイント**:
- ✅ **Fetch API**: REST API呼び出し
- ✅ **ポーリング**: 5秒ごとに新規メッセージチェック
- ✅ **動的DOM操作**: メッセージを動的に追加

---

## 7. Step 5: CSSスタイル

### 7.1 style.css

**ファイル**: `src/main/resources/static/css/style.css`

```css
body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.navbar-brand {
    font-weight: bold;
    font-size: 1.5rem;
}

.list-group-item {
    cursor: pointer;
}

.list-group-item:hover {
    background-color: #f0f0f0;
}

#messageList {
    display: flex;
    flex-direction: column;
}

.message {
    animation: fadeIn 0.3s;
}

@keyframes fadeIn {
    from { opacity: 0; transform: translateY(10px); }
    to { opacity: 1; transform: translateY(0); }
}
```

---

## 8. 動作確認

### 8.1 アクセス

1. ブラウザで`http://localhost:8080`にアクセス
2. 「Create an account」をクリック
3. ユーザー登録
4. ログイン
5. チャンネル一覧が表示される
6. 「Create Channel」でチャンネル作成
7. チャンネルをクリックしてチャット画面へ
8. メッセージ送信

### 8.2 複数ユーザーでのテスト

1. ブラウザAでAliceとしてログイン
2. ブラウザBでBobとしてログイン
3. 同じチャンネルに参加
4. Aliceがメッセージ送信
5. 5秒以内にBobの画面に表示される（ポーリング）

---

## 9. まとめ

### 9.1 実装したもの

- ✅ ログイン画面
- ✅ ユーザー登録機能
- ✅ チャンネル一覧画面
- ✅ チャンネル作成機能
- ✅ チャット画面
- ✅ リアルタイム更新（ポーリング方式）

### 9.2 学んだこと

- ✅ **Thymeleaf**: サーバーサイドテンプレート
- ✅ **Bootstrap**: レスポンシブUI
- ✅ **Fetch API**: REST API呼び出し
- ✅ **ポーリング**: 定期的なデータ取得

### 9.3 次のステップ

Web UIが完成しました！次はテスト戦略を学びます：

- [16-testing.md](16-testing.md) - テスト実装

---

## 10. 発展：WebSocket実装（将来）

現在はポーリング方式（5秒ごとにチェック）ですが、WebSocketを使えば真のリアルタイムが実現できます：

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }
}
```

```javascript
// WebSocket接続
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, () => {
    stompClient.subscribe(`/topic/channel/${channelId}`, (message) => {
        const newMessage = JSON.parse(message.body);
        displayNewMessage(newMessage);
    });
});
```

---

## 11. 参考資料

- [Thymeleaf](https://www.thymeleaf.org/)
- [Bootstrap 5](https://getbootstrap.com/)
- [WebSocket with Spring](https://spring.io/guides/gs/messaging-stomp-websocket)

