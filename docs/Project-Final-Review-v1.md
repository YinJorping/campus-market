# Project Final Review — CampusMarket v1.0

> 校园二手交易平台 · 最终架构评审文档  
> 版本：v1.0 · 日期：2026-07-12 · 状态：Final

---

## 1. 项目总体架构

### 1.1 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                    Browser / App                         │
├─────────────────────────────────────────────────────────┤
│              Spring Boot (Tomcat :8081)                  │
│  ┌──────────────────────┬─────────────────────────────┐ │
│  │   com.hmdp (旧模块)    │   com.campus (新业务模块)     │ │
│  │   · 用户/登录           │   · 分类浏览                  │ │
│  │   · 商户/优惠券         │   · 商品发布/浏览/搜索         │ │
│  │   · 博客/评论           │   · 收藏                      │ │
│  │   · 关注/签到           │   · 订单/预约 (CAS)           │ │
│  │                        │   · GEO 附近商品              │ │
│  └──────────────────────┴─────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────┐ │
│  │              共享基础设施层                            │ │
│  │  · UserHolder (ThreadLocal) · CacheClient (缓存)    │ │
│  │  · RedisIdWorker (分布式ID) · LoginInterceptor       │ │
│  │  · SimpleRedisLock · Redisson · PasswordEncoder     │ │
│  └─────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────┤
│        MySQL 8.0 (hmdp)          Redis 6.2 (127.0.0.1)  │
└─────────────────────────────────────────────────────────┘
```

### 1.2 架构决策记录 (ADR)

| 决策 | 选择 | 理由 |
|------|------|------|
| 后端框架 | Spring Boot 2.3.12 | 已存在项目，稳定可靠 |
| ORM | MyBatis-Plus 3.4.3 | Lambda 查询、分页插件、代码生成 |
| 缓存 | Redis + Cache-Aside | 旁路缓存模式，CacheClient 封装穿透/击穿保护 |
| 并发控制 | CAS (Compare-And-Swap) | UPDATE WHERE status=expected，无锁乐观并发 |
| 分布式锁 | Redisson 3.13.6 | 用于原项目秒杀模块 |
| 认证 | Token (Redis) + ThreadLocal | 无状态 Session 替代方案 |
| 包结构 | com.hmdp + com.campus 双包 | 旧代码保留，新业务隔离 |
| API 鉴权 | PublicUrls 白名单 + Controller 层手动鉴权 | /api/items/** 匿名可读，写操作需登录 |

---

## 2. 模块划分（Sprint 1 ~ 8）

| Sprint | 模块 | 核心交付 | 状态 |
|--------|------|----------|------|
| S1 | 数据模型 | 6 个 CampusMarket 实体、6 个枚举、campus_market.sql | Closed |
| S2 | Mapper 层 | 6 个 Mapper 接口 (extends BaseMapper) | Closed |
| S3 | 分类模块 | CategoryController、CategoryServiceImpl、CategoryVO | Closed |
| S4 | 物品发布 | ItemController (CRUD)、SaveItemDTO、UpdateItemDTO、@Valid 校验、审核开关 | Closed |
| S5 | 商品浏览 | 列表(分页+筛选+排序+搜索)、详情(缓存+浏览计数)、CacheClient 防穿透 | Closed |
| S6 | 收藏模块 | FavoriteController、幂等收藏/取消、静默过滤、isFavorite 透出 | Closed |
| S7 | 预约/订单 | OrderController、CAS 状态机(5 种状态流转)、价格快照、GEO 联动 | Closed |
| S8 | GEO 附近商品 | Redis GEO 存储/搜索、启动预热、CRUD/订单 GEO 同步 | Closed |

---

## 3. 技术栈总结

| 分层 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Java | 21 | JDK 21 |
| 框架 | Spring Boot | 2.3.12.RELEASE | 内嵌 Tomcat 9.0.46 |
| ORM | MyBatis-Plus | 3.4.3 | LambdaWrapper + 分页插件 |
| 数据库 | MySQL | 8.0 | HikariCP 连接池 |
| 缓存 | Redis | 6.2 | Lettuce 客户端 + Redisson 3.13.6 |
| 工具库 | Hutool | (项目依赖) | StrUtil、BeanUtil、RandomUtil |
| 构建 | Maven | 3.x | 多模块编译 |
| 校验 | Jakarta Validation | (Spring Boot 内置) | @Valid + DTO 注解 |

### JVM 启动参数

MyBatis-Plus 3.4.3 在 JDK 21 上需要的反射打开参数：

```
--add-opens java.base/java.lang.invoke=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
```

---

## 4. 数据库设计

### 4.1 ER 图（核心表关系）

```
tb_user ──┐                              tb_category
  id (PK) │                              ┌ id (PK)
  phone    │    tb_item                  │ name
  nickName ├── sellerId (FK) ──────────┤ │ icon
  icon     │    ┌ id (PK)               │ │ sort
           │    │ name                   │
           │    │ categoryId (FK) ──────┘
           │    │ sellerId (FK) ── tb_user.id
           │    │ images
           │    │ price (分)
           │    │ originalPrice (分)
           │    │ campus / meetPlace
           │    │ x / y (经纬度)
           │    │ itemCondition (1-5)
           │    │ status (1=在售/2=已预约/3=已售/4=下架)
           │    │ auditStatus (0=待审/1=通过/2=拒绝)
           │    │ deleted (逻辑删除)
           │    │ viewCount / consultCount
           │    │
           │    tb_favorite (用户-商品 多对多)
           │    ┌ userId (FK) ── tb_user.id
           │    │ itemId (FK) ── tb_item.id
           │    │ UNIQUE(userId, itemId)
           │    │
           │    tb_order
           └── buyerId (FK) ────────────┐
                ┌ id (PK)               │
                │ itemId (FK) ── tb_item.id
                │ price (下单时价格快照)
                │ sellerId (FK) ────────┘
                │ buyerId (FK)
                │ status (1=待确认/2=已确认/3=已完成/4=已拒绝/5=已取消)
                │
                tb_credit_record (预留)
                ┌ userId (FK)
                │ type / score / refId
                │
                tb_report (预留)
                ┌ reporterId / targetType / targetId / reason
```

### 4.2 表统计

**CampusMarket 新增表（6 张）：**

| 表名 | 行数约 | 说明 |
|------|--------|------|
| tb_category | ~10 | 商品分类 |
| tb_item | ~10 | 商品主表 |
| tb_favorite | ~5 | 收藏关系 |
| tb_order | ~5 | 预约订单 |
| tb_credit_record | 0 | 信誉分记录（预留） |
| tb_report | 0 | 举报记录（预留） |

**原 hmdp 项目表（10 张）：** tb_user、tb_user_info、tb_shop、tb_shop_type、tb_voucher、tb_seckill_voucher、tb_voucher_order、tb_blog、tb_blog_comments、tb_follow（保留向后兼容）

### 4.3 核心设计要点

- **价格单位**：所有价格以「分」存储（`bigint`），避免浮点精度问题
- **逻辑删除**：Item 使用 `deleted` 标记，查询均带 `deleted=0`
- **价格快照**：Order.price 记录下单瞬间的商品价格，后续商品调价不影响历史订单
- **索引**：item 表对 categoryId、sellerId、status/campus/price 建立联合查询索引；favorite 表对 (userId, itemId) 建唯一约束

---

## 5. Redis 使用总结

### 5.1 Key 空间设计

| Key Pattern | 类型 | TTL | 用途 | Sprint |
|-------------|------|-----|------|--------|
| `login:code:{phone}` | String | 2 min | 短信验证码 | 原项目 |
| `login:token:{token}` | Hash | 10 h | 用户登录态 | 原项目 |
| `cache:item:{id}` | String(JSON) | 30 min | 商品详情缓存 (Cache-Aside) | S5 |
| `cache:item:null:{id}` | String | 2 min | 空值缓存 (防穿透) | S5 |
| `geo:items` | GEO | 无过期 | 在售商品经纬度索引 | S8 |
| `cache:shop:{id}` | String | 30 min | 商户缓存 | 原项目 |
| `seckill:stock:{id}` | String | — | 秒杀库存 | 原项目 |
| `blog:liked:{id}` | Set | — | 博客点赞 | 原项目 |
| `feed:{userId}` | ZSet | — | 关注 Feed 流 | 原项目 |

### 5.2 缓存模式

**Cache-Aside (旁路缓存)** — 商品详情：

```
读: CacheClient.queryWithPassThrough()
    1. 查 Redis (cache:item:{id})
    2. 命中 → 返回
    3. 未命中 → 查 DB
    4. DB 有 → 写 Redis (TTL 30min) → 返回
    5. DB 无 → 写空值缓存 (TTL 2min) → 防穿透

写: updateItem() / deleteItem()
    1. 更新 DB
    2. 删除 Redis key (cache:item:{id})
    → 下次读重建缓存
```

**GEO 空间索引** — 附近商品：

```
写 (同步):
    createItem → GEOADD geo:items x y itemId
    updateItem → GEOADD geo:items x y itemId
    deleteItem → ZREM geo:items itemId
    createOrder → ZREM geo:items itemId (已预约)
    cancelOrder / rejectOrder → GEOADD geo:items x y itemId (恢复在售)

读:
    nearbyItems → GEOSEARCH geo:items FROMLONLAT x y BYRADIUS radius

启动:
    @EventListener(ApplicationReadyEvent) → 全量加载 ON_SALE + APPROVED 商品坐标到 GEO
```

### 5.3 Redis 技术能力映射

| Redis 能力 | v1.0 使用 | v2 规划 |
|------------|----------|---------|
| String | 缓存 (JSON)、验证码 | — |
| Hash | 用户登录态 | 购物车 |
| GEO | 附近商品 (S8) | — |
| ZSet | (GEO 底层) | 热度排行榜、Feed 流 |
| Set | — | 点赞去重 |
| BitMap | — | 签到统计 |
| HyperLogLog | — | 商品 UV |
| Lua | — | 秒杀 |
| Stream | — | 异步订单 |
| Redisson Lock | — | 预约排他锁 |

---

## 6. API 总览

### 6.1 CampusMarket 接口 (22 个)

| 方法 | 路径 | 说明 | 鉴权 | Sprint |
|------|------|------|------|--------|
| GET | `/api/categories` | 分类列表 | 匿名 | S3 |
| GET | `/api/items` | 商品列表 (分页/筛选/搜索) | 匿名 | S5 |
| GET | `/api/items/nearby` | 附近商品 (GEO) | 匿名 | S8 |
| GET | `/api/items/{id}` | 商品详情 (isFavorite) | 匿名* | S5 |
| POST | `/api/items` | 发布商品 | 需登录 | S4 |
| PUT | `/api/items/{id}` | 修改商品 | 需登录 | S4 |
| DELETE | `/api/items/{id}` | 删除商品 (逻辑) | 需登录 | S4 |
| POST | `/api/items/{id}/favorite` | 收藏商品 | 需登录 | S6 |
| DELETE | `/api/items/{id}/favorite` | 取消收藏 | 需登录 | S6 |
| GET | `/api/users/me/favorites` | 我的收藏 | 需登录 | S6 |
| POST | `/api/orders` | 创建订单 | 需登录 | S7 |
| GET | `/api/orders/buyer` | 我买的 | 需登录 | S7 |
| GET | `/api/orders/seller` | 我卖的 | 需登录 | S7 |
| GET | `/api/orders/{id}` | 订单详情 | 需登录 | S7 |
| PUT | `/api/orders/{id}/confirm` | 确认订单 | 需登录 | S7 |
| PUT | `/api/orders/{id}/reject` | 拒绝订单 | 需登录 | S7 |
| PUT | `/api/orders/{id}/cancel` | 取消订单 | 需登录 | S7 |
| PUT | `/api/orders/{id}/complete` | 完成订单 | 需登录 | S7 |

> *匿名可访问，但 isFavorite 在未登录时返回 false

### 6.2 原 hmdp 项目接口 (保留，约 40 个)

用户、商户、优惠券、秒杀、博客、评论、关注、签到等功能完整保留。

### 6.3 鉴权机制

```
请求 → RefreshTokenInterceptor (order=0, path=/**)
         → 提取 authorization header → Redis 查 token → 写 ThreadLocal
     → LoginInterceptor (order=1, exclude=PublicUrls)
         → 检查 ThreadLocal 是否为空 → 空则 401
     → Controller
         → 白名单内的写接口手动检查 UserHolder.getUser()
```

`/api/items/**` 在 PublicUrls 白名单中（GET 匿名可读），POST/PUT/DELETE 在 Controller 层手动鉴权。

---

## 7. 核心业务流程

### 7.1 商品发布 → 浏览 → 交易 完整链路

```
卖家                         平台                       买家
 │                            │                          │
 ├─ POST /api/items ────────►│                          │
 │  (填写信息+坐标)            │                          │
 │                            ├─ INSERT tb_item          │
 │                            ├─ GEOADD geo:items        │
 │                            │                          │
 │                            │◄─ GET /api/items ───────┤
 │                            │   (浏览/搜索/筛选)        │
 │                            │                          │
 │                            │◄─ GET /api/items/{id} ──┤
 │                            │   (详情+缓存+浏览计数)     │
 │                            │                          │
 │                            │◄─ POST /favorite ───────┤
 │                            │   (收藏)                  │
 │                            │                          │
 │                            │◄─ POST /api/orders ─────┤
 │                            │   (预约)                  │
 │                            ├─ CAS: item ON_SALE→RESERVED
 │                            ├─ ZREM geo:items (下架GEO) │
 │                            ├─ INSERT tb_order (含price快照)
 │                            │                          │
 ├─ GET /api/orders/seller ◄─│                          │
 │  (收到预约通知)              │                          │
 │                            │                          │
 ├─ PUT /confirm ───────────►│                          │
 │  (确认交易)                  │                          │
 │                            ├─ CAS: order PENDING→CONFIRMED
 │                            │                          │
 ├─ PUT /complete ──────────►│                          │
 │  (完成交易)                  │                          │
 │                            ├─ CAS: order CONFIRMED→FINISHED
 │                            ├─ CAS: item RESERVED→SOLD │
 │                            ├─ ZREM geo:items          │
 │                            │                          │
 │                            │    或                     │
 │                            │                          │
 │◄─ PUT /cancel ────────────┤                          │
 │  (买家取消)                  │                          │
 │                            ├─ CAS: order PENDING→CANCELLED
 │                            ├─ CAS: item RESERVED→ON_SALE
 │                            ├─ GEOADD geo:items (恢复)  │
 │                            │                          │
 ├─ PUT /reject ────────────►│                          │
 │  (卖家拒绝)                  │                          │
 │                            ├─ CAS: order PENDING→REJECTED
 │                            ├─ CAS: item RESERVED→ON_SALE
 │                            ├─ GEOADD geo:items (恢复)  │
```

### 7.2 订单状态机

```
                    ┌──────────┐
                    │ PENDING  │ ← 买家下单
                    │  待确认   │
                    └────┬─────┘
              ┌──────────┼──────────┐
        卖家确认│          │卖家拒绝   │买家取消
              ▼          ▼          ▼
        ┌─────────┐ ┌─────────┐ ┌─────────┐
        │CONFIRMED│ │REJECTED │ │CANCELLED│
        │  已确认  │ │  已拒绝  │ │  已取消  │
        └────┬────┘ └─────────┘ └─────────┘
       卖家完成│
              ▼
        ┌─────────┐
        │FINISHED │
        │  已完成  │
        └─────────┘

终止状态: FINISHED, REJECTED, CANCELLED
商品恢复: REJECTED / CANCELLED → item ON_SALE
GEO 恢复: REJECTED / CANCELLED → GEOADD
```

---

## 8. 并发控制 (CAS) 设计

### 8.1 设计原则

所有订单状态流转均采用 **乐观并发控制 (Optimistic Concurrency Control)**，利用数据库行级锁 + WHERE 条件约束，无需分布式锁。

### 8.2 实现模式

```java
// 核心 SQL 模式：UPDATE + WHERE status = expected
LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
wrapper.eq(Order::getId, id)
       .eq(Order::getStatus, expectedStatus)  // ← CAS 条件
       .set(Order::getStatus, newStatus);
boolean updated = update(wrapper);
if (!updated) {
    return Result.fail("订单状态已变更");  // ← 并发冲突
}
```

### 8.3 各操作 CAS 条件矩阵

| 操作 | WHERE 条件 | 失败含义 |
|------|-----------|---------|
| createOrder | `item.status = ON_SALE` | 已被他人预约 |
| confirmOrder | `order.status = PENDING AND order.sellerId = ?` | 状态已变或无权限 |
| rejectOrder | `order.status = PENDING` → then `item.status = RESERVED` | 订单状态已变 |
| cancelOrder | `order.status = PENDING` → then `item.status = RESERVED` | 订单状态已变 |
| completeOrder | `order.status = CONFIRMED` → then `item.status = RESERVED` | 订单状态已变 |

### 8.4 并发安全性分析

| 并发场景 | 安全性 | 机制 |
|---------|--------|------|
| 两人同时预约同一商品 | 安全 | 先到者将 item 改为 RESERVED，后到者 CAS 失败 |
| 卖家确认 + 买家同时取消 | 安全 | 两者都带 status 条件，只有一个成功 |
| 卖家确认 + 卖家同时拒绝 | 不可能 | 同一用户顺序操作 |
| 卖家确认后买家取消 | 安全 | cancel 要求 status=PENDING，已 confirm 的订单无法取消 |

### 8.5 GEO 与订单的并发一致性

GEO 操作在事务内同步执行，遵循「先改数据库，后改缓存」原则：

```
createOrder:
  1. CAS: item ON_SALE → RESERVED
  2. ZREM geo:items            ← 事务内，CAS 成功后立即执行
  3. INSERT order

cancelOrder / rejectOrder:
  1. CAS: order → CANCELLED/REJECTED
  2. CAS: item RESERVED → ON_SALE
  3. GEOADD geo:items          ← 查询 item 坐标后恢复
```

若 GEO 操作失败，数据库已提交的变更不会被回滚，但 GEO 数据会在下次启动时全量修复（`@EventListener(ApplicationReadyEvent)`）。

---

## 9. 项目亮点

### 9.1 架构设计

- **双包共存、渐进式重构**：旧项目 (com.hmdp) 和新增模块 (com.campus) 在同一 JVM 共存，通过 `@MapperScan` + `@ComponentScan` 双包扫描实现零冲突
- **白名单鉴权**：PublicUrls 集中管理匿名路径，Controller 层手动鉴权解决「部分接口部分方法需登录」的混合场景
- **统一 Result 封装**：所有接口返回 `{success, errorMsg, data}`，前端统一处理

### 9.2 并发控制

- **乐观锁 CAS 模式**：5 种订单状态流转全部使用 `UPDATE WHERE status = ?` 实现无锁并发，可靠性等同数据库行级锁但零 Redis 依赖
- **affectedRows 检查**：每次 CAS 操作检查影响行数，为 0 即判定并发冲突并返回友好错误信息

### 9.3 缓存设计

- **防穿透**：CacheClient 在 DB 无结果时写入 2 分钟空值缓存
- **旁路缓存**：严格 Cache-Aside 模式，写操作先更新 DB 再删除 Redis
- **GEO 启动预热**：`@EventListener(ApplicationReadyEvent)` 全量加载在售商品坐标到 Redis GEO

### 9.4 数据一致性

- **价格快照**：Order 表在创建时记录下单瞬间的商品价格，后续商品调价不影响历史订单统计
- **GEO 全生命周期同步**：创建 (GEOADD)、更新 (GEOADD)、删除 (ZREM)、预约 (ZREM)、取消 (GEOADD)、完成 (ZREM) — 6 个关键节点全部覆盖

### 9.5 代码质量

- **防 N+1 查询**：列表接口全部使用 MyBatis-Plus `selectBatchIds` 批量加载关联数据
- **参数安全**：pageSize 上限 20、radius 上限 20000m、limit 上限 50、keyword 上限 100 字符 — 全部静默截断
- **幂等设计**：收藏/取消收藏/预约等操作均支持重复调用，不会产生脏数据

### 9.6 适合面试的总结

> 独立完成了校园二手交易平台 v1.0，基于 Spring Boot + MyBatis-Plus + Redis 架构，实现了从商品发布、分类浏览、关键字搜索、收藏到预约/订单全流程。核心设计包括：基于 CAS（乐观并发控制）的订单状态机，利用数据库行级锁实现无锁并发；Redis 旁路缓存（Cache-Aside）模式 + 空值缓存防穿透；GEO 空间索引实现附近商品搜索并确保增删改查全生命周期同步。项目采用双包架构在旧项目基础上渐进式重构，共交付 6 张新增表、18 个新接口，覆盖 8 个 Sprint 的完整开发周期。

---

## 10. 可优化项与 V2 Roadmap

### 10.1 已知技术债务

| 编号 | 问题 | 优先级 | 建议方案 |
|------|------|--------|---------|
| TD-1 | JDK 21 + MP 3.4.3 需要 JVM 额外参数 | 中 | 升级 MyBatis-Plus 至 3.5.x |
| TD-9 | 缓存删除失败无容错（更新/删除时） | 中 | 引入 MQ 异步重试 或 延迟双删 |
| TD-10 | pageFavorites.total 含已失效商品 | 低 | 改为子查询计数 或 维护 favorite_count 字段 |
| — | pageSize 负数未保护 | 低 | 添加 `Math.max(1, pageSize)` |
| — | consult_count 字段预留但未实现 | 低 | V2 实现 IM/留言 |
| — | CreditRecord 表未投用 | 低 | V2 实现信誉分体系 |

### 10.2 V2 Roadmap

| 优先级 | 功能 | 涉及技术 |
|--------|------|---------|
| P0 | 部署上线 (Docker/Nginx/HTTPS) | DevOps |
| P0 | 单元测试 + 集成测试覆盖 | JUnit 5 + Mockito |
| P1 | 图片上传 (OSS/本地) | MultipartFile + 云存储 |
| P1 | 商品热度排行榜 | Redis ZSet (浏览量+收藏加权) |
| P1 | 信誉分体系 | 事件驱动 + 汇总快照 (已设计) |
| P2 | IM / 在线咨询 | WebSocket / STOMP |
| P2 | 用户关注 Feed 流 | Redis ZSet 推模式 |
| P2 | 校园限时抢购 | Lua 脚本 + Redis Stream |
| P3 | 举报/审核系统 | 工单流 |
| P3 | 管理后台 | Vue/React SPA |
| P3 | Elasticsearch 全文搜索 | 分词搜索替代 MySQL LIKE |

---

## 附录

### A. 文件统计

| 类别 | 数量 |
|------|------|
| Java 源文件 | 114 |
| Entity 类 | 17 (Campus 6 + hmdp 11) |
| Controller 类 | 14 (Campus 4 + hmdp 10) |
| Service 接口 | 14 (Campus 4 + hmdp 10) |
| DTO / VO 类 | 17 (Campus 11 + hmdp 6) |
| Enum 类 | 6 (全部 Campus) |
| Config 类 | 5 |
| Mapper 接口 | 14 (Campus 6 + hmdp 8) |
| SQL Schema | 2 |
| 数据库表 | 16 (Campus 6 + hmdp 10) |

### B. CampusMarket 接口完整列表

```
GET    /api/categories              分类列表
GET    /api/items                   商品列表 (分页/筛选/搜索/排序)
GET    /api/items/nearby           附近商品 (GEO, radius≤20km, limit≤50)
GET    /api/items/{id}             商品详情 (缓存+浏览计数+isFavorite)
POST   /api/items                  发布商品 (@Valid 校验)
PUT    /api/items/{id}             修改商品
DELETE /api/items/{id}             删除商品 (逻辑删除)
POST   /api/items/{id}/favorite    收藏商品 (幂等)
DELETE /api/items/{id}/favorite    取消收藏 (幂等)
GET    /api/users/me/favorites     我的收藏 (静默过滤已失效)
POST   /api/orders                 创建订单 (价格快照+CAS)
GET    /api/orders/buyer           我买的 (分页)
GET    /api/orders/seller          我卖的 (分页)
GET    /api/orders/{id}            订单详情
PUT    /api/orders/{id}/confirm    卖家确认 (CAS)
PUT    /api/orders/{id}/reject     卖家拒绝 (CAS+商品恢复+GEO恢复)
PUT    /api/orders/{id}/cancel     买家取消 (CAS+商品恢复+GEO恢复)
PUT    /api/orders/{id}/complete   卖家完成 (CAS+GEO清理)
```

### C. 枚举值速查

**ItemStatus**: `1-在售` `2-已预约` `3-已售` `4-下架`

**ItemCondition**: `1-全新` `2-几乎全新` `3-良好` `4-一般` `5-有瑕疵`

**AuditStatus**: `0-待审核` `1-审核通过` `2-审核不通过`

**OrderStatus**: `1-待确认` `2-已确认` `3-已完成` `4-已拒绝` `5-已取消`
