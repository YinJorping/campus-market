# CampusMarket 核心功能验收标准

---

## 功能一：发布商品

### 正向用例

| 项目 | 内容 |
|------|------|
| **Given** | 用户已登录，token 有效 |
| **When** | 提交完整商品信息：名称《课本》、价格 10.00 元、分类"图书教材"、图片列表、成色 3、校区、交易地点 |
| **Then** | 接口返回 `success:true`，data 为新商品 ID |

**数据验证：**
- `tb_item` 新增一条记录
- `status = 1`（ON_SALE / 在售）
- `price = 1000`（单位：分）
- `audit_status = 1`（APPROVED，当前审核开关关闭）
- `deleted = 0`
- Redis：`geo:items` ZSet 中新增该商品坐标
- 首页列表（`GET /api/items`）可刷到该商品
- 搜索/按时间排序可查找到

### 异常用例

| # | Given | When | Then |
|---|-------|------|------|
| 1 | 已登录 | 商品名称为空 | 返回 `400`，"商品名称不能为空" |
| 2 | 已登录 | 未上传图片 | 返回 `400`，"请上传至少一张商品图片" |
| 3 | 已登录 | 价格为 0 或负数 | 返回 `400`，"价格必须大于0" |
| 4 | 已登录 | 未选分类 | 返回 `400`，"请选择商品分类" |
| 5 | 已登录 | 未选成色 | 返回 `400`，"请选择商品成色" |
| 6 | 已登录 | 未填校区/交易地点 | 返回 `400`，提示必填 |
| 7 | 未登录 | 提交完整信息 | 返回 `401`，无权限 |
| 8 | 已登录 | 不提供坐标 (x/y) | 商品正常发布，但不加入 GEO 索引 |
| 9 | 已登录 | 价格超过 999999.99 元 | 返回 `400`，超出范围 |

> **修正说明：** 原稿写"状态变为0（在售）"，实际 `ItemStatus.ON_SALE = 1`。状态 0 不存在。

---

## 功能二：下单（创建订单）

### 正向用例

| 项目 | 内容 |
|------|------|
| **Given** | 买家已登录（非商品卖家本人）；目标商品存在且 `status = 1`（在售）；商品审核通过且未删除 |
| **When** | 买家点击"我想要"，提交 `POST /api/orders { "itemId": xxx }` |
| **Then** | 接口返回 `success:true`，data 为新订单 ID |

**数据验证：**
- `tb_order` 新增一条记录
- `status = 1`（PENDING / **待确认**，非"待联系"）
- `price = 下单时商品价格`（价格快照，单位：分）
- `buyer_id` = 当前用户 ID
- `seller_id` = 商品发布者 ID
- `tb_item.status` 原子更新为 `2`（RESERVED / 已预约）
- Redis：`geo:items` 移除该商品（已预约的商品不再出现在附近搜索）
- 卖家通过 `GET /api/orders/seller` 可查到该"待处理"订单

### 异常用例

| # | Given | When | Then |
|---|-------|------|------|
| 1 | 买家已登录 | 下单自己的商品 | 返回 `400`，"不能预约自己的商品" |
| 2 | 买家已登录 | 商品不存在 | 返回 `404`，"商品不存在" |
| 3 | 买家已登录 | 商品已被他人预约（status≠1） | 返回 `400`，"商品已被预约" |
| 4 | 买家已登录 | 商品已下架/已售/已删除 | 返回 `400`，提示商品不可预约 |
| 5 | 未登录 | 下单 | 返回 `401`，无权限 |
| 6 | 两个买家并发 | 同时下单同一商品 | 仅 1 人成功，另一人返回"商品已被预约" |
| 7 | 买家已登录 | itemId 为空 | 返回 `400`，"商品ID不能为空" |

> **修正说明：**
> 1. 原稿状态描述"1（待联系）"有误，正确为 `OrderStatus.PENDING = 1 = 待确认`。
> 2. 原稿"填写"不完整——下单接口仅需 `itemId`，不需要额外填写内容。
> 3. 原稿"卖家个人中心出现通知"是前端展示逻辑，后端验收标准应为：卖家可通过查询接口获取该订单。

---

## 接口清单

| 功能 | Method | Path | 说明 |
|------|:------:|------|------|
| 发布商品 | POST | `/api/items` | Body: SaveItemDTO |
| 商品列表 | GET | `/api/items` | 匿名，支持分页/筛选/排序 |
| 商品详情 | GET | `/api/items/{id}` | 匿名，含收藏状态 |
| 下单 | POST | `/api/orders` | Body: `{ "itemId": xxx }` |
| 卖家订单列表 | GET | `/api/orders/seller` | 需登录 |
| 买家订单列表 | GET | `/api/orders/buyer` | 需登录 |

---

## 数据模型（核心字段）

### tb_item

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | PK |
| seller_id | bigint | 卖家用户ID |
| name | varchar(30) | 商品名称 |
| price | bigint | **价格（分）**，如 1000 = 10.00 元 |
| images | varchar(2048) | 图片URL，逗号分隔 |
| category_id | int | 分类ID |
| status | tinyint | 1=在售 2=已预约 3=已售 |
| audit_status | tinyint | 0=待审核 1=审核通过（当前默认） |
| deleted | tinyint | 0=正常 1=逻辑删除 |
| x / y | double | 经纬度坐标 |

### tb_order

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | PK |
| item_id | bigint | 商品ID |
| buyer_id | bigint | 买家用户ID |
| seller_id | bigint | 卖家用户ID |
| price | bigint | **下单时价格快照（分）** |
| status | tinyint | 1=待确认 2=已确认 3=已完成 4=已拒绝 5=已取消 |

---

## 测试策略

| 层级 | 覆盖内容 | 工具 |
|------|---------|------|
| 单元测试 | Mapper 层 CRUD、Service 层业务逻辑 | JUnit 5 + Mockito |
| 集成测试 | Controller 层全链路、并发场景 | JUnit 5 + TestRestTemplate + CountDownLatch |
| 部署验证 | Docker Compose 一键启动 / API 冒烟测试 | Docker + curl |

---

## 环境速查

| 项目 | 值 |
|------|-----|
| 应用端口 | 8081 |
| MySQL 端口 | 3306，数据库 hmdp |
| Redis 端口 | 6379，密码 123321 |
| SQL 初始化 | `src/main/resources/db/` |
