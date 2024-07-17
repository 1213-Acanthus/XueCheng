package com.xuecheng.content;

import com.xuecheng.content.mapper.TeachplanMapper;
import com.xuecheng.content.model.dto.TeachPlanDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
//课程计划mapper测试
public class TeachPlanMapperTests {
    @Autowired
    TeachplanMapper teachplanMapper;
    @Test
    public void testSelectTreeNodes(){
        List<TeachPlanDto> teachPlanDtos = teachplanMapper.selectTreeNodes(117L);
        System.out.println(teachPlanDtos);
    }

}
