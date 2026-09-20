package dev.joyson.aiworkbench.provider

/**
 * 어댑터가 응답을 읽으면서 **자기 타입으로 매핑하지 못한 필드들**.
 *
 * Provider 마다 응답 모양은 다르지만 "모르는 것이 왔다" 는 사정은 같아서 여기 둔다.
 * 버리더라도 **버렸다는 사실은 알아야** 한다 — 지금 모르는 필드가 나중에 단가의 축일 수 있다.
 *
 * [toString] 이 값을 잘라서 낸다. 자를지 말지를 부르는 쪽이 기억해야 한다면 언젠가 잊고,
 * 그때 로그로 나가는 것이 수 MB 짜리 이미지다 — **모르는 필드는 크기도 모른다.**
 * 선언해 둔 필드가 여기 못 오는 것은 그 필드를 선언했기 때문이지,
 * 모르는 자리에 큰 값이 못 온다는 뜻이 아니다.
 *
 * 원값이 필요하면 [asMap] 으로 **명시적으로** 꺼낸다. 로그에는 이 객체를 그대로 넘긴다.
 */
class UnknownFields {

    /** 온 순서를 지킨다. 응답에서 어디쯤 나왔는지가 단서가 된다. */
    private val values = LinkedHashMap<String, Any?>()

    fun put(name: String, value: Any?) {
        values[name] = value
    }

    val isEmpty: Boolean get() = values.isEmpty()

    val isNotEmpty: Boolean get() = values.isNotEmpty()

    /** 이름만 본다. 무엇이 왔는지 가릴 때는 이것으로 충분하고, 크기 걱정이 없다. */
    val names: Set<String> get() = values.keys.toSet()

    /** 원값. 저장하거나 값을 실제로 쓸 때만 부른다. **로그에는 넘기지 않는다.** */
    fun asMap(): Map<String, Any?> = values.toMap()

    /**
     * 이름은 전부, 값은 잘라서 낸다.
     *
     * 이름이 승격 판단의 재료라 하나도 빠뜨리지 않고, 잘린 길이를 함께 적어
     * "얼마나 큰 것이 왔는지" 까지 드러낸다.
     */
    override fun toString(): String =
        values.entries.joinToString(", ", prefix = "{", postfix = "}") { (name, value) ->
            val rendered = value?.toString() ?: "null"
            if (rendered.length <= VALUE_LIMIT) {
                "$name=$rendered"
            } else {
                "$name=${rendered.take(VALUE_LIMIT)}…(${rendered.length}자)"
            }
        }

    companion object {
        /** 종류를 알아보고 크기를 가늠하기에 충분한 길이. 값을 다시 쓰려는 것이 아니다. */
        const val VALUE_LIMIT = 80
    }
}
