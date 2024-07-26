package com.xuecheng.content.service.jobhandler;

import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MessageProcessAbstract;
import com.xuecheng.messagesdk.service.MqMessageService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

//课程发布的任务类
@Component
@Slf4j
public class CoursePublishTask extends MessageProcessAbstract {
    //任务调度入口
    @XxlJob("CoursePublishHandler")
    public void coursePublishHandler(){
        //分片参数
        int shardIndex = XxlJobHelper.getShardIndex();//执行器的序号，从0开始
        int shardTotal = XxlJobHelper.getShardTotal();//执行器总数

        //调用抽象类的方法执行任务
        process(shardIndex,shardTotal,"course_publish",30,60);
    }

    //执行课程发布任务的逻辑 度过此方法抛出异常就代表执行失败
    @Override
    public boolean execute(MqMessage mqMessage) {
        //从mqMessage拿到课程id
        Long courseId = Long.valueOf(mqMessage.getBusinessKey1());
        //向ES写索引数据
        saveCourseIndex(mqMessage,courseId);


        //向redis写缓存

        //课程静态化上传到minio
        generateCourseHtml(mqMessage,courseId);



        //返回true表示任务完成
        return true;
    }

    private void generateCourseHtml(MqMessage mqMessage,long courseId){
        Long taskId = mqMessage.getId();
        MqMessageService mqMessageService = this.getMqMessageService();
        //做任务幂等性处理
        //查询数据库 取出该阶段执行状态
        int stageOne = mqMessageService.getStageOne(taskId);
        if(stageOne>0){
            log.debug("课程静态化任务完成，无需处理");
            return;
        }
        //开始进行课程静态化处理
//        int i=1/0;

        //..任务处理完成写任务状态为完成
        mqMessageService.completedStageOne(taskId);
    }
    //保存课程索引信息
    private void saveCourseIndex(MqMessage mqMessage,long courseId){
        Long taskId = mqMessage.getId();
        MqMessageService mqMessageService = this.getMqMessageService();
        //做任务幂等性处理
        //查询数据库 取出该阶段执行状态
        int stageTwo = mqMessageService.getStageTwo(taskId);
        if(stageTwo>0){
            log.debug("课程索引信息已写入，无需处理");
            return;
        }
        //查询课程信息，调用搜索服务添加索引

        //..任务处理完成写任务状态为完成
        mqMessageService.completedStageTwo(taskId);
    }
}
