package com.campus.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.dto.ItemQueryDTO;
import com.campus.dto.SaveItemDTO;
import com.campus.dto.UpdateItemDTO;
import com.campus.entity.Category;
import com.campus.entity.Favorite;
import com.campus.entity.Item;
import com.campus.enums.AuditStatus;
import com.campus.enums.ItemCondition;
import com.campus.enums.ItemStatus;
import com.campus.mapper.CategoryMapper;
import com.campus.mapper.FavoriteMapper;
import com.campus.mapper.ItemMapper;
import com.campus.service.IItemService;
import com.campus.vo.ItemDetailVO;
import com.campus.vo.ItemListVO;
import com.campus.vo.ItemNearbyVO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_ITEM_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_ITEM_TTL;
import static com.hmdp.utils.RedisConstants.GEO_ITEM_KEY;

@Service
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private FavoriteMapper favoriteMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Value("${market.audit.enabled:false}")
    private boolean auditEnabled;

    @Override
    @Transactional
    public Result createItem(SaveItemDTO dto, Long sellerId) {
        // 1. 校验分类存在
        Category category = categoryMapper.selectById(dto.getCategoryId());
        if (category == null) {
            return Result.fail("分类不存在");
        }

        // 2. 业务校验
        if (dto.getName().length() > 128) {
            return Result.fail("商品名称不能超过128个字符");
        }
        String[] images = dto.getImages().split(",");
        if (images.length > 9) {
            return Result.fail("商品图片最多9张");
        }
        for (String img : images) {
            if (img.isEmpty()) {
                return Result.fail("图片路径不能为空");
            }
            if (img.length() > 500) {
                return Result.fail("单张图片路径过长");
            }
        }
        if (StrUtil.isNotBlank(dto.getDescription()) && dto.getDescription().length() > 2000) {
            return Result.fail("商品描述不能超过2000个字符");
        }
        if (dto.getOriginalPrice() != null && dto.getOriginalPrice() < dto.getPrice()) {
            return Result.fail("原价不能小于售价");
        }
        if (dto.getCampus().length() > 128) {
            return Result.fail("校区名称不能超过128个字符");
        }
        if (dto.getMeetPlace().length() > 255) {
            return Result.fail("交易地点不能超过255个字符");
        }
        if ((dto.getX() == null) != (dto.getY() == null)) {
            return Result.fail("经纬度必须同时填写或同时为空");
        }
        try {
            ItemCondition.fromValue(dto.getItemCondition());
        } catch (IllegalArgumentException e) {
            return Result.fail("商品成色无效");
        }

        // 3. 创建商品实体
        Item item = new Item();
        item.setName(dto.getName());
        item.setCategoryId(dto.getCategoryId());
        item.setSellerId(sellerId);
        item.setImages(dto.getImages());
        item.setDescription(dto.getDescription());
        item.setPrice(dto.getPrice());
        item.setOriginalPrice(dto.getOriginalPrice());
        item.setCampus(dto.getCampus());
        item.setMeetPlace(dto.getMeetPlace());
        item.setX(dto.getX());
        item.setY(dto.getY());
        item.setItemCondition(dto.getItemCondition());
        item.setStatus(ItemStatus.ON_SALE.getValue());
        item.setAuditStatus(auditEnabled ? AuditStatus.PENDING.getValue() : AuditStatus.APPROVED.getValue());
        item.setDeleted(false);
        item.setViewCount(0);
        item.setConsultCount(0);

        save(item);
        if (item.getX() != null && item.getY() != null) {
            stringRedisTemplate.opsForGeo().add(GEO_ITEM_KEY,
                    new Point(item.getX(), item.getY()), item.getId().toString());
        }
        return Result.ok(item.getId());
    }

    @Override
    @Transactional
    public Result updateItem(Long id, Long userId, UpdateItemDTO dto) {
        Item exist = getById(id);
        if (exist == null) {
            return Result.fail("商品不存在");
        }
        if (!exist.getSellerId().equals(userId)) {
            return Result.fail("无权修改");
        }
        if (exist.getDeleted() || exist.getStatus().equals(ItemStatus.SOLD.getValue()) || exist.getStatus().equals(ItemStatus.RESERVED.getValue())) {
            return Result.fail("商品已无法修改");
        }

        // 业务校验
        if (dto.getCategoryId() != null) {
            Category category = categoryMapper.selectById(dto.getCategoryId());
            if (category == null) {
                return Result.fail("分类不存在");
            }
        }
        if (dto.getImages() != null) {
            String[] images = dto.getImages().split(",");
            if (images.length > 9) {
                return Result.fail("商品图片最多9张");
            }
            for (String img : images) {
                if (img.isEmpty()) {
                    return Result.fail("图片路径不能为空");
                }
                if (img.length() > 500) {
                    return Result.fail("单张图片路径过长");
                }
            }
        }
        if ((dto.getX() == null) != (dto.getY() == null)) {
            return Result.fail("经纬度必须同时填写或同时为空");
        }

        LambdaUpdateWrapper<Item> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Item::getId, id);
        if (dto.getName() != null) wrapper.set(Item::getName, dto.getName());
        if (dto.getCategoryId() != null) wrapper.set(Item::getCategoryId, dto.getCategoryId());
        if (dto.getImages() != null) wrapper.set(Item::getImages, dto.getImages());
        if (dto.getDescription() != null) wrapper.set(Item::getDescription, dto.getDescription());
        if (dto.getPrice() != null) wrapper.set(Item::getPrice, dto.getPrice());
        if (dto.getOriginalPrice() != null) wrapper.set(Item::getOriginalPrice, dto.getOriginalPrice());
        if (dto.getCampus() != null) wrapper.set(Item::getCampus, dto.getCampus());
        if (dto.getMeetPlace() != null) wrapper.set(Item::getMeetPlace, dto.getMeetPlace());
        if (dto.getX() != null) wrapper.set(Item::getX, dto.getX());
        if (dto.getY() != null) wrapper.set(Item::getY, dto.getY());
        if (dto.getItemCondition() != null) wrapper.set(Item::getItemCondition, dto.getItemCondition());
        update(wrapper);
        if (dto.getX() != null && dto.getY() != null) {
            stringRedisTemplate.opsForGeo().add(GEO_ITEM_KEY,
                    new Point(dto.getX(), dto.getY()), id.toString());
        }
        stringRedisTemplate.delete(CACHE_ITEM_KEY + id);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result deleteItem(Long id, Long userId) {
        LambdaUpdateWrapper<Item> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Item::getId, id)
               .eq(Item::getSellerId, userId)
               .eq(Item::getDeleted, false);
        wrapper.set(Item::getDeleted, true);
        boolean updated = update(wrapper);
        if (!updated) {
            return Result.fail("无权删除或商品已删除");
        }
        stringRedisTemplate.delete(CACHE_ITEM_KEY + id);
        stringRedisTemplate.opsForZSet().remove(GEO_ITEM_KEY, id.toString());
        return Result.ok();
    }

    @Override
    public Page<ItemListVO> listItems(ItemQueryDTO query) {
        // 1. 构建查询条件
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Item::getDeleted, false)
               .eq(Item::getStatus, ItemStatus.ON_SALE.getValue())
               .eq(Item::getAuditStatus, AuditStatus.APPROVED.getValue());

        if (query.getCategoryId() != null) {
            wrapper.eq(Item::getCategoryId, query.getCategoryId());
        }
        if (StrUtil.isNotBlank(query.getCampus())) {
            wrapper.eq(Item::getCampus, query.getCampus());
        }
        if (query.getCondition() != null) {
            wrapper.eq(Item::getItemCondition, query.getCondition());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            if (query.getKeyword().length() > 100) {
                query.setKeyword(query.getKeyword().substring(0, 100));
            }
            wrapper.and(w -> w.like(Item::getName, query.getKeyword())
                              .or()
                              .like(Item::getDescription, query.getKeyword()));
        }
        if (query.getPriceMin() != null) {
            wrapper.ge(Item::getPrice, query.getPriceMin());
        }
        if (query.getPriceMax() != null) {
            wrapper.le(Item::getPrice, query.getPriceMax());
        }

        // 2. 排序
        String sort = query.getSort();
        if ("priceAsc".equals(sort)) {
            wrapper.orderByAsc(Item::getPrice);
        } else if ("priceDesc".equals(sort)) {
            wrapper.orderByDesc(Item::getPrice);
        } else {
            wrapper.orderByDesc(Item::getCreateTime);
        }

        // 3. 分页
        int pageSize = Math.min(query.getPageSize() != null ? query.getPageSize() : 10, 20);
        Page<Item> page = new Page<>(query.getCurrent() != null ? query.getCurrent() : 1, pageSize);
        page = page(page, wrapper);

        // 4. 空结果直接返回
        List<Item> items = page.getRecords();
        if (items.isEmpty()) {
            Page<ItemListVO> emptyPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            return emptyPage;
        }

        // 5. 批量查询分类名
        List<Long> categoryIds = items.stream()
                .map(Item::getCategoryId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> categoryNameMap = Collections.emptyMap();
        if (!categoryIds.isEmpty()) {
            categoryNameMap = categoryMapper.selectBatchIds(categoryIds).stream()
                    .collect(Collectors.toMap(Category::getId, Category::getName));
        }

        // 6. 批量查询卖家昵称
        List<Long> sellerIds = items.stream()
                .map(Item::getSellerId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> sellerNameMap = Collections.emptyMap();
        if (!sellerIds.isEmpty()) {
            sellerNameMap = userMapper.selectBatchIds(sellerIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> StrUtil.isNotBlank(u.getNickName()) ? u.getNickName() : "匿名用户"));
        }

        // 7. 组装 VO
        List<ItemListVO> voList = new ArrayList<>();
        for (Item item : items) {
            ItemListVO vo = new ItemListVO();
            vo.setId(item.getId());
            vo.setName(item.getName());
            String[] imgs = item.getImages().split(",");
            vo.setCoverImage(imgs.length > 0 && StrUtil.isNotBlank(imgs[0]) ? imgs[0] : "");
            vo.setPrice(item.getPrice());
            vo.setOriginalPrice(item.getOriginalPrice());
            vo.setCategoryName(categoryNameMap.getOrDefault(item.getCategoryId(), ""));
            vo.setSellerName(sellerNameMap.getOrDefault(item.getSellerId(), "匿名用户"));
            vo.setCampus(item.getCampus());
            vo.setCondition(item.getItemCondition());
            vo.setViewCount(item.getViewCount());
            vo.setCreateTime(item.getCreateTime());
            voList.add(vo);
        }

        Page<ItemListVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public ItemDetailVO getItemDetail(Long id) {
        // 1. 原子增加浏览次数（先更新再查询，保证返回最新 viewCount）
        LambdaUpdateWrapper<Item> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Item::getId, id)
                     .setSql("view_count = view_count + 1");
        update(updateWrapper);

        // 2. 删除缓存，强制从 DB 查询最新数据
        stringRedisTemplate.delete(CACHE_ITEM_KEY + id);

        // 3. 查 DB + 写缓存
        Item item = cacheClient.queryWithPassThrough(
                CACHE_ITEM_KEY, id, Item.class, this::getById, CACHE_ITEM_TTL, TimeUnit.MINUTES);

        if (item == null) {
            return null;
        }

        // 4. 过滤已删除/已下架/审核未通过
        if (item.getDeleted()
                || ItemStatus.ON_SALE.getValue() != item.getStatus()
                || AuditStatus.APPROVED.getValue() != item.getAuditStatus()) {
            return null;
        }

        // 5. 查询分类名
        Category category = categoryMapper.selectById(item.getCategoryId());

        // 6. 查询卖家信息
        User seller = userMapper.selectById(item.getSellerId());

        // 7. 组装 VO
        ItemDetailVO vo = new ItemDetailVO();
        vo.setId(item.getId());
        vo.setName(item.getName());
        String[] imgs = item.getImages().split(",");
        List<String> imageList = new ArrayList<>();
        for (String img : imgs) {
            if (StrUtil.isNotBlank(img)) {
                imageList.add(img);
            }
        }
        vo.setImages(imageList);
        vo.setDescription(item.getDescription());
        vo.setPrice(item.getPrice());
        vo.setOriginalPrice(item.getOriginalPrice());
        vo.setCampus(item.getCampus());
        vo.setMeetPlace(item.getMeetPlace());
        vo.setX(item.getX());
        vo.setY(item.getY());
        vo.setItemCondition(item.getItemCondition());
        vo.setViewCount(item.getViewCount());
        vo.setCreateTime(item.getCreateTime());
        vo.setSoldTime(item.getSoldTime());
        vo.setCategoryName(category != null ? category.getName() : "");
        if (seller != null) {
            vo.setSellerId(seller.getId());
            vo.setSellerName(StrUtil.isNotBlank(seller.getNickName()) ? seller.getNickName() : "匿名用户");
            vo.setSellerAvatar(seller.getIcon());
        }

        UserDTO currentUser = UserHolder.getUser();
        vo.setIsFavorite(false);
        if (currentUser != null) {
            Integer favCount = favoriteMapper.selectCount(
                    new LambdaQueryWrapper<Favorite>()
                            .eq(Favorite::getUserId, currentUser.getId())
                            .eq(Favorite::getItemId, id));
            vo.setIsFavorite(favCount != null && favCount > 0);
        }
        return vo;
    }

    @Override
    public List<ItemNearbyVO> nearbyItems(Double x, Double y, Integer radius, Integer limit) {
        int cappedLimit = Math.min(limit, 50);
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(GEO_ITEM_KEY,
                        org.springframework.data.redis.domain.geo.GeoReference.fromCoordinate(x, y),
                        new org.springframework.data.geo.Distance(radius),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance().limit(cappedLimit));
        if (results == null || results.getContent().isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Double> distanceMap = new LinkedHashMap<>();
        for (org.springframework.data.geo.GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
            Long itemId = Long.valueOf(result.getContent().getName());
            double dist = result.getDistance().getValue();
            distanceMap.put(itemId, dist);
        }

        List<Long> itemIds = new ArrayList<>(distanceMap.keySet());
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Item::getId, itemIds)
               .eq(Item::getDeleted, false)
               .eq(Item::getStatus, ItemStatus.ON_SALE.getValue())
               .eq(Item::getAuditStatus, AuditStatus.APPROVED.getValue());
        List<Item> items = getBaseMapper().selectList(wrapper);
        Map<Long, Item> itemMap = items.stream().collect(Collectors.toMap(Item::getId, i -> i));

        List<Long> categoryIds = items.stream().map(Item::getCategoryId).distinct().collect(Collectors.toList());
        Map<Long, String> categoryNameMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName));

        List<Long> sellerIds = items.stream().map(Item::getSellerId).distinct().collect(Collectors.toList());
        Map<Long, String> sellerNameMap = sellerIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(sellerIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> StrUtil.isNotBlank(u.getNickName()) ? u.getNickName() : "匿名用户"));

        List<ItemNearbyVO> voList = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : distanceMap.entrySet()) {
            Item item = itemMap.get(entry.getKey());
            if (item == null) {
                continue;
            }
            ItemNearbyVO vo = new ItemNearbyVO();
            vo.setId(item.getId());
            vo.setName(item.getName());
            String[] imgs = item.getImages().split(",");
            vo.setCoverImage(imgs.length > 0 && StrUtil.isNotBlank(imgs[0]) ? imgs[0] : "");
            vo.setPrice(item.getPrice());
            vo.setOriginalPrice(item.getOriginalPrice());
            vo.setCategoryName(categoryNameMap.getOrDefault(item.getCategoryId(), ""));
            vo.setSellerName(sellerNameMap.getOrDefault(item.getSellerId(), "匿名用户"));
            vo.setCampus(item.getCampus());
            vo.setCondition(item.getItemCondition());
            vo.setViewCount(item.getViewCount());
            vo.setDistance(Math.round(entry.getValue() * 100.0) / 100.0);
            vo.setCreateTime(item.getCreateTime());
            voList.add(vo);
        }
        return voList;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadItemGeo() {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Item::getDeleted, false)
               .eq(Item::getStatus, ItemStatus.ON_SALE.getValue())
               .eq(Item::getAuditStatus, AuditStatus.APPROVED.getValue())
               .isNotNull(Item::getX)
               .isNotNull(Item::getY);
        List<Item> items = getBaseMapper().selectList(wrapper);
        for (Item item : items) {
            stringRedisTemplate.opsForGeo().add(GEO_ITEM_KEY,
                    new Point(item.getX(), item.getY()), item.getId().toString());
        }
    }
}