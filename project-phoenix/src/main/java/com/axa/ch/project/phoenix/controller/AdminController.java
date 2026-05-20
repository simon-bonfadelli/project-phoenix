package com.axa.ch.project.phoenix.controller;

import com.axa.ch.project.phoenix.model.GroupImport;
import com.axa.ch.project.phoenix.model.User;
import com.axa.ch.project.phoenix.service.SseService;
import com.axa.ch.project.phoenix.service.UserStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final int TARGET_GROUP_COUNT = 10;

    private final UserStore userStore;
    private final SseService sseService;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminController(UserStore userStore, SseService sseService) {
        this.userStore = userStore;
        this.sseService = sseService;
    }

    @GetMapping
    public String admin(Model model) {
        Collection<User> users = userStore.getAllRegisteredUsers();

        model.addAttribute("users", users);
        model.addAttribute("groups", userStore.getGroups().values());
        model.addAttribute("stats", buildStats(users, resolveOrCreateTenGroupIds()));

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
            m.put("generatedName", u.getGeneratedName());
            m.put("gender", u.getGender());
            m.put("jobProfile", u.getJobProfile());
            m.put("division", u.getDivision());
            m.put("groupId", u.getGroupId());
            return m;
        }).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("users", userList);

        return ResponseEntity.ok(payload);
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

        sseService.notifyUsers("<div id=\"group-info\" hx-get=\"/my-group\" hx-trigger=\"load\" hx-swap=\"outerHTML\">Loading group...</div>");
        return ResponseEntity.ok("Import successful");
    }

    @PostMapping("/allocate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> allocateNewUsersToGroups() {
        List<User> allUsers = new ArrayList<>(userStore.getAllRegisteredUsers());

        List<User> unassigned = allUsers.stream()
                .filter(u -> u.getGroupId() == null || u.getGroupId().isBlank())
                .toList();

        List<String> groupIds = resolveOrCreateTenGroupIds();

        Map<String, GroupState> stateByGroup = new LinkedHashMap<>();
        for (String gid : groupIds) stateByGroup.put(gid, new GroupState(gid));

        for (User u : allUsers) {
            String gid = normalize(u.getGroupId());
            if (gid != null && stateByGroup.containsKey(gid)) {
                stateByGroup.get(gid).addExisting(u);
            }
        }

        Map<String, Integer> genderFreq = frequency(unassigned, User::getGender);
        Map<String, Integer> divisionFreq = frequency(unassigned, User::getDivision);
        Map<String, Integer> jobFreq = frequency(unassigned, User::getJobProfile);

        List<User> ordered = new ArrayList<>(unassigned);
        ordered.sort(Comparator
                .comparingDouble((User u) -> rarityScore(u, genderFreq, divisionFreq, jobFreq))
                .reversed()
                .thenComparing(u -> normalize(u.getId()), Comparator.nullsLast(String::compareTo)));

        int assignedCount = 0;
        for (User u : ordered) {
            String bestGroup = pickBestGroup(u, stateByGroup);
            if (bestGroup == null) continue;

            userStore.assignUserToGroup(u.getId(), bestGroup);
            stateByGroup.get(bestGroup).addNew(u);
            assignedCount++;
        }

        sseService.notifyUsers("<div id=\"group-info\" hx-get=\"/my-group\" hx-trigger=\"load\" hx-swap=\"outerHTML\">Loading group...</div>");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unassignedBefore", unassigned.size());
        result.put("assignedNow", assignedCount);
        result.put("groups", groupIds);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> buildStats(Collection<User> users, List<String> groupIds) {
        // Only assigned users contribute to even\-distribution checks.
        List<User> assigned = users.stream()
                .filter(u -> normalize(u.getGroupId()) != null)
                .toList();

        // Per group (for evenness calculations)
        Map<String, Map<String, Long>> byGroupGender = countByGroupAndAttr(assigned, groupIds, User::getGender);
        Map<String, Map<String, Long>> byGroupDivision = countByGroupAndAttr(assigned, groupIds, User::getDivision);
        Map<String, Map<String, Long>> byGroupJob = countByGroupAndAttr(assigned, groupIds, User::getJobProfile);

        // Evenness summary (single number per attribute)
        Map<String, Object> evenGender = evennessSummary(byGroupGender, groupIds.size(), 1);
        Map<String, Object> evenDivision = evennessSummary(byGroupDivision, groupIds.size(), 1);
        Map<String, Object> evenJob = evennessSummary(byGroupJob, groupIds.size(), 1);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("assignedUsers", assigned.size());
        stats.put("unassignedUsers", users.size() - assigned.size());

        stats.put("evennessGender", evenGender);
        stats.put("evennessDivision", evenDivision);
        stats.put("evennessJobProfile", evenJob);

        return stats;
    }

    private static Map<String, Map<String, Long>> countByGroupAndAttr(
            List<User> assigned,
            List<String> groupIds,
            java.util.function.Function<User, String> attrFn
    ) {
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        for (String gid : groupIds) out.put(gid, new TreeMap<>());

        for (User u : assigned) {
            String gid = normalize(u.getGroupId());
            if (gid == null || !out.containsKey(gid)) continue;

            String a = normalize(attrFn.apply(u));
            if (a == null) a = "Unknown";

            Map<String, Long> m = out.get(gid);
            m.put(a, m.getOrDefault(a, 0L) + 1L);
        }
        return out;
    }

    private static Map<String, Object> evennessSummary(Map<String, Map<String, Long>> byGroup, int groupCount, int allowedDeviation) {
        // Flatten to per\-value list of counts across groups.
        Set<String> values = new TreeSet<>();
        for (Map<String, Long> m : byGroup.values()) values.addAll(m.keySet());

        double worstCv = 0.0;
        boolean ok = true;

        List<Map<String, Object>> perValue = new ArrayList<>();
        for (String v : values) {
            List<Long> counts = new ArrayList<>(groupCount);
            for (Map<String, Long> m : byGroup.values()) {
                counts.add(m.getOrDefault(v, 0L));
            }

            long min = counts.stream().min(Long::compare).orElse(0L);
            long max = counts.stream().max(Long::compare).orElse(0L);
            double avg = counts.stream().mapToLong(x -> x).average().orElse(0.0);

            double cv = coefficientOfVariation(counts, avg);
            worstCv = Math.max(worstCv, cv);

            boolean pass = (max - min) <= allowedDeviation;
            ok = ok && pass;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", v);
            row.put("min", min);
            row.put("max", max);
            row.put("avg", round2(avg));
            row.put("cv", round3(cv));
            row.put("pass", pass);
            perValue.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowedDeviation", allowedDeviation);
        out.put("overallPass", ok);
        out.put("worstCv", round3(worstCv));
        out.put("perValue", perValue);
        return out;
    }

    private static double coefficientOfVariation(List<Long> counts, double mean) {
        if (mean <= 0.0) return 0.0;
        double var = 0.0;
        for (long c : counts) {
            double d = c - mean;
            var += d * d;
        }
        var /= counts.size();
        double std = Math.sqrt(var);
        return std / mean;
    }

    private static double round2(double x) { return Math.round(x * 100.0) / 100.0; }
    private static double round3(double x) { return Math.round(x * 1000.0) / 1000.0; }

    private List<String> resolveOrCreateTenGroupIds() {
        List<String> existing = new ArrayList<>(userStore.getGroups().keySet());
        existing.sort(String::compareTo);

        if (existing.size() >= TARGET_GROUP_COUNT) {
            return existing.subList(0, TARGET_GROUP_COUNT);
        }

        LinkedHashSet<String> ids = new LinkedHashSet<>(existing);
        for (int i = 1; ids.size() < TARGET_GROUP_COUNT; i++) {
            ids.add("G" + i);
        }
        return new ArrayList<>(ids);
    }

    private static String pickBestGroup(User u, Map<String, GroupState> stateByGroup) {
        double best = Double.POSITIVE_INFINITY;
        String bestGroup = null;

        for (GroupState gs : stateByGroup.values()) {
            double score = gs.scoreIfAdded(u);
            if (score < best || (score == best && gs.totalAfterIfAdded(u) < stateByGroup.get(bestGroup).totalAfterIfAdded(u))) {
                best = score;
                bestGroup = gs.groupId;
            }
        }
        return bestGroup;
    }

    private static double rarityScore(
            User u,
            Map<String, Integer> genderFreq,
            Map<String, Integer> divisionFreq,
            Map<String, Integer> jobFreq
    ) {
        double g = invFreq(genderFreq.get(normalize(u.getGender())));
        double d = invFreq(divisionFreq.get(normalize(u.getDivision())));
        double j = invFreq(jobFreq.get(normalize(u.getJobProfile())));
        return g + d + j;
    }

    private static double invFreq(Integer freq) {
        if (freq == null || freq <= 0) return 1.0;
        return 1.0 / freq;
    }

    private static Map<String, Integer> frequency(List<User> users, java.util.function.Function<User, String> keyFn) {
        Map<String, Integer> m = new HashMap<>();
        for (User u : users) {
            String k = normalize(keyFn.apply(u));
            if (k == null) continue;
            m.put(k, m.getOrDefault(k, 0) + 1);
        }
        return m;
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static final class GroupState {
        private final String groupId;

        private int totalExisting = 0;
        private int totalNew = 0;

        private final Map<String, Integer> gender = new HashMap<>();
        private final Map<String, Integer> division = new HashMap<>();
        private final Map<String, Integer> jobProfile = new HashMap<>();

        private GroupState(String groupId) {
            this.groupId = groupId;
        }

        void addExisting(User u) {
            totalExisting++;
            inc(gender, normalize(u.getGender()));
            inc(division, normalize(u.getDivision()));
            inc(jobProfile, normalize(u.getJobProfile()));
        }

        void addNew(User u) {
            totalNew++;
            inc(gender, normalize(u.getGender()));
            inc(division, normalize(u.getDivision()));
            inc(jobProfile, normalize(u.getJobProfile()));
        }

        int totalAfterIfAdded(User u) {
            return totalExisting + totalNew + 1;
        }

        double scoreIfAdded(User u) {
            String g = normalize(u.getGender());
            String d = normalize(u.getDivision());
            String j = normalize(u.getJobProfile());

            int size = totalExisting + totalNew;

            double sizePenalty = size * 1.0;

            double genderPenalty = categoryPenalty(gender, g);
            double divisionPenalty = categoryPenalty(division, d);
            double jobPenalty = categoryPenalty(jobProfile, j);

            return (sizePenalty * 1.0) + (genderPenalty * 2.0) + (divisionPenalty * 2.0) + (jobPenalty * 3.0);
        }

        private static double categoryPenalty(Map<String, Integer> counts, String key) {
            if (key == null) return 0.25;
            int c = counts.getOrDefault(key, 0);
            return c * c;
        }

        private static void inc(Map<String, Integer> m, String key) {
            if (key == null) return;
            m.put(key, m.getOrDefault(key, 0) + 1);
        }
    }
}