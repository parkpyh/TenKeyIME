package com.umpire.tenkeyime

data class AutomataResult(
    val deleteCount: Int = 0,
    val insertText: String = ""
)

class HangeulAutomata {

    // ============================================================
    // 현재 음절 상태
    // ============================================================

    private var cho: Char? = null

    /*
     * 중요:
     *
     * 예전처럼 jung을 완성된 Char 하나만 저장하지 않는다.
     *
     * 실제 천지인 입력 순서를 그대로 저장한다.
     *
     * 예:
     *
     * ㅝ = "ㅡㆍㆍㅣ"
     * ㅘ = "ㆍㅡㅣㆍ"
     * ㅙ = "ㆍㅡㅣㆍㅣ"
     */
    private var vowelSequence: String = ""

    private var jong: Char? = null

    // 앞 음절의 받침 뒤에서 아직 확정되지 않은 다음 자음.
    // 예: 만 + ㅅ = 만ㅅ, 같은 키 재입력 = 많.
    private var pendingSecond: Char? = null

    /*
     * 현재 입력창 끝부분에서
     * 아직 다른 글자로 변경될 수 있는 문자열 길이
     */
    private var mutableLength = 0


    // ============================================================
    // 자음 다중탭
    // ============================================================

    private val cycle =
        mapOf(
            'ㅂ' to 'ㅍ',
            'ㅍ' to 'ㅃ',
            'ㅃ' to 'ㅂ',

            'ㅅ' to 'ㅎ',
            'ㅎ' to 'ㅆ',
            'ㅆ' to 'ㅅ',

            'ㅈ' to 'ㅊ',
            'ㅊ' to 'ㅉ',
            'ㅉ' to 'ㅈ',

            'ㄱ' to 'ㅋ',
            'ㅋ' to 'ㄲ',
            'ㄲ' to 'ㄱ',

            'ㄴ' to 'ㄹ',
            'ㄹ' to 'ㄴ',

            'ㄷ' to 'ㅌ',
            'ㅌ' to 'ㄸ',
            'ㄸ' to 'ㄷ',

            'ㅇ' to 'ㅁ',
            'ㅁ' to 'ㅇ'
        )


    private val base =
        mapOf(
            'ㅂ' to 'ㅂ',
            'ㅍ' to 'ㅂ',
            'ㅃ' to 'ㅂ',

            'ㅅ' to 'ㅅ',
            'ㅎ' to 'ㅅ',
            'ㅆ' to 'ㅅ',

            'ㅈ' to 'ㅈ',
            'ㅊ' to 'ㅈ',
            'ㅉ' to 'ㅈ',

            'ㄱ' to 'ㄱ',
            'ㅋ' to 'ㄱ',
            'ㄲ' to 'ㄱ',

            'ㄴ' to 'ㄴ',
            'ㄹ' to 'ㄴ',

            'ㄷ' to 'ㄷ',
            'ㅌ' to 'ㄷ',
            'ㄸ' to 'ㄷ',

            'ㅇ' to 'ㅇ',
            'ㅁ' to 'ㅇ'
        )


    // ============================================================
    // 물리 자음키별 고정 순환 순서
    // ============================================================

    private val consonantGroups =
        mapOf(
            'ㅂ' to listOf('ㅂ', 'ㅍ', 'ㅃ'),
            'ㅅ' to listOf('ㅅ', 'ㅎ', 'ㅆ'),
            'ㅈ' to listOf('ㅈ', 'ㅊ', 'ㅉ'),
            'ㄱ' to listOf('ㄱ', 'ㅋ', 'ㄲ'),
            'ㄴ' to listOf('ㄴ', 'ㄹ'),
            'ㄷ' to listOf('ㄷ', 'ㅌ', 'ㄸ'),
            'ㅇ' to listOf('ㅇ', 'ㅁ')
        )


    // ============================================================
    // 천지인 21개 모음
    // ============================================================

    private val vowelMap =
        mapOf(

            // ----------------------------------------------------
            // 기본 모음
            // ----------------------------------------------------

            "ㅣ" to 'ㅣ',

            "ㅣㆍ" to 'ㅏ',
            "ㅣㆍㆍ" to 'ㅑ',

            "ㆍㅣ" to 'ㅓ',
            "ㆍㆍㅣ" to 'ㅕ',

            "ㆍㅡ" to 'ㅗ',
            "ㆍㆍㅡ" to 'ㅛ',

            "ㅡㆍ" to 'ㅜ',
            "ㅡㆍㆍ" to 'ㅠ',

            "ㅡ" to 'ㅡ',


            // ----------------------------------------------------
            // ㅐ / ㅒ / ㅔ / ㅖ
            // ----------------------------------------------------

            "ㅣㆍㅣ" to 'ㅐ',

            "ㅣㆍㆍㅣ" to 'ㅒ',

            "ㆍㅣㅣ" to 'ㅔ',

            "ㆍㆍㅣㅣ" to 'ㅖ',


            // ----------------------------------------------------
            // ㅗ 계열
            //
            // ㅗ  = ㆍㅡ
            // ㅚ  = ㆍㅡㅣ
            // ㅘ  = ㆍㅡㅣㆍ
            // ㅙ  = ㆍㅡㅣㆍㅣ
            //
            // 실제 입력 중:
            //
            // 오 → 외 → 와 → 왜
            // ----------------------------------------------------

            "ㆍㅡㅣ" to 'ㅚ',

            "ㆍㅡㅣㆍ" to 'ㅘ',

            "ㆍㅡㅣㆍㅣ" to 'ㅙ',


            // ----------------------------------------------------
            // ㅜ 계열
            //
            // ㅜ  = ㅡㆍ
            // ㅟ  = ㅡㆍㅣ
            //
            // ㅝ  = ㅡㆍㆍㅣ
            // ㅞ  = ㅡㆍㆍㅣㅣ
            // ----------------------------------------------------

            "ㅡㆍㅣ" to 'ㅟ',

            "ㅡㆍㆍㅣ" to 'ㅝ',

            "ㅡㆍㆍㅣㅣ" to 'ㅞ',


            // ----------------------------------------------------
            // ㅢ
            // ----------------------------------------------------

            "ㅡㅣ" to 'ㅢ'
        )


    /*
     * 완성 모음뿐 아니라
     * 앞으로 완성 가능한 중간 입력인지 판단하기 위한 Prefix.
     *
     * 예:
     *
     * "ㆍ"      → 아직 모음은 아니지만 유효
     * "ㆍㆍ"    → ㅕ 또는 ㅛ 등이 될 수 있음
     * "ㅡㆍㆍ"  → 현재는 ㅠ지만 ㅣ를 추가하면 ㅝ
     */
    private val vowelPrefixes: Set<String> =
        buildSet {

            for (sequence in vowelMap.keys) {

                for (
                i in 1..sequence.length
                ) {

                    add(
                        sequence.substring(
                            0,
                            i
                        )
                    )
                }
            }
        }


    // ============================================================
    // 겹받침
    // ============================================================

    private val batchimCombine =
        mapOf(

            "ㄱㅅ" to 'ㄳ',

            "ㄴㅈ" to 'ㄵ',
            "ㄴㅎ" to 'ㄶ',

            "ㄹㄱ" to 'ㄺ',
            "ㄹㅁ" to 'ㄻ',
            "ㄹㅂ" to 'ㄼ',
            "ㄹㅅ" to 'ㄽ',
            "ㄹㅌ" to 'ㄾ',
            "ㄹㅍ" to 'ㄿ',
            "ㄹㅎ" to 'ㅀ',

            "ㅂㅅ" to 'ㅄ'
        )


    private val splitBatchim =
        mapOf(

            'ㄳ' to Pair(
                'ㄱ',
                'ㅅ'
            ),

            'ㄵ' to Pair(
                'ㄴ',
                'ㅈ'
            ),

            'ㄶ' to Pair(
                'ㄴ',
                'ㅎ'
            ),

            'ㄺ' to Pair(
                'ㄹ',
                'ㄱ'
            ),

            'ㄻ' to Pair(
                'ㄹ',
                'ㅁ'
            ),

            'ㄼ' to Pair(
                'ㄹ',
                'ㅂ'
            ),

            'ㄽ' to Pair(
                'ㄹ',
                'ㅅ'
            ),

            'ㄾ' to Pair(
                'ㄹ',
                'ㅌ'
            ),

            'ㄿ' to Pair(
                'ㄹ',
                'ㅍ'
            ),

            'ㅀ' to Pair(
                'ㄹ',
                'ㅎ'
            ),

            'ㅄ' to Pair(
                'ㅂ',
                'ㅅ'
            )
        )


    // ============================================================
    // 외부 진입
    // ============================================================

    fun process(
        input: Char,
        isVowel: Boolean,
        allowCycle: Boolean
    ): AutomataResult {

        return if (isVowel) {

            processVowel(
                input
            )

        } else {

            processConsonant(
                input,
                allowCycle
            )
        }
    }


    // ============================================================
    // 자음
    // ============================================================

    private fun processConsonant(input: Char, allowCycle: Boolean): AutomataResult {
        // 앞 음절 + 미확정 다음 자음 (예: 만ㅅ).
        pendingSecond?.let { pending ->
            if (allowCycle && sameGroup(pending, input)) {
                val next = cycle[pending] ?: input
                val combined = batchimCombine["${jong}${next}"]
                pendingSecond = null
                if (combined != null) {
                    jong = combined
                    return replaceCurrent(composeCurrent())
                }
                pendingSecond = next
                return replaceCurrent(composeCurrent() + next)
            }

            // 다른 자음이 오면 가능한 겹받침을 완성한 후 새 초성 시작.
            val combined = batchimCombine["${jong}${pending}"]
            val fixed = if (combined != null) {
                jong = combined
                composeCurrent()
            } else {
                composeCurrent() + pending
            }
            pendingSecond = null
            cho = input
            vowelSequence = ""
            jong = null
            return replaceWithFixedPrefix(fixed, input.toString())
        }

        if (vowelSequence.isEmpty()) {
            val currentCho = cho
            if (allowCycle && currentCho != null && sameGroup(currentCho, input)) {
                cho = cycle[currentCho] ?: input
                return replaceCurrent(composeCurrent())
            }
            cho = input
            vowelSequence = ""
            jong = null
            return appendNewMutable(input.toString())
        }

        if (jong == null) {
            if (isValidFinal(input)) {
                jong = input
                return replaceCurrent(composeCurrent())
            }
            cho = input
            vowelSequence = ""
            jong = null
            return appendNewMutable(input.toString())
        }

        val currentJong = jong ?: return AutomataResult()
        val split = splitBatchim[currentJong]

        // 겹받침 상태에서도 두 번째 자음의 멀티탭을 계속 허용한다.
        if (allowCycle && split != null && sameGroup(split.second, input)) {
            jong = split.first
            val next = cycle[split.second] ?: input
            val combined = batchimCombine["${jong}${next}"]
            if (combined != null) {
                jong = combined
                return replaceCurrent(composeCurrent())
            }
            pendingSecond = next
            return replaceCurrent(composeCurrent() + next)
        }

        // 기존 단일 받침의 멀티탭은 그대로 유지한다.
        if (allowCycle && split == null && sameGroup(currentJong, input)) {
            val next = cycle[currentJong] ?: input
            if (isValidFinal(next)) {
                jong = next
                return replaceCurrent(composeCurrent())
            }
            val fixed = compose(cho, vowelSequence, null)
            cho = next
            vowelSequence = ""
            jong = null
            return replaceWithFixedPrefix(fixed, next.toString())
        }

        // 첫 키에서는 겹받침을 자동 건너뛰지 않는다.
        // 앞 음절을 아직 수정할 수 있게 유지하여 다음 키/타임아웃에서 결정한다.
        pendingSecond = input
        return replaceCurrent(composeCurrent() + input)
    }

    // 서비스의 800ms 타이머가 만료될 때 호출.
    // 결합 가능한 임시 자음은 겹받침으로 완성하고, 나머지는 다음 초성으로 둔다.
    fun onConsonantTimeout(): AutomataResult? {
        val pending = pendingSecond
        if (pending != null) {
            val combined = batchimCombine["${jong}${pending}"]
            pendingSecond = null
            if (combined != null) {
                jong = combined
                val result = replaceCurrent(composeCurrent())
                reset() // 완성된 겹받침을 확정: 다음 모음이 받침을 옮기지 않음.
                return result
            }
            // 만ㅅ 같은 비결합 상태는 '만'을 확정하고 'ㅅ'은 다음 초성으로 유지.
            cho = pending
            vowelSequence = ""
            jong = null
            mutableLength = 1
            return null
        }
        // 초성 단독은 이후 모음을 받을 수 있어야 한다.
        if (vowelSequence.isNotEmpty() && jong != null) reset()
        return null
    }

    // ============================================================
    // 모음
    // ============================================================

    private fun processVowel(
        input: Char
    ): AutomataResult {

        // 임시 다음 자음에 모음이 오면 다음 음절의 초성으로 사용한다.
        pendingSecond?.let { pending ->
            val fixed = composeCurrent()
            pendingSecond = null
            cho = pending
            vowelSequence = input.toString()
            jong = null
            return replaceWithFixedPrefix(fixed, composeCurrent())
        }


        // --------------------------------------------------------
        // 받침이 있는 음절에 모음 입력
        //
        // 각 + ㅏ → 가가
        //
        // 값 + ㅣ → 갑시...
        // --------------------------------------------------------

        if (jong != null) {

            val currentJong =
                jong!!


            val split =
                splitBatchim[
                    currentJong
                ]


            val fixedFinal: Char?
            val nextCho: Char


            if (split != null) {

                fixedFinal =
                    split.first

                nextCho =
                    split.second

            } else {

                fixedFinal =
                    null

                nextCho =
                    currentJong
            }


            val fixedSyllable =
                compose(
                    cho,
                    vowelSequence,
                    fixedFinal
                )


            cho =
                nextCho

            vowelSequence =
                input.toString()

            jong =
                null


            val newMutable =
                composeCurrent()


            return replaceWithFixedPrefix(
                fixedText = fixedSyllable,
                mutableText = newMutable
            )
        }


        // --------------------------------------------------------
        // 현재 모음이 없음
        // --------------------------------------------------------

        if (vowelSequence.isEmpty()) {

            vowelSequence =
                input.toString()


            return replaceCurrent(
                composeCurrent()
            )
        }


        // --------------------------------------------------------
        // 핵심:
        //
        // 기존 입력 시퀀스 + 새 천지인 키
        //
        // 예:
        //
        // ㅡ
        // ㅡㆍ
        // ㅡㆍㆍ
        // ㅡㆍㆍㅣ
        //
        // → ㅝ
        // --------------------------------------------------------

        val candidate =
            vowelSequence +
                    input


        // --------------------------------------------------------
        // 앞으로 유효한 모음이 될 수 있는 입력
        // --------------------------------------------------------

        if (
            vowelPrefixes.contains(
                candidate
            )
        ) {

            vowelSequence =
                candidate


            return replaceCurrent(
                composeCurrent()
            )
        }


        // --------------------------------------------------------
        // 기존 모음과 결합할 수 없음
        //
        // 기존 음절 확정 후
        // 새로운 모음 시작
        // --------------------------------------------------------

        cho =
            null

        vowelSequence =
            input.toString()

        jong =
            null


        return appendNewMutable(
            composeCurrent()
        )
    }


    // ============================================================
    // 스마트 겹받침 후보 탐색
    // ============================================================

    private fun findNextCombinableFinal(
        currentFinal: Char,
        keyBase: Char
    ): Char? {

        val group =
            consonantGroups[keyBase]
                ?: return null

        // 첫 후보는 이미 direct combine에서 검사했으므로
        // 두 번째 후보부터 순서대로 확인한다.
        for (candidate in group.drop(1)) {

            val combined =
                batchimCombine[
                    "$currentFinal$candidate"
                ]

            if (combined != null) {
                return combined
            }
        }

        return null
    }


    // ============================================================
    // 현재 작성 중 한글 역순 삭제
    //
    // 예:
    // 않 → 안 → 아 → ㅇ → 삭제
    // 읽 → 일 → 이 → ㅇ → 삭제
    //
    // 이미 확정된 앞쪽 텍스트는 여기서 건드리지 않는다.
    // ============================================================

    fun backspace(): AutomataResult? {

        if (mutableLength <= 0) {
            return null
        }

        pendingSecond?.let {
            pendingSecond = null
            return replaceCurrent(composeCurrent())
        }

        val currentJong =
            jong

        if (currentJong != null) {

            val split =
                splitBatchim[currentJong]

            jong =
                if (split != null) {
                    split.first
                } else {
                    null
                }

            return replaceCurrent(
                composeCurrent()
            )
        }

        if (vowelSequence.isNotEmpty()) {

            vowelSequence =
                vowelSequence.dropLast(1)

            return replaceCurrent(
                composeCurrent()
            )
        }

        if (cho != null) {

            cho = null

            return replaceCurrent(
                ""
            )
        }

        return null
    }


    // ============================================================
    // 현재 조합 문자열 생성
    // ============================================================

    private fun composeCurrent():
            String {

        return compose(
            cho,
            vowelSequence,
            jong
        )
    }


    // ============================================================
    // 한글 음절 조합
    // ============================================================

    private fun compose(
        cho: Char?,
        vowelSequence: String,
        jong: Char?
    ): String {


        // --------------------------------------------------------
        // 아무것도 없음
        // --------------------------------------------------------

        if (
            cho == null &&
            vowelSequence.isEmpty()
        ) {

            return ""
        }


        // --------------------------------------------------------
        // 초성만 있음
        // --------------------------------------------------------

        if (vowelSequence.isEmpty()) {

            return cho
                ?.toString()
                ?: ""
        }


        // --------------------------------------------------------
        // 현재 천지인 Sequence가
        // 완성된 중성인지 확인
        // --------------------------------------------------------

        val jung =
            vowelMap[
                vowelSequence
            ]


        // --------------------------------------------------------
        // 아직 완성되지 않은 중간상태
        //
        // 예:
        // ㆍ
        // ㆍㆍ
        // --------------------------------------------------------

        if (jung == null) {

            val intermediate =
                displayIntermediateVowel(
                    vowelSequence
                )


            return buildString {

                if (cho != null) {
                    append(cho)
                }

                append(
                    intermediate
                )

                if (jong != null) {
                    append(jong)
                }
            }
        }


        // --------------------------------------------------------
        // 초성 없는 독립 모음
        // --------------------------------------------------------

        if (cho == null) {

            return jung
                .toString()
        }


        // --------------------------------------------------------
        // Unicode 조합
        // --------------------------------------------------------

        val choList =
            "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"


        val jungList =
            "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"


        val jongList =
            " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"


        val choIndex =
            choList.indexOf(
                cho
            )


        val jungIndex =
            jungList.indexOf(
                jung
            )


        if (
            choIndex < 0 ||
            jungIndex < 0
        ) {

            return buildString {

                append(cho)

                append(jung)

                if (jong != null) {
                    append(jong)
                }
            }
        }


        val jongIndex =
            if (jong == null) {

                0

            } else {

                jongList
                    .indexOf(
                        jong
                    )
                    .coerceAtLeast(
                        0
                    )
            }


        val unicode =
            0xAC00 +
                    (
                            choIndex *
                                    21 *
                                    28
                            ) +
                    (
                            jungIndex *
                                    28
                            ) +
                    jongIndex


        return unicode
            .toChar()
            .toString()
    }


    // ============================================================
    // 천지인 중간상태 표시
    // ============================================================

    private fun displayIntermediateVowel(
        sequence: String
    ): String {

        return when (sequence) {

            "ㆍ" ->
                "ㆍ"

            /*
             * 두 점은 화면에서는
             * 천지인 쌍점처럼 표시
             */
            "ㆍㆍ" ->
                "ᆢ"

            else ->
                sequence
        }
    }


    // ============================================================
    // 같은 물리 자음 그룹
    // ============================================================

    private fun sameGroup(
        a: Char,
        b: Char
    ): Boolean {

        val aBase =
            base[a]

        val bBase =
            base[b]


        return (
                aBase != null &&
                        bBase != null &&
                        aBase ==
                        bBase
                )
    }


    // ============================================================
    // 종성 가능 여부
    // ============================================================

    private fun isValidFinal(
        c: Char
    ): Boolean {

        return c in
                "ㄱㄲㄴㄷㄹㅁㅂㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
    }


    // ============================================================
    // 현재 조합 교체
    // ============================================================

    private fun replaceCurrent(
        text: String
    ): AutomataResult {

        val oldLength =
            mutableLength


        mutableLength =
            text.length


        return AutomataResult(
            deleteCount = oldLength,
            insertText = text
        )
    }


    // ============================================================
    // 현재 글자는 확정하고 새 입력 시작
    // ============================================================

    private fun appendNewMutable(
        text: String
    ): AutomataResult {

        mutableLength =
            text.length


        return AutomataResult(
            deleteCount = 0,
            insertText = text
        )
    }


    // ============================================================
    // 기존 mutable 영역을
    //
    // [확정 문자열] + [새 mutable 문자열]
    //
    // 로 변경
    // ============================================================

    private fun replaceWithFixedPrefix(
        fixedText: String,
        mutableText: String
    ): AutomataResult {

        val oldLength =
            mutableLength


        mutableLength =
            mutableText.length


        return AutomataResult(
            deleteCount = oldLength,
            insertText =
                fixedText +
                        mutableText
        )
    }


    // ============================================================
    // 초기화
    // ============================================================

    fun reset() {

        cho =
            null

        vowelSequence =
            ""

        jong =
            null

        pendingSecond = null

        mutableLength =
            0
    }
}
