# Working...

This project is currently in active development.

-----

## Project Description

This project explores the application of **evolutionary computation for developing autonomous combat strategies in Minecraft**. The goal is to create intelligent agents capable of adapting and defeating progressively challenging waves of in-game enemies (referred to as "mobs"). Leveraging Minecraft's flexible environment, this project focuses on evolving diverse combat behaviors and tactics, aiming to identify (and potentially combine) the most effective strategies that emerge through the evolutionary process. Unlike common Reinforcement Learning approaches that optimize a single strategy, this system uses evolutionary computation to explore a broader spectrum of potential behaviors in parallel.

-----

## Getting Started

To run this project, you'll need three main components working together: the Minecraft server with its custom plugin, your Minecraft game client, and the Python client controlling the agent.

### Prerequisites

Before you begin, ensure you have:

  * **Java Development Kit (JDK) 21 or newer:** Essential for running the PaperMC server and compiling the plugin. Verify your `java -version` command outputs `21.0.x`.
  * **Minecraft Java Edition 1.21.8:** Your game client must match the server version for compatibility.
  * **Python 3.x:** For running the evolutionary computation client.
  * **IntelliJ IDEA Community Edition:** Recommended for developing the Java plugin.

### Setup Steps

1.  **Server Setup (PaperMC Plugin)**

      * Set up your PaperMC server (version 1.21.8) as previously configured.
      * Compile the custom Java plugin (`MS.java`) using Maven (run the `package` goal in IntelliJ IDEA).
      * Copy the generated JAR file (e.g., `MS-1.0-SNAPSHOT.jar`) from your project's `target/` directory into the `plugins/` folder of your PaperMC server.

2.  **Python Client Setup**

      * Navigate to the directory containing your `websocket_client.py` script.
      * Install the required Python library:
        ```bash
        pip install websocket-client
        ```
      * **First Run Configuration:** The first time you execute `websocket_client.py`, it will prompt you to **enter your in-game Minecraft player name**. This name will be saved in a `config.json` file in the same directory. This `config.json` file will then be used for all subsequent executions, so you won't need to re-enter your name unless you delete the file or need to change it.

### Running the Project

To start your combat evolution experiment:

1.  **Start the Minecraft Server:**
    Open a terminal, navigate to your server directory, and run the server double-clicking the file

    ```bash
    ./MinecraftServer/start_server.sh
    ```

    Keep this terminal open, as it displays server logs and your plugin's output.

2.  **Join the Minecraft Server with Your Game Client:**
    Launch Minecraft Java Edition (version 1.21.8) and connect to your running server (typically `localhost` if on the same machine). Ensure you log in with the **exact player name** you've configured in the Python client.

3.  **Start the Python Client:**
    Open a **new** terminal, navigate to your Python client directory, and execute the script:

    ```bash
    python websocket_client.py
    ```

    This script will connect to the server, attempt to set your in-game character as the controlled agent, and begin sending commands based on its current logic (initially, random movements and attack reactions).

Observe both the server and Python client terminals for logs indicating successful communication and agent actions.

-----

## Troubleshooting Common Issues

  * **`UnsupportedClassVersionError`**: You're trying to run the server with an older Java version. Ensure your terminal is using **Java 21**. Refer to guides on how to set `JAVA_HOME` and `PATH` for macOS/Windows/Linux.
  * **`plugin.yml not found`**: The `plugin.yml` file isn't correctly included in the plugin JAR. Ensure it's in `src/main/resources/` and the `<resources>` section is correct in your `pom.xml`. Recompile and recopy the JAR.
  * **`Cannot resolve symbol 'json'`**: The `json-simple` dependency is missing in your `pom.xml`. Add the `com.googlecode.json-simple:json-simple:1.1.1` dependency and reload your Maven projects.
  * **`Agent not set!`**:
    1.  Ensure the player name in your `config.json` is **exactly** the same as your Minecraft in-game nickname (case-sensitive).
    2.  Make sure your player is **actively online** and connected to the server when you run the Python script.
    3.  The 2-second delay in `websocket_client.py` (`time.sleep(2)`) helps ensure the server has time.

-----

## Next Steps

With the communication pipeline established, the next crucial phase involves replacing the hardcoded reactive logic in the Python client with a sophisticated **evolutionary computation algorithm**. This algorithm will process game observations, generate optimized combat strategies, and control your agent to adaptively face increasingly challenging mob encounters.
