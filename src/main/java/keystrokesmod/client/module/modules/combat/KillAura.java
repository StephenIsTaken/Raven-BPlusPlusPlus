package keystrokesmod.client.module.modules.combat;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import keystrokesmod.client.event.impl.*;
import keystrokesmod.client.utils.CombatUtils;
import keystrokesmod.client.utils.MillisTimer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.apache.commons.lang3.RandomUtils;


import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.client.Targets;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DoubleSliderSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.CoolDown;
import keystrokesmod.client.utils.Utils;
import net.minecraft.entity.player.EntityPlayer;

public class KillAura extends Module {

    private static EntityPlayer target;
    private List<EntityPlayer> pTargets;
    private CoolDown coolDown = new CoolDown(1);
    private boolean locked;
    public static float yaw, pitch, prevYaw, prevPitch,fixedYaw,fixedPitch;
    private double cps;
    private long lastClick;
    private long hold;
    private boolean blocking;
    private double speed;
    private double holdLength;
    private double min;
    private double max;
    private boolean stopClicker = false;
    MillisTimer clickTimer = new MillisTimer();
    public static SliderSetting reach,rps;
    private DoubleSliderSetting aps;
    private final TickSetting fixMovement,legitAttack,visuals,customRPS,weaponOnly;
    public static ComboSetting<BlockMode> blockMode;
    /**
     * @Author Cosmic-SC
     * @Since 10/6/2023
     * @NEWCHANGES by Plague Ghost
     * @in 13/7/2023
     * @CodeQuality SHIT.
     */
    public KillAura() {
        super("KillAura", ModuleCategory.combat);
        this.registerSetting(reach = new SliderSetting("Reach", 3.3, 3, 6, 0.05));
        this.registerSetting(aps = new DoubleSliderSetting("Left CPS", 9, 13, 1, 60, 0.5));
        this.registerSetting(customRPS = new TickSetting("Custom Rotation Speed",false));
        this.registerSetting(rps = new SliderSetting("Rotation Speed",50,10,100,1));
        this.registerSetting(legitAttack = new TickSetting("Use Legit Clicker",true));
        this.registerSetting(weaponOnly = new TickSetting("Weapon Only",false));
        this.registerSetting(fixMovement = new TickSetting("Movement Fix", true));
        this.registerSetting(visuals = new TickSetting("Visuals",false));
        this.registerSetting(blockMode = new ComboSetting<>("Block mode", BlockMode.Legit));
    }
    @Subscribe
    public void gameLoopEvent(GameLoopEvent e) {
        try {
            EntityPlayer pTarget = Targets.getTarget();
            if ((pTarget == null) || (mc.currentScreen != null) || !coolDown.hasFinished() || (weaponOnly.isToggled() && !Utils.Player.isPlayerHoldingWeapon())) {
                target = null;
                rotate(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, true);
                return;
            }
            target = pTarget;
            //ravenClick();
            float[] i = Utils.Player.getTargetRotations(target, 0);
            locked = false;
            rotate(i[0], i[1], false);
        } catch (Exception exception) {
        }
    }

    /**
     * Clickers.
     * Current Modes: Legit, Normal
     *
     * Legit can be used on anticheats that flag when attacking with the other clicker (intave, polar, grim etc)
     */
    @Subscribe
    public void onTick(TickEvent event) {
        if (!Utils.Player.isPlayerInGame()) return;

        if (target != null && Utils.Player.isPlayerHoldingSword()) {
            if ((blockMode.getMode() == BlockMode.Legit) && (mc.thePlayer.prevSwingProgress < mc.thePlayer.swingProgress)) {
                if (mc.thePlayer.ticksExisted % 15 == 0) {
                    KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                }
            }
        }

        if (!legitAttack.isToggled()) return;
        if (target != null) {
            //double distance = Utils.Player(target.getPosition(), mc.thePlayer.getPosition());

            // Adjust the clicking speed based on the enemy's distance
            double maxDistance = 5.0; // Maximum distance threshold
            double minDistance = 1.0;  // Minimum distance threshold
            double maxSpeed = 1.0;     // Maximum clicking speed (clicks per second)
            double minSpeed = 0.2;     // Minimum clicking speed (clicks per second)

            //double speed = ((distance - minDistance) / (maxDistance - minDistance)) * (maxSpeed - minSpeed) + minSpeed;

            if (System.currentTimeMillis() - lastClick > speed * 1000) {
                lastClick = System.currentTimeMillis();
                if (hold < lastClick) {
                    hold = lastClick;
                }
                int key = mc.gameSettings.keyBindAttack.getKeyCode();
                KeyBinding.setKeyBindState(key, true);
                KeyBinding.onTick(key);
                this.updateVals();
            } else if (System.currentTimeMillis() - hold > holdLength * 1000) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                this.updateVals();
            }
        } else {
            if (!stopClicker) {
                if (mc.gameSettings.keyBindAttack.pressed) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                }
                stopClicker = true;
            }
        }
    }

    @Subscribe
    public void onClickerUpdate(UpdateEvent event){
        if (legitAttack.isToggled()) return;
        Entity casted = CombatUtils.raycastEntity(reach.getInput(), entity -> entity.isEntityAlive() && entity.canBeCollidedWith() && !entity.isDead && entity == target && CombatUtils.canEntityBeSeen(entity));
        syncClicker();
        if (event.isPre()) {
            if (casted != null && Utils.Player.isPlayerHoldingSword()) {
                switch (blockMode.getMode()){
                    case Vanilla:
                        this.block();
                        break;
                    case Damage:
                        if (mc.thePlayer.hurtTime > 0){
                            this.block();
                        }
                        break;
                }
            } else {
                this.unblock();
            }

            if (clickTimer.hasElapsed((long) (1000L / cps))) {
                if (casted != null) {
                    mc.thePlayer.swingItem();
                    mc.playerController.attackEntity(mc.thePlayer, casted);
                    clickTimer.reset();
                }
            }
        }
    }

    /**
     * Rotations are done below
     */
    private boolean isZeroDegree(float yaw) {
        return yaw == 0;
    }

    private boolean isTwentyFiveDegree(float yaw) {
        return yaw == 25;
    }

    private boolean holdingRightButton = false;
    private long blockHitStartTime = 0;
    private static final long BLOCK_HIT_DURATION = 30; // milliseconds
    private static final double REACH = 3.1;

    @Subscribe
    public void onRotationUpdate(UpdateEvent e) {
        if (!Utils.Player.isPlayerInGame() || locked) {
            return;
        }

        float[] currentRots = new float[]{yaw, pitch};
        float[] prevRots = new float[]{prevYaw, prevPitch};
        float[] cappedRots = new float[]{maxAngleChange(prevRots[0], currentRots[0], (float) rps.getInput()),
                maxAngleChange(prevRots[1], currentRots[1], (float) rps.getInput())};
        float[] gcd = getGCDRotations(customRPS.isToggled() ? cappedRots : currentRots, prevRots);

        // Apply the "angel checkpoint" logic
        if (isZeroDegree(gcd[0])) {
            blockHit();
            maintainDistance(REACH);
        } else if (isTwentyFiveDegree(gcd[0])) {
            blockHit();
            if (isZeroDegree(gcd[0])) {
                gcd[0] = 45; // Move to 45 degrees
            }
        }

        e.setYaw(gcd[0]);
        e.setPitch(gcd[1]);

        mc.thePlayer.renderYawOffset = gcd[0];
        mc.thePlayer.rotationYawHead = gcd[0];
        fixedYaw = gcd[0];
        fixedPitch = gcd[1];
        prevYaw = e.getYaw();
        prevPitch = e.getPitch();
    }

    private void blockHit() {
        if (holdingRightButton && (System.currentTimeMillis() - blockHitStartTime) >= BLOCK_HIT_DURATION) {
            mc.rightClickMouse();
            blockHitStartTime = System.currentTimeMillis();
        }
    }

    private void maintainDistance(double reach) {
    }

    @Subscribe
    public void onJumpFix(JumpEvent event) {
        event.setYaw(yaw);
    }

    @Subscribe
    public void move(MoveInputEvent e) {
        if (!fixMovement.isToggled() || locked) return;
        e.setYaw(yaw);
    }

    @Subscribe
    public void lookEvent(LookEvent e) {
        if (locked) return;
        e.setPrevYaw(prevYaw);
        e.setPrevPitch(prevPitch);
        e.setYaw(yaw);
        e.setPitch(pitch);
    }


    /**
     * Visuals
     */
    @Subscribe
    public void renderWorldLast(ForgeEvent fe) {
        if (!visuals.isToggled()) return;
        if((fe.getEvent() instanceof RenderWorldLastEvent) && (target != null)) {
            try { //@reason fix nullpointers
                int red = (int) (((20 - target.getHealth()) * 13) > 255 ? 255 : (20 - target.getHealth()) * 13);
                int green = 255 - red;
                final int rgb = new Color(0, green, 0).getRGB(); // Set the color to green
                Utils.HUD.drawBoxAroundEntity(target, 2, 0, 0, rgb, false);
                for (EntityPlayer p : pTargets)
                    Utils.HUD.drawBoxAroundEntity(p, 2, 0, 0, 0x800000FF, false);
            } catch (Exception e){}
        }
    }

    /**
     * Misc Stuff. Utils are below
     */

    public static EntityPlayer getTraget(){
        return target;
    }
    public void rotate(float yaw, float pitch, boolean e) {
        this.yaw = yaw;
        this.pitch = pitch;
    }
    private void block() {
        this.sendUseItem(KillAura.mc.thePlayer, KillAura.mc.theWorld, KillAura.mc.thePlayer.getCurrentEquippedItem());
        KillAura.mc.gameSettings.keyBindUseItem.pressed = true;
        KillAura.mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, KillAura.mc.thePlayer.getHeldItem(), 0.0f, 0.0f, 0.0f));
        this.blocking = true;
    }

    private void unblock() {
        if (this.blocking) {
            KillAura.mc.gameSettings.keyBindUseItem.pressed = false;
            mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            this.blocking = false;
        }
    }

    public void sendUseItem(EntityPlayer playerIn, World worldIn, ItemStack itemStackIn) {
        if (mc.playerController.getCurrentGameType() != WorldSettings.GameType.SPECTATOR) {
            int i = itemStackIn.stackSize;
            ItemStack itemstack = itemStackIn.useItemRightClick(worldIn, playerIn);
            if (itemstack != itemStackIn || itemstack.stackSize != i) {
                playerIn.inventory.mainInventory[playerIn.inventory.currentItem] = itemstack;
                if (itemstack.stackSize == 0) {
                    playerIn.inventory.mainInventory[playerIn.inventory.currentItem] = null;
                }
            }
        }
    }
    private double Sens() {
        final float sens = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        final float pow = sens * sens * sens * 8.0F;
        return pow * 0.15D;
    }

    private float[] getGCDRotations(final float[] currentRots, final float[] prevRots) {
        final float yawDif = currentRots[0] - prevRots[0];
        final float pitchDif = currentRots[1] - prevRots[1];
        final double gcd = Sens();

        currentRots[0] -= yawDif % gcd;
        currentRots[1] -= pitchDif % gcd;
        return currentRots;
    }
    private float maxAngleChange(final float prev, final float now, final float maxTurn) {
        float dif = MathHelper.wrapAngleTo180_float(now - prev);
        if (dif > maxTurn) dif = maxTurn;
        if (dif < -maxTurn) dif = -maxTurn;
        return prev + dif;
    }
    @Override
    public void onEnable() {
        super.onEnable();
        this.updateVals();
    }

    public void guiUpdate() {
        rps.hideComponent(customRPS.isToggled());
    }
    private void updateVals() {
        stopClicker = false;
        min = aps.getInputMin();
        max = aps.getInputMax();

        if (min >= max) {
            max = min + 1;
        }

        speed = 1.0 / ThreadLocalRandom.current().nextDouble(min - 0.2, max);
        holdLength = speed / ThreadLocalRandom.current().nextDouble(min, max);
    }
    private void syncClicker(){
        double min = aps.getInputMin();
        double max = aps.getInputMax();

        if (min > max) {
            min = max;
        }
        if (min == max) cps = min;
        else cps = RandomUtils.nextDouble(min, max);
    }

    public void onDisable(){
        target = null;
        this.unblock();
    }

    public enum BlockMode {
        NONE,
        Legit,
        Vanilla,
        Damage,
        Fake;
    }
}
