package com.fitcoach.app.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ExerciseTemplate(
    val id: String,
    val name: String,
    val sets: List<SetTemplate>,
    val restSeconds: Int,
    val tip: String = "",
    val youtubeSearchQuery: String = ""
)

data class SetTemplate(
    val reps: Int,
    val weight: String
)

data class WarmupItem(val name: String, val durationSeconds: Int)
data class CooldownItem(val name: String, val durationSeconds: Int)

data class WorkoutTemplate(
    val key: String,
    val phaseName: String,
    val weekRange: IntRange,
    val exercises: List<ExerciseTemplate>,
    val warmup: List<WarmupItem> = defaultWarmup,
    val cooldown: List<CooldownItem> = defaultCooldown
)

private val defaultWarmup = listOf(
    WarmupItem("Ходьба на месте", 120),
    WarmupItem("Круговые движения руками", 60),
    WarmupItem("Наклоны в стороны", 60),
    WarmupItem("Вращение таза", 60),
    WarmupItem("Приседания без отягощения", 60)
)

private val defaultCooldown = listOf(
    CooldownItem("Растяжка квадрицепса", 60),
    CooldownItem("Растяжка поясницы лёжа", 60),
    CooldownItem("Поза ребёнка", 60),
    CooldownItem("Кошка-корова", 60),
    CooldownItem("Растяжка грудных", 60)
)

object WorkoutPlan {

    private val workouts: Map<String, WorkoutTemplate> = mapOf(
        // ФАЗА I: Недели 1-4
        "A1" to WorkoutTemplate(
            key = "A1", phaseName = "Фаза I — Верх тела", weekRange = 1..4,
            exercises = listOf(
                ExerciseTemplate("a1_1", "Жим гантелей лёжа на горизонтальной скамье",
                    listOf(SetTemplate(12, "20кг"), SetTemplate(10, "22кг"), SetTemplate(10, "22кг"), SetTemplate(8, "24кг")),
                    90, "Лопатки сведены, спина нейтральная", "жим гантелей лёжа техника"),
                ExerciseTemplate("a1_2", "Тяга верхнего блока широким хватом",
                    listOf(SetTemplate(12, "40кг"), SetTemplate(10, "45кг"), SetTemplate(10, "45кг"), SetTemplate(8, "50кг")),
                    90, "Тяни к верхней части груди, не раскачивайся", "тяга верхнего блока техника"),
                ExerciseTemplate("a1_3", "Жим гантелей сидя (плечи)",
                    listOf(SetTemplate(12, "14кг"), SetTemplate(10, "16кг"), SetTemplate(10, "16кг")),
                    75, "Нейтральный хват, не поднимай плечи", "жим гантелей сидя плечи техника"),
                ExerciseTemplate("a1_4", "Разведение гантелей в стороны",
                    listOf(SetTemplate(15, "8кг"), SetTemplate(12, "9кг"), SetTemplate(12, "9кг")),
                    60, "Лёгкий вес, контроль", "разведение гантелей в стороны техника"),
                ExerciseTemplate("a1_5", "Сгибание рук с гантелями",
                    listOf(SetTemplate(12, "14кг"), SetTemplate(10, "16кг"), SetTemplate(10, "16кг")),
                    60, "Локти прижаты к телу", "сгибание рук гантели техника"),
                ExerciseTemplate("a1_6", "Французский жим лёжа",
                    listOf(SetTemplate(12, "14кг"), SetTemplate(10, "16кг"), SetTemplate(10, "16кг")),
                    60, "Локти не расходятся", "французский жим лёжа техника"),
                ExerciseTemplate("a1_7", "Планка",
                    listOf(SetTemplate(1, "30 сек"), SetTemplate(1, "30 сек"), SetTemplate(1, "30 сек")),
                    45, "Ровная линия от головы до пяток", "планка техника выполнения")
            )
        ),
        "B1" to WorkoutTemplate(
            key = "B1", phaseName = "Фаза I — Низ тела", weekRange = 1..4,
            exercises = listOf(
                ExerciseTemplate("b1_1", "Сплит-приседания с гантелями (болгарский присед)",
                    listOf(SetTemplate(10, "16кг"), SetTemplate(10, "18кг"), SetTemplate(8, "20кг"), SetTemplate(8, "20кг")),
                    90, "Заднее колено не касается пола, туловище прямо", "болгарский присед техника"),
                ExerciseTemplate("b1_2", "Жим ногами в тренажёре",
                    listOf(SetTemplate(15, "80кг"), SetTemplate(12, "90кг"), SetTemplate(12, "90кг"), SetTemplate(10, "100кг")),
                    90, "Стопы на ширине плеч, колени вдоль стоп", "жим ногами тренажёр техника"),
                ExerciseTemplate("b1_3", "Сгибание ног лёжа",
                    listOf(SetTemplate(15, "30кг"), SetTemplate(12, "35кг"), SetTemplate(12, "35кг")),
                    75, "Без рывков, медленно опускай", "сгибание ног лёжа техника"),
                ExerciseTemplate("b1_4", "Гиперэкстензия без веса",
                    listOf(SetTemplate(15, "б/в"), SetTemplate(15, "б/в"), SetTemplate(12, "б/в")),
                    60, "Не переразгибай поясницу! До параллели с полом", "гиперэкстензия без веса техника"),
                ExerciseTemplate("b1_5", "Подъём на носки стоя",
                    listOf(SetTemplate(20, "б/в"), SetTemplate(20, "б/в"), SetTemplate(20, "б/в")),
                    45, "Полная амплитуда", "подъём на носки стоя техника"),
                ExerciseTemplate("b1_6", "Ягодичный мостик",
                    listOf(SetTemplate(20, "б/в"), SetTemplate(15, "б/в"), SetTemplate(15, "б/в")),
                    60, "Сжимай ягодицы в верхней точке", "ягодичный мостик техника")
            )
        ),
        "C1" to WorkoutTemplate(
            key = "C1", phaseName = "Фаза I — Комплексная", weekRange = 1..4,
            exercises = listOf(
                ExerciseTemplate("c1_1", "Тяга горизонтального блока к поясу",
                    listOf(SetTemplate(12, "45кг"), SetTemplate(10, "50кг"), SetTemplate(10, "50кг"), SetTemplate(8, "55кг")),
                    90, "Тяни к пупку, локти вдоль тела", "тяга горизонтального блока техника"),
                ExerciseTemplate("c1_2", "Разведение гантелей лёжа (грудь)",
                    listOf(SetTemplate(12, "16кг"), SetTemplate(10, "18кг"), SetTemplate(10, "18кг")),
                    75, "Небольшой изгиб в локтях", "разведение гантелей лёжа техника"),
                ExerciseTemplate("c1_3", "Отведение руки с гантелью в наклоне",
                    listOf(SetTemplate(12, "12кг"), SetTemplate(10, "14кг"), SetTemplate(10, "14кг")),
                    60, "Рука до параллели с полом", "отведение руки с гантелью техника"),
                ExerciseTemplate("c1_4", "Разгибание ног в тренажёре",
                    listOf(SetTemplate(15, "30кг"), SetTemplate(12, "35кг"), SetTemplate(12, "35кг")),
                    75, "Полная амплитуда, без рывков", "разгибание ног тренажёр техника"),
                ExerciseTemplate("c1_5", "Скручивания на блоке (пресс)",
                    listOf(SetTemplate(15, "15кг"), SetTemplate(12, "17кг"), SetTemplate(12, "17кг")),
                    60, "Без осевой нагрузки, только сгибание туловища", "скручивания на блоке пресс техника"),
                ExerciseTemplate("c1_6", "Планка на боку",
                    listOf(SetTemplate(1, "20 сек"), SetTemplate(1, "20 сек"), SetTemplate(1, "20 сек")),
                    45, "Бёдра не провисают", "боковая планка техника")
            )
        ),

        // ФАЗА II: Недели 5-8
        "A2" to WorkoutTemplate(
            key = "A2", phaseName = "Фаза II — Верх тела", weekRange = 5..8,
            exercises = listOf(
                ExerciseTemplate("a2_1", "Жим штанги лёжа",
                    listOf(SetTemplate(10, "50кг"), SetTemplate(8, "55кг"), SetTemplate(8, "55кг"), SetTemplate(6, "60кг")),
                    120, "Лопатки сведены, арка минимальная", "жим штанги лёжа техника"),
                ExerciseTemplate("a2_2", "Подтягивания с резиной или в гравитроне",
                    listOf(SetTemplate(8, "помощь -20кг"), SetTemplate(6, "помощь -15кг"), SetTemplate(6, "помощь -15кг")),
                    120, "Полная амплитуда, грудь к перекладине", "подтягивания техника"),
                ExerciseTemplate("a2_3", "Жим штанги стоя (армейский)",
                    listOf(SetTemplate(10, "30кг"), SetTemplate(8, "35кг"), SetTemplate(8, "35кг")),
                    90, "Нет переразгибания поясницы", "армейский жим техника"),
                ExerciseTemplate("a2_4", "Тяга штанги в наклоне",
                    listOf(SetTemplate(10, "40кг"), SetTemplate(8, "45кг"), SetTemplate(8, "45кг"), SetTemplate(6, "50кг")),
                    90, "Спина параллельна полу, нейтральная", "тяга штанги в наклоне техника"),
                ExerciseTemplate("a2_5", "Подъём гантелей на бицепс Хаммер",
                    listOf(SetTemplate(12, "16кг"), SetTemplate(10, "18кг"), SetTemplate(10, "18кг")),
                    60, "Нейтральный хват", "сгибание хаммер бицепс техника"),
                ExerciseTemplate("a2_6", "Отжимания на брусьях",
                    listOf(SetTemplate(10, "б/в"), SetTemplate(8, "б/в"), SetTemplate(8, "б/в")),
                    75, "Наклон вперёд для акцента на груди", "отжимания на брусьях техника")
            )
        ),
        "B2" to WorkoutTemplate(
            key = "B2", phaseName = "Фаза II — Низ тела", weekRange = 5..8,
            exercises = listOf(
                ExerciseTemplate("b2_1", "Приседания в Смите (осторожно)",
                    listOf(SetTemplate(10, "40кг"), SetTemplate(8, "50кг"), SetTemplate(8, "50кг"), SetTemplate(6, "60кг")),
                    120, "Стопы чуть вперёд, спина прямая, колени не за носки", "приседания смит техника"),
                ExerciseTemplate("b2_2", "Жим ногами под углом 45°",
                    listOf(SetTemplate(12, "100кг"), SetTemplate(10, "110кг"), SetTemplate(10, "110кг"), SetTemplate(8, "120кг")),
                    90, "Колени вдоль стоп", "жим ногами 45 техника"),
                ExerciseTemplate("b2_3", "Румынская тяга с гантелями",
                    listOf(SetTemplate(12, "24кг"), SetTemplate(10, "26кг"), SetTemplate(10, "26кг"), SetTemplate(8, "28кг")),
                    90, "Нейтральная спина, опускай до голени", "румынская тяга гантели техника"),
                ExerciseTemplate("b2_4", "Гиперэкстензия с лёгким весом",
                    listOf(SetTemplate(12, "5кг"), SetTemplate(10, "5кг"), SetTemplate(10, "5кг")),
                    75, "Не более 5кг! Контроль поясницы", "гиперэкстензия с весом техника"),
                ExerciseTemplate("b2_5", "Выпады ходьбой с гантелями",
                    listOf(SetTemplate(10, "14кг"), SetTemplate(10, "14кг"), SetTemplate(10, "14кг")),
                    75, "10 шагов на каждую ногу", "выпады ходьбой техника"),
                ExerciseTemplate("b2_6", "Подъём на носки в тренажёре",
                    listOf(SetTemplate(20, "40кг"), SetTemplate(15, "50кг"), SetTemplate(15, "50кг")),
                    45, "Пауза внизу", "подъём на носки тренажёр техника")
            )
        ),
        "C2" to WorkoutTemplate(
            key = "C2", phaseName = "Фаза II — Верх тела B", weekRange = 5..8,
            exercises = listOf(
                ExerciseTemplate("c2_1", "Жим гантелей на наклонной скамье (30°)",
                    listOf(SetTemplate(10, "22кг"), SetTemplate(8, "24кг"), SetTemplate(8, "24кг"), SetTemplate(6, "26кг")),
                    90, "Акцент на верх груди", "жим гантелей наклонная скамья техника"),
                ExerciseTemplate("c2_2", "Тяга Т-образной штанги",
                    listOf(SetTemplate(10, "40кг"), SetTemplate(8, "45кг"), SetTemplate(8, "45кг")),
                    90, "Спина прямая, тяни к животу", "тяга т-грифа техника"),
                ExerciseTemplate("c2_3", "Махи гантелями вперёд",
                    listOf(SetTemplate(12, "10кг"), SetTemplate(10, "12кг"), SetTemplate(10, "12кг")),
                    60, "Контроль опускания", "махи гантелями вперёд техника"),
                ExerciseTemplate("c2_4", "Разгибание на трицепс с верхнего блока",
                    listOf(SetTemplate(15, "20кг"), SetTemplate(12, "22кг"), SetTemplate(12, "22кг")),
                    60, "Локти зафиксированы", "разгибание трицепс блок техника"),
                ExerciseTemplate("c2_5", "Сгибание рук на нижнем блоке",
                    listOf(SetTemplate(15, "20кг"), SetTemplate(12, "22кг"), SetTemplate(12, "22кг")),
                    60, "Полная амплитуда", "сгибание рук нижний блок техника"),
                ExerciseTemplate("c2_6", "Велосипед (пресс лёжа)",
                    listOf(SetTemplate(20, "б/в"), SetTemplate(20, "б/в"), SetTemplate(20, "б/в")),
                    45, "20 повторений на каждую сторону", "упражнение велосипед пресс техника")
            )
        ),
        "D2" to WorkoutTemplate(
            key = "D2", phaseName = "Фаза II — Функциональная", weekRange = 5..8,
            exercises = listOf(
                ExerciseTemplate("d2_1", "Тяга горизонтального блока одной рукой",
                    listOf(SetTemplate(12, "25кг"), SetTemplate(10, "27кг"), SetTemplate(10, "27кг")),
                    75, "Вращение туловища", "тяга одной рукой горизонтальный блок"),
                ExerciseTemplate("d2_2", "Жим гантели одной рукой лёжа",
                    listOf(SetTemplate(12, "22кг"), SetTemplate(10, "24кг"), SetTemplate(10, "24кг")),
                    75, "Стабилизация кора", "жим гантели одной рукой лёжа"),
                ExerciseTemplate("d2_3", "Становая тяга с гантелями на одной ноге (без спинальной нагрузки)",
                    listOf(SetTemplate(10, "16кг"), SetTemplate(10, "16кг"), SetTemplate(10, "16кг")),
                    75, "Нейтральная спина, баланс", "становая одной ноге гантели техника"),
                ExerciseTemplate("d2_4", "Ягодичный мостик с весом",
                    listOf(SetTemplate(15, "20кг"), SetTemplate(12, "25кг"), SetTemplate(12, "25кг")),
                    60, "Гантель на бёдрах", "ягодичный мостик с гантелью техника"),
                ExerciseTemplate("d2_5", "Планка с касанием плеч",
                    listOf(SetTemplate(10, "б/в"), SetTemplate(10, "б/в"), SetTemplate(10, "б/в")),
                    45, "10 касаний каждой рукой", "планка с касанием плеч техника"),
                ExerciseTemplate("d2_6", "Фермерская ходьба с гантелями",
                    listOf(SetTemplate(1, "24кг"), SetTemplate(1, "24кг"), SetTemplate(1, "24кг")),
                    60, "30 метров на подход, спина прямая", "фермерская ходьба техника")
            )
        ),

        // ФАЗА III: Недели 9-12
        "A3" to WorkoutTemplate(
            key = "A3", phaseName = "Фаза III — Верх силовой", weekRange = 9..12,
            exercises = listOf(
                ExerciseTemplate("a3_1", "Жим штанги лёжа",
                    listOf(SetTemplate(6, "65кг"), SetTemplate(5, "70кг"), SetTemplate(5, "70кг"), SetTemplate(4, "75кг"), SetTemplate(4, "75кг")),
                    150, "Максимальная концентрация", "жим штанги лёжа техника"),
                ExerciseTemplate("a3_2", "Подтягивания (с добавлением веса если нужно)",
                    listOf(SetTemplate(8, "+5кг"), SetTemplate(6, "+5кг"), SetTemplate(6, "+5кг"), SetTemplate(6, "б/в")),
                    120, "Полная амплитуда", "подтягивания с весом техника"),
                ExerciseTemplate("a3_3", "Жим штанги стоя",
                    listOf(SetTemplate(8, "40кг"), SetTemplate(6, "45кг"), SetTemplate(6, "45кг"), SetTemplate(5, "47кг")),
                    120, "Кор напряжён", "армейский жим штанга техника"),
                ExerciseTemplate("a3_4", "Тяга штанги в наклоне",
                    listOf(SetTemplate(8, "55кг"), SetTemplate(6, "60кг"), SetTemplate(6, "60кг"), SetTemplate(5, "62кг")),
                    120, "Нейтральная спина", "тяга штанги наклон техника"),
                ExerciseTemplate("a3_5", "Отжимания на брусьях с весом",
                    listOf(SetTemplate(8, "+10кг"), SetTemplate(6, "+10кг"), SetTemplate(6, "+10кг")),
                    90, "Контроль", "отжимания брусья с весом техника"),
                ExerciseTemplate("a3_6", "Скручивания с роликом (пресс)",
                    listOf(SetTemplate(10, "б/в"), SetTemplate(8, "б/в"), SetTemplate(8, "б/в")),
                    60, "От стены, контроль поясницы", "ролик для пресса техника")
            )
        ),
        "B3" to WorkoutTemplate(
            key = "B3", phaseName = "Фаза III — Низ силовой", weekRange = 9..12,
            exercises = listOf(
                ExerciseTemplate("b3_1", "Приседания в Смите",
                    listOf(SetTemplate(8, "70кг"), SetTemplate(6, "80кг"), SetTemplate(5, "85кг"), SetTemplate(4, "90кг"), SetTemplate(4, "90кг")),
                    150, "Глубина — параллель", "приседания смит силовые техника"),
                ExerciseTemplate("b3_2", "Жим ногами",
                    listOf(SetTemplate(10, "130кг"), SetTemplate(8, "140кг"), SetTemplate(8, "140кг"), SetTemplate(6, "150кг")),
                    120, "Полная амплитуда", "жим ногами силовой техника"),
                ExerciseTemplate("b3_3", "Румынская тяга со штангой",
                    listOf(SetTemplate(10, "50кг"), SetTemplate(8, "55кг"), SetTemplate(8, "55кг"), SetTemplate(6, "60кг")),
                    120, "НЕЙТРАЛЬНАЯ СПИНА! Нет округления!", "румынская тяга штанга техника"),
                ExerciseTemplate("b3_4", "Гиперэкстензия с весом",
                    listOf(SetTemplate(12, "10кг"), SetTemplate(10, "12кг"), SetTemplate(10, "12кг")),
                    90, "Не более 12кг, контроль", "гиперэкстензия с весом техника"),
                ExerciseTemplate("b3_5", "Болгарский присед с гантелями",
                    listOf(SetTemplate(10, "22кг"), SetTemplate(8, "24кг"), SetTemplate(8, "24кг")),
                    90, "10 повторений каждая нога", "болгарский присед гантели техника"),
                ExerciseTemplate("b3_6", "Икры в тренажёре стоя",
                    listOf(SetTemplate(20, "60кг"), SetTemplate(15, "70кг"), SetTemplate(15, "70кг")),
                    45, "Медленно вниз", "икры стоя тренажёр техника")
            )
        ),
        "C3" to WorkoutTemplate(
            key = "C3", phaseName = "Фаза III — Верх гипертрофия", weekRange = 9..12,
            exercises = listOf(
                ExerciseTemplate("c3_1", "Наклонный жим гантелей",
                    listOf(SetTemplate(10, "26кг"), SetTemplate(8, "28кг"), SetTemplate(8, "28кг"), SetTemplate(8, "28кг")),
                    90, "4 рабочих подхода", "наклонный жим гантелей техника"),
                ExerciseTemplate("c3_2", "Тяга верхнего блока обратным хватом",
                    listOf(SetTemplate(12, "50кг"), SetTemplate(10, "55кг"), SetTemplate(10, "55кг"), SetTemplate(8, "60кг")),
                    90, "Акцент на бицепс", "тяга обратным хватом техника"),
                ExerciseTemplate("c3_3", "Суперсет: Разведение + Сведение гантелей",
                    listOf(SetTemplate(12, "18кг"), SetTemplate(10, "20кг"), SetTemplate(10, "20кг")),
                    75, "Без отдыха между упражнениями", "разведение сведение гантелей суперсет"),
                ExerciseTemplate("c3_4", "Трицепс: узкий жим штанги",
                    listOf(SetTemplate(12, "30кг"), SetTemplate(10, "35кг"), SetTemplate(10, "35кг")),
                    75, "Локти вертикально", "узкий жим штанги трицепс техника"),
                ExerciseTemplate("c3_5", "Концентрированные сгибания бицепса",
                    listOf(SetTemplate(12, "14кг"), SetTemplate(10, "16кг"), SetTemplate(10, "16кг")),
                    60, "Локоть в колено", "концентрированные сгибания бицепс техника"),
                ExerciseTemplate("c3_6", "L-sit на параллельных брусьях",
                    listOf(SetTemplate(1, "10 сек"), SetTemplate(1, "10 сек"), SetTemplate(1, "10 сек")),
                    45, "Или замена: подъём ног в висе", "l-sit брусья техника")
            )
        ),
        "D3" to WorkoutTemplate(
            key = "D3", phaseName = "Фаза III — Кондиционная", weekRange = 9..12,
            exercises = listOf(
                ExerciseTemplate("d3_1", "Круговой: Жим гантелей / Тяга / Присед в Смите",
                    listOf(SetTemplate(10, "20кг"), SetTemplate(10, "20кг"), SetTemplate(10, "20кг")),
                    60, "3 упражнения подряд без отдыха, потом отдых 90 сек", "круговая тренировка зал техника"),
                ExerciseTemplate("d3_2", "Интервальный велотренажёр",
                    listOf(SetTemplate(1, "20 мин"), SetTemplate(1, "б/в"), SetTemplate(1, "б/в")),
                    0, "10 раундов: 30 сек интенсивно / 60 сек спокойно", "хит велотренажёр"),
                ExerciseTemplate("d3_3", "Тяга гантели к поясу",
                    listOf(SetTemplate(12, "28кг"), SetTemplate(10, "30кг"), SetTemplate(10, "30кг")),
                    75, "Каждая рука", "тяга гантели к поясу техника"),
                ExerciseTemplate("d3_4", "Жим ногами — дроп-сет",
                    listOf(SetTemplate(15, "120кг"), SetTemplate(12, "100кг"), SetTemplate(10, "80кг")),
                    30, "Снижай вес без отдыха", "жим ногами дроп-сет техника"),
                ExerciseTemplate("d3_5", "Планка + подъём ноги",
                    listOf(SetTemplate(10, "б/в"), SetTemplate(10, "б/в"), SetTemplate(10, "б/в")),
                    45, "10 подъёмов каждой ногой", "планка подъём ноги техника"),
                ExerciseTemplate("d3_6", "Берпи (щадящий — без прыжка)",
                    listOf(SetTemplate(10, "б/в"), SetTemplate(8, "б/в"), SetTemplate(8, "б/в")),
                    60, "Без прыжка — опускание в планку и вставание", "берпи без прыжка техника")
            )
        )
    )

    // Расписание: Пн/Ср/Пт для фаз I-II, Пн/Ср/Пт/Сб для фазы III
    private val phase1Schedule = mapOf(1 to "A1", 3 to "B1", 5 to "C1")
    private val phase2Schedule = mapOf(1 to "A2", 3 to "B2", 5 to "C2", 6 to "D2")
    private val phase3Schedule = mapOf(1 to "A3", 3 to "B3", 5 to "C3", 6 to "D3")

    fun getWorkoutForDate(date: LocalDate, startDate: LocalDate): String? {
        val dayOfProgram = ChronoUnit.DAYS.between(startDate, date).toInt()
        if (dayOfProgram < 0) return null

        val weekNumber = dayOfProgram / 7 + 1
        if (weekNumber > 12) return null

        val dayOfWeek = date.dayOfWeek.value // 1=Mon, 7=Sun

        val schedule = when {
            weekNumber <= 4 -> phase1Schedule
            weekNumber <= 8 -> phase2Schedule
            else -> phase3Schedule
        }

        return schedule[dayOfWeek]
    }

    fun getTemplate(key: String): WorkoutTemplate? = workouts[key]

    fun getCurrentWeek(startDate: LocalDate): Int {
        val today = LocalDate.now()
        val days = ChronoUnit.DAYS.between(startDate, today).toInt()
        return (days / 7 + 1).coerceIn(1, 12)
    }

    fun getPhaseName(weekNumber: Int): String = when {
        weekNumber <= 4 -> "Фаза I"
        weekNumber <= 8 -> "Фаза II"
        else -> "Фаза III"
    }
}
