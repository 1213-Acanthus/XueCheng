package com.xuecheng.content.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

//Freemarker入门程序
@Controller
public class FreemarkerController {
    @GetMapping("/testfreemarker")
    public ModelAndView test() {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("test");//根据视图名称加上.ftl找到模版（扩展名在yaml中已经配置）

        modelAndView.addObject("name","小米");
        return modelAndView;
    }
}
