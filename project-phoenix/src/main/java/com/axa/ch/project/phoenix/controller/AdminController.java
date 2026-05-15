package com.axa.ch.project.phoenix.controller;

import com.axa.ch.project.phoenix.model.GroupImport;
import com.axa.ch.project.phoenix.model.User;
import com.axa.ch.project.phoenix.service.QrCodeService;
import com.axa.ch.project.phoenix.service.SseService;
import com.axa.ch.project.phoenix.service.UserStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserStore userStore;
    private final SseService sseService;
    private final QrCodeService qrCodeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminController(UserStore userStore, SseService sseService, QrCodeService qrCodeService) {
        this.userStore = userStore;
        this.sseService = sseService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping
    public String admin(Model model) {
        model.addAttribute("users", userStore.getAllRegisteredUsers());
        model.addAttribute("groups", userStore.getGroups().values());
        return "admin";
    }

    @GetMapping("/sse")
    public SseEmitter adminSse() {
        return sseService.createAdminEmitter();
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> exportUsers() {
        Collection<User> users = userStore.getAllRegisteredUsers();
        List<Map<String, String>> userList = users.stream().map(u -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getGeneratedName());
            m.put("gender", u.getGender());
            m.put("jobProfile", u.getJobProfile());
            m.put("division", u.getDivision());
            m.put("groupId", u.getGroupId());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> groupList = userStore.getGroups().values().stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("name", g.getName());
            m.put("room", g.getRoom());
            m.put("qrCodeUrl", g.getQrCodeUrl());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("users", userList);
        export.put("groups", groupList);
        return ResponseEntity.ok(export);
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<String> importGroups(@RequestBody GroupImport groupImport) {
        if (groupImport.getGroupAssignments() == null) {
            return ResponseEntity.badRequest().body("Invalid import format");
        }

        for (Map.Entry<String, List<String>> entry : groupImport.getGroupAssignments().entrySet()) {
            String groupId = entry.getKey();
            for (String userId : entry.getValue()) {
                userStore.assignUserToGroup(userId, groupId);
            }
        }

        // Notify all users about group assignments
        sseService.notifyUsers("<div id=\"group-info\" hx-get=\"/my-group\" hx-trigger=\"load\" hx-swap=\"outerHTML\">Loading group...</div>");

        return ResponseEntity.ok("Import successful");
    }
}