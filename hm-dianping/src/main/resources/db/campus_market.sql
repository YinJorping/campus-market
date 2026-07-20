-- =============================================
-- CampusMarket 校园二手交易平台 数据库建表脚本
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for tb_category
-- ----------------------------
DROP TABLE IF EXISTS `tb_category`;
CREATE TABLE `tb_category` (
  `id`          bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name`        varchar(32)         NOT NULL                COMMENT '分类名称',
  `parent_id`   bigint(20) UNSIGNED NOT NULL DEFAULT 0      COMMENT '父分类ID，0为一级分类',
  `icon`        varchar(255)        DEFAULT NULL            COMMENT '图标',
  `sort`        int(3) UNSIGNED     DEFAULT NULL            COMMENT '排序',
  `create_time` timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_parent` (`parent_id`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact COMMENT = '二手商品分类表';

-- ----------------------------
-- Table structure for tb_item
-- ----------------------------
DROP TABLE IF EXISTS `tb_item`;
CREATE TABLE `tb_item` (
  `id`              bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `name`            varchar(128)        NOT NULL                COMMENT '商品名称',
  `category_id`     bigint(20) UNSIGNED NOT NULL                COMMENT '分类ID',
  `seller_id`       bigint(20) UNSIGNED NOT NULL                COMMENT '卖家用户ID',
  `images`          varchar(2048)       NOT NULL                COMMENT '商品图片，多张以逗号分隔',
  `description`     varchar(2048)       DEFAULT ''              COMMENT '商品描述',
  `price`           bigint(10) UNSIGNED NOT NULL                COMMENT '售价，单位分',
  `original_price`  bigint(10) UNSIGNED DEFAULT NULL            COMMENT '原价/参考价，单位分',
  `campus`          varchar(128)        DEFAULT ''              COMMENT '校区/生活区',
  `meet_place`      varchar(255)        DEFAULT ''              COMMENT '交易地点',
  `x`               double UNSIGNED     DEFAULT NULL            COMMENT '经度',
  `y`               double UNSIGNED     DEFAULT NULL            COMMENT '纬度',
  `item_condition`  tinyint(2) UNSIGNED NOT NULL DEFAULT 3      COMMENT '新旧程度：1全新 2几乎全新 3良好 4一般 5有瑕疵',
  `status`          tinyint(1) UNSIGNED NOT NULL DEFAULT 1      COMMENT '商品状态：1在售 2已预约 3已售 4下架',
  `audit_status`    tinyint(1) UNSIGNED NOT NULL DEFAULT 0      COMMENT '审核状态：0待审核 1审核通过 2审核不通过',
  `deleted`         tinyint(1) UNSIGNED NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
  `view_count`      int(10) UNSIGNED    NOT NULL DEFAULT 0      COMMENT '浏览次数',
  `consult_count`   int(10) UNSIGNED    NOT NULL DEFAULT 0      COMMENT '咨询量',
  `sold_time`       timestamp           NULL DEFAULT NULL       COMMENT '成交时间',
  `create_time`     timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_category` (`category_id`) USING BTREE,
  KEY `idx_seller` (`seller_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact COMMENT = '二手商品表';

-- ----------------------------
-- Table structure for tb_favorite
-- ----------------------------
DROP TABLE IF EXISTS `tb_favorite`;
CREATE TABLE `tb_favorite` (
  `id`          bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id`     bigint(20) UNSIGNED NOT NULL                COMMENT '用户ID',
  `item_id`     bigint(20) UNSIGNED NOT NULL                COMMENT '商品ID',
  `create_time` timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（收藏时间）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_item` (`user_id`, `item_id`) USING BTREE,
  KEY `idx_item` (`item_id`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact COMMENT = '收藏表';

-- ----------------------------
-- Table structure for tb_credit_record
-- ----------------------------
DROP TABLE IF EXISTS `tb_credit_record`;
CREATE TABLE `tb_credit_record` (
  `id`          bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id`     bigint(20) UNSIGNED NOT NULL                COMMENT '用户ID',
  `type`        tinyint(2) UNSIGNED NOT NULL                COMMENT '事件类型：1交易完成 2爽约 3好评 4差评 5管理员调整',
  `score`       int(8)              NOT NULL                COMMENT '信誉分变动值（可正可负）',
  `ref_id`      bigint(20) UNSIGNED DEFAULT NULL            COMMENT '关联业务ID（如订单ID）',
  `remark`      varchar(255)        DEFAULT ''              COMMENT '备注说明',
  `create_time` timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user` (`user_id`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact COMMENT = '信誉事件记录表';

-- ----------------------------
-- Table structure for tb_report
-- ----------------------------
DROP TABLE IF EXISTS `tb_report`;
CREATE TABLE `tb_report` (
  `id`          bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '举报ID',
  `reporter_id` bigint(20) UNSIGNED NOT NULL                COMMENT '举报人用户ID',
  `target_type` tinyint(1) UNSIGNED NOT NULL                COMMENT '举报类型：1商品 2用户',
  `target_id`   bigint(20) UNSIGNED NOT NULL                COMMENT '被举报对象ID',
  `reason`      varchar(500)        NOT NULL                COMMENT '举报原因',
  `status`      tinyint(1) UNSIGNED NOT NULL DEFAULT 0      COMMENT '处理状态：0待处理 1已通过 2已驳回',
  `admin_id`    bigint(20) UNSIGNED DEFAULT NULL            COMMENT '处理管理员ID',
  `create_time` timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_reporter` (`reporter_id`) USING BTREE,
  KEY `idx_target` (`target_type`, `target_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact COMMENT = '举报记录表';

-- ----------------------------
-- Table structure for tb_order
-- ----------------------------
DROP TABLE IF EXISTS `tb_order`;
CREATE TABLE `tb_order` (
  `id`          bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `item_id`     bigint(20) UNSIGNED NOT NULL                COMMENT '商品ID',
  `price`       bigint(10) UNSIGNED NOT NULL                COMMENT '成交价，单位分（下单时价格快照）',
  `seller_id`   bigint(20) UNSIGNED NOT NULL                COMMENT '卖家用户ID',
  `buyer_id`    bigint(20) UNSIGNED NOT NULL                COMMENT '买家用户ID',
  `status`      tinyint(1) UNSIGNED NOT NULL DEFAULT 1      COMMENT '订单状态：1待确认 2已确认 3已完成 4已拒绝 5已取消',
  `create_time` timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_buyer` (`buyer_id`) USING BTREE,
  KEY `idx_seller` (`seller_id`) USING BTREE,
  KEY `idx_item` (`item_id`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact COMMENT = '交易订单表';

SET FOREIGN_KEY_CHECKS = 1;
