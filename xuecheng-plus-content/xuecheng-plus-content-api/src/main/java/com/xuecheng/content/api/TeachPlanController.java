package com.xuecheng.content.api;

import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.CourseTeacherDto;
import com.xuecheng.content.model.dto.SaveTeachPlanDto;
import com.xuecheng.content.model.dto.TeachPlanDto;
import com.xuecheng.content.model.po.CourseTeacher;
import com.xuecheng.content.service.TeachPlanService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.apache.ibatis.annotations.Delete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//课程计划管理相关接口
@Api(value = "课程计划编辑接口",tags = "课程计划编辑接口")
@RestController
public class TeachPlanController {
    @Autowired
    TeachPlanService teachPlanService;
    //查询课程计划
    @ApiOperation("查询课程计划树形结构")
    @ApiImplicitParam(value = "courseId",name = "课程Id",required = true,dataType = "Long",paramType = "path")
    @GetMapping("/teachplan/{courseId}/tree-nodes")
    public List<TeachPlanDto> getTreeNodes(@PathVariable Long courseId){
        List<TeachPlanDto> teachPlanTree = teachPlanService.findTeachPlanTree(courseId);
        return teachPlanTree;
    }
    //新增大章节、小章节以及修改章节
    @ApiOperation("课程计划创建与修改")
    @PostMapping("/teachplan")
    public void saveTeachPlan(@RequestBody SaveTeachPlanDto teachPlan){
        teachPlanService.saveTeachPlan(teachPlan);
    }

    //删除大章节 小章节
    @ApiOperation("删除课程计划")
    @DeleteMapping("/teachplan/{planId}")
    public void deleteTeachPlan(@PathVariable Long planId){
        teachPlanService.deleteTeachPlan(planId);
    }
    //课程计划排序功能
    @ApiOperation("课程计划排序")
    @PostMapping("/teachplan/{moveType}/{planId}")
    public void moveTeachPlan(@PathVariable String moveType,@PathVariable Long planId){
        teachPlanService.moveTeachPlan(moveType,planId);
    }
    //查询教师功能
    @ApiOperation("查询课程教师")
    @GetMapping("/courseTeacher/list/{courseId}")
    public List<CourseTeacherDto> getCourseTeacher(@PathVariable Long courseId){
        List<CourseTeacherDto> courseTeachers = teachPlanService.getTeacher(courseId);
        return courseTeachers;
    }
    //添加以及修改教师功能
    @ApiOperation("添加、修改课程教师")
    @PostMapping("/courseTeacher")
    public CourseTeacherDto addCourseTeacher(@RequestBody CourseTeacherDto dto){
        Long companyId = 1232141425L;
        CourseTeacherDto courseTeacherDto = teachPlanService.addCourseTeacher(companyId,dto);
        return courseTeacherDto;
    }
    //删除教师
    @ApiOperation("删除教师")
    @DeleteMapping("/courseTeacher/course/{courseId}/{teacherId}")
    public void deleteCourseTeacher(@PathVariable Long courseId,@PathVariable Long teacherId){
        Long companyId = 1232141425L;
        teachPlanService.deleteCourseTeacher(companyId,courseId,teacherId);
    }
}
