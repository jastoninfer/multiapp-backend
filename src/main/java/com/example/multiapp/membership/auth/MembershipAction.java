package com.example.multiapp.membership.auth;

public enum MembershipAction {
    LIST, // 查看一个租户下所有成员, 需要platform admin | tenant admin
    READ, // 查看某个租户成员详情, 除了成员基本信息以外还包括在租户的角色等, 在租户的状态是否正常等, 加入时间等
    // 需要权限是platform admin | tenant admin | 用户本人
    CREATE, // 给当前租户增加成员, 分配租户内角色, 权限是platform admin | tenant admin
    UPDATE, // 禁用成员, 调整成员角色, 权限是tenant admin | platform admin
    DELETE // 移除某租户成员, 权限是tenant admin | platform admin
}
