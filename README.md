# CS2 Bhop

Replaces Minecraft's player movement with Counter-Strike 2's, so you can bunny hop.

Fabric, Minecraft **26.1.2**, client-side only. No server install needed.

`build/libs/cs2bhop-1.0.0.jar`

## Install

1. Create a **Fabric 26.1.2** instance (the one you were setting up in Modrinth App).
2. Put **Fabric API 0.155.2+26.1.2** in its `mods` folder.
3. Put `cs2bhop-1.0.0.jar` next to it.

## Controls

| Key | Does |
| --- | --- |
| **B** | Toggle CS2 movement |
| **N** | Toggle autobhop |
| *(unbound)* | Toggle the speedometer |

Hold **space** and steer with **A**/**D** plus the mouse. That is the whole game.

## How to actually bhop

Hold space. On each jump, hold *only* the strafe key in the direction you are turning, and turn the
mouse smoothly that way — `A` while turning left, `D` while turning right. Do not hold `W`.

The speedometer shows units per second and, mid-air, how much you have gained since takeoff. Green
is good. If it is red you are turning too fast or too slow for your current speed — the faster you
go, the slower you have to turn.

## Why it works

Source's air acceleration has an asymmetry that CS players have been exploiting for 25 years, and
it is reproduced faithfully in [`SourcePhysics`](src/main/java/com/santi/cs2bhop/physics/SourcePhysics.java):

```java
double clampedWishSpeed = Math.min(wishSpeed, airMaxWishSpeed);   // 30 u/s
double addSpeed = clampedWishSpeed - (x * wishX + z * wishZ);
if (addSpeed <= 0.0) return;
double accelSpeed = Math.min(airAccel * wishSpeed * dt, addSpeed); // unclamped 250 u/s
```

`addSpeed` is measured against a wish speed clamped to 30 u/s, but `accelSpeed` uses the full 250.
The clamp therefore limits how much velocity you may add *along the direction you are already
moving*, and not at all how much you may add perpendicular to it. Point your wish direction at a
right angle to your velocity and you get the full 30 u/s every tick, almost none of which is wasted
opposing your existing motion.

Writing `u = |v|·cos θ` for the component of velocity along the wish direction, the new speed after
one tick is

```
|v'|² = |v|² + 2u(30 − u) + (30 − u)² = |v|² + 900 − u²
```

maximal at `u = 0` — exactly perpendicular. So a perfect strafe adds **exactly 900 to v² per tick**,
regardless of how fast you are already going. Speed grows as a square root: quick at first, then
grinding. Remove the 30 u/s clamp and `accelSpeed` stops being the binding constraint, air control
becomes unbounded, and the whole thing collapses into a boring runaway.

Simulating the port with a perfect strafe, 60 Hz, autobhop:

```
jump |  takeoff  |   landing  |   gain
-----+-----------+------------+---------
   1 |   228.3   |    304.4   |   +76.0
   2 |   278.0   |    343.2   |   +65.2
   4 |   340.2   |    395.3   |   +55.1
   8 |   401.4   |    449.0   |   +47.6
```

The drop between each landing and the next takeoff is one tick of ground friction, which costs about
8.7%. Land flat for a whole Minecraft tick instead and you lose **24%**. That is the entire reason
bunny hopping is worth doing.

## Two decisions that are not faithful ports, on purpose

**Substepping.** Minecraft ticks at 20 Hz, CS2 servers at 64. Because air acceleration is quantised
per tick by the 30 u/s clamp, running the model at 20 Hz would give you roughly a third of the
strafe gain per second and bhopping would feel dead. So velocity is integrated in 3 substeps per
tick (60 Hz, `subticks` in the config) while collision still runs once per tick, through Minecraft's
own `move()`. Jump airtime lands at 0.75 s ≈ 45 substeps, against CS2's ~46 ticks.

**Unit scale.** `unitsToBlocks` defaults to **0.025**, which maps a 72-unit CS player onto
Minecraft's 1.8-block player. That puts run speed at 6.25 blocks/s and jump height at ~1.4 blocks,
so you still clear a single block like you expect. The physically correct conversion (1 unit =
0.75 inch = 0.01905) gives a 1.04-block jump and you can no longer step onto a block, which breaks
Minecraft as a game. Set it to `0.01905` if you want true CS2 scale and are willing to live with
that.

## Config

`config/cs2bhop.json`, written on first launch. Field names match the Source cvar where one exists.

Defaults are a **bhop/KZ server**: autobhop on, no speed cap, no stamina, unlimited gain.

For **stock CS2 matchmaking movement** — timed jumps, and the stamina system that makes the fourth
hop in a row barely leave the ground:

```json
{
  "autoBunnyHopping": false,
  "bunnyHopSpeedCap": true,
  "stamina": true
}
```

Other knobs: `sv_maxspeed`, `sv_accelerate`, `sv_airaccelerate`, `sv_friction`, `sv_stopspeed`,
`sv_air_max_wishspeed`, `sv_gravity`, `sv_jump_impulse`, `duckSpeedMultiplier`, `subticks`,
`unitsToBlocks`, `sourceGravity`, `hud`, `enabled`.

CS2 has no sprint, so **the sprint key is ignored** — you always move at `sv_maxspeed` unless
crouching (`duckSpeedMultiplier`, 0.34).

## Where it does not apply

Falls back to vanilla movement for swimming, water and lava, ladders, elytra, creative flight,
riding anything, and spectator. Everything else — collision, step-up, fall damage, block friction
being irrelevant now — goes through Minecraft's normal `move()`.

## Multiplayer

This is client-side because Minecraft movement is client-authoritative: the server accepts whatever
position you report as long as it is under ~100 m² per tick, and bhop speeds are two orders of
magnitude below that. Nothing here weakens or bypasses a server's movement validation, and there is
deliberately no mixin that touches it.

It will still plainly look like a movement advantage to anyone watching, and anti-cheat on public
servers will treat it as one. Intended for singleplayer and servers where everyone is in on it.

## Building

Needs JDK 25. A project-local Temurin 25 lives in `tools/` (gitignored) if you want it:

```bash
JAVA_HOME=tools/jdk-25.0.4+7 ./gradlew build
```
