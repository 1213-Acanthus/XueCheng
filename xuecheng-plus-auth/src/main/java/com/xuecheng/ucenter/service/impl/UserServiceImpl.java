package com.xuecheng.ucenter.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserServiceImpl implements UserDetailsService {
    @Autowired
    XcUserMapper xcUserMapper;
    @Autowired
    ApplicationContext applicationContext;

    //传入的请求认证参数为AuthParamsDto
    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
        //将传入的json转成AuthParamsDto对象
        AuthParamsDto authParamsDto = null;
        try {
            authParamsDto = JSON.parseObject(s, AuthParamsDto.class);
        } catch (Exception e) {
            throw new RuntimeException("请求认证的参数不符合要求");
        }
        //认证类型
        String authType = authParamsDto.getAuthType();
        //根据认证类型从spring容器中取出指定的bean
        String beanName = authType+"_authservice";
        AuthService anthService = applicationContext.getBean(beanName, AuthService.class);
        //调用统一的execute方法完成认证
        XcUserExt execute = anthService.execute(authParamsDto);
        //封装用户数据到userDetails
        UserDetails userPrincipal = getUserPrincipal(execute);
        return userPrincipal;


    }

    public UserDetails getUserPrincipal(XcUserExt xcUser){

        //用户名 密码 权限
        String[] authorities = {"test"};
        String password = xcUser.getPassword();

        //将用户的信息转成json 以便在jwt中保存更多的信息
        xcUser.setPassword(null);
        String jsonString = JSON.toJSONString(xcUser);
        UserDetails userDetails = User.withUsername(jsonString).password(password).authorities(authorities).build();
        //如果查到用户 拿到正确的密码 最终封装成userdetails对象 给security框架返回并进行密码比对
        return userDetails;
    }
}
