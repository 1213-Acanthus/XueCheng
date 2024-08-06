package com.xuecheng.learning.service;

import com.alibaba.fastjson.JSON;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.learning.config.PayNotifyConfig;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MqMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

//接收消息通知
@Service
@Slf4j
public class ReceivePayNotifyService {
    @Autowired
    MyCourseTablesService myCourseTablesService;
    @RabbitListener(queues = PayNotifyConfig.PAYNOTIFY_QUEUE)
    public void receive(Message message){

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //解析出消息
        byte[] body = message.getBody();
        String jsonString = new String(body);
        //转成对象
        MqMessage mqMessage = JSON.parseObject(jsonString, MqMessage.class);
        //解析消息内容
        String chooseCourseId = mqMessage.getBusinessKey1();//选课id
        String orderType = mqMessage.getBusinessKey2();//订单类型
        //学习中心服务只要购买课程类的订单结果
        if (orderType.equals("60201")){
            //根据消息内容更新选课记录表并向我的课程表表插入
            boolean b = myCourseTablesService.saveChooseCourseSuccess(chooseCourseId);
            if (!b){
                XueChengPlusException.cast("保存选课记录状态失败");
            }
        }

    }

}
