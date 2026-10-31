# Règles du Projet Soot-Analyser

Ce document formalise les règles d'architecture, de configuration de l'environnement, de build et d'utilisation de Soot pour le projet `soot-analyser`.

---

## 1. Environnement d'Exécution & Runtime Java

- **Version cible** : Java 21 LTS (`temurin-21.jdk`).
- **Configuration locale requise** :
  ```bash
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
  export PATH=$JAVA_HOME/bin:$PATH
  ```
- **Précision Java 25** : Le projet utilise les dépendances ASM `9.10.1` pour anticiper le parsing de bytecode Java 25 (`major version 69`), mais le projet compile et s'exécute actuellement sous **Java 21** (`major version 65`).

---

## 2. Dépendances & Tests Unitaires (Maven)

- **Framework de test obligatoire** : JUnit 5 (`org.junit.jupiter:junit-jupiter`).
  - *Pourquoi ?* Ne jamais supprimer la dépendance JUnit 5 du `pom.xml`. Sans elle, Surefire bascule automatiquement sur `surefire-junit3` et tente de télécharger des plugins externes depuis le miroir Artifact Registry, échouant en environnement hors-ligne ou sous sandbox.
- **Conventions de nommage des tests** :
  - Tout fichier nommé `*Test.java` sous `src/test/java` DOIT contenir des méthodes de test annotées `@Test` (JUnit Jupiter) avec assertions.
  - Ne jamais créer de classe avec uniquement une méthode `public static void main` dont le nom se termine par `Test`. Si c'est un runner manuel, le suffixer par `Runner` (ex. `SootInitRunner`).
- **Exécution hors-ligne / Réseau d'entreprise** :
  - Si le miroir Maven Artifact Registry n'est pas joignable, exécuter Maven en mode offline : `mvn test -o` ou `mvn package -o`.

---

## 3. Configuration Soot & Traversal de Chemins (`UseCasePathAnalyzer` / `PathFinder`)

Pour maintenir des performances d'analyse inférieures à 2 secondes et éviter les blocages de mémoire ou de threads :

1. **INTERDICTION de `Options.v().set_app(true)` sans filtrage strict** :
   - `set_app(true)` force Soot à transformer l'intégralité du classpath (y compris les milliers de classes des dépendances tierces et du runtime JDK) en classes applicatives.
   - Utiliser exclusivement `Options.v().set_process_dir(...)` pour cibler les classes du projet à analyser (`target/classes` ou `target/test-classes`).
2. **Isolation du `soot_classpath`** :
   - Définir `Options.v().set_soot_classpath(appClasspath)` uniquement sur le classpath de l'application analysée.
   - Ne JAMAIS concaténer aveuglément `System.getProperty("java.class.path")` au classpath Soot lorsqu'on tourne dans un fat-JAR ou sous Maven Surefire, sous peine de charger 18 000+ classes dans la Scène Soot.
3. **Pas d'appel global superflu à `CHATransformer`** :
   - Le moteur `PathFinder` effectue sa propre traversée interprocédurale du CFG (`UnitGraph`) et de la hiérarchie (`Scene.v().getOrMakeFastHierarchy()`).
   - Il ne consomme pas le `CallGraph` global de Soot. L'appel à `CHATransformer.v().transform()` ne doit pas être exécuté inutilement car il tente de construire le graphe d'appel de tout le JDK 21.

---

## 4. Commandes de Validation & Déploiement

```bash
# Compilation et passage des tests unitaires
mvn test

# Packaging du fat JAR (uber-jar exécutable avec dépendances ombrées)
mvn clean package

# 1. Lancement de l'analyseur en CLI avec DÉTECTION AUTOMATIQUE (recommandé)
# Scanne automatiquement tous les points d'entrée (REST, Kafka, Scheduled, Batch, Runners, Temporal)
java -jar target/usecase-path-analyzer.jar \
  -cp target/test-classes \
  -p com.bforbank \
  -o target/output-analysis.json

# 2. Filtrage optionnel par type (ex: uniquement REST et Kafka)
java -jar target/usecase-path-analyzer.jar \
  -cp target/test-classes \
  -p com.bforbank \
  -t rest,kafka \
  -o target/output-rest-kafka.json

# 3. Ciblage explicite d'un entrypoint spécifique via signature Soot
java -jar target/usecase-path-analyzer.jar \
  -cp target/test-classes \
  -p com.bforbank \
  -e "<com.bforbank.sample.controller.TransferController: void executeTransfer(com.bforbank.sample.controller.TransferController\$TransferDto)>" \
  -o target/output-single.json
```
