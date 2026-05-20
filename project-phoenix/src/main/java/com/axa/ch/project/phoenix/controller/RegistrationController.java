package com.axa.ch.project.phoenix.controller;

import com.axa.ch.project.phoenix.model.Group;
import com.axa.ch.project.phoenix.model.User;
import com.axa.ch.project.phoenix.service.SseService;
import com.axa.ch.project.phoenix.service.UserStore;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class RegistrationController {

    private final UserStore userStore;
    private final SseService sseService;

    public RegistrationController(UserStore userStore, SseService sseService) {
        this.userStore = userStore;
        this.sseService = sseService;
    }

    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        User user = userStore.getUserBySession(session.getId());
        if (user != null && user.isRegistered()) {
            model.addAttribute("user", user);
            Group group = userStore.getGroupForUser(session.getId());
            if (group != null) {
                model.addAttribute("group", group);
                // QR code removed from UI, so no qrCode model attribute needed.
            }
            return "registered";
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String gender,
            @RequestParam String jobProfile,
            @RequestParam String division,
            HttpSession session,
            Model model) {
        User user = userStore.registerUser(session.getId(), gender, jobProfile, division);
        model.addAttribute("user", user);

        String html = renderUserRow(user);
        sseService.notifyAdmins(html);

        return "redirect:/";
    }

    @GetMapping("/sse/user")
    public SseEmitter userSse() {
        return sseService.createUserEmitter();
    }

    @GetMapping("/my-group")
    @ResponseBody
    public String myGroup(HttpSession session) {
        User user = userStore.getUserBySession(session.getId());
        if (user == null || user.getGroupId() == null) {
            return "<div id=\"group-info\"><p>No group assigned yet. Please wait...</p></div>";
        }

        Group group = userStore.getGroupForUser(session.getId());
        if (group == null) {
            return "<div id=\"group-info\"><p>No group assigned yet. Please wait...</p></div>";
        }

        return "<div id=\"group-info\">" +
               "<div class=\"card\">" +
               "<h3>🎉 You’ve been assigned!</h3>" +
               "<h2>" + escapeHtml(group.getName()) + "</h2>" +
               "<div class=\"room-big\">" +
               "<div class=\"room-label\">Room</div>" +
               "<div>" + escapeHtml(group.getRoom()) + "</div>" +
               "</div>" +
               "<div class=\"remark\">" +
               "<p><strong>Next:</strong> Go to your room now to dive into the mysteries of the Legacy.</p>" +
               "<p><strong>Then:</strong> On your working laptop start <i>Microsoft Edge</i> and open <strong>go/phoenix</strong>.</p>" +
               "</div>" +
               "</div>" +
               "</div>";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String renderUserRow(User user) {
        return "<tr id=\"user-" + user.getId() + "\">" +
               "<td><img src=\"" + user.getAvatarUrl() + "\" width=\"32\" height=\"32\"/></td>" +
               "<td>" + user.getGeneratedName() + "</td>" +
               "<td>" + user.getGender() + "</td>" +
               "<td>" + user.getJobProfile() + "</td>" +
               "<td>" + user.getDivision() + "</td>" +
               "<td>" + (user.getGroupId() != null ? user.getGroupId() : "-") + "</td>" +
               "</tr>";
    }
}