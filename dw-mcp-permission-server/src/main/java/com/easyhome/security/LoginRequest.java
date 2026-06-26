package com.easyhome.security;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端登录请求
 */
@Data
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户名 */
    private String username;

    /** 密码（明文，服务端 BCrypt 校验） */
    private String password;
}
