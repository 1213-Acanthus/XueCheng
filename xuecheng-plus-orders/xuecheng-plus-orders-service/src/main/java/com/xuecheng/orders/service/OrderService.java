package com.xuecheng.orders.service;

import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.orders.model.dto.AddOrderDto;
import com.xuecheng.orders.model.dto.PayRecordDto;
import com.xuecheng.orders.model.dto.PayStatusDto;
import com.xuecheng.orders.model.po.XcPayRecord;

//订单相关service
public interface OrderService {
    public PayRecordDto createOrder(String userId, AddOrderDto addOrderDto);

    public XcPayRecord getPayRecordByPayno(String payNo);

    /**
     * 请求支付宝查询支付结果
     *
     * @param payNo 支付记录id
     * @return 支付记录信息
     */
    public PayRecordDto queryPayResult(String payNo);

    //保存支付状态
    public void saveAliPayStatus(PayStatusDto payStatusDto);
    //发送通知结果
    public void notifyPayResult(MqMessage mqMessage);
}
