cd "$(dirname "$0")"

SERVER_JAR="paper-1.21.8-6.jar"

MIN_RAM="1G"
MAX_RAM="2G"


java -Xmx$MAX_RAM -Xms$MIN_RAM -jar "$SERVER_JAR" --nogui

echo "Server spento. Premi Invio per chiudere questa finestra."
read -p ""

