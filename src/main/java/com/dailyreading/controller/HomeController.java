package com.dailyreading.controller;

import com.dailyreading.mapper.StatsMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final StatsMapper statsMapper;

    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute(AuthController.SESSION_USER_ID);
        model.addAttribute("username", session.getAttribute(AuthController.SESSION_USERNAME));
        model.addAttribute("todayWords", statsMapper.todayWords(userId));
        model.addAttribute("totalWords", statsMapper.totalWords(userId));
        model.addAttribute("learningMarks", statsMapper.learningMarks(userId));
        model.addAttribute("articles", statsMapper.articles(userId));
        return "index";
    }
}
