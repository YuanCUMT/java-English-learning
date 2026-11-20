package com.dailyreading.controller;
import com.dailyreading.service.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.UUID;

@Controller
@ConditionalOnProperty(name="app.demo",havingValue="true")
@RequiredArgsConstructor
public class DemoController {
    private final UserService users;
    private final ExampleService examples;
    @PostMapping("/demo/start") public String start(HttpServletRequest request) {
        String id=UUID.randomUUID().toString().replace("-","");
        String password=UUID.randomUUID().toString();
        var user=users.register("guest_"+id.substring(0,12),password,password);
        examples.load(user.getId());
        var old=request.getSession(false); if(old!=null) old.invalidate();
        var session=request.getSession(true);
        session.setAttribute(AuthController.SESSION_USER_ID,user.getId());
        session.setAttribute(AuthController.SESSION_USERNAME,user.getUsername());
        return "redirect:/";
    }
}
