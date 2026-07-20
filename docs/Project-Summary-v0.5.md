# CampusMarket v0.6 — 项目总结

**基线版本：** v0.6
**范围：** Sprint 1-6
**日期：** 2026-07-11

---

## 一、已完成能力

### 数据层
- 6 张业务表：tb_category, tb_item, tb_favorite, tb_credit_record, tb_report, tb_order
- 6 个 Entity + 6 个 Enum + 6 个 Mapper
- MyBatis-Plus 分页插件 + LambdaQueryWrapper/LambdaUpdateWrapper
- type-aliases-package 双包配置

### 分类模块
- GET /api/categories — 一级分类列表
- CategoryVO

### 商品发布模块
- POST /api/items — 创建商品（Bean Validation + 业务校验）
- PUT /api/items/{id} — 部分更新（LambdaUpdateWrapper + @Transactional）
- DELETE /api/items/{id} — 逻辑删除（卖家鉴权）
- SaveItemDTO / UpdateItemDTO
- market.audit.enabled 审核开关

### 商品浏览模块
- GET /api/items — 列表（分页+分类+校区+成色+价格区间+搜索+排序）
- GET /api/items/{id} — 详情（CacheClient + viewCount + 空值防穿透）
- ItemQueryDTO / ItemListVO / ItemDetailVO
- Redis 缓存：cache:item:{id}，TTL 30min，缓存淘汰

### 收藏模块
- POST /api/items/{itemId}/favorite — 收藏商品（登录，幂等）
- DELETE /api/items/{itemId}/favorite — 取消收藏（登录，物理删除，幂等）
- GET /api/users/me/favorites — 我的收藏列表（分页，静默过滤已失效商品）
- GET /api/items/{id} 增加 isFavorite 字段（匿名→false，登录→查询 Favorite 表）
- FavoriteController / IFavoriteService / FavoriteServiceImpl

### 基础设施
- PublicUrls 匿名接口白名单
- WebExceptionAdvice @Valid 异常处理
- CacheClient 缓存穿透/击穿防护
- JDK 21 + Lombok 1.18.34 + --add-opens 兼容

---

## 二、当前能力边界

| 能力 | 状态 | 说明 |
|---|---|---|
| 分类浏览 | 完成 | 一级分类，不展开子分类 |
| 商品发布 | 完成 | 含审核开关 |
| 商品浏览 | 完成 | 列表+详情+搜索+缓存 |
| 商品收藏 | 完成 | 含收藏/取消/列表/isFavorite，不缓存 |
| 商品预约/交易 | 未实现 | Sprint 7 |
| 信誉体系 | 未实现 | 表已建，tb_user 缺字段 |
| 举报 | 未实现 | 表已建 |
| GEO 附近商品 | 未实现 | Sprint 8 |
| Feed 关注流 | 未实现 | Sprint 10-11 |
| 秒杀/抢购 | 未实现 | Sprint 16-17 |
| IM/聊天 | V1 不做 | — |

---

## 三、已知 Technical Debt

| 编号 | 问题 | 风险 |
|---|---|---|
| TD-1 | JDK 17/21 + MP 3.4.3 需 --add-opens | 部署时遗漏则启动失败 |
| TD-7 | Controller 手动 UserHolder 鉴权 | 后续需抽 AOP/注解 |
| TD-9 | 缓存删除失败无容错 | 缓存可能短暂不一致 |
| TD-10 | pageFavorites total 含已失效商品 | 分页 total 可能大于实际展示数 |

---

## 四、Sprint 7 前置依赖

| 依赖 | 状态 |
|---|---|
| Favorite 模块（Sprint 6） | 已完成 |
| Order 表（Sprint 1） | 已建 |
| PublicUrls 白名单 | 已就绪 |
| UserHolder 登录态 | 已就绪 |
| tb_user 扩展 creditScore/creditLevel | Sprint 7 待补充 |
