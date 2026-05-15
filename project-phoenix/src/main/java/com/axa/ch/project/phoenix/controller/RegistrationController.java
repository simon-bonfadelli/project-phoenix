package com.axa.ch.project.phoenix.controller;

import com.axa.ch.project.phoenix.model.Group;
import com.axa.ch.project.phoenix.model.User;
import com.axa.ch.project.phoenix.service.QrCodeService;
import com.axa.ch.project.phoenix.service.SseService;
import com.axa.ch.project.phoenix.service.UserStore;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class RegistrationController {

    private final UserStore userStore;
    private final SseService sseService;
    private final QrCodeService qrCodeService;

    public RegistrationController(UserStore userStore, SseService sseService, QrCodeService qrCodeService) {
        this.userStore = userStore;
        this.sseService = sseService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        User user = userStore.getUserBySession(session.getId());
        if (user != null && user.isRegistered()) {
            model.addAttribute("user", user);
            Group group = userStore.getGroupForUser(session.getId());
            if (group != null) {
                model.addAttribute("group", group);
                model.addAttribute("qrCode", qrCodeService.generateQrCodeBase64(group.getQrCodeUrl()));
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

        // Notify admin
        String html = renderUserRow(user);
        sseService.notifyAdmins(html);

        return "registered";
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
        String qr = qrCodeService.generateQrCodeBase64(group.getQrCodeUrl());
        return "<div id=\"group-info\">" +
                "<h2>🎉 You've been assigned!</h2>" +
                "<div class=\"card\">" +
                "<h3>" + group.getName() + "</h3>" +
                "<p><strong>Room:</strong> " + group.getRoom() + "</p>" +
                "<img src=\"data:image/png;base64," + qr + "\" alt=\"QR Code\" />" +
                "<p><small>" + group.getQrCodeUrl() + "</small></p>" +
                "</div></div>";
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