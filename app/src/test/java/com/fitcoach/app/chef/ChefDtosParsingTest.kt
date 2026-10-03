package com.fitcoach.app.chef

import com.fitcoach.app.ai.chef.FoodAnalysisDto
import com.fitcoach.app.ai.chef.FoodPhotoResponseDto
import com.fitcoach.app.ai.chef.MealPlanDto
import com.fitcoach.app.ai.chef.MealPlanResponseDto
import com.fitcoach.app.ai.chef.toDomain
import com.fitcoach.app.ai.chef.toDto
import com.fitcoach.app.domain.model.Confidence
import com.fitcoach.app.domain.model.IngredientCategory
import com.fitcoach.app.domain.model.MealSlot
import com.fitcoach.app.domain.model.ShoppingUnit
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Парсинг ответов прокси /v1/meal-plan и /v1/food-photo — с теми же настройками Json, что в AppModule. */
class ChefDtosParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val planJson = """
        {
          "plan": {
            "days": [
              {
                "day": 1,
                "meals": [
                  {
                    "slot": "breakfast", "title": "Овсянка", "timeMinutes": 10,
                    "ingredients": [
                      {"name": "Овсяные хлопья", "grams": 70, "category": "grains", "brand": "Увелка"},
                      {"name": "Молоко", "grams": 250, "category": "dairy"}
                    ],
                    "calories": 420, "proteinG": 16, "carbsG": 70, "fatG": 9,
                    "steps": ["Сварить", "Подать"],
                    "difficulty": "easy"
                  },
                  {
                    "slot": "supper", "title": "Куырдак", "timeMinutes": 30,
                    "ingredients": [{"name": "Печень", "grams": 150, "category": "meat_fish"}],
                    "calories": 570, "proteinG": 40, "carbsG": 50, "fatG": 20, "steps": []
                  }
                ],
                "totalCalories": 0, "totalProteinG": 0, "totalCarbsG": 0, "totalFatG": 0,
                "mood": "great"
              }
            ],
            "shopping": [
              {"category": "grains", "items": [{"name": "Овсяные хлопья", "quantity": 70, "unit": "g"}]},
              {"category": "dairy", "items": [{"name": "Молоко", "quantity": 1.5, "unit": "l", "note": "2,5%"}]}
            ],
            "notes": "Пейте воду",
            "version": 2
          },
          "model": "claude-opus-5",
          "usage": {"input_tokens": 10, "output_tokens": 20},
          "extra": {"a": 1}
        }
    """.trimIndent()

    @Test
    fun `meal plan parses with unknown fields and falls back for unknown enums`() {
        val dto = json.decodeFromString(MealPlanResponseDto.serializer(), planJson)
        assertNotNull(dto.plan)
        val plan = dto.plan!!.toDomain()
        assertEquals(1, plan.days.size)
        val day = plan.days.first()
        assertEquals(2, day.meals.size)
        assertEquals(MealSlot.BREAKFAST, day.meals[0].slot)
        assertEquals("unknown slot → SNACK", MealSlot.SNACK, day.meals[1].slot)
        assertEquals(IngredientCategory.GRAINS, day.meals[0].ingredients[0].category)
        // Нулевые итоги дня пересчитываются из приёмов.
        assertEquals(990, day.totalCalories)
        assertEquals(56f, day.totalProteinG)
        assertEquals("Пейте воду", plan.notes)
        assertEquals(2, plan.shopping.size)
        assertEquals(ShoppingUnit.L, plan.shopping[1].items[0].unit)
        assertEquals(1.5f, plan.shopping[1].items[0].quantity)
        assertEquals(990, plan.averageCalories)
    }

    @Test
    fun `missing shopping is rebuilt from ingredients`() {
        val dto = json.decodeFromString(MealPlanDto.serializer(), """{"days":[{"day":1,"meals":[{"slot":"lunch","title":"Сорпа","ingredients":[{"name":"Говядина","grams":150,"category":"meat_fish"},{"name":"говядина","grams":900,"category":"meat_fish"}],"calories":500}]}]}""")
        val plan = dto.toDomain()
        assertEquals(1, plan.shopping.size)
        val line = plan.shopping.first().items.single()
        assertEquals("Говядина", line.name)
        assertEquals(ShoppingUnit.KG, line.unit)
        assertEquals(1.1f, line.quantity, 0.001f)
    }

    @Test
    fun `meal plan round-trips through dto for Room storage`() {
        val plan = json.decodeFromString(MealPlanResponseDto.serializer(), planJson).plan!!.toDomain()
        val encoded = json.encodeToString(MealPlanDto.serializer(), plan.toDto())
        val restored = json.decodeFromString(MealPlanDto.serializer(), encoded).toDomain()
        assertEquals(plan, restored)
    }

    @Test
    fun `error envelope parses without plan`() {
        val dto = json.decodeFromString(MealPlanResponseDto.serializer(), """{"error":"rate_limited","text":"Лимит исчерпан"}""")
        assertEquals("rate_limited", dto.error)
        assertEquals("Лимит исчерпан", dto.text)
        assertEquals(null, dto.plan)
    }

    @Test
    fun `food analysis parses items, confidence and isFood`() {
        val raw = """
            {"analysis":{"items":[
               {"name":"Бешбармак","grams":350,"calories":620,"proteinG":38,"carbsG":52,"fatG":26,"confidence":"high","bbox":[1,2,3,4]},
               {"name":"Айран","grams":250,"calories":100,"proteinG":7,"carbsG":10,"fatG":3,"confidence":"weird"}
             ],"totalCalories":720,"totalProteinG":45,"totalCarbsG":62,"totalFatG":29,"note":"Порция большая","isFood":true,"language":"ru"},
             "model":"claude-opus-5","usage":{"input_tokens":1600,"output_tokens":400}}
        """.trimIndent()
        val dto = json.decodeFromString(FoodPhotoResponseDto.serializer(), raw)
        val analysis = dto.analysis!!.toDomain()
        assertTrue(analysis.isFood)
        assertEquals(2, analysis.items.size)
        assertEquals(Confidence.HIGH, analysis.items[0].confidence)
        assertEquals("unknown confidence → MEDIUM", Confidence.MEDIUM, analysis.items[1].confidence)
        assertEquals(720, analysis.totalCalories)
        assertEquals(45f, analysis.totalProteinG)
        assertEquals("Порция большая", analysis.note)
    }

    @Test
    fun `not food keeps empty items`() {
        val dto = json.decodeFromString(FoodAnalysisDto.serializer(), """{"items":[],"note":"На фото ноутбук","isFood":false}""")
        val analysis = dto.toDomain()
        assertFalse(analysis.isFood)
        assertTrue(analysis.items.isEmpty())
        assertEquals(0, analysis.totalCalories)
    }
}
