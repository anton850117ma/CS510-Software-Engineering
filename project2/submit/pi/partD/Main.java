// Further Enhanced version for part D
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        File file = new File(args[0]);
        BugKiller bugKiller;
        if (!(args.length == 1 || args.length == 3 || args.length == 4 || args.length == 5)) System.out.println("Wrong number of arguments!");
        if (args.length == 1) bugKiller = new BugKiller(file);
        else if (args.length == 3) bugKiller = new BugKiller(file, Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        else if (args.length == 4) bugKiller = new BugKiller(file, Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        else bugKiller = new BugKiller(file, Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
        bugKiller.buildCallSites();
        bugKiller.inline();
        bugKiller.generateKillerBook();
        bugKiller.findBugs();
    }
}

class BugKiller {
    private int supportThreshold = 3;
    private int confidenceThreshold = 65;
    private int levels = 0;
    private int unpairedRatioThreshold = 100;
    private File file;


    // method name -> method count
    private Map<String, Integer> methodCnt = new HashMap<>();

    // method pair (no order) -> method pair count
    private Map<Set<String>, Integer> pairCnt = new HashMap<>();

    // caller -> callees set
    private Map<String, Set<String>> callSites = new HashMap<>();

    // caller -> inlined callees set
    private Map<String, Set<String>> callSitesInlined = new HashMap<>();

    // method -> [(partnerName, pair support, confidence), ...]
    // only contains method pair with support and confidence both higher than threshold
    private Map<String, List<Partner>> killerBook = new HashMap<>();

    public BugKiller(File file) {
        this.file = file;
    }

    public BugKiller(File file, int supportThreshold, int confidenceThreshold) {
        this.file = file;
        this.supportThreshold = supportThreshold;
        this.confidenceThreshold = confidenceThreshold;
    }

    public BugKiller(File file, int supportThreshold, int confidenceThreshold, int levels) {
        this.file = file;
        this.supportThreshold = supportThreshold;
        this.confidenceThreshold = confidenceThreshold;
        this.levels = levels;
    }

    public BugKiller(File file, int supportThreshold, int confidenceThreshold, int levels, int unpairedRatioThreshold) {
        this.file = file;
        this.supportThreshold = supportThreshold;
        this.confidenceThreshold = confidenceThreshold;
        this.levels = levels;
        this.unpairedRatioThreshold = unpairedRatioThreshold;
    }

    public void buildCallSites() {
        try {
            Scanner sc = new Scanner(file, "UTF-8");
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith("Call")) {
                    // Skip null function node and the empty line after it
                    if (line.contains("null function")) {
                        while (sc.hasNextLine() && sc.nextLine().startsWith("  CS"));
                        continue;
                    }
                    else {
                        int idx1 = line.indexOf('\'');
                        int idx2 = line.indexOf('\'' , idx1 + 1);
                        String scope = line.substring(idx1 + 1, idx2);
                        if (callSites.containsKey(scope)) System.out.println("Error: duplicated callsite!");
                        Set<String> methodSet = new HashSet<>();
                        while (sc.hasNextLine()) {
                            line = sc.nextLine();
                            if (line.startsWith("  CS")) {
                                idx1 = line.indexOf('\'');
                                if (idx1 == -1) continue;
                                idx2 = line.indexOf('\'' , idx1 + 1);
                                String method = line.substring(idx1 + 1, idx2);
                                methodSet.add(method);
                            }
                            // Skip Empty line after a call site
                            else break;
                        }

                        // No valid call site, skip
                        if (methodSet.isEmpty()) continue;
                        callSites.put(scope, methodSet);
                    }
                }
            }
            sc.close();
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    // Perform method inline, expand scope
    public void inline() {
        if (levels == 0) callSitesInlined = callSites;
        for (int round = 1; round <= levels; round++) {
            if (round == 1) {
                for (Map.Entry<String, Set<String>> entry : callSites.entrySet()) {
                    String caller = entry.getKey();
                    Set<String> calleeSet = entry.getValue();
                    Set<String> calleeSetInlined = new HashSet<>();
                    for (String method : calleeSet) {
                        if (callSites.containsKey(method)) {
                            calleeSetInlined.addAll(callSites.get(method));
                        }
                        calleeSetInlined.add(method);
                    }
                    callSitesInlined.put(caller, calleeSetInlined);
                }
            }
            else {
                for (Map.Entry<String, Set<String>> entry : callSitesInlined.entrySet()) {
                    String caller = entry.getKey();
                    Set<String> calleeSet = entry.getValue();
                    Set<String> calleeSetInlined = new HashSet<>();
                    for (String method : calleeSet) {
                        if (callSites.containsKey(method)) {
                            calleeSetInlined.addAll(callSites.get(method));
                        }
                        calleeSetInlined.add(method);
                    }
                    callSitesInlined.put(caller, calleeSetInlined);
                }
            }
        }
    }

    public void generateKillerBook() {
        // Count support for every method
        for (Map.Entry<String, Set<String>> entry : callSites.entrySet()) {
            Set<String> methodSet = entry.getValue();
            for (String method : methodSet) {
                methodCnt.put(method, methodCnt.getOrDefault(method, 0) + 1);
            }
            // Count support for every pair
            List<String> methodList = new ArrayList(methodSet);
            for (int i = 0; i < methodList.size(); i++) {
                for (int j = i + 1; j < methodList.size(); j++) {
                    Set<String> pair = new HashSet<>();
                    pair.add(methodList.get(i));
                    pair.add(methodList.get(j));
                    pairCnt.put(pair, pairCnt.getOrDefault(pair, 0) + 1);
                }
            }
        }

        // Generate killer book
        // method -> [paired method -> [support, confidence], ...]
        // Map<String, List<Map<String, int[]>>>
        for (Set<String> pair : pairCnt.keySet()) {
            List<String> twoMethods = new ArrayList<>(pair);
            String method1 = twoMethods.get(0);
            String method2 = twoMethods.get(1);

            int pairSupport = pairCnt.get(pair);
            if (pairSupport >= supportThreshold) {
                double confidence1 = (1.0 * pairSupport) / methodCnt.get(method1) * 100;
                double confidence2 = (1.0 * pairSupport) / methodCnt.get(method2) * 100;
                if (confidence1 >= confidenceThreshold) {
                    if (!killerBook.containsKey(method1)) killerBook.put(method1, new ArrayList<>());
                    killerBook.get(method1).add(new Partner(method2, pairSupport, confidence1));
                }
                if (confidence2 >= confidenceThreshold) {
                    if (!killerBook.containsKey(method2)) killerBook.put(method2, new ArrayList<>());
                    killerBook.get(method2).add(new Partner(method1, pairSupport, confidence2));
                }
            }
        }
    }

    public void findBugs() {
        // Analysis for every call site
        for (String caller : callSites.keySet()) {
            // Retrieve all callee in this call site
            Set<String> calleeSet = callSites.get(caller);
            Set<String> calleeSetInlined = callSitesInlined.get(caller);
            for (String callee : calleeSet) {
                // Retrieve all partners of this callee, which should appear together with this callee
                if (!killerBook.containsKey(callee)) continue;
                List<Partner> partners = killerBook.get(callee);

                // Count unpaired number
                int cnt = 0;
                for (Partner partner : partners) {
                    if (!calleeSetInlined.contains(partner.name)) cnt++;
                }
                double unpairedRatio = 100.0 * cnt / partners.size();

                for (Partner partner : partners) {
                    if (!calleeSetInlined.contains(partner.name) && unpairedRatio >= unpairedRatioThreshold) {
                        // Find a bug and output log
                        String[] pair = {callee, partner.name};
                        Arrays.sort(pair);
                        String bug = String.format("bug: %s in %s, pair: (%s, %s), support: %d, confidence: %.2f%%, UP-Ratio: %.2f%%",
                                callee, caller, pair[0], pair[1], partner.support, partner.confidence, unpairedRatio);
                        System.out.println(bug);
                    }
                }
            }
        }
    }

}

class Partner {
    public String name;
    public int support;
    public double confidence;

    public Partner(String name, int support, double confidence) {
        this.name = name;
        this.support = support;
        this.confidence = confidence;
    }

    @Override
    public String toString() {
        return "Partner{" +
                "name='" + name + '\'' +
                ", support=" + support +
                ", confidence=" + confidence +
                '}';
    }
}