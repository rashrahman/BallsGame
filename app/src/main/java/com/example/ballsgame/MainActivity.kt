package com.example.ballsgame

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.ballsgame.ui.theme.BallsGameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BallsGameTheme {
                GameContainer()
            }
        }
    }
}

@Composable
fun GameContainer() {
    val context = LocalContext.current
    val gameState = rememberGameState()

    // Obstacle configuration
    val obstacleConfig = remember(gameState.canvasSize) {
        derivedStateOf {
            gameState.canvasSize.takeIf { it.width > 0 }?.let {
                buildObstacleLayout(it)
            } ?: emptyList()
        }
    }

    // Sensor integration
    val sensorController = rememberSensorController(
        context = context,
        onMovement = { deltaX, deltaY ->
            gameState.updatePosition(deltaX, deltaY, obstacleConfig.value)
        }
    )

    // Game canvas
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .onSizeChanged {
                gameState.setCanvasSize(it.width.toFloat(), it.height.toFloat())
            }
    ) {
        // Render obstacles
        obstacleConfig.value.forEach { obstacle ->
            drawRect(
                color = obstacle.color,
                topLeft = Offset(obstacle.left, obstacle.top),
                size = Size(obstacle.width, obstacle.height)
            )
        }

        // Render player ball
        drawCircle(
            color = Color.Green,
            radius = gameState.ballRadius,
            center = gameState.ballPosition
        )
    }

    DisposableEffect(sensorController) {
        onDispose { sensorController.unregister() }
    }
}

@Stable
class GameState {
    var ballPosition by mutableStateOf(Offset.Zero)
    var canvasSize by mutableStateOf(Size.Zero)
    val ballRadius = 25f

    fun setCanvasSize(width: Float, height: Float) {
        canvasSize = Size(width, height)
        if (ballPosition == Offset.Zero) {
            ballPosition = Offset(width / 2f, height / 2f)
        }
    }

    fun updatePosition(deltaX: Float, deltaY: Float, obstacles: List<Obstacle>) {
        val newX = (ballPosition.x + deltaX).coerceIn(ballRadius, canvasSize.width - ballRadius)
        val newY = (ballPosition.y + deltaY).coerceIn(ballRadius, canvasSize.height - ballRadius)
        val newPosition = Offset(newX, newY)

        if (!obstacles.isCollision(newPosition, ballRadius)) {
            ballPosition = newPosition
        }
    }
}

fun buildObstacleLayout(canvasSize: Size): List<Obstacle> {
    return listOf(
        // Vertical barriers
        Obstacle(
            left = canvasSize.width * 0.3f,
            top = 0f,
            width = 30f,
            height = canvasSize.height * 0.5f,
            color = Color.Red
        ),
        Obstacle(
            left = canvasSize.width * 0.3f,
            top = canvasSize.height * 0.6f,
            width = 30f,
            height = canvasSize.height * 0.4f,
            color = Color.Red
        ),

        // Horizontal barrier
        Obstacle(
            left = 0f,
            top = canvasSize.height * 0.75f,
            width = canvasSize.width * 0.7f,
            height = 30f,
            color = Color.Blue
        ),

        // Maze exit
        Obstacle(
            left = canvasSize.width * 0.85f,
            top = canvasSize.height * 0.2f,
            width = 30f,
            height = canvasSize.height * 0.6f,
            color = Color.Magenta
        )
    )
}

data class Obstacle(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val color: Color
) {
    val right get() = left + width
    val bottom get() = top + height
}

fun List<Obstacle>.isCollision(position: Offset, radius: Float): Boolean {
    return any { obstacle ->
        val closestX = position.x.coerceIn(obstacle.left, obstacle.right)
        val closestY = position.y.coerceIn(obstacle.top, obstacle.bottom)
        val dx = position.x - closestX
        val dy = position.y - closestY
        (dx * dx + dy * dy) <= radius * radius
    }
}

@Composable
fun rememberGameState(): GameState {
    return remember { GameState() }
}

@Composable
fun rememberSensorController(
    context: android.content.Context,
    onMovement: (Float, Float) -> Unit
): SensorController {
    val controller = remember { SensorController(context, onMovement) }
    DisposableEffect(controller) {
        controller.register()
        onDispose { controller.unregister() }
    }
    return controller
}

class SensorController(
    context: android.content.Context,
    private val onMovement: (Float, Float) -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var lastTimestamp = 0L

    fun register() {
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
    }

    fun unregister() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (lastTimestamp == 0L) {
            lastTimestamp = event.timestamp
            return
        }

        val dt = (event.timestamp - lastTimestamp) * 1e-9f
        val sensitivity = 180f

        val deltaX = event.values[1] * sensitivity * dt
        val deltaY = -event.values[0] * sensitivity * dt

        onMovement(deltaX, deltaY)
        lastTimestamp = event.timestamp
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Preview(showBackground = true)
@Composable
fun GamePreview() {
    BallsGameTheme {
        GameContainer()
    }
}