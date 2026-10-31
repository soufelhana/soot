package com.soot.analyzer;

import com.soot.analyzer.model.SinkInfo;
import com.soot.analyzer.model.SinkType;
import soot.SootClass;
import soot.SootMethod;

import java.util.Optional;
import java.util.regex.Pattern;

public class SinkDetector {

    private static final Pattern HEXA_OUT_PORT_PACKAGE = Pattern.compile(".*\\.(ports?|port)\\.out(\\..*)?");
    private static final Pattern HEXA_OUT_ADAPTER_PACKAGE = Pattern.compile(".*\\.(adapters?|adapter)\\.out(\\..*)?");

    public static Optional<SinkInfo> detectSink(SootMethod method, int sourceLine) {
        if (method == null) return Optional.empty();
        SootClass declaringClass = method.getDeclaringClass();
        String className = declaringClass.getName();
        String methodName = method.getName();
        String signature = method.getSignature();

        // 1. Kafka Sink
        if (isKafka(declaringClass, methodName)) {
            String desc = "Kafka Sink: " + className + "." + methodName + "()";
            return Optional.of(new SinkInfo(SinkType.KAFKA, className, methodName, signature, sourceLine, desc));
        }

        // 2. Database Sink (Spring Data, JPA, JDBC)
        if (isDatabase(declaringClass, methodName)) {
            String desc = "Database Sink: " + className + "." + methodName + "()";
            return Optional.of(new SinkInfo(SinkType.DATABASE, className, methodName, signature, sourceLine, desc));
        }

        // 3. Hexagonal Output Port Sink
        if (isHexagonalOutPort(declaringClass, methodName)) {
            String desc = "Hexagonal Out Port Sink: " + className + "." + methodName + "()";
            return Optional.of(new SinkInfo(SinkType.HEXAGONAL_OUT_PORT, className, methodName, signature, sourceLine, desc));
        }

        return Optional.empty();
    }

    private static boolean isKafka(SootClass sc, String methodName) {
        String className = sc.getName();
        if (className.equals("org.springframework.kafka.core.KafkaTemplate") ||
            className.equals("org.springframework.kafka.core.KafkaOperations") ||
            className.equals("org.apache.kafka.clients.producer.Producer") ||
            className.equals("org.apache.kafka.clients.producer.KafkaProducer") ||
            className.equals("org.springframework.cloud.stream.function.StreamBridge")) {
            return methodName.startsWith("send") || methodName.equals("execute");
        }
        if (className.endsWith("KafkaTemplate") || className.endsWith("KafkaProducer")) {
            return methodName.startsWith("send") || methodName.equals("publish");
        }
        return false;
    }

    private static boolean isDatabase(SootClass sc, String methodName) {
        String className = sc.getName();

        // Direct JPA / JDBC interfaces
        if (className.equals("jakarta.persistence.EntityManager") ||
            className.equals("javax.persistence.EntityManager") ||
            className.equals("org.springframework.jdbc.core.JdbcTemplate") ||
            className.equals("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate") ||
            className.equals("java.sql.Connection") ||
            className.equals("java.sql.PreparedStatement") ||
            className.equals("java.sql.Statement")) {
            return true;
        }

        // Spring Data naming or interface inheritance
        if (isSpringDataRepository(sc)) {
            return isPersistenceMethodName(methodName);
        }

        if (className.endsWith("Repository") || className.endsWith("Dao") ||
            className.endsWith("RepositoryImpl") || className.endsWith("DaoImpl")) {
            return isPersistenceMethodName(methodName);
        }

        return false;
    }

    private static boolean isSpringDataRepository(SootClass sc) {
        try {
            for (SootClass iface : sc.getInterfaces()) {
                String name = iface.getName();
                if (name.equals("org.springframework.data.repository.Repository") ||
                    name.equals("org.springframework.data.repository.CrudRepository") ||
                    name.equals("org.springframework.data.jpa.repository.JpaRepository") ||
                    name.equals("org.springframework.data.mongodb.repository.MongoRepository") ||
                    name.equals("org.springframework.data.repository.reactive.ReactiveCrudRepository")) {
                    return true;
                }
                if (isSpringDataRepository(iface)) {
                    return true;
                }
            }
            if (sc.hasSuperclass() && !sc.getSuperclass().getName().equals("java.lang.Object")) {
                return isSpringDataRepository(sc.getSuperclass());
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean isPersistenceMethodName(String methodName) {
        return methodName.startsWith("save") ||
               methodName.startsWith("delete") ||
               methodName.startsWith("insert") ||
               methodName.startsWith("update") ||
               methodName.startsWith("find") ||
               methodName.startsWith("get") ||
               methodName.startsWith("count") ||
               methodName.startsWith("exists") ||
               methodName.equals("persist") ||
               methodName.equals("merge") ||
               methodName.equals("remove") ||
               methodName.equals("flush") ||
               methodName.equals("execute") ||
               methodName.equals("batchUpdate");
    }

    private static boolean isHexagonalOutPort(SootClass sc, String methodName) {
        String packageName = sc.getPackageName();
        String className = sc.getShortName();

        if (HEXA_OUT_PORT_PACKAGE.matcher(packageName).matches() ||
            HEXA_OUT_ADAPTER_PACKAGE.matcher(packageName).matches()) {
            return true;
        }

        if (className.endsWith("OutputPort") || className.endsWith("OutPort") ||
            className.endsWith("ProducerPort") || className.endsWith("RepositoryPort")) {
            return true;
        }

        return false;
    }
}
