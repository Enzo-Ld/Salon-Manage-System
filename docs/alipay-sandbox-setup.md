# BFSM 支付宝沙箱接入说明

## 目标

将 BFSM 的支付模式从 `MANUAL_CASHIER` 切换到 `ALIPAY_PAGE`，通过支付宝沙箱或开放平台页面完成真实支付联调。

## 当前已支持能力

系统当前已经具备这些能力：

1. 后端可生成支付宝页面支付表单。
2. 前端统一收银台可打开支付宝页面。
3. 后端支持主动查询支付宝支付状态。
4. 后端支持 `/api/payments/alipay/notify` 异步回调验签。
5. 支付宝完成后可回跳到前端支付结果页 `/payment/result`。
6. 商品订单、服务预约、钱包充值三条链路都可复用该支付流程。

## 需要准备的配置

你需要准备以下参数：

1. `APP_ID`
2. 商户应用私钥 `RSA2`
3. 支付宝公钥
4. 可被支付宝访问的 `notify_url`
5. 前端回跳页 `return_url`

## application.yml 中的配置项

项目中已经预留好了配置：

```yml
bfsm:
  payment:
    mode: ${BFSM_PAYMENT_MODE:MANUAL_CASHIER}
    manual-completion-enabled: ${BFSM_PAYMENT_MANUAL_COMPLETION_ENABLED:true}
    alipay:
      gateway-url: ${BFSM_ALIPAY_GATEWAY_URL:https://openapi-sandbox.dl.alipaydev.com/gateway.do}
      app-id: ${BFSM_ALIPAY_APP_ID:}
      merchant-private-key: ${BFSM_ALIPAY_PRIVATE_KEY:}
      alipay-public-key: ${BFSM_ALIPAY_PUBLIC_KEY:}
      notify-url: ${BFSM_ALIPAY_NOTIFY_URL:}
      return-url: ${BFSM_ALIPAY_RETURN_URL:http://localhost:5173/payment/result}
      sign-type: ${BFSM_ALIPAY_SIGN_TYPE:RSA2}
      charset: ${BFSM_ALIPAY_CHARSET:UTF-8}
```

## 推荐的本地环境变量

```powershell
$env:BFSM_PAYMENT_MODE="ALIPAY_PAGE"
$env:BFSM_PAYMENT_MANUAL_COMPLETION_ENABLED="false"
$env:BFSM_ALIPAY_APP_ID="你的沙箱APP_ID"
$env:BFSM_ALIPAY_PRIVATE_KEY="你的应用私钥"
$env:BFSM_ALIPAY_PUBLIC_KEY="支付宝公钥"
$env:BFSM_ALIPAY_NOTIFY_URL="https://你的公网域名/api/payments/alipay/notify"
$env:BFSM_ALIPAY_RETURN_URL="http://localhost:5173/payment/result"
```

## 模式说明

- `MANUAL_CASHIER`
  使用项目内统一收银台演示支付，支持手动确认成功或取消。

- `ALIPAY_PAGE`
  使用支付宝页面支付。前端收银台会打开支付宝页面，状态通过主动查询和异步回调同步。

## 联调建议步骤

1. 设置环境变量，并以 `ALIPAY_PAGE` 模式启动后端。
2. 使用内网穿透或测试域名暴露 `notify_url`。
3. 在前端发起商品订单支付、预约支付或钱包充值。
4. 收银台打开支付宝页面后，使用沙箱账号完成支付。
5. 支付完成后，观察 `/payment/result` 页面和原业务页面的状态是否同步。

## 常见问题

### 1. 仍然出现“Confirm Paid”按钮

说明当前仍在手动模式，检查：

- `BFSM_PAYMENT_MODE` 是否为 `ALIPAY_PAGE`
- `BFSM_PAYMENT_MANUAL_COMPLETION_ENABLED` 是否为 `false`

### 2. 支付宝页面打不开

优先检查：

- `APP_ID` 是否正确
- 私钥和支付宝公钥是否完整
- 网关地址是否使用了沙箱地址
- 浏览器是否拦截了弹窗

### 3. 支付完成但本地状态没有变化

优先检查：

- `notify_url` 是否可被支付宝访问
- 后端日志中是否收到了 `/api/payments/alipay/notify`
- 回调验签是否成功
- 前端支付结果页是否可以正常刷新状态
