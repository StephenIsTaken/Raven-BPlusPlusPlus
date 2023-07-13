package keystrokesmod.client.module.modules.combat;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.input.Mouse;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.client.Targets;
import keystrokesmod.client.module.modules.player.RightClicker;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class AimAssist extends Module { //TODO: Patch GCD
    public static SliderSetting speedModifier;
    public static SliderSetting fov;
    public static SliderSetting distance;
    public static SliderSetting pitchOffSet;
    public static TickSetting clickAim;
    public static TickSetting stopWhenOver;
    public static TickSetting aimPitch;
    public static TickSetting weaponOnly;
    public static TickSetting aimInvis;
    public static TickSetting breakBlocks;
    public static TickSetting blatantMode;
    public static ArrayList<Entity> friends = new ArrayList<>();

    public AimAssist() {
        super("AimAssist", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting("Set targets in Client->Targets"));
        this.registerSetting(speedModifier = new SliderSetting("Speed ", 50.0D, 5.0D, 100.0D, 1.0D));
        this.registerSetting(pitchOffSet = new SliderSetting("PitchOffSet (blocks)", 4D, 0, 4, 0.050D));
        this.registerSetting(clickAim = new TickSetting("Click aim", true));
        this.registerSetting(breakBlocks = new TickSetting("Break blocks", true));
        this.registerSetting(weaponOnly = new TickSetting("Weapon only", false));
        this.registerSetting(blatantMode = new TickSetting("Blatant mode", false));
        this.registerSetting(aimPitch = new TickSetting("Aim pitch", false));
    }

    @Subscribe
    public void onRender(ForgeEvent fe) {
        try {
            if (fe.getEvent() instanceof TickEvent.ClientTickEvent) {
                if (!Utils.Client.currentScreenMinecraft() || !Utils.Player.isPlayerInGame())
                    return;

                if (breakBlocks.isToggled() && (mc.objectMouseOver != null)) {
                    BlockPos p = mc.objectMouseOver.getBlockPos();
                    if (p != null) {
                        Block bl = mc.theWorld.getBlockState(p).getBlock();
                        if ((bl != Blocks.air) && !(bl instanceof BlockLiquid) && (bl != null))
                            return;
                    }
                }
                if (Raven.moduleManager.getModuleByClazz(KillAura.class).isEnabled()) return;

                if (!weaponOnly.isToggled() || Utils.Player.isPlayerHoldingWeapon()) {

                    Module autoClicker = Raven.moduleManager.getModuleByClazz(RightClicker.class); // right clicker???????????
                    // what if player clicking but mouse not down ????
                    if ((clickAim.isToggled() && Utils.Client.autoClickerClicking())
                            || (Mouse.isButtonDown(0) && (autoClicker != null) && !autoClicker.isEnabled())
                            || !clickAim.isToggled()) {
                        Entity en = this.getEnemy();
                        if (en != null)
                            if (blatantMode.isToggled())
                                Utils.Player.aim(en, (float) pitchOffSet.getInput());
                            else {
                                double n = Utils.Player.fovFromEntity(en);
                                if ((n > 1.0D) || (n < -1.0D)) {
                                    double complimentSpeed = n
                                            * (ThreadLocalRandom.current().nextDouble(speedModifier.getInput() - 1.47328,
                                            speedModifier.getInput() + 2.48293) / 100);
                                    float val = (float) (-(complimentSpeed + (n / (101.0D - (float) ThreadLocalRandom.current()
                                            .nextDouble(speedModifier.getInput() - 4.723847, speedModifier.getInput())))));
                                    mc.thePlayer.rotationYaw += val;

                                    //Bypass for anticheats with rotation checks xd - ok
                                    mc.thePlayer.rotationPitch += Math.random() * 0.2 - 0.1;
                                    mc.thePlayer.rotationPitch = Math.max(mc.thePlayer.rotationPitch, -90);
                                    mc.thePlayer.rotationPitch = Math.min(mc.thePlayer.rotationPitch, 90);
                                }
                                if (aimPitch.isToggled()) {
                                    double complimentSpeed = Utils.Player.PitchFromEntity(en,
                                            (float) pitchOffSet.getInput())
                                            * (ThreadLocalRandom.current().nextDouble(speedModifier.getInput() - 1.47328,
                                            speedModifier.getInput() + 2.48293) / 100);

                                    float val = (float) (-(complimentSpeed
                                            + (n / (101.0D - (float) ThreadLocalRandom.current()
                                            .nextDouble(speedModifier.getInput() - 4.723847,
                                                    speedModifier.getInput())))));

                                    mc.thePlayer.rotationPitch += val;
                                    //Bypass for anticheats with rotation checks xd
                                    mc.thePlayer.rotationPitch += Math.random() * 0.2 - 0.1;
                                    mc.thePlayer.rotationPitch = Math.max(mc.thePlayer.rotationPitch, -90);
                                    mc.thePlayer.rotationPitch = Math.min(mc.thePlayer.rotationPitch, 90);
                                }
                            }

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Entity getEnemy() {
        return Targets.getTarget();
    }

    public static void addFriend(Entity entityPlayer) {
        friends.add(entityPlayer);
    }

    public static boolean addFriend(String name) {
        boolean found = false;
        for (Entity entity : mc.theWorld.getLoadedEntityList())
            if (entity.getName().equalsIgnoreCase(name) || entity.getCustomNameTag().equalsIgnoreCase(name))
                if (!Targets.isAFriend(entity)) {
                    addFriend(entity);
                    found = true;
                }

        return found;
    }

    public static boolean removeFriend(String name) {
        try {
            boolean removed = false;
            boolean found = false;
            for (NetworkPlayerInfo networkPlayerInfo : new ArrayList<>(mc.getNetHandler().getPlayerInfoMap())) {
                Entity entity = mc.theWorld.getPlayerEntityByName(networkPlayerInfo.getDisplayName().getUnformattedText());
                if (entity.getName().equalsIgnoreCase(name) || entity.getCustomNameTag().equalsIgnoreCase(name)) {
                    removed = removeFriend(entity);
                    found = true;
                }
            }

            return found && removed;
        } catch (Exception e){
            Utils.Player.sendMessageToSelf("Error! Could not Remove "+name);
            return false;
        }
    }

    public static boolean removeFriend(Entity entityPlayer) {
        try {
            friends.remove(entityPlayer);
        } catch (Exception eeeeee) {
            eeeeee.printStackTrace();
            return false;
        }
        return true;
    }

    public static ArrayList<Entity> getFriends() {
        return friends;
    }
}
