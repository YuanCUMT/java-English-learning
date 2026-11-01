package com.dailyreading.controller;

import com.dailyreading.entity.User;
import com.dailyreading.service.LoginAttemptService;
import com.dailyreading.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {

    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_USERNAME = "username";

    private final UserService userService;
    private final LoginAttemptService attempts;

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if (session.getAttribute(SESSION_USER_ID) != null) return "redirect:/";
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpServletRequest request,
                          Model model) {
        String attemptKey = LoginAttemptService.key(username, request.getRemoteAddr());
        long wait = attempts.remainingBlockSeconds(attemptKey);
        if (wait > 0) {
            model.addAttribute("error", "失败次数太多，请 " + Math.max(1, wait / 60) + " 分钟后再试");
            model.addAttribute("username", username);
            return "login";
        }
        try {
            User user = userService.login(username, password);
            attempts.onSuccess(attemptKey);
            HttpSession old = request.getSession(false);
            if (old != null) old.invalidate();          // 防会话固定
            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_USER_ID, user.getId());
            session.setAttribute(SESSION_USERNAME, user.getUsername());
            return "redirect:/";
        } catch (UserService.BizException e) {
            attempts.onFailure(attemptKey);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("username", username);
            return "login";
        }
    }

    @GetMapping("/register")
    public String registerPage(HttpSession session) {
        if (session.getAttribute(SESSION_USER_ID) != null) return "redirect:/";
        return "register";
    }

    @PostMapping("/register")
    public String doRegister(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String confirm,
                             HttpServletRequest request,
                             Model model) {
        try {
            User user = userService.register(username, password, confirm);
            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_USER_ID, user.getId());
            session.setAttribute(SESSION_USERNAME, user.getUsername());
            return "redirect:/";
        } catch (UserService.BizException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("username", username);
            return "register";
        }
    }

    /** 退出只接受 POST：GET 退出会被 IM 预览、浏览器预取之类的东西触发，属于「被登出」漏洞。 */
    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logoutByGet() {
        return "redirect:/";
    }
}
