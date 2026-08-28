import sqlite3
import os
import json

DB_PATH = "healthheat.db"

def create_schema(cursor):
    # Enable foreign keys
    cursor.execute("PRAGMA foreign_keys = ON;")

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS FoodMaster (
        id TEXT PRIMARY KEY,
        canonicalName TEXT NOT NULL,
        category TEXT,
        subcategory TEXT,
        vegetarian BOOLEAN,
        vegan BOOLEAN,
        eggitarian BOOLEAN,
        cuisine TEXT,
        region TEXT,
        defaultPreparation TEXT,
        source TEXT
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS FoodAliases (
        foodId TEXT,
        alias TEXT,
        FOREIGN KEY(foodId) REFERENCES FoodMaster(id)
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS NutrientDefinition (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        unit TEXT NOT NULL,
        category TEXT
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS FoodNutrient (
        foodId TEXT,
        nutrientId TEXT,
        amountPer100g REAL,
        source TEXT,
        FOREIGN KEY(foodId) REFERENCES FoodMaster(id),
        FOREIGN KEY(nutrientId) REFERENCES NutrientDefinition(id)
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ServingSize (
        foodId TEXT,
        unit TEXT,
        quantity REAL,
        grams REAL,
        milliliters REAL,
        description TEXT,
        source TEXT,
        FOREIGN KEY(foodId) REFERENCES FoodMaster(id)
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS FoodPreparation (
        id TEXT PRIMARY KEY,
        foodId TEXT,
        preparationMethod TEXT,
        state TEXT,
        nutrientReference TEXT,
        FOREIGN KEY(foodId) REFERENCES FoodMaster(id)
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS Recipe (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        cuisine TEXT,
        region TEXT,
        mealType TEXT,
        servings REAL,
        instructions TEXT,
        source TEXT
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS RecipeIngredient (
        recipeId TEXT,
        foodId TEXT,
        quantity REAL,
        unit TEXT,
        preparation TEXT,
        FOREIGN KEY(recipeId) REFERENCES Recipe(id),
        FOREIGN KEY(foodId) REFERENCES FoodMaster(id)
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS FoodRoles (
        foodId TEXT,
        role TEXT,
        FOREIGN KEY(foodId) REFERENCES FoodMaster(id)
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS FoodSubstitutions (
        foodId TEXT,
        alternativeFoodId TEXT,
        nutritionalRole TEXT,
        compatibility TEXT,
        FOREIGN KEY(foodId) REFERENCES FoodMaster(id),
        FOREIGN KEY(alternativeFoodId) REFERENCES FoodMaster(id)
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS FoodAllergen (
        foodId TEXT,
        allergenId TEXT,
        source TEXT,
        confidence TEXT,
        FOREIGN KEY(foodId) REFERENCES FoodMaster(id)
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS MealTemplate (
        id TEXT PRIMARY KEY,
        name TEXT,
        mealType TEXT,
        region TEXT
    );
    """)

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS MealItem (
        templateId TEXT,
        foodOrRecipeId TEXT,
        type TEXT,
        FOREIGN KEY(templateId) REFERENCES MealTemplate(id)
    );
    """)

def populate_nutrients(cursor):
    nutrients = [
        ("CALORIES", "Calories", "kcal", "MACRO"),
        ("PROT", "Protein", "g", "MACRO"),
        ("CARB", "Carbohydrates", "g", "MACRO"),
        ("FAT", "Fat", "g", "MACRO"),
        ("FIBER", "Fiber", "g", "MACRO"),
        ("CA", "Calcium", "mg", "MINERAL"),
        ("FE", "Iron", "mg", "MINERAL"),
        ("B12", "Vitamin B12", "mcg", "VITAMIN")
    ]
    cursor.executemany("INSERT INTO NutrientDefinition VALUES (?, ?, ?, ?)", nutrients)

def ingest_food_data(cursor):
    # Simulated ETL ingestion from a structured source
    foods = [
        ("raw_rice", "Rice (Raw)", "Grains", "Cereals", True, True, True, "Indian", "Pan-India", "raw", "IFCT"),
        ("cooked_rice", "Rice (Cooked)", "Grains", "Cereals", True, True, True, "Indian", "Pan-India", "boiled", "IFCT"),
        ("raw_toor_dal", "Toor Dal (Raw)", "Legumes", "Pulses", True, True, True, "Indian", "Pan-India", "raw", "IFCT"),
        ("cooked_toor_dal", "Toor Dal (Cooked)", "Legumes", "Pulses", True, True, True, "Indian", "Pan-India", "boiled", "IFCT"),
        ("paneer", "Paneer", "Dairy", "Cheese", True, False, True, "Indian", "North", "raw", "IFCT"),
        ("tofu", "Tofu", "Soy", "Plant Protein", True, True, True, "Global", "Pan-India", "raw", "IFCT"),
        ("milk_cow", "Cow Milk", "Dairy", "Milk", True, False, True, "Global", "Pan-India", "raw", "IFCT"),
    ]
    cursor.executemany("INSERT INTO FoodMaster VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", foods)

    aliases = [
        ("cooked_rice", "Chawal"),
        ("cooked_toor_dal", "Arhar Dal"),
        ("paneer", "Cottage Cheese")
    ]
    cursor.executemany("INSERT INTO FoodAliases VALUES (?, ?)", aliases)

    food_nutrients = [
        ("raw_rice", "CALORIES", 350.0, "IFCT"),
        ("raw_rice", "PROT", 7.0, "IFCT"),
        ("raw_rice", "CARB", 78.0, "IFCT"),
        
        ("cooked_rice", "CALORIES", 130.0, "IFCT"),
        ("cooked_rice", "PROT", 2.7, "IFCT"),
        ("cooked_rice", "CARB", 28.0, "IFCT"),

        ("cooked_toor_dal", "CALORIES", 116.0, "IFCT"),
        ("cooked_toor_dal", "PROT", 6.0, "IFCT"),
        ("cooked_toor_dal", "CARB", 20.0, "IFCT"),
        ("cooked_toor_dal", "FIBER", 5.0, "IFCT"),
        ("cooked_toor_dal", "FE", 1.5, "IFCT"),
        
        ("paneer", "CALORIES", 296.0, "IFCT"),
        ("paneer", "PROT", 18.0, "IFCT"),
        ("paneer", "FAT", 22.0, "IFCT"),
        ("paneer", "CA", 480.0, "IFCT"),
        
        ("tofu", "CALORIES", 76.0, "IFCT"),
        ("tofu", "PROT", 8.0, "IFCT"),
        ("tofu", "FAT", 4.0, "IFCT"),
        ("tofu", "CA", 350.0, "IFCT")
    ]
    cursor.executemany("INSERT INTO FoodNutrient VALUES (?, ?, ?, ?)", food_nutrients)

    servings = [
        ("cooked_rice", "cup", 1.0, 150.0, 0.0, "1 standard cup", "IFCT"),
        ("cooked_toor_dal", "katori", 1.0, 150.0, 0.0, "1 medium bowl", "IFCT"),
        ("paneer", "piece", 1.0, 30.0, 0.0, "1 medium piece", "IFCT")
    ]
    cursor.executemany("INSERT INTO ServingSize VALUES (?, ?, ?, ?, ?, ?, ?)", servings)

    roles = [
        ("cooked_toor_dal", "PROTEIN_SOURCE"),
        ("cooked_toor_dal", "FIBER_SOURCE"),
        ("paneer", "PROTEIN_SOURCE"),
        ("paneer", "CALCIUM_SOURCE"),
        ("tofu", "PROTEIN_SOURCE"),
        ("tofu", "CALCIUM_SOURCE")
    ]
    cursor.executemany("INSERT INTO FoodRoles VALUES (?, ?)", roles)

    substitutions = [
        ("paneer", "tofu", "PROTEIN_AND_CALCIUM", "VEGAN_ALTERNATIVE")
    ]
    cursor.executemany("INSERT INTO FoodSubstitutions VALUES (?, ?, ?, ?)", substitutions)

    allergens = [
        ("paneer", "dairy", "IFCT", "HIGH"),
        ("milk_cow", "dairy", "IFCT", "HIGH"),
        ("tofu", "soy", "IFCT", "HIGH")
    ]
    cursor.executemany("INSERT INTO FoodAllergen VALUES (?, ?, ?, ?)", allergens)

def ingest_recipes(cursor):
    recipes = [
        ("rec_dal_chawal", "Dal Chawal", "Indian", "Pan-India", "Lunch", 1.0, "Serve dal over rice.", "System")
    ]
    cursor.executemany("INSERT INTO Recipe VALUES (?, ?, ?, ?, ?, ?, ?, ?)", recipes)

    recipe_ingredients = [
        ("rec_dal_chawal", "cooked_rice", 1.0, "cup", "boiled"),
        ("rec_dal_chawal", "cooked_toor_dal", 1.0, "katori", "boiled")
    ]
    cursor.executemany("INSERT INTO RecipeIngredient VALUES (?, ?, ?, ?, ?)", recipe_ingredients)

def main():
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    print("Creating normalized schema...")
    create_schema(cursor)
    
    print("Ingesting nutrient definitions...")
    populate_nutrients(cursor)
    
    print("Ingesting authoritative food data...")
    ingest_food_data(cursor)
    
    print("Ingesting recipes...")
    ingest_recipes(cursor)
    
    conn.commit()
    conn.close()
    
    print(f"ETL pipeline completed successfully. Output: {DB_PATH}")

if __name__ == "__main__":
    main()
