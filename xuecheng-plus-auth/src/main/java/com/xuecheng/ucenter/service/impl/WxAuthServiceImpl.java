package com.xuecheng.ucenter.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.mapper.XcUserRoleMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.model.po.XcUserRole;
import com.xuecheng.ucenter.service.AuthService;
import com.xuecheng.ucenter.service.WxAuthService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service("wx_authservice")
//微信扫码认证
public class WxAuthServiceImpl implements AuthService, WxAuthService {
    @Autowired
    XcUserMapper xcUserMapper;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    XcUserRoleMapper xcUserRoleMapper;
    @Autowired
    WxAuthService proxy;

    @Value("${weixin.appid}")
    String appid;
    @Value("${weixin.secret}")
    String secret;


    @Override
    public XcUserExt execute(AuthParamsDto authParamsDto) {
        //得到账号
        String username = authParamsDto.getUsername();
        //查询数据库
        XcUser xcUser = xcUserMapper.selectOne(new LambdaQueryWrapper<XcUser>().eq(XcUser::getUsername, username));
        if (xcUser == null){
            throw new RuntimeException("用户不存在");
        }
        XcUserExt xcUserExt = new XcUserExt();
        BeanUtils.copyProperties(xcUser,xcUserExt);
        return xcUserExt;
    }

    @Override
    public XcUser wxAuth(String code) {
        //完成功能
        //申请令牌
        Map<String, String> accessToken = getAccess_token(code);
        String access_token = accessToken.get("access_token");
        String openid = accessToken.get("openid");
//        System.out.println(accessToken);
        //携带令牌查询用户信息
        Map<String, String> userinfo = getUserinfo(access_token, openid);
//        System.out.println(userinfo);
        //保存用户信息到数据库
        XcUser xcUser = proxy.addWxUser(userinfo);


        return xcUser;
    }
    //携带授权码申请令牌
    //https://api.weixin.qq.com/sns/oauth2/access_token?appid=APPID&secret=SECRET&code=CODE&grant_type=authorization_code
    private Map<String,String> getAccess_token(String code){
        String url_template = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
        //最终请求路径
        String url = String.format(url_template, appid, secret, code);

        //远程调用此url
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, null, String.class);
        //获取响应的结果(由于中文会产生乱码结果 所以要对编码方式进行转换)
//        String body = exchange.getBody();
        String body = new String(exchange.getBody().getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8);
        //将字符串转为map
        Map<String,String> map = JSON.parseObject(body, Map.class);

        return map;
    }

    //携带令牌查询用户信息
    private Map<String,String> getUserinfo(String access_token,String openid){
        String url_template = "https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s";
        String url = String.format(url_template, access_token, openid);

        //远程调用此url
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        //获取响应的结果
        String body = exchange.getBody();
        //将字符串转为map
        Map<String,String> map = JSON.parseObject(body, Map.class);

        return map;

    }
    //保存用户信息到数据库
    @Transactional
    public XcUser addWxUser(Map<String,String> userInfo_map){
        String unionid = userInfo_map.get("unionid");
        String nickname = userInfo_map.get("nickname");
        String userPic = userInfo_map.get("headimgurl");

        //先查询用户信息
        XcUser xcUser = xcUserMapper.selectOne(new LambdaQueryWrapper<XcUser>().eq(XcUser::getWxUnionid, unionid));
        if (xcUser!=null){

            return xcUser;
        }
        //向数据库新增记录
        xcUser = new XcUser();
        String id = UUID.randomUUID().toString();
        xcUser.setId(id);//主键
        xcUser.setUsername(unionid);
        xcUser.setPassword(unionid);
        xcUser.setWxUnionid(unionid);
        xcUser.setNickname(nickname);
        xcUser.setName(nickname);
        xcUser.setUserpic(userPic);
        xcUser.setUtype("101001");//代表用户为学生类型
        xcUser.setStatus("1");//用户状态
        xcUser.setCreateTime(LocalDateTime.now());
        //插入
        int insert = xcUserMapper.insert(xcUser);



        //向用户角色关系表新增数据
        XcUserRole xcUserRole = new XcUserRole();
        xcUserRole.setId(id);
        xcUserRole.setUserId(id);
        xcUserRole.setRoleId("17");//代表学生角色
        xcUserRole.setCreateTime(LocalDateTime.now());
        xcUserRoleMapper.insert(xcUserRole);

        return xcUser;
    }
}
