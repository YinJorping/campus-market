项目交接文档（Sprint 6 关闭后修订版）
修订日期：2026-07-11

一、项目背景
我正在将一个黑马点评（Spring Boot + Redis）项目重构为校园二手交易平台 CampusMarket。项目保留了原项目的 Redis 技术能力（缓存、GEO、Feed、Lua 秒杀、分布式锁等），但业务全部重新设计。

技术栈：Spring Boot 2.3.12.RELEASE + MyBatis-Plus 3.4.3 + Redis 6.2 + MySQL 8.0 + Redisson 3.13.6 + Hutool 5.7.17
运行环境：JDK 21.0.8（pom.xml 声明 1.8，Lombok 覆盖为 1.18.34，需 --add-opens JVM 参数）
数据库：hmdp，root / 123456
Redis：Docker campus-redis:6.2，密码 123321

二、已完成的工作

Sprint 1 — 数据模型
6 个 Entity：Item, Category, Favorite, CreditRecord, Report, Order
6 个 Enum：ItemStatus, ItemCondition, AuditStatus, OrderStatus, ReportTargetType, ReportStatus
完整的 campus_market.sql（6 张新表）
⚠ tb_user 还未扩展 creditScore / creditLevel 字段（Sprint 6/7 补充）

Sprint 2 — Mapper 层
6 个 Mapper，继承 BaseMapper
@MapperScan 配置双包：com.hmdp.mapper + com.campus.mapper
type-aliases-package：com.hmdp.entity;com.campus.entity

Sprint 3 — 分类模块
CategoryController, ICategoryService, CategoryServiceImpl
com.campus.vo.CategoryVO
匿名接口白名单 PublicUrls.java（所有公开 API 集中管理）
scanBasePackages 配置双包扫描

Sprint 4 — 商品发布模块 [Closed]
SaveItemDTO（含 Bean Validation: @NotBlank/@NotNull/@Positive）
UpdateItemDTO（全可选字段，配合 LambdaUpdateWrapper.set() 按 null 判断更新）
IItemService, ItemServiceImpl（完整业务校验 + @Transactional）
ItemController（POST/PUT/DELETE，Controller 层 UserHolder 手动鉴权）
hibernate-validator 依赖（Bean Validation）
WebExceptionAdvice 新增 @Valid 异常处理器
application.yaml 配置：market.audit.enabled: false
pom.xml：Lombok 覆盖为 1.18.34 + jvmArguments 含 --add-opens（JDK 17+ 兼容）

Sprint 5 — 商品浏览模块 [Closed]
ItemQueryDTO（9 个查询参数：current/pageSize/categoryId/campus/condition/keyword/priceMin/priceMax/sort）
ItemListVO（列表视图：id/name/coverImage/price/originalPrice/categoryName/sellerName/campus/condition/viewCount/createTime）
ItemDetailVO（详情视图：全量字段+分类名+卖家信息+图片数组，预留 isFavorite/sellerOtherCount）
GET /api/items（列表：分页+分类筛选+校区筛选+成色筛选+价格区间+关键字搜索+排序，pageSize上限20）
GET /api/items/{id}（详情：CacheClient 缓存+viewCount 原子更新+空值防穿透，TTL 30min）
Redis 缓存：cache:item:{id}，更新/删除时主动淘汰
keyword 搜索：name+description 双字段 LIKE，100 字符截断
批量查询防 N+1：categoryMapper.selectBatchIds + userMapper.selectBatchIds

三、当前目录结构
com/
├── campus/              ← 新业务代码
│   ├── constant/PublicUrls.java
│   ├── controller/CategoryController.java, ItemController.java, FavoriteController.java
│   ├── dto/SaveItemDTO.java, UpdateItemDTO.java
│   ├── entity/Item.java, Category.java, Favorite.java, CreditRecord.java, Report.java, Order.java
│   ├── enums/ItemStatus.java, ItemCondition.java, AuditStatus.java, OrderStatus.java, ReportTargetType.java, ReportStatus.java
│   ├── mapper/ItemMapper.java, CategoryMapper.java, FavoriteMapper.java, CreditRecordMapper.java, ReportMapper.java, OrderMapper.java
│   ├── service/ICategoryService.java, IItemService.java, IFavoriteService.java
│   ├── service/impl/CategoryServiceImpl.java, ItemServiceImpl.java, FavoriteServiceImpl.java
│   └── vo/CategoryVO.java
├── hmdp/                ← 原项目代码（保留，后续 Sprint 末尾统一删除）
│   ├── config/MvcConfig.java（已改：PublicUrls.PUBLIC_URLS）
│   ├── config/WebExceptionAdvice.java（已改：新增 @Valid 处理）
│   ├── config/RedissonConfig.java（未改）
│   ├── controller/…（未改）
│   ├── service/…（未改）
│   ├── utils/…（未改，最后迁移到 com.campus.utils）
│   └── HmDianPingApplication.java（已改：scanBasePackages）
resources/
├── application.yaml（已改：type-aliases-package 双包 + market.audit.enabled）
├── db/campus_market.sql（新）
├── db/legacy_data.sql（旧，保留）
├── mapper/VoucherMapper.xml（旧）
├── seckill.lua（旧）
└── unlock.lua（旧）

四、技术要点
双包共存：新代码放 com.campus，旧代码在 com.hmdp，通过 @SpringBootApplication(scanBasePackages = {"com.hmdp", "com.campus"}) 和 @MapperScan({"com.hmdp.mapper", "com.campus.mapper"}) 共存。旧模块在最后一轮 Sprint 统一删除，utils 迁移到 com.campus.utils。
匿名接口白名单：PublicUrls.PUBLIC_URLS 集中管理，MvcConfig 引用该常量。
枚举规范：所有状态字段使用枚举的 getValue()，禁止魔法数字。
更新策略：统一使用 LambdaUpdateWrapper.set() 更新非 null 字段，配合 @Transactional。
返回格式：统一使用 com.hmdp.dto.Result（Result.ok() / Result.fail()）。
审核配置：market.audit.enabled: false，控制创建商品时 auditStatus 默认 APPROVED 还是 PENDING。

五、已知问题（Sprint 6 Closed 时记录）

1. JDK 17/21 + MyBatis-Plus 3.4.3 兼容性
   运行时需 --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED
   pom.xml 已配置 jvmArguments，生产部署 java -jar 也需携带。

2. Maven 本地仓库被 Windows Defender 锁定
   C:\Users\yyyj\.m2\repository\org\hibernate 目录无法访问。重启电脑可解决。

3. PUT / DELETE 边界场景未实测（Not Verified）
   - PUT 越权修改：代码逻辑与 POST 未登录鉴权链路一致，低风险
   - DELETE 不存在/重复删除：代码逻辑与 PUT 已删除拦截一致，低风险
   - market.audit.enabled=true 配置路径：三元表达式编译期已验证，低风险
   以上保留 Not Verified，后续统一回归测试补齐。

4. Technical Debt
   - Controller 层手动 UserHolder 鉴权（Spring Interceptor 不支持按 HTTP Method 区分）
   - tb_user 未扩展 creditScore/creditLevel（Sprint 6/7 补充）
   - tb_order 缺 price 字段（下次修改 campus_market.sql 时补上）
   - TD-9：缓存删除失败时缺少容错机制（updateItem/deleteItem 先写 DB 再删 Redis，Redis 失败则缓存保留旧数据至 TTL 过期）
	   - TD-10：pageFavorites total 基于 tb_favorite 计数，静默过滤后 records 可能少于 total

六、开发规范
每个 Sprint 先输出 API 设计 → 确认 → 编码 → 编译 → 启动 → 测试
状态使用枚举，禁止魔法数字
Service 继承 IService<T> 和 ServiceImpl，写操作加 @Transactional
Controller 只做参数接收和调用 Service
返回 Result 统一格式
修改前先分析影响范围，修改后列出修改文件
每个 Sprint 关闭前须通过 Tech Lead 独立审计

七、当前项目状态
Sprint 1-5: Closed (2026-07-11)
Sprint 6: Closed (2026-07-11) — 收藏模块
Sprint 7: 待启动（预约/订单模块）
代码已提交到 Git（commit 6e85106）
开发环境有运行中的 Redis（Docker campus-redis:6.2）和 MySQL 8.0（Windows 服务）
