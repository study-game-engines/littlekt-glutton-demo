package com.lehaine.game.node.entity.mob

import com.lehaine.game.Assets
import com.lehaine.game.Level
import com.lehaine.game.node.entity.Hero
import com.lehaine.littlekt.graph.node.Node
import com.lehaine.littlekt.graph.node.addTo
import com.lehaine.littlekt.graph.node.annotation.SceneGraphDslMarker
import com.lehaine.littlekt.math.geom.cosine
import com.lehaine.littlekt.math.geom.sine
import com.lehaine.rune.engine.node.renderable.entity.angleTo
import com.lehaine.rune.engine.node.renderable.entity.cd
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.sign
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalContracts::class)
fun Node.meatBall(
    hero: Hero,
    level: Level,
    callback: @SceneGraphDslMarker MeatBall.() -> Unit = {}
): MeatBall {
    contract { callsInPlace(callback, InvocationKind.EXACTLY_ONCE) }
    return MeatBall(hero, level).also(callback).addTo(this)
}

/**
 * @author Colton Daily
 * @date 11/4/2022
 */
class MeatBall(hero: Hero, level: Level) : Mob(hero, level) {

    private var xDir = 0f
    private var yDir = 0f


    init {
        sprite.apply {
            registerState(Assets.meatBallSit, 5) { cd.has("sit") }
            registerState(Assets.meatBallRun, 0)
        }
    }

    override fun onSpawn() {
        super.onSpawn()
        cd("sit", (1000..2000).random().milliseconds) {
            cd("stand", Assets.meatBallStandUp.duration)
            sprite.playOnce(Assets.meatBallStandUp)
        }
    }

    override fun update(dt: Duration) {
        super.update(dt)
        if (cd.has("stand") || cd.has("sit")) {
            return
        }

        val angle = angleTo(hero)
        xDir = angle.cosine
        yDir = angle.sine

        dir = xDir.sign.toInt()
    }

    override fun fixedUpdate() {
        super.fixedUpdate()
        if (cd.has("stand") || cd.has("sit")) return

        velocityX += speed * speedMul * xDir
        velocityY += speed * speedMul * yDir
    }
}