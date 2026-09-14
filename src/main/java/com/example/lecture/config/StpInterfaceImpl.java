package com.example.lecture.config;

import cn.dev33.satoken.stp.StpInterface;
import com.example.lecture.entity.Permission;
import com.example.lecture.entity.Role;
import com.example.lecture.service.PermissionService;
import com.example.lecture.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 角色与权限数据源。
 *
 * <p>缺少本实现时，{@code @SaCheckRole} / {@code @SaCheckPermission} 拿不到任何角色，
 * 会对所有登录用户一律返回「没有相关权限」。这里从 user_role -> role / role_permission -> permission
 * 读取真实数据。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final RoleService roleService;
    private final PermissionService permissionService;

    /** 当前账号的权限码集合 */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = toUserId(loginId);
        if (userId == null) {
            return List.of();
        }
        return permissionService.getPermissionsByUserId(userId).stream()
                .map(Permission::getPermissionCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
    }

    /** 当前账号的角色名集合（Sa-Token 的 @SaCheckRole 依据此列表判断） */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = toUserId(loginId);
        if (userId == null) {
            return List.of();
        }
        return roleService.getRolesByUserId(userId).stream()
                .map(Role::getRoleName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    /** 登录ID 统一为 Long；格式异常时视为无身份 */
    private Long toUserId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        if (loginId instanceof Long value) {
            return value;
        }
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(loginId));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
