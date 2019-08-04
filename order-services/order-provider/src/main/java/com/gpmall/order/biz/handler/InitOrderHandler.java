package com.gpmall.order.biz.handler;

import com.gpmall.commons.tool.exception.BizException;
import com.gpmall.order.biz.callback.SendEmailCallback;
import com.gpmall.order.biz.callback.TransCallback;
import com.gpmall.order.biz.context.CreateOrderContext;
import com.gpmall.order.biz.context.TransHandlerContext;
import com.gpmall.order.constant.OrderRetCode;
import com.gpmall.order.constants.OrderConstants;
import com.gpmall.order.dal.entitys.Order;
import com.gpmall.order.dal.entitys.OrderItem;
import com.gpmall.order.dal.persistence.OrderItemMapper;
import com.gpmall.order.dal.persistence.OrderMapper;
import com.gpmall.order.utils.GlobalIdGeneratorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 腾讯课堂搜索【咕泡学院】
 * 官网：www.gupaoedu.com
 * 风骚的Mic 老师
 * create-date: 2019/8/1-下午5:01
 * 初始化订单
 */

@Slf4j
@Component
public class InitOrderHandler extends AbstractTransHandler {

    @Autowired
    SendEmailCallback sendEmailCallback;

    @Autowired
    OrderMapper orderMapper;

    @Autowired
    OrderItemMapper orderItemMapper;

    @Autowired
    GlobalIdGeneratorUtil globalIdGeneratorUtil;

    private final String ORDER_GLOBAL_ID_CACHE_KEY="ORDER_ID";
    private final String ORDER_ITEM_GLOBAL_ID_CACHE_KEY="ORDER_ITEM_ID";
    @Override
    public TransCallback getTransCallback() {
        return sendEmailCallback;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public boolean handle(TransHandlerContext context) {
        log.info("begin InitOrderHandler :context:"+context);
        Order order=new Order();
        try {
            //TODO 幂等校验（防止订单重复提交）
            CreateOrderContext createOrderContext=(CreateOrderContext)context;
            String orderId = globalIdGeneratorUtil.getNextSeq(ORDER_GLOBAL_ID_CACHE_KEY, 1);
            order.setOrderId(orderId);
            order.setUserId(Long.valueOf(createOrderContext.getUserId()));
            order.setBuyerNick(createOrderContext.getBuyerNickName());
            order.setPayment(createOrderContext.getOrderTotal());
            order.setCreateTime(new Date());
            order.setUpdateTime(new Date());
            order.setStatus(OrderConstants.ORDER_STATUS_INIT);
            orderMapper.insert(order); //保存订单
            List<Long> buyProductIds=new ArrayList<>();
            createOrderContext.getCartProductDtoList().parallelStream().forEach(cartProductDto -> {
                OrderItem orderItem = new OrderItem();
                orderItem.setId(globalIdGeneratorUtil.getNextSeq(ORDER_ITEM_GLOBAL_ID_CACHE_KEY, 1));
                orderItem.setItemId(String.valueOf(cartProductDto.getProductId()));
                orderItem.setOrderId(String.valueOf(orderId));
                orderItem.setNum(Math.toIntExact(cartProductDto.getProductNum()));
                orderItem.setPrice(cartProductDto.getSalePrice());
                orderItem.setTitle(cartProductDto.getProductName());
                orderItem.setPicPath(cartProductDto.getProductImg());
                orderItem.setTotalFee(cartProductDto.getSalePrice().multiply(BigDecimal.valueOf(cartProductDto.getProductNum())));
                buyProductIds.add(cartProductDto.getProductId());
                orderItemMapper.insert(orderItem);
            });
            createOrderContext.setOrderId(orderId);
            createOrderContext.setBuyProductIds(buyProductIds);
        }catch (Exception e){
            log.error("InitOrderHandler occur Exception :"+e);
            throw new BizException(OrderRetCode.DB_SAVE_EXCEPTION.getCode(),OrderRetCode.DB_SAVE_EXCEPTION.getMessage());
        }
        return true;
    }
}
