package com.campus.constant;

/**
 * 校园二手交易平台 V1 匿名访问白名单
 *
 * 所有在此数组中的路径将跳过 LoginInterceptor 的登录校验。
 * 后续新增公开接口时，只需在此类中添加一行即可，无需修改 MvcConfig。
 */
public class PublicUrls {

    /**
     * 匿名访问白名单
     */
    public static final String[] PUBLIC_URLS = {
            // === 黑马点评原公开路径（保留，向后兼容） ===
            "/shop/**",
            "/voucher/**",
            "/shop-type/**",
            "/upload/**",
            "/blog/hot",
            "/user/code",
            "/user/login",

            // === CampusMarket 公开路径 ===
            // 分类浏览
            "/api/categories/**",
            // 商品列表、详情、搜索、附近（写操作在 Controller 层鉴权）
            "/api/items/**",
            // 热门排行榜
            "/api/hot-items/**",
            // Spring Boot 错误页
            "/error"
    };
}
