package com.xuecheng.content.service;

import com.xuecheng.content.model.dto.CourseTeacherDto;
import com.xuecheng.content.model.dto.SaveTeachPlanDto;
import com.xuecheng.content.model.dto.TeachPlanDto;
import com.xuecheng.content.model.po.CourseTeacher;

import java.util.List;

//课程计划管理相关接口
public interface TeachPlanService {
    //根据课程id查询课程计划
    public List<TeachPlanDto> findTeachPlanTree(Long courseId);

    //新增大章节、小章节以及修改章节
    public void saveTeachPlan(SaveTeachPlanDto teachPlan);
    //删除大章节小章节
    public void deleteTeachPlan(Long planId);
    //课程计划排序
    public void moveTeachPlan(String moveType, Long planId);
    //查询课程教师
    List<CourseTeacherDto> getTeacher(Long courseId);
    //添加课程教师
    CourseTeacherDto addCourseTeacher(Long companyId,CourseTeacherDto courseTeacher);
    //删除教师
    void deleteCourseTeacher(Long companyId,Long courseId, Long teacherId);
}
