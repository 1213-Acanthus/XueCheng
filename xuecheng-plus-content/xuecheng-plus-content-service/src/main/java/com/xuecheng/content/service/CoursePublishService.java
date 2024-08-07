package com.xuecheng.content.service;

import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.model.po.CoursePublish;

import java.io.File;

//课程发布相关接口
public interface CoursePublishService {
    //获取课程预览信息
    public CoursePreviewDto getCoursePreviewInfo(Long courseId);

    //
    /**
     * @description 提交审核
     * @param courseId  课程id
     * @return void
     * @author Mr.M
     * @date 2022/9/18 10:31
     */
    public void commitAudit(Long companyId,Long courseId);


    void publish(Long companyId, Long courseId);

    /**
     * @description 课程静态化
     * @param courseId  课程id
     * @return File 静态化文件
     * @author Mr.M
     * @date 2022/9/23 16:59
     */
    public File generateCourseHtml(Long courseId);
    /**
     * @description 上传课程静态化页面
     * @param file  静态化文件
     * @return void
     * @author Mr.M
     * @date 2022/9/23 16:59
     */
    public void  uploadCourseHtml(Long courseId,File file);

    CoursePublish getCoursePublish(Long courseId);
    CoursePublish getCoursePublishCache(Long courseId);
}
