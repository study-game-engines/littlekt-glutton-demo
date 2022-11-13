package com.lehaine.game.scene

import com.lehaine.game.Assets
import com.lehaine.game.Config
import com.lehaine.game.node.ui.fadeMask
import com.lehaine.littlekt.Context
import com.lehaine.littlekt.graph.node.Node
import com.lehaine.littlekt.graph.node.component.AlignMode
import com.lehaine.littlekt.graph.node.component.HAlign
import com.lehaine.littlekt.graph.node.ui.column
import com.lehaine.littlekt.graph.node.ui.label
import com.lehaine.littlekt.util.viewport.ExtendViewport
import com.lehaine.rune.engine.Cooldown
import com.lehaine.rune.engine.RuneSceneDefault
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * @author Colton Daily
 * @date 11/13/2022
 */
class GameOverScene(
    val won: Boolean,
    context: Context
) : RuneSceneDefault(context, ExtendViewport(Config.VIRTUAL_WIDTH, Config.VIRTUAL_HEIGHT)) {

    override suspend fun Node.initialize() {
        val cd = Cooldown()
        column {
            anchorRight = 1f
            anchorBottom = 1f

            align = AlignMode.CENTER
            label {
                text = if (won) {
                    "You achieved maximum souls.\nThank you for playing!"
                } else {
                    "Thank you for playing!"
                }
                font = Assets.pixelFont
                wrap = true
                horizontalAlign = HAlign.CENTER
                fontScaleX = 2f
                fontScaleY = 2f
            }

        }

        fadeMask(delay = 250.milliseconds, fadeTime = 1.seconds)
        { onFinish += { destroy() } }

        onReady +=
            {
                cd.timeout("switch", 3.seconds) { changeTo(MenuScene(context)) }
            }
    }
}