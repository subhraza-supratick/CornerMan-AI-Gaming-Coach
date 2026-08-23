# OpenRouter Setup Guide (Android MVP)

This guide explains how OpenRouter is integrated into the **CORNERMAN** Android app and how to configure it for analysis.

---

## Step 1 — Get an API Key

1. Go to [openrouter.ai](https://openrouter.ai/).
2. Create an account and navigate to **Keys**.
3. Create a new key (e.g., `sk-or-v1-...`).
4. Ensure you have credits (or use a free model like `google/gemini-2.0-flash-lite-001`).

---

## Step 2 — Add the Key to the App

Since this is a mobile app, we do not use `.env` files. The key is entered by the user directly in the app for security and local encryption.

1. Run the **CORNERMAN** app on your device/emulator.
2. Tap the **Settings** icon (gear) on the Home screen.
3. Paste your key into the **OpenRouter API Key** field.
4. Tap **Save OpenRouter Key**.

> [!IMPORTANT]
> Your key is encrypted using the **Android Keystore System (AES-GCM)** via `SecurePrefs.kt` and stored locally on your device. It is never logged or exposed in the UI after saving.

---

## Step 3 — Configure the Model

The app is currently configured to use a vision-capable model. You can change the default model in the source code if needed.

**File:** `app/src/main/java/com/cornerman/app/data/OpenRouterApi.kt`

```kotlin
// Change this constant to use a different vision model
private const val MODEL = "google/gemini-2.0-flash-lite-001"
```

### Recommended Vision Models
| Model ID | Notes |
| :--- | :--- |
| `google/gemini-2.0-flash-lite-001` | Fast, high quality, and cost-effective. |
| `google/gemini-pro-1.5` | Exceptional reasoning for complex tactical fights. |
| `openai/gpt-4o-mini` | Very fast and reliable vision understanding. |

---

## Step 4 — How the Integration Works

The app uses a lightweight, dependency-free implementation to keep the APK size small and performance high.

1. **Image Preprocessing**: Screenshots are scaled to ~1600px and compressed to JPEG (~82% quality) in `OpenRouterApi.bitmapToBase64Jpeg`.
2. **Vision Request**: The app sends a `POST` request to `https://openrouter.ai/api/v1/chat/completions` with the image in Base64 format.
3. **Structured Response**: The prompt enforces a specific JSON schema so the app can parse scores, evidence, and rules without hallucinations.

---

## Troubleshooting

*   **"Key is Invalid"**: Double-check that the key starts with `sk-or-v1-` and has not expired.
*   **"Connection Failed"**: Ensure the device has internet access. Check your OpenRouter credit balance.
*   **"Not enough evidence"**: The vision model might not recognize the screenshot as gameplay. Ensure the image is a clear capture of a death screen or an active engagement.
