package com.axa.ch.project.phoenix.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {

    private final List<SseEmitter> adminEmitters = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> userEmitters = new CopyOnWriteArrayList<>();

    public SseEmitter createAdminEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        adminEmitters.add(emitter);
        emitter.onCompletion(() -> adminEmitters.remove(emitter));
        emitter.onTimeout(() -> adminEmitters.remove(emitter));
        emitter.onError(e -> adminEmitters.remove(emitter));
        return emitter;
    }

    public SseEmitter createUserEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        userEmitters.add(emitter);
        emitter.onCompletion(() -> userEmitters.remove(emitter));
        emitter.onTimeout(() -> userEmitters.remove(emitter));
        emitter.onError(e -> userEmitters.remove(emitter));
        return emitter;
    }

    public void notifyAdmins(String html) {
        for (SseEmitter emitter : adminEmitters) {
            try {
                emitter.send(SseEmitter.event().name("user-update").data(html));
            } catch (IOException e) {
                adminEmitters.remove(emitter);
            }
        }
    }

    public void notifyUsers(String html) {
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("group-update").data(html));
            } catch (IOException e) {
                userEmitters.remove(emitter);
            }
        }
    }
}