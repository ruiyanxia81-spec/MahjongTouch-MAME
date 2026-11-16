package nz.arcadetouch

import android.app.AlertDialog
import android.hardware.input.InputManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.util.AtomicFile
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import nz.arcadetouch.InputState.Control
import nz.arcadetouch.InputState.Mode

class GameActivity : ComponentActivity(), InputManager.InputDeviceListener {
    private val input = InputState()
    private val settings by lazy { getSharedPreferences("controls", MODE_PRIVATE) }
    private val devices by lazy { getSystemService(InputManager::class.java) }
    private lateinit var retro: GLRetroView
    private lateinit var pad: LinearLayout
    private var activeDevice: Int? = null
    private var appliedMask = 0
    private var menuOpen = false
    private var binding = false
    private var stateOperation = false
    private var gameReady = false
    private lateinit var gameState: AtomicFile
    private var comboKey = KeyEvent.KEYCODE_BUTTON_R1
    private val heldKeys = mutableSetOf<Int>()
    private var axes = emptySet<Control>()
    private val keyDirections = mapOf(
        KeyEvent.KEYCODE_DPAD_UP to Control.UP, KeyEvent.KEYCODE_DPAD_DOWN to Control.DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to Control.LEFT, KeyEvent.KEYCODE_DPAD_RIGHT to Control.RIGHT
    )
    private val dashKey = KeyEvent.KEYCODE_BUTTON_L1
    private val reservedKeys = setOf(dashKey,
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_MODE
    )
    private val retroKeys = mapOf(
        0 to KeyEvent.KEYCODE_BUTTON_B, 8 to KeyEvent.KEYCODE_BUTTON_A,
        2 to KeyEvent.KEYCODE_BUTTON_SELECT, 3 to KeyEvent.KEYCODE_BUTTON_START,
        4 to KeyEvent.KEYCODE_DPAD_UP, 5 to KeyEvent.KEYCODE_DPAD_DOWN,
        6 to KeyEvent.KEYCODE_DPAD_LEFT, 7 to KeyEvent.KEYCODE_DPAD_RIGHT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rom = intent.getStringExtra("rom") ?: run { finish(); return }
        val directory = File(filesDir, "savestates")
        gameState = AtomicFile(File(directory, File(rom).name + ".state"))
        hideStatusBar()
        comboKey = settings.getInt("combo", KeyEvent.KEYCODE_BUTTON_R1)
            .takeUnless { it == dashKey } ?: KeyEvent.KEYCODE_BUTTON_R1
        if (settings.getBoolean("gamepad", false)) input.setMode(Mode.GAMEPAD)
        val data = GLRetroViewData(this).apply {
            gameFilePath = rom
            coreFilePath = File(applicationInfo.nativeLibraryDir, "libfbneo.so").absolutePath
            rumbleEventsEnabled = false
        }
        val root = FrameLayout(this)
        retro = GLRetroView(this, data).apply {
            isFocusable = false
            setOnTouchListener { _, _ -> true }
        }
        root.addView(retro, FrameLayout.LayoutParams(-1, -1))
        lifecycle.addObserver(retro)
        pad = createPad()
        root.addView(pad, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        pad.visibility = if (input.mode() == Mode.TOUCH) View.VISIBLE else View.GONE
        root.addView(Button(this).apply {
            text = "暂停"
            setOnClickListener { showMenu() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        setContentView(root)
        hideStatusBar()
        devices.registerInputDeviceListener(this, null)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { showMenu() }
        })
        lifecycleScope.launch {
            retro.getGLRetroEvents().collect { event ->
                if (event == GLRetroView.GLRetroEvents.FrameRendered) gameReady = true
            }
        }
        lifecycleScope.launch {
            retro.getGLRetroErrors().collect { code ->
                if (!isFinishing) AlertDialog.Builder(this@GameActivity)
                    .setMessage("FBNeo 无法启动游戏（错误 ${code}）。请检查 ROM 与核心版本。")
                    .setPositiveButton("返回游戏库") { _, _ -> finish() }
                    .setOnCancelListener { finish() }.show()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar()
    }

    private fun hideStatusBar() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun createPad(): LinearLayout {
        val directions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        directions.addView(row(touch("↑", Control.UP)))
        directions.addView(row(touch("←", Control.LEFT), touch("↓", Control.DOWN), touch("→", Control.RIGHT)))
        actions.addView(row(touch("投币", Control.COIN), touch("开始", Control.START)))
        actions.addView(row(touch("A", Control.ATTACK), touch("B", Control.JUMP), touch("A+B", Control.ATTACK_AND_JUMP)))
        return LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            isMotionEventSplittingEnabled = true
            addView(directions)
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(actions)
        }
    }

    private fun row(vararg buttons: Button) = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        buttons.forEach { addView(it) }
    }

    private fun touch(label: String, control: Control) = Button(this).apply {
        text = label
        minimumWidth = 0
        minWidth = 0
        isFocusable = false
        setOnTouchListener { view, event ->
            if (!menuOpen && input.mode() == Mode.TOUCH) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { view.isPressed = true; input.setHeld(Mode.TOUCH, control, true) }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        input.setHeld(Mode.TOUCH, control, false)
                    }
                }
                sendInput()
            }
            true
        }
        setOnClickListener {
            if (!menuOpen && input.mode() == Mode.TOUCH) {
                input.setHeld(Mode.TOUCH, control, true)
                sendInput()
                postDelayed({
                    input.setHeld(Mode.TOUCH, control, false)
                    sendInput()
                }, 90)
            }
        }
    }

    private fun sendInput() {
        if (!::retro.isInitialized) return
        val next = input.joypadMask()
        val changed = appliedMask xor next
        if (changed == 0) return
        val released = retroKeys.filterKeys { changed and (1 shl it) != 0 && next and (1 shl it) == 0 }
        val pressed = retroKeys.filterKeys { changed and (1 shl it) != 0 && next and (1 shl it) != 0 }
        retro.sendJoypadKeys(released.values.toIntArray(), pressed.values.toIntArray())
        appliedMask = next
    }

    private fun clearInput() {
        activeDevice = null
        heldKeys.clear()
        axes = emptySet()
        input.clear(input.mode())
        sendInput()
    }

    private fun showMenu() {
        if (!::retro.isInitialized || menuOpen) return
        menuOpen = true
        clearInput()
        retro.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        val nextMode = if (input.mode() == Mode.TOUCH) Mode.GAMEPAD else Mode.TOUCH
        val gamepad = nextMode == Mode.GAMEPAD
        val switchLabel = if (gamepad) "切换到蓝牙手柄" else "切换到触屏"
        AlertDialog.Builder(this)
            .setTitle("暂停")
            .setItems(arrayOf("继续游戏", "保存存档", "加载存档", switchLabel, "绑定 A+B", "退出游戏")) { _, which ->
                when (which) {
                    1 -> changeState(save = true)
                    2 -> changeState(save = false)
                    3 -> {
                        input.setMode(nextMode)
                        settings.edit().putBoolean("gamepad", gamepad).apply()
                        pad.visibility = if (gamepad) View.GONE else View.VISIBLE
                    }
                    4 -> { binding = true; bindCombo() }
                    5 -> finish()
                }
            }
            .setOnDismissListener { if (!binding && !stateOperation) resumeGame() }
            .show()
    }

    private fun changeState(save: Boolean) {
        if (!gameReady) {
            showStateMessage("请等游戏启动后再操作")
            return
        }
        stateOperation = true
        lifecycleScope.launch {
            try {
                if (save) {
                    val bytes = onGameThread { retro.serializeState(false) }
                    check(bytes.isNotEmpty())
                    withContext(Dispatchers.IO) {
                        val output = gameState.startWrite()
                        try {
                            output.write(bytes)
                            gameState.finishWrite(output)
                        } catch (error: Exception) {
                            gameState.failWrite(output)
                            throw error
                        }
                    }
                    showStateMessage("存档已保存")
                } else {
                    val bytes = withContext(Dispatchers.IO) { gameState.readFully() }
                    check(onGameThread { retro.unserializeState(bytes, false) })
                    showStateMessage("存档已加载")
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: java.io.FileNotFoundException) {
                showStateMessage(if (save) "无法保存存档" else "这个游戏还没有存档")
            } catch (error: Exception) {
                showStateMessage(if (save) "无法保存存档" else "无法加载存档")
            } finally {
                stateOperation = false
                resumeGame()
            }
        }
    }

    private suspend fun <T> onGameThread(action: () -> T): T = suspendCancellableCoroutine { result ->
        retro.queueEvent {
            if (result.isActive) result.resumeWith(runCatching(action))
        }
    }

    private fun showStateMessage(message: String) {
        if (!isFinishing) android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun bindCombo() {
        AlertDialog.Builder(this)
            .setTitle("按下要绑定为 A+B 的手柄键")
            .setMessage("当前绑定：${KeyEvent.keyCodeToString(comboKey).removePrefix("KEYCODE_BUTTON_")}")
            .setNegativeButton("取消", null)
            .setOnKeyListener { dialog, code, event ->
                if (!isController(event.source) || !KeyEvent.isGamepadButton(code)) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP) {
                    if (code in reservedKeys) {
                        android.widget.Toast.makeText(this, "请选择肩键或其他未使用的键", android.widget.Toast.LENGTH_SHORT).show()
                        return@setOnKeyListener true
                    }
                    comboKey = code
                    settings.edit().putInt("combo", code).apply()
                    dialog.dismiss()
                }
                true
            }
            .setOnDismissListener { binding = false; resumeGame() }
            .show()
    }

    private fun resumeGame() {
        menuOpen = false
        if (!isFinishing) retro.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private fun isController(source: Int) =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD

    private fun acceptsController(deviceId: Int): Boolean {
        if (menuOpen || input.mode() != Mode.GAMEPAD) return false
        if (activeDevice == null) activeDevice = deviceId
        return activeDevice == deviceId
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isController(event.source)) return super.dispatchKeyEvent(event)
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (event.action == KeyEvent.ACTION_UP && input.mode() == Mode.GAMEPAD) showMenu()
            return true
        }
        if (acceptsController(event.deviceId)) {
            if (code == dashKey) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    val left = appliedMask and (1 shl 6) != 0
                    val right = appliedMask and (1 shl 7) != 0
                    if (left xor right) {
                        retro.sendDirectionDoubleTap(if (left) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
                    }
                }
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) heldKeys.add(code)
            if (event.action == KeyEvent.ACTION_UP) heldKeys.remove(code)
            updateGamepad()
        }
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isController(event.source)) return super.dispatchGenericMotionEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && acceptsController(event.deviceId)) {
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            axes = buildSet {
                if (x < -0.5f || hatX < -0.5f) add(Control.LEFT)
                if (x > 0.5f || hatX > 0.5f) add(Control.RIGHT)
                if (y < -0.5f || hatY < -0.5f) add(Control.UP)
                if (y > 0.5f || hatY > 0.5f) add(Control.DOWN)
            }
            updateGamepad()
        }
        return true
    }

    private fun updateGamepad() {
        val controls = axes + heldKeys.mapNotNull { key ->
            if (key == comboKey) Control.ATTACK_AND_JUMP else keyDirections[key] ?: when (key) {
                KeyEvent.KEYCODE_BUTTON_A -> Control.ATTACK
                KeyEvent.KEYCODE_BUTTON_B -> Control.JUMP
                KeyEvent.KEYCODE_BUTTON_SELECT -> Control.COIN
                KeyEvent.KEYCODE_BUTTON_START -> Control.START
                else -> null
            }
        }
        Control.values().forEach { input.setHeld(Mode.GAMEPAD, it, it in controls) }
        sendInput()
    }

    override fun onPause() {
        clearInput()
        super.onPause()
    }

    override fun onDestroy() {
        if (::retro.isInitialized) devices.unregisterInputDeviceListener(this)
        super.onDestroy()
    }

    override fun onInputDeviceAdded(deviceId: Int) = Unit
    override fun onInputDeviceChanged(deviceId: Int) = Unit
    override fun onInputDeviceRemoved(deviceId: Int) {
        if (deviceId == activeDevice) clearInput()
    }
}
