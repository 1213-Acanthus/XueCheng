package com.xuecheng.content.service.impl;

import com.xuecheng.content.mapper.CourseCategoryMapper;
import com.xuecheng.content.model.dto.CourseCategoryTreeDto;
import com.xuecheng.content.service.CourseCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CourseCategoryServiceImpl implements CourseCategoryService {
    @Autowired
    CourseCategoryMapper courseCategoryMapper;


    @Override
    public List<CourseCategoryTreeDto> queryTreeNodes(String id) {
        //调用mapper递归查询出分类信息
        List<CourseCategoryTreeDto> courseCategoryTreeDtos = courseCategoryMapper.selectTreeNodes(id);

        //把列表封装成 List<CourseCategoryTreeDto>类型
        //找到每个节点的子节点然后封装
        //先将list转成map key为节点id value为CourseCategoryTreeDto对象，目的是为了方便从map获取节点,filter把根节点排除
        Map<String, CourseCategoryTreeDto> mapTemp = courseCategoryTreeDtos.stream().filter(item -> !id.equals(item.getId())).collect(Collectors.toMap(key -> key.getId(), value -> value, (key1, key2) -> key2));

        //定义一个list作为最终返回值
        List<CourseCategoryTreeDto> courseCategoryList = new ArrayList<>();

        //从头遍历，一边遍历一边找子节点 放在父节点的childrenTreeNodes
        courseCategoryTreeDtos.stream().filter(item -> !id.equals(item.getId())).forEach(item -> {
            //向list中写入元素作为返回值
            if (item.getParentid().equals(id)) {
                courseCategoryList.add(item);
            }
            //找子节点 放在父节点的childrenTreeNodes
            CourseCategoryTreeDto courseCategoryParent = mapTemp.get(item.getParentid());

            if (courseCategoryParent!=null) {
                if(courseCategoryParent .getChildrenTreeNodes() ==null){
                    //如果该父节点现在没有子节点 那么new一个孩子集合
                    courseCategoryParent .setChildrenTreeNodes(new ArrayList<CourseCategoryTreeDto>());
                }
                courseCategoryParent .getChildrenTreeNodes().add(item);
            }
        });

        return courseCategoryList;
    }
}
