#!/usr/bin/env bash
set -euo pipefail

SKILL_RESOURCES_JAR="$HOME/.gemini/config/skills/soot-analyser/resources/usecase-path-analyzer.jar"
SOOT_ANALYSER_HOME="/Users/elhanafi_s/IdeaProjects/soot-analyser"
JAR_PATH="$SKILL_RESOURCES_JAR"

if [ ! -f "$JAR_PATH" ]; then
    JAR_PATH="$SOOT_ANALYSER_HOME/target/usecase-path-analyzer.jar"
fi

JAVA_21_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"

# Configurer JAVA_HOME pour Java 21 si disponible
if [ -d "$JAVA_21_HOME" ]; then
    export JAVA_HOME="$JAVA_21_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Valeurs par défaut : le projet cible est le dossier courant (pwd), le package est com.bforbank
TARGET_DIR="$(pwd)"
BASE_PACKAGE="com.bforbank"
OUTPUT_FILE=""
TYPE_FILTER=""
ENTRYPOINT_SIG=""
USE_SPARK=false
AUTO_COMPILE=true

print_usage() {
    cat << EOF
Usage: $(basename "$0") [options]

Automatise l'analyse statique des Use Cases d'un projet Java avec Soot.
Par défaut, analyse le projet dans le répertoire courant ($(pwd)) avec le package 'com.bforbank'.

Options:
  -d, --dir <path>          Répertoire racine du projet Java à analyser (défaut: PWD)
  -p, --package <pkg>       Package racine à analyser (défaut: com.bforbank)
  -o, --output <file>       Chemin du rapport JSON de sortie (défaut: <target_dir>/target/soot-analysis-report.json)
  -t, --type <types>        Filtre sur les points d'entrée (ex: rest,kafka,scheduled,batch,runner,temporal)
  -e, --entrypoint <sig>    Signature Soot explicite d'un entrypoint spécifique
  -cp, --classpath <path>   Classpath explicite (outrepasse la détection automatique)
  --include-test            Inclure également target/test-classes dans le classpath
  --spark                   Activer l'analyse avancée SPARK points-to
  --no-compile              Désactiver la compilation automatique préalable du projet cible
  -h, --help                Afficher cette aide

Exemples:
  # 1. Analyse automatique du projet dans le dossier courant
  $(basename "$0")

  # 2. Analyse d'un projet spécifique avec filtre REST et Kafka
  $(basename "$0") -d /chemin/mon-service -p com.bforbank.service -t rest,kafka

  # 3. Ciblage d'un entrypoint précis
  $(basename "$0") -d /chemin/mon-service -e "<com.bforbank.ctrl.PaymentCtrl: void pay()>"
EOF
}

CUSTOM_CLASSPATH=""
INCLUDE_TEST=false

# Parsing des arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -d|--dir)
            TARGET_DIR="$2"
            shift 2
            ;;
        -cp|--classpath)
            CUSTOM_CLASSPATH="$2"
            shift 2
            ;;
        --include-test)
            INCLUDE_TEST=true
            shift
            ;;
        -p|--package)
            BASE_PACKAGE="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        -t|--type)
            TYPE_FILTER="$2"
            shift 2
            ;;
        -e|--entrypoint)
            ENTRYPOINT_SIG="$2"
            shift 2
            ;;
        --spark)
            USE_SPARK=true
            shift
            ;;
        --no-compile)
            AUTO_COMPILE=false
            shift
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            echo "Option inconnue: $1" >&2
            print_usage
            exit 1
            ;;
    esac
done

TARGET_DIR="$(cd "$TARGET_DIR" 2>/dev/null && pwd || echo "$TARGET_DIR")"

if [ ! -d "$TARGET_DIR" ]; then
    echo "Erreur: Le répertoire cible '$TARGET_DIR' n'existe pas." >&2
    exit 1
fi

echo "================================================================================"
echo "  SOOT USE CASE PATH ANALYZER"
echo "================================================================================"
echo "Projet cible   : $TARGET_DIR"

# 1. Vérification du Fat JAR soot-analyser
if [ ! -f "$JAR_PATH" ]; then
    echo "[1/4] Construction du JAR soot-analyser..."
    (cd "$SOOT_ANALYSER_HOME" && mvn clean package -DskipTests -q)
    if [ ! -f "$JAR_PATH" ]; then
        echo "Erreur: Échec de génération du JAR $JAR_PATH" >&2
        exit 1
    fi
else
    echo "[1/4] JAR soot-analyser : $JAR_PATH"
fi

# 2. Détection du système de build et compilation si nécessaire
CLASSPATH_ENTRIES=()

if [ -n "$CUSTOM_CLASSPATH" ]; then
    echo "[2/4] Utilisation du classpath explicite fourni."
    JOINED_CP="$CUSTOM_CLASSPATH"
else
    if [ -f "$TARGET_DIR/pom.xml" ]; then
        echo "[2/4] Projet Maven détecté."
        # Détection des sous-modules ou du dossier target/classes
        CLASSES_DIRS=($(find "$TARGET_DIR" -type d -path "*/target/classes" 2>/dev/null || true))
        
        if [ ${#CLASSES_DIRS[@]} -eq 0 ] && [ "$AUTO_COMPILE" = true ]; then
            echo "      Aucun dossier 'target/classes' trouvé. Compilation en cours ('mvn compile -DskipTests')..."
            (cd "$TARGET_DIR" && mvn compile -DskipTests -q)
            CLASSES_DIRS=($(find "$TARGET_DIR" -type d -path "*/target/classes" 2>/dev/null || true))
        fi

        for dir in "${CLASSES_DIRS[@]}"; do
            CLASSPATH_ENTRIES+=("$dir")
        done

        if [ "$INCLUDE_TEST" = true ]; then
            TEST_DIRS=($(find "$TARGET_DIR" -type d -path "*/target/test-classes" 2>/dev/null || true))
            for dir in "${TEST_DIRS[@]}"; do
                CLASSPATH_ENTRIES+=("$dir")
            done
        fi

    elif [ -f "$TARGET_DIR/build.gradle" ] || [ -f "$TARGET_DIR/build.gradle.kts" ]; then
        echo "[2/4] Projet Gradle détecté."
        CLASSES_DIRS=($(find "$TARGET_DIR" -type d -path "*/build/classes/java/main" 2>/dev/null || true))

        if [ ${#CLASSES_DIRS[@]} -eq 0 ] && [ "$AUTO_COMPILE" = true ]; then
            echo "      Aucun dossier compilé trouvé. Compilation en cours ('./gradlew classes -x test')..."
            (cd "$TARGET_DIR" && ./gradlew classes -x test -q)
            CLASSES_DIRS=($(find "$TARGET_DIR" -type d -path "*/build/classes/java/main" 2>/dev/null || true))
        fi

        for dir in "${CLASSES_DIRS[@]}"; do
            CLASSPATH_ENTRIES+=("$dir")
        done

        if [ "$INCLUDE_TEST" = true ]; then
            TEST_DIRS=($(find "$TARGET_DIR" -type d -path "*/build/classes/java/test" 2>/dev/null || true))
            for dir in "${TEST_DIRS[@]}"; do
                CLASSPATH_ENTRIES+=("$dir")
            done
        fi
    else
        echo "[2/4] Système de build non standard. Recherche de dossiers 'classes'..."
        CLASSES_DIRS=($(find "$TARGET_DIR" -type d -name "classes" 2>/dev/null || true))
        for dir in "${CLASSES_DIRS[@]}"; do
            CLASSPATH_ENTRIES+=("$dir")
        done
    fi

    if [ ${#CLASSPATH_ENTRIES[@]} -eq 0 ]; then
        echo "Erreur: Aucun dossier de bytecode compilé ('target/classes' ou 'build/classes') n'a été trouvé dans '$TARGET_DIR'." >&2
        echo "Veuillez compiler le projet d'abord ou vérifier le chemin." >&2
        exit 1
    fi

    # Assembler le classpath séparé par ':'
    JOINED_CP=$(IFS=:; echo "${CLASSPATH_ENTRIES[*]}")
    echo "      Classpath assemblé (${#CLASSPATH_ENTRIES[@]} dossiers)."
fi

# 3. Fichier de sortie par défaut
if [ -z "$OUTPUT_FILE" ]; then
    OUTPUT_FILE="$TARGET_DIR/target/soot-analysis-report.json"
fi
mkdir -p "$(dirname "$OUTPUT_FILE")"

# 4. Préparation de la commande Java
CMD_ARGS=(
    "-jar" "$JAR_PATH"
    "-cp" "$JOINED_CP"
    "-o" "$OUTPUT_FILE"
)

if [ -n "$BASE_PACKAGE" ]; then
    CMD_ARGS+=("-p" "$BASE_PACKAGE")
fi

if [ -n "$TYPE_FILTER" ]; then
    CMD_ARGS+=("-t" "$TYPE_FILTER")
fi

if [ -n "$ENTRYPOINT_SIG" ]; then
    CMD_ARGS+=("-e" "$ENTRYPOINT_SIG")
fi

if [ "$USE_SPARK" = true ]; then
    CMD_ARGS+=("--spark")
fi

echo "[3/4] Lancement de l'analyse Soot..."
echo "      Package cible : ${BASE_PACKAGE:-(aucun filtre - tout le projet)}"
echo "      Sortie JSON   : $OUTPUT_FILE"
if [ -n "$TYPE_FILTER" ]; then
    echo "      Filtre types  : $TYPE_FILTER"
fi
echo "--------------------------------------------------------------------------------"

java "${CMD_ARGS[@]}"

echo "================================================================================"
echo "[4/4] Analyse terminée avec succès !"
echo "      Rapport disponible dans : $OUTPUT_FILE"
echo "================================================================================"
