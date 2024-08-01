package com.xuecheng.content.api;

import com.xuecheng.base.exception.ValidationGroups;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.content.model.dto.AddCourseDto;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.EditCourseDto;
import com.xuecheng.content.model.dto.QueryCourseParamsDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.service.CourseBaseInfoService;
import com.xuecheng.content.service.impl.CourseBaseInfoServiceImpl;
import com.xuecheng.content.util.SecurityUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Api(value = "课程信息管理接口",tags = "课程信息管理接口")
@RestController
public class CourseBaseInfoController {
    @Autowired
    CourseBaseInfoService courseBaseInfoService;

    @ApiOperation("课程分页查询接口")
    @PreAuthorize("hasAnyAuthority('xc_teachmanager_course_list')")//指定权限标识符
    @PostMapping("/course/list")
    public PageResult<CourseBase> list(PageParams pageParams, @RequestBody(required = false) QueryCourseParamsDto queryCourseParamsDto) {
        //获取当前登录的用户
        SecurityUtil.XcUser user = SecurityUtil.getUser();
        Long companyId = null;
        if (!user.getCompanyId().isEmpty()){
            companyId = Long.parseLong(user.getCompanyId());
        }
        PageResult<CourseBase> courseBasePageResult = courseBaseInfoService.queryCourseBaseList(companyId,pageParams, queryCourseParamsDto);
        return courseBasePageResult;
    }

    @ApiOperation("新增课程接口")
    @PostMapping("/course")
    //validate进行校验
    public CourseBaseInfoDto createCourseBase(@RequestBody @Validated(value = ValidationGroups.Insert.class) AddCourseDto addCourseDto) {
        //获取到用户所属机构id
        //先写死进行测试
        Long companyId = 1232141425L;
        CourseBaseInfoDto courseBase = courseBaseInfoService.createCourseBase(companyId, addCourseDto);

        return courseBase;
    }

    @ApiOperation("根据课程id查询课程接口")
    @GetMapping("/course/{id}")
    //validate进行校验
    public CourseBaseInfoDto getCourseById(@PathVariable Long id) {
        //获取当前用户的身份
//        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
//        System.out.println(principal);
        SecurityUtil.XcUser user = SecurityUtil.getUser();
        System.out.println(user.getName());
        CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseBaseInfo(id);
        return courseBaseInfo;
    }

    @ApiOperation("修改课程接口")
    @PutMapping("/course")
    //validate进行校验
    public CourseBaseInfoDto modifyCourseBase(@RequestBody @Validated(ValidationGroups.Update.class) EditCourseDto editCourseDto) {
        //先写死进行测试
        Long companyId = 1232141425L;
        CourseBaseInfoDto courseBaseInfoDto = courseBaseInfoService.updateCourseBase(editCourseDto, companyId);

        return courseBaseInfoDto;
    }

    @ApiOperation("删除课程接口")
    @DeleteMapping("/course/{courseId}")
    public void deleteCourse(@PathVariable Long courseId){
        courseBaseInfoService.deleteCourse(courseId);
    }


}
