import java.io.File

fun main() {
    val ff14Folder = File("F:/documents/ff14/crafting")
    val recipeFile = File(ff14Folder, "recipes.txt")

    recipeFile.readLines().dropWhile { it.any() }.drop(1).forEach {
        Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler https://ffxiv.gamerescape.com/wiki/Special:Search/${it.substringBefore("(")}")
    }
}