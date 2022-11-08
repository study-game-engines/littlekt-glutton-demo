package com.lehaine.game.scene

import com.lehaine.game.*
import com.lehaine.game.node.entity.BoneMan
import com.lehaine.game.node.entity.Hero
import com.lehaine.game.node.entity.hero
import com.lehaine.game.node.level.TestSpawner
import com.lehaine.littlekt.Context
import com.lehaine.littlekt.async.KtScope
import com.lehaine.littlekt.file.ldtk.LDtkMapLoader
import com.lehaine.littlekt.file.vfs.readLDtkMapLoader
import com.lehaine.littlekt.file.vfs.readPixmap
import com.lehaine.littlekt.graph.node.Node
import com.lehaine.littlekt.graph.node.addTo
import com.lehaine.littlekt.graph.node.canvasLayer
import com.lehaine.littlekt.graph.node.component.NinePatchDrawable
import com.lehaine.littlekt.graph.node.node
import com.lehaine.littlekt.graph.node.node2d.Node2D
import com.lehaine.littlekt.graph.node.node2d.node2d
import com.lehaine.littlekt.graph.node.ui.Control
import com.lehaine.littlekt.graph.node.ui.control
import com.lehaine.littlekt.graph.node.ui.panel
import com.lehaine.littlekt.graphics.Color
import com.lehaine.littlekt.graphics.Cursor
import com.lehaine.littlekt.graphics.NinePatch
import com.lehaine.littlekt.graphics.tilemap.ldtk.LDtkLevel
import com.lehaine.littlekt.input.GameAxis
import com.lehaine.littlekt.input.GameButton
import com.lehaine.littlekt.input.Key
import com.lehaine.littlekt.input.Pointer
import com.lehaine.littlekt.util.viewport.ExtendViewport
import com.lehaine.rune.engine.RuneScene
import com.lehaine.rune.engine.node.EntityCamera2D
import com.lehaine.rune.engine.node.entityCamera2D
import com.lehaine.rune.engine.node.pixelPerfectSlice
import com.lehaine.rune.engine.node.pixelSmoothFrameBuffer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration


class GameScene(context: Context) :
    RuneScene<GameInput>(
        context,
        ExtendViewport(Config.VIRTUAL_WIDTH, Config.VIRTUAL_HEIGHT),
        uiInputSignals = createUiGameInputSignals()
    ) {
    lateinit var background: Node
    lateinit var fxBackground: Node
    lateinit var main: Node
    lateinit var foreground: Node
    lateinit var fxForeground: Node
    lateinit var top: Node
    lateinit var ui: Control

    lateinit var entities: Node2D
    var mapLoader: LDtkMapLoader? = null
    lateinit var ldtkLevel: LDtkLevel
    lateinit var hero: Hero
    lateinit var level: Level
    private var levelIdx = 0

    val fx = Fx(this)

    init {
        controller.addBinding(GameInput.MOVE_LEFT, listOf(Key.A, Key.ARROW_LEFT), axes = listOf(GameAxis.LX))
        controller.addBinding(GameInput.MOVE_RIGHT, listOf(Key.D, Key.ARROW_RIGHT), axes = listOf(GameAxis.LX))
        controller.addBinding(GameInput.MOVE_UP, listOf(Key.W, Key.ARROW_UP), axes = listOf(GameAxis.LY))
        controller.addBinding(GameInput.MOVE_DOWN, listOf(Key.S, Key.ARROW_DOWN), axes = listOf(GameAxis.LY))

        controller.addBinding(
            GameInput.ATTACK,
            buttons = listOf(GameButton.XBOX_X),
            pointers = listOf(Pointer.MOUSE_LEFT)
        )
        controller.addBinding(
            GameInput.SOAR,
            buttons = listOf(GameButton.XBOX_A),
            pointers = listOf(Pointer.MOUSE_RIGHT)
        )
        controller.addBinding(
            GameInput.HAND_OF_DEATH,
            buttons = listOf(GameButton.XBOX_Y),
            keys = listOf(Key.Q)
        )
        controller.addBinding(
            GameInput.BONE_SPEAR,
            buttons = listOf(GameButton.XBOX_B),
            keys = listOf(Key.E)
        )

        controller.addAxis(GameInput.HORIZONTAL, GameInput.MOVE_RIGHT, GameInput.MOVE_LEFT)
        controller.addAxis(GameInput.VERTICAL, GameInput.MOVE_DOWN, GameInput.MOVE_UP)

        controller.addVector(
            GameInput.MOVEMENT,
            GameInput.MOVE_RIGHT,
            GameInput.MOVE_DOWN,
            GameInput.MOVE_LEFT,
            GameInput.MOVE_UP
        )

        clearColor = Color.fromHex("#422e37")
    }


    override suspend fun Node.initialize() {
        mapLoader?.dispose()
        val mapLoader = resourcesVfs["world.ldtk"].readLDtkMapLoader().also { this@GameScene.mapLoader = it }
        val world = mapLoader.loadMap(false, 0)
        ldtkLevel = world.levels[0]
        val cursorImage = resourcesVfs["cursor.png"].readPixmap()
        val cursor =
            Cursor(cursorImage, (cursorImage.width * 0.5).roundToInt(), (cursorImage.height * 0.5f).roundToInt())
        context.graphics.setCursor(cursor)
        createNodes()
    }

    private fun Node.createNodes() {
        canvasLayer {
            val entityCamera: EntityCamera2D

            val fbo = pixelSmoothFrameBuffer {
                targetHeight = 235
                entityCamera = entityCamera2D {
                    trackingSpeed = 1.25f
                    viewBounds.width = ldtkLevel.pxWidth.toFloat()
                    viewBounds.height = ldtkLevel.pxHeight.toFloat()
                    camera = canvasCamera
                }


                background = node {
                    name = "Background"

                    level = Level(ldtkLevel).addTo(this)
                }

                fxBackground = node {
                    name = "FX Background"
                }

                main = node {
                    name = "Main"


                    val projectiles = Node2D().apply {
                        name = "Projectiles"
                    }
                    entities = node2d {
                        name = "Entities"
                        ySort = true

                        hero = hero(ldtkLevel.entities("Hero")[0], level, entityCamera, projectiles)
                        entityCamera.follow(hero, true)

                        if (ldtkLevel.entitiesByIdentifier.contains("BoneMan")) {
                            ldtkLevel.entities("BoneMan").firstOrNull()?.let {
                                BoneMan(it).addTo(this)
                            }
                        }
                    }

                    projectiles.addTo(this)

                    if (ldtkLevel.entitiesByIdentifier.contains("MonsterSpawner")) {
                        TestSpawner(hero, level).addTo(this)
                    }
                }

                foreground = node {
                    name = "Foreground"
                }

                fxForeground = node {
                    name = "FX Foreground"
                }

                top = node {
                    name = "Top"
                }
            }

            pixelPerfectSlice {
                this.fbo = fbo
                onUpdate += {
                    scaledDistX = entityCamera.scaledDistX
                    scaledDistY = entityCamera.scaledDistY
                }
            }

        }

        ui = control {
            name = "UI"
            anchorRight = 1f
            anchorBottom = 1f
            panel {
                name = "Border"
                val border9p = NinePatch(Assets.atlas.getByPrefix("uiArenaBorder").slice, 16, 16, 15, 15)
                panel = NinePatchDrawable(border9p)
                anchorRight = 1f
                anchorBottom = 1f
            }

        }

        fx.createParticleBatchNodes()
    }

    override fun update(dt: Duration) {
        fx.update(dt)
        super.update(dt)

        if (input.isKeyJustPressed(Key.R)) {
            destroyRoot()
            root.createNodes()
            resize(graphics.width, graphics.height)
        }

        if (input.isKeyJustPressed(Key.NUM1)) {
            loadLevel(0)

        } else if (input.isKeyJustPressed(Key.NUM2)) {
            loadLevel(1)
        }

        if (input.isKeyJustPressed(Key.T)) {
            println(root.treeString())
        }

        if (input.isKeyJustPressed(Key.P)) {
            println(stats)
        }
    }

    private fun loadLevel(idx: Int) {
        levelIdx = idx
        mapLoader?.let {
            KtScope.launch {
                val world = it.loadMap(false, idx)
                ldtkLevel = world.levels[0]

                destroyRoot()
                root.createNodes()
                resize(graphics.width, graphics.height)
            }
        }
    }
}