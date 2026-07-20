package com.campus.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.dto.CreateOrderDTO;
import com.campus.dto.OrderQueryDTO;
import com.campus.entity.Item;
import com.campus.entity.Order;
import com.campus.enums.AuditStatus;
import com.campus.enums.ItemStatus;
import com.campus.enums.OrderStatus;
import com.campus.mapper.ItemMapper;
import com.campus.mapper.OrderMapper;
import com.campus.service.IOrderService;
import com.campus.vo.OrderDetailVO;
import com.campus.vo.OrderListVO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.utils.UserHolder;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.GEO_ITEM_KEY;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    @Resource
    private ItemMapper itemMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional
    public Result createOrder(CreateOrderDTO dto) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return Result.fail("请先登录");
        }
        Long buyerId = userDTO.getId();

        Item item = itemMapper.selectById(dto.getItemId());
        if (item == null || item.getDeleted()
                || ItemStatus.ON_SALE.getValue() != item.getStatus()
                || AuditStatus.APPROVED.getValue() != item.getAuditStatus()) {
            return Result.fail("商品不存在或已下架");
        }

        if (item.getSellerId().equals(buyerId)) {
            return Result.fail("不能预约自己的商品");
        }

        LambdaUpdateWrapper<Item> itemWrapper = new LambdaUpdateWrapper<>();
        itemWrapper.eq(Item::getId, dto.getItemId())
                   .eq(Item::getStatus, ItemStatus.ON_SALE.getValue())
                   .set(Item::getStatus, ItemStatus.RESERVED.getValue());
        int rows = itemMapper.update(null, itemWrapper);
        if (rows == 0) {
            return Result.fail("商品已被预约");
        }
        stringRedisTemplate.opsForZSet().remove(GEO_ITEM_KEY, dto.getItemId().toString());

        Order order = new Order();
        order.setItemId(dto.getItemId());
        order.setPrice(item.getPrice());
        order.setBuyerId(buyerId);
        order.setSellerId(item.getSellerId());
        order.setStatus(OrderStatus.PENDING.getValue());
        save(order);

        return Result.ok(order.getId());
    }

    @Override
    public Page<OrderListVO> pageBuyerOrders(OrderQueryDTO query) {
        UserDTO userDTO = UserHolder.getUser();
        Long buyerId = userDTO != null ? userDTO.getId() : null;
        if (buyerId == null) {
            return buildEmptyPage(query);
        }

        int cappedSize = Math.min(query.getPageSize() != null ? query.getPageSize() : 10, 20);
        int current = query.getCurrent() != null ? query.getCurrent() : 1;
        Page<Order> page = new Page<>(current, cappedSize);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getBuyerId, buyerId);
        if (query.getStatus() != null) {
            wrapper.eq(Order::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Order::getCreateTime);
        page = page(page, wrapper);

        List<Order> orders = page.getRecords();
        if (orders.isEmpty()) {
            return new Page<>(current, cappedSize, 0);
        }

        List<Long> itemIds = orders.stream().map(Order::getItemId).distinct().collect(Collectors.toList());
        List<Long> sellerIds = orders.stream().map(Order::getSellerId).distinct().collect(Collectors.toList());

        Map<Long, Item> itemMap = itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, i -> i));
        Map<Long, String> sellerNameMap = sellerIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(sellerIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> StrUtil.isNotBlank(u.getNickName()) ? u.getNickName() : "匿名用户"));

        List<OrderListVO> voList = new ArrayList<>();
        for (Order order : orders) {
            OrderListVO vo = new OrderListVO();
            vo.setId(order.getId());
            vo.setItemId(order.getItemId());
            vo.setPrice(order.getPrice());
            vo.setStatus(order.getStatus());
            vo.setCreateTime(order.getCreateTime());
            Item item = itemMap.get(order.getItemId());
            if (item != null) {
                vo.setItemName(item.getName());
                String[] imgs = item.getImages().split(",");
                vo.setItemCoverImage(imgs.length > 0 && StrUtil.isNotBlank(imgs[0]) ? imgs[0] : "");
            }
            vo.setCounterpartyName(sellerNameMap.getOrDefault(order.getSellerId(), "匿名用户"));
            voList.add(vo);
        }

        Page<OrderListVO> resultPage = new Page<>(current, cappedSize, page.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public Page<OrderListVO> pageSellerOrders(OrderQueryDTO query) {
        UserDTO userDTO = UserHolder.getUser();
        Long sellerId = userDTO != null ? userDTO.getId() : null;
        if (sellerId == null) {
            return buildEmptyPage(query);
        }

        int cappedSize = Math.min(query.getPageSize() != null ? query.getPageSize() : 10, 20);
        int current = query.getCurrent() != null ? query.getCurrent() : 1;
        Page<Order> page = new Page<>(current, cappedSize);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getSellerId, sellerId);
        if (query.getStatus() != null) {
            wrapper.eq(Order::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Order::getCreateTime);
        page = page(page, wrapper);

        List<Order> orders = page.getRecords();
        if (orders.isEmpty()) {
            return new Page<>(current, cappedSize, 0);
        }

        List<Long> itemIds = orders.stream().map(Order::getItemId).distinct().collect(Collectors.toList());
        List<Long> buyerIds = orders.stream().map(Order::getBuyerId).distinct().collect(Collectors.toList());

        Map<Long, Item> itemMap = itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, i -> i));
        Map<Long, String> buyerNameMap = buyerIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(buyerIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> StrUtil.isNotBlank(u.getNickName()) ? u.getNickName() : "匿名用户"));

        List<OrderListVO> voList = new ArrayList<>();
        for (Order order : orders) {
            OrderListVO vo = new OrderListVO();
            vo.setId(order.getId());
            vo.setItemId(order.getItemId());
            vo.setPrice(order.getPrice());
            vo.setStatus(order.getStatus());
            vo.setCreateTime(order.getCreateTime());
            Item item = itemMap.get(order.getItemId());
            if (item != null) {
                vo.setItemName(item.getName());
                String[] imgs = item.getImages().split(",");
                vo.setItemCoverImage(imgs.length > 0 && StrUtil.isNotBlank(imgs[0]) ? imgs[0] : "");
            }
            vo.setCounterpartyName(buyerNameMap.getOrDefault(order.getBuyerId(), "匿名用户"));
            voList.add(vo);
        }

        Page<OrderListVO> resultPage = new Page<>(current, cappedSize, page.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public OrderDetailVO getOrderDetail(Long id) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return null;
        }
        Long userId = userDTO.getId();

        Order order = getById(id);
        if (order == null) {
            return null;
        }
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            return null;
        }

        Item item = itemMapper.selectById(order.getItemId());
        User buyer = userMapper.selectById(order.getBuyerId());
        User seller = userMapper.selectById(order.getSellerId());

        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setItemId(order.getItemId());
        vo.setPrice(order.getPrice());
        vo.setStatus(order.getStatus());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());

        if (item != null) {
            vo.setItemName(item.getName());
            String[] imgs = item.getImages().split(",");
            List<String> imageList = new ArrayList<>();
            for (String img : imgs) {
                if (StrUtil.isNotBlank(img)) {
                    imageList.add(img);
                }
            }
            vo.setItemImages(imageList);
            vo.setItemCoverImage(imageList.isEmpty() ? "" : imageList.get(0));
        }

        if (buyer != null) {
            vo.setBuyerId(buyer.getId());
            vo.setBuyerName(StrUtil.isNotBlank(buyer.getNickName()) ? buyer.getNickName() : "匿名用户");
            vo.setBuyerAvatar(buyer.getIcon());
        }
        if (seller != null) {
            vo.setSellerId(seller.getId());
            vo.setSellerName(StrUtil.isNotBlank(seller.getNickName()) ? seller.getNickName() : "匿名用户");
            vo.setSellerAvatar(seller.getIcon());
        }

        return vo;
    }

    @Override
    @Transactional
    public Result confirmOrder(Long id) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return Result.fail("请先登录");
        }
        Long userId = userDTO.getId();

        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Order::getId, id)
               .eq(Order::getSellerId, userId)
               .eq(Order::getStatus, OrderStatus.PENDING.getValue())
               .set(Order::getStatus, OrderStatus.CONFIRMED.getValue());
        boolean updated = update(wrapper);
        if (!updated) {
            return Result.fail("操作失败：订单不存在、状态已变更或无权限");
        }

        return Result.ok();
    }

    @Override
    @Transactional
    public Result rejectOrder(Long id) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return Result.fail("请先登录");
        }
        Long userId = userDTO.getId();

        Order order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!order.getSellerId().equals(userId)) {
            return Result.fail("无权限");
        }

        LambdaUpdateWrapper<Order> orderWrapper = new LambdaUpdateWrapper<>();
        orderWrapper.eq(Order::getId, id)
                    .eq(Order::getStatus, OrderStatus.PENDING.getValue())
                    .set(Order::getStatus, OrderStatus.REJECTED.getValue());
        boolean orderUpdated = update(orderWrapper);
        if (!orderUpdated) {
            return Result.fail("订单状态已变更");
        }

        LambdaUpdateWrapper<Item> itemWrapper = new LambdaUpdateWrapper<>();
        itemWrapper.eq(Item::getId, order.getItemId())
                   .eq(Item::getStatus, ItemStatus.RESERVED.getValue())
                   .set(Item::getStatus, ItemStatus.ON_SALE.getValue());
        itemMapper.update(null, itemWrapper);

        Item item = itemMapper.selectById(order.getItemId());
        if (item != null && item.getX() != null && item.getY() != null) {
            stringRedisTemplate.opsForGeo().add(GEO_ITEM_KEY,
                    new Point(item.getX(), item.getY()), item.getId().toString());
        }

        return Result.ok();
    }

    @Override
    @Transactional
    public Result cancelOrder(Long id) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return Result.fail("请先登录");
        }
        Long userId = userDTO.getId();

        Order order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!order.getBuyerId().equals(userId)) {
            return Result.fail("无权限");
        }

        LambdaUpdateWrapper<Order> orderWrapper = new LambdaUpdateWrapper<>();
        orderWrapper.eq(Order::getId, id)
                    .eq(Order::getStatus, OrderStatus.PENDING.getValue())
                    .set(Order::getStatus, OrderStatus.CANCELLED.getValue());
        boolean orderUpdated = update(orderWrapper);
        if (!orderUpdated) {
            return Result.fail("订单状态已变更");
        }

        LambdaUpdateWrapper<Item> itemWrapper = new LambdaUpdateWrapper<>();
        itemWrapper.eq(Item::getId, order.getItemId())
                   .eq(Item::getStatus, ItemStatus.RESERVED.getValue())
                   .set(Item::getStatus, ItemStatus.ON_SALE.getValue());
        itemMapper.update(null, itemWrapper);

        Item item = itemMapper.selectById(order.getItemId());
        if (item != null && item.getX() != null && item.getY() != null) {
            stringRedisTemplate.opsForGeo().add(GEO_ITEM_KEY,
                    new Point(item.getX(), item.getY()), item.getId().toString());
        }

        return Result.ok();
    }

    @Override
    @Transactional
    public Result completeOrder(Long id) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return Result.fail("请先登录");
        }
        Long userId = userDTO.getId();

        Order order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!order.getSellerId().equals(userId)) {
            return Result.fail("无权限");
        }

        LambdaUpdateWrapper<Order> orderWrapper = new LambdaUpdateWrapper<>();
        orderWrapper.eq(Order::getId, id)
                    .eq(Order::getStatus, OrderStatus.CONFIRMED.getValue())
                    .set(Order::getStatus, OrderStatus.FINISHED.getValue());
        boolean orderUpdated = update(orderWrapper);
        if (!orderUpdated) {
            return Result.fail("订单状态已变更");
        }

        LambdaUpdateWrapper<Item> itemWrapper = new LambdaUpdateWrapper<>();
        itemWrapper.eq(Item::getId, order.getItemId())
                   .eq(Item::getStatus, ItemStatus.RESERVED.getValue())
                   .set(Item::getStatus, ItemStatus.SOLD.getValue());
        itemMapper.update(null, itemWrapper);
        stringRedisTemplate.opsForZSet().remove(GEO_ITEM_KEY, order.getItemId().toString());

        return Result.ok();
    }

    private Page<OrderListVO> buildEmptyPage(OrderQueryDTO query) {
        int cappedSize = Math.min(query.getPageSize() != null ? query.getPageSize() : 10, 20);
        int current = query.getCurrent() != null ? query.getCurrent() : 1;
        return new Page<>(current, cappedSize, 0);
    }
}
