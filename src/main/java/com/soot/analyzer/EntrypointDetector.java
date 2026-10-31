package com.soot.analyzer;

import com.soot.analyzer.model.EntrypointInfo;
import com.soot.analyzer.model.EntrypointType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Body;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.tagkit.*;

import java.util.*;

/**
 * Detects application entrypoints (source nodes) across various technologies:
 * - REST / Web: @RestController, @Controller, @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping, @RequestMapping, JAX-RS
 * - Kafka & Messaging: @KafkaListener, @RabbitListener, @JmsListener, @StreamListener
 * - Scheduling & Batch: @Scheduled, Quartz Job, Spring Batch Tasklet/ItemReader/ItemProcessor/ItemWriter
 * - Spring Lifecycle & Events: @EventListener, @TransactionalEventListener, CommandLineRunner, ApplicationRunner, @PostConstruct
 * - Temporal: @WorkflowMethod, @SignalMethod, @ActivityMethod, @WorkflowInterface, @ActivityInterface
 */
public class EntrypointDetector {
    private static final Logger log = LoggerFactory.getLogger(EntrypointDetector.class);

    public static List<EntrypointInfo> detectEntrypoints(Collection<SootClass> classes, String basePackage) {
        List<EntrypointInfo> detected = new ArrayList<>();
        List<SootClass> snapshot = new ArrayList<>(classes);

        for (SootClass sc : snapshot) {
            if (sc == null || sc.isPhantom()) continue;
            if (basePackage != null && !basePackage.isBlank() && !sc.getName().startsWith(basePackage)) {
                continue;
            }

            // Exclude common framework/library classes just in case
            String className = sc.getName();
            if (className.startsWith("java.") || className.startsWith("javax.") ||
                className.startsWith("jakarta.") || className.startsWith("org.springframework.") ||
                className.startsWith("org.apache.") || className.startsWith("org.slf4j.")) {
                continue;
            }

            // Detect class-level entrypoints or scan methods
            detectClassEntrypoints(sc, detected);
        }

        // Sort detected entrypoints by class name and source line for deterministic ordering
        detected.sort(Comparator.comparing(EntrypointInfo::getClassName)
                .thenComparingInt(EntrypointInfo::getSourceLine)
                .thenComparing(EntrypointInfo::getMethodName));

        log.info("Detected {} application entrypoints in package '{}'", detected.size(), basePackage);
        return detected;
    }

    private static void detectClassEntrypoints(SootClass sc, List<EntrypointInfo> detected) {
        boolean isRestController = hasAnnotation(sc, "RestController");
        boolean isController = isRestController || hasAnnotation(sc, "Controller") || sc.getName().endsWith("Controller");
        String classBasePath = extractClassHttpPath(sc);

        boolean isWorkflowClass = hasAnnotation(sc, "WorkflowInterface") || sc.getName().endsWith("Workflow") || sc.getName().endsWith("WorkflowImpl");
        boolean isActivityClass = hasAnnotation(sc, "ActivityInterface") || sc.getName().endsWith("Activity") || sc.getName().endsWith("ActivityImpl");
        boolean isCommandLineRunner = implementsInterface(sc, "org.springframework.boot.CommandLineRunner");
        boolean isApplicationRunner = implementsInterface(sc, "org.springframework.boot.ApplicationRunner");
        boolean isQuartzJob = implementsInterface(sc, "org.quartz.Job");
        boolean isBatchTasklet = implementsInterface(sc, "org.springframework.batch.core.step.tasklet.Tasklet") || sc.getName().endsWith("Tasklet");
        boolean isBatchReader = implementsInterface(sc, "org.springframework.batch.item.ItemReader");
        boolean isBatchProcessor = implementsInterface(sc, "org.springframework.batch.item.ItemProcessor");
        boolean isBatchWriter = implementsInterface(sc, "org.springframework.batch.item.ItemWriter");

        for (SootMethod sm : sc.getMethods()) {
            if (sm.isAbstract() || sm.isStatic() || sm.isConstructor() || sm.getName().equals("<clinit>")) {
                continue;
            }
            if (!sm.isPublic()) {
                // In Spring, @Scheduled, @EventListener, and @KafkaListener may sometimes be package-private
                if (!hasAnnotation(sm, "Scheduled", "EventListener", "TransactionalEventListener", "KafkaListener")) {
                    continue;
                }
            }

            int line = getMethodLineNumber(sm);
            String methodName = sm.getName();

            // 1. REST Endpoint detection
            Optional<EntrypointInfo> restEp = checkRestEndpoint(sm, isController, classBasePath, line);
            if (restEp.isPresent()) {
                detected.add(restEp.get());
                continue;
            }

            // 2. Kafka & Messaging Listener detection
            Optional<EntrypointInfo> msgEp = checkMessagingListener(sm, line);
            if (msgEp.isPresent()) {
                detected.add(msgEp.get());
                continue;
            }

            // 3. Scheduled Task detection
            Optional<EntrypointInfo> schedEp = checkScheduledTask(sm, line);
            if (schedEp.isPresent()) {
                detected.add(schedEp.get());
                continue;
            }

            // 4. Batch Job & Quartz detection
            if (isQuartzJob && methodName.equals("execute")) {
                EntrypointInfo ep = new EntrypointInfo(
                        EntrypointType.BATCH_JOB, sc.getName(), methodName, sm.getSignature(), line,
                        "Quartz Job: " + sc.getShortName() + ".execute()"
                );
                ep.addMetadata("engine", "Quartz");
                detected.add(ep);
                continue;
            }
            if (isBatchTasklet && methodName.equals("execute")) {
                EntrypointInfo ep = new EntrypointInfo(
                        EntrypointType.BATCH_JOB, sc.getName(), methodName, sm.getSignature(), line,
                        "Spring Batch Tasklet: " + sc.getShortName() + ".execute()"
                );
                ep.addMetadata("engine", "SpringBatch");
                ep.addMetadata("role", "Tasklet");
                detected.add(ep);
                continue;
            }
            if (isBatchReader && methodName.equals("read")) {
                EntrypointInfo ep = new EntrypointInfo(
                        EntrypointType.BATCH_JOB, sc.getName(), methodName, sm.getSignature(), line,
                        "Spring Batch ItemReader: " + sc.getShortName() + ".read()"
                );
                ep.addMetadata("engine", "SpringBatch");
                ep.addMetadata("role", "ItemReader");
                detected.add(ep);
                continue;
            }
            if (isBatchProcessor && methodName.equals("process")) {
                EntrypointInfo ep = new EntrypointInfo(
                        EntrypointType.BATCH_JOB, sc.getName(), methodName, sm.getSignature(), line,
                        "Spring Batch ItemProcessor: " + sc.getShortName() + ".process()"
                );
                ep.addMetadata("engine", "SpringBatch");
                ep.addMetadata("role", "ItemProcessor");
                detected.add(ep);
                continue;
            }
            if (isBatchWriter && methodName.equals("write")) {
                EntrypointInfo ep = new EntrypointInfo(
                        EntrypointType.BATCH_JOB, sc.getName(), methodName, sm.getSignature(), line,
                        "Spring Batch ItemWriter: " + sc.getShortName() + ".write()"
                );
                ep.addMetadata("engine", "SpringBatch");
                ep.addMetadata("role", "ItemWriter");
                detected.add(ep);
                continue;
            }

            // 5. Spring Lifecycle & Event Runners
            if (hasAnnotation(sm, "EventListener", "TransactionalEventListener")) {
                AnnotationTag tag = getAnnotation(sm, "EventListener", "TransactionalEventListener");
                String desc = "Spring Event: " + sc.getShortName() + "." + methodName + "()";
                EntrypointInfo ep = new EntrypointInfo(EntrypointType.SPRING_EVENT, sc.getName(), methodName, sm.getSignature(), line, desc);
                ep.addMetadata("type", tag != null && tag.getType().contains("TransactionalEventListener") ? "Transactional" : "Standard");
                detected.add(ep);
                continue;
            }
            if (isCommandLineRunner && methodName.equals("run")) {
                EntrypointInfo ep = new EntrypointInfo(
                        EntrypointType.APPLICATION_RUNNER, sc.getName(), methodName, sm.getSignature(), line,
                        "CommandLineRunner: " + sc.getShortName() + ".run()"
                );
                ep.addMetadata("runner", "CommandLineRunner");
                detected.add(ep);
                continue;
            }
            if (isApplicationRunner && methodName.equals("run")) {
                EntrypointInfo ep = new EntrypointInfo(
                        EntrypointType.APPLICATION_RUNNER, sc.getName(), methodName, sm.getSignature(), line,
                        "ApplicationRunner: " + sc.getShortName() + ".run()"
                );
                ep.addMetadata("runner", "ApplicationRunner");
                detected.add(ep);
                continue;
            }
            if (hasAnnotation(sm, "PostConstruct")) {
                EntrypointInfo ep = new EntrypointInfo(
                        EntrypointType.APPLICATION_RUNNER, sc.getName(), methodName, sm.getSignature(), line,
                        "@PostConstruct: " + sc.getShortName() + "." + methodName + "()"
                );
                ep.addMetadata("lifecycle", "PostConstruct");
                detected.add(ep);
                continue;
            }

            // 6. Temporal Workflows & Activities
            if (hasAnnotation(sm, "WorkflowMethod") || (isWorkflowClass && !methodName.startsWith("get") && !methodName.startsWith("set"))) {
                AnnotationTag tag = getAnnotation(sm, "WorkflowMethod");
                String workflowName = tag != null ? getElemValue(tag, "name") : "";
                String desc = "Temporal Workflow: " + sc.getShortName() + "." + methodName + "()";
                EntrypointInfo ep = new EntrypointInfo(EntrypointType.TEMPORAL_WORKFLOW, sc.getName(), methodName, sm.getSignature(), line, desc);
                if (workflowName != null && !workflowName.isBlank()) {
                    ep.addMetadata("workflowName", workflowName);
                }
                detected.add(ep);
                continue;
            }
            if (hasAnnotation(sm, "SignalMethod")) {
                AnnotationTag tag = getAnnotation(sm, "SignalMethod");
                String signalName = tag != null ? getElemValue(tag, "name") : "";
                String desc = "Temporal Signal: " + sc.getShortName() + "." + methodName + "()";
                EntrypointInfo ep = new EntrypointInfo(EntrypointType.TEMPORAL_WORKFLOW, sc.getName(), methodName, sm.getSignature(), line, desc);
                ep.addMetadata("signalName", signalName != null && !signalName.isBlank() ? signalName : methodName);
                detected.add(ep);
                continue;
            }
            if (hasAnnotation(sm, "ActivityMethod") || (isActivityClass && !methodName.startsWith("get") && !methodName.startsWith("set"))) {
                AnnotationTag tag = getAnnotation(sm, "ActivityMethod");
                String activityName = tag != null ? getElemValue(tag, "name") : "";
                String desc = "Temporal Activity: " + sc.getShortName() + "." + methodName + "()";
                EntrypointInfo ep = new EntrypointInfo(EntrypointType.TEMPORAL_ACTIVITY, sc.getName(), methodName, sm.getSignature(), line, desc);
                if (activityName != null && !activityName.isBlank()) {
                    ep.addMetadata("activityName", activityName);
                }
                detected.add(ep);
                continue;
            }
        }
    }

    private static Optional<EntrypointInfo> checkRestEndpoint(SootMethod sm, boolean isControllerClass, String classBasePath, int line) {
        String httpMethod = null;
        String methodPath = "";

        if (hasAnnotation(sm, "GetMapping", "GET")) {
            httpMethod = "GET";
            AnnotationTag tag = getAnnotation(sm, "GetMapping", "GET");
            methodPath = extractPathFromTag(tag);
        } else if (hasAnnotation(sm, "PostMapping", "POST")) {
            httpMethod = "POST";
            AnnotationTag tag = getAnnotation(sm, "PostMapping", "POST");
            methodPath = extractPathFromTag(tag);
        } else if (hasAnnotation(sm, "PutMapping", "PUT")) {
            httpMethod = "PUT";
            AnnotationTag tag = getAnnotation(sm, "PutMapping", "PUT");
            methodPath = extractPathFromTag(tag);
        } else if (hasAnnotation(sm, "DeleteMapping", "DELETE")) {
            httpMethod = "DELETE";
            AnnotationTag tag = getAnnotation(sm, "DeleteMapping", "DELETE");
            methodPath = extractPathFromTag(tag);
        } else if (hasAnnotation(sm, "PatchMapping", "PATCH")) {
            httpMethod = "PATCH";
            AnnotationTag tag = getAnnotation(sm, "PatchMapping", "PATCH");
            methodPath = extractPathFromTag(tag);
        } else if (hasAnnotation(sm, "RequestMapping")) {
            AnnotationTag tag = getAnnotation(sm, "RequestMapping");
            httpMethod = extractHttpMethodFromRequestMapping(tag);
            methodPath = extractPathFromTag(tag);
        } else if (hasAnnotation(sm, "Path")) {
            AnnotationTag tag = getAnnotation(sm, "Path");
            httpMethod = "GET";
            methodPath = extractPathFromTag(tag);
        } else if (isControllerClass && sm.isPublic()) {
            // Naming convention or controller method without explicit mapping
            httpMethod = deduceHttpMethodFromName(sm.getName());
            methodPath = "";
        }

        if (httpMethod != null) {
            String fullPath = combinePaths(classBasePath, methodPath);
            String desc = "REST " + httpMethod + " " + fullPath;
            EntrypointInfo ep = new EntrypointInfo(
                    EntrypointType.REST_ENDPOINT,
                    sm.getDeclaringClass().getName(),
                    sm.getName(),
                    sm.getSignature(),
                    line,
                    desc
            );
            ep.addMetadata("httpMethod", httpMethod);
            ep.addMetadata("path", fullPath);
            return Optional.of(ep);
        }

        return Optional.empty();
    }

    private static Optional<EntrypointInfo> checkMessagingListener(SootMethod sm, int line) {
        if (hasAnnotation(sm, "KafkaListener")) {
            AnnotationTag tag = getAnnotation(sm, "KafkaListener");
            String topics = tag != null ? getElemValue(tag, "topics", "value") : "";
            String groupId = tag != null ? getElemValue(tag, "groupId") : "";

            String desc = "Kafka Listener: " + sm.getDeclaringClass().getShortName() + "." + sm.getName() + "()";
            if (topics != null && !topics.isBlank()) {
                desc += " [topic=" + topics + "]";
            }

            EntrypointInfo ep = new EntrypointInfo(EntrypointType.KAFKA_LISTENER, sm.getDeclaringClass().getName(), sm.getName(), sm.getSignature(), line, desc);
            if (topics != null && !topics.isBlank()) ep.addMetadata("topics", topics);
            if (groupId != null && !groupId.isBlank()) ep.addMetadata("groupId", groupId);
            return Optional.of(ep);
        }

        if (hasAnnotation(sm, "RabbitListener")) {
            AnnotationTag tag = getAnnotation(sm, "RabbitListener");
            String queues = tag != null ? getElemValue(tag, "queues", "value") : "";
            String desc = "RabbitMQ Listener: " + sm.getDeclaringClass().getShortName() + "." + sm.getName() + "() [queues=" + queues + "]";
            EntrypointInfo ep = new EntrypointInfo(EntrypointType.MESSAGING_LISTENER, sm.getDeclaringClass().getName(), sm.getName(), sm.getSignature(), line, desc);
            ep.addMetadata("engine", "RabbitMQ");
            if (queues != null && !queues.isBlank()) ep.addMetadata("queues", queues);
            return Optional.of(ep);
        }

        if (hasAnnotation(sm, "JmsListener")) {
            AnnotationTag tag = getAnnotation(sm, "JmsListener");
            String dest = tag != null ? getElemValue(tag, "destination") : "";
            String desc = "JMS Listener: " + sm.getDeclaringClass().getShortName() + "." + sm.getName() + "() [destination=" + dest + "]";
            EntrypointInfo ep = new EntrypointInfo(EntrypointType.MESSAGING_LISTENER, sm.getDeclaringClass().getName(), sm.getName(), sm.getSignature(), line, desc);
            ep.addMetadata("engine", "JMS");
            if (dest != null && !dest.isBlank()) ep.addMetadata("destination", dest);
            return Optional.of(ep);
        }

        return Optional.empty();
    }

    private static Optional<EntrypointInfo> checkScheduledTask(SootMethod sm, int line) {
        if (hasAnnotation(sm, "Scheduled")) {
            AnnotationTag tag = getAnnotation(sm, "Scheduled");
            String cron = tag != null ? getElemValue(tag, "cron") : null;
            String fixedDelay = tag != null ? getElemValue(tag, "fixedDelay", "fixedDelayString") : null;
            String fixedRate = tag != null ? getElemValue(tag, "fixedRate", "fixedRateString") : null;

            StringBuilder details = new StringBuilder();
            if (cron != null && !cron.isBlank()) details.append("cron='").append(cron).append("'");
            else if (fixedDelay != null && !fixedDelay.isBlank()) details.append("fixedDelay=").append(fixedDelay);
            else if (fixedRate != null && !fixedRate.isBlank()) details.append("fixedRate=").append(fixedRate);

            String desc = "Scheduled Task: " + sm.getDeclaringClass().getShortName() + "." + sm.getName() + "()"
                    + (details.length() > 0 ? " [" + details + "]" : "");

            EntrypointInfo ep = new EntrypointInfo(EntrypointType.SCHEDULED_TASK, sm.getDeclaringClass().getName(), sm.getName(), sm.getSignature(), line, desc);
            if (cron != null && !cron.isBlank()) ep.addMetadata("cron", cron);
            if (fixedDelay != null && !fixedDelay.isBlank()) ep.addMetadata("fixedDelay", fixedDelay);
            if (fixedRate != null && !fixedRate.isBlank()) ep.addMetadata("fixedRate", fixedRate);
            return Optional.of(ep);
        }
        return Optional.empty();
    }

    // --- Helper Methods ---

    private static String extractClassHttpPath(SootClass sc) {
        AnnotationTag reqMap = getAnnotation(sc, "RequestMapping", "Path");
        if (reqMap != null) {
            return extractPathFromTag(reqMap);
        }
        return "";
    }

    private static String extractPathFromTag(AnnotationTag tag) {
        if (tag == null) return "";
        String val = getElemValue(tag, "value", "path");
        return val != null ? val : "";
    }

    private static String extractHttpMethodFromRequestMapping(AnnotationTag tag) {
        if (tag != null && tag.getElems() != null) {
            for (AnnotationElem elem : tag.getElems()) {
                if ("method".equals(elem.getName())) {
                    if (elem instanceof AnnotationEnumElem aee) {
                        return aee.getConstantName();
                    } else if (elem instanceof AnnotationArrayElem aae) {
                        for (AnnotationElem sub : aae.getValues()) {
                            if (sub instanceof AnnotationEnumElem subEnum) {
                                return subEnum.getConstantName();
                            }
                        }
                    }
                }
            }
        }
        return "GET"; // Default Spring RequestMapping method
    }

    private static String deduceHttpMethodFromName(String methodName) {
        String lower = methodName.toLowerCase();
        if (lower.startsWith("get") || lower.startsWith("find") || lower.startsWith("query") || lower.startsWith("list")) return "GET";
        if (lower.startsWith("post") || lower.startsWith("create") || lower.startsWith("execute") || lower.startsWith("add")) return "POST";
        if (lower.startsWith("put") || lower.startsWith("update") || lower.startsWith("modify")) return "PUT";
        if (lower.startsWith("delete") || lower.startsWith("remove")) return "DELETE";
        if (lower.startsWith("patch")) return "PATCH";
        return "POST";
    }

    private static String combinePaths(String basePath, String subPath) {
        if (basePath == null) basePath = "";
        if (subPath == null) subPath = "";

        basePath = basePath.trim();
        subPath = subPath.trim();

        if (basePath.isEmpty() && subPath.isEmpty()) return "/";
        if (basePath.isEmpty()) return subPath.startsWith("/") ? subPath : "/" + subPath;
        if (subPath.isEmpty()) return basePath.startsWith("/") ? basePath : "/" + basePath;

        if (!basePath.startsWith("/")) basePath = "/" + basePath;
        if (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
        if (!subPath.startsWith("/")) subPath = "/" + subPath;

        return basePath + subPath;
    }

    private static boolean implementsInterface(SootClass sc, String interfaceName) {
        try {
            for (SootClass iface : sc.getInterfaces()) {
                if (iface.getName().equals(interfaceName) || iface.getShortName().equals(interfaceName)) {
                    return true;
                }
                if (implementsInterface(iface, interfaceName)) {
                    return true;
                }
            }
            if (sc.hasSuperclass() && !sc.getSuperclass().getName().equals("java.lang.Object")) {
                return implementsInterface(sc.getSuperclass(), interfaceName);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean hasAnnotation(AbstractHost host, String... annotationKeywords) {
        return getAnnotation(host, annotationKeywords) != null;
    }

    private static AnnotationTag getAnnotation(AbstractHost host, String... annotationKeywords) {
        if (host == null) return null;
        for (Tag tag : host.getTags()) {
            if (tag instanceof VisibilityAnnotationTag vat) {
                for (AnnotationTag at : vat.getAnnotations()) {
                    String atType = at.getType();
                    for (String kw : annotationKeywords) {
                        if (atType.contains(kw)) {
                            return at;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String getElemValue(AnnotationTag tag, String... elemNames) {
        if (tag == null || tag.getElems() == null) return null;
        for (AnnotationElem elem : tag.getElems()) {
            for (String targetName : elemNames) {
                if (elem.getName().equals(targetName)) {
                    if (elem instanceof AnnotationStringElem ase) {
                        return ase.getValue();
                    } else if (elem instanceof AnnotationArrayElem aae) {
                        StringBuilder sb = new StringBuilder();
                        for (AnnotationElem sub : aae.getValues()) {
                            if (sub instanceof AnnotationStringElem subStr) {
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(subStr.getValue());
                            } else {
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(sub.toString());
                            }
                        }
                        return sb.toString();
                    } else if (elem instanceof AnnotationIntElem aie) {
                        return String.valueOf(aie.getValue());
                    } else if (elem instanceof AnnotationBooleanElem abe) {
                        return String.valueOf(abe.getValue());
                    } else if (elem instanceof AnnotationEnumElem aee) {
                        return aee.getConstantName();
                    } else {
                        return elem.toString();
                    }
                }
            }
        }
        return null;
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
            Body body = method.getActiveBody();
            for (Unit u : body.getUnits()) {
                LineNumberTag tag = (LineNumberTag) u.getTag("LineNumberTag");
                if (tag != null) return tag.getLineNumber();
                SourceLnPosTag pos = (SourceLnPosTag) u.getTag("SourceLnPosTag");
                if (pos != null) return pos.startLn();
            }
        } catch (Exception ignored) {}
        return -1;
    }
}
