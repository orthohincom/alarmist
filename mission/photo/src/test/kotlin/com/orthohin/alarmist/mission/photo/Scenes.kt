package com.orthohin.alarmist.mission.photo

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** Test scenes and camera views of them. */
object Scenes {
    const val WIDTH = 800
    const val HEIGHT = 600
    const val VIEW_WIDTH = 640
    const val VIEW_HEIGHT = 480

    /** A made-up scene: a gradient with random shapes and lines, like a cluttered shelf. */
    fun synthetic(seed: Int): GrayImage {
        val random = Random(seed)
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        fun grey() = random.nextInt(256).let { Color(it, it, it) }
        g.paint = GradientPaint(0f, 0f, grey(), WIDTH.toFloat(), HEIGHT.toFloat(), grey())
        g.fillRect(0, 0, WIDTH, HEIGHT)
        repeat(70) {
            g.color = grey()
            val x = random.nextInt(-40, WIDTH)
            val y = random.nextInt(-40, HEIGHT)
            val w = random.nextInt(10, 160)
            val h = random.nextInt(10, 160)
            when (random.nextInt(4)) {
                0 -> g.fillRect(x, y, w, h)

                1 -> g.fillOval(x, y, w, h)

                2 -> {
                    g.stroke = BasicStroke(random.nextInt(1, 7).toFloat())
                    g.drawLine(x, y, x + w - 80, y + h - 80)
                }

                else -> g.fillPolygon(
                    IntArray(4) { x + random.nextInt(w) },
                    IntArray(4) { y + random.nextInt(h) },
                    4
                )
            }
        }
        g.dispose()
        return gray(image)
    }

    /** [image] stretched to the scene size. */
    fun fromImage(image: BufferedImage): GrayImage {
        val scaled = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(image, 0, 0, WIDTH, HEIGHT, null)
        g.dispose()
        return gray(scaled)
    }

    private fun gray(image: BufferedImage) =
        GrayImage(image.width, image.height, (image.raster.dataBuffer as DataBufferByte).data.copyOf())

    /** How the camera stands relative to registering, and the light. */
    data class View(
        val turnDegrees: Double = 0.0,
        val zoom: Double = 1.0,
        val shiftX: Double = 0.0,
        val shiftY: Double = 0.0,
        val tiltX: Double = 0.0,
        val tiltY: Double = 0.0,
        val gain: Double = 1.0,
        val offset: Double = 0.0,
        val noise: Double = 0.0
    )

    /** A random view within the given limits: a sleepy person a step aside, in morning light. */
    fun randomView(random: Random, turn: Double = 20.0, zoom: Double = 1.3, shift: Double = 60.0, tilt: Double = 4e-4) =
        View(
            turnDegrees = random.nextDouble(-turn, turn),
            zoom = random.nextDouble(1 / zoom, zoom),
            shiftX = random.nextDouble(-shift, shift),
            shiftY = random.nextDouble(-shift, shift),
            tiltX = random.nextDouble(-tilt, tilt),
            tiltY = random.nextDouble(-tilt, tilt),
            gain = random.nextDouble(0.5, 1.3),
            offset = random.nextDouble(-30.0, 30.0),
            noise = 4.0
        )

    /** What a [VIEW_WIDTH]×[VIEW_HEIGHT] camera sees of [scene] from [view]; outside the scene is grey. */
    fun look(scene: GrayImage, view: View, seed: Int = 0): GrayImage {
        val random = java.util.Random(seed.toLong())
        val angle = Math.toRadians(view.turnDegrees)
        val c = cos(angle) / view.zoom
        val s = sin(angle) / view.zoom
        val out = ByteArray(VIEW_WIDTH * VIEW_HEIGHT)
        for (v in 0 until VIEW_HEIGHT) {
            for (u in 0 until VIEW_WIDTH) {
                // View → scene: perspective, zoom and turn about the middle, then the shift.
                val x0 = u - VIEW_WIDTH / 2.0
                val y0 = v - VIEW_HEIGHT / 2.0
                val w = 1 + view.tiltX * x0 + view.tiltY * y0
                val x1 = x0 / w
                val y1 = y0 / w
                val x = c * x1 - s * y1 + scene.width / 2.0 + view.shiftX
                val y = s * x1 + c * y1 + scene.height / 2.0 + view.shiftY
                val value = sample(scene, x, y) * view.gain + view.offset + random.nextGaussian() * view.noise
                out[v * VIEW_WIDTH + u] = value.toInt().coerceIn(0, 255).toByte()
            }
        }
        return GrayImage(VIEW_WIDTH, VIEW_HEIGHT, out)
    }

    private fun sample(image: GrayImage, x: Double, y: Double): Double {
        if (x < 0 || y < 0 || x > image.width - 1.0 || y > image.height - 1.0) return 128.0
        val ix = minOf(floor(x).toInt(), image.width - 2)
        val iy = minOf(floor(y).toInt(), image.height - 2)
        val fx = x - ix
        val fy = y - iy
        val top = image[ix, iy] * (1 - fx) + image[ix + 1, iy] * fx
        val bottom = image[ix, iy + 1] * (1 - fx) + image[ix + 1, iy + 1] * fx
        return top * (1 - fy) + bottom * fy
    }
}
