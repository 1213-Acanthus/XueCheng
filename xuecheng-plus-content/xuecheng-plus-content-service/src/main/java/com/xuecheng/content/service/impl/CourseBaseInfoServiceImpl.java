package com.xuecheng.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.content.mapper.*;
import com.xuecheng.content.model.dto.*;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseCategory;
import com.xuecheng.content.model.po.CourseMarket;
import com.xuecheng.content.model.po.Teachplan;
import com.xuecheng.content.service.CourseBaseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class CourseBaseInfoServiceImpl implements CourseBaseInfoService {
    @Autowired
    CourseBaseMapper courseBaseMapper;
    @Autowired
    CourseMarketMapper courseMarketMapper;
    @Autowired
    CourseCategoryMapper courseCategoryMapper;
    @Autowired
    CourseTeacherMapper courseTeacherMapper;
    @Autowired
    TeachplanMapper teachplanMapper;
    @Autowired
    TeachplanMediaMapper teachplanMediaMapper;
    @Override
    public PageResult<CourseBase> queryCourseBaseList(Long companyId,PageParams pageParams, QueryCourseParamsDto queryCourseParamsDto) {

        //拼装查询条件
        LambdaQueryWrapper<CourseBase> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        //根据名称模糊查询
        lambdaQueryWrapper.like(StringUtils.isNotEmpty(queryCourseParamsDto.getCourseName()),CourseBase::getName, queryCourseParamsDto.getCourseName());
        //根据课程审核状态查询
        lambdaQueryWrapper.eq(StringUtils.isNotEmpty(queryCourseParamsDto.getAuditStatus()),CourseBase::getAuditStatus,queryCourseParamsDto.getAuditStatus());
        //根据课程发布状态查询
        lambdaQueryWrapper.eq(StringUtils.isNotEmpty(queryCourseParamsDto.getPublishStatus()),CourseBase::getStatus,queryCourseParamsDto.getPublishStatus());
        //根据培训机构id拼接查询条件
        lambdaQueryWrapper.eq(CourseBase::getCompanyId,companyId);


        //创建page查询对象
        Page<CourseBase> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
        //开始分页查询
        Page<CourseBase> courseBasePage = courseBaseMapper.selectPage(page, lambdaQueryWrapper);
        //获取数据列表
        List<CourseBase> items = courseBasePage.getRecords();
        //获取总记录数
        long total = courseBasePage.getTotal();

        PageResult<CourseBase> courseBasePageResult = new PageResult<>(items,total,pageParams.getPageNo(), pageParams.getPageSize());
        return courseBasePageResult;
    }
    //新增课程
    @Override
    @Transactional
    public CourseBaseInfoDto createCourseBase(Long companyId,AddCourseDto dto) {
        //参数合法性校验
//        if (StringUtils.isBlank(dto.getName())) {
//            XueChengPlusException.cast("课程名称为空");
//        }
//
//        if (StringUtils.isBlank(dto.getMt())) {
//            XueChengPlusException.cast("课程分类为空");
//        }
//
//        if (StringUtils.isBlank(dto.getSt())) {
//            XueChengPlusException.cast("课程分类为空");
//        }
//
//        if (StringUtils.isBlank(dto.getGrade())) {
//            XueChengPlusException.cast("课程等级为空");
//        }
//
//        if (StringUtils.isBlank(dto.getTeachmode())) {
//            XueChengPlusException.cast("教育模式为空");
//        }
//
//        if (StringUtils.isBlank(dto.getUsers())) {
//            XueChengPlusException.cast("适应人群为空");
//        }
//
//        if (StringUtils.isBlank(dto.getCharge())) {
//            XueChengPlusException.cast("收费规则为空");
//        }

        //向课程基本信息表写数据
        CourseBase courseBaseNew = new CourseBase();
        //将传入的数据封装到基本信息对象中
//        courseBaseNew.setName(dto.getName());
//        courseBaseNew.setDescription(dto.getDescription());
        //用beanutils
        //只要属性名称一致就可以拷贝
        BeanUtils.copyProperties(dto,courseBaseNew);
        courseBaseNew.setCompanyId(companyId);
        courseBaseNew.setCreateDate(LocalDateTime.now());
        //审核状态默认为未提交
        courseBaseNew.setAuditStatus("202002");
        //发布状态为未发布
        courseBaseNew.setStatus("203001");
        //插入数据库
        int insert = courseBaseMapper.insert(courseBaseNew);
        if(insert<=0){
            throw new RuntimeException("添加课程失败");
        }

        //向课程营销表写数据对象中
        CourseMarket courseMarketNew = new CourseMarket();

        //将页面输入的数据拷贝到对象中
        BeanUtils.copyProperties(dto,courseMarketNew);
        Long courseId = courseBaseNew.getId();
        courseMarketNew.setId(courseId);
        //保存营销信息
        saveCourseMarket(courseMarketNew);
        //从数据库查询课程的详细信息
        CourseBaseInfoDto courseBaseInfo = getCourseBaseInfo(courseId);//用于回显

        return courseBaseInfo;
    }


    //查询课程信息
    @Override
    public CourseBaseInfoDto getCourseBaseInfo(Long courseId){
        //从课程基本信息表查询
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        if(courseBase ==null){
            return null;
        }
        //从课程营销表查询 组装
        CourseMarket courseMarket = courseMarketMapper.selectById(courseId);
        CourseBaseInfoDto courseBaseInfoDto = new CourseBaseInfoDto();
        BeanUtils.copyProperties(courseBase,courseBaseInfoDto);
        BeanUtils.copyProperties(courseMarket,courseBaseInfoDto);

        //通过courseCategoryMapper查询分类信息 将分类名称放在对象中
        //先获取大分类 在目录mapper中进行查找
        String mtName = courseCategoryMapper.getCategoryName(courseBase.getMt());
        //再获取小分类 在目录mapper中进行查找
        String stName = courseCategoryMapper.getCategoryName(courseBase.getSt());
        //把大分类小分类名字组装到对象中
        courseBaseInfoDto.setMtName(mtName);
        courseBaseInfoDto.setStName(stName);
        return courseBaseInfoDto;

    }

    //更新课程信息
    @Override
    @Transactional
    public CourseBaseInfoDto updateCourseBase(EditCourseDto editCourseDto, Long companyId) {
        //数据合法性校验
        //拿到id
        Long Id = editCourseDto.getId();
        //查询课程信息
        CourseBase courseBase = courseBaseMapper.selectById(Id);
        if(courseBase == null){
            XueChengPlusException.cast("课程不存在");
        }

        //根据具体业务逻辑校验
        //本机构只能修改本机构的课程
        if(!companyId.equals(courseBase.getCompanyId())){
            XueChengPlusException.cast("本机构只能修改本机构课程");
        }

        //封装数据
        BeanUtils.copyProperties(editCourseDto,courseBase);
        //修改时间
        courseBase.setChangeDate(LocalDateTime.now());

        //更新数据库
        int i = courseBaseMapper.updateById(courseBase);
        if(i<=0){
            XueChengPlusException.cast("修改课程失败");
        }
        //更新营销信息
        //查询课程营销信息
        CourseMarket courseMarket = courseMarketMapper.selectById(Id);
        //封装数据到一个营销对象中
        BeanUtils.copyProperties(editCourseDto,courseMarket);
        int j = courseMarketMapper.updateById(courseMarket);
        if(j<=0){
            XueChengPlusException.cast("修改课程失败");
        }
        //查询课程信息
        CourseBaseInfoDto courseBaseInfo = getCourseBaseInfo(Id);
        return courseBaseInfo;
    }
    //删除课程
    @Override
    @Transactional
    public void deleteCourse(Long courseId) {
        //查找出来所有的相关信息并进行删除
        //删除课程需要删除课程相关的基本信息、营销信息、课程计划、课程教师信息。
        courseBaseMapper.deleteById(courseId);
        courseMarketMapper.deleteById(courseId);
        courseTeacherMapper.deleteByCourseId(courseId);
        List<Teachplan> teachplans = teachplanMapper.getByCourseId(courseId);
        if(!teachplans.isEmpty()){
            teachplans.forEach(teachplan -> {
                Long teachplanId = teachplan.getId();
                teachplanMediaMapper.deleteByTeachPlanId(teachplanId);
                teachplanMapper.deleteByCourseId(courseId);
            });
        }
    }

    //单独写一个方法保存营销信息 存在则更新 不存在则添加
    private int saveCourseMarket(CourseMarket courseMarketNew){
        //参数合法性校验
        String charge = courseMarketNew.getCharge();
        if(charge.isEmpty()){
            XueChengPlusException.cast("收费规则为空");
        }
        //课程收费但价格没有填写
        if(charge.equals("201001")){
            if (courseMarketNew.getPrice()==null|| courseMarketNew.getPrice() <=0.0) {
                XueChengPlusException.cast("课程的价格不能为空且必须大于零");
            }

        }
        //从数据库查询营销信息 存在则更新 不存在则添加
        Long courseMarketId = courseMarketNew.getId();
        CourseMarket courseMarket = courseMarketMapper.selectById(courseMarketId);
        if(courseMarket ==null){
            //插入数据库
            int insert = courseMarketMapper.insert(courseMarketNew);
            return insert;
        }else {
            //将courseMarketNew拷贝到courseMarket
            BeanUtils.copyProperties(courseMarketNew,courseMarket);
            //更新
            int i = courseMarketMapper.updateById(courseMarket);
            return i;
        }

    }

}
