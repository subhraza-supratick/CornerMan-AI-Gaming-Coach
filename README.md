# CORNERMAN — AI In-Game Leader

## Team
| | |
|---|---|
| **Team name** | **CORNERMAN** |
| **Members** | **Dilshad Ali, Subhraza supratick, Dishant mohapatra** |
| **City / Venue** | **Bengaluru** |

## App
| | |
|---|---|
| **App name** | **CORNERMAN** |
| **Theme** | Utility app |
| **One-liner** | AI-powered esports coaching that analyzes gameplay decisions and gives players actionable tactical advice. |

## What we built

CORNERMAN is an AI-powered esports coach designed for competitive mobile gamers. It analyzes gameplay evidence, player intent, and tactical context to identify the decisions that led to a bad outcome and explain what the player could have done differently.

For the hackathon MVP, CORNERMAN focuses deeply on BGMI Erangel through CORNERMAP. Players can reconstruct their drop, rotation, fight, and death on the map, attach gameplay evidence, and receive an AI-generated tactical diagnosis and next-game plan.

## How the AI is used

- **Model:** `google/gemini-2.5-flash-lite` via OpenRouter
- **What the AI does:** Analyzes gameplay screenshots/evidence, understands the player's stated intent, diagnoses tactical mistakes, and generates actionable IGL-style recommendations.
- **AI pattern:** Vision · Extract · Analyze · Generate

---

## Features

- **Quick IGL**: Instant analysis of gameplay screenshots or 27-second video clips.
- **CornerMap**: Deep tactical reconstruction on the Erangel map.
- **Hardware HUD**: Live monitoring of phone temperature, RAM, and battery to detect thermal throttling.
- **Manual Coaching**: Build your tactical memory even without an API key.
- **Decision Memory**: Tracks recurring mistakes and builds your personal tactical profile.

## Build

1. Install Android Studio.
2. Open the project folder.
3. Let Android Studio install/sync the Android SDK and Gradle dependencies.
4. Connect an Android phone or create an emulator with API 26+.
5. Run the app.
6. Open Settings and paste your OpenRouter key (`sk-or-v1-...`).
7. Return home → Choose **Quick IGL** or **CornerMap**.

## Demo Mode

Analyze Decision → **Demo Mode — no API needed**.
Use this to see high-fidelity analysis examples instantly without requiring an active API key or internet credits.

## Security Note

For this hackathon build, the OpenRouter key is entered by the user and encrypted locally using Android Keystore + AES/GCM. In a production environment, AI calls should be moved behind a backend proxy.
