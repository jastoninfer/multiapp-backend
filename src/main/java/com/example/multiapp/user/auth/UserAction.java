package com.example.multiapp.user.auth;

public enum UserAction {
    CHANGE_STATUS, // 用户状态是全局的, 只有超级管理员才有权限修改状态
    CHANGE_DEFAULT_TENANT
}
