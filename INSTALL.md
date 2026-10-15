# Installing on an iPhone from a Windows PC

The full path without a Mac: the code lives on **GitHub**, a **cloud Mac (GitHub Actions)**
builds it, you download the ready `.ipa` and install it on the iPhone with a Windows
signing tool — **AltStore** or **Sideloadly**.

## What you need

- Apple ID (free, with two-factor authentication enabled)
- A GitHub account
- A Windows PC, a cable, and the iPhone itself (iOS 17+)
- Money spent: $0

---

## Step 1. Push the project to GitHub

### Option A — Git (commands)

1. Install **Git for Windows**: <https://git-scm.com/download/win> (keep the defaults).
2. Create a repository: <https://github.com/new> → name it `Zammad`, type **Public**
   (public — builds are free; you can skip the README/.gitignore) → **Create repository**.
3. Open PowerShell and run line by line (substitute your own username):

```powershell
cd "C:\Users\Serg\Documents\Default Project\Zammad"
git init
git add .
git commit -m "Zammad iOS app"
git branch -M main
git remote add origin https://github.com/YOUR-LOGIN/Zammad.git
git push -u origin main
```

On the first `push` a browser window opens for GitHub sign-in — log in.

### Option B — GitHub Desktop (no commands)

1. Install **GitHub Desktop**: <https://desktop.github.com> → sign in to your account.
2. **File → Add Local Repository → Add** → pick the `...\Default Project\Zammad` folder.
   If the app offers to create a repository, agree (it creates one right there).
3. At the bottom of the window choose **Public** → **Publish repository**
   (uncheck "Keep this code private").

---

## Step 2. Build the .ipa in the cloud

1. Open your repository on GitHub → the **Actions** tab.
   If asked, click **I understand my workflows, go ahead and enable them**.
2. On the left select **Build IPA** → on the right **Run workflow** → branch `main`
   → the green **Run workflow** button.
3. Wait 5–15 minutes for the green checkmark.
   - Red cross → open the run, copy the log of the failed step (send it over).
4. Open the finished run → the **Artifacts** section at the bottom → **Zammad-ipa**
   → download the zip, unpack it — inside is **`Zammad.ipa`**.

> GitHub limits: a public repository builds for free with no time limit;
> a private one has 2000 "minutes" per month (macos counts ×10, so ≈ 200 builds).

---

## Step 3. Install on the iPhone

### Method 1 — Sideloadly (easiest for the first time)

1. On the PC install **iTunes** (from the Microsoft Store) and **Sideloadly**: <https://sideloadly.io>.
2. Connect the iPhone with a cable → tap **"Trust"** on the phone.
3. Open Sideloadly — the device is detected automatically.
4. Drag `Zammad.ipa` into the program window → enter your **Apple ID**
   (password + confirmation code if asked) → press **Start**.
5. On the iPhone: **Settings → General → VPN & Device Management** → tap
   **"Trust Apple Development: …"** (one-time action).
6. The app launches.

**Downside:** the free signature lives for **7 days**. A week later simply
repeat items 3–5 (same actions with the same `.ipa`).

### Method 2 — AltStore (automatic signature renewal)

1. On the PC: install **iTunes** (Microsoft Store), then **AltServer**: <https://altstore.io> (Downloads).
2. Connect the iPhone, launch iTunes (confirm "Trust this computer").
3. The AltServer tray icon → **Install AltStore** → pick the iPhone → Apple ID + password.
   AltStore appears on the iPhone (the icon starts out gray — that is normal).
4. Put `Zammad.ipa` on the phone itself: the easy way is sending it to yourself in a
   messenger (Telegram → "Saved Messages"), opening it on the iPhone and saving to **Files**.
5. On the iPhone in **Files** tap `Zammad.ipa` → **Share → AltStore**.
   (If AltStore is missing from the list — update it to the latest version.)
6. After installing, trust the profile: **Settings → General → VPN & Device Management → Trust**.
7. **Auto-renewal:** keep AltServer running on the PC in the same Wi-Fi network as the
   iPhone and enable refresh in AltStore (**My Apps → ⋯ → Refresh All** — it usually
   works automatically while charging on the same Wi-Fi). Then the app never expires.

**Apple limit:** no more than **3 sideloaded apps** at a time — one is enough for us.

---

## Common problems

| Situation | Fix |
|---|---|
| No Actions tab on GitHub | The repository is empty or step 1 is unfinished — push the files |
| Sideloadly/AltStore: Apple ID sign-in error | Enable two-factor authentication: appleid.apple.com → Security |
| On iPhone "Unable to open" / no icon | Settings → General → VPN & Device Management → Trust |
| The app stopped opening after a week | The signature expired (7 days) — reinstall the `.ipa` (step 3) |
| Actions failed at the Build step | Copy the error log and send it over — I'll fix the project |

---

## Future alternative: TestFlight

If you need to install updates without a cable and without the 7-day limit, the paid
**Apple Developer Program ($99/year)** is required: the same workflow can then be
extended with automatic uploads to **TestFlight**, and updates arrive on the iPhone
by link. Say the word and I'll set it up.
