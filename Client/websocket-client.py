import websocket
import threading
import time
import json
import random # Import the random module for randomness
import os # Necessary if you're using a config file, otherwise you can remove it

# WebSocket server address and port of your PaperMC mod
SERVER_ADDRESS = "ws://localhost:8887"

CONFIG_FILE = "config.json" 
PLAYER_NAME = ""
SERVER_ADDRESS_HOST = "localhost"
SERVER_PORT = 8887

if os.path.exists(CONFIG_FILE):
    try:
        with open(CONFIG_FILE, "r") as f:
            # Assuming JSON for greater flexibility
            config = json.load(f)
            PLAYER_NAME = config.get("player_name", "")
            SERVER_ADDRESS_HOST = config.get("server_address", "localhost")
            SERVER_PORT = config.get("server_port", 8887)
    except json.JSONDecodeError:
        print(f"Error: The file '{CONFIG_FILE}' is not a valid JSON. Proceeding with manual input.")
        PLAYER_NAME = input("Enter the player name for control: ")
    except FileNotFoundError: # Even if os.path.exists, better to be safe
        pass # Will be handled by the else block
else:
    print(f"ATTENTION: The file '{CONFIG_FILE}' not found. Creating a sample configuration file.")
    player_name_input = input("Enter the player name for control: ")
    config = {
        "player_name": player_name_input,
        "server_address": "localhost",
        "server_port": 8887
    }
    with open(CONFIG_FILE, "w") as f:
        json.dump(config, f, indent=4)
    PLAYER_NAME = player_name_input
    print(f"File '{CONFIG_FILE}' created. Please check it.")

SERVER_ADDRESS = f"ws://{SERVER_ADDRESS_HOST}:{SERVER_PORT}"

if not PLAYER_NAME:
    print("ERROR: Player name not set in configuration. The script cannot continue.")
    exit()

print(f"Controlling player: {PLAYER_NAME} on {SERVER_ADDRESS}")
# --- END LOGIC FOR CONFIG FILE ---

# PLAYER_NAME = "Depipasd" # Uncomment this line if you are NOT using the config file

def on_message(ws, message):
    """Function called when a message is received from the server."""
    # print(f"Message received from Minecraft server: {message}") # You can disable this verbose log
    try:
        data = json.loads(message)
        # print(f"JSON data received: {data}") # You can disable this verbose log
        if "status" in data:
            if data["status"] == "success":
                print(f"Server confirmed: {data.get('message', 'Success')}")
            elif data["status"] == "error":
                print(f"Server reported an error: {data.get('message', 'Unknown error')}")
        # Handling observations
        elif "agent" in data and "nearby_mobs" in data:
            print(f"OBSERVATIONS RECEIVED - Agent: Health={data['agent']['health']:.1f}, Nearby Mobs: {len(data['nearby_mobs'])}")
            if data['nearby_mobs']:
                # Print data of the closest mob
                closest_mob = sorted(data['nearby_mobs'], key=lambda x: x['distance'])[0]
                print(f"    Closest Mob: {closest_mob['type']} at {closest_mob['distance']:.1f} blocks, Health={closest_mob['health']:.1f}")

    except json.JSONDecodeError:
        print(f"Non-JSON message from server: {message}")


def on_error(ws, error):
    """Function called in case of an error."""
    print(f"WebSocket error: {error}")

def on_close(ws, close_status_code, close_msg):
    """Function called when the connection is closed."""
    print(f"Connection closed. Code: {close_status_code}, Message: {close_msg}")

def on_open(ws):
    """Function called when the connection has been established."""
    print("Connection established with Minecraft server.")

    # Add a small delay here to allow the server time to load the player
    time.sleep(2)  

    # --- NEW: Send command to set the agent ---
    set_agent_command = {
        "type": "set_agent",
        "player_name": PLAYER_NAME
    }
    ws.send(json.dumps(set_agent_command))
    print(f"Sent command to set agent: {PLAYER_NAME}")
    # --- END NEW ---

    # Start a separate thread to send messages, so it doesn't block reception
    threading.Thread(target=send_periodic_messages, args=(ws,)).start() # Pass 'ws' as an argument

# --- MODIFIED FUNCTION FOR RANDOM ACTIONS ---
def send_periodic_messages(ws):
    print("Starting periodic message sending (random actions)...")
    
    # Wait an extra moment to ensure the agent has been set
    time.sleep(1)

    # Define possible movement and attack actions
    movement_actions = [
        {"action": "move_forward", "value": 0.5},
        {"action": "move_backward", "value": 0.2},
        {"action": "turn_left", "value": 15}, # Turn by 15 degrees
        {"action": "turn_right", "value": 15}, # Turn by 15 degrees
        {"action": "jump", "value": 0} # Value not used for jump
    ]
    combat_actions = [
        {"action": "attack", "value": 0},
        # You can add other combat actions here, e.g., "use_item" if implemented
    ]

    # Spawn a zombie to start the test
    spawn_mob_command = {
        "type": "action",
        "action": "spawn_mob",
        "mob_type": "ZOMBIE" 
    }
    ws.send(json.dumps(spawn_mob_command))
    print("Sent command: spawn_mob ZOMBIE")
    time.sleep(1) # Give time for the spawn

    # Main action loop
    for i in range(20): # Perform 20 random actions for demonstration
        # Randomly choose between a movement action and a combat action
        # Give higher probability to movement to avoid getting stuck
        if random.random() < 0.7: # 70% probability of performing a movement
            chosen_action = random.choice(movement_actions)
        else: # 30% probability of performing an attack
            chosen_action = random.choice(combat_actions)
        
        command_to_send = {
            "type": "action",
            "action": chosen_action["action"],
            "value": chosen_action["value"]
        }
        
        ws.send(json.dumps(command_to_send))
        print(f"Sent random command: {chosen_action['action']} (value: {chosen_action['value']})")
        
        time.sleep(random.uniform(0.5, 1.5)) # Wait a random time between actions (0.5 to 1.5 seconds)

    print("Periodic message sending finished.")
    # ws.close() # Uncomment if you want the client to disconnect automatically

if __name__ == "__main__":
    print(f"Attempting to connect to {SERVER_ADDRESS}...")
    websocket.enableTrace(True) 
    ws_app = websocket.WebSocketApp(SERVER_ADDRESS,
                                on_open=on_open,
                                on_message=on_message,
                                on_error=on_error,
                                on_close=on_close)

    ws_app.run_forever()
    print("Python script terminated.")