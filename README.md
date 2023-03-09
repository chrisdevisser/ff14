# combine-crafting-lists

Some tools related to mass completion of the crafting log. Most code was made deliberately bad for a laugh. Some is hardcoded and would need to be changed.

### combine-crafting-lists

Takes all files in a folder named ARR1.csv. HW3a.csv, ShB1-hq.csv, etc. exported from Garland and outputs the contents of a single file containing one line per required item or craft, ordered by expansion, then number, then letters.

Sample format:

```
faded copy of starved (0/1) (trade: 10, 000) [HWd/1]
barding of divine light (0/1) (craft: Lv. 90 WVR) [EW13/1]
```

This also uses a text file containing recipe data and a text file containing data on recipes with weird yields. The former has the following format:

```
Adamantite Ingot (3): 1 sun mica, 2 adamantite nugget, 1 blue quartz, 5 purified coke
```

The latter is a simple list:

```
urunday lumber
adamantite ingot
nightsteel ingot
```

With recipe data, the combining of lists accounts for cases where crafting extras for one list can reduce the number needed for another list. If one list needs 5 of an item with a yield of 3, there will be one left over. If a different list needs 4, that leftover can reduce the number of crafts in this list from 2 to 1, and this will be reflected in the combined number.

During this process, crafts that are in more than one list and suspected to have a yield higher than 1, but don't have a recipe, are listed out so recipes can be added. In addition, reducing the number of crafts also reduces its ingredients. Any missing recipe data to do this will be listed. This process accounts for cases such as an ingredient being crafted with a yield >1, where lowering it may or may not reduce its total number of crafts as well, and so on. 

### dump-inventory

Given recipe data and a text file of items to account for (format: lines of `3 upland wheat`), which could be a new inventory with an existing crafting list or include retainers with a fresh crafting list, processes the items and adjusts the list. Any crafted items included will appropriately lower its ingredients as well. 

This will account for cases where the item being added is a craft with a yield higher than 1. Only when the amount passes the threshold to save a craft will its ingredients be added accordingly.

Any items not found in the list will be listed.

### extract-recipes

Obsoleted by the recipes file. Extracts inline ingredient data from a crafting list.

### group-hq-by-nq

Moves hq versions of items (determined by their name ending in " hq") next to their normal counterparts in the crafting list.

### lower-quality

Merges hq items into their nq entries in the crafting list.

### search-all-recipes

Given a recipes file with a blank line at the end and then lines of items with unknown recipes, opens GamerEscape for each item to show the recipe.

# kagerou-screenshot

Upon pressing shift-pause/break, finds the kagerou overlay and screenshots it to the clipboard.
