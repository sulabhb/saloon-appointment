# Salon Booking Platform

Multi-business salon booking platform: public booking site, staff dashboard and Android app on one Spring Boot API.

## Repository layout

| Path | Contents |
|---|---|
| `backend/` | Spring Boot 4.1 service (Java 25, Gradle, PostgreSQL, Flyway) |
| `android/` | Android app (Kotlin, Jetpack Compose, Material 3, Hilt) for customers and staff |
| `web/` | Web console (React, TypeScript, Vite, Tailwind) for staff, owners and the platform team |
| `infra/local/` | Docker Compose PostgreSQL for local development |
| `scripts/demo/` | End-to-end demo scripts against a running backend |
| `.github/workflows/` | CI |

The public booking website is part of `web/` (routes `/book/...` and `/m/...`); it shares the build with the console for now.

## Web console

Requirements: Node 22.12+ (`web/.nvmrc`). With the backend running on port 8080:

```bash
cd web && npm install && npm run dev
```

Open http://localhost:5173. The dev server proxies `/api` to the backend, so the session cookie and CSRF cookie work as in production. Start the backend with `SALON_AUTH_COOKIE_SECURE=false` (plain HTTP locally) and `SALON_AUTH_APP_BASE_URL=http://localhost:5173` so invite links open the console.

| Page | Who |
|---|---|
| `/login` | Everyone with an account |
| `/forgot-password`, `/reset-password/{token}` | Anyone who forgot their password: request a link, choose a new password |
| `/invite/{token}` | People accepting an invite: new accounts choose a name and password, existing accounts confirm theirs |
| `/home` | Everyone in a business: the day's appointments at their branches (any day), and their branches; business switcher in the sidebar for people in several |
| `/calendar` | Everyone: the day at one branch with a column per stylist (hours, breaks, leave, clean-up time, work at other branches), or the week for one stylist (a column per day) or the whole branch; lists on phones. Reception, managers and owners: new bookings and walk-ins, and drag an appointment to another time or stylist (asks first; clashes are refused). Click an appointment for details, status, staff note and moving it |
| `/setup` | Owners and managers: go-live checklist, business profile with brand colour, and the online booking rules (owners edit) |
| `/branches`, `/branches/{id}` | Branches; per branch: weekly opening hours (with breaks), services offered and branch prices, closures, details |
| `/services` | Service menu by category: duration, clean-up time, price, "from" prices, notices |
| `/staff` | Staff profiles: branches, services, bookable online; invite them to sign in |
| `/schedule` | Weekly rota per branch: usual week per person (several shifts a day, across branches), single-day changes and days off, leave and breaks; warnings before saving |
| `/team` | Owners: who can sign in, roles and branch access; invites; remove access |
| `/book/{business}`, `/book/{business}/{branch}` | Customers, no sign-in: book online in the salon's own colour (services, stylist or anyone, date and time, details, review, confirmation with calendar links) |
| `/m/{token}` | Customers: their private link to check or cancel a booking |
| `/platform` | Platform admins: businesses with counts; create a business and copy the owner invite link; status changes; resend or revoke invites |

The console uses a small in-house component set (`web/src/components`): Inter, warm neutral tokens with dark mode in `styles.css`, Lucide icons, side panels for forms, toasts for confirmations, and in-place confirmation for destructive actions. UI work follows the [ui-skills](https://www.ui-skills.com) `baseline-ui` and `fixing-accessibility` guidelines (`npx ui-skills` prints them), except where they assume libraries the console does not use (`motion`, Base UI, `cn`).

Checks: `npm run lint`, `npm test`, `npm run build`.

## Backend

Requirements: JDK 17+ to run Gradle (Gradle downloads JDK 25 for compiling if it is not installed), and Docker for local PostgreSQL and integration tests.

Start PostgreSQL:

```bash
docker compose -f infra/local/docker-compose.yml up -d
```

Run the service (migrations run on start-up):

```bash
cd backend && ./gradlew bootRun
```

Run the tests (integration tests start their own PostgreSQL with Testcontainers):

```bash
cd backend && ./gradlew test
```

Without Docker, point the tests at an existing PostgreSQL 17 with a superuser:

```bash
cd backend && TEST_DB_URL=jdbc:postgresql://localhost:5432/salon_test TEST_DB_ADMIN_USER=postgres TEST_DB_ADMIN_PASSWORD=secret ./gradlew test
```

## Android

Requirements: Android Studio (or JDK 17+ and the Android SDK with platform 37). Open `android/` in Android Studio, or from the command line:

```bash
cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug builds call the backend at `http://10.0.2.2:8080/`, which is the development machine as seen from the emulator. The API addresses and the booking-link host are set in `android/gradle.properties`.

What works for customers (no account needed):

- **Find a salon:** enter its salon code, tap a salon you opened before, or open a booking link (`https://{bookingHost}/{salon}[/{branch}]`, also the web paths `/book/...`).
- **Book:** branch, services, stylist (or anyone available), date and time, details (pre-filled from the last booking on this phone), review, confirm. The salon's colour applies throughout, a bar always shows the total, and choices survive rotation and the app being closed in the background. If the time is taken just before confirming, the app returns to the times and keeps everything else.
- **Confirmation:** the booking, an "Add to calendar" button that opens the phone's calendar, and a link to the booking.
- **My bookings:** upcoming first, then past and cancelled ones. Kept on this phone only, with each booking's private link; the app's storage is not backed up. Opening a management link from an email (`https://{bookingHost}/m/{token}`) adds the booking too. When offline, the saved copy is shown and changes are disabled.
- **Manage a booking:** change time (same services and branch, the same stylist by default) or cancel, until the cut-off; after that, a button to call the branch.

The booking-link host is `salon.bookingHost` in `android/gradle.properties`. For links to open the app without a chooser, the backend serves `/.well-known/assetlinks.json` on that host: set `SALON_ANDROID_CERT_FINGERPRINTS` to the SHA-256 fingerprint(s) of the signing certificate (comma-separated; the Play Console shows the app-signing one). Without fingerprints it serves an empty list.

**Minimum version (APP-4):** every request carries `X-Client-Version: android/<versionName>+<versionCode>`. At launch the app reads `GET /api/v1/public/app-config`; if its build is below `SALON_ANDROID_MIN_VERSION_CODE` (or any call returns 426 `APP_UPDATE_REQUIRED`), it shows an "update the app" screen linking to `SALON_ANDROID_STORE_URL`. Saved bookings stay on the phone. Web requests and headers without a build number are never refused. Try it locally by starting the backend with `SALON_ANDROID_MIN_VERSION_CODE=2`. To try a link on the emulator: `adb shell am start -a android.intent.action.VIEW -d "https://book.example.com/luna-hair-co" -n com.salonbooking.app/.MainActivity`.

**Staff mode** ("Staff sign in" on the home screen):

- **Sign in** with email and password (an Android session: a bearer token, not a cookie). People in several businesses choose one, and can switch from the Account menu.
- **Today:** the day's appointments as an agenda list, any day, refreshed on return. Stylists see their own; reception and managers their branches; owners everything. The server decides.
- **Appointment:** details, a call button, the customer's note, the status changes the server allows (check in, complete, no-show, cancel with a reason, the manager-only undos), and the staff-only note.
- **Session:** the token is encrypted with an AES key in the Android Keystore and survives restarts. It is sent only on `/api/v1/staff` and `/api/v1/auth` calls. A 401 (expired, revoked, or access removed) or signing out forgets it and returns to sign-in. Debug logs mask management tokens in URLs.

Not built yet in the app: QR scanning, push notifications, rebooking from a past booking, the minimum-version check, staff bookings and walk-ins, customer search, the offline copy of the agenda, biometric unlock.

## Online booking

Customers book from a salon's link, with no account. Everything runs on the server: the browser never decides what is available.

| Endpoint (under `/api/v1/public`) | Purpose |
|---|---|
| `GET /b/{business}` | Branding, branches, cancellation cut-off |
| `GET /b/{business}/locations/{branch}/catalog` | Services offered at the branch (branch prices and durations applied) and stylists bookable online, with what each does |
| `GET /b/{business}/locations/{branch}/availability?services&staff&date` | Start times for one date (`staff` absent = anyone) |
| `GET /b/{business}/locations/{branch}/availability/days?services&staff&from&to` | `AVAILABLE`, `FULL` or `CLOSED` per date (up to 62 days) |
| `POST /b/{business}/bookings` | Book. Returns the booking and a private management token, shown once |
| `GET /manage/{token}`, `POST /manage/{token}/cancel` | The customer's booking (contacts masked); cancel until the cut-off |
| `GET /manage/{token}/options`, `GET .../availability?date&staff`, `GET .../availability/days?from&to&staff`, `POST .../reschedule` | Move the booking until the same cut-off: same branch and services, the current stylist by default or anyone free |

Staff see bookings with `GET /api/v1/staff/agenda?date&locationId` (the console's Today page).

**How times are worked out** (`SlotCalculator`, a pure function with unit tests including both daylight-saving changes): a start is offered when the whole appointment plus the last service's clean-up time fits inside the branch's opening hours (minus closures), inside the stylist's shift at that branch, and clear of their breaks, leave and other appointments at any branch. Starts step by the slot interval from each opening time, skip times that don't exist when clocks go forward, and respect minimum notice and the booking horizon (business defaults 15 minutes, 2 hours, 60 days; branches can override).

**Booking guarantees:**
- The time is re-checked inside the booking transaction, and a PostgreSQL exclusion constraint (`appointment_staff_no_overlap`) makes double booking a stylist impossible even under concurrent requests (tested with 20 parallel requests).
- "Anyone" tries free stylists in order of fewest booked minutes that day, each inside a savepoint, so losing a race to one stylist moves on to the next.
- The client sends an `idempotencyKey`; a retry returns the same booking with a fresh link.
- One email or mobile can hold at most N future bookings per business (default 3).
- Customers are matched per business by exact email, then exact mobile; never by name.
- Each booking keeps a snapshot of its services (name, duration, price), so later edits to the menu don't change it.
- Management links hold 256 random bits; only a SHA-256 hash is stored, and links stop working 30 days after the appointment.

Schedule changes now warn about booked appointments they would affect (`BOOKED_APPOINTMENT`), including leave and breaks (`POST /api/v1/admin/staff/{id}/time-off/check`).

### Staff bookings

| Endpoint (under `/api/v1/staff`) | Purpose |
|---|---|
| `GET /calendar?locationId&date`, `GET /calendar/week?locationId&date` | The day per stylist: working intervals, breaks and leave, appointments (those at other branches without customer details); or the seven days, Monday to Sunday, of that week |
| `GET /booking-options?locationId`, `GET /availability?locationId&services&staffId&date[&reschedule]` | Services and stylists (including "reception bookings only"), and free times with who is free at each |
| `POST /appointments` | Book for an existing customer (`customerId`) or a new one; `walkIn: true` starts checked in and needs only a name |
| `GET /appointments/{id}`, `POST .../status`, `POST .../reschedule`, `PUT .../note` | Detail, status change, move, staff note |
| `GET /customers?q`, `GET /customers/{id}` | Search by name, mobile, email or booking reference; history with no-show and late-cancel counts |

Staff bookings skip the online limits (minimum notice, horizon, bookings per contact) but never double book. A phone booking needs a mobile or email; a walk-in only a name.

Status changes follow the allowed transitions and are rejected otherwise (`INVALID_TRANSITION`): check in and complete; no-show only once the start time has passed; cancel (with an optional reason, flagged late after the cut-off). Cancelling someone already checked in, undoing a no-show and undoing a completion are for managers and owners, and the undos only on the same day. Undoing a no-show fails if the time has been rebooked.

Who can do what: stylists see and update only their own appointments (from the staff profile linked to their sign-in) and cannot book, cancel, move or search customers; reception and managers work at their assigned branches; owners everywhere. Staff notes are never returned by public endpoints.

Customers move their own booking from the management link ("Change time") until the cut-off. Online rules apply (notice, horizon, stylists bookable online), the booking does not block its own new time, the current stylist is kept when free, and the original time stands if the new one is taken first. Staff and customer moves share one code path, and both email the new details.

Not built yet: the bot check on Confirm, rate limiting, "needs status" for appointments left open after the day, the change history and audit log, editing and merging customers, and a separate small bundle for the booking pages.

## Customer emails

Customers get an email when they book (online or by phone), when the salon moves or cancels a booking, when they cancel, 24 hours before the appointment, and when they ask for a lost link. Walk-ins and customers without an email address get none.

- **Outbox:** booking changes add a row to `notification` in the same transaction, so an email exists exactly when the change commits and sending never blocks or undoes it. A background dispatcher (every 10 seconds) claims due rows with `SKIP LOCKED`, renders and sends them, and retries failures after 1, 2, 4 and 8 minutes before giving up. Staff see each email's status in the appointment panel; "Sent" means the mail service accepted it.
- **Content:** sent from the platform address with the business as sender name and the branch (or business) email as reply-to; header in the salon's colour; plain-text alternative; confirmations and moves attach an `.ics` file. Each email gets its own management link, created at send time, so no link or message body is stored.
- **Skipping:** an email that no longer applies when its turn comes (a confirmation for a booking already cancelled, a reminder for a moved one) is marked skipped rather than sent.
- **Lost links:** `POST /api/v1/public/b/{business}/manage/recover` with reference and email, or the booking site's "Find your booking" page (`/book/{business}/find`). Earlier links stop working; the response is the same whether or not anything matched.

Settings (`salon.notifications.*`): `delivery` (`file` by default, `smtp` in production with `spring.mail.host`, `port`, `username`, `password`), `from-address`, `link-base-url` (defaults to `salon.auth.app-base-url`), `reminder-before` (24h), `max-attempts` (5).

Locally, emails are written to `backend/build/outbox/` as `.eml` (opens in a mail app) and `.html`. To browse them, serve that folder (for example `python3 -m http.server 8099 --directory backend/build/outbox`). With Docker, `infra/local/docker-compose.yml` also starts Mailpit: run the backend with `SALON_NOTIFICATIONS_DELIVERY=smtp SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025` and open http://localhost:8025.

Not built yet: SMS and Android push reminders, the business's logo in emails, staff push notifications.

## Tenant isolation

Every business-owned table has a `business_id` column and is protected twice:

1. **Hibernate:** entities extend `TenantOwnedEntity`, whose `@TenantId` field makes Hibernate filter every query and set `business_id` on insert.
2. **PostgreSQL row-level security:** the app connects as `salon_app`, which does not own the tables and cannot bypass RLS. `TenantAwareDataSource` sets `app.business_id` on every connection checkout, and the `tenant_isolation` policy only exposes that business's rows.

The current business comes from `TenantContext`, bound only by trusted code: `PublicBusinessTenantFilter` for `/api/v1/public/b/{businessSlug}/**`, and `SessionAuthenticationFilter` for staff requests (the business selected in the session). New business-owned tables must call `SELECT enable_tenant_rls('<table>')` in their migration; `TenantSchemaCoverageTest` fails the build otherwise.

Bind a business **before** a transaction starts. The connection takes its business when it is checked out, so `TenantContext` refuses to bind inside an active transaction rather than silently keep the old business.

## Staff sign-in

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/auth/login` | Email, password and `client` (`web` or `android`). Web gets an HTTP-only `SALON_SESSION` cookie; Android gets a `token` to send as `Authorization: Bearer`. |
| `GET /api/v1/auth/me` | The person, their businesses and the selected business with role and branch access. |
| `POST /api/v1/auth/business` | Select the business to work in (people in several businesses choose after signing in). |
| `GET /api/v1/auth/sessions`, `DELETE /api/v1/auth/sessions/{id}` | List and sign out the person's own sessions. |
| `POST /api/v1/auth/logout` | End the current session. |
| `GET /api/v1/staff/locations` | Branches of the selected business this person can access. |

Sessions are rows in `auth_session` holding only a SHA-256 hash of the token. They expire after inactivity (web 12 hours, Android 30 days) and absolutely (web 7 days, Android 90 days), and stop working at once when the account or its membership is deactivated. Five wrong passwords lock the account for 15 minutes. Settings are under `salon.auth.*` (`AuthProperties`).

Cookie sessions need a CSRF token on changes: read the `XSRF-TOKEN` cookie and send it as the `X-XSRF-TOKEN` header. Bearer requests do not. For local HTTP development set `SALON_AUTH_COOKIE_SECURE=false`.

**Password reset:** "Forgot your password?" on the sign-in page (`/forgot-password`) emails a link (`POST /api/v1/auth/password-reset`, the same answer whether or not the address has an account; the email is sent after the request commits, so timing doesn't tell either). The link (`/reset-password/{token}`) works once, for an hour, and a newer request replaces it. Setting the new password (`POST /api/v1/auth/password-reset/confirm`, 12+ characters) clears any lockout and signs the person out on every device. Only a hash of the token is stored.

Not built yet: two-factor sign-in for owners and platform admins, rate limiting on sign-in and reset requests.

## Platform console and onboarding

Platform admins onboard businesses. The first platform admin is created at start-up from environment variables (does nothing if unset; never changes an existing password):

```bash
cd backend && SALON_BOOTSTRAP_PLATFORM_ADMIN_EMAIL=ops@example.com SALON_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD=at-least-12-chars ./gradlew bootRun
```

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/platform/businesses` | Create a business in Onboarding status and invite its owner. Returns the invite link. |
| `GET /api/v1/platform/businesses`, `GET .../{id}` | Businesses with branch, staff and open-invite counts; one business with its invites. |
| `POST .../{id}/status` | Lifecycle: Onboarding → Live ⇄ Suspended, any → Closed (final). Only Live businesses take bookings. |
| `POST .../{id}/owner-invites`, `DELETE .../{id}/invites/{inviteId}` | Send a fresh owner invite (replaces open ones to that email); revoke one. |
| `GET /api/v1/invites/{token}` | Public: what the invite is for, and whether the email already has an account. |
| `POST /api/v1/invites/{token}/accept` | Public: new people set a name and password (12+ characters); existing accounts confirm with their password. Signs them in to the business. |

Invite links are single use and expire after 7 days; only a hash of the token is stored. Email delivery is not built yet, so the link is returned to the platform admin to pass on. Invite links point at `salon.auth.app-base-url`.

### Demo

To set up a complete, bookable salon (Luna Hair Co: a branch with hours, three services, two stylists with shifts, live) in one go:

```bash
PLATFORM_ADMIN_EMAIL=ops@example.com PLATFORM_ADMIN_PASSWORD=at-least-12-chars scripts/demo/seed-salon.sh
```

It prints the booking site link and the owner's sign-in. `DEMO_SLUG` creates another salon.

The older walkthrough of onboarding alone:

```bash
PLATFORM_ADMIN_EMAIL=ops@example.com PLATFORM_ADMIN_PASSWORD=at-least-12-chars scripts/demo/onboarding-demo.sh
```

Then enter the salon code `bloom-studio` in the Android app (debug build on the emulator) to see it as a customer.

## Business setup

Owners set up their business under `/api/v1/admin` (console pages `/setup`, `/branches`, `/services`, `/staff`, `/team`). Managers can change opening hours, closures and offered services of the branches they are assigned to; everything else is owner-only. Reception and stylists cannot use these endpoints.

| Endpoint | Purpose |
|---|---|
| `PUT /api/v1/admin/business/policy`, `PUT /api/v1/admin/locations/{id}/policy` | Online booking rules (owners): start times every 5–60 minutes, minimum notice, how far ahead, the online change/cancel cut-off, upcoming bookings per customer; branches can override the first three (null = the business setting). Staff bookings ignore notice, horizon and the per-customer limit. |
| `GET/PUT /api/v1/admin/business`, `POST .../go-live` | Profile and branding; setup checklist; owners take an onboarding business live once a branch has hours, a service is offered there and a bookable staff member performs it. |
| `/api/v1/admin/locations` (+ `/{id}`, `/{id}/hours`, `/{id}/closures`, `/{id}/services/{serviceId}`) | Branches, weekly hours (several ranges per day, no overlaps), closures, which services a branch offers and branch price/duration overrides. |
| `/api/v1/admin/catalog`, `/categories`, `/services` | Service menu: categories, services with duration, clean-up time, price (cents, GST-inclusive), "from" prices, notices. |
| `/api/v1/admin/staff` | Staff profiles with branches, services and "bookable online". |
| `/api/v1/admin/team/members`, `/team/invites` | Who can sign in; invites with role, optional staff profile and branch access; remove access (signs the person out). |
| `GET /api/v1/admin/schedule?date&locationId` | The week (Monday–Sunday) containing `date`: each person's working intervals per day (usual week or that date's change), leave, breaks, and branch closures. |
| `/api/v1/admin/staff/{id}/schedule` (+ `/weekly`, `/days/{date}`) | Usual week and single-date changes (no intervals = day off). Each change has a `POST .../check` twin that returns warnings without saving. |
| `/api/v1/admin/staff/{id}/time-off` | Leave (whole days) and one-off breaks (part of one day). |

Links to branches or services of another business are ignored rather than created; reads and writes of another business's rows return 404.

### Schedules

Times are local to the branch. A lunch break in the usual week is simply two shifts. Someone can work at several branches, but never at two at the same time; overlapping shifts are rejected. Removing a branch from a staff profile removes their shifts there.

Before saving, the console calls the `/check` endpoint and lists warnings, which can be saved anyway (STF-7): a shift outside the branch's opening hours, on a day the branch is closed or has a closure, or a working day during leave. Clashes with booked appointments will be added to these warnings with online booking.

Managers change only work at branches they manage: saving someone's usual week keeps their shifts at other branches, and a day that includes work elsewhere is read-only. Going live now also needs the bookable staff member to have shifts at that branch.

Not built yet: stylist levels and level prices, linking an existing owner account to their own staff profile, appointment conflicts in schedule warnings (with booking).
