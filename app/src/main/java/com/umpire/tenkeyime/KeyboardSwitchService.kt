package com.umpire.tenkeyime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.IBinder
import android.provider.Settings
import android.view.InputDevice

class KeyboardSwitchService :
    Service(),
    InputManager.InputDeviceListener {

    companion object {

        private const val TENKEY_IME =
            "com.umpire.tenkeyime/.TenKeyImeService"

        private const val SAMSUNG_IME =
            "com.samsung.android.honeyboard/.service.HoneyBoardService"

        /*
         * 실제 네 갤럭시 dumpsys에서 확인됨:
         *
         * K21PROBT1 Keyboard
         */
        private const val TARGET_NAME =
            "K21PROBT1"

        private const val CHANNEL_ID =
            "dual_tenkey_service"

        private const val NOTIFICATION_ID =
            1001
    }

    private lateinit var inputManager:
            InputManager


    // ============================================================
    // Service 시작
    // ============================================================

    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        inputManager =
            getSystemService(
                Context.INPUT_SERVICE
            ) as InputManager

        /*
         * 입력장치 추가/삭제 이벤트 등록.
         *
         * 이제 주기적으로 polling하지 않는다.
         */
        inputManager.registerInputDeviceListener(
            this,
            null
        )

        /*
         * 서비스가 시작된 바로 그 순간에도
         * K21이 이미 연결되어 있을 수 있다.
         */
        updateKeyboardForCurrentState()
    }


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        /*
         * 삼성 루틴에서 앱을 다시 실행했을 때도
         * 현재 상태 한 번 더 확인.
         */
        updateKeyboardForCurrentState()

        /*
         * 시스템이 프로세스를 정리해도
         * 가능하면 서비스를 다시 생성하도록 요청.
         */
        return START_STICKY
    }


    // ============================================================
    // InputDeviceListener
    // ============================================================

    override fun onInputDeviceAdded(
        deviceId: Int
    ) {

        val device =
            InputDevice.getDevice(deviceId)

        if (isTargetKeyboard(device)) {

            switchToTenKey()
        }
    }


    override fun onInputDeviceRemoved(
        deviceId: Int
    ) {

        /*
         * 제거된 deviceId는 이미 조회할 수 없을 수 있으므로
         * 현재 남아 있는 전체 입력장치를 다시 확인한다.
         */
        updateKeyboardForCurrentState()
    }


    override fun onInputDeviceChanged(
        deviceId: Int
    ) {

        updateKeyboardForCurrentState()
    }


    // ============================================================
    // 현재 K21 연결 여부에 따라 IME 결정
    // ============================================================

    private fun updateKeyboardForCurrentState() {

        if (isK21Connected()) {

            switchToTenKey()

        } else {

            switchToSamsung()
        }
    }


    // ============================================================
    // K21 연결 여부 확인
    // ============================================================

    private fun isK21Connected(): Boolean {

        val deviceIds =
            InputDevice.getDeviceIds()

        for (deviceId in deviceIds) {

            val device =
                InputDevice.getDevice(deviceId)
                    ?: continue

            if (isTargetKeyboard(device)) {

                return true
            }
        }

        return false
    }


    private fun isTargetKeyboard(
        device: InputDevice?
    ): Boolean {

        if (device == null) {
            return false
        }

        val name =
            device.name ?: return false

        /*
         * K21PROBT1 Keyboard
         *
         * 전체 일치 대신 contains를 써서
         * 펌웨어에서 뒤에 Keyboard 등이 붙어도 인식.
         */
        val nameMatches =
            name.contains(
                TARGET_NAME,
                ignoreCase = true
            )

        /*
         * 가상 키보드가 아니라
         * 실제 외장 입력장치인지도 확인.
         */
        val external =
            device.isExternal

        return (
                nameMatches &&
                        external
                )
    }


    // ============================================================
    // TenKeyIME 전환
    // ============================================================

    private fun switchToTenKey() {

        switchIme(
            TENKEY_IME
        )
    }


    // ============================================================
    // 삼성 키보드 복귀
    // ============================================================

    private fun switchToSamsung() {

        switchIme(
            SAMSUNG_IME
        )
    }


    // ============================================================
    // 실제 DEFAULT_INPUT_METHOD 변경
    // ============================================================

    private fun switchIme(
        imeId: String
    ) {

        try {

            val current =
                Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD
                )

            /*
             * 이미 해당 키보드면 아무것도 하지 않음.
             */
            if (current == imeId) {
                return
            }

            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
                imeId
            )

        } catch (
            e: SecurityException
        ) {

            /*
             * WRITE_SECURE_SETTINGS가 없는 경우.
             *
             * 현재 개발폰에는 ADB로 granted=true 확인 완료.
             */
            e.printStackTrace()

        } catch (
            e: Exception
        ) {

            e.printStackTrace()
        }
    }


    // ============================================================
    // Foreground Service Notification
    // ============================================================

    private fun createNotificationChannel() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Dual TenKey",
                NotificationManager.IMPORTANCE_LOW
            )

        channel.description =
            "K21PROBT1 연결 상태에 따라 키보드를 자동 전환합니다."

        manager.createNotificationChannel(
            channel
        )
    }


    private fun createNotification():
            Notification {

        return Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "Dual TenKey"
            )
            .setContentText(
                "K21 키보드 자동 전환 사용 중"
            )
            .setSmallIcon(
                android.R.drawable.ic_menu_manage
            )
            .setOngoing(true)
            .build()
    }


    // ============================================================
    // 종료
    // ============================================================

    override fun onDestroy() {

        if (
            ::inputManager.isInitialized
        ) {

            inputManager
                .unregisterInputDeviceListener(
                    this
                )
        }

        super.onDestroy()
    }


    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}