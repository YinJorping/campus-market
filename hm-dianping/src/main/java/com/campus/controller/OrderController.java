package com.campus.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.dto.CreateOrderDTO;
import com.campus.dto.OrderQueryDTO;
import com.campus.service.IOrderService;
import com.campus.vo.OrderDetailVO;
import com.campus.vo.OrderListVO;
import com.hmdp.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Resource
    private IOrderService orderService;

    @PostMapping
    public Result createOrder(@Valid @RequestBody CreateOrderDTO dto) {
        return orderService.createOrder(dto);
    }

    @GetMapping("/buyer")
    public Result pageBuyerOrders(OrderQueryDTO query) {
        Page<OrderListVO> page = orderService.pageBuyerOrders(query);
        return Result.ok(page);
    }

    @GetMapping("/seller")
    public Result pageSellerOrders(OrderQueryDTO query) {
        Page<OrderListVO> page = orderService.pageSellerOrders(query);
        return Result.ok(page);
    }

    @GetMapping("/{id}")
    public Result getOrderDetail(@PathVariable Long id) {
        OrderDetailVO vo = orderService.getOrderDetail(id);
        if (vo == null) {
            return Result.fail("订单不存在");
        }
        return Result.ok(vo);
    }

    @PutMapping("/{id}/confirm")
    public Result confirmOrder(@PathVariable Long id) {
        return orderService.confirmOrder(id);
    }

    @PutMapping("/{id}/reject")
    public Result rejectOrder(@PathVariable Long id) {
        return orderService.rejectOrder(id);
    }

    @PutMapping("/{id}/cancel")
    public Result cancelOrder(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }

    @PutMapping("/{id}/complete")
    public Result completeOrder(@PathVariable Long id) {
        return orderService.completeOrder(id);
    }
}
