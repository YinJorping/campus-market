package com.campus.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.entity.Category;
import com.campus.entity.Favorite;
import com.campus.entity.Item;
import com.campus.enums.AuditStatus;
import com.campus.enums.ItemStatus;
import com.campus.mapper.CategoryMapper;
import com.campus.mapper.FavoriteMapper;
import com.campus.mapper.ItemMapper;
import com.campus.service.IFavoriteService;
import com.campus.vo.ItemListVO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements IFavoriteService {

    @Resource
    private ItemMapper itemMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional
    public Result favorite(Long itemId) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return Result.fail("请先登录");
        }
        Long userId = userDTO.getId();

        Item item = itemMapper.selectById(itemId);
        if (item == null || item.getDeleted()
                || ItemStatus.ON_SALE.getValue() != item.getStatus()
                || AuditStatus.APPROVED.getValue() != item.getAuditStatus()) {
            return Result.fail("商品不存在");
        }

        Integer count = getBaseMapper().selectCount(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getItemId, itemId));
        if (count != null && count > 0) {
            return Result.ok();
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setItemId(itemId);
        save(favorite);

        return Result.ok();
    }

    @Override
    @Transactional
    public Result unfavorite(Long itemId) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return Result.fail("请先登录");
        }
        Long userId = userDTO.getId();

        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId)
               .eq(Favorite::getItemId, itemId);
        getBaseMapper().delete(wrapper);

        return Result.ok();
    }

    @Override
    public Page<ItemListVO> pageFavorites(int current, int pageSize) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return new Page<>(current, Math.min(pageSize, 20), 0);
        }
        Long userId = userDTO.getId();

        int cappedSize = Math.min(pageSize, 20);
        Page<Favorite> page = new Page<>(current, cappedSize);
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId)
               .orderByDesc(Favorite::getCreateTime);
        page = page(page, wrapper);

        List<Favorite> favorites = page.getRecords();
        if (favorites.isEmpty()) {
            return new Page<>(current, cappedSize, 0);
        }

        List<Long> itemIds = favorites.stream()
                .map(Favorite::getItemId)
                .collect(Collectors.toList());

        LambdaQueryWrapper<Item> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.in(Item::getId, itemIds)
                   .eq(Item::getDeleted, false)
                   .eq(Item::getStatus, ItemStatus.ON_SALE.getValue())
                   .eq(Item::getAuditStatus, AuditStatus.APPROVED.getValue());
        List<Item> items = itemMapper.selectList(itemWrapper);

        Map<Long, Item> itemMap = items.stream()
                .collect(Collectors.toMap(Item::getId, i -> i));

        List<Long> categoryIds = items.stream()
                .map(Item::getCategoryId).distinct()
                .collect(Collectors.toList());
        Map<Long, String> categoryNameMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName));

        List<Long> sellerIds = items.stream()
                .map(Item::getSellerId).distinct()
                .collect(Collectors.toList());
        Map<Long, String> sellerNameMap = sellerIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(sellerIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> StrUtil.isNotBlank(u.getNickName()) ? u.getNickName() : "匿名用户"));

        List<ItemListVO> voList = new ArrayList<>();
        for (Favorite fav : favorites) {
            Item item = itemMap.get(fav.getItemId());
            if (item == null) {
                continue;
            }
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

        Page<ItemListVO> resultPage = new Page<>(current, cappedSize, page.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }
}
