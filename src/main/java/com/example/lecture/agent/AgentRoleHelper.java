package com.example.lecture.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lecture.entity.Role;
import com.example.lecture.entity.UserRole;
import com.example.lecture.mapper.RoleMapper;
import com.example.lecture.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色判定助手：工具用于做数据范围与权限控制（如教师只能统计/操作自己的讲座）
 */
@Component
@RequiredArgsConstructor
public class AgentRoleHelper {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    /** 用户角色名（小写）集合；查不到返回空集 */
    public Set<String> rolesOf(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        List<UserRole> links = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (links.isEmpty()) {
            return Set.of();
        }
        List<Long> roleIds = links.stream().map(UserRole::getRoleId).distinct().toList();
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        return roles.stream()
                .map(r -> r.getRoleName() == null ? "" : r.getRoleName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public boolean isAdmin(Long userId) {
        return rolesOf(userId).stream().anyMatch(r -> r.contains("admin") || r.contains("管理员"));
    }

    public boolean isTeacher(Long userId) {
        return rolesOf(userId).stream()
                .anyMatch(r -> r.contains("teacher") || r.contains("讲师") || r.contains("教师"));
    }

    public boolean isStudent(Long userId) {
        return rolesOf(userId).stream().anyMatch(r -> r.contains("student") || r.contains("学生"));
    }
}
