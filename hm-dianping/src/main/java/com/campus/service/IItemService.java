package com.campus.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.dto.ItemQueryDTO;
import com.campus.dto.SaveItemDTO;
import com.campus.dto.UpdateItemDTO;
import com.campus.entity.Item;
import com.campus.vo.ItemDetailVO;
import com.campus.vo.ItemListVO;
import com.campus.vo.ItemNearbyVO;
import com.hmdp.dto.Result;

import java.util.List;

public interface IItemService extends IService<Item> {

    Result createItem(SaveItemDTO dto, Long sellerId);

    Result updateItem(Long id, Long userId, UpdateItemDTO dto);

    Result deleteItem(Long id, Long userId);

    Page<ItemListVO> listItems(ItemQueryDTO query);

    ItemDetailVO getItemDetail(Long id);

    List<ItemNearbyVO> nearbyItems(Double x, Double y, Integer radius, Integer limit);
}