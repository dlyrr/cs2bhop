# CS2 Bhop

Counter-Strike 2 movement for Minecraft, plus a progression built on top of it.

Fabric, Minecraft **26.1.2**. Needs to be installed on **both client and server** (it adds items, a
biome and server-side scoring).

`build/libs/cs2bhop-1.0.0.jar`

## Install

1. Create a **Fabric 26.1.2** instance.
2. Put **Fabric API 0.155.2+26.1.2** in its `mods` folder.
3. Put `cs2bhop-1.0.0.jar` next to it. Same on the server for multiplayer.

## Controls

| Key | Does |
| --- | --- |
| **space** | Hold it. That's bhopping. |
| **scroll down** | Jump — the `bind mwheeldown +jump` setup |
| **V** | Release your banked hop chain as a shockwave |
| **B** | Toggle CS2 movement |
| **N** | Toggle autobhop |
| *(unbound)* | Toggle the speedometer |

### Scroll to jump

Minecraft cannot bind the wheel to a key — `KeyMapping` takes keyboard keys and mouse buttons, and
the wheel is neither — so scroll is intercepted and turned into a short jump **pulse**: one notch
asks to jump for the next 3 ticks, and touching the ground during that window fires immediately.

That pulse is the whole reason the CS bind works. A notch is instantaneous with no held state, so it
has to cover a few ticks for one of them to land on the tick you touch down — which is the
frame-perfect timing you would otherwise be hitting by hand. Scroll jumping therefore ignores the
press/release rule manual jumping obeys: during a pulse, landing means jumping.

It pairs with `autoBunnyHopping: false`. With autobhop on, holding space already does this and the
wheel is redundant.

Scrolling down no longer cycles the hotbar (scroll up still does). Menus, chat, the inventory and
the spectator fly-speed wheel are untouched. Configure with `scrollJump`, `scrollJumpDirection`
(`"down"`, `"up"`, `"both"`), `scrollJumpPulseTicks` and `scrollJumpBlocksHotbar`.

Hold space, and on each jump hold *only* the strafe key in the direction you are turning while
turning the mouse that way — `A` turning left, `D` turning right. Do not hold `W`.

`/bhop` for your stats, `/bhop top` for the leaderboard.

## Speed

A clean hop keeps everything you had and adds what you strafed for:

```
jump |  takeoff  |   landing  |   gain
-----+-----------+------------+---------
   1 |   250.0   |    320.9   |   +70.9
   2 |   320.9   |    378.8   |   +57.9
   4 |   429.0   |    473.8   |   +44.9
   8 |   588.2   |    621.7   |   +33.5
```

Each takeoff equals the previous landing — a hop costs you nothing. Botch it and stay on the ground
for a full tick and you lose **24%**, which is the entire game.

Gains shrink as you speed up because that is what the maths says: a perfect strafe adds exactly 900
to *v²* per tick, so speed grows as a square root no matter how good you are.

### Why airstrafing works

Source's air acceleration has an asymmetry, reproduced faithfully in
[`SourcePhysics`](src/main/java/com/santi/cs2bhop/physics/SourcePhysics.java):

```java
double clampedWishSpeed = Math.min(wishSpeed, airMaxWishSpeed);   // 30 u/s
double addSpeed = clampedWishSpeed - (x * wishX + z * wishZ);
if (addSpeed <= 0.0) return;
double accelSpeed = Math.min(airAccel * wishSpeed * dt, addSpeed); // unclamped 250 u/s
```

`addSpeed` is measured against a wish speed clamped to 30 u/s, `accelSpeed` uses the full 250. The
clamp limits velocity you add *along* your current motion and not at all perpendicular to it. Point
your wish direction at a right angle to your velocity and you collect the full 30 u/s every tick.

With `u = |v|·cos θ`, one tick gives `|v'|² = |v|² + 900 − u²`, maximal at `u = 0` — exactly
perpendicular. Remove the clamp and air control becomes unbounded and the game stops being fun.

## Levels

Level 1 to 50. Both ends of your speed envelope scale with it:

| Level | Run speed | Ceiling | Points |
| --- | --- | --- | --- |
| 1 | 250 u/s | 700 u/s | 0 |
| 10 | 263 u/s | 939 u/s | 2,691 |
| 30 | 291 u/s | 1469 u/s | 17,496 |
| 50 | 320 u/s | 2000 u/s | 40,495 |

Level 50 is roughly 18,000 hops, about 3.8 hours of clean bhopping.

### What counts as a hop

**Regular jumps do not count.** A jump scores only if *both* hold:

- it is **chained** — you spent at most 4 ticks flat on the ground before taking off. Standing or
  running racks up far more than that, so an ordinary jump never qualifies. This is the condition
  that actually separates hops from jumps.
- you were at **75% of your level's run speed** or better, so jumping on the spot is worth nothing
  no matter how fast you spam it.

**A missed hop costs you the hop, not the chain.** Scoring a hop and ending a chain are separate
questions with separate windows: `hopChainWindow` (4 ticks) decides whether a takeoff scores, and
`chainGraceTicks` (12) decides whether the chain is over. Sharing one number meant a single scuffed
landing — which flat ground produces constantly, since there is no slope to carry you off — wiped a
chain you had spent a minute building. Replaying a flat-ground run with a botched landing every
fourth hop: chain now peaks at **9**, previously it reset to 0 every fourth hop. Standing still still
ends it.

Hops are detected **server-side** from movement, not reported by the client, so points cannot be
spoofed by sending packets.

Detection keys off **vertical motion, not `onGround`**. The obvious implementation — watch for the
tick where `onGround()` flips false — looks right and fails completely: it needs the single grounded
tick to arrive in its own server tick, and client and server ticks are not locked together. A clean
autobhop lands and takes off inside one tick, so the server often sees *no* grounded tick at all and
scores nothing. Reading speed from one tick's position delta has the same flaw: a tick with no
movement packet reads as zero. Replaying the same motion through both:

| Stream | old (`onGround` edge) | current |
| --- | --- | --- |
| grounded tick observed every landing | 5 | 5 |
| grounded tick never observed | **0** | 5 |
| movement packet dropped at takeoff | **0** | 5 |

So takeoff is a rise in vertical motion, and speed is a rolling peak over 4 ticks. The rules live in
[`HopDetector`](src/main/java/com/santi/cs2bhop/progress/HopDetector.java), free of Minecraft types
so they can be simulated rather than only tested by playing.

`/bhop debug` toggles a live action-bar readout of measured speed against the threshold, vertical
motion, flat-tick count, chain length, and why the last takeoff was or was not counted.

Points per hop scale with speed, your boots, and whether you are in the bhop biome.

## Boots

Six pairs. Damage is per **banked hop** — build a chain, then press **V** to release it as a radial
shockwave. Better boots hit harder *and* wider: each tier has its own base radius, growth per hop,
and cap.

| Boots | Damage/hop | Points | Radius (base → cap) | Recipe |
| --- | --- | --- | --- | --- |
| Wooden | 1 | 1.10x | 4 → 10 | Leather boots + rabbit's foot |
| Copper | 2 | 1.25x | 5 → 12 | Copper boots + rabbit's foot |
| Iron | 5 | 1.50x | 6 → 16 | Iron boots + rabbit's foot |
| Diamond | 6 | 1.75x | 7 → 18 | Diamond boots + rabbit's foot |
| Netherite | 8 | 2.00x | 8 → 22 | Netherite boots + rabbit's foot |
| **Phoon** | 10 | 3.00x | 10 → 28 | — |

A 30-hop chain in iron boots is 150 damage across a 16-block radius. The same chain in Phoon Boots
is 300 damage across 28 blocks.

The **Phoon Boots** have no recipe and are not in the creative menu. Chain 50 hops in a row and they
find you. They play the song.

## The bhop biome

**Bhop Flats** — flat, open, a few scattered oaks, and **1.5x points**.

Terrain shape is a property of the dimension's density functions, not of a biome, so no biome can
force the ground flat. What it can do is claim the parameter space where the generator already makes
flat ground — erosion band 6 (`0.55..1.0`), vanilla's flattest inland terrain, at a narrow weirdness
slice so it shows up as occasional wide plains rather than taking over the temperate band. It is
genuinely flat; it is flat because of *where it lives*, not because anything levelled it.

Fabric's biome API only injects into the Nether and the End, so placement is a mixin on
`OverworldBiomeBuilder`.

## Bhopping mobs

About **2% of mobs** know how to bhop. They airstrafe optimally — wish direction exactly
perpendicular to velocity, the thing you are trying to do by hand — so a bhopping zombie closes
distance alarmingly well. They trail crit particles, and cap at 500 u/s, well under a levelled
player.

Membership comes from a hash of the mob's UUID rather than being stored, so it is stable across
saves with no persistence: a mob that bhops always bhopped.

## Motion blur

Speed-scaled, off the same envelope as your level: FOV opens up to ~18% and a soft vignette closes
in from the edges, both easing in quadratically so normal running is completely clean and it only
shows up once you are flying. Capped at 34% opacity.

This is FOV plus a vignette, **not** a post-process blur. Real motion blur means running a shader
chain over the framebuffer, which is heavy and is the part of the renderer most likely to be
rearranged between versions. Set `motionBlur: false` to disable.

## Two deviations from CS2, on purpose

**Substepping.** Minecraft ticks at 20 Hz, CS2 servers at 64. Air acceleration is quantised per tick
by the 30 u/s clamp, so running the model at 20 Hz would give a third of the strafe gain. Velocity
integrates in 3 substeps per tick (60 Hz) while collision still runs once, through Minecraft's own
`move()`. Jump airtime lands at 0.75 s ≈ 45 substeps against CS2's ~46 ticks.

**Unit scale.** `unitsToBlocks` defaults to **0.025**, mapping a 72-unit CS player onto Minecraft's
1.8-block player: run speed 6.25 blocks/s, jump height ~1.4 blocks, so you still clear a single
block. The physically correct 0.01905 (1 unit = 0.75 inch) gives a 1.04-block jump and you can no
longer step onto a block. Set it if you want true scale.

## Config

`config/cs2bhop.json`, written on first launch. Field names match the Source cvar where one exists.

Defaults are a **bhop/KZ server**: autobhop on, clean hops keep all speed, no stamina.

For **stock CS2 matchmaking movement**:

```json
{
  "autoBunnyHopping": false,
  "bunnyHopSpeedCap": true,
  "stamina": true,
  "frictionOnHopTick": true
}
```

`frictionOnHopTick` is the honest-to-Source model where every hop pays one tick of ground friction
(~9%). It is off by default because it taxes every hop and makes speed climb far slower than an
actual autobhop server feels.

Also: `sv_maxspeed`, `sv_accelerate`, `sv_airaccelerate`, `sv_friction`, `sv_stopspeed`,
`sv_air_max_wishspeed`, `sv_gravity`, `sv_jump_impulse`, `duckSpeedMultiplier`, `subticks`,
`unitsToBlocks`, `sourceGravity`, `hopChainWindow`, `hopSpeedFraction`, `pointsPerHop`,
`bhopBiomePointMultiplier`, `phoonUnlockStreak`, `shockwaveCooldownTicks`, `mobBhop`,
`mobBhopChance`, `mobBhopMaxSpeed`, `motionBlur`, `hud`, `enabled`.

CS2 has no sprint, so **the sprint key is ignored** — you always move at your level's run speed
unless crouching.

## Where CS2 movement does not apply

Falls back to vanilla for swimming, water and lava, ladders, elytra, creative flight, riding, and
spectator.

## Multiplayer

Movement is simulated client-side because Minecraft movement is client-authoritative — the server
accepts any position under ~100 m² per tick and bhop speeds are two orders of magnitude below that.
Nothing here weakens or bypasses server movement validation, and there is deliberately no mixin that
touches it. Scoring is server-side and cannot be spoofed.

## The song

`assets/cs2bhop/sounds/phoon.ogg` is **not in this repo** — it is a copyrighted track and this
repository is public. It is in local builds only. To rebuild it:

```bash
ffmpeg -i "The CSGO bhop song! - Nicolas.m4a" -ac 1 -ar 44100 -c:a libvorbis -q:a 3 src/main/resources/assets/cs2bhop/sounds/phoon.ogg
```

Without it the Phoon Boots work fine and log a missing sound.

## Textures

Generated, not hand-drawn — one silhouette, six palettes, so the set stays consistent and a palette
change is one line:

```bash
java tools/TextureGen.java src/main/resources/assets/cs2bhop/textures/item
```

(GeckoLib does not generate textures — it is an animation library, and its 26.1.2 build is NeoForge
only.)

## Building

Needs JDK 25. A project-local Temurin 25 lives in `tools/` (gitignored):

```bash
JAVA_HOME=tools/jdk-25.0.4+7 ./gradlew build
```
