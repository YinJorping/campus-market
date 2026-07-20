# CampusMarket 项目路线图

**项目基线：** v0.5（Sprint 1-5 稳定基线）
**更新日期：** 2026-07-11

---

## Sprint 进度

| Sprint | 模块 | 核心交付 | 状态 | 关闭日期 |
|---|---|---|---|---|
| Sprint 1 | 数据模型 | 6 Entity + 6 Enum + campus_market.sql | Closed | — |
| Sprint 2 | Mapper 层 | 6 Mapper + @MapperScan 双包 | Closed | — |
| Sprint 3 | 分类模块 | CategoryController + CategoryVO + PublicUrls | Closed | — |
| Sprint 4 | 商品发布 | ItemController(POST/PUT/DELETE) + SaveItemDTO + UpdateItemDTO + Bean Validation + @Transactional | Closed | 2026-07-11 |
| Sprint 5 | 商品浏览 | ItemController(GET) + ItemQueryDTO + ItemListVO + ItemDetailVO + CacheClient + viewCount | Closed | 2026-07-11 |
| Sprint 6 | 收藏模块 | FavoriteController + FavoriteService + 4 API | Closed | 2026-07-11 |
| Sprint 7 | 预约/订单 | 待规划 | Pending | — |
| Sprint 8 | GEO 附近商品 | 待规划 | Pending | — |
| Sprint 10-11 | Feed 关注流 | 待规划 | Pending | — |
| Sprint 12 | Redisson 锁 + UV | 待规划 | Pending | — |
| Sprint 16-17 | Lua 秒杀 + Stream | 待规划 | Pending | — |

---

## 里程碑

| 版本 | 范围 | 日期 |
|---|---|---|
| v0.5 | Sprint 1-5 稳定基线（发布+浏览） | 2026-07-11 |
| v0.6 | Sprint 1-6 稳定基线（发布+浏览+收藏） | 2026-07-11 |
| v0.7 | + 预约/订单 | 待定 |
| v1.0 | + GEO/Feed/秒杀 | 待定 |

---

## 技术债清单

| 编号 | 问题 | 来源 Sprint |
|---|---|---|
| TD-1 | JDK 17/21 + MP 3.4.3 需 --add-opens | Sprint 4 |
| TD-2~6 | 已修复 | Sprint 4 |
| TD-7 | Controller 手动 UserHolder 鉴权 | Sprint 4 |
| TD-8 | MyBatis-Plus 版本评估（3.4.3 → 3.5.x） | Sprint 4 |
| TD-9 | 缓存删除失败无容错 | Sprint 5 |
| TD-10 | pageFavorites total 含已失效商品 | Sprint 6 |

---

## 未完成项（跨 Sprint）

| 项 | 计划 Sprint |
|---|---|
| tb_user 扩展 creditScore/creditLevel | Sprint 6/7 |
| tb_order 补充 price 字段 | Sprint 7 |
| 旧 hmdp 代码清理 | 最后一轮 Sprint |
| Utils 迁移到 com.campus.utils | 最后一轮 Sprint |
