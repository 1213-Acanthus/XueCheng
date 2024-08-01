package com.xuecheng.ucenter.service;

import com.xuecheng.ucenter.model.po.XcUser;

import java.util.Map;

//微信扫码接口
public interface WxAuthService {
    //微信扫码认证：申请令牌 携带令牌查询用户信息 保存用户信息到数据库
    public XcUser wxAuth(String code);

    public XcUser addWxUser(Map<String,String> userInfo_map);
}
