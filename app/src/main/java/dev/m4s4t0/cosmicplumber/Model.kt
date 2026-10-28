package dev.m4s4t0.cosmicplumber

import kotlin.random.Random

enum class Kind(val label: String) { PIPES("PIPE PUZZLE"), WRENCH("WRENCH RHYTHM") }

data class Job(
    val id: Int,
    val client: String,
    val problem: String,
    val quote: String,
    val tx: Int,
    val ty: Int,
    val kind: Kind,
    val level: Int,
    val pay: Int,
    val cosmic: Boolean,
)

class Upgrade(val id: String, val name: String, val blurb: String, val prices: List<Int>) {
    val max get() = prices.size
}

class Paint(val id: String, val name: String, val color: Int, val trim: Int, val price: Int)

object Catalog {
    val upgrades = listOf(
        Upgrade("engine", "V8 ENGINE", "MORE HORSES UNDER THE HOOD. VAN GOES FASTER.", listOf(150, 320, 650)),
        Upgrade("tank", "BIG GAS TANK", "HOLDS MORE GAS. GAS IS 89 CENTS, WHAT A RIPOFF.", listOf(100, 260)),
        Upgrade("torque", "TORQUE WRENCH", "WIDER TIMING WINDOW ON WRENCH JOBS.", listOf(120, 280, 560)),
        Upgrade("gauge", "PRESSURE GAUGE", "MORE TIME BEFORE THE PIPES BLOW.", listOf(120, 280, 560)),
        Upgrade("alderman", "ALDERMAN ENVELOPE", "A LITTLE SOMETHING FOR THE WARD. +15% PAY PER LEVEL.", listOf(200, 450, 900)),
        Upgrade("cb", "CB RADIO", "DOLORES CAN RADIO YOU JOBS. NO MORE PAYPHONES, 10-4.", listOf(750)),
    )

    val paints = listOf(
        Paint("classic", "CLASSIC BROWN", Pal.BROWN, Pal.TAN, 0),
        Paint("vice", "VICE TEAL", Pal.TEAL, Pal.PINK, 250),
        Paint("flamingo", "FLAMINGO", Pal.PINK, Pal.CREAM, 300),
        Paint("knight", "KNIGHT BLACK", 0xFF22202C.toInt(), Pal.RED, 400),
        Paint("chrome", "COSMIC CHROME", Pal.LGRAY, Pal.GREEN, 800),
    )

    fun upgrade(id: String) = upgrades.first { it.id == id }
    fun paint(id: String) = paints.firstOrNull { it.id == id } ?: paints[0]

    private val clients = listOf(
        "MRS. KOWALSKI", "BIG TONY'S BEEF", "ALDERMAN O'FLAHERTY", "CHAD, COMMODITIES TRADER", "DA BEARS FAN CLUB",
        "SISTER MARY AGNES", "DEEP DISH DAVE'S", "ARCADE KING ROLAND", "JAZZERCISE STUDIO", "ROLLER RINK RHONDA",
        "STEEL MILL LOCAL 1010", "THE MAYOR'S COUSIN", "DR. FEELGOOD DDS", "CABLE TV GARY", "POLISH SAUSAGE STAND",
        "HAIR METAL BAND", "VIDEO STORE VINNIE", "GRANDMA NOWAK", "YUPPIE LOFT ON DEARBORN", "TAVERN ON TAYLOR ST",
    )
    private val aliens = listOf(
        "THE ZORBLAX FAMILY", "GLORP FROM SECTOR 9", "THE GRAYS (2ND FLOOR)", "LIZARD PERSON IN A SUIT",
        "QUASAR QUILTING CLUB", "XLL'TH THE DEVOURER", "A SENTIENT HOT DOG", "SPACE ELVIS",
    )
    private val pipeProblems = listOf(
        "PIPES FROZE SOLID. IT IS CHICAGO.", "BASEMENT FLOODED DURING THE CUBS GAME.", "WATER HEATER MAKING DEMON NOISES.",
        "SOMEBODY FLUSHED A RUBIK'S CUBE.", "RADIATOR LEAKING ALL OVER THE SHAG RUG.", "SINK BACKED UP WITH HAIRSPRAY.",
    )
    private val wrenchProblems = listOf(
        "MAIN VALVE RUSTED SHUT SINCE CARTER.", "LEAKY JOINT KEEPS TIME WITH THE STEREO.", "LOOSE BOLTS ON THE BOILER.",
        "TOILET RUNS ALL NIGHT. LIKE THE CITY COUNCIL.", "SHOWER HEAD STUCK ON 'NIAGARA'.", "GAS LINE HUMMING TOP 40 HITS.",
    )
    private val cosmicProblems = listOf(
        "TOILET OPENED A WORMHOLE TO ANDROMEDA.", "SINK GURGLES IN BINARY.", "THE DRAIN IS STARING BACK.",
        "TAP WATER IS GLOWING GREEN AGAIN.", "PIPES ARE LEAKING ANTIMATTER.", "BATHTUB FULL OF TINY STARS.",
    )
    private val quotes = listOf(
        "\"HURRY, THE BEARS GAME IS ON AT NOON!\"", "\"I TRIED CALLING BUT YOU WEREN'T BY THE PHONE.\"",
        "\"MY BROTHER-IN-LAW SAID HE COULD FIX IT. HE COULD NOT.\"", "\"GREED IS GOOD, BUT DRY CARPET IS BETTER.\"",
        "\"DON'T TELL THE ALDERMAN. OR DO. HE KNOWS ANYWAY.\"", "\"CASH ONLY? WHAT AM I, MADE OF TRAVELER'S CHECKS?\"",
        "\"IT STARTED AFTER I PLAYED THE RECORD BACKWARDS.\"", "\"THE LANDLORD SAID CALL THE CITY. THE CITY LAUGHED.\"",
    )
    private val alienQuotes = listOf(
        "\"GREETINGS, PIPE SHAMAN. YOUR PLANET'S PLUMBING IS PRIMITIVE.\"", "\"WE COME IN PEACE. OUR TOILET DOES NOT.\"",
        "\"TAKE ME TO YOUR LEADER. AFTER THE SINK.\"", "\"WE PAY IN EARTH DOLLARS. WE PRINTED THEM OURSELVES.\"",
    )

    /** A new call from dispatch. [night] makes cosmic calls likelier; [sites] are door tiles in the city. */
    fun newJob(id: Int, day: Int, night: Boolean, sites: List<Pair<Int, Int>>, rnd: Random = Random): Job {
        val cosmic = rnd.nextFloat() < (if (night) 0.6f else 0.25f)
        val kind = if (rnd.nextBoolean()) Kind.PIPES else Kind.WRENCH
        val level = (1 + (day - 1) / 2 + rnd.nextInt(-1, 2)).coerceIn(1, 5)
        val site = sites[rnd.nextInt(sites.size)]
        val problem = when {
            cosmic -> cosmicProblems.random(rnd)
            kind == Kind.PIPES -> pipeProblems.random(rnd)
            else -> wrenchProblems.random(rnd)
        }
        val pay = ((40 + level * 30 + rnd.nextInt(0, 30)) * (if (cosmic) 1.5f else 1f)).toInt()
        return Job(
            id, if (cosmic) aliens.random(rnd) else clients.random(rnd), problem,
            if (cosmic) alienQuotes.random(rnd) else quotes.random(rnd), site.first, site.second, kind, level, pay, cosmic,
        )
    }

    /** What a finished job pays: base, times performance, overtime at night and the alderman's goodwill. */
    fun payout(job: Job, score: Float, night: Boolean, alderman: Int): Int {
        val perf = 0.5f + score.coerceIn(0f, 1f)
        val ot = if (night) 1.5f else 1f
        return (job.pay * perf * ot * (1f + 0.15f * alderman)).toInt()
    }
}
