package com.campus.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.dto.CreateOrderDTO;
import com.campus.dto.OrderQueryDTO;
import com.campus.entity.Order;
import com.campus.vo.OrderDetailVO;
import com.campus.vo.OrderListVO;
import com.hmdp.dto.Result;

public interface IOrderService extends IService<Order> {

    Result createOrder(CreateOrderDTO dto);

    Page<OrderListVO> pageBuyerOrders(OrderQueryDTO query);

    Page<OrderListVO> pageSellerOrders(OrderQueryDTO query);

    OrderDetailVO getOrderDetail(Long id);

    Result confirmOrder(Long id);

    Result rejectOrder(Long id);

    Result cancelOrder(Long id);

    Result completeOrder(Long id);
}
