# CampusMarket v1.0 — Backend Acceptance Test Plan

> Tech Lead Final Acceptance | 2026-07-12

---

## 测试环境

| 项目 | 值 |
|------|-----|
| 应用地址 | `http://localhost:8081` |
| Token 头 | `authorization: {token}` |
| 测试用户A (卖家) | phone: `13800138000` |
| 测试用户B (买家) | phone: `13900139000` |
| 登录接口 | `POST /user/code?phone={phone}` → `POST /user/login` |

**测试约定：**
- `{{tokenA}}` = 用户A的 token
- `{{tokenB}}` = 用户B的 token
- `{{itemId}}` / `{{orderId}}` = 测试过程中获取的 ID
- 所有 curl 命令应在 Git Bash 或 WSL 终端执行
- 价格单位为「分」

---

# Phase 2: Category Module

---

## TC-2.1 分类列表查询

| 项目 | 内容 |
|------|------|
| **目的** | 验证分类列表返回正确 |
| **前置** | tb_category 有数据 (id=2,3 两条) |
| **请求** | `GET /api/categories` |
| **期望返回** | `success:true`, data 数组含"电脑办公"和"图书教材" |

```bash
curl -s http://localhost:8081/api/categories
```

**验收标准：** data.length >= 2，每条含 id/name/icon/sort

---

## TC-2.2 分类列表 — 空库容错

| 项目 | 内容 |
|------|------|
| **目的** | 分类表无数据时不应报错 |
| **前置** | 无需（已有数据，已验证框架容错能力） |
| **请求** | `GET /api/categories` |
| **期望返回** | `success:true`, data 为空数组或正常数据 |

```bash
curl -s http://localhost:8081/api/categories
```

**验收标准：** 无 500 错误，返回结构正确

---

## TC-2.3 分类列表匿名访问

| 项目 | 内容 |
|------|------|
| **目的** | 分类接口无需登录 |
| **请求** | `GET /api/categories` (不携带 authorization 头) |
| **期望返回** | `success:true` |

```bash
curl -s http://localhost:8081/api/categories
```

**验收标准：** 无 401，正常返回数据

---

# Phase 3: Item Module

---

## TC-3.1 发布商品 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 验证商品发布完整链路 |
| **前置** | 用户A已登录，categoryId=2 存在 |
| **请求** | `POST /api/items` |

```bash
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{
    "name":"Acceptance Test Item",
    "categoryId":2,
    "images":"https://img.test/a1.jpg,https://img.test/a2.jpg",
    "description":"验收测试商品",
    "price":15000,
    "originalPrice":25000,
    "campus":"Main Campus",
    "meetPlace":"图书馆门口",
    "x":116.398,
    "y":39.909,
    "itemCondition":3
  }'
```

| **期望返回** | `success:true`, data 为商品 ID |
| **期望 DB** | tb_item 新增 1 条: status=1, auditStatus=1, deleted=0, viewCount=0 |
| **期望 Redis** | `geo:items` 新增该 itemId 成员 |

**验收标准：** 三项均满足，记录返回的 `{{itemId_A}}`

---

## TC-3.2 发布商品 — 未登录

| 项目 | 内容 |
|------|------|
| **目的** | 未登录时拒绝发布 |
| **请求** | `POST /api/items` (无 authorization 头) |

```bash
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -d '{"name":"test","categoryId":2,"images":"x.jpg","price":100,"campus":"c","meetPlace":"m","itemCondition":3}'
```

| **期望返回** | `success:false`, errorMsg 含"登录" |

**验收标准：** 无 DB 写入，返回明确错误

---

## TC-3.3 发布商品 — 必填字段缺失

| 项目 | 内容 |
|------|------|
| **目的** | @Valid 校验拒绝空 name |
| **请求** | `POST /api/items` name 为空字符串 |

```bash
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"name":"","categoryId":2,"images":"x.jpg","price":100,"campus":"c","meetPlace":"m","itemCondition":3}'
```

| **期望返回** | `success:false` |

**验收标准：** 被 @Valid 拦截，无 DB 写入

---

## TC-3.4 发布商品 — 分类不存在

| 项目 | 内容 |
|------|------|
| **目的** | 不存在的分类应报错 |
| **请求** | `POST /api/items` categoryId=9999 |

```bash
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"name":"test","categoryId":9999,"images":"x.jpg","price":100,"campus":"c","meetPlace":"m","itemCondition":3}'
```

| **期望返回** | `success:false`, errorMsg "分类不存在" |

**验收标准：** 返回明确错误，无 DB 写入

---

## TC-3.5 发布商品 — 原价低于售价

| 项目 | 内容 |
|------|------|
| **目的** | 业务校验：原价不得 < 售价 |
| **请求** | originalPrice < price |

```bash
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"name":"test","categoryId":2,"images":"x.jpg","price":50000,"originalPrice":30000,"campus":"c","meetPlace":"m","itemCondition":3}'
```

| **期望返回** | `success:false`, errorMsg "原价不能小于售价" |

**验收标准：** 返回明确错误

---

## TC-3.6 发布商品 — 图片超限 (9张)

| 项目 | 内容 |
|------|------|
| **目的** | 图片超过 9 张应拒绝 |
| **请求** | images 含 10 张图片 |

```bash
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"name":"test","categoryId":2,"images":"1.jpg,2.jpg,3.jpg,4.jpg,5.jpg,6.jpg,7.jpg,8.jpg,9.jpg,10.jpg","price":100,"campus":"c","meetPlace":"m","itemCondition":3}'
```

| **期望返回** | `success:false`, errorMsg "最多9张" |

**验收标准：** 返回明确错误

---

## TC-3.7 发布商品 — 经纬度必须同时填写

| 项目 | 内容 |
|------|------|
| **目的** | x 和 y 必须同时提供或同时为空 |
| **请求** | 只填 x 不填 y |

```bash
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"name":"test","categoryId":2,"images":"x.jpg","price":100,"campus":"c","meetPlace":"m","itemCondition":3,"x":116.0}'
```

| **期望返回** | `success:false`, errorMsg "经纬度必须同时填写" |

**验收标准：** 返回明确错误

---

## TC-3.8 发布商品 — 名称超长

| 项目 | 内容 |
|------|------|
| **目的** | 名称超过 128 字符应拒绝 |
| **请求** | name 为 130 个字符 |

```bash
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d "{\"name\":\"$(python3 -c 'print("a"*130)' 2>/dev/null || printf 'a%.0s' {1..130})\",\"categoryId\":2,\"images\":\"x.jpg\",\"price\":100,\"campus\":\"c\",\"meetPlace\":\"m\",\"itemCondition\":3}"
```

| **期望返回** | `success:false`, errorMsg "不能超过128个字符" |

**验收标准：** 返回明确错误

---

## TC-3.9 修改商品 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 正常修改商品信息 |
| **前置** | TC-3.1 创建的商品 `{{itemId_A}}` |
| **请求** | `PUT /api/items/{{itemId_A}}` |

```bash
curl -s -X PUT http://localhost:8081/api/items/{{itemId_A}} \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"name":"Updated Item Name","price":12000}'
```

| **期望返回** | `success:true` |
| **期望 DB** | tb_item.name 和 price 已更新 |
| **期望 Redis** | `cache:item:{{itemId_A}}` 已删除 |

**验收标准：** DB 更新正确，缓存已淘汰

---

## TC-3.10 修改商品 — 无权修改他人商品

| 项目 | 内容 |
|------|------|
| **目的** | 用户B 不能修改用户A 的商品 |
| **请求** | `PUT /api/items/{{itemId_A}}` 用 tokenB |

```bash
curl -s -X PUT http://localhost:8081/api/items/{{itemId_A}} \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenB}}" \
  -d '{"name":"Hacked"}'
```

| **期望返回** | `success:false`, errorMsg "无权修改" |

**验收标准：** DB 未变更

---

## TC-3.11 修改商品 — 修改坐标更新GEO

| 项目 | 内容 |
|------|------|
| **目的** | 修改坐标后 GEO 同步更新 |
| **前置** | `{{itemId_A}}` 当前坐标 (116.398, 39.909) |
| **请求** | 更新 x/y |

```bash
curl -s -X PUT http://localhost:8081/api/items/{{itemId_A}} \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"x":116.5,"y":40.0}'
```

| **期望返回** | `success:true` |
| **期望 Redis** | `geo:items` 中该 item 坐标已更新 |
| **验证** | `GET /api/items/nearby?x=116.5&y=40.0&radius=1000` 应包含此商品 |

**验收标准：** 新坐标出现在 nearby 结果中

---

## TC-3.12 修改商品 — 已售/已预约商品不可修改

| 项目 | 内容 |
|------|------|
| **目的** | status=SOLD 或 RESERVED 时不允许修改 |
| **前置** | (Phase 5 完成后回归) |
| **请求** | `PUT /api/items/{status=SOLD的ID}` |
| **期望返回** | `success:false`, errorMsg "商品已无法修改" |

**验收标准：** 返回明确错误

> 此用例 Phase 5 后执行

---

## TC-3.13 删除商品 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 逻辑删除商品 |
| **前置** | 用用户B发布一个新商品用于删除 |

```bash
# 用户B先发布商品
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenB}}" \
  -d '{"name":"To Be Deleted","categoryId":2,"images":"x.jpg","price":100,"campus":"c","meetPlace":"m","itemCondition":3,"x":116.4,"y":39.91}'
# 记录返回的 {{itemId_delete}}
```

| **请求** | `DELETE /api/items/{{itemId_delete}}` |

```bash
curl -s -X DELETE http://localhost:8081/api/items/{{itemId_delete}} \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | `success:true` |
| **期望 DB** | tb_item.deleted = 1 |
| **期望 Redis** | `cache:item:{{itemId_delete}}` 已删除，`geo:items` 已移除 |

**验收标准：** 三项同步生效

---

## TC-3.14 删除商品 — 无权删除他人商品

| 项目 | 内容 |
|------|------|
| **目的** | 用户A 不能删除用户B 的商品 |
| **请求** | `DELETE /api/items/{{itemId_A}}` 用 tokenB |

```bash
curl -s -X DELETE http://localhost:8081/api/items/{{itemId_A}} \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | `success:false` |

**验收标准：** DB 未变更

---

## TC-3.15 商品详情 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 查看商品详情，包含 isFavorite |
| **前置** | `{{itemId_A}}` 存在 |
| **请求** | `GET /api/items/{{itemId_A}}` (带 tokenA 可查看 isFavorite) |

```bash
curl -s http://localhost:8081/api/items/{{itemId_A}} \
  -H "authorization: {{tokenA}}"
```

| **期望返回** | `success:true`, data 含 images(数组)、sellerName、categoryName、isFavorite、viewCount |
| **期望 DB** | viewCount 自增 1 |

**验收标准：** 返回完整字段，viewCount 增长

---

## TC-3.16 商品详情 — 匿名访问

| 项目 | 内容 |
|------|------|
| **目的** | 未登录时 isFavorite = false |
| **请求** | `GET /api/items/{{itemId_A}}` (无 authorization) |

```bash
curl -s http://localhost:8081/api/items/{{itemId_A}}
```

| **期望返回** | `success:true`, isFavorite = false |

**验收标准：** 不报错，isFavorite 为 false

---

## TC-3.17 商品详情 — 不存在

| 项目 | 内容 |
|------|------|
| **目的** | 不存在的商品返回友好错误 |
| **请求** | `GET /api/items/99999` |

```bash
curl -s http://localhost:8081/api/items/99999
```

| **期望返回** | `success:false`, errorMsg "商品不存在" |

**验收标准：** 明确错误提示

---

## TC-3.18 商品详情 — 已删除

| 项目 | 内容 |
|------|------|
| **目的** | 已删除商品不可见 |
| **请求** | `GET /api/items/{{itemId_delete}}` |

```bash
curl -s http://localhost:8081/api/items/{{itemId_delete}}
```

| **期望返回** | `success:false`, errorMsg "商品不存在" |

**验收标准：** 无法访问已删除商品

---

## TC-3.19 商品列表 — 默认查询

| 项目 | 内容 |
|------|------|
| **目的** | 列表分页默认值正确 |
| **请求** | `GET /api/items` |

```bash
curl -s http://localhost:8081/api/items
```

| **期望返回** | `success:true`, data.records 数组，按时间倒序 |

**验收标准：** 只返回 status=ON_SALE + auditStatus=APPROVED + deleted=0 的商品

---

## TC-3.20 商品列表 — 分类筛选

| 项目 | 内容 |
|------|------|
| **目的** | 按 categoryId 筛选 |
| **请求** | `GET /api/items?categoryId=2` |

```bash
curl -s "http://localhost:8081/api/items?categoryId=2"
```

| **期望返回** | data.records 中所有商品 categoryName = "电脑办公" |

**验收标准：** 筛选正确

---

## TC-3.21 商品列表 — 关键字搜索

| 项目 | 内容 |
|------|------|
| **目的** | 搜索商品名称和描述 |
| **请求** | `GET /api/items?keyword=Acceptance` |

```bash
curl -s "http://localhost:8081/api/items?keyword=Acceptance"
```

| **期望返回** | data.records 包含 TC-3.1 创建的商品 |

**验收标准：** 搜索结果正确匹配

---

## TC-3.22 商品列表 — 价格区间

| 项目 | 内容 |
|------|------|
| **目的** | 按价格区间筛选 |
| **请求** | `GET /api/items?priceMin=10000&priceMax=20000` |

```bash
curl -s "http://localhost:8081/api/items?priceMin=10000&priceMax=20000"
```

| **期望返回** | 所有商品 price 在 10000~20000 之间 |

**验收标准：** 区间筛选正确

---

## TC-3.23 商品列表 — 排序 (价格升序)

| 项目 | 内容 |
|------|------|
| **目的** | priceAsc 排序生效 |
| **请求** | `GET /api/items?sort=priceAsc` |

```bash
curl -s "http://localhost:8081/api/items?sort=priceAsc"
```

| **期望返回** | records 按 price 从小到大排列 |

**验收标准：** 排序正确

---

## TC-3.24 商品列表 — 排序 (价格降序)

| 项目 | 内容 |
|------|------|
| **目的** | priceDesc 排序生效 |
| **请求** | `GET /api/items?sort=priceDesc` |

```bash
curl -s "http://localhost:8081/api/items?sort=priceDesc"
```

| **期望返回** | records 按 price 从大到小排列 |

**验收标准：** 排序正确

---

## TC-3.25 商品列表 — pageSize 上限截断

| 项目 | 内容 |
|------|------|
| **目的** | pageSize 超过 20 时静默截断为 20 |
| **请求** | `GET /api/items?pageSize=100` |

```bash
curl -s "http://localhost:8081/api/items?pageSize=100"
```

| **期望返回** | data.size = 20 (或实际商品数) |

**验收标准：** 不超过 20 条

---

## TC-3.26 商品列表 — keyword 上限截断

| 项目 | 内容 |
|------|------|
| **目的** | keyword 超过 100 字符时截断 |
| **请求** | keyword 为 200 个字符 |

```bash
curl -s "http://localhost:8081/api/items?keyword=aaaaaaaa...（200个a）"
```

| **期望返回** | 不报错，正常查询 (keyword 被截断) |

**验收标准：** 无 SQL 异常，正常返回

---

## TC-3.27 商品列表 — 空结果

| 项目 | 内容 |
|------|------|
| **目的** | 无匹配数据时返回空页 |
| **请求** | `GET /api/items?keyword=zxcvbnmasdfghjkl` |

```bash
curl -s "http://localhost:8081/api/items?keyword=zxcvbnmasdfghjkl"
```

| **期望返回** | `success:true`, data.records = [], data.total = 0 |

**验收标准：** 不报错，返回空页

---

## TC-3.28 商品列表 — 已删除/已预约/已售商品不出现在列表

| 项目 | 内容 |
|------|------|
| **目的** | 列表只展示 ON_SALE + APPROVED + 未删除 |
| **前置** | `{{itemId_delete}}` 已逻辑删除 |
| **请求** | `GET /api/items` |
| **期望返回** | 不包含 `{{itemId_delete}}` |

**验收标准：** 已删除商品被过滤

---

# Phase 4: Favorite Module

---

## TC-4.1 收藏商品 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 用户A 收藏 `{{itemId_A}}` 失败 (自己的商品) → 换一个非自己的商品测试 |
| **前置** | 用户A 先发布一个商品，用户B 收藏它 |

```bash
# 用户B 收藏 TC-3.1 创建的商品 (卖家是用户A)
curl -s -X POST http://localhost:8081/api/items/{{itemId_A}}/favorite \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | `success:true` |
| **期望 DB** | tb_favorite 新增 1 条 (userId=B, itemId={{itemId_A}}) |

**验收标准：** 收藏成功

---

## TC-4.2 收藏商品 — 幂等

| 项目 | 内容 |
|------|------|
| **目的** | 重复收藏同一商品不报错 |
| **请求** | 再次执行 TC-4.1 |

```bash
curl -s -X POST http://localhost:8081/api/items/{{itemId_A}}/favorite \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | `success:true` |
| **期望 DB** | 不产生重复行 (UNIQUE KEY 约束) |

**验收标准：** 幂等，不报错，不脏写

---

## TC-4.3 收藏商品 — 未登录

| 项目 | 内容 |
|------|------|
| **目的** | 未登录时拒绝收藏 |
| **请求** | `POST /api/items/{{itemId_A}}/favorite` (无 authorization) |

```bash
curl -s -X POST http://localhost:8081/api/items/{{itemId_A}}/favorite
```

| **期望返回** | `success:false`, errorMsg "请先登录" |

**验收标准：** 返回 401 或明确错误

---

## TC-4.4 收藏商品 — 不存在的商品

| 项目 | 内容 |
|------|------|
| **目的** | 收藏不存在的商品应报错 |
| **请求** | `POST /api/items/99999/favorite` |

```bash
curl -s -X POST http://localhost:8081/api/items/99999/favorite \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | `success:false` |

**验收标准：** 返回明确错误

---

## TC-4.5 我的收藏 — 列表查询

| 项目 | 内容 |
|------|------|
| **目的** | 查看收藏列表 (静默过滤已失效商品) |
| **前置** | 用户B 已收藏 `{{itemId_A}}` |

```bash
curl -s "http://localhost:8081/api/users/me/favorites?current=1&pageSize=10" \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | `success:true`, data.records 包含 `{{itemId_A}}` |

**验收标准：** 收藏列表正确展示

---

## TC-4.6 收藏 — isFavorite 联动

| 项目 | 内容 |
|------|------|
| **目的** | 商品详情接口反映收藏状态 |
| **请求** | `GET /api/items/{{itemId_A}}` (tokenB) |

```bash
curl -s http://localhost:8081/api/items/{{itemId_A}} \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | isFavorite = true |

**验收标准：** isFavorite 正确反映收藏状态

---

## TC-4.7 取消收藏 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 取消收藏后 isFavorite 恢复 false |
| **请求** | `DELETE /api/items/{{itemId_A}}/favorite` |

```bash
curl -s -X DELETE http://localhost:8081/api/items/{{itemId_A}}/favorite \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | `success:true` |
| **期望 DB** | tb_favorite 删除该行 |

**验收标准：** 取消成功，再次查详情 isFavorite=false

---

## TC-4.8 取消收藏 — 幂等

| 项目 | 内容 |
|------|------|
| **目的** | 重复取消不报错 |
| **请求** | 再次执行 TC-4.7 |
| **期望返回** | `success:true` |

**验收标准：** 幂等，不报错

---

# Phase 5: Order Module

---

## TC-5.1 创建订单 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 用户B 预约用户A 的商品 |
| **前置** | `{{itemId_A}}` 状态为 ON_SALE，卖家=用户A |

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenB}}" \
  -d '{"itemId": {{itemId_A}}}'
```

| **期望返回** | `success:true`, data 为 orderId |
| **期望 DB** | tb_order 新增 1 条: buyerId=B, sellerId=A, price=商品现价, status=1 |
| **期望 DB** | tb_item.status → 2 (RESERVED) |
| **期望 Redis** | `geo:items` 移除该 itemId |

**验收标准：** 三项同步，记录 `{{orderId}}`

---

## TC-5.2 创建订单 — 不能预约自己的商品

| 项目 | 内容 |
|------|------|
| **目的** | 卖家不能预约自己的商品 |
| **请求** | 用户A 预约 `{{itemId_A}}` |

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"itemId": {{itemId_A}}}'
```

| **期望返回** | `success:false`, errorMsg "不能预约自己的商品" |

**验收标准：** 返回错误

---

## TC-5.3 创建订单 — 已预约商品重复预约

| 项目 | 内容 |
|------|------|
| **目的** | CAS 阻止并发预约 |
| **前置** | `{{itemId_A}}` 已是 RESERVED |
| **请求** | 用户B 再次预约同一商品 |

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenB}}" \
  -d '{"itemId": {{itemId_A}}}'
```

| **期望返回** | `success:false`, errorMsg "已被预约" 或 "已下架" |

**验收标准：** CAS 生效，不创建重复订单

---

## TC-5.4 创建订单 — 未登录

| 项目 | 内容 |
|------|------|
| **目的** | 未登录时拒绝下单 |
| **请求** | `POST /api/orders` (无 authorization) |

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"itemId": 1}'
```

| **期望返回** | `success:false`, errorMsg "请先登录" |

**验收标准：** 返回 401 或明确错误

---

## TC-5.5 卖家确认 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 卖家确认预约 |
| **前置** | `{{orderId}}` 状态=PENDING, 卖家=用户A |

```bash
curl -s -X PUT http://localhost:8081/api/orders/{{orderId}}/confirm \
  -H "authorization: {{tokenA}}"
```

| **期望返回** | `success:true` |
| **期望 DB** | tb_order.status → 2 (CONFIRMED) |

**验收标准：** 状态流转正确

---

## TC-5.6 卖家确认 — 非卖家无权

| 项目 | 内容 |
|------|------|
| **目的** | 买家不能确认订单 |
| **请求** | 用户B 确认 `{{orderId}}` |

```bash
curl -s -X PUT http://localhost:8081/api/orders/{{orderId}}/confirm \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | `success:false` |

**验收标准：** 被拒绝，状态不变

---

## TC-5.7 卖家确认 — 非 PENDING 状态不可确认

| 项目 | 内容 |
|------|------|
| **目的** | CAS: 已确认的订单不能再次确认 |
| **前置** | `{{orderId}}` 状态=CONFIRMED |
| **请求** | 用户A 再次确认 |

```bash
curl -s -X PUT http://localhost:8081/api/orders/{{orderId}}/confirm \
  -H "authorization: {{tokenA}}"
```

| **期望返回** | `success:false`, errorMsg "状态已变更" |

**验收标准：** CAS 拦截

---

## TC-5.8 卖家完成 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 卖家标记交易完成 |
| **前置** | `{{orderId}}` 状态=CONFIRMED |

```bash
curl -s -X PUT http://localhost:8081/api/orders/{{orderId}}/complete \
  -H "authorization: {{tokenA}}"
```

| **期望返回** | `success:true` |
| **期望 DB** | tb_order.status → 3 (FINISHED) |
| **期望 DB** | tb_item.status → 3 (SOLD) |
| **期望 Redis** | `geo:items` 移除该 itemId |

**验收标准：** 三项正确

---

## TC-5.9 买家取消 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 买家取消订单，商品恢复在售 |
| **前置** | 需要一个新的 PENDING 订单 |

```bash
# Step 1: 用户A 先发布一个新商品
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"name":"Cancel Test Item","categoryId":2,"images":"x.jpg","price":5000,"campus":"c","meetPlace":"m","itemCondition":3,"x":116.4,"y":39.91}'
# → {{itemId_cancel}}

# Step 2: 用户B 预约
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenB}}" \
  -d '{"itemId": {{itemId_cancel}}}'
# → {{orderId_cancel}}

# Step 3: 用户B 取消
curl -s -X PUT http://localhost:8081/api/orders/{{orderId_cancel}}/cancel \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | `success:true` |
| **期望 DB** | tb_order.status → 5 (CANCELLED) |
| **期望 DB** | tb_item.status → 1 (ON_SALE) |
| **期望 Redis** | `geo:items` 恢复该 itemId |

**验收标准：** 三项正确，商品可被再次预约

---

## TC-5.10 买家取消 — 非买家无权

| 项目 | 内容 |
|------|------|
| **目的** | 卖家不能代为取消 |
| **请求** | 用户A 取消 `{{orderId_cancel}}` |

```bash
curl -s -X PUT http://localhost:8081/api/orders/{{orderId_cancel}}/cancel \
  -H "authorization: {{tokenA}}"
```

| **期望返回** | `success:false`, errorMsg "无权限" |

**验收标准：** 被拒绝

---

## TC-5.11 卖家拒绝 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 卖家拒绝订单，商品恢复在售 |
| **前置** | 新创建 PENDING 订单 |

```bash
# Step 1: 用户B 预约 {{itemId_cancel}} (已恢复 ON_SALE)
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenB}}" \
  -d '{"itemId": {{itemId_cancel}}}'
# → {{orderId_reject}}

# Step 2: 用户A 拒绝
curl -s -X PUT http://localhost:8081/api/orders/{{orderId_reject}}/reject \
  -H "authorization: {{tokenA}}"
```

| **期望返回** | `success:true` |
| **期望 DB** | tb_order.status → 4 (REJECTED) |
| **期望 DB** | tb_item.status → 1 (ON_SALE) |
| **期望 Redis** | `geo:items` 恢复该 itemId |

**验收标准：** 三项正确

---

## TC-5.12 卖家拒绝 — 非卖家无权

| 项目 | 内容 |
|------|------|
| **目的** | 买家不能拒绝 |
| **请求** | 用户B 拒绝 (需要一个新订单，但此处验证快速) |

```bash
# 基于目前 {{orderId_cancel}} 已经 CANCELLED，拒绝应失败
curl -s -X PUT http://localhost:8081/api/orders/{{orderId_reject}}/reject \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | `success:false` |

**验收标准：** 被拒绝

---

## TC-5.13 订单列表 — 买家视角

| 项目 | 内容 |
|------|------|
| **目的** | 我买的订单列表 |
| **请求** | `GET /api/orders/buyer` |

```bash
curl -s "http://localhost:8081/api/orders/buyer?current=1&pageSize=10" \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | `success:true`, data.records 包含 itemName/itemCoverImage/price/status/counterpartyName |

**验收标准：** 卖方名称为 counterpartyName

---

## TC-5.14 订单列表 — 卖家视角

| 项目 | 内容 |
|------|------|
| **目的** | 我卖的订单列表 |
| **请求** | `GET /api/orders/seller` |

```bash
curl -s "http://localhost:8081/api/orders/seller?current=1&pageSize=10" \
  -H "authorization: {{tokenA}}"
```

| **期望返回** | `success:true`, data.records 包含 counterpartyName (买方名) |

**验收标准：** 买方名称为 counterpartyName

---

## TC-5.15 订单列表 — 状态筛选

| 项目 | 内容 |
|------|------|
| **目的** | 按订单状态筛选 |
| **请求** | `GET /api/orders/buyer?status=5` (CANCELLED) |

```bash
curl -s "http://localhost:8081/api/orders/buyer?status=5" \
  -H "authorization: {{tokenB}}"
```

| **期望返回** | 所有返回订单 status=5 |

**验收标准：** 筛选正确

---

## TC-5.16 订单详情 — 正常流程

| 项目 | 内容 |
|------|------|
| **目的** | 查看订单完整详情 |
| **请求** | `GET /api/orders/{{orderId}}` |

```bash
curl -s http://localhost:8081/api/orders/{{orderId}} \
  -H "authorization: {{tokenA}}"
```

| **期望返回** | data 含 buyerId/buyerName/buyerAvatar + sellerId/sellerName/sellerAvatar + itemImages(数组) |

**验收标准：** 买卖双方信息完整

---

## TC-5.17 订单详情 — 无关用户无权查看

| 项目 | 内容 |
|------|------|
| **目的** | 既非买家也非卖家的用户无法查看 |
| **请求** | 第三方用户查看 `{{orderId}}` (如有第三用户) |

**验收标准：** 返回 null 或明确拒绝

---

## TC-5.18 订单 — 状态流转完整链路

| 项目 | 内容 |
|------|------|
| **目的** | 验证完整订单状态机: ON_SALE → PENDING → CONFIRMED → FINISHED + 商品: ON_SALE → RESERVED → SOLD |
| **前置** | 重新执行一次完整的发布→预约→确认→完成 |

```bash
# 1. 用户A发品 → {{itemId_flow}}
# 2. 用户B预约 → {{orderId_flow}} | 商品 RESERVED
# 3. 用户A确认 | 订单 CONFIRMED
# 4. 用户A完成 | 订单 FINISHED, 商品 SOLD
```

**验收标准：** 全程无错误，状态每次流转正确

---

## TC-5.19 订单 — 价格快照验证

| 项目 | 内容 |
|------|------|
| **目的** | 订单 price 为下单时商品价格，后续商品改价不影响 |
| **前置** | 订单已创建 |
| **请求** | 查询订单详情 |
| **期望返回** | 订单 detail.price = 下单时的价格 |

```sql
SELECT o.price AS order_price, i.price AS current_item_price
FROM tb_order o JOIN tb_item i ON o.item_id = i.id
WHERE o.id = {{orderId_flow}};
```

**验收标准：** 订单价格快照正确，不受商品后续调价影响

---

# Phase 6: GEO Module

---

## TC-6.1 Nearby 查询 — 基础搜索

| 项目 | 内容 |
|------|------|
| **目的** | 搜索附近在售商品 |
| **前置** | 至少有一个在售且有坐标的商品 (如 `{{itemId_cancel}}`) |

```bash
curl -s "http://localhost:8081/api/items/nearby?x=116.4&y=39.91&radius=5000&limit=10"
```

| **期望返回** | `success:true`, data 数组含 distance 字段 (Double, 米) |

**验收标准：** 返回距离排序的商品列表

---

## TC-6.2 Nearby 查询 — radius 上限截断

| 项目 | 内容 |
|------|------|
| **目的** | radius > 20000 时静默截断 |
| **请求** | radius=50000 |

```bash
curl -s "http://localhost:8081/api/items/nearby?x=116.4&y=39.91&radius=50000&limit=10"
```

| **期望返回** | `success:true` (静默截断为 20000m，不报错) |

**验收标准：** 无错误，正常返回

---

## TC-6.3 Nearby 查询 — limit 上限截断

| 项目 | 内容 |
|------|------|
| **目的** | limit > 50 时截断 |
| **请求** | limit=100 |

```bash
curl -s "http://localhost:8081/api/items/nearby?x=116.4&y=39.91&radius=5000&limit=100"
```

| **期望返回** | `success:true`, data.length <= 50 |

**验收标准：** 不超过 50 条

---

## TC-6.4 Nearby 查询 — 空结果

| 项目 | 内容 |
|------|------|
| **目的** | 无附近商品时不报错 |
| **请求** | 偏远坐标 |

```bash
curl -s "http://localhost:8081/api/items/nearby?x=0&y=0&radius=100&limit=10"
```

| **期望返回** | `success:true`, data = [] |

**验收标准：** 空数组无报错

---

## TC-6.5 GEO — 创建商品同步

| 项目 | 内容 |
|------|------|
| **目的** | 发布带坐标的商品后 GEO 即时生效 |
| **前置** | 用户A 发布新商品 (带坐标) |

```bash
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"name":"GEO Sync Test","categoryId":2,"images":"x.jpg","price":100,"campus":"c","meetPlace":"m","itemCondition":3,"x":116.399,"y":39.908}'
# → {{itemId_geo}}
```

| **验证** | 立即查询 nearby 应包含该商品 |

```bash
curl -s "http://localhost:8081/api/items/nearby?x=116.398&y=39.909&radius=500"
# 应包含 {{itemId_geo}}
```

**验收标准：** 发布后立即出现在 nearby 结果中

---

## TC-6.6 GEO — 删除商品同步

| 项目 | 内容 |
|------|------|
| **目的** | 删除商品从 GEO 移除 |
| **请求** | `DELETE /api/items/{{itemId_geo}}` |

```bash
curl -s -X DELETE http://localhost:8081/api/items/{{itemId_geo}} \
  -H "authorization: {{tokenA}}"
```

| **验证** | 再次查 nearby 不应包含该商品 |
| **期望 Redis** | `geo:items` 中无该 itemId |

**验收标准：** 删除后不在 nearby 结果中

---

## TC-6.7 GEO — 订单 Cancel 恢复

| 项目 | 内容 |
|------|------|
| **目的** | TC-5.9 已验证 — 取消订单时商品重新加入 GEO |
| **验证** | 检查 `{{itemId_cancel}}` 状态为 ON_SALE 且在 nearby 可见 |

```bash
curl -s "http://localhost:8081/api/items/nearby?x=116.4&y=39.91&radius=5000"
# 应包含 {{itemId_cancel}}
```

**验收标准：** Cancel 后 GEO 恢复

---

## TC-6.8 GEO — 订单 Reject 恢复

| 项目 | 内容 |
|------|------|
| **目的** | TC-5.11 已验证 — 拒绝订单时商品重新加入 GEO |
| **验证** | `{{itemId_cancel}}` (被 reject 后恢复 ON_SALE) 在 nearby 可见 |

```bash
curl -s "http://localhost:8081/api/items/nearby?x=116.4&y=39.91&radius=5000"
# 应包含 {{itemId_cancel}}
```

**验收标准：** Reject 后 GEO 恢复

---

## TC-6.9 GEO — 订单 Complete 移除

| 项目 | 内容 |
|------|------|
| **目的** | TC-5.8 已验证 — 完成交易后从 GEO 移除 |
| **验证** | `{{itemId_flow}}` 状态=SOLD 时不在 nearby 中 |

**验收标准：** Complete 后 GEO 移除

---

## TC-6.10 GEO — 启动预热

| 项目 | 内容 |
|------|------|
| **目的** | 重启应用后 GEO 从 DB 全量恢复 |
| **前置** | 至少有一个 ON_SALE + APPROVED + 未删除 + 有坐标的商品 |
| **操作** | 停止应用 → 清空 GEO key → 重启 |

```bash
# 停止应用
# 清空 GEO
docker exec redis redis-cli -a 123321 DEL geo:items
# 重启应用
# 检查
docker exec redis redis-cli -a 123321 ZCARD geo:items
```

| **期望** | ZCARD > 0 (从 DB 重新加载) |

**验收标准：** 应用启动后自动预热 GEO 数据

---

# Phase 7: Regression (Sprint 4~8 全量回归)

---

## TC-R1 发布 + 浏览 + 收藏 + 取消收藏 完整链路

```bash
# 1. 用户A 发布商品 {{itemR1}}
# 2. 用户B 浏览列表，能看到该商品
# 3. 用户B 点击详情，viewCount=1
# 4. 用户B 收藏，isFavorite=true
# 5. 用户B 查看收藏列表，包含该商品
# 6. 用户B 取消收藏，isFavorite=false
# 7. 用户B 再次查看收藏列表，不包含该商品
```

**验收标准：** 全链路无错误

---

## TC-R2 发布 + 预约 + GEO 联动 + 取消 + 重新预约 + 确认 + 完成 完整链路

```bash
# 1. 用户B 发布商品 {{itemR2}} (带坐标)
# 2. 验证 nearby 可见
# 3. 用户A 预约 → item RESERVED, nearby 不可见
# 4. 用户A 取消 → item ON_SALE, nearby 恢复可见
# 5. 用户A 重新预约 → item RESERVED, nearby 不可见
# 6. 用户B 确认 → order CONFIRMED
# 7. 用户B 完成 → order FINISHED, item SOLD
```

**验收标准：** 全链路无错误，GEO 同步正确

---

## TC-R3 列表筛选组合

```bash
# GET /api/items?categoryId=2&keyword=test&priceMin=100&priceMax=100000&sort=priceAsc
```

**验收标准：** 组合筛选不报错，结果正确

---

## TC-R4 未登录全系列访问

| 接口 | 期望 |
|------|------|
| `GET /api/categories` | 正常 |
| `GET /api/items` | 正常 |
| `GET /api/items/nearby` | 正常 |
| `GET /api/items/{id}` | 正常 (isFavorite=false) |
| `POST /api/items` | 拒绝 |
| `PUT /api/items/{id}` | 拒绝 |
| `DELETE /api/items/{id}` | 拒绝 |
| `POST /api/items/{id}/favorite` | 拒绝 |
| `DELETE /api/items/{id}/favorite` | 拒绝 |
| `GET /api/users/me/favorites` | 拒绝 |
| `POST /api/orders` | 拒绝 |
| `GET /api/orders/buyer` | 拒绝 |
| `GET /api/orders/seller` | 拒绝 |
| `PUT /api/orders/{id}/confirm` | 拒绝 |

**验收标准：** 读接口放行，写接口全部拒绝

---

# Phase 8: Stress & Boundary

---

## TC-S1 SQL 注入防护

| 项目 | 内容 |
|------|------|
| **目的** | 验证 MyBatis-Plus 参数化查询 |
| **请求** | `GET /api/items?keyword=' OR '1'='1` |

```bash
curl -s "http://localhost:8081/api/items?keyword='%20OR%20'1'='1"
```

| **期望** | 返回空结果或正常结果（不泄露数据） |

**验收标准：** 无异常，无数据泄露

---

## TC-S2 价格参数负数

| 项目 | 内容 |
|------|------|
| **目的** | 负数价格是否被正确处理 |
| **请求** | `POST /api/items` price=-100 |

```bash
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"name":"test","categoryId":2,"images":"x.jpg","price":-100,"campus":"c","meetPlace":"m","itemCondition":3}'
```

| **期望** | `success:false` (@Positive 校验拦截) |

**验收标准：** 被 @Valid 拦截

---

## TC-S3 XSS 注入

| 项目 | 内容 |
|------|------|
| **目的** | 名称含脚本标签 |
| **请求** | name = `<script>alert(1)</script>` |

```bash
curl -s -X POST http://localhost:8081/api/items \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"name":"<script>alert(1)</script>","categoryId":2,"images":"x.jpg","price":100,"campus":"c","meetPlace":"m","itemCondition":3}'
```

| **期望** | 数据原样存入 DB（后端不负责转义），前端负责渲染转义 |

**验收标准：** 不报 500，数据正确存储

---

## TC-S4 并发收藏验证

| 项目 | 内容 |
|------|------|
| **目的** | 快速两次收藏同一商品，验证 UNIQUE KEY 保护 |
| **请求** | 连续两次 POST favorite |

```bash
curl -s -X POST http://localhost:8081/api/items/{{itemId_A}}/favorite \
  -H "authorization: {{tokenB}}" &
curl -s -X POST http://localhost:8081/api/items/{{itemId_A}}/favorite \
  -H "authorization: {{tokenB}}" &
wait
```

| **期望 DB** | 最多 1 条记录 |

**验收标准：** UNIQUE KEY 约束 + 幂等逻辑，不产生重复

---

## TC-S5 并发预约验证

| 项目 | 内容 |
|------|------|
| **目的** | 两个用户同时预约同一商品，只有一个成功 |
| **前置** | `{{itemId_cancel}}` 为 ON_SALE |
| **请求** | 用户A 和 用户B 同时预约 |

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"itemId": {{itemId_cancel}}}' &
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenB}}" \
  -d '{"itemId": {{itemId_cancel}}}' &
wait
```

| **期望** | 一个 success，一个 fail |

**验收标准：** CAS 并发保护生效

---

## TC-S6 订单价格快照一致性

| 项目 | 内容 |
|------|------|
| **目的** | 下单后修改商品价格，订单价格不变 |
| **操作** | 创建订单 → 修改商品价格 → 查订单详情 |

```bash
# 1. 下单 (记录价格)
# 2. 卖家改价
curl -s -X PUT http://localhost:8081/api/items/{{itemId}} \
  -H "Content-Type: application/json" \
  -H "authorization: {{tokenA}}" \
  -d '{"price":99999}'
# 3. 查订单详情 → price不变
```

**验收标准：** 订单 price 保持下单时的值

---

## TC-S7 Redis 缓存过期后重建

| 项目 | 内容 |
|------|------|
| **目的** | 缓存过期后能从 DB 重建 |
| **操作** | 查详情 → 手动删缓存 → 再查详情 |

```bash
# 1. 首次查详情 (写缓存)
curl -s http://localhost:8081/api/items/{{itemId_A}}
# 2. 删缓存
docker exec redis redis-cli -a 123321 DEL cache:item:{{itemId_A}}
# 3. 再次查详情
curl -s http://localhost:8081/api/items/{{itemId_A}}
```

| **期望** | 两次都返回正确数据，viewCount 累计+2 |

**验收标准：** 缓存重建成功

---

## TC-S8 数据库数据一致性

| 项目 | 内容 |
|------|------|
| **目的** | 验证数据完整性 |

```sql
-- 1. 订单的 item 必须存在
SELECT o.id FROM tb_order o LEFT JOIN tb_item i ON o.item_id = i.id WHERE i.id IS NULL;
-- 期望: 0 行

-- 2. 没有在售的已删除商品
SELECT COUNT(*) FROM tb_item WHERE deleted = 1 AND status = 1;
-- 期望: 0

-- 3. 收藏的 item 不应是已删除
SELECT f.id FROM tb_favorite f JOIN tb_item i ON f.item_id = i.id WHERE i.deleted = 1;
-- (允许存在，这是已知债务 TD-10)

-- 4. RESERVED / SOLD 的商品不应在 geo:items 中
-- (用代码逻辑保证，非 DB 约束)
```

**验收标准：** 无孤儿数据，核心约束满足

---

## TC-S9 分页边界

| 项目 | 内容 |
|------|------|
| **目的** | current=0、pageSize=0 等边界值 |
| **请求** | `GET /api/items?current=0&pageSize=0` |

```bash
curl -s "http://localhost:8081/api/items?current=0&pageSize=0"
```

| **期望** | 不报 500，返回空或默认值 |

**验收标准：** 框架容错

---

## TC-S10 空关键字

| 项目 | 内容 |
|------|------|
| **目的** | keyword 为空字符串 |
| **请求** | `GET /api/items?keyword=` |

```bash
curl -s "http://localhost:8081/api/items?keyword="
```

| **期望** | 不添加过滤条件，返回全部 |

**验收标准：** 正常返回

---

# 验收结论

> 执行完以上所有用例后，汇总结果填入下表。

| Phase | 用例数 | PASS | FAIL | 备注 |
|-------|--------|------|------|------|
| Phase 2: Category | 3 | | | |
| Phase 3: Item | 19 | | | |
| Phase 4: Favorite | 8 | | | |
| Phase 5: Order | 19 | | | |
| Phase 6: GEO | 10 | | | |
| Phase 7: Regression | 4 | | | |
| Phase 8: Stress & Boundary | 10 | | | |
| **总计** | **73** | | | |

**最终判定：**

- [ ] 达到 Production Ready
- [ ] 达到 Demo Ready
- [ ] 存在阻塞性 Bug
- [ ] 需要修复后重新验收

**已知风险：**

1. TD-1: JDK 21 + MP 3.4.3 JVM 参数依赖
2. TD-9: 缓存删除失败无容错
3. TD-10: pageFavorites.total 含已失效商品
4. consult_count 字段预留未使用
5. CreditRecord / Report 表预留未投用
