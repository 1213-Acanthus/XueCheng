package com.xuecheng.content.service.impl;

import com.alibaba.fastjson.JSON;
import com.xuecheng.base.exception.CommonError;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.config.MultipartSupportConfig;
import com.xuecheng.content.feignclient.MediaServiceClient;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.CourseMarketMapper;
import com.xuecheng.content.mapper.CoursePublishMapper;
import com.xuecheng.content.mapper.CoursePublishPreMapper;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.model.dto.TeachPlanDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseMarket;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.content.model.po.CoursePublishPre;
import com.xuecheng.content.service.CourseBaseInfoService;
import com.xuecheng.content.service.CoursePublishService;
import com.xuecheng.content.service.TeachPlanService;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MqMessageService;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

//课程发布相关接口实现
@Slf4j
@Service
public class CoursePublishServiceImpl implements CoursePublishService {
    @Autowired
    CourseBaseInfoService courseBaseInfoService;
    @Autowired
    TeachPlanService teachPlanService;
    @Autowired
    CourseMarketMapper courseMarketMapper;
    @Autowired
    CoursePublishPreMapper coursePublishPreMapper;
    @Autowired
    CourseBaseMapper courseBaseMapper;
    @Autowired
    CoursePublishMapper coursePublishMapper;
    @Autowired
    MqMessageService mqMessageService;
    @Autowired
    MediaServiceClient mediaServiceClient;
    @Autowired
    RedisTemplate redisTemplate;
    @Autowired
    RedissonClient redissonClient;


    @Override
    public CoursePreviewDto getCoursePreviewInfo(Long courseId) {
        CoursePreviewDto coursePreviewDto = new CoursePreviewDto();
        //查询课程的基本信息以及营销信息
        CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseBaseInfo(courseId);
        coursePreviewDto.setCourseBase(courseBaseInfo);
        //课程计划信息
        List<TeachPlanDto> teachPlanTree = teachPlanService.findTeachPlanTree(courseId);
        coursePreviewDto.setTeachplans(teachPlanTree);
        return coursePreviewDto;
    }

    @Override
    @Transactional
    public void commitAudit(Long companyId, Long courseId) {
        //课程审核状态为已提交则不允许提交
        CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseBaseInfo(courseId);
        if (courseBaseInfo == null) {
            XueChengPlusException.cast("找不到这个课程");
        }
        String auditStatus = courseBaseInfo.getAuditStatus();
        if (auditStatus.equals("202003")) {
            XueChengPlusException.cast("‘已提交请等待审核");
        }
        //todo：本机构只能提交本机构的课程！！！

        //课程必需信息必须填写
        String pic = courseBaseInfo.getPic();
        if (StringUtils.isEmpty(pic)) {
            XueChengPlusException.cast("请上传课程图片");
        }
        List<TeachPlanDto> teachPlanTree = teachPlanService.findTeachPlanTree(courseId);
        if (teachPlanTree == null || teachPlanTree.size() == 0) {
            XueChengPlusException.cast("请填写课程计划");
        }

        //查询课程基本信息 营销信息 计划等信息插入到课程预发布表
        CoursePublishPre coursePublishPre = new CoursePublishPre();
        BeanUtils.copyProperties(courseBaseInfo, coursePublishPre);
//        coursePublishPre.setId(courseId);
        //设置机构id
        coursePublishPre.setCompanyId(companyId);
        //营销信息
        CourseMarket courseMarket = courseMarketMapper.selectById(courseId);
        //转json
        String market_json = JSON.toJSONString(courseMarket);
        coursePublishPre.setMarket(market_json);
        //计划信息
        String teachplan_json = JSON.toJSONString(teachPlanTree);
        coursePublishPre.setTeachplan(teachplan_json);
        //状态为已提交
        coursePublishPre.setStatus("202003");
        //提交时间
        coursePublishPre.setCreateDate(LocalDateTime.now());
        //查询预发布表 有记录就更新 没有就插入
        CoursePublishPre coursePublishPreObj = coursePublishPreMapper.selectById(courseId);
        if (coursePublishPreObj == null) {
            //插入
            coursePublishPreMapper.insert(coursePublishPre);
        } else {
            coursePublishPreMapper.updateById(coursePublishPreObj);
        }

        //更新课程基本信息表的审核状态为已提交
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        courseBase.setAuditStatus("202003");//审核状态为已提交
        courseBaseMapper.updateById(courseBase);
    }

    @Override
    @Transactional
    public void publish(Long companyId, Long courseId) {
        //查询预发布表
        //如果没有审核通过 不允许发布
        CoursePublishPre coursePublishPre = coursePublishPreMapper.selectById(courseId);
        if (coursePublishPre == null) {
            XueChengPlusException.cast("课程没有审核记录，无法发布");
        }
        String status = coursePublishPre.getStatus();
        if (!status.equals("202004")) {
            XueChengPlusException.cast("课程没有审核通过不允许发布");
        }
        //向课程发布表写入数据
        CoursePublish coursePublish = new CoursePublish();
        BeanUtils.copyProperties(coursePublishPre, coursePublish);
        //先查询课程发布表 如果有就更新 没有再添加
        CoursePublish coursePublishObj = coursePublishMapper.selectById(courseId);
        if (coursePublishObj == null) {
            coursePublishMapper.insert(coursePublish);
        } else {
            coursePublishMapper.updateById(coursePublish);
        }
        //向消息表写入数据
        saveCoursePublishMessage(courseId);

        //将发布表数据删除
        coursePublishPreMapper.deleteById(courseId);
    }

    @Override
    public File generateCourseHtml(Long courseId) {
        //配置freemarker
        Configuration configuration = new Configuration(Configuration.getVersion());

        //加载模板
        //选指定模板路径,classpath下templates下
        //最终的静态文件
        File htmlFile = null;
        try {
            //得到classpath路径
//            String classpath = this.getClass().getResource("/").getPath();
//            configuration.setDirectoryForTemplateLoading(new File(classpath + "/templates/"));
//          configuration.setDirectoryForTemplateLoading(new File("E:\\CODE\\XueCheng\\xuecheng-plus-content\\xuecheng-plus-content-service\\src\\main\\resources\\templates"));
            configuration.setTemplateLoader(new ClassTemplateLoader(this.getClass().getClassLoader(), "/templates"));
            ;
            //设置字符编码
            configuration.setDefaultEncoding("utf-8");

            //指定模板文件名称
            Template template = null;
            template = configuration.getTemplate("course_template.ftl");


            //准备数据
            CoursePreviewDto coursePreviewInfo = this.getCoursePreviewInfo(courseId);

            Map<String, Object> map = new HashMap<>();
            map.put("model", coursePreviewInfo);

            //静态化
            //参数1：模板，参数2：数据模型
            String content = FreeMarkerTemplateUtils.processTemplateIntoString(template, map);
            System.out.println(content);
            //将静态化内容输出到文件中
            InputStream inputStream = IOUtils.toInputStream(content);
            htmlFile = File.createTempFile("coursepublish", ".html");
            //输出流
            FileOutputStream outputStream = new FileOutputStream(htmlFile);
            IOUtils.copy(inputStream, outputStream);
        } catch (Exception e) {
            log.error("页面静态化出现问题 课程：{}", courseId, e);
            e.printStackTrace();
        }
        return htmlFile;
    }

    @Override
    public void uploadCourseHtml(Long courseId, File file) {
        MultipartFile multipartFile = MultipartSupportConfig.getMultipartFile(file);
        String result = mediaServiceClient.uploadFile(multipartFile, "course/" + courseId + ".html");

        if (result == null) {
            log.debug("运行了降级逻辑,上传结果为null，课程id：{}", courseId);
            XueChengPlusException.cast("上传静态文件过程中存在异常");
        }

    }

    //根据课程id查询课程发布信息
    @Override
    public CoursePublish getCoursePublish(Long courseId) {
        CoursePublish coursePublish = coursePublishMapper.selectById(courseId);
        return coursePublish;
    }
//解决缓存穿透
//    @Override
//    public CoursePublish getCoursePublishCache(Long courseId) {
//        //使用布隆过滤器 0一定不存在 1不一定存在
//
//        //从缓存中查询
//        Object jsonObj = redisTemplate.opsForValue().get("course:" + courseId);
//        if (jsonObj!=null){
//            //缓存中有 直接返回数据
//            String jsonString = jsonObj.toString();
//            if (jsonString.equals("null")){
//                return null;
//            }
//            CoursePublish coursePublish = JSON.parseObject(jsonString, CoursePublish.class);
//            return coursePublish;
//        }
//        //缓存中没有 查出来存入redis并返回值
//        CoursePublish coursePublish = getCoursePublish(courseId);
//        //缓存空值
//        if (coursePublish!=null) {
//            String jsonString = JSON.toJSONString(coursePublish);
//            redisTemplate.opsForValue().set("course:" + courseId, jsonString,30+new Random().nextInt(100), TimeUnit.SECONDS);
//        }
//            return coursePublish;
//    }

    //使用同步锁解决缓存击穿
//    @Override
//    public CoursePublish getCoursePublishCache(Long courseId) {
//        //使用布隆过滤器 0一定不存在 1不一定存在
//        //spring默认单例模式所以同步锁用this当锁大家用的就是同一把锁
//        //从缓存中查询
//
//        Object jsonObj = redisTemplate.opsForValue().get("course:" + courseId);
//        if (jsonObj != null) {
//            //缓存中有 直接返回数据
//            String jsonString = jsonObj.toString();
//            if (jsonString.equals("null")) {
//                return null;
//            }
//            CoursePublish coursePublish = JSON.parseObject(jsonString, CoursePublish.class);
//            return coursePublish;
//        }
//        //同步锁不能锁住不同的实例 不同实例还是会访问多次数据库 所以要使用分布式锁
//        //分布式锁 把锁单独存储起来 不同市里的jvm进行争抢 可以在数据库中使用乐观锁 或者redis的setnx 或者zookeeper
//        //使用setnx要设置过期时间 否则他会一直占用着这个lock
//        synchronized (this) {
//            //再次查缓存 可能已经有人查了数据库存入缓存了
//            jsonObj = redisTemplate.opsForValue().get("course:" + courseId);
//            if (jsonObj != null) {
//                //缓存中有 直接返回数据
//                String jsonString = jsonObj.toString();
//                if (jsonString.equals("null")) {
//                    return null;
//                }
//                CoursePublish coursePublish = JSON.parseObject(jsonString, CoursePublish.class);
//                return coursePublish;
//            }
//            //缓存中没有 查出来存入redis并返回值
//            CoursePublish coursePublish = getCoursePublish(courseId);
//            //缓存空值
//            if (coursePublish != null) {
//                String jsonString = JSON.toJSONString(coursePublish);
//                redisTemplate.opsForValue().set("course:" + courseId, jsonString, 30 + new Random().nextInt(100), TimeUnit.SECONDS);
//            }
//            return coursePublish;
//        }
//    }
    //使用分布式锁 redisson
    @Override
    public CoursePublish getCoursePublishCache(Long courseId) {
        //使用布隆过滤器 0一定不存在 1不一定存在
        //spring默认单例模式所以同步锁用this当锁大家用的就是同一把锁
        //从缓存中查询

        Object jsonObj = redisTemplate.opsForValue().get("course:" + courseId);
        if (jsonObj != null) {
            //缓存中有 直接返回数据
            String jsonString = jsonObj.toString();
            if (jsonString.equals("null")) {
                return null;
            }
            CoursePublish coursePublish = JSON.parseObject(jsonString, CoursePublish.class);
            return coursePublish;
        }
        //同步锁不能锁住不同的实例 不同实例还是会访问多次数据库 所以要使用分布式锁
        //分布式锁 把锁单独存储起来 不同市里的jvm进行争抢 可以在数据库中使用乐观锁 或者redis的setnx 或者zookeeper
        //使用setnx要设置过期时间 否则他会一直占用着这个lock
        //调用redis的方法 执行setnx 谁成功谁拿到锁执行
        //锁可能在获取锁的进程正在执行操作的过程中过期 这时候另外的进程也可能进入 这样就会导致冲突
//        Boolean lock = redisTemplate.opsForValue().setIfAbsent("coursequerylock:"+courseId, 01);
        RLock lock = redissonClient.getLock("coursequerylock:" + courseId);
        //获取分布式锁
        lock.lock();
        try {
            //再次查缓存 可能已经有人查了数据库存入缓存了
            jsonObj = redisTemplate.opsForValue().get("course:" + courseId);
            if (jsonObj != null) {
                //缓存中有 直接返回数据
                String jsonString = jsonObj.toString();
                if (jsonString.equals("null")) {
                    return null;
                }
                CoursePublish coursePublish = JSON.parseObject(jsonString, CoursePublish.class);
                return coursePublish;
            }
            //缓存中没有 查出来存入redis并返回值
            CoursePublish coursePublish = getCoursePublish(courseId);
            //缓存空值
            if (coursePublish != null) {
                String jsonString = JSON.toJSONString(coursePublish);
                redisTemplate.opsForValue().set("course:" + courseId, jsonString, 30 + new Random().nextInt(100), TimeUnit.SECONDS);
            }
            return coursePublish;
        }finally {
            //手动释放锁
            lock.unlock();
    }
}

private void saveCoursePublishMessage(Long courseId) {
    MqMessage mqMessage = mqMessageService.addMessage("course_publish", String.valueOf(courseId), null, null);
    if (mqMessage == null) {
        XueChengPlusException.cast(CommonError.UNKOWN_ERROR);
    }
}
}
