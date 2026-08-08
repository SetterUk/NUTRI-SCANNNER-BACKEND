package com.example.healthheatv2.data

// ─────────────────────────────────────────────────
//  Enums
// ─────────────────────────────────────────────────
enum class TestDifficulty(val label: String, val emoji: String) {
    EASY("Easy", "🟢"),
    MEDIUM("Medium", "🟡"),
    ADVANCED("Advanced", "🔴")
}

enum class RiskLevel(val label: String) {
    HIGH("High Risk"),
    MEDIUM("Medium Risk"),
    LOW("Low Risk")
}

enum class FoodCategory(val displayName: String, val emoji: String) {
    DAIRY("Dairy Products", "🥛"),
    SPICES("Spices", "🌿"),
    OILS("Oils & Fats", "🫙"),
    SWEETENERS("Sweeteners", "🍯"),
    GRAINS("Grains & Flours", "🌾"),
    PULSES("Pulses", "🫘"),
    BEVERAGES("Beverages", "🍵"),
    BASICS("Kitchen Basics", "🧂"),
    PROTEIN("Protein Foods", "🥩"),
    FRUITS_VEGGIES("Fruits & Veggies", "🥦"),
    PROCESSED("Processed Foods", "🏭")
}

// ─────────────────────────────────────────────────
//  Data Models
// ─────────────────────────────────────────────────
data class AdulterationTest(
    val testName: String,
    val whatYouNeed: String,
    val steps: List<String>,
    val pureResult: String,
    val adulteratedResult: String,
    val difficulty: TestDifficulty
)

data class FoodGuideEntry(
    val id: String,
    val name: String,
    val emoji: String,
    val category: FoodCategory,
    val riskLevel: RiskLevel,
    val commonAdulterants: List<String>,
    val tests: List<AdulterationTest>,
    val healthRisks: String,
    val buyingTip: String
)

// ─────────────────────────────────────────────────
//  All Food Entries (63 foods, 10 categories)
// ─────────────────────────────────────────────────
val allFoodGuideEntries: List<FoodGuideEntry> = listOf(

    // ═══════════════════════════════════════
    //  DAIRY (8)
    // ═══════════════════════════════════════

    FoodGuideEntry(
        id = "milk",
        name = "Milk",
        emoji = "🥛",
        category = FoodCategory.DAIRY,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Water", "Synthetic milk", "Detergent", "Starch", "Urea", "Formalin"),
        tests = listOf(
            AdulterationTest(
                testName = "Detergent Test",
                whatYouNeed = "A small glass or bottle",
                steps = listOf(
                    "Take 5–10 ml of milk in a small bottle.",
                    "Shake it vigorously for 30 seconds.",
                    "Observe the foam formed on top."
                ),
                pureResult = "Thin, small layer of foam that disappears quickly.",
                adulteratedResult = "Thick, dense foam that persists for several minutes — indicates detergent.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Starch Test",
                whatYouNeed = "2–3 drops of iodine solution (available at pharmacies)",
                steps = listOf(
                    "Boil a small amount of milk and let it cool.",
                    "Add 2–3 drops of iodine solution to the cooled milk.",
                    "Observe the color change."
                ),
                pureResult = "No color change — remains original yellow/brown of iodine.",
                adulteratedResult = "Turns blue or black — indicates starch is present.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Water Adulteration Test",
                whatYouNeed = "A flat surface (polished or glass)",
                steps = listOf(
                    "Put a drop of milk on a polished flat surface.",
                    "Tilt the surface slowly and observe how the drop flows."
                ),
                pureResult = "The drop flows slowly and leaves a white trail behind.",
                adulteratedResult = "The drop flows quickly like water and leaves no trail — indicates water dilution.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Urea Test",
                whatYouNeed = "Soya bean powder, litmus paper",
                steps = listOf(
                    "Mix half a teaspoon of soya bean powder with 5 ml of milk.",
                    "Wait 5 minutes.",
                    "Dip a litmus paper strip into the mixture."
                ),
                pureResult = "Litmus paper stays the same or turns very slightly.",
                adulteratedResult = "Litmus paper turns red to blue strongly, indicating ammonia from urea.",
                difficulty = TestDifficulty.MEDIUM
            )
        ),
        healthRisks = "Detergents can cause severe gastrointestinal issues. Urea is toxic to kidneys. Formalin is a carcinogen and can cause liver damage. Synthetic milk often contains caustic soda which burns the digestive tract.",
        buyingTip = "Buy from FSSAI-licensed vendors. Packaged milk from reputed brands is generally safer. Avoid loose/unpackaged milk from unknown sources."
    ),

    FoodGuideEntry(
        id = "ghee",
        name = "Ghee",
        emoji = "🧈",
        category = FoodCategory.DAIRY,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Vanaspati (hydrogenated fat)", "Animal fat (tallow)", "Starch", "Artificial color"),
        tests = listOf(
            AdulterationTest(
                testName = "Melt & Color Test",
                whatYouNeed = "A clear glass, hot water",
                steps = listOf(
                    "Take a small amount of ghee in a transparent glass.",
                    "Place the glass in a bowl of hot water to melt it.",
                    "Observe the color and consistency when fully melted."
                ),
                pureResult = "Melts evenly to a clear, golden-yellow liquid with a rich aroma.",
                adulteratedResult = "Appears cloudy, lighter in color, or has an off smell — may indicate vanaspati or animal fat.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Baudouin Test (Vanaspati)",
                whatYouNeed = "HCl (hydrochloric acid), sugar",
                steps = listOf(
                    "Take 1 ml of melted ghee in a test tube.",
                    "Add 1 ml of concentrated HCl.",
                    "Add a pinch of sugar and shake well.",
                    "Let it stand for 5 minutes and observe the lower acid layer."
                ),
                pureResult = "No color change in the lower acid layer.",
                adulteratedResult = "Lower acid layer turns crimson/red — indicates vanaspati adulteration.",
                difficulty = TestDifficulty.ADVANCED
            ),
            AdulterationTest(
                testName = "Starch Test",
                whatYouNeed = "Iodine solution",
                steps = listOf(
                    "Melt a small amount of ghee.",
                    "Add 2 drops of iodine solution.",
                    "Observe the color."
                ),
                pureResult = "No blue or black coloration.",
                adulteratedResult = "Turns blue or black — starch is present.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Vanaspati contains trans fats which severely increase the risk of heart disease and stroke. Animal fat (tallow) may come from unhygienic sources. Artificial colors used may be toxic.",
        buyingTip = "Buy branded ghee in sealed tins or glass jars. Check for FSSAI license number. Agmark-certified ghee is a reliable indicator of quality."
    ),

    FoodGuideEntry(
        id = "paneer",
        name = "Paneer",
        emoji = "🧀",
        category = FoodCategory.DAIRY,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Starch", "Refined oil (Dalda)", "Skim milk powder", "White soap"),
        tests = listOf(
            AdulterationTest(
                testName = "Iodine Starch Test",
                whatYouNeed = "Iodine solution",
                steps = listOf(
                    "Take a small piece of paneer.",
                    "Add 2–3 drops of iodine solution directly on the paneer.",
                    "Observe the color change."
                ),
                pureResult = "No significant color change — remains yellowish-brown.",
                adulteratedResult = "Turns blue or black — starch is present.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Texture Test",
                whatYouNeed = "Your hands only",
                steps = listOf(
                    "Take a small piece of paneer between your fingers.",
                    "Rub it gently and press it.",
                    "Observe how it breaks and feels."
                ),
                pureResult = "Pure paneer crumbles easily, feels soft but firm, and leaves a white residue.",
                adulteratedResult = "Feels greasy/oily, stretches like rubber, or becomes slimy — indicates refined oil or non-dairy fat.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Refined oil adulterants contain unhealthy trans fats. Starch adulteration reduces nutritional value significantly. Unhygienic manufacturing may also introduce harmful bacteria.",
        buyingTip = "Make paneer at home using full-fat milk for guaranteed purity. When buying packaged paneer, look for refrigeration and an FSSAI mark."
    ),

    FoodGuideEntry(
        id = "curd",
        name = "Curd / Dahi",
        emoji = "🥣",
        category = FoodCategory.DAIRY,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Starch", "Synthetic milk base", "Harmful preservatives"),
        tests = listOf(
            AdulterationTest(
                testName = "Starch Test",
                whatYouNeed = "Iodine solution",
                steps = listOf(
                    "Take 2 teaspoons of curd in a bowl.",
                    "Add 2 drops of iodine solution.",
                    "Stir gently and observe the color."
                ),
                pureResult = "No color change or slight yellow tint.",
                adulteratedResult = "Turns blue or black — starch or flour has been mixed in.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Preservatives in curd can disrupt gut flora. Starch reduces the nutritional benefits of curd significantly.",
        buyingTip = "Homemade curd is always the best. For packaged curd, buy from reputed dairy brands. Check the manufacturing date and ensure it's within 3 days."
    ),

    FoodGuideEntry(
        id = "butter",
        name = "Butter",
        emoji = "🟡",
        category = FoodCategory.DAIRY,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Vanaspati", "Starch", "Artificial yellow color", "Animal body fat"),
        tests = listOf(
            AdulterationTest(
                testName = "Melt Test",
                whatYouNeed = "A pan",
                steps = listOf(
                    "Heat a small piece of butter gently in a pan.",
                    "Observe how it melts and the color of the liquid."
                ),
                pureResult = "Melts quickly to clear golden liquid with a rich buttery aroma.",
                adulteratedResult = "Slow to melt, white/opaque after melting, or has a soapy smell — indicates vanaspati or animal fat.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Starch Test",
                whatYouNeed = "Iodine solution, warm water",
                steps = listOf(
                    "Melt butter slightly and dissolve a teaspoon in warm water.",
                    "Add 2–3 drops of iodine solution.",
                    "Observe the color."
                ),
                pureResult = "No blue or black color.",
                adulteratedResult = "Turns blue/black indicating starch is present.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Vanaspati (hydrogenated fat) contains harmful trans fatty acids that increase LDL cholesterol and risk of cardiovascular disease.",
        buyingTip = "Buy Amul or other FSSAI-certified butter. Salted butter has a longer shelf life. Avoid butter with a very pale or dull yellow color."
    ),

    FoodGuideEntry(
        id = "khoya",
        name = "Khoya / Mawa",
        emoji = "🍮",
        category = FoodCategory.DAIRY,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Starch", "Wheat flour", "Blotting paper", "Coagulated skimmed milk", "Vanaspati"),
        tests = listOf(
            AdulterationTest(
                testName = "Iodine Test",
                whatYouNeed = "Iodine solution, water",
                steps = listOf(
                    "Dissolve a small piece of khoya in 10 ml of warm water.",
                    "Add 2–3 drops of iodine solution.",
                    "Observe the color change."
                ),
                pureResult = "No significant color change.",
                adulteratedResult = "Turns blue or dark — starch or flour is present.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Taste and Texture Test",
                whatYouNeed = "Your senses",
                steps = listOf(
                    "Crumble a small piece of khoya between your fingers.",
                    "Taste a tiny amount.",
                    "Observe the grainy texture."
                ),
                pureResult = "Smooth, granular, milky taste. Feels oily in a good way when rubbed.",
                adulteratedResult = "Gritty texture, bland taste, no milky aroma — likely adulterated with starch or flour.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Adulterated khoya used in sweets is a major health risk, especially during festivals. Starch and flour reduce nutritional value. Blotting paper is indigestible and potentially toxic.",
        buyingTip = "Avoid loose khoya from unknown vendors, especially before festivals when adulteration peaks. Prefer packaged khoya from dairy brands."
    ),

    FoodGuideEntry(
        id = "ice_cream",
        name = "Ice Cream",
        emoji = "🍦",
        category = FoodCategory.DAIRY,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Non-dairy vegetable fat", "Artificial flavors and colors", "Excessive air (overrun)", "Gelatin from non-halal sources"),
        tests = listOf(
            AdulterationTest(
                testName = "Melt Test",
                whatYouNeed = "Room temperature, a plate",
                steps = listOf(
                    "Take a scoop of ice cream and leave it on a plate at room temperature.",
                    "Observe how it melts over 10–15 minutes."
                ),
                pureResult = "Melts uniformly into a smooth cream-like liquid.",
                adulteratedResult = "Leaves a foamy, frothy residue or melts unevenly — indicates excessive air or cheap stabilizers.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Non-dairy fat substitutes and excessive sugar increase the risk of obesity and cardiovascular disease. Artificial colors (especially brilliant blue, sunset yellow) have been linked to hyperactivity in children.",
        buyingTip = "Choose ice creams labeled 'dairy ice cream' not just 'frozen dessert'. Read ingredients — real ice cream has milk/cream as primary ingredients."
    ),

    FoodGuideEntry(
        id = "condensed_milk",
        name = "Condensed Milk",
        emoji = "🥫",
        category = FoodCategory.DAIRY,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Vegetable fat", "Starch", "Excess sugar beyond label claims"),
        tests = listOf(
            AdulterationTest(
                testName = "Starch Test",
                whatYouNeed = "Iodine solution, warm water",
                steps = listOf(
                    "Dilute a teaspoon of condensed milk in warm water.",
                    "Add 2–3 drops of iodine.",
                    "Observe the color."
                ),
                pureResult = "No blue or black color.",
                adulteratedResult = "Blue/black color indicates starch adulteration.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Vegetable fat replacements can contain trans fats. Excess undeclared sugar poses risks for diabetics who may not know the actual sugar content.",
        buyingTip = "Buy branded condensed milk in sealed cans. Check the ingredient label — milk and sugar should be the only primary ingredients."
    ),

    // ═══════════════════════════════════════
    //  SPICES (12)
    // ═══════════════════════════════════════

    FoodGuideEntry(
        id = "turmeric",
        name = "Turmeric",
        emoji = "🌿",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Lead chromate", "Metanil yellow dye", "Yellow chalk powder", "Starch"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Test for Dye",
                whatYouNeed = "A glass of water",
                steps = listOf(
                    "Add half a teaspoon of turmeric powder to a glass of water.",
                    "Stir gently and observe the color of the water.",
                    "Leave it for 10 minutes and observe if the color separates."
                ),
                pureResult = "Water turns slightly yellowish but remains somewhat clear. Color is not intense.",
                adulteratedResult = "Water turns bright intense yellow immediately — indicates synthetic dye (metanil yellow). Dye may float as a separate layer.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "HCl Test for Lead Chromate",
                whatYouNeed = "Hydrochloric acid (HCl), test tube",
                steps = listOf(
                    "Place a small amount of turmeric in a test tube.",
                    "Add a few drops of concentrated HCl.",
                    "Observe the color change."
                ),
                pureResult = "Color changes to magenta/pink (curcumin reacts with acid naturally).",
                adulteratedResult = "Turns bright green/blue — indicates lead chromate is present.",
                difficulty = TestDifficulty.ADVANCED
            ),
            AdulterationTest(
                testName = "Rubbing Test",
                whatYouNeed = "White paper",
                steps = listOf(
                    "Rub a pinch of turmeric between your fingers on white paper.",
                    "Observe the stain left behind."
                ),
                pureResult = "Leaves a naturally golden-yellow stain that doesn't smear intensely.",
                adulteratedResult = "Leaves an intensely bright yellow stain that is hard to remove — chemical dye.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Lead chromate causes severe kidney damage, neurological disorders, and is a confirmed carcinogen. Metanil yellow dye is linked to cancer and is BANNED in India. Long-term consumption can cause brain damage.",
        buyingTip = "Buy whole turmeric roots and grind at home for guaranteed purity. If buying powder, choose ISI/FSSAI marked packaged brands. Avoid loose turmeric."
    ),

    FoodGuideEntry(
        id = "chilli_powder",
        name = "Chilli Powder",
        emoji = "🌶️",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Brick powder", "Sawdust", "Artificial red dye (Sudan dye)", "Talc powder"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Test",
                whatYouNeed = "A glass of water",
                steps = listOf(
                    "Add a teaspoon of chilli powder to a glass of water.",
                    "Stir gently and observe what happens.",
                    "Leave undisturbed for 2–3 minutes."
                ),
                pureResult = "Floats on the surface or disperses slowly. Water turns light reddish.",
                adulteratedResult = "Brick or stone powder sinks immediately to the bottom. Water may turn bright unnatural red from artificial dye.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Paper Blot Test for Dye",
                whatYouNeed = "White blotting paper or a tissue",
                steps = listOf(
                    "Place a small amount of chilli powder on a damp white tissue/paper.",
                    "Press it gently and then remove the powder.",
                    "Observe the stain left on the paper."
                ),
                pureResult = "Leaves a natural brownish-orange stain that is not very vivid.",
                adulteratedResult = "Leaves a bright, vivid red stain that doesn't wash off easily — artificial dye (Sudan red) is present.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Sudan dyes (Sudan I, II, III, IV) are confirmed carcinogens banned across the world. Brick and talc powders cause gastrointestinal damage. Long-term exposure can cause internal organ damage.",
        buyingTip = "Buy whole dried red chillies and grind them at home. When buying packaged, choose reputed brands with FSSAI mark. Avoid chilli powder that looks unnaturally bright red."
    ),

    FoodGuideEntry(
        id = "coriander_powder",
        name = "Coriander Powder",
        emoji = "🟢",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Dung powder", "Sawdust", "Leaf powder", "Starch"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Sink Test",
                whatYouNeed = "A glass of water",
                steps = listOf(
                    "Add a teaspoon of coriander powder to water.",
                    "Stir gently and observe."
                ),
                pureResult = "Floats on water surface with characteristic aroma.",
                adulteratedResult = "Sawdust floats but has no aroma. Dung/starch sinks or makes water appear muddy.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Dung powder carries dangerous pathogens including E. coli and Salmonella. Sawdust is indigestible and can damage the intestinal lining.",
        buyingTip = "Buy whole coriander seeds (dhania) and grind them at home. The aroma should be fresh and strong. Packaged powder from reputed brands is safer."
    ),

    FoodGuideEntry(
        id = "cumin",
        name = "Cumin / Jeera",
        emoji = "⚫",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Grass seeds", "Stone powder", "Charcoal dust coating", "Artificial coloring"),
        tests = listOf(
            AdulterationTest(
                testName = "Palm Rub Test",
                whatYouNeed = "Your hands",
                steps = listOf(
                    "Take a small amount of cumin seeds in your palm.",
                    "Rub them vigorously between both palms for 10–15 seconds.",
                    "Observe what remains on your hands."
                ),
                pureResult = "Hands turn slightly yellowish with a strong, distinctive aroma.",
                adulteratedResult = "Hands turn black or grey from charcoal dust coating. No or very little aroma.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Smell Test",
                whatYouNeed = "Your nose",
                steps = listOf(
                    "Take a small pinch of cumin seeds.",
                    "Crush them slightly with your fingernails.",
                    "Smell immediately."
                ),
                pureResult = "Strong, earthy, warm and distinctively aromatic smell.",
                adulteratedResult = "Very faint or no smell — indicates grass seeds or exhausted cumin.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Charcoal-coated grass seeds have no nutritional value and can cause stomach upset. Artificial coloring agents may be toxic.",
        buyingTip = "Buy from a trustworthy spice vendor. Fresh cumin should have a very strong aroma when crushed. Buy packaged, FSSAI-certified cumin when possible."
    ),

    FoodGuideEntry(
        id = "black_pepper",
        name = "Black Pepper",
        emoji = "🔵",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Papaya seeds (dried)", "Charcoal dust", "Light/hollow berries", "Mineral oil coating"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Float Test",
                whatYouNeed = "A glass of water",
                steps = listOf(
                    "Drop a few black pepper corns into a glass of water.",
                    "Observe what happens."
                ),
                pureResult = "Pure, heavy pepper corns sink to the bottom.",
                adulteratedResult = "Papaya seeds and light hollow berries float on the surface.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Crush and Smell Test",
                whatYouNeed = "Your fingers",
                steps = listOf(
                    "Crush a few pepper corns between your fingers.",
                    "Smell the crushed powder immediately.",
                    "Observe the pungency and aroma."
                ),
                pureResult = "Sharp, pungent, strong aroma that makes you want to sneeze.",
                adulteratedResult = "Mild or no pungency, possibly sweet smell — papaya seeds or other substitutes.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Papaya seeds in large quantities can affect fertility and cause toxicity. Mineral oil coatings on pepper are not food-safe and can cause laxative effects.",
        buyingTip = "Buy whole black peppercorns rather than pre-ground powder. Fresh peppercorns should be heavy, hard, and intensely aromatic."
    ),

    FoodGuideEntry(
        id = "cardamom",
        name = "Cardamom (Elaichi)",
        emoji = "🫛",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Empty shells without seeds", "Artificial flavor injected shells", "Ammonium sulphate coating"),
        tests = listOf(
            AdulterationTest(
                testName = "Weight Test",
                whatYouNeed = "Your sense of weight / scale",
                steps = listOf(
                    "Take a few cardamom pods in your hand.",
                    "Feel their weight.",
                    "Squeeze them gently."
                ),
                pureResult = "Heavy, plump pods with visible seed bulge inside. Seeds rattle when shaken.",
                adulteratedResult = "Suspiciously light or very flat pods — shells with no or very few seeds inside.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Aroma Test",
                whatYouNeed = "Your nose",
                steps = listOf(
                    "Open a cardamom pod and smell the seeds.",
                    "Crush the seeds between your fingers and smell again."
                ),
                pureResult = "Intensely sweet, floral, and slightly spicy aroma that lingers.",
                adulteratedResult = "Faint, artificial, or no real aroma — indicates artificial flavoring or empty shells.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Empty shells provide zero nutritional or therapeutic benefit. Ammonium sulphate coating used to make shells look fresh is harmful if consumed in significant quantities.",
        buyingTip = "Always buy whole cardamom pods and check that they feel heavy. Green color should be natural, not artificially dyed. Avoid pre-opened or powdered cardamom."
    ),

    FoodGuideEntry(
        id = "saffron",
        name = "Saffron (Kesar)",
        emoji = "🌸",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Corn silk threads (dyed)", "Artificial red/yellow dye", "Colored paper or cloth threads", "Synthetic saffron"),
        tests = listOf(
            AdulterationTest(
                testName = "Cold Water Test",
                whatYouNeed = "Cold water (room temperature), white paper",
                steps = listOf(
                    "Place 2–3 saffron strands in a cup of cold water.",
                    "Wait 10–15 minutes without stirring.",
                    "Observe how the color releases."
                ),
                pureResult = "Releases color very slowly, turning water golden-yellow. Strands remain red-orange even after soaking.",
                adulteratedResult = "Releases intense red/orange color almost immediately. Strands lose all color and turn white — artificial dye.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Smell Test",
                whatYouNeed = "Your nose",
                steps = listOf(
                    "Take a strand and roll it between your moistened fingers.",
                    "Bring it close to your nose and smell."
                ),
                pureResult = "Distinct, slightly metallic, honey-like and floral aroma. Can't be replicated artificially.",
                adulteratedResult = "No smell or an artificial, sweet, non-distinctive odor.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Artificial dyes used to color fake saffron (like amaranth or sunset yellow) are potentially carcinogenic. Consuming large amounts of fake saffron during pregnancy is particularly dangerous.",
        buyingTip = "Saffron is expensive — if it's cheap, it's fake. Buy from Kashmir or certified spice retailers. Price below ₹200/gram is a red flag. Buy in sealed, labeled packaging."
    ),

    FoodGuideEntry(
        id = "asafoetida",
        name = "Asafoetida (Hing)",
        emoji = "🟡",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Soapstone powder", "Starch (wheat/rice)", "Resin", "Garlic powder", "Artificial smell additives"),
        tests = listOf(
            AdulterationTest(
                testName = "Flame Test",
                whatYouNeed = "A lighter or match",
                steps = listOf(
                    "Take a small piece of hing on a metal spoon.",
                    "Bring a flame close to it.",
                    "Observe if it catches fire."
                ),
                pureResult = "Pure hing catches fire and burns with a bright flame like a candle.",
                adulteratedResult = "Does not burn or burns very poorly — starch or soapstone is present.",
                difficulty = TestDifficulty.MEDIUM
            ),
            AdulterationTest(
                testName = "Water Test",
                whatYouNeed = "Warm water",
                steps = listOf(
                    "Dissolve a small piece of hing in warm water.",
                    "Observe the color of the water."
                ),
                pureResult = "Water turns milky white — this is normal for pure hing.",
                adulteratedResult = "Water remains clear or turns slightly yellow/grey — indicates heavy adulteration.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Soapstone (magnesium silicate) is indigestible and accumulates in the body. High starch content reduces potency and nutritional value. Gluten-containing starch in hing is dangerous for celiac patients who think hing is gluten-free.",
        buyingTip = "Buy pure asafoetida (100% hing, no fillers) from trusted spice brands. Check the label for ingredients — many commercial hing products contain 70% starch. Pure hing is very sticky and intensely pungent."
    ),

    FoodGuideEntry(
        id = "mustard_seeds",
        name = "Mustard Seeds",
        emoji = "🟤",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Argemone seeds (toxic — looks similar)", "Grass seeds", "Weed seeds"),
        tests = listOf(
            AdulterationTest(
                testName = "Press Test",
                whatYouNeed = "Your fingernail or two hard surfaces",
                steps = listOf(
                    "Take a few seeds.",
                    "Press each seed hard between your fingernails.",
                    "Observe the inside color."
                ),
                pureResult = "Mustard seeds are yellow inside when pressed. They have a pungent, mustard-like smell.",
                adulteratedResult = "Argemone seeds are white inside with no smell. They also have a tiny white dot (micropyle) visible on the surface.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Argemone seeds contain sanguinarine, a toxic alkaloid that causes epidemic dropsy — a dangerous condition causing fluid accumulation, glaucoma, and heart failure. It can be fatal.",
        buyingTip = "Buy mustard seeds from reputed brands in sealed packs. Examine the seeds — genuine mustard seeds should all look uniform. Any differently shaped or sized seeds are suspicious."
    ),

    FoodGuideEntry(
        id = "cinnamon",
        name = "Cinnamon (Dalchini)",
        emoji = "🪵",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Cassia bark (high coumarin)", "Other tree barks"),
        tests = listOf(
            AdulterationTest(
                testName = "Visual Roll Test",
                whatYouNeed = "Your eyes",
                steps = listOf(
                    "Look at the cinnamon stick cross-section.",
                    "Observe how it is rolled."
                ),
                pureResult = "True cinnamon (Ceylon) has thin, multiple layers rolled into a tight scroll. Color is tan/golden brown.",
                adulteratedResult = "Cassia has a single thick layer, dark reddish-brown color, and a rougher texture. Much harder to break.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Cassia contains very high levels of coumarin — a natural compound toxic to the liver in high doses. For people consuming cinnamon as a health supplement daily, cassia can cause liver damage.",
        buyingTip = "Buy Ceylon cinnamon ('true cinnamon') from specialty stores. It's more expensive but safer. For powdered cinnamon, buy from trusted brands that specify the type."
    ),

    FoodGuideEntry(
        id = "fenugreek",
        name = "Fenugreek (Methi)",
        emoji = "🟤",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Grass seeds", "Dried weed seeds"),
        tests = listOf(
            AdulterationTest(
                testName = "Smell and Taste Test",
                whatYouNeed = "Your senses",
                steps = listOf(
                    "Take a few seeds and crush them slightly.",
                    "Smell and taste a tiny amount."
                ),
                pureResult = "Fenugreek has a distinctive bitter taste and a slightly maple-like aroma.",
                adulteratedResult = "No bitter taste or aroma — likely grass or weed seeds mixed in.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Weed seeds mixed in may have unknown toxic properties. Some grass seeds can cause allergic reactions.",
        buyingTip = "Fenugreek seeds should be uniform in size and have a characteristic golden-yellow color. Buy from reputed spice vendors."
    ),

    FoodGuideEntry(
        id = "cloves",
        name = "Cloves (Laung)",
        emoji = "🌰",
        category = FoodCategory.SPICES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Exhausted cloves (oil extracted)", "Clove stems without buds"),
        tests = listOf(
            AdulterationTest(
                testName = "Float Test",
                whatYouNeed = "A glass of water",
                steps = listOf(
                    "Drop a few cloves into a glass of water.",
                    "Observe whether they float or sink."
                ),
                pureResult = "Pure cloves sink vertically (bud pointing down) due to their essential oil content.",
                adulteratedResult = "Exhausted cloves (oil-extracted) float horizontally — they are hollow inside.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Press Test",
                whatYouNeed = "Your fingernail",
                steps = listOf(
                    "Press the bud of the clove with your fingernail.",
                    "Observe if oil is released."
                ),
                pureResult = "A small amount of essential oil (eugenol) is released when pressed — dark brown oil.",
                adulteratedResult = "No oil comes out — the clove has been exhausted of its oil.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Exhausted cloves provide zero medicinal or flavoring benefit. You are essentially paying for wood chips.",
        buyingTip = "Choose cloves with a large, plump head (bud). Fresh cloves should release oil when pressed. Buy from a reputed spice retailer."
    ),

    // ═══════════════════════════════════════
    //  OILS & FATS (6)
    // ═══════════════════════════════════════

    FoodGuideEntry(
        id = "mustard_oil",
        name = "Mustard Oil",
        emoji = "🫙",
        category = FoodCategory.OILS,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Argemone oil (TOXIC)", "Mineral oil", "Other vegetable oils"),
        tests = listOf(
            AdulterationTest(
                testName = "Nitric Acid Test for Argemone Oil",
                whatYouNeed = "Concentrated nitric acid (pharmacy or lab supply), test tube",
                steps = listOf(
                    "Take 5 ml of mustard oil in a test tube.",
                    "Add 5 ml of concentrated nitric acid carefully.",
                    "Shake gently and observe the lower acid layer."
                ),
                pureResult = "The lower acid layer does not turn orange or brown.",
                adulteratedResult = "Lower acid layer turns orange or brownish — argemone oil is present.",
                difficulty = TestDifficulty.ADVANCED
            ),
            AdulterationTest(
                testName = "Pungency Test",
                whatYouNeed = "Your nose",
                steps = listOf(
                    "Smell the mustard oil.",
                    "It should have a very sharp, pungent smell.",
                    "Compare with a known pure sample if possible."
                ),
                pureResult = "Very sharp, eye-watering, distinct mustard pungency.",
                adulteratedResult = "Mild, weak, or no pungency — likely diluted with other oils.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Argemone oil is EXTREMELY DANGEROUS. It causes epidemic dropsy — a life-threatening condition causing widespread edema, glaucoma, heart failure, and death. India has seen multiple mass casualty events from argemone-adulterated mustard oil.",
        buyingTip = "Buy only AGMARK-certified mustard oil from reputed brands in sealed packaging. Never buy loose mustard oil. If the price seems too low, do not buy it."
    ),

    FoodGuideEntry(
        id = "coconut_oil",
        name = "Coconut Oil",
        emoji = "🥥",
        category = FoodCategory.OILS,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Palm oil", "Vegetable oil", "Mineral oil"),
        tests = listOf(
            AdulterationTest(
                testName = "Refrigerator Solidification Test",
                whatYouNeed = "A refrigerator, small glass container",
                steps = listOf(
                    "Pour a small amount of oil into a glass container.",
                    "Place in the refrigerator for 30 minutes.",
                    "Observe the state of the oil."
                ),
                pureResult = "Pure coconut oil solidifies completely and turns white/opaque at temperatures below 24°C.",
                adulteratedResult = "Remains partially or fully liquid, or solidifies unevenly — mixed with other oils.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Palm oil is high in saturated fats and excessive consumption raises LDL cholesterol. Mineral oil is not food-safe and can cause digestive issues.",
        buyingTip = "Buy cold-pressed virgin coconut oil from reputed brands. It should smell strongly of coconuts and solidify in cool temperatures."
    ),

    FoodGuideEntry(
        id = "olive_oil",
        name = "Olive Oil",
        emoji = "🫒",
        category = FoodCategory.OILS,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Sunflower oil", "Soybean oil", "Palm oil", "Cheaper olive pomace oil"),
        tests = listOf(
            AdulterationTest(
                testName = "Refrigerator Test",
                whatYouNeed = "Refrigerator, small container",
                steps = listOf(
                    "Pour some olive oil into a container.",
                    "Refrigerate for 2–3 hours.",
                    "Observe what happens."
                ),
                pureResult = "Extra virgin olive oil partially solidifies or becomes cloudy/thick due to its natural wax content.",
                adulteratedResult = "Remains completely clear and liquid — mixed with other oils that don't solidify at refrigerator temperatures.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Adulterated olive oil misses the polyphenols and healthy fats that make extra virgin olive oil beneficial. You pay premium price for low-quality oil.",
        buyingTip = "Buy only extra virgin olive oil with a certified origin (Italian, Spanish, Greek). Price below ₹800/liter for extra virgin is suspicious. Check for a harvest date on the bottle."
    ),

    FoodGuideEntry(
        id = "groundnut_oil",
        name = "Groundnut Oil",
        emoji = "🥜",
        category = FoodCategory.OILS,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Argemone oil", "Mineral oil", "Cottonseed oil"),
        tests = listOf(
            AdulterationTest(
                testName = "Color and Clarity Check",
                whatYouNeed = "A clear glass",
                steps = listOf(
                    "Pour oil into a clear glass.",
                    "Hold it up to light and observe color and clarity."
                ),
                pureResult = "Clear, light golden/yellowish color with no cloudiness or deposits.",
                adulteratedResult = "Unusually dark color, cloudiness, or foreign particles visible.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Same as mustard oil — argemone oil contamination in groundnut oil has also caused deaths. Mineral oil is carcinogenic.",
        buyingTip = "Buy branded groundnut oil with AGMARK certification. Avoid loose oil purchased from refilling stations."
    ),

    FoodGuideEntry(
        id = "sesame_oil",
        name = "Sesame Oil (Til Oil)",
        emoji = "🌰",
        category = FoodCategory.OILS,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Cheaper vegetable oils", "Artificial sesame flavoring"),
        tests = listOf(
            AdulterationTest(
                testName = "Baudouin-like Test",
                whatYouNeed = "A small jar, furfural (chemical stores)",
                steps = listOf(
                    "Mix 0.1 ml of furfural with 5 ml of oil.",
                    "Add 5 ml of concentrated HCl.",
                    "Shake well."
                ),
                pureResult = "A bright red color forms in the aqueous layer — this confirms sesame oil.",
                adulteratedResult = "No red color — the oil does not contain sesame oil at the claimed purity.",
                difficulty = TestDifficulty.ADVANCED
            ),
            AdulterationTest(
                testName = "Smell Test",
                whatYouNeed = "Your nose",
                steps = listOf("Heat a small amount of sesame oil.", "Smell it as it heats."),
                pureResult = "Strong, nutty, roasted aroma that intensifies on heating.",
                adulteratedResult = "Weak or artificial smell — mixed with other oils.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Adulteration reduces the beneficial lignans (sesamin, sesamol) in sesame oil that provide antioxidant and anti-inflammatory benefits.",
        buyingTip = "Cold-pressed sesame oil from reputed brands. Should have an intense roasted aroma. Pale, odorless 'sesame oil' is likely adulterated."
    ),

    FoodGuideEntry(
        id = "palm_oil",
        name = "Palm Oil",
        emoji = "🌴",
        category = FoodCategory.OILS,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Other vegetable oils", "Mineral oil"),
        tests = listOf(
            AdulterationTest(
                testName = "Color Check",
                whatYouNeed = "A clear glass",
                steps = listOf("Pour palm oil into a clear glass.", "Observe the color."),
                pureResult = "Natural palm oil is deep orange-red. Refined palm oil is light yellow.",
                adulteratedResult = "Colorless or very pale — may be excessively refined or mixed with other oils.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Palm oil itself is high in saturated fats. Mineral oil adulteration is toxic.",
        buyingTip = "Buy from certified brands. Ensure the product is food-grade and not industrial grade."
    ),

    // ═══════════════════════════════════════
    //  SWEETENERS (3)
    // ═══════════════════════════════════════

    FoodGuideEntry(
        id = "honey",
        name = "Honey",
        emoji = "🍯",
        category = FoodCategory.SWEETENERS,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Sugar syrup", "Corn syrup", "Rice syrup", "Artificial sweeteners", "Glucose"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Drop Test",
                whatYouNeed = "A glass of water",
                steps = listOf(
                    "Drop a teaspoon of honey into a glass of water.",
                    "Do NOT stir.",
                    "Observe what happens."
                ),
                pureResult = "Pure honey does not dissolve immediately. It settles to the bottom as a lump.",
                adulteratedResult = "Dissolves quickly and spreads through the water — sugar syrup or corn syrup mixed in.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Flame Test",
                whatYouNeed = "A match stick, honey",
                steps = listOf(
                    "Dip a matchstick head in the honey.",
                    "Strike it against the matchbox to light it.",
                    "Observe if it lights."
                ),
                pureResult = "Pure honey ignites the match easily — it is flammable.",
                adulteratedResult = "Match doesn't light or goes out immediately — water content from added sugar syrup prevents ignition.",
                difficulty = TestDifficulty.MEDIUM
            ),
            AdulterationTest(
                testName = "Thumb Test",
                whatYouNeed = "Your thumb",
                steps = listOf(
                    "Apply a small drop of honey on your thumb.",
                    "Observe how it behaves."
                ),
                pureResult = "Thick, stays in place and doesn't drip or spread.",
                adulteratedResult = "Runny, spreads quickly, drips off — watery consistency from sugar syrup.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Sugar syrup and corn syrup in honey spikes blood sugar rapidly — dangerous for diabetics who rely on honey as a safer sugar alternative. Loses all antibacterial, antioxidant, and medicinal properties of real honey.",
        buyingTip = "Buy raw, unfiltered honey from beekeepers or certified brands. NMR-tested honey brands (like Apis Himalaya, Dabur) are safer. Honey that crystallizes over time is more likely to be pure."
    ),

    FoodGuideEntry(
        id = "sugar",
        name = "Sugar",
        emoji = "🍬",
        category = FoodCategory.SWEETENERS,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Chalk powder", "Urea", "Washing soda"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Clarity Test",
                whatYouNeed = "A glass of water",
                steps = listOf(
                    "Dissolve 2 teaspoons of sugar in a glass of water.",
                    "Observe the clarity of the dissolved solution."
                ),
                pureResult = "Sugar dissolves completely to give a perfectly clear, transparent solution.",
                adulteratedResult = "Solution is cloudy or has undissolved white residue at the bottom — chalk or other insoluble adulterants.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Chalk powder (calcium carbonate) in large amounts can cause kidney stones. Washing soda is an irritant and causes digestive issues.",
        buyingTip = "Buy packaged, branded sugar. The crystals should be uniform in size and it should dissolve completely and clearly in water."
    ),

    FoodGuideEntry(
        id = "jaggery",
        name = "Jaggery (Gur)",
        emoji = "🟤",
        category = FoodCategory.SWEETENERS,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Chalk powder", "Washing soda", "Artificial colors (metanil yellow)", "Sodium hydrosulphite for whitening"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Test",
                whatYouNeed = "A glass of water",
                steps = listOf(
                    "Break off a small piece of jaggery and put it in water.",
                    "Stir to dissolve.",
                    "Observe the color and any residue."
                ),
                pureResult = "Dissolves completely to give a brownish liquid with no residue and natural molasses-like smell.",
                adulteratedResult = "Leaves white chalky residue at bottom, or turns unnaturally bright yellow — chalk or artificial dye.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Taste Test",
                whatYouNeed = "Your taste buds",
                steps = listOf(
                    "Taste a small piece of jaggery.",
                    "Notice the taste and any aftertaste."
                ),
                pureResult = "Sweet, complex, slightly caramel-like with a earthy, molasses undertone.",
                adulteratedResult = "Sharp, salty, or soapy aftertaste — indicates washing soda or other alkaline adulterants.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Sodium hydrosulphite (used for bleaching to make lighter-colored jaggery) is toxic and can cause respiratory issues. Artificial dyes can cause allergic reactions and may be carcinogenic.",
        buyingTip = "Natural jaggery is dark brown. Very light, golden, or perfectly uniform jaggery is likely chemically treated. Buy from trusted local jaggery makers or certified organic brands."
    ),

    // ═══════════════════════════════════════
    //  GRAINS & FLOURS (5)
    // ═══════════════════════════════════════

    FoodGuideEntry(
        id = "wheat_flour",
        name = "Wheat Flour (Atta)",
        emoji = "🌾",
        category = FoodCategory.GRAINS,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Chalk powder", "Barium sulphate", "Inferior grain flour", "Talc"),
        tests = listOf(
            AdulterationTest(
                testName = "HCl Test for Chalk",
                whatYouNeed = "Dilute hydrochloric acid (HCl)",
                steps = listOf(
                    "Take a teaspoon of atta in a bowl.",
                    "Add a few drops of dilute HCl.",
                    "Observe if there is any fizzing/bubbling."
                ),
                pureResult = "No or very mild fizzing.",
                adulteratedResult = "Brisk fizzing/bubbling — chalk (calcium carbonate) reacts with acid to release CO2.",
                difficulty = TestDifficulty.MEDIUM
            ),
            AdulterationTest(
                testName = "Smell and Color Test",
                whatYouNeed = "Your senses",
                steps = listOf(
                    "Smell a handful of flour.",
                    "Rub it between your fingers.",
                    "Observe the color and texture."
                ),
                pureResult = "Slightly sweet, wheaty aroma. Cream/off-white color. Smooth when rubbed.",
                adulteratedResult = "Gritty when rubbed (talc/chalk). Unusually white (bleaching agents). No wheat aroma.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Barium sulphate and talc are indigestible and accumulate in the body. Chalk in large amounts can cause kidney problems. Bleaching agents (benzoyl peroxide) can destroy vitamins.",
        buyingTip = "Buy whole wheat atta from certified brands. Freshly milled atta from a chakki (flour mill) is generally unadulterated. Check for the FSSAI mark."
    ),

    FoodGuideEntry(
        id = "rice",
        name = "Rice",
        emoji = "🍚",
        category = FoodCategory.GRAINS,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Stones and pebbles", "Chalk-coated grains", "Plastic rice (rare)", "Polished with talc"),
        tests = listOf(
            AdulterationTest(
                testName = "Visual Inspection",
                whatYouNeed = "Your eyes, a flat surface",
                steps = listOf(
                    "Spread rice on a flat white surface.",
                    "Look through the grains carefully.",
                    "Check for any grains that look different in color or texture."
                ),
                pureResult = "Uniform grains of consistent size and translucent/white color.",
                adulteratedResult = "Visible stones, discolored grains, or chalky-white non-rice particles.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Plastic Rice Test (if suspected)",
                whatYouNeed = "Boiling water",
                steps = listOf(
                    "Cook a small sample of rice.",
                    "Observe if some grains clump together unnaturally or remain hard.",
                    "Taste the texture carefully."
                ),
                pureResult = "Grains cook uniformly, become soft and separate normally.",
                adulteratedResult = "Some grains remain hard and plastic-like even after cooking, or form a thick plastic-like film.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Talc coating (used to make rice appear polished and shiny) can contain asbestos-like fibers and is carcinogenic with long-term consumption.",
        buyingTip = "Wash rice thoroughly before cooking to remove talc coating. Buy from reputed brands. Parboiled rice generally has less adulteration than polished white rice."
    ),

    FoodGuideEntry(
        id = "besan",
        name = "Besan (Gram Flour)",
        emoji = "🟡",
        category = FoodCategory.GRAINS,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Maize flour", "Artificial yellow color (metanil yellow)", "Khesari dal flour (toxic)"),
        tests = listOf(
            AdulterationTest(
                testName = "Color and Smell Test",
                whatYouNeed = "Your senses",
                steps = listOf(
                    "Look at the color of the besan.",
                    "Smell it carefully.",
                    "Rub a small amount between wet fingers and taste."
                ),
                pureResult = "Natural golden-yellow color (not intense yellow). Slightly nutty, earthy smell. Slightly bitter taste characteristic of chickpeas.",
                adulteratedResult = "Unnaturally bright, vivid yellow — artificial dye. Bland taste and no characteristic smell — maize flour mixed in.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Hydrochloric Acid Test for Metanil Yellow",
                whatYouNeed = "Dilute HCl",
                steps = listOf(
                    "Dissolve some besan in water.",
                    "Add a few drops of dilute HCl.",
                    "Observe the color change."
                ),
                pureResult = "No significant color change.",
                adulteratedResult = "Turns pink or magenta — metanil yellow dye is present.",
                difficulty = TestDifficulty.MEDIUM
            )
        ),
        healthRisks = "Metanil yellow dye is a banned carcinogen. Khesari dal (Lathyrus sativus) flour in large amounts causes lathyrism — an irreversible neurological disease leading to paralysis.",
        buyingTip = "Buy from reputed brands. Genuine besan should be made from chana dal and have a natural chickpea smell. Suspiciously bright yellow besan is a red flag."
    ),

    FoodGuideEntry(
        id = "semolina",
        name = "Semolina (Rava / Sooji)",
        emoji = "🌾",
        category = FoodCategory.GRAINS,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Sand", "Stones", "Other grain powder", "Excessive rice bran"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Clarity Test",
                whatYouNeed = "A glass of water",
                steps = listOf(
                    "Add a tablespoon of sooji to water.",
                    "Stir well.",
                    "Allow to settle and observe."
                ),
                pureResult = "Water becomes cloudy but no gritty sediment or visible sand particles.",
                adulteratedResult = "Gritty sediment settles at the bottom — sand or stone dust present.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Sand and stone particles cause damage to tooth enamel and can injure the gastrointestinal lining.",
        buyingTip = "Buy packaged semolina from trusted brands. Fresh sooji should have a clean wheat smell and be free of any gritty feeling."
    ),

    FoodGuideEntry(
        id = "maida",
        name = "Maida (Refined Flour)",
        emoji = "⚪",
        category = FoodCategory.GRAINS,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Chalk powder", "Excessive bleaching agents", "Talc powder"),
        tests = listOf(
            AdulterationTest(
                testName = "HCl Bubble Test",
                whatYouNeed = "Dilute HCl",
                steps = listOf(
                    "Add a few drops of dilute HCl to a teaspoon of maida.",
                    "Observe for fizzing."
                ),
                pureResult = "No bubbling or fizzing.",
                adulteratedResult = "Fizzes and bubbles — chalk (calcium carbonate) present.",
                difficulty = TestDifficulty.MEDIUM
            )
        ),
        healthRisks = "Potassium bromate and benzoyl peroxide used as bleaching agents in maida are carcinogenic. Potassium bromate is banned in over 50 countries.",
        buyingTip = "Minimize maida consumption for health reasons anyway. If buying, choose brands that claim 'bromate-free' or use certified organic flour."
    ),

    // ═══════════════════════════════════════
    //  PULSES (5)
    // ═══════════════════════════════════════

    FoodGuideEntry(
        id = "moong_dal",
        name = "Moong Dal",
        emoji = "💚",
        category = FoodCategory.PULSES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Metanil yellow dye", "Kesari dal", "Stone powder"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Color Test for Dye",
                whatYouNeed = "A glass of water",
                steps = listOf(
                    "Soak a handful of moong dal in water for 10 minutes.",
                    "Observe the color of the water."
                ),
                pureResult = "Water becomes slightly yellowish-green naturally.",
                adulteratedResult = "Water turns bright, vivid yellow — artificial dye is leaching out.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Metanil yellow is a banned, toxic, cancer-causing dye. Kesari dal causes lathyrism — irreversible nerve damage leading to leg paralysis.",
        buyingTip = "Freshly hulled dal should not bleed color into water excessively. Buy from reputed brands in sealed packets."
    ),

    FoodGuideEntry(
        id = "toor_dal",
        name = "Arhar / Toor Dal",
        emoji = "🟡",
        category = FoodCategory.PULSES,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Kesari dal (Lathyrus sativus) — TOXIC", "Artificial color coating", "Mineral oil coating"),
        tests = listOf(
            AdulterationTest(
                testName = "HCl Dye Test",
                whatYouNeed = "Dilute HCl",
                steps = listOf(
                    "Take 5 ml of wash water from the dal.",
                    "Add a few drops of dilute HCl.",
                    "Observe the color."
                ),
                pureResult = "No pink or magenta color.",
                adulteratedResult = "Water turns pink or red — synthetic dye (metanil yellow) is present.",
                difficulty = TestDifficulty.MEDIUM
            ),
            AdulterationTest(
                testName = "Paper Towel Dye Test",
                whatYouNeed = "Damp paper towel",
                steps = listOf(
                    "Rub a few dal grains on a damp white paper towel.",
                    "Observe the color left behind."
                ),
                pureResult = "Slight natural yellowish stain.",
                adulteratedResult = "Bright, vivid yellow/orange stain — artificial color or mineral oil coating.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Kesari dal (chickling vetch) causes lathyrism — a progressive, permanent neurological disease that causes lower limb paralysis. It predominantly affects the poor who can't afford pure dal. Kesari dal is BANNED IN INDIA.",
        buyingTip = "Buy branded, packaged toor dal only. The dal should not leave bright color on your hands when rubbed. Avoid oily/shiny looking dal — mineral oil coating is used to make old dal look fresh."
    ),

    FoodGuideEntry(
        id = "chana_dal",
        name = "Chana Dal",
        emoji = "🟤",
        category = FoodCategory.PULSES,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Kesari dal (toxic)", "Stones"),
        tests = listOf(
            AdulterationTest(
                testName = "Size and Shape Inspection",
                whatYouNeed = "Your eyes",
                steps = listOf(
                    "Spread the dal on a flat white surface.",
                    "Look for any irregularly shaped or sized grains.",
                    "Kesari dal is smaller, angular, and asymmetric."
                ),
                pureResult = "Uniform, round, split grains all of similar size.",
                adulteratedResult = "Irregular, angular, or unusually small grains mixed in — could be kesari dal.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Even small amounts of kesari dal consumed regularly can trigger lathyrism over time.",
        buyingTip = "Buy from trusted brands. Sort through the dal before cooking and discard any irregular grains."
    ),

    FoodGuideEntry(
        id = "masoor_dal",
        name = "Masoor Dal",
        emoji = "🔴",
        category = FoodCategory.PULSES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Kesari dal", "Artificial red/orange color"),
        tests = listOf(
            AdulterationTest(
                testName = "Washing Water Test",
                whatYouNeed = "Water",
                steps = listOf(
                    "Wash the dal with water.",
                    "Observe the color of the wash water."
                ),
                pureResult = "Water becomes slightly reddish-brown naturally after 1-2 washes.",
                adulteratedResult = "Intense red/orange color that doesn't fade after multiple washes — artificial color.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Artificial colors are potentially carcinogenic. Kesari dal causes lathyrism.",
        buyingTip = "Good quality masoor dal has a natural brownish-orange color. If it bleeds intense color in water repeatedly, it is adulterated."
    ),

    FoodGuideEntry(
        id = "urad_dal",
        name = "Urad Dal",
        emoji = "⚪",
        category = FoodCategory.PULSES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Kesari dal", "Artificial whitening agents", "Starch coating"),
        tests = listOf(
            AdulterationTest(
                testName = "Color and Wash Test",
                whatYouNeed = "Water",
                steps = listOf(
                    "Wash white urad dal with water.",
                    "Observe if anything washes off."
                ),
                pureResult = "Slightly milky wash water, normal for dehusked dal.",
                adulteratedResult = "Bright white, chalky liquid washes off — indicates artificial whitening or chalk coating.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Whitening agents can include harmful chemicals. Kesari dal causes lathyrism.",
        buyingTip = "Natural urad dal has a slightly off-white or cream color. Brilliant pure-white dal may have been artificially whitened."
    ),

    // ═══════════════════════════════════════
    //  BEVERAGES (4)
    // ═══════════════════════════════════════

    FoodGuideEntry(
        id = "tea",
        name = "Tea Leaves",
        emoji = "🍵",
        category = FoodCategory.BEVERAGES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Used/exhausted tea leaves", "Iron filings", "Chalk powder", "Artificial color", "Sawdust"),
        tests = listOf(
            AdulterationTest(
                testName = "Iron Filings Test",
                whatYouNeed = "A magnet",
                steps = listOf(
                    "Spread tea leaves on a flat, non-metallic surface.",
                    "Run a strong magnet slowly over the leaves.",
                    "Observe if any particles stick to the magnet."
                ),
                pureResult = "No or negligible magnetic particles.",
                adulteratedResult = "Iron filings stick to the magnet — iron has been mixed in to increase apparent weight.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Paper Blot Color Test",
                whatYouNeed = "White paper, water",
                steps = listOf(
                    "Place tea leaves on wet white paper.",
                    "Press gently and observe the color that transfers."
                ),
                pureResult = "Leaves a natural golden-brown to dark brown stain.",
                adulteratedResult = "Leaves an unnatural bright red or dark black stain immediately — artificial coloring.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Iron filings can cause iron overload. Used tea has no caffeine or antioxidants but still contains tannins that can irritate the stomach. Artificial colors may be carcinogenic.",
        buyingTip = "Buy branded, sealed tea from known producers. Loose tea is more susceptible to adulteration. Dust tea is more commonly adulterated than whole leaf grades."
    ),

    FoodGuideEntry(
        id = "coffee",
        name = "Coffee",
        emoji = "☕",
        category = FoodCategory.BEVERAGES,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Tamarind seeds (powdered)", "Chicory (not harmful but undisclosed)", "Date seeds", "Caramel color", "Artificial roasting smell"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Float Test",
                whatYouNeed = "A glass of cold water",
                steps = listOf(
                    "Add a teaspoon of ground coffee to cold water.",
                    "Do not stir. Observe."
                ),
                pureResult = "Pure coffee floats on the water surface without dissolving.",
                adulteratedResult = "Chicory/tamarind powder sinks to the bottom immediately — adulterants are denser and more soluble.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Paper Blot Test",
                whatYouNeed = "Blotting paper, water",
                steps = listOf(
                    "Place a pinch of coffee on wet blotting paper.",
                    "Wait 2 minutes and observe the stain pattern."
                ),
                pureResult = "A clear brown stain with a slightly oily ring around it (coffee oils).",
                adulteratedResult = "Stain spreads widely with no oily ring — no natural coffee oils, indicating adulterants.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Tamarind seed powder has no nutritional similarity to coffee. Artificial caramel colors (Class IV) may contain harmful compounds. You lose all the antioxidants and health benefits of real coffee.",
        buyingTip = "Buy whole coffee beans and grind them yourself for guaranteed purity. For filter coffee powder, buy from certified brands. Check if the label states '100% coffee' or 'coffee + chicory' blend."
    ),

    FoodGuideEntry(
        id = "fruit_juice",
        name = "Fruit Juices",
        emoji = "🧃",
        category = FoodCategory.BEVERAGES,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Artificial colors and flavors", "Synthetic sweeteners (saccharin)", "Excessive added sugar", "Water with pulp concentrate"),
        tests = listOf(
            AdulterationTest(
                testName = "Label Reading Test",
                whatYouNeed = "The product label",
                steps = listOf(
                    "Read the ingredient list on the juice pack.",
                    "Check what percentage is actual fruit content.",
                    "Look for terms like '100% juice', 'fruit drink', 'nectar' etc."
                ),
                pureResult = "100% fruit content, no added sugar, no artificial colors or flavors listed.",
                adulteratedResult = "Low fruit content (less than 10%), artificial colors listed, artificial sweeteners (saccharin, aspartame), water as primary ingredient.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Saccharin at high doses is a potential carcinogen. Excessive sugar in fruit drinks causes obesity, diabetes, and tooth decay. Artificial colors cause hyperactivity in children.",
        buyingTip = "Real fruit juice says '100% juice'. 'Fruit drink', 'fruit beverage', 'nectar', 'cocktail' are mostly water and sugar. Fresh-squeezed juice is always the best option."
    ),

    FoodGuideEntry(
        id = "coconut_water",
        name = "Coconut Water",
        emoji = "🥥",
        category = FoodCategory.BEVERAGES,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Tap water + sugar", "Artificial coconut flavor", "Preservatives"),
        tests = listOf(
            AdulterationTest(
                testName = "Fresh vs Packaged Test",
                whatYouNeed = "Common sense",
                steps = listOf(
                    "Compare taste of packaged coconut water with fresh coconut water.",
                    "Fresh coconut water is naturally slightly sweet and slightly salty.",
                    "Note any artificial sweetness or flavor."
                ),
                pureResult = "Slightly sweet, mildly salty, very refreshing with a light coconut aroma.",
                adulteratedResult = "Overly sweet, no natural salty undertone, or an artificial coconut candy smell.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Preservatives in packaged coconut water destroy the natural electrolytes that make it beneficial. Artificial sweeteners can affect gut bacteria.",
        buyingTip = "Always drink fresh coconut water directly from the coconut. Packaged versions lose most of their nutritional benefits due to processing and preservation."
    ),

    // ═══════════════════════════════════════
    //  KITCHEN BASICS (4)
    // ═══════════════════════════════════════

    FoodGuideEntry(
        id = "salt",
        name = "Salt",
        emoji = "🧂",
        category = FoodCategory.BASICS,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Chalk powder", "White sand", "Industrial salt", "Washing soda"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Clarity Test",
                whatYouNeed = "A glass of water",
                steps = listOf(
                    "Dissolve a spoon of salt in a glass of water.",
                    "Observe if the solution is clear or cloudy."
                ),
                pureResult = "Completely clear and transparent solution.",
                adulteratedResult = "Cloudy or milky solution — chalk or other insoluble materials present.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Industrial salt may contain heavy metals or unrefined impurities. Washing soda is corrosive to the digestive system.",
        buyingTip = "Buy branded, iodized salt from reputed companies. FSSAI mandates iodization of all salt in India — unlicensed salt may not be iodized, leading to iodine deficiency."
    ),

    FoodGuideEntry(
        id = "baking_soda",
        name = "Baking Soda",
        emoji = "⬜",
        category = FoodCategory.BASICS,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Washing soda (soda ash)", "Chalk powder"),
        tests = listOf(
            AdulterationTest(
                testName = "Vinegar Fizz Test",
                whatYouNeed = "A spoon of vinegar",
                steps = listOf(
                    "Add a spoon of baking soda to a bowl.",
                    "Add a few drops of vinegar.",
                    "Observe the reaction."
                ),
                pureResult = "Immediate, vigorous, clean fizzing — CO2 is released.",
                adulteratedResult = "Weak fizzing or a yellowish tinge (washing soda also fizzes but differently).",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Washing soda (sodium carbonate) is much more alkaline than baking soda (sodium bicarbonate) and can cause mouth and throat irritation, stomach upsets.",
        buyingTip = "Buy packaged baking soda from food-grade brands like Eno or popular baking brands. Do not buy from hardware stores."
    ),

    FoodGuideEntry(
        id = "vinegar",
        name = "Vinegar",
        emoji = "🫙",
        category = FoodCategory.BASICS,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Industrial acetic acid (synthetic, not food-grade)", "Artificial color"),
        tests = listOf(
            AdulterationTest(
                testName = "Label Check",
                whatYouNeed = "The label",
                steps = listOf(
                    "Read the label carefully.",
                    "Check if it says 'brewed vinegar' or 'synthetic vinegar'.",
                    "Check acidity level (should be 4-8% acetic acid for food grade)."
                ),
                pureResult = "Brewed/fermented from natural sources (apple, grain, rice). Acidity 4-8%.",
                adulteratedResult = "Synthetic acetic acid is the main ingredient — may contain industrial-grade impurities.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Industrial acetic acid can contain methanol and other harmful solvents not present in brewed vinegar. These are toxic and can cause organ damage.",
        buyingTip = "Buy branded, fermented vinegar. 'Apple cider vinegar' with 'the mother' (cloudy appearance) is a sign of authentic fermented vinegar."
    ),

    FoodGuideEntry(
        id = "tamarind",
        name = "Tamarind",
        emoji = "🟤",
        category = FoodCategory.BASICS,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Artificial souring agents", "Clay/mud mixed into pulp", "Artificial color", "Preservatives"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Dissolve Test",
                whatYouNeed = "Warm water",
                steps = listOf(
                    "Dissolve a piece of tamarind in warm water.",
                    "Filter or strain the solution.",
                    "Observe color and any gritty residue."
                ),
                pureResult = "Dark brown liquid, sour smell, no gritty residue. Natural sourness.",
                adulteratedResult = "Clay/mud residue at the bottom. Artificially bright color or very sharp, chemical sour taste.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Clay and mud can contain heavy metals like lead and arsenic. Chemical souring agents can cause gastric irritation.",
        buyingTip = "Buy whole, unprocessed tamarind pods and remove the pulp yourself. If buying processed pulp, choose sealed, branded packages."
    ),

    // ═══════════════════════════════════════
    //  PROTEIN (3)
    // ═══════════════════════════════════════

    FoodGuideEntry(
        id = "eggs",
        name = "Eggs",
        emoji = "🥚",
        category = FoodCategory.PROTEIN,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Rotten/stale eggs sold as fresh", "Artificial/fake plastic eggs (rare)", "Eggs from hormonally treated hens"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Float Test for Freshness",
                whatYouNeed = "A bowl of water",
                steps = listOf(
                    "Place the egg gently in a bowl of water.",
                    "Observe its position."
                ),
                pureResult = "Fresh egg sinks to the bottom and lies flat on its side.",
                adulteratedResult = "Stale egg stands upright or floats to the surface — air cell has grown large due to aging.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Shake Test",
                whatYouNeed = "Your ear",
                steps = listOf(
                    "Hold the egg next to your ear.",
                    "Shake it gently.",
                    "Listen carefully."
                ),
                pureResult = "No sloshing sound — contents are intact and fresh.",
                adulteratedResult = "Sloshing or sloshy sound — egg contents have broken down, indicating it is stale.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Stale eggs contain Salmonella which causes severe food poisoning, diarrhea, vomiting, and can be fatal for the elderly and immunocompromised.",
        buyingTip = "Always check manufacturing and expiry dates. Store eggs in the refrigerator. A 'best before' date of 3-5 weeks from today indicates fresh stock."
    ),

    FoodGuideEntry(
        id = "fish",
        name = "Fish",
        emoji = "🐟",
        category = FoodCategory.PROTEIN,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Formalin (formaldehyde solution) for preservation", "Ammonia injection to preserve", "Water injection to increase weight", "Harmful dyes to improve appearance"),
        tests = listOf(
            AdulterationTest(
                testName = "Smell Test",
                whatYouNeed = "Your nose",
                steps = listOf(
                    "Smell the fish closely.",
                    "Fresh fish should smell like the ocean/river.",
                    "Any chemical or pungent non-fishy smell is suspicious."
                ),
                pureResult = "Clean, fresh sea/river smell. Not overly pungent.",
                adulteratedResult = "Chemical smell, or no smell at all on fish that should be smelly — formalin is masking the natural odor.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Formalin Detection (KMnO4 Test)",
                whatYouNeed = "Potassium permanganate solution",
                steps = listOf(
                    "Place a small piece of fish in a bowl of dilute KMnO4 solution.",
                    "Observe the color change."
                ),
                pureResult = "KMnO4 solution remains purple for a long time.",
                adulteratedResult = "KMnO4 rapidly loses its purple color and turns colorless/brown — formalin is oxidizing it.",
                difficulty = TestDifficulty.ADVANCED
            ),
            AdulterationTest(
                testName = "Texture Test",
                whatYouNeed = "Your fingers",
                steps = listOf(
                    "Press the flesh of the fish.",
                    "Observe how it responds."
                ),
                pureResult = "Flesh springs back quickly. Firm, not mushy.",
                adulteratedResult = "Flesh doesn't spring back, is unusually hard or leaves a dent — water injection or formaldehyde hardening.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Formalin (formaldehyde) is used to embalm dead bodies. It is a confirmed carcinogen causing cancer of the nose, throat, and lungs. It is acutely toxic even in small doses. India has seen multiple formalin-fish scares causing market bans.",
        buyingTip = "Buy fish from reputed, licensed fish markets. Fresh fish should be bright-eyed, have red/pink gills, and smell of the sea — not of chemicals. Wash fish thoroughly before cooking."
    ),

    FoodGuideEntry(
        id = "chicken",
        name = "Chicken / Meat",
        emoji = "🍗",
        category = FoodCategory.PROTEIN,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Injected with water/saline to increase weight", "Growth hormones", "Artificial color for appearance", "Formalin/chemical preservation"),
        tests = listOf(
            AdulterationTest(
                testName = "Color Check",
                whatYouNeed = "Your eyes",
                steps = listOf(
                    "Observe the color of the raw chicken/meat.",
                    "Fresh chicken should be light pink.",
                    "Observe the uniformity of color."
                ),
                pureResult = "Natural light pink color, uniform, no unnatural brightness.",
                adulteratedResult = "Unnaturally bright pink or orange tinge, or artificially uniform color — artificial dye.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Water Injection Test",
                whatYouNeed = "Kitchen paper towel",
                steps = listOf(
                    "Dab a piece of chicken with a kitchen paper towel.",
                    "Pat the surface.",
                    "Observe how much liquid is absorbed."
                ),
                pureResult = "Minimal liquid absorption — normal moisture.",
                adulteratedResult = "Excessive water immediately soaks the paper towel — water has been injected to increase weight.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Formalin is carcinogenic. Growth hormones disrupt human hormonal balance, potentially causing early puberty in children and increasing cancer risk. Antibiotic residues in poultry contribute to antibiotic-resistant bacteria in humans.",
        buyingTip = "Buy from licensed, certified poultry shops. Look for FSSAI/organic certification. 'Free range' or 'organic' chicken is less likely to have hormone treatment. Avoid pre-packaged chicken with excessive liquid in the bag."
    ),

    // ═══════════════════════════════════════
    //  FRUITS & VEGETABLES (8)
    // ═══════════════════════════════════════

    FoodGuideEntry(
        id = "apples",
        name = "Apples",
        emoji = "🍎",
        category = FoodCategory.FRUITS_VEGGIES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Wax coating (carnauba or paraffin wax)", "Artificial ripening agents", "Surface sprayed artificial color"),
        tests = listOf(
            AdulterationTest(
                testName = "Warm Water and Scratch Test",
                whatYouNeed = "Warm water, your fingernail",
                steps = listOf(
                    "Dip the apple in warm water for a minute.",
                    "Rub the surface hard with your fingernail or a cloth.",
                    "Observe if any white/waxy substance comes off."
                ),
                pureResult = "Natural wax from the apple itself (food-safe). Very thin film, no excessive residue.",
                adulteratedResult = "A thick white or translucent wax layer peels off in chunks — artificial wax coating.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Paraffin wax is a petroleum byproduct — not food safe. Artificial colors sprayed on apple surfaces can contain toxic heavy metals. Artificial ripening agents prevent natural antioxidant development.",
        buyingTip = "Wash apples thoroughly with warm water and scrub. Peel apples if you're concerned. Organic, locally grown apples are safer than imported, waxed varieties."
    ),

    FoodGuideEntry(
        id = "watermelon",
        name = "Watermelon",
        emoji = "🍉",
        category = FoodCategory.FRUITS_VEGGIES,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Red dye injected into flesh", "Oxytocin/ripening hormones injected", "Sugar water injection to make it sweeter"),
        tests = listOf(
            AdulterationTest(
                testName = "White Towel Test for Dye",
                whatYouNeed = "White paper or tissue",
                steps = listOf(
                    "Cut the watermelon and press a piece of white tissue against the flesh.",
                    "Rub gently.",
                    "Observe what transfers to the tissue."
                ),
                pureResult = "A natural pinkish-red tinge transfers, but light and not intensely colored.",
                adulteratedResult = "Bright, vivid red color immediately soaks the tissue — artificial red dye injected into the flesh.",
                difficulty = TestDifficulty.EASY
            ),
            AdulterationTest(
                testName = "Needle Hole Test",
                whatYouNeed = "Your eyes — look at the rind",
                steps = listOf(
                    "Examine the outer rind of the watermelon carefully.",
                    "Look for small pin-prick holes, especially near the stem end."
                ),
                pureResult = "Smooth, uniform rind with no visible puncture marks.",
                adulteratedResult = "Small holes visible, often multiple — injection points for dye or sugar water.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Oxytocin is a hormone — its use in food produces is BANNED in India. Artificial red dyes can be carcinogenic. Injecting sugar water creates a breeding ground for bacteria inside the fruit.",
        buyingTip = "Buy watermelons from reputed vendors. A ripe watermelon should have a yellow spot on the bottom, sound hollow when tapped, and have dried/brown tendril near the stem."
    ),

    FoodGuideEntry(
        id = "grapes",
        name = "Grapes",
        emoji = "🍇",
        category = FoodCategory.FRUITS_VEGGIES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Excessive pesticide residue", "Artificial ripening agents", "Color enhancement sprays"),
        tests = listOf(
            AdulterationTest(
                testName = "Wash Test",
                whatYouNeed = "Water, salt",
                steps = listOf(
                    "Soak grapes in salted water for 15 minutes.",
                    "Rinse with clean water.",
                    "Observe the color of the water."
                ),
                pureResult = "Water may turn slightly purple/natural color from grape skin but not artificially bright.",
                adulteratedResult = "Water turns bright, unnatural purple or blue — artificial color enhancement sprays.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Pesticide residues on grapes are among the highest of any fruit. Chronic pesticide exposure is linked to neurological damage, hormonal disruption, and cancer.",
        buyingTip = "Always wash grapes thoroughly before eating. Buy organic grapes when available. Remove grapes from stems and wash under running water for at least 30 seconds."
    ),

    FoodGuideEntry(
        id = "spinach",
        name = "Spinach / Palak",
        emoji = "🥬",
        category = FoodCategory.FRUITS_VEGGIES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Malachite green dye to appear fresh", "Excessive pesticides", "Contaminated irrigation water"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Wash Test for Malachite Green",
                whatYouNeed = "A bowl of water",
                steps = listOf(
                    "Soak the spinach leaves in a bowl of water for 5 minutes.",
                    "Observe the color of the wash water."
                ),
                pureResult = "Water becomes slightly green from natural chlorophyll — this is normal and the green should be very faint.",
                adulteratedResult = "Water turns bright, vivid, cyan-green quickly — malachite green dye is being used to make old/wilted spinach look fresh.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Malachite green is a confirmed carcinogen and a reproductive toxin. It bioaccumulates in the body. Contaminated irrigation water can introduce heavy metals and pathogens.",
        buyingTip = "Buy spinach from reputed vendors. Fresh spinach should not bleed intensely green water. Wash all leafy vegetables very thoroughly before cooking or eating raw."
    ),

    FoodGuideEntry(
        id = "peas",
        name = "Green Peas",
        emoji = "🫛",
        category = FoodCategory.FRUITS_VEGGIES,
        riskLevel = RiskLevel.HIGH,
        commonAdulterants = listOf("Metanil yellow or malachite green to appear fresh/green", "Artificial ripening agents"),
        tests = listOf(
            AdulterationTest(
                testName = "Water Color Test",
                whatYouNeed = "Warm water",
                steps = listOf(
                    "Place peas in warm water for 5–10 minutes.",
                    "Observe the color of the water."
                ),
                pureResult = "Water turns very slightly greenish — natural chlorophyll.",
                adulteratedResult = "Water turns bright green or yellow immediately — artificial color.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Metanil yellow and malachite green are both carcinogens banned in food products. They are particularly harmful to children.",
        buyingTip = "Buy fresh peas in pods — if they're in the pod, it is very hard to add artificial color. For shelled peas, buy from reputed vegetable vendors."
    ),

    FoodGuideEntry(
        id = "cauliflower",
        name = "Cauliflower",
        emoji = "🥦",
        category = FoodCategory.FRUITS_VEGGIES,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Wax coating", "Excessive pesticides", "Artificial whitening agents"),
        tests = listOf(
            AdulterationTest(
                testName = "Warm Water Wash Test",
                whatYouNeed = "Warm water",
                steps = listOf(
                    "Wash the cauliflower under warm water.",
                    "Rub the florets gently.",
                    "Observe any white residue."
                ),
                pureResult = "Water washes clean, no unusual white film.",
                adulteratedResult = "White waxy or chalky substance washes off — artificial whitening or wax coating.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Excessive pesticide residue can cause hormonal disruption and increases cancer risk over the long term.",
        buyingTip = "Blanch cauliflower in hot water before using. Slightly yellow/natural-colored cauliflower is often more natural than perfectly white ones."
    ),

    FoodGuideEntry(
        id = "tomatoes",
        name = "Tomatoes",
        emoji = "🍅",
        category = FoodCategory.FRUITS_VEGGIES,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Calcium carbide for artificial ripening", "Ethylene gas for ripening (safer)", "Artificial red color sprayed on skin"),
        tests = listOf(
            AdulterationTest(
                testName = "Pressure Test for Ripeness",
                whatYouNeed = "Your fingers",
                steps = listOf(
                    "Press the tomato gently on all sides.",
                    "Observe how it feels inside.",
                    "Cut it open and observe the inside."
                ),
                pureResult = "Uniformly soft, red flesh throughout with natural aroma. Ripe from inside out.",
                adulteratedResult = "Red on outside but green/white/hard inside — artificially ripened externally. Or soft skin with firm hard interior.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Calcium carbide produces acetylene gas and also contains arsenic and phosphorus hydride as impurities — these are toxic and potentially carcinogenic. Artificially ripened tomatoes have far fewer lycopene and vitamins than naturally ripened ones.",
        buyingTip = "Buy tomatoes that are red throughout, not just on the skin. Tomatoes ripened naturally on the vine have superior flavor and nutrition. Avoid buying bright-red tomatoes that feel rock-hard inside."
    ),

    FoodGuideEntry(
        id = "potatoes",
        name = "Potatoes",
        emoji = "🥔",
        category = FoodCategory.FRUITS_VEGGIES,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Old potatoes dyed to look fresh", "Excess pesticide residue", "Artificial color on skin"),
        tests = listOf(
            AdulterationTest(
                testName = "Peel and Scratch Test",
                whatYouNeed = "Your fingernail",
                steps = listOf(
                    "Scratch the skin of the potato.",
                    "Observe the color underneath.",
                    "Look at the exposed flesh."
                ),
                pureResult = "Natural cream-white or yellowish flesh immediately visible under the skin.",
                adulteratedResult = "A colored coating on the skin that comes off when scratched, revealing differently colored skin — artificial dye.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Artificial potato dyes are not approved food colors. Old potatoes can develop solanine — a natural toxin that causes nausea and headaches.",
        buyingTip = "Buy potatoes with smooth skin. Avoid potatoes with green spots (solanine) or that have started sprouting. Wash and peel potatoes before cooking."
    ),

    // ═══════════════════════════════════════
    //  PROCESSED FOODS (5)
    // ═══════════════════════════════════════

    FoodGuideEntry(
        id = "jam",
        name = "Jam / Preserves",
        emoji = "🫙",
        category = FoodCategory.PROCESSED,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Artificial colors", "Excessive sugar beyond labeling", "Cheap thickeners instead of natural pectin", "Low/no real fruit content"),
        tests = listOf(
            AdulterationTest(
                testName = "Label Ingredient Check",
                whatYouNeed = "The product label",
                steps = listOf(
                    "Read the ingredient list.",
                    "Ingredients are listed in descending order by weight.",
                    "Check what comes first."
                ),
                pureResult = "Fruit (by name) is the first ingredient, followed by sugar. No artificial colors listed.",
                adulteratedResult = "Sugar is first, fruit is low on the list, or 'fruit flavoring' instead of actual fruit. Artificial colors like Red 40, Yellow 6 listed.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Artificial colors in jam are linked to hyperactivity in children. Excessive undeclared sugar increases diabetes risk. Synthetic pectin substitutes provide no nutritional benefit.",
        buyingTip = "Buy jam with the shortest ingredient list. Real fruit jam should list the specific fruit first. Homemade jam with real fruit is always the best option."
    ),

    FoodGuideEntry(
        id = "ketchup",
        name = "Tomato Ketchup",
        emoji = "🍅",
        category = FoodCategory.PROCESSED,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Artificial red color (replacing real tomatoes)", "Excessive starch for thickness", "Low tomato content", "Artificial flavoring"),
        tests = listOf(
            AdulterationTest(
                testName = "Iodine Starch Test",
                whatYouNeed = "Iodine solution",
                steps = listOf(
                    "Place a teaspoon of ketchup on a white plate.",
                    "Add 2–3 drops of iodine solution.",
                    "Observe the color change."
                ),
                pureResult = "Some starch is natural in tomatoes — slight blue tinge is acceptable but not intense.",
                adulteratedResult = "Intensely dark blue/black color — excessive starch has been added as a cheap thickener.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "High fructose corn syrup in ketchup contributes significantly to obesity and metabolic syndrome. Artificial colors may cause allergic reactions.",
        buyingTip = "Choose ketchup that lists tomatoes as the first ingredient and has no artificial colors. Less than 5 ingredients is a good sign."
    ),

    FoodGuideEntry(
        id = "bread",
        name = "Bread",
        emoji = "🍞",
        category = FoodCategory.PROCESSED,
        riskLevel = RiskLevel.LOW,
        commonAdulterants = listOf("Potassium bromate (banned carcinogen)", "Benzoyl peroxide (bleaching)", "Artificial flavoring", "Excessive preservatives"),
        tests = listOf(
            AdulterationTest(
                testName = "Label Check for Bromate",
                whatYouNeed = "The product label",
                steps = listOf(
                    "Read the ingredient list on the bread packaging.",
                    "Look for 'potassium bromate' in the list."
                ),
                pureResult = "No potassium bromate listed. Ingredients: flour, water, yeast, salt.",
                adulteratedResult = "Potassium bromate is listed — this is a carcinogen BANNED in India, EU, and many other countries.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Potassium bromate converts to bromide in bread during baking, which is carcinogenic (causes cancer of kidneys, thyroid). It is banned in India and the EU but still found in some products.",
        buyingTip = "Buy 100% whole wheat bread from brands that declare 'bromate-free'. Shorter expiry dates indicate fewer preservatives. Freshly baked artisan bread from a bakery is the healthiest option."
    ),

    FoodGuideEntry(
        id = "noodles",
        name = "Noodles / Pasta",
        emoji = "🍜",
        category = FoodCategory.PROCESSED,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Excessive lead content", "Artificial color for yellow appearance", "MSG beyond safe levels", "Cheap inferior flour"),
        tests = listOf(
            AdulterationTest(
                testName = "Cook and Taste Test",
                whatYouNeed = "Hot water",
                steps = listOf(
                    "Cook a small portion.",
                    "Observe if the water turns bright yellow.",
                    "Taste for any unusual chemical aftertaste."
                ),
                pureResult = "Water turns slightly yellow naturally from egg or wheat pigments. Normal noodle flavor.",
                adulteratedResult = "Water turns vivid yellow immediately — artificial dye. Chemical or metallic aftertaste — possible heavy metals.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Lead contamination (which has been found in some popular noodle brands) causes irreversible brain damage in children, neurological disorders, and kidney damage.",
        buyingTip = "Buy noodles from well-known, large brands that are regularly tested by food safety authorities. Check for FSSAI number. Consume in moderation."
    ),

    FoodGuideEntry(
        id = "chips",
        name = "Chips / Namkeen",
        emoji = "🥨",
        category = FoodCategory.PROCESSED,
        riskLevel = RiskLevel.MEDIUM,
        commonAdulterants = listOf("Rancid/reused frying oil", "Excessive artificial colors", "MSG beyond safe limits", "Artificial flavoring chemicals"),
        tests = listOf(
            AdulterationTest(
                testName = "Oil Smell Test",
                whatYouNeed = "Your nose",
                steps = listOf(
                    "Open the packet and smell the chips.",
                    "Good chips should smell like the flavor stated (masala, cheese, etc.).",
                    "Note any rancid or sharp chemical smell."
                ),
                pureResult = "Smells like the stated flavor. Crisp and fresh.",
                adulteratedResult = "Rancid, stale, or sharp chemical smell — old oil or excessive artificial chemicals.",
                difficulty = TestDifficulty.EASY
            )
        ),
        healthRisks = "Rancid oils contain harmful free radicals that damage cells and increase cancer risk. Excessive MSG causes 'MSG symptom complex' — headache, sweating, and chest tightness in sensitive individuals.",
        buyingTip = "Check the manufacturing date and expiry. Buy from reputed brands. Once opened, consume quickly as chips go rancid faster when exposed to air. Avoid heavily colored chips."
    )
)

// ─────────────────────────────────────────────────
//  Lookup Helper
// ─────────────────────────────────────────────────
fun searchFoodGuide(query: String): List<FoodGuideEntry> {
    if (query.isBlank()) return allFoodGuideEntries
    val q = query.trim().lowercase()
    return allFoodGuideEntries.filter {
        it.name.lowercase().contains(q) ||
        it.commonAdulterants.any { a -> a.lowercase().contains(q) } ||
        it.category.displayName.lowercase().contains(q)
    }
}

fun getFoodsByCategory(category: FoodCategory?): List<FoodGuideEntry> {
    return if (category == null) allFoodGuideEntries
    else allFoodGuideEntries.filter { it.category == category }
}
