package keystrokesmod.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DoubleSliderSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.CoolDown;
import keystrokesmod.client.utils.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.lwjgl.input.Keyboard;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class WTap extends Module {
    public ComboSetting eventType;
    public SliderSetting range, chance, tapMultiplier;
    public TickSetting onlyPlayers;
    public TickSetting onlySword;
    public TickSetting dynamic;
    public DoubleSliderSetting waitMs;
    public DoubleSliderSetting actionMs;
    public DoubleSliderSetting hitPer;
    public int hits, rhit;
    public TickSetting autoCombo;
    public boolean call, p;
    public long s;

    private boolean firstHit = false;
    private WtapState state = WtapState.NONE;
    private final CoolDown timer = new CoolDown(0);
    private Entity target;
    private double enemyGroundY;


    public Random r = new Random();

    public WTap() {
        super("WTap", ModuleCategory.combat);

        this.registerSetting(eventType = new ComboSetting("Event:", EventType.Attack));
        this.registerSetting(autoCombo = new TickSetting("Auto Combo", false));

        this.registerSetting(dynamic = new TickSetting("Dynamic tap time", false));
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (state == WtapState.NONE)
            return;
        if (state == WtapState.WAITINGTOTAP && timer.hasFinished()) {
            startCombo();
        } else if (state == WtapState.TAPPING && timer.hasFinished()) {
            finishCombo();
        }

        // Check enemy position for hit algorithm
        if (autoCombo.isToggled() && target != null && state != WtapState.NONE) {
            double targetPosY = target.posY - target.getYOffset();
            enemyGroundY = targetPosY - 2; // Set enemyGroundY 2 pixels below the ground

            //hit algorithm
            if (mc.thePlayer.posY >= enemyGroundY && mc.thePlayer.posY <= enemyGroundY + 0.2) {
                performHit();
            }
        }
    }


    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (!autoCombo.isToggled())
            return;

        if (fe.getEvent() instanceof AttackEntityEvent) {
            AttackEntityEvent e = (AttackEntityEvent) fe.getEvent();

            target = e.target;

            if (isSecondCall() && eventType.getMode() == EventType.Attack) {
                if (!firstHit) {
                    firstHit = true;
                    startCombo();
                } else {
                    wTap();
                }
            }
        } else if (fe.getEvent() instanceof LivingUpdateEvent) {
            LivingUpdateEvent e = (LivingUpdateEvent) fe.getEvent();

            if (eventType.getMode() == EventType.Hurt && e.entityLiving.hurtTime > 0
                    && e.entityLiving.hurtTime == e.entityLiving.maxHurtTime && e.entity == this.target) {
                if (!firstHit) {
                    firstHit = true;
                    startCombo();
                } else {
                    wTap();
                }
            }
        }
    }


    public void wTap() {
        if (!autoCombo.isToggled() || state != WtapState.NONE)
            return;

        //enemy position for combo
        if (target != null) {
            double targetPosY = target.posY - target.getYOffset();
            enemyGroundY = targetPosY - 2; // Set enemyGroundY 2 pixels below the ground

            if (mc.thePlayer.posY >= enemyGroundY && mc.thePlayer.posY <= enemyGroundY + 0.2) {
                trystartCombo();
            }
        }
    }


    public void finishCombo() {
        if (Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        }
        state = WtapState.NONE;
        hits = 0;
        int easports = (int) (hitPer.getInputMax() - hitPer.getInputMin() + 1);
        rhit = ThreadLocalRandom.current().nextInt((easports));
        rhit += (int) hitPer.getInputMin();
        firstHit = false;
    }

    public void startCombo() {
        state = WtapState.TAPPING;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        double cd = ThreadLocalRandom.current().nextDouble(waitMs.getInputMin(), waitMs.getInputMax() + 0.01);
        enemyGroundY = 0;
        if (dynamic.isToggled()) {
            if (target != null) {
                cd = 3 - mc.thePlayer.getDistanceToEntity(target) < 3
                        ? (cd + (3 - mc.thePlayer.getDistanceToEntity(target) * tapMultiplier.getInput() * 10))
                        : cd;
            }
        }

        timer.setCooldown((long) cd);
        timer.start();
    }

    public void trystartCombo() {
        state = WtapState.WAITINGTOTAP;
        timer.setCooldown(
                (long) ThreadLocalRandom.current().nextDouble(actionMs.getInputMin(), actionMs.getInputMax() + 0.01));
        timer.start();
    }

    private void performHit() {
        //hit here
        mc.thePlayer.swingItem();

        // Reset the timer
        double cd = ThreadLocalRandom.current().nextDouble(waitMs.getInputMin(), waitMs.getInputMax() + 0.01);
        timer.setCooldown((long) cd);
        timer.start();
    }


    private boolean isSecondCall() {
        if (call) {
            call = false;
            return true;
        } else {
            call = true;
            return false;
        }
    }

    public enum EventType {
        Attack, Hurt,
    }

    public enum WtapState {
        NONE, WAITINGTOTAP, TAPPING
    }

}