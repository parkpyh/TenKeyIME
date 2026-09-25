package com.umpire.tenkeyime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

class TenKeyImeService : InputMethodService() {

    // =============================================================
    // 입력 모드
    // =============================================================

    private enum class LanguageMode {
        KOREAN,
        ENGLISH
    }

    private var languageMode = LanguageMode.KOREAN
    private var numberMode = false

    // false = abc / true = ABC
    private var englishUppercase = false


    // =============================================================
    // 한글
    // =============================================================

    private val automata = HangeulAutomata()


    // =============================================================
    // Handler
    // =============================================================

    private val handler =
        Handler(Looper.getMainLooper())


    // =============================================================
    // 다중탭
    // =============================================================

    private val multiTapTimeoutMs = 800L

    private var lastKeyCode =
        KeyEvent.KEYCODE_UNKNOWN

    private var lastKeyTime = 0L

    // 자음 입력 후 800ms가 지나면 겹받침/음절 경계를 확정한다.
    private val koreanCommitRunnable = Runnable {
        automata.onConsonantTimeout()?.let { applyAutomataResult(it) }
        clearMultiTap()
    }

    private fun cancelKoreanCommit() {
        handler.removeCallbacks(koreanCommitRunnable)
    }



    // =============================================================
    // 영문
    // =============================================================

    private val englishKeyMap =
        mapOf(
            KeyEvent.KEYCODE_NUMPAD_1 to "abc",
            KeyEvent.KEYCODE_NUMPAD_2 to "def",
            KeyEvent.KEYCODE_NUMPAD_3 to "ghi",
            KeyEvent.KEYCODE_NUMPAD_4 to "jkl",
            KeyEvent.KEYCODE_NUMPAD_5 to "mno",
            KeyEvent.KEYCODE_NUMPAD_6 to "pqrs",
            KeyEvent.KEYCODE_NUMPAD_7 to "tuv",
            KeyEvent.KEYCODE_NUMPAD_8 to "wxyz"
        )

    private var lastEnglishKey =
        KeyEvent.KEYCODE_UNKNOWN

    private var lastEnglishTime = 0L
    private var englishCycleIndex = 0


    // =============================================================
    // 특수문자
    // =============================================================

    private val symbols =
        listOf(
            ".",
            ",",
            "?",
            "!",
            "`",
            "~",
            "@",
            "#",
            "$",
            "%",
            "^",
            "&",
            "*",
            "(",
            ")",
            "-",
            "_",
            "=",
            "+"
        )

    private var symbolIndex = 0

    private val symbolCycleTimeoutMs =
        1000L

    private var lastSymbolTime = 0L

    private var symbolCycleActive =
        false


    // =============================================================
    // Enter Long Press
    // =============================================================

    private val enterLongPressMs =
        700L

    private var enterPressed =
        false

    private var enterLongPressTriggered =
        false

    private val enterLongPressRunnable =
        Runnable {

            if (enterPressed) {

                enterLongPressTriggered = true

                clearAllInputState()

                performSend()
            }
        }


    // =============================================================
    // Slash Long Press
    //
    // / 짧게 = 한/영
    // / 길게 = abc ↔ ABC
    // =============================================================

    private val slashLongPressMs =
        700L

    private var slashPressed =
        false

    private var slashLongPressTriggered =
        false

    private val slashLongPressRunnable =
        Runnable {

            if (slashPressed) {

                slashLongPressTriggered = true

                if (
                    languageMode ==
                    LanguageMode.ENGLISH
                ) {

                    englishUppercase =
                        !englishUppercase

                    clearEnglishMultiTap()
                }
            }
        }


    // =============================================================
    // + Long Press
    //
    // 짧게 = Space 1개
    // 꾹 = Space 연속
    // =============================================================

    private val spaceRepeatStartMs =
        450L

    private val spaceRepeatIntervalMs =
        90L

    private var spacePressed =
        false

    private var spaceRepeatTriggered =
        false

    private val spaceRepeatRunnable =
        object : Runnable {

            override fun run() {

                if (!spacePressed) {
                    return
                }

                spaceRepeatTriggered = true

                currentInputConnection
                    ?.commitText(
                        " ",
                        1
                    )

                handler.postDelayed(
                    this,
                    spaceRepeatIntervalMs
                )
            }
        }


    // =============================================================
    // Backspace Long Press
    //
    // 짧게 = 1글자 삭제
    // 꾹 = 연속 삭제
    // =============================================================

    private val deleteRepeatStartMs =
        450L

    private val deleteRepeatIntervalMs =
        75L

    private var deletePressed =
        false

    private var deleteRepeatTriggered =
        false

    private val deleteRepeatRunnable =
        object : Runnable {

            override fun run() {

                if (!deletePressed) {
                    return
                }

                deleteRepeatTriggered = true

                /*
                 * 작성 중인 한글은 입력 역순으로 해체하고,
                 * 현재 조합이 모두 없어지면 그때부터
                 * 확정된 텍스트를 한 글자씩 삭제한다.
                 */
                deleteOneStep()

                handler.postDelayed(
                    this,
                    deleteRepeatIntervalMs
                )
            }
        }


    // =============================================================
    // KEY DOWN
    // =============================================================

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {

        // ---------------------------------------------------------
        // Enter
        // ---------------------------------------------------------

        if (
            keyCode ==
            KeyEvent.KEYCODE_NUMPAD_ENTER
        ) {

            if (
                event.repeatCount == 0 &&
                !enterPressed
            ) {

                startEnterPress()
            }

            return true
        }


        // ---------------------------------------------------------
        // Backspace
        // ---------------------------------------------------------

        if (
            keyCode ==
            KeyEvent.KEYCODE_DEL
        ) {

            if (
                event.repeatCount == 0 &&
                !deletePressed
            ) {

                startDeletePress()
            }

            return true
        }


        // ---------------------------------------------------------
        // + Space
        //
        // 단, 숫자모드에서는 실제 + 기호이므로
        // Long Press Space 처리하지 않음.
        // ---------------------------------------------------------

        if (
            keyCode ==
            KeyEvent.KEYCODE_NUMPAD_ADD &&
            !numberMode
        ) {

            if (
                event.repeatCount == 0 &&
                !spacePressed
            ) {

                startSpacePress()
            }

            return true
        }


        // ---------------------------------------------------------
        // Num
        // ---------------------------------------------------------

        if (
            keyCode ==
            KeyEvent.KEYCODE_NUM_LOCK
        ) {

            if (event.repeatCount == 0) {

                clearAllInputState()

                numberMode =
                    !numberMode
            }

            return true
        }


        // ---------------------------------------------------------
        // 숫자모드
        // ---------------------------------------------------------

        if (numberMode) {

            if (event.repeatCount > 0) {
                return true
            }

            return handleNumberMode(
                keyCode,
                event
            )
        }


        // ---------------------------------------------------------
        // /
        // ---------------------------------------------------------

        if (
            keyCode ==
            KeyEvent.KEYCODE_NUMPAD_DIVIDE
        ) {

            if (
                event.repeatCount == 0 &&
                !slashPressed
            ) {

                startSlashPress()
            }

            return true
        }


        // ---------------------------------------------------------
        // 나머지 자동 반복은 무시
        // ---------------------------------------------------------

        if (event.repeatCount > 0) {

            return if (isTenKey(keyCode)) {

                true

            } else {

                super.onKeyDown(
                    keyCode,
                    event
                )
            }
        }


        // ---------------------------------------------------------
        // 공통키
        // ---------------------------------------------------------

        when (keyCode) {

            // * = 커서 왼쪽
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> {

                clearAllInputState()

                moveCursorLeft()

                return true
            }


            // - = 커서 오른쪽
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {

                clearAllInputState()

                moveCursorRight()

                return true
            }


            // CE = Forward Delete
            KeyEvent.KEYCODE_FORWARD_DEL -> {

                clearAllInputState()

                currentInputConnection
                    ?.deleteSurroundingText(
                        0,
                        1
                    )

                return true
            }


            // . = 특수문자
            KeyEvent.KEYCODE_NUMPAD_DOT -> {

                handleSymbol(
                    event.eventTime
                )

                return true
            }
        }


        return when (languageMode) {

            LanguageMode.KOREAN ->
                handleKorean(
                    keyCode,
                    event
                )

            LanguageMode.ENGLISH ->
                handleEnglish(
                    keyCode,
                    event
                )
        }
    }


    // =============================================================
    // KEY UP
    // =============================================================

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {

        when (keyCode) {

            KeyEvent.KEYCODE_NUMPAD_ENTER -> {

                finishEnterPress()

                return true
            }


            KeyEvent.KEYCODE_DEL -> {

                finishDeletePress()

                return true
            }


            KeyEvent.KEYCODE_NUMPAD_ADD -> {

                if (!numberMode) {

                    finishSpacePress()

                    return true
                }
            }


            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> {

                if (!numberMode) {

                    finishSlashPress()

                    return true
                }
            }
        }


        return super.onKeyUp(
            keyCode,
            event
        )
    }


    // =============================================================
    // Space 짧게 / 연속
    // =============================================================

    private fun startSpacePress() {

        spacePressed = true
        spaceRepeatTriggered = false

        clearAllInputState()

        handler.removeCallbacks(
            spaceRepeatRunnable
        )

        handler.postDelayed(
            spaceRepeatRunnable,
            spaceRepeatStartMs
        )
    }


    private fun finishSpacePress() {

        if (!spacePressed) {
            return
        }

        spacePressed = false

        handler.removeCallbacks(
            spaceRepeatRunnable
        )


        /*
         * 연속입력이 시작되지 않았다면
         * 짧게 누른 것이므로 공백 하나.
         */
        if (!spaceRepeatTriggered) {

            currentInputConnection
                ?.commitText(
                    " ",
                    1
                )
        }

        spaceRepeatTriggered = false
    }


    // =============================================================
    // Backspace 짧게 / 연속
    // =============================================================

    private fun startDeletePress() {

        deletePressed = true
        deleteRepeatTriggered = false

        handler.removeCallbacks(
            deleteRepeatRunnable
        )

        handler.postDelayed(
            deleteRepeatRunnable,
            deleteRepeatStartMs
        )
    }


    private fun finishDeletePress() {

        if (!deletePressed) {
            return
        }

        deletePressed = false

        handler.removeCallbacks(
            deleteRepeatRunnable
        )


        /*
         * 연속삭제가 시작되지 않았다면
         * 짧은 Backspace.
         */
        if (!deleteRepeatTriggered) {

            deleteOneStep()
        }

        deleteRepeatTriggered = false
    }


    // =============================================================
    // Backspace 1단계
    //
    // 한글 작성 중: 조합을 입력 역순으로 한 단계 해체
    // 그 외:       확정 텍스트 한 글자 삭제
    // =============================================================

    private fun deleteOneStep() {

        val connection =
            currentInputConnection
                ?: return

        cancelKoreanCommit()
        clearMultiTap()
        clearEnglishMultiTap()
        stopSymbolCycle()

        if (
            languageMode == LanguageMode.KOREAN &&
            !numberMode
        ) {

            val result =
                automata.backspace()

            if (result != null) {

                applyAutomataResult(
                    result
                )

                return
            }
        } else {

            automata.reset()
        }

        connection.deleteSurroundingText(
            1,
            0
        )
    }


    // =============================================================
    // / 짧게 / 길게
    // =============================================================

    private fun startSlashPress() {

        slashPressed = true
        slashLongPressTriggered = false

        handler.removeCallbacks(
            slashLongPressRunnable
        )

        handler.postDelayed(
            slashLongPressRunnable,
            slashLongPressMs
        )
    }


    private fun finishSlashPress() {

        if (!slashPressed) {
            return
        }

        slashPressed = false

        handler.removeCallbacks(
            slashLongPressRunnable
        )


        if (slashLongPressTriggered) {

            slashLongPressTriggered = false

            return
        }


        clearAllInputState()

        languageMode =
            if (
                languageMode ==
                LanguageMode.KOREAN
            ) {

                LanguageMode.ENGLISH

            } else {

                LanguageMode.KOREAN
            }
    }


    // =============================================================
    // Enter
    // =============================================================

    private fun startEnterPress() {

        enterPressed = true
        enterLongPressTriggered = false

        handler.removeCallbacks(
            enterLongPressRunnable
        )

        handler.postDelayed(
            enterLongPressRunnable,
            enterLongPressMs
        )
    }


    private fun finishEnterPress() {

        if (!enterPressed) {
            return
        }

        enterPressed = false

        handler.removeCallbacks(
            enterLongPressRunnable
        )


        if (enterLongPressTriggered) {

            enterLongPressTriggered = false

            return
        }


        clearAllInputState()

        currentInputConnection
            ?.commitText(
                "\n",
                1
            )
    }


    private fun performSend() {

        val connection =
            currentInputConnection
                ?: return

        val sent =
            connection.performEditorAction(
                EditorInfo.IME_ACTION_SEND
            )

        if (!sent) {

            connection.performEditorAction(
                EditorInfo.IME_ACTION_DONE
            )
        }
    }


    // =============================================================
    // 숫자모드
    // =============================================================

    private fun handleNumberMode(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {

        stopSymbolCycle()
        clearMultiTap()
        clearEnglishMultiTap()

        val number =
            when (keyCode) {

                KeyEvent.KEYCODE_NUMPAD_0 -> "0"
                KeyEvent.KEYCODE_NUMPAD_1 -> "1"
                KeyEvent.KEYCODE_NUMPAD_2 -> "2"
                KeyEvent.KEYCODE_NUMPAD_3 -> "3"
                KeyEvent.KEYCODE_NUMPAD_4 -> "4"
                KeyEvent.KEYCODE_NUMPAD_5 -> "5"
                KeyEvent.KEYCODE_NUMPAD_6 -> "6"
                KeyEvent.KEYCODE_NUMPAD_7 -> "7"
                KeyEvent.KEYCODE_NUMPAD_8 -> "8"
                KeyEvent.KEYCODE_NUMPAD_9 -> "9"

                else -> null
            }


        if (number != null) {

            currentInputConnection
                ?.commitText(
                    number,
                    1
                )

            return true
        }


        return when (keyCode) {

            KeyEvent.KEYCODE_NUMPAD_ADD -> {

                currentInputConnection
                    ?.commitText(
                        "+",
                        1
                    )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {

                currentInputConnection
                    ?.commitText(
                        "-",
                        1
                    )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> {

                currentInputConnection
                    ?.commitText(
                        "*",
                        1
                    )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> {

                currentInputConnection
                    ?.commitText(
                        "/",
                        1
                    )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_DOT -> {

                currentInputConnection
                    ?.commitText(
                        ".",
                        1
                    )

                true
            }


            KeyEvent.KEYCODE_FORWARD_DEL -> {

                currentInputConnection
                    ?.deleteSurroundingText(
                        0,
                        1
                    )

                true
            }


            else ->
                super.onKeyDown(
                    keyCode,
                    event
                )
        }
    }


    // =============================================================
    // 한글
    // =============================================================

    private fun handleKorean(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {

        stopSymbolCycle()
        clearEnglishMultiTap()

        return when (keyCode) {

            KeyEvent.KEYCODE_NUMPAD_1 -> {

                processConsonant(
                    'ㅂ',
                    keyCode,
                    event.eventTime
                )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_2 -> {

                processConsonant(
                    'ㅅ',
                    keyCode,
                    event.eventTime
                )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_3 -> {

                processConsonant(
                    'ㅈ',
                    keyCode,
                    event.eventTime
                )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_4 -> {

                processConsonant(
                    'ㄱ',
                    keyCode,
                    event.eventTime
                )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_5 -> {

                processConsonant(
                    'ㄴ',
                    keyCode,
                    event.eventTime
                )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_6 -> {

                processConsonant(
                    'ㄷ',
                    keyCode,
                    event.eventTime
                )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_7 -> {

                clearMultiTap()

                processHangul(
                    'ㅣ',
                    true,
                    false
                )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_8 -> {

                clearMultiTap()

                processHangul(
                    'ㆍ',
                    true,
                    false
                )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_9 -> {

                clearMultiTap()

                processHangul(
                    'ㅡ',
                    true,
                    false
                )

                true
            }


            KeyEvent.KEYCODE_NUMPAD_0 -> {

                processConsonant(
                    'ㅇ',
                    keyCode,
                    event.eventTime
                )

                true
            }


            else ->
                super.onKeyDown(
                    keyCode,
                    event
                )
        }
    }


    // =============================================================
    // 영어
    // =============================================================

    private fun handleEnglish(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {

        stopSymbolCycle()
        automata.reset()
        clearMultiTap()


        if (
            keyCode ==
            KeyEvent.KEYCODE_NUMPAD_9
        ) {

            clearEnglishMultiTap()

            currentInputConnection
                ?.commitText(
                    "9",
                    1
                )

            return true
        }


        if (
            keyCode ==
            KeyEvent.KEYCODE_NUMPAD_0
        ) {

            clearEnglishMultiTap()

            currentInputConnection
                ?.commitText(
                    "0",
                    1
                )

            return true
        }


        val letters =
            englishKeyMap[keyCode]
                ?: return super.onKeyDown(
                    keyCode,
                    event
                )


        val now =
            event.eventTime

        val sameKey =
            keyCode ==
                    lastEnglishKey

        val elapsed =
            now -
                    lastEnglishTime

        val continueCycle =
            sameKey &&
                    elapsed >= 0 &&
                    elapsed <=
                    multiTapTimeoutMs


        if (continueCycle) {

            currentInputConnection
                ?.deleteSurroundingText(
                    1,
                    0
                )

            englishCycleIndex =
                (englishCycleIndex + 1) %
                        letters.length

        } else {

            englishCycleIndex = 0
        }


        var output =
            letters[
                englishCycleIndex
            ]


        if (englishUppercase) {

            output =
                output.uppercaseChar()
        }


        currentInputConnection
            ?.commitText(
                output.toString(),
                1
            )


        lastEnglishKey = keyCode
        lastEnglishTime = now

        return true
    }


    // =============================================================
    // 특수문자
    // =============================================================

    private fun handleSymbol(
        eventTime: Long
    ) {

        automata.reset()
        clearMultiTap()
        clearEnglishMultiTap()


        val connection =
            currentInputConnection
                ?: return


        val elapsed =
            eventTime -
                    lastSymbolTime


        val continueCycle =
            symbolCycleActive &&
                    elapsed >= 0 &&
                    elapsed <=
                    symbolCycleTimeoutMs


        if (continueCycle) {

            connection
                .deleteSurroundingText(
                    1,
                    0
                )

            symbolIndex =
                (symbolIndex + 1) %
                        symbols.size

        } else {

            symbolIndex = 0
        }


        connection.commitText(
            symbols[symbolIndex],
            1
        )


        lastSymbolTime = eventTime

        symbolCycleActive = true
    }


    // =============================================================
    // 한글 Automata
    // =============================================================

    private fun processConsonant(
        input: Char,
        keyCode: Int,
        eventTime: Long
    ) {
        cancelKoreanCommit()


        val sameKey =
            keyCode ==
                    lastKeyCode

        val elapsed =
            eventTime -
                    lastKeyTime

        val allowCycle =
            sameKey &&
                    elapsed >= 0 &&
                    elapsed <=
                    multiTapTimeoutMs


        processHangul(
            input,
            false,
            allowCycle
        )


        lastKeyCode = keyCode
        lastKeyTime = eventTime
        handler.postDelayed(koreanCommitRunnable, multiTapTimeoutMs)
    }


    private fun processHangul(
        input: Char,
        isVowel: Boolean,
        allowCycle: Boolean
    ) {
        if (isVowel) cancelKoreanCommit()

        val connection =
            currentInputConnection
                ?: return


        val result =
            automata.process(
                input,
                isVowel,
                allowCycle
            )

        applyAutomataResult(
            result
        )
    }


    private fun applyAutomataResult(
        result: AutomataResult
    ) {

        val connection =
            currentInputConnection
                ?: return

        if (result.deleteCount > 0) {

            connection
                .deleteSurroundingText(
                    result.deleteCount,
                    0
                )
        }

        if (
            result.insertText
                .isNotEmpty()
        ) {

            connection
                .commitText(
                    result.insertText,
                    1
                )
        }
    }


    // =============================================================
    // 커서
    // =============================================================

    private fun moveCursorLeft() {

        sendCursorKey(
            KeyEvent.KEYCODE_DPAD_LEFT
        )
    }


    private fun moveCursorRight() {

        sendCursorKey(
            KeyEvent.KEYCODE_DPAD_RIGHT
        )
    }


    private fun sendCursorKey(
        keyCode: Int
    ) {

        val connection =
            currentInputConnection
                ?: return


        connection.sendKeyEvent(
            KeyEvent(
                KeyEvent.ACTION_DOWN,
                keyCode
            )
        )


        connection.sendKeyEvent(
            KeyEvent(
                KeyEvent.ACTION_UP,
                keyCode
            )
        )
    }


    // =============================================================
    // 상태 초기화
    // =============================================================

    private fun clearMultiTap() {

        lastKeyCode =
            KeyEvent.KEYCODE_UNKNOWN

        lastKeyTime = 0L
    }


    private fun clearEnglishMultiTap() {

        lastEnglishKey =
            KeyEvent.KEYCODE_UNKNOWN

        lastEnglishTime = 0L
        englishCycleIndex = 0
    }


    private fun stopSymbolCycle() {

        symbolCycleActive = false
        symbolIndex = 0
        lastSymbolTime = 0L
    }


    private fun clearAllInputState() {
        cancelKoreanCommit()

        automata.reset()

        clearMultiTap()
        clearEnglishMultiTap()
        stopSymbolCycle()
    }


    // =============================================================
    // 텐키 여부
    // =============================================================

    private fun isTenKey(
        keyCode: Int
    ): Boolean {

        return when (keyCode) {

            KeyEvent.KEYCODE_NUMPAD_0,
            KeyEvent.KEYCODE_NUMPAD_1,
            KeyEvent.KEYCODE_NUMPAD_2,
            KeyEvent.KEYCODE_NUMPAD_3,
            KeyEvent.KEYCODE_NUMPAD_4,
            KeyEvent.KEYCODE_NUMPAD_5,
            KeyEvent.KEYCODE_NUMPAD_6,
            KeyEvent.KEYCODE_NUMPAD_7,
            KeyEvent.KEYCODE_NUMPAD_8,
            KeyEvent.KEYCODE_NUMPAD_9,

            KeyEvent.KEYCODE_NUMPAD_ADD,
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT,
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY,
            KeyEvent.KEYCODE_NUMPAD_DIVIDE,
            KeyEvent.KEYCODE_NUMPAD_DOT,
            KeyEvent.KEYCODE_NUMPAD_ENTER,

            KeyEvent.KEYCODE_NUM_LOCK,

            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_FORWARD_DEL -> true

            else -> false
        }
    }


    // =============================================================
    // 입력 시작/종료
    // =============================================================

    override fun onStartInput(
        attribute: EditorInfo?,
        restarting: Boolean
    ) {

        clearAllInputState()

        cancelAllLongPress()

        super.onStartInput(
            attribute,
            restarting
        )
    }


    override fun onFinishInput() {

        clearAllInputState()

        cancelAllLongPress()

        super.onFinishInput()
    }


    // =============================================================
    // Long Press 정리
    // =============================================================

    private fun cancelAllLongPress() {

        handler.removeCallbacks(
            enterLongPressRunnable
        )

        handler.removeCallbacks(
            slashLongPressRunnable
        )

        handler.removeCallbacks(
            spaceRepeatRunnable
        )

        handler.removeCallbacks(
            deleteRepeatRunnable
        )


        enterPressed = false
        enterLongPressTriggered = false

        slashPressed = false
        slashLongPressTriggered = false

        spacePressed = false
        spaceRepeatTriggered = false

        deletePressed = false
        deleteRepeatTriggered = false
    }


    override fun onDestroy() {

        cancelKoreanCommit()
        cancelAllLongPress()

        super.onDestroy()
    }
}
