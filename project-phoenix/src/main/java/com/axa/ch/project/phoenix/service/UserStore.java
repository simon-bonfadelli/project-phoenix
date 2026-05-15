package com.axa.ch.project.phoenix.service;

import com.axa.ch.project.phoenix.model.Group;
import com.axa.ch.project.phoenix.model.User;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserStore {

    private final Map<String, User> usersBySession = new ConcurrentHashMap<>();
    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();

    private static final String[] MALE_NAMES = {
        "Captain Byte", "Professor Loop", "Sir Stackalot", "Duke Pipeline",
        "Baron Widget", "Count Pixel", "Major Debug", "Admiral Schema",
        "Knight Kernel", "Lord Syntax", "Mister Cache", "Doctor Thread",
        "Agent Proxy", "Chief Vector", "Marshal Token"
    };

    private static final String[] FEMALE_NAMES = {
        "Lady Lambda", "Duchess Deploy", "Countess Config", "Queen Query",
        "Princess Parse", "Baroness Binary", "Dame Docker", "Empress Endpoint",
        "Madam Module", "Sage Servlet", "Oracle Output", "Maven Matrix",
        "Cipher Crystal", "Beacon Bloom", "Rune River"
    };

    private static final String[] OTHER_NAMES = {
        "Phoenix Flux", "Nebula Node", "Comet Compiler", "Astro API",
        "Quantum Queue", "Stellar Stream", "Cosmo Cursor", "Nova Null",
        "Eclipse Entity", "Orbit Object", "Pulsar Patch", "Galaxy Gate",
        "Zenith Zero", "Vortex Value", "Prism Port"
    };

    private int nameCounterMale = 0;
    private int nameCounterFemale = 0;
    private int nameCounterOther = 0;

    @PostConstruct
    public void init() {
        String[] groupNames = {
            "Alpha Architects", "Beta Builders", "Gamma Guardians",
            "Delta Drivers", "Epsilon Engineers", "Zeta Zealots",
            "Eta Explorers", "Theta Thinkers", "Iota Innovators", "Kappa Knights"
        };
        String[] rooms = {
            "W-R1.141", "W-R2.186", "W-R3.141", "W-R3.186", "W-R1.186",
            "W-R1.161", "W-R2.163", "W-R3.161", "W-R2.164", "W-R3.163"
        };
        for (int i = 0; i < 10; i++) {
            String id = "group-" + (i + 1);
            String url = "https://hackathon.axa.ch/group/" + id;
            groups.put(id, new Group(id, groupNames[i], url, rooms[i]));
        }
    }

    public synchronized String generateName(String gender) {
        switch (gender.toLowerCase()) {
            case "male":
                return MALE_NAMES[nameCounterMale++ % MALE_NAMES.length];
            case "female":
                return FEMALE_NAMES[nameCounterFemale++ % FEMALE_NAMES.length];
            default:
                return OTHER_NAMES[nameCounterOther++ % OTHER_NAMES.length];
        }
    }

    public String generateAvatarUrl(String gender, String name) {
        String seed = name.replaceAll("\\s+", "");
        // Using DiceBear API for avatar generation
        String style = gender.equalsIgnoreCase("male") ? "adventurer" :
                       gender.equalsIgnoreCase("female") ? "adventurer" : "bottts";
        return "https://api.dicebear.com/7.x/" + style + "/svg?seed=" + seed;
    }

    public User getOrCreateUser(String sessionId) {
        return usersBySession.computeIfAbsent(sessionId, User::new);
    }

    public User registerUser(String sessionId, String gender, String jobProfile, String division) {
        User user = usersBySession.get(sessionId);
        if (user != null && user.isRegistered()) {
            return user; // immutable
        }
        if (user == null) {
            user = new User(sessionId);
            usersBySession.put(sessionId, user);
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        user.setId(id);
        user.setGender(gender);
        user.setJobProfile(jobProfile);
        user.setDivision(division);
        String name = generateName(gender);
        user.setGeneratedName(name);
        user.setAvatarUrl(generateAvatarUrl(gender, name));
        user.setRegistered(true);
        usersById.put(id, user);
        return user;
    }

    public User getUserBySession(String sessionId) {
        return usersBySession.get(sessionId);
    }

    public Collection<User> getAllRegisteredUsers() {
        return usersById.values();
    }

    public Map<String, Group> getGroups() {
        return groups;
    }

    public void assignUserToGroup(String userId, String groupId) {
        User user = usersById.get(userId);
        Group group = groups.get(groupId);
        if (user != null && group != null) {
            user.setGroupId(groupId);
            if (!group.getUserIds().contains(userId)) {
                group.getUserIds().add(userId);
            }
        }
    }

    public Group getGroupForUser(String sessionId) {
        User user = usersBySession.get(sessionId);
        if (user != null && user.getGroupId() != null) {
            return groups.get(user.getGroupId());
        }
        return null;
    }
}