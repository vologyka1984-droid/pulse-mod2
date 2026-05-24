package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class ExampleMod implements ModInitializer {
    // НАСТРОЙКИ АИМА
    private static final double MAX_DISTANCE = 3.5; // Дистанция (3.5 блока)
    private static final float FOV = 35.0f;         // Радиус работы (35 градусов перед собой)
    private static final float SMOOTHNESS = 6.0f;   // Плавность (6.0 — очень легитно, прицел не дрожит)

    private boolean isActive = true; // Мод включен по умолчанию
    private static KeyBinding toggleKeyBinding;

    @Override
    public void onInitialize() {
        // Регистрируем кнопку "L" в настройках управления
        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.legitaimmod.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,          
                "category.legitaimmod.aim" 
        ));

        // Основной цикл проверки каждый игровой тик
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Проверяем нажатие клавиши L
            while (toggleKeyBinding.wasPressed()) {
                isActive = !isActive; 
                if (isActive) {
                    client.player.sendMessage(Text.literal("[AimAssist] Включен").formatted(Formatting.GREEN), true);
                } else {
                    client.player.sendMessage(Text.literal("[AimAssist] Выключен").formatted(Formatting.RED), true);
                }
            }

            // Если выключен или открыто меню/чат — отдыхаем
            if (!isActive || client.currentScreen != null) return;

            // Аим работает ТОЛЬКО когда ты зажимаешь ЛКМ (бьешь)
            if (!client.options.attackKey.isPressed()) return;

            Entity target = getBestTarget(client);
            if (target != null) {
                aimAtTarget(client, target);
            }
        });
    }

    private Entity getBestTarget(MinecraftClient client) {
        Entity bestTarget = null;
        double closestDistance = MAX_DISTANCE;

        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player  !(entity instanceof LivingEntity)  !entity.isAlive()) continue;

            double distance = client.player.distanceTo(entity);
            if (distance > closestDistance) continue;

            float[] angles = getRotationToEntity(client, entity);
            float yawDiff = MathHelper.wrapDegrees(angles[0] - client.player.getYaw());
            float pitchDiff = MathHelper.wrapDegrees(angles[1] - client.player.getPitch());

            // Срабатывает только если цель в пределах FOV
            if (Math.abs(yawDiff) <= FOV && Math.abs(pitchDiff) <= FOV) {
                closestDistance = distance;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }

    private void aimAtTarget(MinecraftClient client, Entity target) {
        float[] angles = getRotationToEntity(client, target);
        
        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(angles[0] - currentYaw);
        float pitchDiff = angles[1] - currentPitch;

        // Плавная доводка до цели
        client.player.setYaw(currentYaw + (yawDiff / SMOOTHNESS));
        client.player.setPitch(currentPitch + (pitchDiff / SMOOTHNESS));
    }private float[] getRotationToEntity(MinecraftClient client, Entity target) {
        Vec3d playerEyes = client.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() / 2.0, 0);

        double diffX = targetPos.x - playerEyes.x;
        double diffY = targetPos.y - playerEyes.y;
        double diffZ = targetPos.z - playerEyes.z;
        double diffXZ = MathHelper.sqrt((float) (diffX * diffX + diffZ * diffZ));

        float yaw = (float) (MathHelper.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) -(MathHelper.atan2(diffY, diffXZ) * 180.0 / Math.PI);

        return new float[]{yaw, pitch};
    }
}
