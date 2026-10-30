package com.dailyreading.controller;

import com.dailyreading.entity.User;
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
        try {
            User user = userService.login(username, password);
            HttpSession old = request.getSession(false);
            if (old != null) old.invalidate();          // 防会话固定
            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_USER_ID, user.getId());
            session.setAttribute(SESSION_USERNAME, user.getUsername());
            return "redirect:/";
        } catch (UserService.BizException e) {
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

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
