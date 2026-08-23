package com.cornerman.app.data

data class GameDefinition(
    val id: String,
    val name: String,
    val maps: List<MapDefinition>
)

data class MapPOI(
    val name: String,
    val x: Float, // 0.0 to 1.0
    val y: Float  // 0.0 to 1.0
)

data class MapDefinition(
    val id: String,
    val name: String,
    val gameId: String,
    val assetResId: Int? = null, 
    val pois: List<MapPOI> = emptyList()
)

enum class MarkerType { LANDING, ROTATION, FIGHT, DEATH, LAST_ZONE }

data class MapMarker(
    val x: Float, // 0.0 to 1.0 relative to map width
    val y: Float, // 0.0 to 1.0 relative to map height
    val type: MarkerType,
    val label: String = ""
)

data class MatchTimeline(
    val gameId: String,
    val mapId: String,
    val markers: List<MapMarker>,
    val landingPOI: String = "",
    val lastZonePOI: String = "",
    val dropReason: String = "",
    val playerIntent: String = "",
    val whatHappened: String = "",
    val outcome: String = ""
)

object GameData {
    val Games = listOf(
        GameDefinition(
            id = "bgmi",
            name = "BGMI",
            maps = listOf(
                MapDefinition("erangel", "Erangel", "bgmi", assetResId = com.cornerman.app.R.drawable.erangel_map, pois = listOf(
                    MapPOI("Pochinki", 0.45f, 0.52f),
                    MapPOI("Rozhok", 0.48f, 0.38f),
                    MapPOI("School", 0.51f, 0.42f),
                    MapPOI("Military Base", 0.53f, 0.85f),
                    MapPOI("Gatka", 0.32f, 0.58f),
                    MapPOI("Georgopol", 0.18f, 0.32f),
                    MapPOI("Yasnya Polyana", 0.65f, 0.32f),
                    MapPOI("Mylta", 0.68f, 0.68f)
                )),
                MapDefinition("miramar", "Miramar", "bgmi", pois = listOf(
                    MapPOI("Pecado", 0.48f, 0.52f),
                    MapPOI("Hacienda", 0.52f, 0.35f),
                    MapPOI("Los Leones", 0.65f, 0.72f),
                    MapPOI("El Pozo", 0.22f, 0.38f)
                )),
                MapDefinition("sanhok", "Sanhok", "bgmi", pois = listOf(
                    MapPOI("Bootcamp", 0.48f, 0.48f),
                    MapPOI("Paradise Resort", 0.55f, 0.35f),
                    MapPOI("Ruins", 0.35f, 0.55f)
                )),
                MapDefinition("livik", "Livik", "bgmi", pois = listOf(
                    MapPOI("Midstein", 0.48f, 0.55f),
                    MapPOI("Power Plant", 0.75f, 0.42f)
                ))
            )
        ),
        GameDefinition(
            id = "codm",
            name = "CODM BR",
            maps = listOf(
                MapDefinition("isolated", "Isolated", "codm", pois = listOf(
                    MapPOI("Launch Base", 0.5f, 0.5f),
                    MapPOI("Nuclear Plant", 0.3f, 0.3f)
                )),
                MapDefinition("blackout", "Blackout", "codm")
            )
        ),
        GameDefinition(
            id = "ff",
            name = "Free Fire",
            maps = listOf(
                MapDefinition("bermuda", "Bermuda", "ff", pois = listOf(
                    MapPOI("Clock Tower", 0.4f, 0.6f),
                    MapPOI("Peak", 0.5f, 0.45f)
                )),
                MapDefinition("purgatory", "Purgatory", "ff")
            )
        )
    )
}
