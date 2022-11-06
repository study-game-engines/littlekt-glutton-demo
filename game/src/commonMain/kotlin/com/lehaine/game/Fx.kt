package com.lehaine.game

import com.lehaine.game.scene.GameScene
import com.lehaine.littlekt.graph.node.render.BlendMode
import com.lehaine.littlekt.graph.node.render.Material
import com.lehaine.littlekt.graphics.Color
import com.lehaine.littlekt.graphics.Particle
import com.lehaine.littlekt.graphics.ParticleSimulator
import com.lehaine.littlekt.graphics.TextureSlice
import com.lehaine.littlekt.math.PI2_F
import com.lehaine.littlekt.math.random
import com.lehaine.littlekt.util.seconds
import com.lehaine.rune.engine.node.renderable.ParticleBatch
import kotlin.math.sign
import kotlin.random.Random
import kotlin.time.Duration


class Fx(val game: GameScene) {
    private val particleSimulator = ParticleSimulator(2048)

    private var bgAdd = ParticleBatch().apply {
        material = Material().apply {
            blendMode = BlendMode.Add
        }
    }
    private var bgNormal = ParticleBatch()
    private var topAdd = ParticleBatch().apply {
        material = Material().apply {
            blendMode = BlendMode.Add
        }
    }
    private var topNormal = ParticleBatch()

    fun createParticleBatchNodes() {
        bgAdd = ParticleBatch().apply {
            material = Material().apply {
                blendMode = BlendMode.Add
            }
        }
        bgNormal = ParticleBatch()
        topAdd = ParticleBatch().apply {
            material = Material().apply {
                blendMode = BlendMode.Add
            }
        }
        topNormal = ParticleBatch()
        game.fxBackground.apply {
            addChild(bgAdd)
            addChild(bgNormal)
        }
        game.fxForeground.apply {
            addChild(topNormal)
            addChild(topAdd)
        }
    }


    fun update(dt: Duration, tmod: Float = -1f) {
        particleSimulator.update(dt, tmod)
    }

    fun shadowSmall(x: Float, y: Float, duration: Duration) {
        create(1) {
            val shadowSlice = Assets.atlas.getByPrefix("shadowSmall").slice
            val p = allocBotNormal(shadowSlice, x - shadowSlice.width * 0.5f, y - shadowSlice.height * 0.5f)
            p.life = duration
        }
    }

    fun meatBallExplode(x: Float, y: Float) {
        fun setParticle(p: Particle) {
            p.xDelta = (0..2).random().asRandomSign
            p.yDelta = (1..2).random().asRandomSign
            p.gravityY = 0.1f.about()
            p.friction = 0.94f.about(0.05f).coerceAtMost(1f)
            p.rotationDelta = (0f..PI2_F).random()
            p.data0 = y + (0..12).random().toInt()
            p.alpha = (0.7f..1f).random()
            p.life = (1..2).random().seconds
            p.onUpdate = ::bloodPhysics
        }
        create(1) {
            val p = allocTopNormal(Assets.atlas.getByPrefix("fxBigEye").slice, x, y)
            setParticle(p)
        }
        create(1) {
            val p = allocTopNormal(Assets.atlas.getByPrefix("fxLittleEye").slice, x, y)
            setParticle(p)
        }
        create(2) {
            val p = allocTopNormal(Assets.atlas.getByPrefix("fxMeatLeg").slice, x, y)
            setParticle(p)
        }
        create(50) {
            val idx = Random.nextInt(3)
            val p = allocTopNormal(Assets.atlas.getByPrefix("fxGib$idx").slice, x, y)
            p.color.set(MEAT_RED)
            setParticle(p)
        }
    }

    private fun bloodPhysics(particle: Particle) {
        if (particle.isColliding() && particle.data0 != 1f) {
            particle.data0 = 1f
            particle.xDelta *= 0.4f
            particle.yDelta = 0f
            particle.gravityY = (0f..0.001f).random()
            particle.friction = (0.5f..0.7f).random()
            particle.scaleDeltaY = (0f..0.001f).random()
            particle.rotationDelta = 0f
            if (particle.isColliding(-5) || particle.isColliding(5)) {
                particle.scaleY *= (1f..1.25f).random()
            }
            if (particle.isColliding(offsetY = -5) || particle.isColliding(offsetY = 5)) {
                particle.scaleX *= (1f..1.25f).random()
            }
        }
        if (particle.y >= particle.data0 && particle.yDelta > 0) {
            particle.gravityY = 0f
            particle.yDelta = 0f
        }
    }

    private fun groundPhysics(particle: Particle) {
        if (!particle.isColliding()) {
            if (particle.isColliding(2 * particle.xDelta.sign.toInt())) {
                particle.xDelta = -particle.xDelta * 0.7f
            }
            if (particle.isColliding(offsetY = 2 * particle.yDelta.sign.toInt())) {
                particle.yDelta = -particle.yDelta * 0.7f
            }
        }

        if (particle.isColliding() || particle.y >= particle.data0 && particle.yDelta > 0) {
            particle.data0++
            if (particle.data0 == 1f) {
                particle.gravityY = 0f
                particle.yDelta = 0f
                particle.xDelta *= 0.5f
                particle.rotationDelta = 0f
            } else {
                particle.yDelta = -particle.yDelta
                particle.xDelta *= 0.6f
                particle.rotationDelta *= 0.3f
            }
        }
    }

    private fun allocTopNormal(slice: TextureSlice, x: Float, y: Float) =
        particleSimulator.alloc(slice, x, y).also { topNormal.add(it) }

    private fun allocTopAdd(slice: TextureSlice, x: Float, y: Float) =
        particleSimulator.alloc(slice, x, y).also { topAdd.add(it) }

    private fun allocBotNormal(slice: TextureSlice, x: Float, y: Float) =
        particleSimulator.alloc(slice, x, y).also { bgNormal.add(it) }

    private fun allocBotAdd(slice: TextureSlice, x: Float, y: Float) =
        particleSimulator.alloc(slice, x, y).also { bgAdd.add(it) }

    private fun create(num: Int, createParticle: (index: Int) -> Unit) {
        for (i in 0 until num) {
            createParticle(i)
        }
    }


    private fun Particle.isColliding(offsetX: Int = 0, offsetY: Int = 0) =
        game.level.hasCollision(
            ((x + offsetX) / Config.GRID_CELL_SIZE).toInt(),
            ((y + offsetY) / Config.GRID_CELL_SIZE).toInt()
        )

    private fun Float.about(variance: Float = 0.1f, sign: Boolean = false): Float {
        return this * (1 + (0..(variance * 100).toInt() / 100).random()) * (if (sign) randomSign else 1)
    }

    private fun Int.about(variance: Float = 0.1f, sign: Boolean = false): Float {
        return about(this.toFloat(), sign)
    }

    private val randomSign: Int get() = (0..1).random().toInt() * 2 - 1
    private val Float.asRandomSign: Float get() = if (Random.nextFloat() >= 0.5f) this else -this
    private val Int.asRandomSign: Int get() = if (Random.nextFloat() >= 0.5f) this else -this

    companion object {
        private val DUST_COLOR = Color.fromHex("#efddc0")
        private val MEAT_RED = Color.fromHex("#994551")
    }
}