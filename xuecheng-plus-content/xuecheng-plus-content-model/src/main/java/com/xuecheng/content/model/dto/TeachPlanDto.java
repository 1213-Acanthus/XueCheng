package com.xuecheng.content.model.dto;

import com.xuecheng.content.model.po.Teachplan;
import com.xuecheng.content.model.po.TeachplanMedia;
import lombok.Data;
import lombok.ToString;

import java.util.List;

//课程计划信息模型类
@Data
public class TeachPlanDto extends Teachplan {
    //小章节列表
    private List<TeachPlanDto> teachPlanTreeNodes;
    //与媒资关联的信息
    private TeachplanMedia teachplanMedia;
}
