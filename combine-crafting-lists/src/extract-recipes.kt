import java.io.File

data class Ingredient(
    val name: String,
    val quantity: Int
)

data class Recipe(
    val name: String,
    val yield: Int,
    val ingredients: List<Ingredient>
)

fun main() {
    val listFile = File("F:/documents/ff14/crafting/crafting.txt")
    listFile
        .readLines()
        .map {Regex("""(.*?) \(\d+/\d+\) \(.*?\) -- (.*?) --""").find(it)}
        .mapNotNull { match ->
            match ?: return@mapNotNull null

            val name = match.groupValues[1]
            val ingredientsStr = match.groupValues[2]
            val ingredientPieces = ingredientsStr.split(',').map { it.trim() }

            val yield = with (Regex("""\d+/(\d+)""").find(ingredientsStr)) {
                if (this == null) 1 else this.groupValues[1].toInt()
            }

            val ingredients = ingredientPieces.map {
                if (`yield` == 1) {
                    Ingredient(it.substringAfter(' '), it.substringBefore(' ').toInt())
                } else {
                    Ingredient(it.substringAfter(' '), it.substringBefore('/').toInt())
                }
            }

            Recipe(name, `yield`, ingredients)
        }
        .forEach {
            println("${it.name} (${it.yield}): ${it.ingredients.map { "${it.quantity} ${it.name}" }.joinToString(", ")}")
        }
}