package com.xuecheng.orders.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.utils.IdWorkerUtils;
import com.xuecheng.base.utils.QRCodeUtil;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MqMessageService;
import com.xuecheng.orders.config.AlipayConfig;
import com.xuecheng.orders.config.PayNotifyConfig;
import com.xuecheng.orders.mapper.XcOrdersGoodsMapper;
import com.xuecheng.orders.mapper.XcOrdersMapper;
import com.xuecheng.orders.mapper.XcPayRecordMapper;
import com.xuecheng.orders.model.dto.AddOrderDto;
import com.xuecheng.orders.model.dto.PayRecordDto;
import com.xuecheng.orders.model.dto.PayStatusDto;
import com.xuecheng.orders.model.po.XcOrders;
import com.xuecheng.orders.model.po.XcOrdersGoods;
import com.xuecheng.orders.model.po.XcPayRecord;
import com.xuecheng.orders.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.message.StringFormattedMessage;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
//订单相关接口
public class OrderServiceImpl implements OrderService {
    @Value("${pay.alipay.APP_ID}")
    String APP_ID;
    @Value("${pay.alipay.APP_PRIVATE_KEY}")
    String APP_PRIVATE_KEY;

    @Value("${pay.alipay.ALIPAY_PUBLIC_KEY}")
    String ALIPAY_PUBLIC_KEY;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    XcOrdersMapper xcOrdersMapper;
    @Autowired
    XcOrdersGoodsMapper xcOrdersGoodsMapper;
    @Autowired
    XcPayRecordMapper xcPayRecordMapper;
    @Autowired
    OrderServiceImpl orderService;
    @Autowired
    MqMessageService mqMessageService;
    @Value("${pay.qrcodeurl}")
    String qrcodeurl;


    @Override
    @Transactional
    public PayRecordDto createOrder(String userId, AddOrderDto addOrderDto) {
        //插入订单表 订单主表 和订单明细表
        XcOrders xcOrders = saveXcOrders(userId, addOrderDto);

        //插入支付记录
        XcPayRecord record = createPayRecord(xcOrders);
        Long payNo = record.getPayNo();

        //生成二维码
        QRCodeUtil qrCodeUtil = new QRCodeUtil();
        //支付二维码的url地址
        String url = String.format(qrcodeurl, payNo);
        String qrCode = null;
        try {
            qrCode = qrCodeUtil.createQRCode(url, 200, 200);
        } catch (IOException e) {
            XueChengPlusException.cast("生成支付二维码出错");
        }

        PayRecordDto payRecordDto = new PayRecordDto();
        BeanUtils.copyProperties(record,payRecordDto);
        payRecordDto.setQrcode(qrCode);

        return payRecordDto;
    }

    @Override
    public XcPayRecord getPayRecordByPayno(String payNo) {
        XcPayRecord xcPayRecord = xcPayRecordMapper.selectOne(new LambdaQueryWrapper<XcPayRecord>().eq(XcPayRecord::getPayNo, payNo));
        return xcPayRecord;
    }
    //查询支付结果
    @Override
    public PayRecordDto queryPayResult(String payNo) {
        //查询支付结果
        PayStatusDto payStatusDto = queryPayResultFromAlipay(payNo);
//        System.out.println(payStatusDto);
        //支付成功后 更新支付记录表的支付状态以及订单表的状态为支付成功
        //调用事务方法 需要本方法注入进行事务控制
        orderService.saveAliPayStatus(payStatusDto);
        //要返回最新的支付记录信息
        XcPayRecord payRecordByPayno = getPayRecordByPayno(payNo);
        PayRecordDto payRecordDto = new PayRecordDto();
        BeanUtils.copyProperties(payRecordByPayno,payRecordDto);
        return payRecordDto;
    }
    //请求支付宝查询结果
    public PayStatusDto queryPayResultFromAlipay(String payNo){
        AlipayClient alipayClient = new DefaultAlipayClient(AlipayConfig.URL, APP_ID, APP_PRIVATE_KEY, AlipayConfig.FORMAT, AlipayConfig.CHARSET, ALIPAY_PUBLIC_KEY, AlipayConfig.SIGNTYPE); //获得初始化的AlipayClient
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", payNo);
        //bizContent.put("trade_no", "2014112611001004680073956707");
        request.setBizContent(bizContent.toString());
        //支付宝返回的信息
        String body = null;
        try {
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            if (!response.isSuccess()){
                //交易不成功
                XueChengPlusException.cast("请求支付宝查询支付结果失败");
            }
            body = response.getBody();
        } catch (AlipayApiException e) {
            e.printStackTrace();
            XueChengPlusException.cast("请求支付宝查询支付结果异常");
        }
        //将json串转换为map
        Map bodyMap = JSON.parseObject(body, Map.class);
        Map alipay_trade_query_response = (Map) bodyMap.get("alipay_trade_query_response");
        String trade_no = (String) alipay_trade_query_response.get("trade_no");
        String trade_status = (String) alipay_trade_query_response.get("trade_status");
        String total_amount = (String) alipay_trade_query_response.get("total_amount");

        //解析支付结果
        PayStatusDto payStatusDto = new PayStatusDto();
        payStatusDto.setApp_id(APP_ID);
        payStatusDto.setOut_trade_no(payNo);
        payStatusDto.setTrade_no(trade_no);//支付宝的交易号
        payStatusDto.setTrade_status(trade_status);//交易状态
        payStatusDto.setTotal_amount(total_amount);//总金额
        return payStatusDto;
    }
    @Transactional
    //保存支付宝支付结果
    public void saveAliPayStatus(PayStatusDto payStatusDto){
        String payNo = payStatusDto.getOut_trade_no();//支付记录号
        XcPayRecord payRecordByPayno = getPayRecordByPayno(payNo);
        if (payRecordByPayno == null){
            XueChengPlusException.cast("找不到相关的支付记录");
        }
        //拿到相关订单id
        Long orderId = payRecordByPayno.getOrderId();
        XcOrders xcOrders = xcOrdersMapper.selectById(orderId);
        if (xcOrders == null){
            XueChengPlusException.cast("找不到相关联的订单");
        }
        //拿到本地支付状态
        String tradeStatus = payStatusDto.getTrade_status();
        //如果已经支付成功 那么就不用再进行修改了
        if (tradeStatus.equals("601002")){
            return;
        }
        //如果从支付宝查询到的状态为支付成功 那么就要进行修改
        String trade_Status = payStatusDto.getTrade_status();
        if (trade_Status.equals("TRADE_SUCCESS")){
            //支付宝返回的信息为支付成功


            //更新支付记录表的状态为已支付
            payRecordByPayno.setStatus("601002");
            payRecordByPayno.setOutPayNo(payStatusDto.getTrade_no());//支付宝的订单号
            payRecordByPayno.setOutPayChannel("Alipay");//第三方支付渠道编号
            payRecordByPayno.setPaySuccessTime(LocalDateTime.now());
            xcPayRecordMapper.updateById(payRecordByPayno);

            //更新订单表的状态为支付成功
            xcOrders.setStatus("600002");//订单状态为交易成功
            xcOrdersMapper.updateById(xcOrders);

            //将消息写入数据库
            MqMessage mqMessage = mqMessageService.addMessage("payresult_notify", xcOrders.getOutBusinessId(), xcOrders.getOrderType(), null);
            //发送消息
            notifyPayResult(mqMessage);
        }

    }

    @Override
    public void notifyPayResult(MqMessage mqMessage) {
        //消息内容
        String jsonMessage = JSON.toJSONString(mqMessage);
        //创建一个持久化消息
        Message mq = MessageBuilder.withBody(jsonMessage.getBytes(StandardCharsets.UTF_8)).setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
        //全局消息id
        Long id = mqMessage.getId();
        //使用correlation指定回调方法
        CorrelationData correlationData = new CorrelationData(id.toString());
        correlationData.getFuture().addCallback(result->{
            if (result.isAck()){
                //消息成功发送到了交换机
                log.debug("消息发送成功：{}",jsonMessage);
                //将消息从数据库表删掉
                mqMessageService.completed(id);
            }else {
                //消息发送失败
                log.debug("消息发送失败：{}",jsonMessage);
            }
        },ex->{
            //发生异常
            log.debug("消息发送时发生异常：{}",jsonMessage);
        });
        //发送消息
        rabbitTemplate.convertAndSend(PayNotifyConfig.PAYNOTIFY_EXCHANGE_FANOUT,"",mq,correlationData);
    }

    //插入订单表 订单主表 和订单明细表
    public XcOrders saveXcOrders(String userId, AddOrderDto addOrderDto){
        //幂等性判断 同一个选课记录只能有一个订单
        XcOrders xcOrders = getOrderByBusinessId(addOrderDto.getOutBusinessId());
        if(xcOrders != null){
            return xcOrders;
        }
        //插入订单表 订单主表 和订单明细表
        xcOrders = new XcOrders();
        //使用雪花算法生成订单号
        xcOrders.setId(IdWorkerUtils.getInstance().nextId());
        xcOrders.setTotalPrice(addOrderDto.getTotalPrice());
        xcOrders.setCreateDate(LocalDateTime.now());
        xcOrders.setStatus("600001");//订单未支付
        xcOrders.setUserId(userId);
        xcOrders.setOrderType("60201");//订单类型为购买课程
        xcOrders.setOrderName(addOrderDto.getOrderName());
        xcOrders.setOrderDescrip(addOrderDto.getOrderDescrip());
        xcOrders.setOrderDetail(addOrderDto.getOrderDetail());
        xcOrders.setOutBusinessId(addOrderDto.getOutBusinessId());//选课则记录了选课表的主键
        int insert = xcOrdersMapper.insert(xcOrders);
        if (insert<=0){
            //数据写入失败
            XueChengPlusException.cast("添加订单失败");
        }
        Long id = xcOrders.getId();
        //插入订单明细表
        //将前端传入明细的json串转成list
        String orderDetailJson = addOrderDto.getOrderDetail();
        List<XcOrdersGoods> xcOrdersGoods = JSON.parseArray(orderDetailJson, XcOrdersGoods.class);
        //遍历列表 插入订单明细
        xcOrdersGoods.forEach(goods -> {
            goods.setOrderId(id);
            xcOrdersGoodsMapper.insert(goods);//插入订单明细
        });

        return xcOrders;
    }
    //插入支付记录
    public XcPayRecord createPayRecord(XcOrders orders){
        if(orders==null){
            XueChengPlusException.cast("订单不存在");
        }
        if(orders.getStatus().equals("600002")){
            XueChengPlusException.cast("订单已支付");
        }
        XcPayRecord payRecord = new XcPayRecord();
        //生成支付交易流水号
        long payNo = IdWorkerUtils.getInstance().nextId();
        payRecord.setPayNo(payNo);
        payRecord.setOrderId(orders.getId());//商品订单号
        payRecord.setOrderName(orders.getOrderName());
        payRecord.setTotalPrice(orders.getTotalPrice());
        payRecord.setCurrency("CNY");
        payRecord.setCreateDate(LocalDateTime.now());
        payRecord.setStatus("601001");//未支付
        payRecord.setUserId(orders.getUserId());
        xcPayRecordMapper.insert(payRecord);
        return payRecord;

    }


    //生成二维码

    //根据业务id查询订单 业务id是选课记录表中的主键
    public XcOrders getOrderByBusinessId(String businessId){
        XcOrders xcOrders = xcOrdersMapper.selectOne(new LambdaQueryWrapper<XcOrders>().eq(XcOrders::getOutBusinessId, businessId));
        return xcOrders;
    }
}
