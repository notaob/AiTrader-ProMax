# T2 端到端验证：Servlet 异步请求下 ThreadLocal 是否残留（串号 / 越权）
#
# 背景
#   用户身份存于 BaseContext 的 ThreadLocal，由 JwtInterceptor 在 afterCompletion 清理。
#   但 /ai/conversations/{id}/chat/stream 返回 SseEmitter，请求进入 Servlet 异步模式，
#   此时 afterCompletion 不保证在"设置值的那个 Servlet 线程"上执行 —— 线程会带着上一位
#   用户的 userId 回到 Tomcat 线程池。而 /moments/list 被放行匿名访问且会读取
#   BaseContext.getCurrentId() 计算 isLiked，一旦复用到残留线程就会读到他人身份。
#
# 前置
#   1) 后端已启动（默认 http://localhost:8080）
#   2) 用来登录的账号至少给一条动态点过赞（否则 isLiked 恒为 false，观测不到差异）
#
# 用法
#   .\verify-t2-threadlocal.ps1 -Email you@example.com -Password yourpwd

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Email = "",
    [string]$Password = "",
    [int]$Rounds = 100
)

$ErrorActionPreference = "Stop"

if (-not $Email -or -not $Password) {
    throw "请提供 -Email 与 -Password（该账号需至少给一条动态点过赞，才能观测到 isLiked 变化）"
}

# ---------- 1) 登录，取得 token ----------
$loginBody = @{ username = $Email; email = $Email; password = $Password } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "${BaseUrl}/user/login/password" -Method Post -ContentType "application/json" -Body $loginBody
$token = $login.data.token
if (-not $token) {
    throw "登录失败，返回：$($login | ConvertTo-Json -Compress)"
}
Write-Host "[1/4] 登录成功" -ForegroundColor Green

# ---------- 2) 创建会话（供 SSE 请求使用） ----------
$convBody = @{ title = "T2 验证"; sceneType = "chat" } | ConvertTo-Json
$conv = Invoke-RestMethod -Uri "${BaseUrl}/ai/conversations" -Method Post -ContentType "application/json" -Headers @{ Authorization = "Bearer $token" } -Body $convBody
$convId = $conv.data.id
if (-not $convId) {
    throw "创建会话失败，返回：$($conv | ConvertTo-Json -Compress)"
}
Write-Host "[2/4] 会话已创建 id=$convId" -ForegroundColor Green

# ---------- 3) 后台发起 SSE 请求：让请求进入异步模式且 ThreadLocal 已写入 ----------
$job = Start-Job -ScriptBlock {
    param($u, $t, $c)
    try {
        Invoke-WebRequest -Uri "$u/ai/conversations/$c/chat/stream" -Method Post -ContentType "application/json" -Headers @{ Authorization = "Bearer $t" } -Body '{"message":"hi","mode":"chat"}' -TimeoutSec 20 | Out-Null
    } catch {
        # SSE 流被超时中断属预期，忽略
    }
} -ArgumentList $BaseUrl, $token, $convId

Write-Host "[3/4] 已发起 SSE 请求（异步，Servlet 线程已写入 ThreadLocal）" -ForegroundColor Green

# ---------- 4) 连续匿名请求 /moments/list，统计是否读到他人点赞状态 ----------
Start-Sleep -Milliseconds 500

$leaked = 0
$errors = 0
for ($i = 1; $i -le $Rounds; $i++) {
    try {
        $resp = Invoke-RestMethod -Uri "${BaseUrl}/moments/list?page=1&size=10" -Method Get
        $hit = 0
        foreach ($m in $resp.data) {
            if ($m.isLiked -eq $true) { $hit++ }
        }
        if ($hit -gt 0) { $leaked++ }
    } catch {
        $errors++
    }
}

Remove-Job -Job $job -Force -ErrorAction SilentlyContinue

$color = "Green"
if ($leaked -gt 0) { $color = "Red" }
Write-Host "[4/4] 匿名请求 $Rounds 次（失败 $errors 次），读到他人点赞状态的次数 = $leaked" -ForegroundColor $color

if ($leaked -eq 0) {
    Write-Host "PASS：未观测到身份残留，ThreadLocal 清理生效。" -ForegroundColor Green
    exit 0
} else {
    Write-Host "FAIL：仍存在身份残留，说明 ThreadLocal 未在 Servlet 线程上清理干净。" -ForegroundColor Red
    exit 1
}
