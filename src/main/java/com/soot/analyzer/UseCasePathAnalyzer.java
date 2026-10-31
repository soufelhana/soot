package com.soot.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.soot.analyzer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.G;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.spark.SparkTransformer;
import soot.options.Options;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public class UseCasePathAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(UseCasePathAnalyzer.class);

    public static void main(String[] args) {
        String appClasspath = null;
        String entrypointSig = null;
        String basePackage = "";
        String outputPath = "usecase-paths.json";
        String typeFilter = null;
        boolean useSpark = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-cp":
                case "--classpath":
                    if (i + 1 < args.length) appClasspath = args[++i];
                    break;
                case "-e":
                case "--entrypoint":
                    if (i + 1 < args.length) entrypointSig = args[++i];
                    break;
                case "-p":
                case "--package":
                    if (i + 1 < args.length) basePackage = args[++i];
                    break;
                case "-o":
                case "--output":
                    if (i + 1 < args.length) outputPath = args[++i];
                    break;
                case "-t":
                case "--type":
                    if (i + 1 < args.length) typeFilter = args[++i];
                    break;
                case "--spark":
                    useSpark = true;
                    break;
                case "-h":
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (appClasspath == null) {
            System.err.println("Error: --classpath (-cp) is required.");
            printUsage();
            System.exit(1);
        }

        try {
            AnalysisResult result = runAnalysis(appClasspath, entrypointSig, basePackage, typeFilter, useSpark);

            // Write JSON
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            File outFile = new File(outputPath);
            if (outFile.getParentFile() != null) {
                outFile.getParentFile().mkdirs();
            }
            mapper.writeValue(outFile, result);

            System.out.println("================================================================================");
            System.out.println("  SOOT USE CASE PATH ANALYSIS COMPLETE");
            System.out.println("================================================================================");
            System.out.println("Base Package       : " + (result.getBasePackage() == null || result.getBasePackage().isEmpty() ? "(all)" : result.getBasePackage()));
            System.out.println("Total Entrypoints  : " + result.getTotalEntrypoints());
            System.out.println("Total Paths        : " + result.getTotalPaths());
            System.out.println("  - Nominal        : " + result.getNominalPathsCount());
            System.out.println("  - Rejections     : " + result.getRejectionPathsCount());
            System.out.println("Total Sinks        : " + result.getTotalSinks());
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("Discovered Entrypoints & Use Cases:");
            for (EntrypointAnalysis ea : result.getEntrypoints()) {
                System.out.println("  [" + ea.getType() + "] " + ea.getDescription());
                System.out.println("    Signature  : " + ea.getEntrypoint());
                System.out.println("    Paths      : " + ea.getTotalPaths() + " (Nominal: " + ea.getNominalPathsCount() + ", Rejection: " + ea.getRejectionPathsCount() + ")");
                if (!ea.getDetectedSinks().isEmpty()) {
                    System.out.println("    Sinks (" + ea.getDetectedSinks().size() + "):");
                    for (SinkInfo s : ea.getDetectedSinks()) {
                        System.out.println("      * [" + s.getType() + "] " + s.getDescription() + " (line " + s.getSourceLine() + ")");
                    }
                }
                if (!ea.getDetectedRejections().isEmpty()) {
                    System.out.println("    Rejections (" + ea.getDetectedRejections().size() + "):");
                    for (RejectionInfo r : ea.getDetectedRejections()) {
                        System.out.println("      * [THROW] " + r.getExceptionClass() + " at " + r.getEnclosingClass() + ":" + r.getSourceLine());
                    }
                }
            }
            System.out.println("================================================================================");
            System.out.println("Output JSON        : " + outFile.getAbsolutePath());
            System.out.println("================================================================================");

        } catch (Exception e) {
            System.err.println("Analysis failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static AnalysisResult runAnalysis(String appClasspath,
                                             String entrypointSig,
                                             String basePackage,
                                             boolean useSpark) throws IOException {
        return runAnalysis(appClasspath, entrypointSig, basePackage, null, useSpark);
    }

    public static AnalysisResult runAnalysis(String appClasspath,
                                             String entrypointSig,
                                             String basePackage,
                                             String typeFilter,
                                             boolean useSpark) throws IOException {
        G.reset();

        AnalysisResult result = new AnalysisResult();
        result.setAnalysisTimestamp(Instant.now().toString());
        result.setBasePackage(basePackage);

        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_prepend_classpath(true);
        Options.v().set_src_prec(Options.src_prec_only_class);

        // Setup process dirs and classpath
        List<String> processDirs = new ArrayList<>();
        for (String cpEntry : appClasspath.split(File.pathSeparator)) {
            File f = new File(cpEntry);
            if (f.exists() && f.isDirectory()) {
                processDirs.add(f.getAbsolutePath());
            }
        }
        if (processDirs.isEmpty()) {
            processDirs.add(appClasspath);
        }
        Options.v().set_process_dir(processDirs);
        Options.v().set_soot_classpath(appClasspath);

        if (useSpark) {
            Map<String, String> opt = new HashMap<>();
            opt.put("verbose", "false");
            opt.put("propagator", "worklist");
            opt.put("simple-edges-bidirectional", "false");
            opt.put("on-fly-cg", "true");
            opt.put("set-impl", "double");
            opt.put("double-set-old", "hybrid");
            opt.put("double-set-new", "hybrid");
            SparkTransformer.v().transform("", opt);
        }

        Scene.v().loadNecessaryClasses();

        List<EntrypointInfo> entrypointsToAnalyze = new ArrayList<>();

        if (entrypointSig != null && !entrypointSig.isBlank()) {
            // Manual entrypoint override
            SootMethod entryMethod = findEntrypointMethod(entrypointSig);
            if (entryMethod == null) {
                throw new IllegalArgumentException("Could not resolve entrypoint method: " + entrypointSig);
            }
            EntrypointInfo ep = new EntrypointInfo(
                    EntrypointType.MANUAL_ENTRYPOINT,
                    entryMethod.getDeclaringClass().getName(),
                    entryMethod.getName(),
                    entryMethod.getSignature(),
                    getMethodLineNumber(entryMethod),
                    "Manual Entrypoint: " + entryMethod.getDeclaration()
            );
            entrypointsToAnalyze.add(ep);
        } else {
            // Automated discovery across all application classes
            List<EntrypointInfo> detected = EntrypointDetector.detectEntrypoints(Scene.v().getApplicationClasses(), basePackage);

            // Optional type filtering
            if (typeFilter != null && !typeFilter.isBlank()) {
                Set<String> requestedTypes = new HashSet<>();
                for (String t : typeFilter.split(",")) {
                    requestedTypes.add(t.trim().toUpperCase());
                }
                detected.removeIf(ep -> !matchesTypeFilter(ep.getType(), requestedTypes));
            }

            if (detected.isEmpty()) {
                throw new IllegalStateException("No entrypoints detected in application classes (package: '" +
                        basePackage + "'). Please verify classpath and package filter.");
            }

            entrypointsToAnalyze.addAll(detected);
        }

        // Set entry points in Scene
        List<SootMethod> entryMethods = new ArrayList<>();
        Map<EntrypointInfo, SootMethod> resolvedMap = new LinkedHashMap<>();

        for (EntrypointInfo ep : entrypointsToAnalyze) {
            SootMethod sm = findEntrypointMethod(ep.getSignature());
            if (sm != null) {
                entryMethods.add(sm);
                resolvedMap.put(ep, sm);
            } else {
                log.warn("Could not resolve SootMethod for entrypoint: {}", ep.getSignature());
            }
        }

        if (entryMethods.isEmpty()) {
            throw new IllegalStateException("None of the detected entrypoint methods could be resolved in Soot Scene.");
        }

        Scene.v().setEntryPoints(entryMethods);

        // Run Path Finder for each entrypoint
        for (Map.Entry<EntrypointInfo, SootMethod> entry : resolvedMap.entrySet()) {
            EntrypointInfo ep = entry.getKey();
            SootMethod sm = entry.getValue();

            EntrypointAnalysis ea = new EntrypointAnalysis(ep);
            ea.setBasePackage(basePackage);

            PathFinder pathFinder = new PathFinder(basePackage, ea);
            pathFinder.analyze(sm);

            result.addEntrypointAnalysis(ea);
        }

        return result;
    }

    private static boolean matchesTypeFilter(EntrypointType type, Set<String> requested) {
        if (type == null) return false;
        String typeName = type.name();
        for (String req : requested) {
            if (typeName.contains(req) ||
               (req.equals("REST") && type == EntrypointType.REST_ENDPOINT) ||
               (req.equals("KAFKA") && type == EntrypointType.KAFKA_LISTENER) ||
               (req.equals("SCHEDULED") && type == EntrypointType.SCHEDULED_TASK) ||
               (req.equals("BATCH") && type == EntrypointType.BATCH_JOB) ||
               (req.equals("EVENT") && type == EntrypointType.SPRING_EVENT) ||
               (req.equals("RUNNER") && type == EntrypointType.APPLICATION_RUNNER) ||
               (req.equals("TEMPORAL") && (type == EntrypointType.TEMPORAL_WORKFLOW || type == EntrypointType.TEMPORAL_ACTIVITY))) {
                return true;
            }
        }
        return false;
    }

    private static int getMethodLineNumber(SootMethod method) {
        if (method == null) return -1;
        if (!method.hasActiveBody()) {
            try {
                method.retrieveActiveBody();
            } catch (Exception ignored) {
                return -1;
            }
        }
        try {
            return method.getActiveBody().getUnits().getFirst().getJavaSourceStartLineNumber();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static SootMethod findEntrypointMethod(String sigOrName) {
        // 1. Try full Soot signature: <pkg.Class: ReturnType methodName(ParamTypes)>
        if (sigOrName.startsWith("<") && sigOrName.endsWith(">")) {
            try {
                return Scene.v().getMethod(sigOrName);
            } catch (Exception ignored) {}
        }

        // 2. Try Class:methodName or Class#methodName format
        String className;
        String methodName;
        if (sigOrName.contains("#")) {
            String[] parts = sigOrName.split("#");
            className = parts[0].trim();
            methodName = parts[1].trim();
        } else if (sigOrName.contains(":") && !sigOrName.startsWith("<")) {
            String[] parts = sigOrName.split(":");
            className = parts[0].trim();
            methodName = parts[1].trim();
        } else {
            int lastDot = sigOrName.lastIndexOf('.');
            if (lastDot > 0) {
                className = sigOrName.substring(0, lastDot).trim();
                methodName = sigOrName.substring(lastDot + 1).trim();
            } else {
                return null;
            }
        }

        if (Scene.v().containsClass(className)) {
            SootClass sc = Scene.v().getSootClass(className);
            for (SootMethod sm : sc.getMethods()) {
                if (sm.getName().equals(methodName)) {
                    return sm;
                }
            }
        }

        // Scan all loaded application classes
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            if (sc.getName().equals(className) || sc.getShortName().equals(className)) {
                for (SootMethod sm : sc.getMethods()) {
                    if (sm.getName().equals(methodName)) {
                        return sm;
                    }
                }
            }
        }

        return null;
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar usecase-path-analyzer.jar [options]");
        System.out.println("Options:");
        System.out.println("  -cp, --classpath <path>     Classpath to analyze (target/classes, jars) [REQUIRED]");
        System.out.println("  -p,  --package <pkg>        Base package filter (e.g. 'com.bforbank')");
        System.out.println("  -e,  --entrypoint <sig>     Specific entrypoint signature override (optional)");
        System.out.println("  -t,  --type <type>          Filter entrypoints by type (e.g. 'rest,kafka,scheduled')");
        System.out.println("  -o,  --output <file>        Output JSON path (default: usecase-paths.json)");
        System.out.println("  --spark                     Use SPARK points-to analysis instead of CHA");
        System.out.println("  -h,  --help                 Show this help message");
    }
}
