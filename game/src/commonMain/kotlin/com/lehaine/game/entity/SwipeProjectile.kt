package com.lehaine.game.entity

import com.lehaine.game.Assets
import com.lehaine.game.Config
import com.lehaine.littlekt.math.distSqr
import com.lehaine.littlekt.math.geom.cosine
import com.lehaine.littlekt.math.geom.sine
import com.lehaine.littlekt.util.fastForEach
import com.lehaine.rune.engine.node.renderable.entity.Entity
import com.lehaine.rune.engine.node.renderable.entity.angleTo
import kotlin.time.Duration

/**
 * @author Colton Daily
 * @date 11/4/2022
 */
class SwipeProjectile(val hero: Hero) : Entity(Config.GRID_CELL_SIZE.toFloat()), Projectile {
    private var swiped = false

    var timer = Duration.ZERO

    val knockbackPower = 0.1f

    init {
        anchorX = 0.5f
        anchorY = 0.5f
        width = 64f
        height = 64f
    }

    override fun update(dt: Duration) {
        super.update(dt)
        if (!swiped) {
            sprite.playOnce(Assets.swipeAttack1)
            timer = Assets.swipeAttack1.duration
            swiped = true
            Mob.ALL.fastForEach {
                val dist = outerRadius + it.outerRadius
                val colliding = distSqr(px, py, it.px, it.py) <= dist * dist
                if (it.enabled && colliding) {
                    it.hit(hero.damange, hero.angleTo(it))

                    val angle = hero.angleTo(it)
                    it.velocityX += knockbackPower * angle.cosine
                    it.velocityY += knockbackPower * angle.sine
                }
            }
        }
        if(swiped) {
            timer -= dt
        }
        if (timer <= Duration.ZERO && swiped) {
            swiped = false
            enabled = false
            hero.projectileFinished(this)
        }
    }
}