package com.fateflip.platform;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

import java.lang.reflect.Field;

/**
 * Minecraft 26.x : les entrées de déplacement sont un enregistrement immuable (Input) et un vecteur.
 * Les cartes travaillent sur cette copie modifiable, aux champs nommés comme l'ancien
 * net.minecraft.client.player.Input, puis le résultat est réécrit dans le ClientInput.
 */
public final class MoveInput {

    public boolean up, down, left, right, jumping, shiftKeyDown, sprinting;
    public float forwardImpulse, leftImpulse;

    private static Field moveVector;

    public static MoveInput from(ClientInput in) {
        MoveInput m = new MoveInput();
        Input k = in.keyPresses;
        m.up = k.forward();
        m.down = k.backward();
        m.left = k.left();
        m.right = k.right();
        m.jumping = k.jump();
        m.shiftKeyDown = k.shift();
        m.sprinting = k.sprint();
        Vec2 v = in.getMoveVector();
        m.leftImpulse = v.x;
        m.forwardImpulse = v.y;
        return m;
    }

    public void applyTo(ClientInput in) {
        in.keyPresses = new Input(up, down, left, right, jumping, shiftKeyDown, sprinting);
        try {
            if (moveVector == null) moveVector = Compat.field(ClientInput.class, "moveVector", null);
            moveVector.set(in, new Vec2(leftImpulse, forwardImpulse));
        } catch (Exception ignored) {
            // sans réflexion, seules les touches sont modifiées
        }
    }
}
