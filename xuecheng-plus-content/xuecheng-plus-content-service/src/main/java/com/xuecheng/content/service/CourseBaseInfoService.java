package com.xuecheng.content.service;

import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.content.model.dto.AddCourseDto;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.EditCourseDto;
import com.xuecheng.content.model.dto.QueryCourseParamsDto;
import com.xuecheng.content.model.po.CourseBase;

import java.net.InterfaceAddress;

public interface CourseBaseInfoService {
    //课程分页查询
    public PageResult<CourseBase> queryCourseBaseList(Long companyId,PageParams pageParams, QueryCourseParamsDto courseParamsDto);
    //新增课程
    public CourseBaseInfoDto createCourseBase(Long companyId,AddCourseDto addCourseDto);

    //根据id查询课程
    public CourseBaseInfoDto getCourseBaseInfo(Long id);

    //修改课程
    public CourseBaseInfoDto updateCourseBase(EditCourseDto editCourseDto,Long companyId);
    //删除课程
    void deleteCourse(Long courseId);
}
