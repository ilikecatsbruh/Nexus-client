package com.nexus.nexus.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.glfw.GLFW;


public class NexusClient implements ClientModInitializer {
    public static boolean auraEnabled = false;
    public static boolean espEnabled = false;
    public static boolean totemEnabled = false;
    public static boolean elytraFlyEnabled = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            if (espEnabled) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof EndCrystalEntity) e.setGlowing(true);
                }
            }

            if (auraEnabled) doCombat(client);
            if (totemEnabled) doStrictTotem(client);

            if (elytraFlyEnabled) doElytraFly(client);

            if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_Y) == GLFW.GLFW_PRESS) {
                if (client.currentScreen == null) client.setScreen(new NexusGui());
            }

        });
    }
    

    private void doElytraFly(MinecraftClient client) {
        if (client.player == null) return;

        if (!client.player.isFallFlying()) return;

        boolean forward = client.options.forwardKey.isPressed();
        boolean back = client.options.backKey.isPressed();
        boolean left = client.options.leftKey.isPressed();
        boolean right = client.options.rightKey.isPressed();
        boolean up = client.options.jumpKey.isPressed();
        boolean down = client.options.sneakKey.isPressed();

        Vec3d velocity = client.player.getVelocity();

        boolean anyInput = forward || back || left || right || up || down;

        if (!anyInput) {
            // HARD STOP: kill horizontal motion completely
            client.player.setVelocity(0, velocity.y * 0.98, 0);
            return;
        }

        Vec3d newVel = new Vec3d(velocity.x, velocity.y, velocity.z);

        if (up) {
            newVel = newVel.add(0, 0.1, 0);
        }

        if (down) {
            newVel = newVel.add(0, -0.1, 0);
        }

        Vec3d look = client.player.getRotationVec(1.0f);

        if (forward) {
            newVel = newVel.add(look.x * 0.1, 0, look.z * 0.1);
        }

        if (back) {
            newVel = newVel.add(-look.x * 0.1, 0, -look.z * 0.1);
        }

        if (left) {
            Vec3d leftVec = look.rotateY((float) Math.toRadians(90)).multiply(0.1);
            newVel = newVel.add(leftVec.x, 0, leftVec.z);
        }

        if (right) {
            Vec3d rightVec = look.rotateY((float) Math.toRadians(-90)).multiply(0.1);
            newVel = newVel.add(rightVec.x, 0, rightVec.z);
        }

        client.player.setVelocity(newVel);
    }

    private void doCombat(MinecraftClient client) {
        for (PlayerEntity target : client.world.getPlayers()) {
            if (target == client.player || !target.isAlive() || client.player.distanceTo(target) > 5) continue;
            client.interactionManager.attackEntity(client.player, target);
            for (Entity e : client.world.getEntities()) {
                if (e instanceof EndCrystalEntity && e.distanceTo(client.player) <= 5) {
                    client.interactionManager.attackEntity(client.player, e);
                }
            }
            BlockPos p = target.getBlockPos();
            for (BlockPos pos : BlockPos.iterate(p.add(-1, -1, -1), p.add(1, 0, 1))) {
                if (client.world.getBlockState(pos).isOf(Blocks.OBSIDIAN)) {
                    client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                            new BlockHitResult(new Vec3d(pos.getX(), pos.getY(), pos.getZ()), Direction.UP, pos, false));
                }
            }
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void doStrictTotem(MinecraftClient client) {
        if (client.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;
        for (int i = 0; i < 36; i++) {
            if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                int slot = i < 9 ? i + 36 : i;
                client.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(client.player.currentScreenHandler.syncId,
                        client.player.currentScreenHandler.getRevision(), slot, 0, SlotActionType.PICKUP,
                        client.player.getInventory().getStack(i).copy(), new Int2ObjectOpenHashMap<>()));
                client.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(client.player.currentScreenHandler.syncId,
                        client.player.currentScreenHandler.getRevision(), 45, 0, SlotActionType.PICKUP,
                        net.minecraft.item.ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
                return;
            }
        }
    }

    static class NexusGui extends Screen {
        public NexusGui() { super(Text.literal("Nexus Menu")); }
        @Override
        protected void init() {
            int x = this.width / 2 - 100;
            addBtn("Player Kill Aura: ", auraEnabled, b -> auraEnabled = !auraEnabled, x, 60);
            addBtn("Crystal ESP: ", espEnabled, b -> espEnabled = !espEnabled, x, 90);
            addBtn("Auto Totem: ", totemEnabled, b -> totemEnabled = !totemEnabled, x, 120);
            addBtn("Elytra Fly: ", elytraFlyEnabled, b -> elytraFlyEnabled = !elytraFlyEnabled, x, 150);
        }
        private void addBtn(String label, boolean state, ButtonWidget.PressAction action, int x, int y) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal(label + (state ? "ON" : "OFF")), button -> {
                action.onPress(button);
                this.clearAndInit();
            }).dimensions(x, y, 200, 20).build());
        }
        @Override
        public boolean shouldPause() { return false; }
    }
}
