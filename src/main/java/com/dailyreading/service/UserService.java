package com.dailyreading.service;

import com.dailyreading.entity.User;
import com.dailyreading.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    /** 3-20 位字母数字下划线 */
    private static final Pattern USERNAME_RE = Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 业务异常，交给全局处理器转成页面提示 */
    public static class BizException extends RuntimeException {
        public BizException(String message) {
            super(message);
        }
    }

    public User register(String username, String rawPassword, String confirm) {
        if (username == null || !USERNAME_RE.matcher(username).matches()) {
            throw new BizException("用户名要 3-20 位，只能用字母、数字、下划线");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new BizException("密码至少 6 位");
        }
        if (!rawPassword.equals(confirm)) {
            throw new BizException("两次输入的密码不一致");
        }
        if (userMapper.countByUsername(username) > 0) {
            throw new BizException("这个用户名已经被占用了");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(rawPassword));
        userMapper.insert(user);
        return user;
    }

    public User login(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isEmpty()) {
            throw new BizException("请输入用户名和密码");
        }
        User user = userMapper.findByUsername(username.trim());
        // 不区分「用户不存在」和「密码错误」，避免被探测用户名
        if (user == null || !encoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BizException("用户名或密码不对");
        }
        return user;
    }

    public User byId(Long id) {
        return userMapper.findById(id);
    }
}
