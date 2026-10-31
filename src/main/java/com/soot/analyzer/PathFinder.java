package com.soot.analyzer;

import com.soot.analyzer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.tagkit.LineNumberTag;
import soot.tagkit.SourceLnPosTag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.*;

public class PathFinder {
    private static final Logger log = LoggerFactory.getLogger(PathFinder.class);

    private static final int MAX_PATHS = 100;
    private static final int MAX_CALL_DEPTH = 15;
    private static final int MAX_UNITS_PER_PATH = 300;
    private static final int MAX_BRANCHES_PER_LEVEL = 10;
    private static final int MAX_CALLEE_COMPLETIONS = 3;

    private final String basePackage;
    private final EntrypointAnalysis result;
    private int pathSequence = 0;

    public PathFinder(String basePackage, EntrypointAnalysis result) {
        this.basePackage = basePackage;
        this.result = result;
    }

    public PathFinder(String basePackage, AnalysisResult legacyResult) {
        this.basePackage = basePackage;
        EntrypointAnalysis ea = new EntrypointAnalysis();
        this.result = ea;
        legacyResult.addEntrypointAnalysis(ea);
        legacyResult.setBasePackage(basePackage);
    }

    public void analyze(SootMethod entryMethod) {
        log.info("Starting path analysis for entrypoint: {}", entryMethod.getSignature());
        result.setEntrypoint(entryMethod.getSignature());
        result.setEntrypointClass(entryMethod.getDeclaringClass().getName());
        result.setEntrypointMethod(entryMethod.getName());
        result.setBasePackage(basePackage);

        int entryLine = getMethodLineNumber(entryMethod);
        result.setEntrypointLine(entryLine);
        result.addSourceLine(entryMethod.getDeclaringClass().getName(), entryLine);

        List<PathStep> initialSteps = new ArrayList<>();
        PathStep entryStep = new PathStep(
                1,
                entryMethod.getDeclaringClass().getName(),
                entryMethod.getName(),
                entryMethod.getSignature(),
                entryLine,
                PathStepType.ENTRYPOINT,
                "Entrypoint: " + entryMethod.getDeclaration(),
                "Execution begins at entrypoint"
        );
        initialSteps.add(entryStep);

        Deque<SootMethod> callStack = new ArrayDeque<>();
        callStack.push(entryMethod);

        Set<Unit> visitedUnitsInMethod = new HashSet<>();
        exploreMethod(entryMethod, initialSteps, callStack, visitedUnitsInMethod, 0);

        // Update counts
        result.setTotalPaths(result.getPaths().size());
        int nominal = 0;
        int rejection = 0;
        for (ExecutionPath p : result.getPaths()) {
            if (p.getType() == PathType.NOMINAL) nominal++;
            else if (p.getType() == PathType.REJECTION) rejection++;
        }
        result.setNominalPathsCount(nominal);
        result.setRejectionPathsCount(rejection);

        log.info("Path analysis completed: {} paths found ({} nominal, {} rejection)",
                result.getTotalPaths(), nominal, rejection);
    }

    private List<List<PathStep>> exploreMethod(SootMethod method,
                                               List<PathStep> currentSteps,
                                               Deque<SootMethod> callStack,
                                               Set<Unit> visitedUnits,
                                               int callDepth) {
        if (result.getPaths().size() >= MAX_PATHS) {
            return Collections.emptyList();
        }

        if (callDepth > MAX_CALL_DEPTH) {
            recordTerminalPath(currentSteps, PathType.INCOMPLETE,
                    "Max call depth reached (" + MAX_CALL_DEPTH + ")", null, null);
            return Collections.emptyList();
        }

        if (!method.hasActiveBody()) {
            try {
                method.retrieveActiveBody();
            } catch (Exception e) {
                return handleBodylessMethod(method, currentSteps, callStack, callDepth);
            }
        }

        Body body;
        try {
            body = method.getActiveBody();
        } catch (Exception e) {
            return handleBodylessMethod(method, currentSteps, callStack, callDepth);
        }

        UnitGraph cfg = new BriefUnitGraph(body);
        List<Unit> heads = cfg.getHeads();
        if (heads.isEmpty()) {
            return Collections.singletonList(currentSteps);
        }

        List<List<PathStep>> completions = new ArrayList<>();
        for (Unit head : heads) {
            if (result.getPaths().size() >= MAX_PATHS || completions.size() >= MAX_BRANCHES_PER_LEVEL) {
                break;
            }
            completions.addAll(exploreCfg(head, cfg, method, currentSteps, callStack, visitedUnits, callDepth));
        }
        if (completions.size() > MAX_BRANCHES_PER_LEVEL) {
            return completions.subList(0, MAX_BRANCHES_PER_LEVEL);
        }
        return completions;
    }

    private List<List<PathStep>> exploreCfg(Unit unit,
                                           UnitGraph cfg,
                                           SootMethod currentMethod,
                                           List<PathStep> currentSteps,
                                           Deque<SootMethod> callStack,
                                           Set<Unit> visitedInMethod,
                                           int callDepth) {
        if (result.getPaths().size() >= MAX_PATHS || currentSteps.size() >= MAX_UNITS_PER_PATH) {
            return Collections.emptyList();
        }

        // Loop detection in current method CFG
        if (visitedInMethod.contains(unit)) {
            List<Unit> succs = cfg.getSuccsOf(unit);
            for (Unit s : succs) {
                if (!visitedInMethod.contains(s)) {
                    return exploreCfg(s, cfg, currentMethod, currentSteps, callStack, visitedInMethod, callDepth);
                }
            }
            return Collections.singletonList(currentSteps);
        }

        Set<Unit> nextVisited = new HashSet<>(visitedInMethod);
        nextVisited.add(unit);

        int line = getUnitLineNumber(unit);
        if (line > 0) {
            result.addSourceLine(currentMethod.getDeclaringClass().getName(), line);
        }

        // 1. Throw statement -> REJECTION PATH (terminates this path branch)
        if (unit instanceof ThrowStmt) {
            ThrowStmt throwStmt = (ThrowStmt) unit;
            String exType = throwStmt.getOp().getType().toString();
            String instructionStr = throwStmt.toString();

            RejectionInfo rej = new RejectionInfo(
                    exType,
                    currentMethod.getDeclaringClass().getName(),
                    currentMethod.getName(),
                    line,
                    instructionStr,
                    "Throws " + exType + " at line " + line
            );
            result.getDetectedRejections().add(rej);

            List<PathStep> pathSteps = new ArrayList<>(currentSteps);
            PathStep step = new PathStep(
                    pathSteps.size() + 1,
                    currentMethod.getDeclaringClass().getName(),
                    currentMethod.getName(),
                    currentMethod.getSignature(),
                    line,
                    PathStepType.THROW_EXCEPTION,
                    instructionStr,
                    "Rejection / Guard failure: throws " + exType
            );
            step.setRejection(rej);
            pathSteps.add(step);

            recordTerminalPath(pathSteps, PathType.REJECTION,
                    "Rejection / Guard: throws " + exType + " at line " + line, null, rej);
            return Collections.emptyList();
        }

        // 2. Return statements -> Normal exit from current method
        if (unit instanceof ReturnStmt || unit instanceof ReturnVoidStmt) {
            List<PathStep> pathSteps = new ArrayList<>(currentSteps);
            PathStep step = new PathStep(
                    pathSteps.size() + 1,
                    currentMethod.getDeclaringClass().getName(),
                    currentMethod.getName(),
                    currentMethod.getSignature(),
                    line,
                    PathStepType.RETURN,
                    unit.toString(),
                    "Normal method return"
            );
            pathSteps.add(step);

            if (callStack.size() <= 1) {
                // Returned from top-level entrypoint!
                SinkInfo lastSink = findLastSink(pathSteps);
                PathType pType = (lastSink != null) ? PathType.NOMINAL : PathType.EARLY_EXIT;
                String reason = (lastSink != null)
                        ? "Nominal path reaching Sink: " + lastSink.getDescription()
                        : "Return without detected Sink";
                recordTerminalPath(pathSteps, pType, reason, lastSink, null);
                return Collections.emptyList();
            } else {
                // Return normal completion to caller
                return Collections.singletonList(pathSteps);
            }
        }

        // 3. Conditional Branch (IfStmt)
        if (unit instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) unit;
            Value condition = ifStmt.getCondition();
            Unit target = ifStmt.getTarget();

            List<List<PathStep>> branchCompletions = new ArrayList<>();

            // Branch True (Jump to target)
            List<PathStep> trueSteps = new ArrayList<>(currentSteps);
            PathStep trueStep = new PathStep(
                    trueSteps.size() + 1,
                    currentMethod.getDeclaringClass().getName(),
                    currentMethod.getName(),
                    currentMethod.getSignature(),
                    line,
                    PathStepType.BRANCH_DECISION,
                    "if " + condition + " -> jump",
                    "Condition TRUE: (" + condition + ")"
            );
            trueSteps.add(trueStep);
            branchCompletions.addAll(exploreCfg(target, cfg, currentMethod, trueSteps, callStack, nextVisited, callDepth));

            // Branch False (Fall-through)
            if (branchCompletions.size() < MAX_BRANCHES_PER_LEVEL && result.getPaths().size() < MAX_PATHS) {
                List<Unit> succs = cfg.getSuccsOf(unit);
                for (Unit succ : succs) {
                    if (succ != target) {
                        List<PathStep> falseSteps = new ArrayList<>(currentSteps);
                        PathStep falseStep = new PathStep(
                                falseSteps.size() + 1,
                                currentMethod.getDeclaringClass().getName(),
                                currentMethod.getName(),
                                currentMethod.getSignature(),
                                line,
                                PathStepType.BRANCH_DECISION,
                                "if !(" + condition + ") -> fallthrough",
                                "Condition FALSE: !(" + condition + ")"
                        );
                        falseSteps.add(falseStep);
                        branchCompletions.addAll(exploreCfg(succ, cfg, currentMethod, falseSteps, callStack, nextVisited, callDepth));
                        if (branchCompletions.size() >= MAX_BRANCHES_PER_LEVEL) break;
                    }
                }
            }
            if (branchCompletions.size() > MAX_BRANCHES_PER_LEVEL) {
                return branchCompletions.subList(0, MAX_BRANCHES_PER_LEVEL);
            }
            return branchCompletions;
        }

        // 4. Method Invocation (InvokeStmt or AssignStmt with InvokeExpr)
        InvokeExpr invokeExpr = null;
        if (unit instanceof InvokeStmt) {
            invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
        } else if (unit instanceof AssignStmt) {
            Value rightOp = ((AssignStmt) unit).getRightOp();
            if (rightOp instanceof InvokeExpr) {
                invokeExpr = (InvokeExpr) rightOp;
            }
        }

        if (invokeExpr != null) {
            return handleInvocation(invokeExpr, unit, cfg, currentMethod, currentSteps, callStack, nextVisited, callDepth, line);
        }

        // 5. Default: follow linear successors
        List<Unit> succs = cfg.getSuccsOf(unit);
        if (succs.isEmpty()) {
            return Collections.singletonList(currentSteps);
        }
        List<List<PathStep>> linearCompletions = new ArrayList<>();
        for (Unit succ : succs) {
            if (result.getPaths().size() >= MAX_PATHS || linearCompletions.size() >= MAX_BRANCHES_PER_LEVEL) break;
            linearCompletions.addAll(exploreCfg(succ, cfg, currentMethod, currentSteps, callStack, nextVisited, callDepth));
        }
        if (linearCompletions.size() > MAX_BRANCHES_PER_LEVEL) {
            return linearCompletions.subList(0, MAX_BRANCHES_PER_LEVEL);
        }
        return linearCompletions;
    }

    private List<List<PathStep>> handleInvocation(InvokeExpr invokeExpr,
                                                  Unit unit,
                                                  UnitGraph cfg,
                                                  SootMethod currentMethod,
                                                  List<PathStep> currentSteps,
                                                  Deque<SootMethod> callStack,
                                                  Set<Unit> visitedInMethod,
                                                  int callDepth,
                                                  int line) {
        List<PathStep> nextSteps = new ArrayList<>(currentSteps);

        // A. Dynamic Invoke (Lambdas & Stream operations)
        if (invokeExpr instanceof DynamicInvokeExpr) {
            DynamicInvokeExpr die = (DynamicInvokeExpr) invokeExpr;
            List<SootMethod> lambdaTargets = extractLambdaTargets(die);

            PathStep lambdaStep = new PathStep(
                    nextSteps.size() + 1,
                    currentMethod.getDeclaringClass().getName(),
                    currentMethod.getName(),
                    currentMethod.getSignature(),
                    line,
                    PathStepType.LAMBDA_CALL,
                    die.toString(),
                    "Lambda / Stream invokedynamic: " + die.getMethodRef().getName()
            );
            nextSteps.add(lambdaStep);

            List<List<PathStep>> activeStepsList = Collections.singletonList(nextSteps);
            for (SootMethod targetMethod : lambdaTargets) {
                if (targetMethod != null && !callStack.contains(targetMethod)) {
                    callStack.push(targetMethod);
                    List<List<PathStep>> lambdaCompletions = new ArrayList<>();
                    for (List<PathStep> active : activeStepsList) {
                        if (lambdaCompletions.size() >= MAX_BRANCHES_PER_LEVEL || result.getPaths().size() >= MAX_PATHS) break;
                        lambdaCompletions.addAll(exploreMethod(targetMethod, active, callStack, new HashSet<>(), callDepth + 1));
                    }
                    callStack.pop();
                    if (!lambdaCompletions.isEmpty()) {
                        activeStepsList = lambdaCompletions.size() > MAX_CALLEE_COMPLETIONS
                                ? lambdaCompletions.subList(0, MAX_CALLEE_COMPLETIONS)
                                : lambdaCompletions;
                    }
                }
            }

            // Continue with CFG successors in caller
            List<List<PathStep>> afterInvokeCompletions = new ArrayList<>();
            for (Unit succ : cfg.getSuccsOf(unit)) {
                if (afterInvokeCompletions.size() >= MAX_BRANCHES_PER_LEVEL || result.getPaths().size() >= MAX_PATHS) break;
                for (List<PathStep> active : activeStepsList) {
                    if (afterInvokeCompletions.size() >= MAX_BRANCHES_PER_LEVEL || result.getPaths().size() >= MAX_PATHS) break;
                    afterInvokeCompletions.addAll(exploreCfg(succ, cfg, currentMethod, active, callStack, visitedInMethod, callDepth));
                }
            }
            if (afterInvokeCompletions.size() > MAX_BRANCHES_PER_LEVEL) {
                return afterInvokeCompletions.subList(0, MAX_BRANCHES_PER_LEVEL);
            }
            return afterInvokeCompletions;
        }

        // B. Standard Invocation
        SootMethod targetMethod = null;
        try {
            targetMethod = invokeExpr.getMethod();
        } catch (Exception e) {
            try {
                targetMethod = invokeExpr.getMethodRef().resolve();
            } catch (Exception ignored) {}
        }

        if (targetMethod != null) {
            Optional<SinkInfo> sinkOpt = SinkDetector.detectSink(targetMethod, line);
            if (sinkOpt.isPresent()) {
                SinkInfo sink = sinkOpt.get();
                result.getDetectedSinks().add(sink);

                PathStepType sType = switch (sink.getType()) {
                    case DATABASE -> PathStepType.SINK_DATABASE;
                    case KAFKA -> PathStepType.SINK_KAFKA;
                    case HEXAGONAL_OUT_PORT -> PathStepType.SINK_PORT;
                    default -> PathStepType.METHOD_CALL;
                };

                PathStep sinkStep = new PathStep(
                        nextSteps.size() + 1,
                        currentMethod.getDeclaringClass().getName(),
                        currentMethod.getName(),
                        currentMethod.getSignature(),
                        line,
                        sType,
                        unit.toString(),
                        "Sink: " + sink.getDescription()
                );
                sinkStep.setSink(sink);
                nextSteps.add(sinkStep);

                // Kafka send is considered a terminal boundary
                if (sink.getType() == SinkType.KAFKA) {
                    recordTerminalPath(nextSteps, PathType.NOMINAL,
                            "Terminal Kafka Sink reached: " + sink.getDescription(), sink, null);
                }

                // Continue to CFG successors with this sink step
                List<List<PathStep>> afterSinkCompletions = new ArrayList<>();
                for (Unit succ : cfg.getSuccsOf(unit)) {
                    if (afterSinkCompletions.size() >= MAX_BRANCHES_PER_LEVEL || result.getPaths().size() >= MAX_PATHS) break;
                    afterSinkCompletions.addAll(exploreCfg(succ, cfg, currentMethod, nextSteps, callStack, visitedInMethod, callDepth));
                }
                if (afterSinkCompletions.size() > MAX_BRANCHES_PER_LEVEL) {
                    return afterSinkCompletions.subList(0, MAX_BRANCHES_PER_LEVEL);
                }
                return afterSinkCompletions;

            } else if (isApplicationClass(targetMethod.getDeclaringClass())) {
                // Application method call
                PathStep callStep = new PathStep(
                        nextSteps.size() + 1,
                        currentMethod.getDeclaringClass().getName(),
                        currentMethod.getName(),
                        currentMethod.getSignature(),
                        line,
                        PathStepType.METHOD_CALL,
                        unit.toString(),
                        "Calls " + targetMethod.getDeclaringClass().getShortName() + "." + targetMethod.getName() + "()"
                );
                nextSteps.add(callStep);

                List<List<PathStep>> calleeCompletions;
                if (targetMethod.getDeclaringClass().isInterface()) {
                    calleeCompletions = resolveInterfaceCall(targetMethod, nextSteps, callStack, callDepth);
                } else if (!callStack.contains(targetMethod)) {
                    callStack.push(targetMethod);
                    calleeCompletions = exploreMethod(targetMethod, nextSteps, callStack, new HashSet<>(), callDepth + 1);
                    callStack.pop();
                } else {
                    calleeCompletions = Collections.singletonList(nextSteps);
                }

                List<List<PathStep>> boundedCallee = calleeCompletions.size() > MAX_CALLEE_COMPLETIONS
                        ? calleeCompletions.subList(0, MAX_CALLEE_COMPLETIONS)
                        : calleeCompletions;

                // If callee completed normally, continue with CFG successors in caller for each completion
                List<List<PathStep>> afterCallCompletions = new ArrayList<>();
                for (Unit succ : cfg.getSuccsOf(unit)) {
                    if (afterCallCompletions.size() >= MAX_BRANCHES_PER_LEVEL || result.getPaths().size() >= MAX_PATHS) break;
                    for (List<PathStep> active : boundedCallee) {
                        if (afterCallCompletions.size() >= MAX_BRANCHES_PER_LEVEL || result.getPaths().size() >= MAX_PATHS) break;
                        afterCallCompletions.addAll(exploreCfg(succ, cfg, currentMethod, active, callStack, visitedInMethod, callDepth));
                    }
                }
                if (afterCallCompletions.size() > MAX_BRANCHES_PER_LEVEL) {
                    return afterCallCompletions.subList(0, MAX_BRANCHES_PER_LEVEL);
                }
                return afterCallCompletions;
            }
        }

        // Resume CFG successors in caller for non-app calls
        List<List<PathStep>> defaultCompletions = new ArrayList<>();
        for (Unit succ : cfg.getSuccsOf(unit)) {
            if (defaultCompletions.size() >= MAX_BRANCHES_PER_LEVEL || result.getPaths().size() >= MAX_PATHS) break;
            defaultCompletions.addAll(exploreCfg(succ, cfg, currentMethod, nextSteps, callStack, visitedInMethod, callDepth));
        }
        if (defaultCompletions.size() > MAX_BRANCHES_PER_LEVEL) {
            return defaultCompletions.subList(0, MAX_BRANCHES_PER_LEVEL);
        }
        return defaultCompletions;
    }

    private List<List<PathStep>> handleBodylessMethod(SootMethod method,
                                                      List<PathStep> currentSteps,
                                                      Deque<SootMethod> callStack,
                                                      int callDepth) {
        int line = getMethodLineNumber(method);
        Optional<SinkInfo> sinkOpt = SinkDetector.detectSink(method, line);
        if (sinkOpt.isPresent()) {
            SinkInfo sink = sinkOpt.get();
            result.getDetectedSinks().add(sink);
            PathStep sinkStep = new PathStep(
                    currentSteps.size() + 1,
                    method.getDeclaringClass().getName(),
                    method.getName(),
                    method.getSignature(),
                    line,
                    PathStepType.SINK_PORT,
                    "Call to interface/abstract Sink: " + method.getDeclaration(),
                    "Sink interface: " + sink.getDescription()
            );
            sinkStep.setSink(sink);
            List<PathStep> updated = new ArrayList<>(currentSteps);
            updated.add(sinkStep);

            // Interface sink reached
            return Collections.singletonList(updated);
        }

        if (method.getDeclaringClass().isInterface()) {
            return resolveInterfaceCall(method, currentSteps, callStack, callDepth);
        }

        return Collections.singletonList(currentSteps);
    }

    private List<List<PathStep>> resolveInterfaceCall(SootMethod interfaceMethod,
                                                      List<PathStep> currentSteps,
                                                      Deque<SootMethod> callStack,
                                                      int callDepth) {
        SootClass ifaceClass = interfaceMethod.getDeclaringClass();
        List<List<PathStep>> completions = new ArrayList<>();
        try {
            Set<SootClass> implementers = Scene.v().getOrMakeFastHierarchy().getAllImplementersOfInterface(ifaceClass);
            boolean found = false;
            int implCount = 0;
            for (SootClass implClass : implementers) {
                if (isApplicationClass(implClass) && !implClass.isInterface() && !implClass.isAbstract()) {
                    if (++implCount > 2) break;
                    try {
                        SootMethod concreteMethod = implClass.getMethod(interfaceMethod.getSubSignature());
                        if (concreteMethod != null && !callStack.contains(concreteMethod)) {
                            found = true;
                            callStack.push(concreteMethod);
                            completions.addAll(exploreMethod(concreteMethod, currentSteps, callStack, new HashSet<>(), callDepth + 1));
                            callStack.pop();
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (!found) {
                completions.add(currentSteps);
            }
        } catch (Exception e) {
            log.debug("Interface resolution fallback for {}: {}", ifaceClass.getName(), e.getMessage());
            completions.add(currentSteps);
        }
        if (completions.size() > MAX_BRANCHES_PER_LEVEL) {
            return completions.subList(0, MAX_BRANCHES_PER_LEVEL);
        }
        return completions;
    }

    private List<SootMethod> extractLambdaTargets(DynamicInvokeExpr die) {
        List<SootMethod> targets = new ArrayList<>();
        for (Value arg : die.getBootstrapArgs()) {
            if (arg instanceof MethodHandle) {
                MethodHandle mh = (MethodHandle) arg;
                try {
                    SootMethod m = mh.getMethodRef().resolve();
                    if (m != null) {
                        targets.add(m);
                    }
                } catch (Exception ignored) {}
            }
        }
        return targets;
    }

    private boolean isApplicationClass(SootClass sc) {
        if (sc == null) return false;
        String name = sc.getName();
        return name.startsWith(basePackage) &&
                !name.startsWith("java.") &&
                !name.startsWith("javax.") &&
                !name.startsWith("jakarta.") &&
                !name.startsWith("org.springframework.") &&
                !name.startsWith("org.slf4j.");
    }

    private void recordTerminalPath(List<PathStep> steps,
                                    PathType type,
                                    String reason,
                                    SinkInfo sink,
                                    RejectionInfo rejection) {
        if (steps.isEmpty()) return;
        pathSequence++;
        String pathId = String.format("PATH-%02d", pathSequence);
        ExecutionPath ep = new ExecutionPath(pathId, type, reason, reason);
        ep.setTerminalSink(sink);
        ep.setTerminalRejection(rejection);
        ep.setSteps(new ArrayList<>(steps));
        result.getPaths().add(ep);
    }

    private SinkInfo findLastSink(List<PathStep> steps) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).getSink() != null) {
                return steps.get(i).getSink();
            }
        }
        return null;
    }

    private int getUnitLineNumber(Unit unit) {
        if (unit == null) return -1;
        LineNumberTag tag = (LineNumberTag) unit.getTag("LineNumberTag");
        if (tag != null) return tag.getLineNumber();
        SourceLnPosTag pos = (SourceLnPosTag) unit.getTag("SourceLnPosTag");
        if (pos != null) return pos.startLn();
        return -1;
    }

    private int getMethodLineNumber(SootMethod method) {
        if (method == null) return -1;
        if (method.hasActiveBody()) {
            for (Unit u : method.getActiveBody().getUnits()) {
                int line = getUnitLineNumber(u);
                if (line > 0) return line;
            }
        }
        return -1;
    }
}
