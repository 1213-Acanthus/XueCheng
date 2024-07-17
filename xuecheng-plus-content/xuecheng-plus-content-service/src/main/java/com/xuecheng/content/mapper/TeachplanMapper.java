package com.xuecheng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuecheng.content.model.dto.CourseTeacherDto;
import com.xuecheng.content.model.dto.TeachPlanDto;
import com.xuecheng.content.model.po.CourseTeacher;
import com.xuecheng.content.model.po.Teachplan;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 课程计划 Mapper 接口
 * </p>
 *
 * @author itcast
 */
public interface TeachplanMapper extends BaseMapper<Teachplan> {
    //课程计划查询
    public List<TeachPlanDto> selectTreeNodes(Long courseId);
    //课程计划查询
    @Select("select * from xcplus_content.teachplan where id=#{id}")
    public Teachplan getById(Long id);
    //根据父节点查找课程计划
    Integer getByParentId(Long id);
    //找到要交换顺序的课程计划
    Teachplan getOrderByMoveUp(@Param("orderby")Integer orderby, @Param("parentId")Long parentId, @Param("courseId")Long courseId);
    //获取最小的排序值
    Integer getOrderMin(@Param("parentId") Long parentId, @Param("courseId")Long courseId);

    //获取最大的排序值
    @Select("select MAX(orderby) from xcplus_content.teachplan where parentid = #{parentId} and course_id = #{courseId}")
    Integer getOrderMAX(@Param("parentId")Long parentId, @Param("courseId")Long courseId);

    Teachplan getOrderByMoveDown(@Param("orderby")Integer orderby, @Param("parentId")Long parentId, @Param("courseId")Long courseId);
    @Select("select * from xcplus_content.course_teacher where course_id = #{courseId};")
    List<CourseTeacherDto> getTeacher(Long courseId);

    CourseTeacherDto addCourseTeacher(CourseTeacher courseTeacher);
    @Delete("delete from xcplus_content.teachplan where course_id=#{courseId}")
    void deleteByCourseId(Long courseId);
    @Select("select * from xcplus_content.teachplan where course_id=#{courseId}")
    List<Teachplan> getByCourseId(Long courseId);
}
