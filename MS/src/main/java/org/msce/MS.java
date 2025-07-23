package org.msce;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class MS extends JavaPlugin implements Listener {

    private WebSocketServer wsServer;
    private static final int PORT = 8887;

    private Player controlledAgent = null;
    private WebSocket pythonClientConnection = null;

    private static final int OBSERVATION_INTERVAL_TICKS = 5;
    private boolean agentWasHit = false;

    @Override
    public void onEnable() {
        getLogger().info("MSCE enabled!");

        getServer().getPluginManager().registerEvents(this, this); // 'this' refers to MS instance

        try {
            wsServer = new WebSocketServer(new InetSocketAddress(PORT)) {
                @Override
                public void onOpen(WebSocket conn, ClientHandshake handshake) {
                    getLogger().info(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " connected!");

                    if (pythonClientConnection == null) {
                        pythonClientConnection = conn;
                        conn.send("Welcome from the Minecraft server! You are the agent controller.");
                        getLogger().info("Python connection set to control the agent.");
                    } else {
                        conn.send("A controller is already connected. Disconnecting.");
                        conn.close();
                        getLogger().warning("Multiple connection attempt, rejected.");
                    }
                }

                @Override
                public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                    getLogger().info(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " disconnected!");
                    if (conn == pythonClientConnection) {
                        pythonClientConnection = null;
                        getLogger().info("Python controller disconnected.");
                        getServer().getScheduler().cancelTasks(MS.this);
                    }
                }

                @Override
                public void onMessage(WebSocket conn, String message) {
                    getLogger().info("JSON message received from " + conn.getRemoteSocketAddress().getAddress().getHostAddress() + ": " + message);

                    if (conn != pythonClientConnection) {
                        getLogger().warning("Command message from unauthorized client.");
                        return;
                    }

                    try {
                        JSONObject command = (JSONObject) new JSONParser().parse(message);
                        String commandType = (String) command.get("type");

                        if ("set_agent".equals(commandType)) {
                            String agentName = (String) command.get("player_name");
                            getLogger().info("Attempting to set agent with name: " + agentName);
                            Player targetPlayer = getServer().getPlayer(agentName);
                            if (targetPlayer != null && targetPlayer.isOnline()) {
                                controlledAgent = targetPlayer;
                                conn.send("{\"status\": \"success\", \"message\": \"Agent set to " + agentName + "\"}");
                                getLogger().info("Controlled agent set to: " + agentName);
                                startObservationTask();
                            } else {
                                if (targetPlayer == null) {
                                    getLogger().warning("Player '" + agentName + "' not found by server (targetPlayer is null).");
                                } else if (!targetPlayer.isOnline()) {
                                    getLogger().warning("Player '" + agentName + "' found but not online.");
                                }
                                conn.send("{\"status\": \"error\", \"message\": \"Player " + agentName + " not found or not online.\"}");
                                getLogger().warning("Failed to set agent for: " + agentName);
                            }
                        } else if ("action".equals(commandType)) {
                            if (controlledAgent == null) {
                                getLogger().warning("Agent not set! No player to control for action: " + command.get("action"));
                                conn.send("{\"status\": \"error\", \"message\": \"Agent not set for action\"}");
                                return;
                            }

                            String action = (String) command.get("action");
                            double value = (command.containsKey("value") ? ((Number) command.get("value")).doubleValue() : 0);

                            getServer().getScheduler().runTask(MS.this, () -> {
                                switch (action) {
                                    case "move_forward":
                                        controlledAgent.setVelocity(controlledAgent.getLocation().getDirection().multiply(value));
                                        break;
                                    case "move_backward":
                                        controlledAgent.setVelocity(controlledAgent.getLocation().getDirection().multiply(-value / 2.0));
                                        break;
                                    case "turn_left":
                                        controlledAgent.getLocation().setYaw(controlledAgent.getLocation().getYaw() - (float) value);
                                        controlledAgent.teleport(controlledAgent.getLocation());
                                        break;
                                    case "turn_right":
                                        controlledAgent.getLocation().setYaw(controlledAgent.getLocation().getYaw() + (float) value);
                                        controlledAgent.teleport(controlledAgent.getLocation());
                                        break;
                                    case "jump":
                                        if (controlledAgent.isOnGround()) {
                                            controlledAgent.setVelocity(controlledAgent.getVelocity().setY(0.4));
                                        }
                                        break;
                                    case "attack":
                                        LivingEntity targetMob = findNearestTarget(controlledAgent, 5.0);
                                        if (targetMob != null) {
                                            controlledAgent.swingMainHand();
                                            getLogger().info("Agent attacks " + targetMob.getType().name() + " at distance: " + controlledAgent.getLocation().distance(targetMob.getLocation()));
                                        } else {
                                            getLogger().info("Agent attempted to attack but no target in range.");
                                        }
                                        break;
                                    case "use_item":
                                        getLogger().info("Agent attempts to use item (not fully implemented).");
                                        break;
                                    case "spawn_mob":
                                        String mobType = (String) command.get("mob_type");
                                        try {
                                            EntityType type = EntityType.valueOf(mobType.toUpperCase());
                                            if (type.isSpawnable() && type.isAlive()) {
                                                controlledAgent.getWorld().spawnEntity(controlledAgent.getLocation().add(2,0,0), type);
                                                getLogger().info("Spawned mob: " + mobType);
                                                conn.send("{\"status\": \"success\", \"message\": \"Spawned " + mobType + "\"}");
                                            } else {
                                                conn.send("{\"status\": \"error\", \"message\": \"Invalid or unspawnable mob type: " + mobType + "\"}");
                                            }
                                        } catch (IllegalArgumentException e) {
                                            conn.send("{\"status\": \"error\", \"message\": \"Unknown mob type: " + mobType + "\"}");
                                            getLogger().warning("Unknown mob type: " + mobType + " (" + e.getMessage() + ")");
                                        }
                                        break;
                                    default:
                                        getLogger().warning("Unknown action: " + action);
                                        conn.send("{\"status\": \"error\", \"message\": \"Unknown action: " + action + "\"}");
                                }
                            });
                        } else {
                            getLogger().warning("Unknown command type: " + commandType);
                            conn.send("{\"status\": \"error\", \"message\": \"Unknown command type: " + commandType + "\"}");
                        }
                    } catch (Exception e) {
                        getLogger().severe("Error parsing or executing command: " + e.getMessage());
                        conn.send("{\"status\": \"error\", \"message\": \"Command error: " + e.getMessage() + "\"}");
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(WebSocket conn, ByteBuffer message) {
                    // Binary messages are not used for core commands/observations at this time
                }

                @Override
                public void onError(WebSocket conn, Exception ex) {
                    getLogger().warning("WebSocket error: " + ex.getMessage());
                    ex.printStackTrace();
                }

                @Override
                public void onStart() {
                    getLogger().info("WebSocket server started on port: " + PORT);
                    // setConnectionLostTimeout(0); // Set a reasonable timeout in seconds if desired
                }
            };

            wsServer.start();

        } catch (Exception e) {
            getLogger().severe("Error starting WebSocket server: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("MSCE disabled!");
        if (wsServer != null) {
            try {
                wsServer.stop();
                getLogger().info("WebSocket server stopped.");
            } catch (InterruptedException e) {
                getLogger().severe("Error stopping WebSocket server: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Handles EntityDamageEvent to detect if the controlled agent was hit.
     * @param event The EntityDamageEvent.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Check if the entity that took damage is our controlled agent
        if (controlledAgent != null && event.getEntity().equals(controlledAgent)) {
            // The agent was hit! Set the flag
            setAgentWasHit(true);
            getLogger().info("Agent " + controlledAgent.getName() + " was hit! Damage: " + event.getDamage());
        }
    }

    /**
     * Gets the currently controlled player agent.
     * @return The Player instance being controlled, or null if not set.
     */
    public Player getControlledAgent() {
        return controlledAgent;
    }

    /**
     * Sets the flag indicating if the agent was recently hit.
     * This flag is reset after observations are sent to the Python client.
     * @param hit True if the agent was hit, false otherwise.
     */
    public void setAgentWasHit(boolean hit) {
        this.agentWasHit = hit;
    }

    /**
     * Sends current game observations to the Python client.
     * This method runs on the Bukkit main thread.
     */
    private void sendObservations() {
        if (pythonClientConnection != null && controlledAgent != null) {
            getServer().getScheduler().runTask(this, () -> {
                if (!controlledAgent.isOnline()) {
                    getLogger().warning("Agent disconnected! Cannot send observations.");
                    // You might want to close the Python connection here or handle it otherwise
                    return;
                }

                JSONObject observations = new JSONObject();

                // Add "agent_was_hit" status to observations
                observations.put("agent_was_hit", agentWasHit);
                agentWasHit = false; // Reset status after sending

                // Agent Observations
                JSONObject agentData = new JSONObject();
                agentData.put("x", controlledAgent.getLocation().getX());
                agentData.put("y", controlledAgent.getLocation().getY());
                agentData.put("z", controlledAgent.getLocation().getZ());
                agentData.put("health", controlledAgent.getHealth());
                agentData.put("food_level", controlledAgent.getFoodLevel());
                agentData.put("is_sprinting", controlledAgent.isSprinting());
                agentData.put("is_blocking", controlledAgent.isBlocking());
                agentData.put("yaw", controlledAgent.getLocation().getYaw());
                agentData.put("pitch", controlledAgent.getLocation().getPitch());
                observations.put("agent", agentData);

                // Nearby Mobs Observations
                JSONArray nearbyMobs = new JSONArray();
                for (org.bukkit.entity.Entity entity : controlledAgent.getNearbyEntities(20, 20, 20)) {
                    if (entity instanceof LivingEntity && !(entity instanceof Player) && entity instanceof Monster) {
                        LivingEntity mob = (LivingEntity) entity;
                        JSONObject mobData = new JSONObject();
                        mobData.put("id", mob.getUniqueId().toString());
                        mobData.put("type", mob.getType().name());
                        mobData.put("x", mob.getLocation().getX());
                        mobData.put("y", mob.getLocation().getY());
                        mobData.put("z", mob.getLocation().getZ());
                        mobData.put("health", mob.getHealth());
                        mobData.put("distance", controlledAgent.getLocation().distance(mob.getLocation()));
                        nearbyMobs.add(mobData);
                    }
                }
                observations.put("nearby_mobs", nearbyMobs);

                observations.put("timestamp", System.currentTimeMillis());

                pythonClientConnection.send(observations.toJSONString());
            });
        }
    }

    /**
     * Starts the periodic task for sending observations to the Python client.
     * Any existing observation task will be cancelled before starting a new one.
     */
    private void startObservationTask() {
        getServer().getScheduler().cancelTasks(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                sendObservations();
            }
        }.runTaskTimer(this, 0L, OBSERVATION_INTERVAL_TICKS);
    }

    /**
     * Helper method to find the nearest hostile mob within a given radius from the player.
     * @param player The player to search around.
     * @param radius The search radius.
     * @return The nearest hostile LivingEntity, or null if none found.
     */
    private LivingEntity findNearestTarget(Player player, double radius) {
        LivingEntity nearestTarget = null;
        double minDistance = Double.MAX_VALUE;

        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            // Consider only hostile monsters that are LivingEntity (not items, etc.) and not the player itself
            if (entity instanceof LivingEntity && !(entity instanceof Player) && entity instanceof Monster) {
                double distance = player.getLocation().distance(entity.getLocation());
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestTarget = (LivingEntity) entity;
                }
            }
        }
        return nearestTarget;
    }
}