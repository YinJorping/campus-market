# Sprint 6 Design Review

## Campus Market — 收藏模块（Favorite Module）

**Project**

Campus Market（校园二手交易平台）

**Sprint**

Sprint 6

**Status**

Approved for Review Sync

**Author**

Tech Lead（ChatGPT）

**Date**

2026-07-11

------

# 1. Sprint Goal

本 Sprint 完成校园二手交易平台**收藏模块（Favorite）**。

实现：

- 收藏商品
- 取消收藏
- 我的收藏
- 商品详情返回收藏状态（isFavorite）

本 Sprint 不涉及：

- 推荐算法
- 热门商品
- 收藏数量统计
- Redis 收藏缓存

------

# 2. Scope

## In Scope

### F1 收藏商品

```
POST /api/items/{itemId}/favorite
```

------

### F2 取消收藏

```
DELETE /api/items/{itemId}/favorite
```

------

### F3 我的收藏

```
GET /api/users/me/favorites
```

分页：

```
current

pageSize
```

------

### F4 商品详情

```
GET /api/items/{id}
```

新增：

```
isFavorite
```

------

## Out Of Scope

热门商品

推荐算法

收藏排行榜

收藏数统计

Redis 收藏缓存

------

# 3. Database Review

继续使用：

```
tb_favorite
```

字段：

| 字段        | 说明     |
| ----------- | -------- |
| id          | PK       |
| user_id     | 收藏用户 |
| item_id     | 收藏商品 |
| create_time | 收藏时间 |

------

数据库：

不修改。

不新增字段。

------

唯一约束：

```
(user_id,item_id)
```

数据库已存在。

业务：

无需重复收藏。

------

取消收藏：

采用：

物理删除。

```
DELETE

tb_favorite
```

原因：

收藏属于关系数据。

无需保留历史。

------

# 4. API Design

## 收藏

```
POST

/api/items/{itemId}/favorite
```

登录。

Response：

```
Result.ok()
```

重复收藏：

仍返回：

```
success
```

------

## 取消收藏

```
DELETE

/api/items/{itemId}/favorite
```

删除：

```
userId

+

itemId
```

不存在：

仍：

```
success
```

保持：

REST 幂等。

------

## 我的收藏

```
GET

/api/users/me/favorites
```

返回：

```
Page<ItemListVO>
```

继续复用：

Sprint5：

ItemListVO。

------

## 商品详情

继续：

```
GET

/api/items/{id}
```

新增：

```
isFavorite
```

匿名：

默认：

```
false
```

登录：

查询：

Favorite。

------

# 5. DTO / VO Design

## ItemQueryDTO

继续复用：

Sprint5。

不修改。

------

## ItemListVO

继续复用。

------

## ItemDetailVO

新增返回：

```
Boolean isFavorite
```

字段已经预留。

无需新增类。

------

新增：

```
FavoriteController
```

新增：

```
FavoriteService
```

无需新增：

FavoriteVO。

------

# 6. Service Design

新增：

```
FavoriteService
```

接口：

```
favorite(Long itemId)

unfavorite(Long itemId)

pageFavorites(Page)
```

------

业务流程：

收藏：

```
检查登录

↓

检查商品

↓

检查是否已收藏

↓

insert

↓

success
```

------

取消：

```
delete

userId

+

itemId
```

------

我的收藏：

```
favorite

↓

itemIds

↓

batch query

↓

ItemListVO
```

禁止：

N+1。

------

# 7. Permission Design

收藏：

登录。

取消：

登录。

我的收藏：

登录。

详情：

匿名。

继续：

Sprint4：

```
UserHolder
```

手动鉴权。

------

# 8. Cache Design

Sprint6：

不新增：

Redis。

继续：

Sprint5：

详情缓存。

收藏：

不缓存。

------

# 9. Transaction Design

收藏：

```
@Transactional
```

取消：

```
@Transactional
```

原因：

后续：

方便：

增加：

收藏数。

积分。

消息。

------

# 10. Exception Design

商品不存在：

```
商品不存在
```

未登录：

```
请先登录
```

重复收藏：

```
success
```

重复取消：

```
success
```

------

# 11. Technical Debt

TD-10

收藏数。

后续。

------

TD-11

Redis 收藏缓存。

后续。

------

TD-12

推荐系统。

后续。

------

# 12. Acceptance Criteria

Compile：

```
PASS
```

Spring Boot：

```
PASS
```

API：

```
POST

/api/items/{id}/favorite
```

PASS。

------

API：

```
DELETE

/api/items/{id}/favorite
```

PASS。

------

API：

```
GET

/api/users/me/favorites
```

PASS。

------

商品详情：

```
isFavorite
```

正确。

------

数据库：

```
tb_favorite
```

验证。

PASS。

------

# 13. Review Checklist

Claude Review Sync：

必须输出：

```
Confirmed

Changed

Questions
```

禁止：

直接编码。

------

Coding：

必须：

Compile。

API。

数据库验证。

------

Final Review：

Tech Lead。

------

# 14. Deliverables

新增：

```
FavoriteController.java

FavoriteService.java

FavoriteServiceImpl.java
```

修改：

```
ItemController.java

ItemServiceImpl.java

ItemDetailVO.java
```

新增测试：

```
FavoriteControllerTest

FavoriteServiceTest
```

------

# Final Decision

Sprint 6 Design Review

**Status：APPROVED FOR IMPLEMENTATION**

Claude 下一步执行：

```
Review Sync
        ↓
Tech Lead Confirm
        ↓
Coding
        ↓
Compile
        ↓
API Test
        ↓
Delivery Report
        ↓
Final Review
```