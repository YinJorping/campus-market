# Sprint 5 Design Review
## Campus Market（校园二手交易平台）

**Sprint：** Sprint 5  
**模块：** 商品浏览（Item Browse Module）  
**版本：** v1.0  
**日期：** 2026-07-11  
**作者：** Tech Lead（ChatGPT）  
**状态：** Approved

---

# 一、Sprint Goal

Sprint 5 的目标是在 Sprint 4 商品发布能力基础上，完成商品浏览能力。

完成后，平台应支持：

- 商品列表
- 商品详情
- 分类筛选
- 关键字搜索
- 分页
- 排序
- 浏览次数统计
- 商品详情缓存

Sprint 5 完成后，Campus Market 将具备完整的"发布 + 浏览"能力。

---

# 二、Scope

## In Scope

### 商品列表

GET /api/items

支持：

- 分页
- 分类筛选
- 校区筛选
- 成色筛选
- 价格区间
- 关键字搜索
- 排序

---

### 商品详情

GET /api/items/{id}

展示：

- 商品基本信息
- 商品图片
- 商品描述
- 卖家信息
- 分类信息
- 浏览次数
- 发布时间

进入详情：

viewCount +1

---

### Redis

缓存：

商品详情

TTL：

30 Minutes

---

## Out of Scope

本 Sprint 不实现：

- 收藏
- 点赞
- 评论
- 举报
- 热门商品
- 推荐算法
- Elasticsearch
- GEO 距离排序

这些属于后续 Sprint。

---

# 三、Business Rules

浏览接口统一过滤：

```
deleted = 0
status = ON_SALE
audit_status = APPROVED
```

禁止展示：

- 已删除商品
- 已下架商品
- 未审核商品
- 审核失败商品

如果商品不存在：

统一返回：

```
Result.fail("商品不存在")
```

不得暴露：

- deleted
- rejected
- not found

之间的区别。

---

# 四、Database Review

## tb_item

Review：

无需修改表结构。

当前字段满足需求。

---

## tb_category

无需修改。

---

## 新增索引

本 Sprint：

不新增索引。

原因：

当前数据规模较小。

后续根据：

EXPLAIN

慢 SQL

再决定。

---

# 五、API Design

## 1、商品列表

GET

```
/api/items
```

Query：

| 参数       | 默认   |
| ---------- | ------ |
| current    | 1      |
| pageSize   | 10     |
| categoryId | null   |
| campus     | null   |
| condition  | null   |
| keyword    | null   |
| priceMin   | null   |
| priceMax   | null   |
| sort       | newest |

pageSize：

最大：

20

排序：

- newest
- priceAsc
- priceDesc

返回：

```
Result<Page<ItemListVO>>
```

---

## 2、商品详情

GET

```
/api/items/{id}
```

返回：

```
Result<ItemDetailVO>
```

---

# 六、DTO Design

新增：

## ItemQueryDTO

字段：

- current
- pageSize
- categoryId
- campus
- condition
- keyword
- priceMin
- priceMax
- sort

Controller：

禁止直接接收多个 RequestParam。

统一使用 DTO。

---

# 七、VO Design

采用：

列表 VO

+

详情 VO

分离。

---

## ItemListVO

包含：

- id
- name
- coverImage
- price
- originalPrice
- categoryName
- sellerName
- campus
- condition
- viewCount
- createTime

不返回：

- description
- images
- meetPlace

---

## ItemDetailVO

返回：

完整信息：

- 商品
- 图片
- 描述
- 分类
- 卖家
- 浏览次数
- 发布时间

后续：

允许扩展：

- isFavorite
- sellerOtherItems

---

# 八、Service Design

继续使用：

ItemService

新增：

```
Page<ItemListVO> listItems(ItemQueryDTO query)

ItemDetailVO getItemDetail(Long id)
```

禁止：

Controller：

拼 SQL。

所有查询：

Service 完成。

---

# 九、Mapper Design

继续使用：

MyBatis-Plus

采用：

LambdaQueryWrapper

Page

selectPage

selectById

本 Sprint：

不新增 XML。

---

# 十、Cache Design

仅缓存：

商品详情。

Redis Key：

```
cache:item:{id}
```

TTL：

30 Minutes

更新商品：

删除缓存。

删除商品：

删除缓存。

列表：

不缓存。

---

# 十一、Permission Design

浏览接口：

全部匿名。

继续加入：

PublicUrls

```
GET /api/items

GET /api/items/{id}
```

无需登录。

---

# 十二、Performance Design

分页：

默认：

10

最大：

20

图片：

列表：

仅第一张。

详情：

全部。

浏览次数：

数据库：

```
view_count=view_count+1
```

原子更新。

禁止：

N+1 查询。

卖家信息：

批量查询。

---

# 十三、Implementation Requirements

必须：

- DTO → Service → VO
- Bean Validation
- Result
- CacheClient
- LambdaQueryWrapper
- MyBatis-Plus 分页

禁止：

- Entity 返回前端
- Controller 写业务
- 自定义分页
- 新增 XML（无必要）
- 修改 Sprint1~4 已完成逻辑

---

# 十四、Acceptance Criteria

必须完成：

- 商品列表
- 商品详情
- 搜索
- 分类筛选
- 分页
- 排序
- 浏览次数
- Redis 缓存

Compile：

```
mvn clean compile
```

必须通过。

Spring Boot：

必须启动。

API：

至少验证：

商品列表：

- 正常
- 分类
- 搜索
- 分页

商品详情：

- 正常
- 不存在
- 已删除

数据库：

验证：

viewCount

正确增加。

---

# 十五、Claude Implementation Workflow

收到本 Design Review 后：

第一步：

输出：

Sprint 5 Review Sync

内容：

- Confirmed
- Changed
- New
- Risks
- Implementation Checklist

Review Sync 完成后：

开始编码。

编码完成：

执行：

- Compile
- Run
- API Test
- Database Verify

最后：

输出：

Sprint 5 Delivery Report

不得跳过任何步骤。

---

# Final Decision

Sprint 5 Design Review：

Approved

允许进入：

Implementation

Tech Lead：

ChatGPT

Developer：

Claude