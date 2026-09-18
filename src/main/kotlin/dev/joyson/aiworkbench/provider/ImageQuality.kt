package dev.joyson.aiworkbench.provider

/**
 * 포트가 표현하는 품질 눈금.
 *
 * 제품이 사용자에게 제공하는 눈금과 따로 둔다. 제품 메뉴는 "빠르게/보통/고품질" 처럼
 * 사용자가 고를 수 있는 말로 정해지고, 여기는 Provider 에게 전달 가능한 범위를 뜻한다.
 * 둘을 한 타입으로 합치면 제품 메뉴를 바꿀 때 포트가 따라 바뀐다.
 *
 * 각 어댑터가 자기 API 의 허용값으로 옮긴다.
 */
enum class ImageQuality {
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX,
    AUTO,
}
