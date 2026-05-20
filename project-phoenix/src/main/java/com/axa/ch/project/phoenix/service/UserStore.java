package com.axa.ch.project.phoenix.service;

import com.axa.ch.project.phoenix.model.Group;
import com.axa.ch.project.phoenix.model.User;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class UserStore {

    private final Map<String, User> usersBySession = new ConcurrentHashMap<>();
    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();

    private final Map<String, String> nameToUserId = new ConcurrentHashMap<>();

    private static final int MAX_NAME_ATTEMPTS = 200;

    private static final String[] MALE_TITLES = {
            "Captain", "Sir", "Lord", "Duke", "Baron", "Major", "Admiral", "Knight", "Agent", "Chief", "Guru", "Sensei", "Master", "Archon", "Magus", "Warlock", "Wizard", "Sorcerer", "Prophet"
    };
    private static final String[] FEMALE_TITLES = {
            "Lady", "Dame", "Queen", "Duchess", "Baroness", "Princess", "Empress", "Countess", "Priestess"
    };
    private static final String[] NEUTRAL_TITLES = {
            "Captain", "Sage", "Oracle", "Cipher", "Pilot", "Scout", "Warden", "Architect", "Guardian", "Navigator"
    };

    private static final Map<String, String[]> DIVISION_NOUNS = Map.of(
            "Individual Life", new String[]{"Life", "Beacon", "Harbor", "Compass", "Lantern", "Lifeline"},
            "Group Life", new String[]{"Guild", "Cohort", "Alliance", "Squad", "Circle", "Collective"},
            "Health", new String[]{"Pulse", "Clinic", "Remedy", "Vital", "Wellness", "Helix"}
    );

    private static final Map<String, String[]> JOB_EPITHETS = Map.of(
            "Software Engineer", new String[]{
                    "Byte", "Kernel", "Stack", "Compiler", "Runtime", "Patch",
                    "API", "Commit", "Merge", "Branch", "CLI", "Daemon",
                    "Thread", "Heap", "Cache", "Index", "Opcode", "Refactor"
            },
            "UI Specialist", new String[]{
                    "Pixel", "Canvas", "Layout", "Palette", "Chrome", "Vector",
                    "Grid", "Typography", "Shader", "Spline", "Contrast", "Motion",
                    "Figma", "Prototype", "Viewport", "Tooltip", "Icon", "Glow"
            },
            "Apprentice", new String[]{
                    "Rookie", "Spark", "Seed", "Novice", "Learner", "Sprout",
                    "Cadet", "Trainee", "Sidekick", "Intern", "Forge", "Quest"
            },
            "Business Analyst", new String[]{
                    "Insight", "Metric", "Signal", "Forecast", "Query", "Ledger",
                    "KPI", "Dashboard", "Trend", "Variance", "Hypothesis", "Model",
                    "Benchmark", "Segment", "Cohort", "Funnel"
            },
            "Product Owner", new String[]{
                    "Vision", "Roadmap", "Backlog", "Value", "Outcome", "NorthStar",
                    "Scope", "Pivot", "Launch", "Beta", "MVP", "OKR",
                    "Stakeholder", "Discovery", "Experiment", "Impact"
            },
            "Agile Master", new String[]{
                    "Sprint", "Flow", "Kaizen", "Cadence", "Tempo", "Harmony",
                    "Standup", "Retro", "Kanban", "WIP", "Scrum", "Burndown",
                    "Backpressure", "Swarm", "Unblock", "Velocity"
            },
            "Others", new String[]{
                    "Nova", "Orbit", "Prism", "Beacon", "Pulse", "Vertex",
                    "Cipher", "Nexus", "Quantum", "Helix", "Atlas", "Rune"
            }
    );

    @PostConstruct
    public void init() {
        String[] groupNames = {
                "Alpha Architects - Gruppe 1", "Beta Builders - Gruppe 2", "Gamma Guardians - Gruppe 3",
                "Delta Drivers - Gruppe 4", "Epsilon Engineers - Gruppe 5", "Zeta Zealots - Gruppe 6",
                "Eta Explorers - Gruppe 7", "Theta Thinkers - Gruppe 8", "Iota Innovators - Gruppe 9", "Kappa Knights - Gruppe 10"
        };
        String[] rooms = {
                "W-R1.141", "W-R2.186", "W-R3.141", "W-R3.186", "W-R1.186",
                "W-R1.161", "W-R2.163", "W-R3.161", "W-R2.164", "W-R3.163"
        };
        for (int i = 0; i < 10; i++) {
            String id = "Gruppe " + (i + 1);
            groups.put(id, new Group(id, groupNames[i], rooms[i]));
        }
    }

    public synchronized String generateName(String gender, String jobProfile, String division) {
        String[] titles = pickTitles(gender);
        String[] divTokens = DIVISION_NOUNS.getOrDefault(division, new String[]{"Phoenix", "Atlas", "Horizon"});
        String[] jobTokens = JOB_EPITHETS.getOrDefault(jobProfile, new String[]{"Module", "Signal", "Vector"});

        int h = stableHash(normalize(gender) + "|" + normalize(division) + "|" + normalize(jobProfile));

        String title = titles[h % titles.length];
        String divToken = divTokens[(h / 7) % divTokens.length];
        String jobToken = jobTokens[(h / 13) % jobTokens.length];

        return title + " " + divToken + " " + jobToken;
    }

    private static String generateNameWithSalt(String gender, String jobProfile, String division, int salt) {
        String[] titles = pickTitles(gender);
        String[] divTokens = DIVISION_NOUNS.getOrDefault(division, new String[]{"Phoenix", "Atlas", "Horizon"});
        String[] jobTokens = JOB_EPITHETS.getOrDefault(jobProfile, new String[]{"Module", "Signal", "Vector"});

        int h = stableHash(normalize(gender) + "|" + normalize(division) + "|" + normalize(jobProfile) + "|" + salt);

        String title = titles[h % titles.length];
        String divToken = divTokens[(h / 7) % divTokens.length];
        String jobToken = jobTokens[(h / 13) % jobTokens.length];

        return title + " " + divToken + " " + jobToken;
    }

    private static String[] pickTitles(String gender) {
        if (gender == null) return NEUTRAL_TITLES;
        return switch (gender.toLowerCase()) {
            case "male" -> MALE_TITLES;
            case "female" -> FEMALE_TITLES;
            default -> NEUTRAL_TITLES;
        };
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static int stableHash(String s) {
        return (s == null ? 0 : s.hashCode()) & 0x7fffffff;
    }

    public String generateAvatarUrl(String gender, String name) {
        String seed = seed(name);

        String style = "personas";
        String params = switch (normalizeAvatarUrl(gender)) {
            case "male" -> personasMaleParams();
            case "female" -> personasFemaleParams();
            default -> personasNeutralParams();
        };

        return "https://api.dicebear.com/9.x/"
               + style
               + "/svg?seed=" + url(seed)
               + params;
    }

    private static String personasMaleParams() {
        return "&hair=bald,balding,beanie,buzzcut,cap,fade,mohawk,shortCombover,shortComboverChops"
               + "&facialHair=beardMustache,goatee,pyramid,shadow,soulPatch,walrus"
               + "&facialHairProbability=50"
                + "&mouth=bigSmile,pacifier,smile,smirk,surprise";
    }

    private static String personasFemaleParams() {
        return "&hair=bobBangs,bobCut,bunUndercut,curly,curlyBun,curlyHighTop,extraLong,long,pigtails,sideShave,straightBun"
               + "&facialHairProbability=0"
                + "&mouth=bigSmile,pacifier,smile,smirk,surprise,lips";
    }

    private static String personasNeutralParams() {
        return "&hair=bald,beanie,sideShave,mohawk,long,curly,curlyBun"
                + "&mouth=bigSmile,pacifier,smile,smirk,surprise";
    }

    private static String seed(String name) {
        String base = (name == null ? "Phoenix" : name).replaceAll("\\s+", "");
        return base.isBlank() ? "Phoenix" : base;
    }

    private static String normalizeAvatarUrl(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public User getOrCreateUser(String sessionId) {
        return usersBySession.computeIfAbsent(sessionId, User::new);
    }

    public User registerUser(String sessionId, String gender, String jobProfile, String division) {
        User user = usersBySession.get(sessionId);
        if (user != null && user.isRegistered()) {
            return user;
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

        String uniqueName = allocateUniqueName(id, gender, jobProfile, division);
        user.setGeneratedName(uniqueName);
        user.setAvatarUrl(generateAvatarUrl(gender, uniqueName));

        user.setRegistered(true);
        usersById.put(id, user);
        return user;
    }

    private String allocateUniqueName(String userId, String gender, String jobProfile, String division) {
        for (int salt = 0; salt < MAX_NAME_ATTEMPTS; salt++) {
            String candidate = (salt == 0)
                    ? generateName(gender, jobProfile, division)
                    : generateNameWithSalt(gender, jobProfile, division, salt);

            String existing = nameToUserId.putIfAbsent(candidate, userId);
            if (existing == null || existing.equals(userId)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate unique name after " + MAX_NAME_ATTEMPTS + " attempts");
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

    public List<User> getUsersForGroup(String groupId) {
        Group group = groups.get(groupId);
        if (group == null || group.getUserIds() == null) return List.of();

        return group.getUserIds().stream()
                .map(usersById::get)
                .filter(u -> u != null && u.isRegistered())
                .collect(Collectors.toList());
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