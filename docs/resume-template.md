CampusMarket——校园二手交易平台

技术栈：Spring Boot / MySQL / MyBatis-Plus / Redis GEO / Docker / JUnit 5

项目简介：一个覆盖商品发布、分类浏览、收藏、订单交易、GEO附近搜索全流程的校园二手交易平台后端。针对交易场景中的"并发预约冲突"与"地理位置搜索低效"痛点，基于CAS乐观锁+Redis GEO设计了无锁化并发控制与空间索引方案，通过68项企业级Backend Acceptance验收。

● 全订单状态流转采用CAS乐观锁（UPDATE WHERE status=?），无锁化保证并发安全，JUnit 5+CountDownLatch编写真实并发集成测试，双买家同时预约仅1人成功。
● 基于Redis GEO实现附近商品搜索，覆盖商品CRUD+订单流转6个生命周期节点的全量同步，启动预热机制保障Redis重启后自动恢复。
● 设计Cache-Aside旁路缓存+空值防穿透，CacheClient统一封装，商品详情毫秒级响应。
● 订单价格下单时快照至tb_order（bigint存分），后续商品调价不影响历史订单；取消/拒绝订单自动恢复商品上架状态+GEO索引。
● Docker Compose一键部署（MySQL 8.0+Redis 7+App），健康检查+启动顺序控制+SQL自动初始化。
