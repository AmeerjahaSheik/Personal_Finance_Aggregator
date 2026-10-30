# Google OAuth 2.0 Setup Guide for FinPilot

This document explains how to set up Google OAuth 2.0 credentials in the Google Cloud Console for local development and production.

---

## 1. Create a Google Cloud Project

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Log in with your Google account.
3. Click the project dropdown at the top navigation bar and select **New Project**.
4. Enter `FinPilot` as the Project Name.
5. Click **Create**.

---

## 2. Configure OAuth Consent Screen

1. In the left navigation menu, go to **APIs & Services > OAuth consent screen**.
2. Select **External** (or **Internal** if using Google Workspace) and click **Create**.
3. Fill in required App Information:
   - **App name**: `FinPilot`
   - **User support email**: Select your email.
   - **Developer contact information**: Enter your email.
4. Click **Save and Continue**.
5. Under **Scopes**, click **Add or Remove Scopes** and select:
   - `openid`
   - `.../auth/userinfo.email`
   - `.../auth/userinfo.profile`
6. Click **Update**, then **Save and Continue**.
7. Under **Test Users**, add your Google email address for testing.
8. Click **Save and Continue**.

---

## 3. Create OAuth Client Credentials

1. Go to **APIs & Services > Credentials**.
2. Click **+ Create Credentials** at the top and select **OAuth client ID**.
3. Set **Application type** to: `Web application`.
4. Set **Name** to: `FinPilot Web Client`.
5. Under **Authorized JavaScript origins**, add:
   - `http://localhost:8080`
6. Under **Authorized redirect URIs**, add the exact Spring Security OAuth2 callback URI:
   - `http://localhost:8080/login/oauth2/code/google`
   - Do not add a trailing slash.
7. Click **Create**.
8. A modal will display your **Client ID** and **Client Secret**. Copy both values.

---

## 4. Set Environment Variables

For Docker Compose, put the copied credentials in a root `.env` file (Compose reads it automatically). For a backend started directly from PowerShell or an IDE, set the variables in that process's environment; Spring Boot does not load `.env` files by itself:

```bash
GOOGLE_CLIENT_ID=your-actual-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-actual-client-secret
FRONTEND_URL=http://localhost:5173
```

Never commit `.env` or real client secrets to Git. Keep `.env.example` placeholder-only.

---

## 5. How to Start Spring Boot

Ensure the configured MySQL database is running, then launch Spring Boot with your environment variables:

```powershell
$env:GOOGLE_CLIENT_ID="your-client-id"
$env:GOOGLE_CLIENT_SECRET="your-client-secret"
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3308/finpilot?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:SPRING_DATASOURCE_USERNAME="root"
$env:SPRING_DATASOURCE_PASSWORD="root"
.\mvnw.cmd spring-boot:run
```

---

## 6. How to Test Authorization Flow

1. Open your browser and navigate to:
   `http://localhost:8080/oauth2/authorization/google`
2. You will be redirected to Google's consent screen.
3. Authenticate with your Google account.
4. Upon successful login, Spring Boot will process your user details, save/update the user in MySQL, generate an application JWT, and redirect you through `http://localhost:5173/auth/callback` before opening the dashboard.

The registered callback is `http://localhost:8080/login/oauth2/code/google` when the browser starts sign-in at `http://localhost:8080/oauth2/authorization/google`. Keep the host consistent (`localhost`, not `127.0.0.1`), use port `8080`, and do not add a trailing slash. The frontend origin is not required as a Google JavaScript origin because this app uses a backend-initiated redirect, not Google's browser JavaScript SDK.

To inspect backend logs when running directly, use the Maven console output. For Docker, run `docker compose logs -f backend`. OAuth failure logs include the provider error code and sanitized reason; they must not include credentials, tokens, or authorization codes.
