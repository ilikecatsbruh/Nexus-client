package com.nexus.nexus.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class NexusClient implements ClientModInitializer {

    private static final Map<String, String> settings = new HashMap<>();
    private static final Path FILE = MinecraftClient.getInstance().runDirectory.toPath().resolve("options-nexus.txt");
    public static void setting_set(String key, String value) {settings.put(key, value); save();}
    public static String setting_get(String key, String def) {return settings.getOrDefault(key, def);}

    private boolean menuKeyDown = false;

    private static void load() {
        try {
            if (!Files.exists(FILE)) return;

            for (String line : Files.readAllLines(FILE)) {
                int i = line.indexOf('=');
                if (i == -1) continue;

                String k = line.substring(0, i);
                String v = line.substring(i + 1);

                settings.put(k, v);
            }
        } catch (Exception ignored) {}
    }

    private static void save() {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(FILE))) {
            for (Map.Entry<String, String> e : settings.entrySet()) {
                out.println(e.getKey() + "=" + e.getValue());
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onInitializeClient() {

        load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Open GUI
            boolean pressed = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_Y) == GLFW.GLFW_PRESS;
            if (pressed && !menuKeyDown) {
                client.setScreen(new NexusGui());
            }
            menuKeyDown = pressed;
            
            // Pause all modules when GUI is open
            if (client.currentScreen != null) return;

            // Crystal ESP
            if ("true".equals(setting_get("enable_crystal_esp", "false"))) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof EndCrystalEntity) e.setGlowing(true);
                }
            }

            // Kill Aura
            if ("true".equals(setting_get("enable_kill_aura", "false"))) {
                for (PlayerEntity p : client.world.getPlayers()) {
                    if (p == client.player || !p.isAlive() || client.player.distanceTo(p) > 5) continue;
                    client.interactionManager.attackEntity(client.player, p);
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }

            // Elytra Fly
            if ("true".equals(setting_get("enable_fly_elytra", "false"))) {
                if (client.player == null) return;

                // if not wearing elytra cancel
                // if (client.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) return;                

                double speed = 1.0;

                boolean f = client.options.forwardKey.isPressed();
                boolean b = client.options.backKey.isPressed();
                boolean l = client.options.leftKey.isPressed();
                boolean r = client.options.rightKey.isPressed();
                boolean u = client.options.jumpKey.isPressed();
                boolean d = client.options.sneakKey.isPressed();

                Vec3d vel = client.player.getVelocity();

                if (!f && !b && !l && !r && !u && !d) {
                    client.player.setVelocity(0, vel.y * 0.98, 0);
                    return;
                }

                Vec3d look = client.player.getRotationVec(1.0f);
                Vec3d out = new Vec3d(vel.x, vel.y, vel.z);

                if (u) out = out.add(0, speed, 0);
                if (d) out = out.add(0, -speed, 0);
                if (f) out = out.add(look.multiply(speed));
                if (b) out = out.add(look.multiply(-speed));
                if (l) out = out.add(look.rotateY((float)Math.toRadians(90)).multiply(speed));
                if (r) out = out.add(look.rotateY((float)Math.toRadians(-90)).multiply(speed));

                client.player.setVelocity(out);
            }

            // Flight
            if ("true".equals(setting_get("enable_fly_normal", "false"))) {
                if (client.player == null) return;
                client.player.getAbilities().allowFlying = true;
            }

            // No Fall
            if ("true".equals(setting_get("enable_no_fall", "false"))) {
                if (client.player == null) return;            
                client.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.Full(
                        client.player.getX(),
                        client.player.getY() + 0.0001,
                        client.player.getZ(),
                        client.player.getYaw(),
                        client.player.getPitch(),
                        true
                    )
                );
            }

        });
    }

    // GUI
    class NexusGui extends Screen {
        protected NexusGui() {
            super(Text.literal("Nexus Menu"));
        }

        @Override
        protected void init() {
            int startX = this.width / 2 - 105;
            int startY = 35;

            int buttonWidth = 100;
            int buttonHeight = 20;

            int xGap = 10;
            int yGap = 25;

            String[][] modules = {
                    {"Crystal ESP", "crystal_esp"},
                    {"Kill Aura", "kill_aura"},
                    {"Elytra Flight", "fly_elytra"},
                    {"Regular Flight", "fly_normal"},
                    {"No Fall", "no_fall"}
            };

            for (int i = 0; i < modules.length; i++) {
                int col = i % 2;
                int row = i / 2;

                int x = startX + (buttonWidth + xGap) * col;
                int y = startY + yGap * row;

                addModuleToggle(modules[i][0], "enable_" + modules[i][1], x, y, buttonWidth, buttonHeight);
            }
        }

        private void addModuleToggle(String name, String key, int x, int y, int width, int height) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(name + ": " + setting_get(key, "false")),
                    b -> {
                        String v = setting_get(key, "false").equals("true") ? "false" : "true";
                        setting_set(key, v);
                        this.clearAndInit();
                    }
            ).dimensions(x, y, width, height).build());
        }

    }
}