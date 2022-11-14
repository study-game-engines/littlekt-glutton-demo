package com.lehaine.game.node.entity

import com.lehaine.game.Assets
import com.lehaine.game.Config
import com.lehaine.game.GameInput
import com.lehaine.game.data.ExpTable
import com.lehaine.game.node.controller
import com.lehaine.game.node.entity.mob.Effect
import com.lehaine.game.node.entity.mob.Mob
import com.lehaine.game.node.fx
import com.lehaine.game.node.game
import com.lehaine.littlekt.graph.node.Node
import com.lehaine.littlekt.graph.node.addTo
import com.lehaine.littlekt.graph.node.annotation.SceneGraphDslMarker
import com.lehaine.littlekt.graph.node.node
import com.lehaine.littlekt.graph.node.node2d.Node2D
import com.lehaine.littlekt.graphics.tilemap.ldtk.LDtkEntity
import com.lehaine.littlekt.math.geom.Angle
import com.lehaine.littlekt.math.geom.cosine
import com.lehaine.littlekt.math.geom.degrees
import com.lehaine.littlekt.math.geom.sine
import com.lehaine.littlekt.math.isFuzzyZero
import com.lehaine.littlekt.util.datastructure.Pool
import com.lehaine.littlekt.util.fastForEach
import com.lehaine.littlekt.util.signal
import com.lehaine.rune.engine.GameLevel
import com.lehaine.rune.engine.node.EntityCamera2D
import com.lehaine.rune.engine.node.renderable.animatedSprite
import com.lehaine.rune.engine.node.renderable.entity.ObliqueEntity
import com.lehaine.rune.engine.node.renderable.entity.castRayTo
import com.lehaine.rune.engine.node.renderable.entity.cd
import com.lehaine.rune.engine.node.renderable.entity.toGridPosition
import com.lehaine.rune.engine.node.renderable.sprite
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun Node.hero(
    data: LDtkEntity,
    level: GameLevel<*>,
    camera: EntityCamera2D,
    projectiles: Node2D,
    callback: @SceneGraphDslMarker Hero.() -> Unit = {}
) = node(Hero(data, level, camera, projectiles), callback)


/**
 * @author Colton Daily
 * @date 11/2/2022
 */
class Hero(data: LDtkEntity, level: GameLevel<*>, val camera: EntityCamera2D, projectiles: Node2D) :
    ObliqueEntity(level, Config.GRID_CELL_SIZE.toFloat()), Effectible {

    val onDeath = signal()
    val damage = 5
    var health = 4

    private val orbProjectilePool: Pool<OrbProjectile> by lazy {
        Pool(3) {
            OrbProjectile(this, level).apply { enabled = false }.addTo(projectiles)
        }
    }
    private val swipeProjectilePool: Pool<SwipeProjectile> by lazy {
        Pool(2) {
            SwipeProjectile(this).apply { enabled = false }.addTo(projectiles)
        }
    }

    private val boneSpearProjectile: Pool<BoneSpearProjectile> by lazy {
        Pool(1) {
            BoneSpearProjectile(this).apply { enabled = false }.addTo(projectiles)
        }
    }
    private val canMove = data.field<Boolean>("canMove").value

    override val effects: MutableMap<Effect, Duration> = mutableMapOf()
    override val effectsToRemove: MutableList<Effect> = mutableListOf()

    private val mobsTemp = mutableListOf<Mob>()

    private var speed = 0.03f
    private var speedMultiplier = 1f

    private var xMoveStrength = 0f
    private var yMoveStrength = 0f

    private val levelUp = animatedSprite {
        anchorX = 0.5f
        anchorY = 1f

        onFrameChanged += {
            if (it % 4 == 0) {
                fx.levelUp(centerX, attachY)
            }
        }
    }.also { sendChildToTop(it) }

    private val shadow = sprite {
        name = "Shadow"
        slice = Assets.atlas.getByPrefix("shadow").slice
        x -= Config.GRID_CELL_SIZE * data.pivotX
        y -= Config.GRID_CELL_SIZE * data.pivotY - 2f
    }.also { sendChildToTop(it) }


    init {
        anchorX = data.pivotX
        anchorY = data.pivotY
        toGridPosition(data.cx, data.cy)
        sprite.apply {
            registerState(Assets.heroAir, 110) { health <= 0 && !velocityZ.isFuzzyZero(0.05f) }
            registerState(Assets.heroDash, 15) { cd.has("dash") }
            registerState(Assets.heroWalk, 5) {
                !velocityX.isFuzzyZero(0.05f) || !velocityY.isFuzzyZero(
                    0.05f
                )
            }
            registerState(Assets.heroIdle, 0)
        }

        if (!canMove) {
            addEffect(Effect.Invincible, Duration.INFINITE)
        }
    }

    override fun update(dt: Duration) {
        super.update(dt)
        shadow.globalY = (cy + yr) * Config.GRID_CELL_SIZE - Config.GRID_CELL_SIZE + 2

        updateEffects(dt)

        if (cd.has("hit")) {
            val hitRatio = 1f - cd.ratio("hit")
            sprite.color.r = 1f
            sprite.color.g = hitRatio
            sprite.color.b = hitRatio
        }

        if (cd.has("dash") || !canMove) return

        xMoveStrength = 0f
        yMoveStrength = 0f

        if (health <= 0) return
        dir = dirToMouse

        if (controller.down(GameInput.SWING)) {
            attemptSwipeAttack()
        }

        if (controller.down(GameInput.SHOOT)) {
            attemptOrbAttack()
        }

        if (!controller.down(GameInput.SWING) && !controller.down(GameInput.SHOOT) && !hasEffect(Effect.Stun)) {
            val movement = controller.vector(GameInput.MOVEMENT)
            xMoveStrength = movement.x
            yMoveStrength = movement.y
        }

        if (controller.pressed(GameInput.DASH)) {
            attemptDash()
        }
        if (controller.pressed(GameInput.HAND_OF_DEATH)) {
            attemptHandOfDeath()
        }
        if (controller.pressed(GameInput.BONE_SPEAR)) {
            attemptBoneSpearAttack()
        }
    }

    fun attemptSwipeAttack() {
        if (!cd.has("swipeCD")) {
            cd("swipeCD", (750.1100).milliseconds)
            swipeAttack()
        }
    }

    fun attemptOrbAttack() {
        if (!game.state.shootingUnlocked) return

        if (!cd.has("shootCD")) {
            cd("shootCD", 3.seconds)
            orbAttack()
            camera.shake(100.milliseconds, 0.5f * Config.cameraShakeMultiplier)
        }
    }

    fun attemptDash() {
        if (!game.state.dashUnlocked) return

        if (!cd.has("dashCD") && !cd.has("dash")) {
            val angle = angleToMouse
            xMoveStrength = angle.cosine
            yMoveStrength = angle.sine
            speedMultiplier = 5f
            sprite.color.a = 0.5f
            scaleX = 1.25f
            scaleY = 0.9f
            camera.shake(50.milliseconds, 0.5f * Config.cameraShakeMultiplier)
            cd("dashCD", 1.seconds)
            addEffect(Effect.Invincible, 350.milliseconds)
            cd("dash", 250.milliseconds) {
                speedMultiplier = 1f
                scaleX = 1f
                scaleY = 1f
                shadow.globalY // forces to update global position if its dirty just by getting
            }
        }
    }

    fun attemptHandOfDeath() {
        if (!game.state.handOfDeathUnlocked) return

        if (!cd.has("handOfDeathCD")) {
            cd("handOfDeathCD", 30.seconds)
            performHandOfDeath()
        }
    }

    fun attemptBoneSpearAttack() {
        if (!game.state.boneSpearUnlocked) return

        if (!cd.has("boneSpearCD")) {
            val tcx = (mouseX / Config.GRID_CELL_SIZE).toInt()
            val tcy = (mouseY / Config.GRID_CELL_SIZE).toInt()
            if (castRayTo(tcx, tcy) { cx, cy -> !level.hasCollision(cx, cy) }) {
                cd("boneSpearCD", 15.seconds)
                boneSpearAttack(mouseX, mouseY)
            }
        }
    }

    override fun onEffectStart(effect: Effect) {
        super.onEffectStart(effect)
        if (effect == Effect.Invincible) {
            sprite.color.a = 0.5f
        }
    }

    override fun onEffectEnd(effect: Effect) {
        super.onEffectEnd(effect)
        if (effect == Effect.Invincible) {
            sprite.color.a = 1f
        }
    }

    override fun fixedUpdate() {
        super.fixedUpdate()
        velocityX += speed * speedMultiplier * xMoveStrength
        velocityY += speed * speedMultiplier * yMoveStrength
    }

    fun hit(from: Angle) {
        if (hasEffect(Effect.Invincible) || health <= 0) return
        health--
        addEffect(Effect.Invincible, 2.seconds)
        velocityX += 0.25f * from.cosine
        velocityY += 0.25f * from.sine
        velocityZ += 0.25f
        sprite.color.r = 1f
        sprite.color.g = 0f
        sprite.color.b = 0f
        stretchY = 1.25f
        cd.timeout("hit", 250.milliseconds)
    }

    private fun orbAttack() {
        val straightAngle = angleToMouse

        val straightProjectile = orbProjectilePool.alloc()
        straightProjectile.enabled = true
        straightProjectile.globalPosition(centerX + 10f * straightAngle.cosine, centerY + 10f * straightAngle.sine)
        straightProjectile.moveTowardsAngle(straightAngle)

        repeat(2) {
            val sideAngle = angleToMouse + 20.degrees * if (it % 2 == 0) 1 else -1
            val sideProjectile = orbProjectilePool.alloc()
            sideProjectile.enabled = true
            sideProjectile.globalPosition(centerX + 10f * sideAngle.cosine, centerY + 10f * sideAngle.sine)
            sideProjectile.moveTowardsAngle(sideAngle)
        }
    }

    private fun swipeAttack() {
        cd("delay", 200.milliseconds) {
            val projectile = swipeProjectilePool.alloc()
            val offset = 20f

            val angle = angleToMouse
            projectile.sprite.flipY = dir == -1
            projectile.rotation = angle
            projectile.globalPosition(globalX + offset * angle.cosine, globalY + offset * angle.sine)
            projectile.enabled = true
        }
        sprite.playOnce(Assets.heroSwing)
        addEffect(Effect.Stun, 300.milliseconds)
    }

    private fun boneSpearAttack(tx: Float, ty: Float) {
        val projectile = boneSpearProjectile.alloc()
        projectile.globalX = tx
        projectile.globalY = ty
        projectile.enabled = true
    }


    private fun performHandOfDeath() {
        if (Mob.ALL.isEmpty()) return

        repeat(5) {
            var mob = Mob.ALL.random()
            while (mobsTemp.contains(mob)) {
                mob = Mob.ALL.random()
            }
            mobsTemp += mob
        }

        mobsTemp.fastForEach {
            it.handleHandOfDeath()
        }
        mobsTemp.clear()
    }


    fun projectileFinished(projectile: Projectile) {
        when (projectile) {
            is SwipeProjectile -> swipeProjectilePool.free(projectile)
            is BoneSpearProjectile -> boneSpearProjectile.free(projectile)
        }
    }

    override fun onLand() {
        super.onLand()
        if (health <= 0) {
            velocityZ = 0f
            sprite.playOnce(Assets.heroDie)
            sprite.play(Assets.heroDead, 5.seconds, true)
            cd("die", 1.seconds) {
                onDeath.emit()
            }
        }
    }

    override fun isEffectible(): Boolean = health > 0

    fun levelUp() {
        levelUp.playOnce(Assets.levelUp)
        fx.levelUp(centerX, attachY)
    }
}