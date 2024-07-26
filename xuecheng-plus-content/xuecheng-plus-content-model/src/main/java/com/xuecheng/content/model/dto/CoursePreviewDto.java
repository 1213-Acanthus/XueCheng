package com.xuecheng.content.model.dto;

import com.xuecheng.content.model.po.Teachplan;
import lombok.Data;

import java.util.List;

//用于课程预览的模型类
@Data
public class CoursePreviewDto {
    //课程基本信息，营销信息
    private CourseBaseInfoDto courseBase;

    //课程计划信息
    private List<TeachPlanDto> teachplans;
    //课程师资信息
    private List<CourseTeacherDto> courseTeachers;
}
