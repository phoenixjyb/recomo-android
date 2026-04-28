package com.recomo.common.chat

/**
 * Client-side prompt template catalogue.
 *
 * Shown to the user as a chip row above the chat input when the conversation
 * is empty, so they have a starting surface before the backend's
 * [PromptHint] welcome message arrives. Each template fills the input with
 * a suggested starter prompt; the user can edit it before sending.
 *
 * Templates are keyed by a stable [id] so a backend-supplied
 * `prompt_hint.template_ids` can reference them (e.g. to highlight
 * relevant chips when the server returns a clarification).
 *
 * Strings are hard-coded here rather than routed through `strings.xml` because:
 * - The catalogue is owned by :common (no Android resource access)
 * - The same template needs to ship in both EN and ZH forms so the UI can
 *   pick based on the active locale
 * - Future plan: allow the backend to ship a remote catalogue via a new
 *   `prompt_catalogue` message that merges with this fallback
 */
data class PromptTemplate(
    val id: String,
    val category: String,
    val labelEn: String,
    val labelZh: String,
    val promptEn: String,
    val promptZh: String
)

enum class PromptLanguage { EN, ZH }

object PromptTemplates {

    val all: List<PromptTemplate> = listOf(
        PromptTemplate(
            id = "cinematic_sweep",
            category = "Cinematic",
            labelEn = "Cinematic sweep",
            labelZh = "电影感横摇",
            promptEn = "Cinematic sweep across the lobby, 25 seconds, smooth slow-in slow-out",
            promptZh = "做一个 lobby 的 cinematic sweep，25 秒以内，节奏缓入缓出"
        ),
        PromptTemplate(
            id = "product_360",
            category = "Product",
            labelEn = "Product 360°",
            labelZh = "产品环绕",
            promptEn = "Slow 360° orbit around the coffee machine, 30 seconds, keep the subject centered",
            promptZh = "围绕咖啡机做一个 360° 产品展示，30 秒，保持主体居中"
        ),
        PromptTemplate(
            id = "walk_through",
            category = "Establishing",
            labelEn = "Walk through",
            labelZh = "走位穿越",
            promptEn = "Walk from the entrance to the elevator lobby, keep the camera level, 20 seconds",
            promptZh = "从大门走到电梯厅，过程中保持镜头水平，20 秒"
        ),
        PromptTemplate(
            id = "reveal_push",
            category = "Cinematic",
            labelEn = "Reveal push-in",
            labelZh = "推镜揭示",
            promptEn = "Slow dolly push-in on the reception desk with rising tilt, 15 seconds",
            promptZh = "对着前台慢推镜，镜头随之上抬，15 秒"
        ),
        PromptTemplate(
            id = "greeting",
            category = "Action",
            labelEn = "Greeting",
            labelZh = "迎宾动作",
            promptEn = "Move 2 metres toward the guest, raise the arm into a wave, hold briefly, return to idle",
            promptZh = "朝来宾前进 2 米，抬臂挥手，短暂停留后回到待机姿态"
        ),
    )

    fun label(template: PromptTemplate, lang: PromptLanguage): String =
        if (lang == PromptLanguage.ZH) template.labelZh else template.labelEn

    fun prompt(template: PromptTemplate, lang: PromptLanguage): String =
        if (lang == PromptLanguage.ZH) template.promptZh else template.promptEn

    /** Lookup by id — used when a server-side prompt_hint references template_ids. */
    fun byId(id: String): PromptTemplate? = all.firstOrNull { it.id == id }
}
