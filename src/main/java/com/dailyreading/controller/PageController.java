package com.dailyreading.controller;

import com.dailyreading.service.ArticleService;
import com.dailyreading.service.MarkService;
import com.dailyreading.service.WordService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;

/** 页面路由：单词本、历史文章、单篇阅读。 */
@Controller
@RequiredArgsConstructor
public class PageController {

    private final MarkService markService;
    private final WordService wordService;
    private final ArticleService articleService;

    @GetMapping("/settings")
    public String settings(@RequestAttribute("userId") Long userId,
                           @RequestAttribute("username") String username,
                           Model model) {
        model.addAttribute("username", username);
        model.addAttribute("active", "settings");
        model.addAttribute("learningMarks", markService.count(userId, "learning"));
        return "settings";
    }

    @GetMapping("/vocab")
    public String vocab(HttpSession session, Model model) {
        Long userId = userId(session);
        model.addAttribute("username", session.getAttribute(AuthController.SESSION_USERNAME));
        model.addAttribute("active", "vocab");
        model.addAttribute("total", wordService.totalWords(userId));
        model.addAttribute("learning", markService.count(userId, "learning"));
        model.addAttribute("mastered", markService.count(userId, "mastered"));
        return "vocab";
    }

    @GetMapping("/archive")
    public String archive(HttpSession session, Model model) {
        Long userId = userId(session);
        model.addAttribute("username", session.getAttribute(AuthController.SESSION_USERNAME));
        model.addAttribute("active", "archive");
        model.addAttribute("learning", markService.count(userId, "learning"));
        model.addAttribute("mastered", markService.count(userId, "mastered"));
        model.addAttribute("articles", articleService.list(userId));
        return "archive";
    }

    @GetMapping("/article/{id}")
    public String article(HttpSession session, Model model, @PathVariable Long id) {
        Long userId = userId(session);
        if (articleService.findById(userId, id) == null) {
            return "redirect:/archive";
        }
        model.addAttribute("username", session.getAttribute(AuthController.SESSION_USERNAME));
        model.addAttribute("articleId", id);
        return "article";
    }

    private Long userId(HttpSession session) {
        return (Long) session.getAttribute(AuthController.SESSION_USER_ID);
    }
}
