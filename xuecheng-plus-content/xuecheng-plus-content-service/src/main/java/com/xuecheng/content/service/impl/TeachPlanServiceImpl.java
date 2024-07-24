package com.xuecheng.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.CourseTeacherMapper;
import com.xuecheng.content.mapper.TeachplanMapper;
import com.xuecheng.content.mapper.TeachplanMediaMapper;
import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.CourseTeacherDto;
import com.xuecheng.content.model.dto.SaveTeachPlanDto;
import com.xuecheng.content.model.dto.TeachPlanDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseTeacher;
import com.xuecheng.content.model.po.Teachplan;
import com.xuecheng.content.model.po.TeachplanMedia;
import com.xuecheng.content.service.TeachPlanService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
@Service
public class TeachPlanServiceImpl implements TeachPlanService {
    @Autowired
    TeachplanMapper teachplanMapper;

    @Autowired
    TeachplanMediaMapper teachplanMediaMapper;

    @Autowired
    CourseTeacherMapper courseTeacherMapper;

    @Autowired
    CourseBaseMapper courseBaseMapper;

    @Override
    public List<TeachPlanDto> findTeachPlanTree(Long courseId) {
        List<TeachPlanDto> teachPlanDtos = teachplanMapper.selectTreeNodes(courseId);
        return teachPlanDtos;
    }

    @Override
    public void saveTeachPlan(SaveTeachPlanDto saveTeachPlanDto) {
        //通过课程计划id来判断是新增还是修改
        Long teachPlanId = saveTeachPlanDto.getId();
        if(teachPlanId ==null){
            //新增
            Teachplan teachplan = new Teachplan();
            BeanUtils.copyProperties(saveTeachPlanDto,teachplan);
            //确定排序字段，找到同级节点排序字段的最大值 新增节点应该是最大值加一
            Long parentid = saveTeachPlanDto.getParentid();
            Long courseId = saveTeachPlanDto.getCourseId();
            Integer orderMAX = teachplanMapper.getOrderMAX(parentid, courseId);
            teachplan.setOrderby(orderMAX+1);

            teachplanMapper.insert(teachplan);
        }else {
            //修改
            Teachplan teachplan = teachplanMapper.selectById(teachPlanId);
            //将参数复制到对象中
            BeanUtils.copyProperties(saveTeachPlanDto,teachplan);
            teachplanMapper.updateById(teachplan);
        }
    }

    //删除大章节 小章节
    @Override
    @Transactional
    public void deleteTeachPlan(Long planId) {
        //拿到课程号先判断是大章节还是小章节
        Teachplan teachplan  = teachplanMapper.getById(planId);
        //grade=1就是大章节 grade=2就是小章节
        Integer grade = teachplan.getGrade();
        if(grade==1){
            //大章节 判断是否还有子章节
            //去数据库查找是否有课程计划的父节点是本章节 如果有则代表还有子节点没有删除
            Integer num = teachplanMapper.getByParentId(planId);
            if(num==0){
                teachplanMapper.deleteById(planId);
            }else{
                XueChengPlusException.cast("课程计划信息还有子级信息，无法操作");
            }
        }else {
            //小章节 同时需要将teachplan_media表关联的信息也删除
            teachplanMapper.deleteById(planId);
            //删除teachplan_media表对应数据
            teachplanMediaMapper.deleteByTeachPlanId(planId);
        }
    }
    //课程计划排序
    @Override
    @Transactional
    public void moveTeachPlan(String moveType, Long planId) {
        //先获取这个课程计划
        Teachplan teachplan = teachplanMapper.getById(planId);
        //获取他的排序值
        Integer orderby = teachplan.getOrderby();
        //还要获得课程id
        Long courseId = teachplan.getCourseId();
        //获得父亲节点的值 以便进行判断
        Long parentid = teachplan.getParentid();
        //获得当前课程 当前父节点下排序值的最小值和最大值
        Integer orderMin = teachplanMapper.getOrderMin(parentid,courseId);
        Integer orderMAX = teachplanMapper.getOrderMAX(parentid,courseId);
        //移动方向决定了这个章节应该跟上边的章节进行比较还是跟下边的
        if(moveType.equals("moveup")){
            //向上移动 先判断是不是边界（自己本身就是最小的）
            if(orderby == orderMin){
                XueChengPlusException.cast("您已经是第一个啦，无法移动");
            }else {
                //跟排序是上一个的章节进行替换,使用排序的顺序和父节点id进行寻找
                //寻找的是父节点和本节点相同但是排序值是小于本节点排序值的最大值的课程计划
                //获取往上移动的上一个课程计划
                Teachplan orderByMoveUp = teachplanMapper.getOrderByMoveUp(orderby, parentid, courseId);
                //交换两个课程计划的orderby值
                teachplan.setOrderby(orderByMoveUp.getOrderby());
                teachplanMapper.updateById(teachplan);
                orderByMoveUp.setOrderby(orderby);
                teachplanMapper.updateById(orderByMoveUp);
            }
        }else{
            //向下移动 相反处理方式
            if(orderby == orderMAX){
                XueChengPlusException.cast("您已经是最后一个啦，无法移动");
            }else {
                //跟排序是下一个的章节进行替换,使用排序的顺序和父节点id进行寻找
                //寻找的是父节点和本节点相同但是排序值是大于本节点排序值的最小值的课程计划
                //获取往下移动的下一个课程计划
                Teachplan orderByMoveDown = teachplanMapper.getOrderByMoveDown(orderby, parentid, courseId);
                //交换两个课程计划的orderby值
                teachplan.setOrderby(orderByMoveDown.getOrderby());
                teachplanMapper.updateById(teachplan);
                orderByMoveDown.setOrderby(orderby);
                teachplanMapper.updateById(orderByMoveDown);
            }
        }
    }
    //查询课程教师
    @Override
    public List<CourseTeacherDto> getTeacher(Long courseId) {
        List<CourseTeacherDto> courseTeachers = teachplanMapper.getTeacher(courseId);
        return courseTeachers;
    }

    @Override
    public CourseTeacherDto addCourseTeacher(Long companyId,CourseTeacherDto dto) {
        //先进行权限校验
        //通过课程id查询companyid
        Long courseId = dto.getCourseId();
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        Long teacherCompanyId = courseBase.getCompanyId();
        if(!companyId.equals(teacherCompanyId)){
            XueChengPlusException.cast("本机构只能修改本机构教师");
        }
        //通过教师id来判断是新增还是修改
        Long teachId = dto.getId();
        if(teachId == null){
            //新增
            CourseTeacher courseTeacherNew = new CourseTeacher();
            BeanUtils.copyProperties(dto,courseTeacherNew);
            courseTeacherNew.setCreateDate(LocalDateTime.now());
            int insert = courseTeacherMapper.insert(courseTeacherNew);
            if(insert<=0){
                throw new RuntimeException("添加教师失败");
            }
        }else {
            //修改
            CourseTeacher courseTeacher = courseTeacherMapper.selectById(teachId);
            BeanUtils.copyProperties(dto,courseTeacher);
            courseTeacherMapper.updateById(courseTeacher);
        }

        return dto;
    }
    //删除教师
    @Override
    public void deleteCourseTeacher(Long companyId, Long courseId, Long teacherId) {
        //进行权限校验
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        Long teacherCompanyId = courseBase.getCompanyId();
        if(!companyId.equals(teacherCompanyId)){
            XueChengPlusException.cast("本机构只能修改本机构教师");
        }
        //删除教师
        courseTeacherMapper.deleteById(teacherId);
    }
    //课程计划和媒资绑定
    @Override
    @Transactional
    public void associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto) {
        //课程计划id
        Long teachplanId = bindTeachplanMediaDto.getTeachplanId();
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        if(teachplan == null){
            XueChengPlusException.cast("课程计划不存在");
        }

        //先删除原有记录，根据课程计划id删除他所绑定的媒资信息

        teachplanMediaMapper.delete(new LambdaQueryWrapper<TeachplanMedia>().eq(TeachplanMedia::getTeachplanId,bindTeachplanMediaDto.getTeachplanId()));

        //添加新的记录
        TeachplanMedia teachplanMedia = new TeachplanMedia();
        BeanUtils.copyProperties(bindTeachplanMediaDto,teachplanMedia);
        teachplanMedia.setCourseId(teachplan.getCourseId());
        teachplanMedia.setMediaFilename(bindTeachplanMediaDto.getFileName());

        teachplanMediaMapper.insert(teachplanMedia);
    }
}
