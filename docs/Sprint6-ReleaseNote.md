# Sprint 6 Release Note

**Sprint:** Sprint 6 — 收藏模块（Favorite Module）
**Date:** 2026-07-11
**Status:** Closed

---

## 一、交付摘要

实现校园二手交易平台收藏模块：收藏商品、取消收藏、我的收藏、商品详情返回收藏状态。

| 端点 | 方法 | 说明 |
|---|---|---|
| /api/items/{itemId}/favorite | POST | 收藏商品（登录） |
| /api/items/{itemId}/favorite | DELETE | 取消收藏（登录） |
| /api/users/me/favorites | GET | 我的收藏列表（登录，分页） |
| /api/items/{id} | GET | 商品详情增加 isFavorite 字段 |

## 二、文件变更

**新增（3 个）：**
- `com/campus/controller/FavoriteController.java`
- `com/campus/service/IFavoriteService.java`
- `com/campus/service/impl/FavoriteServiceImpl.java`

**修改（1 个）：**
- `com/campus/service/impl/ItemServiceImpl.java` — FavoriteMapper 注入 + isFavorite 填充

**未修改：**
- 数据库 6 张表不变
- ItemDetailVO.isFavorite 字段已在 Sprint 5 预留
- PublicUrls 不变
- Redis 缓存不新增

## 三、设计决策

| 决策 | 结论 |
|---|---|
| 重复收藏 | 幂等返回 success（依赖 DB 唯一索引 uk_user_item） |
| 取消收藏 | 物理 DELETE，幂等 |
| 已失效商品 | 我的收藏静默过滤（不展示已删除/下架/审核未通过商品） |
| N+1 | pageFavorites 使用 selectBatchIds 批量查询 |
| 鉴权 | 沿用 Sprint 4 UserHolder 手动鉴权模式 |
| 缓存 | Sprint 6 不新增 Redis |
| 收藏数统计 | TD-10，后续 Sprint 补充 |

## 四、验证结果

| 验证项 | 结果 |
|---|---|
| mvn clean compile | 106 源文件，0 错误 PASS |
| Spring Boot 启动 | PASS |
| POST favorite（正常/重复/未登录/商品不存在） | PASS |
| DELETE unfavorite（正常/不存在/未登录） | PASS |
| GET my favorites（有收藏/空/未登录） | PASS |
| GET item detail isFavorite（匿名/已收藏/取消后） | PASS |
| Sprint 3-5 回归 | PASS |

## 五、Technical Debt

| 编号 | 问题 | 计划 |
|---|---|---|
| TD-10 | pageFavorites total 基于 tb_favorite 计数，静默过滤后 records 可能少于 total | 后续优化或补充 favorite_count 字段 |
