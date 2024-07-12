package com.xuecheng.base.model;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 分页查询分页参数
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PageParams {
    //当前页码
    @ApiModelProperty(value = "页码")
    private Long pageNo = 1L;
    //每页显示记录数
    @ApiModelProperty(value = "每页记录数")
    private Long pageSize = 30L;
}
